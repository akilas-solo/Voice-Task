package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.NotepadNote
import com.example.ui.theme.BotanicalSage
import com.example.ui.theme.FrauncesFontFamily
import com.example.ui.theme.StudioMonoFontFamily
import com.example.ui.theme.StudioRecordRed
import com.example.ui.theme.TelegramAzure
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotepadScreen(
    viewModel: VoiceTaskViewModel,
    hasAudioPermission: () -> Boolean,
    onRequestAudioPermission: () -> Unit,
    onOpenAiForNote: (NotepadNote) -> Unit = {}
) {
    val context = LocalContext.current
    val notes by viewModel.allNotepadNotes.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var selectedFolder by remember { mutableStateOf("All") }

    var showEditor by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<NotepadNote?>(null) }

    val filteredNotes = remember(notes, searchQuery, selectedFolder) {
        notes.filter { note ->
            val matchesFolder = selectedFolder == "All" || note.category.equals(selectedFolder, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                note.title.contains(searchQuery, ignoreCase = true) ||
                note.content.contains(searchQuery, ignoreCase = true)
            matchesFolder && matchesSearch
        }
    }

    val folders by viewModel.notepadFolders.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Editorial notes with live word count, curated paper tints, and voice attachments.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search note titles or body...", fontSize = 14.sp) },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search Notepad") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
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
                    .fillMaxWidth()
                    .testTag("notepad_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                folders.forEach { folder ->
                    BotanicalFilterChip(
                        label = folder,
                        selected = selectedFolder == folder,
                        onClick = { selectedFolder = folder },
                        modifier = Modifier.testTag("notepad_folder_chip_$folder")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (filteredNotes.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "No notes matching filter",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap the + button below to compose a new note or record a voice attachment.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 280.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 84.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredNotes, key = { it.id }) { note ->
                        val parsedColor = remember(note.colorHex) {
                            if (!note.colorHex.isNullOrBlank()) {
                                runCatching { Color(android.graphics.Color.parseColor(note.colorHex)) }.getOrNull()
                            } else null
                        }
                        val cardInk = if (parsedColor != null) Color(0xFF1E2219) else MaterialTheme.colorScheme.onSurface
                        val cardMutedInk = if (parsedColor != null) Color(0xFF4E5447) else MaterialTheme.colorScheme.onSurfaceVariant

                        Surface(
                            onClick = {
                                editingNote = note
                                showEditor = true
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = parsedColor ?: MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                width = if (note.isPinned) 1.5.dp else 1.dp,
                                color = if (note.isPinned) BotanicalSage else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("note_card_${note.id}")
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = note.title,
                                        fontFamily = FrauncesFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 18.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = cardInk,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (note.isPinned) {
                                            Icon(
                                                imageVector = Icons.Default.PushPin,
                                                contentDescription = "Pinned Note",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (parsedColor != null) Color.Black.copy(alpha = 0.08f)
                                            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                        ) {
                                            Text(
                                                text = note.category,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (parsedColor != null) cardInk else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = if (note.content.isBlank()) "(Empty note body)" else note.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    color = cardMutedInk
                                )

                                if (!note.audioPath.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    val isThisNoteActive = viewModel.playingNotepadNoteId == note.id
                                    val isPlayingThisNote = isThisNoteActive && viewModel.isNotepadPlaying
                                    val waveAccent = if (parsedColor != null) Color(0xFF3E4A2D) else MaterialTheme.colorScheme.primary
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (parsedColor != null) Color.Black.copy(alpha = 0.06f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            IconButton(
                                                onClick = { viewModel.playNotepadVoice(note.id, note.audioPath) },
                                                modifier = Modifier.size(44.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isPlayingThisNote) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                                    contentDescription = "Play or Pause Voice Note",
                                                    tint = waveAccent,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                            ScrubbableAudioWaveform(
                                                seedKey = note.id + 5000,
                                                durationMs = note.audioDurationMs,
                                                progress = if (isThisNoteActive) viewModel.notepadPlaybackProgress else 0f,
                                                isPlaying = isPlayingThisNote,
                                                activeColor = waveAccent,
                                                inactiveColor = waveAccent.copy(alpha = 0.28f),
                                                onSeek = { ratio ->
                                                    if (isThisNoteActive) {
                                                        viewModel.seekNotepadPlayback(ratio)
                                                    } else {
                                                        viewModel.playNotepadVoice(note.id, note.audioPath)
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                barCount = 28
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (isThisNoteActive) formatTimerLabel(viewModel.notepadPlaybackPositionMs) else formatTimerLabel(note.audioDurationMs),
                                                fontFamily = StudioMonoFontFamily,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = waveAccent
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(
                                    color = if (parsedColor != null) Color.Black.copy(alpha = 0.08f)
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val formattedDate = remember(note.updatedAt) {
                                        SimpleDateFormat("MMM d · hh:mm a", Locale.getDefault()).format(Date(note.updatedAt))
                                    }
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp)
                                    ) {
                                        Text(
                                            text = formattedDate,
                                            fontSize = 11.sp,
                                            color = cardMutedInk,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${note.wordCount} words · ${note.charCount} chars",
                                            fontFamily = StudioMonoFontFamily,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (parsedColor != null) Color(0xFF3E4A2D) else MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        FilledTonalButton(
                                            onClick = { onOpenAiForNote(note) },
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                            modifier = Modifier
                                                .height(34.dp)
                                                .testTag("ai_note_${note.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = "AI Actions for Note",
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text("AI", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }

                                        val isSendingNote = viewModel.sendingNoteIds.contains(note.id)
                                        FilledTonalButton(
                                            onClick = {
                                                viewModel.sendNoteToTelegram(note) { _, msg ->
                                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            enabled = !isSendingNote,
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                containerColor = TelegramAzure.copy(alpha = 0.14f),
                                                contentColor = TelegramAzure
                                            ),
                                            modifier = Modifier
                                                .height(34.dp)
                                                .testTag("send_telegram_note_${note.id}")
                                        ) {
                                            if (isSendingNote) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(14.dp),
                                                    strokeWidth = 2.dp,
                                                    color = TelegramAzure
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                                    contentDescription = "Send Note to Telegram",
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text("Telegram", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                editingNote = null
                showEditor = true
            },
            shape = CircleShape,
            containerColor = BotanicalSage,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("create_note_notepad_fab")
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Add New Note")
        }
    }

    if (showEditor) {
        NotepadEditorDialog(
            viewModel = viewModel,
            editingNote = editingNote,
            folders = folders,
            hasAudioPermission = hasAudioPermission,
            onRequestAudioPermission = onRequestAudioPermission,
            onDismiss = {
                if (viewModel.isNotepadRecording) {
                    viewModel.cancelNotepadRecording()
                }
                showEditor = false
            }
        )
    }
}

@Composable
private fun NotepadEditorDialog(
    viewModel: VoiceTaskViewModel,
    editingNote: NotepadNote?,
    folders: List<String>,
    hasAudioPermission: () -> Boolean,
    onRequestAudioPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var tempAudioPath by remember(editingNote) { mutableStateOf(editingNote?.audioPath) }
    var tempAudioDuration by remember(editingNote) { mutableStateOf(editingNote?.audioDurationMs ?: 0L) }
    var newlyRecordedPath by remember(editingNote) { mutableStateOf<String?>(null) }
    var showFolderDropdown by remember { mutableStateOf(false) }

    val handleDiscard: () -> Unit = {
        if (viewModel.isNotepadRecording) {
            viewModel.cancelNotepadRecording()
        }
        newlyRecordedPath?.let { path ->
            runCatching {
                val f = File(path)
                if (f.exists()) f.delete()
            }
            viewModel.refreshCacheStats()
        }
        onDismiss()
    }

    Dialog(
        onDismissRequest = handleDiscard,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            var noteTitle by remember(editingNote) { mutableStateOf(editingNote?.title ?: "") }
            var noteContent by remember(editingNote) { mutableStateOf(editingNote?.content ?: "") }
            var noteFolder by remember(editingNote) { mutableStateOf(editingNote?.category ?: "General") }
            var isPinned by remember(editingNote) { mutableStateOf(editingNote?.isPinned ?: false) }
            var selectedColorHex by remember(editingNote) { mutableStateOf(editingNote?.colorHex) }

            val colorsPalette = listOf(
                null to "Default",
                "#DDE3D0" to "Sage Mist",
                "#EBE4D1" to "Warm Sand",
                "#D4DFD5" to "Eucalyptus",
                "#EDEAE0" to "Oat Milk",
                "#EAD8D0" to "Terracotta",
                "#DFE1DC" to "Soft Stone"
            )

            val editorBgColor = remember(selectedColorHex) {
                if (!selectedColorHex.isNullOrBlank()) {
                    runCatching { Color(android.graphics.Color.parseColor(selectedColorHex)) }.getOrNull()
                } else null
            }

            val inkPrimary = if (editorBgColor != null) Color(0xFF1E2219) else MaterialTheme.colorScheme.onSurface
            val inkMuted = if (editorBgColor != null) Color(0xFF4E5447) else MaterialTheme.colorScheme.onSurfaceVariant

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(editorBgColor ?: MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = handleDiscard) {
                        Text("Discard", color = inkPrimary)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { isPinned = !isPinned },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "Pin Note",
                                tint = if (isPinned) MaterialTheme.colorScheme.primary else inkMuted.copy(alpha = 0.6f)
                            )
                        }

                        if (editingNote != null) {
                            IconButton(
                                onClick = {
                                    newlyRecordedPath?.let { path ->
                                        runCatching { File(path).takeIf { it.exists() }?.delete() }
                                    }
                                    viewModel.removeNotepadNote(editingNote)
                                    onDismiss()
                                    Toast.makeText(context, "Note deleted", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Note",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        Button(
                            onClick = {
                                val originalPath = editingNote?.audioPath
                                if (originalPath != null && originalPath != tempAudioPath) {
                                    runCatching { File(originalPath).takeIf { it.exists() }?.delete() }
                                }
                                newlyRecordedPath = null
                                if (editingNote != null) {
                                    viewModel.modifyNotepadNote(
                                        editingNote.copy(
                                            title = noteTitle.ifBlank { "Untitled Note" },
                                            content = noteContent,
                                            category = noteFolder,
                                            isPinned = isPinned,
                                            colorHex = selectedColorHex,
                                            audioPath = tempAudioPath,
                                            audioDurationMs = tempAudioDuration
                                        )
                                    )
                                } else {
                                    viewModel.addNotepadNote(
                                        title = noteTitle,
                                        content = noteContent,
                                        category = noteFolder,
                                        isPinned = isPinned,
                                        colorHex = selectedColorHex,
                                        audioPath = tempAudioPath,
                                        audioDurationMs = tempAudioDuration
                                    )
                                }
                                onDismiss()
                                Toast.makeText(context, "Note saved securely", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.heightIn(min = 42.dp)
                        ) {
                            Text("Save Note")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = noteTitle,
                    onValueChange = { noteTitle = it },
                    placeholder = { Text("Note Title", fontFamily = FrauncesFontFamily, fontSize = 20.sp) },
                    textStyle = MaterialTheme.typography.headlineSmall.copy(color = inkPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("editor_note_title")
                )

                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Folder:", fontSize = 12.sp, color = inkMuted)
                    Spacer(modifier = Modifier.width(8.dp))
                    Box {
                        Surface(
                            onClick = { showFolderDropdown = true },
                            shape = RoundedCornerShape(10.dp),
                            color = if (editorBgColor != null) Color.Black.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(noteFolder, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = inkPrimary)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = inkPrimary, modifier = Modifier.size(16.dp))
                            }
                        }

                        DropdownMenu(
                            expanded = showFolderDropdown,
                            onDismissRequest = { showFolderDropdown = false }
                        ) {
                            folders.filter { it != "All" }.forEach { folderOption ->
                                DropdownMenuItem(
                                    text = { Text(folderOption) },
                                    onClick = {
                                        noteFolder = folderOption
                                        showFolderDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text(
                        text = "Paper Tint",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = inkMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        colorsPalette.forEach { (colorString, name) ->
                            val chipColor = if (colorString != null) Color(android.graphics.Color.parseColor(colorString))
                            else MaterialTheme.colorScheme.surfaceVariant
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(chipColor)
                                    .border(
                                        width = if (selectedColorHex == colorString) 2.dp else 1.dp,
                                        color = if (selectedColorHex == colorString) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColorHex = colorString }
                                    .testTag("color_picker_$name")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val isRecordingNote = viewModel.isNotepadRecording
                val notepadRecordDuration = viewModel.notepadRecordDurationMs
                val hasVoiceNote = tempAudioPath != null
                val editorPlayId = editingNote?.id ?: -99
                val isThisNoteActive = viewModel.playingNotepadNoteId == editorPlayId
                val isPlayingThisNote = isThisNoteActive && viewModel.isNotepadPlaying

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (editorBgColor != null) Color.Black.copy(alpha = 0.06f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = if (editorBgColor != null) Color(0xFF3E4A2D) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Voice Memo Attachment",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = inkPrimary
                                )
                            }

                            if (isRecordingNote) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(StudioRecordRed)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = formatTimerLabel(notepadRecordDuration),
                                        fontFamily = StudioMonoFontFamily,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = StudioRecordRed
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (isRecordingNote) {
                            Button(
                                onClick = {
                                    val recordedFile = viewModel.stopNotepadRecording()
                                    if (recordedFile != null) {
                                        newlyRecordedPath = recordedFile.absolutePath
                                        tempAudioPath = recordedFile.absolutePath
                                        tempAudioDuration = viewModel.lastNotepadRecordDurationMs
                                        Toast.makeText(context, "Voice recording attached!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = StudioRecordRed, contentColor = Color.White),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Stop, contentDescription = "Stop recording")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Stop & Attach Recording")
                            }
                        } else if (hasVoiceNote && tempAudioPath != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = { viewModel.playNotepadVoice(editorPlayId, tempAudioPath!!) },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPlayingThisNote) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                        contentDescription = "Play or Pause Attached Voice Note",
                                        tint = if (editorBgColor != null) Color(0xFF3E4A2D) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    ScrubbableAudioWaveform(
                                        seedKey = editorPlayId + 7000,
                                        durationMs = tempAudioDuration,
                                        progress = if (isThisNoteActive) viewModel.notepadPlaybackProgress else 0f,
                                        isPlaying = isPlayingThisNote,
                                        activeColor = if (editorBgColor != null) Color(0xFF3E4A2D) else MaterialTheme.colorScheme.primary,
                                        inactiveColor = (if (editorBgColor != null) Color(0xFF3E4A2D) else MaterialTheme.colorScheme.primary).copy(alpha = 0.28f),
                                        onSeek = { ratio ->
                                            if (isThisNoteActive) {
                                                viewModel.seekNotepadPlayback(ratio)
                                            } else {
                                                viewModel.playNotepadVoice(editorPlayId, tempAudioPath!!)
                                            }
                                        },
                                        barCount = 24
                                    )
                                    Text(
                                        text = if (isPlayingThisNote) {
                                            "Playing · ${formatTimerLabel(viewModel.notepadPlaybackPositionMs)}"
                                        } else {
                                            "Duration · ${formatTimerLabel(tempAudioDuration)}"
                                        },
                                        fontFamily = StudioMonoFontFamily,
                                        fontSize = 10.sp,
                                        color = inkMuted
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        if (isThisNoteActive) {
                                            viewModel.stopNotepadVoice()
                                        }
                                        if (tempAudioPath != null && tempAudioPath == newlyRecordedPath) {
                                            runCatching { File(tempAudioPath!!).takeIf { it.exists() }?.delete() }
                                            newlyRecordedPath = null
                                            viewModel.refreshCacheStats()
                                        }
                                        tempAudioPath = null
                                        tempAudioDuration = 0L
                                        Toast.makeText(context, "Voice attachment removed", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove Voice Note",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    if (hasAudioPermission()) {
                                        viewModel.startNotepadRecording()
                                    } else {
                                        onRequestAudioPermission()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, inkPrimary.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Record Voice Note",
                                    tint = inkPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Record Voice Attachment", color = inkPrimary)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = inkPrimary.copy(alpha = 0.14f))
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = noteContent,
                    onValueChange = { noteContent = it },
                    placeholder = { Text("Start writing your thoughts, drafts, or checklists...") },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = inkPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("editor_note_content")
                )

                val wordsCount = remember(noteContent) {
                    val trimmed = noteContent.trim()
                    if (trimmed.isEmpty()) 0 else trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }.size
                }
                val charsCount = noteContent.length
                val readTimeMin = remember(wordsCount) { (wordsCount / 200).coerceAtLeast(1) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$wordsCount words · $charsCount chars",
                        fontFamily = StudioMonoFontFamily,
                        fontSize = 11.sp,
                        color = inkMuted
                    )
                    Text(
                        text = "< $readTimeMin min read",
                        fontFamily = StudioMonoFontFamily,
                        fontSize = 11.sp,
                        color = inkMuted
                    )
                }
            }
        }
    }
}
