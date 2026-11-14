package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.aiConnectorDataStore: DataStore<Preferences> by preferencesDataStore(name = "ai_connector_vault_prefs")

enum class AiProvider(
    val displayName: String,
    val defaultModel: String,
    val modelPresets: List<String>,
    val keyHint: String
) {
    GEMINI(
        displayName = "Google Gemini",
        defaultModel = "gemini-3.8-flash",
        modelPresets = listOf("gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-4-argon"),
        keyHint = "AIza..."
    ),
    OPENAI(
        displayName = "OpenAI",
        defaultModel = "gpt-4o-mini",
        modelPresets = listOf("gpt-4o-mini", "gpt-4o", "gpt-4.1-mini"),
        keyHint = "sk-..."
    ),
    CLAUDE(
        displayName = "Anthropic Claude",
        defaultModel = "claude-sonnet-4-5",
        modelPresets = listOf("claude-sonnet-4-5", "claude-3-5-haiku-latest", "claude-opus-4-1"),
        keyHint = "sk-ant-..."
    )
}

enum class EmailDeliveryMode(val label: String) {
    MAIL_APP_DRAFT("Mail App Draft"),
    RESEND_API("Resend HTTPS API")
}

enum class WebhookPresetType(val label: String) {
    SLACK("Slack"),
    DISCORD("Discord"),
    WEBHOOK("Custom JSON Webhook")
}

data class CustomWebhookConnector(
    val id: String = UUID.randomUUID().toString().take(8),
    val name: String,
    val presetType: WebhookPresetType = WebhookPresetType.WEBHOOK,
    val url: String,
    val authHeader: String = "",
    val isEnabled: Boolean = true
)

data class AiStudioConfig(
    val activeProvider: AiProvider = AiProvider.GEMINI,
    val geminiApiKey: String = "",
    val geminiModel: String = AiProvider.GEMINI.defaultModel,
    val geminiVerifiedAt: Long? = null,
    val openAiApiKey: String = "",
    val openAiModel: String = AiProvider.OPENAI.defaultModel,
    val openAiVerifiedAt: Long? = null,
    val claudeApiKey: String = "",
    val claudeModel: String = AiProvider.CLAUDE.defaultModel,
    val claudeVerifiedAt: Long? = null,
    // Email connector
    val emailDeliveryMode: EmailDeliveryMode = EmailDeliveryMode.MAIL_APP_DRAFT,
    val resendApiKey: String = "",
    val emailFromAddress: String = "",
    val emailDefaultRecipient: String = "",
    // Notion connector
    val notionToken: String = "",
    val notionDatabaseId: String = "",
    val notionDatabaseTitle: String = "",
    val notionTitleProperty: String = "Name",
    // Slack / Discord / Custom Webhooks
    val customWebhooks: List<CustomWebhookConnector> = emptyList()
) {
    fun apiKeyFor(provider: AiProvider): String = when (provider) {
        AiProvider.GEMINI -> geminiApiKey
        AiProvider.OPENAI -> openAiApiKey
        AiProvider.CLAUDE -> claudeApiKey
    }

    fun modelFor(provider: AiProvider): String = when (provider) {
        AiProvider.GEMINI -> geminiModel.ifBlank { provider.defaultModel }
        AiProvider.OPENAI -> openAiModel.ifBlank { provider.defaultModel }
        AiProvider.CLAUDE -> claudeModel.ifBlank { provider.defaultModel }
    }

    fun verifiedAtFor(provider: AiProvider): Long? = when (provider) {
        AiProvider.GEMINI -> geminiVerifiedAt
        AiProvider.OPENAI -> openAiVerifiedAt
        AiProvider.CLAUDE -> claudeVerifiedAt
    }

    val activeApiKey: String get() = apiKeyFor(activeProvider)
    val activeModel: String get() = modelFor(activeProvider)
    val isAiConfigured: Boolean get() = activeApiKey.isNotBlank()
    val isNotionConfigured: Boolean get() = notionToken.isNotBlank() && notionDatabaseId.isNotBlank()
    val isResendConfigured: Boolean get() = resendApiKey.isNotBlank() && emailFromAddress.isNotBlank()
}

