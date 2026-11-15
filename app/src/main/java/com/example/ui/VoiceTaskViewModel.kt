package com.example.ui

import android.app.Application
import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AiChatMessage
import com.example.data.AiConnectorPrefs
import com.example.data.AiProvider
import com.example.data.AiProviderClient
import com.example.data.AiResult
import com.example.data.AiStudioConfig
import com.example.data.AiSuggestedAction
import com.example.data.AppDatabase
import com.example.data.BankSmsFileParser
import com.example.data.BankSmsParser
import com.example.data.ConnectorDispatchLog
import com.example.data.ConnectorResult
import com.example.data.CustomWebhookConnector
import com.example.data.EmailDeliveryMode
import com.example.data.ExternalConnectorManager
import com.example.data.ExtractedVoiceTask
import com.example.data.FinanceAnalyticsEngine
import com.example.data.FinanceChatMessage
import com.example.data.FinanceFilterState
import com.example.data.FinancePeriodMode
import com.example.data.FinanceQueryParser
import com.example.data.FinanceSummary
import com.example.data.FinanceTransaction
import com.example.data.GeneratedConnectorDraft
import com.example.data.NotepadNote
import com.example.data.NotionDatabaseInfo
import com.example.data.ParsedFinanceQuery
import com.example.data.SmsInboxReader
import com.example.data.TaskPriority
import com.example.data.TaskSortOption
import com.example.data.TelegramActivityLog
import com.example.data.TelegramConfig
import com.example.data.TelegramPrefs
import com.example.data.TelegramResult
import com.example.data.TelegramSyncManager
import com.example.data.TimeOfDayPreset
import com.example.data.TransactionType
import com.example.data.VoiceTask
import com.example.data.VoiceTaskRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext

class VoiceTaskViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: VoiceTaskRepository
    private val telegramPrefs = TelegramPrefs(application)
    private val aiConnectorPrefs = AiConnectorPrefs(application)
    
    // UI parameters (persisted in DataStore)
    var themeOption by mutableStateOf("Light") // "Light" (#E5E5E0 default), "Dark" (Deep Moss), "System"
        private set

    // App Recording & Audio Engine Settings (persisted in DataStore)
    var audioQuality by mutableStateOf("High") // "High", "Standard", "Saver"
        private set
    var enableVibration by mutableStateOf(true)
        private set
    var autoPlayAfterRecording by mutableStateOf(false)
        private set
        
    fun changeAudioQuality(quality: String) {
        audioQuality = quality
        viewModelScope.launch {
            aiConnectorPrefs.saveAudioQuality(quality)
        }
    }
    
    fun toggleVibration() {
        val next = !enableVibration
        enableVibration = next
        viewModelScope.launch {
            aiConnectorPrefs.saveVibrationEnabled(next)
        }
    }
    
    fun toggleAutoPlay() {
        val next = !autoPlayAfterRecording
        autoPlayAfterRecording = next
        viewModelScope.launch {
            aiConnectorPrefs.saveAutoPlayEnabled(next)
        }
    }

    // Dynamic storage size indicators
    var totalCacheBytes by mutableStateOf(0L)
        private set
        
    fun refreshCacheStats() {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val totalSize = withContext(Dispatchers.IO) {
                    var sum = 0L
                    val dirs = listOf(
                        File(context.cacheDir, "recordings"),
                        File(context.cacheDir, "notepad_recordings")
                    )
                    dirs.forEach { dir ->
                        if (dir.exists() && dir.isDirectory) {
                            dir.listFiles()?.forEach { file ->
                                if (file.isFile) sum += file.length()
                            }
                        }
                    }
                    sum
                }
                totalCacheBytes = totalSize
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearAudioCache() {
        viewModelScope.launch {
            try {
                stopVoiceMemo()
                stopNotepadVoice()
                AlarmSoundEngine.stopAll()
                val context = getApplication<Application>()
                withContext(Dispatchers.IO) {
                    val dirs = listOf(
                        File(context.cacheDir, "recordings"),
                        File(context.cacheDir, "notepad_recordings")
                    )
                    dirs.forEach { dir ->
                        if (dir.exists() && dir.isDirectory) {
                            dir.listFiles()?.forEach { file ->
                                if (file.isFile &&
                                    file.absolutePath != activeRecordFile?.absolutePath &&
                                    file.absolutePath != notepadActiveRecordFile?.absolutePath
                                ) {
                                    file.delete()
                                }
                            }
                        }
                    }
                }
                
                // Clear audio references in Room for both tasks and notepad notes
                val tasks = repository.getAllTasks()
                for (task in tasks) {
                    if (task.audioPath != null || task.audioDurationMs != 0L) {
                        repository.updateTask(task.copy(audioPath = null, audioDurationMs = 0L))
                    }
                }
                val notes = repository.getAllNotepadNotes()
                for (note in notes) {
                    if (note.audioPath != null || note.audioDurationMs != 0L) {
                        repository.updateNotepadNote(note.copy(audioPath = null, audioDurationMs = 0L))
                    }
                }
                
                refreshCacheStats()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    init {
        val voiceTaskDao = AppDatabase.getDatabase(application).voiceTaskDao()
        repository = VoiceTaskRepository(voiceTaskDao)
        viewModelScope.launch {
            aiConnectorPrefs.studioPreferencesFlow.collect { prefs ->
                themeOption = prefs.themeOption
                audioQuality = prefs.audioQuality
                enableVibration = prefs.enableVibration
                autoPlayAfterRecording = prefs.autoPlayAfterRecording
            }
        }
        refreshCacheStats()
    }

    // Screens or pages
    enum class Screen {
        MEMOS,
        NOTEPAD,
        FINANCE,
        FOLDERS,
        BACKUP_RESTORE,
        USER_GUIDE,
        FEEDBACK,
        SETTINGS
    }

    enum class SettingsTab(val label: String) {
        STUDIO("Studio"),
        AI("AI"),
        CONNECTORS("Connectors")
    }

    var selectedSettingsTab by mutableStateOf(SettingsTab.STUDIO)
        private set

    fun selectSettingsTab(tab: SettingsTab) {
        selectedSettingsTab = tab
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

    // Persistent Sort Mode & Direction
    val sortPreference: StateFlow<Pair<TaskSortOption, Boolean>> = aiConnectorPrefs.taskSortPreferenceFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = TaskSortOption.CHRONOLOGICAL to true
    )

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

    // Filtered & Sorted Tasks list (joins search query, selected category, and sort preference)
    val filteredTasks: StateFlow<List<VoiceTask>> = combine(
        allTasks,
        searchQuery,
        selectedCategory,
        sortPreference
    ) { tasks, query, category, (sortOption, sortDescending) ->
        val filtered = tasks.filter { task ->
            val matchesCategory = (category == "All") || (task.category.equals(category, ignoreCase = true))
            val matchesSearch = query.isBlank() ||
                    task.title.contains(query, ignoreCase = true) ||
                    task.notes.contains(query, ignoreCase = true) ||
                    task.tags.contains(query, ignoreCase = true)
            matchesCategory && matchesSearch
        }
        TaskSortOption.sortTasks(filtered, sortOption, sortDescending)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // --- Finance SMS Ledger & Analytics State ---
    val allFinanceTransactions: StateFlow<List<FinanceTransaction>> = repository.allFinanceTransactionsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _financeFilterState = MutableStateFlow(FinanceFilterState())
    val financeFilterState: StateFlow<FinanceFilterState> = _financeFilterState.asStateFlow()

    val filteredFinanceTransactions: StateFlow<List<FinanceTransaction>> = combine(
        allFinanceTransactions,
        _financeFilterState
    ) { transactions, filter ->
        FinanceAnalyticsEngine.filterTransactions(transactions, filter)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val financeSummary: StateFlow<FinanceSummary> = combine(
        filteredFinanceTransactions,
        allFinanceTransactions,
        _financeFilterState
    ) { filtered, all, filter ->
        val label = FinanceAnalyticsEngine.describePeriodLabel(filter)
        FinanceAnalyticsEngine.summarize(filtered, all, label)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FinanceSummary()
    )

    var isFinanceScanning by mutableStateOf(false)
        private set
    var isFinanceAiBusy by mutableStateOf(false)
        private set
    var financeStatusMessage by mutableStateOf<String?>(null)
        private set
    var financeStatusIsError by mutableStateOf(false)
        private set
    var financeAiReport by mutableStateOf<String?>(null)
        private set
    var financeChatMessages by mutableStateOf<List<FinanceChatMessage>>(emptyList())
        private set

    var clipboardBankPromptText by mutableStateOf<String?>(null)
        private set

    var clipboardDetectedSummary by mutableStateOf<String?>(null)
        private set

    private val dismissedClipboardHashes = mutableSetOf<String>()

    fun clearFinanceStatusBanner() {
        financeStatusMessage = null
        financeStatusIsError = false
    }

    fun inspectClipboardForBankSms(rawClipboardText: String?) {
        val text = rawClipboardText?.trim().orEmpty()
        if (text.length < 12) {
            clipboardBankPromptText = null
            clipboardDetectedSummary = null
            return
        }
        val hash = BankSmsParser.computeFingerprint("clipboard", text, 0L)
        if (hash in dismissedClipboardHashes) {
            clipboardBankPromptText = null
            clipboardDetectedSummary = null
            return
        }
        val parsed = BankSmsParser.parseBatchRawText(text, defaultSender = "Clipboard SMS")
        if (parsed.isEmpty()) {
            clipboardBankPromptText = null
            clipboardDetectedSummary = null
            return
        }
        val existingFingerprints = allFinanceTransactions.value.mapTo(mutableSetOf()) { it.smsFingerprint }
        val newMatches = parsed.filter { it.smsFingerprint !in existingFingerprints }
        if (newMatches.isEmpty()) {
            clipboardBankPromptText = null
            clipboardDetectedSummary = null
            return
        }
        val first = newMatches.first()
        clipboardBankPromptText = text
        clipboardDetectedSummary = if (newMatches.size == 1) {
            "${first.bankName} · ${FinanceAnalyticsEngine.formatSignedMoney(first)}"
        } else {
            "${newMatches.size} bank alerts detected (${first.bankName} & more)"
        }
    }

    fun importClipboardBankSms() {
        val raw = clipboardBankPromptText ?: return
        dismissedClipboardHashes.add(BankSmsParser.computeFingerprint("clipboard", raw, 0L))
        clipboardBankPromptText = null
        clipboardDetectedSummary = null
        importManualBankSms(rawText = raw, senderHint = "Clipboard SMS")
    }

    fun dismissClipboardBankSms() {
        clipboardBankPromptText?.let { raw ->
            dismissedClipboardHashes.add(BankSmsParser.computeFingerprint("clipboard", raw, 0L))
        }
        clipboardBankPromptText = null
        clipboardDetectedSummary = null
    }

    fun importSharedTextFromIntent(sharedText: String) {
        navigateTo(Screen.FINANCE)
        viewModelScope.launch {
            val parsed = BankSmsParser.parseBatchRawText(sharedText, defaultSender = "Shared SMS")
                .map { it.copy(source = "SHARE_INTENT") }
            if (parsed.isEmpty()) {
                financeStatusMessage = "Shared text did not contain a recognized Telebirr, CBE, or bank SMS alert."
                financeStatusIsError = true
                return@launch
            }
            val inserted = repository.insertFinanceTransactionsDedup(parsed)
            val banksList = parsed.map { it.bankName }.distinct().take(4).joinToString(", ")
            financeStatusMessage = if (inserted > 0) {
                "Imported $inserted shared bank alert(s) ($banksList)."
            } else {
                "Shared bank alert (${parsed.first().bankName}) is already in your Finance ledger."
            }
            financeStatusIsError = false
        }
    }

    fun importSmsBackupUris(
        context: Context,
        uris: List<Uri>,
        switchToFinance: Boolean = false
    ) {
        if (uris.isEmpty() || isFinanceScanning) return
        if (switchToFinance) navigateTo(Screen.FINANCE)
        viewModelScope.launch {
            isFinanceScanning = true
            financeStatusMessage = "Importing SMS backup file(s)..."
            financeStatusIsError = false
            val appCtx = context.applicationContext
            var totalInspected = 0
            val allParsed = mutableListOf<FinanceTransaction>()
            var lastError: String? = null
            val formats = mutableSetOf<String>()

            uris.forEach { uri ->
                val res = BankSmsFileParser.parseUri(appCtx, uri)
                totalInspected += res.totalRecordsInspected
                if (res.formatLabel.isNotBlank()) formats.add(res.formatLabel)
                allParsed.addAll(res.parsedTransactions)
                if (res.errorMessage != null) lastError = res.errorMessage
            }

            if (allParsed.isEmpty()) {
                financeStatusMessage = lastError
                    ?: "Inspected $totalInspected record(s) (${formats.joinToString(", ")}) — no supported bank SMS alerts found."
                financeStatusIsError = true
            } else {
                val inserted = repository.insertFinanceTransactionsDedup(allParsed)
                val banksList = allParsed.map { it.bankName }.distinct().take(4).joinToString(", ")
                financeStatusMessage = if (inserted > 0) {
                    "Imported $inserted new transaction(s) from ${formats.joinToString("/")} ($banksList)."
                } else {
                    "Verified ${allParsed.size} alert(s) in backup file — all already in your Finance ledger."
                }
                financeStatusIsError = false
            }
            isFinanceScanning = false
        }
    }

    fun setFinancePeriodMode(mode: FinancePeriodMode) {
        _financeFilterState.value = _financeFilterState.value.copy(periodMode = mode)
    }

    fun setFinanceSpecificDay(dayUtcOrLocalMillis: Long) {
        _financeFilterState.value = _financeFilterState.value.copy(
            periodMode = FinancePeriodMode.SPECIFIC_DAY,
            selectedDayMillis = dayUtcOrLocalMillis
        )
    }

    fun setFinanceSpecificMonth(year: Int, monthZeroBased: Int) {
        _financeFilterState.value = _financeFilterState.value.copy(
            periodMode = FinancePeriodMode.SPECIFIC_MONTH,
            selectedYear = year,
            selectedMonthZeroBased = monthZeroBased
        )
    }

    fun setFinanceTimeWindow(
        preset: TimeOfDayPreset,
        startHour: Int = _financeFilterState.value.customStartHour,
        startMinute: Int = _financeFilterState.value.customStartMinute,
        endHour: Int = _financeFilterState.value.customEndHour,
        endMinute: Int = _financeFilterState.value.customEndMinute
    ) {
        _financeFilterState.value = _financeFilterState.value.copy(
            timePreset = preset,
            customStartHour = startHour.coerceIn(0, 23),
            customStartMinute = startMinute.coerceIn(0, 59),
            customEndHour = endHour.coerceIn(0, 23),
            customEndMinute = endMinute.coerceIn(0, 59)
        )
    }

    fun setFinanceBankFilter(bankCode: String) {
        _financeFilterState.value = _financeFilterState.value.copy(selectedBankCode = bankCode)
    }

    fun setFinanceTypeFilter(typeFilter: String) {
        _financeFilterState.value = _financeFilterState.value.copy(typeFilter = typeFilter)
    }

    fun setFinanceSearchQuery(query: String) {
        _financeFilterState.value = _financeFilterState.value.copy(searchQuery = query)
    }

    fun resetFinanceFilters() {
        _financeFilterState.value = FinanceFilterState()
    }

    fun scanDeviceSmsInbox(sinceDays: Int? = null, silent: Boolean = false) {
        if (isFinanceScanning) return
        viewModelScope.launch {
            if (!silent) {
                isFinanceScanning = true
                financeStatusMessage = "Scanning SMS inbox for Telebirr, CBE, Bank of Abyssinia & bank alerts..."
                financeStatusIsError = false
            }
            val sinceMs = sinceDays?.let { System.currentTimeMillis() - it * 86_400_000L }
            val result = SmsInboxReader.scanInbox(getApplication(), sinceTimestampMs = sinceMs)
            if (!result.permissionGranted) {
                if (!silent) {
                    financeStatusMessage = result.errorMessage ?: "SMS permission is required."
                    financeStatusIsError = true
                }
            } else if (result.errorMessage != null && result.parsedTransactions.isEmpty()) {
                if (!silent) {
                    financeStatusMessage = result.errorMessage
                    financeStatusIsError = true
                }
            } else {
                val newlyInserted = repository.insertFinanceTransactionsDedup(result.parsedTransactions)
                if (!silent) {
                    val banksList = result.parsedTransactions.map { it.bankName }.distinct().take(4).joinToString(", ")
                    financeStatusMessage = if (result.parsedTransactions.isEmpty()) {
                        "Checked ${result.totalMessagesScanned} SMS messages — no new bank transactions found. You can also paste SMS text directly."
                    } else if (newlyInserted == 0) {
                        "Checked ${result.totalMessagesScanned} SMS (${result.parsedTransactions.size} bank alerts verified, all already synced)."
                    } else {
                        "Imported $newlyInserted new transaction(s) from ${result.totalMessagesScanned} SMS ($banksList)."
                    }
                    financeStatusIsError = false
                }
            }
            isFinanceScanning = false
        }
    }

    fun importManualBankSms(
        rawText: String,
        senderHint: String = "Manual SMS",
        onDone: (Int, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val parsed = BankSmsParser.parseBatchRawText(rawText, defaultSender = senderHint.ifBlank { "Manual SMS" })
            if (parsed.isEmpty()) {
                val msg = "Could not detect a bank amount or Telebirr/CBE/Abyssinia format in that message."
                financeStatusMessage = msg
                financeStatusIsError = true
                onDone(0, msg)
                return@launch
            }
            val inserted = repository.insertFinanceTransactionsDedup(parsed)
            val msg = if (inserted > 0) {
                "Imported $inserted transaction(s) (${parsed.joinToString(", ") { it.bankName }})."
            } else {
                "This SMS transaction is already in your Finance ledger."
            }
            financeStatusMessage = msg
            financeStatusIsError = false
            onDone(inserted, msg)
        }
    }

    fun addManualFinanceTransaction(
        bankName: String,
        bankCode: String,
        type: TransactionType,
        amount: Double,
        counterparty: String,
        category: String,
        timestamp: Long,
        notes: String = "",
        onDone: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        if (amount <= 0.0) {
            onDone(false, "Enter a valid amount greater than 0.")
            return
        }
        viewModelScope.launch {
            val syntheticBody = notes.ifBlank { "Manual $bankName ${type.label}: ETB $amount ${if (counterparty.isNotBlank()) "with $counterparty" else ""}" }
            val tx = FinanceTransaction(
                smsFingerprint = BankSmsParser.computeFingerprint(bankName, "$syntheticBody#${System.nanoTime()}", timestamp),
                bankName = bankName,
                bankCode = bankCode,
                type = type.name,
                amount = amount,
                currency = "ETB",
                counterparty = counterparty.ifBlank { bankName },
                category = category.ifBlank { "Other" },
                rawSender = "Manual Entry",
                rawSmsBody = syntheticBody,
                timestamp = timestamp,
                source = "MANUAL_ENTRY"
            )
            repository.insertFinanceTransaction(tx)
            financeStatusMessage = "Added ${tx.bankName} ${FinanceAnalyticsEngine.formatMoney(tx.amount)}"
            financeStatusIsError = false
            onDone(true, "Transaction added.")
        }
    }

    fun updateFinanceCategory(tx: FinanceTransaction, category: String) {
        viewModelScope.launch {
            repository.updateFinanceTransaction(tx.copy(category = category))
        }
    }

    fun deleteFinanceTransaction(tx: FinanceTransaction) {
        viewModelScope.launch {
            repository.deleteFinanceTransactionById(tx.id)
        }
    }

    fun clearAllFinanceTransactions() {
        viewModelScope.launch {
            repository.clearAllFinanceTransactions()
            financeAiReport = null
            financeChatMessages = emptyList()
            financeStatusMessage = "Cleared all Finance SMS ledger entries."
            financeStatusIsError = false
        }
    }

    fun askFinanceAi(question: String) {
        val clean = question.trim()
        if (clean.isBlank() || isFinanceAiBusy) return

        val parsed = FinanceQueryParser.parseQuestion(clean, fallbackFilter = _financeFilterState.value)
        // Automatically sync visual filters to match the requested day / month / time / bank when explicit!
        _financeFilterState.value = parsed.filterState

        val all = allFinanceTransactions.value
        val matched = FinanceAnalyticsEngine.filterTransactions(all, parsed.filterState)
        val matchedSummary = FinanceAnalyticsEngine.summarize(matched, all, parsed.periodLabel)

        val userMsg = FinanceChatMessage(isUser = true, text = clean)
        financeChatMessages = financeChatMessages + userMsg

        viewModelScope.launch {
            isFinanceAiBusy = true
            clearFinanceStatusBanner()

            val cfg = aiConnectorPrefs.getConfig()
            val snapshot = FinanceAnalyticsEngine.buildAiFinanceSnapshot(matched, all, matchedSummary)
            val offlineAnswer = FinanceAnalyticsEngine.buildOfflineQueryAnswer(clean, parsed, matched, all)

            val finalText = if (cfg.isAiConfigured && all.isNotEmpty()) {
                val priorTurns = financeChatMessages.dropLast(1).takeLast(6).map { it.isUser to it.text }
                when (val res = AiProviderClient.analyzeFinanceQuery(cfg, clean, parsed.periodLabel, snapshot, priorTurns)) {
                    is AiResult.Success -> res.data
                    is AiResult.Error -> "$offlineAnswer\n\n(AI Note: ${res.reason})"
                }
            } else if (!cfg.isAiConfigured && all.isNotEmpty()) {
                "$offlineAnswer\n\nTip: Configure Gemini, OpenAI, or Claude in Settings -> AI for deeper conversational insights."
            } else {
                offlineAnswer
            }

            financeChatMessages = financeChatMessages + FinanceChatMessage(
                isUser = false,
                text = finalText,
                matchedPeriodLabel = parsed.periodLabel,
                matchedCount = matchedSummary.transactionCount,
                matchedIncome = matchedSummary.totalIncome,
                matchedOutflow = matchedSummary.totalOutflow
            )
            isFinanceAiBusy = false
        }
    }

    fun runFullFinanceAiReport() {
        if (isFinanceAiBusy) return
        val all = allFinanceTransactions.value
        val filter = _financeFilterState.value
        val matched = FinanceAnalyticsEngine.filterTransactions(all, filter)
        val label = FinanceAnalyticsEngine.describePeriodLabel(filter)
        val summary = FinanceAnalyticsEngine.summarize(matched, all, label)

        if (all.isEmpty()) {
            financeAiReport = "No bank SMS data imported yet. Scan your SMS inbox or paste a Telebirr / CBE / Abyssinia alert to analyze."
            return
        }

        viewModelScope.launch {
            isFinanceAiBusy = true
            clearFinanceStatusBanner()
            val cfg = aiConnectorPrefs.getConfig()
            val snapshot = FinanceAnalyticsEngine.buildAiFinanceSnapshot(matched, all, summary)
            val fallback = FinanceAnalyticsEngine.buildOfflineQueryAnswer(
                question = "Analyze $label",
                parsed = ParsedFinanceQuery(filter, label, "ALL"),
                matching = matched,
                allTransactions = all
            )

            financeAiReport = if (cfg.isAiConfigured) {
                when (val res = AiProviderClient.generateFinanceReport(cfg, label, snapshot)) {
                    is AiResult.Success -> {
                        // Mark matched records as AI analyzed
                        matched.take(30).filter { !it.aiAnalyzed }.forEach { tx ->
                            repository.updateFinanceTransaction(tx.copy(aiAnalyzed = true))
                        }
                        res.data
                    }
                    is AiResult.Error -> "$fallback\n\n(AI Provider Error: ${res.reason})"
                }
            } else {
                "$fallback\n\nTip: Add an AI API key in Settings -> AI for personalized AI narrative analysis."
            }
            isFinanceAiBusy = false
        }
    }

    fun clearFinanceChat() {
        financeChatMessages = emptyList()
        financeAiReport = null
        clearFinanceStatusBanner()
    }

    // --- Dedicated Notepad Notes Flow ---
    val allNotepadNotes = repository.allNotepadNotesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun addNotepadNote(title: String, content: String, category: String = "General", isPinned: Boolean = false, colorHex: String? = null, audioPath: String? = null, audioDurationMs: Long = 0L) {
        viewModelScope.launch {
            try {
                val cleanContent = content.trim()
                val words = if (cleanContent.isEmpty()) 0 else cleanContent.split(Regex("\\s+")).filter { it.isNotBlank() }.size
                val chars = content.length
                val note = NotepadNote(
                    title = title.ifBlank { "New Note" },
                    content = content,
                    category = category,
                    isPinned = isPinned,
                    colorHex = colorHex,
                    wordCount = words,
                    charCount = chars,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    audioPath = audioPath,
                    audioDurationMs = audioDurationMs
                )
                repository.insertNotepadNote(note)
                refreshCacheStats()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun modifyNotepadNote(note: NotepadNote) {
        viewModelScope.launch {
            try {
                val cleanContent = note.content.trim()
                val words = if (cleanContent.isEmpty()) 0 else cleanContent.split(Regex("\\s+")).filter { it.isNotBlank() }.size
                val chars = note.content.length
                val updated = note.copy(
                    wordCount = words,
                    charCount = chars,
                    updatedAt = System.currentTimeMillis()
                )
                repository.updateNotepadNote(updated)
                refreshCacheStats()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleNotePin(note: NotepadNote) {
        viewModelScope.launch {
            try {
                repository.updateNotepadNote(note.copy(isPinned = !note.isPinned, updatedAt = System.currentTimeMillis()))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun removeNotepadNote(note: NotepadNote) {
        viewModelScope.launch {
            try {
                if (playingNotepadNoteId == note.id) {
                    stopNotepadVoice()
                }
                note.audioPath?.let { path ->
                    val audioFile = File(path)
                    if (audioFile.exists()) audioFile.delete()
                }
                repository.deleteNotepadNote(note)
                refreshCacheStats()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

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
        AlarmSoundEngine.stopAll()
        stopVoiceMemo()
        stopNotepadVoice()
        if (isNotepadRecording) {
            cancelNotepadRecording()
        }
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
            if (enableVibration) {
                AlarmSoundEngine.triggerHapticCue(context, strong = false)
            }

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
        iconName: String = "Mic",
        notifyTelegram: Boolean = true,
        priority: Int = TaskPriority.MEDIUM.level
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
            if (enableVibration) {
                AlarmSoundEngine.triggerHapticCue(getApplication(), strong = true)
            }
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
                    iconName = iconName,
                    notifyTelegram = notifyTelegram,
                    priority = priority
                )
                val insertedId = repository.insertTask(task)
                if (reminderTime != null) {
                    ReminderNotificationHelper.scheduleDeviceNotification(getApplication(), insertedId.toInt(), reminderTime)
                }
                refreshCacheStats()
                if (autoPlayAfterRecording) {
                    delay(200)
                    playVoiceMemo(task.copy(id = insertedId.toInt()))
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

        // Stop any competing playback or alarm loop
        AlarmSoundEngine.stopAll()
        stopNotepadVoice()
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
            if (playingTaskId == null) return
            
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
        iconName: String = "Mic",
        notifyTelegram: Boolean = true,
        priority: Int = TaskPriority.MEDIUM.level
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
                    iconName = iconName,
                    notifyTelegram = notifyTelegram,
                    priority = priority
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

    fun updateTaskPriority(task: VoiceTask, priority: TaskPriority) {
        updateTask(task.copy(priority = priority.level))
    }

    fun toggleTaskCompletion(task: VoiceTask) {
        viewModelScope.launch {
            try {
                val updated = task.copy(isCompleted = !task.isCompleted)
                repository.updateTask(updated)
                if (updated.isCompleted) {
                    if (activeReminderInApp?.id == updated.id) {
                        AlarmSoundEngine.stopAll()
                        activeReminderInApp = null
                    }
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
                // If playing or ringing, stop first
                if (playingTaskId == task.id) {
                    stopVoiceMemo()
                }
                if (activeReminderInApp?.id == task.id) {
                    AlarmSoundEngine.stopAll()
                    activeReminderInApp = null
                }
                // Delete actual record file
                task.audioPath?.let {
                    val f = File(it)
                    if (f.exists()) f.delete()
                }
                ReminderNotificationHelper.scheduleDeviceNotification(getApplication(), task.id, null)
                repository.deleteTaskById(task.id)
                refreshCacheStats()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- Search, Categories & Sorting Filter ---
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setSortOption(option: TaskSortOption) {
        viewModelScope.launch {
            aiConnectorPrefs.saveTaskSortPreference(option, option.defaultDescending)
        }
    }

    fun toggleSortDirection() {
        val (currentOption, currentDescending) = sortPreference.value
        viewModelScope.launch {
            aiConnectorPrefs.saveTaskSortPreference(currentOption, !currentDescending)
        }
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
                    val importedTasks = repository.getAllTasks()
                    ReminderNotificationHelper.rescheduleAllPendingReminders(getApplication(), importedTasks)
                    refreshCacheStats()
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

    // --- App Customizer / Themes ---
    fun changeThemeOption(option: String) {
        themeOption = option
        viewModelScope.launch {
            aiConnectorPrefs.saveThemeOption(option)
        }
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

    // --- Telegram Two-Way Integration State ---
    val telegramConfig: StateFlow<TelegramConfig> = telegramPrefs.configFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TelegramConfig()
    )

    val telegramLogs: StateFlow<List<TelegramActivityLog>> = repository.allTelegramLogsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    var isTelegramVerifying by mutableStateOf(false)
        private set
    var isTelegramSyncing by mutableStateOf(false)
        private set
    var sendingTaskIds by mutableStateOf<Set<Int>>(emptySet())
        private set
    var sendingNoteIds by mutableStateOf<Set<Int>>(emptySet())
        private set
    var telegramStatusMessage by mutableStateOf<String?>(null)
        private set
    var telegramStatusIsError by mutableStateOf(false)
        private set
    var pendingTelegramDeepLink by mutableStateOf<String?>(null)
        private set

    val isTelegramBotTokenConfigured: Boolean
        get() = TelegramSyncManager.isBotConfigured()

    private var verificationPollJob: Job? = null

    fun clearTelegramStatusBanner() {
        telegramStatusMessage = null
        telegramStatusIsError = false
    }

    fun generateTelegramVerificationLink(rawUsername: String, onLinkReady: (String) -> Unit) {
        viewModelScope.launch {
            isTelegramVerifying = true
            telegramStatusMessage = "Connecting to Telegram bot..."
            telegramStatusIsError = false

            when (val result = TelegramSyncManager.prepareBotLink(getApplication(), rawUsername)) {
                is TelegramResult.Success -> {
                    pendingTelegramDeepLink = result.data
                    telegramStatusMessage = "Tap Open Telegram Bot, press Start, then return here."
                    telegramStatusIsError = false
                    isTelegramVerifying = false
                    onLinkReady(result.data)
                    startShortVerificationPoll()
                }
                is TelegramResult.Error -> {
                    telegramStatusMessage = result.reason
                    telegramStatusIsError = true
                    isTelegramVerifying = false
                }
            }
        }
    }

    private fun startShortVerificationPoll() {
        verificationPollJob?.cancel()
        verificationPollJob = viewModelScope.launch {
            repeat(15) {
                delay(4000)
                val current = telegramConfig.value
                if (current.isVerified) return@launch
                if (current.pendingCode.isNullOrBlank()) return@launch
                val res = TelegramSyncManager.pollAndVerifyConnection(getApplication())
                if (res is TelegramResult.Success) {
                    telegramStatusMessage = res.message
                    telegramStatusIsError = false
                    pendingTelegramDeepLink = null
                    return@launch
                }
            }
        }
    }

    fun checkTelegramVerification(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) {
                isTelegramVerifying = true
            }
            when (val res = TelegramSyncManager.pollAndVerifyConnection(getApplication())) {
                is TelegramResult.Success -> {
                    verificationPollJob?.cancel()
                    pendingTelegramDeepLink = null
                    telegramStatusMessage = res.message
                    telegramStatusIsError = false
                }
                is TelegramResult.Error -> {
                    if (!silent) {
                        telegramStatusMessage = res.reason
                        telegramStatusIsError = !res.reason.startsWith("Waiting")
                    }
                }
            }
            isTelegramVerifying = false
        }
    }

    fun sendTelegramTestPing(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            isTelegramSyncing = true
            when (val res = TelegramSyncManager.sendTestPing(getApplication())) {
                is TelegramResult.Success -> {
                    telegramStatusMessage = res.data
                    telegramStatusIsError = false
                    onResult(true, res.data)
                }
                is TelegramResult.Error -> {
                    telegramStatusMessage = res.reason
                    telegramStatusIsError = true
                    onResult(false, res.reason)
                }
            }
            isTelegramSyncing = false
        }
    }

    fun sendTaskToTelegram(task: VoiceTask, onResult: (Boolean, String) -> Unit) {
        if (sendingTaskIds.contains(task.id)) return
        viewModelScope.launch {
            sendingTaskIds = sendingTaskIds + task.id
            when (val res = TelegramSyncManager.sendTaskAlert(getApplication(), task, isReminder = false)) {
                is TelegramResult.Success -> {
                    telegramStatusMessage = res.message
                    telegramStatusIsError = false
                    onResult(true, res.message)
                }
                is TelegramResult.Error -> {
                    telegramStatusMessage = res.reason
                    telegramStatusIsError = true
                    onResult(false, res.reason)
                }
            }
            sendingTaskIds = sendingTaskIds - task.id
        }
    }

    fun sendNoteToTelegram(note: NotepadNote, onResult: (Boolean, String) -> Unit) {
        if (sendingNoteIds.contains(note.id)) return
        viewModelScope.launch {
            sendingNoteIds = sendingNoteIds + note.id
            when (val res = TelegramSyncManager.sendNotepadNote(getApplication(), note)) {
                is TelegramResult.Success -> {
                    telegramStatusMessage = res.message
                    telegramStatusIsError = false
                    onResult(true, res.message)
                }
                is TelegramResult.Error -> {
                    telegramStatusMessage = res.reason
                    telegramStatusIsError = true
                    onResult(false, res.reason)
                }
            }
            sendingNoteIds = sendingNoteIds - note.id
        }
    }

    fun syncTelegramNow(silent: Boolean = false, onSummary: (String) -> Unit = {}) {
        viewModelScope.launch {
            if (!silent) isTelegramSyncing = true
            val current = telegramConfig.value
            if (!current.isVerified && !current.pendingCode.isNullOrBlank()) {
                TelegramSyncManager.pollAndVerifyConnection(getApplication())
            }
            when (val res = TelegramSyncManager.syncIncomingUpdates(getApplication())) {
                is TelegramResult.Success -> {
                    val s = res.data
                    if (s.totalProcessed > 0) {
                        val msg = "Synced ${s.totalProcessed} Telegram action(s)"
                        telegramStatusMessage = msg
                        telegramStatusIsError = false
                        onSummary(msg)
                    } else if (!silent) {
                        onSummary("Telegram is up to date")
                    }
                }
                is TelegramResult.Error -> {
                    if (!silent) {
                        telegramStatusMessage = res.reason
                        telegramStatusIsError = true
                        onSummary(res.reason)
                    }
                }
            }
            if (!silent) isTelegramSyncing = false
        }
    }

    fun onAppForegrounded() {
        val current = telegramConfig.value
        if (!current.isVerified && !current.pendingCode.isNullOrBlank()) {
            checkTelegramVerification(silent = true)
        } else if (current.isVerified) {
            syncTelegramNow(silent = true)
        }
        if (SmsInboxReader.hasReadSmsPermission(getApplication())) {
            scanDeviceSmsInbox(sinceDays = 14, silent = true)
        }
    }

    fun setAutoSendTelegramReminders(enabled: Boolean) {
        viewModelScope.launch {
            telegramPrefs.setAutoSendReminders(enabled)
        }
    }

    fun setIncludeAudioInTelegram(enabled: Boolean) {
        viewModelScope.launch {
            telegramPrefs.setIncludeAudioAttachments(enabled)
        }
    }

    fun setAiTelegramCommandsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            telegramPrefs.setAiTelegramCommandsEnabled(enabled)
        }
    }

    fun disconnectTelegram() {
        viewModelScope.launch {
            verificationPollJob?.cancel()
            pendingTelegramDeepLink = null
            telegramPrefs.disconnect()
            telegramStatusMessage = "Telegram account disconnected."
            telegramStatusIsError = false
        }
    }

    fun clearTelegramActivityLogs() {
        viewModelScope.launch {
            repository.clearTelegramLogs()
        }
    }

    // --- AI Studio & External Connectors Hub ---
    val aiStudioConfig: StateFlow<AiStudioConfig> = aiConnectorPrefs.configFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AiStudioConfig()
    )

    val connectorLogs: StateFlow<List<ConnectorDispatchLog>> = repository.allConnectorLogsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    var aiChatMessages by mutableStateOf<List<AiChatMessage>>(emptyList())
        private set
    var isAiBusy by mutableStateOf(false)
        private set
    var isTestingAiKey by mutableStateOf(false)
        private set
    var aiStatusMessage by mutableStateOf<String?>(null)
        private set
    var aiStatusIsError by mutableStateOf(false)
        private set

    var extractedVoiceResult by mutableStateOf<ExtractedVoiceTask?>(null)
        private set
    var extractedForTaskId by mutableStateOf<Int?>(null)
        private set
    var extractedForNoteId by mutableStateOf<Int?>(null)
        private set
    var generatedDraftResult by mutableStateOf<GeneratedConnectorDraft?>(null)
        private set

    var discoveredNotionDatabases by mutableStateOf<List<NotionDatabaseInfo>>(emptyList())
        private set
    var isNotionDiscovering by mutableStateOf(false)
        private set
    var isConnectorDispatching by mutableStateOf(false)
        private set

    fun clearAiStatusBanner() {
        aiStatusMessage = null
        aiStatusIsError = false
    }

    fun setActiveAiProvider(provider: AiProvider) {
        viewModelScope.launch {
            aiConnectorPrefs.setActiveProvider(provider)
            clearAiStatusBanner()
        }
    }

    fun saveAiCredentials(
        provider: AiProvider,
        apiKey: String,
        model: String,
        onSaved: () -> Unit = {}
    ) {
        viewModelScope.launch {
            aiConnectorPrefs.setActiveProvider(provider)
            aiConnectorPrefs.saveProviderCredentials(provider, apiKey, model)
            aiStatusMessage = "${provider.displayName} credentials saved privately on device."
            aiStatusIsError = false
            onSaved()
        }
    }

    fun testActiveAiConnection(
        provider: AiProvider,
        apiKey: String,
        model: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            isTestingAiKey = true
            aiConnectorPrefs.setActiveProvider(provider)
            aiConnectorPrefs.saveProviderCredentials(provider, apiKey, model)
            val currentCfg = aiConnectorPrefs.getConfig()
            when (val res = AiProviderClient.testConnection(currentCfg)) {
                is AiResult.Success -> {
                    aiConnectorPrefs.markProviderVerified(provider)
                    aiStatusMessage = res.data
                    aiStatusIsError = false
                    repository.insertConnectorLog(
                        ConnectorDispatchLog(
                            channel = "AI",
                            title = "${provider.displayName} Verified",
                            detail = "${currentCfg.activeModel} (${res.latencyMs}ms)",
                            status = "SENT"
                        )
                    )
                    onResult(true, res.data)
                }
                is AiResult.Error -> {
                    aiStatusMessage = res.reason
                    aiStatusIsError = true
                    onResult(false, res.reason)
                }
            }
            isTestingAiKey = false
        }
    }

    private fun buildWorkspaceContextSnapshot(): String {
        val tasks = allTasks.value
        val notes = allNotepadNotes.value
        val fmt = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
        return buildString {
            append("VOICE MEMOS & TASKS (${tasks.size} total):\n")
            if (tasks.isEmpty()) {
                append("(No tasks saved yet)\n")
            } else {
                tasks.take(25).forEach { t ->
                    val state = if (t.isCompleted) "DONE" else "OPEN"
                    val alarm = t.reminderTime?.let { " | Alarm: ${fmt.format(Date(it))}" } ?: ""
                    val audio = if (t.audioPath != null) " | Audio: ${formatTimerLabel(t.audioDurationMs)}" else ""
                    append("- [#$state #${t.id}] (${t.category}) ${t.title}")
                    if (t.notes.isNotBlank()) append(" — ${t.notes.take(220)}")
                    if (t.tags.isNotBlank()) append(" [Tags: ${t.tags}]")
                    append(alarm).append(audio).append("\n")
                }
            }
            append("\nNOTEPAD NOTES (${notes.size} total):\n")
            if (notes.isEmpty()) {
                append("(No notepad notes yet)\n")
            } else {
                notes.take(15).forEach { n ->
                    val pinned = if (n.isPinned) "[PINNED] " else ""
                    append("- $pinned(#${n.id}, ${n.category}) ${n.title}: ${n.content.take(260)}\n")
                }
            }
            val finTx = allFinanceTransactions.value
            if (finTx.isNotEmpty()) {
                val finSummary = FinanceAnalyticsEngine.summarize(finTx, finTx, "All Finance SMS")
                append("\nFINANCE SMS LEDGER (${finTx.size} transactions):\n")
                append("- Income: ${FinanceAnalyticsEngine.formatMoney(finSummary.totalIncome, finSummary.primaryCurrency)} | Outflow: ${FinanceAnalyticsEngine.formatMoney(finSummary.totalOutflow, finSummary.primaryCurrency)} | Net: ${FinanceAnalyticsEngine.formatMoney(finSummary.netFlow, finSummary.primaryCurrency)}\n")
                finSummary.bankBreakdowns.filter { it.latestBalance != null }.forEach { b ->
                    append("- ${b.bankName} Balance: ${FinanceAnalyticsEngine.formatMoney(b.latestBalance!!, b.latestCurrency)}\n")
                }
            }
        }
    }

    fun sendAiChatMessage(prompt: String) {
        val clean = prompt.trim()
        if (clean.isBlank() || isAiBusy) return
        val userMsg = AiChatMessage(isUser = true, text = clean)
        val updatedHistory = aiChatMessages + userMsg
        aiChatMessages = updatedHistory
        clearAiStatusBanner()

        viewModelScope.launch {
            isAiBusy = true
            val cfg = aiConnectorPrefs.getConfig()
            val snapshot = buildWorkspaceContextSnapshot()
            when (val res = AiProviderClient.chatWithContext(cfg, updatedHistory, snapshot, allowStructuredActions = true)) {
                is AiResult.Success -> {
                    aiChatMessages = aiChatMessages + res.data
                }
                is AiResult.Error -> {
                    aiStatusMessage = res.reason
                    aiStatusIsError = true
                    aiChatMessages = aiChatMessages + AiChatMessage(
                        isUser = false,
                        text = "Unable to respond: ${res.reason}"
                    )
                }
            }
            isAiBusy = false
        }
    }

    fun clearAiChat() {
        aiChatMessages = emptyList()
        clearAiStatusBanner()
    }

    fun executeAiSuggestedAction(
        action: AiSuggestedAction,
        onFeedback: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val cfg = aiConnectorPrefs.getConfig()
            when (action.type.uppercase(Locale.getDefault())) {
                "CREATE_TASK" -> {
                    val inserted = repository.insertTask(
                        VoiceTask(
                            title = action.title.take(120),
                            notes = action.body,
                            category = action.folder.ifBlank { "Inbox" },
                            tags = action.tags.ifBlank { "AI" },
                            iconName = "AutoAwesome"
                        )
                    )
                    repository.insertConnectorLog(
                        ConnectorDispatchLog(
                            channel = "AI",
                            title = action.title,
                            detail = "Created task #$inserted in ${action.folder}",
                            status = "EXTRACTED"
                        )
                    )
                    onFeedback(true, "Added task \"${action.title}\"")
                }
                "SEND_TELEGRAM" -> {
                    when (val tgRes = TelegramSyncManager.sendCustomMessage(getApplication(), action.title, action.body)) {
                        is TelegramResult.Success -> onFeedback(true, tgRes.message)
                        is TelegramResult.Error -> onFeedback(false, tgRes.reason)
                    }
                }
                "DRAFT_EMAIL" -> {
                    val res = if (cfg.emailDeliveryMode == EmailDeliveryMode.RESEND_API && cfg.isResendConfigured && cfg.emailDefaultRecipient.isNotBlank()) {
                        ExternalConnectorManager.sendViaResendApi(
                            context = getApplication(),
                            config = cfg,
                            toEmail = cfg.emailDefaultRecipient,
                            subject = action.title,
                            body = action.body
                        )
                    } else {
                        ExternalConnectorManager.launchMailComposer(
                            context = getApplication(),
                            toEmail = cfg.emailDefaultRecipient,
                            subject = action.title,
                            body = action.body
                        )
                    }
                    when (res) {
                        is ConnectorResult.Success -> onFeedback(true, res.message)
                        is ConnectorResult.Error -> onFeedback(false, res.reason)
                    }
                }
                "SAVE_NOTION" -> {
                    when (val nRes = ExternalConnectorManager.createNotionPage(
                        context = getApplication(),
                        config = cfg,
                        title = action.title,
                        body = action.body,
                        category = action.folder,
                        tags = action.tags
                    )) {
                        is ConnectorResult.Success -> onFeedback(true, nRes.message)
                        is ConnectorResult.Error -> onFeedback(false, nRes.reason)
                    }
                }
                "SEND_WEBHOOK" -> {
                    val activeWebhook = cfg.customWebhooks.firstOrNull { it.isEnabled }
                    if (activeWebhook == null) {
                        onFeedback(false, "No enabled webhook found. Add one in Settings -> AI & Connectors.")
                    } else {
                        when (val wRes = ExternalConnectorManager.dispatchWebhook(
                            context = getApplication(),
                            webhook = activeWebhook,
                            title = action.title,
                            content = action.body,
                            tags = action.tags
                        )) {
                            is ConnectorResult.Success -> onFeedback(true, wRes.message)
                            is ConnectorResult.Error -> onFeedback(false, wRes.reason)
                        }
                    }
                }
                else -> onFeedback(false, "Unknown action type: ${action.type}")
            }
        }
    }

    fun transcribeAndStructureTaskAudio(
        task: VoiceTask,
        onFeedback: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val path = task.audioPath
        if (path.isNullOrBlank()) {
            onFeedback(false, "This memo does not have an audio recording.")
            return
        }
        viewModelScope.launch {
            isAiBusy = true
            clearAiStatusBanner()
            val cfg = aiConnectorPrefs.getConfig()
            when (val res = AiProviderClient.transcribeAndStructureAudio(cfg, File(path))) {
                is AiResult.Success -> {
                    extractedVoiceResult = res.data
                    extractedForTaskId = task.id
                    extractedForNoteId = null
                    aiStatusMessage = res.message
                    aiStatusIsError = false
                    repository.insertConnectorLog(
                        ConnectorDispatchLog(
                            channel = "AI",
                            title = res.data.title,
                            detail = "Transcribed memo & extracted ${res.data.actionItems.size} action item(s)",
                            status = "EXTRACTED"
                        )
                    )
                    onFeedback(true, res.message)
                }
                is AiResult.Error -> {
                    aiStatusMessage = res.reason
                    aiStatusIsError = true
                    onFeedback(false, res.reason)
                }
            }
            isAiBusy = false
        }
    }

    fun transcribeAndStructureNoteAudio(
        note: NotepadNote,
        onFeedback: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        val path = note.audioPath
        if (path.isNullOrBlank()) {
            onFeedback(false, "This note does not have a voice recording attached.")
            return
        }
        viewModelScope.launch {
            isAiBusy = true
            clearAiStatusBanner()
            val cfg = aiConnectorPrefs.getConfig()
            when (val res = AiProviderClient.transcribeAndStructureAudio(cfg, File(path))) {
                is AiResult.Success -> {
                    extractedVoiceResult = res.data
                    extractedForTaskId = null
                    extractedForNoteId = note.id
                    aiStatusMessage = res.message
                    aiStatusIsError = false
                    repository.insertConnectorLog(
                        ConnectorDispatchLog(
                            channel = "AI",
                            title = res.data.title,
                            detail = "Transcribed note clip & extracted ${res.data.actionItems.size} action item(s)",
                            status = "EXTRACTED"
                        )
                    )
                    onFeedback(true, res.message)
                }
                is AiResult.Error -> {
                    aiStatusMessage = res.reason
                    aiStatusIsError = true
                    onFeedback(false, res.reason)
                }
            }
            isAiBusy = false
        }
    }

    fun applyStructuredExtractionToTask(
        taskId: Int,
        extraction: ExtractedVoiceTask,
        onDone: (String) -> Unit
    ) {
        viewModelScope.launch {
            val task = repository.getTaskById(taskId)
            if (task != null) {
                val combinedNotes = buildString {
                    append(extraction.summary.trim())
                    if (extraction.transcript.isNotBlank()) {
                        append("\n\nTranscript:\n").append(extraction.transcript.trim())
                    }
                }
                repository.updateTask(
                    task.copy(
                        title = extraction.title.ifBlank { task.title },
                        notes = combinedNotes,
                        category = extraction.suggestedFolder.ifBlank { task.category },
                        tags = extraction.tags.ifBlank { task.tags }
                    )
                )
                onDone("Updated memo with AI transcript & structure!")
            } else {
                onDone("Original memo no longer exists.")
            }
        }
    }

    fun applyStructuredExtractionToNote(
        noteId: Int,
        extraction: ExtractedVoiceTask,
        onDone: (String) -> Unit
    ) {
        viewModelScope.launch {
            val note = repository.getNotepadNoteById(noteId)
            if (note != null) {
                val combinedBody = buildString {
                    if (note.content.isNotBlank()) append(note.content.trim()).append("\n\n")
                    append("AI Summary:\n").append(extraction.summary.trim())
                    if (extraction.actionItems.isNotEmpty()) {
                        append("\n\nAction Items:\n")
                        extraction.actionItems.forEach { append("• ").append(it).append("\n") }
                    }
                    if (extraction.transcript.isNotBlank()) {
                        append("\nTranscript:\n").append(extraction.transcript.trim())
                    }
                }
                modifyNotepadNote(
                    note.copy(
                        title = if (note.title == "New Note" || note.title.isBlank()) extraction.title else note.title,
                        content = combinedBody
                    )
                )
                onDone("Appended AI transcript & action items to note!")
            } else {
                onDone("Original note no longer exists.")
            }
        }
    }

    fun createTasksFromActionItems(
        actionItems: List<String>,
        category: String,
        tags: String,
        onDone: (String) -> Unit
    ) {
        val cleanItems = actionItems.map { it.trim() }.filter { it.isNotBlank() }
        if (cleanItems.isEmpty()) {
            onDone("No action items to add.")
            return
        }
        viewModelScope.launch {
            cleanItems.forEach { item ->
                repository.insertTask(
                    VoiceTask(
                        title = item.take(120),
                        notes = "Extracted by AI Voice-to-Task workflow.",
                        category = category.ifBlank { "Inbox" },
                        tags = tags.ifBlank { "AI, Action" },
                        iconName = "AutoAwesome",
                        priority = when {
                            tags.contains("urgent", ignoreCase = true) || item.contains("urgent", ignoreCase = true) -> TaskPriority.URGENT.level
                            tags.contains("high", ignoreCase = true) -> TaskPriority.HIGH.level
                            else -> TaskPriority.MEDIUM.level
                        }
                    )
                )
            }
            onDone("Created ${cleanItems.size} task(s) in ${category.ifBlank { "Inbox" }}!")
        }
    }

    fun generateConnectorDraft(
        channelName: String,
        tone: String,
        sourceTitle: String,
        sourceBody: String,
        extraInstruction: String = "",
        onDone: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            isAiBusy = true
            clearAiStatusBanner()
            val cfg = aiConnectorPrefs.getConfig()
            when (val res = AiProviderClient.draftForConnector(
                config = cfg,
                channelName = channelName,
                tone = tone,
                sourceTitle = sourceTitle,
                sourceBody = sourceBody,
                extraInstruction = extraInstruction
            )) {
                is AiResult.Success -> {
                    generatedDraftResult = res.data
                    aiStatusMessage = res.message
                    aiStatusIsError = false
                    onDone(true, res.message)
                }
                is AiResult.Error -> {
                    aiStatusMessage = res.reason
                    aiStatusIsError = true
                    onDone(false, res.reason)
                }
            }
            isAiBusy = false
        }
    }

    fun dispatchDraftToConnector(
        channelKey: String,
        subjectOrTitle: String,
        body: String,
        tags: String = "",
        emailRecipient: String = "",
        webhookId: String? = null,
        onDone: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            isConnectorDispatching = true
            val cfg = aiConnectorPrefs.getConfig()
            when (channelKey.uppercase(Locale.getDefault())) {
                "TELEGRAM" -> {
                    when (val res = TelegramSyncManager.sendCustomMessage(getApplication(), subjectOrTitle, body)) {
                        is TelegramResult.Success -> onDone(true, res.message)
                        is TelegramResult.Error -> onDone(false, res.reason)
                    }
                }
                "EMAIL" -> {
                    val target = emailRecipient.trim().ifBlank { cfg.emailDefaultRecipient.trim() }
                    val res = if (cfg.emailDeliveryMode == EmailDeliveryMode.RESEND_API && cfg.isResendConfigured && target.isNotBlank()) {
                        ExternalConnectorManager.sendViaResendApi(
                            context = getApplication(),
                            config = cfg,
                            toEmail = target,
                            subject = subjectOrTitle,
                            body = body
                        )
                    } else {
                        ExternalConnectorManager.launchMailComposer(
                            context = getApplication(),
                            toEmail = target,
                            subject = subjectOrTitle,
                            body = body
                        )
                    }
                    when (res) {
                        is ConnectorResult.Success -> onDone(true, res.message)
                        is ConnectorResult.Error -> onDone(false, res.reason)
                    }
                }
                "NOTION" -> {
                    when (val res = ExternalConnectorManager.createNotionPage(
                        context = getApplication(),
                        config = cfg,
                        title = subjectOrTitle,
                        body = body,
                        category = "Voice Task AI",
                        tags = tags
                    )) {
                        is ConnectorResult.Success -> onDone(true, res.message)
                        is ConnectorResult.Error -> onDone(false, res.reason)
                    }
                }
                "WEBHOOK" -> {
                    val webhook = cfg.customWebhooks.firstOrNull { it.id == webhookId }
                        ?: cfg.customWebhooks.firstOrNull { it.isEnabled }
                    if (webhook == null) {
                        onDone(false, "Select or add an enabled Slack / Discord / Webhook connector in Settings.")
                    } else {
                        when (val res = ExternalConnectorManager.dispatchWebhook(
                            context = getApplication(),
                            webhook = webhook,
                            title = subjectOrTitle,
                            content = body,
                            tags = tags
                        )) {
                            is ConnectorResult.Success -> onDone(true, res.message)
                            is ConnectorResult.Error -> onDone(false, res.reason)
                        }
                    }
                }
                else -> onDone(false, "Unsupported connector channel.")
            }
            isConnectorDispatching = false
        }
    }

    fun saveEmailConfig(
        mode: EmailDeliveryMode,
        resendKey: String,
        fromEmail: String,
        defaultRecipient: String,
        onSaved: () -> Unit = {}
    ) {
        viewModelScope.launch {
            aiConnectorPrefs.saveEmailConfig(mode, resendKey, fromEmail, defaultRecipient)
            onSaved()
        }
    }

    fun discoverNotionDatabases(token: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            isNotionDiscovering = true
            when (val res = ExternalConnectorManager.discoverNotionDatabases(token)) {
                is ConnectorResult.Success -> {
                    discoveredNotionDatabases = res.data
                    val current = aiConnectorPrefs.getConfig()
                    val first = res.data.firstOrNull()
                    if (first != null && (current.notionDatabaseId.isBlank() || res.data.none { it.id == current.notionDatabaseId })) {
                        aiConnectorPrefs.saveNotionConfig(token, first.id, first.title, first.titleProperty)
                    } else {
                        aiConnectorPrefs.saveNotionConfig(
                            token,
                            current.notionDatabaseId,
                            current.notionDatabaseTitle,
                            current.notionTitleProperty
                        )
                    }
                    onResult(true, res.message)
                }
                is ConnectorResult.Error -> {
                    onResult(false, res.reason)
                }
            }
            isNotionDiscovering = false
        }
    }

    fun selectNotionDatabase(token: String, db: NotionDatabaseInfo) {
        viewModelScope.launch {
            aiConnectorPrefs.saveNotionConfig(token, db.id, db.title, db.titleProperty)
        }
    }

    fun saveNotionConfig(token: String, databaseId: String, databaseTitle: String, titleProperty: String) {
        viewModelScope.launch {
            aiConnectorPrefs.saveNotionConfig(token, databaseId, databaseTitle, titleProperty)
        }
    }

    fun testNotionPage(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            isConnectorDispatching = true
            val cfg = aiConnectorPrefs.getConfig()
            when (val res = ExternalConnectorManager.createNotionPage(
                context = getApplication(),
                config = cfg,
                title = "Voice Task Notion Connection Test",
                body = "Your Voice Task AI & Connectors Hub successfully connected to Notion.",
                category = "Settings Test",
                tags = "VoiceTask, Verified"
            )) {
                is ConnectorResult.Success -> onResult(true, res.message)
                is ConnectorResult.Error -> onResult(false, res.reason)
            }
            isConnectorDispatching = false
        }
    }

    fun addOrUpdateWebhook(webhook: CustomWebhookConnector) {
        viewModelScope.launch {
            val current = aiConnectorPrefs.getConfig().customWebhooks.toMutableList()
            val idx = current.indexOfFirst { it.id == webhook.id }
            if (idx >= 0) current[idx] = webhook else current.add(webhook)
            aiConnectorPrefs.saveWebhooks(current)
        }
    }

    fun toggleWebhookEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            val updated = aiConnectorPrefs.getConfig().customWebhooks.map {
                if (it.id == id) it.copy(isEnabled = enabled) else it
            }
            aiConnectorPrefs.saveWebhooks(updated)
        }
    }

    fun deleteWebhook(id: String) {
        viewModelScope.launch {
            val updated = aiConnectorPrefs.getConfig().customWebhooks.filterNot { it.id == id }
            aiConnectorPrefs.saveWebhooks(updated)
        }
    }

    fun testWebhook(webhook: CustomWebhookConnector, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            isConnectorDispatching = true
            when (val res = ExternalConnectorManager.dispatchWebhook(
                context = getApplication(),
                webhook = webhook,
                title = "Voice Task Webhook Ping",
                content = "Test dispatch from Voice Task AI & Connectors Hub.",
                tags = "test, voicetask"
            )) {
                is ConnectorResult.Success -> onResult(true, res.message)
                is ConnectorResult.Error -> onResult(false, res.reason)
            }
            isConnectorDispatching = false
        }
    }

    fun clearConnectorLogs() {
        viewModelScope.launch {
            repository.clearConnectorLogs()
        }
    }

    init {
        // Start helper timer to check for reminders & periodic Telegram two-way sync in active app session
        viewModelScope.launch {
            var tickCount = 0
            while (true) {
                delay(6000) // Checks every 6 seconds
                tickCount++
                try {
                    val now = System.currentTimeMillis()
                    val taskWithReminders = allTasks.value.filter { it.reminderTime != null && !it.isCompleted }
                    val triggered = taskWithReminders.firstOrNull { it.reminderTime!! <= now }
                    if (triggered != null && activeReminderInApp == null) {
                        activeReminderInApp = triggered
                        // Clear reminderTime to avoid repeated firing
                        repository.updateTask(triggered.copy(reminderTime = null))

                        val tgConfig = telegramPrefs.getConfig()
                        val recentlySent = triggered.lastTelegramSentAt != null &&
                            (now - triggered.lastTelegramSentAt) < 25_000L
                        if (triggered.notifyTelegram && tgConfig.isVerified && tgConfig.autoSendReminders && !recentlySent) {
                            launch {
                                TelegramSyncManager.sendTaskAlert(getApplication(), triggered, isReminder = true)
                            }
                        }
                    }

                    // Every ~18 seconds while foregrounded & verified, sync two-way Telegram callbacks/commands
                    if (tickCount % 3 == 0 && telegramConfig.value.isVerified) {
                        TelegramSyncManager.syncIncomingUpdates(getApplication())
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun dismissReminder() {
        val current = activeReminderInApp
        AlarmSoundEngine.stopAll()
        if (current != null) {
            ReminderNotificationHelper.cancelDisplayedNotification(getApplication(), current.id)
        }
        activeReminderInApp = null
    }

    fun snoozeReminder(task: VoiceTask, minutes: Int) {
        val snoozedAt = ReminderDateTimeHelper.computeSnoozeTimestamp(minutes)
        AlarmSoundEngine.stopAll()
        ReminderNotificationHelper.cancelDisplayedNotification(getApplication(), task.id)
        activeReminderInApp = null
        viewModelScope.launch {
            try {
                val updated = task.copy(reminderTime = snoozedAt, isCompleted = false)
                repository.updateTask(updated)
                ReminderNotificationHelper.scheduleDeviceNotification(getApplication(), updated.id, snoozedAt)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun openReminderById(taskId: Int) {
        if (taskId <= 0) return
        viewModelScope.launch {
            try {
                val task = repository.getTaskById(taskId)
                if (task != null && !task.isCompleted) {
                    if (task.reminderTime != null && task.reminderTime <= System.currentTimeMillis() + 5_000L) {
                        repository.updateTask(task.copy(reminderTime = null))
                    }
                    activeReminderInApp = task
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- Notepad Audio Recording Engine ---
    private var notepadRecorder: MediaRecorder? = null
    private var notepadRecordTimerJob: Job? = null
    private var notepadRecordingStartTime = 0L

    var isNotepadRecording by mutableStateOf(false)
        private set
    var notepadRecordDurationMs by mutableStateOf(0L)
        private set
    var notepadActiveRecordFile by mutableStateOf<File?>(null)
        private set
    var lastNotepadRecordDurationMs by mutableStateOf(0L)
        private set

    fun startNotepadRecording() {
        if (isNotepadRecording) return
        AlarmSoundEngine.stopAll()
        stopVoiceMemo()
        stopNotepadVoice()
        if (isRecording) {
            cancelRecording()
        }
        try {
            val context = getApplication<Application>()
            val audioDir = File(context.cacheDir, "notepad_recordings").apply { mkdirs() }
            val file = File(audioDir, "note_voice_${System.currentTimeMillis()}.mp4")
            notepadActiveRecordFile = file

            @Suppress("DEPRECATION")
            notepadRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(24000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            notepadRecordingStartTime = System.currentTimeMillis()
            isNotepadRecording = true
            notepadRecordDurationMs = 0L
            if (enableVibration) {
                AlarmSoundEngine.triggerHapticCue(context, strong = false)
            }

            notepadRecordTimerJob = viewModelScope.launch {
                while (isNotepadRecording) {
                    delay(100)
                    notepadRecordDurationMs = System.currentTimeMillis() - notepadRecordingStartTime
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isNotepadRecording = false
            notepadActiveRecordFile = null
        }
    }

    fun stopNotepadRecording(): File? {
        if (!isNotepadRecording) return null
        notepadRecordTimerJob?.cancel()
        notepadRecordTimerJob = null
        isNotepadRecording = false

        try {
            notepadRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        notepadRecorder = null

        val finalFile = notepadActiveRecordFile
        val duration = notepadRecordDurationMs
        lastNotepadRecordDurationMs = duration

        notepadActiveRecordFile = null
        notepadRecordDurationMs = 0L

        return if (finalFile != null && finalFile.exists() && duration > 500) {
            if (enableVibration) {
                AlarmSoundEngine.triggerHapticCue(getApplication(), strong = true)
            }
            refreshCacheStats()
            finalFile
        } else {
            finalFile?.delete()
            null
        }
    }

    fun cancelNotepadRecording() {
        if (!isNotepadRecording) return
        notepadRecordTimerJob?.cancel()
        notepadRecordTimerJob = null
        isNotepadRecording = false
        try {
            notepadRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        notepadRecorder = null
        notepadActiveRecordFile?.delete()
        notepadActiveRecordFile = null
        notepadRecordDurationMs = 0L
    }

    // --- Notepad Audio Playback Engine ---
    private var notepadPlayer: MediaPlayer? = null
    private var notepadPlaybackTimerJob: Job? = null

    var playingNotepadNoteId by mutableStateOf<Int?>(null)
        private set
    var isNotepadPlaying by mutableStateOf(false)
        private set
    var notepadPlaybackProgress by mutableStateOf(0f)
        private set
    var notepadPlaybackPositionMs by mutableStateOf(0L)
        private set
    var notepadPlaybackDurationMs by mutableStateOf(0L)
        private set

    fun playNotepadVoice(noteId: Int, audioPath: String) {
        if (playingNotepadNoteId == noteId) {
            if (isNotepadPlaying) {
                pauseNotepadVoice()
            } else {
                resumeNotepadVoice()
            }
            return
        }

        AlarmSoundEngine.stopAll()
        stopVoiceMemo()
        stopNotepadVoice()

        val file = File(audioPath)
        if (!file.exists()) {
            return
        }

        try {
            notepadPlayer = MediaPlayer().apply {
                setDataSource(audioPath)
                prepare()
                start()
                notepadPlaybackDurationMs = duration.toLong()
                setOnCompletionListener {
                    stopNotepadVoice()
                }
            }
            playingNotepadNoteId = noteId
            isNotepadPlaying = true
            notepadPlaybackPositionMs = 0L

            notepadPlaybackTimerJob = viewModelScope.launch {
                while (isNotepadPlaying && notepadPlayer != null) {
                    try {
                        val current = notepadPlayer?.currentPosition ?: 0
                        notepadPlaybackPositionMs = current.toLong()
                        notepadPlaybackProgress = if (notepadPlaybackDurationMs > 0) {
                            current.toFloat() / notepadPlaybackDurationMs.toFloat()
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
            stopNotepadVoice()
        }
    }

    fun pauseNotepadVoice() {
        try {
            notepadPlayer?.pause()
            isNotepadPlaying = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun resumeNotepadVoice() {
        try {
            notepadPlayer?.start()
            isNotepadPlaying = true

            notepadPlaybackTimerJob?.cancel()
            notepadPlaybackTimerJob = viewModelScope.launch {
                while (isNotepadPlaying && notepadPlayer != null) {
                    try {
                        val current = notepadPlayer?.currentPosition ?: 0
                        notepadPlaybackPositionMs = current.toLong()
                        notepadPlaybackProgress = if (notepadPlaybackDurationMs > 0) {
                            current.toFloat() / notepadPlaybackDurationMs.toFloat()
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

    fun stopNotepadVoice() {
        notepadPlaybackTimerJob?.cancel()
        notepadPlaybackTimerJob = null
        try {
            notepadPlayer?.apply {
                if (isPlaying) {
                     stop()
                }
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        notepadPlayer = null
        playingNotepadNoteId = null
        isNotepadPlaying = false
        notepadPlaybackProgress = 0f
        notepadPlaybackPositionMs = 0L
        notepadPlaybackDurationMs = 0L
    }

    fun seekNotepadPlayback(ratio: Float) {
        val player = notepadPlayer ?: return
        try {
            val clamped = ratio.coerceIn(0f, 1f)
            val targetMs = (clamped * notepadPlaybackDurationMs).toInt().coerceIn(0, notepadPlaybackDurationMs.toInt())
            player.seekTo(targetMs)
            notepadPlaybackProgress = clamped
            notepadPlaybackPositionMs = targetMs.toLong()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCleared() {
        verificationPollJob?.cancel()
        AlarmSoundEngine.stopAll()
        stopVoiceMemo()
        cancelRecording()
        stopNotepadVoice()
        cancelNotepadRecording()
        super.onCleared()
    }
}
