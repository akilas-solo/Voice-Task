package com.example.ui

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.BankBreakdownStat
import com.example.data.BankInstitution
import com.example.data.BankSmsParser
import com.example.data.FinanceAnalyticsEngine
import com.example.data.FinanceFilterState
import com.example.data.FinancePeriodMode
import com.example.data.FinanceSummary
import com.example.data.FinanceTransaction
import com.example.data.TimeOfDayPreset
import com.example.data.TransactionType
import com.example.ui.theme.BotanicalAmber
import com.example.ui.theme.BotanicalSage
import com.example.ui.theme.FrauncesFontFamily
import com.example.ui.theme.StudioMonoFontFamily
import com.example.ui.theme.StudioRecordRed
import com.example.ui.theme.StudioSuccess
import com.example.ui.theme.TelegramAzure
import ir.ehsannarmani.compose_charts.LineChart
import ir.ehsannarmani.compose_charts.PieChart
import ir.ehsannarmani.compose_charts.models.DrawStyle
import ir.ehsannarmani.compose_charts.models.GridProperties
import ir.ehsannarmani.compose_charts.models.HorizontalIndicatorProperties
import ir.ehsannarmani.compose_charts.models.LabelHelperProperties
import ir.ehsannarmani.compose_charts.models.LabelProperties
import ir.ehsannarmani.compose_charts.models.Line
import ir.ehsannarmani.compose_charts.models.Pie
import java.text.DateFormatSymbols
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

    var selectedSubTab by remember { mutableIntStateOf(0) } // 0 = Overview, 1 = Ledger, 2 = Analytics
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
        PasteBankSmsDialog(
            onDismiss = { showPasteSmsDialog = false },
            onImport = { rawText, senderHint ->
                viewModel.importManualBankSms(rawText, senderHint) { count, msg ->
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
        TransactionDetailDialog(
            tx = tx,
            onDismiss = { inspectingTx = null },
            onUpdateCategory = { newCat ->
                viewModel.updateFinanceCategory(tx, newCat)
                inspectingTx = tx.copy(category = newCat)
                Toast.makeText(context, "Category updated to $newCat", Toast.LENGTH_SHORT).show()
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

        // 2. Hero Cashflow & Latest Bank Balances Card
        item {
            FinanceHeroSummaryCard(
                summary = summary,
                filterState = filterState,
                isScanning = viewModel.isFinanceScanning,
                hasAnyData = allTransactions.isNotEmpty(),
                onScanSmsClick = triggerSmsScan,
                onPasteSmsClick = { showPasteSmsDialog = true },
                onImportFileClick = {
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
                },
                onManualAddClick = { showManualTxDialog = true },
                onResetFilters = { viewModel.resetFinanceFilters() },
                onClearAllClick = { showClearConfirm = true }
            )
        }

        // 3. Specific Day / Month / Time Window / Bank Filter Controls
        item {
            FinanceFilterPanel(
                filterState = filterState,
                onSelectPeriod = { mode ->
                    when (mode) {
                        FinancePeriodMode.SPECIFIC_DAY -> showDayPicker = true
                        FinancePeriodMode.SPECIFIC_MONTH -> showMonthPicker = true
                        else -> viewModel.setFinancePeriodMode(mode)
                    }
                },
                onOpenTimeWindow = { showTimeWindowDialog = true },
                onSelectBank = { viewModel.setFinanceBankFilter(it) },
                onSelectType = { viewModel.setFinanceTypeFilter(it) },
                onSearchChange = { viewModel.setFinanceSearchQuery(it) }
            )
        }

        // 4. AI Financial Analyst & Conversational Time/Day/Month Q&A Card
        item {
            FinanceAiAnalystCard(
                viewModel = viewModel,
                summary = summary
            )
        }

        // 5. Sub-tabs: Overview | Transactions Ledger | Analytics
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val subTabs = listOf(
                    Triple(0, "Overview", Icons.Default.Dashboard),
                    Triple(1, "Ledger (${filteredTransactions.size})", Icons.AutoMirrored.Filled.ReceiptLong),
                    Triple(2, "Analytics", Icons.Default.BarChart)
                )
                subTabs.forEach { (tabIdx, title, icon) ->
                    val selected = selectedSubTab == tabIdx
                    Surface(
                        onClick = { selectedSubTab = tabIdx },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("finance_subtab_$tabIdx")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
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
                financeAnalyticsItems(summary = summary)
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

    item(key = "overview_monthly_card") {
        MonthlySpendingSummaryCard(
            monthlySpendingTotal = monthlySpendingTotal,
            monthlyIncomeTotal = monthlyIncomeTotal,
            monthlyMonthLabel = monthlyMonthLabel,
            primaryCurrency = primaryCurrency,
            bankCount = groupedByBank.size,
            txCount = transactions.size,
            hasSmsPermission = hasSmsPermission,
            onRequestSmsPermission = onRequestSmsPermission
        )
    }

    item(key = "overview_chart_card") {
        FinanceOverviewChartCard(
            transactions = transactions,
            primaryCurrency = primaryCurrency
        )
    }

    item(key = "overview_recent_header") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RECENT TRANSACTIONS BY BANK",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (hasSmsPermission) {
                TextButton(
                    onClick = onScanSmsClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh SMS", modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sync SMS", fontSize = 12.sp)
                }
            }
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
        MonthlySpendingSummaryCard(
            monthlySpendingTotal = monthlySpendingTotal,
            monthlyIncomeTotal = monthlyIncomeTotal,
            monthlyMonthLabel = monthlyMonthLabel,
            primaryCurrency = primaryCurrency,
            bankCount = groupedByBank.size,
            txCount = transactions.size,
            hasSmsPermission = hasSmsPermission,
            onRequestSmsPermission = onRequestSmsPermission
        )

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
                text = "RECENT TRANSACTIONS BY BANK",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (hasSmsPermission) {
                TextButton(
                    onClick = onScanSmsClick,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh SMS", modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sync SMS", fontSize = 12.sp)
                }
            }
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
private fun MonthlySpendingSummaryCard(
    monthlySpendingTotal: Double,
    monthlyIncomeTotal: Double,
    monthlyMonthLabel: String,
    primaryCurrency: String,
    bankCount: Int,
    txCount: Int,
    hasSmsPermission: Boolean,
    onRequestSmsPermission: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("finance_monthly_spending_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MONTHLY SPENDING TOTAL",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = FinanceAnalyticsEngine.formatMoney(monthlySpendingTotal, primaryCurrency),
                        fontFamily = FrauncesFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = BotanicalAmber
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = monthlyMonthLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Income: ${FinanceAnalyticsEngine.formatMoney(monthlyIncomeTotal, primaryCurrency)}",
                    fontFamily = StudioMonoFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = StudioSuccess,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$bankCount Bank(s) · $txCount tx",
                    fontFamily = StudioMonoFontFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!hasSmsPermission) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = BotanicalAmber.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, BotanicalAmber.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sms,
                                contentDescription = "SMS Permission",
                                tint = BotanicalAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Enable SMS access to auto-read Telebirr, CBE & bank alerts.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onRequestSmsPermission,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("finance_overview_grant_sms_btn")
                        ) {
                            Text("Allow", fontSize = 12.sp)
                        }
                    }
                }
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

private enum class OverviewChartMode {
    TREND_LINE,
    PIE_CHART
}

@Composable
private fun FinanceOverviewChartCard(
    transactions: List<FinanceTransaction>,
    primaryCurrency: String
) {
    var chartMode by remember { mutableStateOf(OverviewChartMode.TREND_LINE) }
    var categorizeByBank by remember { mutableStateOf(false) } // false = By Spending Type, true = By Bank
    var selectedSliceKey by remember { mutableStateOf<String?>(null) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val chartPalette = remember(primaryColor, tertiaryColor) {
        listOf(
            BotanicalAmber,
            primaryColor,
            TelegramAzure,
            tertiaryColor,
            StudioSuccess,
            StudioRecordRed
        )
    }

    val trendSeries = remember(transactions, categorizeByBank) {
        FinanceAnalyticsEngine.buildTrendChartSeries(
            transactions = transactions,
            categorizeByBank = categorizeByBank
        )
    }

    val pieSlices = remember(transactions, categorizeByBank) {
        FinanceAnalyticsEngine.buildPieSlices(
            transactions = transactions,
            categorizeByBank = categorizeByBank
        ).filter { it.amount.isFinite() && it.amount > 0.0 }
    }

    LaunchedEffect(categorizeByBank, pieSlices) {
        if (selectedSliceKey != null && pieSlices.none { it.key == selectedSliceKey }) {
            selectedSliceKey = null
        }
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("finance_overview_chart_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header & Chart Mode Toggle (Trend Line vs Pie Chart)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TRANSACTION VISUALIZATION",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (chartMode == OverviewChartMode.TREND_LINE) {
                            if (categorizeByBank) "Spending Trend by Bank" else "Cashflow Trend Over Time"
                        } else {
                            if (categorizeByBank) "Distribution by Bank" else "Breakdown by Spending Type"
                        },
                        fontFamily = FrauncesFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val trendSelected = chartMode == OverviewChartMode.TREND_LINE
                    Surface(
                        onClick = { chartMode = OverviewChartMode.TREND_LINE },
                        shape = RoundedCornerShape(12.dp),
                        color = if (trendSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, if (trendSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("finance_chart_mode_trend")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ShowChart,
                                contentDescription = "Trend Line Chart",
                                tint = if (trendSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Trend",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (trendSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val pieSelected = chartMode == OverviewChartMode.PIE_CHART
                    Surface(
                        onClick = { chartMode = OverviewChartMode.PIE_CHART },
                        shape = RoundedCornerShape(12.dp),
                        color = if (pieSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, if (pieSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("finance_chart_mode_pie")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PieChart,
                                contentDescription = "Pie Chart",
                                tint = if (pieSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Pie",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (pieSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Categorization Filter: By Spending Type vs By Bank
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !categorizeByBank,
                    onClick = { categorizeByBank = false },
                    label = {
                        Text(
                            text = "By Spending Type",
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Category,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("finance_chart_group_category")
                )
                FilterChip(
                    selected = categorizeByBank,
                    onClick = { categorizeByBank = true },
                    label = {
                        Text(
                            text = "By Bank",
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.AccountBalance,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("finance_chart_group_bank")
                )
            }

            if (transactions.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (chartMode == OverviewChartMode.TREND_LINE) Icons.AutoMirrored.Filled.ShowChart else Icons.Default.PieChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "No transaction history to chart yet",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Scan your SMS inbox or paste a Telebirr/bank alert to visualize spending trends and bank distribution.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (chartMode == OverviewChartMode.TREND_LINE) {
                val safeMaxY = remember(trendSeries) {
                    val m = trendSeries.maxPointValue
                    if (m.isFinite() && m > 0.0) m * 1.15 else 1.0
                }
                val chartLines = remember(trendSeries, chartPalette) {
                    if (!trendSeries.hasPlottableSeries) {
                        emptyList()
                    } else {
                        trendSeries.lines.mapIndexedNotNull { idx, series ->
                            val cleanValues = series.values.map { if (it.isFinite() && it >= 0.0) it else 0.0 }
                            if (cleanValues.size < 2 || cleanValues.all { it == 0.0 }) return@mapIndexedNotNull null
                            val lineColor = when (series.key) {
                                "OUTFLOW" -> BotanicalAmber
                                "INCOME" -> StudioSuccess
                                else -> chartPalette[idx % chartPalette.size]
                            }
                            Line(
                                label = series.label,
                                values = cleanValues,
                                color = SolidColor(lineColor),
                                firstGradientFillColor = lineColor.copy(alpha = 0.28f),
                                secondGradientFillColor = Color.Transparent,
                                strokeAnimationSpec = tween(420),
                                gradientAnimationDelay = 120,
                                drawStyle = DrawStyle.Stroke(width = 3.dp)
                            )
                        }
                    }
                }

                if (chartLines.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Add transactions with non-zero amounts to draw the trend line chart.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                } else {
                    val axisTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    val labelStyle = remember(axisTextColor) {
                        TextStyle(
                            color = axisTextColor,
                            fontSize = 10.sp,
                            fontFamily = StudioMonoFontFamily
                        )
                    }

                    LineChart(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(206.dp)
                            .padding(top = 6.dp)
                            .testTag("finance_line_chart"),
                        data = chartLines,
                        minValue = 0.0,
                        maxValue = safeMaxY,
                        gridProperties = GridProperties(enabled = true),
                        indicatorProperties = HorizontalIndicatorProperties(
                            enabled = true,
                            textStyle = labelStyle
                        ),
                        labelProperties = LabelProperties(
                            enabled = trendSeries.dateLabels.size >= 2,
                            labels = trendSeries.dateLabels,
                            textStyle = labelStyle
                        ),
                        labelHelperProperties = LabelHelperProperties(enabled = false)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        trendSeries.lines.forEachIndexed { idx, series ->
                            val dotColor = when (series.key) {
                                "OUTFLOW" -> BotanicalAmber
                                "INCOME" -> StudioSuccess
                                else -> chartPalette[idx % chartPalette.size]
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = dotColor.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, dotColor.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(dotColor)
                                    )
                                    Text(
                                        text = "${series.label}: ${FinanceAnalyticsEngine.formatMoney(series.totalAmount, primaryCurrency)}",
                                        fontFamily = StudioMonoFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Pie / Donut Chart mode
                val pieTotal = remember(pieSlices) { pieSlices.sumOf { it.amount } }
                if (pieSlices.isEmpty() || !pieTotal.isFinite() || pieTotal <= 0.0) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No transactions available in this filter to slice.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                } else {
                    val pieModels = remember(pieSlices, selectedSliceKey, chartPalette) {
                        pieSlices.mapIndexed { idx, slice ->
                            val baseColor = chartPalette[idx % chartPalette.size]
                            val isSelected = slice.key == selectedSliceKey
                            Pie(
                                label = slice.label,
                                data = slice.amount,
                                color = baseColor,
                                selectedColor = baseColor.copy(alpha = 0.85f),
                                selected = isSelected
                            )
                        }
                    }

                    val activeSlice = pieSlices.firstOrNull { it.key == selectedSliceKey }
                        ?: pieSlices.firstOrNull()

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        PieChart(
                            modifier = Modifier
                                .size(196.dp)
                                .testTag("finance_pie_chart"),
                            data = pieModels,
                            onPieClick = { clickedPie ->
                                val clickedIndex = pieModels.indexOf(clickedPie)
                                val targetKey = pieSlices.getOrNull(clickedIndex)?.key
                                selectedSliceKey = if (selectedSliceKey == targetKey) null else targetKey
                            },
                            selectedScale = 1.10f,
                            spaceDegree = if (pieModels.size > 1) 4f else 0f,
                            selectedPaddingDegree = if (pieModels.size > 1) 3f else 0f,
                            scaleAnimEnterSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            colorAnimEnterSpec = tween(250),
                            style = Pie.Style.Stroke(width = 36.dp)
                        )

                        activeSlice?.let { focused ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.widthIn(max = 104.dp)
                            ) {
                                Text(
                                    text = "${(focused.shareFraction * 100).toInt()}%",
                                    fontFamily = FrauncesFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = focused.label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        pieSlices.forEachIndexed { idx, slice ->
                            val sliceColor = chartPalette[idx % chartPalette.size]
                            val isSelected = slice.key == selectedSliceKey
                            Surface(
                                onClick = {
                                    selectedSliceKey = if (isSelected) null else slice.key
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) sliceColor.copy(alpha = 0.14f) else Color.Transparent,
                                border = if (isSelected) BorderStroke(1.dp, sliceColor.copy(alpha = 0.5f)) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(sliceColor)
                                        )
                                        Column {
                                            Text(
                                                text = slice.label,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "${slice.transactionCount} tx · ${(slice.shareFraction * 100).toInt()}%",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = FinanceAnalyticsEngine.formatMoney(slice.amount, primaryCurrency),
                                        fontFamily = StudioMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun FinanceHeroSummaryCard(
    summary: FinanceSummary,
    filterState: FinanceFilterState,
    isScanning: Boolean,
    hasAnyData: Boolean,
    onScanSmsClick: () -> Unit,
    onPasteSmsClick: () -> Unit,
    onImportFileClick: () -> Unit,
    onManualAddClick: () -> Unit,
    onResetFilters: () -> Unit,
    onClearAllClick: () -> Unit
) {
    val hasActiveFilter = filterState != FinanceFilterState()
    val netPositive = summary.netFlow >= 0.0

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = summary.periodLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                    if (hasActiveFilter) {
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = onResetFilters,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterAltOff,
                                contentDescription = "Reset date and bank filters",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                if (hasAnyData) {
                    IconButton(
                        onClick = onClearAllClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear Finance Ledger",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Column {
                Text(
                    text = "NET CASHFLOW",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${if (netPositive) "+" else "-"}${FinanceAnalyticsEngine.formatMoney(kotlin.math.abs(summary.netFlow), summary.primaryCurrency)}",
                    fontFamily = FrauncesFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = if (netPositive) StudioSuccess else MaterialTheme.colorScheme.error
                )
                Text(
                    text = "${summary.transactionCount} transaction(s) · Fees ${FinanceAnalyticsEngine.formatMoney(summary.totalFees, summary.primaryCurrency)}",
                    fontFamily = StudioMonoFontFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = StudioSuccess.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, StudioSuccess.copy(alpha = 0.30f)),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("INCOME", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = StudioSuccess)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = FinanceAnalyticsEngine.formatMoney(summary.totalIncome, summary.primaryCurrency),
                            fontFamily = StudioMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = BotanicalAmber.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, BotanicalAmber.copy(alpha = 0.32f)),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("OUTFLOW", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = BotanicalAmber)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = FinanceAnalyticsEngine.formatMoney(summary.totalOutflow, summary.primaryCurrency),
                            fontFamily = StudioMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            val balances = summary.bankBreakdowns.filter { it.latestBalance != null }
            if (balances.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "LATEST BANK BALANCES",
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
                        balances.forEach { b ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text(
                                        text = b.bankName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = FinanceAnalyticsEngine.formatMoney(b.latestBalance!!, b.latestCurrency),
                                        fontFamily = StudioMonoFontFamily,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Balanced 2x2 Action Grid
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onScanSmsClick,
                        enabled = !isScanning,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("finance_scan_sms_btn")
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scanning...", fontSize = 12.sp, maxLines = 1)
                        } else {
                            Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan Inbox", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    OutlinedButton(
                        onClick = onPasteSmsClick,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("finance_paste_sms_btn")
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Paste SMS", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onImportFileClick,
                        enabled = !isScanning,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("finance_import_file_btn")
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import File", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    OutlinedButton(
                        onClick = onManualAddClick,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("finance_add_manual_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Entry", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
internal fun FinanceFilterPanel(
    filterState: FinanceFilterState,
    onSelectPeriod: (FinancePeriodMode) -> Unit,
    onOpenTimeWindow: () -> Unit,
    onSelectBank: (String) -> Unit,
    onSelectType: (String) -> Unit,
    onSearchChange: (String) -> Unit
) {
    var filtersExpanded by remember { mutableStateOf(false) }

    val activeFilterCount = remember(filterState) {
        var count = 0
        if (filterState.periodMode != FinancePeriodMode.ALL) count++
        if (filterState.timePreset != TimeOfDayPreset.ANY) count++
        if (filterState.selectedBankCode != "ALL") count++
        if (filterState.typeFilter != "ALL") count++
        count
    }

    val filterSummaryText = remember(filterState) {
        val periodText = when (filterState.periodMode) {
            FinancePeriodMode.SPECIFIC_DAY -> filterState.selectedDayMillis?.let {
                SimpleDateFormat("MMM dd", Locale.ENGLISH).format(Date(it))
            } ?: "Specific Day"
            FinancePeriodMode.SPECIFIC_MONTH -> if (filterState.selectedMonthZeroBased != null) {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, filterState.selectedYear ?: get(Calendar.YEAR))
                    set(Calendar.MONTH, filterState.selectedMonthZeroBased)
                }
                SimpleDateFormat("MMM yyyy", Locale.ENGLISH).format(cal.time)
            } else "Specific Month"
            else -> filterState.periodMode.label
        }
        val bankText = if (filterState.selectedBankCode == "ALL") {
            "All Banks"
        } else {
            BankInstitution.fromCode(filterState.selectedBankCode).shortBadge
        }
        val typeText = when (filterState.typeFilter) {
            "INCOME" -> "Income"
            "EXPENSE" -> "Outflow"
            else -> null
        }
        listOfNotNull(periodText, bankText, typeText).joinToString(" · ")
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = filterState.searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search merchant, bank, reference...", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (filterState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("finance_search_input")
            )

            Surface(
                onClick = { filtersExpanded = !filtersExpanded },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(
                    1.dp,
                    if (activeFilterCount > 0 || filtersExpanded) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("finance_toggle_filters_btn")
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Filters",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Filters",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (activeFilterCount > 0) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary
                                    ) {
                                        Text(
                                            text = activeFilterCount.toString(),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = filterSummaryText,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (filtersExpanded) "Hide" else "Show",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = if (filtersExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (filtersExpanded) "Collapse filters" else "Expand filters",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = filtersExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "DAY, MONTH & TIME WINDOW",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.7.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
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

                        val timeLabel = if (filterState.timePreset == TimeOfDayPreset.ANY) {
                            "Time Window"
                        } else if (filterState.timePreset == TimeOfDayPreset.CUSTOM) {
                            String.format(
                                Locale.ENGLISH,
                                "%02d:%02d–%02d:%02d",
                                filterState.customStartHour,
                                filterState.customStartMinute,
                                filterState.customEndHour,
                                filterState.customEndMinute
                            )
                        } else {
                            filterState.timePreset.name.lowercase(Locale.ENGLISH)
                                .replaceFirstChar { it.uppercaseChar() }
                        }
                        BotanicalFilterChip(
                            label = timeLabel,
                            selected = filterState.timePreset != TimeOfDayPreset.ANY,
                            onClick = onOpenTimeWindow
                        )
                    }

                    Text(
                        text = "BANK / WALLET",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.7.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
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

                    Text(
                        text = "TRANSACTION FLOW",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.7.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
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
            }
        }
    }
}

@Composable
private fun FinanceAiAnalystCard(
    viewModel: VoiceTaskViewModel,
    summary: FinanceSummary
) {
    var question by remember { mutableStateOf("") }
    val messages = viewModel.financeChatMessages
    val fullReport = viewModel.financeAiReport
    val isBusy = viewModel.isFinanceAiBusy

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, BotanicalSage),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Finance Analyst",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "AI Financial Analyst",
                            fontFamily = FrauncesFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "Ask by time, day, month, or bank",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (messages.isNotEmpty() || fullReport != null) {
                    IconButton(
                        onClick = { viewModel.clearFinanceChat() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.ClearAll, contentDescription = "Clear AI Finance answers", modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Quick Prompt Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val quickPrompts = listOf(
                    "How much did I spend today?",
                    "This month summary",
                    "Yesterday afternoon activity",
                    "Latest bank balances",
                    "Telebirr & CBE breakdown"
                )
                quickPrompts.forEach { sample ->
                    AssistChip(
                        onClick = { viewModel.askFinanceAi(sample) },
                        label = { Text(sample, fontSize = 11.sp) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    placeholder = { Text("e.g. CBE spending in Oct or today 2pm–6pm", fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("finance_ai_input")
                )
                FilledIconButton(
                    onClick = {
                        val q = question
                        question = ""
                        viewModel.askFinanceAi(q)
                    },
                    enabled = question.isNotBlank() && !isBusy,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("finance_ai_send_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Ask Finance AI")
                }
            }

            FilledTonalButton(
                onClick = { viewModel.runFullFinanceAiReport() },
                enabled = !isBusy,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .testTag("finance_full_ai_report_btn")
            ) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Analyzing SMS ledger...")
                } else {
                    Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Run AI Analysis (${summary.periodLabel})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            AnimatedVisibility(visible = !fullReport.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "AI LEDGER REPORT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.7.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = fullReport.orEmpty(),
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            messages.takeLast(6).forEach { msg ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (msg.isUser) {
                            BotanicalSage.copy(alpha = 0.36f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.widthIn(max = 520.dp)
                    ) {
                        Column(modifier = Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (!msg.isUser && msg.matchedPeriodLabel.isNotBlank()) {
                                Text(
                                    text = "${msg.matchedPeriodLabel} · ${msg.matchedCount} tx",
                                    fontFamily = StudioMonoFontFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = msg.text,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FinanceTransactionCard(
    tx: FinanceTransaction,
    onClick: () -> Unit
) {
    val dateFmt = remember { SimpleDateFormat("MMM dd, yyyy · HH:mm", Locale.ENGLISH) }
    val amountColor = if (tx.isIncoming) StudioSuccess else MaterialTheme.colorScheme.onSurface
    val badgeColor = when (tx.bankCode) {
        BankInstitution.TELEBIRR.code -> StudioSuccess.copy(alpha = 0.16f)
        BankInstitution.CBE.code, BankInstitution.CBE_BIRR.code -> MaterialTheme.colorScheme.primaryContainer
        BankInstitution.BOA.code -> BotanicalAmber.copy(alpha = 0.18f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = badgeColor
                    ) {
                        Text(
                            text = tx.bankName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = tx.category,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }

                Text(
                    text = FinanceAnalyticsEngine.formatSignedMoney(tx),
                    fontFamily = StudioMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = amountColor
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tx.counterparty.ifBlank { tx.bankName },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = dateFmt.format(Date(tx.timestamp)),
                    fontFamily = StudioMonoFontFamily,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val metaParts = buildList {
                if (tx.balanceAfter != null) add("Bal: ${FinanceAnalyticsEngine.formatMoney(tx.balanceAfter, tx.currency)}")
                if (tx.serviceCharge != null && tx.serviceCharge > 0.0) add("Fee: ${FinanceAnalyticsEngine.formatMoney(tx.serviceCharge, tx.currency)}")
                if (tx.referenceId.isNotBlank()) add("Ref: ${tx.referenceId}")
            }
            if (metaParts.isNotEmpty()) {
                Text(
                    text = metaParts.joinToString(" · "),
                    fontFamily = StudioMonoFontFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun LazyListScope.financeAnalyticsItems(summary: FinanceSummary) {
    item(key = "analytics_bank_comparison") {
        AnalyticsBankComparisonCard(summary = summary)
    }
    item(key = "analytics_category_breakdown") {
        AnalyticsCategoryBreakdownCard(summary = summary)
    }
    item(key = "analytics_time_activity") {
        AnalyticsTimeActivityCard(summary = summary)
    }
}

@Composable
private fun AnalyticsBankComparisonCard(summary: FinanceSummary) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Income vs Outflow by Bank",
                fontFamily = FrauncesFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp
            )
            val activeBanks = summary.bankBreakdowns.filter { it.income > 0 || it.outflow > 0 }
            if (activeBanks.isEmpty()) {
                Text(
                    text = "No transactions in this period to chart.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                BankComparisonCanvasChart(banks = activeBanks.take(6))
            }
        }
    }
}

@Composable
private fun AnalyticsCategoryBreakdownCard(summary: FinanceSummary) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Outflow by Category",
                fontFamily = FrauncesFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp
            )
            if (summary.categoryBreakdowns.isEmpty()) {
                Text(
                    text = "No outgoing transactions in this period.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                summary.categoryBreakdowns.forEach { cat ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${cat.category} (${cat.count})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${FinanceAnalyticsEngine.formatMoney(cat.totalAmount, summary.primaryCurrency)} · ${(cat.shareFraction * 100).toInt()}%",
                                fontFamily = StudioMonoFontFamily,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        LinearProgressIndicator(
                            progress = { cat.shareFraction.coerceIn(0.03f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsTimeActivityCard(summary: FinanceSummary) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Activity by Time of Day",
                fontFamily = FrauncesFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp
            )
            val maxSlot = summary.timeSlotStats.maxOfOrNull { it.outflow + it.income }?.coerceAtLeast(1.0) ?: 1.0
            summary.timeSlotStats.forEach { slot ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${slot.label} (${slot.rangeHint}) · ${slot.count} tx",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "-${FinanceAnalyticsEngine.formatMoney(slot.outflow, summary.primaryCurrency)}",
                            fontFamily = StudioMonoFontFamily,
                            fontSize = 11.sp,
                            color = BotanicalAmber
                        )
                    }
                    LinearProgressIndicator(
                        progress = { ((slot.outflow + slot.income) / maxSlot).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = BotanicalAmber,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun BankComparisonCanvasChart(banks: List<BankBreakdownStat>) {
    val maxVal = banks.maxOfOrNull { maxOf(it.income, it.outflow) }?.coerceAtLeast(1.0) ?: 1.0
    val incomeColor = StudioSuccess
    val outflowColor = BotanicalAmber
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(incomeColor))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Income", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(outflowColor))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Outflow", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        banks.forEach { stat ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stat.bankName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "+${stat.income.toInt()} / -${stat.outflow.toInt()} ${stat.latestCurrency}",
                        fontFamily = StudioMonoFontFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                ) {
                    val barH = size.height / 2.4f
                    val radius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(0f, 0f),
                        size = Size(size.width, barH),
                        cornerRadius = radius
                    )
                    val inW = ((stat.income / maxVal).toFloat() * size.width).coerceAtLeast(if (stat.income > 0) 6.dp.toPx() else 0f)
                    if (inW > 0f) {
                        drawRoundRect(
                            color = incomeColor,
                            topLeft = Offset(0f, 0f),
                            size = Size(inW, barH),
                            cornerRadius = radius
                        )
                    }

                    val secondY = barH + 3.dp.toPx()
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(0f, secondY),
                        size = Size(size.width, barH),
                        cornerRadius = radius
                    )
                    val outW = ((stat.outflow / maxVal).toFloat() * size.width).coerceAtLeast(if (stat.outflow > 0) 6.dp.toPx() else 0f)
                    if (outW > 0f) {
                        drawRoundRect(
                            color = outflowColor,
                            topLeft = Offset(0f, secondY),
                            size = Size(outW, barH),
                            cornerRadius = radius
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FinanceEmptyLedgerState(
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
private fun FinanceDayPickerDialog(
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
private fun FinanceMonthPickerDialog(
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
private fun FinanceTimeWindowDialog(
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
private fun PasteBankSmsDialog(
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
private fun AddManualTransactionDialog(
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
private fun TransactionDetailDialog(
    tx: FinanceTransaction,
    onDismiss: () -> Unit,
    onUpdateCategory: (String) -> Unit,
    onDelete: () -> Unit
) {
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
                Text("Raw SMS Message", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
