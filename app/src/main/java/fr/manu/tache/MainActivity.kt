package fr.manu.tache

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var permBox: LinearLayout
    private lateinit var listBox: LinearLayout
    private val fmt = SimpleDateFormat("EEE d MMM yyyy 'à' HH:mm", Locale.FRANCE)

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Reminders.createChannel(this)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1
            )
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#111111"))
            setPadding(dp(16), dp(40), dp(16), dp(16))
        }

        val title = TextView(this).apply {
            text = "TÂCHE"
            textSize = 26f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(title)

        permBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(permBox)

        val scroll = ScrollView(this)
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(listBox)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )

        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val add = Button(this).apply {
            text = "+ Nouvelle tâche"
            setOnClickListener { showEditor(null) }
        }
        val test = Button(this).apply {
            text = "Test 1 min"
            setOnClickListener {
                val t = TaskStore.add(
                    this@MainActivity, "Test alarme",
                    System.currentTimeMillis() + 60_000, "none"
                )
                Reminders.schedule(this@MainActivity, t)
                refresh()
                Toast.makeText(
                    this@MainActivity,
                    "Alarme dans 1 minute : verrouille l'écran", Toast.LENGTH_LONG
                ).show()
            }
        }
        val settings = Button(this).apply {
            text = "Réglages"
            setOnClickListener {
                startSafe(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        bar.addView(add, lp)
        bar.addView(test, lp)
        bar.addView(settings, lp)
        root.addView(bar)

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
        Reminders.rescheduleAll(this)
        refresh()
    }

    private fun startSafe(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }

    private fun refreshPermissions() {
        permBox.removeAllViews()
        val pkg = Uri.parse("package:$packageName")

        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            permBox.addView(permButton("1. Autoriser les notifications") {
                startSafe(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                )
            })
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                permBox.addView(permButton("2. Autoriser les alarmes et rappels") {
                    startSafe(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg))
                })
            }
        }
        if (Build.VERSION.SDK_INT >= 34) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.canUseFullScreenIntent()) {
                permBox.addView(permButton("3. Autoriser les notifications plein écran") {
                    startSafe(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg))
                })
            }
        }
    }

    private fun permButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            setBackgroundColor(Color.parseColor("#B3261E"))
            setTextColor(Color.WHITE)
            setOnClickListener { action() }
        }

    private fun refresh() {
        listBox.removeAllViews()
        val tasks = TaskStore.all(this).sortedWith(compareBy({ it.done }, { it.time }))
        for (t in tasks) listBox.addView(row(t))
    }

    private fun repeatLabel(r: String) = when (r) {
        "daily" -> "Chaque jour"
        "weekly" -> "Chaque semaine"
        else -> "Une seule fois"
    }

    private fun row(t: Task): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#222222"))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
            setOnClickListener { showEditor(t) }
        }
        val name = TextView(this).apply {
            text = t.title.uppercase()
            textSize = 18f
            setTextColor(if (t.done) Color.GRAY else Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        val info = TextView(this).apply {
            text = fmt.format(t.time) + " · " + repeatLabel(t.repeat)
            textSize = 14f
            setTextColor(Color.LTGRAY)
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val doneBtn = Button(this).apply {
            text = if (t.done) "Rouvrir" else "Fait"
            setOnClickListener {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (t.done) {
                    val re = t.copy(done = false)
                    TaskStore.update(this@MainActivity, re)
                    Reminders.schedule(this@MainActivity, re)
                } else {
                    nm.cancel(t.id)
                    Reminders.cancel(this@MainActivity, t.id)
                    TaskStore.update(this@MainActivity, t.copy(done = true))
                }
                refresh()
            }
        }
        val delBtn = Button(this).apply {
            text = "Supprimer"
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage("Supprimer cette tâche ?")
                    .setPositiveButton("Oui, supprimer") { _, _ ->
                        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        nm.cancel(t.id)
                        Reminders.cancel(this@MainActivity, t.id)
                        TaskStore.delete(this@MainActivity, t.id)
                        refresh()
                    }
                    .setNegativeButton("Non", null)
                    .show()
            }
        }
        buttons.addView(doneBtn)
        buttons.addView(delBtn)
        box.addView(name)
        box.addView(info)
        box.addView(buttons)
        return box
    }

    private fun showEditor(existing: Task?) {
        val cal = Calendar.getInstance()
        if (existing != null) {
            cal.timeInMillis = existing.time
        } else {
            cal.add(Calendar.HOUR_OF_DAY, 1)
            cal.set(Calendar.MINUTE, 0)
        }
        var repeat = existing?.repeat ?: "none"
        val dayFmt = SimpleDateFormat("EEE d MMM yyyy", Locale.FRANCE)
        val hourFmt = SimpleDateFormat("HH:mm", Locale.FRANCE)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(8))
        }
        val titleField = EditText(this).apply {
            hint = "Titre"
            setText(existing?.title ?: "")
        }
        val dateBtn = Button(this)
        val timeBtn = Button(this)
        val repBtn = Button(this)

        fun update() {
            dateBtn.text = dayFmt.format(cal.time)
            timeBtn.text = hourFmt.format(cal.time)
            repBtn.text = repeatLabel(repeat)
        }
        update()

        dateBtn.setOnClickListener {
            DatePickerDialog(
                this,
                { _, y, m, d ->
                    cal.set(y, m, d)
                    update()
                },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
        timeBtn.setOnClickListener {
            TimePickerDialog(
                this,
                { _, h, mi ->
                    cal.set(Calendar.HOUR_OF_DAY, h)
                    cal.set(Calendar.MINUTE, mi)
                    update()
                },
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true
            ).show()
        }
        repBtn.setOnClickListener {
            repeat = when (repeat) {
                "none" -> "daily"
                "daily" -> "weekly"
                else -> "none"
            }
            update()
        }

        layout.addView(titleField)
        layout.addView(dateBtn)
        layout.addView(timeBtn)
        layout.addView(repBtn)

        AlertDialog.Builder(this)
            .setView(layout)
            .setPositiveButton("Enregistrer") { _, _ ->
                val name = titleField.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Titre vide", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val time = cal.timeInMillis
                val task: Task
                if (existing != null) {
                    Reminders.cancel(this, existing.id)
                    task = existing.copy(title = name, time = time, repeat = repeat, done = false)
                    TaskStore.update(this, task)
                } else {
                    task = TaskStore.add(this, name, time, repeat)
                }
                if (time <= System.currentTimeMillis()) {
                    Toast.makeText(this, "Cette heure est déjà passée", Toast.LENGTH_LONG).show()
                }
                Reminders.schedule(this, task)
                refresh()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
}
