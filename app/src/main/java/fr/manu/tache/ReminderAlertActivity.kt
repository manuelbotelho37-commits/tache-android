package fr.manu.tache

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class ReminderAlertActivity : AppCompatActivity() {

    companion object {
        const val ACTION_CLOSE = "fr.manu.tache.ACTION_CLOSE"
    }

    private var taskId = -1

    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getIntExtra("TASK_ID", -2) == taskId) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        taskId = intent.getIntExtra("TASK_ID", -1)
        val task = TaskStore.get(this, taskId)
        if (task == null || task.done) {
            finish()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#111111"))
            setPadding(48, 48, 48, 48)
        }

        val title = TextView(this).apply {
            text = task.title.uppercase()
            textSize = 32f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(
            title,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 120 }
        )

        val done = Button(this).apply {
            text = "Fait"
            textSize = 26f
            setBackgroundColor(Color.parseColor("#2E9E4F"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                ReminderActionReceiver.handle(
                    this@ReminderAlertActivity, taskId, ReminderActionReceiver.ACTION_DONE
                )
                finish()
            }
        }
        root.addView(
            done,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 260
            ).apply { bottomMargin = 40 }
        )

        val later = Button(this).apply {
            text = "Plus tard"
            textSize = 26f
            setBackgroundColor(Color.parseColor("#D98A00"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                ReminderActionReceiver.handle(
                    this@ReminderAlertActivity, taskId, ReminderActionReceiver.ACTION_SNOOZE
                )
                finish()
            }
        }
        root.addView(
            later,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 260
            )
        )

        setContentView(root)

        ContextCompat.registerReceiver(
            this,
            closeReceiver,
            IntentFilter(ACTION_CLOSE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newId = intent.getIntExtra("TASK_ID", -1)
        if (newId != taskId) {
            try { unregisterReceiver(closeReceiver) } catch (e: Exception) { }
            recreate()
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(closeReceiver) } catch (e: Exception) { }
        super.onDestroy()
    }
}
