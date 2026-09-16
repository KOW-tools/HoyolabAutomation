package cc.kowx712.autohoyolab.network

import cc.kowx712.autohoyolab.data.model.CheckInResult
import cc.kowx712.autohoyolab.data.model.HoyoAccount
import cc.kowx712.autohoyolab.data.model.HoyoGame
import cc.kowx712.autohoyolab.data.model.HoyoGameRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class HoyolabApiClient(private val cookie: String) {
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
                validatedAt = System.currentTimeMillis()
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

    suspend fun checkIn(game: HoyoGame): CheckInResult = withContext(Dispatchers.IO) {
        try {
            // First, check if already signed in
            val infoRequest = Request.Builder()
                .url(game.infoUrl)
                .headers(buildHeaders())
                .get()
                .build()

            val infoResponse = client.newCall(infoRequest).execute()
            val infoBody = infoResponse.body.string()

            if (!infoResponse.isSuccessful) {
                return@withContext CheckInResult.Failed(
                    game.id,
                    "HTTP ${infoResponse.code}",
                    infoResponse.code
                )
            }

            val infoApiResponse = json.decodeFromString<ApiResponse<CheckInInfoData>>(infoBody)

            // Check for cookie expiration
            if (infoApiResponse.retcode == -100 || infoApiResponse.retcode == -1000) {
                return@withContext CheckInResult.CookieExpired(game.id)
            }

            // Check if already signed
            if (infoApiResponse.data?.isSigned == true) {
                return@withContext CheckInResult.AlreadySigned(game.id)
            }

            // Proceed with sign-in
            val signRequestBody = """{"act_id":"${game.actId}"}"""
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val signRequest = Request.Builder()
                .url(game.signUrl)
                .headers(buildHeaders())
                .post(signRequestBody)
                .build()

            val signResponse = client.newCall(signRequest).execute()
            val signBody = signResponse.body.string()

            if (!signResponse.isSuccessful) {
                return@withContext CheckInResult.Failed(
                    game.id,
                    "HTTP ${signResponse.code}",
                    signResponse.code
                )
            }

            val signApiResponse = json.decodeFromString<ApiResponse<EmptyData>>(signBody)

            when (signApiResponse.retcode) {
                0 -> CheckInResult.Success(game.id)
                -5003 -> CheckInResult.AlreadySigned(game.id) // Already signed today
                -100, -1000 -> CheckInResult.CookieExpired(game.id)
                else -> CheckInResult.Failed(
                    game.id,
                    signApiResponse.message,
                    signApiResponse.retcode
                )
            }
        } catch (_: IOException) {
            CheckInResult.NetworkError(game.id)
        } catch (e: Exception) {
            CheckInResult.Failed(game.id, e.message ?: "Unknown error", null)
        }
    }

    private fun buildHeaders() = okhttp3.Headers.Builder()
        .add("Cookie", cookie)
        .add("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Mobile Safari/537.36")
        .add("Referer", "https://act.hoyolab.com/")
        .add("Origin", "https://act.hoyolab.com")
        .add("x-rpc-app_version", "2.71.1")
        .add("x-rpc-client_type", "5")
        .build()
}
