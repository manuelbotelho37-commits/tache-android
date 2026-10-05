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
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
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
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var permBox: LinearLayout
    private lateinit var listBox: LinearLayout
    private lateinit var tabsBox: LinearLayout
    private var tab = 1

    private val gold = Color.parseColor("#C4A04F")
    private val navy = Color.parseColor("#14171C")
    private val cardColor = Color.parseColor("#232831")
    private val grayText = Color.parseColor("#9AA0A8")
    private val lineColor = Color.parseColor("#3A414D")

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

    private fun rounded(fill: Int, radiusDp: Int, strokeColor: Int = 0, strokeDp: Int = 0): GradientDrawable {
        val d = GradientDrawable()
        d.setColor(fill)
        d.cornerRadius = dp(radiusDp).toFloat()
        if (strokeDp > 0) d.setStroke(dp(strokeDp), strokeColor)
        return d
    }

    private fun startOfDay(ms: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = ms
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun dayDiff(ms: Long): Int =
        Math.round((startOfDay(ms) - startOfDay(System.currentTimeMillis())) / 86400000.0).toInt()

    private fun whenLabel(ms: Long): String {
        val hour = SimpleDateFormat("HH:mm", Locale.FRANCE).format(ms)
        return when (dayDiff(ms)) {
            0 -> "Aujourd'hui · $hour"
            1 -> "Demain · $hour"
            else -> SimpleDateFormat("EEE d MMM", Locale.FRANCE).format(ms) + " · " + hour
        }
    }

    private fun repeatLabel(r: String) = when (r) {
        "daily" -> "Chaque jour"
        "weekly" -> "Chaque semaine"
        else -> "Une seule fois"
    }

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

        val frame = FrameLayout(this).apply { setBackgroundColor(navy) }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(44), dp(18), dp(8))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val dateText = SimpleDateFormat("EEEE d MMMM", Locale.FRANCE).format(Date())
            .replaceFirstChar { it.uppercase() }
        left.addView(TextView(this).apply {
            text = dateText
            textSize = 13f
            setTextColor(grayText)
        })
        left.addView(TextView(this).apply {
            text = "Tâche"
            textSize = 38f
            setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        })
        header.addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val testPill = pill("Test 1 min") {
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
        val settingsPill = pill("Réglages") {
            startSafe(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
        }
        header.addView(testPill, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { rightMargin = dp(8) })
        header.addView(settingsPill)
        content.addView(header)

        tabsBox = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        content.addView(tabsBox, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(16) })

        permBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(permBox)

        val scroll = ScrollView(this)
        listBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(100))
        }
        scroll.addView(listBox)
        content.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        frame.addView(content, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val add = Button(this).apply {
            text = "+ Ajouter"
            isAllCaps = false
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(navy)
            background = rounded(gold, 28)
            setPadding(dp(24), dp(14), dp(24), dp(14))
            setOnClickListener { showEditor(null) }
        }
        frame.addView(add, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, dp(56),
            Gravity.BOTTOM or Gravity.END
        ).apply { rightMargin = dp(18); bottomMargin = dp(24) })

        setContentView(frame)
    }

    private fun pill(label: String, action: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(Color.parseColor("#D8DCE2"))
            background = rounded(Color.TRANSPARENT, 12, lineColor, 1)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { action() }
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
            isAllCaps = false
            background = rounded(Color.parseColor("#B3261E"), 12)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
            setOnClickListener { action() }
        }

    private fun tabView(label: String, index: Int): TextView =
        TextView(this).apply {
            text = label
            textSize = 12f
            gravity = Gravity.CENTER
            if (tab == index) {
                setTextColor(navy)
                typeface = Typeface.DEFAULT_BOLD
                background = rounded(gold, 12)
            } else {
                setTextColor(Color.parseColor("#D8DCE2"))
                background = rounded(Color.TRANSPARENT, 12, lineColor, 1)
            }
            setPadding(dp(2), dp(12), dp(2), dp(12))
            setOnClickListener {
                tab = index
                refresh()
            }
        }

    private fun refresh() {
        val all = TaskStore.all(this)
        val todayList = all.filter { !it.done && dayDiff(it.time) <= 0 }.sortedBy { it.time }
        val todoList = all.filter { !it.done }.sortedBy { it.time }
        val doneList = all.filter { it.done }.sortedByDescending { it.time }

        tabsBox.removeAllViews()
        val tabs = listOf(
            "Aujourd'hui · ${todayList.size}",
            "À faire · ${todoList.size}",
            "Terminées · ${doneList.size}"
        )
        for (i in tabs.indices) {
            tabsBox.addView(
                tabView(tabs[i], i),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (i > 0) leftMargin = dp(6)
                }
            )
        }

        listBox.removeAllViews()
        val shown = when (tab) {
            0 -> todayList
            1 -> todoList
            else -> doneList
        }
        if (shown.isEmpty()) {
            listBox.addView(TextView(this).apply {
                text = "Rien ici pour l'instant."
                textSize = 15f
                setTextColor(grayText)
                setPadding(dp(4), dp(24), dp(4), 0)
            })
        }
        for (t in shown) listBox.addView(row(t))
    }

    private fun toggleDone(t: Task) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (t.done) {
            val re = t.copy(done = false)
            TaskStore.update(this, re)
            Reminders.schedule(this, re)
        } else {
            nm.cancel(t.id)
            Reminders.cancel(this, t.id)
            TaskStore.update(this, t.copy(done = true))
        }
        refresh()
    }

    private fun confirmDelete(t: Task) {
        AlertDialog.Builder(this)
            .setMessage("Supprimer cette tâche ?")
            .setPositiveButton("Oui, supprimer") { _, _ ->
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(t.id)
                Reminders.cancel(this, t.id)
                TaskStore.delete(this, t.id)
                refresh()
            }
            .setNegativeButton("Non", null)
            .show()
    }

    private fun row(t: Task): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(cardColor, 14)
            setPadding(dp(14), dp(14), dp(10), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
            setOnClickListener { showEditor(t) }
        }

        val circle = View(this).apply {
            background = if (t.done) rounded(gold, 15, gold, 2)
            else rounded(Color.TRANSPARENT, 15, Color.parseColor("#5A6270"), 2)
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)).apply { rightMargin = dp(12) }
            setOnClickListener { toggleDone(t) }
        }

        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply {
            text = t.title
            textSize = 16f
            setTextColor(if (t.done) grayText else Color.WHITE)
        })
        var sub = whenLabel(t.time)
        if (t.repeat != "none") sub += " · " + repeatLabel(t.repeat)
        texts.addView(TextView(this).apply {
            text = sub
            textSize = 12f
            setTextColor(grayText)
        })

        val del = TextView(this).apply {
            text = "Supprimer"
            textSize = 12f
            setTextColor(grayText)
            setPadding(dp(10), dp(10), dp(6), dp(10))
            setOnClickListener { confirmDelete(t) }
        }

        box.addView(circle)
        box.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(del)
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
