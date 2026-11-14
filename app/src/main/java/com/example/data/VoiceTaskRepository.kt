package com.example.data

import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

class VoiceTaskRepository(private val voiceTaskDao: VoiceTaskDao) {

    val allTasksFlow: Flow<List<VoiceTask>> = voiceTaskDao.getAllTasksFlow()
    val categoriesFlow: Flow<List<String>> = voiceTaskDao.getCategoriesFlow()

    fun getTasksByCategoryFlow(category: String): Flow<List<VoiceTask>> {
        return voiceTaskDao.getTasksByCategoryFlow(category)
    }

    suspend fun getTaskById(id: Int): VoiceTask? = voiceTaskDao.getTaskById(id)

    suspend fun insertTask(task: VoiceTask): Long = voiceTaskDao.insertTask(task)

    suspend fun updateTask(task: VoiceTask) = voiceTaskDao.updateTask(task)

    suspend fun deleteTaskById(id: Int) = voiceTaskDao.deleteTaskById(id)

    suspend fun getAllTasks(): List<VoiceTask> = voiceTaskDao.getAllTasks()

    // --- Dedicated Notepad Notes methods ---
    val allNotepadNotesFlow: Flow<List<NotepadNote>> = voiceTaskDao.getAllNotepadNotesFlow()

    suspend fun getAllNotepadNotes(): List<NotepadNote> = voiceTaskDao.getAllNotepadNotes()

    suspend fun getNotepadNoteById(id: Int): NotepadNote? = voiceTaskDao.getNotepadNoteById(id)

    suspend fun insertNotepadNote(note: NotepadNote): Long = voiceTaskDao.insertNotepadNote(note)

    suspend fun updateNotepadNote(note: NotepadNote) = voiceTaskDao.updateNotepadNote(note)

    suspend fun deleteNotepadNote(note: NotepadNote) = voiceTaskDao.deleteNotepadNote(note)

    suspend fun deleteNotepadNoteById(id: Int) = voiceTaskDao.deleteNotepadNoteById(id)

    // --- Telegram Activity Logs ---
    val allTelegramLogsFlow: Flow<List<TelegramActivityLog>> = voiceTaskDao.getAllTelegramLogsFlow()

    suspend fun insertTelegramLog(log: TelegramActivityLog): Long = voiceTaskDao.insertTelegramLog(log)

    suspend fun clearTelegramLogs() = voiceTaskDao.clearTelegramLogs()

    // --- Multi-Channel Connector Logs ---
    val allConnectorLogsFlow: Flow<List<ConnectorDispatchLog>> = voiceTaskDao.getAllConnectorLogsFlow()

    suspend fun insertConnectorLog(log: ConnectorDispatchLog): Long = voiceTaskDao.insertConnectorLog(log)

    suspend fun clearConnectorLogs() = voiceTaskDao.clearConnectorLogs()

    // --- Finance SMS Ledger ---
    val allFinanceTransactionsFlow: Flow<List<FinanceTransaction>> = voiceTaskDao.getAllFinanceTransactionsFlow()

    suspend fun getAllFinanceTransactions(): List<FinanceTransaction> = voiceTaskDao.getAllFinanceTransactions()

    suspend fun insertFinanceTransaction(tx: FinanceTransaction): Long = voiceTaskDao.insertFinanceTransaction(tx)

    suspend fun insertFinanceTransactionsDedup(list: List<FinanceTransaction>): Int {
        if (list.isEmpty()) return 0
        val results = voiceTaskDao.insertFinanceTransactions(list)
        return results.count { it > 0L }
    }

    suspend fun updateFinanceTransaction(tx: FinanceTransaction) = voiceTaskDao.updateFinanceTransaction(tx)

    suspend fun deleteFinanceTransactionById(id: Int) = voiceTaskDao.deleteFinanceTransactionById(id)

    suspend fun clearAllFinanceTransactions() = voiceTaskDao.clearAllFinanceTransactions()

    suspend fun exportBackupJson(): String {
        val tasks = voiceTaskDao.getAllTasks()
        val rootArray = JSONArray()
        for (task in tasks) {
            val obj = JSONObject().apply {
                put("id", task.id)
                put("title", task.title)
                put("notes", task.notes)
                put("audioPath", task.audioPath ?: "")
                put("audioDurationMs", task.audioDurationMs)
                put("category", task.category)
                put("tags", task.tags)
                put("isCompleted", task.isCompleted)
                put("createdAt", task.createdAt)
                put("isSynced", task.isSynced)
                if (task.reminderTime != null) {
                    put("reminderTime", task.reminderTime)
                }
                put("alertSound", task.alertSound)
                put("iconName", task.iconName)
                put("notifyTelegram", task.notifyTelegram)
                put("priority", task.priority)
            }
            rootArray.put(obj)
        }
        return rootArray.toString(4)
    }

    suspend fun importBackupJson(jsonString: String): Boolean {
        return try {
            val rootArray = JSONArray(jsonString)
            for (i in 0 until rootArray.length()) {
                val obj = rootArray.getJSONObject(i)
                val task = VoiceTask(
                    title = obj.optString("title", "Imported Memo"),
                    notes = obj.optString("notes", ""),
                    audioPath = obj.optString("audioPath", "").let { if (it.isEmpty()) null else it },
                    audioDurationMs = obj.optLong("audioDurationMs", 0L),
                    category = obj.optString("category", "Inbox"),
                    tags = obj.optString("tags", ""),
                    isCompleted = obj.optBoolean("isCompleted", false),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    isSynced = obj.optBoolean("isSynced", false),
                    reminderTime = if (obj.has("reminderTime")) obj.optLong("reminderTime") else null,
                    alertSound = obj.optString("alertSound", "App Default"),
                    iconName = obj.optString("iconName", "Mic"),
                    notifyTelegram = obj.optBoolean("notifyTelegram", true),
                    priority = obj.optInt("priority", TaskPriority.MEDIUM.level)
                        .coerceIn(TaskPriority.LOW.level, TaskPriority.URGENT.level)
                )
                voiceTaskDao.insertTask(task)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
