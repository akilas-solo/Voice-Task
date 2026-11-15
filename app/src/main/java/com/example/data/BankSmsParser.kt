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
        """\b(otp|one[\s-]?time(?:\s+(?:pass(?:word|code)?|pin|code))?|verification\s+code|pin\s+code|security\s+code|2fa|passcode|login\s+code|secret\s+code|expires\s+in\s+\d+|do\s+not\s+share\s+(?:this|your)|changed\s+your\s+pin|pin\s+(?:reset|changed))\b""",
        RegexOption.IGNORE_CASE
    )

    private val FAILED_TX_EXCLUDE_REGEX = Regex(
        """\b(transaction\s+(?:failed|declined|rejected|cancell?ed|unsuccessful|not\s+completed)|has\s+failed|was\s+(?:declined|rejected|unsuccessful)|insufficient\s+(?:balance|funds)|could\s+not\s+be\s+(?:processed|completed)|failed\s+to\s+(?:transfer|send|pay|process)|limit\s+exceeded)\b""",
        RegexOption.IGNORE_CASE
    )

    private val PROMO_EXCLUDE_REGEX = Regex(
        """\b(win\s+up\s+to|download\s+(?:the|our)\s+app(?:\s+now)?|bonus\s+package|dial\s+\*\d+#|subscribe\s+now|cashback\s+offer|get\s+(?:a\s+)?loan\s+up\s+to|eligible\s+for\s+(?:a\s+)?loan|congratulations!?\s+you\s+(?:won|are\s+eligible)|remaining\s+(?:data|voice|sms|package|internet|bonus)\s+balance)\b""",
        RegexOption.IGNORE_CASE
    )

    private val BANK_SIGNAL_REGEX = Regex(
        """\b(telebirr|cbe|cbebirr|cbe\s*birr|abyssinia|boa|awash|dashen|amole|nib|coop|ebirr|hibret|zemen|wegagen|bunna|berhan|oromia|enat|abay|lion|mpesa|m-pesa|debited|credited|transfer(?:red|ed)?|received|withdrawn|deposited|paid|current\s+balance|available\s+balance|service\s+charge|service\s+fee|etb|birr|acct|account)\b""",
        RegexOption.IGNORE_CASE
    )

    private val TRANSACTION_ACTION_REGEX = Regex(
        """\b(credited|debited|transfer(?:red|ed)?|received|withdrawn|deposited|paid|payment|purchased|bought|sent\s+to|cash[\s-]?in|cash[\s-]?out|top[\s-]?up|recharg(?:e|ed)|settled|refunded|transaction\s+(?:number|id|ref)|txn\s*(?:id|no)|ft\s*no)\b""",
        RegexOption.IGNORE_CASE
    )

    private val FOOTER_STRIP_REGEX = Regex(
        """(?is)\b(?:thank\s+you\s+for\s+(?:using|banking\s+with|choosing)|thanks\s+for\s+(?:using|banking\s+with|choosing)|for\s+(?:more\s+info|details|inquiries|help)|visit\s+https?://|download\s+(?:the|our)\s+app).*$"""
    )

    // Patterns for monetary values e.g. "ETB 1,500.00", "Br. 500", "1,250.50 Birr", "USD 40.00"
    private val CURRENCY_FIRST_REGEX = Regex(
        """(?i)\b(ETB|BIRR|BR\.?|USD|\$|EUR)\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)"""
    )
    private val AMOUNT_FIRST_REGEX = Regex(
        """(?i)([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)\s*(ETB|BIRR|BR\.?|USD|EUR)\b"""
    )

    private val BALANCE_REGEX = Regex(
        """(?i)(?:(?:current|available|avail\.?|new|account|telebirr|e-?money|closing|ledger|remaining|your)\s+){0,3}(?:acct\.?\s*)?(?:balance|bal\.?)(?:\s+(?:is|of|now))?[\s:]*(?:(ETB|BIRR|BR\.?|USD|\$|EUR)\s*)?([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)(?:\s*(ETB|BIRR|BR\.?|USD|EUR))?"""
    )

    private val FEE_REGEX = Regex(
        """(?i)(?:service\s+charge|service\s+fee|tx\s*fee|commission|fee\s+of|charge\s+of|total\s+fee)(?:\s+is)?[\s:]*(?:(ETB|BIRR|BR\.?|USD|\$|EUR)\s*)?([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)(?:\s*(ETB|BIRR|BR\.?|USD|EUR))?"""
    )

    private val VAT_REGEX = Regex(
        """(?i)(?:vat|tax)(?:\s*\([^)]*\))?(?:\s+on\s+[^0-9]{1,30})?(?:\s+is)?[\s:]*(?:(ETB|BIRR|BR\.?|USD|\$|EUR)\s*)?([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)(?:\s*(ETB|BIRR|BR\.?|USD|EUR))?"""
    )

    private val REF_REGEX = Regex(
        """(?i)(?:transaction\s+(?:number|id|ref)|txn\s*(?:id|no)|reference\s*(?:number|no|id)?|ref\s*(?:no|id)?|ft\s*no)[\s:#]*(?:is\s+)?([A-Z0-9]{6,22})\b"""
    )

    private val STANDALONE_FT_REGEX = Regex("""\b(FT[0-9A-Z]{8,16}|[A-Z]{2,4}[0-9]{5,12}[A-Z0-9]*)\b""")

    private val TO_COUNTERPARTY_REGEX = Regex(
        """(?i)\b(?:to|beneficiary|merchant)\s+([A-Za-z][A-Za-z0-9\s.&'-]{1,40}?)(?=\s*\(|\s+on\s+\d|\s+at\s+\d|\s+for\s+|\s+with\s+|\s+via\s+|\.\s|$)"""
    )
    private val FROM_COUNTERPARTY_REGEX = Regex(
        """(?i)\bfrom\s+([A-Za-z][A-Za-z0-9\s.&'-]{1,40}?)(?=\s*\(|\s+on\s+\d|\s+at\s+\d|\s+for\s+|\s+with\s+|\s+to\s+|\.\s|$)"""
    )
    private val FOR_REASON_REGEX = Regex(
        """(?i)\bfor\s+([A-Za-z][A-Za-z0-9\s.&'-]{2,40}?)(?=\s+on\s+\d|\s+at\s+\d|\.\s|$)"""
    )
    private val INVALID_COUNTERPARTY_REGEX = Regex(
        """(?i)^(?:your\s+account.*|account\s*[\d*x#]*.*|acct\.?.*|your\s+telebirr.*|telebirr\s+account.*|using\s+telebirr.*|banking\s+with.*|etb|birr|usd|eur|on|at|the|is|this\s+transaction.*)$"""
    )

    private val WHITESPACE_REGEX = Regex("""\s+""")
    private val BLANK_LINE_SPLIT_REGEX = Regex("""\n\s*\n+""")
    private val CBE_WORD_REGEX = Regex("""\bcbe\b""")
    private val BOA_WORD_REGEX = Regex("""\bboa\b""")
    private val NIB_WORD_REGEX = Regex("""\bnib\b""")

    private val AIRTIME_CATEGORY_REGEX = Regex(
        """\b(airtime|recharg(?:e|ed)|top[\s-]?up|mobile\s+(?:package|bundle|data)|voice\s+package|data\s+package|internet\s+package|ethio\s+telecom\s+(?:airtime|package|bill|recharge))\b""",
        RegexOption.IGNORE_CASE
    )
    private val ATM_CATEGORY_REGEX = Regex(
        """\b(atm|withdrawn|withdrawal|cash[\s-]?out|branch\s+withdrawal)\b""",
        RegexOption.IGNORE_CASE
    )
    private val FOOD_UTILITIES_CATEGORY_REGEX = Regex(
        """\b(electric|electricity|eeu|water|aawsa|utility|utilities|dstv|wifi|broadband|restaurant|cafe|coffee|supermarket|grocery|food|bakery|kitchen)\b""",
        RegexOption.IGNORE_CASE
    )
    private val BANK_CHARGES_CATEGORY_REGEX = Regex(
        """\b(service\s+charge|bank\s+charge|maintenance\s+fee|commission\s+fee)\b""",
        RegexOption.IGNORE_CASE
    )
    private val TRANSFER_CATEGORY_REGEX = Regex(
        """\b(transfer(?:red|ed)?|sent\s+to|p2p|inward\s+transfer)\b""",
        RegexOption.IGNORE_CASE
    )
    private val MERCHANT_CATEGORY_REGEX = Regex(
        """\b(merchant|pos|shop|store|market|pharmacy|clinic|hospital|fuel|hotel|paid\s+to|you\s+paid|have\s+paid)\b""",
        RegexOption.IGNORE_CASE
    )

    private val DATE_TIME_PATTERNS: List<Pair<Regex, String>> = listOf(
        Regex("""(\d{2}/\d{2}/\d{4})\s+(?:at\s+)?(\d{1,2}:\d{2}:\d{2})""") to "dd/MM/yyyy HH:mm:ss",
        Regex("""(\d{2}/\d{2}/\d{4})\s+(?:at\s+)?(\d{1,2}:\d{2})""") to "dd/MM/yyyy HH:mm",
        Regex("""(\d{4}-\d{2}-\d{2})\s+(?:at\s+)?(\d{1,2}:\d{2}:\d{2})""") to "yyyy-MM-dd HH:mm:ss",
        Regex("""(\d{4}-\d{2}-\d{2})\s+(?:at\s+)?(\d{1,2}:\d{2})""") to "yyyy-MM-dd HH:mm",
        Regex("""(\d{1,2}-[A-Za-z]{3}-\d{4})\s+(?:at\s+)?(\d{1,2}:\d{2}:\d{2})""") to "dd-MMM-yyyy HH:mm:ss",
        Regex("""(\d{1,2}-[A-Za-z]{3}-\d{4})\s+(?:at\s+)?(\d{1,2}:\d{2})""") to "dd-MMM-yyyy HH:mm",
        Regex("""(\d{2}-\d{2}-\d{4})\s+(?:at\s+)?(\d{1,2}:\d{2}:\d{2})""") to "dd-MM-yyyy HH:mm:ss",
        Regex("""(\d{2}-\d{2}-\d{4})\s+(?:at\s+)?(\d{1,2}:\d{2})""") to "dd-MM-yyyy HH:mm"
    )

    private val DATE_FORMAT_CACHE = ThreadLocal.withInitial { mutableMapOf<String, SimpleDateFormat>() }
    private val HEX_CHARS = "0123456789abcdef".toCharArray()

    fun stripFooterBoilerplate(body: String): String {
        val stripped = FOOTER_STRIP_REGEX.replace(body, "").trim()
        return if (stripped.length >= 12) stripped else body
    }

    fun parseSms(
        sender: String,
        body: String,
        receivedTimestampMs: Long = System.currentTimeMillis(),
        source: String = "SMS_INBOX"
    ): FinanceTransaction? {
        val cleanSender = sender.trim().take(64)
        val trimmedBody = body.trim()
        if (trimmedBody.length < 12) return null
        val cleanBody = if (trimmedBody.length > 1500) trimmedBody.take(1500) else trimmedBody

        // Strictly reject OTPs, failed transactions, and promos regardless of the word "balance"
        if (OTP_EXCLUDE_REGEX.containsMatchIn(cleanBody)) return null
        if (FAILED_TX_EXCLUDE_REGEX.containsMatchIn(cleanBody)) return null
        if (PROMO_EXCLUDE_REGEX.containsMatchIn(cleanBody)) return null

        val institution = detectBank(cleanSender, cleanBody)
        val hasBankSignal = institution != BankInstitution.OTHER || BANK_SIGNAL_REGEX.containsMatchIn(cleanBody)
        if (!hasBankSignal) return null
        if (!TRANSACTION_ACTION_REGEX.containsMatchIn(cleanBody)) return null

        val semanticBody = stripFooterBoilerplate(cleanBody)

        val balanceMatch = BALANCE_REGEX.find(cleanBody)
        val feeMatch = FEE_REGEX.find(cleanBody)
        val vatMatch = VAT_REGEX.find(cleanBody)

        val balanceAfter = balanceMatch?.groupValues?.getOrNull(2)?.let(::parseAmountDouble)
        val serviceCharge = feeMatch?.groupValues?.getOrNull(2)?.let(::parseAmountDouble)

        // Collect all currency amounts excluding balance, fee, and VAT spans so we find the primary transaction amount
        val excludedRanges = listOfNotNull(balanceMatch?.range, feeMatch?.range, vatMatch?.range)
        val primaryAmountAndCurrency = extractPrimaryAmount(cleanBody, excludedRanges) ?: return null
        val (amount, rawCurrency) = primaryAmountAndCurrency
        if (!amount.isFinite() || amount <= 0.0) return null

        val normalizedCurrency = normalizeCurrency(rawCurrency)
        val txType = detectTransactionType(semanticBody)
        val counterparty = extractCounterparty(semanticBody, txType, institution)
        val referenceId = extractReference(semanticBody).ifBlank { extractReference(cleanBody) }
        val effectiveTimestamp = extractEmbeddedTimestamp(cleanBody) ?: receivedTimestampMs
        val category = classifyCategory(semanticBody, txType, counterparty)

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
            rawSmsBody = trimmedBody.take(1500),
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
        val blocks = clean.split(BLANK_LINE_SPLIT_REGEX).map { it.trim() }.filter { it.isNotBlank() }
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
                CBE_WORD_REGEX.containsMatchIn(combined) -> BankInstitution.CBE
            combined.contains("abyssinia") ||
                BOA_WORD_REGEX.containsMatchIn(combined) ||
                sender.trim() == "8115" ||
                sender.trim() == "8397" -> BankInstitution.BOA
            combined.contains("awash") -> BankInstitution.AWASH
            combined.contains("dashen") || combined.contains("amole") -> BankInstitution.DASHEN
            combined.contains("nib bank") || NIB_WORD_REGEX.containsMatchIn(combined) -> BankInstitution.NIB
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
            "tsehay bank" to "Tsehay Bank",
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
                if (amt != null && amt.isFinite() && amt > 0.0) {
                    candidates.add(Triple(match.range.first, amt, match.groupValues[1]))
                }
            }
        }
        AMOUNT_FIRST_REGEX.findAll(body).forEach { match ->
            if (excludedRanges.none { match.range.first in it || match.range.last in it }) {
                val amt = parseAmountDouble(match.groupValues[1])
                if (amt != null && amt.isFinite() && amt > 0.0) {
                    candidates.add(Triple(match.range.first, amt, match.groupValues[2]))
                }
            }
        }

        val first = candidates.minByOrNull { it.first } ?: return null
        return first.second to first.third
    }

    private fun detectTransactionType(semanticBody: String): TransactionType {
        val lower = semanticBody.lowercase(Locale.ROOT)
        val incomeHints = listOf(
            "credited",
            "have received",
            "you received",
            "received etb",
            "received br",
            "deposited",
            "cash in",
            "cash-in",
            "salary",
            "refunded",
            "inward transfer"
        )
        val transferHints = listOf(
            "transferred",
            "transfered",
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
            "paid to",
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

    private fun extractCounterparty(semanticBody: String, type: TransactionType, bank: BankInstitution): String {
        if (type == TransactionType.INCOME) {
            FROM_COUNTERPARTY_REGEX.findAll(semanticBody)
                .map { cleanCounterparty(it.groupValues[1]) }
                .firstOrNull { isValidCounterparty(it) }
                ?.let { return it }
        }
        TO_COUNTERPARTY_REGEX.findAll(semanticBody)
            .map { cleanCounterparty(it.groupValues[1]) }
            .firstOrNull { isValidCounterparty(it) }
            ?.let { return it }

        FROM_COUNTERPARTY_REGEX.findAll(semanticBody)
            .map { cleanCounterparty(it.groupValues[1]) }
            .firstOrNull { isValidCounterparty(it) }
            ?.let { return it }

        FOR_REASON_REGEX.findAll(semanticBody)
            .map { cleanCounterparty(it.groupValues[1]) }
            .firstOrNull { isValidCounterparty(it) && !it.startsWith("using ", ignoreCase = true) }
            ?.let { return it }

        return when {
            AIRTIME_CATEGORY_REGEX.containsMatchIn(semanticBody) -> "Ethio Telecom Airtime"
            semanticBody.contains("cashback", ignoreCase = true) ||
                semanticBody.contains("reward", ignoreCase = true) ||
                semanticBody.contains("bonus", ignoreCase = true) -> "${bank.shortBadge} Bonus / Cashback"
            semanticBody.contains("atm", ignoreCase = true) ||
                semanticBody.contains("withdrawn", ignoreCase = true) -> "ATM Withdrawal"
            type == TransactionType.INCOME -> "${bank.displayName} Credit"
            type == TransactionType.TRANSFER -> "${bank.displayName} Transfer"
            else -> bank.displayName
        }
    }

    private fun isValidCounterparty(candidate: String): Boolean {
        if (candidate.length < 2) return false
        return !INVALID_COUNTERPARTY_REGEX.matches(candidate)
    }

    private fun cleanCounterparty(raw: String): String {
        return raw
            .replace(WHITESPACE_REGEX, " ")
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

    private fun classifyCategory(semanticBody: String, type: TransactionType, counterparty: String): String {
        val combined = "$semanticBody $counterparty"
        val lower = combined.lowercase(Locale.ROOT)
        return when {
            lower.contains("salary") || lower.contains("payroll") -> "Salary & Income"
            type == TransactionType.INCOME -> "Salary & Income"
            AIRTIME_CATEGORY_REGEX.containsMatchIn(combined) -> "Airtime & Data"
            ATM_CATEGORY_REGEX.containsMatchIn(combined) -> "ATM & Cash"
            FOOD_UTILITIES_CATEGORY_REGEX.containsMatchIn(combined) -> "Food & Utilities"
            BANK_CHARGES_CATEGORY_REGEX.containsMatchIn(combined) -> "Bank Charges"
            type == TransactionType.TRANSFER || TRANSFER_CATEGORY_REGEX.containsMatchIn(combined) -> "Transfers"
            MERCHANT_CATEGORY_REGEX.containsMatchIn(combined) -> "Merchant & Shopping"
            else -> "Other"
        }
    }

    fun extractEmbeddedTimestamp(body: String): Long? {
        val cache = DATE_FORMAT_CACHE.get()!!
        for ((regex, pattern) in DATE_TIME_PATTERNS) {
            val match = regex.find(body) ?: continue
            val combined = "${match.groupValues[1]} ${match.groupValues[2]}"
            val sdf = cache.getOrPut(pattern) {
                SimpleDateFormat(pattern, Locale.ENGLISH).apply {
                    isLenient = false
                    timeZone = TimeZone.getDefault()
                }
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
        val normalizedBody = body.lowercase(Locale.ROOT).replace(WHITESPACE_REGEX, " ").trim()
        val normalizedSender = sender.lowercase(Locale.ROOT).trim()
        val input = "$normalizedSender|$normalizedBody|$minuteBucket"
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        val chars = CharArray(32)
        for (i in 0 until 16) {
            val b = digest[i].toInt() and 0xFF
            chars[i * 2] = HEX_CHARS[b ushr 4]
            chars[i * 2 + 1] = HEX_CHARS[b and 0x0F]
        }
        return String(chars)
    }
}
