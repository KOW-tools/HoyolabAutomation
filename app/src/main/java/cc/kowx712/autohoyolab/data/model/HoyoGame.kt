package cc.kowx712.autohoyolab.data.model

import androidx.annotation.DrawableRes
import cc.kowx712.autohoyolab.R

sealed class HoyoGame(
    val id: String,
    val displayName: String,
    val filePath: String,
    val actId: String,
    @DrawableRes val imageResId: Int,
) {
    val infoUrl
        get() = "https://sg-act-public-api.hoyolab.com$filePath/info?act_id=$actId"

    val signUrl
        get() = "https://sg-act-public-api.hoyolab.com$filePath/sign?act_id=$actId"

    data object GenshinImpact :
        HoyoGame("hk4e_global", "Genshin Impact", "/event/sol", "e202102251931481", R.drawable.img_hk4e_global)

    data object HonkaiStarRail :
        HoyoGame("hkrpg_global", "Honkai: Star Rail", "/event/luna/hkrpg/os", "e202303301540311", R.drawable.img_hkrpg_global)

    data object ZenlessZoneZero :
        HoyoGame("nap_global", "Zenless Zone Zero", "/event/luna/zzz/os", "e202406031448091", R.drawable.img_nap_global)

    data object HonkaiImpact3rd :
        HoyoGame("bh3_global", "Honkai Impact 3rd", "/event/mani", "e202110291205111", R.drawable.img_bh3_global)

    data object TearsOfThemis :
        HoyoGame("nxx_global", "Tears of Themis", "/event/luna/nxx/os", "e202202281857121", R.drawable.img_nxx_global)

    companion object {
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
