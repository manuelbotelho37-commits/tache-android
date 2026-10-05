package fr.manu.tache

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import java.util.Calendar

object Reminders {
    const val CHANNEL_ID = "task_alarm_channel_v3"

    fun codeAlarm(id: Int) = id * 10
    fun codeDone(id: Int) = id * 10 + 1
    fun codeSnooze(id: Int) = id * 10 + 2
    fun codeFullScreen(id: Int) = id * 10 + 3
    fun codeContent(id: Int) = id * 10 + 4

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            try {
                nm.deleteNotificationChannel("task_alarm_channel_v2")
            } catch (e: Exception) {
            }
            val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Rappels des tâches",
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "Rappels plein écran des tâches"
            channel.enableVibration(true)
            channel.vibrationPattern = longArrayOf(0, 500, 300, 500)
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            channel.setBypassDnd(true)
            channel.setSound(
                sound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            nm.createNotificationChannel(channel)
        }
    }

    private fun alarmIntent(context: Context, id: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("TASK_ID", id)
        }
        return PendingIntent.getBroadcast(
            context,
            codeAlarm(id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun scheduleAt(context: Context, id: Int, timeMs: Long) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val show = PendingIntent.getActivity(
                context,
                codeContent(id),
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.setAlarmClock(
                AlarmManager.AlarmClockInfo(timeMs, show),
                alarmIntent(context, id)
            )
        } catch (e: Exception) {
        }
    }

    fun schedule(context: Context, task: Task) {
        if (task.done || task.paused) return
        if (task.time <= System.currentTimeMillis()) return
        scheduleAt(context, task.id, task.time)
    }

    fun cancel(context: Context, id: Int) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(alarmIntent(context, id))
        } catch (e: Exception) {
        }
    }

    fun isRepeating(repeat: String) =
        repeat == "day" || repeat == "week" || repeat == "month" || repeat == "year"

    fun nextOccurrence(time: Long, repeat: String): Long {
        if (!isRepeating(repeat)) return time
        val cal = Calendar.getInstance()
        cal.timeInMillis = time
        val now = System.currentTimeMillis()
        do {
            when (repeat) {
                "day" -> cal.add(Calendar.DAY_OF_YEAR, 1)
                "week" -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                "month" -> cal.add(Calendar.MONTH, 1)
                else -> cal.add(Calendar.YEAR, 1)
            }
        } while (cal.timeInMillis <= now)
        return cal.timeInMillis
    }

    fun rescheduleAll(context: Context) {
        try {
            createChannel(context)
        } catch (e: Exception) {
        }
        for (task in TaskStore.all(context)) {
            try {
                if (task.done || task.paused) continue
                var t = task
                if (t.time <= System.currentTimeMillis() && isRepeating(t.repeat)) {
                    t = t.copy(time = nextOccurrence(t.time, t.repeat))
                    TaskStore.update(context, t)
                }
                schedule(context, t)
            } catch (e: Exception) {
            }
        }
    }
}
