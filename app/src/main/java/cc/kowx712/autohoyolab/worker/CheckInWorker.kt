package cc.kowx712.autohoyolab.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cc.kowx712.autohoyolab.R
import cc.kowx712.autohoyolab.data.cookie.CookieStore
import cc.kowx712.autohoyolab.data.local.AppDatabase
import cc.kowx712.autohoyolab.data.local.CheckInLog
import cc.kowx712.autohoyolab.data.model.CheckInResult
import cc.kowx712.autohoyolab.data.model.HoyoGame
import cc.kowx712.autohoyolab.data.model.HoyoGameRole
import cc.kowx712.autohoyolab.network.HoyolabApiClient
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

        // Check if cookie is already marked as expired
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

        val apiClient = HoyolabApiClient(cookie)

        // Validate cookie
        try {
            val account = apiClient.validateCookie()
            cookieStore.saveAccountInfo(
                accountId = account.accountId,
                accountName = account.accountName,
                email = account.email,
                validatedAt = account.validatedAt
            )
            Log.d(TAG, "Cookie validated for account: ${account.accountId}")
        } catch (e: HoyolabApiClient.CookieExpiredException) {
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
        val results = mutableListOf<CheckInResult>()
        for ((game, role) in gamesToCheckIn) {
            Log.d(TAG, "Checking in for ${game.displayName} - ${role.regionName} (Lv.${role.level})...")
            val result = apiClient.checkIn(game)
            results.add(result)

            // Save log
            val status = when (result) {
                is CheckInResult.Success -> "SUCCESS"
                is CheckInResult.AlreadySigned -> "ALREADY_SIGNED"
                is CheckInResult.Failed -> "FAILED"
                is CheckInResult.CookieExpired -> "COOKIE_EXPIRED"
                is CheckInResult.NetworkError -> "NETWORK_ERROR"
            }

            val log = CheckInLog(
                gameId = game.id,
                gameUid = role.gameUid,
                region = role.region,
                timestamp = System.currentTimeMillis(),
                status = status,
                message = when (result) {
                    is CheckInResult.Failed -> result.message
                    is CheckInResult.Success -> applicationContext.getString(R.string.log_message_success)
                    is CheckInResult.AlreadySigned -> applicationContext.getString(R.string.log_message_already_signed)
                    is CheckInResult.CookieExpired -> applicationContext.getString(R.string.log_message_cookie_expired)
                    is CheckInResult.NetworkError -> applicationContext.getString(R.string.log_message_network_error)
                },
                retcode = when (result) {
                    is CheckInResult.Failed -> result.retcode
                    else -> null
                }
            )

            database.checkInLogDao().insert(log)
            Log.d(TAG, "Result for ${game.displayName}: $status")

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
