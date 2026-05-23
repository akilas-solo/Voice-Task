package com.example.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VoiceTaskAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getIntExtra("TASK_ID", -1)
        if (taskId == -1) return

        val coroutineScope = CoroutineScope(Dispatchers.IO)
        coroutineScope.launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val task = db.voiceTaskDao().getTaskById(taskId)
                if (task != null && !task.isCompleted) {
                    showRealDeviceNotification(context, task)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showRealDeviceNotification(context: Context, task: VoiceTask) {
        val channelId = "voistask_reminders_channel"
        val notificationId = task.id

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "VoisTask Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notification channel for scheduled audio memo reminder alarms"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) // System standard alarm icon
            .setContentTitle("⏰ VoisTask Reminder")
            .setContentText(task.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                if (task.notes.isBlank()) "Task: ${task.title}\nFolder: ${task.category}" 
                else "${task.title}\nNotes: ${task.notes}\nFolder: ${task.category}"
            ))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(notificationId, builder.build())
    }
}
