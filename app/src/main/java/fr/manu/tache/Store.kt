package fr.manu.tache

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class Task(
    val id: Int,
    val title: String,
    val time: Long,
    val done: Boolean = false,
    val repeat: String = "none",
    val type: String = "task",
    val priority: Int = 0,
    val paused: Boolean = false,
    val note: String = "",
    val link: String = "",
    val files: List<String> = emptyList()
)

object TaskStore {
    private const val PREFS = "tache_prefs"
    private const val KEY = "tasks"
    private const val KEY_ID = "next_id"
    private const val KEY_SEEDED = "seeded_v1"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun at(y: Int, m: Int, d: Int, h: Int, mi: Int): Long {
        val c = Calendar.getInstance()
        c.clear()
        c.set(y, m - 1, d, h, mi, 0)
        return c.timeInMillis
    }

    private fun seedIfNeeded(context: Context) {
        val p = prefs(context)
        if (p.getBoolean(KEY_SEEDED, false)) return
        p.edit().putBoolean(KEY_SEEDED, true).commit()
        if ((p.getString(KEY, "[]") ?: "[]") != "[]") return

        val base = listOf(
            Task(0, "appeler Laurent pour caller un rendez-vous pour la visite du terrain",
                at(2026, 10, 6, 8, 55)),
            Task(0, "Rdv domicile Mr et Mme Marquenet 29 rue des grilles a fondettes a 10 h le mercredi 07 oct",
                at(2026, 10, 6, 20, 3)),
            Task(0, "voir comment dessiner le toit Brohan",
                at(2026, 10, 14, 9, 10)),
            Task(0, "Remplacement interphone jeudi 29 octobre de 8h30 a 17h\nSatellite 37 : 02.47.50.78.48",
                at(2026, 10, 28, 10, 12), priority = 2),
            Task(0, "Déclaration mensuelle URSSAF",
                at(2026, 11, 1, 9, 5), repeat = "month"),
            Task(0, "Payer loyer",
                at(2026, 11, 1, 9, 10), repeat = "month"),
            Task(0, "Pension alimentaire 888 €",
                at(2026, 11, 1, 9, 10), repeat = "month")
        )
        var id = p.getInt(KEY_ID, 1)
        val list = mutableListOf<Task>()
        for (t in base) {
            list.add(t.copy(id = id))
            id++
        }
        p.edit().putInt(KEY_ID, id).commit()
        saveAll(context, list)
    }

    fun all(context: Context): MutableList<Task> {
        seedIfNeeded(context)
        return readAll(context)
    }

    private fun readAll(context: Context): MutableList<Task> {
        val raw = prefs(context).getString(KEY, "[]") ?: "[]"
        val list = mutableListOf<Task>()
        val arr = JSONArray(raw)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val r = o.optString("repeat", "none")
            val rep = when (r) {
                "daily" -> "day"
                "weekly" -> "week"
                else -> r
            }
            val files = mutableListOf<String>()
            val fa = o.optJSONArray("files")
            if (fa != null) {
                for (k in 0 until fa.length()) files.add(fa.getString(k))
            }
            list.add(
                Task(
                    id = o.getInt("id"),
                    title = o.getString("title"),
                    time = o.getLong("time"),
                    done = o.optBoolean("done", false),
                    repeat = rep,
                    type = o.optString("type", "task"),
                    priority = o.optInt("priority", 0),
                    paused = o.optBoolean("paused", false),
                    note = o.optString("note", ""),
                    link = o.optString("link", ""),
                    files = files
                )
            )
        }
        return list
    }

    private fun saveAll(context: Context, list: List<Task>) {
        val arr = JSONArray()
        for (t in list) {
            val o = JSONObject()
            o.put("id", t.id)
            o.put("title", t.title)
            o.put("time", t.time)
            o.put("done", t.done)
            o.put("repeat", t.repeat)
            o.put("type", t.type)
            o.put("priority", t.priority)
            o.put("paused", t.paused)
            o.put("note", t.note)
            o.put("link", t.link)
            val fa = JSONArray()
            for (f in t.files) fa.put(f)
            o.put("files", fa)
            arr.put(o)
        }
        prefs(context).edit().putString(KEY, arr.toString()).commit()
    }

    fun get(context: Context, id: Int): Task? = all(context).firstOrNull { it.id == id }

    fun add(context: Context, task: Task): Task {
        val p = prefs(context)
        val list = all(context)
        val id = p.getInt(KEY_ID, 1)
        p.edit().putInt(KEY_ID, id + 1).commit()
        val created = task.copy(id = id)
        list.add(created)
        saveAll(context, list)
        return created
    }

    fun update(context: Context, task: Task) {
        val list = all(context)
        val index = list.indexOfFirst { it.id == task.id }
        if (index >= 0) list[index] = task else list.add(task)
        saveAll(context, list)
    }

    fun delete(context: Context, id: Int) {
        saveAll(context, all(context).filter { it.id != id })
    }

    /* ---------- Sauvegarde / Restauration ---------- */
    private const val KEY_LAST_BACKUP = "last_backup"
    private const val KEY_BACKUP_START = "backup_start"
    const val BACKUP_DAYS = 60

    fun exportJson(context: Context): String {
        val o = JSONObject()
        o.put("app", "tache")
        o.put("version", 1)
        o.put("date", System.currentTimeMillis())
        o.put("next_id", prefs(context).getInt(KEY_ID, 1))
        o.put("tasks", JSONArray(prefs(context).getString(KEY, "[]") ?: "[]"))
        return o.toString()
    }

    /** Remplace toutes les tâches par celles du fichier. Renvoie le nombre de tâches, ou -1 si le fichier n'est pas reconnu. */
    fun importJson(context: Context, raw: String): Int {
        return try {
            val o = JSONObject(raw)
            if (o.optString("app") != "tache") return -1
            val arr = o.getJSONArray("tasks")
            var maxId = 0
            for (i in 0 until arr.length()) {
                val t = arr.getJSONObject(i)
                t.getString("title"); t.getLong("time")
                maxId = maxOf(maxId, t.getInt("id"))
            }
            val p = prefs(context)
            val next = maxOf(o.optInt("next_id", 1), maxId + 1, p.getInt(KEY_ID, 1))
            p.edit().putString(KEY, arr.toString()).putInt(KEY_ID, next).putBoolean(KEY_SEEDED, true).commit()
            arr.length()
        } catch (e: Exception) {
            -1
        }
    }

    fun lastBackup(context: Context): Long = prefs(context).getLong(KEY_LAST_BACKUP, 0L)

    fun markBackup(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_BACKUP, System.currentTimeMillis()).commit()
    }

    /** Vrai quand la dernière sauvegarde (ou la première utilisation) date de plus de 2 mois. */
    fun backupDue(context: Context): Boolean {
        val p = prefs(context)
        var since = p.getLong(KEY_LAST_BACKUP, 0L)
        if (since == 0L) {
            since = p.getLong(KEY_BACKUP_START, 0L)
            if (since == 0L) {
                p.edit().putLong(KEY_BACKUP_START, System.currentTimeMillis()).commit()
                return false
            }
        }
        return System.currentTimeMillis() - since >= BACKUP_DAYS * 86_400_000L
    }
}
