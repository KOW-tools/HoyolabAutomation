package cc.kowx712.autohoyolab.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "check_in_logs")
data class CheckInLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val gameId: String,
    val gameUid: String?,
    val region: String?,
    val timestamp: Long,
    val status: String, // SUCCESS, ALREADY_SIGNED, FAILED, COOKIE_EXPIRED, NETWORK_ERROR
    val message: String?,
    val retcode: Int?,
)
