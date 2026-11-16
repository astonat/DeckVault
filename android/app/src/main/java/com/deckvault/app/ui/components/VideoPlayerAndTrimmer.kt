package com.deckvault.app.ui.components

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.deckvault.app.data.DeckVaultApi
import com.deckvault.app.model.FileItem
import com.deckvault.app.model.SavedHost
import com.deckvault.app.model.TrimResponse
import com.deckvault.app.model.UserPreferences
import com.deckvault.app.ui.theme.OledColors
import com.deckvault.app.ui.util.Formatters
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerAndTrimmerOverlay(
    host: SavedHost,
    api: DeckVaultApi,
    file: FileItem,
    prefs: UserPreferences,
    initialOpenTrimmer: Boolean,
    hasPrev: Boolean,
    hasNext: Boolean,
    isTrimming: Boolean,
    lastTrimResult: TrimResponse?,
    trimError: String?,
    cacheVersion: Long,
    onPrevVideo: () -> Unit,
    onNextVideo: () -> Unit,
    onDismiss: () -> Unit,
    onExecuteTrim: (startMs: Long, endMs: Long, mode: String, crf: Int, recycleOriginal: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val streamUrl = remember(host.baseUrl, file.path, cacheVersion) {
        api.streamUrl(host, file.path, cacheBust = cacheVersion)
    }
    val storyboardUrl = remember(host.baseUrl, file.path, cacheVersion) {
        api.videoStoryboardUrl(host, file.path, cacheBust = cacheVersion)
    }

    var playbackSpeed by remember { mutableFloatStateOf(prefs.defaultPlaybackSpeed) }
    val seekStepMs = (prefs.seekStepSeconds.coerceIn(3, 60)) * 1000L

    val player = remember(streamUrl) {
        val dataSourceFactory = OkHttpDataSource.Factory(api.okHttpClient)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build().apply {
                setMediaItem(MediaItem.fromUri(streamUrl))
                playbackParameters = PlaybackParameters(playbackSpeed)
                prepare()
                playWhenReady = if (initialOpenTrimmer) false else prefs.autoPlayVideos
            }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember(file.path) { mutableLongStateOf(file.meta?.durationMs ?: 0L) }
    var positionMs by remember(file.path) { mutableLongStateOf(0L) }
    var trimModeOpen by remember(file.path) { mutableStateOf(initialOpenTrimmer) }
    var previewCutoffMs by remember { mutableLongStateOf(-1L) }

    var trimStartMs by remember(file.path, cacheVersion) { mutableLongStateOf(0L) }
    var trimEndMs by remember(file.path, cacheVersion) { mutableLongStateOf(file.meta?.durationMs ?: 0L) }
    var trimEncodingMode by remember(file.path) { mutableStateOf(prefs.defaultTrimMode) } // lossless | compress
    var compressCrf by remember(file.path) { mutableIntStateOf(prefs.defaultCompressCrf.coerceIn(18, 32)) }
    var recycleOriginal by remember { mutableStateOf(prefs.recycleByDefault) }
    var showOverwriteConfirm by remember { mutableStateOf(false) }

    var resizeFill by remember { mutableStateOf(false) }

    LaunchedEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (player.duration > 0L) {
                    durationMs = player.duration
                    if (trimEndMs <= 0L || trimEndMs > player.duration) {
                        trimEndMs = player.duration
                    }
                }
            }
        }
        player.addListener(listener)
        while (true) {
            val pos = player.currentPosition.coerceAtLeast(0L)
            positionMs = pos
            if (player.duration > 0L && durationMs != player.duration) {
                durationMs = player.duration
                if (trimEndMs <= 0L || trimEndMs > durationMs) {
                    trimEndMs = durationMs
                }
            }
            if (previewCutoffMs > 0L && pos >= previewCutoffMs && player.isPlaying) {
                player.pause()
                previewCutoffMs = -1L
            }
            delay(100L)
        }
    }

    Surface(
        color = OledColors.PitchBlack,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OledColors.PanelSurface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close", tint = OledColors.TextPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = OledColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            append(Formatters.formatBytes(file.sizeBytes))
                            if (durationMs > 0L) append("  •  ${Formatters.formatDuration(durationMs)}")
                            file.meta?.let {
                                val res = Formatters.formatResolution(it.width, it.height)
                                if (res.isNotBlank()) append("  •  $res")
                                if (it.videoCodec.isNotBlank()) append(" (${it.videoCodec.uppercase()})")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                    )
                }

                // Speed cycle pill
                Surface(
                    onClick = {
                        playbackSpeed = when (playbackSpeed) {
                            1.0f -> 1.25f
                            1.25f -> 1.5f
                            1.5f -> 2.0f
                            2.0f -> 0.5f
                            else -> 1.0f
                        }
                        player.playbackParameters = PlaybackParameters(playbackSpeed)
                    },
                    shape = RoundedCornerShape(50),
                    color = OledColors.ElevatedSurface,
                    border = BorderStroke(1.dp, OledColors.HairlineBorder),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Icon(Icons.Rounded.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                        Text("${playbackSpeed}x", style = MaterialTheme.typography.labelMedium, color = OledColors.TextPrimary)
                    }
                }

                // Fit / Zoom toggle
                IconButton(onClick = { resizeFill = !resizeFill }) {
                    Icon(
                        Icons.Rounded.AspectRatio,
                        contentDescription = "Toggle Fill",
                        tint = if (resizeFill) MaterialTheme.colorScheme.primary else OledColors.TextSecondary,
                    )
                }

                // Cut & Save toggle pill
                Button(
                    onClick = { trimModeOpen = !trimModeOpen },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (trimModeOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (trimModeOpen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Rounded.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (trimModeOpen) "Hide Cutter" else "Cut Video")
                }
            }

            // Video Surface
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = false
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                        }
                    },
                    update = { pv ->
                        if (pv.player !== player) pv.player = player
                        pv.resizeMode = if (resizeFill) {
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        } else {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    if (player.isPlaying) player.pause() else player.play()
                                },
                                onDoubleTap = { offset ->
                                    val half = size.width / 2f
                                    val delta = if (offset.x < half) -seekStepMs else seekStepMs
                                    player.seekTo((player.currentPosition + delta).coerceIn(0L, durationMs.coerceAtLeast(0L)))
                                },
                            )
                        },
                )
            }

            // Bottom Transport Controls + Built-in Cutter Panel
            Surface(
                color = OledColors.PanelSurface,
                border = BorderStroke(1.dp, OledColors.HairlineBorder),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    // Normal Scrubber + Transport Row
                    val safeDur = durationMs.coerceAtLeast(1L)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            Formatters.formatDuration(positionMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Slider(
                            value = (positionMs.toFloat() / safeDur.toFloat()).coerceIn(0f, 1f),
                            onValueChange = { frac ->
                                previewCutoffMs = -1L
                                val target = (frac * safeDur).roundToLong()
                                positionMs = target
                                player.seekTo(target)
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = OledColors.HighestSurface,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            Formatters.formatDuration(durationMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = OledColors.TextSecondary,
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        IconButton(onClick = onPrevVideo, enabled = hasPrev) {
                            Icon(
                                Icons.Rounded.SkipPrevious,
                                contentDescription = "Previous video",
                                tint = if (hasPrev) OledColors.TextPrimary else OledColors.TextSecondary.copy(alpha = 0.35f),
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = { player.seekTo((player.currentPosition - seekStepMs).coerceAtLeast(0L)) }) {
                            Icon(Icons.Rounded.Replay10, contentDescription = "-${prefs.seekStepSeconds}s", tint = OledColors.TextPrimary)
                        }
                        Spacer(Modifier.width(10.dp))
                        FilledTonalIconButton(
                            onClick = {
                                previewCutoffMs = -1L
                                if (player.isPlaying) player.pause() else player.play()
                            },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = "Play or Pause",
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        IconButton(onClick = { player.seekTo((player.currentPosition + seekStepMs).coerceAtMost(safeDur)) }) {
                            Icon(Icons.Rounded.Forward10, contentDescription = "+${prefs.seekStepSeconds}s", tint = OledColors.TextPrimary)
                        }
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = onNextVideo, enabled = hasNext) {
                            Icon(
                                Icons.Rounded.SkipNext,
                                contentDescription = "Next video",
                                tint = if (hasNext) OledColors.TextPrimary else OledColors.TextSecondary.copy(alpha = 0.35f),
                            )
                        }
                    }

                    // Visual Trimmer & Save-Over-Original Studio
                    AnimatedVisibility(visible = trimModeOpen) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        ) {
                            // Visual Filmstrip Range Trimmer
                            VisualFilmstripRangeTrimmer(
                                storyboardUrl = storyboardUrl,
                                durationMs = safeDur,
                                startMs = trimStartMs,
                                endMs = trimEndMs.coerceIn(0L, safeDur),
                                playheadMs = positionMs,
                                onRangeChange = { newStart, newEnd, seekTarget ->
                                    trimStartMs = newStart
                                    trimEndMs = newEnd
                                    player.pause()
                                    player.seekTo(seekTarget)
                                },
                            )

                            // Precision In / Out Controls + Preview Selection
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        trimStartMs = positionMs.coerceAtMost((trimEndMs - 300L).coerceAtLeast(0L))
                                    },
                                    shape = RoundedCornerShape(50),
                                ) {
                                    Text("Set In (${Formatters.formatDurationPrecise(trimStartMs)})")
                                }
                                OutlinedButton(
                                    onClick = {
                                        trimEndMs = positionMs.coerceAtLeast((trimStartMs + 300L).coerceAtMost(safeDur))
                                    },
                                    shape = RoundedCornerShape(50),
                                ) {
                                    Text("Set Out (${Formatters.formatDurationPrecise(trimEndMs)})")
                                }
                                FilledTonalButton(
                                    onClick = {
                                        player.seekTo(trimStartMs)
                                        previewCutoffMs = trimEndMs
                                        player.play()
                                    },
                                    shape = RoundedCornerShape(50),
                                ) {
                                    Text("Preview Cut")
                                }
                            }

                            // Sub-second In/Out Nudge Controls
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                listOf(
                                    "In -0.5s" to { trimStartMs = (trimStartMs - 500L).coerceAtLeast(0L); player.seekTo(trimStartMs) },
                                    "In +0.5s" to { trimStartMs = (trimStartMs + 500L).coerceAtMost((trimEndMs - 300L).coerceAtLeast(0L)); player.seekTo(trimStartMs) },
                                    "Out -0.5s" to { trimEndMs = (trimEndMs - 500L).coerceAtLeast((trimStartMs + 300L).coerceAtMost(safeDur)); player.seekTo(trimEndMs) },
                                    "Out +0.5s" to { trimEndMs = (trimEndMs + 500L).coerceAtMost(safeDur); player.seekTo(trimEndMs) },
                                ).forEach { (label, action) ->
                                    OutlinedButton(
                                        onClick = {
                                            player.pause()
                                            action()
                                        },
                                        shape = RoundedCornerShape(50),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(label, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            // Estimated Savings Banner
                            val cutDurationMs = (trimEndMs - trimStartMs).coerceAtLeast(0L)
                            val ratio = if (safeDur > 0) (cutDurationMs.toDouble() / safeDur.toDouble()).coerceIn(0.0, 1.0) else 1.0
                            val estNewBytes = (file.sizeBytes * ratio).roundToLong()
                            val estSavedBytes = (file.sizeBytes - estNewBytes).coerceAtLeast(0L)
                            val pctSaved = ((1.0 - ratio) * 100).roundToInt().coerceIn(0, 99)

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                ) {
                                    Text(
                                        text = "Keep ${Formatters.formatDurationPrecise(cutDurationMs)}  •  " +
                                            "${Formatters.formatBytes(file.sizeBytes)} → ~${Formatters.formatBytes(estNewBytes)}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = OledColors.TextPrimary,
                                    )
                                    Text(
                                        text = "Saves ~${Formatters.formatBytes(estSavedBytes)} ($pctSaved%)",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = OledColors.SuccessGreen,
                                    )
                                }
                            }

                            // Mode Selection + Recycle Toggle + Save Button
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                FilterChip(
                                    selected = trimEncodingMode == "lossless",
                                    onClick = { trimEncodingMode = "lossless" },
                                    label = { Text("Lossless Keyframe (Instant)") },
                                )
                                FilterChip(
                                    selected = trimEncodingMode == "compress",
                                    onClick = { trimEncodingMode = "compress" },
                                    label = { Text("Re-encode & Compress") },
                                )
                                Spacer(Modifier.weight(1f))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Recycle original", style = MaterialTheme.typography.labelMedium, color = OledColors.TextSecondary)
                                    Switch(checked = recycleOriginal, onCheckedChange = { recycleOriginal = it })
                                }
                            }

                            if (trimEncodingMode == "compress") {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("CRF Quality: $compressCrf (higher = smaller)", style = MaterialTheme.typography.labelMedium, color = OledColors.TextSecondary)
                                    Slider(
                                        value = compressCrf.toFloat(),
                                        onValueChange = { compressCrf = it.roundToInt() },
                                        valueRange = 18f..32f,
                                        steps = 13,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }

                            if (trimError != null) {
                                Text(trimError, style = MaterialTheme.typography.bodySmall, color = OledColors.DangerRed)
                            }
                            if (lastTrimResult != null && lastTrimResult.path == file.path) {
                                Text(
                                    text = "Saved over original! ${Formatters.formatBytes(lastTrimResult.oldSizeBytes)} → " +
                                        "${Formatters.formatBytes(lastTrimResult.newSizeBytes)} (Reclaimed ${Formatters.formatBytes(lastTrimResult.savedBytes)})",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = OledColors.SuccessGreen,
                                )
                            }

                            Button(
                                onClick = { showOverwriteConfirm = true },
                                enabled = !isTrimming && cutDurationMs >= 300L,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                            ) {
                                if (isTrimming) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                    Spacer(Modifier.width(10.dp))
                                    Text("Cutting & Overwriting on ${host.displayName}...")
                                } else {
                                    Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Cut Video & Save Over Original File", style = MaterialTheme.typography.titleSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showOverwriteConfirm) {
        AlertDialog(
            onDismissRequest = { showOverwriteConfirm = false },
            containerColor = OledColors.ElevatedSurface,
            title = { Text("Save Over Original Video?", color = OledColors.TextPrimary) },
            text = {
                Text(
                    "DeckVault will trim '${file.name}' to " +
                        "${Formatters.formatDurationPrecise(trimStartMs)} – ${Formatters.formatDurationPrecise(trimEndMs)} " +
                        "and overwrite the file on ${host.displayName}.\n\n" +
                        if (recycleOriginal) "The old copy will be moved to the Windows Recycle Bin first."
                        else "The old copy will be permanently overwritten.",
                    color = OledColors.TextSecondary,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showOverwriteConfirm = false
                        player.pause()
                        onExecuteTrim(trimStartMs, trimEndMs, trimEncodingMode, compressCrf, recycleOriginal)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text("Overwrite Original")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverwriteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun VisualFilmstripRangeTrimmer(
    storyboardUrl: String,
    durationMs: Long,
    startMs: Long,
    endMs: Long,
    playheadMs: Long,
    onRangeChange: (newStartMs: Long, newEndMs: Long, seekMs: Long) -> Unit,
) {
    val safeDur = durationMs.coerceAtLeast(1L)
    val startFrac = (startMs.toFloat() / safeDur.toFloat()).coerceIn(0f, 1f)
    val endFrac = (endMs.toFloat() / safeDur.toFloat()).coerceIn(startFrac, 1f)
    val playFrac = (playheadMs.toFloat() / safeDur.toFloat()).coerceIn(0f, 1f)
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF12121A)),
    ) {
        val totalWidthPx = with(density) { maxWidth.toPx().coerceAtLeast(1f) }
        val handleWidth = 18.dp
        val handlePx = with(density) { handleWidth.toPx() }

        StoryboardFilmstripRow(
            storyboardUrl = storyboardUrl,
            slices = 12,
            modifier = Modifier.fillMaxSize(),
        )

        // Dimmed left & right trimmed-out regions
        if (startFrac > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(startFrac)
                    .background(Color.Black.copy(alpha = 0.72f)),
            )
        }
        if (endFrac < 1f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth((1f - endFrac).coerceIn(0f, 1f))
                    .background(Color.Black.copy(alpha = 0.72f)),
            )
        }

        // Active selection border frame
        val selStartPx = (startFrac * totalWidthPx).roundToInt()
        val selWidthPx = ((endFrac - startFrac) * totalWidthPx).roundToInt().coerceAtLeast(4)
        Box(
            modifier = Modifier
                .offset { IntOffset(selStartPx, 0) }
                .width(with(density) { selWidthPx.toDp() })
                .fillMaxHeight()
                .background(Color.Transparent)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            Box(Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.primary).align(Alignment.TopCenter))
            Box(Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.primary).align(Alignment.BottomCenter))
        }

        // Playhead line
        Box(
            modifier = Modifier
                .offset { IntOffset((playFrac * totalWidthPx).roundToInt(), 0) }
                .width(2.dp)
                .fillMaxHeight()
                .background(Color.White),
        )

        // Left Start Handle
        Box(
            modifier = Modifier
                .offset { IntOffset((startFrac * (totalWidthPx - handlePx)).roundToInt(), 0) }
                .width(handleWidth)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
                .pointerInput(safeDur, endMs) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaMs = ((dragAmount.x / totalWidthPx) * safeDur).roundToLong()
                        val nextStart = (startMs + deltaMs).coerceIn(0L, (endMs - 300L).coerceAtLeast(0L))
                        onRangeChange(nextStart, endMs, nextStart)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.width(3.dp).height(24.dp).background(Color.Black, CircleShape))
        }

        // Right End Handle
        Box(
            modifier = Modifier
                .offset { IntOffset((endFrac * (totalWidthPx - handlePx)).roundToInt(), 0) }
                .width(handleWidth)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
                .pointerInput(safeDur, startMs) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaMs = ((dragAmount.x / totalWidthPx) * safeDur).roundToLong()
                        val nextEnd = (endMs + deltaMs).coerceIn((startMs + 300L).coerceAtMost(safeDur), safeDur)
                        onRangeChange(startMs, nextEnd, nextEnd)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.width(3.dp).height(24.dp).background(Color.Black, CircleShape))
        }
    }
}
