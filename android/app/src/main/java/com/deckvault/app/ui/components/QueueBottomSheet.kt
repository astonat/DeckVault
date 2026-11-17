package com.deckvault.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Queue
import androidx.compose.material.icons.rounded.Tablet
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.deckvault.app.model.JobExecutionTarget
import com.deckvault.app.model.RenderQueueItem
import com.deckvault.app.ui.theme.LocalDeckSurfaces
import com.deckvault.app.ui.util.Formatters
import kotlin.math.roundToInt

@Composable
fun FloatingQueueMiniBar(
    jobs: List<RenderQueueItem>,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (jobs.isEmpty()) return
    val surfaces = LocalDeckSurfaces.current
    val active = jobs.filter { it.status in listOf("running", "uploading", "downloading", "queued") }
    val doneCount = jobs.count { it.status == "done" }
    val current = active.firstOrNull { it.status != "queued" } ?: active.firstOrNull() ?: jobs.first()
    val progress = (current.progressPct.toFloat() / 100f).coerceIn(0f, 1f)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = surfaces.elevatedSurface.copy(alpha = 0.96f),
        border = BorderStroke(
            1.dp,
            if (active.isNotEmpty()) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f) else surfaces.hairlineBorder,
        ),
        shadowElevation = 6.dp,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onOpenQueue() },
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Queue,
                    contentDescription = "Render Queue",
                    tint = if (active.isNotEmpty()) MaterialTheme.colorScheme.primary else surfaces.successMuted,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = if (active.isNotEmpty()) {
                        "${active.size} active • ${current.title}"
                    } else {
                        "Queue ($doneCount done) • Tap to view or open outputs"
                    },
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = surfaces.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (active.isNotEmpty()) current.message else "View",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (active.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                )
            }
        }
    }
}

@Composable
fun QueueManagerDialog(
    jobs: List<RenderQueueItem>,
    onCancelJob: (RenderQueueItem) -> Unit,
    onClearFinished: () -> Unit,
    onPlayOutput: (RenderQueueItem) -> Unit,
    onOpenInStudio: (RenderQueueItem) -> Unit,
    onGoToFolder: (RenderQueueItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val surfaces = LocalDeckSurfaces.current
    val activeCount = jobs.count { it.status in listOf("queued", "uploading", "running", "downloading") }
    val doneCount = jobs.count { it.status == "done" }

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
                .heightIn(max = 680.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    Icon(Icons.Rounded.Queue, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Render & Offload Queue",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = surfaces.textPrimary,
                        )
                        Text(
                            "$activeCount active • $doneCount completed",
                            style = MaterialTheme.typography.labelMedium,
                            color = surfaces.textSecondary,
                        )
                    }
                    if (jobs.any { it.status in listOf("done", "error", "cancelled") }) {
                        TextButton(onClick = onClearFinished) {
                            Icon(Icons.Rounded.ClearAll, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Clear Done")
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = surfaces.textSecondary)
                    }
                }

                HorizontalDivider(color = surfaces.hairlineBorder)

                if (jobs.isEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    ) {
                        Icon(Icons.Rounded.Queue, contentDescription = null, tint = surfaces.textSecondary, modifier = Modifier.size(44.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("Queue is empty", style = MaterialTheme.typography.titleSmall, color = surfaces.textPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Add Studio edits, Compressors, Upscalers, Audio extractions, or Merges here — on-device or offloaded to your paired PC.",
                            style = MaterialTheme.typography.bodySmall,
                            color = surfaces.textSecondary,
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                    ) {
                        items(jobs, key = { it.id }) { item ->
                            QueueJobCard(
                                item = item,
                                onCancel = { onCancelJob(item) },
                                onPlay = { onPlayOutput(item) },
                                onStudio = { onOpenInStudio(item) },
                                onGoFolder = { onGoToFolder(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueJobCard(
    item: RenderQueueItem,
    onCancel: () -> Unit,
    onPlay: () -> Unit,
    onStudio: () -> Unit,
    onGoFolder: () -> Unit,
) {
    val surfaces = LocalDeckSurfaces.current
    val isActive = item.status in listOf("queued", "uploading", "running", "downloading")

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = surfaces.cardSurface,
        border = BorderStroke(
            1.dp,
            when (item.status) {
                "done" -> surfaces.successMuted.copy(alpha = 0.55f)
                "error" -> surfaces.dangerMuted.copy(alpha = 0.6f)
                else -> surfaces.hairlineBorder
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = when (item.target) {
                        JobExecutionTarget.ON_DEVICE -> Icons.Rounded.Tablet
                        JobExecutionTarget.PC_OFFLOAD, JobExecutionTarget.PC_REMOTE -> Icons.Rounded.Computer
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = surfaces.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val metaLine = buildString {
                        append(item.target.label)
                        append(" • ")
                        append(item.message)
                        if (item.newSizeBytes > 0L) {
                            append(" • ${Formatters.formatBytes(item.newSizeBytes)}")
                        }
                        if (item.savedBytes > 0L) {
                            append(" (Saved ${Formatters.formatBytes(item.savedBytes)})")
                        }
                    }
                    Text(
                        metaLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.status == "error") surfaces.dangerMuted else surfaces.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                when (item.status) {
                    "done" -> Icon(Icons.Rounded.CheckCircle, contentDescription = "Done", tint = surfaces.successMuted)
                    "error" -> Icon(Icons.Rounded.ErrorOutline, contentDescription = "Error", tint = surfaces.dangerMuted)
                    else -> if (isActive) {
                        Text(
                            "${item.progressPct.roundToInt()}%",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            if (isActive) {
                LinearProgressIndicator(
                    progress = { (item.progressPct.toFloat() / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                )
            }

            if (item.error.isNotBlank()) {
                Text(item.error, style = MaterialTheme.typography.labelSmall, color = surfaces.dangerMuted)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isActive) {
                    OutlinedButton(onClick = onCancel) {
                        Icon(Icons.Rounded.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Cancel")
                    }
                }
                if (item.status == "done" && item.outputPath.isNotBlank()) {
                    AssistChip(
                        onClick = onPlay,
                        leadingIcon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Play") },
                    )
                    if (item.outputMediaType == "video" || item.outputName.endsWith(".mp4", true)) {
                        AssistChip(
                            onClick = onStudio,
                            leadingIcon = { Icon(Icons.Rounded.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("Edit Studio") },
                        )
                    }
                    AssistChip(
                        onClick = onGoFolder,
                        leadingIcon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Folder") },
                    )
                }
            }
        }
    }
}
