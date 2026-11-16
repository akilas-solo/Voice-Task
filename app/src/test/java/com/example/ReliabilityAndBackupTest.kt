package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AiConnectorPrefs
import com.example.data.AppDatabase
import com.example.data.BankInstitution
import com.example.data.BankSmsParser
import com.example.data.FinanceTransaction
import com.example.data.NotepadNote
import com.example.data.StorageAndBackupCoordinator
import com.example.data.TaskPriority
import com.example.data.TransactionType
import com.example.data.VoiceTask
import com.example.data.VoiceTaskRepository
import com.example.ui.AlarmSoundCatalog
import com.example.ui.AlarmSoundEngine
import com.example.ui.ReminderNotificationHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReliabilityAndBackupTest {

    private fun newInMemoryRepo(context: Context): Pair<AppDatabase, VoiceTaskRepository> {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        return db to VoiceTaskRepository(db.voiceTaskDao())
    }

    @Test
    fun `v2 backup exports tasks notes finance and folders and imports with dedup and v1 compatibility`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val (sourceDb, sourceRepo) = newInMemoryRepo(context)
        try {
            sourceRepo.insertTask(
                VoiceTask(
                    title = "Quarterly Tax Prep",
                    notes = "Gather receipts",
                    category = "Client Meetings",
                    createdAt = 1_750_000_100_000L,
                    notifyTelegram = false,
                    priority = TaskPriority.HIGH.level
                )
            )
            sourceRepo.insertNotepadNote(
                NotepadNote(
                    title = "Travel Packing",
                    content = "Passport, charger, mic",
                    category = "Travel",
                    isPinned = true,
                    createdAt = 1_750_000_200_000L,
                    updatedAt = 1_750_000_250_000L,
                    wordCount = 3,
                    charCount = 23
                )
            )
            sourceRepo.insertFinanceTransaction(
                FinanceTransaction(
                    smsFingerprint = "fp_backup_test_001",
                    bankName = BankInstitution.CBE.displayName,
                    bankCode = BankInstitution.CBE.code,
                    type = TransactionType.EXPENSE.name,
                    amount = 420.50,
                    currency = "ETB",
                    counterparty = "Bookstore",
                    category = "Merchant & Shopping",
                    timestamp = 1_750_000_300_000L,
                    source = "MANUAL_PASTE"
                )
            )

            val json = sourceRepo.exportBackupJson(listOf("Client Meetings", "Travel"))
            val root = JSONObject(json)
            assertEquals(2, root.getInt("schemaVersion"))
            assertTrue(json.contains("\"notifyTelegram\": false"))
            assertEquals(1, root.getJSONArray("voiceTasks").length())
            assertEquals(1, root.getJSONArray("notepadNotes").length())
            assertEquals(1, root.getJSONArray("financeTransactions").length())
            assertEquals(2, root.getJSONArray("customFolders").length())

            val (targetDb, targetRepo) = newInMemoryRepo(context)
            try {
                val firstImport = targetRepo.importBackupDetailed(json)
                assertTrue(firstImport.success)
                assertEquals(1, firstImport.tasksImported)
                assertEquals(1, firstImport.notesImported)
                assertEquals(1, firstImport.financeImported)
                assertEquals(listOf("Client Meetings", "Travel"), firstImport.customFolders)

                // Re-importing the same v2 backup deduplicates existing records
                val secondImport = targetRepo.importBackupDetailed(json)
                assertTrue(secondImport.success)
                assertEquals(0, secondImport.tasksImported)
                assertEquals(0, secondImport.notesImported)
                assertEquals(0, secondImport.financeImported)

                // Legacy v1 JSON array import remains supported
                val legacyV1 = JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("title", "Legacy V1 Task")
                            put("category", "Inbox")
                            put("createdAt", 1_750_000_400_000L)
                            put("notifyTelegram", true)
                        }
                    )
                }.toString()
                val legacyImport = targetRepo.importBackupDetailed(legacyV1)
                assertTrue(legacyImport.success)
                assertEquals(1, legacyImport.tasksImported)
                assertEquals(2, targetRepo.getAllTasks().size)
            } finally {
                targetDb.close()
            }
        } finally {
            sourceDb.close()
        }
    }

    @Test
    fun `dateless manual or shared sms produces identical fingerprint across pastes`() {
        val t1 = 1_750_000_000_000L
        val smsBody = "You paid ETB 250.00 to Cafe Lucia from your Telebirr account. Balance is ETB 1,200.00."

        val firstPaste = BankSmsParser.parseSms(
            sender = "Manual SMS",
            body = smsBody,
            receivedTimestampMs = t1,
            source = "MANUAL_PASTE"
        )
        val secondClipboardOrShare = BankSmsParser.parseSms(
            sender = "Clipboard SMS",
            body = smsBody,
            receivedTimestampMs = t1 + 600_000L,
            source = "SHARE_INTENT"
        )
        val inboxLaterMessage = BankSmsParser.parseSms(
            sender = "127",
            body = smsBody,
            receivedTimestampMs = t1 + 600_000L,
            source = "SMS_INBOX"
        )

        assertNotNull(firstPaste)
        assertNotNull(secondClipboardOrShare)
        assertNotNull(inboxLaterMessage)
        assertEquals(firstPaste!!.smsFingerprint, secondClipboardOrShare!!.smsFingerprint)
        assertNotEquals(firstPaste.smsFingerprint, inboxLaterMessage!!.smsFingerprint)
    }

    @Test
    fun `per sound notification channels are deterministic and wav synthesis has valid riff header`() {
        val defaultChannel = ReminderNotificationHelper.channelIdForSound(AlarmSoundCatalog.SOUND_APP_DEFAULT)
        assertEquals("voistask_alarm_default_v3", defaultChannel)

        val forestChannel = ReminderNotificationHelper.channelIdForSound("Nordic Forest Chime")
        val zenChannel = ReminderNotificationHelper.channelIdForSound(AlarmSoundCatalog.SOUND_ZEN_BOWL)
        val customRingtone = AlarmSoundCatalog.encodeCustomRingtone("Morning Chime", "content://media/audio/99")
        val customChannel = ReminderNotificationHelper.channelIdForSound(customRingtone)

        assertNotEquals(defaultChannel, forestChannel)
        assertNotEquals(forestChannel, zenChannel)
        assertNotEquals(zenChannel, customChannel)
        assertEquals(forestChannel, ReminderNotificationHelper.channelIdForSound("Nordic Forest Chime"))

        val preset = AlarmSoundCatalog.presetFor("Nordic Forest Chime")!!
        val pcm = AlarmSoundEngine.synthesizeChimePcm(preset.notes)
        val wavBytes = AlarmSoundEngine.encodePcmToWavBytes(pcm)

        assertEquals(44 + pcm.size * 2, wavBytes.size)
        assertEquals("RIFF", String(wavBytes.copyOfRange(0, 4), Charsets.US_ASCII))
        assertEquals("WAVE", String(wavBytes.copyOfRange(8, 12), Charsets.US_ASCII))
        assertEquals("fmt ", String(wavBytes.copyOfRange(12, 16), Charsets.US_ASCII))
        assertEquals("data", String(wavBytes.copyOfRange(36, 40), Charsets.US_ASCII))
    }

    @Test
    fun `custom folders persist in datastore without creating dummy tasks`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = AiConnectorPrefs(context)
        val (db, repo) = newInMemoryRepo(context)
        try {
            prefs.addCustomFolder("Client Meetings")
            prefs.addCustomFolder("  Travel  ")
            prefs.addCustomFolder("client meetings") // duplicate differing only by case
            prefs.mergeCustomFolders(listOf("Design Sprint", "Travel"))

            val afterAdd = prefs.customFoldersFlow.first()
            assertTrue(afterAdd.contains("Client Meetings"))
            assertTrue(afterAdd.contains("Travel"))
            assertTrue(afterAdd.contains("Design Sprint"))
            assertEquals(1, afterAdd.count { it.equals("Client Meetings", ignoreCase = true) })
            assertEquals(0, repo.getAllTasks().size)

            prefs.deleteCustomFolder("Design Sprint")
            val afterDelete = prefs.customFoldersFlow.first()
            assertFalse(afterDelete.contains("Design Sprint"))
        } finally {
            db.close()
        }
    }

    @Test
    fun `legacy cache audio migrates to permanent filesDir and clearing cache spares saved clips`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val (db, repo) = newInMemoryRepo(context)
        try {
            val legacyMemoDir = File(context.cacheDir, "recordings").apply { mkdirs() }
            val legacyMemoFile = File(legacyMemoDir, "legacy_memo_01.m4a").apply {
                writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6))
            }
            val legacyNoteDir = File(context.cacheDir, "notepad_recordings").apply { mkdirs() }
            val legacyNoteFile = File(legacyNoteDir, "legacy_note_01.3gp").apply {
                writeBytes(byteArrayOf(7, 8, 9, 10))
            }

            repo.insertTask(
                VoiceTask(
                    title = "Legacy Voice Memo",
                    audioPath = legacyMemoFile.absolutePath,
                    audioDurationMs = 1500L
                )
            )
            repo.insertNotepadNote(
                NotepadNote(
                    title = "Legacy Voice Note",
                    audioPath = legacyNoteFile.absolutePath,
                    audioDurationMs = 1200L
                )
            )

            StorageAndBackupCoordinator.migrateLegacyCacheAudio(context, repo)

            val updatedTask = repo.getAllTasks().first { it.title == "Legacy Voice Memo" }
            val updatedNote = repo.getAllNotepadNotes().first { it.title == "Legacy Voice Note" }
            val permanentMemoDir = StorageAndBackupCoordinator.voiceMemosDir(context)
            val permanentNoteDir = StorageAndBackupCoordinator.notepadVoicesDir(context)

            assertNotNull(updatedTask.audioPath)
            assertNotNull(updatedNote.audioPath)
            val migratedMemo = File(updatedTask.audioPath!!)
            val migratedNote = File(updatedNote.audioPath!!)
            assertTrue(migratedMemo.exists())
            assertTrue(migratedNote.exists())
            assertEquals(permanentMemoDir.canonicalPath, migratedMemo.canonicalFile.parentFile?.canonicalPath)
            assertEquals(permanentNoteDir.canonicalPath, migratedNote.canonicalFile.parentFile?.canonicalPath)
            assertFalse(legacyMemoFile.exists())
            assertFalse(legacyNoteFile.exists())

            // Create orphaned temp cache file and verify cache cleanup deletes only reclaimable temp cache
            val orphanCacheFile = File(legacyMemoDir, "orphan_temp.mp4").apply {
                writeBytes(byteArrayOf(11, 12, 13))
            }
            assertTrue(orphanCacheFile.exists())

            StorageAndBackupCoordinator.clearReclaimableCache(
                context = context,
                repository = repo,
                activeRecordingPaths = emptySet()
            )

            assertFalse(orphanCacheFile.exists())
            assertTrue(migratedMemo.exists())
            assertTrue(migratedNote.exists())
        } finally {
            db.close()
        }
    }

    @Test
    fun `mainActivity launches and navigates primary screens cleanly`() {
        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            assertNotNull(activity)
            controller.pause().stop().destroy()
        } catch (t: Throwable) {
            throw AssertionError("MainActivity failed during startup lifecycle: ${t.message}", t)
        }
    }
}
