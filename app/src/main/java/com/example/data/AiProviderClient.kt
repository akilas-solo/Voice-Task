package com.example.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

sealed class AiResult<out T> {
    data class Success<T>(
        val data: T,
        val message: String = "",
        val latencyMs: Long = 0L
    ) : AiResult<T>()

    data class Error(val reason: String) : AiResult<Nothing>()
}

data class AiSuggestedAction(
    val type: String, // "CREATE_TASK", "SEND_TELEGRAM", "DRAFT_EMAIL", "SAVE_NOTION", "SEND_WEBHOOK"
    val label: String,
    val title: String,
    val body: String,
    val tags: String = "",
    val folder: String = "Inbox"
)

data class AiChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val suggestedActions: List<AiSuggestedAction> = emptyList()
)

data class ExtractedVoiceTask(
    val transcript: String,
    val title: String,
    val summary: String,
    val suggestedFolder: String,
    val tags: String,
    val actionItems: List<String>
)

data class GeneratedConnectorDraft(
    val subject: String,
    val body: String,
    val tags: String
)

object AiProviderClient {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private const val MAX_INLINE_AUDIO_BYTES = 18L * 1024L * 1024L

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    suspend fun testConnection(config: AiStudioConfig): AiResult<String> = withContext(Dispatchers.IO) {
        val key = config.activeApiKey.trim()
        val provider = config.activeProvider
        val model = config.activeModel.trim()
        if (key.isBlank()) {
            return@withContext AiResult.Error("Enter your ${provider.displayName} API key first.")
        }

        val start = System.currentTimeMillis()
        val res = generateText(
            provider = provider,
            apiKey = key,
            model = model,
            systemPrompt = "You are a connectivity check. Reply concisely.",
            messages = listOf(true to "Reply with OK."),
            expectJson = false
        )
        val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)
        when (res) {
            is AiResult.Success -> AiResult.Success(
                data = "Verified ${provider.displayName} ($model) in ${latency}ms",
                message = "Connected to ${provider.displayName}",
                latencyMs = latency
            )
            is AiResult.Error -> res
        }
    }

    suspend fun chatWithContext(
        config: AiStudioConfig,
        history: List<AiChatMessage>,
        workspaceContext: String,
        allowStructuredActions: Boolean = true
    ): AiResult<AiChatMessage> = withContext(Dispatchers.IO) {
        val key = config.activeApiKey.trim()
        val provider = config.activeProvider
        val model = config.activeModel.trim()
        if (key.isBlank()) {
            return@withContext AiResult.Error("Add your ${provider.displayName} API key in Settings -> AI & Connectors.")
        }

        val systemPrompt = if (allowStructuredActions) {
            """
            You are Voice Task Studio AI, a calm, concise personal productivity assistant embedded inside a voice-memo and notepad app.
            You have direct read access to the user's current workspace snapshot below. Ground your answers accurately in these memos, alarms, and notes without inventing tasks or dates.
            
            Workspace Snapshot:
            $workspaceContext
            
            Respond ONLY with valid JSON matching this exact schema:
            {
              "reply": "Clear, helpful answer grounded in the user's memos & notes.",
              "actions": [
                {
                  "type": "CREATE_TASK" | "SEND_TELEGRAM" | "DRAFT_EMAIL" | "SAVE_NOTION" | "SEND_WEBHOOK",
                  "label": "Short 2-4 word button label",
                  "title": "Concise task or subject title",
                  "body": "Ready-to-send message body or notes",
                  "tags": "comma,separated,tags",
                  "folder": "Inbox or suggested folder"
                }
              ]
            }
            Include 0 to 3 relevant actions ONLY when the user asks to create tasks, summarize for sharing, email, push to Notion/Telegram, or send a follow-up. Otherwise keep "actions" as [].
            """.trimIndent()
        } else {
            """
            You are Voice Task Studio AI responding concisely inside Telegram chat.
            Use the user's workspace snapshot below to answer accurately without inventing tasks or notes.
            Keep formatting clean and scannable with short bullet points (no markdown tables or code fences).
            
            Workspace Snapshot:
            $workspaceContext
            """.trimIndent()
        }

        val turns = history.takeLast(12).map { it.isUser to it.text }
        val start = System.currentTimeMillis()
        val rawResult = generateText(
            provider = provider,
            apiKey = key,
            model = model,
            systemPrompt = systemPrompt,
            messages = turns.ifEmpty { listOf(true to "Summarize my current priorities.") },
            expectJson = allowStructuredActions
        )
        val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)

        when (rawResult) {
            is AiResult.Error -> rawResult
            is AiResult.Success -> {
                if (!allowStructuredActions) {
                    AiResult.Success(
                        data = AiChatMessage(isUser = false, text = rawResult.data.trim()),
                        latencyMs = latency
                    )
                } else {
                    val parsed = parseChatJsonResponse(rawResult.data)
                    AiResult.Success(data = parsed, latencyMs = latency)
                }
            }
        }
    }

    suspend fun transcribeAndStructureAudio(
        config: AiStudioConfig,
        audioFile: File
    ): AiResult<ExtractedVoiceTask> = withContext(Dispatchers.IO) {
        val key = config.activeApiKey.trim()
        val provider = config.activeProvider
        val model = config.activeModel.trim()
        if (key.isBlank()) {
            return@withContext AiResult.Error("Add your ${provider.displayName} API key in Settings -> AI & Connectors.")
        }
        if (!audioFile.exists() || audioFile.length() <= 0L) {
            return@withContext AiResult.Error("Audio file is missing or empty.")
        }
        if (audioFile.length() > MAX_INLINE_AUDIO_BYTES) {
            return@withContext AiResult.Error("Audio file exceeds the 18 MB inline limit.")
        }

        val start = System.currentTimeMillis()
        when (provider) {
            AiProvider.CLAUDE -> {
                AiResult.Error(
                    "Anthropic Claude does not accept raw audio uploads directly. Switch your active AI provider to Google Gemini or OpenAI for audio transcription."
                )
            }
            AiProvider.OPENAI -> {
                val transcriptRes = transcribeWithOpenAiWhisper(key, audioFile)
                when (transcriptRes) {
                    is AiResult.Error -> transcriptRes
                    is AiResult.Success -> {
                        val transcript = transcriptRes.data
                        val structPrompt = """
                            Convert this voice memo transcript into a structured task & summary.
                            Return ONLY valid JSON:
                            {
                              "transcript": "verbatim cleaned transcript",
                              "title": "concise 3-7 word memo title",
                              "summary": "2-3 sentence summary of key points",
                              "suggestedFolder": "Inbox | Work | Personal | Ideas | Quick Memo",
                              "tags": "tag1, tag2",
                              "actionItems": ["concrete follow-up item 1", "item 2"]
                            }
                            Transcript:
                            $transcript
                        """.trimIndent()
                        val structRes = generateText(
                            provider = AiProvider.OPENAI,
                            apiKey = key,
                            model = model,
                            systemPrompt = "You extract clean structured tasks from spoken transcripts.",
                            messages = listOf(true to structPrompt),
                            expectJson = true
                        )
                        val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                        when (structRes) {
                            is AiResult.Error -> AiResult.Success(
                                data = ExtractedVoiceTask(
                                    transcript = transcript,
                                    title = transcript.take(48).ifBlank { "Transcribed Voice Memo" },
                                    summary = transcript,
                                    suggestedFolder = "Inbox",
                                    tags = "Voice, AI",
                                    actionItems = emptyList()
                                ),
                                message = "Transcribed audio with Whisper (structuring skipped).",
                                latencyMs = latency
                            )
                            is AiResult.Success -> {
                                val parsed = parseExtractedTaskJson(structRes.data, fallbackTranscript = transcript)
                                AiResult.Success(parsed, "Transcribed & structured via OpenAI", latency)
                            }
                        }
                    }
                }
            }
            AiProvider.GEMINI -> {
                val res = transcribeAndStructureWithGemini(key, model, audioFile)
                val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                when (res) {
                    is AiResult.Error -> res
                    is AiResult.Success -> AiResult.Success(res.data, "Transcribed & structured via Gemini", latency)
                }
            }
        }
    }

    suspend fun draftForConnector(
        config: AiStudioConfig,
        channelName: String,
        tone: String,
        sourceTitle: String,
        sourceBody: String,
        extraInstruction: String = ""
    ): AiResult<GeneratedConnectorDraft> = withContext(Dispatchers.IO) {
        val key = config.activeApiKey.trim()
        val provider = config.activeProvider
        val model = config.activeModel.trim()
        if (key.isBlank()) {
            return@withContext AiResult.Error("Add your ${provider.displayName} API key in Settings -> AI & Connectors.")
        }

        val formatGuidance = when (channelName.lowercase(Locale.getDefault())) {
            "email" -> "Write an email with a clear Subject line and polished, ready-to-send email body."
            "telegram" -> "Write a crisp Telegram message suitable for quick mobile reading with clear bullets."
            "notion" -> "Write a clean Notion database page title and structured section notes / action items."
            "slack" -> "Write a concise Slack channel update with bold section lead-ins and bullet points."
            "discord" -> "Write a friendly, well-structured Discord embed/update message."
            else -> "Write a clean structured payload title and concise summary body for webhook automation."
        }

        val prompt = """
            Draft a ready-to-send $channelName message with a $tone tone based on the source content below.
            $formatGuidance
            ${if (extraInstruction.isNotBlank()) "Additional user instruction: $extraInstruction" else ""}
            
            Source Title: $sourceTitle
            Source Content:
            $sourceBody
            
            Return ONLY valid JSON:
            {
              "subject": "Email subject / Notion title / Message headline",
              "body": "Full ready-to-send message body",
              "tags": "comma,separated,tags"
            }
        """.trimIndent()

        val start = System.currentTimeMillis()
        val res = generateText(
            provider = provider,
            apiKey = key,
            model = model,
            systemPrompt = "You are Voice Task Studio AI drafting clear, ready-to-send communications for external connectors.",
            messages = listOf(true to prompt),
            expectJson = true
        )
        val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)

        when (res) {
            is AiResult.Error -> res
            is AiResult.Success -> {
                val obj = extractJsonObject(res.data)
                val draft = if (obj != null) {
                    GeneratedConnectorDraft(
                        subject = obj.optString("subject", sourceTitle.ifBlank { "Voice Task Update" }),
                        body = obj.optString("body", res.data).trim(),
                        tags = obj.optString("tags", "")
                    )
                } else {
                    GeneratedConnectorDraft(
                        subject = sourceTitle.ifBlank { "Voice Task Update" },
                        body = res.data.trim(),
                        tags = ""
                    )
                }
                AiResult.Success(draft, "Draft ready ($channelName)", latency)
            }
        }
    }

    suspend fun analyzeFinanceQuery(
        config: AiStudioConfig,
        question: String,
        periodLabel: String,
        ledgerSnapshot: String,
        history: List<Pair<Boolean, String>> = emptyList()
    ): AiResult<String> = withContext(Dispatchers.IO) {
        val key = config.activeApiKey.trim()
        val provider = config.activeProvider
        val model = config.activeModel.trim()
        if (key.isBlank()) {
            return@withContext AiResult.Error("Add your ${provider.displayName} API key in Settings -> AI for generative insights.")
        }

        val systemPrompt = """
            You are Voice Task Financial Analyst. You analyze the user's parsed bank & mobile-money SMS transactions (Telebirr, CBE, Bank of Abyssinia, Awash, Dashen, Nib, Coop, M-Pesa, etc.).
            Ground every number, bank balance, merchant name, date, and time strictly in the verified Financial Ledger Snapshot provided below. Never fabricate amounts or transactions.
            If asked about a specific time, day, or month, highlight the exact matching transactions, total income, total outflow, service charges, net flow, and key observations.
            Keep your answer well-structured, concise, and practical with clear bullet points (no markdown tables).

            Target Scope: $periodLabel

            Verified Financial Ledger Snapshot:
            $ledgerSnapshot
        """.trimIndent()

        val msgs = (history.takeLast(6) + (true to question))
        val start = System.currentTimeMillis()
        val res = generateText(
            provider = provider,
            apiKey = key,
            model = model,
            systemPrompt = systemPrompt,
            messages = msgs,
            expectJson = false
        )
        val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)
        when (res) {
            is AiResult.Error -> res
            is AiResult.Success -> AiResult.Success(res.data.trim(), "Analyzed via ${provider.displayName}", latency)
        }
    }

    suspend fun generateFinanceReport(
        config: AiStudioConfig,
        periodLabel: String,
        ledgerSnapshot: String
    ): AiResult<String> = analyzeFinanceQuery(
        config = config,
        question = "Provide a clear financial health analysis for $periodLabel: summarize cashflow, bank balances across Telebirr/CBE/Abyssinia/other banks, top spending categories, service fees paid, unusual spikes or time-of-day habits, and 3 practical savings tips.",
        periodLabel = periodLabel,
        ledgerSnapshot = ledgerSnapshot
    )

    private fun generateText(
        provider: AiProvider,
        apiKey: String,
        model: String,
        systemPrompt: String,
        messages: List<Pair<Boolean, String>>,
        expectJson: Boolean
    ): AiResult<String> {
        return when (provider) {
            AiProvider.GEMINI -> callGeminiGenerateContent(apiKey, model, systemPrompt, messages, expectJson)
            AiProvider.OPENAI -> callOpenAiChatCompletions(apiKey, model, systemPrompt, messages, expectJson)
            AiProvider.CLAUDE -> callClaudeMessages(apiKey, model, systemPrompt, messages)
        }
    }

    private fun callGeminiGenerateContent(
        apiKey: String,
        model: String,
        systemPrompt: String,
        messages: List<Pair<Boolean, String>>,
        expectJson: Boolean
    ): AiResult<String> {
        return try {
            val encodedModel = URLEncoder.encode(model, "UTF-8")
            val encodedKey = URLEncoder.encode(apiKey, "UTF-8")
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$encodedModel:generateContent?key=$encodedKey"

            val contentsArray = JSONArray()
            messages.forEach { (isUser, text) ->
                contentsArray.put(
                    JSONObject().apply {
                        put("role", if (isUser) "user" else "model")
                        put("parts", JSONArray().put(JSONObject().put("text", text)))
                    }
                )
            }

            val payload = JSONObject().apply {
                if (systemPrompt.isNotBlank()) {
                    put(
                        "systemInstruction",
                        JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                    )
                }
                put("contents", contentsArray)
                put(
                    "generationConfig",
                    JSONObject().apply {
                        put("temperature", 0.35)
                        if (expectJson) {
                            put("responseMimeType", "application/json")
                        }
                    }
                )
            }

            val req = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return AiResult.Error(parseApiErrorMessage(raw, "Gemini HTTP ${resp.code}"))
                }
                val json = JSONObject(raw)
                val candidates = json.optJSONArray("candidates")
                val parts = candidates?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                val out = buildString {
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val t = parts.optJSONObject(i)?.optString("text", "").orEmpty()
                            if (t.isNotEmpty()) append(t)
                        }
                    }
                }.trim()
                if (out.isBlank()) {
                    AiResult.Error("Gemini returned an empty response.")
                } else {
                    AiResult.Success(out)
                }
            }
        } catch (e: Exception) {
            AiResult.Error("Gemini request failed: ${e.localizedMessage ?: "Network error"}")
        }
    }

    private fun transcribeAndStructureWithGemini(
        apiKey: String,
        model: String,
        audioFile: File
    ): AiResult<ExtractedVoiceTask> {
        return try {
            val encodedModel = URLEncoder.encode(model, "UTF-8")
            val encodedKey = URLEncoder.encode(apiKey, "UTF-8")
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$encodedModel:generateContent?key=$encodedKey"

            val bytes = audioFile.readBytes()
            val base64Audio = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val ext = audioFile.extension.lowercase(Locale.getDefault())
            val mimeType = when (ext) {
                "3gp", "3gpp" -> "audio/3gpp"
                "wav" -> "audio/wav"
                "mp3" -> "audio/mpeg"
                "ogg" -> "audio/ogg"
                else -> "audio/mp4"
            }

            val promptText = """
                Listen carefully to this audio recording, transcribe it faithfully, and structure it into tasks and a summary.
                Return ONLY valid JSON matching:
                {
                  "transcript": "full accurate transcript",
                  "title": "concise 3-7 word memo title",
                  "summary": "2-3 sentence executive summary",
                  "suggestedFolder": "Inbox | Work | Personal | Ideas | Quick Memo",
                  "tags": "tag1, tag2",
                  "actionItems": ["concrete task 1", "concrete task 2"]
                }
            """.trimIndent()

            val parts = JSONArray().apply {
                put(
                    JSONObject().put(
                        "inline_data",
                        JSONObject().apply {
                            put("mime_type", mimeType)
                            put("data", base64Audio)
                        }
                    )
                )
                put(JSONObject().put("text", promptText))
            }

            val payload = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("parts", parts)
                }))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.2)
                    put("responseMimeType", "application/json")
                })
            }

            val req = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return AiResult.Error(parseApiErrorMessage(raw, "Gemini Audio HTTP ${resp.code}"))
                }
                val json = JSONObject(raw)
                val outParts = json.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                val text = outParts?.optJSONObject(0)?.optString("text", "").orEmpty().trim()
                if (text.isBlank()) {
                    return AiResult.Error("Gemini did not return transcription output.")
                }
                AiResult.Success(parseExtractedTaskJson(text, fallbackTranscript = text))
            }
        } catch (e: Exception) {
            AiResult.Error("Gemini audio error: ${e.localizedMessage ?: "Network error"}")
        }
    }

    private fun callOpenAiChatCompletions(
        apiKey: String,
        model: String,
        systemPrompt: String,
        messages: List<Pair<Boolean, String>>,
        expectJson: Boolean
    ): AiResult<String> {
        return try {
            val msgsArray = JSONArray()
            if (systemPrompt.isNotBlank()) {
                msgsArray.put(JSONObject().put("role", "system").put("content", systemPrompt))
            }
            messages.forEach { (isUser, text) ->
                msgsArray.put(JSONObject().put("role", if (isUser) "user" else "assistant").put("content", text))
            }

            val payload = JSONObject().apply {
                put("model", model)
                put("messages", msgsArray)
                put("temperature", 0.35)
                if (expectJson) {
                    put("response_format", JSONObject().put("type", "json_object"))
                }
            }

            val req = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return AiResult.Error(parseApiErrorMessage(raw, "OpenAI HTTP ${resp.code}"))
                }
                val content = JSONObject(raw)
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content", "")
                    .orEmpty()
                    .trim()
                if (content.isBlank()) {
                    AiResult.Error("OpenAI returned an empty message.")
                } else {
                    AiResult.Success(content)
                }
            }
        } catch (e: Exception) {
            AiResult.Error("OpenAI request failed: ${e.localizedMessage ?: "Network error"}")
        }
    }

    private fun transcribeWithOpenAiWhisper(apiKey: String, audioFile: File): AiResult<String> {
        return try {
            val ext = audioFile.extension.lowercase(Locale.getDefault())
            val uploadExt = if (ext == "mp4") "m4a" else ext
            val mime = if (ext == "3gp") "audio/3gpp" else "audio/mp4"
            val uploadName = "${audioFile.nameWithoutExtension}.$uploadExt"

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", "whisper-1")
                .addFormDataPart("file", uploadName, audioFile.asRequestBody(mime.toMediaType()))
                .build()

            val req = Request.Builder()
                .url("https://api.openai.com/v1/audio/transcriptions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(body)
                .build()

            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return AiResult.Error(parseApiErrorMessage(raw, "Whisper HTTP ${resp.code}"))
                }
                val text = JSONObject(raw).optString("text", "").trim()
                if (text.isBlank()) {
                    AiResult.Error("Whisper returned an empty transcript.")
                } else {
                    AiResult.Success(text)
                }
            }
        } catch (e: Exception) {
            AiResult.Error("Whisper transcription failed: ${e.localizedMessage ?: "Network error"}")
        }
    }

    private fun callClaudeMessages(
        apiKey: String,
        model: String,
        systemPrompt: String,
        messages: List<Pair<Boolean, String>>
    ): AiResult<String> {
        return try {
            val msgsArray = JSONArray()
            messages.forEach { (isUser, text) ->
                msgsArray.put(JSONObject().put("role", if (isUser) "user" else "assistant").put("content", text))
            }

            val payload = JSONObject().apply {
                put("model", model)
                put("max_tokens", 1600)
                if (systemPrompt.isNotBlank()) {
                    put("system", systemPrompt)
                }
                put("messages", msgsArray)
            }

            val req = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return AiResult.Error(parseApiErrorMessage(raw, "Claude HTTP ${resp.code}"))
                }
                val contentArr = JSONObject(raw).optJSONArray("content")
                val text = buildString {
                    if (contentArr != null) {
                        for (i in 0 until contentArr.length()) {
                            val block = contentArr.optJSONObject(i)
                            if (block?.optString("type") == "text") {
                                append(block.optString("text", ""))
                            }
                        }
                    }
                }.trim()
                if (text.isBlank()) {
                    AiResult.Error("Claude returned an empty response.")
                } else {
                    AiResult.Success(text)
                }
            }
        } catch (e: Exception) {
            AiResult.Error("Claude request failed: ${e.localizedMessage ?: "Network error"}")
        }
    }

    private fun parseChatJsonResponse(rawText: String): AiChatMessage {
        val obj = extractJsonObject(rawText)
        if (obj == null) {
            return AiChatMessage(isUser = false, text = rawText.trim())
        }
        val reply = obj.optString("reply", "").ifBlank { rawText.trim() }
        val actionsArr = obj.optJSONArray("actions")
        val actions = mutableListOf<AiSuggestedAction>()
        if (actionsArr != null) {
            for (i in 0 until actionsArr.length()) {
                val item = actionsArr.optJSONObject(i) ?: continue
                val type = item.optString("type", "").trim().uppercase(Locale.getDefault())
                val title = item.optString("title", "").trim()
                val body = item.optString("body", "").trim()
                if (type.isNotBlank() && (title.isNotBlank() || body.isNotBlank())) {
                    actions.add(
                        AiSuggestedAction(
                            type = type,
                            label = item.optString("label", type.replace("_", " ")).trim(),
                            title = title.ifBlank { "Voice Task Item" },
                            body = body,
                            tags = item.optString("tags", "").trim(),
                            folder = item.optString("folder", "Inbox").trim().ifBlank { "Inbox" }
                        )
                    )
                }
            }
        }
        return AiChatMessage(
            isUser = false,
            text = reply,
            suggestedActions = actions
        )
    }

    private fun parseExtractedTaskJson(rawText: String, fallbackTranscript: String): ExtractedVoiceTask {
        val obj = extractJsonObject(rawText) ?: return ExtractedVoiceTask(
            transcript = fallbackTranscript,
            title = fallbackTranscript.take(48).ifBlank { "Transcribed Voice Memo" },
            summary = fallbackTranscript,
            suggestedFolder = "Inbox",
            tags = "Voice, AI",
            actionItems = emptyList()
        )
        val itemsArr = obj.optJSONArray("actionItems")
        val actionItems = mutableListOf<String>()
        if (itemsArr != null) {
            for (i in 0 until itemsArr.length()) {
                val item = itemsArr.optString(i, "").trim()
                if (item.isNotBlank()) actionItems.add(item)
            }
        }
        val transcript = obj.optString("transcript", fallbackTranscript).ifBlank { fallbackTranscript }
        val title = obj.optString("title", "").ifBlank { transcript.take(48).ifBlank { "Transcribed Voice Memo" } }
        val summary = obj.optString("summary", "").ifBlank { transcript }
        val folder = obj.optString("suggestedFolder", "Inbox").ifBlank { "Inbox" }
        val tags = obj.optString("tags", "Voice, AI")
        return ExtractedVoiceTask(
            transcript = transcript,
            title = title,
            summary = summary,
            suggestedFolder = folder,
            tags = tags,
            actionItems = actionItems
        )
    }

    private fun extractJsonObject(rawText: String): JSONObject? {
        val cleaned = rawText.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        runCatching { return JSONObject(cleaned) }
        val firstBrace = cleaned.indexOf('{')
        val lastBrace = cleaned.lastIndexOf('}')
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            runCatching { return JSONObject(cleaned.substring(firstBrace, lastBrace + 1)) }
        }
        return null
    }

    private fun parseApiErrorMessage(raw: String, fallback: String): String {
        return runCatching {
            val obj = JSONObject(raw)
            val errObj = obj.optJSONObject("error")
            val msg = errObj?.optString("message")?.takeIf { it.isNotBlank() }
                ?: obj.optString("message").takeIf { it.isNotBlank() }
            msg ?: fallback
        }.getOrDefault(fallback)
    }
}
