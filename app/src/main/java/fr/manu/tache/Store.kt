package fr.manu.tache

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Task(
    val id: Int,
    val title: String,
    val time: Long,
    val done: Boolean = false,
    val repeat: String = "none"
)

object TaskStore {
    private const val PREFS = "tache_prefs"
    private const val KEY = "tasks"
    private const val KEY_ID = "next_id"

    fun all(context: Context): MutableList<Task> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        val list = mutableListOf<Task>()
        val arr = JSONArray(raw)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                Task(
                    o.getInt("id"),
                    o.getString("title"),
                    o.getLong("time"),
                    o.optBoolean("done", false),
                    o.optString("repeat", "none")
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
            arr.put(o)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    fun get(context: Context, id: Int): Task? = all(context).firstOrNull { it.id == id }

    fun add(context: Context, title: String, time: Long, repeat: String): Task {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = prefs.getInt(KEY_ID, 1)
        prefs.edit().putInt(KEY_ID, id + 1).apply()
        val task = Task(id, title, time, false, repeat)
        val list = all(context)
        list.add(task)
        saveAll(context, list)
        return task
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
