package com.example.data

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class BankBreakdownStat(
    val bankCode: String,
    val bankName: String,
    val income: Double,
    val outflow: Double,
    val count: Int,
    val latestBalance: Double?,
    val latestCurrency: String,
    val latestTimestamp: Long
)

data class CategoryBreakdownStat(
    val category: String,
    val totalAmount: Double,
    val count: Int,
    val shareFraction: Float
)

data class TimeSlotStat(
    val label: String,
    val rangeHint: String,
    val outflow: Double,
    val income: Double,
    val count: Int
)

data class BankGroupedTransactions(
    val bankCode: String,
    val bankName: String,
    val totalOutflow: Double,
    val totalIncome: Double,
    val latestBalance: Double?,
    val currency: String,
    val recentTransactions: List<FinanceTransaction>
)

data class ChartTrendLineSeries(
    val key: String,
    val label: String,
    val values: List<Double>,
    val totalAmount: Double
)

data class ChartTrendSeries(
    val dateLabels: List<String>,
    val lines: List<ChartTrendLineSeries>
)

data class ChartCategorySlice(
    val key: String,
    val label: String,
    val amount: Double,
    val shareFraction: Float,
    val transactionCount: Int
)

data class FinanceSummary(
    val transactionCount: Int = 0,
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val totalTransferOut: Double = 0.0,
    val totalOutflow: Double = 0.0,
    val netFlow: Double = 0.0,
    val totalFees: Double = 0.0,
    val primaryCurrency: String = "ETB",
    val periodLabel: String = "All Time",
    val bankBreakdowns: List<BankBreakdownStat> = emptyList(),
    val categoryBreakdowns: List<CategoryBreakdownStat> = emptyList(),
    val timeSlotStats: List<TimeSlotStat> = emptyList()
)

data class ParsedFinanceQuery(
    val filterState: FinanceFilterState,
    val periodLabel: String,
    val intentType: String // "ALL", "INCOME", "OUTFLOW", "BALANCE", "FEE"
)

object FinanceAnalyticsEngine {

    fun formatMoney(amount: Double, currency: String = "ETB"): String {
        val nf = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        return "$currency ${nf.format(amount)}"
    }

    fun formatSignedMoney(tx: FinanceTransaction): String {
        val sign = if (tx.isIncoming) "+" else "-"
        return "$sign${formatMoney(tx.amount, tx.currency)}"
    }

    fun computeMonthlySpendingTotal(
        transactions: List<FinanceTransaction>,
        nowMillis: Long = System.currentTimeMillis()
    ): Double {
        val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val targetYear = nowCal.get(Calendar.YEAR)
        val targetMonth = nowCal.get(Calendar.MONTH)
        val cal = Calendar.getInstance()
        return transactions.filter { tx ->
            cal.timeInMillis = tx.timestamp
            tx.isOutgoing &&
                cal.get(Calendar.YEAR) == targetYear &&
                cal.get(Calendar.MONTH) == targetMonth
        }.sumOf { it.amount }
    }

    fun computeMonthlyIncomeTotal(
        transactions: List<FinanceTransaction>,
        nowMillis: Long = System.currentTimeMillis()
    ): Double {
        val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val targetYear = nowCal.get(Calendar.YEAR)
        val targetMonth = nowCal.get(Calendar.MONTH)
        val cal = Calendar.getInstance()
        return transactions.filter { tx ->
            cal.timeInMillis = tx.timestamp
            tx.isIncoming &&
                cal.get(Calendar.YEAR) == targetYear &&
                cal.get(Calendar.MONTH) == targetMonth
        }.sumOf { it.amount }
    }

    fun computeMonthlyTransactionCount(
        transactions: List<FinanceTransaction>,
        nowMillis: Long = System.currentTimeMillis()
    ): Int {
        val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val targetYear = nowCal.get(Calendar.YEAR)
        val targetMonth = nowCal.get(Calendar.MONTH)
        val cal = Calendar.getInstance()
        return transactions.count { tx ->
            cal.timeInMillis = tx.timestamp
            cal.get(Calendar.YEAR) == targetYear &&
                cal.get(Calendar.MONTH) == targetMonth
        }
    }

    fun groupRecentTransactionsByBank(
        transactions: List<FinanceTransaction>,
        limitPerBank: Int = 5
    ): List<BankGroupedTransactions> {
        if (transactions.isEmpty()) return emptyList()
        return transactions
            .sortedByDescending { it.timestamp }
            .groupBy { it.bankCode }
            .map { (code, groupList) ->
                val latestTx = groupList.first()
                val latestBalanceTx = groupList.firstOrNull { it.balanceAfter != null }
                BankGroupedTransactions(
                    bankCode = code,
                    bankName = latestTx.bankName,
                    totalOutflow = groupList.filter { it.isOutgoing }.sumOf { it.amount },
                    totalIncome = groupList.filter { it.isIncoming }.sumOf { it.amount },
                    latestBalance = latestBalanceTx?.balanceAfter,
                    currency = latestBalanceTx?.currency ?: latestTx.currency,
                    recentTransactions = groupList.take(limitPerBank)
                )
            }
            .sortedByDescending { it.recentTransactions.firstOrNull()?.timestamp ?: 0L }
    }

