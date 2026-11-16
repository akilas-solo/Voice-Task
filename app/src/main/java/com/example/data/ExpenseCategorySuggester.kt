package com.example.data

import java.util.Locale

data class CategorySuggestionResult(
    val category: String,
    val matchedKeyword: String? = null,
    val reason: String,
    val alternatives: List<String> = emptyList(),
    val confidence: Float = 0.7f
)

object ExpenseCategorySuggester {

    val FIN_CATEGORIES = listOf(
        "Groceries",
        "Rent & Housing",
        "Utilities",
        "Food & Dining",
        "Transport & Fuel",
        "Health & Medical",
        "Airtime & Data",
        "Merchant & Shopping",
        "Transfers",
        "ATM & Cash",
        "Bank Charges",
        "Salary & Income",
        "Other"
    )

    private data class KeywordRule(
        val category: String,
        val regex: Regex,
        val confidence: Float
    )

    private val RENT_REGEX = Regex(
        """\b(rent|rental|lease|landlord|tenant|tenancy|apartment|condo|condominium|housing|mortgage|house\s+rent|monthly\s+rent|room\s+rent|office\s+rent|kiray|real\s+estate)\b""",
        RegexOption.IGNORE_CASE
    )

    private val GROCERIES_REGEX = Regex(
        """\b(grocer(?:y|ies)|supermarket|hypermarket|mini[\s-]?market|minimart|produce|vegetables|fruits|butcher|bakery|dairy|shoa|queens\s+supermarket|fantu|bambis|safeway|walmart|aldi|costco|carrefour|whole\s+foods|kroger|tesco|fresh\s+corner)\b""",
        RegexOption.IGNORE_CASE
    )

    private val UTILITIES_REGEX = Regex(
        """\b(utilit(?:y|ies)|utility\s+bill|electric(?:ity)?(?:\s+bill)?|eeu|power\s+bill|water(?:\s+bill|\s+supply)?|aawsa|sewer|trash|garbage|waste|gas\s+bill|heating|broadband|wi[\s-]?fi|fiber|internet\s+bill|dstv|cable(?:\s+tv)?|canal\+)\b""",
        RegexOption.IGNORE_CASE
    )

    private val FOOD_DINING_REGEX = Regex(
        """\b(restaurant|cafe|coffee|dining|dinner|lunch|breakfast|pizza|burger|bistro|eatery|kitchen|food\s+delivery|takeout|takeaway|food|tomoca|kaldi(?:'s)?|beu|deliver\s+addis|uber\s+eats|doordash|starbucks)\b""",
        RegexOption.IGNORE_CASE
    )

    private val TRANSPORT_FUEL_REGEX = Regex(
        """\b(fuel|petrol|diesel|gas\s+station|totalenergies|oilibya|noc|uber|lyft|feres|ride|yango|taxi|bajaj|bus\s+ticket|train|metro|parking|toll|airline|flight|ethiopian\s+airlines|car\s+wash)\b""",
        RegexOption.IGNORE_CASE
    )

    private val HEALTH_MEDICAL_REGEX = Regex(
        """\b(pharmacy|chemist|drugstore|clinic|hospital|medical|doctor|dentist|medicine|prescription|kenema|gishen|laboratory|lab\s+test|healthcare|health\s+care|optometry)\b""",
        RegexOption.IGNORE_CASE
    )

    private val AIRTIME_REGEX = Regex(
        """\b(airtime|recharg(?:e|ed)|top[\s-]?up|topup|mobile\s+(?:package|bundle|data)|voice\s+package|data\s+package|internet\s+package|ethio\s+telecom\s+(?:airtime|package|bill|recharge)|safaricom)\b""",
        RegexOption.IGNORE_CASE
    )

    private val ATM_REGEX = Regex(
        """\b(atm|withdrawn|withdrawal|cash[\s-]?out|branch\s+withdrawal)\b""",
        RegexOption.IGNORE_CASE
    )

    private val BANK_CHARGES_REGEX = Regex(
        """\b(service\s+charge|bank\s+charge|maintenance\s+fee|commission\s+fee|sms\s+fee|monthly\s+fee)\b""",
        RegexOption.IGNORE_CASE
    )

    private val SALARY_INCOME_REGEX = Regex(
        """\b(salary|payroll|paycheck|wage|bonus|cashback|dividend|refund(?:ed)?|reimbursement)\b""",
        RegexOption.IGNORE_CASE
    )

    private val MERCHANT_REGEX = Regex(
        """\b(merchant|pos|shop|store|market|mall|boutique|retail|electronics|hardware|clothing|shoes|amazon|paid\s+to|you\s+paid|have\s+paid)\b""",
        RegexOption.IGNORE_CASE
    )

    private val TRANSFER_REGEX = Regex(
        """\b(transfer(?:red|ed)?|sent\s+to|p2p|inward\s+transfer|fund\s+transfer)\b""",
        RegexOption.IGNORE_CASE
    )

    // Ordered from highest-specificity expense keywords down to broader categories
    private val SPECIFIC_EXPENSE_RULES = listOf(
        KeywordRule("Rent & Housing", RENT_REGEX, 0.96f),
        KeywordRule("Groceries", GROCERIES_REGEX, 0.95f),
        KeywordRule("Utilities", UTILITIES_REGEX, 0.94f),
        KeywordRule("Airtime & Data", AIRTIME_REGEX, 0.93f),
        KeywordRule("ATM & Cash", ATM_REGEX, 0.92f),
        KeywordRule("Health & Medical", HEALTH_MEDICAL_REGEX, 0.91f),
        KeywordRule("Transport & Fuel", TRANSPORT_FUEL_REGEX, 0.90f),
        KeywordRule("Food & Dining", FOOD_DINING_REGEX, 0.88f),
        KeywordRule("Bank Charges", BANK_CHARGES_REGEX, 0.88f)
    )

