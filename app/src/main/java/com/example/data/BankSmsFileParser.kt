package com.example.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

data class SmsFileImportResult(
    val totalRecordsInspected: Int,
    val parsedTransactions: List<FinanceTransaction>,
    val formatLabel: String,
    val errorMessage: String? = null
)

object BankSmsFileParser {

    private val SMS_TAG_REGEX = Regex("""<sms\b([^>]*)/?>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val XML_ATTR_REGEX = Regex("""(\w+)\s*=\s*"([^"]*)"""")

    suspend fun parseUri(context: Context, uri: Uri): SmsFileImportResult = withContext(Dispatchers.IO) {
        try {
            val displayName = resolveDisplayName(context, uri)
            val mime = context.contentResolver.getType(uri).orEmpty()
            val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            }.orEmpty()

            if (content.isBlank()) {
                return@withContext SmsFileImportResult(
                    totalRecordsInspected = 0,
                    parsedTransactions = emptyList(),
                    formatLabel = displayName.ifBlank { "Empty File" },
                    errorMessage = "The selected file is empty or unreadable."
                )
            }
            parseContent(
                content = content,
                fileNameOrMime = "$displayName $mime",
                source = "BACKUP_FILE"
            )
        } catch (e: Exception) {
            SmsFileImportResult(
                totalRecordsInspected = 0,
                parsedTransactions = emptyList(),
                formatLabel = "File",
                errorMessage = e.localizedMessage ?: "Failed to read the selected SMS backup file."
            )
        }
    }

    fun parseContent(
        content: String,
        fileNameOrMime: String = "",
        source: String = "BACKUP_FILE"
    ): SmsFileImportResult {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) {
            return SmsFileImportResult(0, emptyList(), "Empty")
        }

        val hint = fileNameOrMime.lowercase(Locale.ENGLISH)
        return when {
            hint.contains(".xml") || hint.contains("xml") || trimmed.startsWith("<smses", ignoreCase = true) || trimmed.contains("<sms ", ignoreCase = true) -> {
                parseXmlBackup(trimmed, source)
            }
            hint.contains(".csv") || hint.contains("csv") || looksLikeCsv(trimmed) -> {
                parseCsvBackup(trimmed, source)
            }
            else -> {
                val parsed = BankSmsParser.parseBatchRawText(trimmed, defaultSender = "Backup Import")
                    .map { it.copy(source = source) }
                val lineBlocks = trimmed.split(Regex("""\n{2,}|\r\n{2,}""")).count { it.isNotBlank() }.coerceAtLeast(1)
                SmsFileImportResult(
                    totalRecordsInspected = lineBlocks,
                    parsedTransactions = parsed,
                    formatLabel = "Text"
                )
            }
        }
    }

    private fun parseXmlBackup(xml: String, source: String): SmsFileImportResult {
        val matches = SMS_TAG_REGEX.findAll(xml).toList()
        val parsed = mutableListOf<FinanceTransaction>()
        matches.forEach { tagMatch ->
            val attrsRaw = tagMatch.groupValues[1]
            val attrs = mutableMapOf<String, String>()
            XML_ATTR_REGEX.findAll(attrsRaw).forEach { attr ->
                attrs[attr.groupValues[1].lowercase(Locale.ENGLISH)] = unescapeXml(attr.groupValues[2])
            }
            val body = attrs["body"].orEmpty()
            val address = attrs["address"].orEmpty().ifBlank { attrs["sender"].orEmpty() }
            val dateMs = parseFlexibleTimestamp(attrs["date"]) ?: System.currentTimeMillis()
            if (body.isNotBlank()) {
                val tx = BankSmsParser.parseSms(
                    sender = address,
                    body = body,
                    receivedTimestampMs = dateMs,
                    source = source
                )
                if (tx != null) parsed.add(tx)
            }
        }
        return SmsFileImportResult(
            totalRecordsInspected = matches.size,
            parsedTransactions = parsed,
            formatLabel = "XML Backup"
        )
    }

    private fun parseCsvBackup(csv: String, source: String): SmsFileImportResult {
        val rows = parseCsvRows(csv)
        if (rows.isEmpty()) return SmsFileImportResult(0, emptyList(), "CSV")

        val header = rows.first().map { it.trim().lowercase(Locale.ENGLISH) }
        val hasHeader = header.any {
            it in setOf("body", "message", "sms", "text", "content", "address", "sender", "from", "date", "timestamp")
        }

        val senderIdx = header.indexOfFirst { it in setOf("address", "sender", "from", "number", "bank") }
        val bodyIdx = header.indexOfFirst { it in setOf("body", "message", "sms", "text", "content", "details") }
        val dateIdx = header.indexOfFirst { it in setOf("date", "timestamp", "time", "received_at") }

        val dataRows = if (hasHeader) rows.drop(1) else rows
        val parsed = mutableListOf<FinanceTransaction>()

        dataRows.forEach { cols ->
            if (cols.all { it.isBlank() }) return@forEach
            val body = when {
                bodyIdx >= 0 && bodyIdx < cols.size -> cols[bodyIdx]
                else -> cols.maxByOrNull { it.length }.orEmpty()
            }
            val sender = if (senderIdx >= 0 && senderIdx < cols.size) cols[senderIdx] else ""
            val dateMs = if (dateIdx >= 0 && dateIdx < cols.size) {
                parseFlexibleTimestamp(cols[dateIdx]) ?: System.currentTimeMillis()
            } else {
                System.currentTimeMillis()
            }
            if (body.isNotBlank()) {
                val tx = BankSmsParser.parseSms(
                    sender = sender,
                    body = body,
                    receivedTimestampMs = dateMs,
                    source = source
                )
                if (tx != null) parsed.add(tx)
            }
        }

        return SmsFileImportResult(
            totalRecordsInspected = dataRows.size,
            parsedTransactions = parsed,
            formatLabel = "CSV"
        )
    }

    private fun looksLikeCsv(text: String): Boolean {
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() }?.lowercase(Locale.ENGLISH) ?: return false
        return firstLine.contains(",") && (
            firstLine.contains("body") ||
                firstLine.contains("message") ||
                firstLine.contains("address") ||
                firstLine.contains("sender") ||
                firstLine.contains("sms")
            )
    }

    private fun parseCsvRows(input: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < input.length) {
            val ch = input[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < input.length && input[i + 1] == '"') {
                        cell.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == ',' && !inQuotes -> {
                    currentRow.add(cell.toString())
                    cell.setLength(0)
                }
                (ch == '\n' || ch == '\r') && !inQuotes -> {
                    if (ch == '\r' && i + 1 < input.length && input[i + 1] == '\n') {
                        i++
                    }
                    currentRow.add(cell.toString())
                    cell.setLength(0)
                    if (currentRow.any { it.isNotBlank() }) {
                        rows.add(currentRow)
                    }
                    currentRow = mutableListOf()
                }
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(cell.toString())
            if (currentRow.any { it.isNotBlank() }) {
                rows.add(currentRow)
            }
        }
        return rows
    }

    private fun parseFlexibleTimestamp(raw: String?): Long? {
        val s = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        s.toLongOrNull()?.let { numeric ->
            return if (numeric in 1_000_000_000L..9_999_999_999L) numeric * 1000L else numeric
        }
        val formats = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "dd/MM/yyyy HH:mm:ss",
            "dd/MM/yyyy HH:mm",
            "MM/dd/yyyy HH:mm:ss"
        )
        for (pattern in formats) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.ENGLISH).apply { isLenient = false }.parse(s)?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }

    private fun unescapeXml(value: String): String {
        var out = value
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#10;", "\n")
            .replace("&#xA;", "\n", ignoreCase = true)
            .replace("&#13;", "\r")
        out = Regex("""&#(\d+);""").replace(out) { m ->
            m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: m.value
        }
        out = Regex("""&#x([0-9A-Fa-f]+);""").replace(out) { m ->
            m.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: m.value
        }
        return out
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
            }
        }.getOrNull().orEmpty().ifBlank { uri.lastPathSegment.orEmpty() }
    }
}