    fun buildTrendChartSeries(
        transactions: List<FinanceTransaction>,
        categorizeByBank: Boolean,
        maxDays: Int = 14
    ): ChartTrendSeries {
        if (transactions.isEmpty()) return ChartTrendSeries(emptyList(), emptyList())

        val keyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val labelFmt = SimpleDateFormat("MMM d", Locale.ENGLISH)
        val sortedAsc = transactions.sortedBy { it.timestamp }

        val groupedByDay = linkedMapOf<String, MutableList<FinanceTransaction>>()
        sortedAsc.forEach { tx ->
            val key = keyFmt.format(Date(tx.timestamp))
            groupedByDay.getOrPut(key) { mutableListOf() }.add(tx)
        }

        val dayEntries = groupedByDay.entries.toList().takeLast(maxDays)
        val rawLabels = dayEntries.map { (_, list) -> labelFmt.format(Date(list.first().timestamp)) }
        val needsBaselinePoint = dayEntries.size == 1
        val finalLabels = if (needsBaselinePoint) listOf("Start") + rawLabels else rawLabels

        if (categorizeByBank) {
            val useOutflowOnly = transactions.any { it.isOutgoing }
            val topBanks = transactions
                .groupBy { it.bankCode to it.bankName }
                .map { (pair, list) ->
                    val vol = list.filter { !useOutflowOnly || it.isOutgoing }.sumOf { it.amount }
                    Triple(pair.first, pair.second, vol)
                }
                .filter { it.third > 0.0 }
                .sortedByDescending { it.third }
                .take(4)

            val lines = topBanks.map { (bankCode, bankName, totalVol) ->
                val dayValues = dayEntries.map { (_, dayTxs) ->
                    dayTxs.filter {
                        it.bankCode == bankCode && (!useOutflowOnly || it.isOutgoing)
                    }.sumOf { it.amount }
                }
                val paddedValues = if (needsBaselinePoint) listOf(0.0) + dayValues else dayValues
                ChartTrendLineSeries(
                    key = bankCode,
                    label = bankName,
                    values = paddedValues,
                    totalAmount = totalVol
                )
            }
            return ChartTrendSeries(dateLabels = finalLabels, lines = lines)
        } else {
            val outflowValues = dayEntries.map { (_, dayTxs) ->
                dayTxs.filter { it.isOutgoing }.sumOf { it.amount }
            }.let { if (needsBaselinePoint) listOf(0.0) + it else it }

            val incomeValues = dayEntries.map { (_, dayTxs) ->
                dayTxs.filter { it.isIncoming }.sumOf { it.amount }
            }.let { if (needsBaselinePoint) listOf(0.0) + it else it }

            val totalOutflow = outflowValues.sum()
            val totalIncome = incomeValues.sum()
            val lines = buildList {
                if (totalOutflow > 0.0 || totalIncome == 0.0) {
                    add(
                        ChartTrendLineSeries(
                            key = "OUTFLOW",
                            label = "Spending",
                            values = outflowValues,
                            totalAmount = totalOutflow
                        )
                    )
                }
                if (totalIncome > 0.0) {
                    add(
                        ChartTrendLineSeries(
                            key = "INCOME",
                            label = "Income",
                            values = incomeValues,
                            totalAmount = totalIncome
                        )
                    )
                }
            }
            return ChartTrendSeries(dateLabels = finalLabels, lines = lines)
        }
    }

    fun buildPieSlices(
        transactions: List<FinanceTransaction>,
        categorizeByBank: Boolean,
        maxSlices: Int = 5
    ): List<ChartCategorySlice> {
        if (transactions.isEmpty()) return emptyList()
        val outflows = transactions.filter { it.isOutgoing }
        val sourceList = if (outflows.isNotEmpty()) outflows else transactions
        val total = sourceList.sumOf { it.amount }.coerceAtLeast(0.01)

        val rawGroups = if (categorizeByBank) {
            sourceList.groupBy { it.bankCode }.map { (code, list) ->
                val amt = list.sumOf { it.amount }
                ChartCategorySlice(
                    key = code,
                    label = list.first().bankName,
                    amount = amt,
                    shareFraction = (amt / total).toFloat().coerceIn(0f, 1f),
                    transactionCount = list.size
                )
            }
        } else {
            sourceList.groupBy { it.category.ifBlank { "Other" } }.map { (cat, list) ->
                val amt = list.sumOf { it.amount }
                ChartCategorySlice(
                    key = cat,
                    label = cat,
                    amount = amt,
                    shareFraction = (amt / total).toFloat().coerceIn(0f, 1f),
                    transactionCount = list.size
                )
            }
        }.sortedByDescending { it.amount }

        if (rawGroups.size <= maxSlices) return rawGroups

        val top = rawGroups.take(maxSlices - 1)
        val tail = rawGroups.drop(maxSlices - 1)
        val tailAmount = tail.sumOf { it.amount }
        val tailCount = tail.sumOf { it.transactionCount }
        return top + ChartCategorySlice(
            key = "OTHER_GROUP",
            label = "Other",
            amount = tailAmount,
            shareFraction = (tailAmount / total).toFloat().coerceIn(0f, 1f),
            transactionCount = tailCount
        )
    }

