package com.example.data

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.ui.ReminderNotificationHelper
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

sealed class TelegramResult<out T> {
    data class Success<T>(val data: T, val message: String = "") : TelegramResult<T>()
    data class Error(val reason: String, val isTokenMissing: Boolean = false) : TelegramResult<Nothing>()
}

data class TelegramSyncSummary(
    val completedCount: Int = 0,
    val snoozedCount: Int = 0,
    val createdCount: Int = 0,
    val tasksListedCount: Int = 0
) {
    val totalProcessed: Int get() = completedCount + snoozedCount + createdCount + tasksListedCount
}

enum class TelegramTokenSource {
    IN_APP,
    BUILD_SECRET,
    NONE
}

object TelegramSyncManager {
    private const val TAG = "TelegramSyncManager"
    private const val PLACEHOLDER_TOKEN = "MY_TELEGRAM_BOT_TOKEN"
    private val BOT_TOKEN_REGEX = Regex("^\\d{6,15}:[A-Za-z0-9_-]{20,}$")
    private val syncMutex = Mutex()

    fun getSecretBotToken(): String {
        return try {
            val token = BuildConfig.TELEGRAM_BOT_TOKEN.trim()
            if (token.isBlank() || token == PLACEHOLDER_TOKEN) "" else token
        } catch (_: Throwable) {
            ""
        }
    }

    fun isValidBotTokenFormat(rawToken: String): Boolean {
        return BOT_TOKEN_REGEX.matches(rawToken.trim())
    }

    fun resolveBotToken(customToken: String?): Pair<String, TelegramTokenSource> {
        val custom = customToken?.trim().orEmpty()
        if (custom.isNotEmpty()) {
            return custom to TelegramTokenSource.IN_APP
        }
        val secret = getSecretBotToken()
        if (secret.isNotEmpty()) {
            return secret to TelegramTokenSource.BUILD_SECRET
        }
        return "" to TelegramTokenSource.NONE
    }

    suspend fun getBotToken(context: Context): String {
        val custom = runCatching { TelegramPrefs(context).getConfig().customBotToken }.getOrDefault("")
        return resolveBotToken(custom).first
    }

    fun getBotToken(): String = getSecretBotToken()

    fun isBotConfigured(): Boolean = getSecretBotToken().isNotEmpty()

    suspend fun isBotConfigured(context: Context): Boolean = getBotToken(context).isNotEmpty()

    fun normalizeUsername(raw: String): String {
        return raw.trim().removePrefix("@").trim()
    }

    fun isValidUsername(username: String): Boolean {
        val clean = normalizeUsername(username)
        return clean.matches(Regex("^[a-zA-Z0-9_]{5,32}$"))
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    private fun generatePairingCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val suffix = (1..6).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        return "VT-$suffix"
    }

    private fun missingTokenError(): TelegramResult.Error {
        return TelegramResult.Error(
            reason = "No Telegram bot token configured. Paste your @BotFather token in Settings → Connectors or add TELEGRAM_BOT_TOKEN in Secrets.",
            isTokenMissing = true
        )
    }

    suspend fun verifyAndSaveCustomBotToken(context: Context, rawToken: String): TelegramResult<String> {
        val cleanToken = rawToken.trim()
        if (!isValidBotTokenFormat(cleanToken)) {
            return TelegramResult.Error(
                "Invalid BotFather token format. It should look like 123456789:AAH..."
            )
        }
        return try {
            val meResp = TelegramApiClient.api.getMe(cleanToken)
            val botUser = meResp.body()?.result
            val botUsername = botUser?.username
            if (!meResp.isSuccessful || meResp.body()?.ok != true || botUsername.isNullOrBlank()) {
                val err = meResp.body()?.description ?: "HTTP ${meResp.code()}"
                return TelegramResult.Error("Telegram rejected this bot token: $err")
            }
            TelegramPrefs(context).saveCustomBotToken(
                token = cleanToken,
                verifiedBotUsername = botUsername,
                resetVerificationIfBotChanged = true
            )
            TelegramResult.Success(
                data = botUsername,
                message = "Bot @$botUsername verified! Now enter your Telegram @username below to link."
            )
        } catch (e: Exception) {
            Log.e(TAG, "verifyAndSaveCustomBotToken error", e)
            TelegramResult.Error("Network error verifying bot token: ${e.localizedMessage ?: "Unknown"}")
        }
    }

    suspend fun prepareBotLink(context: Context, rawUsername: String): TelegramResult<String> {
        val token = getBotToken(context)
        if (token.isEmpty()) {
            return missingTokenError()
        }

        val cleanUsername = normalizeUsername(rawUsername)
        if (!isValidUsername(cleanUsername)) {
            return TelegramResult.Error("Enter a valid Telegram username (5–32 letters, numbers, or underscores).")
        }

        return try {
            val meResp = TelegramApiClient.api.getMe(token)
            val botUser = meResp.body()?.result
            val botUsername = botUser?.username
            if (!meResp.isSuccessful || meResp.body()?.ok != true || botUsername.isNullOrBlank()) {
                val err = meResp.body()?.description ?: "HTTP ${meResp.code()}"
                return TelegramResult.Error("Could not reach Telegram bot: $err")
            }

            val code = generatePairingCode()
            val prefs = TelegramPrefs(context)
            prefs.savePendingVerification(
                username = cleanUsername,
                pendingCode = code,
                botUsername = botUsername
            )
            val deepLink = "https://t.me/$botUsername?start=$code"
            TelegramResult.Success(deepLink, "Bot @$botUsername ready for pairing ($code)")
        } catch (e: Exception) {
            Log.e(TAG, "prepareBotLink error", e)
            TelegramResult.Error("Network error connecting to Telegram: ${e.localizedMessage ?: "Unknown"}")
        }
    }

