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

private val Context.telegramDataStore: DataStore<Preferences> by preferencesDataStore(name = "telegram_studio_prefs")

data class TelegramConfig(
    val username: String = "",
    val chatId: Long? = null,
    val isVerified: Boolean = false,
    val pendingCode: String? = null,
    val botUsername: String? = null,
    val autoSendReminders: Boolean = true,
    val includeAudioAttachments: Boolean = true,
    val aiTelegramCommandsEnabled: Boolean = true,
    val lastUpdateOffset: Long = 0L,
    val lastVerifiedAt: Long? = null
)

class TelegramPrefs(private val context: Context) {

    private object Keys {
        val USERNAME = stringPreferencesKey("tg_username")
        val CHAT_ID = longPreferencesKey("tg_chat_id")
        val IS_VERIFIED = booleanPreferencesKey("tg_is_verified")
        val PENDING_CODE = stringPreferencesKey("tg_pending_code")
        val BOT_USERNAME = stringPreferencesKey("tg_bot_username")
        val AUTO_SEND_REMINDERS = booleanPreferencesKey("tg_auto_send_reminders")
        val INCLUDE_AUDIO = booleanPreferencesKey("tg_include_audio")
        val AI_TG_COMMANDS = booleanPreferencesKey("tg_ai_commands_enabled")
        val LAST_UPDATE_OFFSET = longPreferencesKey("tg_last_update_offset")
        val LAST_VERIFIED_AT = longPreferencesKey("tg_last_verified_at")
    }

    val configFlow: Flow<TelegramConfig> = context.telegramDataStore.data.map { prefs ->
        TelegramConfig(
            username = prefs[Keys.USERNAME] ?: "",
            chatId = prefs[Keys.CHAT_ID],
            isVerified = prefs[Keys.IS_VERIFIED] ?: false,
            pendingCode = prefs[Keys.PENDING_CODE],
            botUsername = prefs[Keys.BOT_USERNAME],
            autoSendReminders = prefs[Keys.AUTO_SEND_REMINDERS] ?: true,
            includeAudioAttachments = prefs[Keys.INCLUDE_AUDIO] ?: true,
            aiTelegramCommandsEnabled = prefs[Keys.AI_TG_COMMANDS] ?: true,
            lastUpdateOffset = prefs[Keys.LAST_UPDATE_OFFSET] ?: 0L,
            lastVerifiedAt = prefs[Keys.LAST_VERIFIED_AT]
        )
    }

    suspend fun getConfig(): TelegramConfig = configFlow.first()

    suspend fun savePendingVerification(
        username: String,
        pendingCode: String,
        botUsername: String
    ) {
        context.telegramDataStore.edit { prefs ->
            prefs[Keys.USERNAME] = username
            prefs[Keys.PENDING_CODE] = pendingCode
            prefs[Keys.BOT_USERNAME] = botUsername
            prefs[Keys.IS_VERIFIED] = false
            prefs.remove(Keys.CHAT_ID)
        }
    }

    suspend fun markVerified(
        username: String,
        chatId: Long,
        botUsername: String?,
        lastOffset: Long
    ) {
        context.telegramDataStore.edit { prefs ->
            prefs[Keys.USERNAME] = username
            prefs[Keys.CHAT_ID] = chatId
            prefs[Keys.IS_VERIFIED] = true
            prefs[Keys.LAST_VERIFIED_AT] = System.currentTimeMillis()
            if (lastOffset > (prefs[Keys.LAST_UPDATE_OFFSET] ?: 0L)) {
                prefs[Keys.LAST_UPDATE_OFFSET] = lastOffset
            }
            if (!botUsername.isNullOrBlank()) {
                prefs[Keys.BOT_USERNAME] = botUsername
            }
            prefs.remove(Keys.PENDING_CODE)
        }
    }

    suspend fun updateLastOffset(offset: Long) {
        context.telegramDataStore.edit { prefs ->
            val current = prefs[Keys.LAST_UPDATE_OFFSET] ?: 0L
            if (offset > current) {
                prefs[Keys.LAST_UPDATE_OFFSET] = offset
            }
        }
    }

    suspend fun setAutoSendReminders(enabled: Boolean) {
        context.telegramDataStore.edit { prefs ->
            prefs[Keys.AUTO_SEND_REMINDERS] = enabled
        }
    }

    suspend fun setIncludeAudioAttachments(enabled: Boolean) {
        context.telegramDataStore.edit { prefs ->
            prefs[Keys.INCLUDE_AUDIO] = enabled
        }
    }

    suspend fun setAiTelegramCommandsEnabled(enabled: Boolean) {
        context.telegramDataStore.edit { prefs ->
            prefs[Keys.AI_TG_COMMANDS] = enabled
        }
    }

    suspend fun disconnect() {
        context.telegramDataStore.edit { prefs ->
            prefs[Keys.IS_VERIFIED] = false
            prefs.remove(Keys.CHAT_ID)
            prefs.remove(Keys.PENDING_CODE)
            prefs.remove(Keys.LAST_VERIFIED_AT)
        }
    }
}