    fun filterTransactions(
        all: List<FinanceTransaction>,
        filter: FinanceFilterState,
        nowMillis: Long = System.currentTimeMillis()
    ): List<FinanceTransaction> {
        val (dateStart, dateEnd) = resolveDateRange(filter, nowMillis)
        return all.filter { tx ->
            val matchesDate = (dateStart == null || tx.timestamp >= dateStart) &&
                (dateEnd == null || tx.timestamp <= dateEnd)

            val matchesTimeWindow = matchesTimeOfDay(tx.timestamp, filter)

            val matchesBank = filter.selectedBankCode == "ALL" ||
                tx.bankCode.equals(filter.selectedBankCode, ignoreCase = true)

            val matchesType = when (filter.typeFilter.uppercase(Locale.ROOT)) {
                "INCOME" -> tx.isIncoming
                "EXPENSE", "OUTFLOW" -> tx.isOutgoing
                "TRANSFER" -> tx.transactionType == TransactionType.TRANSFER
                else -> true
            }

            val q = filter.searchQuery.trim()
            val matchesSearch = q.isBlank() ||
                tx.bankName.contains(q, ignoreCase = true) ||
                tx.counterparty.contains(q, ignoreCase = true) ||
                tx.category.contains(q, ignoreCase = true) ||
                tx.referenceId.contains(q, ignoreCase = true) ||
                tx.rawSmsBody.contains(q, ignoreCase = true)

            matchesDate && matchesTimeWindow && matchesBank && matchesType && matchesSearch
        }.sortedByDescending { it.timestamp }
    }

    fun resolveDateRange(
        filter: FinanceFilterState,
        nowMillis: Long = System.currentTimeMillis()
    ): Pair<Long?, Long?> {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        return when (filter.periodMode) {
            FinancePeriodMode.ALL -> null to null
            FinancePeriodMode.TODAY -> startOfDay(cal) to endOfDay(cal)
            FinancePeriodMode.YESTERDAY -> {
                cal.add(Calendar.DAY_OF_YEAR, -1)
                startOfDay(cal) to endOfDay(cal)
            }
            FinancePeriodMode.THIS_WEEK -> {
                val end = endOfDay(cal)
                cal.add(Calendar.DAY_OF_YEAR, -6)
                startOfDay(cal) to end
            }
            FinancePeriodMode.SPECIFIC_DAY -> {
                val dayMs = filter.selectedDayMillis ?: nowMillis
                val dayCal = Calendar.getInstance().apply { timeInMillis = dayMs }
                startOfDay(dayCal) to endOfDay(dayCal)
            }
            FinancePeriodMode.SPECIFIC_MONTH -> {
                val year = filter.selectedYear ?: cal.get(Calendar.YEAR)
                val month = filter.selectedMonthZeroBased ?: cal.get(Calendar.MONTH)
                val monthCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                val start = startOfDay(monthCal)
                monthCal.set(Calendar.DAY_OF_MONTH, monthCal.getActualMaximum(Calendar.DAY_OF_MONTH))
                val end = endOfDay(monthCal)
                start to end
            }
        }
    }

    fun matchesTimeOfDay(timestampMs: Long, filter: FinanceFilterState): Boolean {
        if (filter.timePreset == TimeOfDayPreset.ANY) return true
        val cal = Calendar.getInstance().apply { timeInMillis = timestampMs }
        val minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        val (startMin, endMin) = if (filter.timePreset == TimeOfDayPreset.CUSTOM) {
            (filter.customStartHour * 60 + filter.customStartMinute) to
                (filter.customEndHour * 60 + filter.customEndMinute)
        } else {
            (filter.timePreset.startHour * 60 + filter.timePreset.startMinute) to
                (filter.timePreset.endHour * 60 + filter.timePreset.endMinute)
        }

        return if (startMin <= endMin) {
            minuteOfDay in startMin..endMin
        } else {
            // Wraps midnight e.g. 21:00..05:59
            minuteOfDay >= startMin || minuteOfDay <= endMin
        }
    }

