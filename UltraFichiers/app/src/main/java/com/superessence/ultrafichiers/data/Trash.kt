package com.superessence.ultrafichiers.data

import android.content.Context
import java.io.File

data class TrashItem(val file: File, val originalPath: String, val deletedAt: Long, val size: Long)

/**
 * Corbeille de sécurité : les fichiers supprimés sont déplacés (renommage instantané,
 * même partition) puis purgés automatiquement après N jours.
 */
object Trash {
    val dir: File get() = File(Util.root, ".UltraFichiers/Corbeille")
    private val index: File get() = File(dir, ".index")

    @Synchronized
    private fun readIndex(): MutableMap<String, Pair<String, Long>> {
        val m = LinkedHashMap<String, Pair<String, Long>>()
        if (!index.exists()) return m
        index.readLines().forEach { l ->
            val parts = l.split('\t')
            if (parts.size == 3) m[parts[0]] = parts[1] to (parts[2].toLongOrNull() ?: 0L)
        }
        return m
    }

    @Synchronized
    private fun writeIndex(m: Map<String, Pair<String, Long>>) {
        dir.mkdirs()
        index.writeText(m.entries.joinToString("\n") { "${it.key}\t${it.value.first}\t${it.value.second}" })
    }

    /** Supprime (ou envoie à la corbeille) et renvoie le nombre d'octets libérés. */
    fun delete(ctx: Context, f: File): Long {
        if (!f.exists()) return 0
        val size = if (f.isDirectory) Util.dirSize(f) else f.length()
        val prefs = Prefs(ctx)
        if (prefs.useTrash && !f.absolutePath.startsWith(dir.absolutePath)) {
            dir.mkdirs()
            val name = System.currentTimeMillis().toString() + "_" + f.name
            val target = File(dir, name)
            val ok = f.renameTo(target) || (FileOps.copyRecursively(f, target) && f.deleteRecursively())
            if (ok) {
                synchronized(this) {
                    val m = readIndex()
                    m[name] = f.absolutePath to System.currentTimeMillis()
                    writeIndex(m)
                }
                return size
            }
        }
        f.deleteRecursively()
        return size
    }

    fun list(): List<TrashItem> {
        val m = readIndex()
        return (dir.listFiles() ?: emptyArray()).filter { it.name != ".index" }.map {
            val e = m[it.name]
            TrashItem(it, e?.first ?: it.name, e?.second ?: it.lastModified(), Util.dirSize(it))
        }.sortedByDescending { it.deletedAt }
    }

    fun restore(item: TrashItem): Boolean {
        var target = File(item.originalPath)
        target.parentFile?.mkdirs()
        if (target.exists()) target = FileOps.uniqueName(target)
        val ok = item.file.renameTo(target)
        if (ok) synchronized(this) { val m = readIndex(); m.remove(item.file.name); writeIndex(m) }
        return ok
    }

    fun purge(item: TrashItem) {
        item.file.deleteRecursively()
        synchronized(this) { val m = readIndex(); m.remove(item.file.name); writeIndex(m) }
    }

    fun emptyAll(): Long {
        val items = list()
        items.forEach { it.file.deleteRecursively() }
        writeIndex(emptyMap())
        return items.sumOf { it.size }
    }

    fun purgeOld(days: Int): Long {
        val limit = System.currentTimeMillis() - days * 86_400_000L
        var freed = 0L
        list().filter { it.deletedAt < limit }.forEach { freed += it.size; purge(it) }
        return freed
    }

    fun totalSize(): Long = list().sumOf { it.size }
}
