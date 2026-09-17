package cc.kowx712.autohoyolab.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CheckInLog::class, GameProfilePreference::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun checkInLogDao(): CheckInLogDao
    abstract fun gameProfilePreferenceDao(): GameProfilePreferenceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hoyolab_checkin_database"
                )
                    .fallbackToDestructiveMigration(false) // Don't drop all tables
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
