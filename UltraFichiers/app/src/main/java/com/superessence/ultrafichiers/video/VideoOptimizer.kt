package com.superessence.ultrafichiers.video

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.superessence.ultrafichiers.data.Prefs
import com.superessence.ultrafichiers.data.Trash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class VideoResult(
    val name: String,
    val before: Long,
    val after: Long,
    val removedMs: Long,
    val ok: Boolean,
    val message: String,
) {
    val saved get() = if (ok) max(0L, before - after) else 0L
}

/**
 * Optimiseur vidéo :
 *  - suppression des « blancs » (silences) par analyse du niveau audio,
 *  - ré-encodage HEVC/H.264 à débit calibré (« sans perte visible »),
 *  - remplacement sûr (l'original part à la corbeille, jamais d'agrandissement).
 */
@OptIn(UnstableApi::class)
class VideoOptimizer(private val ctx: Context) {

    data class Options(val compress: Boolean, val removeSilence: Boolean, val sensitivity: Int, val keepOriginal: Boolean)

    fun optionsFromPrefs(): Options {
        val p = Prefs(ctx)
        return Options(p.vCompress, p.vSilence, p.vSensitivity, p.vKeepOriginal)
    }

    suspend fun process(input: File, opt: Options, onProgress: (Float, String) -> Unit): VideoResult {
        val before = input.length()
        val name = input.name
        try {
            val info = probe(input) ?: return VideoResult(name, before, before, 0, false, "Format illisible")
            onProgress(0f, "Analyse des blancs…")

            // 1) Détection des silences
            var keep: List<LongRange> = listOf(0L..info.durationMs)
            var removedMs = 0L
            if (opt.removeSilence && info.hasAudio) {
                val k = withContext(Dispatchers.Default) { SilenceDetector.keepSegments(input, info.durationMs, opt.sensitivity) }
                if (k != null) {
                    val kept = k.sumOf { it.last - it.first }
                    if (info.durationMs - kept >= 800) { keep = k; removedMs = info.durationMs - kept }
                }
            }
            val cuts = removedMs > 0

            // 2) Débit cible
            val hevc = hasEncoder(MimeTypes.VIDEO_H265)
            val mime = if (hevc) MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264
            val pixels = info.width.toLong() * info.height
            val fps = info.fps.coerceIn(15f, 60f)
            val bpp = if (hevc) 0.075 else 0.12
            var target = (pixels * fps * bpp).toLong().coerceIn(700_000L, 40_000_000L)
            val src = info.videoBitrate
            var compress = opt.compress
            if (src > 0 && target >= src * 0.85) {
                // la vidéo est déjà compacte : pas de gain sans perte visible
                compress = false
            }
            if (!compress) target = if (src > 0) src else (pixels * fps * 0.15).toLong()
            if (!compress && !cuts) {
                Prefs(ctx).markProcessed(input)
                return VideoResult(name, before, before, 0, false, "Déjà optimisée")
            }

            // 3) Export
            val tmp = File(input.parentFile, ".ultra_tmp_" + input.nameWithoutExtension + ".mp4")
            tmp.delete()
            val items = keep.map { r ->
                val mi = MediaItem.Builder().setUri(Uri.fromFile(input))
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(r.first).setEndPositionMs(r.last).build()
                    ).build()
                EditedMediaItem.Builder(mi).build()
            }
            onProgress(0.02f, if (cuts) "Coupe des blancs + compression…" else "Compression…")
            export(items, tmp, mime, target.toInt()) { p -> onProgress(p, if (cuts) "Coupe des blancs + compression…" else "Compression…") }

            val after = tmp.length()
            if (after <= 0 || (!cuts && after >= before * 0.97)) {
                tmp.delete()
                Prefs(ctx).markProcessed(input)
                return VideoResult(name, before, before, 0, false, "Aucun gain possible, original conservé")
            }

            // 4) Remplacement sûr
            val mtime = input.lastModified()
            val finalFile = File(input.parentFile, input.nameWithoutExtension + ".mp4")
            if (opt.keepOriginal) Trash.delete(ctx, input) else input.delete()
            if (finalFile.exists()) finalFile.delete()
            tmp.renameTo(finalFile)
            finalFile.setLastModified(mtime)
            Prefs(ctx).markProcessed(finalFile)
            MediaScannerConnection.scanFile(ctx, arrayOf(input.absolutePath, finalFile.absolutePath), null, null)
            Prefs(ctx).totalFreed += max(0L, before - after)
            val msg = buildString {
                if (cuts) append("${removedMs / 1000}s de blancs retirés · ")
                append("${(100 - after * 100 / before)}% plus léger")
            }
            return VideoResult(name, before, after, removedMs, true, msg)
        } catch (e: Exception) {
            File(input.parentFile, ".ultra_tmp_" + input.nameWithoutExtension + ".mp4").delete()
            return VideoResult(name, before, before, 0, false, "Erreur : " + (e.message ?: e.javaClass.simpleName))
        }
    }

    private suspend fun export(items: List<EditedMediaItem>, out: File, mime: String, bitrate: Int, onProgress: (Float) -> Unit) =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val handler = Handler(Looper.getMainLooper())
                val encoder = DefaultEncoderFactory.Builder(ctx)
                    .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(bitrate).build())
                    .setEnableFallback(true)
                    .build()
                lateinit var poll: Runnable
                val transformer = Transformer.Builder(ctx)
                    .setVideoMimeType(mime)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(encoder)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            handler.removeCallbacks(poll)
                            if (cont.isActive) cont.resume(Unit)
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            handler.removeCallbacks(poll)
                            if (cont.isActive) cont.resumeWithException(exportException)
                        }
                    })
                    .build()
                val comp = Composition.Builder(EditedMediaItemSequence(items)).build()
                transformer.start(comp, out.absolutePath)
                val holder = ProgressHolder()
                poll = Runnable {
                    if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress / 100f)
                    handler.postDelayed(poll, 400)
                }
                handler.post(poll)
                cont.invokeOnCancellation {
                    handler.post { handler.removeCallbacks(poll); transformer.cancel(); out.delete() }
                }
            }
        }

    private fun hasEncoder(mime: String): Boolean =
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { c ->
            c.isEncoder && c.supportedTypes.any { it.equals(mime, true) }
        }

    data class Probe(val durationMs: Long, val width: Int, val height: Int, val fps: Float, val videoBitrate: Long, val hasAudio: Boolean)

    fun probe(f: File): Probe? {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(f.absolutePath)
            val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: return null
            val w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return null
            val h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return null
            val total = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
            val hasAudio = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            var fps = 30f
            var vbr = 0L
            val ex = MediaExtractor()
            try {
                ex.setDataSource(f.absolutePath)
                for (i in 0 until ex.trackCount) {
                    val fmt = ex.getTrackFormat(i)
                    if (fmt.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                        if (fmt.containsKey(MediaFormat.KEY_FRAME_RATE)) fps = try { fmt.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() } catch (e: Exception) { fmt.getFloat(MediaFormat.KEY_FRAME_RATE) }
                        if (fmt.containsKey(MediaFormat.KEY_BIT_RATE)) vbr = fmt.getInteger(MediaFormat.KEY_BIT_RATE).toLong()
                    }
                }
            } catch (_: Exception) {
            } finally { ex.release() }
            if (vbr <= 0 && total > 0) vbr = max(0L, total - if (hasAudio) 160_000L else 0L)
            if (vbr <= 0 && dur > 0) vbr = f.length() * 8 * 1000 / dur
            Probe(dur, w, h, fps, vbr, hasAudio)
        } catch (e: Exception) { null } finally { try { mmr.release() } catch (_: Exception) {} }
    }
}

