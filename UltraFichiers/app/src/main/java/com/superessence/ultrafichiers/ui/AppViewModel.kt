package com.superessence.ultrafichiers.ui

import android.app.Application
import android.media.MediaScannerConnection
import android.os.StatFs
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.superessence.ultrafichiers.data.FileKind
import com.superessence.ultrafichiers.data.FileOps
import com.superessence.ultrafichiers.data.Prefs
import com.superessence.ultrafichiers.data.ScanResult
import com.superessence.ultrafichiers.data.Scanner
import com.superessence.ultrafichiers.data.Trash
import com.superessence.ultrafichiers.data.Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Cat(val label: String, val icon: ImageVector, val color: Color) {
    IMAGES("Images", Icons.Rounded.Image, kindColor(FileKind.IMAGE)),
    VIDEOS("Vidéos", Icons.Rounded.Movie, kindColor(FileKind.VIDEO)),
    AUDIO("Audio", Icons.Rounded.MusicNote, kindColor(FileKind.AUDIO)),
    DOCS("Documents", Icons.Rounded.Description, kindColor(FileKind.DOCUMENT)),
    ARCHIVES("Archives", Icons.Rounded.Archive, kindColor(FileKind.ARCHIVE)),
    APK("APK", Icons.Rounded.Android, kindColor(FileKind.APK)),
    DOWNLOADS("Téléchargements", Icons.Rounded.Download, Color(0xFF64B5F6)),
    BIG("Gros fichiers", Icons.Rounded.Storage, Color(0xFFFF8A65)),
}

sealed class Overlay {
    data class Category(val cat: Cat) : Overlay()
    data object Trash : Overlay()
    data object Settings : Overlay()
    data object VideoPicker : Overlay()
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()
    val prefs = Prefs(app)

    // Navigation
    var tab by mutableStateOf(0)
    val overlays = mutableStateListOf<Overlay>()
    fun push(o: Overlay) { overlays.add(o) }
    fun pop(): Boolean = if (overlays.isNotEmpty()) { overlays.removeAt(overlays.lastIndex); true } else false

    var message by mutableStateOf<String?>(null)
    fun toast(m: String) { message = m }

    // Stockage
    var total by mutableLongStateOf(0L)
    var free by mutableLongStateOf(0L)
    var totalFreed by mutableLongStateOf(prefs.totalFreed)

    fun refreshStorage() {
        try {
            val s = StatFs(Util.root.absolutePath)
            total = s.totalBytes; free = s.availableBytes
        } catch (_: Exception) {}
        totalFreed = prefs.totalFreed
    }

    // Index par catégories
    var indexing by mutableStateOf(false)
    var cats by mutableStateOf<Map<Cat, List<File>>>(emptyMap())
    var recent by mutableStateOf<List<File>>(emptyList())

    fun index() {
        if (indexing) return
        indexing = true
        viewModelScope.launch(Dispatchers.IO) {
            val root = Util.root
            val m = Cat.entries.associateWith { ArrayList<File>() }
            val all = ArrayList<File>()
            val dl = File(root, "Download").absolutePath
            root.walkTopDown().onEnter {
                it == root || (!it.name.startsWith(".") && !Util.isProtected(it) && it.absolutePath != "$root/Android/data")
            }.forEach { f ->
                if (!f.isFile || f.name.startsWith(".")) return@forEach
                all.add(f)
                when (Util.kind(f)) {
                    FileKind.IMAGE -> m[Cat.IMAGES]!!.add(f)
                    FileKind.VIDEO -> m[Cat.VIDEOS]!!.add(f)
                    FileKind.AUDIO -> m[Cat.AUDIO]!!.add(f)
                    FileKind.DOCUMENT -> m[Cat.DOCS]!!.add(f)
                    FileKind.ARCHIVE -> m[Cat.ARCHIVES]!!.add(f)
                    FileKind.APK -> m[Cat.APK]!!.add(f)
                    else -> {}
                }
                if (f.absolutePath.startsWith(dl)) m[Cat.DOWNLOADS]!!.add(f)
                if (f.length() >= 50L * 1024 * 1024) m[Cat.BIG]!!.add(f)
            }
            val sorted = m.mapValues { (k, v) -> if (k == Cat.BIG) v.sortedByDescending { it.length() } else v.sortedByDescending { it.lastModified() } }
            val rec = all.sortedByDescending { it.lastModified() }.take(12)
            withContext(Dispatchers.Main) { cats = sorted; recent = rec; indexing = false }
        }
    }

