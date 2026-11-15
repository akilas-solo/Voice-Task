package com.example.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class SmsScanResult(
    val totalMessagesScanned: Int,
    val parsedTransactions: List<FinanceTransaction>,
    val permissionGranted: Boolean,
    val errorMessage: String? = null
)

object SmsInboxReader {

    fun hasReadSmsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun scanInbox(
        context: Context,
        sinceTimestampMs: Long? = null,
        maxMessagesToInspect: Int = 1500
    ): SmsScanResult = withContext(Dispatchers.IO) {
        if (!hasReadSmsPermission(context)) {
            return@withContext SmsScanResult(
                totalMessagesScanned = 0,
                parsedTransactions = emptyList(),
                permissionGranted = false,
                errorMessage = "SMS permission is required to read bank alerts automatically."
            )
        }

        val parsed = LinkedHashMap<String, FinanceTransaction>()
        var scannedCount = 0

        try {
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE
            )
            val selection = if (sinceTimestampMs != null) "${Telephony.Sms.DATE} >= ?" else null
            val selectionArgs = if (sinceTimestampMs != null) arrayOf(sinceTimestampMs.toString()) else null

            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)

                while (cursor.moveToNext() && scannedCount < maxMessagesToInspect) {
                    if (scannedCount % 64 == 0) {
                        coroutineContext.ensureActive()
                    }
                    scannedCount++
                    val sender = if (addressIdx >= 0) cursor.getString(addressIdx).orEmpty() else ""
                    val body = if (bodyIdx >= 0) cursor.getString(bodyIdx).orEmpty() else ""
                    if (body.length < 12) continue
                    val dateMs = if (dateIdx >= 0) cursor.getLong(dateIdx) else System.currentTimeMillis()

                    val tx = BankSmsParser.parseSms(
                        sender = sender,
                        body = body,
                        receivedTimestampMs = dateMs,
                        source = "SMS_INBOX"
                    )
                    if (tx != null) {
                        parsed.putIfAbsent(tx.smsFingerprint, tx)
                    }
                }
            }

            SmsScanResult(
                totalMessagesScanned = scannedCount,
                parsedTransactions = parsed.values.toList(),
                permissionGranted = true
            )
        } catch (se: SecurityException) {
            SmsScanResult(
                totalMessagesScanned = scannedCount,
                parsedTransactions = emptyList(),
                permissionGranted = false,
                errorMessage = "SMS permission was denied by the system."
            )
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            SmsScanResult(
                totalMessagesScanned = scannedCount,
                parsedTransactions = parsed.values.toList(),
                permissionGranted = true,
                errorMessage = e.localizedMessage ?: "Failed to read SMS inbox."
            )
        }
    }
}
