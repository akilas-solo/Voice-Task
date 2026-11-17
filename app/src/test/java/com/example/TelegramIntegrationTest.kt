package com.example

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.FinanceTransaction
import com.example.data.TelegramActivityLog
import com.example.data.TelegramConfig
import com.example.data.TelegramPrefs
import com.example.data.TelegramSyncManager
import com.example.data.TelegramTokenSource
import com.example.data.VoiceTask
import com.example.data.VoiceTaskRepository
import com.example.ui.TelegramSettingsCardContent
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class TelegramIntegrationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `username normalization and validation work accurately`() {
        assertEquals("voice_user_1", TelegramSyncManager.normalizeUsername("  @voice_user_1 "))
        assertTrue(TelegramSyncManager.isValidUsername("@voice_user_1"))
        assertFalse(TelegramSyncManager.isValidUsername("@abc"))
        assertFalse(TelegramSyncManager.isValidUsername("bad user!"))
    }

    @Test
    fun `botfather token format validation and hybrid precedence work properly`() {
        val validSample = "123456789:AAHdqTcvCH1vGWJxfSeofSAs0K5PALDsaw"
        assertTrue(TelegramSyncManager.isValidBotTokenFormat("  $validSample  "))
        assertFalse(TelegramSyncManager.isValidBotTokenFormat("not-a-token"))
        assertFalse(TelegramSyncManager.isValidBotTokenFormat("123:short"))

        val (resolvedCustom, customSource) = TelegramSyncManager.resolveBotToken(validSample)
        assertEquals(validSample, resolvedCustom)
        assertEquals(TelegramTokenSource.IN_APP, customSource)

        val (resolvedEmpty, emptySource) = TelegramSyncManager.resolveBotToken("   ")
        val expectedSecret = TelegramSyncManager.getSecretBotToken()
        assertEquals(expectedSecret, resolvedEmpty)
        assertEquals(
            if (expectedSecret.isNotEmpty()) TelegramTokenSource.BUILD_SECRET else TelegramTokenSource.NONE,
            emptySource
        )
    }

    @Test
    fun `telegram prefs persist custom bot token, finance toggle, and reset verification on bot change`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = TelegramPrefs(context)

        prefs.markVerified(username = "akilas_user", chatId = 987654321L, botUsername = "OldBot", lastOffset = 42L)
        assertTrue(prefs.getConfig().isVerified)

        prefs.saveCustomBotToken(
            token = "111222333:AAHdqTcvCH1vGWJxfSeofSAs0K5PALDsaw",
            verifiedBotUsername = "NewFinanceBot",
            resetVerificationIfBotChanged = true
        )
        val afterNewBot = prefs.getConfig()
        assertEquals("NewFinanceBot", afterNewBot.botUsername)
        assertFalse(afterNewBot.isVerified)
        assertEquals(null, afterNewBot.chatId)

        prefs.setAutoSendFinanceAlerts(false)
        assertFalse(prefs.getConfig().autoSendFinanceAlerts)
        prefs.setAutoSendFinanceAlerts(true)
        assertTrue(prefs.getConfig().autoSendFinanceAlerts)

        prefs.clearCustomBotToken()
        assertEquals("", prefs.getConfig().customBotToken)
    }

    @Test
    fun `finance alert and summary html formatters include amount, category, and balances`() {
        val now = 1_763_300_000_000L
        val tx1 = FinanceTransaction(
            smsFingerprint = "fp_tg_1",
            bankName = "Telebirr",
            bankCode = "TELEBIRR",
            type = "EXPENSE",
            amount = 850.0,
            currency = "ETB",
            balanceAfter = 4150.0,
            serviceCharge = 5.0,
            counterparty = "Shoa Supermarket",
            category = "Groceries",
            referenceId = "TXN9981",
            timestamp = now,
            source = "SMS_INBOX"
        )
        val tx2 = FinanceTransaction(
            smsFingerprint = "fp_tg_2",
            bankName = "CBE",
            bankCode = "CBE",
            type = "INCOME",
            amount = 15000.0,
            currency = "ETB",
            balanceAfter = 28500.0,
            counterparty = "Monthly Payroll",
            category = "Salary & Income",
            timestamp = now + 1000L,
            source = "SMS_INBOX"
        )

        val singleHtml = TelegramSyncManager.formatFinanceTransactionAlertHtml(tx1)
        assertTrue(singleHtml.contains("Telebirr"))
        assertTrue(singleHtml.contains("-ETB 850.00"))
        assertTrue(singleHtml.contains("Groceries"))
        assertTrue(singleHtml.contains("Shoa Supermarket"))
        assertTrue(singleHtml.contains("ETB 4,150.00"))

        val batchHtml = TelegramSyncManager.formatFinanceBatchSummaryHtml(listOf(tx1, tx2), "SMS Inbox Scan")
        assertTrue(batchHtml.contains("Added <b>2</b> new transaction(s)"))
        assertTrue(batchHtml.contains("ETB 15,000.00"))
        assertTrue(batchHtml.contains("Groceries"))

        val overviewHtml = TelegramSyncManager.formatFinanceOverviewHtml(listOf(tx1, tx2))
        assertTrue(overviewHtml.contains("Finance &amp; Task Ledger"))
        assertTrue(overviewHtml.contains("CBE"))
    }

    @Test
    fun `room database persists telegram task fields and dedup returns only new transactions`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getDatabase(context)
        val dao = db.voiceTaskDao()
        val repo = VoiceTaskRepository(dao)

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

        val txA = FinanceTransaction(
            smsFingerprint = "unique_tg_fp_a_${System.nanoTime()}",
            bankName = "Telebirr",
            bankCode = "TELEBIRR",
            type = "EXPENSE",
            amount = 200.0,
            category = "Utilities",
            timestamp = 1_763_300_000_000L
        )
        val firstInserted = repo.insertFinanceTransactionsDedupReturningInserted(listOf(txA))
        assertEquals(1, firstInserted.size)
        val duplicateInserted = repo.insertFinanceTransactionsDedupReturningInserted(listOf(txA))
        assertEquals(0, duplicateInserted.size)
    }

    @Test
    fun `telegram settings card shows BotFather guide when token needed and saves pasted token`() {
        var savedToken = ""
        composeRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    TelegramSettingsCardContent(
                        config = TelegramConfig(),
                        tokenSource = TelegramTokenSource.NONE,
                        deepLinkUrl = null,
                        isTokenSaving = false,
                        isVerifying = false,
                        isSyncing = false,
                        isSendingFinance = false,
                        statusMsg = null,
                        statusIsError = false,
                        onSaveCustomBotToken = { savedToken = it },
                        onClearCustomBotToken = {},
                        onGenerateVerificationLink = {},
                        onCheckVerification = {},
                        onDisconnect = {},
                        onSetAutoSendReminders = {},
                        onSetAutoSendFinanceAlerts = {},
                        onSetIncludeAudio = {},
                        onSetAiCommandsEnabled = {},
                        onSendTestPing = {},
                        onSyncNow = {},
                        onSendFinanceSummary = {},
                        onDismissStatus = {},
                        onOpenActivityLog = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("Token Needed").assertIsDisplayed()
        composeRule.onNodeWithTag("tg_setup_guide_panel").assertIsDisplayed()
        composeRule.onNodeWithTag("tg_open_botfather_btn").assertIsDisplayed()

        val testToken = "99887766:AAHdqTcvCH1vGWJxfSeofSAs0K5PALDsaw"
        composeRule.onNodeWithTag("tg_bot_token_input").performScrollTo().performTextInput(testToken)
        composeRule.onNodeWithTag("tg_save_token_btn").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(testToken, savedToken)
    }

    @Test
    fun `connected telegram settings card toggles finance alerts and triggers summary dispatch`() {
        var toggledFinance: Boolean? = null
        var sentSummary = false
        composeRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    TelegramSettingsCardContent(
                        config = TelegramConfig(
                            username = "akilas_user",
                            chatId = 12345678L,
                            isVerified = true,
                            botUsername = "FinanceTaskBot",
                            customBotToken = "99887766:AAHdqTcvCH1vGWJxfSeofSAs0K5PALDsaw",
                            autoSendFinanceAlerts = true
                        ),
                        tokenSource = TelegramTokenSource.IN_APP,
                        deepLinkUrl = null,
                        isTokenSaving = false,
                        isVerifying = false,
                        isSyncing = false,
                        isSendingFinance = false,
                        statusMsg = null,
                        statusIsError = false,
                        onSaveCustomBotToken = {},
                        onClearCustomBotToken = {},
                        onGenerateVerificationLink = {},
                        onCheckVerification = {},
                        onDisconnect = {},
                        onSetAutoSendReminders = {},
                        onSetAutoSendFinanceAlerts = { toggledFinance = it },
                        onSetIncludeAudio = {},
                        onSetAiCommandsEnabled = {},
                        onSendTestPing = {},
                        onSyncNow = {},
                        onSendFinanceSummary = { sentSummary = true },
                        onDismissStatus = {},
                        onOpenActivityLog = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("Connected").assertIsDisplayed()
        composeRule.onNodeWithTag("tg_auto_finance_switch").performScrollTo().performClick()
        composeRule.onNodeWithTag("tg_send_finance_summary_btn").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(false, toggledFinance)
        assertTrue(sentSummary)
    }
}
