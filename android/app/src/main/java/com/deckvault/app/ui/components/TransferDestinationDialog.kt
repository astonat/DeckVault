package com.deckvault.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.deckvault.app.model.DriveInfo
import com.deckvault.app.model.FileItem
import com.deckvault.app.model.FolderItem
import com.deckvault.app.model.ListResponse
import com.deckvault.app.model.QuickFolder
import com.deckvault.app.model.RootsResponse
import com.deckvault.app.model.SavedHost
import com.deckvault.app.ui.theme.LocalDeckSurfaces
import com.deckvault.app.ui.util.Formatters
import kotlinx.coroutines.launch

@Composable
fun TransferDestinationDialog(
    items: List<FileItem>,
    initialMove: Boolean,
    sourceHost: SavedHost,
    savedHosts: List<SavedHost>,
    initialPath: String,
    fetchRoots: suspend (SavedHost) -> RootsResponse,
    fetchFolders: suspend (SavedHost, String) -> ListResponse,
    onCreateFolder: suspend (SavedHost, String, String) -> String,
    onTransferToRemoteHost: (targetHost: SavedHost, destDir: String, move: Boolean) -> Unit,
    onTransferToLocalDevice: (move: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val surfaces = LocalDeckSurfaces.current
    val scope = rememberCoroutineScope()

    var isMove by remember { mutableStateOf(initialMove) }
    var targetIsLocalDevice by remember { mutableStateOf(false) }
    var selectedHost by remember { mutableStateOf(sourceHost) }

    var drives by remember { mutableStateOf<List<DriveInfo>>(emptyList()) }
    var quickFolders by remember { mutableStateOf<List<QuickFolder>>(emptyList()) }
    var currentDestPath by remember { mutableStateOf(initialPath) }
    var parentDestPath by remember { mutableStateOf("") }
    var subFolders by remember { mutableStateOf<List<FolderItem>>(emptyList()) }
    var isLoadingDest by remember { mutableStateOf(false) }
    var destError by remember { mutableStateOf<String?>(null) }
    var showNewFolderPrompt by remember { mutableStateOf(false) }

    val totalBytes = remember(items) { items.sumOf { it.sizeBytes } }

    fun loadTargetHostFolder(host: SavedHost, explicitPath: String? = null) {
        scope.launch {
            isLoadingDest = true
            destError = null
            runCatching {
                val roots = fetchRoots(host)
                val targetPath = explicitPath?.takeIf { it.isNotBlank() }
                    ?: if (host.hostId == sourceHost.hostId && initialPath.isNotBlank() && !initialPath.startsWith("mediastore://")) initialPath
                    else roots.quickFolders.firstOrNull { !it.path.startsWith("mediastore://") && (it.name.contains("Videos", true) || it.name.contains("Movies", true) || it.name.contains("SD", true)) }?.path
                        ?: roots.drives.firstOrNull()?.path.orEmpty()
                val listed = if (targetPath.isNotBlank()) fetchFolders(host, targetPath) else null
                Triple(roots, targetPath, listed)
            }.onSuccess { (roots, targetPath, listed) ->
                drives = roots.drives
                quickFolders = roots.quickFolders
                currentDestPath = listed?.path ?: targetPath
                parentDestPath = listed?.parentPath ?: ""
                subFolders = listed?.folders ?: emptyList()
                isLoadingDest = false
            }.onFailure { err ->
                destError = err.message ?: "Could not load folders on ${host.displayName}"
                isLoadingDest = false
            }
        }
    }

    LaunchedEffect(selectedHost.hostId, targetIsLocalDevice) {
        if (!targetIsLocalDevice) {
            loadTargetHostFolder(selectedHost, if (selectedHost.hostId == sourceHost.hostId) currentDestPath else null)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.86f),
            shape = RoundedCornerShape(22.dp),
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
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${if (isMove) "Move" else "Copy"} ${items.size} item(s)",
                            style = MaterialTheme.typography.titleLarge,
                            color = surfaces.textPrimary,
                        )
                        Text(
                            text = "From ${sourceHost.displayName} • ${Formatters.formatBytes(totalBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = surfaces.textSecondary,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = surfaces.textSecondary)
                    }
                }

                // Mode Switch (Move vs Copy)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 18.dp),
                ) {
                    FilterChip(
                        selected = isMove,
                        onClick = { isMove = true },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Move (transfer & remove original)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                    FilterChip(
                        selected = !isMove,
                        onClick = { isMove = false },
                        leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Copy") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Device Target Selector (PCs, Laptops, or This Galaxy Device)
                Text(
                    text = "Destination Device",
                    style = MaterialTheme.typography.labelLarge,
                    color = surfaces.textSecondary,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
                val quickTargetLabel = if (sourceHost.isLocalDevice) "" else "Quick Save (Movies/Pictures)"
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                ) {
                    savedHosts.forEach { host ->
                        val selected = !targetIsLocalDevice && selectedHost.hostId == host.hostId
                        val isLaptop = host.displayName.contains("laptop", ignoreCase = true)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                targetIsLocalDevice = false
                                selectedHost = host
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = when {
                                        host.isLocalDevice -> Icons.Rounded.PhoneAndroid
                                        isLaptop -> Icons.Rounded.Laptop
                                        else -> Icons.Rounded.Computer
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            label = {
                                Text(
                                    text = when {
                                        host.isLocalDevice && sourceHost.isLocalDevice -> "This Device (SD / Internal)"
                                        host.hostId == sourceHost.hostId -> "${host.displayName} (Same Host)"
                                        else -> host.displayName
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                        )
                    }

                    if (quickTargetLabel.isNotBlank()) {
                        FilterChip(
                            selected = targetIsLocalDevice,
                            onClick = { targetIsLocalDevice = true },
                            leadingIcon = { Icon(Icons.Rounded.Storage, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text(quickTargetLabel, style = MaterialTheme.typography.labelLarge) },
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }

                HorizontalDivider(color = surfaces.hairlineBorder)

                if (targetIsLocalDevice) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = surfaces.cardSurface,
                            border = BorderStroke(1.dp, surfaces.hairlineBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "Save to Local Galaxy Storage",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = surfaces.textPrimary,
                                )
                                Text(
                                    text = "• Videos -> Movies/DeckVault\n• Photos -> Pictures/DeckVault\n• Other files -> Downloads/DeckVault",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = surfaces.textSecondary,
                                )
                                Text(
                                    text = if (isMove) {
                                        "After verifying each download completes, the original on ${sourceHost.displayName} will be moved to its Recycle Bin."
                                    } else {
                                        "Files will be copied to your device and originals stay untouched on ${sourceHost.displayName}."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }
                } else {
                    Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        // Drives & Quick Folders on target Windows PC/Laptop
                        if (drives.isNotEmpty() || quickFolders.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 18.dp, vertical = 8.dp),
                            ) {
                                drives.forEach { d ->
                                    val active = currentDestPath.startsWith(d.path, ignoreCase = true)
                                    Surface(
                                        onClick = { loadTargetHostFolder(selectedHost, d.path) },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else surfaces.cardSurface,
                                        border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary else surfaces.hairlineBorder),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        ) {
                                            Icon(Icons.Rounded.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                            Text(d.name, style = MaterialTheme.typography.labelMedium, color = surfaces.textPrimary)
                                            Text(
                                                "${Formatters.formatBytes(d.freeBytes)} free",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = surfaces.textSecondary,
                                            )
                                        }
                                    }
                                }
                                quickFolders.forEach { qf ->
                                    Surface(
                                        onClick = { loadTargetHostFolder(selectedHost, qf.path) },
                                        shape = RoundedCornerShape(10.dp),
                                        color = surfaces.cardSurface,
                                        border = BorderStroke(1.dp, surfaces.hairlineBorder),
                                    ) {
                                        Text(
                                            qf.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = surfaces.textPrimary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        )
                                    }
                                }
                            }
                        }

                        // Path Breadcrumb + Up + New Folder
                        Surface(
                            color = surfaces.cardSurface,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, surfaces.hairlineBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                IconButton(
                                    onClick = {
                                        if (parentDestPath.isNotBlank() && parentDestPath != currentDestPath) {
                                            loadTargetHostFolder(selectedHost, parentDestPath)
                                        }
                                    },
                                    enabled = parentDestPath.isNotBlank() && parentDestPath != currentDestPath,
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "Up",
                                        tint = if (parentDestPath.isNotBlank() && parentDestPath != currentDestPath) {
                                            MaterialTheme.colorScheme.primary
                                        } else surfaces.textSecondary.copy(alpha = 0.35f),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Text(
                                    text = currentDestPath.ifBlank { "Pick a folder" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = surfaces.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = { showNewFolderPrompt = true },
                                    enabled = currentDestPath.isNotBlank(),
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.CreateNewFolder,
                                        contentDescription = "New Folder",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        when {
                            isLoadingDest -> {
                                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                            destError != null -> {
                                Box(Modifier.fillMaxWidth().weight(1f).padding(20.dp), contentAlignment = Alignment.Center) {
                                    Text(destError ?: "", color = MaterialTheme.colorScheme.error)
                                }
                            }
                            subFolders.isEmpty() -> {
                                Box(Modifier.fillMaxWidth().weight(1f).padding(20.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        "No subfolders here — tap ${if (isMove) "Move Here" else "Copy Here"} below to place files in this folder.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = surfaces.textSecondary,
                                    )
                                }
                            }
                            else -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 18.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    items(subFolders, key = { it.path }) { folder ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = surfaces.cardSurface,
                                            border = BorderStroke(1.dp, surfaces.hairlineBorder),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable { loadTargetHostFolder(selectedHost, folder.path) },
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            ) {
                                                Icon(Icons.Rounded.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                                Text(
                                                    folder.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = surfaces.textPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f),
                                                )
                                                if (folder.sizeBytes >= 0) {
                                                    Text(Formatters.formatBytes(folder.sizeBytes), style = MaterialTheme.typography.labelSmall, color = surfaces.textSecondary)
                                                }
                                                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = surfaces.textSecondary, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = surfaces.hairlineBorder)

                // Footer Action Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = targetIsLocalDevice || (currentDestPath.isNotBlank() && !isLoadingDest),
                        shape = RoundedCornerShape(14.dp),
                        onClick = {
                            if (targetIsLocalDevice) {
                                onTransferToLocalDevice(isMove)
                            } else {
                                onTransferToRemoteHost(selectedHost, currentDestPath, isMove)
                            }
                        },
                    ) {
                        Icon(
                            imageVector = if (isMove) Icons.AutoMirrored.Rounded.DriveFileMove else Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        val btnText = when {
                            targetIsLocalDevice && isMove -> "Move to This Device"
                            targetIsLocalDevice -> "Save Copy to This Device"
                            isMove -> "Move Here"
                            else -> "Copy Here"
                        }
                        Text(btnText, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (showNewFolderPrompt) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolderPrompt = false },
            containerColor = surfaces.elevatedSurface,
            title = { Text("Create Subfolder", color = surfaces.textPrimary) },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    singleLine = true,
                    label = { Text("Folder name") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = folderName.trim()
                        showNewFolderPrompt = false
                        if (name.isNotBlank()) {
                            scope.launch {
                                runCatching {
                                    onCreateFolder(selectedHost, currentDestPath, name)
                                }.onSuccess { newPath ->
                                    loadTargetHostFolder(selectedHost, newPath)
                                }.onFailure { err ->
                                    destError = err.message
                                }
                            }
                        }
                    },
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderPrompt = false }) { Text("Cancel") }
            },
        )
    }
}
