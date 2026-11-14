package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AiConnectorPrefs
import com.example.data.AppDatabase
import com.example.data.TaskPriority
import com.example.data.VoiceTask
import com.example.data.VoiceTaskRepository
import com.example.ui.AlarmSoundCatalog
import com.example.ui.AlarmSoundCategory
import com.example.ui.AlarmSoundEngine
import com.example.ui.ReminderDateTimeHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReminderAndAuditTest {

    @Test
    fun `utc date and local time combination avoids off-by-one timezone drift`() {
        val laTz = TimeZone.getTimeZone("America/Los_Angeles")
        val tokyoTz = TimeZone.getTimeZone("Asia/Tokyo")

        val utcMidnight = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.MARCH)
            set(Calendar.DAY_OF_MONTH, 15)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
        }.timeInMillis

        val laCombined = ReminderDateTimeHelper.combineUtcDateWithLocalTime(utcMidnight, 21, 45, laTz)
        val laCal = Calendar.getInstance(laTz).apply { timeInMillis = laCombined }
        assertEquals(2026, laCal.get(Calendar.YEAR))
        assertEquals(Calendar.MARCH, laCal.get(Calendar.MONTH))
        assertEquals(15, laCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(21, laCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(45, laCal.get(Calendar.MINUTE))

        val roundtripUtc = ReminderDateTimeHelper.extractLocalDateAsUtcMidnight(laCombined, laTz)
        assertEquals(utcMidnight, roundtripUtc)
        assertEquals(21 to 45, ReminderDateTimeHelper.extractLocalHourMinute(laCombined, laTz))

        val tokyoCombined = ReminderDateTimeHelper.combineUtcDateWithLocalTime(utcMidnight, 6, 15, tokyoTz)
        val tokyoCal = Calendar.getInstance(tokyoTz).apply { timeInMillis = tokyoCombined }
        assertEquals(2026, tokyoCal.get(Calendar.YEAR))
        assertEquals(Calendar.MARCH, tokyoCal.get(Calendar.MONTH))
        assertEquals(15, tokyoCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(6, tokyoCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(15, tokyoCal.get(Calendar.MINUTE))
    }

    @Test
    fun `reminder validation snooze math and relative formatting behave accurately`() {
        val now = 1_750_000_000_000L
        assertFalse(ReminderDateTimeHelper.isFutureReminder(now - 10_000L, now))
        assertFalse(ReminderDateTimeHelper.isFutureReminder(now + 2_000L, now))
        assertTrue(ReminderDateTimeHelper.isFutureReminder(now + 30_000L, now))

        val snoozed10 = ReminderDateTimeHelper.computeSnoozeTimestamp(10, now)
        assertEquals(now + 600_000L, snoozed10)
        assertEquals(now + 60_000L, ReminderDateTimeHelper.computeSnoozeTimestamp(0, now))

        assertEquals("Past time", ReminderDateTimeHelper.formatRelativeRemaining(now - 5_000L, now))
        assertEquals("in 15m", ReminderDateTimeHelper.formatRelativeRemaining(now + 15L * 60_000L, now))
        assertEquals("in 2h 30m", ReminderDateTimeHelper.formatRelativeRemaining(now + 150L * 60_000L, now))
        assertEquals("in 1d 3h", ReminderDateTimeHelper.formatRelativeRemaining(now + (27L * 60L * 60_000L), now))

        val presets = ReminderDateTimeHelper.quickPresets()
        assertTrue(presets.size >= 6)
        presets.forEach { preset ->
            val target = preset.computeMillis(now)
            assertTrue("Preset ${preset.id} must be in the future", target > now)
        }
    }

    @Test
    fun `alarm catalog presets legacy compatibility custom ringtone roundtrip and pcm synthesis`() {
        assertTrue(AlarmSoundCatalog.presets.size >= 15)
        assertTrue(AlarmSoundCatalog.presetsByCategory(AlarmSoundCategory.BOTANICAL).size >= 5)
        assertTrue(AlarmSoundCatalog.presetsByCategory(AlarmSoundCategory.STUDIO_CHIMES).size >= 4)
        assertTrue(AlarmSoundCatalog.presetsByCategory(AlarmSoundCategory.ALERTS).size >= 4)

        assertNotNull(AlarmSoundCatalog.presetFor(AlarmSoundCatalog.SOUND_ZEN_BOWL))
        assertNotNull(AlarmSoundCatalog.presetFor(AlarmSoundCatalog.SOUND_DIGITAL_BEEP))

        val encoded = AlarmSoundCatalog.encodeCustomRingtone("Morning Chime #1", "content://media/internal/audio/media/42")
        assertTrue(AlarmSoundCatalog.isCustomRingtone(encoded))
        assertEquals("Morning Chime #1", AlarmSoundCatalog.extractRingtoneTitle(encoded))
        assertEquals("content://media/internal/audio/media/42", AlarmSoundCatalog.extractRingtoneUri(encoded))
        assertEquals("Morning Chime #1", AlarmSoundCatalog.displayName(encoded))

        AlarmSoundCatalog.presets.forEach { preset ->
            val pcm = AlarmSoundEngine.synthesizeChimePcm(preset.notes)
            assertTrue("Preset ${preset.id} should synthesize non-empty PCM", pcm.isNotEmpty())
        }
    }

    @Test
    fun `studio preferences persist in DataStore and backup json preserves notifyTelegram`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = AiConnectorPrefs(context)

        prefs.saveThemeOption("Dark")
        prefs.saveAudioQuality("Standard")
        prefs.saveVibrationEnabled(false)
        prefs.saveAutoPlayEnabled(true)

        val loaded = prefs.studioPreferencesFlow.first()
        assertEquals("Dark", loaded.themeOption)
        assertEquals("Standard", loaded.audioQuality)
        assertEquals(false, loaded.enableVibration)
        assertEquals(true, loaded.autoPlayAfterRecording)

        val dao = AppDatabase.getDatabase(context).voiceTaskDao()
        val repo = VoiceTaskRepository(dao)
        val futureTime = System.currentTimeMillis() + 3_600_000L
        dao.insertTask(
            VoiceTask(
                title = "Silent Alarm Audit",
                category = "Work",
                reminderTime = futureTime,
                alertSound = "Nordic Forest Chime",
                notifyTelegram = false,
                priority = TaskPriority.HIGH.level
            )
        )

        val json = repo.exportBackupJson()
        assertTrue(json.contains("\"notifyTelegram\": false"))
        assertTrue(repo.importBackupJson(json))

        val restored = repo.getAllTasks().filter { it.title == "Silent Alarm Audit" }
        assertTrue(restored.isNotEmpty())
        assertTrue(restored.all { !it.notifyTelegram && it.alertSound == "Nordic Forest Chime" })
    }
}
