package cc.kowx712.autohoyolab.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HoyoGameRole(
    @SerialName("game_biz") val gameBiz: String,
    @SerialName("region") val region: String,
    @SerialName("game_uid") val gameUid: String,
    @SerialName("nickname") val nickname: String,
    @SerialName("level") val level: Int,
    @SerialName("is_chosen") val isChosen: Boolean = false,
    @SerialName("region_name") val regionName: String,
    @SerialName("is_official") val isOfficial: Boolean = true,
    @SerialName("is_banned") val isBanned: Boolean = false,
)
