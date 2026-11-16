package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.BankInstitution
import com.example.data.BankSmsFileParser
import com.example.data.BankSmsParser
import com.example.data.FinanceAnalyticsEngine
import com.example.data.FinanceFilterState
import com.example.data.FinancePeriodMode
import com.example.data.FinanceQueryParser
import com.example.data.FinanceTransaction
import com.example.data.TimeOfDayPreset
import com.example.data.TransactionType
import com.example.data.VoiceTaskRepository
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.data.ExpenseCategorySuggester
import com.example.data.FinanceSummary
import com.example.ui.CompactFinanceActionBar
import com.example.ui.FinanceFilterBottomSheet
import com.example.ui.FinanceFilterPanel
import com.example.ui.FinanceHeroSummaryCard
import com.example.ui.FinanceLedgerSearchHeader
import com.example.ui.FinanceOverviewComposable
import com.example.ui.PasteBankSmsDialog
import com.example.ui.TransactionDetailDialog
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FinanceModuleTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `parses Telebirr transfer and received SMS accurately`() {
        val sentSms = "You have transferred ETB 1,500.00 to Abebe Bikila (251911223344) on 13/11/2026 14:25:10. Your transaction number is DBK71829A. The service fee is ETB 5.00. Your current balance is ETB 12,450.00."
        val txSent = BankSmsParser.parseSms(sender = "127", body = sentSms)
        assertNotNull(txSent)
        assertEquals(BankInstitution.TELEBIRR.code, txSent!!.bankCode)
        assertEquals("Telebirr", txSent.bankName)
        assertEquals(TransactionType.TRANSFER, txSent.transactionType)
        assertEquals(1500.0, txSent.amount, 0.01)
        assertEquals(5.0, txSent.serviceCharge ?: 0.0, 0.01)
        assertEquals(12450.0, txSent.balanceAfter ?: 0.0, 0.01)
        assertEquals("DBK71829A", txSent.referenceId)
        assertTrue(txSent.counterparty.contains("Abebe Bikila"))

        val receivedSms = "Dear Customer, You have received ETB 3,200.00 from Tigist Alemu on 13/11/2026 09:10:00. Your current balance is ETB 15,650.00."
        val txIn = BankSmsParser.parseSms(sender = "telebirr", body = receivedSms)
        assertNotNull(txIn)
        assertEquals(BankInstitution.TELEBIRR.code, txIn!!.bankCode)
        assertEquals(TransactionType.INCOME, txIn.transactionType)
        assertEquals(3200.0, txIn.amount, 0.01)
        assertEquals(15650.0, txIn.balanceAfter ?: 0.0, 0.01)
    }

    @Test
    fun `parses CBE and Bank of Abyssinia and Awash SMS and ignores OTP messages`() {
        val cbeDebit = "Dear Customer, your Account 1000*****4321 has been debited with ETB 2,500.00 on 13-Nov-2026 at 10:15:00. Service charge ETB 10.00. Ref No FT26318ABC99. Your Current Balance is ETB 45,230.50. Thank you for Banking with CBE."
        val txCbe = BankSmsParser.parseSms(sender = "CBE", body = cbeDebit)
        assertNotNull(txCbe)
        assertEquals(BankInstitution.CBE.code, txCbe!!.bankCode)
        assertEquals(TransactionType.EXPENSE, txCbe.transactionType)
        assertEquals(2500.0, txCbe.amount, 0.01)
        assertEquals(10.0, txCbe.serviceCharge ?: 0.0, 0.01)
        assertEquals(45230.50, txCbe.balanceAfter ?: 0.0, 0.01)
        assertEquals("FT26318ABC99", txCbe.referenceId)

        val boaCredit = "Dear Customer, Your account ***1234 has been credited with ETB 18,000.00 from TechCorp Payroll on 2026-11-12 08:30:00. Available Balance: ETB 63,230.50."
        val txBoa = BankSmsParser.parseSms(sender = "Abyssinia", body = boaCredit)
        assertNotNull(txBoa)
        assertEquals(BankInstitution.BOA.code, txBoa!!.bankCode)
        assertEquals("Bank of Abyssinia", txBoa.bankName)
        assertEquals(TransactionType.INCOME, txBoa.transactionType)
        assertEquals(18000.0, txBoa.amount, 0.01)
        assertEquals(63230.50, txBoa.balanceAfter ?: 0.0, 0.01)

        val awashPaid = "Awash Bank: You paid ETB 450.00 to Cafe Lucia on 13/11/2026 16:40:00. Current Balance: ETB 8,100.00."
        val txAwash = BankSmsParser.parseSms(sender = "AwashBank", body = awashPaid)
        assertNotNull(txAwash)
        assertEquals(BankInstitution.AWASH.code, txAwash!!.bankCode)
        assertEquals(450.0, txAwash.amount, 0.01)

        val otpSms = "Your Telebirr OTP verification code is 839201. Do not share this code with anyone. Expires in 5 minutes."
        assertNull(BankSmsParser.parseSms(sender = "127", body = otpSms))
    }

    @Test
    fun `filters and answers natural language queries by specific time, day, month, and bank`() {
        fun ts(year: Int, monthZero: Int, day: Int, hour: Int, minute: Int): Long {
            return Calendar.getInstance().apply {
                set(year, monthZero, day, hour, minute, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }

        val nowMs = ts(2026, Calendar.NOVEMBER, 13, 18, 0)

        val txTodayMorningCbe = FinanceTransaction(
            id = 1,
            smsFingerprint = "fp1",
            bankName = "CBE",
            bankCode = BankInstitution.CBE.code,
            type = TransactionType.INCOME.name,
            amount = 10000.0,
            counterparty = "Client Deposit",
            timestamp = ts(2026, Calendar.NOVEMBER, 13, 9, 15)
        )
        val txTodayAfternoonTelebirr = FinanceTransaction(
            id = 2,
            smsFingerprint = "fp2",
            bankName = "Telebirr",
            bankCode = BankInstitution.TELEBIRR.code,
            type = TransactionType.EXPENSE.name,
            amount = 750.0,
            counterparty = "Supermarket",
            timestamp = ts(2026, Calendar.NOVEMBER, 13, 15, 20)
        )
        val txYesterdayBoa = FinanceTransaction(
            id = 3,
            smsFingerprint = "fp3",
            bankName = "Bank of Abyssinia",
            bankCode = BankInstitution.BOA.code,
            type = TransactionType.EXPENSE.name,
            amount = 1200.0,
            counterparty = "Pharmacy",
            timestamp = ts(2026, Calendar.NOVEMBER, 12, 16, 45)
        )
        val txLastMonthOct = FinanceTransaction(
            id = 4,
            smsFingerprint = "fp4",
            bankName = "Telebirr",
            bankCode = BankInstitution.TELEBIRR.code,
            type = TransactionType.EXPENSE.name,
            amount = 3000.0,
            counterparty = "Rent",
            timestamp = ts(2026, Calendar.OCTOBER, 20, 11, 0)
        )

        val all = listOf(txTodayMorningCbe, txTodayAfternoonTelebirr, txYesterdayBoa, txLastMonthOct)

        // Specific day + time range query: "today between 2pm and 5pm"
        val parsedAfternoonToday = FinanceQueryParser.parseQuestion(
            question = "Show my Telebirr spending today between 2pm and 5pm",
            fallbackFilter = FinanceFilterState(),
            nowMillis = nowMs
        )
        assertEquals(FinancePeriodMode.TODAY, parsedAfternoonToday.filterState.periodMode)
        assertEquals(TimeOfDayPreset.CUSTOM, parsedAfternoonToday.filterState.timePreset)
        assertEquals(BankInstitution.TELEBIRR.code, parsedAfternoonToday.filterState.selectedBankCode)

        val matchedAfternoon = FinanceAnalyticsEngine.filterTransactions(all, parsedAfternoonToday.filterState, nowMillis = nowMs)
        assertEquals(listOf(2), matchedAfternoon.map { it.id })

        // Specific month query: "October 2026"
        val parsedOct = FinanceQueryParser.parseQuestion(
            question = "How much did I spend in October 2026?",
            fallbackFilter = FinanceFilterState(),
            nowMillis = nowMs
        )
        assertEquals(FinancePeriodMode.SPECIFIC_MONTH, parsedOct.filterState.periodMode)
        assertEquals(Calendar.OCTOBER, parsedOct.filterState.selectedMonthZeroBased)
        val matchedOct = FinanceAnalyticsEngine.filterTransactions(all, parsedOct.filterState, nowMillis = nowMs)
        assertEquals(listOf(4), matchedOct.map { it.id })

        val answerOct = FinanceAnalyticsEngine.buildOfflineQueryAnswer(
            question = "How much did I spend in October 2026?",
            parsed = parsedOct,
            matching = matchedOct,
            allTransactions = all
        )
        assertTrue(answerOct.contains("3,000.00"))

        // Specific calendar day query: "Nov 12"
        val parsedNov12 = FinanceQueryParser.parseQuestion(
            question = "Show Abyssinia activity on Nov 12",
            fallbackFilter = FinanceFilterState(),
            nowMillis = nowMs
        )
        val matchedNov12 = FinanceAnalyticsEngine.filterTransactions(all, parsedNov12.filterState, nowMillis = nowMs)
        assertEquals(listOf(3), matchedNov12.map { it.id })
    }

    @Test
    fun `room repository inserts and deduplicates parsed bank SMS transactions`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dao = AppDatabase.getDatabase(context).voiceTaskDao()
        val repo = VoiceTaskRepository(dao)
        repo.clearAllFinanceTransactions()

        val rawSms = "You have transferred ETB 500.00 to Dawit Tadesse on 13/11/2026 11:00:00. Your transaction number is TX112233. Current balance is ETB 4,500.00."
        val parsed1 = BankSmsParser.parseSms("127", rawSms)!!
        val parsedDuplicate = BankSmsParser.parseSms("127", rawSms)!!

        val firstInsertCount = repo.insertFinanceTransactionsDedup(listOf(parsed1))
        val secondInsertCount = repo.insertFinanceTransactionsDedup(listOf(parsedDuplicate))

        assertEquals(1, firstInsertCount)
        assertEquals(0, secondInsertCount)

        val stored = repo.getAllFinanceTransactions()
        assertEquals(1, stored.size)
        assertEquals(500.0, stored.first().amount, 0.01)
    }

    @Test
    fun `computes monthly spending totals and groups recent transactions by bank`() {
        fun ts(year: Int, monthZero: Int, day: Int, hour: Int, minute: Int): Long {
            return Calendar.getInstance().apply {
                set(year, monthZero, day, hour, minute, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }

        val nowMs = ts(2026, Calendar.NOVEMBER, 14, 15, 0)

        val txs = listOf(
            FinanceTransaction(
                id = 1,
                smsFingerprint = "m1",
                bankName = "Telebirr",
                bankCode = BankInstitution.TELEBIRR.code,
                type = TransactionType.EXPENSE.name,
                amount = 650.0,
                balanceAfter = 4350.0,
                counterparty = "Coffee Shop",
                timestamp = ts(2026, Calendar.NOVEMBER, 14, 10, 0)
            ),
            FinanceTransaction(
                id = 2,
                smsFingerprint = "m2",
                bankName = "Telebirr",
                bankCode = BankInstitution.TELEBIRR.code,
                type = TransactionType.TRANSFER.name,
                amount = 1350.0,
                balanceAfter = 5000.0,
                counterparty = "Abel Kebede",
                timestamp = ts(2026, Calendar.NOVEMBER, 12, 14, 0)
            ),
            FinanceTransaction(
                id = 3,
                smsFingerprint = "m3",
                bankName = "CBE",
                bankCode = BankInstitution.CBE.code,
                type = TransactionType.INCOME.name,
                amount = 22000.0,
                balanceAfter = 68000.0,
                counterparty = "Salary",
                timestamp = ts(2026, Calendar.NOVEMBER, 10, 9, 0)
            ),
            FinanceTransaction(
                id = 4,
                smsFingerprint = "m4",
                bankName = "CBE",
                bankCode = BankInstitution.CBE.code,
                type = TransactionType.EXPENSE.name,
                amount = 4000.0,
                balanceAfter = null,
                counterparty = "Utility Bill",
                timestamp = ts(2026, Calendar.NOVEMBER, 13, 16, 0)
            ),
            FinanceTransaction(
                id = 5,
                smsFingerprint = "m5",
                bankName = "Telebirr",
                bankCode = BankInstitution.TELEBIRR.code,
                type = TransactionType.EXPENSE.name,
                amount = 9999.0,
                balanceAfter = 9000.0,
                counterparty = "Last Month Expense",
                timestamp = ts(2026, Calendar.OCTOBER, 28, 12, 0)
            )
        )

        val monthlySpending = FinanceAnalyticsEngine.computeMonthlySpendingTotal(txs, nowMillis = nowMs)
        val monthlyIncome = FinanceAnalyticsEngine.computeMonthlyIncomeTotal(txs, nowMillis = nowMs)
        val monthlyCount = FinanceAnalyticsEngine.computeMonthlyTransactionCount(txs, nowMillis = nowMs)

        // November outflows: 650 + 1350 + 4000 = 6000 (October's 9999 excluded)
        assertEquals(6000.0, monthlySpending, 0.01)
        assertEquals(22000.0, monthlyIncome, 0.01)
        assertEquals(4, monthlyCount)

        val grouped = FinanceAnalyticsEngine.groupRecentTransactionsByBank(txs, limitPerBank = 2)
        assertEquals(2, grouped.size)

        // Telebirr has the most recent tx (Nov 14), so it comes first; limited to 2 most recent
        val telebirrGroup = grouped[0]
        assertEquals(BankInstitution.TELEBIRR.code, telebirrGroup.bankCode)
        assertEquals(2, telebirrGroup.recentTransactions.size)
        assertEquals(listOf(1, 2), telebirrGroup.recentTransactions.map { it.id })
        assertEquals(4350.0, telebirrGroup.latestBalance ?: 0.0, 0.01)
        assertEquals(650.0 + 1350.0 + 9999.0, telebirrGroup.totalOutflow, 0.01)

        // CBE is second (most recent tx Nov 13 with null balance, falls back to Nov 10 balance 68,000)
        val cbeGroup = grouped[1]
        assertEquals(BankInstitution.CBE.code, cbeGroup.bankCode)
        assertEquals(68000.0, cbeGroup.latestBalance ?: 0.0, 0.01)
        assertEquals(4000.0, cbeGroup.totalOutflow, 0.01)
        assertEquals(22000.0, cbeGroup.totalIncome, 0.01)
    }

    @Test
    fun `builds trend line series and pie slices categorized by bank and spending type`() {
        fun ts(year: Int, monthZero: Int, day: Int, hour: Int): Long {
            return Calendar.getInstance().apply {
                set(year, monthZero, day, hour, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }

        val txs = listOf(
            FinanceTransaction(
                id = 1, smsFingerprint = "c1", bankName = "Telebirr", bankCode = BankInstitution.TELEBIRR.code,
                type = TransactionType.EXPENSE.name, amount = 500.0, category = "Dining & Food",
                timestamp = ts(2026, Calendar.NOVEMBER, 10, 12)
            ),
            FinanceTransaction(
                id = 2, smsFingerprint = "c2", bankName = "CBE", bankCode = BankInstitution.CBE.code,
                type = TransactionType.INCOME.name, amount = 10000.0, category = "Income / Deposit",
                timestamp = ts(2026, Calendar.NOVEMBER, 10, 14)
            ),
            FinanceTransaction(
                id = 3, smsFingerprint = "c3", bankName = "CBE", bankCode = BankInstitution.CBE.code,
                type = TransactionType.EXPENSE.name, amount = 1500.0, category = "Shopping",
                timestamp = ts(2026, Calendar.NOVEMBER, 11, 16)
            ),
            FinanceTransaction(
                id = 4, smsFingerprint = "c4", bankName = "Bank of Abyssinia", bankCode = BankInstitution.BOA.code,
                type = TransactionType.TRANSFER.name, amount = 2000.0, category = "Transfers",
                timestamp = ts(2026, Calendar.NOVEMBER, 12, 18)
            )
        )

        // Trend by spending type (Spending vs Income)
        val cashflowTrend = FinanceAnalyticsEngine.buildTrendChartSeries(txs, categorizeByBank = false)
        assertEquals(3, cashflowTrend.dateLabels.size)
        assertEquals(2, cashflowTrend.lines.size)
        val spendingLine = cashflowTrend.lines.first { it.key == "OUTFLOW" }
        val incomeLine = cashflowTrend.lines.first { it.key == "INCOME" }
        assertEquals(listOf(500.0, 1500.0, 2000.0), spendingLine.values)
        assertEquals(listOf(10000.0, 0.0, 0.0), incomeLine.values)

        // Trend by bank
        val bankTrend = FinanceAnalyticsEngine.buildTrendChartSeries(txs, categorizeByBank = true)
        assertEquals(3, bankTrend.lines.size) // BoA (2000), CBE (1500), Telebirr (500)
        assertEquals("Bank of Abyssinia", bankTrend.lines[0].label)
        assertEquals(listOf(0.0, 0.0, 2000.0), bankTrend.lines[0].values)

        // Pie slices by bank (outflows only: BoA 2000, CBE 1500, Telebirr 500 -> total 4000)
        val bankPie = FinanceAnalyticsEngine.buildPieSlices(txs, categorizeByBank = true)
        assertEquals(3, bankPie.size)
        assertEquals("Bank of Abyssinia", bankPie[0].label)
        assertEquals(0.5f, bankPie[0].shareFraction, 0.01f)

        // Pie slices by spending category
        val catPie = FinanceAnalyticsEngine.buildPieSlices(txs, categorizeByBank = false)
        assertEquals(3, catPie.size)
        assertEquals("Transfers", catPie[0].label)
        assertEquals("Shopping", catPie[1].label)
        assertEquals("Dining & Food", catPie[2].label)
    }

    @Test
    fun `parses XML CSV and TXT SMS backup files cleanly while ignoring OTPs`() {
        val xmlBackup = """
            <?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
            <smses count="3">
              <sms address="127" date="1763020800000" body="You have transferred ETB 850.00 to Sara &amp; Co on 13/11/2026 10:00:00. Your transaction number is TX9011. Your current balance is ETB 4,150.00." />
              <sms address="CBE" date="1763024400000" body="Your verification code is 123456. Do not share this code." />
              <sms address="Abyssinia" date="1763028000000" body="Dear Customer, Your account ***9911 has been credited with ETB 5,000.00 from Client A on 13/11/2026 12:00:00. Available Balance: ETB 19,000.00." />
            </smses>
        """.trimIndent()

        val xmlRes = BankSmsFileParser.parseContent(xmlBackup, "sms-backup.xml")
        assertEquals("XML Backup", xmlRes.formatLabel)
        assertEquals(3, xmlRes.totalRecordsInspected)
        assertEquals(2, xmlRes.parsedTransactions.size)
        assertEquals("Sara & Co", xmlRes.parsedTransactions[0].counterparty)
        assertEquals(BankInstitution.BOA.code, xmlRes.parsedTransactions[1].bankCode)

        val csvBackup = """
            address,date,body
            "CBE","2026-11-13 14:00:00","Dear Customer, your Account 1000***12 has been debited with ETB 1,200.00 on 13/11/2026 14:00:00. Current Balance is ETB 10,000.00."
            "127","2026-11-13 15:00:00","Dear Customer, You have received ETB 600.00 from Dawit on 13/11/2026 15:00:00. Current balance is ETB 6,600.00."
        """.trimIndent()

        val csvRes = BankSmsFileParser.parseContent(csvBackup, "export.csv")
        assertEquals("CSV", csvRes.formatLabel)
        assertEquals(2, csvRes.parsedTransactions.size)
        assertEquals(1200.0, csvRes.parsedTransactions[0].amount, 0.01)
        assertEquals(600.0, csvRes.parsedTransactions[1].amount, 0.01)

        val txtBackup = """
            Awash Bank: You paid ETB 350.00 to Bakery on 13/11/2026 08:30:00. Current Balance: ETB 2,500.00.

            Dashen Bank: Your account is credited with ETB 2,000.00 from Freelance on 13/11/2026 11:15:00. Balance ETB 7,000.00.
        """.trimIndent()

        val txtRes = BankSmsFileParser.parseContent(txtBackup, "alerts.txt")
        assertEquals("Text", txtRes.formatLabel)
        assertEquals(2, txtRes.parsedTransactions.size)
        assertEquals(BankInstitution.AWASH.code, txtRes.parsedTransactions[0].bankCode)
        assertEquals(BankInstitution.DASHEN.code, txtRes.parsedTransactions[1].bankCode)
    }

    @Test
    fun `trend chart and pie slice builders handle single-day, flat, and non-finite inputs safely`() {
        val singleDayIncome = listOf(
            FinanceTransaction(
                id = 10,
                smsFingerprint = "sd1",
                bankName = "CBE",
                bankCode = BankInstitution.CBE.code,
                type = TransactionType.INCOME.name,
                amount = 12500.0,
                category = "Income / Deposit",
                timestamp = 1763020800000L
            )
        )
        val incomeTrend = FinanceAnalyticsEngine.buildTrendChartSeries(singleDayIncome, categorizeByBank = false)
        assertEquals(2, incomeTrend.dateLabels.size)
        assertEquals("Start", incomeTrend.dateLabels[0])
        assertEquals(1, incomeTrend.lines.size)
        assertEquals("INCOME", incomeTrend.lines[0].key)
        assertTrue(incomeTrend.hasPlottableSeries)

        val badValues = listOf(
            FinanceTransaction(
                id = 11,
                smsFingerprint = "bad1",
                bankName = "CBE",
                bankCode = BankInstitution.CBE.code,
                type = TransactionType.EXPENSE.name,
                amount = 0.0,
                category = "Other",
                timestamp = 1763020800000L
            ),
            FinanceTransaction(
                id = 12,
                smsFingerprint = "bad2",
                bankName = "CBE",
                bankCode = BankInstitution.CBE.code,
                type = TransactionType.EXPENSE.name,
                amount = Double.NaN,
                category = "Other",
                timestamp = 1763107200000L
            )
        )
        val zeroTrend = FinanceAnalyticsEngine.buildTrendChartSeries(badValues, categorizeByBank = false)
        assertFalse(zeroTrend.hasPlottableSeries)
        assertTrue(FinanceAnalyticsEngine.buildPieSlices(badValues, categorizeByBank = true).isEmpty())
    }

    @Test
    fun `chunked Room insertion deduplicates more than 250 unique SMS transactions cleanly`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dao = AppDatabase.getDatabase(context).voiceTaskDao()
        val repo = VoiceTaskRepository(dao)
        repo.clearAllFinanceTransactions()

        val longBodyPrefix = "Dear Customer, your Account 1000***99 has been debited with ETB 120.00. Current Balance is ETB 9,000.00. "
        val hugeBody = longBodyPrefix + "X".repeat(3000)
        assertNotNull(BankSmsParser.parseSms("CBE", hugeBody))

        val batch = (1..260).map { idx ->
            FinanceTransaction(
                smsFingerprint = "bulk_fp_$idx",
                bankName = "Telebirr",
                bankCode = BankInstitution.TELEBIRR.code,
                type = TransactionType.EXPENSE.name,
                amount = 100.0 + idx,
                counterparty = "Merchant $idx",
                timestamp = 1763020800000L + idx * 60_000L
            )
        }
        val inserted = repo.insertFinanceTransactionsDedup(batch + batch.take(15))
        assertEquals(260, inserted)
        assertEquals(260, repo.getAllFinanceTransactions().size)
    }

    @Test
    fun `finance overview charts and responsive 2x2 plus collapsible filter UI compose without crashing`() {
        val singleDayIncomeOnly = listOf(
            FinanceTransaction(
                id = 1,
                smsFingerprint = "ui_1",
                bankName = "Telebirr",
                bankCode = BankInstitution.TELEBIRR.code,
                type = TransactionType.INCOME.name,
                amount = 4500.0,
                balanceAfter = 14500.0,
                counterparty = "Client A",
                category = "Income / Deposit",
                timestamp = 1763020800000L
            )
        )

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                FinanceOverviewComposable(
                    transactions = singleDayIncomeOnly,
                    monthlySpendingTotal = 0.0,
                    monthlyIncomeTotal = 4500.0,
                    hasSmsPermission = true
                )
            }
        }

        composeTestRule.onNodeWithTag("finance_overview_chart_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("finance_chart_mode_pie").performClick()
        composeTestRule.onNodeWithTag("finance_chart_group_bank").performClick()
        composeTestRule.onNodeWithTag("finance_chart_mode_trend").performClick()
    }

    @Test
    fun `telebirr footer does not pollute counterparty or category and non-transactions are rejected`() = runTest {
        val telebirrTransfer = "Dear Zelalem, You have transferred ETB 40.00 to Belete Yisemaw (251911000000) on 14/11/2026 18:46:10. Your transaction number is DBK991122. The service fee is ETB 0.00 and VAT (15%) is ETB 0.00. Your current telebirr account balance is ETB 1,087.95. Thank you for using telebirr ethio telecom"
        val parsedTransfer = BankSmsParser.parseSms("127", telebirrTransfer)
        assertNotNull(parsedTransfer)
        assertEquals("Belete Yisemaw", parsedTransfer!!.counterparty)
        assertEquals("Transfers", parsedTransfer.category)
        assertEquals(40.0, parsedTransfer.amount, 0.01)
        assertEquals(1087.95, parsedTransfer.balanceAfter ?: 0.0, 0.01)

        val telebirrBonus = "Dear Customer, You have received ETB 5.00 cashback reward on 12/11/2026 19:10:00. Your current telebirr account balance is ETB 1,092.95. Thank you for using telebirr ethio telecom"
        val parsedBonus = BankSmsParser.parseSms("127", telebirrBonus)
        assertNotNull(parsedBonus)
        assertFalse(parsedBonus!!.counterparty.contains("using telebirr", ignoreCase = true))

        val telebirrAirtime = "You have paid ETB 25.00 for airtime recharge on 14/11/2026 10:00:00. Your current balance is ETB 900.00. Thank you for using telebirr ethio telecom"
        val parsedAirtime = BankSmsParser.parseSms("127", telebirrAirtime)
        assertNotNull(parsedAirtime)
        assertEquals("Airtime & Data", parsedAirtime!!.category)

        // Strict rejections even when "balance" appears in the text
        val failedTx = "Your Telebirr transfer of ETB 5,000.00 has failed due to insufficient balance. Current balance is ETB 200.00."
        assertNull(BankSmsParser.parseSms("127", failedTx))

        val otpWithBalanceWord = "Your CBE verification code is 448811 to check your balance. Do not share this code."
        assertNull(BankSmsParser.parseSms("CBE", otpWithBalanceWord))

        val telecomDataBalance = "Dear Customer, your remaining data package balance is 1024 MB and expires in 5 days."
        assertNull(BankSmsParser.parseSms("ethiotel", telecomDataBalance))

        val balanceInquiryOnly = "Dear Customer, your current account balance is ETB 12,450.00."
        assertNull(BankSmsParser.parseSms("CBE", balanceInquiryOnly))

        // Verify stored Room reparse repairs legacy misclassified rows and deletes false positives
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dao = AppDatabase.getDatabase(context).voiceTaskDao()
        val repo = VoiceTaskRepository(dao)
        repo.clearAllFinanceTransactions()

        repo.insertFinanceTransaction(
            FinanceTransaction(
                smsFingerprint = "legacy_bad_1",
                bankName = "Telebirr",
                bankCode = BankInstitution.TELEBIRR.code,
                type = TransactionType.TRANSFER.name,
                amount = 40.0,
                counterparty = "using telebirr ethio telecom",
                category = "Airtime & Data",
                rawSender = "127",
                rawSmsBody = telebirrTransfer,
                timestamp = parsedTransfer.timestamp,
                source = "SMS_INBOX"
            )
        )
        repo.insertFinanceTransaction(
            FinanceTransaction(
                smsFingerprint = "legacy_failed_2",
                bankName = "Telebirr",
                bankCode = BankInstitution.TELEBIRR.code,
                type = TransactionType.EXPENSE.name,
                amount = 5000.0,
                counterparty = "Telebirr",
                category = "Other",
                rawSender = "127",
                rawSmsBody = failedTx,
                timestamp = 1763020800000L,
                source = "SMS_INBOX"
            )
        )

        val (updated, removed) = repo.reparseStoredSmsTransactions()
        assertEquals(1, updated)
        assertEquals(1, removed)
        val cleaned = repo.getAllFinanceTransactions()
        assertEquals(1, cleaned.size)
        assertEquals("Belete Yisemaw", cleaned.first().counterparty)
        assertEquals("Transfers", cleaned.first().category)
    }

    @Test
    fun `streamlined finance hero uses single + menu and collapsible filter drawer`() {
        val filterState = FinanceFilterState()
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    FinanceHeroSummaryCard(
                        summary = FinanceSummary(
                            transactionCount = 2,
                            totalIncome = 5000.0,
                            totalOutflow = 1200.0,
                            netFlow = 3800.0
                        ),
                        monthlySpendingTotal = 1200.0,
                        monthlyIncomeTotal = 5000.0,
                        monthlyLabel = "November 2026",
                        filterState = filterState,
                        isScanning = false,
                        hasAnyData = true,
                        hasSmsPermission = true,
                        onScanSmsClick = {},
                        onPasteSmsClick = {},
                        onImportFileClick = {},
                        onManualAddClick = {},
                        onResetFilters = {},
                        onClearAllClick = {}
                    )
                    FinanceFilterPanel(
                        filterState = filterState,
                        onSelectPeriod = {},
                        onOpenTimeWindow = {},
                        onSelectBank = {},
                        onSelectType = {},
                        onSearchChange = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("finance_scan_sms_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("finance_add_menu_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("finance_add_menu_btn").performClick()
        composeTestRule.onNodeWithTag("finance_menu_paste_sms").assertIsDisplayed()
        composeTestRule.onNodeWithTag("finance_menu_import_file").assertIsDisplayed()
        composeTestRule.onNodeWithTag("finance_menu_add_manual").assertIsDisplayed()

        // Collapsed by default
        composeTestRule.onNodeWithText("BANK / WALLET").assertDoesNotExist()
        // Expand filters
        composeTestRule.onNodeWithTag("finance_toggle_filters_btn")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("BANK / WALLET")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `compact action bar opens filter bottom sheet and ledger header updates search`() {
        composeTestRule.setContent {
            var filterState by remember { mutableStateOf(FinanceFilterState()) }
            var showFilterSheet by remember { mutableStateOf(false) }

            MyApplicationTheme(darkTheme = false) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    CompactFinanceActionBar(
                        periodLabel = "All Time",
                        filterState = filterState,
                        isScanning = false,
                        hasAnyData = true,
                        hasSmsPermission = true,
                        onScanSmsClick = {},
                        onOpenFilters = { showFilterSheet = true },
                        onPasteSmsClick = {},
                        onImportFileClick = {},
                        onManualAddClick = {},
                        onResetFilters = { filterState = FinanceFilterState() },
                        onClearAllClick = {}
                    )
                    FinanceHeroSummaryCard(
                        summary = FinanceSummary(
                            transactionCount = 2,
                            totalIncome = 5000.0,
                            totalOutflow = 1200.0,
                            netFlow = 3800.0
                        ),
                        filterState = filterState,
                        isScanning = false,
                        hasAnyData = true,
                        hasSmsPermission = true,
                        showTopActions = false,
                        onScanSmsClick = {},
                        onPasteSmsClick = {},
                        onImportFileClick = {},
                        onManualAddClick = {},
                        onResetFilters = {},
                        onClearAllClick = {}
                    )
                    FinanceLedgerSearchHeader(
                        filterState = filterState,
                        onSearchChange = { filterState = filterState.copy(searchQuery = it) },
                        onOpenFilters = { showFilterSheet = true },
                        onResetFilters = { filterState = FinanceFilterState() }
                    )
                    if (showFilterSheet) {
                        FinanceFilterBottomSheet(
                            filterState = filterState,
                            filteredCount = 2,
                            onDismiss = { showFilterSheet = false },
                            onSelectPeriod = { filterState = filterState.copy(periodMode = it) },
                            onOpenTimeWindow = {},
                            onSelectBank = { filterState = filterState.copy(selectedBankCode = it) },
                            onSelectType = { filterState = filterState.copy(typeFilter = it) },
                            onResetAll = { filterState = FinanceFilterState() }
                        )
                    }
                }
            }
        }

        composeTestRule.onNodeWithTag("finance_compact_action_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("finance_open_filters_btn").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Filter Transactions").assertIsDisplayed()
        composeTestRule.onNodeWithText("Telebirr").performClick()
        composeTestRule.onNodeWithTag("finance_filter_sheet_apply_btn").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Filtered: Telebirr").assertIsDisplayed()
        composeTestRule.onNodeWithTag("finance_search_input").performTextInput("Abebe")
        composeTestRule.onNodeWithText("Abebe").assertIsDisplayed()
    }

    @Test
    fun `auto suggests expense categories from sms keywords including groceries rent and utility`() {
        val grocerySms = "Dear Customer, you have paid ETB 1,240.00 to Shoa Supermarket for groceries on 16/11/2026 11:20:00. Current balance is ETB 8,400.00."
        val rentSms = "Dear Customer, you have transferred ETB 12,000.00 to Dawit Tadesse for monthly apartment rent on 16/11/2026 09:05:00. Ref FT26119001."
        val utilitySms = "You have paid ETB 680.50 via CBE Birr for electricity utility bill on 15/11/2026 16:40:00. Balance is ETB 4,100.00."
        val pharmacySms = "You have paid ETB 350.00 to Kenema Pharmacy on 15/11/2026 14:15:00. Current telebirr balance is ETB 950.00."
        val learnedMerchantSms = "You have paid ETB 210.00 to Blue Sky Trading on 15/11/2026 12:00:00. Current telebirr balance is ETB 740.00."

        val txGrocery = BankSmsParser.parseSms("127", grocerySms)
        val txRent = BankSmsParser.parseSms("CBE", rentSms)
        val txUtility = BankSmsParser.parseSms("CBEBirr", utilitySms)
        val txPharmacy = BankSmsParser.parseSms("127", pharmacySms)

        assertNotNull(txGrocery)
        assertEquals("Groceries", txGrocery!!.category)

        assertNotNull(txRent)
        assertEquals("Rent & Housing", txRent!!.category)

        assertNotNull(txUtility)
        assertEquals("Utilities", txUtility!!.category)

        assertNotNull(txPharmacy)
        assertEquals("Health & Medical", txPharmacy!!.category)

        // Verify learned counterparty rule applies automatically for ambiguous merchants
        val learnedRules = mapOf(
            ExpenseCategorySuggester.normalizeCounterpartyKey("Blue Sky Trading") to "Groceries"
        )
        val txLearned = BankSmsParser.parseSms(
            sender = "127",
            body = learnedMerchantSms,
            learnedCategoryRules = learnedRules
        )
        assertNotNull(txLearned)
        assertEquals("Groceries", txLearned!!.category)

        // Verify live suggestion metadata exposes matched keywords & alternatives
        val preview = BankSmsParser.suggestCategoryForSms(rentSms)
        assertNotNull(preview)
        assertEquals("Rent & Housing", preview!!.category)
        assertNotNull(preview.matchedKeyword)
        assertTrue(preview.alternatives.isNotEmpty())
    }

    @Test
    fun `paste sms dialog previews suggested category and supports quick override`() {
        var importedCategoryOverride: String? = null
        var importedBody = ""

        val sampleRentSms = "You have transferred ETB 9,500.00 to Landlord Mekonnen for rent on 16/11/2026 10:00:00. Current telebirr balance is ETB 2,500.00."

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                PasteBankSmsDialog(
                    onDismiss = {},
                    onImport = { body, _, overrideCat ->
                        importedBody = body
                        importedCategoryOverride = overrideCat
                    }
                )
            }
        }

        composeTestRule.onNodeWithTag("paste_sms_body_input").performTextInput(sampleRentSms)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("paste_sms_category_suggestion_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Suggested: Rent & Housing").assertIsDisplayed()

        // Tap alternative category chip inside suggestion card and verify live state before import
        composeTestRule.onNodeWithText("Transfers").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Suggested: Transfers").assertIsDisplayed()

        composeTestRule.onNodeWithTag("confirm_paste_sms_btn").performClick()
        composeTestRule.waitForIdle()
        assertEquals(sampleRentSms, importedBody)
        assertEquals("Transfers", importedCategoryOverride)
    }

    @Test
    fun `transaction detail dialog displays keyword suggestion banner and applies category`() {
        var appliedCategory = ""

        val sampleGroceryTx = FinanceTransaction(
            id = 42,
            smsFingerprint = "fp_grocery_detail",
            bankName = "Telebirr",
            bankCode = BankInstitution.TELEBIRR.code,
            type = TransactionType.EXPENSE.name,
            amount = 450.0,
            counterparty = "Fresh Groceries Mart",
            category = "Other",
            rawSender = "127",
            rawSmsBody = "You have paid ETB 450.00 to Fresh Groceries Mart for groceries.",
            timestamp = 1763280000000L
        )

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                TransactionDetailDialog(
                    tx = sampleGroceryTx,
                    onDismiss = {},
                    onUpdateCategory = { appliedCategory = it },
                    onUpdateType = {},
                    onReparse = {},
                    onDelete = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("tx_detail_category_suggestion").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Apply").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals("Groceries", appliedCategory)
    }
}
