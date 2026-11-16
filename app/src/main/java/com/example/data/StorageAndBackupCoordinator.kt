package com.example.data

import android.content.Context
import java.io.File

data class StorageStats(
    val permanentAudioBytes: Long = 0L,
    val reclaimableCacheBytes: Long = 0L,
    val voiceFileCount: Int = 0
)

object StorageAndBackupCoordinator {

    fun voiceMemosDir(context: Context): File {
        return File(context.filesDir, "voice_memos").apply { mkdirs() }
    }

    fun notepadVoicesDir(context: Context): File {
        return File(context.filesDir, "notepad_voices").apply { mkdirs() }
    }

    fun alarmTonesCacheDir(context: Context): File {
        return File(context.cacheDir, "alarm_tones").apply { mkdirs() }
    }

    suspend fun migrateLegacyCacheAudio(context: Context, repository: VoiceTaskRepository) {
        val legacyRecDir = File(context.cacheDir, "recordings")
        val legacyNoteDir = File(context.cacheDir, "notepad_recordings")
        val targetMemoDir = voiceMemosDir(context)
        val targetNoteDir = notepadVoicesDir(context)

        val tasks = repository.getAllTasks()
        for (task in tasks) {
            val rawPath = task.audioPath ?: continue
            val sourceFile = File(rawPath)
            if (sourceFile.exists() && isInsideDir(sourceFile, legacyRecDir)) {
                val dest = File(targetMemoDir, sourceFile.name)
                if (moveOrCopyFile(sourceFile, dest)) {
                    repository.updateTask(task.copy(audioPath = dest.absolutePath))
                }
            }
        }

        val notes = repository.getAllNotepadNotes()
        for (note in notes) {
            val rawPath = note.audioPath ?: continue
            val sourceFile = File(rawPath)
            if (sourceFile.exists() && isInsideDir(sourceFile, legacyNoteDir)) {
                val dest = File(targetNoteDir, sourceFile.name)
                if (moveOrCopyFile(sourceFile, dest)) {
                    repository.updateNotepadNote(note.copy(audioPath = dest.absolutePath))
                }
            }
        }
    }

    fun computeStorageStats(
        context: Context,
        tasks: List<VoiceTask>,
        notes: List<NotepadNote>
    ): StorageStats {
        var permanentBytes = 0L
        var fileCount = 0

        val permanentDirs = listOf(voiceMemosDir(context), notepadVoicesDir(context))
        val countedPaths = mutableSetOf<String>()

        permanentDirs.forEach { dir ->
            dir.listFiles()?.forEach { f ->
                if (f.isFile) {
                    permanentBytes += f.length()
                    fileCount++
                    countedPaths.add(f.absolutePath)
                }
            }
        }

        // Also count any DB-referenced files outside permanentDirs
        (tasks.mapNotNull { it.audioPath } + notes.mapNotNull { it.audioPath }).forEach { path ->
            if (path !in countedPaths) {
                val f = File(path)
                if (f.exists() && f.isFile) {
                    permanentBytes += f.length()
                    fileCount++
                    countedPaths.add(path)
                }
            }
        }

        var cacheBytes = 0L
        val cacheDirs = listOf(
            File(context.cacheDir, "recordings"),
            File(context.cacheDir, "notepad_recordings"),
            File(context.cacheDir, "alarm_tones")
        )
        cacheDirs.forEach { dir ->
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { f ->
                    if (f.isFile && f.absolutePath !in countedPaths) {
                        cacheBytes += f.length()
                    }
                }
            }
        }

        return StorageStats(
            permanentAudioBytes = permanentBytes,
            reclaimableCacheBytes = cacheBytes,
            voiceFileCount = fileCount
        )
    }

    suspend fun clearReclaimableCache(
        context: Context,
        repository: VoiceTaskRepository,
        activeRecordingPaths: Set<String>
    ) {
        val dbPaths = (repository.getAllTasks().mapNotNull { it.audioPath } +
            repository.getAllNotepadNotes().mapNotNull { it.audioPath }).toSet()
        val protectedPaths = dbPaths + activeRecordingPaths

        val cacheDirs = listOf(
            File(context.cacheDir, "recordings"),
            File(context.cacheDir, "notepad_recordings"),
            File(context.cacheDir, "alarm_tones")
        )
        cacheDirs.forEach { dir ->
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    if (file.isFile && file.absolutePath !in protectedPaths) {
                        runCatching { file.delete() }
                    }
                }
            }
        }
    }

    private fun isInsideDir(file: File, dir: File): Boolean {
        val fileParent = runCatching { file.canonicalFile.parentFile?.canonicalPath }.getOrNull() ?: return false
        val dirPath = runCatching { dir.canonicalPath }.getOrNull() ?: return false
        return fileParent == dirPath
    }

    private fun moveOrCopyFile(source: File, dest: File): Boolean {
        if (source.absolutePath == dest.absolutePath) return true
        return runCatching {
            if (!dest.exists()) {
                if (source.renameTo(dest)) return true
                source.copyTo(dest, overwrite = true)
            }
            if (dest.exists() && dest.length() > 0L) {
                source.delete()
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }
}