data class StudioPreferences(
    val themeOption: String = "Light",
    val audioQuality: String = "High",
    val enableVibration: Boolean = true,
    val autoPlayAfterRecording: Boolean = false
)

class AiConnectorPrefs(private val context: Context) {

    private object Keys {
        val ACTIVE_PROVIDER = stringPreferencesKey("ai_active_provider")
        val GEMINI_KEY = stringPreferencesKey("ai_gemini_key")
        val GEMINI_MODEL = stringPreferencesKey("ai_gemini_model")
        val GEMINI_VERIFIED_AT = longPreferencesKey("ai_gemini_verified_at")
        val OPENAI_KEY = stringPreferencesKey("ai_openai_key")
        val OPENAI_MODEL = stringPreferencesKey("ai_openai_model")
        val OPENAI_VERIFIED_AT = longPreferencesKey("ai_openai_verified_at")
        val CLAUDE_KEY = stringPreferencesKey("ai_claude_key")
        val CLAUDE_MODEL = stringPreferencesKey("ai_claude_model")
        val CLAUDE_VERIFIED_AT = longPreferencesKey("ai_claude_verified_at")

        val EMAIL_MODE = stringPreferencesKey("conn_email_mode")
        val RESEND_KEY = stringPreferencesKey("conn_resend_key")
        val EMAIL_FROM = stringPreferencesKey("conn_email_from")
        val EMAIL_TO = stringPreferencesKey("conn_email_to")

        val NOTION_TOKEN = stringPreferencesKey("conn_notion_token")
        val NOTION_DB_ID = stringPreferencesKey("conn_notion_db_id")
        val NOTION_DB_TITLE = stringPreferencesKey("conn_notion_db_title")
        val NOTION_TITLE_PROP = stringPreferencesKey("conn_notion_title_prop")

        val WEBHOOKS_JSON = stringPreferencesKey("conn_custom_webhooks_json")

        val TASK_SORT_OPTION = stringPreferencesKey("ui_task_sort_option")
        val TASK_SORT_DESCENDING = booleanPreferencesKey("ui_task_sort_descending")

        val STUDIO_THEME = stringPreferencesKey("studio_theme_option")
        val STUDIO_QUALITY = stringPreferencesKey("studio_audio_quality")
        val STUDIO_VIBRATION = booleanPreferencesKey("studio_enable_vibration")
        val STUDIO_AUTO_PLAY = booleanPreferencesKey("studio_auto_play_after_rec")
    }

    val configFlow: Flow<AiStudioConfig> = context.aiConnectorDataStore.data.map { prefs ->
        val provider = runCatching {
            AiProvider.valueOf(prefs[Keys.ACTIVE_PROVIDER] ?: AiProvider.GEMINI.name)
        }.getOrDefault(AiProvider.GEMINI)

        val emailMode = runCatching {
            EmailDeliveryMode.valueOf(prefs[Keys.EMAIL_MODE] ?: EmailDeliveryMode.MAIL_APP_DRAFT.name)
        }.getOrDefault(EmailDeliveryMode.MAIL_APP_DRAFT)

        AiStudioConfig(
            activeProvider = provider,
            geminiApiKey = prefs[Keys.GEMINI_KEY] ?: "",
            geminiModel = prefs[Keys.GEMINI_MODEL] ?: AiProvider.GEMINI.defaultModel,
            geminiVerifiedAt = prefs[Keys.GEMINI_VERIFIED_AT],
            openAiApiKey = prefs[Keys.OPENAI_KEY] ?: "",
            openAiModel = prefs[Keys.OPENAI_MODEL] ?: AiProvider.OPENAI.defaultModel,
            openAiVerifiedAt = prefs[Keys.OPENAI_VERIFIED_AT],
            claudeApiKey = prefs[Keys.CLAUDE_KEY] ?: "",
            claudeModel = prefs[Keys.CLAUDE_MODEL] ?: AiProvider.CLAUDE.defaultModel,
            claudeVerifiedAt = prefs[Keys.CLAUDE_VERIFIED_AT],
            emailDeliveryMode = emailMode,
            resendApiKey = prefs[Keys.RESEND_KEY] ?: "",
            emailFromAddress = prefs[Keys.EMAIL_FROM] ?: "",
            emailDefaultRecipient = prefs[Keys.EMAIL_TO] ?: "",
            notionToken = prefs[Keys.NOTION_TOKEN] ?: "",
            notionDatabaseId = prefs[Keys.NOTION_DB_ID] ?: "",
            notionDatabaseTitle = prefs[Keys.NOTION_DB_TITLE] ?: "",
            notionTitleProperty = prefs[Keys.NOTION_TITLE_PROP] ?: "Name",
            customWebhooks = decodeWebhooks(prefs[Keys.WEBHOOKS_JSON])
        )
    }

