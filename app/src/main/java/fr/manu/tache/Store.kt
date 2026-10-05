package fr.manu.tache

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

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

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(context: Context): MutableList<Task> {
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
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }

    fun get(context: Context, id: Int): Task? = all(context).firstOrNull { it.id == id }

    fun add(context: Context, task: Task): Task {
        val p = prefs(context)
        val id = p.getInt(KEY_ID, 1)
        p.edit().putInt(KEY_ID, id + 1).apply()
        val created = task.copy(id = id)
        val list = all(context)
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
}
