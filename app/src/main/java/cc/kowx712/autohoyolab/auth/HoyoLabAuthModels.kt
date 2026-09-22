package cc.kowx712.autohoyolab.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.json.JSONObject

/**
 * Result from app login (Step 1)
 */
data class AppLoginResult(
    val stoken: String,
    val ltuidV2: String,
    val ltmidV2: String,
    val accountIdV2: String,
    val accountMidV2: String
)

/**
 * Result from token refresh (Step 2)
 */
data class TokenRefreshResult(
    val ltokenV2: String?,
    val cookieTokenV2: String?,
    val expiresAt: Long = 0
)

/**
 * Complete cookie set with all required tokens
 */
data class CompleteCookie(
    val stoken: String,
    val ltokenV2: String,
    val ltuidV2: String,
    val ltmidV2: String,
    val cookieTokenV2: String,
    val accountMidV2: String,
    val accountIdV2: String,
    val expiresAt: Long = 0
) {
    /**
     * Convert to cookie string for API requests
     */
    fun toCookieString(): String {
        return "stoken=$stoken; ltoken_v2=$ltokenV2; ltuid_v2=$ltuidV2; " +
                "ltmid_v2=$ltmidV2; cookie_token_v2=$cookieTokenV2; " +
                "account_mid_v2=$accountMidV2; account_id_v2=$accountIdV2"
    }
}

/**
 * Geetest v3 captcha session
 * Used when retcode -3101 is returned with x-rpc-aigis header indicating v3 captcha
 */
data class SessionMMT(
    val sessionId: String,
    val challenge: String,
    val gt: String,
    val newCaptcha: Int,
    val success: Int
)

/**
 * Geetest v4 captcha session
 * Used when retcode -3101 is returned with x-rpc-aigis header indicating v4 captcha (use_v4: true)
 */
data class SessionMMTv4(
    val sessionId: String,
    val captchaId: String,
    val riskType: String,
    val newCaptcha: Int = 0,
    val success: Int = 0,
    val useV4: Boolean = true
)

sealed class CaptchaSession {
    data class V3(val session: SessionMMT) : CaptchaSession()
    data class V4(val session: SessionMMTv4) : CaptchaSession()
}

/**
 * Action ticket for email verification
 */
data class ActionTicket(
    val riskTicket: String,
    val ticket: String,
    val verifyType: String
) {
    fun toRpcVerifyHeader(): String {
        val verifyStrJson = JSONObject()
            .put("ticket", ticket)
            .put("verify_type", verifyType)
            .toString()
        return JSONObject()
            .put("risk_ticket", riskTicket)
            .put("verify_str", verifyStrJson)
            .toString()
    }
}

// Internal serialization models for kotlinx.serialization
// These are used when parsing JSON responses with the kotlinx serialization library
// Currently using org.json.JSONObject for parsing, but these are ready for migration
@Serializable
internal data class ApiResponse<T>(
    val retcode: Int,
    val message: String,
    val data: T? = null
)

@Serializable
internal data class AppLoginData(
    val token: TokenData,
    @SerialName("user_info") val userInfo: UserInfoData
)

@Serializable
internal data class TokenData(
    val token: String,
    @SerialName("token_type") val tokenType: Int
)

@Serializable
internal data class UserInfoData(
    val aid: String,
    val mid: String
)

@Serializable
internal data class TokenRefreshData(
    val tokens: List<TokenItem>
)

@Serializable
internal data class TokenItem(
    val token: String,
    @SerialName("token_type") val tokenType: Int
)
