package com.deckvault.app.viewmodel

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deckvault.app.data.DeckVaultApi
import com.deckvault.app.data.LanDiscovery
import com.deckvault.app.data.PrefsRepository
import com.deckvault.app.model.DiscoveredHost
import com.deckvault.app.model.DriveInfo
import com.deckvault.app.model.FileItem
import com.deckvault.app.model.FolderItem
import com.deckvault.app.model.HostDrivesSnapshot
import com.deckvault.app.model.ListResponse
import com.deckvault.app.model.QuickFolder
import com.deckvault.app.model.RootsResponse
import com.deckvault.app.model.SavedHost
import com.deckvault.app.model.TrimRequest
import com.deckvault.app.model.TrimResponse
import com.deckvault.app.model.UserPreferences
import com.deckvault.app.peer.DeckVaultPeerService
import com.deckvault.app.ui.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class BrowserUiState(
    val drives: List<DriveInfo> = emptyList(),
    val quickFolders: List<QuickFolder> = emptyList(),
    val currentPath: String = "",
    val folderName: String = "",
    val parentPath: String = "",
    val currentFolderSizeBytes: Long = -1L,
    val isIndexing: Boolean = false,
    val spaceSavedBytes: Long = 0L,
    val treeFolders: List<FolderItem> = emptyList(),
    val listFolders: List<FolderItem> = emptyList(),
    val files: List<FileItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val treeSortBySize: Boolean = true,
    val isSpaceHogsMode: Boolean = false,
    val searchQuery: String = "",
    val recursiveSearch: Boolean = false,
    val selectedPaths: Set<String> = emptySet(),
    val showHostsModal: Boolean = false,
    val showSettingsModal: Boolean = false,
    val localCacheBytes: Long = 0L,
    val discoveredHosts: List<DiscoveredHost> = emptyList(),
    val isScanningLan: Boolean = false,
    val isPairing: Boolean = false,
    val pairingError: String? = null,
    val activeVideoFile: FileItem? = null,
    val openPlayerInTrimmer: Boolean = false,
    val isTrimming: Boolean = false,
    val lastTrimResult: TrimResponse? = null,
    val trimError: String? = null,
    val activeImageList: List<FileItem> = emptyList(),
    val activeImageIndex: Int = -1,
    val activeAudioFile: FileItem? = null,
    val activePdfFile: FileItem? = null,
    val activeTextFile: FileItem? = null,
    val activeOtherFile: FileItem? = null,
    val showAllDrivesDashboard: Boolean = false,
    val allDrivesSnapshots: List<HostDrivesSnapshot> = emptyList(),
    val isRefreshingAllDrives: Boolean = false,
    val pendingTransferFiles: List<FileItem> = emptyList(),
    val pendingTransferMoveDefault: Boolean = true,
    val isTransferring: Boolean = false,
    val transferStatusText: String = "",
    val transferProgressFraction: Float = 0f,
    val cacheVersion: Long = 0L,
    val toastMessage: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val api = DeckVaultApi()
    private val prefsRepo = PrefsRepository(application)

    val preferences: StateFlow<UserPreferences> = prefsRepo.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = UserPreferences(),
    )

    private val _ui = MutableStateFlow(BrowserUiState())
    val ui: StateFlow<BrowserUiState> = _ui.asStateFlow()

    private var loadedHostId: String? = null
    private var pendingNavigatePath: String? = null
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            var lastSortKey = ""
            var lastFilterKey = ""
            preferences.collectLatest { prefs ->
                val activeHost = prefs.hosts.firstOrNull { it.hostId == prefs.activeHostId }
                    ?: prefs.hosts.firstOrNull()

                if (activeHost == null) {
                    loadedHostId = null
                    _ui.update {
                        it.copy(
                            drives = emptyList(),
                            quickFolders = emptyList(),
                            treeFolders = emptyList(),
                            listFolders = emptyList(),
                            files = emptyList(),
                            currentPath = "",
                            showHostsModal = it.showHostsModal || prefs.hosts.isEmpty(),
                        )
                    }
                    if (prefs.hosts.isEmpty()) {
                        scanLan()
                    }
                } else {
                    val sortKey = "${prefs.sortBy.apiKey}:${prefs.sortDescending}"
                    val filterKey = prefs.mediaFilter.apiKey
                    if (loadedHostId != activeHost.hostId) {
                        loadedHostId = activeHost.hostId
                        lastSortKey = sortKey
                        lastFilterKey = filterKey
                        connectAndLoadHost(activeHost, prefs)
                    } else if (sortKey != lastSortKey || filterKey != lastFilterKey) {
                        lastSortKey = sortKey
                        lastFilterKey = filterKey
                        val path = _ui.value.currentPath
                        if (path.isNotBlank() && !_ui.value.isSpaceHogsMode) {
                            loadFolder(activeHost, path, prefs)
                        }
                    }
                }
            }
        }
    }

    fun updatePreferences(transform: (UserPreferences) -> UserPreferences) {
        viewModelScope.launch {
            prefsRepo.update(transform)
        }
    }

    fun openHostsModal() {
        _ui.update { it.copy(showHostsModal = true, pairingError = null) }
        scanLan()
    }

    fun closeHostsModal() {
        _ui.update { it.copy(showHostsModal = false, pairingError = null) }
    }

    fun openSettingsModal() {
        _ui.update { it.copy(showSettingsModal = true) }
        refreshLocalCacheSize()
    }

    fun closeSettingsModal() {
        _ui.update { it.copy(showSettingsModal = false) }
    }

    fun refreshLocalCacheSize() {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                dirSizeBytes(getApplication<Application>().cacheDir)
            }
            _ui.update { it.copy(localCacheBytes = bytes) }
        }
    }

    fun clearLocalCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val cache = getApplication<Application>().cacheDir
                cache.listFiles()?.forEach { it.deleteRecursively() }
            }
            _ui.update {
                it.copy(
                    localCacheBytes = 0L,
                    cacheVersion = System.currentTimeMillis(),
                    toastMessage = "Thumbnail cache cleared",
                )
            }
        }
    }

    private fun dirSizeBytes(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun scanLan() {
        viewModelScope.launch {
            _ui.update { it.copy(isScanningLan = true) }
            val found = LanDiscovery.discover(getApplication())
            _ui.update { it.copy(discoveredHosts = found, isScanningLan = false) }
        }
    }

    fun pairAndConnect(ip: String, port: Int, pin: String, customLabel: String) {
        viewModelScope.launch {
            _ui.update { it.copy(isPairing = true, pairingError = null) }
            runCatching {
                val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
                val resp = api.pair(ip, port, pin, deviceModel)
                val saved = SavedHost(
                    hostId = resp.hostId,
                    name = resp.deviceName,
                    ip = ip,
                    port = port,
                    token = resp.token,
                    label = customLabel.ifBlank { resp.deviceName },
                    os = resp.os,
                )
                prefsRepo.upsertHost(saved, makeActive = true)
            }.onSuccess {
                _ui.update { it.copy(isPairing = false, showHostsModal = false, toastMessage = "Paired & connected") }
            }.onFailure { err ->
                _ui.update { it.copy(isPairing = false, pairingError = err.message ?: "Pairing failed") }
            }
        }
    }

    fun selectHost(host: SavedHost) {
        viewModelScope.launch {
            prefsRepo.update { it.copy(activeHostId = host.hostId) }
        }
    }

    fun removeHost(hostId: String) {
        viewModelScope.launch {
            prefsRepo.removeHost(hostId)
        }
    }

    private fun activeHost(): SavedHost? {
        val prefs = preferences.value
        return prefs.hosts.firstOrNull { it.hostId == prefs.activeHostId } ?: prefs.hosts.firstOrNull()
    }

    private suspend fun connectAndLoadHost(host: SavedHost, prefs: UserPreferences) {
        _ui.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                selectedPaths = emptySet(),
                isSpaceHogsMode = false,
                searchQuery = "",
            )
        }
        runCatching {
            api.getRoots(host)
        }.onSuccess { roots ->
            val overridePath = pendingNavigatePath
            pendingNavigatePath = null
            val initialPath = overridePath?.takeIf { it.isNotBlank() }
                ?: roots.quickFolders.firstOrNull { it.name.equals("Videos", ignoreCase = true) }?.path
                ?: roots.quickFolders.firstOrNull()?.path
                ?: roots.drives.firstOrNull()?.path.orEmpty()
            _ui.update {
                it.copy(
                    drives = roots.drives,
                    quickFolders = roots.quickFolders,
                    spaceSavedBytes = roots.spaceSavedBytes,
                    isIndexing = roots.indexer.scanning,
                    currentPath = initialPath,
                )
            }
            if (initialPath.isNotBlank()) {
                loadFolder(host, initialPath, prefs)
            } else {
                _ui.update { it.copy(isLoading = false) }
            }
        }.onFailure { err ->
            _ui.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "Could not reach ${host.displayName} (${host.ip}:${host.port}): ${err.message}",
                )
            }
        }
    }

    fun navigateFolder(path: String) {
        val host = activeHost() ?: return
        viewModelScope.launch {
            _ui.update { it.copy(isSpaceHogsMode = false, selectedPaths = emptySet()) }
            loadFolder(host, path, preferences.value)
        }
    }

    fun toggleTreeSort() {
        val host = activeHost() ?: return
        val nextSortBySize = !_ui.value.treeSortBySize
        _ui.update { it.copy(treeSortBySize = nextSortBySize) }
        val path = _ui.value.currentPath
        if (path.isNotBlank()) {
            viewModelScope.launch {
                runCatching {
                    api.getTree(host, path, if (nextSortBySize) "size" else "name")
                }.onSuccess { tree ->
                    _ui.update { it.copy(treeFolders = tree.children) }
                }
            }
        }
    }

    fun rescanDisks() {
        val host = activeHost() ?: return
        viewModelScope.launch {
            runCatching {
                api.rescan(host)
                delay(500)
                val roots = api.getRoots(host)
                _ui.update {
                    it.copy(
                        drives = roots.drives,
                        quickFolders = roots.quickFolders,
                        isIndexing = roots.indexer.scanning,
                        spaceSavedBytes = roots.spaceSavedBytes,
                        toastMessage = "Rescanning disk sizes on ${host.displayName}...",
                    )
                }
                val path = _ui.value.currentPath
                if (path.isNotBlank()) {
                    loadFolder(host, path, preferences.value)
                }
            }
        }
    }

    fun scanLargestInCurrentFolder() {
        val host = activeHost() ?: return
        val path = _ui.value.currentPath.ifBlank { _ui.value.drives.firstOrNull()?.path.orEmpty() }
        if (path.isBlank()) return

        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true, isSpaceHogsMode = true, selectedPaths = emptySet()) }
            runCatching {
                api.getLargest(host, path, filter = "video", limit = 80)
            }.onSuccess { resp ->
                _ui.update {
                    it.copy(
                        isLoading = false,
                        files = resp.files,
                        listFolders = emptyList(),
                        errorMessage = null,
                    )
                }
            }.onFailure { err ->
                _ui.update { it.copy(isLoading = false, errorMessage = err.message) }
            }
        }
    }

    fun exitSpaceHogs() {
        val host = activeHost() ?: return
        val path = _ui.value.currentPath
        if (path.isNotBlank()) {
            viewModelScope.launch {
                _ui.update { it.copy(isSpaceHogsMode = false) }
                loadFolder(host, path, preferences.value)
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _ui.update { it.copy(searchQuery = query) }
        val host = activeHost() ?: return
        val path = _ui.value.currentPath
        if (path.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(250)
            loadFolder(host, path, preferences.value)
        }
    }

    fun toggleRecursiveSearch() {
        _ui.update { it.copy(recursiveSearch = !it.recursiveSearch) }
        val host = activeHost() ?: return
        val path = _ui.value.currentPath
        if (path.isNotBlank() && _ui.value.searchQuery.isNotBlank()) {
            viewModelScope.launch {
                loadFolder(host, path, preferences.value)
            }
        }
    }

    private suspend fun loadFolder(host: SavedHost, path: String, prefs: UserPreferences) {
        _ui.update { it.copy(isLoading = true, errorMessage = null) }
        val treeSort = if (_ui.value.treeSortBySize) "size" else "name"
        val order = if (prefs.sortDescending) "desc" else "asc"
        val query = _ui.value.searchQuery.trim()
        val recursive = _ui.value.recursiveSearch && query.isNotBlank()

        runCatching {
            val listResp = api.listFolder(
                host = host,
                path = path,
                sort = prefs.sortBy.apiKey,
                order = order,
                filter = prefs.mediaFilter.apiKey,
                query = if (recursive) "" else query,
            )
            val treeResp = api.getTree(host, path, treeSort)
            val recursiveFiles = if (recursive) {
                val searched = api.search(host, path, query).files
                val filtered = when (prefs.mediaFilter.apiKey) {
                    "video" -> searched.filter { it.isVideo }
                    "image" -> searched.filter { it.isImage }
                    "media" -> searched.filter { it.isVideo || it.isImage }
                    else -> searched
                }
                val comparator = when (prefs.sortBy.apiKey) {
                    "size" -> compareBy<FileItem> { it.sizeBytes }
                    "date" -> compareBy<FileItem> { it.modifiedMs }
                    "type" -> compareBy<FileItem> { it.extension.lowercase() }.thenBy { it.name.lowercase() }
                    else -> compareBy<FileItem> { it.name.lowercase() }
                }
                if (prefs.sortDescending) filtered.sortedWith(comparator.reversed()) else filtered.sortedWith(comparator)
            } else {
                null
            }
            Triple(listResp, treeResp, recursiveFiles)
        }.onSuccess { (listResp, treeResp, recursiveFiles) ->
            _ui.update {
                it.copy(
                    isLoading = false,
                    currentPath = listResp.path,
                    folderName = listResp.name,
                    parentPath = listResp.parentPath,
                    currentFolderSizeBytes = listResp.totalSizeBytes,
                    isIndexing = listResp.indexer.scanning,
                    listFolders = if (recursiveFiles != null) emptyList() else listResp.folders,
                    files = recursiveFiles ?: listResp.files,
                    treeFolders = treeResp.children,
                )
            }
        }.onFailure { err ->
            _ui.update { it.copy(isLoading = false, errorMessage = err.message ?: "Failed to load folder") }
        }
    }

    fun toggleSelect(path: String) {
        _ui.update { state ->
            val next = if (path in state.selectedPaths) state.selectedPaths - path else state.selectedPaths + path
            state.copy(selectedPaths = next)
        }
    }

    fun selectAllFiles() {
        _ui.update { state ->
            val all = state.files.map { it.path }.toSet()
            state.copy(selectedPaths = all)
        }
    }

    fun clearSelection() {
        _ui.update { it.copy(selectedPaths = emptySet()) }
    }

    fun renameFile(file: FileItem, newName: String) {
        val host = activeHost() ?: return
        val trimmed = newName.trim()
        if (trimmed.isBlank() || trimmed == file.name) return

        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true) }
            runCatching {
                api.renameFile(host, file.path, trimmed)
            }.onSuccess {
                _ui.update { it.copy(toastMessage = "Renamed to $trimmed") }
                refreshAfterMutation(host)
            }.onFailure { err ->
                _ui.update { it.copy(isLoading = false, toastMessage = err.message ?: "Rename failed") }
            }
        }
    }

    fun deleteSelected(recycle: Boolean) {
        val host = activeHost() ?: return
        val paths = _ui.value.selectedPaths.toList()
        if (paths.isEmpty()) return

        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true) }
            runCatching {
                api.deleteFiles(host, paths, recycle)
            }.onSuccess { res ->
                _ui.update {
                    it.copy(
                        selectedPaths = emptySet(),
                        spaceSavedBytes = res.totalSpaceSavedBytes,
                        toastMessage = "Freed ${com.deckvault.app.ui.util.Formatters.formatBytes(res.freedBytes)} (${res.deletedCount} items)",
                    )
                }
                refreshAfterMutation(host)
            }.onFailure { err ->
                _ui.update { it.copy(isLoading = false, errorMessage = err.message) }
            }
        }
    }

    fun openVideo(file: FileItem, openTrimmer: Boolean) {
        _ui.update {
            it.copy(
                activeVideoFile = file,
                openPlayerInTrimmer = openTrimmer,
                lastTrimResult = null,
                trimError = null,
            )
        }
    }

    fun openAdjacentVideo(delta: Int) {
        val current = _ui.value.activeVideoFile ?: return
        val videos = _ui.value.files.filter { it.isVideo }
        val idx = videos.indexOfFirst { it.path == current.path }
        if (idx < 0) return
        val next = videos.getOrNull(idx + delta) ?: return
        _ui.update {
            it.copy(
                activeVideoFile = next,
                openPlayerInTrimmer = false,
                lastTrimResult = null,
                trimError = null,
            )
        }
    }

    fun dismissVideoPlayer() {
        _ui.update { it.copy(activeVideoFile = null, trimError = null, lastTrimResult = null) }
    }

    fun executeTrim(startMs: Long, endMs: Long, mode: String, crf: Int, recycleOriginal: Boolean) {
        val host = activeHost() ?: return
        val file = _ui.value.activeVideoFile ?: return
        viewModelScope.launch {
            _ui.update { it.copy(isTrimming = true, trimError = null, lastTrimResult = null) }
            runCatching {
                api.trimVideo(
                    host,
                    TrimRequest(
                        path = file.path,
                        startMs = startMs,
                        endMs = endMs,
                        mode = mode,
                        crf = crf,
                        recycleOriginal = recycleOriginal,
                    ),
                )
            }.onSuccess { resp ->
                val newStamp = System.currentTimeMillis()
                val updatedFile = file.copy(
                    sizeBytes = resp.newSizeBytes,
                    meta = file.meta?.copy(durationMs = resp.newDurationMs),
                )
                _ui.update {
                    it.copy(
                        isTrimming = false,
                        lastTrimResult = resp,
                        activeVideoFile = updatedFile,
                        spaceSavedBytes = resp.totalSpaceSavedBytes,
                        cacheVersion = newStamp,
                        toastMessage = "Reclaimed ${com.deckvault.app.ui.util.Formatters.formatBytes(resp.savedBytes)}",
                    )
                }
                refreshAfterMutation(host)
            }.onFailure { err ->
                _ui.update { it.copy(isTrimming = false, trimError = err.message ?: "Trim failed") }
            }
        }
    }

    fun openImage(file: FileItem) {
        val imgs = _ui.value.files.filter { it.isImage }
        val idx = imgs.indexOfFirst { it.path == file.path }.coerceAtLeast(0)
        _ui.update { it.copy(activeImageList = imgs, activeImageIndex = idx) }
    }

    fun dismissImageViewer() {
        _ui.update { it.copy(activeImageList = emptyList(), activeImageIndex = -1) }
    }

    fun deleteSingleImage(file: FileItem) {
        val host = activeHost() ?: return
        viewModelScope.launch {
            runCatching {
                api.deleteFiles(host, listOf(file.path), preferences.value.recycleByDefault)
            }.onSuccess { res ->
                val remaining = _ui.value.activeImageList.filterNot { it.path == file.path }
                _ui.update {
                    it.copy(
                        activeImageList = remaining,
                        activeImageIndex = if (remaining.isEmpty()) -1 else it.activeImageIndex.coerceAtMost(remaining.lastIndex),
                        spaceSavedBytes = res.totalSpaceSavedBytes,
                        toastMessage = "Deleted ${file.name}",
                    )
                }
                refreshAfterMutation(host)
            }
        }
    }

    fun openTransferDialogForSelection(move: Boolean) {
        val picked = _ui.value.files.filter { it.path in _ui.value.selectedPaths }
        if (picked.isEmpty()) return
        _ui.update { it.copy(pendingTransferFiles = picked, pendingTransferMoveDefault = move) }
    }

    fun openTransferDialogForFile(file: FileItem, move: Boolean) {
        _ui.update { it.copy(pendingTransferFiles = listOf(file), pendingTransferMoveDefault = move) }
    }

    fun closeTransferDialog() {
        _ui.update { it.copy(pendingTransferFiles = emptyList()) }
    }

    suspend fun fetchHostRoots(host: SavedHost): RootsResponse = api.getRoots(host)

    suspend fun fetchHostFolders(host: SavedHost, path: String): ListResponse =
        api.listFolder(host, path = path, sort = "name", order = "asc", filter = "all")

    suspend fun createRemoteFolder(host: SavedHost, parentDir: String, name: String): String {
        val resp = api.createFolder(host, parentDir, name.trim())
        return resp.path
    }

    fun executeTransferToRemoteHost(
        items: List<FileItem>,
        targetHost: SavedHost,
        destinationDir: String,
        move: Boolean,
    ) {
        val sourceHost = activeHost() ?: return
        if (items.isEmpty()) return

        viewModelScope.launch {
            val totalBytes = items.sumOf { it.sizeBytes }.coerceAtLeast(1L)
            val verb = if (move) "Moving" else "Copying"
            _ui.update {
                it.copy(
                    pendingTransferFiles = emptyList(),
                    isTransferring = true,
                    transferProgressFraction = 0f,
                    transferStatusText = "$verb ${items.size} item(s) to ${targetHost.displayName}...",
                )
            }

            runCatching {
                if (sourceHost.hostId == targetHost.hostId) {
                    val res = api.transferLocal(sourceHost, items.map { it.path }, destinationDir, move)
                    _ui.update { it.copy(transferProgressFraction = 1f) }
                    val doneVerb = if (move) "Moved" else "Copied"
                    if (res.errors.isNotEmpty()) {
                        "$doneVerb ${res.transferredCount}/${items.size} (${res.errors.first()})"
                    } else {
                        "$doneVerb ${res.transferredCount} item(s) (${Formatters.formatBytes(res.transferredBytes)})"
                    }
                } else {
                    // Try fast direct PC <-> Laptop LAN pull first; relay via Android only for any item that fails direct pull
                    val pullRes = runCatching {
                        api.pullRemote(targetHost, sourceHost, items.map { it.path }, destinationDir)
                    }.getOrNull()

                    val succeededSrcPaths = mutableListOf<String>()
                    var copiedBytes = 0L
                    if (pullRes != null) {
                        pullRes.results.forEach { succeededSrcPaths.add(it.src) }
                        copiedBytes += pullRes.transferredBytes
                    }

                    val remaining = items.filterNot { it.path in succeededSrcPaths }
                    if (remaining.isNotEmpty()) {
                        var completedBefore = copiedBytes
                        remaining.forEachIndexed { idx, file ->
                            _ui.update {
                                it.copy(
                                    transferStatusText = "Relaying ${file.name} (${idx + 1}/${remaining.size}) -> ${targetHost.displayName}",
                                    transferProgressFraction = (completedBefore.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f),
                                )
                            }
                            api.relayFileBetweenHosts(
                                sourceHost = sourceHost,
                                sourcePath = file.path,
                                fileName = file.name,
                                expectedBytes = file.sizeBytes,
                                targetHost = targetHost,
                                destinationDir = destinationDir,
                            ) { bytesSoFar ->
                                val pct = ((completedBefore + bytesSoFar).toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                _ui.update { it.copy(transferProgressFraction = pct) }
                            }
                            completedBefore += file.sizeBytes
                            copiedBytes += file.sizeBytes
                            succeededSrcPaths.add(file.path)
                        }
                    }

                    if (move && succeededSrcPaths.isNotEmpty()) {
                        _ui.update { it.copy(transferStatusText = "Cleaning up originals on ${sourceHost.displayName}...") }
                        api.deleteFiles(sourceHost, succeededSrcPaths, recycle = preferences.value.recycleByDefault)
                    }

                    val doneVerb = if (move) "Moved" else "Copied"
                    "$doneVerb ${succeededSrcPaths.size} item(s) to ${targetHost.displayName} (${Formatters.formatBytes(copiedBytes)})"
                }
            }.onSuccess { msg ->
                _ui.update {
                    it.copy(
                        isTransferring = false,
                        transferProgressFraction = 0f,
                        transferStatusText = "",
                        selectedPaths = emptySet(),
                        toastMessage = msg,
                    )
                }
                refreshAfterMutation(sourceHost)
            }.onFailure { err ->
                _ui.update {
                    it.copy(
                        isTransferring = false,
                        transferProgressFraction = 0f,
                        transferStatusText = "",
                        errorMessage = "Transfer failed: ${err.message}",
                    )
                }
            }
        }
    }

    fun executeTransferToLocalDevice(items: List<FileItem>, move: Boolean) {
        val sourceHost = activeHost() ?: return
        if (items.isEmpty()) return

        viewModelScope.launch {
            val totalBytes = items.sumOf { it.sizeBytes }.coerceAtLeast(1L)
            _ui.update {
                it.copy(
                    pendingTransferFiles = emptyList(),
                    isTransferring = true,
                    transferProgressFraction = 0f,
                    transferStatusText = "Downloading ${items.size} item(s) to this device...",
                )
            }

            runCatching {
                val ctx = getApplication<Application>()
                val savedPaths = mutableListOf<String>()
                var completedBytes = 0L

                items.forEachIndexed { idx, file ->
                    _ui.update {
                        it.copy(
                            transferStatusText = "Saving ${file.name} (${idx + 1}/${items.size}) to device",
                            transferProgressFraction = (completedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f),
                        )
                    }
                    val written = saveRemoteFileToAndroidStorage(ctx, sourceHost, file) { bytesRead ->
                        val frac = ((completedBytes + bytesRead).toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                        _ui.update { it.copy(transferProgressFraction = frac) }
                    }
                    completedBytes += written.coerceAtLeast(file.sizeBytes)
                    savedPaths.add(file.path)
                }

                if (move && savedPaths.isNotEmpty()) {
                    _ui.update { it.copy(transferStatusText = "Removing moved originals on ${sourceHost.displayName}...") }
                    api.deleteFiles(sourceHost, savedPaths, recycle = preferences.value.recycleByDefault)
                }

                val verb = if (move) "Moved" else "Saved"
                "$verb ${savedPaths.size} item(s) to device (${Formatters.formatBytes(completedBytes)})"
            }.onSuccess { msg ->
                _ui.update {
                    it.copy(
                        isTransferring = false,
                        transferProgressFraction = 0f,
                        transferStatusText = "",
                        selectedPaths = emptySet(),
                        toastMessage = msg,
                    )
                }
                if (move) refreshAfterMutation(sourceHost)
            }.onFailure { err ->
                _ui.update {
                    it.copy(
                        isTransferring = false,
                        transferProgressFraction = 0f,
                        transferStatusText = "",
                        errorMessage = "Download failed: ${err.message}",
                    )
                }
            }
        }
    }

    private suspend fun saveRemoteFileToAndroidStorage(
        ctx: Application,
        host: SavedHost,
        file: FileItem,
        onProgress: (Long) -> Unit,
    ): Long {
        val ext = file.extension.trimStart('.').lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when {
            file.isVideo -> "video/mp4"
            file.isImage -> "image/jpeg"
            else -> "application/octet-stream"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = ctx.contentResolver
            val (collection, relPath) = when {
                file.isVideo -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to "${Environment.DIRECTORY_MOVIES}/DeckVault"
                file.isImage -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to "${Environment.DIRECTORY_PICTURES}/DeckVault"
                else -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to "${Environment.DIRECTORY_DOWNLOADS}/DeckVault"
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values) ?: throw IllegalStateException("Could not create MediaStore entry for ${file.name}")
            return try {
                val bytes = api.downloadFile(
                    host = host,
                    remotePath = file.path,
                    openOutput = { resolver.openOutputStream(uri) ?: throw IllegalStateException("Cannot open output stream") },
                    onProgress = onProgress,
                )
                val doneValues = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(uri, doneValues, null, null)
                bytes
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }
        } else {
            val dir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "DeckVault")
            dir.mkdirs()
            val outFile = File(dir, file.name)
            return api.downloadFile(host, file.path, openOutput = { outFile.outputStream() }, onProgress = onProgress)
        }
    }

    fun uploadDeviceUrisToCurrentFolder(uris: List<Uri>, deleteLocalAfter: Boolean = false) {
        val host = activeHost() ?: return
        val destDir = _ui.value.currentPath
        if (uris.isEmpty() || destDir.isBlank()) return

        viewModelScope.launch {
            _ui.update {
                it.copy(
                    isTransferring = true,
                    transferProgressFraction = 0f,
                    transferStatusText = "Sending ${uris.size} file(s) to ${host.displayName}...",
                )
            }
            runCatching {
                val resolver = getApplication<Application>().contentResolver
                val metas = uris.mapIndexed { idx, uri ->
                    var name = "file_${idx}_${System.currentTimeMillis()}"
                    var size = -1L
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                        if (c.moveToFirst()) {
                            val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                            if (nameIdx >= 0 && !c.isNull(nameIdx)) name = c.getString(nameIdx)
                            if (sizeIdx >= 0 && !c.isNull(sizeIdx)) size = c.getLong(sizeIdx)
                        }
                    }
                    Triple(uri, name, size)
                }

                val totalBytes = metas.sumOf { it.third.coerceAtLeast(1L) }.coerceAtLeast(1L)
                var doneBytes = 0L
                var count = 0

                metas.forEachIndexed { idx, (uri, name, size) ->
                    _ui.update {
                        it.copy(
                            transferStatusText = "Uploading $name (${idx + 1}/${metas.size}) -> ${host.displayName}",
                            transferProgressFraction = (doneBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f),
                        )
                    }
                    val up = api.uploadFromStream(
                        host = host,
                        destDir = destDir,
                        fileName = name,
                        contentLength = size,
                        openStream = { resolver.openInputStream(uri) },
                    ) { written ->
                        if (size > 0L) {
                            _ui.update {
                                it.copy(transferProgressFraction = ((doneBytes + written).toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
                            }
                        }
                    }
                    doneBytes += up.sizeBytes.coerceAtLeast(size.coerceAtLeast(0L))
                    count++
                    if (deleteLocalAfter) {
                        runCatching { DocumentsContract.deleteDocument(resolver, uri) }
                            .onFailure { runCatching { resolver.delete(uri, null, null) } }
                    }
                }
                val verb = if (deleteLocalAfter) "Moved" else "Sent"
                "$verb $count file(s) to ${host.displayName}"
            }.onSuccess { msg ->
                _ui.update {
                    it.copy(
                        isTransferring = false,
                        transferProgressFraction = 0f,
                        transferStatusText = "",
                        toastMessage = msg,
                    )
                }
                refreshAfterMutation(host)
            }.onFailure { err ->
                _ui.update {
                    it.copy(
                        isTransferring = false,
                        transferProgressFraction = 0f,
                        transferStatusText = "",
                        errorMessage = "Upload failed: ${err.message}",
                    )
                }
            }
        }
    }

    fun rotatePeerPin() {
        val nextPin = DeckVaultPeerService.rotatePin()
        if (nextPin != null) {
            _ui.update { it.copy(toastMessage = "New sharing PIN: $nextPin") }
        }
    }

    fun openAllDrivesDashboard() {
        _ui.update { it.copy(showAllDrivesDashboard = true) }
        refreshAllDrives()
    }

    fun closeAllDrivesDashboard() {
        _ui.update { it.copy(showAllDrivesDashboard = false) }
    }

    fun refreshAllDrives() {
        viewModelScope.launch {
            _ui.update { it.copy(isRefreshingAllDrives = true) }
            val app = getApplication<Application>()
            val localHostId = DeckVaultPeerService.activeServer?.hostId
            val localName = "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "This Device" }
            val savedHosts = preferences.value.hosts

            val remoteSnapshots = coroutineScope {
                savedHosts.filter { it.hostId != localHostId }.map { host ->
                    async(Dispatchers.IO) {
                        runCatching { api.getRoots(host) }.fold(
                            onSuccess = { roots ->
                                HostDrivesSnapshot(
                                    host = host,
                                    isLocalDevice = false,
                                    deviceName = host.displayName,
                                    os = roots.os.ifBlank { host.os },
                                    online = true,
                                    drives = roots.drives,
                                    quickFolders = roots.quickFolders,
                                )
                            },
                            onFailure = { err ->
                                HostDrivesSnapshot(
                                    host = host,
                                    isLocalDevice = false,
                                    deviceName = host.displayName,
                                    os = host.os,
                                    online = false,
                                    error = err.message ?: "Offline",
                                )
                            },
                        )
                    }
                }.awaitAll()
            }

            val localDrives = withContext(Dispatchers.IO) { DeckVaultPeerService.localDrives(app) }
            val localQuick = withContext(Dispatchers.IO) { DeckVaultPeerService.localQuickFolders(app) }
            val existingLocalSaved = savedHosts.firstOrNull { it.hostId == localHostId }
            val localSnap = HostDrivesSnapshot(
                host = existingLocalSaved,
                isLocalDevice = true,
                deviceName = localName,
                os = "Android ${Build.VERSION.RELEASE}",
                online = true,
                drives = localDrives,
                quickFolders = localQuick,
            )

            _ui.update {
                it.copy(
                    allDrivesSnapshots = listOf(localSnap) + remoteSnapshots,
                    isRefreshingAllDrives = false,
                )
            }
        }
    }

    fun openDriveFromDashboard(snapshot: HostDrivesSnapshot, initialPath: String) {
        viewModelScope.launch {
            _ui.update { it.copy(showAllDrivesDashboard = false) }
            if (snapshot.host != null) {
                val targetHost = snapshot.host
                val curActive = activeHost()
                if (curActive?.hostId == targetHost.hostId) {
                    navigateFolder(initialPath)
                } else {
                    pendingNavigatePath = initialPath
                    prefsRepo.update { it.copy(activeHostId = targetHost.hostId) }
                }
                return@launch
            }

            if (snapshot.isLocalDevice) {
                val app = getApplication<Application>()
                DeckVaultPeerService.start(app)
                var srv = DeckVaultPeerService.activeServer
                var waitedMs = 0
                while (srv == null && waitedMs < 1500) {
                    delay(80)
                    waitedMs += 80
                    srv = DeckVaultPeerService.activeServer
                }
                if (srv == null) {
                    _ui.update { it.copy(errorMessage = "Could not start local peer server") }
                    return@launch
                }
                val loopbackIp = "127.0.0.1"
                val port = com.deckvault.app.peer.AndroidPeerServer.HTTP_PORT
                runCatching {
                    val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
                    val resp = api.pair(loopbackIp, port, srv.pin, "$deviceModel (Local)")
                    SavedHost(
                        hostId = resp.hostId,
                        name = resp.deviceName,
                        ip = loopbackIp,
                        port = port,
                        token = resp.token,
                        label = "${resp.deviceName} (This Device)",
                        os = resp.os,
                    )
                }.onSuccess { saved ->
                    pendingNavigatePath = initialPath
                    prefsRepo.upsertHost(saved, makeActive = true)
                }.onFailure { err ->
                    _ui.update { it.copy(errorMessage = "Could not open local drive: ${err.message}") }
                }
            }
        }
    }

    fun openFile(file: FileItem) {
        when {
            file.isVideo -> openVideo(file, openTrimmer = false)
            file.isImage -> openImage(file)
            file.isAudio -> _ui.update { it.copy(activeAudioFile = file) }
            file.isPdf -> _ui.update { it.copy(activePdfFile = file) }
            file.isText -> _ui.update { it.copy(activeTextFile = file) }
            else -> _ui.update { it.copy(activeOtherFile = file) }
        }
    }

    fun openAdjacentAudio(delta: Int) {
        val current = _ui.value.activeAudioFile ?: return
        val audioFiles = _ui.value.files.filter { it.isAudio }
        val idx = audioFiles.indexOfFirst { it.path == current.path }
        if (idx < 0) return
        val target = audioFiles.getOrNull(idx + delta) ?: return
        _ui.update { it.copy(activeAudioFile = target) }
    }

    fun dismissAudioPlayer() {
        _ui.update { it.copy(activeAudioFile = null) }
    }

    fun dismissPdfViewer() {
        _ui.update { it.copy(activePdfFile = null) }
    }

    fun dismissTextViewer() {
        _ui.update { it.copy(activeTextFile = null) }
    }

    fun dismissOtherFileViewer() {
        _ui.update { it.copy(activeOtherFile = null) }
    }

    fun deleteSingleFile(file: FileItem) {
        val host = activeHost() ?: return
        viewModelScope.launch {
            runCatching {
                api.deleteFiles(host, listOf(file.path), preferences.value.recycleByDefault)
            }.onSuccess { res ->
                _ui.update {
                    it.copy(
                        activeAudioFile = if (it.activeAudioFile?.path == file.path) null else it.activeAudioFile,
                        activePdfFile = if (it.activePdfFile?.path == file.path) null else it.activePdfFile,
                        activeTextFile = if (it.activeTextFile?.path == file.path) null else it.activeTextFile,
                        activeOtherFile = if (it.activeOtherFile?.path == file.path) null else it.activeOtherFile,
                        spaceSavedBytes = res.totalSpaceSavedBytes,
                        toastMessage = "Deleted ${file.name}",
                    )
                }
                refreshAfterMutation(host)
            }.onFailure { err ->
                _ui.update { it.copy(errorMessage = err.message ?: "Delete failed") }
            }
        }
    }

    fun showToast(message: String) {
        _ui.update { it.copy(toastMessage = message) }
    }

    fun consumeToast() {
        _ui.update { it.copy(toastMessage = null) }
    }

    fun clearError() {
        _ui.update { it.copy(errorMessage = null) }
    }

    private suspend fun refreshAfterMutation(host: SavedHost) {
        runCatching {
            val roots = api.getRoots(host)
            _ui.update {
                it.copy(
                    drives = roots.drives,
                    quickFolders = roots.quickFolders,
                    spaceSavedBytes = roots.spaceSavedBytes,
                )
            }
        }
        val path = _ui.value.currentPath
        if (_ui.value.isSpaceHogsMode && path.isNotBlank()) {
            val largest = runCatching { api.getLargest(host, path, "video", 80) }.getOrNull()
            if (largest != null) {
                _ui.update { it.copy(files = largest.files, isLoading = false) }
            }
        } else if (path.isNotBlank()) {
            loadFolder(host, path, preferences.value)
        }
    }
}