    fun describePeriodLabel(
        filter: FinanceFilterState,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val base = when (filter.periodMode) {
            FinancePeriodMode.ALL -> "All Time"
            FinancePeriodMode.TODAY -> "Today"
            FinancePeriodMode.YESTERDAY -> "Yesterday"
            FinancePeriodMode.THIS_WEEK -> "Last 7 Days"
            FinancePeriodMode.SPECIFIC_DAY -> {
                val dayMs = filter.selectedDayMillis ?: nowMillis
                SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH).format(Date(dayMs))
            }
            FinancePeriodMode.SPECIFIC_MONTH -> {
                val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
                val yr = filter.selectedYear ?: cal.get(Calendar.YEAR)
                val mo = filter.selectedMonthZeroBased ?: cal.get(Calendar.MONTH)
                cal.set(Calendar.YEAR, yr)
                cal.set(Calendar.MONTH, mo)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(cal.time)
            }
        }
        val timePart = when (filter.timePreset) {
            TimeOfDayPreset.ANY -> null
            TimeOfDayPreset.CUSTOM -> String.format(
                Locale.ENGLISH,
                "%02d:%02d–%02d:%02d",
                filter.customStartHour,
                filter.customStartMinute,
                filter.customEndHour,
                filter.customEndMinute
            )
            else -> filter.timePreset.label
        }
        val bankPart = if (filter.selectedBankCode == "ALL") null else BankInstitution.fromCode(filter.selectedBankCode).shortBadge
        return listOfNotNull(base, timePart, bankPart).joinToString(" · ")
    }

    fun summarize(
        filtered: List<FinanceTransaction>,
        allTransactions: List<FinanceTransaction>,
        periodLabel: String
    ): FinanceSummary {
        var income = 0.0
        var expense = 0.0
        var transfer = 0.0
        var fees = 0.0

        filtered.forEach { tx ->
            fees += tx.serviceCharge ?: 0.0
            when (tx.transactionType) {
                TransactionType.INCOME -> income += tx.amount
                TransactionType.EXPENSE -> expense += tx.amount
                TransactionType.TRANSFER -> transfer += tx.amount
            }
        }

        val totalOutflow = expense + transfer
        val primaryCurrency = filtered.firstOrNull()?.currency
            ?: allTransactions.firstOrNull()?.currency
            ?: "ETB"

        // Bank breakdown over filtered slice + latest known balance across ALL messages for that bank
        val allByBank = allTransactions.groupBy { it.bankCode }
        val filteredByBank = filtered.groupBy { it.bankCode }
        val bankCodes = (filteredByBank.keys + allByBank.keys).distinct()

        val bankStats = bankCodes.map { code ->
            val slice = filteredByBank[code].orEmpty()
            val allForBank = allByBank[code].orEmpty().sortedByDescending { it.timestamp }
            val latestWithBal = allForBank.firstOrNull { it.balanceAfter != null }
            val anyTx = slice.firstOrNull() ?: allForBank.first()
            BankBreakdownStat(
                bankCode = code,
                bankName = anyTx.bankName,
                income = slice.filter { it.isIncoming }.sumOf { it.amount },
                outflow = slice.filter { it.isOutgoing }.sumOf { it.amount },
                count = slice.size,
                latestBalance = latestWithBal?.balanceAfter,
                latestCurrency = latestWithBal?.currency ?: anyTx.currency,
                latestTimestamp = latestWithBal?.timestamp ?: allForBank.first().timestamp
            )
        }.sortedByDescending { it.count + if (it.latestBalance != null) 1 else 0 }

        // Spending category breakdown
        val outgoing = filtered.filter { it.isOutgoing }
        val totalOut = outgoing.sumOf { it.amount }.coerceAtLeast(0.01)
        val catStats = outgoing.groupBy { it.category.ifBlank { "Other" } }
            .map { (cat, list) ->
                val amt = list.sumOf { it.amount }
                CategoryBreakdownStat(
                    category = cat,
                    totalAmount = amt,
                    count = list.size,
                    shareFraction = (amt / totalOut).toFloat().coerceIn(0f, 1f)
                )
            }.sortedByDescending { it.totalAmount }

        // Time of day distribution
        val slotDefs = listOf(
            TimeOfDayPreset.MORNING to "Morning",
            TimeOfDayPreset.AFTERNOON to "Afternoon",
            TimeOfDayPreset.EVENING to "Evening",
            TimeOfDayPreset.NIGHT to "Night"
        )
        val slotStats = slotDefs.map { (preset, shortName) ->
            val dummyFilter = FinanceFilterState(timePreset = preset)
            val slotTx = filtered.filter { matchesTimeOfDay(it.timestamp, dummyFilter) }
            TimeSlotStat(
                label = shortName,
                rangeHint = preset.label.substringAfter("(").substringBefore(")"),
                outflow = slotTx.filter { it.isOutgoing }.sumOf { it.amount },
                income = slotTx.filter { it.isIncoming }.sumOf { it.amount },
                count = slotTx.size
            )
        }

        return FinanceSummary(
            transactionCount = filtered.size,
            totalIncome = income,
            totalExpense = expense,
            totalTransferOut = transfer,
            totalOutflow = totalOutflow,
            netFlow = income - totalOutflow,
            totalFees = fees,
            primaryCurrency = primaryCurrency,
            periodLabel = periodLabel,
            bankBreakdowns = bankStats,
            categoryBreakdowns = catStats,
            timeSlotStats = slotStats
        )
    }

    fun buildAiFinanceSnapshot(
        matching: List<FinanceTransaction>,
        allTransactions: List<FinanceTransaction>,
        summary: FinanceSummary,
        maxRows: Int = 60
    ): String {
        val dtFmt = SimpleDateFormat("yyyy-MM-dd HH:mm (EEE)", Locale.ENGLISH)
        return buildString {
            append("PERIOD / FILTER MATCHED: ${summary.periodLabel}\n")
            append("MATCHED TRANSACTIONS: ${summary.transactionCount} (out of ${allTransactions.size} total stored SMS transactions)\n")
            append("TOTAL INCOME: ${formatMoney(summary.totalIncome, summary.primaryCurrency)}\n")
            append("TOTAL OUTFLOW (Expenses + Transfers): ${formatMoney(summary.totalOutflow, summary.primaryCurrency)}\n")
            append("NET CASHFLOW: ${formatMoney(summary.netFlow, summary.primaryCurrency)}\n")
            append("SERVICE CHARGES / FEES: ${formatMoney(summary.totalFees, summary.primaryCurrency)}\n\n")

            append("LATEST DETECTED BANK BALANCES:\n")
            summary.bankBreakdowns.forEach { b ->
                val bal = b.latestBalance?.let { formatMoney(it, b.latestCurrency) } ?: "N/A"
                append("- ${b.bankName}: Latest balance = $bal | Matched Income = ${formatMoney(b.income, b.latestCurrency)} | Matched Outflow = ${formatMoney(b.outflow, b.latestCurrency)} (${b.count} tx)\n")
            }

            if (summary.categoryBreakdowns.isNotEmpty()) {
                append("\nTOP SPENDING CATEGORIES:\n")
                summary.categoryBreakdowns.forEach { c ->
                    append("- ${c.category}: ${formatMoney(c.totalAmount, summary.primaryCurrency)} (${c.count} tx, ${(c.shareFraction * 100).toInt()}%)\n")
                }
            }

            append("\nTRANSACTIONS IN SELECTED WINDOW:\n")
            if (matching.isEmpty()) {
                append("(No transactions matched this specific time/day/month filter.)\n")
            } else {
                matching.take(maxRows).forEach { tx ->
                    val balText = tx.balanceAfter?.let { " | Bal: ${formatMoney(it, tx.currency)}" } ?: ""
                    val feeText = tx.serviceCharge?.let { " | Fee: ${formatMoney(it, tx.currency)}" } ?: ""
                    val refText = if (tx.referenceId.isNotBlank()) " | Ref: ${tx.referenceId}" else ""
                    append("- [${dtFmt.format(Date(tx.timestamp))}] ${tx.bankName} | ${tx.type} | ${formatMoney(tx.amount, tx.currency)} | ${tx.counterparty} | Cat: ${tx.category}$feeText$balText$refText\n")
                }
            }
        }
    }

    fun buildOfflineQueryAnswer(
        question: String,
        parsed: ParsedFinanceQuery,
        matching: List<FinanceTransaction>,
        allTransactions: List<FinanceTransaction>
    ): String {
        val summary = summarize(matching, allTransactions, parsed.periodLabel)
        val dtFmt = SimpleDateFormat("MMM dd HH:mm", Locale.ENGLISH)

        if (allTransactions.isEmpty()) {
            return "No bank SMS transactions have been imported yet. Tap 'Scan SMS Inbox' to import Telebirr, CBE, Bank of Abyssinia, or other bank alerts, or tap 'Paste SMS' to add a message."
        }

        if (parsed.intentType == "BALANCE") {
            return buildString {
                append("Latest Detected Balances (${parsed.periodLabel}):\n")
                summary.bankBreakdowns.filter { it.latestBalance != null }.forEach { b ->
                    append("• ${b.bankName}: ${formatMoney(b.latestBalance!!, b.latestCurrency)} (updated ${dtFmt.format(Date(b.latestTimestamp))})\n")
                }
                if (summary.bankBreakdowns.none { it.latestBalance != null }) {
                    append("No account balance strings were found in your imported SMS alerts.")
                }
            }.trim()
        }

        if (matching.isEmpty()) {
            return "No transactions found for ${parsed.periodLabel}. Try another day, month, time window, or scan more SMS messages."
        }

        return buildString {
            append("${parsed.periodLabel} Summary (${matching.size} transactions):\n")
            append("• Income: ${formatMoney(summary.totalIncome, summary.primaryCurrency)}\n")
            append("• Outflow: ${formatMoney(summary.totalOutflow, summary.primaryCurrency)}")
            if (summary.totalFees > 0.0) {
                append(" (incl. fees ${formatMoney(summary.totalFees, summary.primaryCurrency)})")
            }
            append("\n• Net Flow: ${formatMoney(summary.netFlow, summary.primaryCurrency)}\n")

            val activeBanks = summary.bankBreakdowns.filter { it.count > 0 }
            if (activeBanks.isNotEmpty()) {
                append("\nBy Bank:\n")
                activeBanks.forEach { b ->
                    append("• ${b.bankName}: ${b.count} tx | +${formatMoney(b.income, b.latestCurrency)} / -${formatMoney(b.outflow, b.latestCurrency)}\n")
                }
            }

            if (summary.categoryBreakdowns.isNotEmpty()) {
                val top = summary.categoryBreakdowns.take(3)
                append("\nTop Categories:\n")
                top.forEach { c ->
                    append("• ${c.category}: ${formatMoney(c.totalAmount, summary.primaryCurrency)} (${(c.shareFraction * 100).toInt()}%)\n")
                }
            }

            append("\nRecent Matching Activity:\n")
            matching.take(5).forEach { tx ->
                append("• ${dtFmt.format(Date(tx.timestamp))} · ${tx.bankName}: ${formatSignedMoney(tx)} (${tx.counterparty})\n")
            }
        }.trim()
    }

    private fun startOfDay(cal: Calendar): Long {
        val c = cal.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun endOfDay(cal: Calendar): Long {
        val c = cal.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, 23)
        c.set(Calendar.MINUTE, 59)
        c.set(Calendar.SECOND, 59)
        c.set(Calendar.MILLISECOND, 999)
        return c.timeInMillis
    }
}

