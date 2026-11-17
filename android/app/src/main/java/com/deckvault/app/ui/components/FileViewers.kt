package com.deckvault.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.webkit.MimeTypeMap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.WrapText
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import com.deckvault.app.data.DeckVaultApi
import com.deckvault.app.model.FileItem
import com.deckvault.app.model.SavedHost
import com.deckvault.app.ui.theme.LocalDeckSurfaces
import com.deckvault.app.ui.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private suspend fun downloadToPreviewCache(
    context: Context,
    api: DeckVaultApi,
    host: SavedHost,
    file: FileItem,
    onProgress: (Float) -> Unit,
): File = withContext(Dispatchers.IO) {
    if (host.isLocalDevice) {
        if (!com.deckvault.app.data.LocalStorageRepository.isContentUriPath(file.path)) {
            val localFile = File(file.path)
            if (localFile.exists()) {
                onProgress(1f)
                return@withContext localFile
            }
        }
        val previewDir = File(context.cacheDir, "preview").apply { mkdirs() }
        val safeName = file.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val target = File(previewDir, "local_${file.sizeBytes}_$safeName")
        context.contentResolver.openInputStream(android.net.Uri.parse(file.path))?.use { input ->
            FileOutputStream(target).use { out -> input.copyTo(out) }
        }
        onProgress(1f)
        return@withContext target
    }
    val previewDir = File(context.cacheDir, "preview").apply { mkdirs() }
    val safeName = file.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    val target = File(previewDir, "${host.hostId.take(8)}_${file.sizeBytes}_$safeName")
    if (target.exists() && target.length() == file.sizeBytes && file.sizeBytes > 0L) {
        onProgress(1f)
        return@withContext target
    }
    val tmp = File(previewDir, "${target.name}.part")
    api.downloadFile(
        host = host,
        remotePath = file.path,
        openOutput = { FileOutputStream(tmp) },
        onProgress = { read ->
            if (file.sizeBytes > 0L) {
                onProgress((read.toFloat() / file.sizeBytes.toFloat()).coerceIn(0f, 1f))
            }
        },
    )
    if (target.exists()) target.delete()
    tmp.renameTo(target)
    onProgress(1f)
    target
}

private fun openCachedFileWithSystemApp(context: Context, localFile: File, ext: String): String? {
    return try {
        val cleanExt = ext.trimStart('.').lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(cleanExt) ?: "*/*"
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", localFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Open ${localFile.name}"))
        null
    } catch (e: Exception) {
        e.message ?: "No app found to open this file type"
    }
}

