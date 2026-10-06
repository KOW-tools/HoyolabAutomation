package cc.kowx712.autohoyolab.data.model

import androidx.annotation.DrawableRes
import cc.kowx712.autohoyolab.R

sealed class HoyoGame(
    val id: String,
    val displayName: String,
    val filePath: String,
    val actId: String,
    val signGame: String,
    @DrawableRes val imageResId: Int,
    val redeemSlug: String,
    val supportRedeem: Boolean,
) {
    val infoUrl
        get() = "https://sg-act-public-api.hoyolab.com$filePath/info?act_id=$actId"

    val signUrl
        get() = "https://sg-act-public-api.hoyolab.com$filePath/sign"

    // Resign
    val resignInfoUrl
        get() = "https://sg-act-public-api.hoyolab.com$filePath/resign_info?act_id=$actId"

    val resignUrl
        get() = "https://sg-act-public-api.hoyolab.com$filePath/resign"

    // Resign task
    val taskListUrl
        get() = "https://sg-act-public-api.hoyolab.com$filePath/task/list?act_id=$actId"

    val taskCompleteUrl
        get() = "https://sg-act-public-api.hoyolab.com$filePath/task/complete"

    val taskAwardUrl
        get() = "https://sg-act-public-api.hoyolab.com$filePath/task/award"

    // Promo code redemption
    val codesUrl
        get() = "https://autohoyolab.kowx712.cc/mihoyo/$redeemSlug/codes"

    val redeemUrl: String?
        get() = REDEEM_ENDPOINTS[redeemSlug]

    data object GenshinImpact :
        HoyoGame("hk4e_global", "Genshin Impact", "/event/sol", "e202102251931481", "hk4e", R.drawable.img_hk4e_global, "genshin", true)

    data object HonkaiStarRail :
        HoyoGame(
            "hkrpg_global",
            "Honkai: Star Rail",
            "/event/luna/hkrpg/os",
            "e202303301540311",
            "hkrpg",
            R.drawable.img_hkrpg_global,
            "starrail",
            true
        )

    data object ZenlessZoneZero :
        HoyoGame("nap_global", "Zenless Zone Zero", "/event/luna/zzz/os", "e202406031448091", "zzz", R.drawable.img_nap_global, "zenless", true)

    data object HonkaiImpact3rd :
        HoyoGame("bh3_global", "Honkai Impact 3rd", "/event/mani", "e202110291205111", "bh3", R.drawable.img_bh3_global, "honkai", false)

    data object TearsOfThemis :
        HoyoGame("nxx_global", "Tears of Themis", "/event/luna/nxx/os", "e202202281857121", "nxx", R.drawable.img_nxx_global, "themis", true)

    companion object {
        private val REDEEM_ENDPOINTS = mapOf(
            "genshin" to "https://sg-hk4e-api.hoyoverse.com/common/apicdkey/api/webExchangeCdkey",
            "starrail" to "https://sg-hkrpg-api.hoyoverse.com/common/apicdkey/api/webExchangeCdkey",
            "zenless" to "https://public-operation-nap.hoyoverse.com/common/apicdkey/api/webExchangeCdkey",
            "themis" to "https://public-operation-common.hoyoverse.com/common/apicdkey/api/webExchangeCdkey",
        )

        val ALL = listOf(
            GenshinImpact,
            HonkaiStarRail,
            ZenlessZoneZero,
            HonkaiImpact3rd,
            TearsOfThemis,
        )

        fun fromGameBiz(gameBiz: String): HoyoGame? =
            ALL.firstOrNull { it.id == gameBiz }
    }
}
