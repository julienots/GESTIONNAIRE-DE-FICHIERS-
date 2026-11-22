package com.superessence.ultrafichiers.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.superessence.ultrafichiers.data.FileKind
import com.superessence.ultrafichiers.data.Util
import com.superessence.ultrafichiers.work.Scheduler
import com.superessence.ultrafichiers.work.VideoWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

data class VideoProgress(val running: Boolean, val i: Int, val n: Int, val name: String, val p: Float, val step: String)

@Composable
fun rememberVideoProgress(): VideoProgress {
    val ctx = LocalContext.current
    var st by remember { mutableStateOf(VideoProgress(false, 0, 0, "", 0f, "")) }
    LaunchedEffect(Unit) {
        val wm = WorkManager.getInstance(ctx)
        while (true) {
            st = withContext(Dispatchers.IO) {
                val infos = try {
                    wm.getWorkInfosForUniqueWork(Scheduler.VIDEO).get() + wm.getWorkInfosForUniqueWork(Scheduler.MAGIC).get()
                } catch (e: Exception) { emptyList() }
                val run = infos.firstOrNull { it.state == WorkInfo.State.RUNNING && it.progress.keyValueMap.isNotEmpty() }
                if (run == null) VideoProgress(false, 0, 0, "", 0f, "")
                else VideoProgress(
                    true, run.progress.getInt("i", 0), run.progress.getInt("n", 0),
                    run.progress.getString("name") ?: "", run.progress.getFloat("p", 0f), run.progress.getString("step") ?: ""
                )
            }
            delay(700)
        }
    }
    return st
}

@Composable
fun VideoScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val p = vm.prefs
    var compress by remember { mutableStateOf(p.vCompress) }
    var silence by remember { mutableStateOf(p.vSilence) }
    var sens by remember { mutableStateOf(p.vSensitivity) }
    var keep by remember { mutableStateOf(p.vKeepOriginal) }
    var magicOn by remember { mutableStateOf(p.magicEnabled) }
    val prog = rememberVideoProgress()
    var log by remember { mutableStateOf(VideoWorker.readLog(ctx)) }
    LaunchedEffect(prog.running, prog.i) { log = VideoWorker.readLog(ctx) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Text("Vidéos", color = Color.White, fontFamily = Baloo, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, modifier = Modifier.padding(top = 14.dp))
            Text("Compression sans perte visible + suppression automatique des blancs", color = Muted, fontSize = 13.sp)
        }
        if (prog.running) item {
            UCard(Modifier.padding(top = 12.dp), color = Surface2) {
                Text("En cours · ${prog.i + 1}/${prog.n}", color = Yellow, fontWeight = FontWeight.Bold)
                Text(prog.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(prog.step, color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator({ prog.p }, Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = Yellow, trackColor = Bg)
            }
        }
        item {
            Column(
                Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Yellow, YellowDark))).padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Color.Black)
                    Text("  Dossier Magique", color = Color.Black, fontFamily = Baloo, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                    Switch(magicOn, { magicOn = it; p.magicEnabled = it; Scheduler.apply(ctx) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Yellow, checkedTrackColor = Color.Black))
                }
                Text("Déposez des vidéos dans ce dossier : tout ce qui est dedans est optimisé automatiquement.", color = Color.Black.copy(alpha = 0.75f), fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text(p.magicFolder.removePrefix(Util.root.absolutePath).ifEmpty { "/" }, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ File(p.magicFolder).mkdirs(); vm.open(File(p.magicFolder)); vm.tab = 1 },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Yellow)) {
                        Icon(Icons.Rounded.FolderOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Ouvrir")
                    }
                    Button({ Scheduler.runNow(ctx); vm.toast("Traitement du Dossier Magique lancé") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Yellow)) {
                        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Traiter")
                    }
                }
                Text("Astuce : appui long sur n'importe quel dossier › « Définir comme Dossier Magique »", color = Color.Black.copy(alpha = 0.6f), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
        item {
            Button(
                { vm.push(Overlay.VideoPicker) }, Modifier.padding(top = 12.dp).fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Surface2, contentColor = Color.White)
            ) { Icon(Icons.Rounded.VideoLibrary, null, tint = Yellow); Spacer(Modifier.width(8.dp)); Text("Sélectionner des vidéos à optimiser") }
        }
        item { SectionTitle("Réglages d'optimisation") }
        item {
            UCard {
                ToggleRow("Compresser sans perte visible", "H.265/HEVC (ou H.264) au débit idéal. Jamais de fichier plus gros.", compress) { compress = it; p.vCompress = it }
                ToggleRow("Enlever les blancs", "Coupe automatiquement les silences et temps morts.", silence) { silence = it; p.vSilence = it }
                if (silence) {
                    Text("Intensité de coupe", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Douce", "Normale", "Forte").forEachIndexed { i, s ->
                            val on = sens == i
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (on) Yellow else Surface2)
                                    .clickable { sens = i; p.vSensitivity = i }.padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) { Text(s, color = if (on) Color.Black else Color.White, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
                ToggleRow("Garder l'original en corbeille", "Sécurité : récupérable pendant ${p.trashDays} jours.", keep) { keep = it; p.vKeepOriginal = it }
            }
        }
        if (log.isNotEmpty()) {
            item { SectionTitle("Historique") }
            items(log.take(40)) { l ->
                val ok = l[4] == "true"
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.VideoLibrary, null, tint = if (ok) Ok else Muted, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(l[1], color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                        Text(l[5], color = Muted, fontSize = 12.sp)
                    }
                    if (ok) Text("${Util.size(l[2].toLong())} → ${Util.size(l[3].toLong())}", color = Yellow, fontSize = 12.sp)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun ToggleRow(title: String, desc: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(desc, color = Muted, fontSize = 12.sp)
        }
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Yellow))
    }
}

