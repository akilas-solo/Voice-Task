package com.example

import android.Manifest
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.VoiceTask
import com.example.ui.VoiceTaskViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Single simple factory for the ViewModel
        val viewModel = ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(application))[VoiceTaskViewModel::class.java]

        setContent {
            // Retrieve dynamic theme options: Light, Dark or System default
            val currentThemeOption = viewModel.themeOption
            val isDark = when (currentThemeOption) {
                "Dark" -> true
                "Light" -> false
                else -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = isDark) {
                MainLayout(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainLayout(viewModel: VoiceTaskViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    
    // Collecting flows
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val filteredTasks by viewModel.filteredTasks.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    // Dialog & bottom sheet state managers
    var showRecordSheet by remember { mutableStateOf(false) }
    var showQuickAddDialog by remember { mutableStateOf(false) }
    var isRecordingAuthorized by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        isRecordingAuthorized = isGranted
        if (isGranted) {
            showRecordSheet = true
        } else {
            Toast.makeText(context, "Voice Recording permissions are required to use VoisTask Audio memos.", Toast.LENGTH_LONG).show()
        }
    }

    // Check system level notifications / reminder Popups
    val activeReminder = viewModel.activeReminderInApp
    if (activeReminder != null) {
        // Trigger generic ringtone chime
        LaunchedEffect(activeReminder) {
            try {
                val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val r = RingtoneManager.getRingtone(context, notification)
                r.play()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        AlertDialog(
            onDismissRequest = { viewModel.dismissReminder() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = "Alert",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("⏰ VoisTask Reminder", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = activeReminder.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = activeReminder.notes.ifBlank { "No transcription notes attached." })
                    if (!activeReminder.category.isBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Folder: ${activeReminder.category}", 
                            style = MaterialTheme.typography.bodySmall, 
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.toggleTaskCompletion(activeReminder)
                        viewModel.dismissReminder()
                        Toast.makeText(context, "Completed Task!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Complete Task")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissReminder() }) {
                    Text("Snooze / Dismiss")
                }
            }
        )
    }

    // Responsive adaptation: Check screen size class
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    Row(modifier = Modifier.fillMaxSize()) {
        // If tablet dimension, show persistent Navigation Rail sidebar on screen side
        if (isTablet) {
            Surface(
                modifier = Modifier
                    .width(80.dp)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "App Head",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        // Icons matching pages
                        val screens = listOf(
                            Triple(VoiceTaskViewModel.Screen.MEMOS, Icons.Default.List, "Memos"),
                            Triple(VoiceTaskViewModel.Screen.FOLDERS, Icons.Default.Folder, "Folders"),
                            Triple(VoiceTaskViewModel.Screen.COLLABORATION, Icons.Default.Share, "Collab"),
                            Triple(VoiceTaskViewModel.Screen.BACKUP_RESTORE, Icons.Default.Backup, "Backups"),
                            Triple(VoiceTaskViewModel.Screen.FEEDBACK, Icons.Default.Feedback, "Feedback"),
                            Triple(VoiceTaskViewModel.Screen.USER_GUIDE, Icons.AutoMirrored.Filled.Help, "Help")
                        )

                        screens.forEach { (scr, icon, lbl) ->
                            IconButton(
                                onClick = { viewModel.navigateTo(scr) },
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .testTag("rail_${lbl.lowercase()}")
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = lbl,
                                    tint = if (currentScreen == scr) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    // Theme selector in tablet corner
                    IconButton(
                        onClick = {
                            viewModel.changeThemeOption(if (viewModel.themeOption == "Dark") "Light" else "Dark")
                        },
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Icon(
                            imageVector = if (viewModel.themeOption == "Dark") Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Theme"
                        )
                    }
                }
            }
            VerticalDivider()
        }

        // Modal navigation drawer wrap for phone sizes
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = !isTablet,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.width(300.dp),
                    drawerContainerColor = MaterialTheme.colorScheme.surface
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "VoisTask Sidebar",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    
                    Text(
                        text = "NAVIGATE PAGES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Mic, contentDescription = "Memos") },
                        label = { Text("Task & Voice Memos") },
                        selected = currentScreen == VoiceTaskViewModel.Screen.MEMOS,
                        onClick = {
                            viewModel.navigateTo(VoiceTaskViewModel.Screen.MEMOS)
                            scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_memos")
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Folder, contentDescription = "Folders") },
                        label = { Text("Folders & Tags") },
                        selected = currentScreen == VoiceTaskViewModel.Screen.FOLDERS,
                        onClick = {
                            viewModel.navigateTo(VoiceTaskViewModel.Screen.FOLDERS)
                            scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_folders")
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Share, contentDescription = "Collab") },
                        label = { Text("Shared Collaboration") },
                        selected = currentScreen == VoiceTaskViewModel.Screen.COLLABORATION,
                        onClick = {
                            viewModel.navigateTo(VoiceTaskViewModel.Screen.COLLABORATION)
                            scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_collab")
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Backup, contentDescription = "Backups") },
                        label = { Text("Backup & Cloud Sync") },
                        selected = currentScreen == VoiceTaskViewModel.Screen.BACKUP_RESTORE,
                        onClick = {
                            viewModel.navigateTo(VoiceTaskViewModel.Screen.BACKUP_RESTORE)
                            scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_backups")
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Feedback, contentDescription = "Feedback") },
                        label = { Text("Feedback & Bug Reports") },
                        selected = currentScreen == VoiceTaskViewModel.Screen.FEEDBACK,
                        onClick = {
                            viewModel.navigateTo(VoiceTaskViewModel.Screen.FEEDBACK)
                            scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_feedback")
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.Help, contentDescription = "Help") },
                        label = { Text("User Guide & Tips") },
                        selected = currentScreen == VoiceTaskViewModel.Screen.USER_GUIDE,
                        onClick = {
                            viewModel.navigateTo(VoiceTaskViewModel.Screen.USER_GUIDE)
                            scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_guide")
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    
                    Text(
                        text = "THEME CONTROLLER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("System", "Light", "Dark").forEach { opt ->
                            ElevatedFilterChip(
                                selected = viewModel.themeOption == opt,
                                onClick = { viewModel.changeThemeOption(opt) },
                                label = { Text(opt, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f).testTag("theme_btn_$opt")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    
                    Text(
                        text = "Offline-First Local Ledger active.\nVersion 1.0.0",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp)
                    )
                }
            }
        ) {
            // Main Content Area
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                                Text(
                                    text = "VoisTask",
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        navigationIcon = {
                            if (!isTablet) {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "Sidebar Drawer Open",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        actions = {
                            val isSyncing = viewModel.isSyncing
                            IconButton(onClick = { viewModel.performCloudSync() }) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.CloudSync,
                                        contentDescription = "Trigger Cloud Sync",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                },
                floatingActionButton = {
                    if (currentScreen == VoiceTaskViewModel.Screen.MEMOS) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ElevatedButton(
                                onClick = { showQuickAddDialog = true },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.testTag("text_note_fab")
                            ) {
                                Icon(imageVector = Icons.Default.EditNote, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                                Text("New Note", fontWeight = FontWeight.SemiBold)
                            }
                            
                            ExtendedFloatingActionButton(
                                onClick = {
                                    if (isRecordingAuthorized) {
                                        showRecordSheet = true
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.testTag("record_voice_fab")
                            ) {
                                Icon(imageVector = Icons.Default.KeyboardVoice, contentDescription = null)
                                Text("Vois Recorder", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    when (currentScreen) {
                        VoiceTaskViewModel.Screen.MEMOS -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Search bar
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { viewModel.setSearchQuery(it) },
                                        placeholder = { Text("Search memos, notes, or tags...") },
                                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "SearchIcon") },
                                        trailingIcon = {
                                            if (searchQuery.isNotEmpty()) {
                                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                                }
                                            }
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(24.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("search_bar")
                                    )
                                }

                                // Categories tabs (Horizontal layout)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // "All" pill
                                    ElevatedFilterChip(
                                        selected = selectedCategory == "All",
                                        onClick = { viewModel.selectCategory("All") },
                                        label = { Text("All Memos") }
                                    )
                                    categories.forEach { cat ->
                                        ElevatedFilterChip(
                                            selected = selectedCategory == cat,
                                            onClick = { viewModel.selectCategory(cat) },
                                            label = { Text(cat) }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // List of tasks
                                if (filteredTasks.isEmpty()) {
                                    EmptyMemosState()
                                } else {
                                    LazyColumn(
                                        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 84.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(filteredTasks, key = { it.id }) { task ->
                                            VoiceTaskCard(
                                                task = task,
                                                viewModel = viewModel,
                                                onDelete = { viewModel.deleteTask(task) },
                                                onCompleteToggle = { viewModel.toggleTaskCompletion(task) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        VoiceTaskViewModel.Screen.FOLDERS -> {
                            FoldersManagerScreen(viewModel = viewModel)
                        }
                        VoiceTaskViewModel.Screen.COLLABORATION -> {
                            CollaborationScreen(viewModel = viewModel)
                        }
                        VoiceTaskViewModel.Screen.BACKUP_RESTORE -> {
                            BackupRestoreScreen(viewModel = viewModel)
                        }
                        VoiceTaskViewModel.Screen.USER_GUIDE -> {
                            UserGuideScreen()
                        }
                        VoiceTaskViewModel.Screen.FEEDBACK -> {
                            FeedbackScreen(viewModel = viewModel)
                        }
                    }

                    // Bottom sheet wrapper for recording dialog
                    if (showRecordSheet) {
                        VoiceRecordSheet(
                            viewModel = viewModel,
                            onDismiss = { showRecordSheet = false }
                        )
                    }

                    // Text Note / Quick Add Dialog
                    if (showQuickAddDialog) {
                        QuickAddNoteDialog(
                            viewModel = viewModel,
                            onDismiss = { showQuickAddDialog = false }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyMemosState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            modifier = Modifier.size(96.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = "Empty",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No voice memos found",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Hit the Vois Recorder button below to record your first dynamic audio task or memo instantly!",
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun VoiceTaskCard(
    task: VoiceTask,
    viewModel: VoiceTaskViewModel,
    onDelete: () -> Unit,
    onCompleteToggle: () -> Unit
) {
    val context = LocalContext.current
    val isPlayingThis = viewModel.playingTaskId == task.id
    var showEditMenu by remember { mutableStateOf(false) }
    var renameTitle by remember { mutableStateOf(task.title) }
    var renameNotes by remember { mutableStateOf(task.notes) }
    var isEditing by remember { mutableStateOf(false) }
    var showAlarmChangeDialog by remember { mutableStateOf(false) }

    if (showAlarmChangeDialog) {
        AlertDialog(
            onDismissRequest = { showAlarmChangeDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Schedule Task Alarm")
                }
            },
            text = {
                Column {
                    Text("Choose a reminder duration or clear existing alarm:")
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("15 Secs", "1 Min", "5 Mins", "10 Mins").forEach { durationStr ->
                            ElevatedButton(
                                onClick = {
                                    val addedMs = when (durationStr) {
                                        "15 Secs" -> 15000L
                                        "1 Min" -> 60000L
                                        "5 Mins" -> 300000L
                                        else -> 600000L
                                    }
                                    val newReminderTime = System.currentTimeMillis() + addedMs
                                    viewModel.updateTask(task.copy(reminderTime = newReminderTime))
                                    showAlarmChangeDialog = false
                                    Toast.makeText(context, "Alarm scheduled in $durationStr!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(durationStr, fontSize = 9.sp, maxLines = 1)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateTask(task.copy(reminderTime = null))
                        showAlarmChangeDialog = false
                        Toast.makeText(context, "Alarm cleared!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Clear Alarm", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAlarmChangeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (task.isCompleted) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_card_${task.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Checkbox(
                        checked = task.isCompleted,
                        onCheckedChange = { onCompleteToggle() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    if (isEditing) {
                        OutlinedTextField(
                            value = renameTitle,
                            onValueChange = { renameTitle = it },
                            label = { Text("Title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = task.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (task.isCompleted) {
                                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.onBackground
                            }
                        )
                    }
                }

                // Actions Menu button
                Box {
                    IconButton(onClick = { showEditMenu = true }) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = showEditMenu,
                        onDismissRequest = { showEditMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (isEditing) "Cancel Edit" else "Edit Details") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                isEditing = !isEditing
                                showEditMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Set / Edit Alarm") },
                            leadingIcon = { Icon(Icons.Default.NotificationsActive, contentDescription = null) },
                            onClick = {
                                showAlarmChangeDialog = true
                                showEditMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share Collab Link") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                val code = viewModel.generateWorkspaceInvitationCode(task)
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cb.setPrimaryClip(ClipData.newPlainText("collab_code", code))
                                Toast.makeText(context, "Copied Collaboration Link Code!", Toast.LENGTH_SHORT).show()
                                showEditMenu = false
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete", color = Color.Red) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                            onClick = {
                                onDelete()
                                showEditMenu = false
                            }
                        )
                    }
                }
            }

            // Expanded edit text fields or read-only notes output
            if (isEditing) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = renameNotes,
                    onValueChange = { renameNotes = it },
                    label = { Text("Memo Notes & Transcriptions") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = { isEditing = false }) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            viewModel.updateTask(
                                task.copy(
                                    title = renameTitle.ifBlank { "Untitled Note" },
                                    notes = renameNotes
                                )
                            )
                            isEditing = false
                        }
                    ) {
                        Text("Save Changes")
                    }
                }
            } else {
                if (task.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = task.notes,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }

            if (task.reminderTime != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .background(
                            color = Color(0xFFD4AF37).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = Color(0xFFD4AF37).copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { showAlarmChangeDialog = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = "Active Alarm",
                        tint = Color(0xFFD4AF37),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val formatter = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
                    Text(
                        text = "Alarm: ${formatter.format(Date(task.reminderTime!!))}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD4AF37)
                    )
                }
            }

            // Audio Player controls if file exists!
            if (task.audioPath != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.playVoiceMemo(task) },
                            modifier = Modifier.testTag("play_btn_${task.id}")
                        ) {
                            Icon(
                                imageVector = if (isPlayingThis && viewModel.isPlaying) {
                                    Icons.Default.Pause
                                } else {
                                    Icons.Default.PlayArrow
                                },
                                contentDescription = "Play/Pause Memo",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Progress Line
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        ) {
                            if (isPlayingThis) {
                                Slider(
                                    value = viewModel.playbackProgress,
                                    onValueChange = { viewModel.seekPlayback(it) },
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            } else {
                                LinearProgressIndicator(
                                    progress = { 0f },
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                )
                            }
                            
                            // Timer display
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val currentLabel = if (isPlayingThis) {
                                    formatTimerLabel(viewModel.playbackPositionMs)
                                } else {
                                    "00:00"
                                }
                                Text(
                                    text = currentLabel,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = formatTimerLabel(task.audioDurationMs),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Badges (Tag & Folder information!)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Folder/Category
                    SuggestionChip(
                        onClick = { viewModel.selectCategory(task.category) },
                        label = { Text(task.category, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.height(28.dp)
                    )

                    // Tags
                    if (task.tags.isNotBlank()) {
                        task.tags.split(",").forEach { tag ->
                            if (tag.trim().isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "#${tag.trim()}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Ledger sync status icon
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (task.isSynced) {
                        Icon(
                            imageVector = Icons.Default.CloudQueue,
                            contentDescription = "Synced",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cloud Syned", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                    } else {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = "Offline only",
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Offline Local", fontSize = 10.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}

// Format MS timestamp to MM:SS format helper
fun formatTimerLabel(ms: Long): String {
    val totalSecs = ms / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
}

@Composable
fun VoiceRecordSheet(
    viewModel: VoiceTaskViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var memoTitle by remember { mutableStateOf("") }
    var memoNotes by remember { mutableStateOf("") }
    var memoCategory by remember { mutableStateOf("Inbox") }
    var memoTags by remember { mutableStateOf("") }

    // Selected folders list lookup
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var showFoldersMenu by remember { mutableStateOf(false) }

    // Datetime picker simulation for user reminders
    var reminderTime by remember { mutableStateOf<Long?>(null) }
    var showReminderDialog by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = {
            if (viewModel.isRecording) {
                viewModel.cancelRecording()
            }
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .wrapContentHeight()
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "VoisTask Audio Recorder",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))

                // The audio recording state / waves!
                if (viewModel.isRecording) {
                    // Amplitude visualizer
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val list = viewModel.amlitudeList
                                val spacing = 6.dp.toPx()
                                val barWidth = 4.dp.toPx()
                                val startX = size.width - (list.size * (barWidth + spacing))
                                
                                list.forEachIndexed { idx, amp ->
                                    val x = startX + idx * (barWidth + spacing)
                                    val barHeight = amp * size.height * 0.8f
                                    val y = (size.height - barHeight) / 2
                                    drawRect(
                                        color = Color(0xFFD4AF37), // Dark Yellow accent
                                        topLeft = Offset(x, y),
                                        size = Size(barWidth, barHeight)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Recording audio memo...",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp
                    )
                    Text(
                        text = formatTimerLabel(viewModel.recordDurationMs),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { viewModel.cancelRecording() },
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.Red.copy(alpha = 0.1f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close, 
                                contentDescription = "Discard", 
                                tint = Color.Red,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Discard", 
                                color = Color.Red, 
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.stopRecording(
                                    title = memoTitle,
                                    notes = memoNotes,
                                    category = memoCategory,
                                    tags = memoTags,
                                    reminderTime = reminderTime
                                )
                                onDismiss()
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save, 
                                contentDescription = "Save Memo",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Save Task", 
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    // Start screen instructions
                    IconButton(
                        onClick = { viewModel.startRecording() },
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color(0xFFE30B5C), CircleShape) // Raspberry/Ruby Red
                            .testTag("start_rec_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardVoice,
                            contentDescription = "Tap to Record",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "TAP RED BUTTON TO START",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE30B5C)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // Metadata Forms (Title, Notes, Categories, Tags)
                OutlinedTextField(
                    value = memoTitle,
                    onValueChange = { memoTitle = it },
                    label = { Text("Task Title / Voice Name") },
                    placeholder = { Text("e.g. Discuss Q3 metrics") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_title")
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = memoNotes,
                    onValueChange = { memoNotes = it },
                    label = { Text("Transcription notes or extra context") },
                    modifier = Modifier.fillMaxWidth().testTag("add_notes")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Folder selector dropdown trigger
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Select Folder:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Box {
                        TextButton(onClick = { showFoldersMenu = true }) {
                            Text(memoCategory)
                            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = showFoldersMenu,
                            onDismissRequest = { showFoldersMenu = false }
                        ) {
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    onClick = {
                                        memoCategory = cat
                                        showFoldersMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = memoTags,
                    onValueChange = { memoTags = it },
                    label = { Text("Tags (comma separated)") },
                    placeholder = { Text("e.g. Urgent, Deliverable") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Reminder selector button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Reminder Alarm:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    TextButton(onClick = { showReminderDialog = true }) {
                        if (reminderTime == null) {
                            Text("No Schedule")
                        } else {
                            val formatter = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
                            Text(formatter.format(Date(reminderTime!!)))
                        }
                    }
                }

                // Reminder time Picker Simulation Dialog to keep code fully active and error free
                if (showReminderDialog) {
                    AlertDialog(
                        onDismissRequest = { showReminderDialog = false },
                        title = { Text("Schedule Task Reminder") },
                        text = {
                            Column {
                                Text("Choose simulated reminder duration trigger:")
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("15 Secs", "1 Min", "5 Mins", "10 Mins").forEach { minsString ->
                                        ElevatedButton(
                                            onClick = {
                                                val addedMs = when (minsString) {
                                                    "15 Secs" -> 15000L
                                                    "1 Min" -> 60000L
                                                    "5 Mins" -> 300000L
                                                    else -> 600000L
                                                }
                                                reminderTime = System.currentTimeMillis() + addedMs
                                                showReminderDialog = false
                                                Toast.makeText(context, "Reminder scheduled in $minsString!", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(minsString, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                reminderTime = null
                                showReminderDialog = false
                            }) {
                                Text("Clear Reminder")
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Safe exit if not recording
                if (!viewModel.isRecording) {
                    TextButton(
                        onClick = { onDismiss() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel & Go Back")
                    }
                }
            }
        }
    }
}

@Composable
fun QuickAddNoteDialog(
    viewModel: VoiceTaskViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Inbox") }
    var tags by remember { mutableStateOf("") }
    
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var showFoldersMenu by remember { mutableStateOf(false) }

    // Alarm reminder states
    var reminderTime by remember { mutableStateOf<Long?>(null) }
    var showReminderDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text("📝 Add Text Note Only") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("quick_title")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Task description notes...") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Select Folder:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Box {
                        TextButton(onClick = { showFoldersMenu = true }) {
                            Text(category)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = showFoldersMenu,
                            onDismissRequest = { showFoldersMenu = false }
                        ) {
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    onClick = {
                                        category = cat
                                        showFoldersMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    singleLine = true,
                    label = { Text("Tags (comma separated)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Reminder alarm row for text notes!
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Reminder Alarm:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    TextButton(onClick = { showReminderDialog = true }) {
                        if (reminderTime == null) {
                            Text("No Schedule")
                        } else {
                            val formatter = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
                            Text(formatter.format(Date(reminderTime!!)))
                        }
                    }
                }

                if (showReminderDialog) {
                    AlertDialog(
                        onDismissRequest = { showReminderDialog = false },
                        title = { Text("Schedule Task Reminder") },
                        text = {
                            Column {
                                Text("Choose simulated reminder duration trigger:")
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("15 Secs", "1 Min", "5 Mins", "10 Mins").forEach { minsString ->
                                        ElevatedButton(
                                            onClick = {
                                                val addedMs = when (minsString) {
                                                    "15 Secs" -> 15000L
                                                    "1 Min" -> 60000L
                                                    "5 Mins" -> 300000L
                                                    else -> 600000L
                                                }
                                                reminderTime = System.currentTimeMillis() + addedMs
                                                showReminderDialog = false
                                                Toast.makeText(context, "Reminder scheduled in $minsString!", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(minsString, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                reminderTime = null
                                showReminderDialog = false
                            }) {
                                Text("Clear Reminder")
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.addTask(title, notes, category, tags, reminderTime)
                    onDismiss()
                }
            ) {
                Text("Insert Task")
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun FoldersManagerScreen(viewModel: VoiceTaskViewModel) {
    var newFolderText by remember { mutableStateOf("") }
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val allTasks by viewModel.allTasks.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Folders and Tags Organization", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Create New Folder / Category", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newFolderText,
                        onValueChange = { newFolderText = it },
                        placeholder = { Text("e.g., Client meeting") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("folder_input")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (newFolderText.isNotBlank()) {
                                viewModel.addTask(
                                    title = "Folder Initializer Memo",
                                    notes = "This note acts as physical placeholder initializer for category: $newFolderText.",
                                    category = newFolderText.trim(),
                                    tags = "System"
                                )
                                newFolderText = ""
                            }
                        },
                        modifier = Modifier.testTag("add_folder")
                    ) {
                        Text("Add")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Active Folders List", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))

        categories.forEach { folder ->
            val count = allTasks.count { it.category == folder }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { viewModel.selectCategory(folder); viewModel.navigateTo(VoiceTaskViewModel.Screen.MEMOS) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(folder, fontWeight = FontWeight.Medium)
                    }
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "$count memos",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CollaborationScreen(viewModel: VoiceTaskViewModel) {
    var workspaceCode by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Social Collaboration Hub", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Generate and import shared notes across your team. Instantly synchronize voice workspace files using encrypted coordination ledger codes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(24.dp))

        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🔗 Import Shared Note Workspace", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = workspaceCode,
                    onValueChange = { workspaceCode = it },
                    placeholder = { Text("Paste invite code e.g. VOIS-COLLAB-...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        val success = viewModel.importSharedWorkspaceMemo(workspaceCode)
                        if (success) {
                            Toast.makeText(context, "Successfully downloaded shared voice note!", Toast.LENGTH_LONG).show()
                            workspaceCode = ""
                        } else {
                            Toast.makeText(context, "Invalid collaborator link formatting. Verify and retry.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Connect shared Ledger")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("📬 Instructions for Sharing", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "1. Navigate to the task list screen.\n" +
                           "2. Tap the actions menu button on any voice task card.\n" +
                           "3. Click 'Share Collab Link' to copy the secure link directly to your device clipboard.\n" +
                           "4. Send it to colleagues to allow them to sync.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun BackupRestoreScreen(viewModel: VoiceTaskViewModel) {
    val context = LocalContext.current
    var backupInputText by remember { mutableStateOf("") }
    val backupStatus = viewModel.backupStatusText

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Local Backups & Cloud Ledger", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Back up your local database files or restore them manually to prevent data loss. Secure transfers bypass servers for absolute personal privacy.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        
        if (backupStatus != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = backupStatus,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("📤 Export Device Backup", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Generate a single secure text string of all local memos, folders, and checklists.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        viewModel.performExportBackup { jsonString ->
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("backup_data", jsonString))
                            Toast.makeText(context, "Backup copied to clipboard!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Export & Copy Backup JSON String")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("📥 Import / Restore Device Backup", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = backupInputText,
                    onValueChange = { backupInputText = it },
                    label = { Text("Paste Backup JSON string...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (backupInputText.isNotBlank()) {
                            viewModel.performImportBackup(backupInputText) { success ->
                                if (success) {
                                    backupInputText = ""
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Parse and Restore Database")
                }
            }
        }
    }
}

@Composable
fun FeedbackScreen(viewModel: VoiceTaskViewModel) {
    var email by remember { mutableStateOf("") }
    var feedbackContent by remember { mutableStateOf("") }
    var categorySelection by remember { mutableStateOf("Bug Report") }
    var showCategoryMenu by remember { mutableStateOf(false) }
    
    val submitted = viewModel.feedbackSubmitted

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Feedback & Developer Reporting", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Report bugs, suggest features, or praise our custom lemon themes directly to developer logs.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (submitted) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Feedback Submitted successfully!", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Your reports are synchronized instantly to clean dev server queues.\nThank you for assisting VoisTask progress!", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
            }
        } else {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Report Details", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Feedback Category:")
                        Box {
                            TextButton(onClick = { showCategoryMenu = true }) {
                                Text(categorySelection)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(expanded = showCategoryMenu, onDismissRequest = { showCategoryMenu = false }) {
                                listOf("Bug Report", "Feature Suggestion", "Lemon Aesthetic feedback", "Offline ledger inquiry").forEach { cat ->
                                    DropdownMenuItem(text = { Text(cat) }, onClick = { categorySelection = cat; showCategoryMenu = false })
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Your Email (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = feedbackContent,
                        onValueChange = { feedbackContent = it },
                        label = { Text("Describe details or bugs...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (feedbackContent.isNotBlank()) {
                                viewModel.sendUserFeedback(categorySelection, feedbackContent, email)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Upload Secure feedback bundle")
                    }
                }
            }
        }
    }
}

@Composable
fun UserGuideScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("VoisTask Handheld User Guide", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(12.dp))

        val guides = listOf(
            Triple("🎤 Voice Memo Memos", "Tap 'Vois Recorder' FAB to open the multi-functional audio dashboard. Press raw microphone button to record. You can type titles, extra notes, folder context and schedule reminders before saving directly to database.", Icons.Default.Mic),
            Triple("📁 Folders & Tags filtering", "Organize memos by custom labels or folder directories. Tap Folder tags on navigation bars or inside task cards to filter current list targets instantly.", Icons.Default.FolderOpen),
            Triple("⏰ Reminders Scheduling", "Set durations (from 15 seconds test alarm to 10 minutes) inside recording dashboard. Even in-app background routines will activate bright notifications overlays complete with ringtone chime sound!", Icons.Default.NotificationsActive),
            Triple("☁️ Offline Privacy & Ledger Sync", "VoisTask stores 100% of data locally first. Secure Cloud Sync uploaded simulation matches modern client requests quickly. Tap sync icon to backup and verify.", Icons.Default.CloudQueue),
            Triple("🎨 Customizable Lemon Themes", "Choose bright Yellow-lemon & White Light combinations, or rich contrast dark mustard Dark settings instantly under Sidebar options panel.", Icons.Default.Palette)
        )

        guides.forEach { (title, desc, icon) ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp)) {
                    Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(desc, fontSize = 12.sp, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
