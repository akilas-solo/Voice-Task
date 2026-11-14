package com.example.data

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object BankSmsParser {

    val FIN_CATEGORIES = listOf(
        "Transfers",
        "Airtime & Data",
        "Merchant & Shopping",
        "Food & Utilities",
        "Salary & Income",
        "ATM & Cash",
        "Bank Charges",
        "Other"
    )

    private val OTP_EXCLUDE_REGEX = Regex(
        """\b(otp|one[\s-]?time|verification\s+code|pin\s+code|security\s+code|2fa|passcode|login\s+code|expires\s+in\s+\d+|do\s+not\s+share\s+this\s+code)\b""",
        RegexOption.IGNORE_CASE
    )

    private val PROMO_EXCLUDE_REGEX = Regex(
        """\b(win\s+up\s+to|download\s+the\s+app\s+now|bonus\s+package|dial\s+\*\d+#|subscribe\s+now|cashback\s+offer)\b""",
        RegexOption.IGNORE_CASE
    )

    private val BANK_SIGNAL_REGEX = Regex(
        """\b(telebirr|cbe|cbebirr|cbe\s*birr|abyssinia|boa|awash|dashen|amole|nib|coop|ebirr|hibret|zemen|wegagen|bunna|berhan|oromia|enat|abay|lion|mpesa|m-pesa|debited|credited|transferred|received|withdrawn|deposited|paid|current\s+balance|available\s+balance|service\s+charge|service\s+fee|etb|birr|acct|account)\b""",
        RegexOption.IGNORE_CASE
    )

    // Patterns for monetary values e.g. "ETB 1,500.00", "Br. 500", "1,250.50 Birr", "USD 40.00"
    private val CURRENCY_FIRST_REGEX = Regex(
        """(?i)\b(ETB|BIRR|BR\.?|USD|\$|EUR)\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)"""
    )
    private val AMOUNT_FIRST_REGEX = Regex(
        """(?i)([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)\s*(ETB|BIRR|BR\.?|USD|EUR)\b"""
    )

    private val BALANCE_REGEX = Regex(
        """(?i)(?:current\s+balance|available\s+balance|avail\.?\s*bal|new\s+balance|account\s+balance|your\s+balance|balance)(?:\s+is)?[\s:]*(?:(ETB|BIRR|BR\.?|USD|\$|EUR)\s*)?([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)(?:\s*(ETB|BIRR|BR\.?|USD|EUR))?"""
    )

    private val FEE_REGEX = Regex(
        """(?i)(?:service\s+charge|service\s+fee|tx\s*fee|commission|fee\s+of|charge\s+of|total\s+fee)(?:\s+is)?[\s:]*(?:(ETB|BIRR|BR\.?|USD|\$|EUR)\s*)?([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)(?:\s*(ETB|BIRR|BR\.?|USD|EUR))?"""
    )

    private val REF_REGEX = Regex(
        """(?i)(?:transaction\s+(?:number|id|ref)|txn\s*(?:id|no)|reference\s*(?:number|no|id)?|ref\s*(?:no|id)?|ft\s*no)[\s:#]*(?:is\s+)?([A-Z0-9]{6,22})\b"""
    )

    private val STANDALONE_FT_REGEX = Regex("""\b(FT[0-9A-Z]{8,16}|[A-Z]{2,4}[0-9]{5,12}[A-Z0-9]*)\b""")

    private val TO_COUNTERPARTY_REGEX = Regex(
        """(?i)\b(?:to|beneficiary|merchant)\s+([A-Za-z][A-Za-z0-9\s.&'-]{1,40}?)(?=\s*\(|\s+on\s+\d|\s+at\s+\d|\s+for\s+|\s+with\s+|\s+via\s+|\.\s|$)"""
    )
    private val FROM_COUNTERPARTY_REGEX = Regex(
        """(?i)\bfrom\s+([A-Za-z][A-Za-z0-9\s.&'-]{1,40}?)(?=\s*\(|\s+on\s+\d|\s+at\s+\d|\s+for\s+|\s+with\s+|\.\s|$)"""
    )
    private val FOR_REASON_REGEX = Regex(
        """(?i)\bfor\s+([A-Za-z][A-Za-z0-9\s.&'-]{2,40}?)(?=\s+on\s+\d|\s+at\s+\d|\.\s|$)"""
    )

    fun parseSms(
        sender: String,
        body: String,
        receivedTimestampMs: Long = System.currentTimeMillis(),
        source: String = "SMS_INBOX"
    ): FinanceTransaction? {
        val cleanSender = sender.trim()
        val cleanBody = body.trim()
        if (cleanBody.length < 12) return null

        val hasOtpWithoutBalance = OTP_EXCLUDE_REGEX.containsMatchIn(cleanBody) &&
            !cleanBody.contains("balance", ignoreCase = true)
        if (hasOtpWithoutBalance) return null

        val institution = detectBank(cleanSender, cleanBody)
        val hasBankSignal = institution != BankInstitution.OTHER || BANK_SIGNAL_REGEX.containsMatchIn(cleanBody)
        if (!hasBankSignal) return null
        if (PROMO_EXCLUDE_REGEX.containsMatchIn(cleanBody) && !cleanBody.contains("balance", ignoreCase = true)) {
            return null
        }

        // Skip statements without transactional direction or primary amount
        val balanceMatch = BALANCE_REGEX.find(cleanBody)
        val feeMatch = FEE_REGEX.find(cleanBody)

        val balanceAfter = balanceMatch?.groupValues?.getOrNull(2)?.let(::parseAmountDouble)
        val serviceCharge = feeMatch?.groupValues?.getOrNull(2)?.let(::parseAmountDouble)

        // Collect all currency amounts excluding the balance and fee spans so we find the primary transaction amount
        val excludedRanges = listOfNotNull(balanceMatch?.range, feeMatch?.range)
        val primaryAmountAndCurrency = extractPrimaryAmount(cleanBody, excludedRanges) ?: return null
        val (amount, rawCurrency) = primaryAmountAndCurrency
        if (amount <= 0.0) return null

        val normalizedCurrency = normalizeCurrency(rawCurrency)
        val txType = detectTransactionType(cleanBody)
        val counterparty = extractCounterparty(cleanBody, txType, institution)
        val referenceId = extractReference(cleanBody)
        val effectiveTimestamp = extractEmbeddedTimestamp(cleanBody) ?: receivedTimestampMs
        val category = classifyCategory(cleanBody, txType, counterparty)

        val displayBankName = if (institution == BankInstitution.OTHER) {
            inferOtherBankName(cleanSender, cleanBody)
        } else {
            institution.displayName
        }

        val fingerprint = computeFingerprint(cleanSender, cleanBody, effectiveTimestamp)

        return FinanceTransaction(
            smsFingerprint = fingerprint,
            bankName = displayBankName,
            bankCode = institution.code,
            type = txType.name,
            amount = amount,
            currency = normalizedCurrency,
            balanceAfter = balanceAfter,
            serviceCharge = serviceCharge,
            counterparty = counterparty,
            referenceId = referenceId,
            category = category,
            rawSender = cleanSender.ifBlank { displayBankName },
            rawSmsBody = cleanBody,
            timestamp = effectiveTimestamp,
            source = source
        )
    }

    fun parseBatchRawText(
        rawInput: String,
        defaultSender: String = "Manual SMS",
        nowMs: Long = System.currentTimeMillis()
    ): List<FinanceTransaction> {
        val clean = rawInput.trim()
        if (clean.isBlank()) return emptyList()

        // Split by blank lines if user pasted multiple SMS blocks at once; otherwise treat as 1 or line-separated SMS
        val blocks = clean.split(Regex("""\n\s*\n+""")).map { it.trim() }.filter { it.isNotBlank() }
        val parsedBlocks = blocks.mapIndexedNotNull { idx, block ->
            parseSms(
                sender = defaultSender,
                body = block,
                receivedTimestampMs = nowMs + idx,
                source = "MANUAL_PASTE"
            )
        }
        if (parsedBlocks.isNotEmpty()) return parsedBlocks

        // Retry single line split if each line is an individual SMS
        val lines = clean.lines().map { it.trim() }.filter { it.length > 20 }
        if (lines.size > 1) {
            return lines.mapIndexedNotNull { idx, line ->
                parseSms(
                    sender = defaultSender,
                    body = line,
                    receivedTimestampMs = nowMs + idx,
                    source = "MANUAL_PASTE"
                )
            }
        }
        return emptyList()
    }

    fun detectBank(sender: String, body: String): BankInstitution {
        val combined = "${sender.lowercase(Locale.ROOT)} ${body.lowercase(Locale.ROOT)}"
        return when {
            combined.contains("telebirr") || sender.trim() == "127" -> BankInstitution.TELEBIRR
            combined.contains("cbebirr") || combined.contains("cbe birr") || combined.contains("cbe-birr") -> BankInstitution.CBE_BIRR
            combined.contains("commercial bank of ethiopia") ||
                Regex("""\bcbe\b""").containsMatchIn(combined) -> BankInstitution.CBE
            combined.contains("abyssinia") ||
                Regex("""\bboa\b""").containsMatchIn(combined) ||
                sender.trim() == "8115" -> BankInstitution.BOA
            combined.contains("awash") -> BankInstitution.AWASH
            combined.contains("dashen") || combined.contains("amole") -> BankInstitution.DASHEN
            combined.contains("nib bank") || Regex("""\bnib\b""").containsMatchIn(combined) -> BankInstitution.NIB
            combined.contains("coop") || combined.contains("cooperative bank") || combined.contains("ebirr") -> BankInstitution.COOP
            combined.contains("hibret") -> BankInstitution.HIBRET
            combined.contains("zemen") -> BankInstitution.ZEMEN
            combined.contains("m-pesa") || combined.contains("mpesa") -> BankInstitution.MPESA
            else -> BankInstitution.OTHER
        }
    }

    private fun inferOtherBankName(sender: String, body: String): String {
        val lower = body.lowercase(Locale.ROOT)
        val knownNamed = listOf(
            "wegagen" to "Wegagen Bank",
            "bunna" to "Bunna Bank",
            "berhan" to "Berhan Bank",
            "oromia bank" to "Oromia Bank",
            "enat" to "Enat Bank",
            "abay bank" to "Abay Bank",
            "lion bank" to "Lion Bank",
            "zembaba" to "Zembaba",
            "tsebri" to "Tsehay Bank",
            "siinqee" to "Siinqee Bank",
            "amhara bank" to "Amhara Bank",
            "ahadu" to "Ahadu Bank"
        )
        knownNamed.firstOrNull { lower.contains(it.first) }?.let { return it.second }

        val cleanSender = sender.trim()
        if (cleanSender.isNotBlank() &&
            !cleanSender.equals("Manual SMS", ignoreCase = true) &&
            !cleanSender.all { it.isDigit() || it == '+' }
        ) {
            return cleanSender.take(24)
        }
        return "Other Bank"
    }

    private fun extractPrimaryAmount(body: String, excludedRanges: List<IntRange>): Pair<Double, String>? {
        val candidates = mutableListOf<Triple<Int, Double, String>>()

        CURRENCY_FIRST_REGEX.findAll(body).forEach { match ->
            if (excludedRanges.none { match.range.first in it || match.range.last in it }) {
                val amt = parseAmountDouble(match.groupValues[2])
                if (amt != null && amt > 0.0) {
                    candidates.add(Triple(match.range.first, amt, match.groupValues[1]))
                }
            }
        }
        AMOUNT_FIRST_REGEX.findAll(body).forEach { match ->
            if (excludedRanges.none { match.range.first in it || match.range.last in it }) {
                val amt = parseAmountDouble(match.groupValues[1])
                if (amt != null && amt > 0.0) {
                    candidates.add(Triple(match.range.first, amt, match.groupValues[2]))
                }
            }
        }

        val first = candidates.minByOrNull { it.first } ?: return null
        return first.second to first.third
    }

    private fun detectTransactionType(body: String): TransactionType {
        val lower = body.lowercase(Locale.ROOT)
        val incomeHints = listOf(
            "credited",
            "have received",
            "you received",
            "received etb",
            "deposited",
            "cash in",
            "cash-in",
            "salary",
            "refunded",
            "inward transfer"
        )
        val transferHints = listOf(
            "transferred",
            "sent to",
            "fund transfer",
            "account transfer"
        )
        val expenseHints = listOf(
            "debited",
            "withdrawn",
            "cash out",
            "cash-out",
            "have paid",
            "you paid",
            "payment of",
            "purchased",
            "bought",
            "airtime",
            "package",
            "merchant",
            "bill",
            "fee deducted"
        )

        return when {
            incomeHints.any { lower.contains(it) } -> TransactionType.INCOME
            expenseHints.any { lower.contains(it) } -> TransactionType.EXPENSE
            transferHints.any { lower.contains(it) } -> TransactionType.TRANSFER
            else -> TransactionType.EXPENSE
        }
    }

    private fun extractCounterparty(body: String, type: TransactionType, bank: BankInstitution): String {
        if (type == TransactionType.INCOME) {
            FROM_COUNTERPARTY_REGEX.find(body)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.length >= 2 }?.let {
                return cleanCounterparty(it)
            }
        }
        TO_COUNTERPARTY_REGEX.find(body)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.length >= 2 }?.let {
            return cleanCounterparty(it)
        }
        FROM_COUNTERPARTY_REGEX.find(body)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.length >= 2 }?.let {
            return cleanCounterparty(it)
        }
        FOR_REASON_REGEX.find(body)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.length >= 2 }?.let {
            return cleanCounterparty(it)
        }
        return when {
            body.contains("airtime", ignoreCase = true) -> "Ethio Telecom Airtime"
            body.contains("package", ignoreCase = true) -> "Data / Voice Package"
            body.contains("atm", ignoreCase = true) || body.contains("withdrawn", ignoreCase = true) -> "ATM Withdrawal"
            else -> bank.displayName
        }
    }

    private fun cleanCounterparty(raw: String): String {
        return raw
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trimEnd('.', ',', ';', ':')
            .take(42)
    }

    private fun extractReference(body: String): String {
        REF_REGEX.find(body)?.groupValues?.getOrNull(1)?.trim()?.let { return it }
        STANDALONE_FT_REGEX.find(body)?.groupValues?.getOrNull(1)?.trim()?.let { candidate ->
            if (!candidate.equals("ETB", ignoreCase = true) && !candidate.equals("BIRR", ignoreCase = true)) {
                return candidate
            }
        }
        return ""
    }

    private fun classifyCategory(body: String, type: TransactionType, counterparty: String): String {
        val lower = "$body $counterparty".lowercase(Locale.ROOT)
        return when {
            lower.contains("salary") || lower.contains("payroll") -> "Salary & Income"
            type == TransactionType.INCOME -> "Salary & Income"
            lower.contains("airtime") || lower.contains("ethio telecom") || lower.contains("data package") || lower.contains("top up") || lower.contains("recharge") -> "Airtime & Data"
            lower.contains("atm") || lower.contains("withdrawn") || lower.contains("cash out") || lower.contains("cash-out") -> "ATM & Cash"
            lower.contains("electric") || lower.contains("water") || lower.contains("utility") || lower.contains("restaurant") || lower.contains("cafe") || lower.contains("supermarket") || lower.contains("grocery") || lower.contains("food") -> "Food & Utilities"
            lower.contains("merchant") || lower.contains("pos") || lower.contains("shop") || lower.contains("store") || lower.contains("paid") -> "Merchant & Shopping"
            type == TransactionType.TRANSFER || lower.contains("transfer") -> "Transfers"
            lower.contains("service charge") || lower.contains("bank charge") || lower.contains("maintenance fee") -> "Bank Charges"
            else -> "Other"
        }
    }

    fun extractEmbeddedTimestamp(body: String): Long? {
        // Supports common Ethiopian SMS date/time formats:
        // "13/11/2026 14:25:10", "13/11/2026 at 14:25", "13-Nov-2026 at 10:15:00", "2026-11-13 14:25:10", "13-11-2026 02:25 PM"
        val dateTimePatterns = listOf(
            Regex("""(\d{2}/\d{2}/\d{4})\s+(?:at\s+)?(\d{1,2}:\d{2}:\d{2})""") to "dd/MM/yyyy HH:mm:ss",
            Regex("""(\d{2}/\d{2}/\d{4})\s+(?:at\s+)?(\d{1,2}:\d{2})""") to "dd/MM/yyyy HH:mm",
            Regex("""(\d{4}-\d{2}-\d{2})\s+(?:at\s+)?(\d{1,2}:\d{2}:\d{2})""") to "yyyy-MM-dd HH:mm:ss",
            Regex("""(\d{4}-\d{2}-\d{2})\s+(?:at\s+)?(\d{1,2}:\d{2})""") to "yyyy-MM-dd HH:mm",
            Regex("""(\d{1,2}-[A-Za-z]{3}-\d{4})\s+(?:at\s+)?(\d{1,2}:\d{2}:\d{2})""") to "dd-MMM-yyyy HH:mm:ss",
            Regex("""(\d{1,2}-[A-Za-z]{3}-\d{4})\s+(?:at\s+)?(\d{1,2}:\d{2})""") to "dd-MMM-yyyy HH:mm",
            Regex("""(\d{2}-\d{2}-\d{4})\s+(?:at\s+)?(\d{1,2}:\d{2}:\d{2})""") to "dd-MM-yyyy HH:mm:ss",
            Regex("""(\d{2}-\d{2}-\d{4})\s+(?:at\s+)?(\d{1,2}:\d{2})""") to "dd-MM-yyyy HH:mm"
        )
        for ((regex, pattern) in dateTimePatterns) {
            val match = regex.find(body) ?: continue
            val combined = "${match.groupValues[1]} ${match.groupValues[2]}"
            val sdf = SimpleDateFormat(pattern, Locale.ENGLISH).apply {
                isLenient = false
                timeZone = TimeZone.getDefault()
            }
            val parsed = runCatching { sdf.parse(combined)?.time }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }

    private fun parseAmountDouble(raw: String): Double? {
        return raw.replace(",", "").trim().toDoubleOrNull()
    }

    private fun normalizeCurrency(raw: String): String {
        val upper = raw.trim().uppercase(Locale.ROOT).trimEnd('.')
        return when (upper) {
            "BIRR", "BR", "ETB" -> "ETB"
            "$" -> "USD"
            else -> upper.ifBlank { "ETB" }
        }
    }

    fun computeFingerprint(sender: String, body: String, timestampMs: Long): String {
        val minuteBucket = timestampMs / 60_000L
        val normalizedBody = body.lowercase(Locale.ROOT).replace(Regex("""\s+"""), " ").trim()
        val normalizedSender = sender.lowercase(Locale.ROOT).trim()
        val input = "$normalizedSender|$normalizedBody|$minuteBucket"
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }
}
