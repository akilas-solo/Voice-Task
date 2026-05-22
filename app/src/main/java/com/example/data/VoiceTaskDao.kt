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
}
