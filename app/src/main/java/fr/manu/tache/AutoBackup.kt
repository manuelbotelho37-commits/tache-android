package fr.manu.tache

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Sauvegarde automatique chaque jour, dans Téléchargements/Tache.
 * Les fichiers restent sur le téléphone même si l'appli est désinstallée,
 * et se rechargent avec « Restaurer une sauvegarde ».
 */
object AutoBackup {
    private const val PREFS = "tache_prefs"
    private const val KEY_LAST_AUTO = "last_auto_backup"
    private const val PREFIX = "Tache-sauvegarde-auto-"
    private const val LATEST = "Tache-sauvegarde-derniere.json"
    private val executor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
    private var pending: java.util.concurrent.ScheduledFuture<*>? = null
    private const val KEEP = 14
    private const val DAY_MS = 86_400_000L
    private const val WORK = "tache-auto-backup"
    private val folder = android.os.Environment.DIRECTORY_DOWNLOADS + "/Tache/"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun lastAuto(context: Context): Long = prefs(context).getLong(KEY_LAST_AUTO, 0L)

    /** Planifie la sauvegarde quotidienne (sans effet si elle est déjà planifiée). */
    fun schedule(context: Context) {
        try {
            val req = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        } catch (e: Exception) { }
    }

    /** Après chaque changement : met à jour « Tache-sauvegarde-derniere.json » (regroupé sur 3 secondes). */
    @Synchronized
    fun onChange(context: Context) {
        val app = context.applicationContext
        pending?.cancel(false)
        pending = executor.schedule(Runnable {
            try {
                val bytes = TaskStore.exportJson(app).toByteArray(Charsets.UTF_8)
                if (Build.VERSION.SDK_INT >= 29) writeMediaStore(app, LATEST, bytes, false)
                else writeLegacy(app, LATEST, bytes, false)
            } catch (e: Exception) { }
        }, 3, TimeUnit.SECONDS)
    }

    /** Sauvegarde si la dernière date de plus de 24 h. A appeler hors du fil principal. */
    fun runIfDue(context: Context): Boolean {
        if (System.currentTimeMillis() - lastAuto(context) < DAY_MS - 3_600_000L) return true
        return runNow(context)
    }

    /** Ecrit la sauvegarde du jour. A appeler hors du fil principal. */
    fun runNow(context: Context): Boolean {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date())
        val name = "$PREFIX$day.json"
        val bytes = TaskStore.exportJson(context).toByteArray(Charsets.UTF_8)
        val ok = try {
            if (Build.VERSION.SDK_INT >= 29) writeMediaStore(context, name, bytes, true) else writeLegacy(context, name, bytes, true)
            true
        } catch (e: Exception) {
            false
        }
        if (ok) prefs(context).edit().putLong(KEY_LAST_AUTO, System.currentTimeMillis()).commit()
        return ok
    }

    private fun writeMediaStore(context: Context, name: String, bytes: ByteArray, prune: Boolean) {
        val cr = context.contentResolver
        val coll = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        // Fichiers de sauvegarde auto déjà créés par l'appli, du plus récent au plus ancien.
        val found = mutableListOf<Pair<Uri, String>>()
        cr.query(
            coll,
            arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME),
            MediaStore.Downloads.RELATIVE_PATH + "=? AND " + MediaStore.Downloads.DISPLAY_NAME + " LIKE ?",
            arrayOf(folder, if (prune) "$PREFIX%" else name),
            MediaStore.Downloads.DISPLAY_NAME + " DESC"
        )?.use { c ->
            while (c.moveToNext()) {
                found.add(Pair(android.content.ContentUris.withAppendedId(coll, c.getLong(0)), c.getString(1)))
            }
        }
        val today = found.firstOrNull { it.second == name }?.first
        val uri = today ?: cr.insert(coll, ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, folder)
        }) ?: throw Exception("insert")
        cr.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: throw Exception("flux")
        if (!prune) return
        // Garder seulement les 14 derniers jours.
        val all = (found.map { it.second } + name).distinct().sortedDescending()
        val keep = all.take(KEEP).toSet()
        for ((u, n) in found) {
            if (n !in keep) try { cr.delete(u, null, null) } catch (e: Exception) { }
        }
    }

    private fun writeLegacy(context: Context, name: String, bytes: ByteArray, prune: Boolean) {
        val dir = File(context.getExternalFilesDir(null), "Tache").apply { mkdirs() }
        File(dir, name).writeBytes(bytes)
        if (!prune) return
        val files = dir.listFiles { f -> f.name.startsWith(PREFIX) }?.sortedByDescending { it.name } ?: emptyList()
        files.drop(KEEP).forEach { it.delete() }
    }
}

class AutoBackupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result =
        if (AutoBackup.runIfDue(applicationContext)) Result.success() else Result.retry()
}
