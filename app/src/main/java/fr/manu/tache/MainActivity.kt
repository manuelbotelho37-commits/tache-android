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
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(t.id)
        Reminders.cancel(this, t.id)
        TaskStore.delete(this, t.id)
        refresh()
    }

    private fun small(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(grayText)
            setPadding(0, dp(14), 0, dp(6))
        }

    private fun fieldBox(): GradientDrawable = rounded(fieldColor, 12, lineColor, 1)

    private fun editField(hint: String, value: String, lines: Int): EditText =
        EditText(this).apply {
            setText(value)
            this.hint = hint
            setHintTextColor(grayText)
            setTextColor(Color.WHITE)
            textSize = 16f
            background = fieldBox()
            setPadding(dp(14), dp(12), dp(14), dp(12))
            if (lines > 1) {
                inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                minLines = lines
                gravity = Gravity.TOP
            } else {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            }
        }

    private fun smallButton(label: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#D8DCE2"))
            setOnClickListener { onClick() }
        }

    private fun segment(
        options: List<String>,
        initial: Int,
        radius: Int,
        textSp: Float,
        onPick: (Int) -> Unit
    ): LinearLayout {
        val box = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val views = mutableListOf<TextView>()
        fun paint(sel: Int) {
            for (i in views.indices) {
                val v = views[i]
                if (i == sel) {
                    v.setTextColor(navy)
                    v.typeface = Typeface.DEFAULT_BOLD
                    v.background = rounded(gold, radius)
                } else {
                    v.setTextColor(Color.parseColor("#D8DCE2"))
                    v.typeface = Typeface.DEFAULT
                    v.background = rounded(Color.TRANSPARENT, radius, lineColor, 1)
                }
            }
        }
        for (i in options.indices) {
            val v = TextView(this).apply {
                text = options[i]
                textSize = textSp
                gravity = Gravity.CENTER
                setPadding(dp(2), dp(11), dp(2), dp(11))
                setOnClickListener {
                    paint(i)
                    onPick(i)
                }
            }
            views.add(v)
            box.addView(
                v,
                LinearLayout.LayoutParams(0, WRAP, 1f).apply {
                    if (i > 0) leftMargin = dp(6)
                }
            )
        }
        paint(initial)
        return box
    }

    private fun row(t: Task): View {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(cardColor, 14)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(10) }
            setOnClickListener { showEditor(t) }
        }

        val barColor = when (t.priority) {
            2 -> red
            1 -> orange
            else -> Color.TRANSPARENT
        }
        val bar = View(this).apply { background = rounded(barColor, 3) }
        outer.addView(
            bar,
            LinearLayout.LayoutParams(dp(5), dp(44)).apply { leftMargin = dp(8) }
        )

        val check = TextView(this).apply {
            text = if (t.done) "✓" else ""
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(navy)
            background = if (t.done) rounded(gold, 15, gold, 2)
            else rounded(Color.TRANSPARENT, 15, gold, 2)
            setOnClickListener { toggleDone(t) }
        }
        outer.addView(
            check,
            LinearLayout.LayoutParams(dp(30), dp(30)).apply {
                leftMargin = dp(10)
                rightMargin = dp(12)
            }
        )

        val mid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, dp(14))
        }
        mid.addView(TextView(this).apply {
            text = t.title
            textSize = 16f
            setTextColor(if (t.done || t.paused) grayText else Color.WHITE)
            if (t.done) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
        })
        val parts = mutableListOf<String>()
        if (t.type == "appointment") parts.add("Rendez-vous")
        parts.add(whenLabel(t.time))
        if (repeatLabel(t.repeat).isNotEmpty()) parts.add(repeatLabel(t.repeat))
        if (t.paused) parts.add("En pause")
        mid.addView(TextView(this).apply {
            text = parts.joinToString(" · ")
            textSize = 12f
            setTextColor(grayText)
        })
        outer.addView(mid, LinearLayout.LayoutParams(0, WRAP, 1f))

        val pause = smallButton(if (t.paused) "▶" else "⏸") { togglePause(t) }
        outer.addView(pause, LinearLayout.LayoutParams(dp(40), dp(40)))

        val clip = smallButton("📎") {
            showDossier(t.note, t.link, t.files) { n, l, f ->
                TaskStore.update(this, t.copy(note = n, link = l, files = f))
                refresh()
            }
        }
        if (t.note.isNotEmpty() || t.link.isNotEmpty() || t.files.isNotEmpty()) {
            clip.setTextColor(gold)
        }
        outer.addView(
            clip,
            LinearLayout.LayoutParams(dp(40), dp(40)).apply { rightMargin = dp(6) }
        )
        return outer
    }

    private fun startSpeech(target: EditText) {
        speechTarget = target
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
        try {
            speechLauncher.launch(i)
        } catch (e: Exception) {
            Toast.makeText(this, "Dictée vocale indisponible", Toast.LENGTH_LONG).show()
        }
    }

    private fun fileName(s: String): String {
        try {
            contentResolver.query(Uri.parse(s), null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) return c.getString(idx)
            }
        } catch (e: Exception) { }
        return "Fichier"
    }

    private fun openFile(s: String) {
        val uri = Uri.parse(s)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, contentResolver.getType(uri) ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(this, "Impossible d'ouvrir ce fichier", Toast.LENGTH_LONG).show()
        }
    }

    private fun openLink(s: String) {
        val u = if (s.startsWith("http")) s else "https://$s"
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
        } catch (e: Exception) {
            Toast.makeText(this, "Lien impossible à ouvrir", Toast.LENGTH_LONG).show()
        }
    }