object FinanceQueryParser {

    private val MONTH_NAMES = mapOf(
        "january" to Calendar.JANUARY, "jan" to Calendar.JANUARY,
        "february" to Calendar.FEBRUARY, "feb" to Calendar.FEBRUARY,
        "march" to Calendar.MARCH, "mar" to Calendar.MARCH,
        "april" to Calendar.APRIL, "apr" to Calendar.APRIL,
        "may" to Calendar.MAY,
        "june" to Calendar.JUNE, "jun" to Calendar.JUNE,
        "july" to Calendar.JULY, "jul" to Calendar.JULY,
        "august" to Calendar.AUGUST, "aug" to Calendar.AUGUST,
        "september" to Calendar.SEPTEMBER, "sep" to Calendar.SEPTEMBER, "sept" to Calendar.SEPTEMBER,
        "october" to Calendar.OCTOBER, "oct" to Calendar.OCTOBER,
        "november" to Calendar.NOVEMBER, "nov" to Calendar.NOVEMBER,
        "december" to Calendar.DECEMBER, "dec" to Calendar.DECEMBER
    )

    private val WEEKDAY_NAMES = mapOf(
        "sunday" to Calendar.SUNDAY,
        "monday" to Calendar.MONDAY,
        "tuesday" to Calendar.TUESDAY,
        "wednesday" to Calendar.WEDNESDAY,
        "thursday" to Calendar.THURSDAY,
        "friday" to Calendar.FRIDAY,
        "saturday" to Calendar.SATURDAY
    )