/** Analyse le niveau sonore pour trouver les passages à garder (hors silences). */
object SilenceDetector {
    private const val WINDOW_MS = 30

    fun keepSegments(f: File, durationMs: Long, sensitivity: Int): List<LongRange>? {
        val levels = levels(f) ?: return null
        if (levels.size < 10) return null
        val sorted = levels.sorted()
        val floor = sorted[(sorted.size * 0.10).toInt()]
        val loud = sorted[(sorted.size * 0.95).toInt()]
        val absThr = floatArrayOf(-50f, -42f, -36f)[sensitivity.coerceIn(0, 2)]
        var thr = max(absThr, floor + 6f)
        thr = min(thr, loud - 12f)
        var minSilence = intArrayOf(1200, 700, 400)[sensitivity.coerceIn(0, 2)]
        val pad = intArrayOf(250, 180, 120)[sensitivity.coerceIn(0, 2)]

        repeat(4) {
            val cuts = ArrayList<LongRange>()
            var start = -1
            for (i in 0..levels.size) {
                val silent = i < levels.size && levels[i] < thr
                if (silent && start < 0) start = i
                if (!silent && start >= 0) {
                    val s = start.toLong() * WINDOW_MS
                    val e = i.toLong() * WINDOW_MS
                    if (e - s >= minSilence) {
                        val cs = if (start == 0) 0L else s + pad
                        val ce = if (i == levels.size) min(durationMs, e) else e - pad
                        if (ce > cs) cuts.add(cs..ce)
                    }
                    start = -1
                }
            }
            // complément = segments conservés
            val keep = ArrayList<LongRange>()
            var cur = 0L
            for (c in cuts) {
                if (c.first - cur >= 120) keep.add(cur..c.first)
                cur = c.last
            }
            if (durationMs - cur >= 120) keep.add(cur..durationMs)
            if (keep.isEmpty()) return null
            if (keep.size <= 250) return keep
            minSilence *= 2 // trop fragmenté : on ne garde que les grands blancs
        }
        return null
    }