@Composable
fun AudioPlayerOverlay(
    file: FileItem,
    streamUrl: String,
    hasPrevious: Boolean,
    hasNext: Boolean,
    initialSpeed: Float,
    seekStepSeconds: Int,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    onSpeedChanged: (Float) -> Unit,
    onMoveCopy: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val surfaces = LocalDeckSurfaces.current

    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember(file.path) { mutableLongStateOf(file.meta?.durationMs ?: 0L) }
    var speed by remember { mutableFloatStateOf(initialSpeed) }

    val player = remember(context) {
        val dataSource = DefaultDataSource.Factory(context)
        val extractors = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setConstantBitrateSeekingAlwaysEnabled(true)
        val renderers = NextRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
        ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource, extractors))
            .build().apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && player.duration > 0) {
                    durationMs = player.duration
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(streamUrl) {
        player.setMediaItem(MediaItem.fromUri(streamUrl))
        player.prepare()
        player.setPlaybackSpeed(speed)
        player.play()
    }

    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            if (player.duration > 0) durationMs = player.duration
            delay(180L)
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(surfaces.background.copy(alpha = 0.96f))
            .padding(20.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = surfaces.panelSurface,
            border = BorderStroke(1.dp, surfaces.hairlineBorder),
            modifier = Modifier.fillMaxWidth(0.95f),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(24.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            file.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = surfaces.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${file.extension.trimStart('.').uppercase()}  •  ${Formatters.formatBytes(file.sizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = surfaces.textSecondary,
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = surfaces.textPrimary)
                    }
                }

                // Visual waveform banner
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = surfaces.cardSurface,
                    border = BorderStroke(1.dp, surfaces.hairlineBorder),
                    modifier = Modifier.fillMaxWidth().height(110.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                            modifier = Modifier.size(56.dp),
                        )
                    }
                }

                val safeDur = durationMs.coerceAtLeast(1L)
                Column {
                    Slider(
                        value = (positionMs.toFloat() / safeDur.toFloat()).coerceIn(0f, 1f),
                        onValueChange = { frac ->
                            val target = (frac * safeDur).toLong()
                            positionMs = target
                            player.seekTo(target)
                        },
                    )
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(Formatters.formatDuration(positionMs), style = MaterialTheme.typography.labelMedium, color = surfaces.textSecondary)
                        Text(Formatters.formatDuration(durationMs), style = MaterialTheme.typography.labelMedium, color = surfaces.textSecondary)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    IconButton(onClick = onPreviousTrack, enabled = hasPrevious) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous")
                    }
                    IconButton(onClick = { player.seekTo((positionMs - seekStepSeconds * 1000L).coerceAtLeast(0L)) }) {
                        Icon(Icons.Rounded.Replay10, contentDescription = "Back $seekStepSeconds seconds")
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalIconButton(
                        onClick = { if (isPlaying) player.pause() else player.play() },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier.size(62.dp),
                    ) {
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { player.seekTo((positionMs + seekStepSeconds * 1000L).coerceAtMost(safeDur)) }) {
                        Icon(Icons.Rounded.Forward10, contentDescription = "Forward $seekStepSeconds seconds")
                    }
                    IconButton(onClick = onNextTrack, enabled = hasNext) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Next")
                    }
                }

                // Speed selector + Actions
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                        FilterChip(
                            selected = kotlin.math.abs(speed - spd) < 0.05f,
                            onClick = {
                                speed = spd
                                player.setPlaybackSpeed(spd)
                                onSpeedChanged(spd)
                            },
                            label = { Text("${spd}x") },
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(onClick = onMoveCopy, shape = RoundedCornerShape(50), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.DriveFileMove, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Move / Copy")
                    }
                    OutlinedButton(
                        onClick = onDelete,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = surfaces.dangerMuted),
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete")
                    }
                }
            }
        }
    }
}

