package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.TaskPriority
import com.example.data.VoiceTask
import com.example.ui.theme.BotanicalAmber
import com.example.ui.theme.BotanicalSage
import com.example.ui.theme.FrauncesFontFamily
import com.example.ui.theme.StudioMonoFontFamily

@Composable
fun AppPasscodeLockScreen(
    correctPasscode: String,
    onUnlockSuccess: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = if (isError) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                },
                border = BorderStroke(
                    1.dp,
                    if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                    else MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Vault Lock",
                        tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Finance & Task",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Enter your 4-digit passcode to open your botanical workspace",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 300.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 1..4) {
                    val active = enteredPin.length >= i
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                color = when {
                                    isError -> MaterialTheme.colorScheme.error
                                    active -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                            .border(
                                width = 1.dp,
                                color = when {
                                    isError -> MaterialTheme.colorScheme.error
                                    active -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.outline
                                },
                                shape = CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier.height(20.dp),
                contentAlignment = Alignment.Center
            ) {
                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            Column(
                modifier = Modifier.widthIn(max = 276.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val buttons = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("Clear", "0", "Delete")
                )

                buttons.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        row.forEach { digit ->
                            val isUtility = digit == "Clear" || digit == "Delete"
                            Surface(
                                shape = CircleShape,
                                color = if (isUtility) {
                                    MaterialTheme.colorScheme.background
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                border = if (isUtility) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        if (isError) {
                                            isError = false
                                            errorMessage = ""
                                        }
                                        when (digit) {
                                            "Clear" -> enteredPin = ""
                                            "Delete" -> {
                                                if (enteredPin.isNotEmpty()) {
                                                    enteredPin = enteredPin.dropLast(1)
                                                }
                                            }
                                            else -> {
                                                if (enteredPin.length < 4) {
                                                    enteredPin += digit
                                                    if (enteredPin.length == 4) {
                                                        if (enteredPin == correctPasscode) {
                                                            onUnlockSuccess()
                                                        } else {
                                                            isError = true
                                                            errorMessage = "Incorrect passcode. Please try again."
                                                            enteredPin = ""
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    when (digit) {
                                        "Clear" -> Text(
                                            text = "CLR",
                                            fontWeight = FontWeight.SemiBold,
                                            fontFamily = StudioMonoFontFamily,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        "Delete" -> Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                                            contentDescription = "Backspace",
                                            tint = MaterialTheme.colorScheme.onBackground,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        else -> Text(
                                            text = digit,
                                            fontFamily = FrauncesFontFamily,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Powered by AKILAS TECH",
                fontFamily = StudioMonoFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun BotanicalTopHeader(
    currentScreen: VoiceTaskViewModel.Screen,
    isDark: Boolean,
    onBackToPrevious: () -> Unit,
    onQuickAddTextNote: () -> Unit,
    onOpenAiStudio: () -> Unit,
    onToggleTheme: () -> Unit,
    onNavigate: (VoiceTaskViewModel.Screen) -> Unit
) {
    var showMoreMenu by remember { mutableStateOf(false) }

    val isUtilityScreen = currentScreen == VoiceTaskViewModel.Screen.FOLDERS ||
        currentScreen == VoiceTaskViewModel.Screen.BACKUP_RESTORE ||
        currentScreen == VoiceTaskViewModel.Screen.USER_GUIDE ||
        currentScreen == VoiceTaskViewModel.Screen.FEEDBACK

    val (title, subtitle) = when (currentScreen) {
        VoiceTaskViewModel.Screen.MEMOS -> "Finance & Task" to "Powered by AKILAS TECH"
        VoiceTaskViewModel.Screen.NOTEPAD -> "Notepad" to "Notes & Voice Clips"
        VoiceTaskViewModel.Screen.FINANCE -> "Finance" to "SMS Ledger & AI Insights"
        VoiceTaskViewModel.Screen.FOLDERS -> "Folders" to "Curated Workspaces"
        VoiceTaskViewModel.Screen.SETTINGS -> "Settings" to "Studio, AI & Connectors"
        VoiceTaskViewModel.Screen.BACKUP_RESTORE -> "Backup & Restore" to "Local JSON Archive"
        VoiceTaskViewModel.Screen.USER_GUIDE -> "User Guide" to "Workflow Reference"
        VoiceTaskViewModel.Screen.FEEDBACK -> "Feedback" to "Share Notes with AKILAS TECH"
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isUtilityScreen) {
                        IconButton(
                            onClick = onBackToPrevious,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("utility_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Finance & Task",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = onOpenAiStudio,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = BotanicalSage.copy(alpha = 0.35f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("top_ai_studio_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Open AI Studio",
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    if (currentScreen == VoiceTaskViewModel.Screen.MEMOS) {
                        OutlinedButton(
                            onClick = onQuickAddTextNote,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("text_note_fab")
                        ) {
                            Icon(
                                imageVector = Icons.Default.EditNote,
                                contentDescription = "Add Text Note",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Text Note",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { showMoreMenu = true },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("studio_more_menu_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (isDark) "Switch to Stone Light" else "Switch to Deep Moss") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onToggleTheme()
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Manage Folders") },
                                leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    onNavigate(VoiceTaskViewModel.Screen.FOLDERS)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Backup & Restore") },
                                leadingIcon = { Icon(Icons.Default.Backup, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    onNavigate(VoiceTaskViewModel.Screen.BACKUP_RESTORE)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("User Guide") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    onNavigate(VoiceTaskViewModel.Screen.USER_GUIDE)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Send Feedback") },
                                leadingIcon = { Icon(Icons.Default.Feedback, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    onNavigate(VoiceTaskViewModel.Screen.FEEDBACK)
                                }
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
fun BotanicalBottomNavBar(
    currentScreen: VoiceTaskViewModel.Screen,
    onNavigate: (VoiceTaskViewModel.Screen) -> Unit,
    onRecordClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            BotanicalNavTab(
                label = "Memos",
                icon = Icons.Default.GraphicEq,
                selected = currentScreen == VoiceTaskViewModel.Screen.MEMOS,
                onClick = { onNavigate(VoiceTaskViewModel.Screen.MEMOS) },
                modifier = Modifier.weight(1f).testTag("nav_memos")
            )
            BotanicalNavTab(
                label = "Notepad",
                icon = Icons.Default.EditNote,
                selected = currentScreen == VoiceTaskViewModel.Screen.NOTEPAD,
                onClick = { onNavigate(VoiceTaskViewModel.Screen.NOTEPAD) },
                modifier = Modifier.weight(1f).testTag("nav_notepad")
            )

            // Raised Center Botanical Record Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Surface(
                    onClick = onRecordClick,
                    shape = CircleShape,
                    color = BotanicalSage,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .size(58.dp)
                        .shadow(4.dp, CircleShape)
                        .testTag("record_voice_fab")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.KeyboardVoice,
                            contentDescription = "Record Voice Memo",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            BotanicalNavTab(
                label = "Finance",
                icon = Icons.Default.AccountBalanceWallet,
                selected = currentScreen == VoiceTaskViewModel.Screen.FINANCE,
                onClick = { onNavigate(VoiceTaskViewModel.Screen.FINANCE) },
                modifier = Modifier.weight(1f).testTag("nav_finance")
            )
            BotanicalNavTab(
                label = "Settings",
                icon = Icons.Default.Tune,
                selected = currentScreen == VoiceTaskViewModel.Screen.SETTINGS ||
                    currentScreen == VoiceTaskViewModel.Screen.FOLDERS ||
                    currentScreen == VoiceTaskViewModel.Screen.BACKUP_RESTORE ||
                    currentScreen == VoiceTaskViewModel.Screen.USER_GUIDE ||
                    currentScreen == VoiceTaskViewModel.Screen.FEEDBACK,
                onClick = { onNavigate(VoiceTaskViewModel.Screen.SETTINGS) },
                modifier = Modifier.weight(1f).testTag("nav_settings")
            )
        }
    }
}

@Composable
private fun BotanicalNavTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    else androidx.compose.ui.graphics.Color.Transparent
                )
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
fun BotanicalTabletRail(
    currentScreen: VoiceTaskViewModel.Screen,
    isDark: Boolean,
    onNavigate: (VoiceTaskViewModel.Screen) -> Unit,
    onRecordClick: () -> Unit,
    onOpenAiStudio: () -> Unit,
    onToggleTheme: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(88.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    onClick = onRecordClick,
                    shape = CircleShape,
                    color = BotanicalSage,
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .size(54.dp)
                        .testTag("record_voice_fab")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.KeyboardVoice,
                            contentDescription = "Record Voice Memo",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                val destinations = listOf(
                    Triple(VoiceTaskViewModel.Screen.MEMOS, Icons.Default.GraphicEq, "Memos"),
                    Triple(VoiceTaskViewModel.Screen.NOTEPAD, Icons.Default.EditNote, "Notepad"),
                    Triple(VoiceTaskViewModel.Screen.FINANCE, Icons.Default.AccountBalanceWallet, "Finance"),
                    Triple(VoiceTaskViewModel.Screen.FOLDERS, Icons.Default.FolderOpen, "Folders"),
                    Triple(VoiceTaskViewModel.Screen.BACKUP_RESTORE, Icons.Default.Backup, "Backups"),
                    Triple(VoiceTaskViewModel.Screen.USER_GUIDE, Icons.AutoMirrored.Filled.Help, "Guide"),
                    Triple(VoiceTaskViewModel.Screen.FEEDBACK, Icons.Default.Feedback, "Feedback"),
                    Triple(VoiceTaskViewModel.Screen.SETTINGS, Icons.Default.Tune, "Settings")
                )

                destinations.forEach { (scr, icon, lbl) ->
                    val selected = currentScreen == scr
                    IconButton(
                        onClick = { onNavigate(scr) },
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                else androidx.compose.ui.graphics.Color.Transparent
                            )
                            .testTag("rail_${lbl.lowercase()}")
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = lbl,
                            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = onOpenAiStudio,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("rail_ai_studio_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Open AI Studio",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Toggle Theme",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun InAppReminderDialog(
    activeReminder: VoiceTask,
    viewModel: VoiceTaskViewModel
) {
    val context = LocalContext.current
    val priority = remember(activeReminder.priority) { TaskPriority.fromLevel(activeReminder.priority) }
    val soundName = remember(activeReminder.alertSound) { AlarmSoundCatalog.displayName(activeReminder.alertSound) }
    val isMuted = AlarmSoundEngine.isAlarmMuted

    DisposableEffect(activeReminder.id, activeReminder.alertSound, viewModel.enableVibration) {
        AlarmSoundEngine.startAlarmLoop(
            context = context,
            soundSpec = activeReminder.alertSound,
            taskAudioPath = activeReminder.audioPath,
            enableVibration = viewModel.enableVibration
        )
        onDispose {
            AlarmSoundEngine.stopAll()
        }
    }

    AlertDialog(
        onDismissRequest = { viewModel.dismissReminder() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(26.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = BotanicalAmber.copy(alpha = 0.20f),
                        border = BorderStroke(1.dp, BotanicalAmber.copy(alpha = 0.55f)),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "Active Alarm",
                                tint = BotanicalAmber,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Reminder Alarm",
                            fontFamily = FrauncesFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 19.sp
                        )
                        Text(
                            text = if (isMuted) "Sound muted" else "Ringing · $soundName",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = {
                        AlarmSoundEngine.setAlarmMuted(
                            context = context,
                            muted = !isMuted,
                            soundSpec = activeReminder.alertSound,
                            taskAudioPath = activeReminder.audioPath
                        )
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("alarm_mute_toggle_btn")
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isMuted) "Unmute alarm sound" else "Mute alarm sound",
                        tint = if (isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = activeReminder.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = priority.label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    if (activeReminder.category.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = activeReminder.category,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    if (viewModel.enableVibration) {
                        Icon(
                            imageVector = Icons.Default.Vibration,
                            contentDescription = "Vibration active",
                            tint = BotanicalAmber,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Text(
                    text = activeReminder.notes.ifBlank { "No transcription notes attached." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (activeReminder.audioPath != null) {
                    val isPlayingThis = viewModel.playingTaskId == activeReminder.id
                    FilledTonalButton(
                        onClick = { viewModel.playVoiceMemo(activeReminder) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlayingThis && viewModel.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isPlayingThis && viewModel.isPlaying) "Pause Voice Memo" else "Play Voice Memo",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "SNOOZE REMINDER",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(5 to "+5m", 10 to "+10m", 30 to "+30m", 60 to "+1h").forEach { (mins, label) ->
                            OutlinedButton(
                                onClick = {
                                    viewModel.snoozeReminder(activeReminder, mins)
                                    Toast.makeText(context, "Snoozed for $mins mins", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .heightIn(min = 40.dp)
                                    .testTag("snooze_${mins}_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Snooze,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!activeReminder.isCompleted) {
                        viewModel.toggleTaskCompletion(activeReminder)
                    }
                    viewModel.dismissReminder()
                    Toast.makeText(context, "Task completed!", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("alarm_complete_btn")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(
                onClick = { viewModel.dismissReminder() },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("alarm_dismiss_btn")
            ) {
                Text("Dismiss")
            }
        }
    )
}
