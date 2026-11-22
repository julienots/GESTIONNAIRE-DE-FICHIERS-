package com.superessence.ultrafichiers.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.security.MessageDigest

/** Un lot de fichiers nettoyables. */
data class CleanGroup(
    val id: String,
    val title: String,
    val desc: String,
    val files: List<File>,
    val bytes: Long,
    /** true = supprimable sans risque, coché par défaut et utilisé par le mode auto. */
    val safe: Boolean,
)

/** Ensemble de fichiers strictement identiques (même contenu octet par octet). */
data class DupGroup(val keep: File, val copies: List<File>, val size: Long) {
    val wasted get() = size * copies.size
}

data class ScanResult(
    val duplicates: List<DupGroup>,
    val groups: List<CleanGroup>,
    val bigFiles: List<File>,
    val heavyVideos: List<File>,
    val oldDownloads: List<File>,
    val scannedFiles: Int,
    val durationMs: Long,
) {
    val dupBytes get() = duplicates.sumOf { it.wasted }
    val safeBytes get() = dupBytes + groups.filter { it.safe }.sumOf { it.bytes }
}

class Scanner(private val ctx: Context) {

    private val tmpExt = setOf("tmp", "temp", "log", "crdownload", "part", "partial", "download", "dmp", "bak~", "old")
    private val junkNames = setOf("thumbs.db", ".ds_store", "desktop.ini", ".trashed")
    private val standardDirs = setOf(
        "DCIM", "Pictures", "Music", "Movies", "Download", "Documents", "Alarms", "Ringtones",
        "Notifications", "Podcasts", "Audiobooks", "Recordings", "Android", "Ultra Fichiers", "Optimiser"
    )

    fun scan(onProgress: (String, Float) -> Unit = { _, _ -> }): ScanResult {
        val t0 = System.currentTimeMillis()
        val root = Util.root
        val files = ArrayList<File>(20000)
        val emptyDirs = ArrayList<File>()
        val thumbs = ArrayList<File>()
        val appCaches = ArrayList<File>()

        onProgress("Analyse du stockage…", 0.02f)
        // 1) Parcours complet (hors zones protégées)
        val walker = root.walkTopDown().onEnter { d ->
            if (d == root) return@onEnter true
            val rel = d.absolutePath.removePrefix(root.absolutePath + "/")
            when {
                Util.isProtected(d) -> false
                rel == "Android/data" || rel == "Android/media" -> false
                d.name.equals(".thumbnails", true) || d.name.equals(".thumbnail", true) -> {
                    d.walkTopDown().filter { it.isFile }.forEach { thumbs.add(it) }; false
                }
                else -> true
            }
        }
        var n = 0
        for (f in walker) {
            if (f.isFile) {
                files.add(f)
                if (++n % 500 == 0) onProgress("Analyse : $n fichiers", 0.02f + (n % 20000) / 20000f * 0.2f)
            } else if (f != root && f.isDirectory) {
                val c = f.list()
                if (c != null && c.isEmpty() && f.name !in standardDirs && f.parentFile != File(root, "Android")) emptyDirs.add(f)
            }
        }

        // 2) Caches d'applications accessibles (anciens Android / appareils permissifs)
        File(root, "Android/data").listFiles()?.forEach { app ->
            val c = File(app, "cache")
            if (c.isDirectory) c.listFiles()?.let { appCaches.addAll(it) }
        }
        ctx.cacheDir.listFiles()?.let { appCaches.addAll(it) }
        ctx.externalCacheDir?.listFiles()?.let { appCaches.addAll(it) }

        onProgress("Classement des fichiers…", 0.25f)
        val now = System.currentTimeMillis()
        val temp = ArrayList<File>()
        val empty = ArrayList<File>()
        val apks = ArrayList<File>()
        val big = ArrayList<File>()
        val heavyVideos = ArrayList<File>()
        val oldDl = ArrayList<File>()
        val dl = File(root, "Download").absolutePath
        for (f in files) {
            val name = f.name.lowercase()
            val ext = name.substringAfterLast('.', "")
            val len = f.length()
            val hiddenPath = isInHiddenDir(f, root)
            when {
                name in junkNames -> temp.add(f)
                ext in tmpExt && now - f.lastModified() > 86_400_000L -> temp.add(f)
                len == 0L && !name.startsWith(".") && !hiddenPath -> empty.add(f)
            }
            if (ext == "apk") apks.add(f)
            if (len >= 100L * 1024 * 1024) big.add(f)
            if (Util.isVideo(f) && len >= 80L * 1024 * 1024 && !hiddenPath) heavyVideos.add(f)
            if (f.absolutePath.startsWith(dl) && now - f.lastModified() > 90L * 86_400_000L) oldDl.add(f)
        }

        onProgress("Vérification des APK…", 0.3f)
        val installedApks = apks.filter { isInstalledApk(it) }

        // 3) Vrais doublons
        val dups = findDuplicates(files.filter { !isInHiddenDir(it, root) && it.length() > 0 }) { p ->
            onProgress("Recherche des doublons…", 0.35f + p * 0.6f)
        }

        val groups = listOf(
            CleanGroup("temp", "Fichiers temporaires & journaux", "Téléchargements interrompus, .tmp, .log, Thumbs.db…", temp, temp.sumOf { it.length() }, true),
            CleanGroup("thumbs", "Cache de miniatures", "Aperçus régénérés automatiquement par le système", thumbs, thumbs.sumOf { it.length() }, true),
            CleanGroup("cache", "Caches d'applications", "Données temporaires d'applis, recréées si besoin", appCaches, appCaches.sumOf { Util.dirSize(it) }, true),
            CleanGroup("apk", "APK déjà installés", "Fichiers d'installation d'applis déjà présentes sur le téléphone", installedApks, installedApks.sumOf { it.length() }, true),
            CleanGroup("empty", "Fichiers vides", "Fichiers de 0 octet inutiles", empty, 0, true),
            CleanGroup("emptyDirs", "Dossiers vides", "Dossiers ne contenant rien", emptyDirs, 0, true),
            CleanGroup("oldDl", "Anciens téléchargements", "Téléchargements de plus de 90 jours (à vérifier)", oldDl, oldDl.sumOf { it.length() }, false),
        ).filter { it.files.isNotEmpty() }

        onProgress("Terminé", 1f)
        return ScanResult(
            dups, groups,
            big.sortedByDescending { it.length() }.take(100),
            heavyVideos.sortedByDescending { it.length() },
            oldDl, files.size, System.currentTimeMillis() - t0
        )
    }

