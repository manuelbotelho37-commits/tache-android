package fr.manu.tache

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Locale

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

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

    private fun rounded(fill: Int, radiusDp: Int, strokeColor: Int = 0, strokeDp: Int = 0): GradientDrawable {
        val d = GradientDrawable()
        d.setColor(fill)
        d.cornerRadius = dp(radiusDp).toFloat()
        if (strokeDp > 0) d.setStroke(dp(strokeDp), strokeColor)
        return d
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

        val gold = Color.parseColor("#C4A04F")
        val navy = Color.parseColor("#14171C")
        val cardColor = Color.parseColor("#232831")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(navy)
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = rounded(cardColor, 24)
            setPadding(dp(24), dp(32), dp(24), dp(24))
        }

        val label = TextView(this).apply {
            text = "RAPPEL · " + SimpleDateFormat("HH:mm", Locale.FRANCE).format(task.time)
            textSize = 14f
            setTextColor(gold)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.1f
            gravity = Gravity.CENTER
        }
        card.addView(label)

        val title = TextView(this).apply {
            text = task.title
            textSize = 28f
            setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        card.addView(
            title,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(16); bottomMargin = dp(32) }
        )

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val later = Button(this).apply {
            text = "Plus tard"
            isAllCaps = false
            textSize = 18f
            setTextColor(gold)
            background = rounded(Color.TRANSPARENT, 16, gold, 2)
            setOnClickListener {
                ReminderActionReceiver.handle(
                    this@ReminderAlertActivity, taskId, ReminderActionReceiver.ACTION_SNOOZE
                )
                finish()
            }
        }
        val done = Button(this).apply {
            text = "Fait"
            isAllCaps = false
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(navy)
            background = rounded(gold, 16)
            setOnClickListener {
                ReminderActionReceiver.handle(
                    this@ReminderAlertActivity, taskId, ReminderActionReceiver.ACTION_DONE
                )
                finish()
            }
        }
        buttons.addView(
            later,
            LinearLayout.LayoutParams(0, dp(64), 1f).apply { rightMargin = dp(8) }
        )
        buttons.addView(
            done,
            LinearLayout.LayoutParams(0, dp(64), 1f).apply { leftMargin = dp(8) }
        )
        card.addView(
            buttons,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
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
