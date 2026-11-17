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
        return insertFinanceTransactionsDedupReturningInserted(list).size
    }

    suspend fun insertFinanceTransactionsDedupReturningInserted(list: List<FinanceTransaction>): List<FinanceTransaction> {
        if (list.isEmpty()) return emptyList()
        val distinct = list.distinctBy { it.smsFingerprint }
        val insertedItems = mutableListOf<FinanceTransaction>()
        distinct.chunked(200).forEach { chunk ->
            val results = voiceTaskDao.insertFinanceTransactions(chunk)
            results.forEachIndexed { idx, rowId ->
                if (rowId > 0L) {
                    insertedItems.add(chunk[idx])
                }
            }
        }
        return insertedItems
    }

    suspend fun updateFinanceTransaction(tx: FinanceTransaction) = voiceTaskDao.updateFinanceTransaction(tx)

    suspend fun deleteFinanceTransactionById(id: Int) = voiceTaskDao.deleteFinanceTransactionById(id)

    suspend fun clearAllFinanceTransactions() = voiceTaskDao.clearAllFinanceTransactions()

    suspend fun reparseStoredSmsTransactions(
        learnedCategoryRules: Map<String, String> = emptyMap()
    ): Pair<Int, Int> {
        val all = voiceTaskDao.getAllFinanceTransactions()
        var updatedCount = 0
        var removedCount = 0
        for (tx in all) {
            if (tx.source == "MANUAL_ENTRY" || tx.rawSmsBody.isBlank()) continue
            val reparsed = BankSmsParser.parseSms(
                sender = tx.rawSender,
                body = tx.rawSmsBody,
                receivedTimestampMs = tx.timestamp,
                source = tx.source,
                learnedCategoryRules = learnedCategoryRules
            )
            if (reparsed == null) {
                voiceTaskDao.deleteFinanceTransactionById(tx.id)
                removedCount++
            } else {
                val changed = reparsed.bankName != tx.bankName ||
                    reparsed.bankCode != tx.bankCode ||
                    reparsed.type != tx.type ||
                    reparsed.amount != tx.amount ||
                    reparsed.balanceAfter != tx.balanceAfter ||
                    reparsed.serviceCharge != tx.serviceCharge ||
                    reparsed.counterparty != tx.counterparty ||
                    reparsed.referenceId != tx.referenceId ||
                    reparsed.category != tx.category
                if (changed) {
                    voiceTaskDao.updateFinanceTransaction(
                        tx.copy(
                            bankName = reparsed.bankName,
                            bankCode = reparsed.bankCode,
                            type = reparsed.type,
                            amount = reparsed.amount,
                            currency = reparsed.currency,
                            balanceAfter = reparsed.balanceAfter,
                            serviceCharge = reparsed.serviceCharge,
                            counterparty = reparsed.counterparty,
                            referenceId = reparsed.referenceId,
                            category = reparsed.category
                        )
                    )
                    updatedCount++
                }
            }
        }
        return updatedCount to removedCount
    }

    data class BackupImportResult(
        val success: Boolean,
        val tasksImported: Int = 0,
        val notesImported: Int = 0,
        val financeImported: Int = 0,
        val customFolders: List<String> = emptyList()
    )

    suspend fun exportBackupJson(customFolders: List<String> = emptyList()): String {
        val tasks = voiceTaskDao.getAllTasks()
        val notes = voiceTaskDao.getAllNotepadNotes()
        val finance = voiceTaskDao.getAllFinanceTransactions()

        val tasksArray = JSONArray()
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
            tasksArray.put(obj)
        }

        val notesArray = JSONArray()
        for (note in notes) {
            val obj = JSONObject().apply {
                put("id", note.id)
                put("title", note.title)
                put("content", note.content)
                put("category", note.category)
                put("isPinned", note.isPinned)
                put("createdAt", note.createdAt)
                put("updatedAt", note.updatedAt)
                put("wordCount", note.wordCount)
                put("charCount", note.charCount)
                if (!note.colorHex.isNullOrBlank()) {
                    put("colorHex", note.colorHex)
                }
                put("audioPath", note.audioPath ?: "")
                put("audioDurationMs", note.audioDurationMs)
            }
            notesArray.put(obj)
        }

        val financeArray = JSONArray()
        for (tx in finance) {
            val obj = JSONObject().apply {
                put("smsFingerprint", tx.smsFingerprint)
                put("bankName", tx.bankName)
                put("bankCode", tx.bankCode)
                put("type", tx.type)
                put("amount", tx.amount)
                put("currency", tx.currency)
                if (tx.balanceAfter != null) put("balanceAfter", tx.balanceAfter)
                if (tx.serviceCharge != null) put("serviceCharge", tx.serviceCharge)
                put("counterparty", tx.counterparty)
                put("referenceId", tx.referenceId)
                put("category", tx.category)
                put("aiSummary", tx.aiSummary)
                put("rawSender", tx.rawSender)
                put("rawSmsBody", tx.rawSmsBody)
                put("timestamp", tx.timestamp)
                put("source", tx.source)
                put("aiAnalyzed", tx.aiAnalyzed)
            }
            financeArray.put(obj)
        }

        val foldersArray = JSONArray()
        customFolders.map { it.trim() }.filter { it.isNotBlank() }.distinct().forEach {
            foldersArray.put(it)
        }

        val root = JSONObject().apply {
            put("schemaVersion", 2)
            put("exportedAt", System.currentTimeMillis())
            put("customFolders", foldersArray)
            put("voiceTasks", tasksArray)
            put("notepadNotes", notesArray)
            put("financeTransactions", financeArray)
        }
        return root.toString(4)
    }

    suspend fun importBackupDetailed(jsonString: String): BackupImportResult {
        return try {
            val trimmed = jsonString.trim()
            if (trimmed.isEmpty()) return BackupImportResult(success = false)

            val tasksArray: JSONArray
            val notesArray: JSONArray
            val financeArray: JSONArray
            val customFolders = mutableListOf<String>()

            if (trimmed.startsWith("[")) {
                // v1 legacy array of VoiceTask
                tasksArray = JSONArray(trimmed)
                notesArray = JSONArray()
                financeArray = JSONArray()
            } else {
                val root = JSONObject(trimmed)
                tasksArray = root.optJSONArray("voiceTasks") ?: JSONArray()
                notesArray = root.optJSONArray("notepadNotes") ?: JSONArray()
                financeArray = root.optJSONArray("financeTransactions") ?: JSONArray()
                val foldersJson = root.optJSONArray("customFolders") ?: JSONArray()
                for (i in 0 until foldersJson.length()) {
                    val name = foldersJson.optString(i).trim()
                    if (name.isNotBlank()) customFolders.add(name)
                }
            }

            val existingTaskKeys = voiceTaskDao.getAllTasks()
                .mapTo(mutableSetOf()) { "${it.title.trim().lowercase()}|${it.createdAt}" }
            var tasksImported = 0
            for (i in 0 until tasksArray.length()) {
                val obj = tasksArray.getJSONObject(i)
                val title = obj.optString("title", "Imported Memo")
                val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                val dedupKey = "${title.trim().lowercase()}|$createdAt"
                if (obj.has("createdAt") && dedupKey in existingTaskKeys) continue
                existingTaskKeys.add(dedupKey)
                val rawAudioPath = obj.optString("audioPath", "").takeIf { it.isNotEmpty() && java.io.File(it).exists() }
                val reminderVal = if (obj.has("reminderTime") && !obj.isNull("reminderTime")) obj.optLong("reminderTime") else null
                val task = VoiceTask(
                    title = title,
                    notes = obj.optString("notes", ""),
                    audioPath = rawAudioPath,
                    audioDurationMs = if (rawAudioPath != null) obj.optLong("audioDurationMs", 0L) else 0L,
                    category = obj.optString("category", "Inbox"),
                    tags = obj.optString("tags", ""),
                    isCompleted = obj.optBoolean("isCompleted", false),
                    createdAt = createdAt,
                    isSynced = obj.optBoolean("isSynced", false),
                    reminderTime = reminderVal,
                    alertSound = obj.optString("alertSound", "App Default"),
                    iconName = obj.optString("iconName", "Mic"),
                    notifyTelegram = obj.optBoolean("notifyTelegram", true),
                    priority = obj.optInt("priority", TaskPriority.MEDIUM.level)
                        .coerceIn(TaskPriority.LOW.level, TaskPriority.URGENT.level)
                )
                voiceTaskDao.insertTask(task)
                tasksImported++
            }

            val existingNoteKeys = voiceTaskDao.getAllNotepadNotes()
                .mapTo(mutableSetOf()) { "${it.title.trim().lowercase()}|${it.createdAt}" }
            var notesImported = 0
            for (i in 0 until notesArray.length()) {
                val obj = notesArray.getJSONObject(i)
                val title = obj.optString("title", "Imported Note")
                val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                val dedupKey = "${title.trim().lowercase()}|$createdAt"
                if (obj.has("createdAt") && dedupKey in existingNoteKeys) continue
                existingNoteKeys.add(dedupKey)
                val content = obj.optString("content", "")
                val rawAudioPath = obj.optString("audioPath", "").takeIf { it.isNotEmpty() && java.io.File(it).exists() }
                val note = NotepadNote(
                    title = title,
                    content = content,
                    category = obj.optString("category", "General"),
                    isPinned = obj.optBoolean("isPinned", false),
                    createdAt = createdAt,
                    updatedAt = obj.optLong("updatedAt", createdAt),
                    wordCount = obj.optInt("wordCount", if (content.isBlank()) 0 else content.trim().split(Regex("\\s+")).size),
                    charCount = obj.optInt("charCount", content.length),
                    colorHex = obj.optString("colorHex", "").takeIf { it.isNotBlank() },
                    audioPath = rawAudioPath,
                    audioDurationMs = if (rawAudioPath != null) obj.optLong("audioDurationMs", 0L) else 0L
                )
                voiceTaskDao.insertNotepadNote(note)
                notesImported++
            }

            val finList = mutableListOf<FinanceTransaction>()
            for (i in 0 until financeArray.length()) {
                val obj = financeArray.getJSONObject(i)
                val fp = obj.optString("smsFingerprint", "")
                if (fp.isBlank()) continue
                val bal = if (obj.has("balanceAfter") && !obj.isNull("balanceAfter")) obj.optDouble("balanceAfter") else null
                val fee = if (obj.has("serviceCharge") && !obj.isNull("serviceCharge")) obj.optDouble("serviceCharge") else null
                finList.add(
                    FinanceTransaction(
                        smsFingerprint = fp,
                        bankName = obj.optString("bankName", "Other Bank"),
                        bankCode = obj.optString("bankCode", BankInstitution.OTHER.code),
                        type = obj.optString("type", TransactionType.EXPENSE.name),
                        amount = obj.optDouble("amount", 0.0),
                        currency = obj.optString("currency", "ETB"),
                        balanceAfter = bal?.takeIf { it.isFinite() },
                        serviceCharge = fee?.takeIf { it.isFinite() },
                        counterparty = obj.optString("counterparty", ""),
                        referenceId = obj.optString("referenceId", ""),
                        category = obj.optString("category", "Other"),
                        aiSummary = obj.optString("aiSummary", ""),
                        rawSender = obj.optString("rawSender", ""),
                        rawSmsBody = obj.optString("rawSmsBody", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        source = obj.optString("source", "BACKUP_RESTORE"),
                        aiAnalyzed = obj.optBoolean("aiAnalyzed", false)
                    )
                )
            }
            val financeImported = insertFinanceTransactionsDedup(finList)

            BackupImportResult(
                success = true,
                tasksImported = tasksImported,
                notesImported = notesImported,
                financeImported = financeImported,
                customFolders = customFolders.distinct()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            BackupImportResult(success = false)
        }
    }

    suspend fun importBackupJson(jsonString: String): Boolean {
        return importBackupDetailed(jsonString).success
    }
}
