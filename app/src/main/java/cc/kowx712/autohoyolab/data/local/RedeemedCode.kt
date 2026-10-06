package cc.kowx712.autohoyolab.data.local

import androidx.room.Entity

@Entity(tableName = "redeemed_codes", primaryKeys = ["gameId", "code"])
data class RedeemedCode(
    val gameId: String,
    val code: String,
    val gameUid: String?,
    val redeemedAt: Long,
)