/** Sélecteur de vidéos en grille avec vignettes. */
@Composable
fun VideoPickerScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val videos = vm.cats[Cat.VIDEOS].orEmpty()
    val picked = remember { mutableStateListOf<File>() }
    var bySize by remember { mutableStateOf(true) }
    val list = remember(videos, bySize) { if (bySize) videos.sortedByDescending { it.length() } else videos }
    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ vm.pop() }) { Icon(Icons.Rounded.ArrowBack, null, tint = Color.White) }
            Text("Choisir des vidéos", color = Color.White, fontFamily = Baloo, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, modifier = Modifier.weight(1f))
            OutlinedButton({ bySize = !bySize }) { Text(if (bySize) "Par taille" else "Par date", color = Yellow) }
        }
        if (videos.isEmpty()) Text(if (vm.indexing) "Recherche des vidéos…" else "Aucune vidéo trouvée", color = Muted, modifier = Modifier.padding(24.dp))
        LazyVerticalGrid(GridCells.Fixed(3), Modifier.weight(1f).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(list, key = { it.absolutePath }) { f ->
                val on = f in picked
                Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(14.dp)).clickable { if (on) picked.remove(f) else picked.add(f) }) {
                    FileThumb(f, size = 200.dp, kind = FileKind.VIDEO)
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).padding(4.dp)) {
                        Text(Util.size(f.length()), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    if (on) Box(Modifier.matchParentSizeCompat().background(Yellow.copy(alpha = 0.35f)))
                    if (on) Icon(Icons.Rounded.CheckCircle, null, tint = Yellow, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black))
                }
            }
        }
        if (picked.isNotEmpty()) {
            Button(
                {
                    Scheduler.processVideos(ctx, picked.toList())
                    vm.toast("${picked.size} vidéo(s) en cours d'optimisation"); vm.pop(); vm.tab = 3
                },
                Modifier.fillMaxWidth().padding(16.dp).height(54.dp), shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Color.Black)
            ) { Text("Optimiser ${picked.size} vidéo(s) · ${Util.size(picked.sumOf { it.length() })}", fontSize = 16.sp) }
        }
    }
}

private fun Modifier.matchParentSizeCompat() = this.fillMaxSize()
