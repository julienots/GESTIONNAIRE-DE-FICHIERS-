package com.superessence.ultrafichiers.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object FileOps {
    fun uniqueName(f: File): File {
        if (!f.exists()) return f
        val base = f.nameWithoutExtension
        val ext = if (f.isDirectory || f.extension.isEmpty()) "" else "." + f.extension
        var i = 1
        while (true) {
            val c = File(f.parentFile, "$base ($i)$ext")
            if (!c.exists()) return c
            i++
        }
    }

    fun copyRecursively(src: File, dst: File): Boolean = try {
        src.copyRecursively(dst, overwrite = false); true
    } catch (e: Exception) { false }

    fun copy(src: File, destDir: File): Boolean {
        val target = uniqueName(File(destDir, src.name))
        if (target.absolutePath.startsWith(src.absolutePath + "/")) return false
        return copyRecursively(src, target)
    }

    fun move(src: File, destDir: File): Boolean {
        val target = uniqueName(File(destDir, src.name))
        if (target.absolutePath.startsWith(src.absolutePath + "/")) return false
        if (src.renameTo(target)) return true
        return copyRecursively(src, target) && src.deleteRecursively()
    }

    fun rename(f: File, newName: String): File? {
        val t = File(f.parentFile, newName.trim())
        if (newName.isBlank() || t.exists()) return null
        return if (f.renameTo(t)) t else null
    }

    fun zip(files: List<File>, out: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { zos ->
            files.forEach { root ->
                val base = root.parentFile?.absolutePath?.length?.plus(1) ?: 0
                root.walkTopDown().forEach { f ->
                    val name = f.absolutePath.substring(base) + if (f.isDirectory) "/" else ""
                    zos.putNextEntry(ZipEntry(name))
                    if (f.isFile) BufferedInputStream(FileInputStream(f)).use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
    }

    fun unzip(zip: File, destDir: File) {
        val canonical = destDir.canonicalPath
        ZipInputStream(BufferedInputStream(FileInputStream(zip))).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                val out = File(destDir, e.name)
                if (!out.canonicalPath.startsWith(canonical)) throw SecurityException("Zip invalide")
                if (e.isDirectory) out.mkdirs() else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { zis.copyTo(it) }
                }
                e = zis.nextEntry
            }
        }
    }

    fun uri(ctx: Context, f: File): Uri =
        FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)

    fun open(ctx: Context, f: File) {
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri(ctx, f), Util.mime(f))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(Intent.createChooser(i, "Ouvrir avec").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
    }

    fun share(ctx: Context, files: List<File>) {
        val uris = ArrayList(files.filter { it.isFile }.map { uri(ctx, it) })
        if (uris.isEmpty()) return
        val i = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0]).setType(Util.mime(files[0]))
        else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris).setType("*/*")
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "Partager").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
