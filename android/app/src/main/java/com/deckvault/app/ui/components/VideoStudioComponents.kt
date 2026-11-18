package com.deckvault.app.ui.components

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GifBox
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Queue
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Tablet
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.deckvault.app.model.CropBoxSpec
import com.deckvault.app.model.EditStudioRequest
import com.deckvault.app.model.ExtractAudioRequest
import com.deckvault.app.model.GifExportRequest
import com.deckvault.app.model.JobExecutionTarget
import com.deckvault.app.model.SavedHost
import com.deckvault.app.ui.theme.LocalDeckSurfaces
import com.deckvault.app.ui.util.Formatters
import kotlin.math.roundToInt

data class PlayerTrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val language: String,
    val selected: Boolean,
)

private enum class CropDragTarget {
    MOVE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    LEFT,
    RIGHT,
    TOP,
    BOTTOM,
}

@Composable
fun VisualCropOverlay(
    crop: CropBoxSpec,
    sourceWidth: Int = 0,
    sourceHeight: Int = 0,
    onCropChange: (CropBoxSpec) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val currentCrop by rememberUpdatedState(crop)
    val latestOnCropChange by rememberUpdatedState(onCropChange)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val wPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val hPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val density = LocalDensity.current
        val minFrac = 0.12f

        Canvas(modifier = Modifier.fillMaxSize()) {
            val left = crop.x * size.width
            val top = crop.y * size.height
            val cw = crop.w * size.width
            val ch = crop.h * size.height
            val right = left + cw
            val bottom = top + ch

            // Dim outside crop rectangle
            drawRect(Color.Black.copy(alpha = 0.56f), Offset.Zero, Size(size.width, top))
            drawRect(Color.Black.copy(alpha = 0.56f), Offset(0f, bottom), Size(size.width, (size.height - bottom).coerceAtLeast(0f)))
            drawRect(Color.Black.copy(alpha = 0.56f), Offset(0f, top), Size(left, ch))
            drawRect(Color.Black.copy(alpha = 0.56f), Offset(right, top), Size((size.width - right).coerceAtLeast(0f), ch))

            // Primary border + rule of thirds grid
            drawRect(
                color = accent,
                topLeft = Offset(left, top),
                size = Size(cw, ch),
                style = Stroke(width = 2.5.dp.toPx()),
            )
            for (i in 1..2) {
                val gx = left + cw * (i / 3f)
                val gy = top + ch * (i / 3f)
                drawLine(Color.White.copy(alpha = 0.34f), Offset(gx, top), Offset(gx, bottom), strokeWidth = 1.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.34f), Offset(left, gy), Offset(right, gy), strokeWidth = 1.dp.toPx())
            }

            // Crisp white L-shaped corner brackets
            val arm = minOf(18.dp.toPx(), cw * 0.28f, ch * 0.28f)
            val cornerStroke = 3.5.dp.toPx()
            drawLine(Color.White, Offset(left, top), Offset(left + arm, top), strokeWidth = cornerStroke)
            drawLine(Color.White, Offset(left, top), Offset(left, top + arm), strokeWidth = cornerStroke)
            drawLine(Color.White, Offset(right, top), Offset(right - arm, top), strokeWidth = cornerStroke)
            drawLine(Color.White, Offset(right, top), Offset(right, top + arm), strokeWidth = cornerStroke)
            drawLine(Color.White, Offset(left, bottom), Offset(left + arm, bottom), strokeWidth = cornerStroke)
            drawLine(Color.White, Offset(left, bottom), Offset(left, bottom - arm), strokeWidth = cornerStroke)
            drawLine(Color.White, Offset(right, bottom), Offset(right - arm, bottom), strokeWidth = cornerStroke)
            drawLine(Color.White, Offset(right, bottom), Offset(right, bottom - arm), strokeWidth = cornerStroke)

            // Edge midpoint pill bars
            val barHalf = minOf(12.dp.toPx(), cw * 0.16f)
            val vBarHalf = minOf(12.dp.toPx(), ch * 0.16f)
            drawLine(Color.White, Offset(left + cw * 0.5f - barHalf, top), Offset(left + cw * 0.5f + barHalf, top), strokeWidth = cornerStroke)
            drawLine(Color.White, Offset(left + cw * 0.5f - barHalf, bottom), Offset(left + cw * 0.5f + barHalf, bottom), strokeWidth = cornerStroke)
            drawLine(Color.White, Offset(left, top + ch * 0.5f - vBarHalf), Offset(left, top + ch * 0.5f + vBarHalf), strokeWidth = cornerStroke)
            drawLine(Color.White, Offset(right, top + ch * 0.5f - vBarHalf), Offset(right, top + ch * 0.5f + vBarHalf), strokeWidth = cornerStroke)
        }

        fun applyDelta(target: CropDragTarget, dxPx: Float, dyPx: Float) {
            val c = currentCrop
            val dx = dxPx / wPx
            val dy = dyPx / hPx
            val next = when (target) {
                CropDragTarget.MOVE -> c.copy(
                    x = (c.x + dx).coerceIn(0f, 1f - c.w),
                    y = (c.y + dy).coerceIn(0f, 1f - c.h),
                )
                CropDragTarget.LEFT -> {
                    val nx = (c.x + dx).coerceIn(0f, c.x + c.w - minFrac)
                    c.copy(x = nx, w = c.w + (c.x - nx))
                }
                CropDragTarget.RIGHT -> c.copy(
                    w = (c.w + dx).coerceIn(minFrac, 1f - c.x),
                )
                CropDragTarget.TOP -> {
                    val ny = (c.y + dy).coerceIn(0f, c.y + c.h - minFrac)
                    c.copy(y = ny, h = c.h + (c.y - ny))
                }
                CropDragTarget.BOTTOM -> c.copy(
                    h = (c.h + dy).coerceIn(minFrac, 1f - c.y),
                )
                CropDragTarget.TOP_LEFT -> {
                    val nx = (c.x + dx).coerceIn(0f, c.x + c.w - minFrac)
                    val ny = (c.y + dy).coerceIn(0f, c.y + c.h - minFrac)
                    c.copy(x = nx, y = ny, w = c.w + (c.x - nx), h = c.h + (c.y - ny))
                }
                CropDragTarget.TOP_RIGHT -> {
                    val ny = (c.y + dy).coerceIn(0f, c.y + c.h - minFrac)
                    c.copy(y = ny, w = (c.w + dx).coerceIn(minFrac, 1f - c.x), h = c.h + (c.y - ny))
                }
                CropDragTarget.BOTTOM_LEFT -> {
                    val nx = (c.x + dx).coerceIn(0f, c.x + c.w - minFrac)
                    c.copy(x = nx, w = c.w + (c.x - nx), h = (c.h + dy).coerceIn(minFrac, 1f - c.y))
                }
                CropDragTarget.BOTTOM_RIGHT -> c.copy(
                    w = (c.w + dx).coerceIn(minFrac, 1f - c.x),
                    h = (c.h + dy).coerceIn(minFrac, 1f - c.y),
                )
            }
            latestOnCropChange(next)
        }

        // Center body pan gesture
        val leftDp = with(density) { (crop.x * wPx).toDp() }
        val topDp = with(density) { (crop.y * hPx).toDp() }
        val widthDp = with(density) { (crop.w * wPx).toDp() }
        val heightDp = with(density) { (crop.h * hPx).toDp() }

        Box(
            modifier = Modifier
                .offset(x = leftDp, y = topDp)
                .size(width = widthDp, height = heightDp)
                .pointerInput(wPx, hPx) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        applyDelta(CropDragTarget.MOVE, drag.x, drag.y)
                    }
                },
        ) {
            val dimLabel = if (sourceWidth > 0 && sourceHeight > 0) {
                "${(crop.w * sourceWidth).roundToInt()} × ${(crop.h * sourceHeight).roundToInt()}"
            } else {
                "${(crop.w * 100).roundToInt()}% × ${(crop.h * 100).roundToInt()}%"
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(alpha = 0.72f),
                modifier = Modifier.padding(6.dp),
            ) {
                Text(
                    text = dimLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        // 8 Touch Handles (4 corners + 4 edges) kept cleanly inside stage bounds
        val touchSizePx = with(density) { 36.dp.roundToPx() }
        val halfTouchPx = touchSizePx / 2
        val leftPx = (crop.x * wPx).roundToInt()
        val rightPx = ((crop.x + crop.w) * wPx).roundToInt()
        val topPx = (crop.y * hPx).roundToInt()
        val bottomPx = ((crop.y + crop.h) * hPx).roundToInt()
        val midXPx = (leftPx + rightPx) / 2
        val midYPx = (topPx + bottomPx) / 2

        val handles = listOf(
            CropDragTarget.TOP_LEFT to IntOffset(leftPx - halfTouchPx, topPx - halfTouchPx),
            CropDragTarget.TOP_RIGHT to IntOffset(rightPx - halfTouchPx, topPx - halfTouchPx),
            CropDragTarget.BOTTOM_LEFT to IntOffset(leftPx - halfTouchPx, bottomPx - halfTouchPx),
            CropDragTarget.BOTTOM_RIGHT to IntOffset(rightPx - halfTouchPx, bottomPx - halfTouchPx),
            CropDragTarget.TOP to IntOffset(midXPx - halfTouchPx, topPx - halfTouchPx),
            CropDragTarget.BOTTOM to IntOffset(midXPx - halfTouchPx, bottomPx - halfTouchPx),
            CropDragTarget.LEFT to IntOffset(leftPx - halfTouchPx, midYPx - halfTouchPx),
            CropDragTarget.RIGHT to IntOffset(rightPx - halfTouchPx, midYPx - halfTouchPx),
        )
        handles.forEach { (dragTarget, pos) ->
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            pos.x.coerceIn(-halfTouchPx / 2, (wPx.toInt() - halfTouchPx).coerceAtLeast(0)),
                            pos.y.coerceIn(-halfTouchPx / 2, (hPx.toInt() - halfTouchPx).coerceAtLeast(0)),
                        )
                    }
                    .size(36.dp)
                    .pointerInput(dragTarget, wPx, hPx) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            applyDelta(dragTarget, drag.x, drag.y)
                        }
                    },
            )
        }
    }
}

