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

    suspend fun getTaskById(id: Int): VoiceTask? {
        return voiceTaskDao.getTaskById(id)
    }

    suspend fun insertTask(task: VoiceTask): Long {
        return voiceTaskDao.insertTask(task)
    }

    suspend fun updateTask(task: VoiceTask) {
        voiceTaskDao.updateTask(task)
    }

    suspend fun deleteTaskById(id: Int) {
        voiceTaskDao.deleteTaskById(id)
    }

    suspend fun getAllTasks(): List<VoiceTask> {
        return voiceTaskDao.getAllTasks()
    }

    // Export database content to a robust JSON string for personal backups
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
            }
            rootArray.put(obj)
        }
        return rootArray.toString(4) // Beautifully formatted
    }

    // Import and restore from backup JSON string
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
                    iconName = obj.optString("iconName", "Mic")
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
