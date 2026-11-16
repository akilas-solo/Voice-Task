package com.example.ui

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.data.VoiceTask
import com.example.data.VoiceTaskAlarmReceiver
import java.util.Locale

object ReminderNotificationHelper {

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false)
    }

    fun createExactAlarmSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.fromParts("package", context.packageName, null)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun channelIdForSound(alertSound: String): String {
        val trimmed = alertSound.trim()
        if (trimmed.isBlank() || trimmed == AlarmSoundCatalog.SOUND_APP_DEFAULT) {
            return "voistask_alarm_default_v3"
        }
        val baseLabel = AlarmSoundCatalog.displayName(trimmed)
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(24)
            .ifBlank { "custom" }
        val hash = (trimmed.hashCode() and 0x7FFFFFFF).toString(16)
        return "voistask_alarm_${baseLabel}_$hash"
    }

    fun resolveNotificationSoundUri(context: Context, alertSound: String): Uri? {
        val customUri = AlarmSoundCatalog.extractRingtoneUri(alertSound)
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (customUri != null) return customUri

        val preset = AlarmSoundCatalog.presetFor(alertSound)
        if (preset != null) {
            val wavFile = AlarmSoundEngine.synthesizeChimeWavFile(context, preset)
            if (wavFile != null && wavFile.exists()) {
                val authority = "${context.packageName}.fileprovider"
                val contentUri = runCatching {
                    FileProvider.getUriForFile(context, authority, wavFile)
                }.getOrNull()
                if (contentUri != null) {
                    runCatching {
                        context.grantUriPermission(
                            "com.android.systemui",
                            contentUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                    return contentUri
                }
            }
        }

        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    fun ensureAlarmNotificationChannel(context: Context, alertSound: String): String {
        val channelId = channelIdForSound(alertSound)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return channelId
            if (nm.getNotificationChannel(channelId) == null) {
                val soundLabel = AlarmSoundCatalog.displayName(alertSound)
                val soundUri = resolveNotificationSoundUri(context, alertSound)
                val channel = NotificationChannel(
                    channelId,
                    "Alarms · $soundLabel",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Scheduled alarm reminders using $soundLabel"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0L, 280L, 220L, 280L, 600L)
                    if (soundUri != null) {
                        setSound(
                            soundUri,
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                    }
                }
                nm.createNotificationChannel(channel)
            }
        }
        return channelId
    }

    fun scheduleDeviceNotification(context: Context, taskId: Int, reminderTime: Long?) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, VoiceTaskAlarmReceiver::class.java).apply {
            putExtra("TASK_ID", taskId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (reminderTime == null) {
            // Cancel any existing alarm & clear active notification
            try {
                alarmManager.cancel(pendingIntent)
                cancelDisplayedNotification(context, taskId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return
        }

        val triggerAt = reminderTime.coerceAtLeast(System.currentTimeMillis() + 1_000L)

        // Schedule the alarm
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            // Fallback for exact alarm permission limits
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun rescheduleAllPendingReminders(context: Context, tasks: List<VoiceTask>) {
        val now = System.currentTimeMillis()
        tasks.forEach { task ->
            val target = task.reminderTime
            if (!task.isCompleted && target != null && target > now - 30_000L) {
                scheduleDeviceNotification(context, task.id, target)
            }
        }
    }

    fun cancelDisplayedNotification(context: Context, taskId: Int) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(taskId)
        } catch (_: Exception) {
        }
    }
}
