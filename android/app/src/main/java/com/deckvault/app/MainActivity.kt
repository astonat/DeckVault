package com.deckvault.app

import android.Manifest
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Queue
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okio.Path.Companion.toOkioPath
import java.io.File
import com.deckvault.app.data.DeckVaultLocalFetcherFactory
import com.deckvault.app.model.FoldLayoutMode
import com.deckvault.app.peer.DeckVaultPeerService
import com.deckvault.app.ui.components.AllDrivesDashboard
import com.deckvault.app.ui.components.AnyFileViewerOverlay
import com.deckvault.app.ui.components.AudioPlayerOverlay
import com.deckvault.app.ui.components.FloatingQueueMiniBar
import com.deckvault.app.ui.components.FolderTreePane
import com.deckvault.app.ui.components.HostsModalDialog
import com.deckvault.app.ui.components.ImageViewerOverlay
import com.deckvault.app.ui.components.MediaBrowserPane
import com.deckvault.app.ui.components.MediaOptimizerDialog
import com.deckvault.app.ui.components.MergeVideosDialog
import com.deckvault.app.ui.components.PdfViewerOverlay
import com.deckvault.app.ui.components.QueueManagerDialog
import com.deckvault.app.ui.components.SettingsDialog
import com.deckvault.app.ui.components.TextViewerOverlay
import com.deckvault.app.ui.components.TransferDestinationDialog
import com.deckvault.app.ui.components.VideoPlayerAndTrimmerOverlay
import com.deckvault.app.ui.theme.DeckVaultTheme
import com.deckvault.app.ui.theme.OledColors
import com.deckvault.app.ui.util.Formatters
import com.deckvault.app.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var externalLaunchUri by mutableStateOf<Uri?>(null)

    private fun extractViewUri(incoming: Intent?): Uri? {
        if (incoming?.action == Intent.ACTION_VIEW) return incoming.data
        return null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        externalLaunchUri = extractViewUri(intent)
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.BLACK),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.BLACK),
        )
        super.onCreate(savedInstanceState)
        externalLaunchUri = extractViewUri(intent)

        setContent {
            val vm: MainViewModel = viewModel()

            LaunchedEffect(externalLaunchUri) {
                val uri = externalLaunchUri
                if (uri != null) {
                    externalLaunchUri = null
                    vm.openExternalViewUri(uri)
                }
            }

            val mediaPermLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions(),
            ) {
                vm.onPermissionChanged()
            }

            LaunchedEffect(Unit) {
                if (!vm.localRepo.hasMediaReadAccess()) {
                    val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        arrayOf(
                            Manifest.permission.READ_MEDIA_VIDEO,
                            Manifest.permission.READ_MEDIA_IMAGES,
                            Manifest.permission.READ_MEDIA_AUDIO,
                        )
                    } else {
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                    mediaPermLauncher.launch(perms)
                }
            }

            // Wire Coil 3 to Local Direct Thumbnail Fetcher + Tuned OkHttp + Fast Memory/Disk Cache
            LaunchedEffect(Unit) {
                SingletonImageLoader.setSafe { ctx ->
                    ImageLoader.Builder(ctx)
                        .components {
                            add(DeckVaultLocalFetcherFactory(vm.localRepo))
                            add(OkHttpNetworkFetcherFactory(callFactory = { vm.api.okHttpClient }))
                        }
                        .memoryCache {
                            MemoryCache.Builder()
                                .maxSizePercent(ctx, 0.22)
                                .build()
                        }
                        .diskCache {
                            DiskCache.Builder()
                                .directory(File(ctx.cacheDir, "coil_image_cache").toOkioPath())
                                .maxSizeBytes(160L * 1024 * 1024)
                                .build()
                        }
                        .crossfade(100)
                        .build()
                }
            }

            val windowSizeClass = calculateWindowSizeClass(this)
            val layoutInfo by WindowInfoTracker.getOrCreate(this)
                .windowLayoutInfo(this)
                .collectAsState(initial = WindowLayoutInfo(emptyList()))

            val prefs by vm.preferences.collectAsState()

            DeckVaultTheme(
                accentPalette = prefs.accentPalette,
                pureBlackBackground = prefs.pureBlackBackground,
                surfaceStyle = prefs.surfaceStyle,
            ) {
                DeckVaultRoot(
                    vm = vm,
                    widthSizeClass = windowSizeClass.widthSizeClass,
                    layoutInfo = layoutInfo,
                )
            }
        }
    }
}

