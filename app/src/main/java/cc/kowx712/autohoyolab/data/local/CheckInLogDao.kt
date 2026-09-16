package cc.kowx712.autohoyolab.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckInLogDao {
    @Insert
    suspend fun insert(log: CheckInLog)

    @Query("SELECT * FROM check_in_logs ORDER BY timestamp DESC LIMIT 100")
    fun getAllLogs(): Flow<List<CheckInLog>>

    @Query("SELECT * FROM check_in_logs WHERE gameId = :gameId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastLogForGame(gameId: String): CheckInLog?

    @Query("SELECT * FROM check_in_logs WHERE status = :status ORDER BY timestamp DESC")
    fun getLogsByStatus(status: String): Flow<List<CheckInLog>>

    @Query("DELETE FROM check_in_logs WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    @Query("SELECT * FROM check_in_logs WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getLogsInRange(startTime: Long, endTime: Long): Flow<List<CheckInLog>>
}
