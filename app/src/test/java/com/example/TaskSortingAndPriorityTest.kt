package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AiConnectorPrefs
import com.example.data.AppDatabase
import com.example.data.TaskPriority
import com.example.data.TaskSortOption
import com.example.data.VoiceTask
import com.example.data.VoiceTaskRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TaskSortingAndPriorityTest {

    private val sampleTasks = listOf(
        VoiceTask(
            id = 1,
            title = "Zebra Audit",
            category = "Work",
            createdAt = 1_000L,
            priority = TaskPriority.LOW.level
        ),
        VoiceTask(
            id = 2,
            title = "Alpha Groceries",
            category = "Personal",
            createdAt = 3_000L,
            priority = TaskPriority.URGENT.level
        ),
        VoiceTask(
            id = 3,
            title = "Beta Proposal",
            category = "Work",
            createdAt = 2_000L,
            priority = TaskPriority.HIGH.level
        ),
        VoiceTask(
            id = 4,
            title = "Gamma Notes",
            category = "Ideas",
            createdAt = 4_000L,
            priority = TaskPriority.MEDIUM.level
        )
    )

    @Test
    fun `chronological sorting flips cleanly between newest and oldest`() {
        val newestFirst = TaskSortOption.sortTasks(sampleTasks, TaskSortOption.CHRONOLOGICAL, descending = true)
        assertEquals(listOf(4, 2, 3, 1), newestFirst.map { it.id })

        val oldestFirst = TaskSortOption.sortTasks(sampleTasks, TaskSortOption.CHRONOLOGICAL, descending = false)
        assertEquals(listOf(1, 3, 2, 4), oldestFirst.map { it.id })
    }

    @Test
    fun `priority sorting orders Urgent High Medium Low and supports reversing`() {
        val urgentFirst = TaskSortOption.sortTasks(sampleTasks, TaskSortOption.PRIORITY, descending = true)
        assertEquals(listOf(2, 3, 4, 1), urgentFirst.map { it.id })

        val lowFirst = TaskSortOption.sortTasks(sampleTasks, TaskSortOption.PRIORITY, descending = false)
        assertEquals(listOf(1, 4, 3, 2), lowFirst.map { it.id })
    }

    @Test
    fun `alphabetical categorization sorts by category then title in both directions`() {
        val aToZ = TaskSortOption.sortTasks(sampleTasks, TaskSortOption.CATEGORY, descending = false)
        // Ideas (Gamma Notes), Personal (Alpha Groceries), Work (Beta Proposal), Work (Zebra Audit)
        assertEquals(listOf(4, 2, 3, 1), aToZ.map { it.id })

        val zToA = TaskSortOption.sortTasks(sampleTasks, TaskSortOption.CATEGORY, descending = true)
        assertEquals(listOf(1, 3, 2, 4), zToA.map { it.id })
    }

    @Test
    fun `sort preference persists in DataStore and priority survives JSON backup roundtrip`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = AiConnectorPrefs(context)

        prefs.saveTaskSortPreference(TaskSortOption.PRIORITY, false)
        val savedPref = prefs.taskSortPreferenceFlow.first()
        assertEquals(TaskSortOption.PRIORITY, savedPref.first)
        assertEquals(false, savedPref.second)

        val dao = AppDatabase.getDatabase(context).voiceTaskDao()
        val repo = VoiceTaskRepository(dao)
        dao.insertTask(
            VoiceTask(
                title = "Urgent Pitch",
                category = "Work",
                priority = TaskPriority.URGENT.level
            )
        )
        val exportedJson = repo.exportBackupJson()
        assertTrue(exportedJson.contains("\"priority\": 3"))
        assertTrue(repo.importBackupJson(exportedJson))

        val restored = repo.getAllTasks()
        assertTrue(restored.any { it.title == "Urgent Pitch" && it.priority == TaskPriority.URGENT.level })
    }
}