    fun parseQuestion(
        question: String,
        fallbackFilter: FinanceFilterState = FinanceFilterState(),
        nowMillis: Long = System.currentTimeMillis()
    ): ParsedFinanceQuery {
        val lower = question.lowercase(Locale.ENGLISH)
        val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }

        // 1. Bank detection
        val bankCode = when {
            lower.contains("telebirr") -> BankInstitution.TELEBIRR.code
            lower.contains("cbe birr") || lower.contains("cbebirr") -> BankInstitution.CBE_BIRR.code
            Regex("""\bcbe\b""").containsMatchIn(lower) || lower.contains("commercial bank") -> BankInstitution.CBE.code
            lower.contains("abyssinia") || Regex("""\bboa\b""").containsMatchIn(lower) -> BankInstitution.BOA.code
            lower.contains("awash") -> BankInstitution.AWASH.code
            lower.contains("dashen") || lower.contains("amole") -> BankInstitution.DASHEN.code
            Regex("""\bnib\b""").containsMatchIn(lower) -> BankInstitution.NIB.code
            lower.contains("coop") || lower.contains("ebirr") -> BankInstitution.COOP.code
            lower.contains("hibret") -> BankInstitution.HIBRET.code
            lower.contains("zemen") -> BankInstitution.ZEMEN.code
            lower.contains("mpesa") || lower.contains("m-pesa") -> BankInstitution.MPESA.code
            else -> "ALL"
        }

