package com.deckvault.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.deckvault.app.model.AccentPalette
import com.deckvault.app.model.CardAspect
import com.deckvault.app.model.FoldLayoutMode
import com.deckvault.app.model.SavedHost
import com.deckvault.app.model.ThemeSurfaceStyle
import com.deckvault.app.model.UserPreferences
import com.deckvault.app.model.ViewMode
import com.deckvault.app.peer.PeerServiceStatus
import com.deckvault.app.ui.theme.LocalDeckSurfaces
import com.deckvault.app.ui.util.Formatters
import kotlin.math.roundToInt

@Composable
fun SettingsDialog(
    prefs: UserPreferences,
    activeHost: SavedHost?,
    peerStatus: PeerServiceStatus,
    localCacheBytes: Long,
    onUpdatePrefs: ((UserPreferences) -> UserPreferences) -> Unit,
    onRotatePin: () -> Unit,
    onOpenHostManager: () -> Unit,
    onRescanDisks: () -> Unit,
    onClearCache: () -> Unit,
    onDismiss: () -> Unit,
) {
    val surfaces = LocalDeckSurfaces.current
    val isTablet = LocalConfiguration.current.smallestScreenWidthDp >= 600

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(24.dp),
            color = surfaces.panelSurface,
            border = BorderStroke(1.dp, surfaces.hairlineBorder),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    Column {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.headlineSmall,
                            color = surfaces.textPrimary,
                        )
                        Text(
                            text = activeHost?.let { "Connected: ${it.displayName} (${it.ip})" } ?: "No host paired",
                            style = MaterialTheme.typography.bodySmall,
                            color = surfaces.textSecondary,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close Settings", tint = surfaces.textSecondary)
                    }
                }

                HorizontalDivider(color = surfaces.hairlineBorder)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // 1. Appearance & Full-Color Material 3 Themes
                    SettingsSection(title = "Appearance & Material Theme") {
                        Text(
                            text = "Color Palette",
                            style = MaterialTheme.typography.labelLarge,
                            color = surfaces.textSecondary,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            AccentPalette.entries.forEach { palette ->
                                PaletteSwatchCard(
                                    palette = palette,
                                    selected = prefs.accentPalette == palette,
                                    onClick = { onUpdatePrefs { it.copy(accentPalette = palette) } },
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Dark Surface Style",
                            style = MaterialTheme.typography.labelLarge,
                            color = surfaces.textSecondary,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            ThemeSurfaceStyle.entries.forEach { style ->
                                FilterChip(
                                    selected = prefs.surfaceStyle == style,
                                    onClick = {
                                        onUpdatePrefs {
                                            it.copy(
                                                surfaceStyle = style,
                                                pureBlackBackground = style != ThemeSurfaceStyle.TONAL_DARK,
                                            )
                                        }
                                    },
                                    label = { Text(style.label, style = MaterialTheme.typography.labelMedium) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = matteChipColors(),
                                )
                            }
                        }
                        Text(
                            text = prefs.surfaceStyle.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = surfaces.textSecondary,
                        )
                    }

                    // 2. Tablet & Z Fold Layout
                    SettingsSection(title = "Z Fold & Tablet Layout") {
                        Text(
                            text = "Split-Pane Behavior",
                            style = MaterialTheme.typography.labelLarge,
                            color = surfaces.textSecondary,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            FoldLayoutMode.entries.forEach { mode ->
                                val title = when (mode) {
                                    FoldLayoutMode.AUTO -> "Adaptive"
                                    FoldLayoutMode.SPLIT -> "Always Split"
                                    FoldLayoutMode.DRAWER -> "Slide Drawer"
                                }
                                FilterChip(
                                    selected = prefs.foldLayoutMode == mode,
                                    onClick = { onUpdatePrefs { it.copy(foldLayoutMode = mode) } },
                                    label = { Text(title, style = MaterialTheme.typography.labelMedium) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = matteChipColors(),
                                )
                            }
                        }

                        val widthPct = (prefs.folderPaneWidthFraction * 100f).roundToInt()
                        Column {
                            Text(
                                text = "Left Folder Panel Width: $widthPct%",
                                style = MaterialTheme.typography.labelLarge,
                                color = surfaces.textPrimary,
                            )
                            Slider(
                                value = prefs.folderPaneWidthFraction.coerceIn(0.26f, 0.46f),
                                onValueChange = { v ->
                                    onUpdatePrefs { it.copy(folderPaneWidthFraction = v.coerceIn(0.26f, 0.46f)) }
                                },
                                valueRange = 0.26f..0.46f,
                            )
                        }

                        SettingsToggleRow(
                            title = "Drive Space Strips in Folder Panel",
                            subtitle = "Show C:\\, D:\\ usage pills at top of left panel",
                            checked = prefs.showDriveGaugesInPane,
                            onCheckedChange = { v -> onUpdatePrefs { it.copy(showDriveGaugesInPane = v) } },
                        )
                        SettingsToggleRow(
                            title = "Quick Folders Strip",
                            subtitle = "Show Videos, Pictures, Downloads, Desktop pills",
                            checked = prefs.showQuickFoldersInPane,
                            onCheckedChange = { v -> onUpdatePrefs { it.copy(showQuickFoldersInPane = v) } },
                        )
                        SettingsToggleRow(
                            title = "Show Subfolder Item Counts",
                            subtitle = "Keep rows minimal by hiding item counts when off",
                            checked = prefs.showSubfolderCounts,
                            onCheckedChange = { v -> onUpdatePrefs { it.copy(showSubfolderCounts = v) } },
                        )
                    }

                    // 3. Gallery & List Display
                    SettingsSection(title = "Gallery & List") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Default View", style = MaterialTheme.typography.bodyMedium, color = surfaces.textPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ViewMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = prefs.viewMode == mode,
                                        onClick = { onUpdatePrefs { it.copy(viewMode = mode) } },
                                        label = { Text(mode.label, style = MaterialTheme.typography.labelMedium) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = matteChipColors(),
                                    )
                                }
                            }
                        }

                        Column {
                            val colLabel = if (prefs.gridColumns <= 0) "Auto (Adaptive)" else "${prefs.gridColumns} Columns"
                            Text(
                                text = "Gallery Columns: $colLabel",
                                style = MaterialTheme.typography.labelLarge,
                                color = surfaces.textPrimary,
                            )
                            Slider(
                                value = prefs.gridColumns.toFloat(),
                                onValueChange = { v -> onUpdatePrefs { it.copy(gridColumns = v.roundToInt()) } },
                                valueRange = 0f..8f,
                                steps = 7,
                            )
                        }

                        Text("Thumbnail Aspect Ratio", style = MaterialTheme.typography.labelLarge, color = surfaces.textSecondary)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            CardAspect.entries.forEach { aspect ->
                                FilterChip(
                                    selected = prefs.cardAspect == aspect,
                                    onClick = { onUpdatePrefs { it.copy(cardAspect = aspect) } },
                                    label = { Text(aspect.label, style = MaterialTheme.typography.labelMedium) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = matteChipColors(),
                                )
                            }
                        }

                        SettingsToggleRow(
                            title = "Compact List Rows",
                            subtitle = "Tighter vertical density for large folders",
                            checked = prefs.compactListMode,
                            onCheckedChange = { v -> onUpdatePrefs { it.copy(compactListMode = v) } },
                        )
                        SettingsToggleRow(
                            title = "Video Scrub Previews",
                            subtitle = "Drag or hover S Pen over cards to scrub keyframes",
                            checked = prefs.enableHoverScrub,
                            onCheckedChange = { v -> onUpdatePrefs { it.copy(enableHoverScrub = v) } },
                        )
                        SettingsToggleRow(
                            title = "Show File Size Badge",
                            checked = prefs.showFileSize,
                            onCheckedChange = { v -> onUpdatePrefs { it.copy(showFileSize = v) } },
                        )
                        SettingsToggleRow(
                            title = "Show Duration & Resolution",
                            checked = prefs.showDuration,
                            onCheckedChange = { v -> onUpdatePrefs { it.copy(showDuration = v, showResolutionBadge = v) } },
                        )
                        SettingsToggleRow(
                            title = "Show Filenames & Modified Date",
                            checked = prefs.showFileName,
                            onCheckedChange = { v -> onUpdatePrefs { it.copy(showFileName = v, showModifiedDate = v) } },
                        )
                    }

                    // 4. Player & Video Cutter Defaults
                    SettingsSection(title = "Video Player & Space-Saver Cutter") {
                        SettingsToggleRow(
                            title = "Auto-Play Videos on Open",
                            checked = prefs.autoPlayVideos,
                            onCheckedChange = { v -> onUpdatePrefs { it.copy(autoPlayVideos = v) } },
                        )

                        Text("Default Playback Speed", style = MaterialTheme.typography.labelLarge, color = surfaces.textSecondary)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                                val label = if (spd == 1.0f) "1x" else "${spd}x"
                                FilterChip(
                                    selected = kotlin.math.abs(prefs.defaultPlaybackSpeed - spd) < 0.05f,
                                    onClick = { onUpdatePrefs { it.copy(defaultPlaybackSpeed = spd) } },
                                    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = matteChipColors(),
                                )
                            }
                        }

                        Text("Skip / Seek Step", style = MaterialTheme.typography.labelLarge, color = surfaces.textSecondary)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            listOf(5, 10, 15, 30).forEach { sec ->
                                FilterChip(
                                    selected = prefs.seekStepSeconds == sec,
                                    onClick = { onUpdatePrefs { it.copy(seekStepSeconds = sec) } },
                                    label = { Text("${sec}s", style = MaterialTheme.typography.labelMedium) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = matteChipColors(),
                                )
                            }
                        }

                        Text("Default Cut Mode", style = MaterialTheme.typography.labelLarge, color = surfaces.textSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("lossless" to "Lossless Copy", "compress" to "Cut + Compress").forEach { (key, title) ->
                                FilterChip(
                                    selected = prefs.defaultTrimMode == key,
                                    onClick = { onUpdatePrefs { it.copy(defaultTrimMode = key) } },
                                    label = { Text(title, style = MaterialTheme.typography.labelMedium) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = matteChipColors(),
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "Default Compression Quality (CRF ${prefs.defaultCompressCrf})",
                                style = MaterialTheme.typography.labelLarge,
                                color = surfaces.textPrimary,
                            )
                            Text(
                                text = "Lower = sharper / larger, Higher = smaller file size",
                                style = MaterialTheme.typography.bodySmall,
                                color = surfaces.textSecondary,
                            )
                            Slider(
                                value = prefs.defaultCompressCrf.coerceIn(18, 32).toFloat(),
                                onValueChange = { v -> onUpdatePrefs { it.copy(defaultCompressCrf = v.roundToInt()) } },
                                valueRange = 18f..32f,
                                steps = 13,
                            )
                        }

                        SettingsToggleRow(
                            title = "Send Replaced / Deleted Files to Recycle Bin",
                            subtitle = "Recommended so trimmed originals can be recovered on Windows",
                            checked = prefs.recycleByDefault,
                            onCheckedChange = { v -> onUpdatePrefs { it.copy(recycleByDefault = v) } },
                        )
                    }

                    // 5. Storage & Devices
                    SettingsSection(title = "Devices, Peer Sharing & Cache") {
                        SettingsToggleRow(
                            title = "Share This Device on Local Wi-Fi",
                            subtitle = if (peerStatus.running) {
                                val ip = peerStatus.ips.firstOrNull() ?: "LAN"
                                "Running on http://$ip:${peerStatus.port}/  •  PIN ${peerStatus.pin}"
                            } else {
                                "Allow your Fold, Tab S8 Ultra, PC, Laptop, or iPhone browser to access this device"
                            },
                            checked = prefs.deviceShareEnabled,
                            onCheckedChange = { v -> onUpdatePrefs { it.copy(deviceShareEnabled = v) } },
                        )
                        if (peerStatus.running) {
                            OutlinedButton(
                                onClick = onRotatePin,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, surfaces.hairlineBorder),
                            ) {
                                Text("Generate New Pairing PIN (Current: ${peerStatus.pin})")
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onDismiss()
                                    onOpenHostManager()
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, surfaces.hairlineBorder),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Rounded.Computer, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Manage PCs", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = onRescanDisks,
                                enabled = activeHost != null,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, surfaces.hairlineBorder),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Rescan Disks", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }

                        Text(
                            text = "Thumbnail Disk Cache Limit",
                            style = MaterialTheme.typography.labelLarge,
                            color = surfaces.textSecondary,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            listOf(
                                0 to "Auto (${if (isTablet) "512 MB" else "256 MB"})",
                                256 to "256 MB",
                                512 to "512 MB",
                                1024 to "1 GB",
                                2048 to "2 GB",
                            ).forEach { (limitMb, lbl) ->
                                FilterChip(
                                    selected = prefs.thumbnailCacheSizeMb == limitMb,
                                    onClick = { onUpdatePrefs { it.copy(thumbnailCacheSizeMb = limitMb) } },
                                    label = { Text(lbl, style = MaterialTheme.typography.labelMedium) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = matteChipColors(),
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Local Thumbnail Cache", style = MaterialTheme.typography.bodyMedium, color = surfaces.textPrimary)
                                Text(
                                    text = "Used ${Formatters.formatBytes(localCacheBytes)} / ${prefs.resolvedThumbnailCacheMb(isTablet)} MB limit • Survives Wi-Fi IP changes",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = surfaces.textSecondary,
                                )
                            }
                            Button(
                                onClick = onClearCache,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = surfaces.elevatedSurface,
                                    contentColor = surfaces.textPrimary,
                                ),
                            ) {
                                Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Clear Cache")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val surfaces = LocalDeckSurfaces.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = surfaces.cardSurface,
        border = BorderStroke(1.dp, surfaces.hairlineBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

@Composable
private fun PaletteSwatchCard(
    palette: AccentPalette,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val surfaces = LocalDeckSurfaces.current
    val swatchColor = when (palette) {
        AccentPalette.DYNAMIC_YOU -> Color(0xFF8B9EFF)
        AccentPalette.COBALT -> Color(0xFF60A5FA)
        AccentPalette.EMERALD -> Color(0xFF34D399)
        AccentPalette.AMETHYST -> Color(0xFFC084FC)
        AccentPalette.CRIMSON -> Color(0xFFFB7185)
        AccentPalette.AMBER -> Color(0xFFFBBF24)
        AccentPalette.CYBER_TEAL -> Color(0xFF2DD4BF)
        AccentPalette.MATTE_SLATE -> Color(0xFFB9C3DE)
        AccentPalette.MATTE_SAGE -> Color(0xFFA8C2B2)
        AccentPalette.MATTE_CLAY -> Color(0xFFD1B3A7)
        AccentPalette.PLAIN_BLACK -> Color(0xFFE2E2E7)
    }
    val swatchBg = when (palette) {
        AccentPalette.DYNAMIC_YOU -> Color(0xFF171B2E)
        AccentPalette.COBALT -> Color(0xFF0F1D35)
        AccentPalette.EMERALD -> Color(0xFF0C221A)
        AccentPalette.AMETHYST -> Color(0xFF1B132E)
        AccentPalette.CRIMSON -> Color(0xFF280F17)
        AccentPalette.AMBER -> Color(0xFF241A0B)
        AccentPalette.CYBER_TEAL -> Color(0xFF0D2024)
        AccentPalette.MATTE_SLATE -> Color(0xFF141822)
        AccentPalette.MATTE_SAGE -> Color(0xFF131B16)
        AccentPalette.MATTE_CLAY -> Color(0xFF1B1513)
        AccentPalette.PLAIN_BLACK -> Color(0xFF000000)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else surfaces.elevatedSurface,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else surfaces.hairlineBorder,
        ),
        modifier = Modifier
            .width(106.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(swatchBg),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(swatchColor),
                    )
                    if (selected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Selected",
                            tint = swatchColor,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            Text(
                text = palette.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) surfaces.textPrimary else surfaces.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val surfaces = LocalDeckSurfaces.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = surfaces.textPrimary,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = surfaces.textSecondary,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun matteChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
    containerColor = LocalDeckSurfaces.current.elevatedSurface,
    labelColor = LocalDeckSurfaces.current.textSecondary,
)