    /** Niveau RMS (dBFS) par fenêtre de 30 ms. */
    private fun levels(f: File): List<Float>? {
        val ex = MediaExtractor()
        var dec: MediaCodec? = null
        try {
            ex.setDataSource(f.absolutePath)
            val idx = (0 until ex.trackCount).firstOrNull {
                ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            ex.selectTrack(idx)
            val fmt = ex.getTrackFormat(idx)
            val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            dec = codec
            codec.configure(fmt, null, null, 0)
            codec.start()
            var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var isFloat = false
            val out = ArrayList<Float>()
            var sum = 0.0
            var frames = 0
            var winFrames = rate * WINDOW_MS / 1000
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val ii = codec.dequeueInputBuffer(5000)
                    if (ii >= 0) {
                        val buf = codec.getInputBuffer(ii)!!
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true
                        } else {
                            codec.queueInputBuffer(ii, 0, n, ex.sampleTime, 0); ex.advance()
                        }
                    }
                }
                val oi = codec.dequeueOutputBuffer(info, 5000)
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val of = codec.outputFormat
                    rate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    ch = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    isFloat = of.containsKey(MediaFormat.KEY_PCM_ENCODING) && of.getInteger(MediaFormat.KEY_PCM_ENCODING) == 4
                    winFrames = rate * WINDOW_MS / 1000
                } else if (oi >= 0) {
                    val b = codec.getOutputBuffer(oi)!!
                    b.position(info.offset); b.limit(info.offset + info.size)
                    b.order(ByteOrder.nativeOrder())
                    if (isFloat) {
                        val fb = b.asFloatBuffer()
                        while (fb.remaining() >= ch) {
                            var s = 0.0
                            repeat(ch) { val v = fb.get().toDouble(); s += v * v }
                            sum += s / ch; frames++
                            if (frames >= winFrames) { out.add(db(sum / frames)); sum = 0.0; frames = 0 }
                        }
                    } else {
                        val sb = b.asShortBuffer()
                        while (sb.remaining() >= ch) {
                            var s = 0.0
                            repeat(ch) { val v = sb.get() / 32768.0; s += v * v }
                            sum += s / ch; frames++
                            if (frames >= winFrames) { out.add(db(sum / frames)); sum = 0.0; frames = 0 }
                        }
                    }
                    codec.releaseOutputBuffer(oi, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
            return out
        } catch (e: Exception) {
            return null
        } finally {
            try { dec?.stop(); dec?.release() } catch (_: Exception) {}
            ex.release()
        }
    }

    private fun db(meanSq: Double): Float = if (meanSq <= 1e-10) -100f else (10 * log10(meanSq)).toFloat().coerceAtLeast(-100f)
    @Suppress("unused") private fun rms(x: Double) = sqrt(x)
}
