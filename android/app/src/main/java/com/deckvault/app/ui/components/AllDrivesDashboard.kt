package com.deckvault.app.ui.components

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TabletAndroid
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deckvault.app.model.DriveInfo
import com.deckvault.app.model.HostDrivesSnapshot
import com.deckvault.app.peer.PeerServiceStatus
import com.deckvault.app.ui.theme.LocalDeckSurfaces
import com.deckvault.app.ui.util.Formatters

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AllDrivesDashboard(
    snapshots: List<HostDrivesSnapshot>,
    isRefreshing: Boolean,
    activeHostId: String?,
    peerStatus: PeerServiceStatus,
    onRefresh: () -> Unit,
    onBrowseDrive: (HostDrivesSnapshot, String) -> Unit,
    onOpenHostsModal: () -> Unit,
    onRotatePin: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val surfaces = LocalDeckSurfaces.current
    val hasAllFilesAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        true
    }

    val allDrives = snapshots.flatMap { it.drives }
    val totalCapacity = allDrives.sumOf { it.totalBytes.coerceAtLeast(0L) }
    val totalFree = allDrives.sumOf { it.freeBytes.coerceAtLeast(0L) }
    val totalUsed = (totalCapacity - totalFree).coerceAtLeast(0L)
    val onlineCount = snapshots.count { it.online }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaces.background),
    ) {
        val compact = maxWidth < 500.dp
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Surface(
                color = surfaces.panelSurface,
                border = BorderStroke(1.dp, surfaces.hairlineBorder),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = if (compact) 12.dp else 18.dp, vertical = 12.dp),
                ) {
                    Icon(Icons.Rounded.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "All Devices & Drives",
                            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                            color = surfaces.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "$onlineCount/${snapshots.size} online • ${allDrives.size} drives • ${Formatters.formatBytes(totalFree)} free",
                            style = MaterialTheme.typography.bodySmall,
                            color = surfaces.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh all devices")
                        }
                    }
                    OutlinedButton(
                        onClick = onOpenHostsModal,
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = if (compact) 10.dp else 14.dp, vertical = 6.dp),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        if (!compact) {
                            Spacer(Modifier.width(4.dp))
                            Text("Pair")
                        }
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                // Aggregate Fleet Storage Card
                if (totalCapacity > 0L) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = surfaces.cardSurface,
                            border = BorderStroke(1.dp, surfaces.hairlineBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(18.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Combined Connected Storage", style = MaterialTheme.typography.titleSmall, color = surfaces.textPrimary)
                                    Text(
                                        "${Formatters.formatBytes(totalFree)} Available",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                val usedFraction = (totalUsed.toFloat() / totalCapacity.toFloat()).coerceIn(0f, 1f)
                                LinearProgressIndicator(
                                    progress = { usedFraction },
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                                )
                                Text(
                                    "${Formatters.formatBytes(totalUsed)} used  •  ${Formatters.formatBytes(totalCapacity)} total across ${allDrives.size} volume(s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = surfaces.textSecondary,
                                )
                            }
                        }
                    }
                }

                // Android 11+ All Files Access prompt if not yet granted
                if (!hasAllFilesAccess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(16.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Enable Full File Sharing on This Device", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "Grant All Files Access so your phone, tablet, and PC can view all folders on this device.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = surfaces.textSecondary,
                                    )
                                }
                                Button(
                                    onClick = {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                            Uri.parse("package:${context.packageName}"),
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    },
                                    shape = RoundedCornerShape(50),
                                ) {
                                    Text("Allow")
                                }
                            }
                        }
                    }
                }

                // This Device Live Share Banner (includes iPhone Safari URL)
                if (peerStatus.running) {
                    item {
                        val primaryIp = peerStatus.ips.firstOrNull() ?: "127.0.0.1"
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = surfaces.panelSurface,
                            border = BorderStroke(1.dp, surfaces.hairlineBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        "This Device Peer Server: http://$primaryIp:${peerStatus.port}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f),
                                    )
                                    OutlinedButton(
                                        onClick = onRotatePin,
                                        shape = RoundedCornerShape(50),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    ) {
                                        Text("PIN: ${peerStatus.pin} (New)")
                                    }
                                }
                                Text(
                                    "Other Android/PC devices can pair using IP $primaryIp + PIN ${peerStatus.pin}. " +
                                        "iPhone Safari can open http://$primaryIp:${peerStatus.port}/ directly.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = surfaces.textSecondary,
                                )
                            }
                        }
                    }
                }

                // Device Cards
                items(snapshots, key = { (it.host?.hostId ?: "local") + "_" + it.deviceName }) { snap ->
                    DeviceDrivesCard(
                        snapshot = snap,
                        isCurrentActive = snap.host?.hostId != null && snap.host.hostId == activeHostId,
                        onBrowsePath = { path -> onBrowseDrive(snap, path) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceDrivesCard(
    snapshot: HostDrivesSnapshot,
    isCurrentActive: Boolean,
    onBrowsePath: (String) -> Unit,
) {
    val surfaces = LocalDeckSurfaces.current
    val osLower = snapshot.os.lowercase()
    val deviceIcon = when {
        osLower.contains("android") && snapshot.deviceName.contains("tab", ignoreCase = true) -> Icons.Rounded.TabletAndroid
        osLower.contains("android") -> Icons.Rounded.Smartphone
        osLower.contains("ios") || osLower.contains("iphone") -> Icons.Rounded.PhoneIphone
        else -> Icons.Rounded.Computer
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = surfaces.cardSurface,
        border = BorderStroke(
            width = if (isCurrentActive) 1.5.dp else 1.dp,
            color = if (isCurrentActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else surfaces.hairlineBorder,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (snapshot.online) MaterialTheme.colorScheme.primaryContainer else surfaces.elevatedSurface,
                    modifier = Modifier.size(42.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            deviceIcon,
                            contentDescription = null,
                            tint = if (snapshot.online) MaterialTheme.colorScheme.onPrimaryContainer else surfaces.textSecondary,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            snapshot.deviceName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (snapshot.isLocalDevice) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                            ) {
                                Text(
                                    "This Device",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    Text(
                        "${snapshot.os}${snapshot.host?.let { "  •  ${it.ip}:${it.port}" } ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = surfaces.textSecondary,
                    )
                }

                Icon(
                    imageVector = if (snapshot.online) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = if (snapshot.online) surfaces.successMuted else surfaces.dangerMuted,
                    modifier = Modifier.size(20.dp),
                )
            }

            if (!snapshot.online) {
                Text(
                    snapshot.error ?: "Device offline or unreachable on current Wi-Fi/LAN",
                    style = MaterialTheme.typography.bodySmall,
                    color = surfaces.dangerMuted,
                )
            } else {
                HorizontalDivider(color = surfaces.hairlineBorder)
                snapshot.drives.forEach { drive ->
                    DriveBarRow(
                        drive = drive,
                        onClick = { onBrowsePath(drive.path) },
                    )
                }

                if (snapshot.quickFolders.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        snapshot.quickFolders.take(6).forEach { folder ->
                            AssistChip(
                                onClick = { onBrowsePath(folder.path) },
                                label = { Text(folder.name) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DriveBarRow(
    drive: DriveInfo,
    onClick: () -> Unit,
) {
    val surfaces = LocalDeckSurfaces.current
    val total = drive.totalBytes.coerceAtLeast(1L)
    val used = (drive.totalBytes - drive.freeBytes).coerceAtLeast(0L)
    val usedPct = (used.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val barColor = when {
        usedPct > 0.90f -> surfaces.dangerMuted
        usedPct > 0.78f -> surfaces.warningMuted
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = surfaces.panelSurface,
        border = BorderStroke(1.dp, surfaces.hairlineBorder),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "${drive.name}  (${drive.path})",
                    style = MaterialTheme.typography.titleSmall,
                    color = surfaces.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${Formatters.formatBytes(drive.freeBytes)} free  /  ${Formatters.formatBytes(drive.totalBytes)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            LinearProgressIndicator(
                progress = { usedPct },
                color = barColor,
                trackColor = surfaces.elevatedSurface,
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
            )
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Used: ${Formatters.formatBytes(used)} (${(usedPct * 100).toInt()}%)",
                    style = MaterialTheme.typography.labelSmall,
                    color = surfaces.textSecondary,
                )
                Text("Tap to open", style = MaterialTheme.typography.labelSmall, color = surfaces.textSecondary)
            }
        }
    }
}
