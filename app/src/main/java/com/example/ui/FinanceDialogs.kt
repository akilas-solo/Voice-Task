package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BankInstitution
import com.example.data.BankSmsParser
import com.example.data.FinanceAnalyticsEngine
import com.example.data.FinanceFilterState
import com.example.data.FinancePeriodMode
import com.example.data.FinanceTransaction
import com.example.data.TimeOfDayPreset
import com.example.data.TransactionType
import com.example.ui.theme.FrauncesFontFamily
import com.example.ui.theme.StudioMonoFontFamily
import com.example.ui.theme.StudioSuccess
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
internal fun FinanceEmptyLedgerState(
    hasAnyStored: Boolean,
    periodLabel: String,
    onScanSms: () -> Unit,
    onPasteSms: () -> Unit,
    onResetFilters: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AccountBalanceWallet,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = if (hasAnyStored) "No transactions in $periodLabel" else "Import Bank & Mobile Money SMS",
                fontFamily = FrauncesFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
            Text(
                text = if (hasAnyStored) {
                    "Try choosing another day, month, time window, or clearing the active filters."
                } else {
                    "Automatically reads SMS alerts from Telebirr, CBE, Bank of Abyssinia, Awash, Dashen, Nib, Coop, M-Pesa, and other banks so you can filter and ask AI by exact time, day, or month."
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (hasAnyStored) {
                OutlinedButton(onClick = onResetFilters, shape = RoundedCornerShape(14.dp)) {
                    Text("Reset Filters")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onScanSms, shape = RoundedCornerShape(14.dp)) {
                        Text("Scan SMS")
                    }
                    OutlinedButton(onClick = onPasteSms, shape = RoundedCornerShape(14.dp)) {
                        Text("Paste SMS")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FinanceDayPickerDialog(
    initialDayMillis: Long,
    onDismiss: () -> Unit,
    onDaySelected: (Long) -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = ReminderDateTimeHelper.extractLocalDateAsUtcMidnight(initialDayMillis)
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    val picked = state.selectedDateMillis ?: return@Button
                    onDaySelected(picked)
                }
            ) { Text("Select Day") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        DatePicker(state = state)
    }
}

@Composable
internal fun FinanceMonthPickerDialog(
    initialYear: Int,
    initialMonthZeroBased: Int,
    onDismiss: () -> Unit,
    onSelectMonth: (Int, Int) -> Unit
) {
    var year by remember { mutableIntStateOf(initialYear) }
    var month by remember { mutableIntStateOf(initialMonthZeroBased) }
    val monthNames = remember { DateFormatSymbols(Locale.ENGLISH).shortMonths.take(12) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick Month & Year", fontFamily = FrauncesFontFamily) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { year-- }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Year")
                    }
                    Text(
                        text = year.toString(),
                        fontFamily = FrauncesFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    IconButton(onClick = { year++ }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next Year")
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (row in 0 until 3) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (col in 0 until 4) {
                                val mIdx = row * 4 + col
                                val selected = mIdx == month
                                Surface(
                                    onClick = { month = mIdx },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = monthNames[mIdx],
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSelectMonth(year, month) }) { Text("Apply Month") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
internal fun FinanceTimeWindowDialog(
    currentFilter: FinanceFilterState,
    onDismiss: () -> Unit,
    onApply: (TimeOfDayPreset, Int, Int, Int, Int) -> Unit
) {
    var preset by remember { mutableStateOf(currentFilter.timePreset) }
    var startHour by remember { mutableIntStateOf(currentFilter.customStartHour) }
    var endHour by remember { mutableIntStateOf(currentFilter.customEndHour) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Time of Day Window", fontFamily = FrauncesFontFamily) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TimeOfDayPreset.entries.forEach { item ->
                    val selected = preset == item
                    Surface(
                        onClick = { preset = item },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(item.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            if (selected) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                if (preset == TimeOfDayPreset.CUSTOM) {
                    HorizontalDivider()
                    Text("Custom Hour Range (24h)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("From: %02d:00".format(startHour), fontFamily = StudioMonoFontFamily)
                        Row {
                            OutlinedButton(onClick = { startHour = (startHour - 1).coerceAtLeast(0) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("-") }
                            Spacer(modifier = Modifier.width(6.dp))
                            OutlinedButton(onClick = { startHour = (startHour + 1).coerceAtMost(23) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("+") }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("To: %02d:59".format(endHour), fontFamily = StudioMonoFontFamily)
                        Row {
                            OutlinedButton(onClick = { endHour = (endHour - 1).coerceAtLeast(0) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("-") }
                            Spacer(modifier = Modifier.width(6.dp))
                            OutlinedButton(onClick = { endHour = (endHour + 1).coerceAtMost(23) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("+") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(preset, startHour, 0, endHour, 59) }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
internal fun PasteBankSmsDialog(
    onDismiss: () -> Unit,
    onImport: (String, String) -> Unit
) {
    var senderHint by remember { mutableStateOf("Telebirr / CBE / Bank") }
    var rawSms by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste Bank SMS", fontFamily = FrauncesFontFamily) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Paste one or multiple Telebirr, CBE, Bank of Abyssinia, Awash, Dashen, or other bank SMS alerts (separated by a blank line).",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = senderHint,
                    onValueChange = { senderHint = it },
                    label = { Text("Sender / Bank Hint (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = rawSms,
                    onValueChange = { rawSms = it },
                    label = { Text("Paste SMS message text here") },
                    minLines = 4,
                    maxLines = 8,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("paste_sms_body_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(rawSms, senderHint) },
                enabled = rawSms.isNotBlank(),
                modifier = Modifier.testTag("confirm_paste_sms_btn")
            ) {
                Text("Parse & Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
internal fun AddManualTransactionDialog(
    onDismiss: () -> Unit,
    onSave: (BankInstitution, TransactionType, Double, String, String, Long, String) -> Unit
) {
    var selectedBank by remember { mutableStateOf(BankInstitution.TELEBIRR) }
    var selectedType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var amountText by remember { mutableStateOf("") }
    var counterparty by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Transfers") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Finance Entry", fontFamily = FrauncesFontFamily) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        BankInstitution.TELEBIRR,
                        BankInstitution.CBE,
                        BankInstitution.BOA,
                        BankInstitution.AWASH,
                        BankInstitution.DASHEN,
                        BankInstitution.OTHER
                    ).forEach { b ->
                        BotanicalFilterChip(
                            label = b.shortBadge,
                            selected = selectedBank == b,
                            onClick = { selectedBank = b }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TransactionType.entries.forEach { t ->
                        BotanicalFilterChip(
                            label = t.label,
                            selected = selectedType == t,
                            onClick = { selectedType = t }
                        )
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (ETB)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = counterparty,
                    onValueChange = { counterparty = it },
                    label = { Text("Merchant / Recipient / Sender") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BankSmsParser.FIN_CATEGORIES.forEach { c ->
                        BotanicalFilterChip(
                            label = c,
                            selected = category == c,
                            onClick = { category = c }
                        )
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.replace(",", "").toDoubleOrNull() ?: 0.0
                    onSave(selectedBank, selectedType, amt, counterparty, category, System.currentTimeMillis(), notes)
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
internal fun TransactionDetailDialog(
    tx: FinanceTransaction,
    onDismiss: () -> Unit,
    onUpdateCategory: (String) -> Unit,
    onUpdateType: (TransactionType) -> Unit,
    onReparse: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val dateFmt = remember { SimpleDateFormat("EEE, MMM dd yyyy · HH:mm:ss", Locale.ENGLISH) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(tx.bankName, fontFamily = FrauncesFontFamily, fontSize = 20.sp)
                Text(
                    text = FinanceAnalyticsEngine.formatSignedMoney(tx),
                    fontFamily = StudioMonoFontFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (tx.isIncoming) StudioSuccess else MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Counterparty: ${tx.counterparty}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("Date & Time: ${dateFmt.format(Date(tx.timestamp))}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (tx.balanceAfter != null) {
                    Text("Balance After: ${FinanceAnalyticsEngine.formatMoney(tx.balanceAfter, tx.currency)}", fontSize = 12.sp)
                }
                if (tx.serviceCharge != null && tx.serviceCharge > 0.0) {
                    Text("Service Charge: ${FinanceAnalyticsEngine.formatMoney(tx.serviceCharge, tx.currency)}", fontSize = 12.sp)
                }
                if (tx.referenceId.isNotBlank()) {
                    Text("Reference: ${tx.referenceId}", fontFamily = StudioMonoFontFamily, fontSize = 11.sp)
                }

                HorizontalDivider()
                Text("Transaction Flow", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TransactionType.entries.forEach { t ->
                        BotanicalFilterChip(
                            label = t.label,
                            selected = tx.transactionType == t,
                            onClick = { onUpdateType(t) }
                        )
                    }
                }

                Text("Category", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BankSmsParser.FIN_CATEGORIES.forEach { cat ->
                        BotanicalFilterChip(
                            label = cat,
                            selected = tx.category == cat,
                            onClick = { onUpdateCategory(cat) }
                        )
                    }
                }

                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Raw SMS Message", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (tx.rawSmsBody.isNotBlank()) {
                            TextButton(
                                onClick = {
                                    runCatching {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        cm?.setPrimaryClip(ClipData.newPlainText("Bank SMS", tx.rawSmsBody))
                                        Toast.makeText(context, "Raw SMS copied", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("tx_copy_raw_sms_btn")
                            ) {
                                Text("Copy", fontSize = 11.sp)
                            }
                            TextButton(
                                onClick = onReparse,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("tx_reparse_sms_btn")
                            ) {
                                Text("Re-parse", fontSize = 11.sp)
                            }
                        }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = tx.rawSmsBody,
                        fontFamily = StudioMonoFontFamily,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Delete") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FinanceFilterBottomSheet(
    filterState: FinanceFilterState,
    filteredCount: Int,
    onDismiss: () -> Unit,
    onSelectPeriod: (FinancePeriodMode) -> Unit,
    onOpenTimeWindow: () -> Unit,
    onSelectBank: (String) -> Unit,
    onSelectType: (String) -> Unit,
    onResetAll: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val activeCount = remember(filterState) {
        var count = 0
        if (filterState.periodMode != FinancePeriodMode.ALL) count++
        if (filterState.timePreset != TimeOfDayPreset.ANY) count++
        if (filterState.selectedBankCode != "ALL") count++
        if (filterState.typeFilter != "ALL") count++
        count
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("finance_filter_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Filter Transactions",
                        fontFamily = FrauncesFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp
                    )
                    if (activeCount > 0) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = activeCount.toString(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (activeCount > 0 || filterState.searchQuery.isNotBlank()) {
                    TextButton(
                        onClick = onResetAll,
                        modifier = Modifier.testTag("finance_filter_sheet_reset_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterAltOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset")
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "DATE PERIOD",
                    fontSize = 11.sp,
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
                    FinancePeriodMode.entries.forEach { mode ->
                        val label = when (mode) {
                            FinancePeriodMode.SPECIFIC_DAY -> {
                                if (filterState.periodMode == FinancePeriodMode.SPECIFIC_DAY && filterState.selectedDayMillis != null) {
                                    SimpleDateFormat("MMM dd", Locale.ENGLISH).format(Date(filterState.selectedDayMillis))
                                } else "Pick Day"
                            }
                            FinancePeriodMode.SPECIFIC_MONTH -> {
                                if (filterState.periodMode == FinancePeriodMode.SPECIFIC_MONTH && filterState.selectedMonthZeroBased != null) {
                                    val cal = Calendar.getInstance().apply {
                                        set(Calendar.YEAR, filterState.selectedYear ?: get(Calendar.YEAR))
                                        set(Calendar.MONTH, filterState.selectedMonthZeroBased)
                                    }
                                    SimpleDateFormat("MMM yyyy", Locale.ENGLISH).format(cal.time)
                                } else "Pick Month"
                            }
                            else -> mode.label
                        }
                        BotanicalFilterChip(
                            label = label,
                            selected = filterState.periodMode == mode,
                            onClick = { onSelectPeriod(mode) }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "TIME OF DAY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.7.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val timeLabel = if (filterState.timePreset == TimeOfDayPreset.ANY) {
                    "Any Time Window"
                } else if (filterState.timePreset == TimeOfDayPreset.CUSTOM) {
                    String.format(
                        Locale.ENGLISH,
                        "%02d:%02d – %02d:%02d",
                        filterState.customStartHour,
                        filterState.customStartMinute,
                        filterState.customEndHour,
                        filterState.customEndMinute
                    )
                } else {
                    filterState.timePreset.name.lowercase(Locale.ENGLISH)
                        .replaceFirstChar { it.uppercaseChar() }
                }
                OutlinedButton(
                    onClick = onOpenTimeWindow,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(timeLabel, modifier = Modifier.weight(1f))
                    Text("Change", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "BANK / WALLET",
                    fontSize = 11.sp,
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
                    val bankChips = listOf(
                        "ALL" to "All Banks",
                        BankInstitution.TELEBIRR.code to "Telebirr",
                        BankInstitution.CBE.code to "CBE",
                        BankInstitution.BOA.code to "Abyssinia",
                        BankInstitution.CBE_BIRR.code to "CBE Birr",
                        BankInstitution.AWASH.code to "Awash",
                        BankInstitution.DASHEN.code to "Dashen",
                        BankInstitution.OTHER.code to "Other"
                    )
                    bankChips.forEach { (code, title) ->
                        BotanicalFilterChip(
                            label = title,
                            selected = filterState.selectedBankCode == code,
                            onClick = { onSelectBank(code) }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "TRANSACTION FLOW",
                    fontSize = 11.sp,
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
                    listOf("ALL" to "All Types", "INCOME" to "Income", "EXPENSE" to "Outflow").forEach { (key, title) ->
                        BotanicalFilterChip(
                            label = title,
                            selected = filterState.typeFilter == key,
                            onClick = { onSelectType(key) }
                        )
                    }
                }
            }

            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("finance_filter_sheet_apply_btn")
            ) {
                Text(
                    text = "Show $filteredCount Transaction${if (filteredCount == 1) "" else "s"}",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
