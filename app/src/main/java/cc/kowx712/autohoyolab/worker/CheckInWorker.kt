package cc.kowx712.autohoyolab.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cc.kowx712.autohoyolab.auth.HoyoLabAuthClient
import cc.kowx712.autohoyolab.data.cookie.CookieStore
import cc.kowx712.autohoyolab.data.local.AppDatabase
import cc.kowx712.autohoyolab.data.local.CheckInLog
import cc.kowx712.autohoyolab.data.model.SignResult
import cc.kowx712.autohoyolab.data.model.HoyoGame
import cc.kowx712.autohoyolab.data.model.HoyoGameRole
import cc.kowx712.autohoyolab.data.model.ResignResult
import cc.kowx712.autohoyolab.auth.HoyoLabApiClient
import cc.kowx712.autohoyolab.notification.CheckInNotifier
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

class CheckInWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val cookieStore = CookieStore(context)
    private val database = AppDatabase.getDatabase(context)
    private val notifier = CheckInNotifier(context)

    override suspend fun doWork(): Result {
        Log.d(TAG, "CheckInWorker started")

        // Prune logs older than 30 days
        try {
            val cutoffTime = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)
            database.checkInLogDao().deleteOlderThan(cutoffTime)
            Log.d(TAG, "Pruned logs older than 30 days")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prune old logs: ${e.message}")
        }

        // Check if cookie exists
        if (!cookieStore.hasCookie()) {
            Log.d(TAG, "No cookie found, skipping check-in")
            return Result.success()
        }

        // Refresh tokens before checking the expired marker or making any API request.
        if (cookieStore.hasRefreshCredentials()) {
            Log.d(TAG, "Refresh credentials found, refreshing tokens...")
            try {
                val stoken = cookieStore.getStoken()
                val ltuidV2 = cookieStore.getLtuidV2()
                val ltmidV2 = cookieStore.getLtmidV2()
                val accountIdV2 = cookieStore.getAccountIdV2()
                val accountMidV2 = cookieStore.getAccountMidV2()

                if (stoken != null && ltuidV2 != null && ltmidV2 != null) {
                    val authClient = HoyoLabAuthClient()
                    val refreshResult = authClient.refreshTokens(stoken, ltuidV2, ltmidV2)

                    if (refreshResult.ltokenV2 != null && refreshResult.cookieTokenV2 != null) {
                        // Build complete cookie string
                        val refreshedCookie = "stoken=$stoken; ltoken_v2=${refreshResult.ltokenV2}; " +
                                "ltuid_v2=$ltuidV2; ltmid_v2=$ltmidV2; " +
                                "cookie_token_v2=${refreshResult.cookieTokenV2}; " +
                                "account_mid_v2=${accountMidV2 ?: ltmidV2}; " +
                                "account_id_v2=${accountIdV2 ?: ltuidV2}"

                        // Save refreshed cookie
                        // Keep the previous expiry when the endpoint omits Max-Age.
                        val expiresAt = refreshResult.expiresAt.takeIf { it > 0 }
                            ?: cookieStore.getExpiresAt()
                        cookieStore.saveCookie(refreshedCookie, expiresAt)
                        Log.d(TAG, "Tokens refreshed successfully")
                    } else {
                        Log.e(TAG, "Token refresh returned incomplete tokens")
                        cookieStore.markAsExpired()
                        notifier.showCookieExpiredNotification()
                        notifier.dismissProgressNotification()
                        return Result.failure()
                    }
                }
            } catch (e: HoyoLabAuthClient.RefreshCredentialsRejectedException) {
                Log.e(TAG, "Refresh credentials were rejected: ${e.message}")
                cookieStore.markAsExpired()
                notifier.showCookieExpiredNotification()
                notifier.dismissProgressNotification()
                return Result.failure()
            } catch (e: HoyoLabAuthClient.AuthException) {
                // Network, HTTP, and malformed-response errors are transient.
                // Preserve the credentials and let WorkManager retry later.
                Log.e(TAG, "Token refresh failed temporarily: ${e.message}")
                notifier.showNetworkErrorNotification()
                notifier.dismissProgressNotification()
                return Result.retry()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh tokens: ${e.message}")
                notifier.showNetworkErrorNotification()
                notifier.dismissProgressNotification()
                return Result.retry()
            }
        }

        // A cookie with no refresh credentials cannot be repaired in the background.
        if (cookieStore.isExpired()) {
            Log.e(TAG, "Cookie is marked as expired")
            notifier.showCookieExpiredNotification()
            notifier.dismissProgressNotification()
            return Result.failure()
        }

        // Load cookie
        val cookie = cookieStore.getCookie()
        if (cookie == null) {
            Log.e(TAG, "Cookie exists but couldn't be retrieved")
            notifier.showCookieExpiredNotification()
            notifier.dismissProgressNotification()
            return Result.failure()
        }

        val apiClient = HoyoLabApiClient(cookie)

        // Validate cookie
        try {
            val account = apiClient.validateCookie()
            cookieStore.saveAccountInfo(
                accountId = account.accountId,
                accountName = account.accountName,
                email = account.email,
                validatedAt = account.validatedAt
            )
            cookieStore.updateExpiresAt(account.expiresAt)
            Log.d(TAG, "Cookie validated for account: ${account.accountId}")
        } catch (e: HoyoLabApiClient.CookieExpiredException) {
            Log.e(TAG, "Cookie expired: ${e.message}")
            cookieStore.markAsExpired()
            notifier.showCookieExpiredNotification()
            notifier.dismissProgressNotification()
            return Result.failure()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to validate cookie: ${e.message}")
            notifier.showNetworkErrorNotification()
            notifier.dismissProgressNotification()
            return Result.retry()
        }

        // Get user game roles
        val gameRoles = try {
            apiClient.getUserGameRoles()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get game roles: ${e.message}")
            notifier.showNetworkErrorNotification()
            return Result.retry()
        }

        Log.d(TAG, "Found ${gameRoles.size} game roles")

        // Map to supported games
        val allGameProfiles = gameRoles
            .mapNotNull { role ->
                val game = HoyoGame.fromGameBiz(role.gameBiz)
                if (game != null) {
                    game to role
                } else {
                    Log.w(TAG, "Unsupported game_biz: ${role.gameBiz}")
                    null
                }
            }

        // Group by game to handle multiple servers
        val gamesByGameId = allGameProfiles.groupBy { it.first.id }

        // Load user preferences
        val preferences = database.gameProfilePreferenceDao().getAllPreferences()
        val preferenceMap = preferences.associate { it.gameId to it.selectedGameUid }

        // Select one profile per game based on preference or priority
        val gamesToCheckIn = gamesByGameId.mapNotNull { (gameId, profiles) ->
            if (profiles.isEmpty()) return@mapNotNull null

            val selected = if (profiles.size == 1) {
                // Only one server, use it
                profiles.first()
            } else {
                // Multiple servers: check preference first
                val preferredUid = preferenceMap[gameId]
                if (preferredUid != null) {
                    // User has preference, use it
                    profiles.firstOrNull { it.second.gameUid == preferredUid }
                        ?: run {
                            Log.w(TAG, "Preferred profile $preferredUid not found for $gameId, using priority fallback")
                            selectByPriority(profiles)
                        }
                } else {
                    // No preference: use highest level, or first if tie
                    selectByPriority(profiles)
                }
            }

            Log.d(TAG, "Selected ${selected.first.displayName} - ${selected.second.regionName} (Lv.${selected.second.level})")
            selected
        }

        if (gamesToCheckIn.isEmpty()) {
            Log.w(TAG, "No supported games found")
            notifier.showNoGamesNotification()
            notifier.dismissProgressNotification()
            return Result.success()
        }

        // Perform check-ins
        val results = mutableListOf<SignResult>()
        val resignResults = mutableListOf<ResignResult>()
        for ((game, role) in gamesToCheckIn) {
            Log.d(TAG, "Checking in for ${game.displayName} - ${role.regionName} (Lv.${role.level})...")
            val result = apiClient.sign(game)
            results.add(result)

            // Save log
            val status = when (result) {
                is SignResult.Success -> "SUCCESS"
                is SignResult.AlreadySigned -> "ALREADY_SIGNED"
                is SignResult.Failed -> "FAILED"
                is SignResult.CookieExpired -> "COOKIE_EXPIRED"
                is SignResult.NetworkError -> "NETWORK_ERROR"
            }

            val log = CheckInLog(
                gameId = game.id,
                gameUid = role.gameUid,
                region = role.region,
                timestamp = System.currentTimeMillis(),
                status = status,
                message = when (result) {
                    is SignResult.Failed -> result.message
                    else -> null
                },
                retcode = when (result) {
                    is SignResult.Failed -> result.retcode
                    else -> null
                }
            )

            database.checkInLogDao().insert(log)
            Log.d(TAG, "Result for ${game.displayName}: $status")

            // Add delay between requests
            delay(2.seconds)

            // Attempt resign after check-in
            Log.d(TAG, "Attempting resign for ${game.displayName}...")
            val resignResult = apiClient.resign(game)
            resignResults.add(resignResult)

            val resignStatus = when (resignResult) {
                is ResignResult.Success -> {
                    Log.d(TAG, "Resign successful for ${game.displayName}")
                    "RESIGN_SUCCESS"
                }

                is ResignResult.NotSupported -> {
                    Log.d(TAG, "Resign not supported for ${game.displayName}")
                    "RESIGN_NOT_SUPPORTED"
                }

                is ResignResult.NotEligible -> {
                    Log.d(TAG, "Resign not eligible for ${game.displayName}: ${resignResult.reason}")
                    "RESIGN_NOT_ELIGIBLE"
                }

                is ResignResult.Failed -> {
                    Log.d(TAG, "Resign failed for ${game.displayName}: ${resignResult.message}")
                    "RESIGN_FAILED"
                }

                is ResignResult.CookieExpired -> {
                    Log.e(TAG, "Cookie expired during resign for ${game.displayName}")
                    "RESIGN_COOKIE_EXPIRED"
                }

                is ResignResult.NetworkError -> {
                    Log.e(TAG, "Network error during resign for ${game.displayName}")
                    "RESIGN_NETWORK_ERROR"
                }
            }

            if (resignResult !is ResignResult.NotSupported && resignResult !is ResignResult.NotEligible) {
                val resignLog = CheckInLog(
                    gameId = game.id,
                    gameUid = role.gameUid,
                    region = role.region,
                    timestamp = System.currentTimeMillis(),
                    status = resignStatus,
                    message = when (resignResult) {
                        is ResignResult.Failed -> resignResult.message
                        else -> null
                    },
                    retcode = when (resignResult) {
                        is ResignResult.Failed -> resignResult.retcode
                        else -> null
                    }
                )

                database.checkInLogDao().insert(resignLog)
            }

            // Add delay between requests
            delay(2.seconds)
        }

        // Show summary notification
        notifier.showSummaryNotification(results)

        // Dismiss progress notification
        notifier.dismissProgressNotification()

        // Re-schedule next alarm
        AlarmScheduler.scheduleNextMidnight(applicationContext)

        Log.d(TAG, "CheckInWorker completed")
        return Result.success()
    }

    private fun selectByPriority(profiles: List<Pair<HoyoGame, HoyoGameRole>>): Pair<HoyoGame, HoyoGameRole> {
        // Priority: highest level > first occurrence
        return profiles.maxByOrNull { it.second.level } ?: profiles.first()
    }

    companion object {
        private const val TAG = "CheckInWorker"
    }
}
