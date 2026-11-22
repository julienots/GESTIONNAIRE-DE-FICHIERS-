package com.superessence.ultrafichiers.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superessence.ultrafichiers.data.FileOps
import com.superessence.ultrafichiers.data.Util
import com.superessence.ultrafichiers.work.Scheduler
import java.io.File

@Composable
fun CleanerScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val r = vm.scan
    // éléments décochés manuellement (tout ce qui est sûr est coché par défaut)
    val unchecked = remember(r) { mutableStateMapOf<String, Boolean>() }
    val groupOn = remember(r) { mutableStateMapOf<String, Boolean>().apply { r?.groups?.forEach { put(it.id, it.safe) } } }
    var dupOn by remember(r) { mutableStateOf(true) }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    var confirm by remember { mutableStateOf(false) }
    var auto by remember { mutableStateOf(vm.prefs.autoClean) }

    fun selectedFiles(): List<File> {
        if (r == null) return emptyList()
        val out = ArrayList<File>()
        if (dupOn) r.duplicates.forEach { g -> g.copies.forEach { if (unchecked[it.absolutePath] != true) out.add(it) } }
        r.groups.forEach { g -> if (groupOn[g.id] == true) g.files.forEach { if (unchecked[it.absolutePath] != true) out.add(it) } }
        return out
    }
    val sel = selectedFiles()
    val selBytes = sel.sumOf { if (it.isDirectory) 0L else it.length() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Nettoyage", color = Color.White, fontFamily = Baloo, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, modifier = Modifier.weight(1f))
            IconButton({ vm.push(Overlay.Trash) }) { Icon(Icons.Rounded.AutoDelete, "Corbeille", tint = Color.White) }
            IconButton({ vm.runScan() }, enabled = !vm.scanning) { Icon(Icons.Rounded.Refresh, "Relancer", tint = Color.White) }
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            item {
                UCard(Modifier.padding(top = 8.dp)) {
                    if (vm.scanning) {
                        Text(vm.scanStep, color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator({ vm.scanProgress }, Modifier.fillMaxWidth(), color = Yellow, trackColor = Surface2)
                    } else if (r == null) {
                        Text("Analyse complète du stockage", color = Color.White, fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Vrais doublons (vérifiés par empreinte SHA-256), fichiers temporaires, caches, miniatures, APK installés, dossiers vides…", color = Muted, fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        Button({ vm.runScan() }) { Icon(Icons.Rounded.CleaningServices, null); Spacer(Modifier.width(6.dp)); Text("Lancer l'analyse") }
                    } else {
                        Text("Sélectionné", color = Muted, fontSize = 13.sp)
                        Text(Util.size(selBytes), color = Yellow, fontFamily = Baloo, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp)
                        Text("${sel.size} éléments · ${r.scannedFiles} fichiers analysés en ${r.durationMs / 1000}s", color = Muted, fontSize = 12.sp)
                    }
                }
            }
            item {
                UCard(Modifier.padding(top = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Nettoyage automatique", color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Chaque jour : doublons + fichiers inutiles + caches, sans rien demander", color = Muted, fontSize = 12.sp)
                        }
                        Switch(auto, { auto = it; vm.prefs.autoClean = it; Scheduler.apply(ctx) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Yellow))
                    }
                }
            }
            if (r != null) {
                if (r.duplicates.isNotEmpty()) {
                    item {
                        GroupHeader(
                            "Vrais doublons", "${r.duplicates.size} groupes · l'original est gardé automatiquement",
                            Util.size(r.dupBytes), dupOn, { dupOn = it }, expanded["dup"] == true
                        ) { expanded["dup"] = expanded["dup"] != true }
                    }
                    if (expanded["dup"] == true) {
                        r.duplicates.take(300).forEach { g ->
                            item(key = "k" + g.keep.absolutePath) {
                                FileRow(g.keep, "GARDÉ · ${g.keep.parent?.removePrefix(Util.root.absolutePath)}", modifier = Modifier.clickable { FileOps.open(ctx, g.keep) }) {
                                    Text("Original", color = Ok, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            items(g.copies, key = { "c" + it.absolutePath }) { c ->
                                val on = unchecked[c.absolutePath] != true
                                FileRow(c, "Copie · ${Util.size(c.length())} · ${c.parent?.removePrefix(Util.root.absolutePath)}",
                                    modifier = Modifier.padding(start = 18.dp).clickable { unchecked[c.absolutePath] = on }) {
                                    Checkbox(on, { unchecked[c.absolutePath] = !it }, colors = CheckboxDefaults.colors(checkedColor = Yellow, checkmarkColor = Color.Black))
                                }
                            }
                        }
                    }
                }
                r.groups.forEach { g ->
                    item(key = "g" + g.id) {
                        GroupHeader(g.title, g.desc + " · ${g.files.size}", if (g.bytes > 0) Util.size(g.bytes) else "", groupOn[g.id] == true,
                            { groupOn[g.id] = it }, expanded[g.id] == true) { expanded[g.id] = expanded[g.id] != true }
                    }
                    if (expanded[g.id] == true) {
                        items(g.files.take(500), key = { g.id + it.absolutePath }) { f ->
                            val on = unchecked[f.absolutePath] != true
                            FileRow(f, f.parent?.removePrefix(Util.root.absolutePath).orEmpty() + if (f.isFile) " · " + Util.size(f.length()) else "",
                                modifier = Modifier.padding(start = 18.dp).clickable { unchecked[f.absolutePath] = on }) {
                                Checkbox(on, { unchecked[f.absolutePath] = !it }, colors = CheckboxDefaults.colors(checkedColor = Yellow, checkmarkColor = Color.Black))
                            }
                        }
                    }
                }
                if (r.bigFiles.isNotEmpty()) {
                    item { SectionTitle("Gros fichiers à vérifier") }
                    items(r.bigFiles.take(15), key = { "b" + it.absolutePath }) { f ->
                        FileRow(f, Util.size(f.length()) + " · " + f.parent?.removePrefix(Util.root.absolutePath), modifier = Modifier.clickable { FileOps.open(ctx, f) })
                    }
                }
                if (r.duplicates.isEmpty() && r.groups.isEmpty()) item {
                    Text("✨ Rien à nettoyer, tout est propre !", color = Ok, modifier = Modifier.padding(24.dp))
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
        if (r != null && sel.isNotEmpty()) {
            Button(
                { confirm = true }, enabled = !vm.cleaning,
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Color.Black)
            ) { Text(if (vm.cleaning) "Nettoyage…" else "Nettoyer ${Util.size(selBytes)}", fontSize = 17.sp) }
        }
    }
    if (confirm) ConfirmDialog(
        "Nettoyer ${sel.size} éléments ?",
        "${Util.size(selBytes)} seront libérés." + if (vm.prefs.useTrash) " Les fichiers personnels passent par la corbeille de sécurité." else "",
        "Nettoyer", onDismiss = { confirm = false }
    ) { vm.clean(sel) }
}

@Composable
private fun GroupHeader(title: String, desc: String, size: String, on: Boolean, onToggle: (Boolean) -> Unit, open: Boolean, onExpand: () -> Unit) {
    Row(
        Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface1).clickable(onClick = onExpand).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(on, onToggle, colors = CheckboxDefaults.colors(checkedColor = Yellow, checkmarkColor = Color.Black))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(desc, color = Muted, fontSize = 12.sp)
        }
        if (size.isNotEmpty()) Text(size, color = Yellow, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            Icon(if (open) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null, tint = Muted)
        }
    }
}
