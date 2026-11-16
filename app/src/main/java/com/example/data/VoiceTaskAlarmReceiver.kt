package com.example.data

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.ui.AlarmSoundCatalog
import com.example.ui.ReminderNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VoiceTaskAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val tasks = db.voiceTaskDao().getAllTasks()
                    ReminderNotificationHelper.rescheduleAllPendingReminders(context, tasks)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        val taskId = intent.getIntExtra("TASK_ID", -1)
        if (taskId == -1) {
            pendingResult.finish()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val task = db.voiceTaskDao().getTaskById(taskId)
                if (task != null && !task.isCompleted) {
                    showRealDeviceNotification(context, task)

                    val prefs = TelegramPrefs(context).getConfig()
                    val recentlySent = task.lastTelegramSentAt != null &&
                        (System.currentTimeMillis() - task.lastTelegramSentAt) < 25_000L
                    if (task.notifyTelegram && prefs.isVerified && prefs.autoSendReminders && !recentlySent) {
                        TelegramSyncManager.sendTaskAlert(
                            context = context.applicationContext,
                            task = task,
                            isReminder = true
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showRealDeviceNotification(context: Context, task: VoiceTask) {
        val channelId = ReminderNotificationHelper.ensureAlarmNotificationChannel(context, task.alertSound)
        val notificationId = task.id

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("TRIGGERED_TASK_ID", task.id)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundTitle = AlarmSoundCatalog.displayName(task.alertSound)
        val priorityLabel = TaskPriority.fromLevel(task.priority).label
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Reminder: ${task.title}")
            .setContentText("$priorityLabel · ${task.category} · $soundTitle")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    buildString {
                        append(task.title)
                        if (task.notes.isNotBlank()) {
                            append("\n").append(task.notes)
                        }
                        append("\nFolder: ").append(task.category)
                        append(" • Priority: ").append(priorityLabel)
                        append(" • Sound: ").append(soundTitle)
                    }
                )
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(notificationId, builder.build())
    }
}