private fun showDossier(
        note: String,
        link: String,
        files: List<String>,
        onSave: (String, String, List<String>) -> Unit
    ) {
        val fl = files.toMutableList()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(8))
        }
        box.addView(TextView(this).apply {
            text = "Dossier"
            textSize = 24f
            setTextColor(Color.WHITE)
            typeface = serifBold
        })

        box.addView(small("Note"))
        val noteField = editField("Écrire une note", note, 4)
        box.addView(noteField)

        box.addView(small("Lien"))
        val linkField = editField("https://...", link, 1)
        box.addView(linkField)
        box.addView(smallButton("🔗  Ouvrir le lien") {
            val l = linkField.text.toString().trim()
            if (l.isNotEmpty()) openLink(l)
        })

        box.addView(small("Fichiers et photos"))
        val filesBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(filesBox)

        fun drawFiles() {
            filesBox.removeAllViews()
            for (f in fl.toList()) {
                val line = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(4), 0, dp(4))
                }
                line.addView(
                    TextView(this).apply {
                        text = "📄  " + fileName(f)
                        textSize = 15f
                        setTextColor(Color.WHITE)
                        setOnClickListener { openFile(f) }
                    },
                    LinearLayout.LayoutParams(0, WRAP, 1f)
                )
                line.addView(
                    smallButton("✕") {
                        AlertDialog.Builder(this)
                            .setMessage("Retirer ce fichier ?")
                            .setPositiveButton("Oui, retirer") { _, _ ->
                                fl.remove(f)
                                drawFiles()
                            }
                            .setNegativeButton("Non", null)
                            .show()
                    },
                    LinearLayout.LayoutParams(dp(40), dp(40))
                )
                filesBox.addView(line)
            }
        }
        drawFiles()

        box.addView(smallButton("📎  Ajouter une photo ou un fichier") {
            pickedFile = { uri ->
                fl.add(uri.toString())
                drawFiles()
            }
            fileLauncher.launch(arrayOf("*/*"))
        })

        val dialog = AlertDialog.Builder(this)
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Enregistrer") { _, _ ->
                onSave(
                    noteField.text.toString().trim(),
                    linkField.text.toString().trim(),
                    fl.toList()
                )
            }
            .setNegativeButton("Annuler", null)
            .create()
        dialog.show()
        dialog.window?.setBackgroundDrawable(rounded(navy2, 18))
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(gold)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(grayText)
    }

    private fun showEditor(existing: Task?) {
        var type = existing?.type ?: "task"
        val repeatKeys = listOf("none", "day", "week", "month", "year")
        var repeat = existing?.repeat ?: "none"
        var priority = existing?.priority ?: 0
        var note = existing?.note ?: ""
        var link = existing?.link ?: ""
        var files: List<String> = existing?.files ?: emptyList()
        val cal = Calendar.getInstance().apply {
            timeInMillis = existing?.time ?: (System.currentTimeMillis() + 3_600_000L)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(8))
        }
        box.addView(TextView(this).apply {
            text = if (existing == null) "Nouveau" else "Modifier"
            textSize = 24f
            setTextColor(Color.WHITE)
            typeface = serifBold
        })

        box.addView(
            segment(
                listOf("Tâche", "Rendez-vous"),
                if (type == "appointment") 1 else 0,
                12,
                14f
            ) { type = if (it == 0) "task" else "appointment" },
            LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(14) }
        )

        box.addView(small("Texte"))
        val titleField = editField("Que faut-il faire ?", existing?.title ?: "", 3)
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(titleField, LinearLayout.LayoutParams(0, WRAP, 1f))
        titleRow.addView(
            smallButton("🎤") { startSpeech(titleField) },
            LinearLayout.LayoutParams(dp(46), dp(46))
        )
        box.addView(titleRow)

        box.addView(small("Date et heure"))
        val dateBtn = TextView(this).apply {
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = fieldBox()
            setPadding(dp(8), dp(12), dp(8), dp(12))
        }
        val timeBtn = TextView(this).apply {
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = fieldBox()
            setPadding(dp(8), dp(12), dp(8), dp(12))
        }
        fun paintDate() {
            dateBtn.text = SimpleDateFormat("EEE d MMM yyyy", Locale.FRANCE).format(cal.time)
            timeBtn.text = SimpleDateFormat("HH:mm", Locale.FRANCE).format(cal.time)
        }
        paintDate()
        dateBtn.setOnClickListener {
            DatePickerDialog(
                this,
                { _, y, m, d ->
                    cal.set(y, m, d)
                    paintDate()
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
        timeBtn.setOnClickListener {
            TimePickerDialog(
                this,
                { _, h, mi ->
                    cal.set(Calendar.HOUR_OF_DAY, h)
                    cal.set(Calendar.MINUTE, mi)
                    paintDate()
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                true
            ).show()
        }
        val dateRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        dateRow.addView(dateBtn, LinearLayout.LayoutParams(0, WRAP, 2f))
        dateRow.addView(
            timeBtn,
            LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = dp(8) }
        )
        box.addView(dateRow)

        box.addView(small("Répétition"))
        box.addView(
            segment(
                listOf("Jamais", "Jours", "Semaines", "Mois", "Ans"),
                repeatKeys.indexOf(repeat).coerceAtLeast(0),
                10,
                11f
            ) { repeat = repeatKeys[it] }
        )

        box.addView(small("Priorité"))
        box.addView(
            segment(listOf("Normal", "Important", "Urgent"), priority, 12, 13f) {
                priority = it
            }
        )

        box.addView(small("Dossier"))
        val dossierBtn = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.WHITE)
            background = fieldBox()
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        fun paintDossier() {
            val has = note.isNotEmpty() || link.isNotEmpty() || files.isNotEmpty()
            dossierBtn.text = if (has) "📎  Dossier  ●" else "📎  Dossier"
            dossierBtn.setTextColor(if (has) gold else Color.WHITE)
        }
        paintDossier()
        dossierBtn.setOnClickListener {
            showDossier(note, link, files) { n, l, f ->
                note = n
                link = l
                files = f
                paintDossier()
            }
        }
        box.addView(dossierBtn)

        val b = AlertDialog.Builder(this)
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("Enregistrer", null)
            .setNegativeButton("Annuler", null)
        if (existing != null) b.setNeutralButton("Supprimer", null)
        val dialog = b.create()
        dialog.show()
        dialog.window?.setBackgroundDrawable(rounded(navy2, 18))

        val pos = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        pos.setTextColor(gold)
        pos.setOnClickListener {
            val title = titleField.text.toString().trim()
            if (title.isEmpty()) {
                Toast.makeText(this, "Écris d'abord le texte", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val task = Task(
                id = existing?.id ?: 0,
                title = title,
                time = cal.timeInMillis,
                done = existing?.done ?: false,
                repeat = repeat,
                type = type,
                priority = priority,
                paused = existing?.paused ?: false,
                note = note,
                link = link,
                files = files
            )
            if (existing == null) {
                val created = TaskStore.add(this, task)
                Reminders.schedule(this, created)
            } else {
                TaskStore.update(this, task)
                Reminders.cancel(this, task.id)
                if (!task.done) Reminders.schedule(this, task)
            }
            dialog.dismiss()
            refresh()
        }

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(grayText)

        if (existing != null) {
            val del = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            del.setTextColor(red)
            del.setOnClickListener {
                confirmDelete {
                    deleteTask(existing)
                    dialog.dismiss()
                }
            }
        }
    }
}