    suspend fun pollAndVerifyConnection(context: Context): TelegramResult<TelegramConfig> {
        val token = getBotToken(context)
        if (token.isEmpty()) {
            return missingTokenError()
        }

        val prefs = TelegramPrefs(context)
        val config = prefs.getConfig()
        if (config.isVerified && config.chatId != null) {
            return TelegramResult.Success(config, "Already connected as @${config.username}")
        }
        val expectedCode = config.pendingCode
        val expectedUsername = config.username
        if (expectedUsername.isBlank()) {
            return TelegramResult.Error("Enter your Telegram username first.")
        }

        return syncMutex.withLock {
            try {
                val resp = TelegramApiClient.api.getUpdates(
                    token = token,
                    offset = if (config.lastUpdateOffset > 0) config.lastUpdateOffset + 1 else null,
                    limit = 100
                )
                val body = resp.body()
                if (!resp.isSuccessful || body?.ok != true) {
                    return@withLock TelegramResult.Error(body?.description ?: "Failed to poll Telegram updates.")
                }

                val updates = body.result.orEmpty()
                val maxUpdateId = updates.maxOfOrNull { it.updateId } ?: config.lastUpdateOffset

                val matchingUpdate = updates.lastOrNull { update ->
                    val msg = update.message ?: return@lastOrNull false
                    val fromUser = msg.from?.username
                    val text = msg.text.orEmpty()
                    val codeMatched = !expectedCode.isNullOrBlank() && text.contains(expectedCode, ignoreCase = true)
                    val userMatched = !fromUser.isNullOrBlank() && fromUser.equals(expectedUsername, ignoreCase = true)
                    codeMatched || (userMatched && text.startsWith("/start"))
                }

                if (matchingUpdate?.message != null) {
                    val msg = matchingUpdate.message
                    val actualUsername = msg.from?.username?.takeIf { it.isNotBlank() } ?: expectedUsername
                    val chatId = msg.chat.id

                    prefs.markVerified(
                        username = actualUsername,
                        chatId = chatId,
                        botUsername = config.botUsername,
                        lastOffset = maxUpdateId
                    )

                    val db = AppDatabase.getDatabase(context)
                    db.voiceTaskDao().insertTelegramLog(
                        TelegramActivityLog(
                            direction = "INBOUND",
                            eventType = "VERIFIED",
                            title = "Telegram Connected",
                            detail = "Verified @$actualUsername via bot link",
                            status = "SYNCED"
                        )
                    )

                    // Send welcome confirmation in Telegram
                    val welcomeText = """
                        <b>🌿 Finance &amp; Task Studio Connected!</b>
                        
                        Hi <b>@${escapeHtml(actualUsername)}</b>, your account is now linked.
                        
                        <b>What you can do:</b>
                        • Receive scheduled reminders &amp; voice memos
                        • Receive Finance SMS alerts &amp; balance updates
                        • Tap <b>✅ Done</b> or <b>⏰ Snooze</b> directly in chat
                        • Send <code>/tasks</code> or <code>/finance</code> for live status
                        • Send <code>/add Call Alex at 5pm</code> to capture tasks remotely
                        • Send <code>/digest</code> or <code>/ask ...</code> for AI summaries
                    """.trimIndent()

                    runCatching {
                        TelegramApiClient.api.sendMessage(
                            token = token,
                            request = TgSendMessageRequest(chatId = chatId, text = welcomeText)
                        )
                    }

                    val updated = prefs.getConfig()
                    TelegramResult.Success(updated, "Connected to @$actualUsername!")
                } else {
                    TelegramResult.Error("Waiting for you to tap Start in Telegram (@${config.botUsername ?: "bot"})...")
                }
            } catch (e: Exception) {
                Log.e(TAG, "pollAndVerifyConnection error", e)
                TelegramResult.Error("Verification check failed: ${e.localizedMessage ?: "Network error"}")
            }
        }
    }

    suspend fun sendTestPing(context: Context): TelegramResult<String> {
        val token = getBotToken(context)
        if (token.isEmpty()) {
            return missingTokenError()
        }
        val config = TelegramPrefs(context).getConfig()
        val chatId = config.chatId ?: return TelegramResult.Error("Connect and verify your Telegram account first.")

        return try {
            val timeStr = SimpleDateFormat("MMM dd, hh:mm:ss a", Locale.getDefault()).format(Date())
            val text = """
                <b>🔔 Finance &amp; Task Studio Ping</b>
                Your Telegram delivery channel for <b>@${escapeHtml(config.username)}</b> is active!
                <i>Checked at $timeStr</i>
            """.trimIndent()

            val resp = TelegramApiClient.api.sendMessage(
                token = token,
                request = TgSendMessageRequest(chatId = chatId, text = text)
            )
            val db = AppDatabase.getDatabase(context)
            if (resp.isSuccessful && resp.body()?.ok == true) {
                db.voiceTaskDao().insertTelegramLog(
                    TelegramActivityLog(
                        direction = "OUTBOUND",
                        eventType = "TEST_PING",
                        title = "Test Ping",
                        detail = "Delivered to @${config.username}",
                        status = "SENT"
                    )
                )
                TelegramResult.Success("Test ping delivered to @${config.username}!")
            } else {
                val err = resp.body()?.description ?: "HTTP ${resp.code()}"
                db.voiceTaskDao().insertTelegramLog(
                    TelegramActivityLog(
                        direction = "OUTBOUND",
                        eventType = "TEST_PING",
                        title = "Test Ping",
                        detail = err,
                        status = "FAILED"
                    )
                )
                TelegramResult.Error("Telegram rejected message: $err")
            }
        } catch (e: Exception) {
            TelegramResult.Error("Failed to reach Telegram: ${e.localizedMessage ?: "Network error"}")
        }
    }

