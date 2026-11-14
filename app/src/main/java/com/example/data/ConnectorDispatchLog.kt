package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "connector_dispatch_logs")
data class ConnectorDispatchLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val channel: String, // "EMAIL", "NOTION", "TELEGRAM", "SLACK", "DISCORD", "WEBHOOK", "AI"
    val title: String,
    val detail: String,
    val status: String, // "SENT", "DRAFTED", "EXTRACTED", "FAILED"
    val timestamp: Long = System.currentTimeMillis()
)
