package com.example.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.BotanicalAmber
import com.example.ui.theme.BotanicalSage
import com.example.ui.theme.FrauncesFontFamily
import com.example.ui.theme.StudioMonoFontFamily
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ReminderQuickPreset(
    val id: String,
    val label: String,
    val computeMillis: (Long) -> Long
)

object ReminderDateTimeHelper {

    fun quickPresets(): List<ReminderQuickPreset> = listOf(
        ReminderQuickPreset("1m", "In 1 min") { now -> now + 60_000L },
        ReminderQuickPreset("5m", "In 5 mins") { now -> now + 5L * 60_000L },
        ReminderQuickPreset("15m", "In 15 mins") { now -> now + 15L * 60_000L },
        ReminderQuickPreset("1h", "In 1 hour") { now -> now + 60L * 60_000L },
        ReminderQuickPreset("tonight", "Tonight 8 PM") { now ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 20)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis <= now + 60_000L) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            cal.timeInMillis
        },
        ReminderQuickPreset("tomorrow", "Tomorrow 9 AM") { now ->
            Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
    )

    fun combineUtcDateWithLocalTime(
        utcDateMillis: Long,
        hour24: Int,
        minute: Int,
        localTimeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = utcDateMillis
        }
        val year = utcCal.get(Calendar.YEAR)
        val month = utcCal.get(Calendar.MONTH)
        val day = utcCal.get(Calendar.DAY_OF_MONTH)

        return Calendar.getInstance(localTimeZone).apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour24.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun extractLocalDateAsUtcMidnight(
        localMillis: Long,
        localTimeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        val localCal = Calendar.getInstance(localTimeZone).apply {
            timeInMillis = localMillis
        }
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(Calendar.YEAR, localCal.get(Calendar.YEAR))
            set(Calendar.MONTH, localCal.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, localCal.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun extractLocalHourMinute(
        localMillis: Long,
        localTimeZone: TimeZone = TimeZone.getDefault()
    ): Pair<Int, Int> {
        val cal = Calendar.getInstance(localTimeZone).apply {
            timeInMillis = localMillis
        }
        return cal.get(Calendar.HOUR_OF_DAY) to cal.get(Calendar.MINUTE)
    }

    fun isFutureReminder(
        targetMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        return targetMillis > nowMillis + 5_000L
    }

    fun computeSnoozeTimestamp(
        minutes: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        return nowMillis + (minutes.coerceAtLeast(1) * 60_000L)
    }

    fun formatScheduledDateTime(targetMillis: Long): String {
        val fmt = SimpleDateFormat("EEE, MMM d · hh:mm a", Locale.getDefault())
        return fmt.format(Date(targetMillis))
    }

    fun formatDateOnly(utcDateMillis: Long): String {
        val localMidnight = combineUtcDateWithLocalTime(utcDateMillis, 12, 0)
        val fmt = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
        return fmt.format(Date(localMidnight))
    }

    fun formatTimeOnly(hour24: Int, minute: Int): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour24)
            set(Calendar.MINUTE, minute)
        }
        return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(cal.time)
    }

    fun formatRelativeRemaining(
        targetMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val diff = targetMillis - nowMillis
        if (diff <= 0L) return "Past time"
        val totalMins = (diff / 60_000L).coerceAtLeast(1L)
        val days = totalMins / (60L * 24L)
        val hours = (totalMins % (60L * 24L)) / 60L
        val mins = totalMins % 60L
        return when {
            days > 0L -> if (hours > 0L) "in ${days}d ${hours}h" else "in ${days}d"
            hours > 0L -> if (mins > 0L) "in ${hours}h ${mins}m" else "in ${hours}h"
            else -> "in ${mins}m"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderScheduleDialog(
    initialReminderTime: Long?,
    selectedSound: String? = null,
    onSoundSelected: ((String) -> Unit)? = null,
    hasAudio: Boolean = false,
    taskAudioPath: String? = null,
    notifyTelegram: Boolean? = null,
    onNotifyTelegramChanged: ((Boolean) -> Unit)? = null,
    onSave: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val initialBaseMillis = remember(initialReminderTime) {
        initialReminderTime?.takeIf { it > System.currentTimeMillis() }
            ?: (System.currentTimeMillis() + 15L * 60_000L)
    }
    var selectedUtcDateMillis by remember {
        mutableLongStateOf(ReminderDateTimeHelper.extractLocalDateAsUtcMidnight(initialBaseMillis))
    }
    val initialHm = remember(initialBaseMillis) {
        ReminderDateTimeHelper.extractLocalHourMinute(initialBaseMillis)
    }
    var selectedHour by remember { mutableIntStateOf(initialHm.first) }
    var selectedMinute by remember { mutableIntStateOf(initialHm.second) }

    var showDatePickerModal by remember { mutableStateOf(false) }
    var showTimePickerModal by remember { mutableStateOf(false) }
    var showSoundSheet by remember { mutableStateOf(false) }

    val candidateTimestamp = remember(selectedUtcDateMillis, selectedHour, selectedMinute) {
        ReminderDateTimeHelper.combineUtcDateWithLocalTime(
            utcDateMillis = selectedUtcDateMillis,
            hour24 = selectedHour,
            minute = selectedMinute
        )
    }
    val isCandidateValid = ReminderDateTimeHelper.isFutureReminder(candidateTimestamp)

    val lifecycleOwner = LocalLifecycleOwner.current
    var canScheduleExact by remember {
        mutableStateOf(ReminderNotificationHelper.canScheduleExactAlarms(context))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canScheduleExact = ReminderNotificationHelper.canScheduleExactAlarms(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val todayStartUtcMillis = remember {
        ReminderDateTimeHelper.extractLocalDateAsUtcMidnight(System.currentTimeMillis())
    }

    if (showDatePickerModal) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedUtcDateMillis.coerceAtLeast(todayStartUtcMillis),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcTimeMillis >= todayStartUtcMillis
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePickerModal = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedUtcDateMillis = it }
                        showDatePickerModal = false
                    },
                    modifier = Modifier.testTag("confirm_calendar_date_btn")
                ) {
                    Text("Set Date")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerModal = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePickerModal) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedHour,
            initialMinute = selectedMinute,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePickerModal = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text("Select Time", fontFamily = FrauncesFontFamily, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedHour = timePickerState.hour
                        selectedMinute = timePickerState.minute
                        showTimePickerModal = false
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("confirm_clock_time_btn")
                ) {
                    Text("Apply Time")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePickerModal = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSoundSheet && selectedSound != null && onSoundSelected != null) {
        AlarmSoundPickerSheet(
            selectedSound = selectedSound,
            hasAudio = hasAudio,
            taskAudioPath = taskAudioPath,
            onSoundSelected = { newSound ->
                onSoundSelected(newSound)
                showSoundSheet = false
            },
            onDismiss = { showSoundSheet = false }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .padding(horizontal = 18.dp, vertical = 20.dp)
                .widthIn(min = 290.dp, max = 560.dp)
                .fillMaxWidth()
                .testTag("reminder_schedule_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Event,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Schedule Date & Time",
                                fontFamily = FrauncesFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 19.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Pick calendar date, exact clock time, or preset",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Reminder Scheduler")
                    }
                }

                // Live Preview Banner
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isCandidateValid) {
                        BotanicalSage.copy(alpha = 0.24f)
                    } else {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                    },
                    border = BorderStroke(
                        1.dp,
                        if (isCandidateValid) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ReminderDateTimeHelper.formatScheduledDateTime(candidateTimestamp),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isCandidateValid) {
                                    "Triggers ${ReminderDateTimeHelper.formatRelativeRemaining(candidateTimestamp)}"
                                } else {
                                    "Selected time is in the past — adjust clock or date"
                                },
                                fontFamily = StudioMonoFontFamily,
                                fontSize = 11.sp,
                                color = if (isCandidateValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                        if (!isCandidateValid) {
                            TextButton(
                                onClick = {
                                    selectedUtcDateMillis += 86_400_000L
                                }
                            ) {
                                Text("+1 Day", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Quick Presets Row
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "QUICK PRESETS",
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
                        ReminderDateTimeHelper.quickPresets().forEach { preset ->
                            Surface(
                                onClick = {
                                    val target = preset.computeMillis(System.currentTimeMillis())
                                    selectedUtcDateMillis = ReminderDateTimeHelper.extractLocalDateAsUtcMidnight(target)
                                    val hm = ReminderDateTimeHelper.extractLocalHourMinute(target)
                                    selectedHour = hm.first
                                    selectedMinute = hm.second
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .heightIn(min = 38.dp)
                                    .testTag("reminder_preset_${preset.id}")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = preset.label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // Calendar Date & Clock Time Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        onClick = { showDatePickerModal = true },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 68.dp)
                            .testTag("open_calendar_picker_btn")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = "Choose Date",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "DATE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.7.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = ReminderDateTimeHelper.formatDateOnly(selectedUtcDateMillis),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Surface(
                        onClick = { showTimePickerModal = true },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .weight(0.85f)
                            .heightIn(min = 68.dp)
                            .testTag("open_time_picker_btn")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "Choose Time",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "TIME",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.7.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = ReminderDateTimeHelper.formatTimeOnly(selectedHour, selectedMinute),
                                fontFamily = StudioMonoFontFamily,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Sound row if passed
                if (selectedSound != null && onSoundSelected != null) {
                    Surface(
                        onClick = { showSoundSheet = true },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .testTag("schedule_dialog_sound_btn")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Alarm Sound",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = AlarmSoundCatalog.displayName(selectedSound),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Change Sound",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Telegram toggle if passed
                if (notifyTelegram != null && onNotifyTelegramChanged != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Notify on Telegram", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Switch(
                            checked = notifyTelegram,
                            onCheckedChange = onNotifyTelegramChanged
                        )
                    }
                }

                if (!canScheduleExact) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = BotanicalAmber.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, BotanicalAmber.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("exact_alarm_permission_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Alarm,
                                    contentDescription = null,
                                    tint = BotanicalAmber,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Exact alarm permission is off; alarms may be delayed by Android.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            ReminderNotificationHelper.createExactAlarmSettingsIntent(context)
                                        )
                                    }.onFailure {
                                        Toast.makeText(
                                            context,
                                            "Unable to open exact alarm settings.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                modifier = Modifier.testTag("exact_alarm_allow_btn")
                            ) {
                                Text("Allow", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Footer Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (initialReminderTime != null) {
                        TextButton(
                            onClick = {
                                onSave(null)
                                Toast.makeText(context, "Reminder cleared", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("clear_reminder_btn")
                        ) {
                            Text("Clear", color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Cancel")
                        }
                    }

                    Button(
                        onClick = {
                            if (isCandidateValid) {
                                onSave(candidateTimestamp)
                            }
                        },
                        enabled = isCandidateValid,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("save_reminder_schedule_btn")
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Set Alarm", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun AlarmSoundPickerSheet(
    selectedSound: String,
    hasAudio: Boolean,
    taskAudioPath: String? = null,
    onSoundSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val previewingKey = AlarmSoundEngine.activePreviewKey

    DisposableEffect(Unit) {
        onDispose {
            AlarmSoundEngine.stopPreview()
        }
    }

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pickedUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            if (pickedUri != null) {
                val title = runCatching {
                    RingtoneManager.getRingtone(context, pickedUri)?.getTitle(context)
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Device Ringtone"
                val encoded = AlarmSoundCatalog.encodeCustomRingtone(title, pickedUri.toString())
                AlarmSoundEngine.stopPreview()
                onSoundSelected(encoded)
                Toast.makeText(context, "Selected: $title", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Dialog(
        onDismissRequest = {
            AlarmSoundEngine.stopPreview()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 20.dp)
                .widthIn(min = 290.dp, max = 600.dp)
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .testTag("alarm_sound_picker_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = CircleShape,
                            color = BotanicalSage.copy(alpha = 0.28f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Alarm Sound Studio",
                                fontFamily = FrauncesFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 19.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tap a sound to choose or preview live",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            AlarmSoundEngine.stopPreview()
                            onDismiss()
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Alarm Sound Studio")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Device Ringtone Picker Banner
                Surface(
                    onClick = {
                        AlarmSoundEngine.stopPreview()
                        val existingUri = AlarmSoundCatalog.extractRingtoneUri(selectedSound)
                            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
                            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Choose Alarm or Ringtone")
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            if (existingUri != null) {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existingUri)
                            }
                        }
                        runCatching {
                            ringtonePickerLauncher.launch(intent)
                        }.onFailure {
                            Toast.makeText(context, "System ringtone picker unavailable on this device.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, BotanicalSage),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("browse_device_ringtone_btn")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.LibraryMusic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Browse Device Alarms & Ringtones",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (AlarmSoundCatalog.isCustomRingtone(selectedSound)) {
                                        "Active: ${AlarmSoundCatalog.extractRingtoneTitle(selectedSound)}"
                                    } else {
                                        "Pick any built-in phone alarm or ringtone"
                                    },
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Personal & Device Default Section
                    item(key = "hdr_personal") {
                        SectionOverline("VOICE & SYSTEM")
                    }

                    item(key = "opt_app_default") {
                        AlarmSoundOptionRow(
                            title = AlarmSoundCatalog.SOUND_APP_DEFAULT,
                            subtitle = "System default alarm / notification chime",
                            isSelected = selectedSound == AlarmSoundCatalog.SOUND_APP_DEFAULT,
                            isPreviewing = previewingKey == AlarmSoundCatalog.SOUND_APP_DEFAULT,
                            onSelect = {
                                AlarmSoundEngine.stopPreview()
                                onSoundSelected(AlarmSoundCatalog.SOUND_APP_DEFAULT)
                            },
                            onTogglePreview = {
                                AlarmSoundEngine.previewSound(context, AlarmSoundCatalog.SOUND_APP_DEFAULT, taskAudioPath)
                            }
                        )
                    }

                    if (hasAudio) {
                        item(key = "opt_saved_audio") {
                            AlarmSoundOptionRow(
                                title = AlarmSoundCatalog.SOUND_SAVED_AUDIO,
                                subtitle = "Loop this task's recorded voice memo",
                                isSelected = selectedSound == AlarmSoundCatalog.SOUND_SAVED_AUDIO,
                                isPreviewing = previewingKey == AlarmSoundCatalog.SOUND_SAVED_AUDIO,
                                onSelect = {
                                    AlarmSoundEngine.stopPreview()
                                    onSoundSelected(AlarmSoundCatalog.SOUND_SAVED_AUDIO)
                                },
                                onTogglePreview = {
                                    AlarmSoundEngine.previewSound(context, AlarmSoundCatalog.SOUND_SAVED_AUDIO, taskAudioPath)
                                }
                            )
                        }
                    }

                    if (AlarmSoundCatalog.isCustomRingtone(selectedSound)) {
                        item(key = "opt_custom_ringtone") {
                            AlarmSoundOptionRow(
                                title = AlarmSoundCatalog.extractRingtoneTitle(selectedSound),
                                subtitle = "Selected device ringtone",
                                isSelected = true,
                                isPreviewing = previewingKey == selectedSound,
                                onSelect = {
                                    AlarmSoundEngine.stopPreview()
                                    onSoundSelected(selectedSound)
                                },
                                onTogglePreview = {
                                    AlarmSoundEngine.previewSound(context, selectedSound, taskAudioPath)
                                }
                            )
                        }
                    }

                    listOf(
                        AlarmSoundCategory.BOTANICAL,
                        AlarmSoundCategory.STUDIO_CHIMES,
                        AlarmSoundCategory.ALERTS
                    ).forEach { category ->
                        item(key = "hdr_${category.name}") {
                            Spacer(modifier = Modifier.height(6.dp))
                            SectionOverline(category.label.uppercase(Locale.getDefault()))
                        }
                        val categoryPresets = AlarmSoundCatalog.presetsByCategory(category)
                        items(categoryPresets, key = { it.id }) { preset ->
                            AlarmSoundOptionRow(
                                title = preset.title,
                                subtitle = preset.subtitle,
                                isSelected = selectedSound == preset.id,
                                isPreviewing = previewingKey == preset.id,
                                onSelect = {
                                    AlarmSoundEngine.stopPreview()
                                    onSoundSelected(preset.id)
                                },
                                onTogglePreview = {
                                    AlarmSoundEngine.previewSound(context, preset.id, taskAudioPath)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionOverline(text: String) {
    Text(
        text = text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    )
}

@Composable
private fun AlarmSoundOptionRow(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    isPreviewing: Boolean,
    onSelect: () -> Unit,
    onTogglePreview: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) BotanicalSage.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .testTag("sound_option_${title.lowercase().replace(Regex("[^a-z0-9]+"), "_")}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = title,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onTogglePreview,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (isPreviewing) Icons.Default.StopCircle else Icons.Default.PlayCircle,
                    contentDescription = if (isPreviewing) "Stop preview of $title" else "Preview $title",
                    tint = if (isPreviewing) BotanicalAmber else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun SoundAlertSelector(
    selectedSound: String,
    onSoundSelected: (String) -> Unit,
    hasAudio: Boolean,
    taskAudioPath: String? = null
) {
    val context = LocalContext.current
    var showFullStudio by remember { mutableStateOf(false) }

    if (showFullStudio) {
        AlarmSoundPickerSheet(
            selectedSound = selectedSound,
            hasAudio = hasAudio,
            taskAudioPath = taskAudioPath,
            onSoundSelected = { chosen ->
                onSoundSelected(chosen)
                showFullStudio = false
            },
            onDismiss = { showFullStudio = false }
        )
    }

    val featuredSounds = remember(selectedSound, hasAudio) {
        buildList {
            add(AlarmSoundCatalog.SOUND_APP_DEFAULT)
            if (selectedSound != AlarmSoundCatalog.SOUND_APP_DEFAULT &&
                selectedSound != AlarmSoundCatalog.SOUND_SAVED_AUDIO
            ) {
                add(selectedSound)
            }
            listOf(
                "Nordic Forest Chime",
                AlarmSoundCatalog.SOUND_ZEN_BOWL,
                "Morning Dew Harp",
                "Gentle Pulse Beacon",
                "Classic Digital Alarm"
            ).forEach { candidate ->
                if (!contains(candidate)) add(candidate)
            }
            if (hasAudio) add(AlarmSoundCatalog.SOUND_SAVED_AUDIO)
        }
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Alarm Sound",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val isPreviewingSelected = AlarmSoundEngine.activePreviewKey == selectedSound
                IconButton(
                    onClick = {
                        AlarmSoundEngine.previewSound(context, selectedSound, taskAudioPath)
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isPreviewingSelected) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Preview Selected Alarm Sound",
                        tint = if (isPreviewingSelected) BotanicalAmber else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                TextButton(
                    onClick = { showFullStudio = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("browse_all_sounds_btn")
                ) {
                    Text("All Sounds & Ringtones", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            featuredSounds.forEach { soundSpec ->
                val isSelected = selectedSound == soundSpec
                val displayTitle = AlarmSoundCatalog.displayName(soundSpec)
                Surface(
                    onClick = {
                        onSoundSelected(soundSpec)
                        AlarmSoundEngine.previewSound(context, soundSpec, taskAudioPath)
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) BotanicalSage else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.heightIn(min = 38.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        val icon = when {
                            soundSpec == AlarmSoundCatalog.SOUND_APP_DEFAULT -> Icons.Default.Notifications
                            soundSpec == AlarmSoundCatalog.SOUND_SAVED_AUDIO -> Icons.Default.Mic
                            AlarmSoundCatalog.isCustomRingtone(soundSpec) -> Icons.Default.LibraryMusic
                            else -> Icons.Default.MusicNote
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = displayTitle,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
