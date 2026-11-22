package com.superessence.ultrafichiers.data

import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class FileKind { FOLDER, IMAGE, VIDEO, AUDIO, DOCUMENT, ARCHIVE, APK, CODE, OTHER }

object Util {
    val root: File get() = Environment.getExternalStorageDirectory()

    private val images = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg", "raw", "dng")
    private val videos = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v", "ts", "flv", "wmv", "mpeg", "mpg")
    private val audios = setOf("mp3", "wav", "ogg", "m4a", "aac", "flac", "opus", "amr", "wma", "mid")
    private val docs = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "odt", "ods", "odp", "rtf", "csv", "epub", "md")
    private val archives = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")
    private val code = setOf("json", "xml", "html", "htm", "css", "js", "kt", "java", "py", "c", "cpp", "h", "sh", "yml", "yaml", "ini", "cfg", "log", "properties")

    fun kind(f: File): FileKind = if (f.isDirectory) FileKind.FOLDER else kindOf(f.name)

    fun kindOf(name: String): FileKind {
        val e = name.substringAfterLast('.', "").lowercase()
        return when (e) {
            in images -> FileKind.IMAGE
            in videos -> FileKind.VIDEO
            in audios -> FileKind.AUDIO
            in docs -> FileKind.DOCUMENT
            in archives -> FileKind.ARCHIVE
            "apk", "apks", "xapk" -> FileKind.APK
            in code -> FileKind.CODE
            else -> FileKind.OTHER
        }
    }

    fun isVideo(f: File) = kindOf(f.name) == FileKind.VIDEO

    fun size(b: Long): String {
        if (b < 1024) return "$b o"
        val u = arrayOf("Ko", "Mo", "Go", "To")
        var v = b / 1024.0
        var i = 0
        while (v >= 1024 && i < u.size - 1) { v /= 1024; i++ }
        return if (v >= 100) String.format(Locale.FRANCE, "%.0f %s", v, u[i])
        else String.format(Locale.FRANCE, "%.1f %s", v, u[i])
    }

    fun date(t: Long): String = SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.FRANCE).format(Date(t))

    fun mime(f: File): String {
        val e = f.extension.lowercase()
        return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(e) ?: when (kind(f)) {
            FileKind.APK -> "application/vnd.android.package-archive"
            else -> "*/*"
        }
    }

    /** Dossiers système à ne jamais parcourir pour le nettoyage. */
    fun isProtected(f: File): Boolean {
        val p = f.absolutePath
        val r = root.absolutePath
        return p == "$r/Android" || p.startsWith("$r/Android/obb") || p.startsWith(Trash.dir.absolutePath)
    }

    fun dirSize(f: File): Long {
        if (!f.isDirectory) return f.length()
        var s = 0L
        f.walkTopDown().onEnter { !isProtected(it) || it == f }.forEach { if (it.isFile) s += it.length() }
        return s
    }
}
