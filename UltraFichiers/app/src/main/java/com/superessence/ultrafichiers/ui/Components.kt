package com.superessence.ultrafichiers.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superessence.ultrafichiers.data.FileKind
import com.superessence.ultrafichiers.data.Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

fun kindIcon(k: FileKind): ImageVector = when (k) {
    FileKind.FOLDER -> Icons.Rounded.Folder
    FileKind.IMAGE -> Icons.Rounded.Image
    FileKind.VIDEO -> Icons.Rounded.Movie
    FileKind.AUDIO -> Icons.Rounded.MusicNote
    FileKind.DOCUMENT -> Icons.Rounded.Description
    FileKind.ARCHIVE -> Icons.Rounded.Archive
    FileKind.APK -> Icons.Rounded.Android
    FileKind.CODE -> Icons.Rounded.Code
    FileKind.OTHER -> Icons.Rounded.InsertDriveFile
}

fun kindColor(k: FileKind): Color = when (k) {
    FileKind.FOLDER -> Yellow
    FileKind.IMAGE -> Color(0xFF5AC8FA)
    FileKind.VIDEO -> Color(0xFFFF6B6B)
    FileKind.AUDIO -> Color(0xFFB98CFF)
    FileKind.DOCUMENT -> Color(0xFF6BE39A)
    FileKind.ARCHIVE -> Color(0xFFFFA24C)
    FileKind.APK -> Color(0xFF9BE15D)
    FileKind.CODE -> Color(0xFF7FD3D3)
    FileKind.OTHER -> Muted
}

private val thumbCache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}

private fun loadThumb(f: File): Bitmap? {
    val key = f.absolutePath + f.lastModified()
    thumbCache.get(key)?.let { return it }
    val bmp: Bitmap? = try {
        when (Util.kind(f)) {
            FileKind.IMAGE -> if (Build.VERSION.SDK_INT >= 29) ThumbnailUtils.createImageThumbnail(f, Size(192, 192), null) else {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.absolutePath, o)
                var s = 1
                while (o.outWidth / s > 384 || o.outHeight / s > 384) s *= 2
                BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = s })
            }
            FileKind.VIDEO -> if (Build.VERSION.SDK_INT >= 29) ThumbnailUtils.createVideoThumbnail(f, Size(192, 192), null) else {
                val m = MediaMetadataRetriever()
                try { m.setDataSource(f.absolutePath); m.getFrameAtTime(1_000_000)?.let { Bitmap.createScaledBitmap(it, 192, 192 * it.height / it.width.coerceAtLeast(1), true) } } finally { m.release() }
            }
            else -> null
        }
    } catch (e: Throwable) { null }
    if (bmp != null) thumbCache.put(key, bmp)
    return bmp
}

/** Icône de fichier avec vignette pour images et vidéos. */
@Composable
fun FileThumb(f: File, size: Dp = 44.dp, kind: FileKind = Util.kind(f)) {
    val canThumb = kind == FileKind.IMAGE || kind == FileKind.VIDEO
    val bmp: ImageBitmap? by produceState<ImageBitmap?>(null, f.absolutePath) {
        if (canThumb) value = withContext(Dispatchers.IO) { loadThumb(f)?.asImageBitmap() }
    }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(kindColor(kind).copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) Image(b, null, Modifier.size(size), contentScale = ContentScale.Crop)
        else Icon(kindIcon(kind), null, tint = kindColor(kind), modifier = Modifier.size(size * 0.55f))
        if (kind == FileKind.VIDEO && b != null) {
            Icon(Icons.Rounded.Movie, null, tint = Color.White, modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp).size(14.dp))
        }
    }
}

@Composable
fun UCard(modifier: Modifier = Modifier, color: Color = Surface1, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(color)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(16.dp),
        content = content
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text, fontFamily = Baloo, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color.White)
        Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

@Composable
fun FileRow(
    f: File,
    subtitle: String,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (selected) Yellow.copy(alpha = 0.16f) else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileThumb(f)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(f.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
        }
        trailing()
    }
}
