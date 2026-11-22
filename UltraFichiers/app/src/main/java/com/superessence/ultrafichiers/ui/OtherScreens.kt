package com.superessence.ultrafichiers.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superessence.ultrafichiers.R
import com.superessence.ultrafichiers.data.FileOps
import com.superessence.ultrafichiers.data.Trash
import com.superessence.ultrafichiers.data.TrashItem
import com.superessence.ultrafichiers.data.Util
import com.superessence.ultrafichiers.work.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun TopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBack) { Icon(Icons.Rounded.ArrowBack, "Retour", tint = Color.White) }
        Text(title, color = Color.White, fontFamily = Baloo, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, modifier = Modifier.weight(1f))
        actions()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryScreen(vm: AppViewModel, cat: Cat) {
    val ctx = LocalContext.current
    val files = vm.cats[cat].orEmpty()
    val sel = remember { mutableStateListOf<File>() }
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Bg)) {
        TopBar(if (sel.isEmpty()) cat.label else "${sel.size} sélectionné(s)", { if (sel.isNotEmpty()) sel.clear() else vm.pop() }) {
            if (sel.isNotEmpty()) {
                IconButton({ FileOps.share(ctx, sel.toList()) }) { Icon(Icons.Rounded.Share, null, tint = Yellow) }
                IconButton({ confirm = true }) { Icon(Icons.Rounded.Delete, null, tint = Yellow) }
            }
        }
        Text("${files.size} fichiers · ${Util.size(files.sumOf { it.length() })}", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 18.dp))
        LazyColumn(Modifier.weight(1f).padding(horizontal = 6.dp)) {
            items(files, key = { it.absolutePath }) { f ->
                val on = f in sel
                FileRow(
                    f, "${Util.size(f.length())} · ${f.parent?.removePrefix(Util.root.absolutePath)}", on,
                    Modifier.combinedClickable(
                        onClick = { if (sel.isNotEmpty()) { if (on) sel.remove(f) else sel.add(f) } else FileOps.open(ctx, f) },
                        onLongClick = { if (on) sel.remove(f) else sel.add(f) }
                    )
                ) {
                    if (sel.isNotEmpty()) Icon(if (on) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = if (on) Yellow else Muted)
                    else IconButton({ f.parentFile?.let { vm.open(it) }; vm.pop(); vm.tab = 1 }) { Icon(Icons.Rounded.FolderOpen, "Afficher dans le dossier", tint = Muted) }
                }
            }
        }
    }
    if (confirm) ConfirmDialog("Supprimer ${sel.size} fichier(s) ?", "Ils iront dans la corbeille de sécurité.", "Supprimer", onDismiss = { confirm = false }) {
        vm.deleteFiles(sel.toList()); sel.clear(); vm.index()
    }
}

@Composable
fun TrashScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<TrashItem>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(reload) { items = withContext(Dispatchers.IO) { Trash.list() } }
    Column(Modifier.fillMaxSize().background(Bg)) {
        TopBar("Corbeille", { vm.pop() }) {
            if (items.isNotEmpty()) TextButton({ confirm = true }) { Text("Vider", color = Danger) }
        }
        Text("${items.size} éléments · ${Util.size(items.sumOf { it.size })} · purge auto après ${vm.prefs.trashDays} jours", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 18.dp))
        if (items.isEmpty()) Text("La corbeille est vide", color = Muted, modifier = Modifier.padding(24.dp))
        LazyColumn(Modifier.weight(1f).padding(horizontal = 6.dp)) {
            items(items, key = { it.file.absolutePath }) { t ->
                FileRow(t.file, "${Util.size(t.size)} · ${t.originalPath.removePrefix(Util.root.absolutePath)}") {
                    IconButton({ scope.launch(Dispatchers.IO) { Trash.restore(t); reload++ }; vm.toast("Restauré") }) { Icon(Icons.Rounded.Restore, "Restaurer", tint = Yellow) }
                    IconButton({ scope.launch(Dispatchers.IO) { Trash.purge(t); reload++ } }) { Icon(Icons.Rounded.DeleteForever, "Supprimer", tint = Danger) }
                }
            }
        }
    }
    if (confirm) ConfirmDialog("Vider la corbeille ?", "Suppression définitive de tous les éléments.", "Vider", onDismiss = { confirm = false }) {
        scope.launch(Dispatchers.IO) {
            val f = Trash.emptyAll()
            vm.prefs.totalFreed += f
            withContext(Dispatchers.Main) { vm.toast("${Util.size(f)} libérés"); vm.refreshStorage(); reload++ }
        }
    }
    @Suppress("UNUSED_VARIABLE") val unused = ctx
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val p = vm.prefs
    var auto by remember { mutableStateOf(p.autoClean) }
    var dup by remember { mutableStateOf(p.autoDuplicates) }
    var junk by remember { mutableStateOf(p.autoJunk) }
    var apks by remember { mutableStateOf(p.autoApks) }
    var trash by remember { mutableStateOf(p.useTrash) }
    var days by remember { mutableIntStateOf(p.trashDays) }
    var hidden by remember { mutableStateOf(p.showHidden) }
    Column(Modifier.fillMaxSize().background(Bg)) {
        TopBar("Réglages", { vm.pop() })
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            item { SectionTitle("Automatisation") }
            item {
                UCard {
                    ToggleRow("Nettoyage automatique quotidien", "S'exécute en arrière-plan, batterie suffisante.", auto) { auto = it; p.autoClean = it; Scheduler.apply(ctx) }
                    ToggleRow("Supprimer les vrais doublons", "Garde toujours l'original (le plus ancien / le mieux rangé).", dup) { dup = it; p.autoDuplicates = it }
                    ToggleRow("Supprimer fichiers inutiles & caches", "Temporaires, journaux, miniatures, vides…", junk) { junk = it; p.autoJunk = it }
                    ToggleRow("Supprimer APK déjà installés", "Fichiers d'installation devenus inutiles.", apks) { apks = it; p.autoApks = it }
                }
            }
            item { SectionTitle("Sécurité") }
            item {
                UCard {
                    ToggleRow("Corbeille de sécurité", "Les suppressions sont récupérables un temps.", trash) { trash = it; p.useTrash = it }
                    if (trash) {
                        Text("Purge automatique après $days jour(s)", color = Color.White, modifier = Modifier.padding(top = 8.dp))
                        Slider(days.toFloat(), { days = it.toInt(); p.trashDays = days }, valueRange = 1f..30f, steps = 28,
                            colors = SliderDefaults.colors(thumbColor = Yellow, activeTrackColor = Yellow))
                    }
                    Text("Ouvrir la corbeille ›", color = Yellow, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp).clickable { vm.push(Overlay.Trash) })
                }
            }
            item { SectionTitle("Affichage") }
            item { UCard { ToggleRow("Afficher les fichiers cachés", "Fichiers et dossiers commençant par un point.", hidden) { hidden = it; p.showHidden = it; vm.showHidden = it; vm.refresh() } } }
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painterResource(R.drawable.ic_logo), null, Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)))
                    Spacer(Modifier.height(8.dp))
                    Text("Ultra Fichiers 1.0", color = Color.White, fontFamily = Baloo, fontWeight = FontWeight.Bold)
                    Text("par SuperEssence Studio", color = Muted, fontSize = 12.sp)
                    Text("Total libéré : ${Util.size(p.totalFreed)}", color = Yellow, fontSize = 12.sp)
                }
            }
        }
    }
}
