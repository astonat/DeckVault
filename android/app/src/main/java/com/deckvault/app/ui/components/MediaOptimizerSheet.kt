package com.deckvault.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Queue
import androidx.compose.material.icons.rounded.Tablet
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.deckvault.app.model.CompressEstimateRequest
import com.deckvault.app.model.CompressEstimateResponse
import com.deckvault.app.model.CompressRequest
import com.deckvault.app.model.FileItem
import com.deckvault.app.model.JobExecutionTarget
import com.deckvault.app.model.SavedHost
import com.deckvault.app.model.UpscaleRequest
import com.deckvault.app.ui.theme.LocalDeckSurfaces
import com.deckvault.app.ui.util.Formatters
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

enum class OptimizerTab(val label: String) {
    COMPRESS("Compress Size"),
    UPSCALE("Upscale / Enhance"),
}

@Composable
fun MediaOptimizerDialog(
    file: FileItem,
    initialTab: OptimizerTab,
    isLocalSource: Boolean,
    pairedPcHosts: List<SavedHost>,
    defaultPcHost: SavedHost?,
    estimateFn: suspend (CompressEstimateRequest) -> CompressEstimateResponse?,
    onSubmitCompress: (req: CompressRequest, target: JobExecutionTarget, offloadHost: SavedHost?, runNow: Boolean) -> Unit,
    onSubmitUpscale: (req: UpscaleRequest, target: JobExecutionTarget, offloadHost: SavedHost?, runNow: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val surfaces = LocalDeckSurfaces.current
    var activeTab by remember { mutableStateOf(initialTab) }

    var selectedOffloadHost by remember { mutableStateOf(defaultPcHost ?: pairedPcHosts.firstOrNull()) }
    var executionTarget by remember {
        mutableStateOf(
            when {
                !isLocalSource -> JobExecutionTarget.PC_REMOTE
                else -> JobExecutionTarget.ON_DEVICE
            }
        )
    }

    // Compress state
    var targetHeight by remember { mutableIntStateOf(if (file.isVideo) 0 else 0) }
    var crf by remember { mutableIntStateOf(24) }
    var codec by remember { mutableStateOf("h264") }
    var audioKbps by remember { mutableIntStateOf(128) }
    var targetMbText by remember { mutableStateOf("") }
    var imageQuality by remember { mutableIntStateOf(82) }
    var saveMode by remember { mutableStateOf("copy") }
    var estimate by remember { mutableStateOf<CompressEstimateResponse?>(null) }

    // Upscale state
    var upscalePreset by remember { mutableStateOf("2x") }
    var upscaleMode by remember { mutableStateOf("cas") }
    var sharpen by remember { mutableFloatStateOf(0.9f) }
    var denoiseMode by remember { mutableStateOf("off") }
    var fps60 by remember { mutableStateOf(false) }
    var deband by remember { mutableStateOf(false) }
    var stabilize by remember { mutableStateOf(false) }

    // Auto-select PC Offload if user picks a PC-only enhancement on local tablet media
    val usesPcProFeature = (upscaleMode != "cas" || denoiseMode != "off" || fps60 || deband || stabilize)
    LaunchedEffect(activeTab, usesPcProFeature, isLocalSource) {
        if (isLocalSource && activeTab == OptimizerTab.UPSCALE && usesPcProFeature && selectedOffloadHost != null) {
            executionTarget = JobExecutionTarget.PC_OFFLOAD
        }
    }

    // Debounced live size estimator
    LaunchedEffect(activeTab, targetHeight, crf, codec, audioKbps, targetMbText, imageQuality) {
        if (activeTab == OptimizerTab.COMPRESS) {
            delay(220L)
            val parsedMb = targetMbText.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 0.0
            estimate = runCatching {
                estimateFn(
                    CompressEstimateRequest(
                        path = file.path,
                        targetHeight = targetHeight,
                        crf = crf,
                        codec = codec,
                        audioKbps = audioKbps,
                        targetMb = parsedMb,
                        quality = imageQuality,
                    )
                )
            }.getOrNull()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = surfaces.panelSurface,
            border = BorderStroke(1.dp, surfaces.hairlineBorder),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    Icon(
                        imageVector = if (activeTab == OptimizerTab.COMPRESS) Icons.Rounded.Compress else Icons.Rounded.AutoFixHigh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (activeTab == OptimizerTab.COMPRESS) "Media Compressor" else "AI / CAS Upscaler & Enhancer",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = surfaces.textPrimary,
                        )
                        Text(
                            text = "${file.name} • ${Formatters.formatBytes(file.sizeBytes)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = surfaces.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = surfaces.textSecondary)
                    }
                }

                HorizontalDivider(color = surfaces.hairlineBorder)

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    // Mode Tabs
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OptimizerTab.entries.forEach { tab ->
                            FilterChip(
                                selected = activeTab == tab,
                                onClick = { activeTab = tab },
                                label = { Text(tab.label) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (tab == OptimizerTab.COMPRESS) Icons.Rounded.Compress else Icons.Rounded.HighQuality,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                            )
                        }
                    }

                    // Execution Engine Card (Local vs PC Offload)
                    if (isLocalSource) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = surfaces.cardSurface,
                            border = BorderStroke(1.dp, surfaces.hairlineBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Processing Engine", style = MaterialTheme.typography.labelLarge, color = surfaces.textPrimary)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                ) {
                                    FilterChip(
                                        selected = executionTarget == JobExecutionTarget.ON_DEVICE,
                                        onClick = { executionTarget = JobExecutionTarget.ON_DEVICE },
                                        leadingIcon = { Icon(Icons.Rounded.Tablet, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        label = { Text("This Tablet (Offline)") },
                                    )
                                    pairedPcHosts.forEach { pc ->
                                        val selectedPc = executionTarget == JobExecutionTarget.PC_OFFLOAD && selectedOffloadHost?.hostId == pc.hostId
                                        FilterChip(
                                            selected = selectedPc,
                                            onClick = {
                                                selectedOffloadHost = pc
                                                executionTarget = JobExecutionTarget.PC_OFFLOAD
                                            },
                                            leadingIcon = { Icon(Icons.Rounded.Computer, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                            label = { Text("Offload to ${pc.displayName}") },
                                        )
                                    }
                                }
                                if (pairedPcHosts.isEmpty()) {
                                    Text(
                                        "Pair your Windows PC over Wi-Fi to offload heavy FFmpeg filters (Anime, Denoise, 60fps, Stabilize) & return the result automatically.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = surfaces.textSecondary,
                                    )
                                } else if (executionTarget == JobExecutionTarget.PC_OFFLOAD) {
                                    Text(
                                        "File uploads to ${selectedOffloadHost?.displayName}, processes via desktop FFmpeg queue, and downloads straight back to your tablet/SD card folder.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }

                    if (activeTab == OptimizerTab.COMPRESS) {
                        // Live Size Estimation Banner
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = surfaces.cardSurface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(14.dp),
                            ) {
                                Column {
                                    Text("Estimated Output Size", style = MaterialTheme.typography.labelMedium, color = surfaces.textSecondary)
                                    val estBytes = estimate?.estimatedBytes ?: 0L
                                    Text(
                                        text = if (estBytes > 0L) Formatters.formatBytes(estBytes) else "Calculating...",
                                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    val pct = estimate?.savingsPct ?: 0.0
                                    Text(
                                        text = if (pct > 0.5) "-${pct.roundToInt()}% smaller" else "High quality",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = surfaces.successMuted,
                                    )
                                    if ((estimate?.targetBitrateKbps ?: 0) > 0) {
                                        Text(
                                            "${estimate?.targetBitrateKbps} kbps target",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = surfaces.textSecondary,
                                        )
                                    }
                                }
                            }
                        }

                        if (file.isVideo) {
                            Text("Resolution Cap", style = MaterialTheme.typography.labelLarge, color = surfaces.textPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                listOf(0 to "Original", 1080 to "1080p", 720 to "720p", 480 to "480p").forEach { (h, lbl) ->
                                    FilterChip(selected = targetHeight == h, onClick = { targetHeight = h }, label = { Text(lbl) })
                                }
                            }

                            Text("Compression Strength (CRF: $crf)", style = MaterialTheme.typography.labelLarge, color = surfaces.textPrimary)
                            Slider(
                                value = crf.toFloat(),
                                onValueChange = { crf = it.roundToInt() },
                                valueRange = 18f..32f,
                                steps = 13,
                            )

                            Text("Video Codec", style = MaterialTheme.typography.labelLarge, color = surfaces.textPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = codec == "h264", onClick = { codec = "h264" }, label = { Text("H.264 (Fast & Universal)") })
                                FilterChip(selected = codec == "hevc", onClick = { codec = "hevc" }, label = { Text("HEVC H.265 (Smallest)") })
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = targetMbText,
                                    onValueChange = { targetMbText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                    label = { Text("Optional Exact Target MB (e.g. 25)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            Text("Audio Bitrate", style = MaterialTheme.typography.labelLarge, color = surfaces.textPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(0 to "Mute", 96 to "96k", 128 to "128k", 192 to "192k").forEach { (kbps, lbl) ->
                                    FilterChip(selected = audioKbps == kbps, onClick = { audioKbps = kbps }, label = { Text(lbl) })
                                }
                            }
                        } else {
                            Text("Max Image Height", style = MaterialTheme.typography.labelLarge, color = surfaces.textPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(0 to "Original", 2160 to "4K", 1440 to "1440p", 1080 to "1080p").forEach { (h, lbl) ->
                                    FilterChip(selected = targetHeight == h, onClick = { targetHeight = h }, label = { Text(lbl) })
                                }
                            }
                            Text("Quality ($imageQuality%)", style = MaterialTheme.typography.labelLarge, color = surfaces.textPrimary)
                            Slider(value = imageQuality.toFloat(), onValueChange = { imageQuality = it.roundToInt() }, valueRange = 50f..98f)
                        }
                    } else {
                        // UPSCALE TAB
                        Text("Target Resolution", style = MaterialTheme.typography.labelLarge, color = surfaces.textPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            listOf("2x" to "2x Native", "1080p" to "1080p FHD", "1440p" to "1440p QHD", "4k" to "4K UHD").forEach { (p, lbl) ->
                                FilterChip(selected = upscalePreset == p, onClick = { upscalePreset = p }, label = { Text(lbl) })
                            }
                        }

                        Text("Enhancement Profile", style = MaterialTheme.typography.labelLarge, color = surfaces.textPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            listOf(
                                "cas" to "Sharp CAS",
                                "anime" to "Anime Smooth (PC)",
                                "restore" to "Restore Old Video (PC)",
                            ).forEach { (m, lbl) ->
                                val enabled = !isLocalSource || m == "cas" || pairedPcHosts.isNotEmpty()
                                FilterChip(
                                    selected = upscaleMode == m,
                                    enabled = enabled,
                                    onClick = { upscaleMode = m },
                                    label = { Text(lbl) },
                                )
                            }
                        }

                        Text("Sharpen Clarity (${String.format("%.1f", sharpen)})", style = MaterialTheme.typography.labelLarge, color = surfaces.textPrimary)
                        Slider(value = sharpen, onValueChange = { sharpen = it }, valueRange = 0f..1.5f)

                        if (file.isVideo) {
                            Text("Denoise (PC FFmpeg Pro)", style = MaterialTheme.typography.labelLarge, color = surfaces.textPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("off" to "Off", "fast" to "Fast 3D", "hq" to "HQ NLMeans").forEach { (d, lbl) ->
                                    FilterChip(
                                        selected = denoiseMode == d,
                                        enabled = !isLocalSource || d == "off" || pairedPcHosts.isNotEmpty(),
                                        onClick = { denoiseMode = d },
                                        label = { Text(lbl) },
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("60 FPS Motion Interpolation (PC)", color = surfaces.textPrimary, style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = fps60,
                                    enabled = !isLocalSource || pairedPcHosts.isNotEmpty(),
                                    onCheckedChange = { fps60 = it },
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Remove Gradient Banding (PC)", color = surfaces.textPrimary, style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = deband,
                                    enabled = !isLocalSource || pairedPcHosts.isNotEmpty(),
                                    onCheckedChange = { deband = it },
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Deshake Video Stabilizer (PC)", color = surfaces.textPrimary, style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = stabilize,
                                    enabled = !isLocalSource || pairedPcHosts.isNotEmpty(),
                                    onCheckedChange = { stabilize = it },
                                )
                            }
                        }
                    }

                    // Output mode
                    Text("Save Output As", style = MaterialTheme.typography.labelLarge, color = surfaces.textPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = saveMode == "copy", onClick = { saveMode = "copy" }, label = { Text("New Copy (Safe)") })
                        FilterChip(selected = saveMode == "overwrite", onClick = { saveMode = "overwrite" }, label = { Text("Replace Original (Recycle Old)") })
                    }
                }

                HorizontalDivider(color = surfaces.hairlineBorder)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            if (activeTab == OptimizerTab.COMPRESS) {
                                val req = CompressRequest(
                                    path = file.path,
                                    targetHeight = targetHeight,
                                    crf = crf,
                                    codec = codec,
                                    audioKbps = audioKbps,
                                    targetMb = targetMbText.toDoubleOrNull() ?: 0.0,
                                    saveMode = saveMode,
                                    recycleOriginal = true,
                                    quality = imageQuality,
                                )
                                onSubmitCompress(req, executionTarget, selectedOffloadHost, false)
                            } else {
                                val req = UpscaleRequest(
                                    path = file.path,
                                    preset = upscalePreset,
                                    sharpen = sharpen,
                                    denoise = denoiseMode != "off",
                                    fps60 = fps60,
                                    crf = 19,
                                    saveMode = saveMode,
                                    recycleOriginal = true,
                                    mode = upscaleMode,
                                    denoiseMode = denoiseMode,
                                    deband = deband,
                                    stabilize = stabilize,
                                )
                                onSubmitUpscale(req, executionTarget, selectedOffloadHost, false)
                            }
                        },
                    ) {
                        Icon(Icons.Rounded.Queue, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add to Queue")
                    }

                    Button(
                        onClick = {
                            if (activeTab == OptimizerTab.COMPRESS) {
                                val req = CompressRequest(
                                    path = file.path,
                                    targetHeight = targetHeight,
                                    crf = crf,
                                    codec = codec,
                                    audioKbps = audioKbps,
                                    targetMb = targetMbText.toDoubleOrNull() ?: 0.0,
                                    saveMode = saveMode,
                                    recycleOriginal = true,
                                    quality = imageQuality,
                                )
                                onSubmitCompress(req, executionTarget, selectedOffloadHost, true)
                            } else {
                                val req = UpscaleRequest(
                                    path = file.path,
                                    preset = upscalePreset,
                                    sharpen = sharpen,
                                    denoise = denoiseMode != "off",
                                    fps60 = fps60,
                                    crf = 19,
                                    saveMode = saveMode,
                                    recycleOriginal = true,
                                    mode = upscaleMode,
                                    denoiseMode = denoiseMode,
                                    deband = deband,
                                    stabilize = stabilize,
                                )
                                onSubmitUpscale(req, executionTarget, selectedOffloadHost, true)
                            }
                        },
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Run Now")
                    }
                }
            }
        }
    }
}

@Composable
fun MergeVideosDialog(
    clips: List<FileItem>,
    isLocalSource: Boolean,
    pairedPcHosts: List<SavedHost>,
    defaultPcHost: SavedHost?,
    onSubmitMerge: (orderedClips: List<FileItem>, outputName: String, target: JobExecutionTarget, offloadHost: SavedHost?, runNow: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val surfaces = LocalDeckSurfaces.current
    var outputName by remember { mutableStateOf("Merged_${clips.firstOrNull()?.name?.substringBeforeLast('.') ?: "Video"}.mp4") }
    var executionTarget by remember {
        mutableStateOf(if (isLocalSource) JobExecutionTarget.ON_DEVICE else JobExecutionTarget.PC_REMOTE)
    }
    var selectedOffloadHost by remember { mutableStateOf(defaultPcHost ?: pairedPcHosts.firstOrNull()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = surfaces.panelSurface,
            border = BorderStroke(1.dp, surfaces.hairlineBorder),
            modifier = Modifier.fillMaxWidth(0.90f),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Merge ${clips.size} Videos",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = surfaces.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = surfaces.textSecondary)
                    }
                }
                HorizontalDivider(color = surfaces.hairlineBorder)

                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    clips.forEachIndexed { idx, clip ->
                        Text(
                            "${idx + 1}. ${clip.name} (${Formatters.formatBytes(clip.sizeBytes)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = surfaces.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                OutlinedTextField(
                    value = outputName,
                    onValueChange = { outputName = it },
                    label = { Text("Merged Output Filename") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (isLocalSource) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        FilterChip(
                            selected = executionTarget == JobExecutionTarget.ON_DEVICE,
                            onClick = { executionTarget = JobExecutionTarget.ON_DEVICE },
                            leadingIcon = { Icon(Icons.Rounded.Tablet, null, Modifier.size(16.dp)) },
                            label = { Text("This Tablet") },
                        )
                        pairedPcHosts.forEach { pc ->
                            FilterChip(
                                selected = executionTarget == JobExecutionTarget.PC_OFFLOAD && selectedOffloadHost?.hostId == pc.hostId,
                                onClick = { executionTarget = JobExecutionTarget.PC_OFFLOAD; selectedOffloadHost = pc },
                                leadingIcon = { Icon(Icons.Rounded.Computer, null, Modifier.size(16.dp)) },
                                label = { Text("Offload: ${pc.displayName}") },
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { onSubmitMerge(clips, outputName, executionTarget, selectedOffloadHost, false) }) {
                        Icon(Icons.Rounded.Queue, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add to Queue")
                    }
                    Button(onClick = { onSubmitMerge(clips, outputName, executionTarget, selectedOffloadHost, true) }) {
                        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Merge Now")
                    }
                }
            }
        }
    }
}