    private val GENERIC_COUNTERPARTY_STOPWORDS = setOf(
        "",
        "other",
        "other bank",
        "telebirr",
        "cbe",
        "cbe birr",
        "bank of abyssinia",
        "awash bank",
        "dashen bank",
        "manual entry",
        "manual sms",
        "clipboard sms",
        "atm withdrawal"
    )

    private val WHITESPACE_REGEX = Regex("""\s+""")

    fun normalizeCounterpartyKey(counterparty: String): String {
        val cleaned = counterparty
            .lowercase(Locale.ROOT)
            .replace(WHITESPACE_REGEX, " ")
            .trim()
        if (cleaned.length < 3 || cleaned in GENERIC_COUNTERPARTY_STOPWORDS) return ""
        return cleaned
    }

    fun suggestCategory(
        semanticBody: String,
        counterparty: String = "",
        type: TransactionType = TransactionType.EXPENSE,
        learnedRules: Map<String, String> = emptyMap()
    ): CategorySuggestionResult {
        val combined = "$semanticBody $counterparty".trim()
        val counterpartyKey = normalizeCounterpartyKey(counterparty)
        val learnedCat = counterpartyKey.takeIf { it.isNotEmpty() }?.let { learnedRules[it] }
            ?.takeIf { it in FIN_CATEGORIES }

        val incomeKeywordMatch = SALARY_INCOME_REGEX.find(combined)
        if (type == TransactionType.INCOME || incomeKeywordMatch != null) {
            val primary = learnedCat ?: "Salary & Income"
            val matchedKw = incomeKeywordMatch?.value?.lowercase(Locale.ROOT)
            val reason = when {
                learnedCat != null -> "Learned rule for '$counterparty'"
                matchedKw != null -> "Matched keyword '$matchedKw'"
                else -> "Incoming credit transaction"
            }
            val alts = listOf("Salary & Income", "Transfers", "Other")
                .filter { it != primary }
                .distinct()
                .take(3)
            return CategorySuggestionResult(
                category = primary,
                matchedKeyword = matchedKw,
                reason = reason,
                alternatives = alts,
                confidence = if (matchedKw != null || learnedCat != null) 0.94f else 0.80f
            )
        }

        // Gather all matching specific expense rules so we keep the strongest primary match + smart alternatives
        val specificMatches = SPECIFIC_EXPENSE_RULES.mapNotNull { rule ->
            rule.regex.find(combined)?.let { m -> Triple(rule, m.value.lowercase(Locale.ROOT), m.range.first) }
        }

        if (specificMatches.isNotEmpty()) {
            val best = specificMatches.first()
            val primaryCategory = best.first.category
            val matchedKw = best.second
            val secondaryCats = specificMatches.drop(1).map { it.first.category }
            val fallbackAlts = when (type) {
                TransactionType.TRANSFER -> listOf("Transfers", "Merchant & Shopping", "Other")
                else -> listOf("Merchant & Shopping", "Transfers", "Other")
            }
            val alts = (listOfNotNull(learnedCat) + secondaryCats + fallbackAlts)
                .filter { it != primaryCategory }
                .distinct()
                .take(3)
            return CategorySuggestionResult(
                category = primaryCategory,
                matchedKeyword = matchedKw,
                reason = "Matched keyword '$matchedKw'",
                alternatives = alts,
                confidence = best.first.confidence
            )
        }

        if (learnedCat != null) {
            val alts = listOf(
                if (type == TransactionType.TRANSFER) "Transfers" else "Merchant & Shopping",
                "Groceries",
                "Utilities"
            ).filter { it != learnedCat }.distinct().take(3)
            return CategorySuggestionResult(
                category = learnedCat,
                matchedKeyword = counterpartyKey,
                reason = "Learned from past '$counterparty' transactions",
                alternatives = alts,
                confidence = 0.89f
            )
        }

        val transferMatch = TRANSFER_REGEX.find(combined)
        val merchantMatch = MERCHANT_REGEX.find(combined)

        return when {
            type == TransactionType.TRANSFER || transferMatch != null -> {
                val kw = transferMatch?.value?.lowercase(Locale.ROOT)
                CategorySuggestionResult(
                    category = "Transfers",
                    matchedKeyword = kw,
                    reason = if (kw != null) "Matched keyword '$kw'" else "Person-to-person transfer",
                    alternatives = listOf("Rent & Housing", "Groceries", "Utilities"),
                    confidence = 0.78f
                )
            }
            merchantMatch != null -> {
                val kw = merchantMatch.value.lowercase(Locale.ROOT)
                CategorySuggestionResult(
                    category = "Merchant & Shopping",
                    matchedKeyword = kw,
                    reason = "Matched keyword '$kw'",
                    alternatives = listOf("Groceries", "Food & Dining", "Utilities"),
                    confidence = 0.74f
                )
            }
            else -> CategorySuggestionResult(
                category = "Other",
                matchedKeyword = null,
                reason = "No specific expense keyword detected",
                alternatives = listOf("Groceries", "Rent & Housing", "Utilities"),
                confidence = 0.45f
            )
        }
    }
}
