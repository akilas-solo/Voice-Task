package com.example.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.TaskPriority
import com.example.data.TaskSortOption
import com.example.data.TelegramConfig
import com.example.data.VoiceTask
import com.example.ui.theme.BotanicalAmber
import com.example.ui.theme.BotanicalSage
import com.example.ui.theme.FrauncesFontFamily
import com.example.ui.theme.StudioMonoFontFamily
import com.example.ui.theme.StudioRecordRed
import com.example.ui.theme.TelegramAzure
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MemosWorkspaceScreen(
    viewModel: VoiceTaskViewModel,
    filteredTasks: List<VoiceTask>,
    categories: List<String>,
    selectedCategory: String,
    searchQuery: String,
    onOpenAiForTask: (VoiceTask) -> Unit
) {
    val sortPreference by viewModel.sortPreference.collectAsStateWithLifecycle()
    val (sortOption, sortDescending) = sortPreference
    var showSortMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Pill Search Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search memos, notes, or #tags...", fontSize = 14.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Memos",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
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
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("search_bar")
            )
        }

        // Category Filter Pills + Pinned Sort Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .padding(end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BotanicalFilterChip(
                    label = "All Memos",
                    selected = selectedCategory == "All",
                    onClick = { viewModel.selectCategory("All") }
                )
                categories.forEach { cat ->
                    BotanicalFilterChip(
                        label = cat,
                        selected = selectedCategory == cat,
                        onClick = { viewModel.selectCategory(cat) }
                    )
                }
            }

            VerticalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier
                    .height(22.dp)
                    .padding(end = 8.dp)
            )

            // Pinned Sort Mode Chip
            Box {
                Surface(
                    onClick = { showSortMenu = true },
                    shape = RoundedCornerShape(20.dp),
                    color = if (sortOption != TaskSortOption.CHRONOLOGICAL) {
                        BotanicalSage.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (sortOption != TaskSortOption.CHRONOLOGICAL) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    ),
                    modifier = Modifier
                        .heightIn(min = 38.dp)
                        .testTag("sort_mode_chip")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort tasks",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = sortOption.compactLabel(sortDescending),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    TaskSortOption.entries.forEach { option ->
                        val isSelected = sortOption == option
                        val optionIcon = when (option) {
                            TaskSortOption.CHRONOLOGICAL -> Icons.Default.Schedule
                            TaskSortOption.PRIORITY -> Icons.Default.Flag
                            TaskSortOption.CATEGORY -> Icons.Default.SortByAlpha
                        }
                        val subtitle = when (option) {
                            TaskSortOption.CHRONOLOGICAL -> "Newest or oldest created"
                            TaskSortOption.PRIORITY -> "Urgent, High, Medium, Low"
                            TaskSortOption.CATEGORY -> "Alphabetical by folder & title"
                        }
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = option.label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = subtitle,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = optionIcon,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingIcon = {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            onClick = {
                                viewModel.setSortOption(option)
                                showSortMenu = false
                            },
                            modifier = Modifier.testTag("sort_option_${option.name.lowercase()}")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Sort Direction Toggle Button
            Surface(
                onClick = { viewModel.toggleSortDirection() },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .size(38.dp)
                    .testTag("sort_direction_btn")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (sortDescending) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        contentDescription = "Flip sort order (${sortOption.directionLabel(sortDescending)})",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (filteredTasks.isEmpty()) {
            BotanicalEmptyMemosState()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredTasks, key = { it.id }) { task ->
                    Box(modifier = Modifier.animateItem()) {
                        VoiceTaskCard(
                            task = task,
                            viewModel = viewModel,
                            onDelete = { viewModel.deleteTask(task) },
                            onCompleteToggle = { viewModel.toggleTaskCompletion(task) },
                            onOpenAi = { onOpenAiForTask(task) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BotanicalFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) BotanicalSage else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        modifier = modifier.heightIn(min = 38.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun BotanicalEmptyMemosState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .widthIn(max = 260.dp)
                .aspectRatio(4f / 3f)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_botanical_empty),
                contentDescription = "Quiet Nordic botanical studio illustration",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(22.dp))
        Text(
            text = "A Quiet Canvas",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap the center Sage microphone below to capture your first voice memo, or add a quick text note above.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 310.dp)
        )
    }
}

@Composable
fun VoiceTaskCard(
    task: VoiceTask,
    viewModel: VoiceTaskViewModel,
    onDelete: () -> Unit,
    onCompleteToggle: () -> Unit,
    onOpenAi: () -> Unit = {}
) {
    val context = LocalContext.current
    val isPlayingThis = viewModel.playingTaskId == task.id
    var showEditMenu by remember { mutableStateOf(false) }
    var renameTitle by remember(task.id, task.title) { mutableStateOf(task.title) }
    var renameNotes by remember(task.id, task.notes) { mutableStateOf(task.notes) }
    var renameIcon by remember(task.id, task.iconName) { mutableStateOf(task.iconName) }
    var renameAlertSound by remember(task.id, task.alertSound) { mutableStateOf(task.alertSound) }
    var alarmNotifyTelegram by remember(task.id, task.notifyTelegram) { mutableStateOf(task.notifyTelegram) }
    var renamePriority by remember(task.id, task.priority) { mutableStateOf(TaskPriority.fromLevel(task.priority)) }
    var isEditing by remember { mutableStateOf(false) }
    var showAlarmChangeDialog by remember { mutableStateOf(false) }
    var isCardExpanded by remember { mutableStateOf(false) }
    val isExpanded = isCardExpanded || isPlayingThis || isEditing

    if (showAlarmChangeDialog) {
        ReminderScheduleDialog(
            initialReminderTime = task.reminderTime,
            selectedSound = renameAlertSound,
            onSoundSelected = { renameAlertSound = it },
            hasAudio = (task.audioPath != null),
            taskAudioPath = task.audioPath,
            notifyTelegram = alarmNotifyTelegram,
            onNotifyTelegramChanged = { alarmNotifyTelegram = it },
            onSave = { newReminderTime ->
                viewModel.updateTask(
                    task.copy(
                        reminderTime = newReminderTime,
                        alertSound = renameAlertSound,
                        notifyTelegram = alarmNotifyTelegram
                    )
                )
                showAlarmChangeDialog = false
                val feedback = if (newReminderTime != null) {
                    "Alarm set for ${ReminderDateTimeHelper.formatScheduledDateTime(newReminderTime)}"
                } else {
                    "Alarm cleared"
                }
                Toast.makeText(context, feedback, Toast.LENGTH_SHORT).show()
            },
            onDismiss = {
                alarmNotifyTelegram = task.notifyTelegram
                showAlarmChangeDialog = false
            }
        )
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (task.isCompleted) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (isExpanded) 1.5.dp else 1.dp,
            color = if (isExpanded) BotanicalSage else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_card_${task.id}")
            .clickable { isCardExpanded = !isCardExpanded }
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
                            checkedColor = MaterialTheme.colorScheme.primary,
                            checkmarkColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = getIconForName(task.iconName),
                                contentDescription = "Task Category Icon",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    if (isEditing) {
                        OutlinedTextField(
                            value = renameTitle,
                            onValueChange = { renameTitle = it },
                            label = { Text("Title") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = task.title,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                                color = if (task.isCompleted) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TaskPriorityBadge(
                                    priority = TaskPriority.fromLevel(task.priority),
                                    onSelectPriority = { selectedPriority ->
                                        viewModel.updateTaskPriority(task, selectedPriority)
                                    },
                                    testTag = "task_priority_badge_${task.id}"
                                )
                                if (task.audioPath != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.GraphicEq,
                                            contentDescription = "Has Voice Recording",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = formatTimerLabel(task.audioDurationMs),
                                            fontFamily = StudioMonoFontFamily,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                if (task.reminderTime != null) {
                                    val compactFormatter = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.NotificationsActive,
                                            contentDescription = "Has Set Alarm",
                                            tint = BotanicalAmber,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = compactFormatter.format(Date(task.reminderTime)),
                                            fontSize = 11.sp,
                                            color = BotanicalAmber,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1
                                        )
                                    }
                                }
                                if (!isExpanded && task.notes.isNotBlank() && task.audioPath == null) {
                                    Text(
                                        text = task.notes.take(24) + if (task.notes.length > 24) "..." else "",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                Box {
                    IconButton(
                        onClick = { showEditMenu = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More Task Actions",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                            text = { Text("AI Transcribe / Draft") },
                            leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                showEditMenu = false
                                onOpenAi()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Send to Telegram") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = TelegramAzure) },
                            onClick = {
                                showEditMenu = false
                                viewModel.sendTaskToTelegram(task) { _, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                onDelete()
                                showEditMenu = false
                            }
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = spring(dampingRatio = 0.85f)) + fadeIn(),
                exit = shrinkVertically(animationSpec = spring(dampingRatio = 0.85f)) + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (isEditing) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = renameNotes,
                            onValueChange = { renameNotes = it },
                            label = { Text("Memo Notes & Transcriptions") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TaskPrioritySelector(
                            selectedPriority = renamePriority,
                            onPrioritySelected = { renamePriority = it }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TaskIconSelector(
                            selectedIcon = renameIcon,
                            onIconSelected = { renameIcon = it }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SoundAlertSelector(
                            selectedSound = renameAlertSound,
                            onSoundSelected = { renameAlertSound = it },
                            hasAudio = (task.audioPath != null),
                            taskAudioPath = task.audioPath
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(onClick = { isEditing = false }) {
                                Text("Cancel")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    viewModel.updateTask(
                                        task.copy(
                                            title = renameTitle.ifBlank { "Untitled Note" },
                                            notes = renameNotes,
                                            iconName = renameIcon,
                                            alertSound = renameAlertSound,
                                            priority = renamePriority.level
                                        )
                                    )
                                    isEditing = false
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Save Changes")
                            }
                        }
                    } else if (task.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "NOTES & TRANSCRIPTION",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    letterSpacing = 0.7.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = task.notes,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    if (task.reminderTime != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        val scheduledLabel = ReminderDateTimeHelper.formatScheduledDateTime(task.reminderTime)
                        val remainingLabel = ReminderDateTimeHelper.formatRelativeRemaining(task.reminderTime)
                        val soundLabel = AlarmSoundCatalog.displayName(task.alertSound)
                        Surface(
                            onClick = { showAlarmChangeDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            color = BotanicalAmber.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, BotanicalAmber.copy(alpha = 0.45f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = "Active Alarm",
                                    tint = BotanicalAmber,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "$scheduledLabel ($remainingLabel) · $soundLabel",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BotanicalAmber,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (task.audioPath != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { viewModel.playVoiceMemo(task) },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("play_btn_${task.id}")
                                ) {
                                    Icon(
                                        imageVector = if (isPlayingThis && viewModel.isPlaying) {
                                            Icons.Default.PauseCircle
                                        } else {
                                            Icons.Default.PlayCircle
                                        },
                                        contentDescription = "Play or Pause Memo",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                ) {
                                    ScrubbableAudioWaveform(
                                        seedKey = task.id,
                                        durationMs = task.audioDurationMs,
                                        progress = if (isPlayingThis) viewModel.playbackProgress else 0f,
                                        isPlaying = isPlayingThis && viewModel.isPlaying,
                                        activeColor = MaterialTheme.colorScheme.primary,
                                        inactiveColor = BotanicalSage.copy(alpha = 0.45f),
                                        onSeek = { ratio ->
                                            if (isPlayingThis) {
                                                viewModel.seekPlayback(ratio)
                                            } else {
                                                viewModel.playVoiceMemo(task)
                                            }
                                        }
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (isPlayingThis) formatTimerLabel(viewModel.playbackPositionMs) else "00:00",
                                            fontFamily = StudioMonoFontFamily,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = formatTimerLabel(task.audioDurationMs),
                                            fontFamily = StudioMonoFontFamily,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Surface(
                                onClick = { viewModel.selectCategory(task.category) },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Text(
                                    text = task.category,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }

                            if (task.tags.isNotBlank()) {
                                val firstTag = task.tags.split(",").firstOrNull { it.trim().isNotEmpty() }?.trim()
                                if (!firstTag.isNullOrBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    ) {
                                        Text(
                                            text = "#$firstTag",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(
                                onClick = onOpenAi,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("ai_task_${task.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI Actions for Memo",
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("AI", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }

                            val isSendingTg = viewModel.sendingTaskIds.contains(task.id)
                            FilledTonalButton(
                                onClick = {
                                    viewModel.sendTaskToTelegram(task) { _, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = !isSendingTg,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = TelegramAzure.copy(alpha = 0.14f),
                                    contentColor = TelegramAzure
                                ),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("send_telegram_task_${task.id}")
                            ) {
                                if (isSendingTg) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = TelegramAzure
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send to Telegram",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (task.lastTelegramSentAt != null) "Sent" else "Telegram",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

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
    var memoIcon by remember { mutableStateOf("Mic") }
    var memoAlertSound by remember { mutableStateOf("App Default") }
    var memoPriority by remember { mutableStateOf(TaskPriority.MEDIUM) }

    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var showFoldersMenu by remember { mutableStateOf(false) }

    var reminderTime by remember { mutableStateOf<Long?>(null) }
    var notifyTelegram by remember { mutableStateOf(true) }
    var showReminderDialog by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val notesFocusRequester = remember { FocusRequester() }
    val tagsFocusRequester = remember { FocusRequester() }

    val pulseTransition = rememberInfiniteTransition(label = "record_halo")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse_scale"
    )

    Dialog(
        onDismissRequest = {
            if (viewModel.isRecording) {
                viewModel.cancelRecording()
            }
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .widthIn(min = 280.dp, max = 560.dp)
                .fillMaxWidth()
                .wrapContentHeight()
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Voice Studio Recorder",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (viewModel.isRecording) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, BotanicalSage),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(104.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            StudioLiveWaveform(
                                amplitudes = viewModel.amlitudeList,
                                primaryColor = MaterialTheme.colorScheme.primary,
                                secondaryColor = BotanicalSage
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "CAPTURING AUDIO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = formatTimerLabel(viewModel.recordDurationMs),
                        fontFamily = StudioMonoFontFamily,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.cancelRecording() },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Discard", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Discard", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        Button(
                            onClick = {
                                viewModel.stopRecording(
                                    title = memoTitle,
                                    notes = memoNotes,
                                    category = memoCategory,
                                    tags = memoTags,
                                    reminderTime = reminderTime,
                                    alertSound = memoAlertSound,
                                    iconName = memoIcon,
                                    notifyTelegram = notifyTelegram,
                                    priority = memoPriority.level
                                )
                                onDismiss()
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = "Save Memo", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Task", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(92.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(BotanicalSage.copy(alpha = 0.32f))
                        )
                        IconButton(
                            onClick = { viewModel.startRecording() },
                            modifier = Modifier
                                .size(74.dp)
                                .clip(CircleShape)
                                .background(StudioRecordRed)
                                .testTag("start_rec_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardVoice,
                                contentDescription = "Tap to Record",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "TAP TO START RECORDING",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = memoTitle,
                    onValueChange = { memoTitle = it },
                    label = { Text("Task Title / Voice Name") },
                    placeholder = { Text("e.g. Discuss Q3 metrics") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(onNext = { notesFocusRequester.requestFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_title")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = memoNotes,
                    onValueChange = { memoNotes = it },
                    label = { Text("Transcription notes or extra context") },
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(onNext = { tagsFocusRequester.requestFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(notesFocusRequester)
                        .testTag("add_notes")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Folder", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
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

                OutlinedTextField(
                    value = memoTags,
                    onValueChange = { memoTags = it },
                    label = { Text("Tags (comma separated)") },
                    placeholder = { Text("e.g. Urgent, Deliverable") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(tagsFocusRequester)
                )

                Spacer(modifier = Modifier.height(14.dp))
                TaskPrioritySelector(
                    selectedPriority = memoPriority,
                    onPrioritySelected = { memoPriority = it }
                )

                Spacer(modifier = Modifier.height(14.dp))
                TaskIconSelector(
                    selectedIcon = memoIcon,
                    onIconSelected = { memoIcon = it }
                )

                Spacer(modifier = Modifier.height(12.dp))
                SoundAlertSelector(
                    selectedSound = memoAlertSound,
                    onSoundSelected = { memoAlertSound = it },
                    hasAudio = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Reminder Alarm", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    TextButton(
                        onClick = { showReminderDialog = true },
                        modifier = Modifier.testTag("record_sheet_schedule_btn")
                    ) {
                        if (reminderTime == null) {
                            Text("Pick Date & Time")
                        } else {
                            Text(ReminderDateTimeHelper.formatScheduledDateTime(reminderTime!!))
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Notify on Telegram", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Switch(
                        checked = notifyTelegram,
                        onCheckedChange = { notifyTelegram = it }
                    )
                }

                if (showReminderDialog) {
                    ReminderScheduleDialog(
                        initialReminderTime = reminderTime,
                        selectedSound = memoAlertSound,
                        onSoundSelected = { memoAlertSound = it },
                        hasAudio = true,
                        onSave = { chosen ->
                            reminderTime = chosen
                            showReminderDialog = false
                            if (chosen != null) {
                                Toast.makeText(
                                    context,
                                    "Scheduled for ${ReminderDateTimeHelper.formatScheduledDateTime(chosen)}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onDismiss = { showReminderDialog = false }
                    )
                }

                if (!viewModel.isRecording) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Close")
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
    var selectedIcon by remember { mutableStateOf("Work") }
    var selectedAlertSound by remember { mutableStateOf("App Default") }
    var selectedPriority by remember { mutableStateOf(TaskPriority.MEDIUM) }

    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var showFoldersMenu by remember { mutableStateOf(false) }

    var reminderTime by remember { mutableStateOf<Long?>(null) }
    var notifyTelegram by remember { mutableStateOf(true) }
    var showReminderDialog by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val notesFocusRequester = remember { FocusRequester() }
    val tagsFocusRequester = remember { FocusRequester() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .widthIn(min = 280.dp, max = 560.dp)
                .fillMaxWidth()
                .wrapContentHeight()
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.EditNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "New Text Task",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(onNext = { notesFocusRequester.requestFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_title")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Task description notes...") },
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(onNext = { tagsFocusRequester.requestFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(notesFocusRequester)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Folder", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
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

                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    singleLine = true,
                    label = { Text("Tags (comma separated)") },
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(tagsFocusRequester)
                )

                Spacer(modifier = Modifier.height(14.dp))
                TaskPrioritySelector(
                    selectedPriority = selectedPriority,
                    onPrioritySelected = { selectedPriority = it }
                )

                Spacer(modifier = Modifier.height(14.dp))
                TaskIconSelector(
                    selectedIcon = selectedIcon,
                    onIconSelected = { selectedIcon = it }
                )

                Spacer(modifier = Modifier.height(12.dp))
                SoundAlertSelector(
                    selectedSound = selectedAlertSound,
                    onSoundSelected = { selectedAlertSound = it },
                    hasAudio = false
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Reminder Alarm", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    TextButton(
                        onClick = { showReminderDialog = true },
                        modifier = Modifier.testTag("quick_note_schedule_btn")
                    ) {
                        if (reminderTime == null) {
                            Text("Pick Date & Time")
                        } else {
                            Text(ReminderDateTimeHelper.formatScheduledDateTime(reminderTime!!))
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Notify on Telegram", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Switch(
                        checked = notifyTelegram,
                        onCheckedChange = { notifyTelegram = it }
                    )
                }

                if (showReminderDialog) {
                    ReminderScheduleDialog(
                        initialReminderTime = reminderTime,
                        selectedSound = selectedAlertSound,
                        onSoundSelected = { selectedAlertSound = it },
                        hasAudio = false,
                        onSave = { chosen ->
                            reminderTime = chosen
                            showReminderDialog = false
                            if (chosen != null) {
                                Toast.makeText(
                                    context,
                                    "Scheduled for ${ReminderDateTimeHelper.formatScheduledDateTime(chosen)}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onDismiss = { showReminderDialog = false }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Cancel", fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            viewModel.addTask(
                                title = title,
                                notes = notes,
                                category = category,
                                tags = tags,
                                reminderTime = reminderTime,
                                alertSound = selectedAlertSound,
                                iconName = selectedIcon,
                                notifyTelegram = notifyTelegram,
                                priority = selectedPriority.level
                            )
                            onDismiss()
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Save Task", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun TaskIconSelector(
    selectedIcon: String,
    onIconSelected: (String) -> Unit
) {
    val icons = listOf(
        "Mic" to Icons.Default.Mic,
        "Work" to Icons.Default.Work,
        "Personal" to Icons.Default.Person,
        "Idea" to Icons.Default.Star,
        "Call" to Icons.Default.Call,
        "Bookmark" to Icons.Default.Bookmark,
        "Shopping" to Icons.Default.ShoppingCart,
        "Finance" to Icons.Default.AttachMoney,
        "Health" to Icons.Default.Favorite,
        "Home" to Icons.Default.Home,
        "School" to Icons.Default.Book,
        "Tech" to Icons.Default.Code,
        "Music" to Icons.Default.MusicNote,
        "Fitness" to Icons.AutoMirrored.Filled.DirectionsRun,
        "Travel" to Icons.Default.DirectionsCar,
        "Food" to Icons.Default.Restaurant
    )

    Column {
        Text(
            text = "Task Icon",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            icons.forEach { (name, icon) ->
                val isSelected = selectedIcon == name
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) BotanicalSage else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape
                        )
                        .clickable { onIconSelected(name) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = name,
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TaskPriorityBadge(
    priority: TaskPriority,
    onSelectPriority: (TaskPriority) -> Unit,
    testTag: String = ""
) {
    var showMenu by remember { mutableStateOf(false) }

    val (bgColor, fgColor, borderColor) = when (priority) {
        TaskPriority.LOW -> Triple(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.outlineVariant
        )
        TaskPriority.MEDIUM -> Triple(
            BotanicalSage.copy(alpha = 0.28f),
            MaterialTheme.colorScheme.primary,
            BotanicalSage.copy(alpha = 0.65f)
        )
        TaskPriority.HIGH -> Triple(
            BotanicalAmber.copy(alpha = 0.16f),
            BotanicalAmber,
            BotanicalAmber.copy(alpha = 0.48f)
        )
        TaskPriority.URGENT -> Triple(
            MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.error.copy(alpha = 0.48f)
        )
    }

    Box {
        Surface(
            onClick = { showMenu = true },
            shape = RoundedCornerShape(8.dp),
            color = bgColor,
            border = BorderStroke(1.dp, borderColor),
            modifier = if (testTag.isNotBlank()) Modifier.testTag(testTag) else Modifier
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Flag,
                    contentDescription = "Priority ${priority.label}",
                    tint = fgColor,
                    modifier = Modifier.size(11.dp)
                )
                Text(
                    text = priority.label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = fgColor
                )
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            TaskPriority.entries.forEach { option ->
                val isSelected = option == priority
                val dotColor = when (option) {
                    TaskPriority.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
                    TaskPriority.MEDIUM -> MaterialTheme.colorScheme.primary
                    TaskPriority.HIGH -> BotanicalAmber
                    TaskPriority.URGENT -> MaterialTheme.colorScheme.error
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = null,
                            tint = dotColor,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    trailingIcon = {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    },
                    onClick = {
                        onSelectPriority(option)
                        showMenu = false
                    }
                )
            }
        }
    }
}

@Composable
fun TaskPrioritySelector(
    selectedPriority: TaskPriority,
    onPrioritySelected: (TaskPriority) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Priority Level",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TaskPriority.entries.forEach { option ->
                val isSelected = selectedPriority == option
                val (bg, fg, border) = when (option) {
                    TaskPriority.LOW -> Triple(
                        if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        if (isSelected) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outlineVariant
                    )
                    TaskPriority.MEDIUM -> Triple(
                        if (isSelected) BotanicalSage.copy(alpha = 0.42f) else MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.primary,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    )
                    TaskPriority.HIGH -> Triple(
                        if (isSelected) BotanicalAmber.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface,
                        BotanicalAmber,
                        if (isSelected) BotanicalAmber else MaterialTheme.colorScheme.outlineVariant
                    )
                    TaskPriority.URGENT -> Triple(
                        if (isSelected) MaterialTheme.colorScheme.error.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.error,
                        if (isSelected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
                    )
                }
                Surface(
                    onClick = { onPrioritySelected(option) },
                    shape = RoundedCornerShape(14.dp),
                    color = bg,
                    border = BorderStroke(1.dp, border),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .testTag("priority_select_${option.name.lowercase()}")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = option.shortLabel,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = fg,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

fun getIconForName(iconName: String): ImageVector {
    return when (iconName) {
        "Work" -> Icons.Default.Work
        "Personal" -> Icons.Default.Person
        "Idea" -> Icons.Default.Star
        "Call" -> Icons.Default.Call
        "Bookmark" -> Icons.Default.Bookmark
        "Shopping" -> Icons.Default.ShoppingCart
        "Finance" -> Icons.Default.AttachMoney
        "Health" -> Icons.Default.Favorite
        "Home" -> Icons.Default.Home
        "School" -> Icons.Default.Book
        "Tech" -> Icons.Default.Code
        "Music" -> Icons.Default.MusicNote
        "Fitness" -> Icons.AutoMirrored.Filled.DirectionsRun
        "Travel" -> Icons.Default.DirectionsCar
        "Food" -> Icons.Default.Restaurant
        "AutoAwesome" -> Icons.Default.AutoAwesome
        else -> Icons.Default.Mic
    }
}