    private fun isInHiddenDir(f: File, root: File): Boolean {
        var p = f.parentFile
        while (p != null && p != root) {
            if (p.name.startsWith(".")) return true
            p = p.parentFile
        }
        return false
    }

    @Suppress("DEPRECATION")
    private fun isInstalledApk(f: File): Boolean = try {
        val pm = ctx.packageManager
        val info = pm.getPackageArchiveInfo(f.absolutePath, 0)
        if (info == null) false else {
            val inst = pm.getPackageInfo(info.packageName, 0)
            val a = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
            val b = if (Build.VERSION.SDK_INT >= 28) inst.longVersionCode else inst.versionCode.toLong()
            b >= a
        }
    } catch (e: PackageManager.NameNotFoundException) { false } catch (e: Exception) { false }

    /**
     * Doublons stricts en 3 passes : même taille → même empreinte rapide (début+fin)
     * → même SHA-256 complet. Aucun faux positif possible.
     */
    fun findDuplicates(files: List<File>, onProgress: (Float) -> Unit = {}): List<DupGroup> {
        val bySize = files.groupBy { it.length() }.filter { it.value.size > 1 }
        val candidates = ArrayList<List<File>>()
        bySize.values.forEach { same ->
            same.groupBy { quickHash(it) }.values.filter { it.size > 1 }.forEach { candidates.add(it) }
        }
        val result = ArrayList<DupGroup>()
        candidates.forEachIndexed { i, group ->
            onProgress(i.toFloat() / candidates.size.coerceAtLeast(1))
            group.groupBy { fullHash(it) }.filter { it.key.isNotEmpty() && it.value.size > 1 }.values.forEach { same ->
                val sorted = same.sortedByDescending { keepScore(it) }
                result.add(DupGroup(sorted.first(), sorted.drop(1), sorted.first().length()))
            }
        }
        return result.sortedByDescending { it.wasted }
    }

    /** Choisit intelligemment l'original à garder. */
    private fun keepScore(f: File): Double {
        val p = f.absolutePath.lowercase()
        val n = f.name.lowercase()
        var s = 0.0
        if ("/dcim/" in p) s += 4
        if ("/pictures/" in p || "/documents/" in p || "/music/" in p || "/movies/" in p) s += 2
        if ("/download" in p) s -= 1
        if ("whatsapp" in p && "/sent" in p) s -= 2
        if (Regex("""\(\d+\)""").containsMatchIn(n) || "copy" in n || "copie" in n || n.matches(Regex(""".*[-_ ]\d\.\w+$"""))) s -= 3
        s -= p.count { it == '/' } * 0.05
        // le plus ancien est généralement l'original
        s += (Long.MAX_VALUE - f.lastModified()).toDouble() / 1e17
        return s
    }

    private fun quickHash(f: File): String = try {
        val md = MessageDigest.getInstance("MD5")
        RandomAccessFile(f, "r").use { r ->
            val buf = ByteArray(16 * 1024)
            val len = r.length()
            var read = r.read(buf); if (read > 0) md.update(buf, 0, read)
            if (len > buf.size * 2) {
                r.seek(len / 2); read = r.read(buf); if (read > 0) md.update(buf, 0, read)
                r.seek(len - buf.size); read = r.read(buf); if (read > 0) md.update(buf, 0, read)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) { "" + f.absolutePath }

    private fun fullHash(f: File): String = try {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(f).use { ins ->
            val buf = ByteArray(256 * 1024)
            while (true) { val r = ins.read(buf); if (r <= 0) break; md.update(buf, 0, r) }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) { "" }

    companion object {
        /** Nettoie les éléments donnés et renvoie les octets libérés. */
        fun clean(ctx: Context, files: List<File>): Long {
            var freed = 0L
            files.forEach { f ->
                freed += if (f.absolutePath.startsWith(ctx.cacheDir.absolutePath) ||
                    (ctx.externalCacheDir != null && f.absolutePath.startsWith(ctx.externalCacheDir!!.absolutePath)) ||
                    f.absolutePath.contains("/Android/data/") || f.absolutePath.contains("/.thumbnails")
                ) {
                    // cache : suppression directe, pas de corbeille
                    val s = Util.dirSize(f); f.deleteRecursively(); s
                } else Trash.delete(ctx, f)
            }
            Prefs(ctx).totalFreed += freed
            return freed
        }
    }
}