        // 2. Date / Day / Month resolution
        var periodMode: FinancePeriodMode? = null
        var selectedDayMillis: Long? = null
        var selectedYear: Int? = null
        var selectedMonthZero: Int? = null

        val isoDateMatch = Regex("""\b(20\d{2})-(\d{1,2})-(\d{1,2})\b""").find(lower)
        val slashDateMatch = Regex("""\b(\d{1,2})/(\d{1,2})/(20\d{2})\b""").find(lower)
        val monthDayMatch = Regex("""\b(january|jan|february|feb|march|mar|april|apr|may|june|jun|july|jul|august|aug|september|sept|sep|october|oct|november|nov|december|dec)\s+(\d{1,2})(?:st|nd|rd|th)?(?:,?\s*(20\d{2}))?\b""").find(lower)
        val dayMonthMatch = Regex("""\b(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?(january|jan|february|feb|march|mar|april|apr|may|june|jun|july|jul|august|aug|september|sept|sep|october|oct|november|nov|december|dec)(?:\s+(20\d{2}))?\b""").find(lower)

        when {
            isoDateMatch != null -> {
                val yr = isoDateMatch.groupValues[1].toInt()
                val mo = (isoDateMatch.groupValues[2].toInt() - 1).coerceIn(0, 11)
                val dy = isoDateMatch.groupValues[3].toInt().coerceIn(1, 31)
                val c = Calendar.getInstance().apply { set(yr, mo, dy, 12, 0, 0) }
                periodMode = FinancePeriodMode.SPECIFIC_DAY
                selectedDayMillis = c.timeInMillis
            }
            slashDateMatch != null -> {
                val dy = slashDateMatch.groupValues[1].toInt().coerceIn(1, 31)
                val mo = (slashDateMatch.groupValues[2].toInt() - 1).coerceIn(0, 11)
                val yr = slashDateMatch.groupValues[3].toInt()
                val c = Calendar.getInstance().apply { set(yr, mo, dy, 12, 0, 0) }
                periodMode = FinancePeriodMode.SPECIFIC_DAY
                selectedDayMillis = c.timeInMillis
            }
            monthDayMatch != null -> {
                val mo = MONTH_NAMES[monthDayMatch.groupValues[1]] ?: nowCal.get(Calendar.MONTH)
                val dy = monthDayMatch.groupValues[2].toInt().coerceIn(1, 31)
                val yr = monthDayMatch.groupValues[3].toIntOrNull() ?: nowCal.get(Calendar.YEAR)
                val c = Calendar.getInstance().apply { set(yr, mo, dy, 12, 0, 0) }
                periodMode = FinancePeriodMode.SPECIFIC_DAY
                selectedDayMillis = c.timeInMillis
            }
            dayMonthMatch != null -> {
                val dy = dayMonthMatch.groupValues[1].toInt().coerceIn(1, 31)
                val mo = MONTH_NAMES[dayMonthMatch.groupValues[2]] ?: nowCal.get(Calendar.MONTH)
                val yr = dayMonthMatch.groupValues[3].toIntOrNull() ?: nowCal.get(Calendar.YEAR)
                val c = Calendar.getInstance().apply { set(yr, mo, dy, 12, 0, 0) }
                periodMode = FinancePeriodMode.SPECIFIC_DAY
                selectedDayMillis = c.timeInMillis
            }
            lower.contains("today") || lower.contains("tonight") || lower.contains("this morning") || lower.contains("this afternoon") || lower.contains("this evening") -> {
                periodMode = FinancePeriodMode.TODAY
            }
            lower.contains("yesterday") -> {
                periodMode = FinancePeriodMode.YESTERDAY
            }
            lower.contains("this week") || lower.contains("last 7 days") || lower.contains("past week") -> {
                periodMode = FinancePeriodMode.THIS_WEEK
            }
            lower.contains("this month") -> {
                periodMode = FinancePeriodMode.SPECIFIC_MONTH
                selectedYear = nowCal.get(Calendar.YEAR)
                selectedMonthZero = nowCal.get(Calendar.MONTH)
            }
            lower.contains("last month") || lower.contains("previous month") -> {
                val prev = (nowCal.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
                periodMode = FinancePeriodMode.SPECIFIC_MONTH
                selectedYear = prev.get(Calendar.YEAR)
                selectedMonthZero = prev.get(Calendar.MONTH)
            }
            else -> {
                val matchedWeekday = WEEKDAY_NAMES.entries.firstOrNull { (k, _) ->
                    Regex("""\b$k\b""").containsMatchIn(lower)
                }
                val matchedMonth = MONTH_NAMES.entries.firstOrNull { (k, _) ->
                    Regex("""\b$k\b""").containsMatchIn(lower)
                }
                if (matchedWeekday != null) {
                    val targetDow = matchedWeekday.value
                    val c = nowCal.clone() as Calendar
                    while (c.get(Calendar.DAY_OF_WEEK) != targetDow) {
                        c.add(Calendar.DAY_OF_YEAR, -1)
                    }
                    periodMode = FinancePeriodMode.SPECIFIC_DAY
                    selectedDayMillis = c.timeInMillis
                } else if (matchedMonth != null) {
                    val yearMatch = Regex("""\b(20\d{2})\b""").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    periodMode = FinancePeriodMode.SPECIFIC_MONTH
                    selectedYear = yearMatch ?: nowCal.get(Calendar.YEAR)
                    selectedMonthZero = matchedMonth.value
                }
            }
        }

        // 3. Time-of-day window resolution
        var timePreset = TimeOfDayPreset.ANY
        var startH = 0
        var startM = 0
        var endH = 23
        var endM = 59

        val rangeMatch = Regex(
            """(?:between|from)\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\s+(?:and|to|-)\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm)?"""
        ).find(lower)
        val exactTimeMatch = Regex(
            """\b(?:at|around)\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\b"""
        ).find(lower)

        when {
            rangeMatch != null -> {
                startH = to24Hour(rangeMatch.groupValues[1].toInt(), rangeMatch.groupValues[3])
                startM = rangeMatch.groupValues[2].toIntOrNull()?.coerceIn(0, 59) ?: 0
                endH = to24Hour(rangeMatch.groupValues[4].toInt(), rangeMatch.groupValues[6])
                endM = rangeMatch.groupValues[5].toIntOrNull()?.coerceIn(0, 59) ?: 59
                timePreset = TimeOfDayPreset.CUSTOM
            }
            exactTimeMatch != null -> {
                val h = to24Hour(exactTimeMatch.groupValues[1].toInt(), exactTimeMatch.groupValues[3])
                val m = exactTimeMatch.groupValues[2].toIntOrNull()?.coerceIn(0, 59) ?: 0
                // Match a ±60-minute window around the specified time
                val centerMin = h * 60 + m
                val sMin = (centerMin - 60).coerceAtLeast(0)
                val eMin = (centerMin + 60).coerceAtMost(23 * 60 + 59)
                startH = sMin / 60
                startM = sMin % 60
                endH = eMin / 60
                endM = eMin % 60
                timePreset = TimeOfDayPreset.CUSTOM
            }
            lower.contains("morning") -> timePreset = TimeOfDayPreset.MORNING
            lower.contains("afternoon") -> timePreset = TimeOfDayPreset.AFTERNOON
            lower.contains("evening") -> timePreset = TimeOfDayPreset.EVENING
            lower.contains("night") || lower.contains("tonight") -> timePreset = TimeOfDayPreset.NIGHT
        }

        // 4. Intent detection
        val intentType = when {
            lower.contains("balance") || lower.contains("how much do i have") -> "BALANCE"
            lower.contains("fee") || lower.contains("service charge") || lower.contains("charge") -> "FEE"
            lower.contains("spent") || lower.contains("spend") || lower.contains("expense") || lower.contains("paid") || lower.contains("outflow") -> "OUTFLOW"
            lower.contains("income") || lower.contains("received") || lower.contains("credited") || lower.contains("earned") || lower.contains("deposit") -> "INCOME"
            else -> "ALL"
        }

        val resolvedFilter = FinanceFilterState(
            periodMode = periodMode ?: fallbackFilter.periodMode,
            selectedDayMillis = selectedDayMillis ?: fallbackFilter.selectedDayMillis,
            selectedYear = selectedYear ?: fallbackFilter.selectedYear,
            selectedMonthZeroBased = selectedMonthZero ?: fallbackFilter.selectedMonthZeroBased,
            timePreset = if (timePreset != TimeOfDayPreset.ANY) timePreset else fallbackFilter.timePreset,
            customStartHour = if (timePreset == TimeOfDayPreset.CUSTOM) startH else fallbackFilter.customStartHour,
            customStartMinute = if (timePreset == TimeOfDayPreset.CUSTOM) startM else fallbackFilter.customStartMinute,
            customEndHour = if (timePreset == TimeOfDayPreset.CUSTOM) endH else fallbackFilter.customEndHour,
            customEndMinute = if (timePreset == TimeOfDayPreset.CUSTOM) endM else fallbackFilter.customEndMinute,
            selectedBankCode = if (bankCode != "ALL") bankCode else fallbackFilter.selectedBankCode
        )

        val label = FinanceAnalyticsEngine.describePeriodLabel(resolvedFilter, nowMillis)
        return ParsedFinanceQuery(
            filterState = resolvedFilter,
            periodLabel = label,
            intentType = intentType
        )
    }

    private fun to24Hour(hour: Int, amPm: String): Int {
        val h = hour.coerceIn(0, 23)
        return when (amPm.lowercase(Locale.ENGLISH)) {
            "pm" -> if (h < 12) h + 12 else h
            "am" -> if (h == 12) 0 else h
            else -> h
        }
    }
}
