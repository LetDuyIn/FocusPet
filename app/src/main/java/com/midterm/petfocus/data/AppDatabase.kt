package com.midterm.petfocus.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PetState::class, Task::class, FocusSession::class, OwnedItem::class, BadgeClaim::class, DailyClaim::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun petDao(): PetDao
    abstract fun taskDao(): TaskDao
    abstract fun focusDao(): FocusDao
    abstract fun ownedItemDao(): OwnedItemDao
    abstract fun badgeDao(): BadgeDao
    abstract fun dailyDao(): DailyDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** v1 → v2: thêm bảng vật phẩm đã mua. Giữ nguyên dữ liệu cũ của người dùng. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `owned_items` (`itemId` TEXT NOT NULL, " +
                        "`equipped` INTEGER NOT NULL, PRIMARY KEY(`itemId`))"
                )
            }
        }

        /** v2 → v3: việc lặp lại (repeat, parentId) và bảng thưởng huy hiệu. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `repeat` TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `parentId` INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `badge_claims` (`badgeId` TEXT NOT NULL, " +
                        "`claimedAt` INTEGER NOT NULL, PRIMARY KEY(`badgeId`))"
                )
            }
        }

        /** v3 → v4: bảng thưởng hằng ngày (nhiệm vụ, điểm danh). */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `daily_claims` (`dayStart` INTEGER NOT NULL, `questId` TEXT NOT NULL, " +
                        "`coins` INTEGER NOT NULL, `claimedAt` INTEGER NOT NULL, PRIMARY KEY(`dayStart`, `questId`))"
                )
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "petfocus.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
                .also { instance = it }
        }
    }
}