@Composable
private fun DeckVaultRoot(
    vm: MainViewModel,
    widthSizeClass: WindowWidthSizeClass,
    layoutInfo: WindowLayoutInfo,
) {
    val prefs by vm.preferences.collectAsState()
    val ui by vm.ui.collectAsState()
    val peerStatus by DeckVaultPeerService.status.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(prefs.deviceShareEnabled) {
        if (prefs.deviceShareEnabled) {
            DeckVaultPeerService.start(context.applicationContext)
        } else {
            DeckVaultPeerService.stop(context.applicationContext)
        }
    }

    val activeHost = remember(prefs.hosts, prefs.activeHostId) {
        prefs.hosts.firstOrNull { it.hostId == prefs.activeHostId } ?: prefs.hosts.firstOrNull()
    }

    val uploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            vm.uploadDeviceUrisToCurrentFolder(uris)
        }
    }

    val allFilesPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        vm.onPermissionChanged()
    }

    val openAllFilesGrantScreen: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = runCatching {
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                )
            }.getOrElse { Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION) }
            allFilesPermissionLauncher.launch(intent)
        }
    }

    val sdCardTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        val pending = ui.pendingSdCardAccess
        if (uri != null && pending != null) {
            runCatching {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            }
            vm.onSdCardTreeGranted(pending.first, uri)
        } else {
            vm.dismissSdCardAccessPrompt()
        }
    }

    LaunchedEffect(ui.toastMessage) {
        val msg = ui.toastMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            vm.consumeToast()
        }
    }

    LaunchedEffect(ui.errorMessage) {
        val err = ui.errorMessage
        if (!err.isNullOrBlank()) {
            snackbarHostState.showSnackbar(err)
            vm.clearError()
        }
    }

    val isExpandedTablet = widthSizeClass == WindowWidthSizeClass.Expanded
    val isCompactScreen = widthSizeClass == WindowWidthSizeClass.Compact
    val useSplitPane = when (prefs.foldLayoutMode) {
        FoldLayoutMode.SPLIT -> !isCompactScreen
        FoldLayoutMode.DRAWER -> false
        FoldLayoutMode.AUTO -> !isCompactScreen
    }
    val showNavigationRail = isExpandedTablet && useSplitPane

    val foldingFeature = layoutInfo.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
    var folderPaneCollapsed by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    // Back handling for viewers / dashboard / drawer / modals
    BackHandler(
        enabled = ui.activeVideoFile != null ||
            ui.activeImageIndex >= 0 ||
            ui.activeAudioFile != null ||
            ui.activePdfFile != null ||
            ui.activeTextFile != null ||
            ui.activeOtherFile != null ||
            ui.showQueueSheet ||
            ui.optimizerFile != null ||
            ui.mergeCandidates.isNotEmpty() ||
            ui.showAllDrivesDashboard ||
            (!useSplitPane && drawerState.isOpen),
    ) {
        when {
            ui.activeVideoFile != null -> vm.dismissVideoPlayer()
            ui.activeImageIndex >= 0 -> vm.dismissImageViewer()
            ui.activeAudioFile != null -> vm.dismissAudioPlayer()
            ui.activePdfFile != null -> vm.dismissPdfViewer()
            ui.activeTextFile != null -> vm.dismissTextViewer()
            ui.activeOtherFile != null -> vm.dismissOtherFileViewer()
            ui.showQueueSheet -> vm.closeQueueSheet()
            ui.optimizerFile != null -> vm.closeOptimizer()
            ui.mergeCandidates.isNotEmpty() -> vm.closeMergeDialog()
            ui.showAllDrivesDashboard -> vm.closeAllDrivesDashboard()
            !useSplitPane && drawerState.isOpen -> scope.launch { drawerState.close() }
        }
    }

    Scaffold(
        containerColor = OledColors.PitchBlack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (useSplitPane) {
                // Tab S8 Ultra (Expanded): Slim Rail + Resizable Folder Panel + Media Pane
                // Z Fold 5 Unfolded (Medium): Direct 2-Pane Split (no rail crowding!) + 1-tap Collapse
                Row(modifier = Modifier.fillMaxSize()) {
                    if (showNavigationRail) {
                        val activeQueueCount = ui.queueJobs.count { it.status in listOf("queued", "uploading", "running", "downloading") }
                        ExpressiveSideRail(
                            savedHosts = prefs.hosts,
                            activeHost = activeHost,
                            isSpaceHogsMode = ui.isSpaceHogsMode,
                            showAllDrivesDashboard = ui.showAllDrivesDashboard,
                            activeQueueCount = activeQueueCount,
                            spaceSavedBytes = ui.spaceSavedBytes,
                            folderPaneCollapsed = folderPaneCollapsed,
                            onToggleCollapseFolders = { folderPaneCollapsed = !folderPaneCollapsed },
                            onSelectHost = { vm.selectHost(it) },
                            onOpenAllDrives = { vm.openAllDrivesDashboard() },
                            onOpenQueue = { vm.openQueueSheet() },
                            onOpenHostManager = { vm.openHostsModal() },
                            onOpenSettings = { vm.openSettingsModal() },
                            onScanSpaceHogs = { vm.scanLargestInCurrentFolder() },
                        )
                    }

                    BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        val containerMaxWidth = maxWidth
                        val density = LocalDensity.current
                        val totalWidthPx = with(density) { containerMaxWidth.toPx().coerceAtLeast(1f) }
                        val railPx = if (showNavigationRail) with(density) { 68.dp.toPx() } else 0f

                        // Respect Z Fold 5 vertical hinge if book-mode separating
                        val hingeWidthFraction = if (
                            foldingFeature != null &&
                            foldingFeature.orientation == FoldingFeature.Orientation.VERTICAL &&
                            foldingFeature.isSeparating
                        ) {
                            ((foldingFeature.bounds.left.toFloat() - railPx) / totalWidthPx).coerceIn(0.32f, 0.50f)
                        } else null

                        val defaultFraction = if (!isExpandedTablet) {
                            prefs.folderPaneWidthFraction.coerceIn(0.34f, 0.46f)
                        } else {
                            prefs.folderPaneWidthFraction.coerceIn(0.24f, 0.46f)
                        }
                        var dragFraction by remember(prefs.folderPaneWidthFraction, isExpandedTablet) {
                            mutableFloatStateOf(defaultFraction)
                        }
                        val effectiveFraction = hingeWidthFraction ?: dragFraction

                        Row(modifier = Modifier.fillMaxSize()) {
                            AnimatedVisibility(visible = !folderPaneCollapsed) {
                                Row(modifier = Modifier.fillMaxHeight()) {
                                    FolderTreePane(
                                        activeHost = activeHost,
                                        savedHosts = prefs.hosts,
                                        drives = ui.drives,
                                        quickFolders = ui.quickFolders,
                                        currentPath = ui.currentPath,
                                        parentPath = ui.parentPath,
                                        currentFolderSizeBytes = ui.currentFolderSizeBytes,
                                        isIndexing = ui.isIndexing,
                                        treeFolders = ui.treeFolders,
                                        treeSortBySize = ui.treeSortBySize,
                                        spaceSavedBytes = ui.spaceSavedBytes,
                                        showDriveGauges = prefs.showDriveGaugesInPane,
                                        showQuickFolders = prefs.showQuickFoldersInPane,
                                        showSubfolderCounts = prefs.showSubfolderCounts,
                                        showCollapseButton = true,
                                        onCollapsePane = { folderPaneCollapsed = true },
                                        onSelectHost = { vm.selectHost(it) },
                                        onOpenHostManager = { vm.openHostsModal() },
                                        onOpenAllDrives = { vm.openAllDrivesDashboard() },
                                        onOpenSettings = { vm.openSettingsModal() },
                                        onNavigateFolder = { vm.navigateFolder(it) },
                                        onToggleTreeSort = { vm.toggleTreeSort() },
                                        onRescanDisks = { vm.rescanDisks() },
                                        onScanLargestInFolder = { vm.scanLargestInCurrentFolder() },
                                        modifier = Modifier.width(containerMaxWidth * effectiveFraction),
                                    )

                                    // Draggable Matte Pane Resize Divider
                                    Box(
                                        modifier = Modifier
                                            .width(10.dp)
                                            .fillMaxHeight()
                                            .pointerInput(totalWidthPx) {
                                                detectDragGestures(
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        dragFraction = (dragFraction + dragAmount.x / totalWidthPx)
                                                            .coerceIn(0.24f, 0.48f)
                                                    },
                                                    onDragEnd = {
                                                        vm.updatePreferences { it.copy(folderPaneWidthFraction = dragFraction) }
                                                    },
                                                )
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(3.dp)
                                                .height(40.dp)
                                                .background(OledColors.HairlineBorder, CircleShape),
                                        )
                                    }
                                }
                            }

                            MediaBrowserPane(
                                host = activeHost,
                                api = vm.api,
                                folderName = ui.folderName,
                                currentPath = ui.currentPath,
                                totalFolderSizeBytes = ui.currentFolderSizeBytes,
                                folders = ui.listFolders,
                                files = ui.files,
                                isLoading = ui.isLoading,
                                isSpaceHogsMode = ui.isSpaceHogsMode,
                                searchQuery = ui.searchQuery,
                                recursiveSearch = ui.recursiveSearch,
                                prefs = prefs,
                                selectedPaths = ui.selectedPaths,
                                cacheVersion = ui.cacheVersion,
                                showOpenFoldersButton = folderPaneCollapsed,
                                onOpenFoldersDrawer = { folderPaneCollapsed = false },
                                onExitSpaceHogs = { vm.exitSpaceHogs() },
                                onSearchQueryChange = { vm.onSearchQueryChanged(it) },
                                onToggleRecursiveSearch = { vm.toggleRecursiveSearch() },
                                onSelectFolder = { vm.navigateFolder(it) },
                                onOpenVideo = { file, trim -> vm.openVideo(file, trim) },
                                onOpenImage = { file -> vm.openImage(file) },
                                onOpenFile = { file -> vm.openFile(file) },
                                onOpenAllDrives = { vm.openAllDrivesDashboard() },
                                onToggleSelect = { vm.toggleSelect(it) },
                                onSelectAll = { vm.selectAllFiles() },
                                onClearSelection = { vm.clearSelection() },
                                onDeleteSelected = { recycle -> vm.deleteSelected(recycle) },
                                onMoveSelected = { vm.openTransferDialogForSelection(move = true) },
                                onCopySelected = { vm.openTransferDialogForSelection(move = false) },
                                onMoveFile = { file -> vm.openTransferDialogForFile(file, move = true) },
                                onCopyFile = { file -> vm.openTransferDialogForFile(file, move = false) },
                                onUploadFromDevice = { uploadLauncher.launch(arrayOf("*/*")) },
                                onRenameFile = { file, name -> vm.renameFile(file, name) },
                                onOpenSettings = { vm.openSettingsModal() },
                                onUpdatePrefs = { vm.updatePreferences(it) },
                                needsAllFilesPermission = ui.needsAllFilesPermission,
                                savedHosts = prefs.hosts,
                                quickFolders = ui.quickFolders,
                                onSelectHost = { vm.selectHost(it) },
                                onOpenHostManager = { vm.openHostsModal() },
                                onGrantAllFilesAccess = openAllFilesGrantScreen,
                                onOptimizeFile = { file, tab -> vm.openOptimizer(file, tab) },
                                onDeleteSingleFile = { file, recycle -> vm.deleteSingleFileWithMode(file, recycle) },
                                onMergeSelected = { vm.openMergeForSelection() },
                                activeQueueCount = ui.queueJobs.count { it.status in listOf("queued", "uploading", "running", "downloading") },
                                onOpenQueue = { vm.openQueueSheet() },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
            } else {
                // Z Fold 5 Cover Screen or Drawer Mode:
                // Slide Drawer for Folder Panel + Full-width Right Media Pane
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet(
                            drawerContainerColor = OledColors.PanelSurface,
                            modifier = Modifier.fillMaxWidth(if (isCompactScreen) 0.86f else 0.56f),
                        ) {
                            FolderTreePane(
                                activeHost = activeHost,
                                savedHosts = prefs.hosts,
                                drives = ui.drives,
                                quickFolders = ui.quickFolders,
                                currentPath = ui.currentPath,
                                parentPath = ui.parentPath,
                                currentFolderSizeBytes = ui.currentFolderSizeBytes,
                                isIndexing = ui.isIndexing,
                                treeFolders = ui.treeFolders,
                                treeSortBySize = ui.treeSortBySize,
                                spaceSavedBytes = ui.spaceSavedBytes,
                                showDriveGauges = prefs.showDriveGaugesInPane,
                                showQuickFolders = prefs.showQuickFoldersInPane,
                                showSubfolderCounts = prefs.showSubfolderCounts,
                                showCollapseButton = true,
                                onCollapsePane = { scope.launch { drawerState.close() } },
                                onSelectHost = { vm.selectHost(it) },
                                onOpenHostManager = {
                                    scope.launch { drawerState.close() }
                                    vm.openHostsModal()
                                },
                                onOpenAllDrives = {
                                    scope.launch { drawerState.close() }
                                    vm.openAllDrivesDashboard()
                                },
                                onOpenSettings = {
                                    scope.launch { drawerState.close() }
                                    vm.openSettingsModal()
                                },
                                onNavigateFolder = { path ->
                                    vm.navigateFolder(path)
                                    scope.launch { drawerState.close() }
                                },
                                onToggleTreeSort = { vm.toggleTreeSort() },
                                onRescanDisks = { vm.rescanDisks() },
                                onScanLargestInFolder = {
                                    vm.scanLargestInCurrentFolder()
                                    scope.launch { drawerState.close() }
                                },
                            )
                        }
                    },
                ) {
                    MediaBrowserPane(
                        host = activeHost,
                        api = vm.api,
                        folderName = ui.folderName,
                        currentPath = ui.currentPath,
                        totalFolderSizeBytes = ui.currentFolderSizeBytes,
                        folders = ui.listFolders,
                        files = ui.files,
                        isLoading = ui.isLoading,
                        isSpaceHogsMode = ui.isSpaceHogsMode,
                        searchQuery = ui.searchQuery,
                        recursiveSearch = ui.recursiveSearch,
                        prefs = prefs,
                        selectedPaths = ui.selectedPaths,
                        cacheVersion = ui.cacheVersion,
                        showOpenFoldersButton = true,
                        onOpenFoldersDrawer = { scope.launch { drawerState.open() } },
                        onExitSpaceHogs = { vm.exitSpaceHogs() },
                        onSearchQueryChange = { vm.onSearchQueryChanged(it) },
                        onToggleRecursiveSearch = { vm.toggleRecursiveSearch() },
                        onSelectFolder = { vm.navigateFolder(it) },
                        onOpenVideo = { file, trim -> vm.openVideo(file, trim) },
                        onOpenImage = { file -> vm.openImage(file) },
                        onOpenFile = { file -> vm.openFile(file) },
                        onOpenAllDrives = { vm.openAllDrivesDashboard() },
                        onToggleSelect = { vm.toggleSelect(it) },
                        onSelectAll = { vm.selectAllFiles() },
                        onClearSelection = { vm.clearSelection() },
                        onDeleteSelected = { recycle -> vm.deleteSelected(recycle) },
                        onMoveSelected = { vm.openTransferDialogForSelection(move = true) },
                        onCopySelected = { vm.openTransferDialogForSelection(move = false) },
                        onMoveFile = { file -> vm.openTransferDialogForFile(file, move = true) },
                        onCopyFile = { file -> vm.openTransferDialogForFile(file, move = false) },
                        onUploadFromDevice = { uploadLauncher.launch(arrayOf("*/*")) },
                        onRenameFile = { file, name -> vm.renameFile(file, name) },
                        onOpenSettings = { vm.openSettingsModal() },
                        onUpdatePrefs = { vm.updatePreferences(it) },
                        needsAllFilesPermission = ui.needsAllFilesPermission,
                        savedHosts = prefs.hosts,
                        quickFolders = ui.quickFolders,
                        onSelectHost = { vm.selectHost(it) },
                        onOpenHostManager = { vm.openHostsModal() },
                        onGrantAllFilesAccess = openAllFilesGrantScreen,
                        onOptimizeFile = { file, tab -> vm.openOptimizer(file, tab) },
                        onDeleteSingleFile = { file, recycle -> vm.deleteSingleFileWithMode(file, recycle) },
                        onMergeSelected = { vm.openMergeForSelection() },
                        activeQueueCount = ui.queueJobs.count { it.status in listOf("queued", "uploading", "running", "downloading") },
                        onOpenQueue = { vm.openQueueSheet() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // Full-Screen Built-in Cinema Video Player & Studio Overlay
            val videoFile = ui.activeVideoFile
            if (videoFile != null && activeHost != null) {
                val videoList = remember(ui.files) { ui.files.filter { it.isVideo } }
                val durMs = (videoFile.meta?.durationMs ?: 0L).coerceAtLeast(1000L)
                val frameUrls = remember(activeHost.hostId, videoFile.path, durMs, ui.cacheVersion) {
                    (0..5).map { idx ->
                        val ms = ((durMs * (idx + 0.5)) / 6.0).toLong()
                        vm.api.videoFrameUrl(activeHost, videoFile.path, ms, width = 320)
                    }
                }
                VideoPlayerAndTrimmerOverlay(
                    file = videoFile,
                    playlist = videoList,
                    streamUrl = vm.api.streamUrl(activeHost, videoFile.path, ui.cacheVersion),
                    playableFallbackUrl = vm.api.playableStreamUrlOrNull(activeHost, videoFile.path, cacheBust = ui.cacheVersion),
                    framePreviewUrls = frameUrls,
                    api = vm.api,
                    isLocalSource = activeHost.isLocalDevice,
                    pairedPcHosts = vm.pairedPcHosts(),
                    defaultOffloadHost = vm.pairedPcHosts().firstOrNull(),
                    initialResumePositionMs = prefs.videoResumePositionsMs[videoFile.path] ?: 0L,
                    initialSpeed = prefs.defaultPlaybackSpeed,
                    seekStepSeconds = prefs.seekStepSeconds,
                    autoPlay = prefs.autoPlayVideos,
                    initialOpenStudio = ui.openPlayerInTrimmer,
                    onSelectVideo = { nextVid -> vm.openVideo(nextVid, false) },
                    onSaveResumePosition = { p, ms -> vm.saveVideoResumePosition(p, ms) },
                    onSaveSnapshot = { p, ms -> vm.saveVideoSnapshot(p, ms) },
                    onOpenOptimizer = { f -> vm.openOptimizer(f) },
                    onSubmitStudioEdit = { req, target, offloadHost, runNow ->
                        vm.submitStudioEdit(req, target, offloadHost, runNow)
                    },
                    onSubmitExtractAudio = { req, target, offloadHost ->
                        vm.submitExtractAudio(req, target, offloadHost)
                    },
                    onSubmitExportGif = { req, offloadHost ->
                        vm.submitExportGif(req, offloadHost)
                    },
                    onClose = { vm.dismissVideoPlayer() },
                )
            }

            // Full-Screen Image Viewer Overlay
            if (ui.activeImageIndex >= 0 && ui.activeImageList.isNotEmpty() && activeHost != null) {
                ImageViewerOverlay(
                    host = activeHost,
                    api = vm.api,
                    images = ui.activeImageList,
                    initialIndex = ui.activeImageIndex,
                    cacheVersion = ui.cacheVersion,
                    onDismiss = { vm.dismissImageViewer() },
                    onDeleteImage = { img -> vm.deleteSingleImage(img) },
                )
            }

            // Full-Screen Built-in Audio Player Overlay
            val audioFile = ui.activeAudioFile
            if (audioFile != null && activeHost != null) {
                val audioList = remember(ui.files) { ui.files.filter { it.isAudio } }
                val audioIdx = remember(audioList, audioFile.path) {
                    audioList.indexOfFirst { it.path == audioFile.path }
                }
                AudioPlayerOverlay(
                    file = audioFile,
                    streamUrl = vm.api.streamUrl(activeHost, audioFile.path, ui.cacheVersion),
                    hasPrevious = audioIdx > 0,
                    hasNext = audioIdx in 0 until (audioList.size - 1),
                    initialSpeed = prefs.defaultPlaybackSpeed,
                    seekStepSeconds = prefs.seekStepSeconds,
                    onPreviousTrack = { vm.openAdjacentAudio(-1) },
                    onNextTrack = { vm.openAdjacentAudio(1) },
                    onSpeedChanged = { spd -> vm.updatePreferences { it.copy(defaultPlaybackSpeed = spd) } },
                    onMoveCopy = { vm.openTransferDialogForFile(audioFile, move = true) },
                    onDelete = { vm.deleteSingleFile(audioFile) },
                    onClose = { vm.dismissAudioPlayer() },
                )
            }

            // Built-in PDF Document Viewer Overlay
            val pdfFile = ui.activePdfFile
            if (pdfFile != null && activeHost != null) {
                PdfViewerOverlay(
                    file = pdfFile,
                    host = activeHost,
                    api = vm.api,
                    onMoveCopy = { vm.openTransferDialogForFile(pdfFile, move = true) },
                    onDelete = { vm.deleteSingleFile(pdfFile) },
                    onClose = { vm.dismissPdfViewer() },
                )
            }

            // Built-in Text / Code / Log Viewer Overlay
            val textFile = ui.activeTextFile
            if (textFile != null && activeHost != null) {
                TextViewerOverlay(
                    file = textFile,
                    host = activeHost,
                    api = vm.api,
                    onMoveCopy = { vm.openTransferDialogForFile(textFile, move = true) },
                    onDelete = { vm.deleteSingleFile(textFile) },
                    onToast = { vm.showToast(it) },
                    onClose = { vm.dismissTextViewer() },
                )
            }

            // Universal File Inspector + System Open With Overlay
            val otherFile = ui.activeOtherFile
            if (otherFile != null && activeHost != null) {
                AnyFileViewerOverlay(
                    file = otherFile,
                    host = activeHost,
                    api = vm.api,
                    onMoveCopy = { vm.openTransferDialogForFile(otherFile, move = true) },
                    onDelete = { vm.deleteSingleFile(otherFile) },
                    onToast = { vm.showToast(it) },
                    onClose = { vm.dismissOtherFileViewer() },
                )
            }

            // Full-Screen All Devices & Drives Dashboard
            if (ui.showAllDrivesDashboard) {
                AllDrivesDashboard(
                    snapshots = ui.allDrivesSnapshots,
                    isRefreshing = ui.isRefreshingAllDrives,
                    activeHostId = activeHost?.hostId,
                    peerStatus = peerStatus,
                    onRefresh = { vm.refreshAllDrives() },
                    onBrowseDrive = { snap, path -> vm.openDriveFromDashboard(snap, path) },
                    onOpenHostsModal = { vm.openHostsModal() },
                    onRotatePin = { vm.rotatePeerPin() },
                    onClose = { vm.closeAllDrivesDashboard() },
                )
            }

            // Hosts Manager & Wi-Fi Discovery Dialog
            if (ui.showHostsModal) {
                HostsModalDialog(
                    savedHosts = prefs.hosts,
                    activeHostId = activeHost?.hostId,
                    discoveredHosts = ui.discoveredHosts,
                    peerStatus = peerStatus,
                    isScanningLan = ui.isScanningLan,
                    isPairing = ui.isPairing,
                    pairingError = ui.pairingError,
                    onScanLan = { vm.scanLan() },
                    onRotatePin = { vm.rotatePeerPin() },
                    onSelectHost = { vm.selectHost(it) },
                    onRemoveHost = { vm.removeHost(it) },
                    onPairHost = { ip, port, pin, label -> vm.pairAndConnect(ip, port, pin, label) },
                    onDismiss = { vm.closeHostsModal() },
                )
            }

            // Comprehensive Settings Modal
            if (ui.showSettingsModal) {
                SettingsDialog(
                    prefs = prefs,
                    activeHost = activeHost,
                    peerStatus = peerStatus,
                    localCacheBytes = ui.localCacheBytes,
                    onUpdatePrefs = { vm.updatePreferences(it) },
                    onRotatePin = { vm.rotatePeerPin() },
                    onOpenHostManager = { vm.openHostsModal() },
                    onRescanDisks = { vm.rescanDisks() },
                    onClearCache = { vm.clearLocalCache() },
                    onDismiss = { vm.closeSettingsModal() },
                )
            }

            // Cross-Folder / Cross-Drive / Cross-Device Move & Copy Picker
            if (ui.pendingTransferFiles.isNotEmpty() && activeHost != null) {
                TransferDestinationDialog(
                    items = ui.pendingTransferFiles,
                    initialMove = ui.pendingTransferMoveDefault,
                    sourceHost = activeHost,
                    savedHosts = prefs.hosts,
                    initialPath = ui.currentPath,
                    fetchRoots = { vm.fetchHostRoots(it) },
                    fetchFolders = { host, path -> vm.fetchHostFolders(host, path) },
                    onCreateFolder = { host, parent, name -> vm.createRemoteFolder(host, parent, name) },
                    onTransferToRemoteHost = { targetHost, destDir, move ->
                        vm.executeTransferToRemoteHost(ui.pendingTransferFiles, targetHost, destDir, move)
                    },
                    onTransferToLocalDevice = { move ->
                        vm.executeTransferToLocalDevice(ui.pendingTransferFiles, move)
                    },
                    onDismiss = { vm.closeTransferDialog() },
                )
            }

            // Media Optimizer (Compress & Upscale) Modal
            ui.optimizerFile?.let { optFile ->
                MediaOptimizerDialog(
                    file = optFile,
                    initialTab = ui.optimizerTab,
                    isLocalSource = activeHost?.isLocalDevice == true,
                    pairedPcHosts = vm.pairedPcHosts(),
                    defaultPcHost = vm.pairedPcHosts().firstOrNull(),
                    estimateFn = { req -> vm.estimateCompression(req) },
                    onSubmitCompress = { req, target, offloadHost, runNow ->
                        vm.submitCompress(req, target, offloadHost, runNow)
                    },
                    onSubmitUpscale = { req, target, offloadHost, runNow ->
                        vm.submitUpscale(req, target, offloadHost, runNow)
                    },
                    onDismiss = { vm.closeOptimizer() },
                )
            }

            // Merge Videos Modal
            if (ui.mergeCandidates.isNotEmpty()) {
                MergeVideosDialog(
                    clips = ui.mergeCandidates,
                    isLocalSource = activeHost?.isLocalDevice == true,
                    pairedPcHosts = vm.pairedPcHosts(),
                    defaultPcHost = vm.pairedPcHosts().firstOrNull(),
                    onSubmitMerge = { clips, outName, target, offloadHost, runNow ->
                        vm.submitMergeVideos(clips, outName, target, offloadHost, runNow)
                    },
                    onDismiss = { vm.closeMergeDialog() },
                )
            }

            // Render & PC Offload Queue Sheet
            if (ui.showQueueSheet) {
                QueueManagerDialog(
                    jobs = ui.queueJobs,
                    onCancelJob = { vm.cancelQueueJob(it) },
                    onClearFinished = { vm.clearFinishedQueueJobs() },
                    onPlayOutput = { vm.playQueueOutput(it) },
                    onOpenInStudio = { vm.openQueueOutputInStudio(it) },
                    onGoToFolder = { vm.goToQueueOutputFolder(it) },
                    onDismiss = { vm.closeQueueSheet() },
                )
            }

            // SD Card / USB DocumentTree Permission Prompt
            ui.pendingSdCardAccess?.let { (_, volumeName) ->
                AlertDialog(
                    onDismissRequest = { vm.dismissSdCardAccessPrompt() },
                    containerColor = OledColors.ElevatedSurface,
                    title = { Text("Enable SD Card Writes", color = OledColors.TextPrimary) },
                    text = {
                        Text(
                            "Android requires one-time folder access to write, move, or delete directly on \"$volumeName\". Pick the root of \"$volumeName\" and tap \"Use this folder\".",
                            color = OledColors.TextSecondary,
                        )
                    },
                    confirmButton = {
                        Button(onClick = { sdCardTreeLauncher.launch(null) }) {
                            Text("Choose SD Card Root")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { vm.dismissSdCardAccessPrompt() }) {
                            Text("Cancel")
                        }
                    },
                )
            }

            // Floating Bottom Queue Mini Bar (when browser visible & items aren't covered by multi-select bar)
            if (ui.queueJobs.isNotEmpty() && ui.selectedPaths.isEmpty() && ui.activeVideoFile == null) {
                FloatingQueueMiniBar(
                    jobs = ui.queueJobs,
                    onOpenQueue = { vm.openQueueSheet() },
                    modifier = Modifier
                        .align(if (isCompactScreen) Alignment.BottomCenter else Alignment.BottomEnd)
                        .padding(
                            start = if (isCompactScreen) 12.dp else 0.dp,
                            end = if (isCompactScreen) 12.dp else 16.dp,
                            bottom = 16.dp,
                        )
                        .fillMaxWidth(if (isCompactScreen) 0.94f else 0.46f),
                )
            }

            // Global Top Activity & Transfer Progress Banner + Window Progress Strip
            val activeQueueItem = ui.queueJobs.firstOrNull { it.status in listOf("uploading", "running", "downloading") }
            val showTopProgress = ui.isTransferring || activeQueueItem != null || ui.isLoading || ui.isIndexing
            if (showTopProgress) {
                if (ui.isTransferring || activeQueueItem != null) {
                    val bannerTitle = if (ui.isTransferring) {
                        ui.transferStatusText.ifBlank { "Transferring..." }
                    } else {
                        "${activeQueueItem!!.title} • ${activeQueueItem.message}"
                    }
                    val frac = if (ui.isTransferring) {
                        ui.transferProgressFraction.coerceIn(0f, 1f)
                    } else {
                        (activeQueueItem!!.progressPct.toFloat() / 100f).coerceIn(0f, 1f)
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = OledColors.HighestSurface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp, start = 16.dp, end = 16.dp)
                            .fillMaxWidth(0.7f),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = bannerTitle,
                                style = MaterialTheme.typography.labelLarge,
                                color = OledColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            LinearProgressIndicator(
                                progress = { frac },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                            )
                        }
                    }
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(2.5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpressiveSideRail(
    savedHosts: List<com.deckvault.app.model.SavedHost>,
    activeHost: com.deckvault.app.model.SavedHost?,
    isSpaceHogsMode: Boolean,
    showAllDrivesDashboard: Boolean,
    activeQueueCount: Int,
    spaceSavedBytes: Long,
    folderPaneCollapsed: Boolean,
    onToggleCollapseFolders: () -> Unit,
    onSelectHost: (com.deckvault.app.model.SavedHost) -> Unit,
    onOpenAllDrives: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenHostManager: () -> Unit,
    onOpenSettings: () -> Unit,
    onScanSpaceHogs: () -> Unit,
) {
    NavigationRail(
        containerColor = OledColors.PitchBlack,
        contentColor = OledColors.TextPrimary,
        header = {
            IconButton(onClick = onToggleCollapseFolders) {
                Icon(
                    imageVector = if (folderPaneCollapsed) Icons.Rounded.ChevronRight else Icons.Rounded.ChevronLeft,
                    contentDescription = "Toggle Folder Panel",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        modifier = Modifier.width(68.dp).fillMaxHeight(),
    ) {
        NavigationRailItem(
            selected = !folderPaneCollapsed && !isSpaceHogsMode && !showAllDrivesDashboard,
            onClick = { if (folderPaneCollapsed) onToggleCollapseFolders() },
            icon = { Icon(Icons.Rounded.Folder, contentDescription = "Folders") },
            label = { Text("Browse", maxLines = 1) },
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                unselectedIconColor = OledColors.TextSecondary,
                unselectedTextColor = OledColors.TextSecondary,
            ),
        )

        NavigationRailItem(
            selected = showAllDrivesDashboard,
            onClick = onOpenAllDrives,
            icon = { Icon(Icons.Rounded.Storage, contentDescription = "All Drives") },
            label = { Text("Drives", maxLines = 1) },
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                unselectedIconColor = OledColors.TextSecondary,
                unselectedTextColor = OledColors.TextSecondary,
            ),
        )

        NavigationRailItem(
            selected = isSpaceHogsMode,
            onClick = onScanSpaceHogs,
            icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = "Space Hogs") },
            label = { Text("Hogs", maxLines = 1) },
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.tertiary,
                selectedTextColor = MaterialTheme.colorScheme.tertiary,
                indicatorColor = MaterialTheme.colorScheme.tertiaryContainer,
                unselectedIconColor = OledColors.TextSecondary,
                unselectedTextColor = OledColors.TextSecondary,
            ),
        )

        Spacer(Modifier.height(10.dp))

        // Quick PC & Laptop Host Icons right on the tablet rail
        savedHosts.take(4).forEach { host ->
            val selected = host.hostId == activeHost?.hostId
            val isLaptop = host.displayName.contains("laptop", ignoreCase = true)
            NavigationRailItem(
                selected = selected,
                onClick = { onSelectHost(host) },
                icon = {
                    Icon(
                        imageVector = when {
                            host.isLocalDevice -> Icons.Rounded.PhoneAndroid
                            isLaptop -> Icons.Rounded.Laptop
                            else -> Icons.Rounded.Computer
                        },
                        contentDescription = host.displayName,
                    )
                },
                label = {
                    Text(
                        text = if (host.isLocalDevice) "Device" else host.displayName.take(8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondary,
                    selectedTextColor = MaterialTheme.colorScheme.secondary,
                    indicatorColor = MaterialTheme.colorScheme.secondary,
                    unselectedIconColor = OledColors.TextSecondary,
                    unselectedTextColor = OledColors.TextSecondary,
                ),
            )
        }

        NavigationRailItem(
            selected = false,
            onClick = onOpenQueue,
            icon = { Icon(Icons.Rounded.Queue, contentDescription = "Render Queue") },
            label = { Text(if (activeQueueCount > 0) "Q ($activeQueueCount)" else "Queue", maxLines = 1) },
            colors = NavigationRailItemDefaults.colors(
                unselectedIconColor = if (activeQueueCount > 0) MaterialTheme.colorScheme.primary else OledColors.TextSecondary,
                unselectedTextColor = OledColors.TextSecondary,
            ),
        )

        Spacer(Modifier.weight(1f))

        if (spaceSavedBytes > 0L) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = OledColors.CardSurface,
                border = BorderStroke(1.dp, OledColors.HairlineBorder),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                ) {
                    Text("SAVED", style = MaterialTheme.typography.labelSmall, color = OledColors.SuccessGreen)
                    Text(
                        Formatters.formatBytes(spaceSavedBytes),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = OledColors.TextPrimary,
                        maxLines = 1,
                    )
                }
            }
        }

        NavigationRailItem(
            selected = false,
            onClick = onOpenHostManager,
            icon = { Icon(Icons.Rounded.AddLink, contentDescription = "Pair PC / Laptop") },
            label = { Text("Hosts", maxLines = 1) },
            colors = NavigationRailItemDefaults.colors(
                unselectedIconColor = MaterialTheme.colorScheme.primary,
                unselectedTextColor = OledColors.TextSecondary,
            ),
        )
        NavigationRailItem(
            selected = false,
            onClick = onOpenSettings,
            icon = { Icon(Icons.Rounded.Settings, contentDescription = "Settings") },
            label = { Text("Settings", maxLines = 1) },
            colors = NavigationRailItemDefaults.colors(
                unselectedIconColor = OledColors.TextSecondary,
                unselectedTextColor = OledColors.TextSecondary,
            ),
        )
        Spacer(Modifier.height(8.dp))
    }
}
