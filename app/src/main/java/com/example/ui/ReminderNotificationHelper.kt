package com.example.ui

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.VoiceTask
import com.example.data.VoiceTaskAlarmReceiver

object ReminderNotificationHelper {
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
