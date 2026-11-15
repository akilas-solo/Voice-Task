package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.SmsInboxReader
import com.example.ui.AiStudioQuickSheet
import com.example.ui.AlarmSoundEngine
import com.example.ui.AppPasscodeLockScreen
import com.example.ui.BackupRestoreScreen
import com.example.ui.BotanicalBottomNavBar
import com.example.ui.BotanicalEmptyMemosState
import com.example.ui.BotanicalTabletRail
import com.example.ui.BotanicalTopHeader
import com.example.ui.FeedbackScreen
import com.example.ui.FinanceScreen
import com.example.ui.FoldersManagerScreen
import com.example.ui.InAppReminderDialog
import com.example.ui.MemosWorkspaceScreen
import com.example.ui.NotepadScreen
import com.example.ui.QuickAddNoteDialog
import com.example.ui.SettingsScreen
import com.example.ui.UserGuideScreen
import com.example.ui.VoiceRecordSheet
import com.example.ui.VoiceTaskViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private var viewModelRef: VoiceTaskViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[VoiceTaskViewModel::class.java]
        viewModelRef = viewModel
        handleIncomingIntent(intent, viewModel)

        setContent {
            val isDark = when (viewModel.themeOption) {
                "Dark" -> true
                "Light" -> false
                else -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = isDark) {
                val isSecurityEnabled = remember {
                    runCatching { BuildConfig.SECURITY_MODE_ENABLED.toBoolean() }.getOrDefault(false)
                }
                val securePasscode = remember {
                    runCatching { BuildConfig.APP_SECURE_PASSCODE.ifBlank { "1234" } }.getOrDefault("1234")
                }

                var isUnlocked by remember { mutableStateOf(!isSecurityEnabled) }

                if (!isUnlocked) {
                    AppPasscodeLockScreen(
                        correctPasscode = securePasscode,
                        onUnlockSuccess = { isUnlocked = true }
                    )
                } else {
                    MainLayout(
                        viewModel = viewModel,
                        isDark = isDark
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModelRef?.let { handleIncomingIntent(intent, it) }
    }

    private fun handleIncomingIntent(intent: Intent?, viewModel: VoiceTaskViewModel) {
        if (intent == null) return
        val triggeredTaskId = intent.getIntExtra("TRIGGERED_TASK_ID", -1)
        if (triggeredTaskId > 0) {
            intent.removeExtra("TRIGGERED_TASK_ID")
            viewModel.openReminderById(triggeredTaskId)
        }

        when (intent.action) {
            Intent.ACTION_SEND -> {
                val sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                val sharedUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                intent.action = null
                when {
                    !sharedText.isNullOrBlank() -> viewModel.importSharedTextFromIntent(sharedText)
                    sharedUri != null -> viewModel.importSmsBackupUris(this, listOf(sharedUri), switchToFinance = true)
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val sharedTexts = intent.getCharSequenceArrayListExtra(Intent.EXTRA_TEXT)
                    ?.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
                    ?.joinToString("\n\n")
                val sharedUris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                intent.action = null
                when {
                    !sharedTexts.isNullOrBlank() -> viewModel.importSharedTextFromIntent(sharedTexts)
                    !sharedUris.isNullOrEmpty() -> viewModel.importSmsBackupUris(this, sharedUris, switchToFinance = true)
                }
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            AlarmSoundEngine.stopAll()
        }
        super.onDestroy()
    }
}

@Composable
fun MainLayout(
    viewModel: VoiceTaskViewModel,
    isDark: Boolean
) {
    val context = LocalContext.current

    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val filteredTasks by viewModel.filteredTasks.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onAppForegrounded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var showRecordSheet by remember { mutableStateOf(false) }
    var showQuickAddDialog by remember { mutableStateOf(false) }
    var showAiStudioSheet by remember { mutableStateOf(false) }
    var aiSheetInitialTab by remember { mutableIntStateOf(0) }
    var aiSheetTaskId by remember { mutableStateOf<Int?>(null) }
    var aiSheetNoteId by remember { mutableStateOf<Int?>(null) }
    var pendingRecordSource by remember { mutableStateOf("MEMOS") }

    fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            if (pendingRecordSource == "NOTEPAD") {
                viewModel.startNotepadRecording()
            } else {
                showRecordSheet = true
            }
        } else {
            Toast.makeText(
                context,
                "Microphone permission is required to record voice memos.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(
                context,
                "Reminder notifications are disabled. Enable them in system settings.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    var showSmsRationaleDialog by remember { mutableStateOf(false) }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.scanDeviceSmsInbox(sinceDays = 90, silent = false)
        } else {
            val activity = context as? Activity
            val permanentlyDenied = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_SMS)
            Toast.makeText(
                context,
                if (permanentlyDenied) {
                    "SMS permission disabled in system settings. Enable SMS permission or paste bank SMS text."
                } else {
                    "SMS permission denied. You can still paste bank SMS messages directly."
                },
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val requestSmsPermission = {
        val activity = context as? Activity
        when {
            SmsInboxReader.hasReadSmsPermission(context) -> {
                viewModel.scanDeviceSmsInbox(sinceDays = 90, silent = false)
            }
            activity != null && ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.READ_SMS
            ) -> {
                showSmsRationaleDialog = true
            }
            else -> {
                smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
            }
        }
    }

    if (showSmsRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showSmsRationaleDialog = false },
            title = { Text("Allow SMS Inbox Access?") },
            text = {
                Text(
                    "Voice Task reads bank and mobile-money SMS alerts (such as Telebirr, CBE, and Bank of Abyssinia) directly on your device to track spending and generate financial summaries. Personal OTPs are automatically ignored."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSmsRationaleDialog = false
                        smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
                    }
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSmsRationaleDialog = false
                        runCatching {
                            val settingsIntent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                            context.startActivity(settingsIntent)
                        }
                    }
                ) {
                    Text("App Settings")
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotificationPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasNotificationPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Back navigation handler for custom screen switching
    val isUtilityScreen = currentScreen == VoiceTaskViewModel.Screen.BACKUP_RESTORE ||
        currentScreen == VoiceTaskViewModel.Screen.USER_GUIDE ||
        currentScreen == VoiceTaskViewModel.Screen.FEEDBACK

    BackHandler(enabled = currentScreen != VoiceTaskViewModel.Screen.MEMOS) {
        if (isUtilityScreen) {
            viewModel.navigateTo(VoiceTaskViewModel.Screen.SETTINGS)
        } else {
            viewModel.navigateTo(VoiceTaskViewModel.Screen.MEMOS)
        }
    }

    val activeReminder = viewModel.activeReminderInApp
    if (activeReminder != null) {
        InAppReminderDialog(activeReminder = activeReminder, viewModel = viewModel)
    }

    val launchMainRecorder = {
        pendingRecordSource = "MEMOS"
        if (hasAudioPermission()) {
            showRecordSheet = true
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val toggleTheme = {
        viewModel.changeThemeOption(if (isDark) "Light" else "Dark")
    }

    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (isTablet) {
            BotanicalTabletRail(
                currentScreen = currentScreen,
                isDark = isDark,
                onNavigate = { viewModel.navigateTo(it) },
                onRecordClick = launchMainRecorder,
                onOpenAiStudio = {
                    aiSheetInitialTab = 0
                    aiSheetTaskId = null
                    aiSheetNoteId = null
                    showAiStudioSheet = true
                },
                onToggleTheme = toggleTheme
            )
        }

        Scaffold(
            modifier = Modifier.weight(1f),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                BotanicalTopHeader(
                    currentScreen = currentScreen,
                    isDark = isDark,
                    onBackToPrevious = {
                        viewModel.navigateTo(VoiceTaskViewModel.Screen.SETTINGS)
                    },
                    onQuickAddTextNote = { showQuickAddDialog = true },
                    onOpenAiStudio = {
                        aiSheetInitialTab = 0
                        aiSheetTaskId = null
                        aiSheetNoteId = null
                        showAiStudioSheet = true
                    },
                    onToggleTheme = toggleTheme,
                    onNavigate = { viewModel.navigateTo(it) }
                )
            },
            bottomBar = {
                if (!isTablet) {
                    BotanicalBottomNavBar(
                        currentScreen = currentScreen,
                        onNavigate = { viewModel.navigateTo(it) },
                        onRecordClick = launchMainRecorder
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 760.dp)
                ) {
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                        },
                        label = "botanical_screen_transition",
                        modifier = Modifier.fillMaxSize()
                    ) { targetScreen ->
                        when (targetScreen) {
                            VoiceTaskViewModel.Screen.MEMOS -> {
                                MemosWorkspaceScreen(
                                    viewModel = viewModel,
                                    filteredTasks = filteredTasks,
                                    categories = categories,
                                    selectedCategory = selectedCategory,
                                    searchQuery = searchQuery,
                                    onOpenAiForTask = { task ->
                                        aiSheetTaskId = task.id
                                        aiSheetNoteId = null
                                        aiSheetInitialTab = if (task.audioPath != null) 1 else 2
                                        showAiStudioSheet = true
                                    }
                                )
                            }
                            VoiceTaskViewModel.Screen.NOTEPAD -> {
                                NotepadScreen(
                                    viewModel = viewModel,
                                    hasAudioPermission = { hasAudioPermission() },
                                    onRequestAudioPermission = {
                                        pendingRecordSource = "NOTEPAD"
                                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    },
                                    onOpenAiForNote = { note ->
                                        aiSheetTaskId = null
                                        aiSheetNoteId = note.id
                                        aiSheetInitialTab = if (note.audioPath != null) 1 else 2
                                        showAiStudioSheet = true
                                    }
                                )
                            }
                            VoiceTaskViewModel.Screen.FINANCE -> {
                                FinanceScreen(
                                    viewModel = viewModel,
                                    hasSmsPermission = { SmsInboxReader.hasReadSmsPermission(context) },
                                    onRequestSmsPermission = requestSmsPermission
                                )
                            }
                            VoiceTaskViewModel.Screen.FOLDERS -> FoldersManagerScreen(viewModel = viewModel)
                            VoiceTaskViewModel.Screen.BACKUP_RESTORE -> BackupRestoreScreen(viewModel = viewModel)
                            VoiceTaskViewModel.Screen.USER_GUIDE -> UserGuideScreen()
                            VoiceTaskViewModel.Screen.FEEDBACK -> FeedbackScreen(viewModel = viewModel)
                            VoiceTaskViewModel.Screen.SETTINGS -> SettingsScreen(
                                viewModel = viewModel,
                                onOpenAiStudio = {
                                    aiSheetInitialTab = 0
                                    aiSheetTaskId = null
                                    aiSheetNoteId = null
                                    showAiStudioSheet = true
                                }
                            )
                        }
                    }

                    if (showRecordSheet) {
                        VoiceRecordSheet(
                            viewModel = viewModel,
                            onDismiss = { showRecordSheet = false }
                        )
                    }

                    if (showQuickAddDialog) {
                        QuickAddNoteDialog(
                            viewModel = viewModel,
                            onDismiss = { showQuickAddDialog = false }
                        )
                    }

                    if (showAiStudioSheet) {
                        AiStudioQuickSheet(
                            viewModel = viewModel,
                            initialTab = aiSheetInitialTab,
                            preselectedTaskId = aiSheetTaskId,
                            preselectedNoteId = aiSheetNoteId,
                            onOpenSettings = {
                                viewModel.selectSettingsTab(VoiceTaskViewModel.SettingsTab.AI)
                                viewModel.navigateTo(VoiceTaskViewModel.Screen.SETTINGS)
                            },
                            onDismiss = { showAiStudioSheet = false }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyMemosState() {
    BotanicalEmptyMemosState()
}