@Composable
fun VisualFilmstripRangeTrimmer(
    frameUrls: List<String>,
    durationMs: Long,
    startMs: Long,
    endMs: Long,
    currentPositionMs: Long,
    onRangeChange: (Long, Long) -> Unit,
) {
    val surfaces = LocalDeckSurfaces.current
    val safeDur = durationMs.coerceAtLeast(1000L).toFloat()
    val startVal = startMs.coerceIn(0L, durationMs.coerceAtLeast(1000L)).toFloat().coerceAtMost((safeDur - 200f).coerceAtLeast(0f))
    val endVal = if (safeDur <= startVal + 200f) safeDur else endMs.toFloat().coerceIn(startVal + 200f, safeDur)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black)
                .border(1.dp, surfaces.hairlineBorder, RoundedCornerShape(10.dp)),
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                frameUrls.forEach { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                }
            }
            val startFrac = (startVal / safeDur).coerceIn(0f, 1f)
            val endFrac = (endVal / safeDur).coerceIn(0f, 1f)
            val playFrac = (currentPositionMs.toFloat() / safeDur).coerceIn(0f, 1f)
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(Color.Black.copy(alpha = 0.6f), Offset.Zero, Size(startFrac * size.width, size.height))
                drawRect(Color.Black.copy(alpha = 0.6f), Offset(endFrac * size.width, 0f), Size((1f - endFrac) * size.width, size.height))
                drawRect(
                    color = Color(0xFF7DD3FC),
                    topLeft = Offset(startFrac * size.width, 0f),
                    size = Size(((endFrac - startFrac) * size.width).coerceAtLeast(2f), size.height),
                    style = Stroke(width = 4f),
                )
                drawLine(Color.White, Offset(playFrac * size.width, 0f), Offset(playFrac * size.width, size.height), strokeWidth = 3f)
            }
        }

        RangeSlider(
            value = startVal..endVal,
            onValueChange = { rng ->
                val s = rng.start.roundToInt().toLong()
                val e = rng.endInclusive.roundToInt().toLong().coerceAtLeast(s + 250L)
                onRangeChange(s, e)
            },
            valueRange = 0f..safeDur,
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("In: ${Formatters.formatDurationPrecise(startMs)}", style = MaterialTheme.typography.labelSmall, color = surfaces.textPrimary)
            Text("Clip: ${Formatters.formatDurationPrecise((endMs - startMs).coerceAtLeast(0L))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text("Out: ${Formatters.formatDurationPrecise(endMs)}", style = MaterialTheme.typography.labelSmall, color = surfaces.textPrimary)
        }
    }
}