    // Nettoyage intelligent
    var scanning by mutableStateOf(false)
    var scanStep by mutableStateOf("")
    var scanProgress by mutableFloatStateOf(0f)
    var scan by mutableStateOf<ScanResult?>(null)

    fun runScan(then: (() -> Unit)? = null) {
        if (scanning) return
        scanning = true
        viewModelScope.launch(Dispatchers.IO) {
            val r = Scanner(ctx).scan { s, p -> viewModelScope.launch(Dispatchers.Main) { scanStep = s; scanProgress = p } }
            withContext(Dispatchers.Main) { scan = r; scanning = false; then?.invoke() }
        }
    }

    var cleaning by mutableStateOf(false)
    fun clean(files: List<File>, done: (Long) -> Unit = {}) {
        if (files.isEmpty()) return
        cleaning = true
        viewModelScope.launch(Dispatchers.IO) {
            val freed = Scanner.clean(ctx, files)
            MediaScannerConnection.scanFile(ctx, files.map { it.absolutePath }.toTypedArray(), null, null)
            withContext(Dispatchers.Main) {
                cleaning = false
                toast("${Util.size(freed)} libérés · ${files.size} éléments")
                refreshStorage()
                done(freed)
            }
            withContext(Dispatchers.Main) { runScan() }
            index()
        }
    }

    /** Nettoyage 1 tap : analyse puis supprime tout ce qui est sans risque. */
    fun oneTapClean() {
        val go = {
            val r = scan
            if (r != null) {
                val files = r.duplicates.flatMap { it.copies } + r.groups.filter { it.safe }.flatMap { it.files }
                if (files.isEmpty()) toast("Votre téléphone est déjà propre ✨") else clean(files)
            }
        }
        if (scan == null) runScan { go() } else go()
    }

    // Explorateur
    var dir by mutableStateOf(Util.root)
    var entries by mutableStateOf<List<File>>(emptyList())
    val selection = mutableStateListOf<File>()
    var clipboard by mutableStateOf<List<File>>(emptyList())
    var clipCut by mutableStateOf(false)
    var busy by mutableStateOf<String?>(null)
    var showHidden by mutableStateOf(prefs.showHidden)
    var sort by mutableStateOf(prefs.sort)
    var query by mutableStateOf("")
    var searchResults by mutableStateOf<List<File>?>(null)
    private var searchJob: Job? = null

    fun open(d: File) {
        selection.clear(); query = ""; searchResults = null
        dir = d
        refresh()
    }

    fun up(): Boolean {
        if (selection.isNotEmpty()) { selection.clear(); return true }
        if (searchResults != null) { query = ""; searchResults = null; return true }
        val p = dir.parentFile
        if (dir.absolutePath == Util.root.absolutePath || p == null) return false
        open(p); return true
    }

    fun refresh() {
        val d = dir
        viewModelScope.launch(Dispatchers.IO) {
            val list = (d.listFiles() ?: emptyArray()).filter { showHidden || !it.name.startsWith(".") }
            val s = sortFiles(list)
            withContext(Dispatchers.Main) { if (d == dir) entries = s }
        }
    }

    fun sortFiles(list: List<File>): List<File> {
        val folders = list.filter { it.isDirectory }
        val files = list.filter { !it.isDirectory }
        val c: Comparator<File> = when (sort) {
            1 -> compareByDescending { it.lastModified() }
            2 -> compareByDescending { if (it.isDirectory) 0 else it.length() }
            3 -> compareBy<File> { it.extension.lowercase() }.thenBy { it.name.lowercase() }
            else -> compareBy { it.name.lowercase() }
        }
        return folders.sortedWith(c) + files.sortedWith(c)
    }

