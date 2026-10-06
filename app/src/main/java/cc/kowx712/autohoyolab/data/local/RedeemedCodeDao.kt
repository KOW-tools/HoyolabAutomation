package cc.kowx712.autohoyolab.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RedeemedCodeDao {
    @Query("SELECT code FROM redeemed_codes WHERE gameId = :gameId")
    suspend fun getRedeemedCodes(gameId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(code: RedeemedCode)
}
