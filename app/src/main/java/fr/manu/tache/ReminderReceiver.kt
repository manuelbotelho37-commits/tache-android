package fr.manu.tache

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("TASK_ID", -1)
        if (id < 0) return
        val task = TaskStore.get(context, id) ?: return
        if (task.done || task.paused) return

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Tache:ReminderWakeLock"
        )
        wakeLock.acquire(30_000)
        try {
            Reminders.createChannel(context)
            show(context, task)
            Reminders.scheduleAt(context, id, System.currentTimeMillis() + 3_600_000L)
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    companion object {
        fun show(context: Context, task: Task) {
            val id = task.id
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val fullScreenIntent = Intent(context, ReminderAlertActivity::class.java).apply {
                putExtra("TASK_ID", id)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val fullScreenPending = PendingIntent.getActivity(
                context,
                Reminders.codeFullScreen(id),
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val doneIntent = Intent(context, ReminderActionReceiver::class.java).apply {
                action = ReminderActionReceiver.ACTION_DONE
                putExtra("TASK_ID", id)
            }
            val donePending = PendingIntent.getBroadcast(
                context,
                Reminders.codeDone(id),
                doneIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val snoozeIntent = Intent(context, ReminderActionReceiver::class.java).apply {
                action = ReminderActionReceiver.ACTION_SNOOZE
                putExtra("TASK_ID", id)
            }
            val snoozePending = PendingIntent.getBroadcast(
                context,
                Reminders.codeSnooze(id),
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, Reminders.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(task.title)
                .setContentText("Rappel")
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(fullScreenPending)
                .setFullScreenIntent(fullScreenPending, true)
                .addAction(0, "Fait", donePending)
                .addAction(0, "Plus tard", snoozePending)

            nm.notify(id, builder.build())
        }
    }
}