    val taskSortPreferenceFlow: Flow<Pair<TaskSortOption, Boolean>> = context.aiConnectorDataStore.data.map { prefs ->
        val option = runCatching {
            TaskSortOption.valueOf(prefs[Keys.TASK_SORT_OPTION] ?: TaskSortOption.CHRONOLOGICAL.name)
        }.getOrDefault(TaskSortOption.CHRONOLOGICAL)
        val descending = prefs[Keys.TASK_SORT_DESCENDING] ?: option.defaultDescending
        option to descending
    }

    suspend fun saveTaskSortPreference(option: TaskSortOption, descending: Boolean) {
        context.aiConnectorDataStore.edit { prefs ->
            prefs[Keys.TASK_SORT_OPTION] = option.name
            prefs[Keys.TASK_SORT_DESCENDING] = descending
        }
    }

    val studioPreferencesFlow: Flow<StudioPreferences> = context.aiConnectorDataStore.data.map { prefs ->
        StudioPreferences(
            themeOption = prefs[Keys.STUDIO_THEME] ?: "Light",
            audioQuality = prefs[Keys.STUDIO_QUALITY] ?: "High",
            enableVibration = prefs[Keys.STUDIO_VIBRATION] ?: true,
            autoPlayAfterRecording = prefs[Keys.STUDIO_AUTO_PLAY] ?: false
        )
    }

    suspend fun saveThemeOption(theme: String) {
        context.aiConnectorDataStore.edit { prefs ->
            prefs[Keys.STUDIO_THEME] = theme
        }
    }

    suspend fun saveAudioQuality(quality: String) {
        context.aiConnectorDataStore.edit { prefs ->
            prefs[Keys.STUDIO_QUALITY] = quality
        }
    }

    suspend fun saveVibrationEnabled(enabled: Boolean) {
        context.aiConnectorDataStore.edit { prefs ->
            prefs[Keys.STUDIO_VIBRATION] = enabled
        }
    }

    suspend fun saveAutoPlayEnabled(enabled: Boolean) {
        context.aiConnectorDataStore.edit { prefs ->
            prefs[Keys.STUDIO_AUTO_PLAY] = enabled
        }
    }

    suspend fun getConfig(): AiStudioConfig = configFlow.first()

    suspend fun setActiveProvider(provider: AiProvider) {
        context.aiConnectorDataStore.edit { it[Keys.ACTIVE_PROVIDER] = provider.name }
    }

