package cc.kowx712.autohoyolab.auth

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * HoYoLAB authentication client for stoken-based login
 */
class HoyoLabAuthClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Complete authentication flow to obtain all required cookies
     *
     * @param account Email or username
     * @param password Account password
     * @param deviceId 16-character device identifier (will be generated if not provided)
     * @param deviceName Device name for identification
     * @param deviceModel Device model/OS name
     * @param onEmailVerificationNeeded Suspend callback that receives the pending
     * action ticket and returns the verification code. No OTP email is sent
     * automatically; the caller decides when to call [sendVerificationEmail].
     * @return CompleteCookie containing all authentication tokens
     * @throws AuthException if authentication fails
     */
    suspend fun authenticate(
        account: String,
        password: String,
        deviceId: String = DeviceIdGenerator.generate(),
        deviceName: String = "Android Device",
        deviceModel: String = "Android",
        onCaptchaVerificationNeeded: suspend (CaptchaSession) -> String = {
            throw AuthException("Captcha verification required but no handler provided")
        },
        onEmailVerificationNeeded: suspend (ActionTicket) -> String = {
            throw AuthException("Email verification required but no handler provided")
        }
    ): CompleteCookie {
        // Keep this deviceId unchanged through every retry
        var verifyHeader: String? = null
        var aigisHeader: String? = null

        fun login(): AppLoginResult {
            return appLogin(
                account = account,
                password = password,
                deviceId = deviceId,
                deviceName = deviceName,
                deviceModel = deviceModel,
                verifyHeader = verifyHeader,
                aigisHeader = aigisHeader
            )
        }

        var loginResult: AppLoginResult? = null
        var loginAttempts = 0
        while (loginResult == null) {
            if (++loginAttempts > MAX_CHALLENGE_ATTEMPTS) {
                throw AuthException("Login did not complete after challenge retries")
            }
            try {
                loginResult = login()
            } catch (e: CaptchaRequiredException) {
                aigisHeader = onCaptchaVerificationNeeded(e.session)
                if (aigisHeader.isBlank()) throw AuthException("Captcha verification returned an empty result")
            } catch (e: EmailVerificationRequiredException) {
                val code = onEmailVerificationNeeded(e.actionTicket)
                verifyEmailCode(code, e.actionTicket)
                // genshin.py sends the final ticket retry without the old captcha result.
                aigisHeader = null
                verifyHeader = e.actionTicket.toRpcVerifyHeader()
            }
        }
        val completedLogin = loginResult

        // Step 2: Fetch additional tokens
        val refreshResult = fetchAdditionalTokens(completedLogin)

        // Validate results
        if (refreshResult.ltokenV2 == null || refreshResult.cookieTokenV2 == null) {
            throw AuthException("Failed to obtain required tokens")
        }

        return CompleteCookie(
            stoken = completedLogin.stoken,
            ltokenV2 = refreshResult.ltokenV2,
            ltuidV2 = completedLogin.ltuidV2,
            ltmidV2 = completedLogin.ltmidV2,
            cookieTokenV2 = refreshResult.cookieTokenV2,
            accountMidV2 = completedLogin.accountMidV2,
            accountIdV2 = completedLogin.accountIdV2,
            expiresAt = refreshResult.expiresAt
        )
    }

    /**
     * Refresh ltoken_v2 and cookie_token_v2 using an existing stoken
     *
     * @param stoken The stored stoken value
     * @param ltuidV2 The stored ltuid_v2 value
     * @param ltmidV2 The stored ltmid_v2 value
     * @return TokenRefreshResult with refreshed tokens
     * @throws AuthException if refresh fails
     */
    fun refreshTokens(
        stoken: String,
        ltuidV2: String,
        ltmidV2: String
    ): TokenRefreshResult {
        val loginResult = AppLoginResult(
            stoken = stoken,
            ltuidV2 = ltuidV2,
            ltmidV2 = ltmidV2,
            accountIdV2 = ltuidV2,
            accountMidV2 = ltmidV2
        )

        return fetchAdditionalTokens(loginResult)
    }

    private fun appLogin(
        account: String,
        password: String,
        deviceId: String,
        deviceName: String,
        deviceModel: String,
        verifyHeader: String? = null,
        aigisHeader: String? = null
    ): AppLoginResult {
        // Encrypt credentials
        val encryptedAccount = CredentialsEncryptor.encryptCredentials(account)
        val encryptedPassword = CredentialsEncryptor.encryptCredentials(password)

        // Build compact JSON body (no spaces)
        val jsonBody = """{"account":"$encryptedAccount","password":"$encryptedPassword"}"""

        // Generate DS signature
        val ds = DSGenerator.generateAppLoginDS(jsonBody)

        // Build request
        val requestBody = jsonBody.toRequestBody(jsonMediaType)
        val requestBuilder = Request.Builder()
            .url(HoyoLabAuthConstants.APP_LOGIN_URL)
            .post(requestBody)
            .header("Content-Type", "application/json")
            .header("x-rpc-app_id", "c9oqaq3s3gu8")
            .header("x-rpc-client_type", "2")
            .header("x-rpc-aigis_v4", "true")
            .header("x-rpc-app_version", "4.8.0")
            .header("x-rpc-sdk_version", "2.2.0")
            .header("x-rpc-device_id", deviceId)
            .header("x-rpc-device_name", deviceName)
            .header("x-rpc-device_model", deviceModel)
            .header("ds", ds)

        // Add verify header on retry
        if (verifyHeader != null) {
            requestBuilder.header("x-rpc-verify", verifyHeader)
        }
        if (aigisHeader != null) {
            requestBuilder.header("x-rpc-aigis", aigisHeader)
        }

        val request = requestBuilder.build()

        // Execute request
        try {
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body.string()
                if (responseBody.isBlank()) {
                    throw AuthException("Login returned an empty response")
                }
                if (!response.isSuccessful) {
                    throw AuthException("Login request failed with HTTP ${response.code}")
                }

                val jsonObject = JSONObject(responseBody)
                when (val retcode = jsonObject.getInt("retcode")) {
                    0 -> {
                        // Success
                        val data = jsonObject.getJSONObject("data")
                        val token = data.getJSONObject("token").getString("token")
                        val userInfo = data.getJSONObject("user_info")
                        val aid = userInfo.getString("aid")
                        val mid = userInfo.getString("mid")

                        return AppLoginResult(
                            stoken = token,
                            ltuidV2 = aid,
                            ltmidV2 = mid,
                            accountIdV2 = aid,
                            accountMidV2 = mid
                        )
                    }

                    -3101 -> {
                        val aigisHeader = response.header("x-rpc-aigis")
                        val session = parseCaptchaSession(aigisHeader)
                        throw CaptchaRequiredException("Captcha verification required", aigisHeader, session)
                    }

                    -3239 -> {
                        val verifyHeader = response.header("x-rpc-verify")
                            ?: throw AuthException("Email verification required but no x-rpc-verify header found")

                        val verifyJson = JSONObject(verifyHeader)
                        val verifyStrJson = when (val rawVerifyStr = verifyJson.opt("verify_str")) {
                            is JSONObject -> rawVerifyStr
                            is String -> JSONObject(rawVerifyStr)
                            null -> throw AuthException("Email verification response has an invalid verify_str")
                            else -> throw AuthException("Email verification response has an invalid verify_str")
                        }

                        val actionTicket = ActionTicket(
                            riskTicket = verifyJson.getString("risk_ticket"),
                            ticket = verifyStrJson.getString("ticket"),
                            verifyType = verifyStrJson.getString("verify_type")
                        )

                        throw EmailVerificationRequiredException(
                            "Email verification required",
                            actionTicket
                        )
                    }

                    else -> {
                        val message = jsonObject.optString("message", "Unknown error")
                        throw AuthException("Login failed: $message (retcode: $retcode)")
                    }
                }
            }
        } catch (e: IOException) {
            throw AuthException("Network error: ${e.message}", e)
        } catch (e: AuthException) {
            throw e
        } catch (e: Exception) {
            throw AuthException("Unexpected error: ${e.message}", e)
        }
    }

    private fun fetchAdditionalTokens(loginResult: AppLoginResult): TokenRefreshResult {
        // Generate DS for this request
        val ds = DSGenerator.generateDynamicSecret(HoyoLabAuthConstants.Salts.APP_LOGIN)

        // Build JSON body
        val jsonBody = """{"dst_token_types":[2,4]}"""

        // Build cookie header
        // CRITICAL: Only stoken and mid are required. Do NOT include ltuid_v2.
        // Format must be exactly: "stoken=xxx; mid=xxx" where mid = ltmid_v2
        val cookieHeader = "stoken=${loginResult.stoken}; mid=${loginResult.ltmidV2}"

        // Build request
        val requestBody = jsonBody.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(HoyoLabAuthConstants.COOKIE_V2_REFRESH_URL)
            .post(requestBody)
            .header("Content-Type", "application/json")
            .header("x-rpc-app_id", "c9oqaq3s3gu8")
            .header("ds", ds)
            .header("Cookie", cookieHeader)
            .build()

        // Execute request
        try {
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body.string()
                if (responseBody.isBlank()) {
                    throw AuthException("Token refresh returned an empty response")
                }
                if (!response.isSuccessful) {
                    throw AuthException("Token refresh failed with HTTP ${response.code}")
                }

                val jsonObject = JSONObject(responseBody)
                val retcode = jsonObject.getInt("retcode")

                if (retcode != 0) {
                    val message = jsonObject.optString("message", "Unknown error")
                    throw RefreshCredentialsRejectedException(
                        "Token fetch failed: $message (retcode: $retcode)"
                    )
                }

                val data = jsonObject.getJSONObject("data")
                val tokens = data.getJSONArray("tokens")

                var ltokenV2: String? = null
                var cookieTokenV2: String? = null
                var expiresAt = 0L

                for (i in 0 until tokens.length()) {
                    val tokenObj = tokens.getJSONObject(i)
                    val tokenType = tokenObj.getInt("token_type")
                    val token = tokenObj.getString("token")

                    when (tokenType) {
                        2 -> ltokenV2 = token
                        4 -> cookieTokenV2 = token
                    }
                }

                response.headers("Set-Cookie").let { setCookieHeaders ->
                    val parsed = LtokenExpiry.expiresAt(setCookieHeaders)
                    if (parsed > 0) expiresAt = parsed
                }

                return TokenRefreshResult(
                    ltokenV2 = ltokenV2,
                    cookieTokenV2 = cookieTokenV2,
                    expiresAt = expiresAt
                )
            }
        } catch (e: IOException) {
            throw AuthException("Network error: ${e.message}", e)
        } catch (e: AuthException) {
            throw e
        } catch (e: Exception) {
            throw AuthException("Unexpected error: ${e.message}", e)
        }
    }

    // Exception classes
    open class AuthException(message: String, cause: Throwable? = null) : Exception(message, cause)

    class RefreshCredentialsRejectedException(message: String) : AuthException(message)

    class CaptchaRequiredException(
        message: String,
        val aigisHeader: String?,
        val session: CaptchaSession
    ) : AuthException(message)

    private fun parseCaptchaSession(header: String?): CaptchaSession {
        if (header.isNullOrBlank()) throw AuthException("Captcha response did not include x-rpc-aigis")
        try {
            val root = JSONObject(header)
            val sessionId = root.optString("session_id")
            val data = when (val rawData = root.opt("data")) {
                is JSONObject -> rawData
                is String -> JSONObject(rawData)
                null -> JSONObject()
                else -> JSONObject()
            }
            if (sessionId.isBlank()) throw AuthException("Captcha response did not include session_id")
            return if (data.optBoolean("use_v4", false)) {
                val captchaId = data.optString("captcha_id", data.optString("gt"))
                val riskType = data.optString("risk_type")
                if (captchaId.isBlank() || riskType.isBlank()) {
                    throw AuthException("Captcha v4 payload is missing captcha_id or risk_type")
                }
                CaptchaSession.V4(
                    SessionMMTv4(
                        sessionId = sessionId,
                        // genshin.py models v4's `gt` field as captcha_id.
                        captchaId = captchaId,
                        riskType = riskType,
                        newCaptcha = data.optInt("new_captcha", 0),
                        success = data.optInt("success", 0),
                        useV4 = true
                    )
                )
            } else {
                val challenge = data.optString("challenge")
                val gt = data.optString("gt")
                if (challenge.isBlank() || gt.isBlank()) {
                    throw AuthException("Captcha v3 payload is missing challenge or gt")
                }
                CaptchaSession.V3(
                    SessionMMT(
                        sessionId = sessionId,
                        challenge = challenge,
                        gt = gt,
                        newCaptcha = data.optInt("new_captcha", 0),
                        success = data.optInt("success", 0)
                    )
                )
            }
        } catch (e: AuthException) {
            throw e
        } catch (e: Exception) {
            throw AuthException("Invalid x-rpc-aigis captcha payload: ${e.message}", e)
        }
    }

    class EmailVerificationRequiredException(
        message: String,
        val actionTicket: ActionTicket
    ) : AuthException(message)

    // Email verification helper functions

    /**
     * Request that an OTP email be sent for the given action ticket.
     * Called explicitly by the user (not automatically by [authenticate]).
     * A Geetest challenge may be requested via [onCaptchaVerificationNeeded].
     * Duplicate sends (-3206) are treated as success because the email may
     * already be in the user's inbox.
     */
    suspend fun sendVerificationEmail(
        ticket: ActionTicket,
        onCaptchaVerificationNeeded: suspend (CaptchaSession) -> String
    ) {
        val jsonBody = JSONObject()
            .put("action_type", "verify_for_component")
            .put("action_ticket", ticket.ticket)
            .toString()
        var aigisHeader: String? = null
        repeat(MAX_CHALLENGE_ATTEMPTS) {
            val request = Request.Builder()
                .url(HoyoLabAuthConstants.EMAIL_CAPTCHA_REQUEST_URL)
                .post(jsonBody.toRequestBody(jsonMediaType))
                .header("Content-Type", "application/json")
                .header("x-rpc-app_id", "c9oqaq3s3gu8")
                .header("x-rpc-client_type", "2")
                .apply { aigisHeader?.let { header("x-rpc-aigis", it) } }
                .build()

            try {
                httpClient.newCall(request).execute().use { response ->
                    val responseBody = response.body.string()
                    if (responseBody.isBlank()) {
                        throw AuthException("Verification-email request returned an empty response")
                    }
                    if (!response.isSuccessful) {
                        throw AuthException("Failed to send verification email with HTTP ${response.code}")
                    }
                    val jsonObject = JSONObject(responseBody)
                    when (val retcode = jsonObject.getInt("retcode")) {
                        0 -> return
                        -3101 -> {
                            val session = parseCaptchaSession(response.header("x-rpc-aigis"))
                            aigisHeader = onCaptchaVerificationNeeded(session)
                            if (aigisHeader.isBlank()) throw AuthException("Captcha verification returned an empty result")
                        }

                        -3206 -> {
                            // HoYoLAB can deliver the email and still return the
                            // duplicate-send rate-limit response. Continue to the
                            // code prompt instead of asking for another email.
                            return
                        }

                        else -> {
                            val message = jsonObject.optString("message", "Unknown error")
                            throw AuthException("Failed to send verification email: $message (retcode: $retcode)")
                        }
                    }
                }
            } catch (e: IOException) {
                throw AuthException("Network error sending verification email: ${e.message}", e)
            }
        }
        throw AuthException("Email verification challenge did not complete")
    }

    private fun verifyEmailCode(code: String, ticket: ActionTicket) {
        val jsonBody = JSONObject()
            .put("action_type", "verify_for_component")
            .put("action_ticket", ticket.ticket)
            .put("email_captcha", code)
            .put("verify_method", 2)
            .toString()
        val requestBody = jsonBody.toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url(HoyoLabAuthConstants.EMAIL_CAPTCHA_VERIFY_URL)
            .post(requestBody)
            .header("Content-Type", "application/json")
            .header("x-rpc-app_id", "c9oqaq3s3gu8")
            .header("x-rpc-client_type", "2")
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body.string()
                if (responseBody.isBlank()) {
                    throw AuthException("Email verification returned an empty response")
                }
                if (!response.isSuccessful) {
                    throw AuthException("Email verification failed with HTTP ${response.code}")
                }
                val jsonObject = JSONObject(responseBody)
                val retcode = jsonObject.getInt("retcode")

                if (retcode != 0) {
                    val message = jsonObject.optString("message", "Unknown error")
                    throw AuthException("Email verification failed: $message (retcode: $retcode)")
                }
            }
        } catch (e: IOException) {
            throw AuthException("Network error verifying email: ${e.message}", e)
        }
    }

    companion object {
        private const val MAX_CHALLENGE_ATTEMPTS = 6
    }
}
