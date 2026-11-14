package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "telegram_activity_logs")
data class TelegramActivityLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val taskId: Int? = null,
    val direction: String, // "OUTBOUND" or "INBOUND"
    val eventType: String, // "REMINDER", "MANUAL_SEND", "COMPLETED_FROM_TG", "SNOOZED_FROM_TG", "CREATED_FROM_TG", "VERIFIED", "TEST_PING"
    val title: String,
    val detail: String,
    val status: String, // "SENT", "SYNCED", "FAILED"
    val timestamp: Long = System.currentTimeMillis()
)
