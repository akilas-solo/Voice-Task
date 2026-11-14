package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AiConnectorPrefs
import com.example.data.AiProvider
import com.example.data.AiProviderClient
import com.example.data.AiResult
import com.example.data.AiStudioConfig
import com.example.data.AppDatabase
import com.example.data.ConnectorDispatchLog
import com.example.data.ConnectorResult
import com.example.data.CustomWebhookConnector
import com.example.data.EmailDeliveryMode
import com.example.data.ExternalConnectorManager
import com.example.data.WebhookPresetType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiConnectorTest {

    @Test
    fun `ai connector prefs persist provider keys email notion and webhooks`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = AiConnectorPrefs(context)

        prefs.setActiveProvider(AiProvider.OPENAI)
        prefs.saveProviderCredentials(AiProvider.OPENAI, "sk-test-1234", "gpt-4o-mini")
        prefs.saveEmailConfig(EmailDeliveryMode.RESEND_API, "re_abc", "bot@example.com", "user@example.com")
        prefs.saveNotionConfig("ntn_secret", "db-123", "Voice Inbox", "Title")
        prefs.saveWebhooks(
            listOf(
                CustomWebhookConnector(
                    id = "wh1",
                    name = "Team Slack",
                    presetType = WebhookPresetType.SLACK,
                    url = "https://hooks.slack.com/services/T0/B0/X0",
                    isEnabled = true
                )
            )
        )

        val loaded = prefs.getConfig()
        assertEquals(AiProvider.OPENAI, loaded.activeProvider)
        assertEquals("sk-test-1234", loaded.activeApiKey)
        assertEquals("gpt-4o-mini", loaded.activeModel)
        assertEquals(EmailDeliveryMode.RESEND_API, loaded.emailDeliveryMode)
        assertEquals("re_abc", loaded.resendApiKey)
        assertEquals("db-123", loaded.notionDatabaseId)
        assertEquals("Title", loaded.notionTitleProperty)
        assertEquals(1, loaded.customWebhooks.size)
        assertEquals(WebhookPresetType.SLACK, loaded.customWebhooks.first().presetType)
    }

    @Test
    fun `ai provider client validates empty keys and claude audio limitation`() = runTest {
        val emptyKeyRes = AiProviderClient.testConnection(AiStudioConfig(activeProvider = AiProvider.GEMINI))
        assertTrue(emptyKeyRes is AiResult.Error)

        val tempAudio = File.createTempFile("sample", ".mp4").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
            deleteOnExit()
        }
        val claudeAudioRes = AiProviderClient.transcribeAndStructureAudio(
            config = AiStudioConfig(activeProvider = AiProvider.CLAUDE, claudeApiKey = "sk-ant-test"),
            audioFile = tempAudio
        )
        assertTrue(claudeAudioRes is AiResult.Error)
        assertTrue((claudeAudioRes as AiResult.Error).reason.contains("Claude", ignoreCase = true))
    }

    @Test
    fun `external connector manager validates webhook urls and logs room entries`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val badWebhook = CustomWebhookConnector(
            name = "Invalid",
            presetType = WebhookPresetType.WEBHOOK,
            url = "ftp://not-http"
        )
        val res = ExternalConnectorManager.dispatchWebhook(context, badWebhook, "Hello", "Body")
        assertTrue(res is ConnectorResult.Error)

        val dao = AppDatabase.getDatabase(context).voiceTaskDao()
        dao.insertConnectorLog(
            ConnectorDispatchLog(
                channel = "NOTION",
                title = "Meeting Summary",
                detail = "Saved to Notion DB",
                status = "SENT"
            )
        )
        val logs = dao.getAllConnectorLogsFlow().first()
        assertTrue(logs.any { it.channel == "NOTION" && it.title == "Meeting Summary" })
    }
}