    suspend fun sendTaskAlert(
        context: Context,
        task: VoiceTask,
        isReminder: Boolean
    ): TelegramResult<Long> {
        val token = getBotToken(context)
        if (token.isEmpty()) {
            return missingTokenError()
        }
        val config = TelegramPrefs(context).getConfig()
        val chatId = config.chatId
        if (!config.isVerified || chatId == null) {
            return TelegramResult.Error("Telegram is not connected yet. Link your username in Settings.")
        }

        val db = AppDatabase.getDatabase(context)
        val header = if (isReminder) "⏰ <b>Finance &amp; Task Reminder</b>" else "🎙️ <b>Finance &amp; Task Shared</b>"
        val bodyBuilder = StringBuilder().apply {
            append(header).append("\n")
            append("<b>").append(escapeHtml(task.title)).append("</b>\n")
            if (task.notes.isNotBlank()) {
                append("\n").append(escapeHtml(task.notes.take(700))).append("\n")
            }
            append("\n📁 <i>").append(escapeHtml(task.category)).append("</i>")
            if (task.tags.isNotBlank()) {
                append("  •  🏷️ ").append(escapeHtml(task.tags))
            }
            if (task.reminderTime != null) {
                val fmt = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
                append("\n🕒 Scheduled: ").append(fmt.format(Date(task.reminderTime)))
            }
        }
        val messageText = bodyBuilder.toString()

        val keyboard = TgInlineKeyboardMarkup(
            inlineKeyboard = listOf(
                listOf(
                    TgInlineKeyboardButton(text = "✅ Mark Done", callbackData = "done:${task.id}")
                ),
                listOf(
                    TgInlineKeyboardButton(text = "⏰ Snooze 10m", callbackData = "snooze10:${task.id}"),
                    TgInlineKeyboardButton(text = "⏰ Snooze 1h", callbackData = "snooze60:${task.id}")
                )
            )
        )

        return try {
            val audioFile = task.audioPath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0L }
            val sendAudio = audioFile != null && config.includeAudioAttachments

            val response = if (audioFile != null && config.includeAudioAttachments) {
                val markupJson = TelegramApiClient.moshi.adapter(TgInlineKeyboardMarkup::class.java).toJson(keyboard)
                val ext = audioFile.extension.lowercase(Locale.getDefault())
                val uploadName = "${task.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "voice_memo" }.take(32)}.${if (ext == "mp4") "m4a" else ext}"
                val mime = if (ext == "3gp") "audio/3gpp" else "audio/mp4"
                val filePart = MultipartBody.Part.createFormData(
                    "audio",
                    uploadName,
                    audioFile.asRequestBody(mime.toMediaType())
                )
                val textPlain = "text/plain".toMediaType()
                val durationSecs = ((task.audioDurationMs / 1000L).coerceAtLeast(1L)).toString()
                TelegramApiClient.api.sendAudio(
                    token = token,
                    chatId = chatId.toString().toRequestBody(textPlain),
                    audio = filePart,
                    caption = messageText.take(1000).toRequestBody(textPlain),
                    parseMode = "HTML".toRequestBody(textPlain),
                    title = task.title.take(64).toRequestBody(textPlain),
                    performer = "Finance & Task".toRequestBody(textPlain),
                    durationSeconds = durationSecs.toRequestBody(textPlain),
                    replyMarkup = markupJson.toRequestBody(textPlain)
                )
            } else {
                TelegramApiClient.api.sendMessage(
                    token = token,
                    request = TgSendMessageRequest(
                        chatId = chatId,
                        text = messageText,
                        replyMarkup = keyboard
                    )
                )
            }

            val body = response.body()
            if (response.isSuccessful && body?.ok == true && body.result != null) {
                val now = System.currentTimeMillis()
                db.voiceTaskDao().updateTask(task.copy(lastTelegramSentAt = now))
                db.voiceTaskDao().insertTelegramLog(
                    TelegramActivityLog(
                        taskId = task.id,
                        direction = "OUTBOUND",
                        eventType = if (isReminder) "REMINDER" else "MANUAL_SEND",
                        title = task.title,
                        detail = if (sendAudio) "Sent with audio attachment to @${config.username}" else "Sent to @${config.username}",
                        status = "SENT",
                        timestamp = now
                    )
                )
                TelegramResult.Success(body.result.messageId, "Sent to @${config.username} on Telegram!")
            } else {
                val err = body?.description ?: "HTTP ${response.code()}"
                db.voiceTaskDao().insertTelegramLog(
                    TelegramActivityLog(
                        taskId = task.id,
                        direction = "OUTBOUND",
                        eventType = if (isReminder) "REMINDER" else "MANUAL_SEND",
                        title = task.title,
                        detail = err,
                        status = "FAILED"
                    )
                )
                TelegramResult.Error("Telegram error: $err")
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendTaskAlert error", e)
            db.voiceTaskDao().insertTelegramLog(
                TelegramActivityLog(
                    taskId = task.id,
                    direction = "OUTBOUND",
                    eventType = if (isReminder) "REMINDER" else "MANUAL_SEND",
                    title = task.title,
                    detail = e.localizedMessage ?: "Network error",
                    status = "FAILED"
                )
            )
            TelegramResult.Error("Network error sending to Telegram: ${e.localizedMessage ?: "Unknown"}")
        }
    }