@Composable
fun StudioEditorPanel(
    filePath: String,
    frameUrls: List<String>,
    durationMs: Long,
    currentPosMs: Long,
    startMs: Long,
    endMs: Long,
    crop: CropBoxSpec,
    cropActive: Boolean,
    rotate: Int,
    flipH: Boolean,
    flipV: Boolean,
    speed: Float,
    muteAudio: Boolean,
    brightness: Float,
    contrast: Float,
    saturation: Float,
    sharpen: Float,
    deband: Boolean,
    stabilize: Boolean,
    normalizeAudio: Boolean,
    saveMode: String,
    isLocalSource: Boolean,
    pairedPcHosts: List<SavedHost>,
    selectedOffloadHost: SavedHost?,
    executionTarget: JobExecutionTarget,
    activeTab: Int = 0,
    onActiveTabChange: (Int) -> Unit = {},
    sideRailMode: Boolean = false,
    onCollapsePeek: (() -> Unit)? = null,
    onSetStartEnd: (Long, Long) -> Unit,
    onToggleCropActive: (Boolean) -> Unit,
    onCropChange: (CropBoxSpec) -> Unit,
    onRotateChange: (Int) -> Unit,
    onFlipHChange: (Boolean) -> Unit,
    onFlipVChange: (Boolean) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onMuteChange: (Boolean) -> Unit,
    onColorChange: (Float, Float, Float) -> Unit,
    onProFlagsChange: (Float, Boolean, Boolean, Boolean) -> Unit,
    onSaveModeChange: (String) -> Unit,
    onExecutionTargetChange: (JobExecutionTarget, SavedHost?) -> Unit,
    onExtractAudio: (ExtractAudioRequest, JobExecutionTarget, SavedHost?) -> Unit,
    onExportGif: (GifExportRequest, SavedHost?) -> Unit,
    onSubmitEdit: (EditStudioRequest, JobExecutionTarget, SavedHost?, runNow: Boolean) -> Unit,
) {
    val surfaces = LocalDeckSurfaces.current
    val tab = activeTab.coerceIn(0, 3)
    val studioChipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        containerColor = surfaces.elevatedSurface,
        labelColor = surfaces.textSecondary,
    )

    Surface(
        shape = if (sideRailMode) RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp) else RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = surfaces.panelSurface,
        border = BorderStroke(1.dp, surfaces.hairlineBorder),
        modifier = if (sideRailMode) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (sideRailMode) Modifier.fillMaxHeight() else Modifier)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                ) {
                    FilterChip(selected = tab == 0, onClick = { onActiveTabChange(0); onToggleCropActive(false) }, colors = studioChipColors, leadingIcon = { Icon(Icons.Rounded.ContentCut, null, Modifier.size(16.dp)) }, label = { Text("Trim & Cut") })
                    FilterChip(selected = tab == 1, onClick = { onActiveTabChange(1); onToggleCropActive(true) }, colors = studioChipColors, leadingIcon = { Icon(Icons.Rounded.Crop, null, Modifier.size(16.dp)) }, label = { Text("Crop & Rotate") })
                    FilterChip(selected = tab == 2, onClick = { onActiveTabChange(2); onToggleCropActive(false) }, colors = studioChipColors, label = { Text("Color & Pro") })
                    FilterChip(selected = tab == 3, onClick = { onActiveTabChange(3); onToggleCropActive(false) }, colors = studioChipColors, leadingIcon = { Icon(Icons.Rounded.GifBox, null, Modifier.size(16.dp)) }, label = { Text("GIF / Audio") })
                }
                if (onCollapsePeek != null) {
                    AssistChip(
                        onClick = onCollapsePeek,
                        leadingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(16.dp)) },
                        label = { Text("Peek") },
                    )
                }
            }

            if (isLocalSource) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    FilterChip(
                        selected = executionTarget == JobExecutionTarget.ON_DEVICE,
                        onClick = { onExecutionTargetChange(JobExecutionTarget.ON_DEVICE, selectedOffloadHost) },
                        leadingIcon = { Icon(Icons.Rounded.Tablet, null, Modifier.size(15.dp)) },
                        label = { Text("This Tablet") },
                    )
                    pairedPcHosts.forEach { pc ->
                        FilterChip(
                            selected = executionTarget == JobExecutionTarget.PC_OFFLOAD && selectedOffloadHost?.hostId == pc.hostId,
                            onClick = { onExecutionTargetChange(JobExecutionTarget.PC_OFFLOAD, pc) },
                            leadingIcon = { Icon(Icons.Rounded.Computer, null, Modifier.size(15.dp)) },
                            label = { Text("Offload: ${pc.displayName}") },
                        )
                    }
                }
            }

            when (tab) {
                0 -> {
                    VisualFilmstripRangeTrimmer(
                        frameUrls = frameUrls,
                        durationMs = durationMs,
                        startMs = startMs,
                        endMs = endMs,
                        currentPositionMs = currentPosMs,
                        onRangeChange = onSetStartEnd,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { onSetStartEnd(currentPosMs, maxOf(endMs, currentPosMs + 500L)) }, label = { Text("Set In = Now") })
                        AssistChip(onClick = { onSetStartEnd(minOf(startMs, currentPosMs - 500L).coerceAtLeast(0L), currentPosMs) }, label = { Text("Set Out = Now") })
                        AssistChip(onClick = { onSetStartEnd(0L, durationMs) }, label = { Text("Reset") })
                    }
                }
                1 -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Drag crop box directly on video", style = MaterialTheme.typography.labelMedium, color = surfaces.textPrimary)
                        Switch(checked = cropActive, onCheckedChange = onToggleCropActive)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        AssistChip(onClick = { onCropChange(CropBoxSpec(0f, 0f, 1f, 1f)) }, label = { Text("Full Frame") })
                        AssistChip(onClick = { onCropChange(CropBoxSpec(0.12f, 0.1f, 0.76f, 0.8f)) }, label = { Text("Tight") })
                        AssistChip(onClick = { onCropChange(CropBoxSpec(0.22f, 0.05f, 0.56f, 0.9f)) }, label = { Text("Vertical 9:16") })
                        AssistChip(onClick = { onCropChange(CropBoxSpec(0.2f, 0.1f, 0.6f, 0.8f)) }, label = { Text("Square 1:1") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = { onRotateChange((rotate + 90) % 360) },
                            leadingIcon = { Icon(Icons.Rounded.RotateRight, null, Modifier.size(16.dp)) },
                            label = { Text("Rotate (${rotate}°)") },
                        )
                        FilterChip(
                            selected = flipH,
                            onClick = { onFlipHChange(!flipH) },
                            leadingIcon = { Icon(Icons.Rounded.Flip, null, Modifier.size(16.dp)) },
                            label = { Text("Flip H") },
                        )
                        FilterChip(
                            selected = flipV,
                            onClick = { onFlipVChange(!flipV) },
                            label = { Text("Flip V") },
                        )
                        FilterChip(
                            selected = muteAudio,
                            onClick = { onMuteChange(!muteAudio) },
                            leadingIcon = { Icon(Icons.Rounded.VolumeOff, null, Modifier.size(16.dp)) },
                            label = { Text("Mute") },
                        )
                    }
                    Text("Export Speed: ${String.format("%.2fx", speed)}", style = MaterialTheme.typography.labelMedium, color = surfaces.textPrimary)
                    Slider(value = speed, onValueChange = onSpeedChange, valueRange = 0.25f..3.0f)
                }
                2 -> {
                    Text("Brightness (${String.format("%+.2f", brightness)})", style = MaterialTheme.typography.labelSmall, color = surfaces.textSecondary)
                    Slider(value = brightness, onValueChange = { onColorChange(it, contrast, saturation) }, valueRange = -0.5f..0.5f)
                    Text("Contrast (${String.format("%.2f", contrast)})", style = MaterialTheme.typography.labelSmall, color = surfaces.textSecondary)
                    Slider(value = contrast, onValueChange = { onColorChange(brightness, it, saturation) }, valueRange = 0.5f..1.8f)
                    Text("Saturation (${String.format("%.2f", saturation)})", style = MaterialTheme.typography.labelSmall, color = surfaces.textSecondary)
                    Slider(value = saturation, onValueChange = { onColorChange(brightness, contrast, it) }, valueRange = 0f..2.0f)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        AssistChip(onClick = { onColorChange(0f, 1f, 1f) }, label = { Text("Reset Color") })
                        FilterChip(
                            selected = sharpen > 0.2f,
                            enabled = !isLocalSource || pairedPcHosts.isNotEmpty(),
                            onClick = { onProFlagsChange(if (sharpen > 0.2f) 0f else 0.7f, deband, stabilize, normalizeAudio) },
                            label = { Text("CAS Sharpen (PC)") },
                        )
                        FilterChip(
                            selected = deband,
                            enabled = !isLocalSource || pairedPcHosts.isNotEmpty(),
                            onClick = { onProFlagsChange(sharpen, !deband, stabilize, normalizeAudio) },
                            label = { Text("Deband (PC)") },
                        )
                        FilterChip(
                            selected = stabilize,
                            enabled = !isLocalSource || pairedPcHosts.isNotEmpty(),
                            onClick = { onProFlagsChange(sharpen, deband, !stabilize, normalizeAudio) },
                            label = { Text("Stabilize (PC)") },
                        )
                        FilterChip(
                            selected = normalizeAudio,
                            enabled = !isLocalSource || pairedPcHosts.isNotEmpty(),
                            onClick = { onProFlagsChange(sharpen, deband, stabilize, !normalizeAudio) },
                            label = { Text("Loudnorm (PC)") },
                        )
                    }
                }
                3 -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                onExtractAudio(
                                    ExtractAudioRequest(
                                        path = filePath,
                                        format = "m4a",
                                        bitrateKbps = 192,
                                        startMs = startMs,
                                        endMs = if (endMs < durationMs) endMs else 0L,
                                    ),
                                    if (isLocalSource) executionTarget else JobExecutionTarget.PC_REMOTE,
                                    selectedOffloadHost,
                                )
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.MusicNote, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Extract M4A Audio")
                        }

                        OutlinedButton(
                            enabled = !isLocalSource || pairedPcHosts.isNotEmpty(),
                            onClick = {
                                onExportGif(
                                    GifExportRequest(
                                        path = filePath,
                                        startMs = startMs,
                                        endMs = endMs.coerceAtMost(startMs + 15_000L),
                                        fps = 15,
                                        width = 640,
                                        crop = if (crop != CropBoxSpec()) crop else null,
                                        rotate = rotate,
                                        flipH = flipH,
                                        flipV = flipV,
                                        speed = speed.toDouble(),
                                    ),
                                    selectedOffloadHost,
                                )
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.GifBox, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isLocalSource) "GIF (PC Offload)" else "Export GIF")
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = saveMode == "copy", onClick = { onSaveModeChange("copy") }, label = { Text("Save Copy") })
                    FilterChip(selected = saveMode == "overwrite", onClick = { onSaveModeChange("overwrite") }, label = { Text("Overwrite") })
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val req = EditStudioRequest(
                        path = filePath,
                        startMs = startMs,
                        endMs = endMs,
                        crop = if (crop.x > 0.005f || crop.y > 0.005f || crop.w < 0.995f || crop.h < 0.995f) crop else null,
                        rotate = rotate,
                        flipH = flipH,
                        flipV = flipV,
                        speed = speed.toDouble(),
                        muteAudio = muteAudio,
                        saveMode = saveMode,
                        recycleOriginal = true,
                        brightness = brightness,
                        contrast = contrast,
                        saturation = saturation,
                        sharpen = sharpen,
                        deband = deband,
                        stabilize = stabilize,
                        normalizeAudio = normalizeAudio,
                    )
                    OutlinedButton(onClick = { onSubmitEdit(req, executionTarget, selectedOffloadHost, false) }) {
                        Icon(Icons.Rounded.Queue, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Queue")
                    }
                    Button(onClick = { onSubmitEdit(req, executionTarget, selectedOffloadHost, true) }) {
                        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Render")
                    }
                }
            }
        }
    }
}

