package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.BankBreakdownStat
import com.example.data.BankInstitution
import com.example.data.FinanceAnalyticsEngine
import com.example.data.FinanceSummary
import com.example.data.FinanceTransaction
import com.example.ui.theme.BotanicalAmber
import com.example.ui.theme.BotanicalSage
import com.example.ui.theme.FrauncesFontFamily
import com.example.ui.theme.StudioMonoFontFamily
import com.example.ui.theme.StudioSuccess
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceAiModalSheet(
    viewModel: VoiceTaskViewModel,
    onDismiss: () -> Unit
) {
    val summary by viewModel.financeSummary.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FinanceAiAnalystCard(
                viewModel = viewModel,
                summary = summary
            )
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
internal fun FinanceTransactionCard(
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
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
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

internal fun LazyListScope.financeAnalyticsItems(
    transactions: List<FinanceTransaction>,
    summary: FinanceSummary
) {
    item(key = "analytics_spending_mix_pie") {
        FinanceOverviewChartCard(
            transactions = transactions,
            primaryCurrency = summary.primaryCurrency,
            lockedMode = OverviewChartMode.PIE_CHART
        )
    }
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