@Composable
fun PdfViewerOverlay(
    file: FileItem,
    host: SavedHost,
    api: DeckVaultApi,
    onMoveCopy: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val surfaces = LocalDeckSurfaces.current
    val scope = rememberCoroutineScope()

    var localPdf by remember(file.path) { mutableStateOf<File?>(null) }
    var progress by remember(file.path) { mutableFloatStateOf(0f) }
    var errorMsg by remember(file.path) { mutableStateOf<String?>(null) }
    var pageCount by remember(file.path) { mutableIntStateOf(0) }
    var pfdHolder by remember(file.path) { mutableStateOf<Pair<ParcelFileDescriptor, PdfRenderer>?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(file.path, host.hostId) {
        errorMsg = null
        try {
            val cached = downloadToPreviewCache(context, api, host, file) { progress = it }
            localPdf = cached
            val opened = withContext(Dispatchers.IO) {
                val pfd = ParcelFileDescriptor.open(cached, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                pfd to renderer
            }
            pfdHolder = opened
            pageCount = opened.second.pageCount
        } catch (e: Exception) {
            errorMsg = e.message ?: "Failed to load PDF"
        }
    }

    DisposableEffect(pfdHolder) {
        val current = pfdHolder
        onDispose {
            runCatching { current?.second?.close() }
            runCatching { current?.first?.close() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaces.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = surfaces.panelSurface,
                border = BorderStroke(1.dp, surfaces.hairlineBorder),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(file.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (pageCount > 0) "$pageCount pages  •  ${Formatters.formatBytes(file.sizeBytes)}"
                            else Formatters.formatBytes(file.sizeBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = surfaces.textSecondary,
                        )
                    }
                    if (localPdf != null) {
                        FilledTonalButton(
                            onClick = {
                                val err = openCachedFileWithSystemApp(context, localPdf!!, "pdf")
                                if (err != null) errorMsg = err
                            },
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Open With")
                        }
                    }
                    IconButton(onClick = onMoveCopy) {
                        Icon(Icons.Rounded.DriveFileMove, contentDescription = "Move / Copy")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = surfaces.dangerMuted)
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }
            }

            when {
                errorMsg != null -> {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(24.dp)) {
                        Text(errorMsg!!, color = surfaces.dangerMuted)
                    }
                }
                pfdHolder == null -> {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(32.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator()
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(0.55f))
                            Text("Loading PDF (${(progress * 100).toInt()}%)...", color = surfaces.textSecondary)
                        }
                    }
                }
                else -> {
                    val renderer = pfdHolder!!.second
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    ) {
                        items(pageCount) { pageIndex ->
                            PdfPageItem(renderer = renderer, pageIndex = pageIndex)
                        }
                    }

                    if (pageCount > 1) {
                        val currentPage = (listState.firstVisibleItemIndex + 1).coerceIn(1, pageCount)
                        Surface(
                            color = surfaces.panelSurface,
                            border = BorderStroke(1.dp, surfaces.hairlineBorder),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            ) {
                                Text("Page $currentPage / $pageCount", style = MaterialTheme.typography.labelMedium)
                                Slider(
                                    value = currentPage.toFloat(),
                                    onValueChange = { target ->
                                        val idx = (target.toInt() - 1).coerceIn(0, pageCount - 1)
                                        scope.launch { listState.scrollToItem(idx) }
                                    },
                                    valueRange = 1f..pageCount.toFloat(),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPageItem(renderer: PdfRenderer, pageIndex: Int) {
    val surfaces = LocalDeckSurfaces.current
    var bitmap by remember(renderer, pageIndex) { mutableStateOf<Bitmap?>(null) }
    var aspect by remember(renderer, pageIndex) { mutableFloatStateOf(0.75f) }

    LaunchedEffect(renderer, pageIndex) {
        val result = withContext(Dispatchers.IO) {
            synchronized(renderer) {
                renderer.openPage(pageIndex).use { page ->
                    val targetWidth = 1400
                    val targetHeight = (targetWidth * (page.height.toFloat() / page.width.coerceAtLeast(1).toFloat()))
                        .toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(AndroidColor.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp to (page.width.toFloat() / page.height.coerceAtLeast(1).toFloat())
                }
            }
        }
        bitmap = result.first
        aspect = result.second
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, surfaces.hairlineBorder),
        modifier = Modifier.fillMaxWidth().aspectRatio(aspect),
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
            )
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().background(surfaces.cardSurface)) {
                CircularProgressIndicator(modifier = Modifier.size(26.dp))
            }
        }
    }
}

@Composable
fun TextViewerOverlay(
    file: FileItem,
    host: SavedHost,
    api: DeckVaultApi,
    onMoveCopy: () -> Unit,
    onDelete: () -> Unit,
    onToast: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val surfaces = LocalDeckSurfaces.current

    var textContent by remember(file.path) { mutableStateOf<String?>(null) }
    var truncated by remember(file.path) { mutableStateOf(false) }
    var localFile by remember(file.path) { mutableStateOf<File?>(null) }
    var errorMsg by remember(file.path) { mutableStateOf<String?>(null) }
    var searchTerm by remember { mutableStateOf("") }
    var wrapLines by remember { mutableStateOf(true) }

    LaunchedEffect(file.path, host.hostId) {
        errorMsg = null
        try {
            val cached = downloadToPreviewCache(context, api, host, file) {}
            localFile = cached
            val maxBytes = 2 * 1024 * 1024
            val loaded = withContext(Dispatchers.IO) {
                val len = cached.length()
                val bytes = cached.inputStream().use { ins ->
                    val buf = ByteArray(minOf(len, maxBytes.toLong()).toInt().coerceAtLeast(0))
                    var off = 0
                    while (off < buf.size) {
                        val r = ins.read(buf, off, buf.size - off)
                        if (r <= 0) break
                        off += r
                    }
                    buf.copyOf(off)
                }
                (len > maxBytes) to bytes.decodeToString()
            }
            truncated = loaded.first
            textContent = loaded.second
        } catch (e: Exception) {
            errorMsg = e.message ?: "Unable to read text file"
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(surfaces.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(color = surfaces.panelSurface, border = BorderStroke(1.dp, surfaces.hairlineBorder)) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(file.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${Formatters.formatBytes(file.sizeBytes)}${if (truncated) " (showing first 2 MB)" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = surfaces.textSecondary,
                            )
                        }
                        IconButton(onClick = { wrapLines = !wrapLines }) {
                            Icon(
                                Icons.Rounded.WrapText,
                                contentDescription = "Toggle line wrap",
                                tint = if (wrapLines) MaterialTheme.colorScheme.primary else surfaces.textSecondary,
                            )
                        }
                        IconButton(onClick = {
                            val txt = textContent ?: return@IconButton
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText(file.name, txt.take(250_000)))
                            onToast("Copied text to clipboard")
                        }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy text")
                        }
                        if (localFile != null) {
                            IconButton(onClick = {
                                val err = openCachedFileWithSystemApp(context, localFile!!, file.extension)
                                if (err != null) onToast(err)
                            }) {
                                Icon(Icons.Rounded.OpenInNew, contentDescription = "Open With")
                            }
                        }
                        IconButton(onClick = onMoveCopy) {
                            Icon(Icons.Rounded.DriveFileMove, contentDescription = "Move / Copy")
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = surfaces.dangerMuted)
                        }
                        IconButton(onClick = onClose) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = searchTerm,
                        onValueChange = { searchTerm = it },
                        singleLine = true,
                        placeholder = { Text("Filter lines in file...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            when {
                errorMsg != null -> {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(24.dp)) {
                        Text(errorMsg!!, color = surfaces.dangerMuted)
                    }
                }
                textContent == null -> {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator()
                    }
                }
                else -> {
                    val displayed = remember(textContent, searchTerm) {
                        val raw = textContent.orEmpty()
                        if (searchTerm.isBlank()) raw
                        else raw.lineSequence()
                            .mapIndexed { idx, line -> idx + 1 to line }
                            .filter { (_, line) -> line.contains(searchTerm, ignoreCase = true) }
                            .joinToString("\n") { (ln, line) -> "$ln: $line" }
                    }
                    val vScroll = rememberScrollState()
                    val hScroll = rememberScrollState()
                    val scrollMod = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(vScroll)
                        .let { if (wrapLines) it else it.horizontalScroll(hScroll) }
                        .padding(16.dp)

                    SelectionContainer {
                        Text(
                            text = displayed.ifEmpty { "(No matching lines)" },
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = surfaces.textPrimary,
                            softWrap = wrapLines,
                            modifier = scrollMod,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AnyFileViewerOverlay(
    file: FileItem,
    host: SavedHost,
    api: DeckVaultApi,
    onMoveCopy: () -> Unit,
    onDelete: () -> Unit,
    onToast: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val surfaces = LocalDeckSurfaces.current
    val scope = rememberCoroutineScope()

    var isDownloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(surfaces.background.copy(alpha = 0.94f))
            .padding(20.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = surfaces.panelSurface,
            border = BorderStroke(1.dp, surfaces.hairlineBorder),
            modifier = Modifier.fillMaxWidth(0.94f),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(24.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        Icons.Rounded.InsertDriveFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(file.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${file.extension.trimStart('.').uppercase().ifBlank { "FILE" }}  •  ${Formatters.formatBytes(file.sizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = surfaces.textSecondary,
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = surfaces.cardSurface,
                    border = BorderStroke(1.dp, surfaces.hairlineBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(14.dp)) {
                        Text("Path: ${file.path}", style = MaterialTheme.typography.bodySmall, color = surfaces.textSecondary)
                        Text(
                            "Modified: ${Formatters.formatShortDate(file.modifiedMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = surfaces.textSecondary,
                        )
                        Text("Host: ${host.label}", style = MaterialTheme.typography.bodySmall, color = surfaces.textSecondary)
                    }
                }

                if (isDownloading) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }

                Button(
                    onClick = {
                        if (isDownloading) return@Button
                        isDownloading = true
                        scope.launch {
                            try {
                                val cached = downloadToPreviewCache(context, api, host, file) { progress = it }
                                val err = openCachedFileWithSystemApp(context, cached, file.extension)
                                if (err != null) onToast(err)
                            } catch (e: Exception) {
                                onToast(e.message ?: "Download failed")
                            } finally {
                                isDownloading = false
                            }
                        }
                    },
                    enabled = !isDownloading,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isDownloading) "Downloading ${(progress * 100).toInt()}%..." else "Open With System App")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onMoveCopy, shape = RoundedCornerShape(50), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.DriveFileMove, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Move / Copy")
                    }
                    OutlinedButton(
                        onClick = onDelete,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = surfaces.dangerMuted),
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete")
                    }
                }
            }
        }
    }
}
