package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TaskPriority(
    val level: Int,
    val label: String,
    val shortLabel: String
) {
    LOW(0, "Low", "Low"),
    MEDIUM(1, "Medium", "Med"),
    HIGH(2, "High", "High"),
    URGENT(3, "Urgent", "Urgent");

    companion object {
        fun fromLevel(level: Int): TaskPriority =
            entries.firstOrNull { it.level == level } ?: MEDIUM
    }
}

enum class TaskSortOption(
    val label: String,
    val defaultDescending: Boolean
) {
    CHRONOLOGICAL("Chronological", true),
    PRIORITY("Priority", true),
    CATEGORY("Category", false);

    fun directionLabel(descending: Boolean): String = when (this) {
        CHRONOLOGICAL -> if (descending) "Newest first" else "Oldest first"
        PRIORITY -> if (descending) "Urgent to Low" else "Low to Urgent"
        CATEGORY -> if (descending) "Z to A" else "A to Z"
    }

    fun compactLabel(descending: Boolean): String = when (this) {
        CHRONOLOGICAL -> if (descending) "Newest" else "Oldest"
        PRIORITY -> if (descending) "Priority ↓" else "Priority ↑"
        CATEGORY -> if (descending) "Cat Z–A" else "Cat A–Z"
    }

    companion object {
        fun sortTasks(
            tasks: List<VoiceTask>,
            option: TaskSortOption,
            descending: Boolean
        ): List<VoiceTask> {
            val comparator = when (option) {
                CHRONOLOGICAL -> {
                    if (descending) {
                        compareByDescending<VoiceTask> { it.createdAt }
                            .thenByDescending { it.id }
                    } else {
                        compareBy<VoiceTask> { it.createdAt }
                            .thenBy { it.id }
                    }
                }
                PRIORITY -> {
                    if (descending) {
                        compareByDescending<VoiceTask> { it.priority }
                            .thenByDescending { it.createdAt }
                            .thenBy { it.title.lowercase() }
                    } else {
                        compareBy<VoiceTask> { it.priority }
                            .thenByDescending { it.createdAt }
                            .thenBy { it.title.lowercase() }
                    }
                }
                CATEGORY -> {
                    if (descending) {
                        compareByDescending<VoiceTask> { it.category.lowercase() }
                            .thenByDescending { it.title.lowercase() }
                            .thenByDescending { it.createdAt }
                    } else {
                        compareBy<VoiceTask> { it.category.lowercase() }
                            .thenBy { it.title.lowercase() }
                            .thenByDescending { it.createdAt }
                    }
                }
            }
            return tasks.sortedWith(comparator)
        }
    }
}

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
    val reminderTime: Long? = null, // Milliseconds timestamp for scheduled reminder notifications
    val alertSound: String = "App Default", // "App Default", "Zen Bowl Resonance", "Digital Beep Pitch", "Play Saved Task Audio"
    val iconName: String = "Mic", // "Mic", "Work", "Personal", "Idea", "Call", "Bookmark", "Shopping"
    val notifyTelegram: Boolean = true, // Dispatch reminder alert to connected Telegram
    val lastTelegramSentAt: Long? = null, // Timestamp of last successful Telegram dispatch
    val priority: Int = TaskPriority.MEDIUM.level // 0=Low, 1=Medium, 2=High, 3=Urgent
)
