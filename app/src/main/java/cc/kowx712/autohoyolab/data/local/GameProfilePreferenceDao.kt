package cc.kowx712.autohoyolab.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface GameProfilePreferenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePreference(preference: GameProfilePreference)

    @Query("SELECT * FROM game_profile_preferences WHERE gameId = :gameId")
    suspend fun getPreference(gameId: String): GameProfilePreference?

    @Query("SELECT * FROM game_profile_preferences")
    suspend fun getAllPreferences(): List<GameProfilePreference>

    @Query("DELETE FROM game_profile_preferences WHERE gameId = :gameId")
    suspend fun deletePreference(gameId: String)

    @Query("DELETE FROM game_profile_preferences")
    suspend fun clearAll()
}
