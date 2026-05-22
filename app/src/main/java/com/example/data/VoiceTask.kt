package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "voice_tasks")
data class VoiceTask(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val notes: String = "",
    val audioPath: String? = null,
    val audioDurationMs: Long = 0L,
    val category: String = "Inbox", // Folder/Category (e.g., "Inbox", "Work", "Personal", "Ideas")
    val tags: String = "", // Comma-separated tags (e.g., "Urgent, Voice, Note")
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false, // Track simulated cloud sync status
    val reminderTime: Long? = null // Milliseconds timestamp for scheduled reminder notifications
)
