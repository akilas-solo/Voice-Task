package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BankInstitution
import com.example.data.FinanceAnalyticsEngine
import com.example.data.FinanceFilterState
import com.example.data.FinancePeriodMode
import com.example.data.FinanceSummary
import com.example.data.FinanceTransaction
import com.example.data.TimeOfDayPreset
import com.example.ui.theme.BotanicalAmber
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class OverviewChartMode {
    TREND_LINE,
    PIE_CHART
}

@Composable
internal fun FinanceOverviewChartCard(
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
                        text = "VISUALIZATION",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (chartMode == OverviewChartMode.TREND_LINE) {
                            if (categorizeByBank) "Spending by Bank" else "Cashflow Trend"
                        } else {
                            if (categorizeByBank) "By Bank" else "Spending Mix"
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

            // Categorization Filter: By Type vs By Bank
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
                            text = "Tap Sync SMS or use + to paste or import a Telebirr/bank alert.",
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
                    val xAxisTicks = remember(trendSeries.dateLabels) {
                        sampleAxisLabels(trendSeries.dateLabels, maxTicks = 4)
                    }

                    LineChart(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(194.dp)
                            .padding(top = 6.dp)
                            .testTag("finance_line_chart"),
                        data = chartLines,
                        minValue = 0.0,
                        maxValue = safeMaxY,
                        gridProperties = GridProperties(enabled = true),
                        indicatorProperties = HorizontalIndicatorProperties(
                            enabled = true,
                            textStyle = labelStyle,
                            contentBuilder = { value -> formatCompactAxisAmount(value) }
                        ),
                        labelProperties = LabelProperties(enabled = false),
                        labelHelperProperties = LabelHelperProperties(enabled = false)
                    )

                    if (xAxisTicks.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 42.dp, end = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            xAxisTicks.forEach { tick ->
                                Text(
                                    text = tick,
                                    fontFamily = StudioMonoFontFamily,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    color = axisTextColor
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        trendSeries.lines.withIndex().chunked(2).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { (idx, series) ->
                                    val dotColor = when (series.key) {
                                        "OUTFLOW" -> BotanicalAmber
                                        "INCOME" -> StudioSuccess
                                        else -> chartPalette[idx % chartPalette.size]
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = dotColor.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, dotColor.copy(alpha = 0.35f)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
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
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                                if (rowItems.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
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

private fun sampleAxisLabels(labels: List<String>, maxTicks: Int = 4): List<String> {
    if (labels.isEmpty()) return emptyList()
    if (labels.size <= maxTicks) return labels
    val indices = (0 until maxTicks).map { step ->
        ((step.toDouble() / (maxTicks - 1)) * (labels.size - 1)).toInt()
    }.distinct()
    return indices.map { labels[it] }
}

private fun formatCompactAxisAmount(value: Double): String {
    val abs = kotlin.math.abs(value)
    return when {
        abs >= 1_000_000.0 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
        abs >= 1_000.0 -> String.format(Locale.US, "%.1fk", value / 1_000.0)
        abs >= 100.0 -> String.format(Locale.US, "%.0f", value)
        else -> String.format(Locale.US, "%.1f", value)
    }
}

@Composable
internal fun FinanceHeroSummaryCard(
    summary: FinanceSummary,
    monthlySpendingTotal: Double = 0.0,
    monthlyIncomeTotal: Double = 0.0,
    monthlyLabel: String = "",
    filterState: FinanceFilterState,
    isScanning: Boolean,
    hasAnyData: Boolean,
    hasSmsPermission: Boolean = true,
    onRequestSmsPermission: () -> Unit = {},
    onScanSmsClick: () -> Unit,
    onPasteSmsClick: () -> Unit,
    onImportFileClick: () -> Unit,
    onManualAddClick: () -> Unit,
    onResetFilters: () -> Unit,
    onClearAllClick: () -> Unit
) {
    val hasActiveFilter = filterState != FinanceFilterState()
    val netPositive = summary.netFlow >= 0.0
    var showAddMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top bar: Active Period Pill on left + Single Sync & [+] Menu on right
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
                            modifier = Modifier.size(36.dp)
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (hasSmsPermission) {
                        FilledTonalButton(
                            onClick = onScanSmsClick,
                            enabled = !isScanning,
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("finance_scan_sms_btn")
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(15.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Syncing", fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sync SMS", fontSize = 12.sp)
                            }
                        }
                    }

                    Box {
                        FilledIconButton(
                            onClick = { showAddMenu = true },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("finance_add_menu_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add or import transactions"
                            )
                        }

                        DropdownMenu(
                            expanded = showAddMenu,
                            onDismissRequest = { showAddMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Paste Bank SMS") },
                                leadingIcon = { Icon(Icons.Default.ContentPaste, contentDescription = null) },
                                onClick = {
                                    showAddMenu = false
                                    onPasteSmsClick()
                                },
                                modifier = Modifier.testTag("finance_menu_paste_sms")
                            )
                            DropdownMenuItem(
                                text = { Text("Import Backup File") },
                                leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = null) },
                                enabled = !isScanning,
                                onClick = {
                                    showAddMenu = false
                                    onImportFileClick()
                                },
                                modifier = Modifier.testTag("finance_menu_import_file")
                            )
                            DropdownMenuItem(
                                text = { Text("Add Manual Entry") },
                                leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null) },
                                onClick = {
                                    showAddMenu = false
                                    onManualAddClick()
                                },
                                modifier = Modifier.testTag("finance_menu_add_manual")
                            )
                            DropdownMenuItem(
                                text = { Text(if (hasSmsPermission) "Re-scan 90d Inbox" else "Allow & Scan SMS") },
                                leadingIcon = { Icon(Icons.Default.Sms, contentDescription = null) },
                                enabled = !isScanning,
                                onClick = {
                                    showAddMenu = false
                                    onScanSmsClick()
                                },
                                modifier = Modifier.testTag("finance_menu_scan_sms")
                            )
                            if (hasAnyData) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                DropdownMenuItem(
                                    text = { Text("Clear Ledger", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.DeleteSweep,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showAddMenu = false
                                        onClearAllClick()
                                    },
                                    modifier = Modifier.testTag("finance_menu_clear_all")
                                )
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "NET CASHFLOW",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${if (netPositive) "+" else "-"}${FinanceAnalyticsEngine.formatMoney(kotlin.math.abs(summary.netFlow), summary.primaryCurrency)}",
                    fontFamily = FrauncesFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 27.sp,
                    color = if (netPositive) StudioSuccess else MaterialTheme.colorScheme.error
                )
                val monthlyNote = if (monthlyLabel.isNotBlank()) {
                    "$monthlyLabel spend ${FinanceAnalyticsEngine.formatMoney(monthlySpendingTotal, summary.primaryCurrency)} · "
                } else ""
                Text(
                    text = "${monthlyNote}${summary.transactionCount} tx · Fees ${FinanceAnalyticsEngine.formatMoney(summary.totalFees, summary.primaryCurrency)}",
                    fontFamily = StudioMonoFontFamily,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                                Column(
                                    modifier = Modifier
                                        .widthIn(min = 104.dp, max = 164.dp)
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = BankInstitution.fromCode(b.bankCode).shortBadge.ifBlank { b.bankName },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = FinanceAnalyticsEngine.formatMoney(b.latestBalance!!, b.latestCurrency),
                                        fontFamily = StudioMonoFontFamily,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
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
                                text = "Allow SMS access to auto-read bank alerts, or tap + to paste/import.",
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
