package com.deckvault.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.Crop
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.deckvault.app.data.LocalStorageRepository
import com.deckvault.app.model.CompressEstimateRequest
import com.deckvault.app.model.CompressEstimateResponse
import com.deckvault.app.model.CompressRequest
import com.deckvault.app.model.EditStudioRequest
import com.deckvault.app.model.ExtractAudioRequest
import com.deckvault.app.model.MediaOpResponse
import com.deckvault.app.model.MergeRequest
import com.deckvault.app.model.UpscaleRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
class LocalMediaProcessor(
    private val context: Context,
    private val localRepo: LocalStorageRepository,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val tempDir = File(context.cacheDir, "deckvault_exports").apply { mkdirs() }
    private val activeTransformers = ConcurrentHashMap<String, Transformer>()

    fun cancel(jobId: String) {
        val tr = activeTransformers.remove(jobId) ?: return
        mainHandler.post { runCatching { tr.cancel() } }
    }

    fun estimateCompression(req: CompressEstimateRequest): CompressEstimateResponse {
        val currentBytes = localRepo.getFileLengthAny(req.path).coerceAtLeast(0L)
        val name = if (LocalStorageRepository.isContentUriPath(req.path)) "media.mp4" else File(req.path).name
        val mediaType = LocalStorageRepository.classifyMedia(name)

        if (mediaType == "image") {
            val scaleFactor = if (req.targetHeight > 0) 0.45 else (req.quality / 100.0).coerceIn(0.25, 0.95) * 0.65
            val est = max(16 * 1024L, (currentBytes * scaleFactor).roundToInt().toLong()).coerceAtMost(currentBytes.coerceAtLeast(1L))
            val saved = (currentBytes - est).coerceAtLeast(0L)
            val pct = if (currentBytes > 0) (saved * 100.0 / currentBytes) else 0.0
            return CompressEstimateResponse(
                ok = true,
                mediaType = "image",
                currentBytes = currentBytes,
                estimatedBytes = est,
                estimatedSavedBytes = saved,
                savingsPct = pct,
                targetBitrateKbps = 0,
            )
        }

        val meta = localRepo.probeVideoMeta(req.path)
        val durSec = ((meta?.durationMs ?: 0L) / 1000.0).coerceAtLeast(1.0)
        val audioKbps = req.audioKbps.coerceIn(0, 320)
        val targetVideoKbps: Int = if (req.targetMb > 0.1) {
            val totalKbps = ((req.targetMb * 8192.0) / durSec).toInt()
            (totalKbps - audioKbps).coerceAtLeast(180)
        } else {
            val srcKbps = ((meta?.bitrate ?: 0L) / 1000L).toInt().takeIf { it > 0 }
                ?: ((currentBytes * 8.0 / durSec) / 1000.0).toInt().coerceAtLeast(1200)
            val crfFactor = when {
                req.crf <= 18 -> 0.88
                req.crf <= 22 -> 0.65
                req.crf <= 26 -> 0.45
                req.crf <= 30 -> 0.30
                else -> 0.20
            }
            val resFactor = when {
                req.targetHeight in 1..480 -> 0.42
                req.targetHeight in 481..720 -> 0.64
                req.targetHeight in 721..1080 -> 0.85
                else -> 1.0
            }
            val hevcFactor = if (req.codec.equals("hevc", true) || req.codec.equals("h265", true)) 0.72 else 1.0
            (srcKbps * crfFactor * resFactor * hevcFactor).toInt().coerceIn(220, 45_000)
        }
        val estBytes = if (req.targetMb > 0.1) {
            (req.targetMb * 1024.0 * 1024.0).toLong()
        } else {
            (((targetVideoKbps + audioKbps) * 1000.0 / 8.0) * durSec).toLong().coerceAtLeast(32 * 1024L)
        }
        val saved = (currentBytes - estBytes).coerceAtLeast(0L)
        val pct = if (currentBytes > 0) (saved * 100.0 / currentBytes) else 0.0
        return CompressEstimateResponse(
            ok = true,
            mediaType = "video",
            currentBytes = currentBytes,
            estimatedBytes = estBytes,
            estimatedSavedBytes = saved,
            savingsPct = pct,
            targetBitrateKbps = targetVideoKbps,
        )
    }

    suspend fun editVideo(
        jobId: String,
        req: EditStudioRequest,
        safTreeUris: Map<String, String>,
        onProgress: (pct: Double, msg: String) -> Unit,
    ): MediaOpResponse = withContext(Dispatchers.IO) {
        val oldSize = localRepo.getFileLengthAny(req.path).coerceAtLeast(0L)
        val uri = toInputUri(req.path)
        val meta = localRepo.probeVideoMeta(req.path)
        val totalDurMs = meta?.durationMs ?: 0L

        val miBuilder = MediaItem.Builder().setUri(uri)
        val hasTrim = req.startMs > 0L || (req.endMs in 1L until totalDurMs)
        if (hasTrim && (req.endMs > req.startMs || req.endMs <= 0L)) {
            val clip = MediaItem.ClippingConfiguration.Builder().setStartPositionMs(req.startMs.coerceAtLeast(0L))
            if (req.endMs > req.startMs) clip.setEndPositionMs(req.endMs)
            miBuilder.setClippingConfiguration(clip.build())
        }

        val videoEffects = mutableListOf<Effect>()
        val audioProcessors = mutableListOf<AudioProcessor>()

        req.crop?.let { c ->
            val cleanW = c.w.coerceIn(0.05f, 1f)
            val cleanH = c.h.coerceIn(0.05f, 1f)
            val cleanX = c.x.coerceIn(0f, 1f - cleanW)
            val cleanY = c.y.coerceIn(0f, 1f - cleanH)
            if (cleanX > 0.005f || cleanY > 0.005f || cleanW < 0.995f || cleanH < 0.995f) {
                val left = cleanX * 2f - 1f
                val right = (cleanX + cleanW) * 2f - 1f
                val top = 1f - cleanY * 2f
                val bottom = 1f - (cleanY + cleanH) * 2f
                videoEffects.add(Crop(left, right, bottom, top))
            }
        }

        val normRot = ((req.rotate % 360) + 360) % 360
        if (normRot != 0 || req.flipH || req.flipV) {
            val ccwDeg = when (normRot) {
                90 -> 270f
                180 -> 180f
                270 -> 90f
                else -> 0f
            }
            val sr = ScaleAndRotateTransformation.Builder()
                .setRotationDegrees(ccwDeg)
                .setScale(if (req.flipH) -1f else 1f, if (req.flipV) -1f else 1f)
                .build()
            videoEffects.add(sr)
        }

        if (kotlin.math.abs(req.brightness) > 0.02f) {
            videoEffects.add(Brightness(req.brightness.coerceIn(-0.9f, 0.9f)))
        }
        if (kotlin.math.abs(req.contrast - 1f) > 0.02f) {
            videoEffects.add(Contrast((req.contrast - 1f).coerceIn(-0.9f, 0.9f)))
        }
        if (kotlin.math.abs(req.saturation - 1f) > 0.02f) {
            videoEffects.add(
                HslAdjustment.Builder()
                    .adjustSaturation(((req.saturation - 1f) * 100f).coerceIn(-100f, 100f))
                    .build()
            )
        }

        val spd = req.speed.toFloat().coerceIn(0.25f, 4.0f)
        if (kotlin.math.abs(spd - 1.0f) > 0.02f) {
            videoEffects.add(SpeedChangeEffect(spd))
            if (!req.muteAudio) {
                audioProcessors.add(SonicAudioProcessor().apply { setSpeed(spd) })
            }
        }

        val edited = EditedMediaItem.Builder(miBuilder.build())
            .setRemoveAudio(req.muteAudio)
            .setEffects(Effects(audioProcessors, videoEffects))
            .build()

        val tempOut = File(tempDir, "edit_${UUID.randomUUID()}.mp4")
        val noGlEffects = videoEffects.isEmpty() && audioProcessors.isEmpty() && !req.muteAudio && req.mode == "lossless"
        val estBitrate = when {
            req.crf <= 18 -> 16_000_000
            req.crf <= 22 -> 9_000_000
            req.crf <= 26 -> 4_500_000
            else -> 2_200_000
        }

        try {
            val builder = Transformer.Builder(context)
                .setLooper(Looper.getMainLooper())
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .experimentalSetTrimOptimizationEnabled(noGlEffects)
            if (!noGlEffects) {
                builder.setEncoderFactory(
                    DefaultEncoderFactory.Builder(context)
                        .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(estBitrate).build())
                        .setEnableFallback(true)
                        .build()
                )
            }
            runTransformer(jobId, builder.build(), edited, null, tempOut) { pct ->
                onProgress(pct * 0.92, "Rendering on device ${pct.roundToInt()}%")
            }
            onProgress(95.0, "Saving output...")
            finalizeOutput(
                sourcePath = req.path,
                tempOutput = tempOut,
                suffix = "_edited",
                forcedExt = "mp4",
                saveMode = req.saveMode,
                recycleOriginal = req.recycleOriginal,
                oldSize = oldSize,
                safTreeUris = safTreeUris,
            )
        } finally {
            runCatching { tempOut.delete() }
        }
    }

    suspend fun compressMedia(
        jobId: String,
        req: CompressRequest,
        safTreeUris: Map<String, String>,
        onProgress: (pct: Double, msg: String) -> Unit,
    ): MediaOpResponse = withContext(Dispatchers.IO) {
        val oldSize = localRepo.getFileLengthAny(req.path).coerceAtLeast(0L)
        val srcName = sourceDisplayName(req.path)
        val mediaType = LocalStorageRepository.classifyMedia(srcName)

        if (mediaType == "image") {
            return@withContext processImageLocally(
                sourcePath = req.path,
                targetHeight = req.targetHeight,
                scaleMultiplier = 1.0f,
                quality = req.quality,
                saveMode = req.saveMode,
                recycleOriginal = req.recycleOriginal,
                suffix = "_compressed",
                safTreeUris = safTreeUris,
                onProgress = onProgress,
            )
        }

        val est = estimateCompression(
            CompressEstimateRequest(
                path = req.path,
                targetHeight = req.targetHeight,
                crf = req.crf,
                codec = req.codec,
                audioKbps = req.audioKbps,
                targetMb = req.targetMb,
            )
        )
        val videoEffects = mutableListOf<Effect>()
        if (req.targetHeight > 0) {
            videoEffects.add(Presentation.createForHeight(req.targetHeight))
        }
        val edited = EditedMediaItem.Builder(MediaItem.fromUri(toInputUri(req.path)))
            .setEffects(Effects(emptyList(), videoEffects))
            .build()

        val tempOut = File(tempDir, "comp_${UUID.randomUUID()}.mp4")
        val mime = if (req.codec.equals("hevc", true) || req.codec.equals("h265", true)) {
            MimeTypes.VIDEO_H265
        } else {
            MimeTypes.VIDEO_H264
        }
        val targetBitrateBps = (est.targetBitrateKbps.coerceAtLeast(250) * 1000)

        try {
            val transformer = Transformer.Builder(context)
                .setLooper(Looper.getMainLooper())
                .setVideoMimeType(mime)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(
                    DefaultEncoderFactory.Builder(context)
                        .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(targetBitrateBps).build())
                        .setEnableFallback(true)
                        .build()
                )
                .build()
            runTransformer(jobId, transformer, edited, null, tempOut) { pct ->
                onProgress(pct * 0.92, "Compressing ${pct.roundToInt()}%")
            }
            onProgress(95.0, "Saving compressed file...")
            finalizeOutput(
                sourcePath = req.path,
                tempOutput = tempOut,
                suffix = "_compressed",
                forcedExt = "mp4",
                saveMode = req.saveMode,
                recycleOriginal = req.recycleOriginal,
                oldSize = oldSize,
                safTreeUris = safTreeUris,
            )
        } finally {
            runCatching { tempOut.delete() }
        }
    }

    suspend fun upscaleMedia(
        jobId: String,
        req: UpscaleRequest,
        safTreeUris: Map<String, String>,
        onProgress: (pct: Double, msg: String) -> Unit,
    ): MediaOpResponse = withContext(Dispatchers.IO) {
        val oldSize = localRepo.getFileLengthAny(req.path).coerceAtLeast(0L)
        val srcName = sourceDisplayName(req.path)
        val mediaType = LocalStorageRepository.classifyMedia(srcName)

        if (mediaType == "image") {
            val scale = if (req.preset == "2x") 2.0f else 1.5f
            val tgtH = when (req.preset.lowercase(Locale.US)) {
                "1080p" -> 1080
                "1440p" -> 1440
                "4k" -> 2160
                else -> 0
            }
            return@withContext processImageLocally(
                sourcePath = req.path,
                targetHeight = tgtH,
                scaleMultiplier = scale,
                quality = 95,
                saveMode = req.saveMode,
                recycleOriginal = req.recycleOriginal,
                suffix = "_upscaled",
                safTreeUris = safTreeUris,
                onProgress = onProgress,
            )
        }

        val meta = localRepo.probeVideoMeta(req.path)
        val srcH = minOf(meta?.width ?: 720, meta?.height ?: 720).takeIf { it > 0 } ?: 720
        val targetH = when (req.preset.lowercase(Locale.US)) {
            "1080p" -> 1080
            "1440p" -> 1440
            "4k" -> 2160
            else -> (srcH * 2).coerceAtMost(2160)
        }
        val effects = mutableListOf<Effect>(Presentation.createForHeight(targetH))
        if (req.sharpen > 0.25f) {
            effects.add(Contrast((req.sharpen * 0.08f).coerceIn(0.02f, 0.14f)))
        }
        val edited = EditedMediaItem.Builder(MediaItem.fromUri(toInputUri(req.path)))
            .setEffects(Effects(emptyList(), effects))
            .build()

        val tempOut = File(tempDir, "upscale_${UUID.randomUUID()}.mp4")
        val targetBitrate = if (targetH >= 2160) 24_000_000 else if (targetH >= 1440) 14_000_000 else 8_500_000

        try {
            val transformer = Transformer.Builder(context)
                .setLooper(Looper.getMainLooper())
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(
                    DefaultEncoderFactory.Builder(context)
                        .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(targetBitrate).build())
                        .setEnableFallback(true)
                        .build()
                )
                .build()
            runTransformer(jobId, transformer, edited, null, tempOut) { pct ->
                onProgress(pct * 0.92, "Upscaling to ${targetH}p (${pct.roundToInt()}%)")
            }
            onProgress(95.0, "Saving upscaled file...")
            finalizeOutput(
                sourcePath = req.path,
                tempOutput = tempOut,
                suffix = "_${req.preset}_up",
                forcedExt = "mp4",
                saveMode = req.saveMode,
                recycleOriginal = req.recycleOriginal,
                oldSize = oldSize,
                safTreeUris = safTreeUris,
            )
        } finally {
            runCatching { tempOut.delete() }
        }
    }

    suspend fun extractAudio(
        jobId: String,
        req: ExtractAudioRequest,
        safTreeUris: Map<String, String>,
        onProgress: (pct: Double, msg: String) -> Unit,
    ): MediaOpResponse = withContext(Dispatchers.IO) {
        val oldSize = localRepo.getFileLengthAny(req.path).coerceAtLeast(0L)
        val miBuilder = MediaItem.Builder().setUri(toInputUri(req.path))
        if (req.startMs > 0L || req.endMs > req.startMs) {
            val clip = MediaItem.ClippingConfiguration.Builder().setStartPositionMs(req.startMs.coerceAtLeast(0L))
            if (req.endMs > req.startMs) clip.setEndPositionMs(req.endMs)
            miBuilder.setClippingConfiguration(clip.build())
        }
        val edited = EditedMediaItem.Builder(miBuilder.build())
            .setRemoveVideo(true)
            .build()
        val tempOut = File(tempDir, "audio_${UUID.randomUUID()}.m4a")
        try {
            val transformer = Transformer.Builder(context)
                .setLooper(Looper.getMainLooper())
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .build()
            runTransformer(jobId, transformer, edited, null, tempOut) { pct ->
                onProgress(pct * 0.92, "Extracting AAC audio ${pct.roundToInt()}%")
            }
            finalizeOutput(
                sourcePath = req.path,
                tempOutput = tempOut,
                suffix = "_audio",
                forcedExt = "m4a",
                saveMode = "copy",
                recycleOriginal = false,
                oldSize = oldSize,
                safTreeUris = safTreeUris,
            )
        } finally {
            runCatching { tempOut.delete() }
        }
    }

    suspend fun mergeVideos(
        jobId: String,
        req: MergeRequest,
        safTreeUris: Map<String, String>,
        onProgress: (pct: Double, msg: String) -> Unit,
    ): MediaOpResponse = withContext(Dispatchers.IO) {
        if (req.paths.size < 2) throw IllegalArgumentException("Select at least 2 videos to merge")
        val items = req.paths.map { p ->
            EditedMediaItem.Builder(MediaItem.fromUri(toInputUri(p))).build()
        }
        val sequence = EditedMediaItemSequence.Builder(items).build()
        val composition = Composition.Builder(sequence).build()
        val tempOut = File(tempDir, "merge_${UUID.randomUUID()}.mp4")
        val totalSrcBytes = req.paths.sumOf { localRepo.getFileLengthAny(it).coerceAtLeast(0L) }

        try {
            val transformer = Transformer.Builder(context)
                .setLooper(Looper.getMainLooper())
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .build()
            runTransformer(jobId, transformer, null, composition, tempOut) { pct ->
                onProgress(pct * 0.92, "Merging clips ${pct.roundToInt()}%")
            }
            onProgress(95.0, "Saving merged video...")
            val destFolder = if (req.outDir.isNotBlank() && !LocalStorageRepository.isVirtualMediaStorePath(req.outDir)) {
                localRepo.resolveSafePath(req.outDir)
            } else {
                defaultOutputDirFor(req.paths.first())
            }
            val cleanName = File(req.outputName.ifBlank { "Merged_Video.mp4" }).name
            val finalTarget = localRepo.resolveUniqueTargetFile(destFolder, cleanName, "rename")
            copyTempToWritableTarget(tempOut, finalTarget, safTreeUris)
            localRepo.scanMediaPath(finalTarget.absolutePath)
            val newSize = finalTarget.length().takeIf { it > 0L } ?: tempOut.length()
            MediaOpResponse(
                ok = true,
                path = finalTarget.absolutePath,
                name = finalTarget.name,
                oldSizeBytes = totalSrcBytes,
                newSizeBytes = newSize,
                savedBytes = 0L,
                sizeDeltaBytes = newSize,
                saveMode = "copy",
            )
        } finally {
            runCatching { tempOut.delete() }
        }
    }

    private suspend fun processImageLocally(
        sourcePath: String,
        targetHeight: Int,
        scaleMultiplier: Float,
        quality: Int,
        saveMode: String,
        recycleOriginal: Boolean,
        suffix: String,
        safTreeUris: Map<String, String>,
        onProgress: (pct: Double, msg: String) -> Unit,
    ): MediaOpResponse = withContext(Dispatchers.IO) {
        onProgress(18.0, "Decoding image...")
        val raw = localRepo.openInputStreamAny(sourcePath)?.use { BitmapFactory.decodeStream(it) }
            ?: throw IOException("Unable to decode image")
        val oldSize = localRepo.getFileLengthAny(sourcePath).coerceAtLeast(0L)
        val outW: Int
        val outH: Int
        if (targetHeight > 0) {
            outH = targetHeight
            outW = max(1, (raw.width.toDouble() * targetHeight / raw.height.coerceAtLeast(1)).roundToInt())
        } else {
            outW = max(1, (raw.width * scaleMultiplier).roundToInt())
            outH = max(1, (raw.height * scaleMultiplier).roundToInt())
        }
        onProgress(55.0, "Scaling ${outW}x$outH...")
        val scaled = if (outW != raw.width || outH != raw.height) {
            Bitmap.createScaledBitmap(raw, outW, outH, true)
        } else raw
        val tempOut = File(tempDir, "img_${UUID.randomUUID()}.jpg")
        try {
            tempOut.outputStream().use { fos ->
                scaled.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(30, 100), fos)
            }
            if (scaled !== raw) scaled.recycle()
            raw.recycle()
            onProgress(90.0, "Saving image...")
            finalizeOutput(
                sourcePath = sourcePath,
                tempOutput = tempOut,
                suffix = suffix,
                forcedExt = "jpg",
                saveMode = saveMode,
                recycleOriginal = recycleOriginal,
                oldSize = oldSize,
                safTreeUris = safTreeUris,
            )
        } finally {
            runCatching { tempOut.delete() }
        }
    }

    private suspend fun runTransformer(
        jobId: String,
        transformer: Transformer,
        editedItem: EditedMediaItem?,
        composition: Composition?,
        tempOut: File,
        onPct: (Double) -> Unit,
    ): Unit = withContext(Dispatchers.Main) {
        activeTransformers[jobId] = transformer
        try {
            suspendCancellableCoroutine<Unit> { cont ->
                val listener = object : Transformer.Listener {
                    override fun onCompleted(comp: Composition, exportResult: ExportResult) {
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onError(comp: Composition, exportResult: ExportResult, exportException: ExportException) {
                        if (cont.isActive) cont.resumeWithException(exportException)
                    }
                }
                transformer.addListener(listener)
                cont.invokeOnCancellation {
                    mainHandler.post { runCatching { transformer.cancel() } }
                }

                val progressHolder = ProgressHolder()
                val poller = object : Runnable {
                    override fun run() {
                        if (!cont.isActive || !isActive) return
                        val state = transformer.getProgress(progressHolder)
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onPct(progressHolder.progress.toDouble().coerceIn(0.0, 99.0))
                        }
                        mainHandler.postDelayed(this, 250L)
                    }
                }

                if (composition != null) {
                    transformer.start(composition, tempOut.absolutePath)
                } else if (editedItem != null) {
                    transformer.start(editedItem, tempOut.absolutePath)
                } else {
                    cont.resumeWithException(IllegalArgumentException("No media item provided"))
                    return@suspendCancellableCoroutine
                }
                mainHandler.postDelayed(poller, 250L)
            }
        } catch (ce: CancellationException) {
            runCatching { transformer.cancel() }
            throw ce
        } finally {
            activeTransformers.remove(jobId)
        }
    }

    private suspend fun finalizeOutput(
        sourcePath: String,
        tempOutput: File,
        suffix: String,
        forcedExt: String,
        saveMode: String,
        recycleOriginal: Boolean,
        oldSize: Long,
        safTreeUris: Map<String, String>,
    ): MediaOpResponse {
        val destDir = defaultOutputDirFor(sourcePath)
        val srcName = sourceDisplayName(sourcePath)
        val stem = srcName.substringBeforeLast('.', srcName).ifBlank { "DeckVault_Out" }
        val targetName = if (saveMode == "overwrite" && !LocalStorageRepository.isContentUriPath(sourcePath)) {
            "$stem.$forcedExt"
        } else {
            "${stem}${suffix}.$forcedExt"
        }

        val targetFile: File
        var recycled = false
        if (saveMode == "overwrite" && !LocalStorageRepository.isContentUriPath(sourcePath)) {
            val srcFile = File(sourcePath)
            val stagedDest = localRepo.resolveUniqueTargetFile(destDir, targetName, "rename")
            copyTempToWritableTarget(tempOutput, stagedDest, safTreeUris)
            localRepo.deleteItems(listOf(sourcePath), recycle = recycleOriginal, safTreeUris = safTreeUris)
            recycled = recycleOriginal
            val desiredSame = File(destDir, targetName)
            targetFile = if (srcFile.extension.equals(forcedExt, true) && !desiredSame.exists() && stagedDest.renameTo(desiredSame)) {
                desiredSame
            } else {
                stagedDest
            }
        } else {
            targetFile = localRepo.resolveUniqueTargetFile(destDir, targetName, "rename")
            copyTempToWritableTarget(tempOutput, targetFile, safTreeUris)
        }

        localRepo.scanMediaPath(targetFile.absolutePath)
        val newSize = targetFile.length().takeIf { it > 0L } ?: tempOutput.length()
        val saved = if (saveMode == "overwrite") (oldSize - newSize).coerceAtLeast(0L) else 0L
        return MediaOpResponse(
            ok = true,
            path = targetFile.absolutePath,
            name = targetFile.name,
            oldSizeBytes = oldSize,
            newSizeBytes = newSize,
            savedBytes = saved,
            sizeDeltaBytes = newSize - (if (saveMode == "overwrite") oldSize else 0L),
            saveMode = saveMode,
            recycledOriginal = recycled,
        )
    }

    private fun copyTempToWritableTarget(temp: File, target: File, safTreeUris: Map<String, String>) {
        FileInputStream(temp).use { input ->
            localRepo.openOutputStreamWritable(target, safTreeUris).use { output ->
                val buf = ByteArray(256 * 1024)
                while (true) {
                    val r = input.read(buf)
                    if (r < 0) break
                    if (r > 0) output.write(buf, 0, r)
                }
                output.flush()
            }
        }
    }

    private fun defaultOutputDirFor(sourcePath: String): File {
        if (!LocalStorageRepository.isContentUriPath(sourcePath) && !LocalStorageRepository.isVirtualMediaStorePath(sourcePath)) {
            File(sourcePath).parentFile?.takeIf { it.exists() }?.let { return it }
        }
        val movies = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES)
        return File(movies ?: context.filesDir, "DeckVault").apply { mkdirs() }
    }

    private fun sourceDisplayName(path: String): String {
        return if (LocalStorageRepository.isContentUriPath(path)) {
            "media_${path.substringAfterLast('/')}.mp4"
        } else {
            File(path).name
        }
    }

    private fun toInputUri(path: String): Uri =
        if (LocalStorageRepository.isContentUriPath(path)) Uri.parse(path) else Uri.fromFile(File(path))
}
