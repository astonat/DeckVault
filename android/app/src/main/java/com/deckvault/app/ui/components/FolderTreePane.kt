package com.deckvault.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.TabletAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deckvault.app.model.DriveInfo
import com.deckvault.app.model.FolderItem
import com.deckvault.app.model.QuickFolder
import com.deckvault.app.model.SavedHost
import com.deckvault.app.ui.theme.OledColors
import com.deckvault.app.ui.util.Formatters

@Composable
fun FolderTreePane(
    activeHost: SavedHost?,
    savedHosts: List<SavedHost>,
    drives: List<DriveInfo>,
    quickFolders: List<QuickFolder>,
    currentPath: String,
    parentPath: String,
    currentFolderSizeBytes: Long,
    isIndexing: Boolean,
    treeFolders: List<FolderItem>,
    treeSortBySize: Boolean,
    spaceSavedBytes: Long,
    showDriveGauges: Boolean,
    showQuickFolders: Boolean,
    showSubfolderCounts: Boolean,
    showCollapseButton: Boolean,
    onCollapsePane: () -> Unit,
    onSelectHost: (SavedHost) -> Unit,
    onOpenHostManager: () -> Unit,
    onOpenAllDrives: () -> Unit,
    onOpenSettings: () -> Unit,
    onNavigateFolder: (String) -> Unit,
    onToggleTreeSort: () -> Unit,
    onRescanDisks: () -> Unit,
    onScanLargestInFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = OledColors.PanelSurface,
        border = BorderStroke(1.dp, OledColors.HairlineBorder),
        shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
    ) {
        Column(modifier = Modifier.fillMaxHeight().padding(horizontal = 12.dp, vertical = 10.dp)) {
            // 1. Clean Single-Row Host Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    onClick = onOpenHostManager,
                    shape = RoundedCornerShape(14.dp),
                    color = OledColors.CardSurface,
                    border = BorderStroke(1.dp, OledColors.HairlineBorder),
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            imageVector = when {
                                activeHost?.isTabletDevice == true -> Icons.Rounded.TabletAndroid
                                activeHost?.isAndroidDevice == true -> Icons.Rounded.PhoneAndroid
                                activeHost?.displayName?.contains("laptop", ignoreCase = true) == true -> Icons.Rounded.Laptop
                                else -> Icons.Rounded.Computer
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeHost?.displayName ?: "Connect PC / Laptop",
                                style = MaterialTheme.typography.titleSmall,
                                color = OledColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (activeHost != null) {
                                Text(
                                    text = if (spaceSavedBytes > 0L) {
                                        "${activeHost.ip} • Saved ${Formatters.formatBytes(spaceSavedBytes)}"
                                    } else {
                                        activeHost.ip
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OledColors.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Icon(
                            Icons.Rounded.SwapHoriz,
                            contentDescription = "Switch Host",
                            tint = OledColors.TextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onOpenAllDrives, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Rounded.Storage,
                        contentDescription = "All Devices & Drives",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = onOpenSettings, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = OledColors.TextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                if (showCollapseButton) {
                    IconButton(onClick = onCollapsePane, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Rounded.ChevronLeft,
                            contentDescription = "Hide Folder Panel",
                            tint = OledColors.TextSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // Optional 1-tap PC / Laptop segmented chips if multiple hosts paired
            if (savedHosts.size > 1) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    savedHosts.forEach { host ->
                        val active = host.hostId == activeHost?.hostId
                        Surface(
                            onClick = { onSelectHost(host) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (active) MaterialTheme.colorScheme.primaryContainer else OledColors.CardSurface,
                            border = BorderStroke(
                                1.dp,
                                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else OledColors.HairlineBorder,
                            ),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (active) OledColors.SuccessGreen else OledColors.TextSecondary.copy(alpha = 0.4f)),
                                )
                                Text(
                                    text = host.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else OledColors.TextPrimary,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }

            // 2. Compact Drive & Quick-Jump Strip
            if ((showDriveGauges && drives.isNotEmpty()) || (showQuickFolders && quickFolders.isNotEmpty())) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (showDriveGauges) {
                        drives.forEach { drive ->
                            val isSelected = currentPath.startsWith(drive.path, ignoreCase = true)
                            val usedFrac = if (drive.totalBytes > 0) (drive.usedBytes.toFloat() / drive.totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
                            Surface(
                                onClick = { onNavigateFolder(drive.path) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else OledColors.CardSurface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else OledColors.HairlineBorder,
                                ),
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(
                                            Icons.Rounded.Storage,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else OledColors.TextSecondary,
                                            modifier = Modifier.size(13.dp),
                                        )
                                        Text(
                                            text = drive.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = OledColors.TextPrimary,
                                            maxLines = 1,
                                        )
                                        Text(
                                            text = "${Formatters.formatBytes(drive.freeBytes)} free",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = OledColors.TextSecondary,
                                        )
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    LinearProgressIndicator(
                                        progress = { usedFrac },
                                        modifier = Modifier.width(106.dp).height(3.dp).clip(CircleShape),
                                        color = if (usedFrac > 0.90f) OledColors.DangerRed else MaterialTheme.colorScheme.primary,
                                        trackColor = OledColors.ElevatedSurface,
                                    )
                                }
                            }
                        }
                    }

                    if (showQuickFolders) {
                        quickFolders.forEach { qf ->
                            val active = currentPath.equals(qf.path, ignoreCase = true)
                            Surface(
                                onClick = { onNavigateFolder(qf.path) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (active) MaterialTheme.colorScheme.secondaryContainer else OledColors.CardSurface,
                                border = BorderStroke(
                                    1.dp,
                                    if (active) MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f) else OledColors.HairlineBorder,
                                ),
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                    Text(
                                        text = qf.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else OledColors.TextPrimary,
                                        maxLines = 1,
                                    )
                                    if (qf.sizeBytes >= 0) {
                                        Text(
                                            text = Formatters.formatBytes(qf.sizeBytes),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = OledColors.TextSecondary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // 3. Clean Current Folder Bar + Actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (parentPath.isNotBlank()) {
                    Surface(
                        onClick = { onNavigateFolder(parentPath) },
                        shape = CircleShape,
                        color = OledColors.ElevatedSurface,
                        modifier = Modifier.size(30.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Up",
                                tint = OledColors.TextPrimary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Icon(
                    Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentPath.ifBlank { "Select a folder" },
                        style = MaterialTheme.typography.titleSmall,
                        color = OledColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (currentPath.isNotBlank()) {
                        val sizeTxt = Formatters.formatBytes(currentFolderSizeBytes)
                        Text(
                            text = if (isIndexing) "$sizeTxt • Indexing" else "$sizeTxt total",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                        )
                    }
                }
                IconButton(onClick = onRescanDisks, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = "Rescan",
                        tint = OledColors.TextSecondary,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }

            if (currentPath.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Surface(
                        onClick = onToggleTreeSort,
                        shape = RoundedCornerShape(10.dp),
                        color = OledColors.CardSurface,
                        border = BorderStroke(1.dp, OledColors.HairlineBorder),
                    ) {
                        Text(
                            text = if (treeSortBySize) "Size ↓" else "A–Z",
                            style = MaterialTheme.typography.labelSmall,
                            color = OledColors.TextPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                    Surface(
                        onClick = onScanLargestInFolder,
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = "Space Hogs",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = OledColors.HairlineBorder)
            Spacer(Modifier.height(6.dp))

            // 4. Airy, Matte Subfolder List
            if (treeFolders.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (activeHost == null) "Pair a PC or laptop to browse"
                        else "No subfolders",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OledColors.TextSecondary,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(treeFolders, key = { it.path }) { folder ->
                        FolderTreeRow(
                            folder = folder,
                            showCounts = showSubfolderCounts,
                            onClick = { onNavigateFolder(folder.path) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderTreeRow(
    folder: FolderItem,
    showCounts: Boolean,
    onClick: () -> Unit,
) {
    val pct = (folder.percentOfParent / 100.0).toFloat().coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(targetValue = pct, label = "folderBar")
    val barColor = when {
        pct >= 0.45f -> MaterialTheme.colorScheme.tertiary
        pct >= 0.15f -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = OledColors.CardSurface,
        border = BorderStroke(1.dp, OledColors.HairlineBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = barColor,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = OledColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = Formatters.formatBytes(folder.sizeBytes),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = OledColors.TextPrimary,
                    maxLines = 1,
                )
                if (folder.hasSubfolders) {
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = OledColors.TextSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { if (folder.sizeBytes >= 0) animatedProgress.coerceAtLeast(0.02f) else 0f },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                color = barColor,
                trackColor = OledColors.ElevatedSurface,
            )

            if (showCounts && (folder.mediaCount >= 0 || folder.sizeBytes >= 0)) {
                Spacer(Modifier.height(5.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val countLabel = buildString {
                        if (folder.mediaCount >= 0) append("${folder.mediaCount} media")
                        if (folder.fileCount >= 0) {
                            if (isNotEmpty()) append(" • ")
                            append("${folder.fileCount} files")
                        }
                    }
                    Text(
                        text = countLabel.ifEmpty { "Calculating" },
                        style = MaterialTheme.typography.labelSmall,
                        color = OledColors.TextSecondary,
                        maxLines = 1,
                    )
                    if (folder.sizeBytes >= 0) {
                        Text(
                            text = "${folder.percentOfParent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = OledColors.TextSecondary,
                        )
                    }
                }
            }
        }
    }
}