    suspend fun saveProviderCredentials(
        provider: AiProvider,
        apiKey: String,
        model: String,
        verifiedAt: Long? = null
    ) {
        val cleanKey = apiKey.trim()
        val cleanModel = model.trim().ifBlank { provider.defaultModel }
        context.aiConnectorDataStore.edit { prefs ->
            when (provider) {
                AiProvider.GEMINI -> {
                    prefs[Keys.GEMINI_KEY] = cleanKey
                    prefs[Keys.GEMINI_MODEL] = cleanModel
                    if (verifiedAt != null) prefs[Keys.GEMINI_VERIFIED_AT] = verifiedAt
                    else if (cleanKey.isEmpty()) prefs.remove(Keys.GEMINI_VERIFIED_AT)
                }
                AiProvider.OPENAI -> {
                    prefs[Keys.OPENAI_KEY] = cleanKey
                    prefs[Keys.OPENAI_MODEL] = cleanModel
                    if (verifiedAt != null) prefs[Keys.OPENAI_VERIFIED_AT] = verifiedAt
                    else if (cleanKey.isEmpty()) prefs.remove(Keys.OPENAI_VERIFIED_AT)
                }
                AiProvider.CLAUDE -> {
                    prefs[Keys.CLAUDE_KEY] = cleanKey
                    prefs[Keys.CLAUDE_MODEL] = cleanModel
                    if (verifiedAt != null) prefs[Keys.CLAUDE_VERIFIED_AT] = verifiedAt
                    else if (cleanKey.isEmpty()) prefs.remove(Keys.CLAUDE_VERIFIED_AT)
                }
            }
        }
    }

    suspend fun markProviderVerified(provider: AiProvider) {
        val now = System.currentTimeMillis()
        context.aiConnectorDataStore.edit { prefs ->
            when (provider) {
                AiProvider.GEMINI -> prefs[Keys.GEMINI_VERIFIED_AT] = now
                AiProvider.OPENAI -> prefs[Keys.OPENAI_VERIFIED_AT] = now
                AiProvider.CLAUDE -> prefs[Keys.CLAUDE_VERIFIED_AT] = now
            }
        }
    }

    suspend fun saveEmailConfig(
        mode: EmailDeliveryMode,
        resendApiKey: String,
        fromEmail: String,
        defaultRecipient: String
    ) {
        context.aiConnectorDataStore.edit { prefs ->
            prefs[Keys.EMAIL_MODE] = mode.name
            prefs[Keys.RESEND_KEY] = resendApiKey.trim()
            prefs[Keys.EMAIL_FROM] = fromEmail.trim()
            prefs[Keys.EMAIL_TO] = defaultRecipient.trim()
        }
    }

    suspend fun saveNotionConfig(
        token: String,
        databaseId: String,
        databaseTitle: String,
        titleProperty: String
    ) {
        context.aiConnectorDataStore.edit { prefs ->
            prefs[Keys.NOTION_TOKEN] = token.trim()
            prefs[Keys.NOTION_DB_ID] = databaseId.trim()
            prefs[Keys.NOTION_DB_TITLE] = databaseTitle.trim()
            prefs[Keys.NOTION_TITLE_PROP] = titleProperty.trim().ifBlank { "Name" }
        }
    }

    suspend fun saveWebhooks(webhooks: List<CustomWebhookConnector>) {
        context.aiConnectorDataStore.edit { prefs ->
            prefs[Keys.WEBHOOKS_JSON] = encodeWebhooks(webhooks)
        }
    }

    private fun encodeWebhooks(list: List<CustomWebhookConnector>): String {
        val arr = JSONArray()
        list.forEach { w ->
            arr.put(
                JSONObject().apply {
                    put("id", w.id)
                    put("name", w.name)
                    put("presetType", w.presetType.name)
                    put("url", w.url)
                    put("authHeader", w.authHeader)
                    put("isEnabled", w.isEnabled)
                }
            )
        }
        return arr.toString()
    }

    private fun decodeWebhooks(raw: String?): List<CustomWebhookConnector> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { idx ->
                val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
                val url = obj.optString("url", "")
                if (url.isBlank()) return@mapNotNull null
                val preset = runCatching {
                    WebhookPresetType.valueOf(obj.optString("presetType", WebhookPresetType.WEBHOOK.name))
                }.getOrDefault(WebhookPresetType.WEBHOOK)
                CustomWebhookConnector(
                    id = obj.optString("id", UUID.randomUUID().toString().take(8)),
                    name = obj.optString("name", preset.label),
                    presetType = preset,
                    url = url,
                    authHeader = obj.optString("authHeader", ""),
                    isEnabled = obj.optBoolean("isEnabled", true)
                )
            }
        }.getOrDefault(emptyList())
    }
}
