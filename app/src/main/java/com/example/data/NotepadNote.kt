package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notepad_notes")
data class NotepadNote(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String = "",
    val category: String = "General", // "General", "Work", "Personal", "Ideas", "Reminders"
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val wordCount: Int = 0,
    val charCount: Int = 0,
    val colorHex: String? = null, // For customizable premium card background colors (e.g., "#FFF9C4" for yellow, "#E1F5FE" for blue, etc.)
    val audioPath: String? = null,
    val audioDurationMs: Long = 0L
)
