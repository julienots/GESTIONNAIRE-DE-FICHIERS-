package com.superessence.ultrafichiers.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.superessence.ultrafichiers.data.Util
import java.io.File

@Composable
fun InputDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var t by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        title = { Text(title, fontFamily = Baloo) },
        text = { OutlinedTextField(t, { t = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onOk(t); onDismiss() }) { Text(confirm, color = Yellow) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler", color = Muted) } }
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, danger: Boolean = true, onDismiss: () -> Unit, onOk: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        title = { Text(title, fontFamily = Baloo) },
        text = { Text(text, color = Muted) },
        confirmButton = { TextButton(onClick = { onOk(); onDismiss() }) { Text(confirm, color = if (danger) Danger else Yellow) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler", color = Muted) } }
    )
}

@Composable
fun InfoDialog(f: File, onDismiss: () -> Unit) {
    val size = remember(f) { if (f.isDirectory) Util.dirSize(f) else f.length() }
    val count = remember(f) { if (f.isDirectory) f.walkTopDown().count { it.isFile } else 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface2,
        title = { Text(f.name, fontFamily = Baloo) },
        text = {
            Column {
                Line("Emplacement", f.parent ?: "")
                Line("Taille", Util.size(size) + " ($size octets)")
                if (f.isDirectory) Line("Contenu", "$count fichiers")
                else Line("Type", Util.mime(f))
                Line("Modifié", Util.date(f.lastModified()))
                Line("Droits", (if (f.canRead()) "lecture " else "") + (if (f.canWrite()) "écriture" else ""))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK", color = Yellow) } }
    )
}

@Composable
private fun Line(k: String, v: String) {
    Text(k, color = Muted, fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp))
    Text(v, color = Color.White)
}
