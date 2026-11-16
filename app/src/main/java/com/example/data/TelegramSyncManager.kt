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

object TelegramSyncManager {
    private const val TAG = "TelegramSyncManager"
    private const val PLACEHOLDER_TOKEN = "MY_TELEGRAM_BOT_TOKEN"
    private val syncMutex = Mutex()

    fun getBotToken(): String {
        return try {
            val token = BuildConfig.TELEGRAM_BOT_TOKEN.trim()
            if (token.isBlank() || token == PLACEHOLDER_TOKEN) "" else token
        } catch (_: Throwable) {
            ""
        }
    }

    fun isBotConfigured(): Boolean = getBotToken().isNotEmpty()

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

    suspend fun prepareBotLink(context: Context, rawUsername: String): TelegramResult<String> {
        val token = getBotToken()
        if (token.isEmpty()) {
            return TelegramResult.Error(
                reason = "TELEGRAM_BOT_TOKEN is not configured in AI Studio Secrets.",
                isTokenMissing = true
            )
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
        val token = getBotToken()
        if (token.isEmpty()) {
            return TelegramResult.Error("TELEGRAM_BOT_TOKEN is not configured in AI Studio Secrets.", isTokenMissing = true)
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
                        <b>🌿 Voice Task Studio Connected!</b>
                        
                        Hi <b>@${escapeHtml(actualUsername)}</b>, your account is now linked.
                        
                        <b>What you can do:</b>
                        • Receive scheduled reminders &amp; voice memos
                        • Tap <b>✅ Done</b> or <b>⏰ Snooze</b> directly in chat
                        • Send <code>/tasks</code> to view active tasks
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
        val token = getBotToken()
        if (token.isEmpty()) {
            return TelegramResult.Error("TELEGRAM_BOT_TOKEN is not configured in AI Studio Secrets.", isTokenMissing = true)
        }
        val config = TelegramPrefs(context).getConfig()
        val chatId = config.chatId ?: return TelegramResult.Error("Connect and verify your Telegram account first.")

        return try {
            val timeStr = SimpleDateFormat("MMM dd, hh:mm:ss a", Locale.getDefault()).format(Date())
            val text = """
                <b>🔔 Voice Task Studio Ping</b>
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
        val token = getBotToken()
        if (token.isEmpty()) {
            return TelegramResult.Error("TELEGRAM_BOT_TOKEN is not configured in AI Studio Secrets.", isTokenMissing = true)
        }
        val config = TelegramPrefs(context).getConfig()
        val chatId = config.chatId
        if (!config.isVerified || chatId == null) {
            return TelegramResult.Error("Telegram is not connected yet. Link your username in Settings.")
        }

        val db = AppDatabase.getDatabase(context)
        val header = if (isReminder) "⏰ <b>Voice Task Reminder</b>" else "🎙️ <b>Voice Task Shared</b>"
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
                    performer = "Voice Task".toRequestBody(textPlain),
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
        val token = getBotToken()
        if (token.isEmpty()) {
            return TelegramResult.Error("TELEGRAM_BOT_TOKEN is not configured in AI Studio Secrets.", isTokenMissing = true)
        }
        val config = TelegramPrefs(context).getConfig()
        val chatId = config.chatId
        if (!config.isVerified || chatId == null) {
            return TelegramResult.Error("Telegram is not connected yet. Link your username in Settings.")
        }

        val db = AppDatabase.getDatabase(context)
        val text = buildString {
            append("📝 <b>Voice Task Notepad</b>\n")
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
                    performer = "Voice Task Notepad".toRequestBody(textPlain),
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
        val token = getBotToken()
        if (token.isEmpty()) {
            return TelegramResult.Error("TELEGRAM_BOT_TOKEN is not configured in AI Studio Secrets.", isTokenMissing = true)
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

    suspend fun syncIncomingUpdates(context: Context): TelegramResult<TelegramSyncSummary> {
        val token = getBotToken()
        if (token.isEmpty()) {
            return TelegramResult.Error("TELEGRAM_BOT_TOKEN is not configured in AI Studio Secrets.", isTokenMissing = true)
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
                                                listOf(listOf(TgInlineKeyboardButton("✅ Completed in Voice Task", callbackData = "noop")))
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
                                    "🎉 <b>All caught up!</b> No pending tasks in Voice Task."
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
                                                text = "✅ Added to Voice Task Inbox:\n<b>${escapeHtml(newTitle.take(120))}</b>"
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
                                                text = "AI bot commands are turned off in Voice Task Settings → Telegram."
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
                                                    text = "🔑 Add your Gemini, OpenAI, or Claude API key in Voice Task Settings → AI Studio to use /ask and /digest."
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
                            text.startsWith("/help", ignoreCase = true) -> {
                                runCatching {
                                    TelegramApiClient.api.sendMessage(
                                        token = token,
                                        request = TgSendMessageRequest(
                                            chatId = verifiedChatId,
                                            text = """
                                                <b>🌿 Voice Task Bot Commands</b>
                                                • <code>/tasks</code> — List active tasks
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
