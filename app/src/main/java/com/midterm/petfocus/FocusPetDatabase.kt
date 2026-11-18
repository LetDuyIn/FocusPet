package com.midterm.petfocus

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TaskEntity::class], version = 1, exportSchema = false)
abstract class FocusPetDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile
        private var INSTANCE: FocusPetDatabase? = null

        fun getDatabase(context: Context): FocusPetDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FocusPetDatabase::class.java,
                    "focus_pet_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}