    suspend fun sendNotepadNote(context: Context, note: NotepadNote): TelegramResult<Long> {
        val token = getBotToken(context)
        if (token.isEmpty()) {
            return missingTokenError()
        }
        val config = TelegramPrefs(context).getConfig()
        val chatId = config.chatId
        if (!config.isVerified || chatId == null) {
            return TelegramResult.Error("Telegram is not connected yet. Link your username in Settings.")
        }

        val db = AppDatabase.getDatabase(context)
        val text = buildString {
            append("📝 <b>Finance &amp; Task Notepad</b>\n")
            append("<b>").append(escapeHtml(note.title)).append("</b>\n")
            if (note.content.isNotBlank()) {
                append("\n").append(escapeHtml(note.content.take(900))).append("\n")
            }
            append("\n📁 <i>").append(escapeHtml(note.category)).append("</i>")
            if (note.isPinned) append("  •  📌 Pinned")
        }

        return try {
            val audioFile = note.audioPath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0L }
            val sendAudio = audioFile != null && config.includeAudioAttachments
            val response = if (audioFile != null && config.includeAudioAttachments) {
                val ext = audioFile.extension.lowercase(Locale.getDefault())
                val uploadName = "${note.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "note_voice" }.take(32)}.${if (ext == "mp4") "m4a" else ext}"
                val mime = if (ext == "3gp") "audio/3gpp" else "audio/mp4"
                val filePart = MultipartBody.Part.createFormData(
                    "audio",
                    uploadName,
                    audioFile.asRequestBody(mime.toMediaType())
                )
                val textPlain = "text/plain".toMediaType()
                val durationSecs = ((note.audioDurationMs / 1000L).coerceAtLeast(1L)).toString()
                TelegramApiClient.api.sendAudio(
                    token = token,
                    chatId = chatId.toString().toRequestBody(textPlain),
                    audio = filePart,
                    caption = text.take(1000).toRequestBody(textPlain),
                    parseMode = "HTML".toRequestBody(textPlain),
                    title = note.title.take(64).toRequestBody(textPlain),
                    performer = "Finance & Task Notepad".toRequestBody(textPlain),
                    durationSeconds = durationSecs.toRequestBody(textPlain),
                    replyMarkup = null
                )
            } else {
                TelegramApiClient.api.sendMessage(
                    token = token,
                    request = TgSendMessageRequest(chatId = chatId, text = text)
                )
            }

            val body = response.body()
            if (response.isSuccessful && body?.ok == true && body.result != null) {
                db.voiceTaskDao().insertTelegramLog(
                    TelegramActivityLog(
                        direction = "OUTBOUND",
                        eventType = "MANUAL_SEND",
                        title = note.title,
                        detail = if (sendAudio) "Note + voice memo sent to @${config.username}" else "Note sent to @${config.username}",
                        status = "SENT"
                    )
                )
                TelegramResult.Success(body.result.messageId, "Note sent to @${config.username}!")
            } else {
                TelegramResult.Error(body?.description ?: "Telegram error HTTP ${response.code()}")
            }
        } catch (e: Exception) {
            TelegramResult.Error("Network error: ${e.localizedMessage ?: "Unknown"}")
        }
    }

    suspend fun sendCustomMessage(
        context: Context,
        title: String,
        body: String
    ): TelegramResult<Long> {
        val token = getBotToken(context)
        if (token.isEmpty()) {
            return missingTokenError()
        }
        val config = TelegramPrefs(context).getConfig()
        val chatId = config.chatId
        if (!config.isVerified || chatId == null) {
            return TelegramResult.Error("Telegram is not connected yet. Link your username in Settings.")
        }

        val db = AppDatabase.getDatabase(context)
        val cleanTitle = title.trim().ifBlank { "AI Draft" }
        val messageText = buildString {
            append("🌿 <b>").append(escapeHtml(cleanTitle)).append("</b>\n\n")
            append(escapeHtml(body.trim().take(3400)))
        }

        return try {
            val response = TelegramApiClient.api.sendMessage(
                token = token,
                request = TgSendMessageRequest(chatId = chatId, text = messageText)
            )
            val respBody = response.body()
            if (response.isSuccessful && respBody?.ok == true && respBody.result != null) {
                db.voiceTaskDao().insertTelegramLog(
                    TelegramActivityLog(
                        direction = "OUTBOUND",
                        eventType = "AI_DRAFT",
                        title = cleanTitle,
                        detail = "AI draft sent to @${config.username}",
                        status = "SENT"
                    )
                )
                db.voiceTaskDao().insertConnectorLog(
                    ConnectorDispatchLog(
                        channel = "TELEGRAM",
                        title = cleanTitle,
                        detail = "Delivered to @${config.username}",
                        status = "SENT"
                    )
                )
                TelegramResult.Success(respBody.result.messageId, "Sent to @${config.username} on Telegram!")
            } else {
                val err = respBody?.description ?: "HTTP ${response.code()}"
                db.voiceTaskDao().insertConnectorLog(
                    ConnectorDispatchLog(
                        channel = "TELEGRAM",
                        title = cleanTitle,
                        detail = err,
                        status = "FAILED"
                    )
                )
                TelegramResult.Error("Telegram error: $err")
            }
        } catch (e: Exception) {
            val err = e.localizedMessage ?: "Network error"
            db.voiceTaskDao().insertConnectorLog(
                ConnectorDispatchLog(
                    channel = "TELEGRAM",
                    title = cleanTitle,
                    detail = err,
                    status = "FAILED"
                )
            )
            TelegramResult.Error("Network error sending to Telegram: $err")
        }
    }

    internal fun formatFinanceTransactionAlertHtml(tx: FinanceTransaction): String {
        val emoji = if (tx.isIncoming) "💰" else "💸"
        val typeLabel = TransactionType.fromString(tx.type).label
        val signedAmt = FinanceAnalyticsEngine.formatSignedMoney(tx)
        val dateStr = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(Date(tx.timestamp))
        return buildString {
            append("$emoji <b>Finance Alert · ${escapeHtml(tx.bankName)}</b>\n")
            append("<b>$signedAmt</b> <i>(${escapeHtml(typeLabel)})</i>\n")
            if (tx.counterparty.isNotBlank() && !tx.counterparty.equals(tx.bankName, ignoreCase = true)) {
                append("👤 Counterparty: ${escapeHtml(tx.counterparty)}\n")
            }
            append("🏷️ Category: <b>${escapeHtml(tx.category.ifBlank { "Other" })}</b>\n")
            if (tx.serviceCharge != null && tx.serviceCharge > 0.0) {
                append("🧾 Fee: ${FinanceAnalyticsEngine.formatMoney(tx.serviceCharge, tx.currency)}\n")
            }
            if (tx.balanceAfter != null) {
                append("🏦 Balance: <b>${FinanceAnalyticsEngine.formatMoney(tx.balanceAfter, tx.currency)}</b>\n")
            }
            if (tx.referenceId.isNotBlank()) {
                append("🔖 Ref: <code>${escapeHtml(tx.referenceId)}</code>\n")
            }
            append("🕒 $dateStr")
        }
    }

    internal fun formatFinanceBatchSummaryHtml(
        transactions: List<FinanceTransaction>,
        sourceLabel: String
    ): String {
        val summary = FinanceAnalyticsEngine.summarize(
            filtered = transactions,
            allTransactions = transactions,
            periodLabel = sourceLabel
        )
        val cur = summary.primaryCurrency
        return buildString {
            append("📥 <b>Finance SMS Import · ${escapeHtml(sourceLabel)}</b>\n")
            append("Added <b>${transactions.size}</b> new transaction(s).\n\n")
            append("💰 Inflow: <b>+${FinanceAnalyticsEngine.formatMoney(summary.totalIncome, cur)}</b>\n")
            append("💸 Outflow: <b>-${FinanceAnalyticsEngine.formatMoney(summary.totalOutflow, cur)}</b>\n")
            append("📊 Net: <b>${FinanceAnalyticsEngine.formatMoney(summary.netFlow, cur)}</b>\n")
            if (summary.categoryBreakdowns.isNotEmpty()) {
                append("\n<b>Top Categories:</b>\n")
                summary.categoryBreakdowns.take(4).forEach { cat ->
                    append("• ${escapeHtml(cat.category)}: ${FinanceAnalyticsEngine.formatMoney(cat.totalAmount, cur)} (${cat.count})\n")
                }
            }
            val balances = summary.bankBreakdowns.filter { it.latestBalance != null }
            if (balances.isNotEmpty()) {
                append("\n<b>Latest Balances:</b>\n")
                balances.take(4).forEach { b ->
                    append("• ${escapeHtml(b.bankName)}: <b>${FinanceAnalyticsEngine.formatMoney(b.latestBalance ?: 0.0, b.latestCurrency)}</b>\n")
                }
            }
        }.trimEnd()
    }

    internal fun formatFinanceOverviewHtml(transactions: List<FinanceTransaction>): String {
        if (transactions.isEmpty()) {
            return "💼 <b>Finance &amp; Task Summary</b>\nNo bank or mobile-money transactions recorded yet."
        }
        val monthFilter = FinanceFilterState(periodMode = FinancePeriodMode.SPECIFIC_MONTH)
        val (monthStart, monthEnd) = FinanceAnalyticsEngine.resolveDateRange(monthFilter)
        val monthTxs = transactions.filter { tx ->
            (monthStart == null || tx.timestamp >= monthStart) &&
                (monthEnd == null || tx.timestamp <= monthEnd)
        }
        val useMonth = monthTxs.isNotEmpty()
        val scopeLabel = if (useMonth) "This Month" else "All Time"
        val activeSummary = FinanceAnalyticsEngine.summarize(
            filtered = if (useMonth) monthTxs else transactions,
            allTransactions = transactions,
            periodLabel = scopeLabel
        )
        val cur = activeSummary.primaryCurrency

        return buildString {
            append("💼 <b>Finance &amp; Task Ledger ($scopeLabel)</b>\n")
            append("Transactions: <b>${activeSummary.transactionCount}</b>\n\n")
            append("💰 Income: <b>+${FinanceAnalyticsEngine.formatMoney(activeSummary.totalIncome, cur)}</b>\n")
            append("💸 Outflow: <b>-${FinanceAnalyticsEngine.formatMoney(activeSummary.totalOutflow, cur)}</b>\n")
            append("📊 Net Flow: <b>${FinanceAnalyticsEngine.formatMoney(activeSummary.netFlow, cur)}</b>\n")
            if (activeSummary.totalFees > 0.0) {
                append("🧾 Fees: ${FinanceAnalyticsEngine.formatMoney(activeSummary.totalFees, cur)}\n")
            }
            if (activeSummary.categoryBreakdowns.isNotEmpty()) {
                append("\n<b>Top Spending Categories:</b>\n")
                activeSummary.categoryBreakdowns.take(4).forEach { cat ->
                    append("• ${escapeHtml(cat.category)}: ${FinanceAnalyticsEngine.formatMoney(cat.totalAmount, cur)}\n")
                }
            }
            val balances = activeSummary.bankBreakdowns.filter { it.latestBalance != null }
            if (balances.isNotEmpty()) {
                append("\n<b>Latest Account Balances:</b>\n")
                balances.take(5).forEach { b ->
                    append("• ${escapeHtml(b.bankName)}: <b>${FinanceAnalyticsEngine.formatMoney(b.latestBalance ?: 0.0, b.latestCurrency)}</b>\n")
                }
            }
        }.trimEnd()
    }

    suspend fun sendFinanceImportNotification(
        context: Context,
        inserted: List<FinanceTransaction>,
        sourceLabel: String
    ): TelegramResult<Int> {
        if (inserted.isEmpty()) return TelegramResult.Success(0)
        val token = getBotToken(context)
        if (token.isEmpty()) return TelegramResult.Success(0)
        val config = TelegramPrefs(context).getConfig()
        val chatId = config.chatId
        if (!config.isVerified || chatId == null || !config.autoSendFinanceAlerts) {
            return TelegramResult.Success(0)
        }

        val isBatch = inserted.size >= 2
        val html = if (isBatch) {
            formatFinanceBatchSummaryHtml(inserted, sourceLabel)
        } else {
            formatFinanceTransactionAlertHtml(inserted.first())
        }
        val eventType = if (isBatch) "FINANCE_SUMMARY" else "FINANCE_ALERT"
        val logTitle = if (isBatch) {
            "${inserted.size} Finance SMS Imported"
        } else {
            val single = inserted.first()
            "${single.bankName}: ${FinanceAnalyticsEngine.formatSignedMoney(single)}"
        }

        return try {
            val resp = TelegramApiClient.api.sendMessage(
                token = token,
                request = TgSendMessageRequest(chatId = chatId, text = html)
            )
            val db = AppDatabase.getDatabase(context)
            if (resp.isSuccessful && resp.body()?.ok == true) {
                db.voiceTaskDao().insertTelegramLog(
                    TelegramActivityLog(
                        direction = "OUTBOUND",
                        eventType = eventType,
                        title = logTitle,
                        detail = "Sent to @${config.username} ($sourceLabel)",
                        status = "SENT"
                    )
                )
                TelegramResult.Success(inserted.size, "Finance alert sent to @${config.username}!")
            } else {
                val err = resp.body()?.description ?: "HTTP ${resp.code()}"
                db.voiceTaskDao().insertTelegramLog(
                    TelegramActivityLog(
                        direction = "OUTBOUND",
                        eventType = eventType,
                        title = logTitle,
                        detail = err,
                        status = "FAILED"
                    )
                )
                TelegramResult.Error("Telegram error: $err")
            }
        } catch (e: Exception) {
            TelegramResult.Error("Network error sending Finance alert: ${e.localizedMessage ?: "Unknown"}")
        }
    }

    suspend fun sendFinanceSummary(context: Context): TelegramResult<Long> {
        val token = getBotToken(context)
        if (token.isEmpty()) {
            return missingTokenError()
        }
        val config = TelegramPrefs(context).getConfig()
        val chatId = config.chatId
        if (!config.isVerified || chatId == null) {
            return TelegramResult.Error("Telegram is not connected yet. Link your username in Settings → Connectors.")
        }

        val db = AppDatabase.getDatabase(context)
        val dao = db.voiceTaskDao()
        val allTxs = dao.getAllFinanceTransactions()
        val html = formatFinanceOverviewHtml(allTxs)

        return try {
            val resp = TelegramApiClient.api.sendMessage(
                token = token,
                request = TgSendMessageRequest(chatId = chatId, text = html)
            )
            val body = resp.body()
            if (resp.isSuccessful && body?.ok == true && body.result != null) {
                dao.insertTelegramLog(
                    TelegramActivityLog(
                        direction = "OUTBOUND",
                        eventType = "FINANCE_SUMMARY",
                        title = "Finance Ledger Summary",
                        detail = "Sent ${allTxs.size} transaction summary to @${config.username}",
                        status = "SENT"
                    )
                )
                dao.insertConnectorLog(
                    ConnectorDispatchLog(
                        channel = "TELEGRAM",
                        title = "Finance Ledger Summary",
                        detail = "Delivered to @${config.username}",
                        status = "SENT"
                    )
                )
                TelegramResult.Success(body.result.messageId, "Finance summary sent to @${config.username}!")
            } else {
                val err = body?.description ?: "HTTP ${resp.code()}"
                dao.insertTelegramLog(
                    TelegramActivityLog(
                        direction = "OUTBOUND",
                        eventType = "FINANCE_SUMMARY",
                        title = "Finance Ledger Summary",
                        detail = err,
                        status = "FAILED"
                    )
                )
                TelegramResult.Error("Telegram error: $err")
            }
        } catch (e: Exception) {
            TelegramResult.Error("Network error sending Finance summary: ${e.localizedMessage ?: "Unknown"}")
        }
    }

    suspend fun syncIncomingUpdates(context: Context): TelegramResult<TelegramSyncSummary> {
        val token = getBotToken(context)
        if (token.isEmpty()) {
            return missingTokenError()
        }
        val prefs = TelegramPrefs(context)
        val config = prefs.getConfig()
        val verifiedChatId = config.chatId
        if (!config.isVerified || verifiedChatId == null) {
            return TelegramResult.Success(TelegramSyncSummary())
        }

        return syncMutex.withLock {
            try {
                val resp = TelegramApiClient.api.getUpdates(
                    token = token,
                    offset = if (config.lastUpdateOffset > 0) config.lastUpdateOffset + 1 else null,
                    limit = 50
                )
                val body = resp.body()
                if (!resp.isSuccessful || body?.ok != true) {
                    return@withLock TelegramResult.Error(body?.description ?: "Failed to sync Telegram updates.")
                }

                val updates = body.result.orEmpty()
                if (updates.isEmpty()) {
                    return@withLock TelegramResult.Success(TelegramSyncSummary())
                }

                val db = AppDatabase.getDatabase(context)
                val dao = db.voiceTaskDao()
                var completedCount = 0
                var snoozedCount = 0
                var createdCount = 0
                var tasksListedCount = 0
                var maxUpdateId = config.lastUpdateOffset

                for (update in updates) {
                    if (update.updateId > maxUpdateId) {
                        maxUpdateId = update.updateId
                    }

                    // 1. Handle inline button callbacks (Done / Snooze)
                    val callback = update.callbackQuery
                    if (callback != null && callback.message?.chat?.id == verifiedChatId) {
                        val data = callback.data.orEmpty()
                        val parts = data.split(":")
                        val action = parts.getOrNull(0)
                        val taskId = parts.getOrNull(1)?.toIntOrNull()
                        val task = taskId?.let { dao.getTaskById(it) }

                        when {
                            action == "done" && task != null -> {
                                dao.updateTask(task.copy(isCompleted = true, reminderTime = null))
                                ReminderNotificationHelper.scheduleDeviceNotification(context, task.id, null)
                                completedCount++
                                dao.insertTelegramLog(
                                    TelegramActivityLog(
                                        taskId = task.id,
                                        direction = "INBOUND",
                                        eventType = "COMPLETED_FROM_TG",
                                        title = task.title,
                                        detail = "Marked completed from Telegram button",
                                        status = "SYNCED"
                                    )
                                )
                                runCatching {
                                    TelegramApiClient.api.answerCallbackQuery(
                                        token = token,
                                        request = TgAnswerCallbackRequest(
                                            callbackQueryId = callback.id,
                                            text = "✅ Completed: ${task.title}"
                                        )
                                    )
                                    TelegramApiClient.api.editMessageReplyMarkup(
                                        token = token,
                                        request = TgEditReplyMarkupRequest(
                                            chatId = verifiedChatId,
                                            messageId = callback.message.messageId,
                                            replyMarkup = TgInlineKeyboardMarkup(
                                                listOf(listOf(TgInlineKeyboardButton("✅ Completed in Finance & Task", callbackData = "noop")))
                                            )
                                        )
                                    )
                                }
                            }
                            (action == "snooze10" || action == "snooze60") && task != null -> {
                                val delayMs = if (action == "snooze60") 3_600_000L else 600_000L
                                val label = if (action == "snooze60") "1 hour" else "10 mins"
                                val newTime = System.currentTimeMillis() + delayMs
                                dao.updateTask(task.copy(isCompleted = false, reminderTime = newTime))
                                ReminderNotificationHelper.scheduleDeviceNotification(context, task.id, newTime)
                                snoozedCount++
                                dao.insertTelegramLog(
                                    TelegramActivityLog(
                                        taskId = task.id,
                                        direction = "INBOUND",
                                        eventType = "SNOOZED_FROM_TG",
                                        title = task.title,
                                        detail = "Snoozed $label from Telegram",
                                        status = "SYNCED"
                                    )
                                )
                                runCatching {
                                    TelegramApiClient.api.answerCallbackQuery(
                                        token = token,
                                        request = TgAnswerCallbackRequest(
                                            callbackQueryId = callback.id,
                                            text = "⏰ Snoozed for $label"
                                        )
                                    )
                                }
                            }
                            action == "noop" -> {
                                runCatching {
                                    TelegramApiClient.api.answerCallbackQuery(
                                        token = token,
                                        request = TgAnswerCallbackRequest(
                                            callbackQueryId = callback.id,
                                            text = "Already synced!"
                                        )
                                    )
                                }
                            }
                            else -> {
                                runCatching {
                                    TelegramApiClient.api.answerCallbackQuery(
                                        token = token,
                                        request = TgAnswerCallbackRequest(
                                            callbackQueryId = callback.id,
                                            text = "Task not found locally."
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // 2. Handle inbound chat commands (/tasks, /add, /help)
                    val msg = update.message
                    if (msg != null && msg.chat.id == verifiedChatId) {
                        val text = msg.text?.trim().orEmpty()
                        when {
                            text.startsWith("/tasks", ignoreCase = true) -> {
                                val active = dao.getAllTasks().filter { !it.isCompleted }.take(10)
                                val reply = if (active.isEmpty()) {
                                    "🎉 <b>All caught up!</b> No pending tasks in Finance &amp; Task."
                                } else {
                                    buildString {
                                        append("📋 <b>Active Tasks (${active.size})</b>\n\n")
                                        active.forEachIndexed { idx, t ->
                                            val audioTag = if (t.audioPath != null) " 🎙️" else ""
                                            val alarmTag = if (t.reminderTime != null) " ⏰" else ""
                                            append("${idx + 1}. <b>${escapeHtml(t.title)}</b>$audioTag$alarmTag\n")
                                            append("    <i>${escapeHtml(t.category)}</i>\n")
                                        }
                                    }
                                }
                                runCatching {
                                    TelegramApiClient.api.sendMessage(
                                        token = token,
                                        request = TgSendMessageRequest(chatId = verifiedChatId, text = reply)
                                    )
                                }
                                tasksListedCount++
                            }
                            text.startsWith("/add ", ignoreCase = true) -> {
                                val newTitle = text.substringAfter(" ").trim()
                                if (newTitle.isNotBlank()) {
                                    val newId = dao.insertTask(
                                        VoiceTask(
                                            title = newTitle.take(120),
                                            notes = "Captured remotely via Telegram (@${config.username})",
                                            category = "Inbox",
                                            tags = "Telegram",
                                            iconName = "Bookmark"
                                        )
                                    )
                                    createdCount++
                                    dao.insertTelegramLog(
                                        TelegramActivityLog(
                                            taskId = newId.toInt(),
                                            direction = "INBOUND",
                                            eventType = "CREATED_FROM_TG",
                                            title = newTitle.take(120),
                                            detail = "Created via /add command from Telegram",
                                            status = "SYNCED"
                                        )
                                    )
                                    runCatching {
                                        TelegramApiClient.api.sendMessage(
                                            token = token,
                                            request = TgSendMessageRequest(
                                                chatId = verifiedChatId,
                                                text = "✅ Added to Finance &amp; Task Inbox:\n<b>${escapeHtml(newTitle.take(120))}</b>"
                                            )
                                        )
                                    }
                                }
                            }
                            text.startsWith("/digest", ignoreCase = true) || text.startsWith("/ask", ignoreCase = true) -> {
                                if (!config.aiTelegramCommandsEnabled) {
                                    runCatching {
                                        TelegramApiClient.api.sendMessage(
                                            token = token,
                                            request = TgSendMessageRequest(
                                                chatId = verifiedChatId,
                                                text = "AI bot commands are turned off in Finance & Task Settings → Telegram."
                                            )
                                        )
                                    }
                                } else {
                                    val aiConfig = AiConnectorPrefs(context).getConfig()
                                    if (!aiConfig.isAiConfigured) {
                                        runCatching {
                                            TelegramApiClient.api.sendMessage(
                                                token = token,
                                                request = TgSendMessageRequest(
                                                    chatId = verifiedChatId,
                                                    text = "🔑 Add your Gemini, OpenAI, or Claude API key in Finance & Task Settings → AI Studio to use /ask and /digest."
                                                )
                                            )
                                        }
                                    } else {
                                        val isDigest = text.startsWith("/digest", ignoreCase = true)
                                        val query = if (isDigest) {
                                            "Give me a crisp daily digest of my pending tasks, upcoming reminders, and recent notepad notes."
                                        } else {
                                            text.substringAfter(" ", "").trim()
                                        }
                                        if (query.isBlank()) {
                                            runCatching {
                                                TelegramApiClient.api.sendMessage(
                                                    token = token,
                                                    request = TgSendMessageRequest(
                                                        chatId = verifiedChatId,
                                                        text = "Usage: <code>/ask What memos did I record about work?</code>"
                                                    )
                                                )
                                            }
                                        } else {
                                            val tasks = dao.getAllTasks()
                                            val notes = dao.getAllNotepadNotes()
                                            val snapshot = buildWorkspaceSnapshotForBot(tasks, notes)
                                            val aiRes = AiProviderClient.chatWithContext(
                                                config = aiConfig,
                                                history = listOf(AiChatMessage(isUser = true, text = query)),
                                                workspaceContext = snapshot,
                                                allowStructuredActions = false
                                            )
                                            val replyBody = when (aiRes) {
                                                is AiResult.Success -> "🤖 <b>${aiConfig.activeProvider.displayName}</b>\n\n${escapeHtml(aiRes.data.text.take(3500))}"
                                                is AiResult.Error -> "⚠️ AI request failed: ${escapeHtml(aiRes.reason)}"
                                            }
                                            runCatching {
                                                TelegramApiClient.api.sendMessage(
                                                    token = token,
                                                    request = TgSendMessageRequest(chatId = verifiedChatId, text = replyBody)
                                                )
                                            }
                                            dao.insertTelegramLog(
                                                TelegramActivityLog(
                                                    direction = "INBOUND",
                                                    eventType = if (isDigest) "AI_DIGEST_TG" else "AI_ASK_TG",
                                                    title = if (isDigest) "AI /digest" else "AI /ask",
                                                    detail = query.take(100),
                                                    status = if (aiRes is AiResult.Success) "SYNCED" else "FAILED"
                                                )
                                            )
                                            dao.insertConnectorLog(
                                                ConnectorDispatchLog(
                                                    channel = "AI",
                                                    title = if (isDigest) "Telegram /digest" else "Telegram /ask",
                                                    detail = "${aiConfig.activeProvider.displayName} replied to @${config.username}",
                                                    status = if (aiRes is AiResult.Success) "SENT" else "FAILED"
                                                )
                                            )
                                            tasksListedCount++
                                        }
                                    }
                                }
                            }
                            text.startsWith("/finance", ignoreCase = true) -> {
                                val txs = dao.getAllFinanceTransactions()
                                val reply = formatFinanceOverviewHtml(txs)
                                runCatching {
                                    TelegramApiClient.api.sendMessage(
                                        token = token,
                                        request = TgSendMessageRequest(chatId = verifiedChatId, text = reply)
                                    )
                                }
                                dao.insertTelegramLog(
                                    TelegramActivityLog(
                                        direction = "INBOUND",
                                        eventType = "FINANCE_QUERY_TG",
                                        title = "/finance command",
                                        detail = "Replied with ${txs.size}-tx finance summary",
                                        status = "SYNCED"
                                    )
                                )
                                tasksListedCount++
                            }
                            text.startsWith("/help", ignoreCase = true) -> {
                                runCatching {
                                    TelegramApiClient.api.sendMessage(
                                        token = token,
                                        request = TgSendMessageRequest(
                                            chatId = verifiedChatId,
                                            text = """
                                                <b>🌿 Finance &amp; Task Bot Commands</b>
                                                • <code>/tasks</code> — List active tasks
                                                • <code>/finance</code> — Monthly cashflow &amp; bank balances
                                                • <code>/add Buy studio mic</code> — Add task to Inbox
                                                • <code>/digest</code> — AI daily digest of tasks &amp; notes
                                                • <code>/ask your question</code> — Ask AI across saved memos
                                                • Use <b>✅ Mark Done</b> or <b>⏰ Snooze</b> on any alert!
                                            """.trimIndent()
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                prefs.updateLastOffset(maxUpdateId)
                TelegramResult.Success(
                    TelegramSyncSummary(
                        completedCount = completedCount,
                        snoozedCount = snoozedCount,
                        createdCount = createdCount,
                        tasksListedCount = tasksListedCount
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "syncIncomingUpdates error", e)
                TelegramResult.Error("Sync error: ${e.localizedMessage ?: "Network error"}")
            }
        }
    }

    private fun buildWorkspaceSnapshotForBot(
        tasks: List<VoiceTask>,
        notes: List<NotepadNote>
    ): String {
        val dateFmt = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
        return buildString {
            append("TASKS (${tasks.size} total):\n")
            tasks.take(25).forEach { t ->
                val status = if (t.isCompleted) "DONE" else "OPEN"
                val alarm = t.reminderTime?.let { " | Alarm: ${dateFmt.format(Date(it))}" } ?: ""
                append("- [$status] (${t.category}) ${t.title}")
                if (t.notes.isNotBlank()) append(": ${t.notes.take(180)}")
                append(alarm).append("\n")
            }
            append("\nNOTEPAD NOTES (${notes.size} total):\n")
            notes.take(15).forEach { n ->
                append("- (${n.category}) ${n.title}: ${n.content.take(220)}\n")
            }
        }
    }
}
