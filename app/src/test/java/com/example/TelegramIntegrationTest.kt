package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.TelegramActivityLog
import com.example.data.TelegramSyncManager
import com.example.data.VoiceTask
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TelegramIntegrationTest {

    @Test
    fun `username normalization and validation work accurately`() {
        assertEquals("voice_user_1", TelegramSyncManager.normalizeUsername("  @voice_user_1 "))
        assertTrue(TelegramSyncManager.isValidUsername("@voice_user_1"))
        assertFalse(TelegramSyncManager.isValidUsername("@abc"))
        assertFalse(TelegramSyncManager.isValidUsername("bad user!"))
    }

    @Test
    fun `room database persists telegram task fields and activity logs`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getDatabase(context)
        val dao = db.voiceTaskDao()

        val id = dao.insertTask(
            VoiceTask(
                title = "Send Studio Memo",
                category = "Work",
                notifyTelegram = true,
                lastTelegramSentAt = 123456789L
            )
        ).toInt()

        val loaded = dao.getTaskById(id)
        assertEquals(true, loaded?.notifyTelegram)
        assertEquals(123456789L, loaded?.lastTelegramSentAt)

        dao.insertTelegramLog(
            TelegramActivityLog(
                taskId = id,
                direction = "OUTBOUND",
                eventType = "MANUAL_SEND",
                title = "Send Studio Memo",
                detail = "Sent to @voice_user_1",
                status = "SENT"
            )
        )
        val logs = dao.getAllTelegramLogsFlow().first()
        assertTrue(logs.any { it.taskId == id && it.status == "SENT" })
    }
}
