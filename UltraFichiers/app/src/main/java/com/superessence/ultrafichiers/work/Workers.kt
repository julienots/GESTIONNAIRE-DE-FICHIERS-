package com.superessence.ultrafichiers.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.superessence.ultrafichiers.R
import com.superessence.ultrafichiers.data.Prefs
import com.superessence.ultrafichiers.data.Scanner
import com.superessence.ultrafichiers.data.Trash
import com.superessence.ultrafichiers.data.Util
import com.superessence.ultrafichiers.video.VideoOptimizer
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.TimeUnit

object Notif {
    const val CH = "ultra_tasks"
    fun channel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH, "Tâches Ultra Fichiers", NotificationManager.IMPORTANCE_LOW))
        }
    }

    fun build(ctx: Context, title: String, text: String, progress: Int = -1) =
        NotificationCompat.Builder(ctx, CH)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title).setContentText(text)
            .setOnlyAlertOnce(true)
            .apply { if (progress >= 0) setProgress(100, progress, false).setOngoing(true) }
            .build()

    fun show(ctx: Context, id: Int, title: String, text: String) {
        try {
            ctx.getSystemService(NotificationManager::class.java).notify(id, build(ctx, title, text))
        } catch (_: Exception) {}
    }
}

object Scheduler {
    const val VIDEO = "video_now"
    const val MAGIC = "magic_folder"
    const val AUTO = "auto_clean"

    fun apply(ctx: Context) {
        val p = Prefs(ctx)
        val wm = WorkManager.getInstance(ctx)
        if (p.autoClean) {
            wm.enqueueUniquePeriodicWork(
                AUTO, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<AutoCleanWorker>(1, TimeUnit.DAYS)
                    .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                    .setInitialDelay(1, TimeUnit.HOURS).build()
            )
        } else wm.cancelUniqueWork(AUTO)

        if (p.magicEnabled) {
            File(p.magicFolder).mkdirs()
            wm.enqueueUniquePeriodicWork(
                MAGIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<VideoWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build()).build()
            )
        } else wm.cancelUniqueWork(MAGIC)
    }

    /** Ajoute des vidéos à la file et lance le traitement immédiatement. */
    fun processVideos(ctx: Context, files: List<File>) {
        val q = File(ctx.filesDir, "video_queue.txt")
        val existing = if (q.exists()) q.readLines().toMutableSet() else mutableSetOf()
        existing.addAll(files.map { it.absolutePath })
        q.writeText(existing.joinToString("\n"))
        runNow(ctx)
    }

    fun runNow(ctx: Context) {
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            VIDEO, ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<VideoWorker>().build()
        )
    }
}

class VideoWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = lock.withLock { run() }

    private suspend fun run(): Result {
        val ctx = applicationContext
        val prefs = Prefs(ctx)
        val q = File(ctx.filesDir, "video_queue.txt")
        val todo = LinkedHashSet<File>()
        if (q.exists()) q.readLines().filter { it.isNotBlank() }.forEach { todo.add(File(it)) }
        if (prefs.magicEnabled) {
            val dir = File(prefs.magicFolder)
            dir.mkdirs()
            val candidates = dir.walkTopDown().filter {
                it.isFile && Util.isVideo(it) && !it.name.startsWith(".") && !prefs.isProcessed(it)
            }.associateWith { it.length() }
            if (candidates.isNotEmpty()) {
                delay(4000) // on ignore les vidéos encore en cours de copie
                candidates.forEach { (f, len) -> if (f.length() == len && len > 0) todo.add(f) }
            }
        }
        val list = todo.filter { it.exists() }
        if (list.isEmpty()) { q.delete(); return Result.success() }

        Notif.channel(ctx)
        val opt = VideoOptimizer(ctx).let { it to it.optionsFromPrefs() }
        var saved = 0L
        var okCount = 0
        list.forEachIndexed { i, f ->
            val title = "Optimisation vidéo ${i + 1}/${list.size}"
            safeForeground(title, f.name, 0)
            val r = opt.first.process(f, opt.second) { p, step ->
                setProgressAsync(workDataOf("i" to i, "n" to list.size, "name" to f.name, "p" to p, "step" to step, "saved" to saved))
                safeForegroundAsync(title, "${f.name} · $step", (p * 100).toInt())
            }
            if (r.ok) { saved += r.saved; okCount++ } else if (f.exists()) prefs.markProcessed(f) // pas de boucle infinie sur un fichier en erreur
            appendLog(ctx, "${r.name}\t${r.before}\t${r.after}\t${r.ok}\t${r.message}")
            // retire de la file
            if (q.exists()) q.writeText(q.readLines().filter { it != f.absolutePath }.joinToString("\n"))
        }
        Notif.show(ctx, 2, "Vidéos optimisées : $okCount/${list.size}", "Espace gagné : ${Util.size(saved)}")
        return Result.success(workDataOf("saved" to saved, "ok" to okCount, "n" to list.size))
    }

    private var lastFg = 0L
    private fun safeForegroundAsync(title: String, text: String, p: Int) {
        val now = System.currentTimeMillis()
        if (now - lastFg < 1000) return
        lastFg = now
        try { setForegroundAsync(info(title, text, p)) } catch (_: Exception) {}
    }

    private suspend fun safeForeground(title: String, text: String, p: Int) {
        try { setForeground(info(title, text, p)) } catch (_: Exception) {}
    }

    private fun info(title: String, text: String, p: Int): ForegroundInfo {
        val n = Notif.build(applicationContext, title, text, p)
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(1, n)
    }

    companion object {
        private val lock = Mutex()

        fun appendLog(ctx: Context, line: String) {
            val f = File(ctx.filesDir, "video_log.txt")
            val lines = (if (f.exists()) f.readLines() else emptyList()) + (System.currentTimeMillis().toString() + "\t" + line)
            f.writeText(lines.takeLast(200).joinToString("\n"))
        }

        fun readLog(ctx: Context): List<List<String>> {
            val f = File(ctx.filesDir, "video_log.txt")
            return if (f.exists()) f.readLines().map { it.split('\t') }.filter { it.size >= 6 }.reversed() else emptyList()
        }
    }
}

class AutoCleanWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val p = Prefs(ctx)
        if (!p.autoClean) return Result.success()
        val r = Scanner(ctx).scan()
        val toDelete = ArrayList<File>()
        if (p.autoDuplicates) r.duplicates.forEach { toDelete.addAll(it.copies) }
        r.groups.filter { it.safe }.forEach { g ->
            if (g.id == "apk" && !p.autoApks) return@forEach
            if (!p.autoJunk && g.id != "apk") return@forEach
            toDelete.addAll(g.files)
        }
        val freed = Scanner.clean(ctx, toDelete) + Trash.purgeOld(p.trashDays)
        p.lastAutoClean = System.currentTimeMillis()
        Notif.channel(ctx)
        if (freed > 0) Notif.show(ctx, 3, "Nettoyage automatique terminé", "${Util.size(freed)} libérés · ${toDelete.size} éléments")
        return Result.success()
    }
}
