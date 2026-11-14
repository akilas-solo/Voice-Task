package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceTaskDao {
    @Query("SELECT * FROM voice_tasks ORDER BY createdAt DESC")
    fun getAllTasksFlow(): Flow<List<VoiceTask>>

    @Query("SELECT * FROM voice_tasks ORDER BY createdAt DESC")
    suspend fun getAllTasks(): List<VoiceTask>

    @Query("SELECT * FROM voice_tasks WHERE id = :id")
    suspend fun getTaskById(id: Int): VoiceTask?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: VoiceTask): Long

    @Update
    suspend fun updateTask(task: VoiceTask)

    @Delete
    suspend fun deleteTask(task: VoiceTask)

    @Query("DELETE FROM voice_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Int)

    @Query("SELECT DISTINCT category FROM voice_tasks")
    fun getCategoriesFlow(): Flow<List<String>>

    @Query("SELECT * FROM voice_tasks WHERE category = :category ORDER BY createdAt DESC")
    fun getTasksByCategoryFlow(category: String): Flow<List<VoiceTask>>

    // --- Dedicated Notepad Notes queries ---
    @Query("SELECT * FROM notepad_notes ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllNotepadNotesFlow(): Flow<List<NotepadNote>>

    @Query("SELECT * FROM notepad_notes ORDER BY isPinned DESC, updatedAt DESC")
    suspend fun getAllNotepadNotes(): List<NotepadNote>

    @Query("SELECT * FROM notepad_notes WHERE id = :id")
    suspend fun getNotepadNoteById(id: Int): NotepadNote?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotepadNote(note: NotepadNote): Long

    @Update
    suspend fun updateNotepadNote(note: NotepadNote)

    @Delete
    suspend fun deleteNotepadNote(note: NotepadNote)

    @Query("DELETE FROM notepad_notes WHERE id = :id")
    suspend fun deleteNotepadNoteById(id: Int)

    // --- Telegram Activity Ledger queries ---
    @Query("SELECT * FROM telegram_activity_logs ORDER BY timestamp DESC LIMIT 50")
    fun getAllTelegramLogsFlow(): Flow<List<TelegramActivityLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTelegramLog(log: TelegramActivityLog): Long

    @Query("DELETE FROM telegram_activity_logs")
    suspend fun clearTelegramLogs()

    // --- Multi-Channel Connector Dispatch Ledger queries ---
    @Query("SELECT * FROM connector_dispatch_logs ORDER BY timestamp DESC LIMIT 80")
    fun getAllConnectorLogsFlow(): Flow<List<ConnectorDispatchLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConnectorLog(log: ConnectorDispatchLog): Long

    @Query("DELETE FROM connector_dispatch_logs")
    suspend fun clearConnectorLogs()

    // --- Finance SMS Ledger queries ---
    @Query("SELECT * FROM finance_transactions ORDER BY timestamp DESC")
    fun getAllFinanceTransactionsFlow(): Flow<List<FinanceTransaction>>

    @Query("SELECT * FROM finance_transactions ORDER BY timestamp DESC")
    suspend fun getAllFinanceTransactions(): List<FinanceTransaction>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFinanceTransaction(tx: FinanceTransaction): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFinanceTransactions(list: List<FinanceTransaction>): List<Long>

    @Update
    suspend fun updateFinanceTransaction(tx: FinanceTransaction)

    @Query("DELETE FROM finance_transactions WHERE id = :id")
    suspend fun deleteFinanceTransactionById(id: Int)

    @Query("DELETE FROM finance_transactions")
    suspend fun clearAllFinanceTransactions()

    @Query("SELECT COUNT(*) FROM finance_transactions WHERE smsFingerprint = :fingerprint")
    suspend fun countBySmsFingerprint(fingerprint: String): Int
}
