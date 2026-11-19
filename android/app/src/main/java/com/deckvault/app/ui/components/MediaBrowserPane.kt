package com.deckvault.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.Merge
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.TabletAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Queue
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SubdirectoryArrowRight
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.deckvault.app.data.DeckVaultApi
import com.deckvault.app.model.AccentPalette
import com.deckvault.app.model.CardAspect
import com.deckvault.app.model.FileItem
import com.deckvault.app.model.FolderItem
import com.deckvault.app.model.MediaFilter
import com.deckvault.app.model.QuickFolder
import com.deckvault.app.model.SavedHost
import com.deckvault.app.model.SortBy
import com.deckvault.app.model.ThemeSurfaceStyle
import com.deckvault.app.model.UserPreferences
import com.deckvault.app.model.ViewMode
import com.deckvault.app.ui.theme.LocalDeckSurfaces
import com.deckvault.app.ui.theme.OledColors
import com.deckvault.app.ui.util.Formatters
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MediaBrowserPane(
    host: SavedHost?,
    api: DeckVaultApi,
    folderName: String,
    currentPath: String,
    totalFolderSizeBytes: Long,
    folders: List<FolderItem>,
    files: List<FileItem>,
    isLoading: Boolean,
    isSpaceHogsMode: Boolean,
    searchQuery: String,
    recursiveSearch: Boolean,
    prefs: UserPreferences,
    selectedPaths: Set<String>,
    cacheVersion: Long,
    showOpenFoldersButton: Boolean,
    onOpenFoldersDrawer: () -> Unit,
    onExitSpaceHogs: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleRecursiveSearch: () -> Unit,
    onSelectFolder: (String) -> Unit,
    onOpenVideo: (FileItem, Boolean) -> Unit, // file, openDirectlyInTrimmer
    onOpenImage: (FileItem) -> Unit,
    onOpenFile: (FileItem) -> Unit,
    onOpenAllDrives: () -> Unit,
    onToggleSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: (Boolean) -> Unit,
    onMoveSelected: () -> Unit,
    onCopySelected: () -> Unit,
    onMoveFile: (FileItem) -> Unit,
    onCopyFile: (FileItem) -> Unit,
    onUploadFromDevice: () -> Unit,
    onRenameFile: (FileItem, String) -> Unit,
    onOpenSettings: () -> Unit,
    onUpdatePrefs: ((UserPreferences) -> UserPreferences) -> Unit,
    modifier: Modifier = Modifier,
    savedHosts: List<SavedHost> = emptyList(),
    quickFolders: List<QuickFolder> = emptyList(),
    onSelectHost: (SavedHost) -> Unit = {},
    onOpenHostManager: () -> Unit = {},
    needsAllFilesPermission: Boolean = false,
    onGrantAllFilesAccess: () -> Unit = {},
    onOptimizeFile: (FileItem, OptimizerTab) -> Unit = { _, _ -> },
    onDeleteSingleFile: (FileItem, Boolean) -> Unit = { _, _ -> },
    onMergeSelected: () -> Unit = {},
    activeQueueCount: Int = 0,
    onOpenQueue: () -> Unit = {},
) {
    var showCustomizeSheet by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var contextFile by remember { mutableStateOf<FileItem?>(null) }
    var infoFile by remember { mutableStateOf<FileItem?>(null) }
    var renameTarget by remember { mutableStateOf<FileItem?>(null) }

    val surfaces = LocalDeckSurfaces.current
    val maxFileBytes = remember(files) { files.maxOfOrNull { it.sizeBytes }?.coerceAtLeast(1L) ?: 1L }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(OledColors.PitchBlack)) {
        val isCompactPhone = maxWidth < 600.dp
        val isVeryNarrow = maxWidth < 390.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isCompactPhone) 10.dp else 12.dp, vertical = if (isCompactPhone) 8.dp else 10.dp),
        ) {
            if (needsAllFilesPermission && host?.isLocalDevice == true) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text(
                            "Grant All Files Access to browse & manage all device folders directly.",
                            style = MaterialTheme.typography.bodySmall,
                            color = surfaces.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = onGrantAllFilesAccess, shape = RoundedCornerShape(10.dp)) {
                            Text("Allow")
                        }
                    }
                }
            }

            if (isCompactPhone) {
                // FLAGSHIP PHONE / Z FOLD COVER SCREEN RESPONSIVE HEADER (Zero overflow, zero squishing)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (showOpenFoldersButton) {
                        Surface(
                            onClick = onOpenFoldersDrawer,
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.padding(horizontal = if (isVeryNarrow) 8.dp else 10.dp, vertical = 8.dp),
                            ) {
                                Icon(Icons.Rounded.Folder, contentDescription = "Folders", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
                                if (!isVeryNarrow) {
                                    Text("Folders", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = if (isSpaceHogsMode) "Largest Space Hogs" else folderName.ifBlank { host?.displayName ?: "Files & Media" },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = OledColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (isSpaceHogsMode) {
                                Surface(
                                    onClick = onExitSpaceHogs,
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                ) {
                                    Text(
                                        "Exit",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                        Text(
                            text = buildString {
                                if (totalFolderSizeBytes >= 0 && !isSpaceHogsMode) {
                                    append(Formatters.formatBytes(totalFolderSizeBytes))
                                } else if (currentPath.isNotBlank()) {
                                    append(currentPath)
                                }
                                if (files.isNotEmpty()) {
                                    if (isNotEmpty()) append(" • ")
                                    append("${files.size} items")
                                }
                            }.ifBlank { host?.displayName ?: "Browse storage" },
                            style = MaterialTheme.typography.labelSmall,
                            color = OledColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Surface(
                        onClick = onOpenQueue,
                        shape = RoundedCornerShape(12.dp),
                        color = if (activeQueueCount > 0) MaterialTheme.colorScheme.primaryContainer else OledColors.CardSurface,
                        border = BorderStroke(1.dp, if (activeQueueCount > 0) MaterialTheme.colorScheme.primary else OledColors.HairlineBorder),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        ) {
                            Icon(Icons.Rounded.Queue, contentDescription = "Queue", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            if (activeQueueCount > 0) {
                                Text("$activeQueueCount", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                    Surface(
                        onClick = onOpenAllDrives,
                        shape = RoundedCornerShape(12.dp),
                        color = OledColors.CardSurface,
                        border = BorderStroke(1.dp, OledColors.HairlineBorder),
                    ) {
                        Icon(Icons.Rounded.Storage, contentDescription = "All Drives", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp).size(18.dp))
                    }
                    Surface(
                        onClick = onOpenSettings,
                        shape = RoundedCornerShape(12.dp),
                        color = OledColors.CardSurface,
                        border = BorderStroke(1.dp, OledColors.HairlineBorder),
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = OledColors.TextSecondary, modifier = Modifier.padding(8.dp).size(18.dp))
                    }
                }

                // 1-Tap Device Switcher Strip on Phones
                if (savedHosts.isNotEmpty() || showOpenFoldersButton) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    ) {
                        savedHosts.forEach { sh ->
                            val active = sh.hostId == host?.hostId
                            val isLap = sh.displayName.contains("laptop", ignoreCase = true)
                            Surface(
                                onClick = { onSelectHost(sh) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (active) MaterialTheme.colorScheme.primaryContainer else OledColors.CardSurface,
                                border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary else OledColors.HairlineBorder),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                ) {
                                    Icon(
                                        imageVector = when {
                                            sh.isTabletDevice -> Icons.Rounded.TabletAndroid
                                            sh.isAndroidDevice -> Icons.Rounded.PhoneAndroid
                                            isLap -> Icons.Rounded.Laptop
                                            else -> Icons.Rounded.Computer
                                        },
                                        contentDescription = null,
                                        tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer else OledColors.TextSecondary,
                                        modifier = Modifier.size(15.dp),
                                    )
                                    Text(
                                        text = if (sh.isLocalDevice) "This Phone" else sh.displayName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else OledColors.TextPrimary,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        Surface(
                            onClick = onOpenHostManager,
                            shape = RoundedCornerShape(10.dp),
                            color = OledColors.ElevatedSurface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            ) {
                                Icon(Icons.Rounded.AddLink, contentDescription = "Pair Device", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                                Text("Pair", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Phone Search + Un-crushed View/Sort/Tune Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = {
                            Text(
                                if (recursiveSearch) "Search subfolders..." else "Filter folder...",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(17.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = OledColors.CardSurface,
                            unfocusedContainerColor = OledColors.CardSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = OledColors.HairlineBorder,
                            focusedTextColor = OledColors.TextPrimary,
                            unfocusedTextColor = OledColors.TextPrimary,
                        ),
                        modifier = Modifier.weight(1f).height(46.dp),
                    )

                    ViewModePillSwitcher(
                        selected = prefs.viewMode,
                        onSelect = { mode -> onUpdatePrefs { it.copy(viewMode = mode) } },
                    )

                    Box {
                        Surface(
                            onClick = { showSortMenu = true },
                            shape = RoundedCornerShape(12.dp),
                            color = OledColors.CardSurface,
                            border = BorderStroke(1.dp, OledColors.HairlineBorder),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(17.dp))
                                Text(if (prefs.sortDescending) "↓" else "↑", style = MaterialTheme.typography.labelMedium, color = OledColors.TextPrimary)
                            }
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            containerColor = OledColors.ElevatedSurface,
                        ) {
                            SortBy.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (prefs.sortBy == option) {
                                                "${option.label} (${if (prefs.sortDescending) "Desc" else "Asc"})"
                                            } else option.label,
                                            color = if (prefs.sortBy == option) MaterialTheme.colorScheme.primary else OledColors.TextPrimary,
                                        )
                                    },
                                    onClick = {
                                        showSortMenu = false
                                        onUpdatePrefs {
                                            if (it.sortBy == option) it.copy(sortDescending = !it.sortDescending)
                                            else it.copy(sortBy = option, sortDescending = option != SortBy.NAME)
                                        }
                                    },
                                )
                            }
                        }
                    }

                    if (host != null && currentPath.isNotBlank() && !isSpaceHogsMode) {
                        Surface(
                            onClick = onUploadFromDevice,
                            shape = RoundedCornerShape(12.dp),
                            color = OledColors.CardSurface,
                            border = BorderStroke(1.dp, OledColors.HairlineBorder),
                        ) {
                            Icon(Icons.Rounded.FileUpload, contentDescription = "Upload", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(9.dp).size(18.dp))
                        }
                    }

                    Surface(
                        onClick = { showCustomizeSheet = true },
                        shape = RoundedCornerShape(12.dp),
                        color = OledColors.CardSurface,
                        border = BorderStroke(1.dp, OledColors.HairlineBorder),
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Customize View", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(9.dp).size(18.dp))
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Phone Scope & Media Type Filter Strip (never clipped!)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = recursiveSearch,
                        onClick = onToggleRecursiveSearch,
                        leadingIcon = { Icon(Icons.Rounded.SubdirectoryArrowRight, null, Modifier.size(15.dp)) },
                        label = { Text(if (recursiveSearch) "Subfolders" else "This Folder", style = MaterialTheme.typography.labelMedium) },
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            containerColor = OledColors.CardSurface,
                            labelColor = OledColors.TextSecondary,
                        ),
                    )
                    MediaFilter.entries.forEach { mf ->
                        val selected = prefs.mediaFilter == mf
                        FilterChip(
                            selected = selected,
                            onClick = { onUpdatePrefs { it.copy(mediaFilter = mf) } },
                            label = { Text(mf.label, style = MaterialTheme.typography.labelMedium) },
                            shape = RoundedCornerShape(10.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                containerColor = OledColors.CardSurface,
                                labelColor = OledColors.TextSecondary,
                            ),
                        )
                    }
                }
            } else {
                // TABLET & Z FOLD UNFOLDED WIDE HEADER
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (showOpenFoldersButton) {
                        Surface(
                            onClick = onOpenFoldersDrawer,
                            shape = RoundedCornerShape(14.dp),
                            color = OledColors.ElevatedSurface,
                            border = BorderStroke(1.dp, OledColors.HairlineBorder),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(Icons.Rounded.Folder, contentDescription = "Folders", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Text("Folders", style = MaterialTheme.typography.labelLarge, color = OledColors.TextPrimary)
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = if (isSpaceHogsMode) "Largest Space Hogs" else folderName.ifBlank { "Files & Media" },
                                style = MaterialTheme.typography.titleLarge,
                                color = OledColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (isSpaceHogsMode) {
                                Surface(
                                    onClick = onExitSpaceHogs,
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                ) {
                                    Text(
                                        "Back to Folder",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                        Text(
                            text = buildString {
                                if (currentPath.isNotBlank()) append(currentPath)
                                if (totalFolderSizeBytes >= 0 && !isSpaceHogsMode) {
                                    if (isNotEmpty()) append("  •  ")
                                    append(Formatters.formatBytes(totalFolderSizeBytes))
                                }
                                if (files.isNotEmpty()) {
                                    if (isNotEmpty()) append("  •  ")
                                    append("${files.size} items")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = OledColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Surface(
                        onClick = onOpenQueue,
                        shape = RoundedCornerShape(14.dp),
                        color = if (activeQueueCount > 0) MaterialTheme.colorScheme.primaryContainer else OledColors.CardSurface,
                        border = BorderStroke(1.dp, if (activeQueueCount > 0) MaterialTheme.colorScheme.primary else OledColors.HairlineBorder),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Icon(Icons.Rounded.Queue, contentDescription = "Queue", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text(
                                if (activeQueueCount > 0) "Queue ($activeQueueCount)" else "Queue",
                                style = MaterialTheme.typography.labelLarge,
                                color = OledColors.TextPrimary,
                            )
                        }
                    }

                    Surface(
                        onClick = onOpenAllDrives,
                        shape = RoundedCornerShape(14.dp),
                        color = OledColors.CardSurface,
                        border = BorderStroke(1.dp, OledColors.HairlineBorder),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Icon(Icons.Rounded.Storage, contentDescription = "All Drives", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text("Drives", style = MaterialTheme.typography.labelLarge, color = OledColors.TextPrimary)
                        }
                    }

                    ViewModePillSwitcher(
                        selected = prefs.viewMode,
                        onSelect = { mode -> onUpdatePrefs { it.copy(viewMode = mode) } },
                    )

                    Box {
                        Surface(
                            onClick = { showSortMenu = true },
                            shape = RoundedCornerShape(14.dp),
                            color = OledColors.CardSurface,
                            border = BorderStroke(1.dp, OledColors.HairlineBorder),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "${prefs.sortBy.label} ${if (prefs.sortDescending) "↓" else "↑"}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = OledColors.TextPrimary,
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            containerColor = OledColors.ElevatedSurface,
                        ) {
                            SortBy.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (prefs.sortBy == option) {
                                                "${option.label} (${if (prefs.sortDescending) "Desc" else "Asc"})"
                                            } else option.label,
                                            color = if (prefs.sortBy == option) MaterialTheme.colorScheme.primary else OledColors.TextPrimary,
                                        )
                                    },
                                    onClick = {
                                        showSortMenu = false
                                        onUpdatePrefs {
                                            if (it.sortBy == option) it.copy(sortDescending = !it.sortDescending)
                                            else it.copy(sortBy = option, sortDescending = option != SortBy.NAME)
                                        }
                                    },
                                )
                            }
                        }
                    }

                    if (host != null && currentPath.isNotBlank() && !isSpaceHogsMode) {
                        Surface(
                            onClick = onUploadFromDevice,
                            shape = RoundedCornerShape(12.dp),
                            color = OledColors.CardSurface,
                            border = BorderStroke(1.dp, OledColors.HairlineBorder),
                        ) {
                            Icon(Icons.Rounded.FileUpload, contentDescription = "Send from this device", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(8.dp).size(19.dp))
                        }
                    }

                    Surface(
                        onClick = { showCustomizeSheet = true },
                        shape = RoundedCornerShape(12.dp),
                        color = OledColors.CardSurface,
                        border = BorderStroke(1.dp, OledColors.HairlineBorder),
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Customize View", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp).size(19.dp))
                    }

                    Surface(
                        onClick = onOpenSettings,
                        shape = RoundedCornerShape(12.dp),
                        color = OledColors.CardSurface,
                        border = BorderStroke(1.dp, OledColors.HairlineBorder),
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = OledColors.TextSecondary, modifier = Modifier.padding(8.dp).size(19.dp))
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = {
                            Text(
                                if (recursiveSearch) "Search all subfolders..." else "Filter folder...",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = OledColors.CardSurface,
                            unfocusedContainerColor = OledColors.CardSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = OledColors.HairlineBorder,
                            focusedTextColor = OledColors.TextPrimary,
                            unfocusedTextColor = OledColors.TextPrimary,
                        ),
                        modifier = Modifier.widthIn(min = 160.dp, max = 220.dp).height(48.dp),
                    )

                    FilterChip(
                        selected = recursiveSearch,
                        onClick = onToggleRecursiveSearch,
                        leadingIcon = { Icon(Icons.Rounded.SubdirectoryArrowRight, contentDescription = null, modifier = Modifier.size(15.dp)) },
                        label = { Text(if (recursiveSearch) "All Subfolders" else "This Folder", style = MaterialTheme.typography.labelMedium) },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            containerColor = OledColors.CardSurface,
                            labelColor = OledColors.TextSecondary,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = recursiveSearch,
                            borderColor = OledColors.HairlineBorder,
                            selectedBorderColor = MaterialTheme.colorScheme.secondary,
                        ),
                    )

                    Row(
                        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        MediaFilter.entries.forEach { mf ->
                            val selected = prefs.mediaFilter == mf
                            FilterChip(
                                selected = selected,
                                onClick = { onUpdatePrefs { it.copy(mediaFilter = mf) } },
                                label = { Text(mf.label, style = MaterialTheme.typography.labelMedium) },
                                shape = RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    containerColor = OledColors.CardSurface,
                                    labelColor = OledColors.TextSecondary,
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = OledColors.HairlineBorder,
                                    selectedBorderColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Main Content Area: Gallery or List
            when {
                host == null -> {
                    EmptyStateBox(
                        title = "Pair Your PC or Laptop",
                        subtitle = "Run DeckVault Companion on your Windows PC and laptop, then tap Pair above to connect over Wi-Fi.",
                        showQuickActions = true,
                        isCompactPhone = isCompactPhone,
                        hasActiveFilters = false,
                        quickFolders = emptyList(),
                        onOpenFolders = onOpenFoldersDrawer,
                        onOpenAllDrives = onOpenAllDrives,
                        onClearFilters = {},
                        onSelectFolder = onSelectFolder,
                    )
                }
                isLoading && files.isEmpty() && folders.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                files.isEmpty() && folders.isEmpty() -> {
                    val hasFilters = searchQuery.isNotBlank() || prefs.mediaFilter != MediaFilter.ALL || recursiveSearch
                    EmptyStateBox(
                        title = "Nothing matches here",
                        subtitle = if (showOpenFoldersButton || isCompactPhone) {
                            "Try clearing filters, switching devices above, or picking a quick folder below."
                        } else {
                            "Try clearing filters or picking another folder from the left panel."
                        },
                        showQuickActions = showOpenFoldersButton || isCompactPhone || hasFilters,
                        isCompactPhone = isCompactPhone,
                        hasActiveFilters = hasFilters,
                        quickFolders = quickFolders,
                        onOpenFolders = onOpenFoldersDrawer,
                        onOpenAllDrives = onOpenAllDrives,
                        onClearFilters = {
                            if (searchQuery.isNotBlank()) onSearchQueryChange("")
                            if (prefs.mediaFilter != MediaFilter.ALL) onUpdatePrefs { it.copy(mediaFilter = MediaFilter.ALL) }
                            if (recursiveSearch) onToggleRecursiveSearch()
                        },
                        onSelectFolder = onSelectFolder,
                    )
                }
                prefs.viewMode == ViewMode.GALLERY -> {
                    BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        val cols = if (prefs.gridColumns <= 0) {
                            if (maxWidth < 460.dp) 2 else (maxWidth.value / 210f).roundToInt().coerceIn(2, 7)
                        } else {
                            prefs.gridColumns.coerceIn(1, 8)
                        }

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(cols),
                            contentPadding = PaddingValues(bottom = if (selectedPaths.isNotEmpty()) 96.dp else 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            if (folders.isNotEmpty() && !isSpaceHogsMode) {
                                item(span = { GridItemSpan(cols) }) {
                                    InlineFolderStrip(folders = folders, onSelectFolder = onSelectFolder)
                                }
                            }
                            items(files, key = { it.path }, contentType = { it.mediaType }) { file ->
                                GalleryMediaCard(
                                    host = host,
                                    api = api,
                                    file = file,
                                    prefs = prefs,
                                    isSelected = file.path in selectedPaths,
                                    inSelectionMode = selectedPaths.isNotEmpty(),
                                    cacheVersion = cacheVersion,
                                    onClick = {
                                        if (selectedPaths.isNotEmpty()) {
                                            onToggleSelect(file.path)
                                        } else if (file.isVideo) {
                                            onOpenVideo(file, false)
                                        } else if (file.isImage) {
                                            onOpenImage(file)
                                        } else {
                                            onOpenFile(file)
                                        }
                                    },
                                    onLongClick = { onToggleSelect(file.path) },
                                    onQuickTrim = { onOpenVideo(file, true) },
                                    onMoreActions = { contextFile = file },
                                )
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        contentPadding = PaddingValues(bottom = if (selectedPaths.isNotEmpty()) 96.dp else 16.dp),
                        verticalArrangement = Arrangement.spacedBy(if (prefs.compactListMode) 5.dp else 8.dp),
                    ) {
                        if (folders.isNotEmpty() && !isSpaceHogsMode) {
                            item {
                                InlineFolderStrip(folders = folders, onSelectFolder = onSelectFolder)
                            }
                        }
                        items(files, key = { it.path }, contentType = { it.mediaType }) { file ->
                            ListMediaRow(
                                host = host,
                                api = api,
                                file = file,
                                maxFileBytes = maxFileBytes,
                                prefs = prefs,
                                isSelected = file.path in selectedPaths,
                                cacheVersion = cacheVersion,
                                onClick = {
                                    if (selectedPaths.isNotEmpty()) {
                                        onToggleSelect(file.path)
                                    } else if (file.isVideo) {
                                        onOpenVideo(file, false)
                                    } else if (file.isImage) {
                                        onOpenImage(file)
                                    } else {
                                        onOpenFile(file)
                                    }
                                },
                                onLongClick = { onToggleSelect(file.path) },
                                onQuickTrim = { onOpenVideo(file, true) },
                                onMoreActions = { contextFile = file },
                            )
                        }
                    }
                }
            }
        }

        // Matte Floating Multi-Select Action Bar
        AnimatedVisibility(
            visible = selectedPaths.isNotEmpty(),
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp, start = 12.dp, end = 12.dp),
        ) {
            val selectedBytes = files.filter { it.path in selectedPaths }.sumOf { it.sizeBytes }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = OledColors.HighestSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                shadowElevation = 8.dp,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "${selectedPaths.size} (${Formatters.formatBytes(selectedBytes)})",
                        style = MaterialTheme.typography.titleSmall,
                        color = OledColors.TextPrimary,
                    )
                    Button(
                        onClick = onMoveSelected,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Move")
                    }
                    OutlinedButton(
                        onClick = onCopySelected,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, surfaces.hairlineBorder),
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy")
                    }
                    OutlinedButton(
                        onClick = onSelectAll,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, surfaces.hairlineBorder),
                    ) {
                        Icon(Icons.Rounded.SelectAll, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("All")
                    }
                    val selectedVideoCount = files.count { it.path in selectedPaths && it.isVideo }
                    if (selectedVideoCount >= 2) {
                        OutlinedButton(
                            onClick = onMergeSelected,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                        ) {
                            Icon(androidx.compose.material.icons.Icons.Rounded.Merge, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Merge ($selectedVideoCount)")
                        }
                    }
                    Button(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete")
                    }
                    IconButton(onClick = onClearSelection) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear selection", tint = OledColors.TextSecondary)
                    }
                }
            }
        }
    }

    if (showCustomizeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCustomizeSheet = false },
            containerColor = OledColors.PanelSurface,
            contentColor = OledColors.TextPrimary,
        ) {
            CustomizeViewSheetContent(
                prefs = prefs,
                onUpdatePrefs = onUpdatePrefs,
                onOpenAllSettings = {
                    showCustomizeSheet = false
                    onOpenSettings()
                },
            )
        }
    }

    // Quick Context Sheet for Move / Copy / Info / Rename / Cut / Select
    contextFile?.let { file ->
        ModalBottomSheet(
            onDismissRequest = { contextFile = null },
            containerColor = surfaces.panelSurface,
            contentColor = surfaces.textPrimary,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = surfaces.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${Formatters.formatBytes(file.sizeBytes)} • ${file.path}",
                    style = MaterialTheme.typography.bodySmall,
                    color = surfaces.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                ContextActionRow(
                    icon = Icons.AutoMirrored.Rounded.DriveFileMove,
                    label = "Move to Folder or Device",
                    onClick = {
                        contextFile = null
                        onMoveFile(file)
                    },
                )
                ContextActionRow(
                    icon = Icons.Rounded.ContentCopy,
                    label = "Copy to Folder or Device",
                    onClick = {
                        contextFile = null
                        onCopyFile(file)
                    },
                )
                ContextActionRow(
                    icon = Icons.Rounded.Info,
                    label = "File Details & Codec Info",
                    onClick = {
                        contextFile = null
                        infoFile = file
                    },
                )
                if (file.isVideo) {
                    ContextActionRow(
                        icon = Icons.Rounded.ContentCut,
                        label = "Edit Studio (Cut, Crop, Rotate, Effects)",
                        onClick = {
                            contextFile = null
                            onOpenVideo(file, true)
                        },
                    )
                }
                if (file.isVideo || file.isImage) {
                    ContextActionRow(
                        icon = androidx.compose.material.icons.Icons.Rounded.Compress,
                        label = "Compress Size (Target MB / H.264 / HEVC)",
                        onClick = {
                            contextFile = null
                            onOptimizeFile(file, OptimizerTab.COMPRESS)
                        },
                    )
                    ContextActionRow(
                        icon = androidx.compose.material.icons.Icons.Rounded.AutoFixHigh,
                        label = "Upscale & Enhance (1080p / 4K / Pro)",
                        onClick = {
                            contextFile = null
                            onOptimizeFile(file, OptimizerTab.UPSCALE)
                        },
                    )
                }
                ContextActionRow(
                    icon = Icons.Rounded.DriveFileRenameOutline,
                    label = "Rename File",
                    onClick = {
                        contextFile = null
                        renameTarget = file
                    },
                )
                ContextActionRow(
                    icon = Icons.Rounded.CheckCircle,
                    label = if (file.path in selectedPaths) "Deselect" else "Select",
                    onClick = {
                        contextFile = null
                        onToggleSelect(file.path)
                    },
                )
                ContextActionRow(
                    icon = androidx.compose.material.icons.Icons.Rounded.DeleteOutline,
                    label = "Move to Trash / Recycle Bin",
                    onClick = {
                        contextFile = null
                        onDeleteSingleFile(file, true)
                    },
                )
                ContextActionRow(
                    icon = androidx.compose.material.icons.Icons.Rounded.DeleteForever,
                    label = "Permanently Delete",
                    onClick = {
                        contextFile = null
                        onDeleteSingleFile(file, false)
                    },
                )
                Spacer(Modifier.height(14.dp))
            }
        }
    }

    // File Info Inspector Dialog
    infoFile?.let { file ->
        AlertDialog(
            onDismissRequest = { infoFile = null },
            containerColor = surfaces.elevatedSurface,
            title = { Text("File Details", color = surfaces.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoLine("Name", file.name)
                    InfoLine("Size", Formatters.formatBytes(file.sizeBytes))
                    InfoLine("Path", file.path)
                    if (file.modifiedMs > 0) InfoLine("Modified", Formatters.formatShortDate(file.modifiedMs))
                    file.meta?.let { m ->
                        if (m.durationMs > 0) InfoLine("Duration", Formatters.formatDuration(m.durationMs))
                        if (m.width > 0 && m.height > 0) InfoLine("Resolution", "${m.width} x ${m.height}")
                        if (m.videoCodec.isNotBlank()) InfoLine("Video Codec", m.videoCodec.uppercase())
                        if (m.fps > 0) InfoLine("Frame Rate", "${m.fps.roundToInt()} fps")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { infoFile = null }) { Text("Close") }
            },
        )
    }

    // Rename File Dialog
    renameTarget?.let { file ->
        var newName by remember(file.path) { mutableStateOf(file.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = surfaces.elevatedSurface,
            title = { Text("Rename File", color = surfaces.textPrimary) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("New filename") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = file
                        val nameToApply = newName.trim()
                        renameTarget = null
                        if (nameToApply.isNotBlank() && nameToApply != target.name) {
                            onRenameFile(target, nameToApply)
                        }
                    },
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            },
        )
    }

    if (showDeleteConfirm) {
        val selectedBytes = files.filter { it.path in selectedPaths }.sumOf { it.sizeBytes }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = OledColors.ElevatedSurface,
            title = { Text("Delete ${selectedPaths.size} files?", color = OledColors.TextPrimary) },
            text = {
                Text(
                    "This will reclaim ${Formatters.formatBytes(selectedBytes)} on ${host?.displayName ?: "this device"}. " +
                        "Choose whether to move files to Trash/Recycle Bin or Permanently Delete them.",
                    color = OledColors.TextSecondary,
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            showDeleteConfirm = false
                            onDeleteSelected(true)
                        },
                    ) {
                        Text("Recycle")
                    }
                    Button(
                        onClick = {
                            showDeleteConfirm = false
                            onDeleteSelected(false)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text("Permanently Delete")
                    }
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun InlineFolderStrip(
    folders: List<FolderItem>,
    onSelectFolder: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        folders.take(24).forEach { folder ->
            Surface(
                onClick = { onSelectFolder(folder.path) },
                shape = RoundedCornerShape(14.dp),
                color = OledColors.CardSurface,
                border = BorderStroke(1.dp, OledColors.HairlineBorder),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    Icon(Icons.Rounded.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text(folder.name, style = MaterialTheme.typography.labelLarge, color = OledColors.TextPrimary, maxLines = 1)
                    if (folder.sizeBytes >= 0) {
                        Text(
                            Formatters.formatBytes(folder.sizeBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryMediaCard(
    host: SavedHost,
    api: DeckVaultApi,
    file: FileItem,
    prefs: UserPreferences,
    isSelected: Boolean,
    inSelectionMode: Boolean,
    cacheVersion: Long,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onQuickTrim: () -> Unit,
    onMoreActions: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = OledColors.CardSurface,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else OledColors.HairlineBorder,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(prefs.cardAspect.ratio)
                    .background(Color.Black),
            ) {
                when {
                    file.isVideo -> {
                        VideoScrubPreview(
                            thumbnailUrl = api.videoThumbnailUrl(host, file.path, width = 480, cacheBust = cacheVersion),
                            storyboardUrl = api.videoStoryboardUrl(host, file.path, cacheBust = cacheVersion),
                            durationMs = file.meta?.durationMs ?: 0L,
                            enableScrub = prefs.enableHoverScrub,
                            contentDescription = file.name,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    file.isImage -> {
                        AsyncImage(
                            model = api.imageUrl(host, file.path, width = 480, cacheBust = cacheVersion),
                            contentDescription = file.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    else -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = if (file.isAudio) Icons.Rounded.AudioFile else Icons.Rounded.InsertDriveFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(34.dp),
                                )
                                if (file.extension.isNotBlank()) {
                                    Text(
                                        text = file.extension.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OledColors.TextSecondary,
                                    )
                                }
                            }
                        }
                    }
                }

                // Top-left size badge
                if (prefs.showFileSize) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.78f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.align(Alignment.TopStart).padding(7.dp),
                    ) {
                        Text(
                            text = Formatters.formatBytes(file.sizeBytes),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (file.sizeBytes > 500L * 1024 * 1024) MaterialTheme.colorScheme.tertiary else Color.White,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        )
                    }
                }

                // Top-right selection check OR Quick Trim button for videos
                if (inSelectionMode || isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Selected",
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp),
                    )
                } else if (file.isVideo) {
                    Surface(
                        onClick = onQuickTrim,
                        color = Color.Black.copy(alpha = 0.78f),
                        shape = CircleShape,
                        modifier = Modifier.align(Alignment.TopEnd).padding(7.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCut,
                            contentDescription = "Cut video",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(6.dp).size(15.dp),
                        )
                    }
                }

                // Bottom-right duration & resolution badges
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(horizontal = 7.dp, vertical = 14.dp),
                ) {
                    if (prefs.showResolutionBadge && file.meta != null) {
                        val res = Formatters.formatResolution(file.meta.width, file.meta.height)
                        if (res.isNotBlank()) {
                            Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f), shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    text = res,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    if (prefs.showDuration && file.isVideo && (file.meta?.durationMs ?: 0L) > 0L) {
                        Surface(color = Color.Black.copy(alpha = 0.82f), shape = RoundedCornerShape(6.dp)) {
                            Text(
                                text = Formatters.formatDuration(file.meta?.durationMs ?: 0L),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            if (prefs.showFileName) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 2.dp, top = 7.dp, bottom = 7.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = OledColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val sub = buildString {
                            if (file.meta != null && file.meta.videoCodec.isNotBlank()) {
                                append(file.meta.videoCodec.uppercase())
                                if (file.meta.fps > 0) append(" • ${file.meta.fps.roundToInt()}fps")
                            } else if (file.extension.isNotBlank()) {
                                append(file.extension.uppercase())
                            }
                            if (prefs.showModifiedDate) {
                                val dt = Formatters.formatShortDate(file.modifiedMs)
                                if (dt.isNotBlank()) {
                                    if (isNotEmpty()) append(" • ")
                                    append(dt)
                                }
                            }
                        }
                        if (sub.isNotBlank()) {
                            Text(
                                text = sub,
                                style = MaterialTheme.typography.labelSmall,
                                color = OledColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(
                        onClick = onMoreActions,
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "File options",
                            tint = OledColors.TextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListMediaRow(
    host: SavedHost,
    api: DeckVaultApi,
    file: FileItem,
    maxFileBytes: Long,
    prefs: UserPreferences,
    isSelected: Boolean,
    cacheVersion: Long,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onQuickTrim: () -> Unit,
    onMoreActions: () -> Unit,
) {
    val sizeFrac = (file.sizeBytes.toFloat() / maxFileBytes.toFloat()).coerceIn(0f, 1f)
    val compact = prefs.compactListMode
    Surface(
        shape = RoundedCornerShape(if (compact) 14.dp else 16.dp),
        color = OledColors.CardSurface,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else OledColors.HairlineBorder,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
            modifier = Modifier.padding(if (compact) 6.dp else 10.dp),
        ) {
            Checkbox(checked = isSelected, onCheckedChange = { onLongClick() })

            // Scrubbable preview thumbnail even in List View!
            Box(
                modifier = Modifier
                    .width(if (compact) 92.dp else 128.dp)
                    .height(if (compact) 52.dp else 72.dp)
                    .clip(RoundedCornerShape(if (compact) 8.dp else 12.dp))
                    .background(Color.Black),
            ) {
                when {
                    file.isVideo -> {
                        VideoScrubPreview(
                            thumbnailUrl = api.videoThumbnailUrl(host, file.path, width = 360, cacheBust = cacheVersion),
                            storyboardUrl = api.videoStoryboardUrl(host, file.path, cacheBust = cacheVersion),
                            durationMs = file.meta?.durationMs ?: 0L,
                            enableScrub = prefs.enableHoverScrub,
                            contentDescription = file.name,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    file.isImage -> {
                        AsyncImage(
                            model = api.imageUrl(host, file.path, width = 360, cacheBust = cacheVersion),
                            contentDescription = file.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    else -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (file.isAudio) Icons.Rounded.AudioFile else Icons.Rounded.InsertDriveFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp)) {
                Text(
                    text = file.name,
                    style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                    color = OledColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = Formatters.formatBytes(file.sizeBytes),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (file.meta != null) {
                        if (file.meta.durationMs > 0) {
                            Text(Formatters.formatDuration(file.meta.durationMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                        }
                        val res = Formatters.formatResolution(file.meta.width, file.meta.height)
                        if (res.isNotBlank()) {
                            Text(res, style = MaterialTheme.typography.labelSmall, color = OledColors.TextSecondary)
                        }
                        if (!compact && file.meta.videoCodec.isNotBlank()) {
                            Text(file.meta.videoCodec.uppercase(), style = MaterialTheme.typography.labelSmall, color = OledColors.TextSecondary)
                        }
                    }
                    if (prefs.showModifiedDate) {
                        val dt = Formatters.formatShortDate(file.modifiedMs)
                        if (dt.isNotBlank()) {
                            Text(dt, style = MaterialTheme.typography.labelSmall, color = OledColors.TextSecondary)
                        }
                    }
                }
                LinearProgressIndicator(
                    progress = { sizeFrac.coerceAtLeast(0.02f) },
                    modifier = Modifier.fillMaxWidth().height(if (compact) 3.dp else 4.dp).clip(RoundedCornerShape(50)),
                    color = if (sizeFrac > 0.65f) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    trackColor = OledColors.ElevatedSurface,
                )
            }

            if (file.isVideo) {
                Surface(
                    onClick = onQuickTrim,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    ) {
                        Icon(Icons.Rounded.ContentCut, contentDescription = "Cut", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                        if (!compact) {
                            Text("Cut", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
                if (!compact) {
                    IconButton(onClick = onClick) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = OledColors.TextPrimary)
                    }
                }
            }
            IconButton(onClick = onMoreActions) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Options", tint = OledColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun CustomizeViewSheetContent(
    prefs: UserPreferences,
    onUpdatePrefs: ((UserPreferences) -> UserPreferences) -> Unit,
    onOpenAllSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Quick Customize", style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(
                onClick = onOpenAllSettings,
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("All Settings")
            }
        }

        // Columns slider
        Column {
            val colLabel = if (prefs.gridColumns <= 0) "Auto (Adaptive)" else "${prefs.gridColumns} Columns"
            Text("Gallery Grid Columns: $colLabel", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Slider(
                value = prefs.gridColumns.toFloat(),
                onValueChange = { v -> onUpdatePrefs { it.copy(gridColumns = v.roundToInt()) } },
                valueRange = 0f..8f,
                steps = 7,
            )
        }

        // Aspect Ratio
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Thumbnail Aspect Ratio", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CardAspect.entries.forEach { aspect ->
                    val sel = prefs.cardAspect == aspect
                    FilterChip(
                        selected = sel,
                        onClick = { onUpdatePrefs { it.copy(cardAspect = aspect) } },
                        label = { Text(aspect.label) },
                    )
                }
            }
        }

        // Material 3 Palette + Dark Surface Style
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Material Theme", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                AccentPalette.entries.forEach { palette ->
                    FilterChip(
                        selected = prefs.accentPalette == palette,
                        onClick = { onUpdatePrefs { it.copy(accentPalette = palette) } },
                        label = { Text(palette.label) },
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Surface Style", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
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
                        label = { Text(style.label) },
                    )
                }
            }
        }

        // Toggles
        ToggleRow("Compact List Density", prefs.compactListMode) { v ->
            onUpdatePrefs { it.copy(compactListMode = v) }
        }
        ToggleRow("Video Scrub Previews (S Pen / Drag)", prefs.enableHoverScrub) { v ->
            onUpdatePrefs { it.copy(enableHoverScrub = v) }
        }
        ToggleRow("Show File Size Badge", prefs.showFileSize) { v ->
            onUpdatePrefs { it.copy(showFileSize = v) }
        }
        ToggleRow("Show Duration & Resolution Badges", prefs.showDuration) { v ->
            onUpdatePrefs { it.copy(showDuration = v, showResolutionBadge = v) }
        }
        ToggleRow("Show Filename Under Cards", prefs.showFileName) { v ->
            onUpdatePrefs { it.copy(showFileName = v) }
        }

        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun ContextActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val surfaces = LocalDeckSurfaces.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = surfaces.cardSurface,
        border = BorderStroke(1.dp, surfaces.hairlineBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, color = surfaces.textPrimary)
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    val surfaces = LocalDeckSurfaces.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelMedium,
            color = surfaces.textSecondary,
            modifier = Modifier.width(88.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = surfaces.textPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = OledColors.TextPrimary)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyStateBox(
    title: String,
    subtitle: String,
    showQuickActions: Boolean = false,
    isCompactPhone: Boolean = false,
    hasActiveFilters: Boolean = false,
    quickFolders: List<QuickFolder> = emptyList(),
    onOpenFolders: () -> Unit = {},
    onOpenAllDrives: () -> Unit = {},
    onClearFilters: () -> Unit = {},
    onSelectFolder: (String) -> Unit = {},
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = OledColors.TextPrimary)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = OledColors.TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (showQuickActions) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (hasActiveFilters) {
                        Button(onClick = onClearFilters, shape = RoundedCornerShape(12.dp)) {
                            Text("Clear Filters")
                        }
                    }
                    OutlinedButton(onClick = onOpenFolders, shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Rounded.FolderOpen, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isCompactPhone) "Browse Folders" else "Folders")
                    }
                    OutlinedButton(onClick = onOpenAllDrives, shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Rounded.Storage, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("All Drives")
                    }
                }
            }
            if (quickFolders.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    quickFolders.take(6).forEach { qf ->
                        FilterChip(
                            selected = false,
                            onClick = { onSelectFolder(qf.path) },
                            leadingIcon = { Icon(Icons.Rounded.Folder, null, Modifier.size(14.dp)) },
                            label = { Text(qf.name, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewModePillSwitcher(
    selected: ViewMode,
    onSelect: (ViewMode) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = OledColors.CardSurface,
        border = BorderStroke(1.dp, OledColors.HairlineBorder),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(2.dp),
        ) {
            ViewMode.entries.forEach { mode ->
                val isSel = mode == selected
                Surface(
                    onClick = { onSelect(mode) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                ) {
                    Icon(
                        imageVector = if (mode == ViewMode.GALLERY) Icons.Rounded.GridView else Icons.AutoMirrored.Rounded.ViewList,
                        contentDescription = mode.label,
                        tint = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else OledColors.TextSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp).size(18.dp),
                    )
                }
            }
        }
    }
}
