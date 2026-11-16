package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class NotionDatabaseInfo(
    val id: String,
    val title: String,
    val titleProperty: String
)

sealed class ConnectorResult<out T> {
    data class Success<T>(val data: T, val message: String = "") : ConnectorResult<T>()
    data class Error(val reason: String) : ConnectorResult<Nothing>()
}

object ExternalConnectorManager {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private const val NOTION_VERSION = "2022-06-28"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    suspend fun discoverNotionDatabases(token: String): ConnectorResult<List<NotionDatabaseInfo>> =
        withContext(Dispatchers.IO) {
            val cleanToken = token.trim()
            if (cleanToken.isBlank()) {
                return@withContext ConnectorResult.Error("Enter your Notion integration token first.")
            }
            try {
                val payload = JSONObject().apply {
                    put("filter", JSONObject().apply {
                        put("value", "database")
                        put("property", "object")
                    })
                    put("page_size", 25)
                }

                val req = Request.Builder()
                    .url("https://api.notion.com/v1/search")
                    .addHeader("Authorization", "Bearer $cleanToken")
                    .addHeader("Notion-Version", NOTION_VERSION)
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .build()

                client.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        val errMsg = parseJsonMessage(raw, "Notion HTTP ${resp.code}")
                        return@withContext ConnectorResult.Error(errMsg)
                    }
                    val root = JSONObject(raw)
                    val results = root.optJSONArray("results") ?: JSONArray()
                    val list = mutableListOf<NotionDatabaseInfo>()
                    for (i in 0 until results.length()) {
                        val dbObj = results.optJSONObject(i) ?: continue
                        val dbId = dbObj.optString("id", "")
                        if (dbId.isBlank()) continue
                        val titleArr = dbObj.optJSONArray("title")
                        val dbTitle = buildString {
                            if (titleArr != null) {
                                for (j in 0 until titleArr.length()) {
                                    append(titleArr.optJSONObject(j)?.optString("plain_text", "").orEmpty())
                                }
                            }
                        }.trim().ifBlank { "Untitled Database" }

                        val titleProp = extractTitlePropertyName(dbObj.optJSONObject("properties"))
                        list.add(
                            NotionDatabaseInfo(
                                id = dbId,
                                title = dbTitle,
                                titleProperty = titleProp
                            )
                        )
                    }
                    if (list.isEmpty()) {
                        ConnectorResult.Error("No shared databases found. Share at least one database with your Notion integration.")
                    } else {
                        ConnectorResult.Success(list, "Found ${list.size} Notion database(s)")
                    }
                }
            } catch (e: Exception) {
                ConnectorResult.Error("Notion discovery failed: ${e.localizedMessage ?: "Network error"}")
            }
        }

    suspend fun getDatabaseTitleProperty(token: String, databaseId: String): ConnectorResult<String> =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("https://api.notion.com/v1/databases/${databaseId.trim()}")
                    .addHeader("Authorization", "Bearer ${token.trim()}")
                    .addHeader("Notion-Version", NOTION_VERSION)
                    .get()
                    .build()

                client.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        return@withContext ConnectorResult.Error(parseJsonMessage(raw, "Notion HTTP ${resp.code}"))
                    }
                    val propName = extractTitlePropertyName(JSONObject(raw).optJSONObject("properties"))
                    ConnectorResult.Success(propName)
                }
            } catch (e: Exception) {
                ConnectorResult.Error(e.localizedMessage ?: "Failed to inspect Notion schema")
            }
        }

    suspend fun createNotionPage(
        context: Context,
        config: AiStudioConfig,
        title: String,
        body: String,
        category: String = "",
        tags: String = ""
    ): ConnectorResult<String> = withContext(Dispatchers.IO) {
        val token = config.notionToken.trim()
        val dbId = config.notionDatabaseId.trim()
        val titleProp = config.notionTitleProperty.trim().ifBlank { "Name" }

        if (token.isBlank() || dbId.isBlank()) {
            return@withContext ConnectorResult.Error("Configure your Notion token and select a database in Settings.")
        }

        val dao = AppDatabase.getDatabase(context).voiceTaskDao()
        val cleanTitle = title.trim().ifBlank { "Finance & Task Note" }.take(200)

        try {
            val children = JSONArray()
            if (category.isNotBlank() || tags.isNotBlank()) {
                val metaText = buildString {
                    if (category.isNotBlank()) append("Folder: $category")
                    if (category.isNotBlank() && tags.isNotBlank()) append("  •  ")
                    if (tags.isNotBlank()) append("Tags: $tags")
                }
                children.put(buildNotionParagraphBlock(metaText))
            }

            val paragraphs = body.trim()
                .split(Regex("\\n{2,}"))
                .map { it.trim() }
                .filter { it.isNotBlank() }

            if (paragraphs.isEmpty()) {
                children.put(buildNotionParagraphBlock("Captured via Finance & Task Studio AI."))
            } else {
                paragraphs.take(25).forEach { para ->
                    para.chunked(1800).forEach { chunk ->
                        children.put(buildNotionParagraphBlock(chunk))
                    }
                }
            }

            val payload = JSONObject().apply {
                put("parent", JSONObject().put("database_id", dbId))
                put(
                    "properties",
                    JSONObject().put(
                        titleProp,
                        JSONObject().put(
                            "title",
                            JSONArray().put(
                                JSONObject().put("text", JSONObject().put("content", cleanTitle))
                            )
                        )
                    )
                )
                put("children", children)
            }

            val req = Request.Builder()
                .url("https://api.notion.com/v1/pages")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Notion-Version", NOTION_VERSION)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    val pageUrl = runCatching { JSONObject(raw).optString("url", "") }.getOrDefault("")
                    dao.insertConnectorLog(
                        ConnectorDispatchLog(
                            channel = "NOTION",
                            title = cleanTitle,
                            detail = "Saved to ${config.notionDatabaseTitle.ifBlank { "Notion DB" }}",
                            status = "SENT"
                        )
                    )
                    ConnectorResult.Success(pageUrl, "Saved \"$cleanTitle\" to Notion!")
                } else {
                    val err = parseJsonMessage(raw, "Notion HTTP ${resp.code}")
                    dao.insertConnectorLog(
                        ConnectorDispatchLog(
                            channel = "NOTION",
                            title = cleanTitle,
                            detail = err,
                            status = "FAILED"
                        )
                    )
                    ConnectorResult.Error("Notion error: $err")
                }
            }
        } catch (e: Exception) {
            val err = e.localizedMessage ?: "Network error"
            dao.insertConnectorLog(
                ConnectorDispatchLog(
                    channel = "NOTION",
                    title = cleanTitle,
                    detail = err,
                    status = "FAILED"
                )
            )
            ConnectorResult.Error("Notion request failed: $err")
        }
    }

    suspend fun sendViaResendApi(
        context: Context,
        config: AiStudioConfig,
        toEmail: String,
        subject: String,
        body: String
    ): ConnectorResult<String> = withContext(Dispatchers.IO) {
        val apiKey = config.resendApiKey.trim()
        val fromAddr = config.emailFromAddress.trim()
        val targetEmail = toEmail.trim().ifBlank { config.emailDefaultRecipient.trim() }

        if (apiKey.isBlank() || fromAddr.isBlank()) {
            return@withContext ConnectorResult.Error("Add Resend API key & From address in Settings, or switch Email to Mail App Draft.")
        }
        if (targetEmail.isBlank()) {
            return@withContext ConnectorResult.Error("Enter a recipient email address.")
        }

        val dao = AppDatabase.getDatabase(context).voiceTaskDao()
        val cleanSubject = subject.trim().ifBlank { "Finance & Task Update" }

        try {
            val payload = JSONObject().apply {
                put("from", fromAddr)
                put("to", JSONArray().put(targetEmail))
                put("subject", cleanSubject)
                put("text", body)
            }

            val req = Request.Builder()
                .url("https://api.resend.com/emails")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    val id = runCatching { JSONObject(raw).optString("id", "ok") }.getOrDefault("ok")
                    dao.insertConnectorLog(
                        ConnectorDispatchLog(
                            channel = "EMAIL",
                            title = cleanSubject,
                            detail = "Sent via Resend API to $targetEmail",
                            status = "SENT"
                        )
                    )
                    ConnectorResult.Success(id, "Email sent to $targetEmail!")
                } else {
                    val err = parseJsonMessage(raw, "Resend HTTP ${resp.code}")
                    dao.insertConnectorLog(
                        ConnectorDispatchLog(
                            channel = "EMAIL",
                            title = cleanSubject,
                            detail = err,
                            status = "FAILED"
                        )
                    )
                    ConnectorResult.Error("Email delivery error: $err")
                }
            }
        } catch (e: Exception) {
            val err = e.localizedMessage ?: "Network error"
            dao.insertConnectorLog(
                ConnectorDispatchLog(
                    channel = "EMAIL",
                    title = cleanSubject,
                    detail = err,
                    status = "FAILED"
                )
            )
            ConnectorResult.Error("Email failed: $err")
        }
    }

    suspend fun launchMailComposer(
        context: Context,
        toEmail: String,
        subject: String,
        body: String
    ): ConnectorResult<String> {
        val dao = AppDatabase.getDatabase(context).voiceTaskDao()
        val cleanSubject = subject.trim().ifBlank { "Finance & Task Note" }
        val cleanRecipient = toEmail.trim()

        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                if (cleanRecipient.isNotBlank()) {
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(cleanRecipient))
                }
                putExtra(Intent.EXTRA_SUBJECT, cleanSubject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            withContext(Dispatchers.IO) {
                dao.insertConnectorLog(
                    ConnectorDispatchLog(
                        channel = "EMAIL",
                        title = cleanSubject,
                        detail = if (cleanRecipient.isNotBlank()) "Opened Mail draft for $cleanRecipient" else "Opened Mail composer draft",
                        status = "DRAFTED"
                    )
                )
            }
            ConnectorResult.Success(cleanSubject, "Opened Mail draft!")
        } catch (e: Exception) {
            ConnectorResult.Error("No email app available on this device to compose mail.")
        }
    }

    suspend fun dispatchWebhook(
        context: Context,
        webhook: CustomWebhookConnector,
        title: String,
        content: String,
        tags: String = ""
    ): ConnectorResult<String> = withContext(Dispatchers.IO) {
        val url = webhook.url.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return@withContext ConnectorResult.Error("Webhook URL must start with https:// or http://")
        }

        val dao = AppDatabase.getDatabase(context).voiceTaskDao()
        val cleanTitle = title.trim().ifBlank { "Finance & Task Dispatch" }

        try {
            val payload = when (webhook.presetType) {
                WebhookPresetType.SLACK -> {
                    val text = buildString {
                        append("*").append(cleanTitle).append("*\n")
                        append(content.trim())
                        if (tags.isNotBlank()) append("\n_Tags: ").append(tags).append("_")
                    }
                    JSONObject().put("text", text)
                }
                WebhookPresetType.DISCORD -> {
                    val text = buildString {
                        append("**").append(cleanTitle).append("**\n")
                        append(content.trim())
                        if (tags.isNotBlank()) append("\n*Tags: ").append(tags).append("*")
                    }.take(1950)
                    JSONObject().put("content", text)
                }
                WebhookPresetType.WEBHOOK -> {
                    JSONObject().apply {
                        put("source", "Finance & Task Studio")
                        put("title", cleanTitle)
                        put("content", content.trim())
                        put("tags", tags)
                        put("timestamp", System.currentTimeMillis())
                    }
                }
            }

            val reqBuilder = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
            if (webhook.authHeader.isNotBlank()) {
                reqBuilder.addHeader("Authorization", webhook.authHeader.trim())
            }

            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    dao.insertConnectorLog(
                        ConnectorDispatchLog(
                            channel = webhook.presetType.name,
                            title = cleanTitle,
                            detail = "Delivered to ${webhook.name}",
                            status = "SENT"
                        )
                    )
                    ConnectorResult.Success("ok", "Delivered to ${webhook.name}!")
                } else {
                    val errBody = resp.body?.string().orEmpty()
                    val err = parseJsonMessage(errBody, "HTTP ${resp.code}")
                    dao.insertConnectorLog(
                        ConnectorDispatchLog(
                            channel = webhook.presetType.name,
                            title = cleanTitle,
                            detail = "${webhook.name}: $err",
                            status = "FAILED"
                        )
                    )
                    ConnectorResult.Error("${webhook.name} rejected request ($err)")
                }
            }
        } catch (e: Exception) {
            val err = e.localizedMessage ?: "Network error"
            dao.insertConnectorLog(
                ConnectorDispatchLog(
                    channel = webhook.presetType.name,
                    title = cleanTitle,
                    detail = "${webhook.name}: $err",
                    status = "FAILED"
                )
            )
            ConnectorResult.Error("Webhook error (${webhook.name}): $err")
        }
    }

    private fun extractTitlePropertyName(propertiesObj: JSONObject?): String {
        if (propertiesObj == null) return "Name"
        val keys = propertiesObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val prop = propertiesObj.optJSONObject(key)
            if (prop?.optString("type") == "title") {
                return key
            }
        }
        return "Name"
    }

    private fun buildNotionParagraphBlock(text: String): JSONObject {
        return JSONObject().apply {
            put("object", "block")
            put("type", "paragraph")
            put(
                "paragraph",
                JSONObject().put(
                    "rich_text",
                    JSONArray().put(
                        JSONObject().apply {
                            put("type", "text")
                            put("text", JSONObject().put("content", text))
                        }
                    )
                )
            )
        }
    }

    private fun parseJsonMessage(raw: String, fallback: String): String {
        return runCatching {
            val obj = JSONObject(raw)
            obj.optString("message").takeIf { it.isNotBlank() }
                ?: obj.optString("error").takeIf { it.isNotBlank() }
                ?: fallback
        }.getOrDefault(fallback)
    }
}
