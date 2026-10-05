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
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.speech.RecognizerIntent
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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

    private var speechTarget: EditText? = null
    private var pickedFile: ((Uri) -> Unit)? = null

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        val text = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        val target = speechTarget
        if (text != null && target != null) {
            val old = target.text.toString()
            target.setText(if (old.isEmpty()) text else "$old $text")
            target.setSelection(target.text.length)
        }
    }

    private val fileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) { }
            pickedFile?.invoke(uri)
        }
    }

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private val gold = Color.parseColor("#C4A04F")
    private val navy = Color.parseColor("#101216")
    private val navy2 = Color.parseColor("#15181D")
    private val cardColor = Color.parseColor("#232831")
    private val fieldColor = Color.parseColor("#20252D")
    private val grayText = Color.parseColor("#9AA0A8")
    private val lineColor = Color.parseColor("#3A414D")
    private val red = Color.parseColor("#D9534F")
    private val orange = Color.parseColor("#D98A00")
    private val serifBold = Typeface.create(Typeface.SERIF, Typeface.BOLD)

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
        "day" -> "Chaque jour"
        "week" -> "Chaque semaine"
        "month" -> "Chaque mois"
        "year" -> "Chaque année"
        else -> ""
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
            gravity = Gravity.TOP
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
            typeface = serifBold
        })
        header.addView(left, LinearLayout.LayoutParams(0, WRAP, 1f))

        val gear = TextView(this).apply {
            text = "⚙"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#D8DCE2"))
            background = rounded(navy2, 12, lineColor, 1)
            setOnClickListener { showMenu() }
        }
        header.addView(gear, LinearLayout.LayoutParams(dp(46), dp(46)))
        content.addView(header)

        tabsBox = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        content.addView(tabsBox, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(16) })

        permBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(permBox)

        val scroll = ScrollView(this)
        listBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(100))
        }
        scroll.addView(listBox)
        content.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))

        frame.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))

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
        frame.addView(
            add,
            FrameLayout.LayoutParams(WRAP, dp(56), Gravity.BOTTOM or Gravity.END)
                .apply { rightMargin = dp(18); bottomMargin = dp(24) }
        )

        setContentView(frame)
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
        Reminders.rescheduleAll(this)
        refresh()
    }

    private fun showMenu() {
        AlertDialog.Builder(this)
            .setItems(arrayOf("Test d'alarme dans 1 minute", "Réglages de l'application")) { _, which ->
                if (which == 0) {
                    val t = TaskStore.add(
                        this,
                        Task(0, "Test alarme", System.currentTimeMillis() + 60_000)
                    )
                    Reminders.schedule(this, t)
                    refresh()
                    Toast.makeText(
                        this,
                        "Alarme dans 1 minute : verrouille l'écran", Toast.LENGTH_LONG
                    ).show()
                } else {
                    startSafe(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:$packageName")
                        )
                    )
                }
            }
            .show()
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
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(10) }
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
                LinearLayout.LayoutParams(0, WRAP, 1f).apply {
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
        nm.cancel(t.id)
        Reminders.cancel(this, t.id)
        if (t.done) {
            val re = t.copy(done = false)
            TaskStore.update(this, re)
            Reminders.schedule(this, re)
        } else if (Reminders.isRepeating(t.repeat)) {
            val next = t.copy(time = Reminders.nextOccurrence(t.time, t.repeat))
            TaskStore.update(this, next)
            Reminders.schedule(this, next)
        } else {
            TaskStore.update(this, t.copy(done = true))
        }
        refresh()
    }

    private fun togglePause(t: Task) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (t.paused) {
            val re = t.copy(paused = false)
            TaskStore.update(this, re)
            Reminders.schedule(this, re)
        } else {
            nm.cancel(t.id)
            Reminders.cancel(this, t.id)
            TaskStore.update(this, t.copy(paused = true))
        }
        refresh()
    }

    private fun confirmDelete(onYes: () -> Unit) {
        AlertDialog.Builder(this)
            .setMessage("Supprimer cette tâche ?")
            .setPositiveButton("Oui, supprimer") { _, _ -> onYes() }
            .setNegativeButton("Non", null)
            .show()
    }

    private fun deleteTask(t: Task) {
        val nm = getSystemService(Context.NOTIFIC
