package com.example.ui

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.BankGroupedTransactions
import com.example.data.BankSmsParser
import com.example.data.FinanceAnalyticsEngine
import com.example.data.FinancePeriodMode
import com.example.data.FinanceTransaction
import com.example.ui.theme.BotanicalAmber
import com.example.ui.theme.BotanicalSage
import com.example.ui.theme.FrauncesFontFamily
import com.example.ui.theme.StudioMonoFontFamily
import com.example.ui.theme.StudioSuccess
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun FinanceScreen(
    viewModel: VoiceTaskViewModel,
    hasSmsPermission: () -> Boolean,
    onRequestSmsPermission: () -> Unit
) {
    val context = LocalContext.current
    val allTransactions by viewModel.allFinanceTransactions.collectAsStateWithLifecycle()
    val filteredTransactions by viewModel.filteredFinanceTransactions.collectAsStateWithLifecycle()
    val filterState by viewModel.financeFilterState.collectAsStateWithLifecycle()
    val summary by viewModel.financeSummary.collectAsStateWithLifecycle()
    val monthlyTotals by viewModel.monthlyFinanceTotals.collectAsStateWithLifecycle()
    val monthlySpendingTotal = monthlyTotals.first
    val monthlyIncomeTotal = monthlyTotals.second

    var selectedSubTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Overview, 1 = Ledger, 2 = Insights
    var showFilterSheet by remember { mutableStateOf(false) }
    var showDayPicker by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var showTimeWindowDialog by remember { mutableStateOf(false) }
    var showPasteSmsDialog by remember { mutableStateOf(false) }
    var showManualTxDialog by remember { mutableStateOf(false) }
    var inspectingTx by remember { mutableStateOf<FinanceTransaction?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val monthlyLabel = remember {
        SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(Date())
    }

    val triggerSmsScan = {
        if (hasSmsPermission()) {
            viewModel.scanDeviceSmsInbox(sinceDays = 90, silent = false)
        } else {
            onRequestSmsPermission()
        }
    }

    val backupFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importSmsBackupUris(context, listOf(uri))
        }
    }

    val readClipboardForBankSms = {
        val clipText = runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        }.getOrNull()
        viewModel.inspectClipboardForBankSms(clipText)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        readClipboardForBankSms()
        if (hasSmsPermission()) {
            viewModel.scanDeviceSmsInbox(sinceDays = 90, silent = true)
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                readClipboardForBankSms()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val openBackupImporter = {
        backupFileLauncher.launch(
            arrayOf(
                "text/plain",
                "text/csv",
                "text/comma-separated-values",
                "text/xml",
                "application/xml",
                "*/*"
            )
        )
    }

    if (showFilterSheet) {
        FinanceFilterBottomSheet(
            filterState = filterState,
            filteredCount = filteredTransactions.size,
            onDismiss = { showFilterSheet = false },
            onSelectPeriod = { mode ->
                when (mode) {
                    FinancePeriodMode.SPECIFIC_DAY -> {
                        showFilterSheet = false
                        showDayPicker = true
                    }
                    FinancePeriodMode.SPECIFIC_MONTH -> {
                        showFilterSheet = false
                        showMonthPicker = true
                    }
                    else -> viewModel.setFinancePeriodMode(mode)
                }
            },
            onOpenTimeWindow = {
                showFilterSheet = false
                showTimeWindowDialog = true
            },
            onSelectBank = { viewModel.setFinanceBankFilter(it) },
            onSelectType = { viewModel.setFinanceTypeFilter(it) },
            onResetAll = { viewModel.resetFinanceFilters() }
        )
    }

    if (showDayPicker) {
        FinanceDayPickerDialog(
            initialDayMillis = filterState.selectedDayMillis ?: System.currentTimeMillis(),
            onDismiss = { showDayPicker = false },
            onDaySelected = { utcDateMillis ->
                val calUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcDateMillis }
                val localCal = Calendar.getInstance().apply {
                    set(calUtc.get(Calendar.YEAR), calUtc.get(Calendar.MONTH), calUtc.get(Calendar.DAY_OF_MONTH), 12, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                viewModel.setFinanceSpecificDay(localCal.timeInMillis)
                showDayPicker = false
            }
        )
    }

    if (showMonthPicker) {
        FinanceMonthPickerDialog(
            initialYear = filterState.selectedYear ?: Calendar.getInstance().get(Calendar.YEAR),
            initialMonthZeroBased = filterState.selectedMonthZeroBased ?: Calendar.getInstance().get(Calendar.MONTH),
            onDismiss = { showMonthPicker = false },
            onSelectMonth = { year, month ->
                viewModel.setFinanceSpecificMonth(year, month)
                showMonthPicker = false
            }
        )
    }

    if (showTimeWindowDialog) {
        FinanceTimeWindowDialog(
            currentFilter = filterState,
            onDismiss = { showTimeWindowDialog = false },
            onApply = { preset, sH, sM, eH, eM ->
                viewModel.setFinanceTimeWindow(preset, sH, sM, eH, eM)
                showTimeWindowDialog = false
            }
        )
    }

    if (showPasteSmsDialog) {
        val learnedRules = remember(showPasteSmsDialog) { viewModel.getLearnedCategoryRules() }
        PasteBankSmsDialog(
            learnedCategoryRules = learnedRules,
            onDismiss = { showPasteSmsDialog = false },
            onImport = { rawText, senderHint, categoryOverride ->
                viewModel.importManualBankSms(
                    rawText = rawText,
                    senderHint = senderHint,
                    overrideCategory = categoryOverride
                ) { count, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    if (count > 0) showPasteSmsDialog = false
                }
            }
        )
    }

    if (showManualTxDialog) {
        AddManualTransactionDialog(
            onDismiss = { showManualTxDialog = false },
            onSave = { bank, type, amount, counterparty, category, ts, notes ->
                viewModel.addManualFinanceTransaction(
                    bankName = bank.displayName,
                    bankCode = bank.code,
                    type = type,
                    amount = amount,
                    counterparty = counterparty,
                    category = category,
                    timestamp = ts,
                    notes = notes
                ) { ok, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    if (ok) showManualTxDialog = false
                }
            }
        )
    }

    inspectingTx?.let { tx ->
        val learnedRules = remember(tx.id) { viewModel.getLearnedCategoryRules() }
        TransactionDetailDialog(
            tx = tx,
            learnedCategoryRules = learnedRules,
            onDismiss = { inspectingTx = null },
            onUpdateCategory = { newCat ->
                viewModel.updateFinanceCategory(tx, newCat)
                inspectingTx = tx.copy(category = newCat)
                Toast.makeText(context, "Category updated to $newCat", Toast.LENGTH_SHORT).show()
            },
            onUpdateType = { newType ->
                viewModel.updateFinanceType(tx, newType)
                inspectingTx = tx.copy(type = newType.name)
                Toast.makeText(context, "Type updated to ${newType.label}", Toast.LENGTH_SHORT).show()
            },
            onReparse = {
                viewModel.reparseSingleFinanceTransaction(tx) { kept, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    if (!kept) {
                        inspectingTx = null
                    } else {
                        val reparsed = BankSmsParser.parseSms(tx.rawSender, tx.rawSmsBody, tx.timestamp, tx.source)
                        if (reparsed != null) {
                            inspectingTx = tx.copy(
                                bankName = reparsed.bankName,
                                bankCode = reparsed.bankCode,
                                type = reparsed.type,
                                amount = reparsed.amount,
                                currency = reparsed.currency,
                                balanceAfter = reparsed.balanceAfter,
                                serviceCharge = reparsed.serviceCharge,
                                counterparty = reparsed.counterparty,
                                referenceId = reparsed.referenceId,
                                category = reparsed.category
                            )
                        }
                    }
                }
            },
            onDelete = {
                viewModel.deleteFinanceTransaction(tx)
                inspectingTx = null
                Toast.makeText(context, "Transaction removed", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear Finance Ledger?", fontFamily = FrauncesFontFamily) },
            text = { Text("Removes all parsed bank SMS transactions stored in this workspace. Your device SMS messages remain untouched.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllFinanceTransactions()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Clear All") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("finance_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Scan / Import Action Banner + Status
        if (!viewModel.financeStatusMessage.isNullOrBlank()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (viewModel.financeStatusIsError) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                    } else {
                        BotanicalSage.copy(alpha = 0.24f)
                    },
                    border = BorderStroke(
                        1.dp,
                        if (viewModel.financeStatusIsError) MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                        else BotanicalSage
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = viewModel.financeStatusMessage!!,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearFinanceStatusBanner() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // 1b. One-Tap Clipboard Bank SMS Detector Prompt
        if (!viewModel.clipboardBankPromptText.isNullOrBlank()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = BotanicalAmber.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, BotanicalAmber.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("finance_clipboard_prompt_card")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPasteGo,
                            contentDescription = "Clipboard Bank SMS",
                            tint = BotanicalAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bank SMS detected in clipboard",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = viewModel.clipboardDetectedSummary ?: "Tap Import to add to ledger",
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { viewModel.importClipboardBankSms() },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("finance_clipboard_import_btn")
                        ) {
                            Text("Import", fontSize = 12.sp)
                        }
                        IconButton(
                            onClick = { viewModel.dismissClipboardBankSms() },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("finance_clipboard_dismiss_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss clipboard SMS prompt",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Compact Action Bar (Period Pill + Sync SMS + Filters Sheet + Import/Add Menu)
        item(key = "compact_action_bar") {
            CompactFinanceActionBar(
                periodLabel = summary.periodLabel,
                filterState = filterState,
                isScanning = viewModel.isFinanceScanning,
                hasAnyData = allTransactions.isNotEmpty(),
                hasSmsPermission = hasSmsPermission(),
                onScanSmsClick = triggerSmsScan,
                onOpenFilters = { showFilterSheet = true },
                onPasteSmsClick = { showPasteSmsDialog = true },
                onImportFileClick = openBackupImporter,
                onManualAddClick = { showManualTxDialog = true },
                onResetFilters = { viewModel.resetFinanceFilters() },
                onClearAllClick = { showClearConfirm = true },
                onSendTelegramSummary = {
                    viewModel.sendFinanceSummaryToTelegram { _, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // 3. Slim Unified Net Cashflow & Account Balances Card
        item(key = "slim_hero_summary") {
            FinanceHeroSummaryCard(
                summary = summary,
                monthlySpendingTotal = monthlySpendingTotal,
                monthlyIncomeTotal = monthlyIncomeTotal,
                monthlyLabel = monthlyLabel,
                filterState = filterState,
                isScanning = viewModel.isFinanceScanning,
                hasAnyData = allTransactions.isNotEmpty(),
                hasSmsPermission = hasSmsPermission(),
                showTopActions = false,
                onRequestSmsPermission = onRequestSmsPermission,
                onScanSmsClick = triggerSmsScan,
                onPasteSmsClick = { showPasteSmsDialog = true },
                onImportFileClick = openBackupImporter,
                onManualAddClick = { showManualTxDialog = true },
                onResetFilters = { viewModel.resetFinanceFilters() },
                onClearAllClick = { showClearConfirm = true }
            )
        }

        // 4. Three Clean Sub-tabs: Overview | Ledger | Insights
        item(key = "finance_tabs") {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val subTabs = listOf(
                        Triple(0, "Overview", Icons.Default.Dashboard),
                        Triple(1, "Ledger (${filteredTransactions.size})", Icons.AutoMirrored.Filled.ReceiptLong),
                        Triple(2, "Insights", Icons.Default.BarChart)
                    )
                    subTabs.forEach { (tabIdx, title, icon) ->
                        val selected = selectedSubTab == tabIdx
                        Surface(
                            onClick = { selectedSubTab = tabIdx },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                            border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)) else null,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                                .testTag("finance_subtab_$tabIdx")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        when (selectedSubTab) {
            0 -> {
                financeOverviewItems(
                    transactions = filteredTransactions,
                    groupedByBank = summary.bankGroups,
                    monthlySpendingTotal = monthlySpendingTotal,
                    monthlyIncomeTotal = monthlyIncomeTotal,
                    monthlyMonthLabel = monthlyLabel,
                    hasSmsPermission = hasSmsPermission(),
                    onRequestSmsPermission = onRequestSmsPermission,
                    onScanSmsClick = triggerSmsScan,
                    onTransactionClick = { inspectingTx = it }
                )
            }
            1 -> {
                item(key = "ledger_search_header") {
                    FinanceLedgerSearchHeader(
                        filterState = filterState,
                        onSearchChange = { viewModel.setFinanceSearchQuery(it) },
                        onOpenFilters = { showFilterSheet = true },
                        onResetFilters = { viewModel.resetFinanceFilters() }
                    )
                }
                if (filteredTransactions.isEmpty()) {
                    item(key = "ledger_empty") {
                        FinanceEmptyLedgerState(
                            hasAnyStored = allTransactions.isNotEmpty(),
                            periodLabel = summary.periodLabel,
                            onScanSms = triggerSmsScan,
                            onPasteSms = { showPasteSmsDialog = true },
                            onResetFilters = { viewModel.resetFinanceFilters() }
                        )
                    }
                } else {
                    items(filteredTransactions, key = { "tx_${it.id}" }) { tx ->
                        FinanceTransactionCard(
                            tx = tx,
                            onClick = { inspectingTx = tx }
                        )
                    }
                }
            }
            else -> {
                financeAnalyticsItems(
                    transactions = filteredTransactions,
                    summary = summary
                )
            }
        }

        item(key = "bottom_spacer") { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

private fun LazyListScope.financeOverviewItems(
    transactions: List<FinanceTransaction>,
    groupedByBank: List<com.example.data.BankGroupedTransactions>,
    monthlySpendingTotal: Double,
    monthlyIncomeTotal: Double,
    monthlyMonthLabel: String,
    hasSmsPermission: Boolean,
    onRequestSmsPermission: () -> Unit,
    onScanSmsClick: () -> Unit,
    onTransactionClick: (FinanceTransaction) -> Unit
) {
    val primaryCurrency = transactions.firstOrNull()?.currency ?: "ETB"

    item(key = "overview_chart_card") {
        FinanceOverviewChartCard(
            transactions = transactions,
            primaryCurrency = primaryCurrency,
            lockedMode = OverviewChartMode.TREND_LINE
        )
    }

    item(key = "overview_recent_header") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RECENT BY BANK",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${groupedByBank.size} Bank(s) · ${transactions.size} tx",
                fontFamily = StudioMonoFontFamily,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (groupedByBank.isEmpty()) {
        item(key = "overview_empty_banks") {
            EmptyBankGroupsCard()
        }
    } else {
        items(groupedByBank, key = { "bank_${it.bankCode}" }) { group ->
            BankTransactionGroupCard(
                group = group,
                onTransactionClick = onTransactionClick
            )
        }
    }
}

@Composable
fun FinanceOverviewComposable(
    transactions: List<FinanceTransaction>,
    monthlySpendingTotal: Double,
    monthlyIncomeTotal: Double = 0.0,
    monthlyMonthLabel: String = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(Date()),
    hasSmsPermission: Boolean = true,
    onRequestSmsPermission: () -> Unit = {},
    onScanSmsClick: () -> Unit = {},
    onTransactionClick: (FinanceTransaction) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val groupedByBank = remember(transactions) {
        FinanceAnalyticsEngine.groupRecentTransactionsByBank(transactions, limitPerBank = 5)
    }
    val primaryCurrency = remember(transactions) {
        transactions.firstOrNull()?.currency ?: "ETB"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("finance_overview_composable"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FinanceOverviewChartCard(
            transactions = transactions,
            primaryCurrency = primaryCurrency
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RECENT BY BANK",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${groupedByBank.size} Bank(s) · ${transactions.size} tx",
                fontFamily = StudioMonoFontFamily,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (groupedByBank.isEmpty()) {
            EmptyBankGroupsCard()
        } else {
            groupedByBank.forEach { group ->
                BankTransactionGroupCard(
                    group = group,
                    onTransactionClick = onTransactionClick
                )
            }
        }
    }
}


@Composable
private fun EmptyBankGroupsCard() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AccountBalance,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "No bank SMS transactions yet",
                fontFamily = FrauncesFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            Text(
                text = "Once SMS alerts from Telebirr, CBE, Bank of Abyssinia, or other banks are scanned or pasted, they will appear grouped cleanly by bank here.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BankTransactionGroupCard(
    group: com.example.data.BankGroupedTransactions,
    onTransactionClick: (FinanceTransaction) -> Unit
) {
    val timeFmt = remember { SimpleDateFormat("MMM dd · HH:mm", Locale.ENGLISH) }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bank_group_${group.bankCode}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AccountBalance,
                                contentDescription = group.bankName,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = group.bankName,
                            fontFamily = FrauncesFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val balLabel = group.latestBalance?.let {
                            "Bal: ${FinanceAnalyticsEngine.formatMoney(it, group.currency)}"
                        } ?: "${group.recentTransactions.size} recent tx"
                        Text(
                            text = balLabel,
                            fontFamily = StudioMonoFontFamily,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "-${FinanceAnalyticsEngine.formatMoney(group.totalOutflow, group.currency)}",
                        fontFamily = StudioMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = BotanicalAmber
                    )
                    if (group.totalIncome > 0.0) {
                        Text(
                            text = "+${FinanceAnalyticsEngine.formatMoney(group.totalIncome, group.currency)}",
                            fontFamily = StudioMonoFontFamily,
                            fontSize = 11.sp,
                            color = StudioSuccess
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            group.recentTransactions.forEachIndexed { idx, tx ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onTransactionClick(tx) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tx.counterparty.ifBlank { tx.bankName },
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${tx.category} · ${timeFmt.format(Date(tx.timestamp))}",
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = FinanceAnalyticsEngine.formatSignedMoney(tx),
                        fontFamily = StudioMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (tx.isIncoming) StudioSuccess else MaterialTheme.colorScheme.onSurface
                    )
                }
                if (idx < group.recentTransactions.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}
