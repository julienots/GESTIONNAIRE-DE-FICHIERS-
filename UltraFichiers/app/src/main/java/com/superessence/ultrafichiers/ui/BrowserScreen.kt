package com.superessence.ultrafichiers.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superessence.ultrafichiers.data.FileKind
import com.superessence.ultrafichiers.data.FileOps
import com.superessence.ultrafichiers.data.Util
import com.superessence.ultrafichiers.work.Scheduler
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowserScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var dialog by remember { mutableStateOf<String?>(null) }
    var target by remember { mutableStateOf<File?>(null) }
    var sortMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    val sel = vm.selection
    val list = vm.searchResults ?: vm.entries

    Column(Modifier.fillMaxSize()) {
        // Barre du haut
        if (sel.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ sel.clear() }) { Icon(Icons.Rounded.Close, null, tint = Color.White) }
                Text("${sel.size} sélectionné(s)", color = Color.White, fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                IconButton({ sel.clear(); sel.addAll(list) }) { Icon(Icons.Rounded.SelectAll, null, tint = Yellow) }
            }
        } else if (searching) {
            Row(Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(16.dp)).background(Surface1).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, tint = Muted)
                BasicTextField(
                    vm.query, { vm.search(it) }, singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp), cursorBrush = SolidColor(Yellow),
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    decorationBox = { inner -> if (vm.query.isEmpty()) Text("Rechercher dans ${vm.dir.name}…", color = Muted); inner() }
                )
                IconButton({ searching = false; vm.search("") }, Modifier.size(28.dp)) { Icon(Icons.Rounded.Close, null, tint = Muted) }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Fichiers", color = Color.White, fontFamily = Baloo, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, modifier = Modifier.weight(1f))
                IconButton({ searching = true }) { Icon(Icons.Rounded.Search, "Rechercher", tint = Color.White) }
                IconButton({ dialog = "newFolder" }) { Icon(Icons.Rounded.CreateNewFolder, "Nouveau dossier", tint = Color.White) }
                Box {
                    IconButton({ sortMenu = true }) { Icon(Icons.Rounded.Sort, "Trier", tint = Color.White) }
                    DropdownMenu(sortMenu, { sortMenu = false }, containerColor = Surface2) {
                        listOf("Nom", "Date (récent)", "Taille (gros)", "Type").forEachIndexed { i, s ->
                            DropdownMenuItem({ Text(s, color = if (vm.sort == i) Yellow else Color.White) }, { vm.setSortMode(i); sortMenu = false })
                        }
                        DropdownMenuItem(
                            { Text(if (vm.showHidden) "Masquer fichiers cachés" else "Afficher fichiers cachés") },
                            { vm.toggleHidden(); sortMenu = false },
                            leadingIcon = { Icon(if (vm.showHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null) }
                        )
                    }
                }
            }
        }

        // Raccourcis + fil d'Ariane
        if (sel.isEmpty() && !searching) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val r = Util.root
                listOf(
                    "Interne" to r, "Download" to File(r, "Download"), "DCIM" to File(r, "DCIM"),
                    "Documents" to File(r, "Documents"), "Dossier magique" to File(vm.prefs.magicFolder),
                ).forEach { (n, f) ->
                    AssistChip(
                        onClick = { f.mkdirs(); vm.open(f) }, label = { Text(n) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = if (vm.dir == f) Yellow else Surface1, labelColor = if (vm.dir == f) Color.Black else Color.White),
                        border = null
                    )
                }
            }
        }
        Breadcrumb(vm)
        vm.busy?.let {
            Text(it, color = Yellow, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), color = Yellow, trackColor = Surface2)
        }

        // Liste
        Box(Modifier.weight(1f)) {
            if (list.isEmpty()) {
                Text(if (vm.searchResults != null) "Aucun résultat" else "Dossier vide", color = Muted, modifier = Modifier.align(Alignment.Center))
            }
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
                items(list, key = { it.absolutePath }) { f ->
                    val selected = f in sel
                    val sub = if (f.isDirectory) "${f.list()?.size ?: 0} éléments · ${Util.date(f.lastModified())}"
                    else "${Util.size(f.length())} · ${Util.date(f.lastModified())}"
                    FileRow(
                        f, if (vm.searchResults != null) f.parent?.removePrefix(Util.root.absolutePath).orEmpty().ifEmpty { "/" } + " · " + sub else sub,
                        selected = selected,
                        modifier = Modifier.combinedClickable(
                            onClick = {
                                when {
                                    sel.isNotEmpty() -> vm.toggleSelect(f)
                                    f.isDirectory -> vm.open(f)
                                    else -> FileOps.open(ctx, f)
                                }
                            },
                            onLongClick = { vm.toggleSelect(f) }
                        )
                    ) {
                        if (sel.isNotEmpty()) Icon(
                            if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                            tint = if (selected) Yellow else Muted
                        ) else if (f.isDirectory) Icon(Icons.Rounded.ChevronRight, null, tint = Muted)
                    }
                }
                item { Spacer(Modifier.height(90.dp)) }
            }
        }

        // Presse-papiers
        if (vm.clipboard.isNotEmpty() && sel.isEmpty()) {
            Row(Modifier.fillMaxWidth().background(Surface2).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${vm.clipboard.size} élément(s) ${if (vm.clipCut) "à déplacer" else "à coller"}", color = Color.White, modifier = Modifier.weight(1f))
                TextButton({ vm.clipboard = emptyList() }) { Text("Annuler", color = Muted) }
                TextButton({ vm.paste() }) { Icon(Icons.Rounded.ContentPaste, null, tint = Yellow); Spacer(Modifier.width(4.dp)); Text("Coller ici", color = Yellow) }
            }
        }

        // Actions de sélection
        if (sel.isNotEmpty()) {
            val one = sel.singleOrNull()
            Row(Modifier.fillMaxWidth().background(Surface2).padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Action(Icons.Rounded.ContentCopy, "Copier") { vm.copyToClipboard(sel.toList(), false) }
                Action(Icons.Rounded.ContentCut, "Couper") { vm.copyToClipboard(sel.toList(), true) }
                Action(Icons.Rounded.Delete, "Supprimer") { dialog = "delete" }
                Action(Icons.Rounded.Share, "Partager") { FileOps.share(ctx, sel.toList()) }
                Box {
                    Action(Icons.Rounded.MoreVert, "Plus") { moreMenu = true }
                    DropdownMenu(moreMenu, { moreMenu = false }, containerColor = Surface2) {
                        if (one != null) {
                            DropdownMenuItem({ Text("Renommer") }, { target = one; dialog = "rename"; moreMenu = false }, leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, null) })
                            DropdownMenuItem({ Text("Propriétés") }, { target = one; dialog = "info"; moreMenu = false }, leadingIcon = { Icon(Icons.Rounded.Info, null) })
                        }
                        DropdownMenuItem({ Text("Compresser en ZIP") }, { vm.zip(sel.toList()); moreMenu = false }, leadingIcon = { Icon(Icons.Rounded.FolderZip, null) })
                        if (one != null && one.extension.equals("zip", true)) {
                            DropdownMenuItem({ Text("Extraire ici") }, { vm.unzip(one); moreMenu = false }, leadingIcon = { Icon(Icons.Rounded.Unarchive, null) })
                        }
                        val videos = sel.flatMap { if (it.isDirectory) it.walkTopDown().filter { v -> v.isFile && Util.isVideo(v) }.toList() else listOf(it) }
                            .filter { Util.kind(it) == FileKind.VIDEO }
                        if (videos.isNotEmpty()) {
                            DropdownMenuItem({ Text("Optimiser ${videos.size} vidéo(s)") }, {
                                Scheduler.processVideos(ctx, videos); sel.clear(); moreMenu = false
                                vm.toast("Optimisation lancée — suivez-la dans l'onglet Vidéos"); vm.tab = 3
                            }, leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null) })
                        }
                        if (one != null && one.isDirectory) {
                            DropdownMenuItem({ Text("Définir comme Dossier Magique") }, { vm.setMagicFolder(one); moreMenu = false }, leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null, tint = Yellow) })
                        }
                    }
                }
            }
        }
    }

    when (dialog) {
        "newFolder" -> InputDialog("Nouveau dossier", "", "Créer", { dialog = null }) { vm.newFolder(it) }
        "rename" -> target?.let { t -> InputDialog("Renommer", t.name, "Renommer", { dialog = null }) { vm.rename(t, it) } }
        "info" -> target?.let { InfoDialog(it) { dialog = null } }
        "delete" -> ConfirmDialog(
            "Supprimer ${sel.size} élément(s) ?",
            if (vm.prefs.useTrash) "Ils seront placés dans la corbeille (restaurables ${vm.prefs.trashDays} jours)." else "Suppression définitive.",
            "Supprimer", onDismiss = { dialog = null }
        ) { vm.deleteFiles(sel.toList()) }
    }
}

@Composable
private fun Breadcrumb(vm: AppViewModel) {
    val root = Util.root
    val parts = ArrayList<File>()
    var c: File? = vm.dir
    while (c != null && c.absolutePath.startsWith(root.absolutePath)) { parts.add(0, c); if (c == root) break; c = c.parentFile }
    val sc = rememberScrollState(Int.MAX_VALUE)
    Row(Modifier.fillMaxWidth().horizontalScroll(sc).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        parts.forEachIndexed { i, f ->
            if (i == 0) Icon(Icons.Rounded.Home, null, tint = Yellow, modifier = Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)))
            Text(
                if (i == 0) " Interne" else f.name,
                color = if (i == parts.lastIndex) Color.White else Muted, fontSize = 13.sp,
                fontWeight = if (i == parts.lastIndex) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { vm.open(f) }.padding(4.dp)
            )
            if (i < parts.lastIndex) Icon(Icons.Rounded.ChevronRight, null, tint = Muted, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = Yellow)
        Text(label, color = Color.White, fontSize = 11.sp)
    }
}
