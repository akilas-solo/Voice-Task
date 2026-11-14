package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class TransactionType(val label: String) {
    INCOME("Income"),
    EXPENSE("Expense"),
    TRANSFER("Transfer");

    companion object {
        fun fromString(raw: String): TransactionType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: EXPENSE
    }
}

enum class BankInstitution(
    val code: String,
    val displayName: String,
    val shortBadge: String
) {
    TELEBIRR("TELEBIRR", "Telebirr", "Telebirr"),
    CBE("CBE", "CBE", "CBE"),
    BOA("BOA", "Bank of Abyssinia", "Abyssinia"),
    CBE_BIRR("CBE_BIRR", "CBE Birr", "CBE Birr"),
    AWASH("AWASH", "Awash Bank", "Awash"),
    DASHEN("DASHEN", "Dashen Bank", "Dashen"),
    NIB("NIB", "Nib Bank", "Nib"),
    COOP("COOP", "Coop Bank", "Coop"),
    HIBRET("HIBRET", "Hibret Bank", "Hibret"),
    ZEMEN("ZEMEN", "Zemen Bank", "Zemen"),
    MPESA("MPESA", "M-Pesa", "M-Pesa"),
    OTHER("OTHER", "Other Bank", "Bank");

    companion object {
        fun fromCode(code: String): BankInstitution =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: OTHER
    }
}

enum class FinancePeriodMode(val label: String) {
    ALL("All"),
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    THIS_WEEK("Week"),
    SPECIFIC_DAY("Pick Day"),
    SPECIFIC_MONTH("Pick Month")
}

enum class TimeOfDayPreset(
    val label: String,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int
) {
    ANY("Any Time", 0, 0, 23, 59),
    MORNING("Morning (6am–12pm)", 6, 0, 11, 59),
    AFTERNOON("Afternoon (12pm–5pm)", 12, 0, 16, 59),
    EVENING("Evening (5pm–9pm)", 17, 0, 20, 59),
    NIGHT("Night (9pm–6am)", 21, 0, 5, 59),
    CUSTOM("Custom Time", 8, 0, 18, 0)
}

data class FinanceFilterState(
    val periodMode: FinancePeriodMode = FinancePeriodMode.ALL,
    val selectedDayMillis: Long? = null,
    val selectedYear: Int? = null,
    val selectedMonthZeroBased: Int? = null, // 0..11
    val timePreset: TimeOfDayPreset = TimeOfDayPreset.ANY,
    val customStartHour: Int = 8,
    val customStartMinute: Int = 0,
    val customEndHour: Int = 20,
    val customEndMinute: Int = 0,
    val selectedBankCode: String = "ALL",
    val searchQuery: String = "",
    val typeFilter: String = "ALL" // "ALL", "INCOME", "EXPENSE", "TRANSFER"
)

data class FinanceChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val matchedPeriodLabel: String = "",
    val matchedCount: Int = 0,
    val matchedIncome: Double = 0.0,
    val matchedOutflow: Double = 0.0
)

@Entity(
    tableName = "finance_transactions",
    indices = [
        Index(value = ["smsFingerprint"], unique = true),
        Index(value = ["timestamp"])
    ]
)
data class FinanceTransaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val smsFingerprint: String,
    val bankName: String,
    val bankCode: String,
    val type: String, // TransactionType.name
    val amount: Double,
    val currency: String = "ETB",
    val balanceAfter: Double? = null,
    val serviceCharge: Double? = null,
    val counterparty: String = "",
    val referenceId: String = "",
    val category: String = "Other",
    val aiSummary: String = "",
    val rawSender: String = "",
    val rawSmsBody: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val source: String = "SMS_INBOX", // "SMS_INBOX", "MANUAL_PASTE", "MANUAL_ENTRY"
    val aiAnalyzed: Boolean = false
) {
    val transactionType: TransactionType
        get() = TransactionType.fromString(type)

    val isIncoming: Boolean
        get() = transactionType == TransactionType.INCOME

    val isOutgoing: Boolean
        get() = transactionType == TransactionType.EXPENSE || transactionType == TransactionType.TRANSFER
}
