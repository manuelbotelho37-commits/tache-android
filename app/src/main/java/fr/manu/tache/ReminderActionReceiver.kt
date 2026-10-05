package fr.manu.tache

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_DONE = "fr.manu.tache.ACTION_DONE"
        const val ACTION_SNOOZE = "fr.manu.tache.ACTION_SNOOZE"
        const val SNOOZE_MS = 60L * 60L * 1000L

        // Appelé par les boutons de la notification ET par l'écran d'alarme
        fun handle(context: Context, id: Int, action: String) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // Arrête le son et enlève la notification
            nm.cancel(id)
            Reminders.cancel(context, id)

            val task = TaskStore.get(context, id) ?: return

            if (action == ACTION_DONE) {
                if (task.repeat == "none") {
                    TaskStore.update(context, task.copy(done = true))
                } else {
                    // Tâche récurrente : programme la prochaine occurrence
                    val next = task.copy(time = Reminders.nextOccurrence(task.time, task.repeat))
                    TaskStore.update(context, next)
                    Reminders.schedule(context, next)
                }
            } else if (action == ACTION_SNOOZE) {
                val later = task.copy(time = System.currentTimeMillis() + SNOOZE_MS)
                TaskStore.update(context, later)
                Reminders.schedule(context, later)
            }

            // Ferme l'écran d'alarme s'il est ouvert
            context.sendBroadcast(
                Intent(ReminderAlertActivity.ACTION_CLOSE)
                    .setPackage(context.packageName)
                    .putExtra("TASK_ID", id)
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("TASK_ID", -1)
        if (id < 0) return
        val action = intent.action ?: return
        handle(context, id, action)
    }
}