@Composable
fun TracksAndSleepDialog(
    audioTracks: List<PlayerTrackOption>,
    subtitleTracks: List<PlayerTrackOption>,
    sleepMinutesRemaining: Int?,
    onSelectAudioTrack: (PlayerTrackOption) -> Unit,
    onSelectSubtitleTrack: (PlayerTrackOption?) -> Unit,
    onPickExternalSubtitle: () -> Unit,
    onSetSleepMinutes: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val surfaces = LocalDeckSurfaces.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = surfaces.panelSurface,
            border = BorderStroke(1.dp, surfaces.hairlineBorder),
            modifier = Modifier.fillMaxWidth(0.85f),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Audio, Subtitles & Sleep Timer", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = surfaces.textPrimary, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Close") }
                }
                HorizontalDivider(color = surfaces.hairlineBorder)

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Audiotrack, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Audio Tracks", style = MaterialTheme.typography.labelLarge, color = surfaces.textPrimary)
                }
                if (audioTracks.isEmpty()) {
                    Text("Default single audio track", style = MaterialTheme.typography.bodySmall, color = surfaces.textSecondary)
                } else {
                    audioTracks.forEach { tr ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (tr.selected) MaterialTheme.colorScheme.primaryContainer else surfaces.cardSurface,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onSelectAudioTrack(tr) },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(10.dp)) {
                                Text("${tr.label} ${if (tr.language.isNotBlank()) "(${tr.language})" else ""}", color = surfaces.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (tr.selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Subtitles, null, tint = MaterialTheme.colorScheme.primary)
                        Text("Subtitles (.srt / .vtt)", style = MaterialTheme.typography.labelLarge, color = surfaces.textPrimary)
                    }
                    AssistChip(
                        onClick = onPickExternalSubtitle,
                        leadingIcon = { Icon(Icons.Rounded.FolderOpen, null, Modifier.size(16.dp)) },
                        label = { Text("Load File") },
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (subtitleTracks.none { it.selected }) MaterialTheme.colorScheme.primaryContainer else surfaces.cardSurface,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onSelectSubtitleTrack(null) },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(10.dp)) {
                        Text("Subtitles Off", color = surfaces.textPrimary, modifier = Modifier.weight(1f))
                        if (subtitleTracks.none { it.selected }) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                subtitleTracks.forEach { tr ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (tr.selected) MaterialTheme.colorScheme.primaryContainer else surfaces.cardSurface,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onSelectSubtitleTrack(tr) },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(10.dp)) {
                            Text("${tr.label} ${if (tr.language.isNotBlank()) "(${tr.language})" else ""}", color = surfaces.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (tr.selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Bedtime, null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        if (sleepMinutesRemaining != null) "Sleep Timer ($sleepMinutesRemaining min remaining)" else "Sleep Timer",
                        style = MaterialTheme.typography.labelLarge,
                        color = surfaces.textPrimary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf(null to "Off", 15 to "15m", 30 to "30m", 60 to "60m", 90 to "90m").forEach { (mins, lbl) ->
                        FilterChip(
                            selected = (sleepMinutesRemaining == null && mins == null) || (sleepMinutesRemaining != null && mins == sleepMinutesRemaining),
                            onClick = { onSetSleepMinutes(mins); onDismiss() },
                            label = { Text(lbl) },
                        )
                    }
                }
            }
        }
    }
}
