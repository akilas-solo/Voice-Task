package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AiProvider
import com.example.data.EmailDeliveryMode
import com.example.data.NotepadNote
import com.example.data.VoiceTask
import com.example.ui.theme.BotanicalAmber
import com.example.ui.theme.BotanicalSage
import com.example.ui.theme.FrauncesFontFamily
import com.example.ui.theme.StudioMonoFontFamily
import com.example.ui.theme.TelegramAzure

@Composable
fun AiStudioQuickSheet(
    viewModel: VoiceTaskViewModel,
    initialTab: Int = 0,
    preselectedTaskId: Int? = null,
    preselectedNoteId: Int? = null,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val aiConfig by viewModel.aiStudioConfig.collectAsStateWithLifecycle()
    val allTasks by viewModel.allTasks.collectAsStateWithLifecycle()
    val allNotes by viewModel.allNotepadNotes.collectAsStateWithLifecycle()

    var selectedTab by remember(initialTab, preselectedTaskId, preselectedNoteId) {
        mutableIntStateOf(initialTab)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 20.dp)
                .widthIn(min = 300.dp, max = 660.dp)
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .imePadding()
                .testTag("ai_studio_quick_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
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
                            color = BotanicalSage.copy(alpha = 0.28f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI Studio",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "AI Studio",
                                fontFamily = FrauncesFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (aiConfig.isAiConfigured) {
                                    "${aiConfig.activeProvider.displayName} · ${aiConfig.activeModel}"
                                } else {
                                    "Add an API key in Settings to begin"
                                },
                                fontFamily = StudioMonoFontFamily,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (aiConfig.isAiConfigured) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    BotanicalAmber
                                }
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                onDismiss()
                                onOpenSettings()
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("ai_sheet_settings_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Configure AI & Connectors",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close AI Studio")
                        }
                    }
                }

                if (!aiConfig.isAiConfigured) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        onClick = {
                            onDismiss()
                            onOpenSettings()
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = BotanicalAmber.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, BotanicalAmber.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Key, contentDescription = null, tint = BotanicalAmber, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "No ${aiConfig.activeProvider.displayName} key configured. Tap to add your key.",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = BotanicalAmber)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3-Tab Botanical Segmented Control
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val tabs = listOf(
                            Triple(0, "Chat", "ai_tab_chat"),
                            Triple(1, "Voice Tasks", "ai_tab_voice"),
                            Triple(2, "Draft & Send", "ai_tab_draft")
                        )
                        tabs.forEach { (index, label, tag) ->
                            val isSelected = selectedTab == index
                            Surface(
                                onClick = { selectedTab = index },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                border = if (isSelected) BorderStroke(1.dp, BotanicalSage) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 42.dp)
                                    .testTag(tag)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (!viewModel.aiStatusMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (viewModel.aiStatusIsError) MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
                        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = viewModel.aiStatusMessage!!,
                                fontSize = 11.sp,
                                color = if (viewModel.aiStatusIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.clearAiStatusBanner() }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (selectedTab) {
                        0 -> AiChatTabContent(viewModel = viewModel)
                        1 -> AiVoiceToTaskTabContent(
                            viewModel = viewModel,
                            allTasks = allTasks,
                            allNotes = allNotes,
                            preselectedTaskId = preselectedTaskId,
                            preselectedNoteId = preselectedNoteId,
                            activeProvider = aiConfig.activeProvider
                        )
                        else -> AiDraftAndDispatchTabContent(
                            viewModel = viewModel,
                            allTasks = allTasks,
                            allNotes = allNotes,
                            preselectedTaskId = preselectedTaskId,
                            preselectedNoteId = preselectedNoteId,
                            onToast = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiChatTabContent(viewModel: VoiceTaskViewModel) {
    val context = LocalContext.current
    val messages = viewModel.aiChatMessages
    var promptInput by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        if (messages.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Ask across your memos & notes",
                    fontFamily = FrauncesFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Your assistant reads saved tasks, alarms, and notepad notes to answer questions or trigger connector actions.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))

                val starterPrompts = listOf(
                    "Summarize my open tasks & reminders",
                    "Turn my latest notes into an email draft",
                    "Create a Telegram digest of today's priorities",
                    "What follow-ups are in my Work folder?"
                )
                starterPrompts.forEach { sample ->
                    Surface(
                        onClick = { viewModel.sendAiChatMessage(sample) },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(sample, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    Column(
                        horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (msg.isUser) {
                                BotanicalSage.copy(alpha = 0.40f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                            },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.widthIn(max = 520.dp)
                        ) {
                            Text(
                                text = msg.text,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(12.dp)
                            )
                        }

                        if (!msg.isUser && msg.suggestedActions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                msg.suggestedActions.forEach { action ->
                                    FilledTonalButton(
                                        onClick = {
                                            viewModel.executeAiSuggestedAction(action) { _, feedback ->
                                                Toast.makeText(context, feedback, Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.heightIn(min = 38.dp)
                                    ) {
                                        Icon(
                                            imageVector = when (action.type) {
                                                "SEND_TELEGRAM" -> Icons.AutoMirrored.Filled.Send
                                                "DRAFT_EMAIL" -> Icons.Default.Email
                                                "SAVE_NOTION" -> Icons.Default.Description
                                                "SEND_WEBHOOK" -> Icons.Default.Hub
                                                else -> Icons.Default.AddTask
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(action.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }

                if (viewModel.isAiBusy) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 6.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Thinking across your memos...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isNotEmpty()) {
                IconButton(
                    onClick = { viewModel.clearAiChat() },
                    modifier = Modifier.size(46.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Chat", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            OutlinedTextField(
                value = promptInput,
                onValueChange = { promptInput = it },
                placeholder = { Text("Ask AI or request an action...", fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("ai_chat_input")
            )

            FilledIconButton(
                onClick = {
                    val toSend = promptInput
                    promptInput = ""
                    viewModel.sendAiChatMessage(toSend)
                },
                enabled = promptInput.isNotBlank() && !viewModel.isAiBusy,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("ai_chat_send_btn")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send to AI")
            }
        }
    }
}

@Composable
private fun AiVoiceToTaskTabContent(
    viewModel: VoiceTaskViewModel,
    allTasks: List<VoiceTask>,
    allNotes: List<NotepadNote>,
    preselectedTaskId: Int?,
    preselectedNoteId: Int?,
    activeProvider: AiProvider
) {
    val context = LocalContext.current
    val audioTasks = remember(allTasks) { allTasks.filter { !it.audioPath.isNullOrBlank() } }
    val audioNotes = remember(allNotes) { allNotes.filter { !it.audioPath.isNullOrBlank() } }

    // Key format: "T:<id>" or "N:<id>"
    var selectedClipKey by remember(preselectedTaskId, preselectedNoteId, audioTasks, audioNotes) {
        val initial = when {
            preselectedTaskId != null && audioTasks.any { it.id == preselectedTaskId } -> "T:$preselectedTaskId"
            preselectedNoteId != null && audioNotes.any { it.id == preselectedNoteId } -> "N:$preselectedNoteId"
            audioTasks.isNotEmpty() -> "T:${audioTasks.first().id}"
            audioNotes.isNotEmpty() -> "N:${audioNotes.first().id}"
            else -> ""
        }
        mutableStateOf(initial)
    }

    val selectedTask = remember(selectedClipKey, audioTasks) {
        if (selectedClipKey.startsWith("T:")) {
            val id = selectedClipKey.removePrefix("T:").toIntOrNull()
            audioTasks.firstOrNull { it.id == id }
        } else null
    }
    val selectedNote = remember(selectedClipKey, audioNotes) {
        if (selectedClipKey.startsWith("N:")) {
            val id = selectedClipKey.removePrefix("N:").toIntOrNull()
            audioNotes.firstOrNull { it.id == id }
        } else null
    }

    val extraction = viewModel.extractedVoiceResult

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Transcribe recorded voice clips and extract structured tasks, summaries, folders, and tags.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (activeProvider == AiProvider.CLAUDE) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = BotanicalAmber.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, BotanicalAmber.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Tip: Anthropic Claude does not process raw audio files directly. Switch your active AI provider to Gemini or OpenAI in Settings to transcribe audio.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        if (audioTasks.isEmpty() && audioNotes.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "No audio recordings found yet. Record a voice memo using the center sage microphone or attach a voice clip in Notepad.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            Text(
                text = "SELECT RECORDING",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.7.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                audioTasks.forEach { t ->
                    val key = "T:${t.id}"
                    BotanicalFilterChip(
                        label = "${t.title.take(18)} (${formatTimerLabel(t.audioDurationMs)})",
                        selected = selectedClipKey == key,
                        onClick = { selectedClipKey = key }
                    )
                }
                audioNotes.forEach { n ->
                    val key = "N:${n.id}"
                    BotanicalFilterChip(
                        label = "Note: ${n.title.take(16)} (${formatTimerLabel(n.audioDurationMs)})",
                        selected = selectedClipKey == key,
                        onClick = { selectedClipKey = key }
                    )
                }
            }

            Button(
                onClick = {
                    when {
                        selectedTask != null -> viewModel.transcribeAndStructureTaskAudio(selectedTask) { _, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                        selectedNote != null -> viewModel.transcribeAndStructureNoteAudio(selectedNote) { _, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = !viewModel.isAiBusy && (selectedTask != null || selectedNote != null),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("ai_transcribe_btn")
            ) {
                if (viewModel.isAiBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Transcribing & Structuring...")
                } else {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Transcribe & Turn Into Tasks")
                }
            }
        }

        if (extraction != null) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, BotanicalSage),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = extraction.title,
                            fontFamily = FrauncesFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = extraction.suggestedFolder,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Text(
                        text = extraction.summary,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (extraction.tags.isNotBlank()) {
                        Text(
                            text = "Tags: ${extraction.tags}",
                            fontFamily = StudioMonoFontFamily,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (extraction.actionItems.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            text = "EXTRACTED ACTION ITEMS (${extraction.actionItems.size})",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.7.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        extraction.actionItems.forEach { item ->
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.CheckCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(item, fontSize = 12.sp)
                            }
                        }
                    }

                    if (extraction.transcript.isNotBlank()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            text = "TRANSCRIPT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.7.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = extraction.transcript,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val targetTaskId = viewModel.extractedForTaskId
                        val targetNoteId = viewModel.extractedForNoteId
                        if (targetTaskId != null || targetNoteId != null) {
                            OutlinedButton(
                                onClick = {
                                    if (targetTaskId != null) {
                                        viewModel.applyStructuredExtractionToTask(targetTaskId, extraction) { msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    } else if (targetNoteId != null) {
                                        viewModel.applyStructuredExtractionToNote(targetNoteId, extraction) { msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                            ) {
                                Text("Update Original", fontSize = 12.sp)
                            }
                        }

                        if (extraction.actionItems.isNotEmpty()) {
                            Button(
                                onClick = {
                                    viewModel.createTasksFromActionItems(
                                        actionItems = extraction.actionItems,
                                        category = extraction.suggestedFolder,
                                        tags = extraction.tags
                                    ) { msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                            ) {
                                Text("Add ${extraction.actionItems.size} Task(s)", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiDraftAndDispatchTabContent(
    viewModel: VoiceTaskViewModel,
    allTasks: List<VoiceTask>,
    allNotes: List<NotepadNote>,
    preselectedTaskId: Int?,
    preselectedNoteId: Int?,
    onToast: (String) -> Unit
) {
    val aiConfig by viewModel.aiStudioConfig.collectAsStateWithLifecycle()

    // Source options: "PENDING", "CUSTOM", "T:<id>", "N:<id>"
    var sourceKey by remember(preselectedTaskId, preselectedNoteId) {
        val initial = when {
            preselectedTaskId != null -> "T:$preselectedTaskId"
            preselectedNoteId != null -> "N:$preselectedNoteId"
            else -> "PENDING"
        }
        mutableStateOf(initial)
    }
    var customTopicInput by remember { mutableStateOf("") }

    // Target channel: "TELEGRAM", "EMAIL", "NOTION", "WH:<id>"
    var targetChannel by remember { mutableStateOf("TELEGRAM") }
    var selectedTone by remember { mutableStateOf("Concise") }

    var draftSubject by remember { mutableStateOf("") }
    var draftBody by remember { mutableStateOf("") }
    var draftTags by remember { mutableStateOf("") }
    var emailRecipient by remember(aiConfig.emailDefaultRecipient) {
        mutableStateOf(aiConfig.emailDefaultRecipient)
    }

    val latestGenerated = viewModel.generatedDraftResult
    LaunchedEffect(latestGenerated) {
        if (latestGenerated != null) {
            draftSubject = latestGenerated.subject
            draftBody = latestGenerated.body
            draftTags = latestGenerated.tags
        }
    }

    val resolvedSourcePair: Pair<String, String> = remember(sourceKey, customTopicInput, allTasks, allNotes) {
        when {
            sourceKey == "PENDING" -> {
                val open = allTasks.filter { !it.isCompleted }.take(12)
                val summaryBody = if (open.isEmpty()) {
                    "No open tasks currently recorded."
                } else {
                    open.joinToString("\n") { t ->
                        "- [${t.category}] ${t.title}${if (t.notes.isNotBlank()) ": ${t.notes.take(150)}" else ""}"
                    }
                }
                "Voice Task Status Update" to summaryBody
            }
            sourceKey == "CUSTOM" -> {
                "Custom Update" to customTopicInput.trim()
            }
            sourceKey.startsWith("T:") -> {
                val id = sourceKey.removePrefix("T:").toIntOrNull()
                val t = allTasks.firstOrNull { it.id == id }
                (t?.title ?: "Voice Memo") to (t?.notes?.ifBlank { t.title } ?: "")
            }
            sourceKey.startsWith("N:") -> {
                val id = sourceKey.removePrefix("N:").toIntOrNull()
                val n = allNotes.firstOrNull { it.id == id }
                (n?.title ?: "Notepad Note") to (n?.content?.ifBlank { n.title } ?: "")
            }
            else -> "Voice Task Note" to ""
        }
    }

    val enabledWebhooks = aiConfig.customWebhooks.filter { it.isEnabled }
    val channelDisplayName = remember(targetChannel, enabledWebhooks) {
        when {
            targetChannel == "TELEGRAM" -> "Telegram"
            targetChannel == "EMAIL" -> "Email"
            targetChannel == "NOTION" -> "Notion"
            targetChannel.startsWith("WH:") -> {
                val id = targetChannel.removePrefix("WH:")
                enabledWebhooks.firstOrNull { it.id == id }?.presetType?.label ?: "Webhook"
            }
            else -> "Telegram"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "1. SOURCE MEMO OR SUMMARY",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.7.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BotanicalFilterChip(
                label = "All Open Tasks",
                selected = sourceKey == "PENDING",
                onClick = { sourceKey = "PENDING" }
            )
            BotanicalFilterChip(
                label = "Custom Prompt",
                selected = sourceKey == "CUSTOM",
                onClick = { sourceKey = "CUSTOM" }
            )
            allTasks.take(8).forEach { t ->
                BotanicalFilterChip(
                    label = t.title.take(18),
                    selected = sourceKey == "T:${t.id}",
                    onClick = { sourceKey = "T:${t.id}" }
                )
            }
            allNotes.take(6).forEach { n ->
                BotanicalFilterChip(
                    label = "Note: ${n.title.take(15)}",
                    selected = sourceKey == "N:${n.id}",
                    onClick = { sourceKey = "N:${n.id}" }
                )
            }
        }

        if (sourceKey == "CUSTOM") {
            OutlinedTextField(
                value = customTopicInput,
                onValueChange = { customTopicInput = it },
                label = { Text("What should AI summarize or write?") },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Text(
            text = "2. TARGET CONNECTOR & TONE",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.7.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BotanicalFilterChip(
                label = "Telegram",
                selected = targetChannel == "TELEGRAM",
                onClick = { targetChannel = "TELEGRAM" }
            )
            BotanicalFilterChip(
                label = "Email",
                selected = targetChannel == "EMAIL",
                onClick = { targetChannel = "EMAIL" }
            )
            BotanicalFilterChip(
                label = "Notion",
                selected = targetChannel == "NOTION",
                onClick = { targetChannel = "NOTION" }
            )
            enabledWebhooks.forEach { wh ->
                BotanicalFilterChip(
                    label = wh.name.take(16),
                    selected = targetChannel == "WH:${wh.id}",
                    onClick = { targetChannel = "WH:${wh.id}" }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Concise", "Executive", "Friendly", "Checklist").forEach { tone ->
                BotanicalFilterChip(
                    label = tone,
                    selected = selectedTone == tone,
                    onClick = { selectedTone = tone }
                )
            }
        }

        Button(
            onClick = {
                viewModel.generateConnectorDraft(
                    channelName = channelDisplayName,
                    tone = selectedTone,
                    sourceTitle = resolvedSourcePair.first,
                    sourceBody = resolvedSourcePair.second
                ) { _, msg -> onToast(msg) }
            },
            enabled = !viewModel.isAiBusy && resolvedSourcePair.second.isNotBlank(),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("ai_generate_draft_btn")
        ) {
            if (viewModel.isAiBusy) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Drafting with AI...")
            } else {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Summarize & Draft for $channelDisplayName")
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (targetChannel == "EMAIL") {
            OutlinedTextField(
                value = emailRecipient,
                onValueChange = { emailRecipient = it },
                label = { Text("Recipient Email") },
                placeholder = { Text("recipient@example.com") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        OutlinedTextField(
            value = draftSubject,
            onValueChange = { draftSubject = it },
            label = { Text(if (targetChannel == "EMAIL") "Email Subject" else "Title / Headline") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = draftBody,
            onValueChange = { draftBody = it },
            label = { Text("Message Body") },
            minLines = 4,
            maxLines = 9,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )

        val dispatchLabel = when {
            targetChannel == "TELEGRAM" -> "Send to Telegram"
            targetChannel == "EMAIL" -> if (aiConfig.emailDeliveryMode == EmailDeliveryMode.RESEND_API && aiConfig.isResendConfigured) "Send Email via Resend" else "Open Mail App Draft"
            targetChannel == "NOTION" -> "Save Page to Notion"
            targetChannel.startsWith("WH:") -> "Push to $channelDisplayName"
            else -> "Dispatch Now"
        }

        Button(
            onClick = {
                val channelKey = if (targetChannel.startsWith("WH:")) "WEBHOOK" else targetChannel
                val webhookId = if (targetChannel.startsWith("WH:")) targetChannel.removePrefix("WH:") else null
                viewModel.dispatchDraftToConnector(
                    channelKey = channelKey,
                    subjectOrTitle = draftSubject.ifBlank { resolvedSourcePair.first },
                    body = draftBody.ifBlank { resolvedSourcePair.second },
                    tags = draftTags,
                    emailRecipient = emailRecipient,
                    webhookId = webhookId
                ) { _, msg -> onToast(msg) }
            },
            enabled = !viewModel.isConnectorDispatching && (draftBody.isNotBlank() || resolvedSourcePair.second.isNotBlank()),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (targetChannel == "TELEGRAM") TelegramAzure else MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("ai_dispatch_connector_btn")
        ) {
            if (viewModel.isConnectorDispatching) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sending...")
            } else {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(dispatchLabel, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
