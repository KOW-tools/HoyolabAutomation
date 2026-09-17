package cc.kowx712.autohoyolab.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_profile_preferences")
data class GameProfilePreference(
    @PrimaryKey
    val gameId: String, // game_biz like "hk4e_global"
    val selectedRegion: String, // region code like "os_asia"
    val selectedGameUid: String, // game UID
    val updatedAt: Long = System.currentTimeMillis()
)
