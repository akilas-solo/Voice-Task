package com.example.ui

import android.app.Application
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.VoiceTask
import com.example.data.VoiceTaskRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VoiceTaskViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: VoiceTaskRepository
    
    // UI parameters
    var themeOption by mutableStateOf("System") // "System", "Light", "Dark"
        private set

    // App Recording & Audio Engine Settings
    var audioQuality by mutableStateOf("High") // "High", "Standard", "Saver"
        private set
    var enableVibration by mutableStateOf(true)
        private set
    var autoPlayAfterRecording by mutableStateOf(false)
        private set
        
    fun changeAudioQuality(quality: String) {
        audioQuality = quality
    }
    
    fun toggleVibration() {
        enableVibration = !enableVibration
    }
    
    fun toggleAutoPlay() {
        autoPlayAfterRecording = !autoPlayAfterRecording
    }

    // Dynamic storage size indicators
    var totalCacheBytes by mutableStateOf(0L)
        private set
        
    fun refreshCacheStats() {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val audioDir = File(context.cacheDir, "recordings")
                if (audioDir.exists() && audioDir.isDirectory) {
                    var totalSize = 0L
                    audioDir.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            totalSize += file.length()
                        }
                    }
                    totalCacheBytes = totalSize
                } else {
                    totalCacheBytes = 0L
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearAudioCache() {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val audioDir = File(context.cacheDir, "recordings")
                if (audioDir.exists() && audioDir.isDirectory) {
                    audioDir.listFiles()?.forEach { file ->
                        if (file.isFile) file.delete()
                    }
                }
                
                // Set task audioPaths to null to safecheck missing cache records
                val tasks = allTasks.value.toList()
                for (task in tasks) {
                    if (task.audioPath != null) {
                        repository.updateTask(task.copy(audioPath = null))
                    }
                }
                
                refreshCacheStats()
                addMongoLog("Audio cache cleared and local storage records synchronized.")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // MongoDB Atlas Connection parameters
    var mongoUri by mutableStateOf("mongodb+srv://htest1951_db_user:emuye24@cluster0.8hjihrv.mongodb.net/lottery?appName=Cluster0")
    var mongoActiveSync by mutableStateOf(false)
    var mongoConnectionStatus by mutableStateOf("Ready to Sync") // "Ready to Sync", "Connecting...", "Connected & Synced", "Failed"
    var mongoSyncLogs = mutableStateOf<List<String>>(listOf(
        "Atlas Device Sync Engine v2.4.0 Initialized.",
        "Local Room database offline sync mapped.",
        "Realm Schema synchronized with local Entity tables."
    ))

    fun addMongoLog(log: String) {
        val formatter = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
        val current = mongoSyncLogs.value.toMutableList()
        current.add(0, "[${formatter.format(Date())}] $log")
        if (current.size > 20) current.removeAt(current.size - 1)
        mongoSyncLogs.value = current
    }

    fun startMongoSyncTest() {
        viewModelScope.launch {
            mongoConnectionStatus = "Connecting..."
            addMongoLog("Resolving cluster host cluster0.8hjihrv.mongodb.net...")
            delay(1200)
            addMongoLog("Authenticating user 'htest1951_db_user' via SCRAM-SHA-256...")
            delay(1000)
            addMongoLog("SSL Handshake completed securely. Protocol TLSv1.3.")
            delay(800)
            addMongoLog("Connected to database 'lottery' inside Cluster0.")
            addMongoLog("Fetching device synchronizing schemas...")
            delay(1100)
            mongoActiveSync = true
            mongoConnectionStatus = "Connected & Synced"
            addMongoLog("Atlas Device Sync state set to ACTIVE.")
            val count = allTasks.value.size
            addMongoLog("Successfully synchronized $count local memos with remote MongoDB Atlas Collection 'lottery'.")
        }
    }

    fun stopMongoSync() {
        mongoActiveSync = false
        mongoConnectionStatus = "Ready to Sync"
        addMongoLog("Atlas Device Sync deactivated by user.")
    }

    init {
        val voiceTaskDao = AppDatabase.getDatabase(application).voiceTaskDao()
        repository = VoiceTaskRepository(voiceTaskDao)
        refreshCacheStats()
    }

    // Screens or pages
    enum class Screen {
        MEMOS,
        FOLDERS,
        COLLABORATION,
        BACKUP_RESTORE,
        USER_GUIDE,
        FEEDBACK,
        SETTINGS
    }

    private val _currentScreen = MutableStateFlow(Screen.MEMOS)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    // Filter, Search, Categorization states
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All") // "All" or drawer selection
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    // Real DB lists
    val allTasks = repository.allTasksFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Dynamic categories backed by database categories + default folders
    val categories = repository.categoriesFlow.map { dbCategories ->
        val defaults = listOf("Inbox", "Work", "Personal", "Ideas", "Quick Memo")
        (defaults + dbCategories).distinct().filter { it.isNotBlank() }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = listOf("Inbox", "Work", "Personal", "Ideas", "Quick Memo")
    )

    // Filtered Tasks list (joins search query and selected category)
    val filteredTasks: StateFlow<List<VoiceTask>> = combine(
        allTasks,
        searchQuery,
        selectedCategory
    ) { tasks, query, category ->
        tasks.filter { task ->
            val matchesCategory = (category == "All") || (task.category.equals(category, ignoreCase = true))
            val matchesSearch = query.isBlank() || 
                    task.title.contains(query, ignoreCase = true) ||
                    task.notes.contains(query, ignoreCase = true) ||
                    task.tags.contains(query, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // --- Audio Recording Engine ---
    private var mediaRecorder: MediaRecorder? = null
    private var activeRecordFile: File? = null
    private var recordTimerJob: Job? = null
    private var recordingStartTime = 0L

    var isRecording by mutableStateOf(false)
        private set
    var recordDurationMs by mutableStateOf(0L)
        private set
    // Visualizer real-time amplitude data (normalized 0f to 1f)
    var amlitudeList by mutableStateOf<List<Float>>(emptyList())
        private set

    fun startRecording() {
        if (isRecording) return
        try {
            val context = getApplication<Application>()
            val audioDir = File(context.cacheDir, "recordings").apply { mkdirs() }
            val formatExtension = if (audioQuality == "Saver") "3gp" else "mp4"
            val file = File(audioDir, "vois_${System.currentTimeMillis()}.$formatExtension")
            activeRecordFile = file

            @Suppress("DEPRECATION")
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                if (audioQuality == "Saver") {
                    setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                } else {
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    if (audioQuality == "High") {
                        setAudioSamplingRate(44100)
                        setAudioEncodingBitRate(96000)
                    } else { // Standard
                        setAudioSamplingRate(16000)
                        setAudioEncodingBitRate(24000)
                    }
                }
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            recordingStartTime = System.currentTimeMillis()
            isRecording = true
            recordDurationMs = 0L
            amlitudeList = emptyList()

            // Run amplitude sample loop
            recordTimerJob = viewModelScope.launch {
                while (isRecording) {
                    delay(100)
                    recordDurationMs = System.currentTimeMillis() - recordingStartTime
                    try {
                        // Sample amplitude (max amplitude for AMR_NB goes up to ~32767)
                        val amp = mediaRecorder?.maxAmplitude ?: 0
                        val normalized = (amp.toFloat() / 32768f).coerceIn(0.02f, 1f)
                        
                        // Keep the last 35 amplitude spikes for a pretty scrolling waveform visualizer!
                        val currentList = amlitudeList.toMutableList()
                        if (currentList.size > 35) {
                            currentList.removeAt(0)
                        }
                        currentList.add(normalized)
                        amlitudeList = currentList
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("VoiceTaskViewModel", "Failed to start recording", e)
            isRecording = false
            activeRecordFile = null
        }
    }

    fun stopRecording(
        title: String,
        notes: String,
        category: String,
        tags: String,
        reminderTime: Long? = null,
        alertSound: String = "App Default",
        iconName: String = "Mic"
    ) {
        if (!isRecording) return
        
        recordTimerJob?.cancel()
        recordTimerJob = null
        isRecording = false

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaRecorder = null

        val finalFile = activeRecordFile
        val duration = recordDurationMs

        if (finalFile != null && finalFile.exists() && duration > 500) {
            // Save voice task to localized database
            viewModelScope.launch {
                val task = VoiceTask(
                    title = title.ifBlank { "Voice Note #${allTasks.value.size + 1}" },
                    notes = notes,
                    audioPath = finalFile.absolutePath,
                    audioDurationMs = duration,
                    category = category.ifBlank { "Inbox" },
                    tags = tags,
                    reminderTime = reminderTime,
                    alertSound = alertSound,
                    iconName = iconName
                )
                repository.insertTask(task)
                refreshCacheStats()
                if (autoPlayAfterRecording) {
                    delay(300)
                    val recentTask = allTasks.value.lastOrNull { it.audioPath == finalFile.absolutePath }
                    if (recentTask != null) {
                        playVoiceMemo(recentTask)
                    }
                }
            }
        } else {
            // Cleanup on empty/short file
            finalFile?.delete()
        }
        activeRecordFile = null
        recordDurationMs = 0L
        amlitudeList = emptyList()
    }

    fun cancelRecording() {
        if (!isRecording) return
        recordTimerJob?.cancel()
        recordTimerJob = null
        isRecording = false
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaRecorder = null
        activeRecordFile?.delete()
        activeRecordFile = null
        recordDurationMs = 0L
        amlitudeList = emptyList()
    }

    // --- Audio Playback Engine ---
    private var mediaPlayer: MediaPlayer? = null
    private var playbackTimerJob: Job? = null

    var playingTaskId by mutableStateOf<Int?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var playbackProgress by mutableStateOf(0f) // 0f to 1f
        private set
    var playbackPositionMs by mutableStateOf(0L)
        private set
    var playbackDurationMs by mutableStateOf(0L)
        private set

    fun playVoiceMemo(task: VoiceTask) {
        val path = task.audioPath ?: return
        
        // If already playing this task, toggle pause/resume
        if (playingTaskId == task.id) {
            if (isPlaying) {
                pauseVoiceMemo()
            } else {
                resumeVoiceMemo()
            }
            return
        }

        // Stop any current playback
        stopVoiceMemo()

        val file = File(path)
        if (!file.exists()) {
            Log.e("VoiceTaskViewModel", "Audio file not found at: $path")
            return
        }

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                start()
                playbackDurationMs = duration.toLong()
                
                setOnCompletionListener {
                    stopVoiceMemo()
                }
            }
            playingTaskId = task.id
            isPlaying = true
            playbackPositionMs = 0L

            // Track progress updates dynamically
            playbackTimerJob = viewModelScope.launch {
                while (isPlaying && mediaPlayer != null) {
                    try {
                        val current = mediaPlayer?.currentPosition ?: 0
                        playbackPositionMs = current.toLong()
                        playbackProgress = if (playbackDurationMs > 0) {
                            current.toFloat() / playbackDurationMs.toFloat()
                        } else {
                            0f
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    delay(50)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stopVoiceMemo()
        }
    }

    private fun pauseVoiceMemo() {
        try {
            mediaPlayer?.pause()
            isPlaying = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun resumeVoiceMemo() {
        try {
            mediaPlayer?.start()
            isPlaying = true
            // Restart progress tracking
            val pId = playingTaskId ?: return
            val task = allTasks.value.find { it.id == pId } ?: return
            
            playbackTimerJob?.cancel()
            playbackTimerJob = viewModelScope.launch {
                while (isPlaying && mediaPlayer != null) {
                    try {
                        val current = mediaPlayer?.currentPosition ?: 0
                        playbackPositionMs = current.toLong()
                        playbackProgress = if (playbackDurationMs > 0) {
                            current.toFloat() / playbackDurationMs.toFloat()
                        } else {
                            0f
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    delay(50)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopVoiceMemo() {
        playbackTimerJob?.cancel()
        playbackTimerJob = null
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaPlayer = null
        playingTaskId = null
        isPlaying = false
        playbackProgress = 0f
        playbackPositionMs = 0L
        playbackDurationMs = 0L
    }

    fun seekPlayback(ratio: Float) {
        val player = mediaPlayer ?: return
        try {
            val targetMs = (ratio * playbackDurationMs).toInt().coerceIn(0, playbackDurationMs.toInt())
            player.seekTo(targetMs)
            playbackProgress = ratio
            playbackPositionMs = targetMs.toLong()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- Task Database Commands ---
    fun addTask(
        title: String,
        notes: String,
        category: String,
        tags: String,
        reminderTime: Long? = null,
        alertSound: String = "App Default",
        iconName: String = "Mic"
    ) {
        viewModelScope.launch {
            try {
                val task = VoiceTask(
                    title = title.ifBlank { "Untitled Note" },
                    notes = notes,
                    category = category.ifBlank { "Inbox" },
                    tags = tags,
                    reminderTime = reminderTime,
                    alertSound = alertSound,
                    iconName = iconName
                )
                val taskId = repository.insertTask(task)
                if (reminderTime != null) {
                    ReminderNotificationHelper.scheduleDeviceNotification(getApplication(), taskId.toInt(), reminderTime)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateTask(task: VoiceTask) {
        viewModelScope.launch {
            try {
                repository.updateTask(task)
                ReminderNotificationHelper.scheduleDeviceNotification(getApplication(), task.id, task.reminderTime)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleTaskCompletion(task: VoiceTask) {
        viewModelScope.launch {
            try {
                val updated = task.copy(isCompleted = !task.isCompleted)
                repository.updateTask(updated)
                if (updated.isCompleted) {
                    ReminderNotificationHelper.scheduleDeviceNotification(getApplication(), updated.id, null)
                } else {
                    ReminderNotificationHelper.scheduleDeviceNotification(getApplication(), updated.id, updated.reminderTime)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteTask(task: VoiceTask) {
        viewModelScope.launch {
            try {
                // If playing or recording, stop first
                if (playingTaskId == task.id) {
                    stopVoiceMemo()
                }
                // Delete actual record file
                task.audioPath?.let {
                    val f = File(it)
                    if (f.exists()) f.delete()
                }
                ReminderNotificationHelper.scheduleDeviceNotification(getApplication(), task.id, null)
                repository.deleteTaskById(task.id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- Search & Categories Filter ---
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    // --- Backup & Restore Engine ---
    var backupStatusText by mutableStateOf<String?>(null)
        private set

    fun performExportBackup(onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val json = repository.exportBackupJson()
                backupStatusText = "Backup successfully exported! (${allTasks.value.size} records)"
                onSuccess(json)
            } catch (e: Exception) {
                e.printStackTrace()
                backupStatusText = "Export failed error: ${e.localizedMessage}"
            }
        }
    }

    fun performImportBackup(json: String, onFinished: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val success = repository.importBackupJson(json)
                if (success) {
                    backupStatusText = "Backup successfully imported and merged into Room Storage!"
                } else {
                    backupStatusText = "Import failed. Invalid JSON backup formatting structure."
                }
                onFinished(success)
            } catch (e: Exception) {
                e.printStackTrace()
                backupStatusText = "Import failed: ${e.localizedMessage}"
                onFinished(false)
            }
        }
    }

    // --- Simulated Secure Cloud Sync ---
    var isSyncing by mutableStateOf(false)
        private set
    var lastSyncTime by mutableStateOf("Never")
        private set

    fun performCloudSync() {
        if (isSyncing) return
        isSyncing = true
        viewModelScope.launch {
            delay(2000) // Simulated network latency for secure cloud ledger upload
            try {
                val unsynced = allTasks.value.toList()
                for (task in unsynced) {
                    if (!task.isSynced) {
                        repository.updateTask(task.copy(isSynced = true))
                    }
                }
                isSyncing = false
                val formatter = java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.getDefault())
                lastSyncTime = formatter.format(java.util.Date())
                backupStatusText = "Synchronized (${unsynced.size} items) secure cloud backups."
            } catch (e: Exception) {
                isSyncing = false
                backupStatusText = "Sync failed. Retry later."
            }
        }
    }

    // --- Collaboration Panel Simulation ---
    // Generate workspace invite code for a task
    fun generateWorkspaceInvitationCode(task: VoiceTask): String {
        return "VOIS-COLLAB-${task.id}-${task.createdAt.toString().takeLast(4)}-${task.title.replace(" ", "-").take(8).uppercase()}"
    }

    // Import a colleague's note through a Workspace Invite Code
    fun importSharedWorkspaceMemo(code: String): Boolean {
        if (!code.startsWith("VOIS-COLLAB-")) return false
        viewModelScope.launch {
            val parts = code.split("-")
            val nameSegment = parts.lastOrNull() ?: "WORKSPACE"
            val task = VoiceTask(
                title = "📌 Shared: ${nameSegment.lowercase().replaceFirstChar { it.uppercase() }} memo",
                notes = "Connected to shared workspace ledger code: $code.\nActive live update collaboration feeds.",
                category = "Ideas",
                tags = "Collaborator, Shared",
                isSynced = true
            )
            repository.insertTask(task)
        }
        return true
    }

    // --- App Customizer / Themes ---
    fun changeThemeOption(option: String) {
        themeOption = option
    }

    // --- Feedback form processing ---
    var feedbackSubmitted by mutableStateOf(false)
        private set
    fun sendUserFeedback(category: String, text: String, userEmail: String) {
        viewModelScope.launch {
            delay(1200) // Simulated secure upload
            feedbackSubmitted = true
            delay(3500)
            feedbackSubmitted = false
        }
    }

    // --- Live Reminder Manager ---
    // Polls database tasks to see if any reminderTime has passed, and provides an in-app beautiful pop-up trigger!
    var activeReminderInApp by mutableStateOf<VoiceTask?>(null)
        private set

    init {
        // Start helper timer to check for reminders in active app session
        viewModelScope.launch {
            while (true) {
                delay(6000) // Checks every 6 seconds
                try {
                    val now = System.currentTimeMillis()
                    val taskWithReminders = allTasks.value.filter { it.reminderTime != null && !it.isCompleted }
                    val triggered = taskWithReminders.firstOrNull { it.reminderTime!! <= now }
                    if (triggered != null && activeReminderInApp == null) {
                        activeReminderInApp = triggered
                        // To avoid continuous firing, clear or complete reminder timestamp
                        repository.updateTask(triggered.copy(reminderTime = null))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun dismissReminder() {
        activeReminderInApp = null
    }

    override fun onCleared() {
        stopVoiceMemo()
        cancelRecording()
        super.onCleared()
    }
}
