package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [VoiceTask::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun voiceTaskDao(): VoiceTaskDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                var dbName = try {
                    val name = com.example.BuildConfig.DATABASE_NAME
                    if (name.isNullOrBlank()) "voistask_database" else name
                } catch (e: Exception) {
                    "voistask_database"
                }
                // Sanitize: replace spaces with underscores, and strip out non-alphanumeric chars (keep underscores, periods)
                dbName = dbName.replace(" ", "_").filter { it.isLetterOrDigit() || it == '_' || it == '.' }
                if (dbName.isBlank()) {
                    dbName = "voistask_database"
                }
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    dbName
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
