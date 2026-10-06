package cc.kowx712.autohoyolab.auth

import android.util.Log
import cc.kowx712.autohoyolab.data.model.HoyoAccount
import cc.kowx712.autohoyolab.data.model.HoyoGame
import cc.kowx712.autohoyolab.data.model.HoyoGameRole
import cc.kowx712.autohoyolab.data.model.RedeemResult
import cc.kowx712.autohoyolab.data.model.ResignResult
import cc.kowx712.autohoyolab.data.model.SignResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class HoyoLabApiClient(private val cookie: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Serializable
    private data class ApiResponse<T>(
        val retcode: Int,
        val message: String,
        val data: T? = null
    )

    @Serializable
    private data class AccountData(
        @SerialName("account_id") val accountId: String,
        @SerialName("account_name") val accountName: String? = null,
        @SerialName("email") val email: String? = null
    )

    @Serializable
    private data class GameRolesData(
        val list: List<HoyoGameRole>
    )

    @Serializable
    private data class CheckInInfoData(
        @SerialName("is_sign") val isSigned: Boolean = false,
        @SerialName("total_sign_day") val totalSignDay: Int = 0
    )

    @Serializable
    private data class EmptyData(
        val dummy: String? = null
    )

    @Serializable
    private data class ResignInfoData(
        @SerialName("resign_cnt_daily") val resignCntDaily: Int = 0,
        @SerialName("resign_cnt_monthly") val resignCntMonthly: Int = 0,
        @SerialName("resign_limit_daily") val resignLimitDaily: Int = 0,
        @SerialName("resign_limit_monthly") val resignLimitMonthly: Int = 0,
        @SerialName("sign_cnt_missed") val signCntMissed: Int = 0,
        @SerialName("quality_cnt") val qualityCnt: Int = 0,
        @SerialName("signed") val signed: Boolean = false,
        @SerialName("sign_cnt") val signCnt: Int = 0,
        @SerialName("cost") val cost: Int = 0,
        @SerialName("month_quality_cnt") val monthQualityCnt: Int = 0
    )

    @Serializable
    private data class TaskListData(
        val total: Int = 0,
        val list: List<TaskItem> = emptyList()
    )

    @Serializable
    private data class TaskItem(
        val id: Int,
        val name: String = "",
        val status: String = ""
    )

    @Serializable
    private data class PromoCodesResponse(
        val active: List<PromoCodeItem> = emptyList(),
        val inactive: List<PromoCodeItem> = emptyList(),
    )

    @Serializable
    private data class PromoCodeItem(
        val code: String = "",
    )

    @Serializable
    private data class RedeemResponse(
        val retcode: Int = 0,
        val message: String = "",
    )

    class CookieExpiredException(message: String) : Exception(message)
    class ApiException(message: String) : Exception(message)

    suspend fun validateCookie(): HoyoAccount = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api-account-os.hoyolab.com/auth/api/getUserAccountInfoByLToken")
                .headers(buildHeaders())
                .get()
                .build()

            val response = client.newCall(request).execute()
            val setCookieHeaders = response.headers("Set-Cookie")
            val body = response.body.string()

            if (!response.isSuccessful) {
                throw ApiException("HTTP ${response.code}")
            }

            val apiResponse = json.decodeFromString<ApiResponse<AccountData>>(body)

            if (apiResponse.retcode != 0) {
                throw CookieExpiredException(apiResponse.message)
            }

            val data = apiResponse.data ?: throw ApiException("Missing account data")

            HoyoAccount(
                accountId = data.accountId,
                accountName = data.accountName?.ifBlank { null },
                email = data.email?.ifBlank { null },
                validatedAt = System.currentTimeMillis(),
                expiresAt = LtokenExpiry.expiresAt(setCookieHeaders)
            )
        } catch (e: IOException) {
            throw ApiException("Network error: ${e.message}")
        }
    }

    suspend fun getUserGameRoles(): List<HoyoGameRole> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api-os-takumi.hoyolab.com/binding/api/getUserGameRolesByCookie")
                .headers(buildHeaders())
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body.string()

            if (!response.isSuccessful) {
                throw ApiException("HTTP ${response.code}")
            }

            val apiResponse = json.decodeFromString<ApiResponse<GameRolesData>>(body)

            if (apiResponse.retcode != 0) {
                throw CookieExpiredException(apiResponse.message)
            }

            apiResponse.data?.list ?: emptyList()
        } catch (e: IOException) {
            throw ApiException("Network error: ${e.message}")
        }
    }

    suspend fun sign(game: HoyoGame): SignResult = withContext(Dispatchers.IO) {
        try {
            // First, check if already signed in
            val infoRequest = Request.Builder()
                .url(game.infoUrl)
                .headers(buildHeaders(game.signGame))
                .get()
                .build()

            val infoResponse = client.newCall(infoRequest).execute()
            val infoBody = infoResponse.body.string()

            if (!infoResponse.isSuccessful) {
                return@withContext SignResult.Failed(
                    game.id,
                    "HTTP ${infoResponse.code}",
                    infoResponse.code
                )
            }

            val infoApiResponse = json.decodeFromString<ApiResponse<CheckInInfoData>>(infoBody)

            // Check for cookie expiration
            if (infoApiResponse.retcode == -100 || infoApiResponse.retcode == -1000) {
                return@withContext SignResult.CookieExpired(game.id)
            }

            // Check if already signed
            if (infoApiResponse.data?.isSigned == true) {
                return@withContext SignResult.AlreadySigned(game.id)
            }

            // Proceed with sign-in
            val signRequestBody = """{"act_id":"${game.actId}","lang":"en-us"}"""
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val signRequest = Request.Builder()
                .url(game.signUrl)
                .headers(buildHeaders(game.signGame))
                .post(signRequestBody)
                .build()

            val signResponse = client.newCall(signRequest).execute()
            val signBody = signResponse.body.string()

            if (!signResponse.isSuccessful) {
                return@withContext SignResult.Failed(
                    game.id,
                    "HTTP ${signResponse.code}",
                    signResponse.code
                )
            }

            val signApiResponse = json.decodeFromString<ApiResponse<EmptyData>>(signBody)

            when (signApiResponse.retcode) {
                0 -> SignResult.Success(game.id)
                -5003 -> SignResult.AlreadySigned(game.id) // Already signed today
                -100, -1000 -> SignResult.CookieExpired(game.id)
                else -> SignResult.Failed(
                    game.id,
                    signApiResponse.message,
                    signApiResponse.retcode
                )
            }
        } catch (_: IOException) {
            SignResult.NetworkError(game.id)
        } catch (e: Exception) {
            SignResult.Failed(game.id, e.message ?: "Unknown error", null)
        }
    }

    suspend fun resign(game: HoyoGame): ResignResult = withContext(Dispatchers.IO) {
        try {
            // First, check resign eligibility
            val resignInfoRequest = Request.Builder()
                .url(game.resignInfoUrl)
                .headers(buildHeaders(game.signGame))
                .get()
                .build()

            val resignInfoResponse = client.newCall(resignInfoRequest).execute()
            val resignInfoBody = resignInfoResponse.body.string()

            // HTTP errors or non-zero retcode means resign is not supported for this game
            if (!resignInfoResponse.isSuccessful) {
                return@withContext ResignResult.NotSupported(game.id)
            }

            val resignInfoApiResponse = json.decodeFromString<ApiResponse<ResignInfoData>>(resignInfoBody)

            // Check for cookie expiration
            if (resignInfoApiResponse.retcode == -100 || resignInfoApiResponse.retcode == -1000) {
                return@withContext ResignResult.CookieExpired(game.id)
            }

            // Non-zero retcode means resign is not supported for this game
            if (resignInfoApiResponse.retcode != 0) {
                return@withContext ResignResult.NotSupported(game.id)
            }

            val resignInfo = resignInfoApiResponse.data ?: return@withContext ResignResult.Failed(
                game.id,
                "Missing resign info data",
                null
            )

            // Check eligibility conditions
            if (resignInfo.signCntMissed <= 0) {
                return@withContext ResignResult.NotEligible(game.id, "No missed check-ins")
            }

            if (resignInfo.resignCntDaily >= resignInfo.resignLimitDaily) {
                return@withContext ResignResult.NotEligible(game.id, "Daily resign limit reached")
            }

            if (resignInfo.resignCntMonthly >= resignInfo.resignLimitMonthly) {
                return@withContext ResignResult.NotEligible(game.id, "Monthly resign limit reached")
            }

            // Check if we need to complete tasks first
            if (resignInfo.monthQualityCnt < resignInfo.resignLimitMonthly) {
                // Need to complete tasks to qualify - fetch task list first
                val taskListRequest = Request.Builder()
                    .url(game.taskListUrl)
                    .headers(buildHeaders(game.signGame))
                    .get()
                    .build()

                val taskListResponse = client.newCall(taskListRequest).execute()
                val taskListBody = taskListResponse.body.string()

                if (!taskListResponse.isSuccessful) {
                    return@withContext ResignResult.Failed(
                        game.id,
                        "HTTP ${taskListResponse.code}",
                        taskListResponse.code
                    )
                }

                val taskListApiResponse = json.decodeFromString<ApiResponse<TaskListData>>(taskListBody)

                if (taskListApiResponse.retcode != 0) {
                    return@withContext ResignResult.Failed(
                        game.id,
                        "Task list fetch failed: ${taskListApiResponse.message}",
                        taskListApiResponse.retcode
                    )
                }

                // Find a task with status "TT_Ready"
                val readyTask = taskListApiResponse.data?.list?.firstOrNull { it.status == "TT_Ready" }
                    ?: return@withContext ResignResult.Failed(
                        game.id,
                        "No ready tasks available",
                        null
                    )

                // Complete the task
                val completeRequestBody = """{"id":${readyTask.id},"lang":"en-us","act_id":"${game.actId}"}"""
                    .toRequestBody("application/json; charset=utf-8".toMediaType())

                val completeRequest = Request.Builder()
                    .url(game.taskCompleteUrl)
                    .headers(buildHeaders(game.signGame))
                    .post(completeRequestBody)
                    .build()

                val completeResponse = client.newCall(completeRequest).execute()
                val completeBody = completeResponse.body.string()

                if (!completeResponse.isSuccessful) {
                    return@withContext ResignResult.Failed(
                        game.id,
                        "HTTP ${completeResponse.code}",
                        completeResponse.code
                    )
                }

                val completeApiResponse = json.decodeFromString<ApiResponse<EmptyData>>(completeBody)

                if (completeApiResponse.retcode != 0) {
                    return@withContext ResignResult.Failed(
                        game.id,
                        "Complete task failed: ${completeApiResponse.message}",
                        completeApiResponse.retcode
                    )
                }

                // Get award
                val awardRequestBody = """{"act_id":"${game.actId}","lang":"en-us","id":${readyTask.id}}"""
                    .toRequestBody("application/json; charset=utf-8".toMediaType())

                val awardRequest = Request.Builder()
                    .url(game.taskAwardUrl)
                    .headers(buildHeaders(game.signGame))
                    .post(awardRequestBody)
                    .build()

                val awardResponse = client.newCall(awardRequest).execute()
                val awardBody = awardResponse.body.string()

                if (!awardResponse.isSuccessful) {
                    return@withContext ResignResult.Failed(
                        game.id,
                        "HTTP ${awardResponse.code}",
                        awardResponse.code
                    )
                }

                val awardApiResponse = json.decodeFromString<ApiResponse<EmptyData>>(awardBody)

                if (awardApiResponse.retcode != 0) {
                    return@withContext ResignResult.Failed(
                        game.id,
                        "Award claim failed: ${awardApiResponse.message}",
                        awardApiResponse.retcode
                    )
                }
            }

            // Now perform resign
            val resignRequestBody = """{"act_id":"${game.actId}"}"""
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val resignRequest = Request.Builder()
                .url(game.resignUrl)
                .headers(buildHeaders(game.signGame))
                .post(resignRequestBody)
                .build()

            val resignResponse = client.newCall(resignRequest).execute()
            val resignBody = resignResponse.body.string()

            if (!resignResponse.isSuccessful) {
                return@withContext ResignResult.Failed(
                    game.id,
                    "HTTP ${resignResponse.code}",
                    resignResponse.code
                )
            }

            val resignApiResponse = json.decodeFromString<ApiResponse<EmptyData>>(resignBody)

            when (resignApiResponse.retcode) {
                0 -> ResignResult.Success(game.id)
                -100, -1000 -> ResignResult.CookieExpired(game.id)
                else -> ResignResult.Failed(
                    game.id,
                    resignApiResponse.message,
                    resignApiResponse.retcode
                )
            }
        } catch (_: IOException) {
            ResignResult.NetworkError(game.id)
        } catch (e: Exception) {
            ResignResult.Failed(game.id, e.message ?: "Unknown error", null)
        }
    }

    suspend fun fetchPromoCodes(game: HoyoGame): List<String>? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(game.codesUrl)
                .addHeader("User-Agent", BROWSER_USER_AGENT)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body.string()

            if (!response.isSuccessful) {
                return@withContext null
            }

            val promoCodes = json.decodeFromString<PromoCodesResponse>(body)
            promoCodes.active.map { it.code }.filter { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "fetchPromoCodes failed for ${game.displayName}: ${e::class.simpleName}: ${e.message}")
            null
        }
    }

    suspend fun redeemCode(
        game: HoyoGame,
        uid: String,
        region: String,
        code: String,
    ): RedeemResult = withContext(Dispatchers.IO) {
        val endpoint = game.redeemUrl
        if (endpoint == null) {
            return@withContext RedeemResult.Failed(game.id, code, "No redeem endpoint", null)
        }

        try {
            val url = endpoint.toHttpUrl().newBuilder()
                .addQueryParameter("cdkey", code)
                .addQueryParameter("uid", uid)
                .addQueryParameter("region", region)
                .addQueryParameter("lang", "en")
                .addQueryParameter("game_biz", game.id)
                .addQueryParameter("t", System.currentTimeMillis().toString())
                .apply {
                    if (game.redeemSlug == "genshin") {
                        addQueryParameter("sLangKey", "en-us")
                    }
                }
                .build()

            val headersBuilder = Headers.Builder()
                .add("Cookie", cookie)
                .add("User-Agent", BROWSER_USER_AGENT)
            if (game.redeemSlug == "themis") {
                headersBuilder.add("Referer", "https://tot.hoyoverse.com/")
            }

            val request = Request.Builder()
                .url(url)
                .headers(headersBuilder.build())
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body.string()

            if (!response.isSuccessful) {
                return@withContext RedeemResult.NetworkError(game.id, code)
            }

            val redeemResponse = json.decodeFromString<RedeemResponse>(body)

            when (redeemResponse.retcode) {
                0 -> RedeemResult.Success(game.id, code)
                -2017, -2018 -> RedeemResult.AlreadyRedeemed(game.id, code)
                -2021, -2011 -> RedeemResult.LevelTooLow(game.id, code)
                -2016 -> RedeemResult.Cooldown(game.id, code)
                -1071, -1073, -1075, -100, -1000 ->
                    RedeemResult.CredentialError(game.id, code, redeemResponse.message, redeemResponse.retcode)

                else -> RedeemResult.Failed(
                    game.id,
                    code,
                    redeemResponse.message,
                    redeemResponse.retcode
                )
            }
        } catch (e: IOException) {
            Log.e(TAG, "redeemCode IO error for $code: ${e.message}")
            RedeemResult.NetworkError(game.id, code)
        } catch (e: Exception) {
            Log.e(TAG, "redeemCode failed for $code: ${e::class.simpleName}: ${e.message}")
            RedeemResult.Failed(game.id, code, e.message ?: "Unknown error", null)
        }
    }

    private fun buildHeaders(signGame: String? = null): Headers {
        val builder = Headers.Builder()
            .add("Cookie", cookie)
            .add("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Mobile Safari/537.36")
            .add("Referer", "https://act.hoyolab.com/")
            .add("Origin", "https://act.hoyolab.com")
            .add("x-rpc-app_version", "2.71.1")
            .add("x-rpc-client_type", "5")
        if (signGame != null) {
            builder.add("x-rpc-signgame", signGame)
        }
        return builder.build()
    }

    companion object {
        private const val TAG = "HoyoLabApiClient"
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36"
    }
}
