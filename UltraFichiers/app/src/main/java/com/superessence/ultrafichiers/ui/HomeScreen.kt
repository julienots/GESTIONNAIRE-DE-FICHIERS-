package com.superessence.ultrafichiers.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superessence.ultrafichiers.R
import com.superessence.ultrafichiers.data.Util

@Composable
fun HomeScreen(vm: AppViewModel) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.ic_logo), null, Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(buildAnnotatedString {
                        withStyle(SpanStyle(color = Color.White)) { append("Ultra ") }
                        withStyle(SpanStyle(color = Yellow)) { append("Fichiers") }
                    }, fontFamily = Baloo, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
                    Text("par SuperEssence Studio", color = Muted, fontSize = 12.sp)
                }
                IconButton(onClick = { vm.push(Overlay.Settings) }) { Icon(Icons.Rounded.Settings, "Réglages", tint = Color.White) }
            }
        }
        item { StorageCard(vm) }
        item { SmartCard(vm) }
        item { SectionTitle("Catégories") { if (vm.indexing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) } }
        item {
            val list = Cat.entries
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                list.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { c ->
                            val files = vm.cats[c].orEmpty()
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(Surface1)
                                    .clickable { vm.push(Overlay.Category(c)) }.padding(14.dp)
                            ) {
                                Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(c.color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                                    Icon(c.icon, null, tint = c.color, modifier = Modifier.size(22.dp))
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(c.label, color = Color.White, fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("${files.size} · ${Util.size(files.sumOf { it.length() })}", color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
        val r = vm.scan
        if (r != null) {
            item { SectionTitle("Suggestions intelligentes") }
            if (r.duplicates.isNotEmpty()) item {
                Suggestion(Icons.Rounded.ContentCopy, "${r.duplicates.sumOf { it.copies.size }} vrais doublons", "${Util.size(r.dupBytes)} gaspillés — contenu identique octet par octet") { vm.tab = 2 }
            }
            if (r.heavyVideos.isNotEmpty()) item {
                Suggestion(Icons.Rounded.Movie, "${r.heavyVideos.size} vidéos lourdes", "Jusqu'à ~${Util.size(r.heavyVideos.sumOf { it.length() } * 4 / 10)} récupérables en compressant") { vm.tab = 3 }
            }
            r.groups.filter { it.bytes > 0 }.take(3).forEach { g ->
                item { Suggestion(Icons.Rounded.Delete, g.title, "${g.files.size} éléments · ${Util.size(g.bytes)}") { vm.tab = 2 } }
            }
        }
        if (vm.recent.isNotEmpty()) {
            item { SectionTitle("Récents") }
            items(vm.recent, key = { it.absolutePath }) { f ->
                FileRow(f, "${Util.size(f.length())} · ${Util.date(f.lastModified())}",
                    modifier = Modifier.clickable { com.superessence.ultrafichiers.data.FileOps.open(vm.getApplication(), f) })
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun StorageCard(vm: AppViewModel) {
    val used = (vm.total - vm.free).coerceAtLeast(0)
    val pct = if (vm.total > 0) used.toFloat() / vm.total else 0f
    UCard(Modifier.padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(96.dp)) {
                    val s = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round)
                    val inset = s.width / 2
                    val sz = Size(size.width - s.width, size.height - s.width)
                    drawArc(Surface2, 0f, 360f, false, Offset(inset, inset), sz, style = s)
                    drawArc(Brush.sweepGradient(listOf(YellowDark, Yellow, YellowDark)), -90f, 360f * pct, false, Offset(inset, inset), sz, style = s)
                }
                Text("${(pct * 100).toInt()}%", color = Color.White, fontFamily = Baloo, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            }
            Column(Modifier.padding(start = 16.dp)) {
                Text("Stockage interne", color = Muted, fontSize = 13.sp)
                Text("${Util.size(used)} / ${Util.size(vm.total)}", color = Color.White, fontFamily = Baloo, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("${Util.size(vm.free)} libres", color = Yellow, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (vm.totalFreed > 0) Text("Déjà libéré par l'app : ${Util.size(vm.totalFreed)}", color = Ok, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SmartCard(vm: AppViewModel) {
    val r = vm.scan
    Column(
        Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Yellow, YellowDark))).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = Color.Black)
            Text("  Nettoyage intelligent", color = Color.Black, fontFamily = Baloo, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        }
        Spacer(Modifier.height(4.dp))
        when {
            vm.scanning -> {
                Text(vm.scanStep, color = Color.Black.copy(alpha = 0.75f), fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator({ vm.scanProgress }, Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = Color.Black, trackColor = Color.Black.copy(alpha = 0.15f))
            }
            vm.cleaning -> Text("Nettoyage en cours…", color = Color.Black)
            r != null -> {
                Text(Util.size(r.safeBytes), color = Color.Black, fontFamily = Baloo, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp)
                Text("récupérables sans risque · ${r.scannedFiles} fichiers analysés", color = Color.Black.copy(alpha = 0.75f), fontSize = 13.sp)
            }
            else -> Text("Doublons, fichiers inutiles, caches, APK installés… tout en un geste.", color = Color.Black.copy(alpha = 0.75f), fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { vm.oneTapClean() }, enabled = !vm.scanning && !vm.cleaning,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Yellow)
            ) {
                Icon(Icons.Rounded.Bolt, null); Spacer(Modifier.width(6.dp))
                Text(if (r == null) "Analyser & nettoyer" else "Nettoyer en 1 tap")
            }
            if (r != null) Button(
                onClick = { vm.tab = 2 },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.12f), contentColor = Color.Black)
            ) { Text("Détails") }
        }
    }
}

@Composable
private fun Suggestion(icon: ImageVector, title: String, desc: String, onClick: () -> Unit) {
    Row(
        Modifier.padding(bottom = 8.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface1).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Yellow.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Yellow, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(desc, color = Muted, fontSize = 12.sp)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = Muted)
    }
}