    fun setSortMode(s: Int) { sort = s; prefs.sort = s; refresh() }
    fun toggleHidden() { showHidden = !showHidden; prefs.showHidden = showHidden; refresh() }

    fun search(q: String) {
        query = q
        searchJob?.cancel()
        if (q.isBlank()) { searchResults = null; return }
        val base = dir
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(250)
            val res = ArrayList<File>()
            base.walkTopDown().onEnter { showHidden || !it.name.startsWith(".") || it == base }.forEach {
                if (it != base && it.name.contains(q, true)) res.add(it)
                if (res.size >= 500) return@forEach
            }
            withContext(Dispatchers.Main) { if (query == q) searchResults = res }
        }
    }

    fun toggleSelect(f: File) { if (f in selection) selection.remove(f) else selection.add(f) }

    private fun work(label: String, block: suspend () -> String?) {
        busy = label
        viewModelScope.launch(Dispatchers.IO) {
            val msg = try { block() } catch (e: Exception) { "Erreur : ${e.message}" }
            withContext(Dispatchers.Main) {
                busy = null; selection.clear(); refresh(); refreshStorage()
                msg?.let { toast(it) }
            }
        }
    }

    fun deleteFiles(files: List<File>) = work("Suppression…") {
        var freed = 0L
        files.forEach { freed += Trash.delete(ctx, it) }
        MediaScannerConnection.scanFile(ctx, files.map { it.absolutePath }.toTypedArray(), null, null)
        index()
        if (prefs.useTrash) "${files.size} élément(s) envoyé(s) à la corbeille" else "${Util.size(freed)} supprimés"
    }

    fun copyToClipboard(files: List<File>, cut: Boolean) {
        clipboard = files.toList(); clipCut = cut; selection.clear()
        toast(if (cut) "${files.size} élément(s) coupé(s)" else "${files.size} élément(s) copié(s)")
    }

    fun paste() {
        val items = clipboard; val cut = clipCut; val target = dir
        work(if (cut) "Déplacement…" else "Copie…") {
            var ok = 0
            items.forEach { if (if (cut) FileOps.move(it, target) else FileOps.copy(it, target)) ok++ }
            withContext(Dispatchers.Main) { if (cut) clipboard = emptyList() }
            "$ok/${items.size} élément(s) ${if (cut) "déplacé(s)" else "collé(s)"}"
        }
    }

    fun rename(f: File, name: String) = work("Renommage…") {
        if (FileOps.rename(f, name) != null) "Renommé" else "Impossible de renommer (nom déjà pris ?)"
    }

    fun newFolder(name: String) = work("Création…") {
        val d = File(dir, name.trim())
        if (name.isBlank() || d.exists()) "Ce nom existe déjà" else if (d.mkdirs()) "Dossier créé" else "Création impossible"
    }

    fun zip(files: List<File>) = work("Compression ZIP…") {
        val base = if (files.size == 1) files[0].nameWithoutExtension else "Archive"
        val out = FileOps.uniqueName(File(dir, "$base.zip"))
        FileOps.zip(files, out)
        "Archive créée : ${out.name} (${Util.size(out.length())})"
    }

    fun unzip(f: File) = work("Extraction…") {
        val out = FileOps.uniqueName(File(f.parentFile, f.nameWithoutExtension))
        out.mkdirs(); FileOps.unzip(f, out)
        "Extrait dans ${out.name}"
    }

    fun setMagicFolder(f: File) {
        prefs.magicFolder = f.absolutePath
        prefs.magicEnabled = true
        com.superessence.ultrafichiers.work.Scheduler.apply(ctx)
        selection.clear()
        toast("« ${f.name} » est maintenant le Dossier Magique vidéo")
    }
}
