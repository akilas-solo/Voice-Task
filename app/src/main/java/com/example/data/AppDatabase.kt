package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        VoiceTask::class,
        NotepadNote::class,
        TelegramActivityLog::class,
        ConnectorDispatchLog::class,
        FinanceTransaction::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun voiceTaskDao(): VoiceTaskDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE voice_tasks ADD COLUMN priority INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS finance_transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        smsFingerprint TEXT NOT NULL,
                        bankName TEXT NOT NULL,
                        bankCode TEXT NOT NULL,
                        type TEXT NOT NULL,
                        amount REAL NOT NULL,
                        currency TEXT NOT NULL,
                        balanceAfter REAL,
                        serviceCharge REAL,
                        counterparty TEXT NOT NULL,
                        referenceId TEXT NOT NULL,
                        category TEXT NOT NULL,
                        aiSummary TEXT NOT NULL,
                        rawSender TEXT NOT NULL,
                        rawSmsBody TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        source TEXT NOT NULL,
                        aiAnalyzed INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_finance_transactions_smsFingerprint ON finance_transactions(smsFingerprint)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_finance_transactions_timestamp ON finance_transactions(timestamp)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                var dbName = try {
                    val name = com.example.BuildConfig.DATABASE_NAME
                    if (name.isNullOrBlank()) "voistask_database" else name
                } catch (e: Exception) {
                    "voistask_database"
                }
                dbName = dbName.replace(" ", "_").filter { it.isLetterOrDigit() || it == '_' || it == '.' }
                if (dbName.isBlank()) {
                    dbName = "voistask_database"
                }
                val instance = try {
                    Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        dbName
                    )
                        .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
                        .fallbackToDestructiveMigration()
                        .build()
                } catch (e: Exception) {
                    try {
                        context.deleteDatabase(dbName)
                        Room.databaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java,
                            dbName
                        )
                            .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
                            .fallbackToDestructiveMigration()
                            .build()
                    } catch (e2: Exception) {
                        Room.inMemoryDatabaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java
                        )
                            .fallbackToDestructiveMigration()
                            .build()
                    }
                }
                INSTANCE = instance
                instance
            }
        }
    }
}
