package cc.kowx712.autohoyolab.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CheckInLog::class, GameProfilePreference::class, RedeemedCode::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun checkInLogDao(): CheckInLogDao
    abstract fun gameProfilePreferenceDao(): GameProfilePreferenceDao
    abstract fun redeemedCodeDao(): RedeemedCodeDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `redeemed_codes` (" +
                            "`gameId` TEXT NOT NULL, " +
                            "`code` TEXT NOT NULL, " +
                            "`gameUid` TEXT, " +
                            "`redeemedAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`gameId`, `code`))"
                )
                db.execSQL("ALTER TABLE `check_in_logs` ADD COLUMN `redeemCount` INTEGER")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hoyolab_checkin_database"
                )
                    .fallbackToDestructiveMigration(false) // Don't drop all tables
                    .addMigrations(MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
