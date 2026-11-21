package com.deckvault.app.viewmodel

import android.app.Application
import android.content.ContentValues
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.LruCache
import android.webkit.MimeTypeMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.deckvault.app.data.AllFilesPermissionRequiredException
import com.deckvault.app.data.AndroidMetaCache
import com.deckvault.app.data.DeckVaultApi
import com.deckvault.app.data.HttpStatusException
import com.deckvault.app.data.LanDiscovery
import com.deckvault.app.data.LocalStorageRepository
import com.deckvault.app.data.PrefsRepository
import com.deckvault.app.data.SdCardWritePermissionRequiredException
import com.deckvault.app.media.LocalMediaProcessor
import com.deckvault.app.model.CompressEstimateRequest
import com.deckvault.app.model.CompressEstimateResponse
import com.deckvault.app.model.CompressRequest
import com.deckvault.app.model.DiscoveredHost
import com.deckvault.app.model.DriveInfo
import com.deckvault.app.model.EditStudioRequest
import com.deckvault.app.model.ExtractAudioRequest
import com.deckvault.app.model.FileItem
import com.deckvault.app.model.FolderItem
import com.deckvault.app.model.GifExportRequest
import com.deckvault.app.model.HostDrivesSnapshot
import com.deckvault.app.model.JobExecutionTarget
import com.deckvault.app.model.LargestResponse
import com.deckvault.app.model.ListResponse
import com.deckvault.app.model.MediaOpResponse
import com.deckvault.app.model.MergeRequest
import com.deckvault.app.model.QuickFolder
import com.deckvault.app.model.RenderQueueItem
import com.deckvault.app.model.RootsResponse
import com.deckvault.app.model.SavedHost
import com.deckvault.app.model.SearchResponse
import com.deckvault.app.model.TreeResponse
import com.deckvault.app.model.TrimResponse
import com.deckvault.app.model.UpscaleRequest
import com.deckvault.app.model.UserPreferences
import com.deckvault.app.model.VideoMeta
import com.deckvault.app.peer.DeckVaultPeerService
import com.deckvault.app.ui.components.OptimizerTab
import com.deckvault.app.ui.util.CoilCacheKeys
import com.deckvault.app.ui.util.Formatters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

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
    val needsAllFilesPermission: Boolean = false,
    val pendingSdCardAccess: Pair<String, String>? = null,
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
    val optimizerFile: FileItem? = null,
    val optimizerTab: OptimizerTab = OptimizerTab.COMPRESS,
    val mergeCandidates: List<FileItem> = emptyList(),
    val queueJobs: List<RenderQueueItem> = emptyList(),
    val showQueueSheet: Boolean = false,
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
    val precacheProgressText: String? = null,
    val toastMessage: String? = null,
)

private sealed class LocalTaskSpec {
    data class Edit(val req: EditStudioRequest) : LocalTaskSpec()
    data class Compress(val req: CompressRequest) : LocalTaskSpec()
    data class Upscale(val req: UpscaleRequest) : LocalTaskSpec()
    data class ExtractAudio(val req: ExtractAudioRequest) : LocalTaskSpec()
    data class Merge(val req: MergeRequest) : LocalTaskSpec()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val api = DeckVaultApi()
    val localRepo = LocalStorageRepository(application)
    val localMedia = LocalMediaProcessor(application, localRepo)
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
    private var folderNavJob: Job? = null
    private var browseGeneration: Long = 0L

    private val rootsCache = ConcurrentHashMap<String, RootsResponse>()
    private val folderCache = LruCache<String, ListResponse>(48)
    private val treeCache = LruCache<String, TreeResponse>(48)
    private val snapshotDb = AndroidMetaCache(application)
    private var precacheJob: Job? = null

    private fun folderCacheKey(hostId: String, path: String, prefs: UserPreferences, query: String): String =
        "$hostId|$path|${prefs.sortBy.apiKey}|${prefs.sortDescending}|${prefs.mediaFilter.apiKey}|$query"

    private fun treeCacheKey(hostId: String, path: String, sort: String): String =
        "$hostId|$path|$sort"

    fun invalidateHostCache(hostId: String? = null) {
        if (hostId == null) {
            rootsCache.clear()
            folderCache.evictAll()
            treeCache.evictAll()
            localRepo.invalidateFolderStatsCache()
        } else {
            rootsCache.remove(hostId)
            folderCache.snapshot().keys.filter { it.startsWith("$hostId|") }.forEach { folderCache.remove(it) }
            treeCache.snapshot().keys.filter { it.startsWith("$hostId|") }.forEach { treeCache.remove(it) }
            if (hostId == SavedHost.LOCAL_DEVICE_ID) {
                localRepo.invalidateFolderStatsCache()
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            snapshotDb.clearSnapshotsForHost(hostId)
        }
    }

    private val localTasks = ConcurrentHashMap<String, LocalTaskSpec>()
    private val localQueueChannel = Channel<String>(Channel.UNLIMITED)
    private val runningCoroutines = ConcurrentHashMap<String, Job>()
    private val cancelledIds = ConcurrentHashMap.newKeySet<String>()

    init {
        api.onResolveHostIps = { host ->
            LanDiscovery.resolveHostIpsById(getApplication(), host.hostId, timeoutMs = 900)
        }
        api.onHostIpHealed = { staleHost, goodIp, extraIps ->
            prefsRepo.update { prefs ->
                val updatedHosts = prefs.hosts.map { h ->
                    if (h.hostId == staleHost.hostId) h.withPrimaryIp(goodIp, extraIps) else h
                }
                prefs.copy(hosts = updatedHosts)
            }
        }
        // Ensure This Device host is always registered & remove any broken legacy 127.0.0.1 loopback host
        viewModelScope.launch {
            prefsRepo.update { prefs ->
                val cleaned = prefs.hosts.filterNot {
                    it.ip == "127.0.0.1" && !it.isLocalDevice
                }
                val hasLocal = cleaned.any { it.isLocalDevice }
                val finalHosts = if (hasLocal) cleaned else listOf(SavedHost.localDevice()) + cleaned
                val nextActive = prefs.activeHostId?.takeIf { id -> finalHosts.any { it.hostId == id } }
                    ?: finalHosts.firstOrNull()?.hostId
                prefs.copy(hosts = finalHosts, activeHostId = nextActive)
            }
        }

        // Start sequential local hardware worker
        viewModelScope.launch(Dispatchers.Default) {
            for (jobId in localQueueChannel) {
                val spec = localTasks.remove(jobId) ?: continue
                if (cancelledIds.contains(jobId)) {
                    updateQueueJob(jobId) { it.copy(status = "cancelled", message = "Cancelled") }
                    continue
                }
                val jobScope = launch {
                    runLocalTask(jobId, spec)
                }
                runningCoroutines[jobId] = jobScope
                jobScope.join()
                runningCoroutines.remove(jobId)
            }
        }

        viewModelScope.launch {
            var lastSortKey = ""
            var lastFilterKey = ""
            preferences.collectLatest { prefs ->
                val activeHost = prefs.hosts.firstOrNull { it.hostId == prefs.activeHostId }
                    ?: prefs.hosts.firstOrNull()
                    ?: SavedHost.localDevice()

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

    fun updatePreferences(transform: (UserPreferences) -> UserPreferences) {
        viewModelScope.launch { prefsRepo.update(transform) }
    }

    fun pairedPcHosts(): List<SavedHost> =
        preferences.value.hosts.filter { it.isDesktopPcHost }

    fun activeHost(): SavedHost =
        preferences.value.hosts.firstOrNull { it.hostId == preferences.value.activeHostId }
            ?: preferences.value.hosts.firstOrNull()
            ?: SavedHost.localDevice()

    fun openHostsModal() {
        _ui.update { it.copy(showHostsModal = true, pairingError = null) }
        scanLan()
    }

    fun closeHostsModal() {
        _ui.update { it.copy(showHostsModal = false, pairingError = null) }
    }

    fun openSettingsModal() {
        refreshLocalCacheSize()
        _ui.update { it.copy(showSettingsModal = true) }
    }

    fun closeSettingsModal() {
        _ui.update { it.copy(showSettingsModal = false) }
    }

    fun refreshLocalCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            var total = 0L
            ctx.cacheDir.walkTopDown().forEach { f -> if (f.isFile) total += f.length() }
            _ui.update { it.copy(localCacheBytes = total) }
        }
    }

    fun clearLocalCache() {
        precacheJob?.cancel()
        precacheJob = null
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            runCatching { SingletonImageLoader.get(ctx).memoryCache?.clear() }
            runCatching { SingletonImageLoader.get(ctx).diskCache?.clear() }
            snapshotDb.clearSnapshotsForHost(null)
            ctx.cacheDir.listFiles()?.forEach { child ->
                if (child.name != "datastore") child.deleteRecursively()
            }
            refreshLocalCacheSize()
            _ui.update {
                it.copy(
                    cacheVersion = System.currentTimeMillis(),
                    precacheProgressText = null,
                    toastMessage = "Local preview & snapshot cache cleared",
                )
            }
        }
    }

    fun precacheCurrentFolderThumbnails() {
        if (precacheJob?.isActive == true) {
            precacheJob?.cancel()
            precacheJob = null
            _ui.update { it.copy(precacheProgressText = null, toastMessage = "Folder thumbnail caching stopped") }
            return
        }
        val host = activeHost()
        if (host.isLocalDevice) {
            _ui.update { it.copy(toastMessage = "Local files generate thumbnails directly") }
            return
        }
        val targets = _ui.value.files.filter { it.isVideo || it.isImage }.take(150)
        if (targets.isEmpty()) {
            _ui.update { it.copy(toastMessage = "No photos or videos in this folder to cache") }
            return
        }
        val ver = _ui.value.cacheVersion
        val total = targets.size
        precacheJob = viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val loader = SingletonImageLoader.get(ctx)
            val sem = Semaphore(4)
            val doneCount = AtomicInteger(0)
            _ui.update { it.copy(precacheProgressText = "Caching 0/$total") }
            try {
                coroutineScope {
                    targets.map { file ->
                        async {
                            sem.withPermit {
                                val url = if (file.isVideo) {
                                    api.videoThumbnailUrl(host, file.path, width = 480, cacheBust = ver)
                                } else {
                                    api.imageUrl(host, file.path, width = 480, cacheBust = ver)
                                }
                                val key = CoilCacheKeys.mediaKey(
                                    hostId = host.hostId,
                                    kind = if (file.isVideo) "vthumb" else "img",
                                    path = file.path,
                                    width = 480,
                                    sizeBytes = file.sizeBytes,
                                    modifiedMs = file.modifiedMs,
                                    cacheVersion = ver,
                                )
                                val req = ImageRequest.Builder(ctx)
                                    .data(url)
                                    .memoryCacheKey(key)
                                    .diskCacheKey(key)
                                    .memoryCachePolicy(CachePolicy.DISABLED)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .build()
                                runCatching { loader.execute(req) }
                                val done = doneCount.incrementAndGet()
                                _ui.update { it.copy(precacheProgressText = "Caching $done/$total") }
                            }
                        }
                    }.awaitAll()
                }
                refreshLocalCacheSize()
                _ui.update { it.copy(precacheProgressText = null, toastMessage = "Cached $total thumbnails to disk") }
            } catch (_: CancellationException) {
                _ui.update { it.copy(precacheProgressText = null) }
            } finally {
                precacheJob = null
            }
        }
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
                val discoveredIps = _ui.value.discoveredHosts
                    .firstOrNull { it.hostId == resp.hostId || ip in it.ips }
                    ?.ips
                    .orEmpty()
                    .filter { it != ip && it.isNotBlank() && it != "127.0.0.1" }
                val saved = SavedHost(
                    hostId = resp.hostId,
                    name = resp.deviceName,
                    ip = ip,
                    port = port,
                    token = resp.token,
                    label = customLabel.ifBlank { resp.deviceName },
                    os = resp.os,
                    alternateIps = discoveredIps,
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
        if (hostId == SavedHost.LOCAL_DEVICE_ID) return
        viewModelScope.launch { prefsRepo.removeHost(hostId) }
    }

    fun onPermissionChanged() {
        val host = activeHost()
        if (host.isLocalDevice) {
            invalidateHostCache(host.hostId)
            viewModelScope.launch {
                _ui.update { it.copy(needsAllFilesPermission = false, errorMessage = null) }
                connectAndLoadHost(host, preferences.value)
            }
        }
    }

    fun onSdCardTreeGranted(volumeRoot: String, treeUri: Uri) {
        viewModelScope.launch {
            prefsRepo.saveSafTreeUri(volumeRoot, treeUri.toString())
            _ui.update {
                it.copy(
                    pendingSdCardAccess = null,
                    toastMessage = "SD Card / USB write access saved — tap Move/Delete again!",
                )
            }
        }
    }

    fun dismissSdCardAccessPrompt() {
        _ui.update { it.copy(pendingSdCardAccess = null) }
    }

    private suspend fun resolveRoots(host: SavedHost): RootsResponse {
        if (host.isLocalDevice) {
            return withContext(Dispatchers.IO) {
                val drives = localRepo.detectStorageDrives()
                val quick = localRepo.detectQuickFolders()
                RootsResponse(
                    deviceName = "This Device",
                    hostId = SavedHost.LOCAL_DEVICE_ID,
                    os = "Android ${Build.VERSION.RELEASE}",
                    ffmpeg = true,
                    spaceSavedBytes = 0L,
                    drives = drives,
                    quickFolders = quick,
                )
            }
        }
        return api.getRoots(host)
    }

    private suspend fun resolveListFolder(host: SavedHost, path: String, prefs: UserPreferences, query: String): ListResponse {
        if (host.isLocalDevice) {
            val base = localRepo.listDirectory(path, prefs.sortBy, prefs.sortDescending, prefs.mediaFilter)
            return if (query.isBlank()) base else {
                val qLower = query.lowercase()
                base.copy(
                    folders = base.folders.filter { it.name.lowercase().contains(qLower) },
                    files = base.files.filter { it.name.lowercase().contains(qLower) },
                )
            }
        }
        return api.listFolder(
            host = host,
            path = path,
            sort = prefs.sortBy.apiKey,
            order = if (prefs.sortDescending) "desc" else "asc",
            filter = prefs.mediaFilter.apiKey,
            query = query,
        )
    }

    private suspend fun resolveTree(host: SavedHost, path: String, treeSort: String): TreeResponse {
        if (host.isLocalDevice) return localRepo.getTree(path, sortBySize = treeSort != "name")
        return api.getTree(host, path, treeSort)
    }

    private suspend fun connectAndLoadHost(host: SavedHost, prefs: UserPreferences) {
        val connectGen = ++browseGeneration
        var cachedRoots = rootsCache[host.hostId]
        if (cachedRoots == null && !host.isLocalDevice) {
            val snapRoots = withContext(Dispatchers.IO) {
                snapshotDb.getRootsSnapshot(host.hostId)?.let { raw ->
                    runCatching { api.json.decodeFromString(RootsResponse.serializer(), raw) }.getOrNull()
                }
            }
            if (snapRoots != null) {
                cachedRoots = snapRoots
                rootsCache[host.hostId] = snapRoots
            }
        }
        val cachedInitialPath = pendingNavigatePath?.takeIf { it.isNotBlank() }
            ?: cachedRoots?.quickFolders?.firstOrNull { it.name.contains("Videos", true) || it.name.contains("Movies", true) }?.path
            ?: cachedRoots?.quickFolders?.firstOrNull()?.path
            ?: cachedRoots?.drives?.firstOrNull()?.path.orEmpty()
        var cachedInitialList = if (cachedInitialPath.isNotBlank()) {
            folderCache.get(folderCacheKey(host.hostId, cachedInitialPath, prefs, ""))
        } else null
        if (cachedInitialList == null && cachedInitialPath.isNotBlank() && !host.isLocalDevice) {
            val fKey = folderCacheKey(host.hostId, cachedInitialPath, prefs, "")
            val snapList = withContext(Dispatchers.IO) {
                snapshotDb.getFolderSnapshot(fKey)?.let { raw ->
                    runCatching { api.json.decodeFromString(ListResponse.serializer(), raw) }.getOrNull()
                }
            }
            if (snapList != null) {
                cachedInitialList = snapList
                folderCache.put(fKey, snapList)
            }
        }

        if (cachedRoots != null) {
            // Instant stale-while-revalidate UI switch between phones, tablet, and PC (<2ms)
            _ui.update {
                it.copy(
                    isLoading = cachedInitialList == null,
                    errorMessage = null,
                    needsAllFilesPermission = host.isLocalDevice && !localRepo.hasAllFilesAccess(),
                    selectedPaths = emptySet(),
                    isSpaceHogsMode = false,
                    searchQuery = "",
                    drives = cachedRoots.drives,
                    quickFolders = cachedRoots.quickFolders,
                    spaceSavedBytes = cachedRoots.spaceSavedBytes,
                    isIndexing = cachedRoots.indexer.scanning,
                    currentPath = cachedInitialPath,
                    folderName = cachedInitialList?.name ?: it.folderName,
                    parentPath = cachedInitialList?.parentPath ?: it.parentPath,
                    currentFolderSizeBytes = cachedInitialList?.totalSizeBytes ?: it.currentFolderSizeBytes,
                    listFolders = cachedInitialList?.folders ?: emptyList(),
                    files = cachedInitialList?.files ?: emptyList(),
                )
            }
        } else {
            _ui.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    needsAllFilesPermission = host.isLocalDevice && !localRepo.hasAllFilesAccess(),
                    selectedPaths = emptySet(),
                    isSpaceHogsMode = false,
                    searchQuery = "",
                )
            }
        }

        runCatching {
            if (host.isLocalDevice) resolveRoots(host)
            else withTimeout(10_000L) { resolveRoots(host) }
        }.onSuccess { roots ->
            rootsCache[host.hostId] = roots
            if (!host.isLocalDevice) {
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching {
                        snapshotDb.putRootsSnapshot(host.hostId, api.json.encodeToString(RootsResponse.serializer(), roots))
                    }
                }
            }
            if (!host.isLocalDevice && roots.os.isNotBlank() && !roots.os.equals(host.os, ignoreCase = true)) {
                prefsRepo.update { cur ->
                    cur.copy(hosts = cur.hosts.map { if (it.hostId == host.hostId) it.copy(os = roots.os) else it })
                }
            }
            if (connectGen != browseGeneration) return
            val overridePath = pendingNavigatePath
            pendingNavigatePath = null
            val initialPath = overridePath?.takeIf { it.isNotBlank() }
                ?: roots.quickFolders.firstOrNull { it.name.contains("Videos", true) || it.name.contains("Movies", true) }?.path
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
            if (err is CancellationException && err !is TimeoutCancellationException) throw err
            if (connectGen == browseGeneration) {
                val msg = if (err is TimeoutCancellationException) {
                    "Connection to ${host.displayName} timed out — check Wi-Fi & Companion"
                } else {
                    "${host.displayName}: ${err.message}"
                }
                handleStorageFailure(err, msg)
            }
        }
    }

    fun navigateFolder(path: String) {
        val host = activeHost()
        folderNavJob?.cancel()
        folderNavJob = viewModelScope.launch {
            _ui.update { it.copy(isSpaceHogsMode = false, selectedPaths = emptySet()) }
            loadFolder(host, path, preferences.value)
        }
    }

    fun toggleTreeSort() {
        val host = activeHost()
        val nextSortBySize = !_ui.value.treeSortBySize
        val sortKey = if (nextSortBySize) "size" else "name"
        _ui.update { cur ->
            val resorted = if (nextSortBySize) {
                cur.treeFolders.sortedWith(compareByDescending<FolderItem> { it.sizeBytes }.thenBy { it.name.lowercase() })
            } else {
                cur.treeFolders.sortedBy { it.name.lowercase() }
            }
            cur.copy(treeSortBySize = nextSortBySize, treeFolders = resorted)
        }
        val path = _ui.value.currentPath
        if (path.isNotBlank()) {
            viewModelScope.launch {
                runCatching {
                    resolveTree(host, path, sortKey)
                }.onSuccess { tree ->
                    treeCache.put("${host.hostId}|$path|$sortKey", tree)
                    _ui.update {
                        it.copy(
                            treeFolders = tree.children,
                            currentFolderSizeBytes = maxOf(it.currentFolderSizeBytes, tree.sizeBytes),
                        )
                    }
                }
            }
        }
    }

    fun rescanDisks() {
        val host = activeHost()
        invalidateHostCache(host.hostId)
        viewModelScope.launch {
            runCatching {
                if (!host.isLocalDevice) api.rescan(host)
                delay(250)
                val roots = resolveRoots(host)
                _ui.update {
                    it.copy(
                        drives = roots.drives,
                        quickFolders = roots.quickFolders,
                        isIndexing = roots.indexer.scanning,
                        spaceSavedBytes = roots.spaceSavedBytes,
                        needsAllFilesPermission = host.isLocalDevice && !localRepo.hasAllFilesAccess(),
                        toastMessage = "Refreshed storage on ${host.displayName}",
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
        val host = activeHost()
        val path = _ui.value.currentPath.ifBlank { _ui.value.drives.firstOrNull()?.path.orEmpty() }
        if (path.isBlank()) return

        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true, isSpaceHogsMode = true, selectedPaths = emptySet()) }
            runCatching {
                if (host.isLocalDevice) localRepo.findLargest(path, 80)
                else api.getLargest(host, path, filter = "video", limit = 80)
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
                handleStorageFailure(err, err.message ?: "Scan failed")
            }
        }
    }

    fun exitSpaceHogs() {
        val host = activeHost()
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
        val host = activeHost()
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
        val host = activeHost()
        val path = _ui.value.currentPath
        if (path.isNotBlank() && _ui.value.searchQuery.isNotBlank()) {
            viewModelScope.launch { loadFolder(host, path, preferences.value) }
        }
    }

    private suspend fun loadFolder(host: SavedHost, path: String, prefs: UserPreferences) {
        val myGen = ++browseGeneration
        val treeSort = if (_ui.value.treeSortBySize) "size" else "name"
        val query = _ui.value.searchQuery.trim()
        val recursive = _ui.value.recursiveSearch && query.isNotBlank()
        val fKey = folderCacheKey(host.hostId, path, prefs, if (recursive) "" else query)
        val tKey = treeCacheKey(host.hostId, path, treeSort)
        var cachedList = if (!recursive) folderCache.get(fKey) else null
        var cachedTree = treeCache.get(tKey)
        if (!host.isLocalDevice && ((cachedList == null && !recursive) || cachedTree == null)) {
            withContext(Dispatchers.IO) {
                if (cachedList == null && !recursive) {
                    snapshotDb.getFolderSnapshot(fKey)?.let { raw ->
                        runCatching { api.json.decodeFromString(ListResponse.serializer(), raw) }.getOrNull()
                    }?.also {
                        cachedList = it
                        folderCache.put(fKey, it)
                    }
                }
                if (cachedTree == null) {
                    snapshotDb.getTreeSnapshot(tKey)?.let { raw ->
                        runCatching { api.json.decodeFromString(TreeResponse.serializer(), raw) }.getOrNull()
                    }?.also {
                        cachedTree = it
                        treeCache.put(tKey, it)
                    }
                }
            }
        }

        if (cachedList != null) {
            _ui.update {
                it.copy(
                    isLoading = false,
                    errorMessage = null,
                    needsAllFilesPermission = host.isLocalDevice && !localRepo.hasAllFilesAccess(),
                    currentPath = cachedList.path,
                    folderName = cachedList.name,
                    parentPath = cachedList.parentPath,
                    currentFolderSizeBytes = cachedList.totalSizeBytes,
                    listFolders = cachedList.folders,
                    files = cachedList.files,
                    treeFolders = cachedTree?.children ?: it.treeFolders,
                )
            }
        } else {
            _ui.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    needsAllFilesPermission = host.isLocalDevice && !localRepo.hasAllFilesAccess(),
                )
            }
        }

        runCatching {
            val block: suspend () -> Unit = {
                coroutineScope {
                    val treeDef = async { runCatching { resolveTree(host, path, treeSort) }.getOrNull() }
                    val listDef = async { resolveListFolder(host, path, prefs, if (recursive) "" else query) }
                    val recDef = if (recursive) {
                        async {
                            val searched = if (host.isLocalDevice) {
                                localRepo.searchFiles(query, path, prefs.mediaFilter, 100).files
                            } else {
                                api.search(host, path, query).files
                            }
                            val comparator = when (prefs.sortBy.apiKey) {
                                "size" -> compareBy<FileItem> { it.sizeBytes }
                                "date" -> compareBy<FileItem> { it.modifiedMs }
                                "type" -> compareBy<FileItem> { it.extension.lowercase() }.thenBy { it.name.lowercase() }
                                else -> compareBy<FileItem> { it.name.lowercase() }
                            }
                            if (prefs.sortDescending) searched.sortedWith(comparator.reversed()) else searched.sortedWith(comparator)
                        }
                    } else null

                    val listResp = listDef.await()
                    if (!recursive) {
                        folderCache.put(fKey, listResp)
                        if (!host.isLocalDevice) {
                            viewModelScope.launch(Dispatchers.IO) {
                                runCatching {
                                    snapshotDb.putFolderSnapshot(
                                        fKey,
                                        host.hostId,
                                        api.json.encodeToString(ListResponse.serializer(), listResp),
                                    )
                                }
                            }
                        }
                    }
                    val recursiveFiles = recDef?.await()
                    if (myGen == browseGeneration) {
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
                            )
                        }
                    }
                    val treeResp = treeDef.await()
                    if (treeResp != null) {
                        treeCache.put(tKey, treeResp)
                        if (!host.isLocalDevice) {
                            viewModelScope.launch(Dispatchers.IO) {
                                runCatching {
                                    snapshotDb.putTreeSnapshot(
                                        tKey,
                                        host.hostId,
                                        api.json.encodeToString(TreeResponse.serializer(), treeResp),
                                    )
                                }
                            }
                        }
                    }
                    if (myGen == browseGeneration && treeResp != null) {
                        _ui.update {
                            it.copy(
                                treeFolders = treeResp.children,
                                currentFolderSizeBytes = maxOf(it.currentFolderSizeBytes, treeResp.sizeBytes),
                            )
                        }
                    }
                }
            }
            if (host.isLocalDevice) block() else withTimeout(14_000L) { block() }
        }.onFailure { err ->
            if (err is CancellationException && err !is TimeoutCancellationException) throw err
            val msg = if (err is TimeoutCancellationException) {
                "Timed out loading folder on ${host.displayName}"
            } else {
                err.message ?: "Failed to load folder"
            }
            if (myGen == browseGeneration && cachedList == null) {
                handleStorageFailure(err, msg)
            } else if (myGen == browseGeneration) {
                _ui.update { it.copy(isLoading = false, toastMessage = if (err is TimeoutCancellationException) msg else it.toastMessage) }
            }
        }
    }

    private fun handleStorageFailure(err: Throwable, fallbackMsg: String) {
        if (err is kotlinx.coroutines.CancellationException) return
        when (err) {
            is AllFilesPermissionRequiredException -> {
                _ui.update {
                    it.copy(
                        isLoading = false,
                        isTransferring = false,
                        needsAllFilesPermission = true,
                        errorMessage = "Tap 'Grant All Files Access' above (or browse All Device Videos) to unlock ${File(err.targetPath).name}.",
                    )
                }
            }
            is SdCardWritePermissionRequiredException -> {
                _ui.update {
                    it.copy(
                        isLoading = false,
                        isTransferring = false,
                        pendingSdCardAccess = err.volumeRoot to err.volumeName,
                    )
                }
            }
            else -> {
                _ui.update { it.copy(isLoading = false, isTransferring = false, errorMessage = fallbackMsg) }
            }
        }
    }

    fun toggleSelect(path: String) {
        _ui.update { state ->
            val next = if (path in state.selectedPaths) state.selectedPaths - path else state.selectedPaths + path
            state.copy(selectedPaths = next)
        }
    }

    fun selectAllFiles() {
        _ui.update { state -> state.copy(selectedPaths = state.files.map { it.path }.toSet()) }
    }

    fun clearSelection() {
        _ui.update { it.copy(selectedPaths = emptySet()) }
    }

    fun renameFile(file: FileItem, newName: String) {
        val host = activeHost()
        val trimmed = newName.trim()
        if (trimmed.isBlank() || trimmed == file.name) return

        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true) }
            runCatching {
                if (host.isLocalDevice) {
                    localRepo.renameItem(file.path, trimmed, preferences.value.safSdTreeUris)
                } else {
                    api.renameFile(host, file.path, trimmed)
                }
            }.onSuccess {
                _ui.update { it.copy(toastMessage = "Renamed to $trimmed") }
                refreshAfterMutation(host)
            }.onFailure { err ->
                handleStorageFailure(err, err.message ?: "Rename failed")
            }
        }
    }

    fun deleteSelected(recycle: Boolean) {
        val host = activeHost()
        val paths = _ui.value.selectedPaths.toList()
        if (paths.isEmpty()) return

        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true) }
            runCatching {
                if (host.isLocalDevice) {
                    localRepo.deleteItems(paths, recycle, preferences.value.safSdTreeUris)
                } else {
                    api.deleteFiles(host, paths, recycle)
                }
            }.onSuccess { res ->
                val verb = if (recycle) "Moved to Trash" else "Permanently deleted"
                _ui.update {
                    it.copy(
                        selectedPaths = emptySet(),
                        spaceSavedBytes = maxOf(it.spaceSavedBytes, res.totalSpaceSavedBytes),
                        toastMessage = "$verb ${res.deletedCount} item(s) • Freed ${Formatters.formatBytes(res.freedBytes)}",
                    )
                }
                refreshAfterMutation(host)
            }.onFailure { err ->
                handleStorageFailure(err, err.message ?: "Delete failed")
            }
        }
    }

    fun deleteSingleFileWithMode(file: FileItem, recycle: Boolean) {
        val host = activeHost()
        viewModelScope.launch {
            runCatching {
                if (host.isLocalDevice) {
                    localRepo.deleteItems(listOf(file.path), recycle, preferences.value.safSdTreeUris)
                } else {
                    api.deleteFiles(host, listOf(file.path), recycle)
                }
            }.onSuccess { res ->
                _ui.update {
                    it.copy(
                        activeVideoFile = if (it.activeVideoFile?.path == file.path) null else it.activeVideoFile,
                        activeImageList = it.activeImageList.filterNot { img -> img.path == file.path },
                        activeAudioFile = if (it.activeAudioFile?.path == file.path) null else it.activeAudioFile,
                        activePdfFile = if (it.activePdfFile?.path == file.path) null else it.activePdfFile,
                        activeTextFile = if (it.activeTextFile?.path == file.path) null else it.activeTextFile,
                        activeOtherFile = if (it.activeOtherFile?.path == file.path) null else it.activeOtherFile,
                        toastMessage = "${if (recycle) "Recycled" else "Permanently deleted"} ${file.name} (${Formatters.formatBytes(res.freedBytes)})",
                    )
                }
                refreshAfterMutation(host)
            }.onFailure { err ->
                handleStorageFailure(err, err.message ?: "Delete failed")
            }
        }
    }

    private var videoMetaProbeJob: Job? = null

    fun openVideo(file: FileItem, openTrimmer: Boolean) {
        _ui.update {
            it.copy(
                activeVideoFile = file,
                openPlayerInTrimmer = openTrimmer,
                lastTrimResult = null,
                trimError = null,
            )
        }
        videoMetaProbeJob?.cancel()
        if ((file.meta?.durationMs ?: 0L) <= 0L) {
            val host = activeHost()
            videoMetaProbeJob = viewModelScope.launch {
                val resolved: VideoMeta? = runCatching {
                    if (host.isLocalDevice) {
                        withContext(Dispatchers.IO) { localRepo.probeVideoMeta(file.path) }
                    } else {
                        val sb = api.getStoryboardInfo(host, file.path)
                        if (sb.durationMs > 0L || sb.width > 0) {
                            (file.meta ?: VideoMeta()).copy(
                                durationMs = sb.durationMs.coerceAtLeast(file.meta?.durationMs ?: 0L),
                                width = if (sb.width > 0) sb.width else (file.meta?.width ?: 0),
                                height = if (sb.height > 0) sb.height else (file.meta?.height ?: 0),
                            )
                        } else null
                    }
                }.getOrNull()
                if (resolved != null && (resolved.durationMs > 0L || resolved.width > 0)) {
                    _ui.update { state ->
                        val curActive = state.activeVideoFile
                        val updatedActive = if (curActive?.path == file.path) {
                            curActive.copy(meta = resolved)
                        } else curActive
                        val updatedFiles = state.files.map { f ->
                            if (f.path == file.path) f.copy(meta = resolved) else f
                        }
                        state.copy(activeVideoFile = updatedActive, files = updatedFiles)
                    }
                }
            }
        }
    }

    fun dismissVideoPlayer() {
        _ui.update { it.copy(activeVideoFile = null, trimError = null, lastTrimResult = null) }
    }

    fun saveVideoResumePosition(path: String, posMs: Long) {
        viewModelScope.launch { prefsRepo.saveVideoResumePosition(path, posMs) }
    }

    fun saveVideoSnapshot(path: String, ms: Long) {
        val host = activeHost()
        viewModelScope.launch {
            runCatching {
                if (host.isLocalDevice) {
                    withContext(Dispatchers.IO) {
                        localRepo.saveBitmapSnapshot(path, ms, preferences.value.safSdTreeUris).name
                    }
                } else {
                    val outDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                        ?: getApplication<Application>().filesDir
                    val dest = File(outDir, "snapshot_${ms}.jpg")
                    val frameUrl = api.videoFrameUrl(host, path, ms, 1920)
                    withContext(Dispatchers.IO) {
                        val req = okhttp3.Request.Builder().url(frameUrl).build()
                        api.okHttpClient.newCall(req).execute().use { resp ->
                            if (resp.isSuccessful) {
                                resp.body?.byteStream()?.use { src ->
                                    FileOutputStream(dest).use { dst -> src.copyTo(dst) }
                                }
                            }
                        }
                        localRepo.scanMediaPath(dest.absolutePath)
                    }
                    dest.name
                }
            }.onSuccess { snapName ->
                _ui.update { it.copy(toastMessage = "Saved frame snapshot: $snapName") }
                if (host.isLocalDevice) refreshAfterMutation(host)
            }.onFailure { err ->
                handleStorageFailure(err, "Snapshot failed: ${err.message}")
            }
        }
    }

    // OPTIMIZER & MERGE DIALOGS
    fun openOptimizer(file: FileItem, initialTab: OptimizerTab = OptimizerTab.COMPRESS) {
        _ui.update { it.copy(optimizerFile = file, optimizerTab = initialTab) }
    }

    fun closeOptimizer() {
        _ui.update { it.copy(optimizerFile = null) }
    }

    fun openMergeForSelection() {
        val selected = _ui.value.files.filter { it.path in _ui.value.selectedPaths && it.isVideo }
        if (selected.size >= 2) {
            _ui.update { it.copy(mergeCandidates = selected) }
        }
    }

    fun closeMergeDialog() {
        _ui.update { it.copy(mergeCandidates = emptyList()) }
    }

    fun openQueueSheet() {
        _ui.update { it.copy(showQueueSheet = true) }
    }

    fun closeQueueSheet() {
        _ui.update { it.copy(showQueueSheet = false) }
    }

    suspend fun estimateCompression(req: CompressEstimateRequest): CompressEstimateResponse? {
        val host = activeHost()
        return if (host.isLocalDevice) {
            withContext(Dispatchers.IO) { localMedia.estimateCompression(req) }
        } else {
            runCatching { api.estimateCompression(host, req) }.getOrNull()
        }
    }

    // QUEUE & RENDER SUBMISSIONS
    fun submitStudioEdit(req: EditStudioRequest, target: JobExecutionTarget, offloadHost: SavedHost?, runNow: Boolean) {
        val name = File(req.path).name.ifBlank { "Video" }
        enqueueUnifiedJob(
            type = "edit",
            title = "Edit Studio: $name",
            target = target,
            offloadHost = offloadHost,
            sourcePath = req.path,
            overwriteLocalOriginal = req.saveMode == "overwrite",
            localSpec = LocalTaskSpec.Edit(req),
            remotePayload = buildJsonObject {
                put("path", req.path)
                put("startMs", req.startMs)
                put("endMs", req.endMs)
                req.crop?.let { c ->
                    put("crop", buildJsonObject {
                        put("x", c.x); put("y", c.y); put("w", c.w); put("h", c.h)
                    })
                }
                put("rotate", req.rotate)
                put("flipH", req.flipH)
                put("flipV", req.flipV)
                put("speed", req.speed)
                put("muteAudio", req.muteAudio)
                put("mode", req.mode)
                put("crf", req.crf)
                put("saveMode", req.saveMode)
                put("recycleOriginal", req.recycleOriginal)
                put("brightness", req.brightness)
                put("contrast", req.contrast)
                put("saturation", req.saturation)
                put("sharpen", req.sharpen)
                put("deband", req.deband)
                put("stabilize", req.stabilize)
                put("fadeSec", req.fadeSec)
                put("normalizeAudio", req.normalizeAudio)
            },
            runNow = runNow,
        )
    }

    fun submitCompress(req: CompressRequest, target: JobExecutionTarget, offloadHost: SavedHost?, runNow: Boolean) {
        val name = File(req.path).name.ifBlank { "Media" }
        _ui.update { it.copy(optimizerFile = null) }
        enqueueUnifiedJob(
            type = "compress",
            title = "Compress: $name",
            target = target,
            offloadHost = offloadHost,
            sourcePath = req.path,
            overwriteLocalOriginal = req.saveMode == "overwrite",
            localSpec = LocalTaskSpec.Compress(req),
            remotePayload = buildJsonObject {
                put("path", req.path)
                put("targetHeight", req.targetHeight)
                put("crf", req.crf)
                put("codec", req.codec)
                put("audioKbps", req.audioKbps)
                put("targetMb", req.targetMb)
                put("saveMode", req.saveMode)
                put("recycleOriginal", req.recycleOriginal)
                put("quality", req.quality)
            },
            runNow = runNow,
        )
    }

    fun submitUpscale(req: UpscaleRequest, target: JobExecutionTarget, offloadHost: SavedHost?, runNow: Boolean) {
        val name = File(req.path).name.ifBlank { "Media" }
        _ui.update { it.copy(optimizerFile = null) }
        enqueueUnifiedJob(
            type = "upscale",
            title = "Upscale (${req.preset.uppercase()}): $name",
            target = target,
            offloadHost = offloadHost,
            sourcePath = req.path,
            overwriteLocalOriginal = req.saveMode == "overwrite",
            localSpec = LocalTaskSpec.Upscale(req),
            remotePayload = buildJsonObject {
                put("path", req.path)
                put("preset", req.preset)
                put("sharpen", req.sharpen)
                put("denoise", req.denoise)
                put("fps60", req.fps60)
                put("crf", req.crf)
                put("saveMode", req.saveMode)
                put("recycleOriginal", req.recycleOriginal)
                put("mode", req.mode)
                put("denoiseMode", req.denoiseMode)
                put("deband", req.deband)
                put("stabilize", req.stabilize)
            },
            runNow = runNow,
        )
    }

    fun submitExtractAudio(req: ExtractAudioRequest, target: JobExecutionTarget, offloadHost: SavedHost?) {
        val name = File(req.path).name.ifBlank { "Video" }
        enqueueUnifiedJob(
            type = "extract_audio",
            title = "Extract Audio (${req.format.uppercase()}): $name",
            target = target,
            offloadHost = offloadHost,
            sourcePath = req.path,
            overwriteLocalOriginal = false,
            localSpec = LocalTaskSpec.ExtractAudio(req),
            remotePayload = buildJsonObject {
                put("path", req.path)
                put("format", req.format)
                put("bitrateKbps", req.bitrateKbps)
                put("normalize", req.normalize)
                put("startMs", req.startMs)
                put("endMs", req.endMs)
            },
            runNow = true,
        )
    }

    fun submitExportGif(req: GifExportRequest, offloadHost: SavedHost?) {
        val name = File(req.path).name.ifBlank { "Video" }
        val host = activeHost()
        val isAndroidSource = host.isAndroidDevice
        val tgt = if (isAndroidSource) JobExecutionTarget.PC_OFFLOAD else JobExecutionTarget.PC_REMOTE
        val pc = if (isAndroidSource) (offloadHost ?: pairedPcHosts().firstOrNull()) else host
        if (pc == null) {
            _ui.update { it.copy(toastMessage = "Pair a PC host to export animated GIFs") }
            return
        }
        enqueueUnifiedJob(
            type = "gif",
            title = "GIF Clip: $name",
            target = tgt,
            offloadHost = pc,
            sourcePath = req.path,
            overwriteLocalOriginal = false,
            localSpec = null,
            remotePayload = buildJsonObject {
                put("path", req.path)
                put("startMs", req.startMs)
                put("endMs", req.endMs)
                put("fps", req.fps)
                put("width", req.width)
                req.crop?.let { c ->
                    put("crop", buildJsonObject { put("x", c.x); put("y", c.y); put("w", c.w); put("h", c.h) })
                }
                put("rotate", req.rotate)
                put("flipH", req.flipH)
                put("flipV", req.flipV)
                put("speed", req.speed)
            },
            runNow = true,
        )
    }

    fun submitMergeVideos(
        clips: List<FileItem>,
        outputName: String,
        target: JobExecutionTarget,
        offloadHost: SavedHost?,
        runNow: Boolean,
    ) {
        if (clips.size < 2) return
        val firstDir = File(clips.first().path).parent.orEmpty()
        _ui.update { it.copy(mergeCandidates = emptyList(), selectedPaths = emptySet()) }
        val jobId = "q_${UUID.randomUUID().toString().take(10)}"
        val srcHost = activeHost()
        val cleanOut = outputName.trim().ifBlank { "Merged_Video.mp4" }.let {
            if (it.endsWith(".mp4", true)) it else "$it.mp4"
        }
        val item = RenderQueueItem(
            id = jobId,
            type = "merge",
            title = "Merge ${clips.size} clips -> $cleanOut",
            target = target,
            status = "queued",
            message = "Queued",
            sourceHostId = srcHost.hostId,
            sourcePath = clips.first().path,
            outputHostId = if (target == JobExecutionTarget.ON_DEVICE || target == JobExecutionTarget.PC_OFFLOAD) SavedHost.LOCAL_DEVICE_ID else srcHost.hostId,
            localDestDir = firstDir,
        )
        prependQueueJob(item)

        val mergeSpec = LocalTaskSpec.Merge(
            MergeRequest(paths = clips.map { it.path }, outDir = firstDir, outputName = cleanOut)
        )
        val mergePayload = buildJsonObject {
            put("outDir", firstDir)
            put("outputName", cleanOut)
            put("crf", 20)
            put(
                "paths",
                kotlinx.serialization.json.buildJsonArray {
                    clips.forEach { add(JsonPrimitive(it.path)) }
                },
            )
        }
        val effectiveTarget = if (target == JobExecutionTarget.PC_REMOTE && srcHost.isAndroidDevice) {
            JobExecutionTarget.ON_DEVICE
        } else {
            target
        }

        when (effectiveTarget) {
            JobExecutionTarget.ON_DEVICE -> {
                if (srcHost.isLocalDevice) {
                    localTasks[jobId] = mergeSpec
                    localQueueChannel.trySend(jobId)
                } else {
                    val job = viewModelScope.launch {
                        runRemotePullAndLocalProcess(jobId, srcHost, "merge", clips.map { it.path }, mergeSpec)
                    }
                    runningCoroutines[jobId] = job
                }
            }
            JobExecutionTarget.PC_REMOTE -> {
                val job = viewModelScope.launch {
                    runRemoteHostJob(jobId, srcHost, "merge", mergePayload, clips.map { it.path }, mergeSpec)
                }
                runningCoroutines[jobId] = job
            }
            JobExecutionTarget.PC_OFFLOAD -> {
                val pc = offloadHost ?: pairedPcHosts().firstOrNull()
                if (pc == null) {
                    updateQueueJob(jobId) { it.copy(status = "error", error = "No paired PC available for offload") }
                    return
                }
                val job = viewModelScope.launch {
                    val offloadMeta = buildJsonObject {
                        put("outputName", cleanOut)
                        put("crf", 20)
                    }
                    if (srcHost.isLocalDevice) {
                        runPcOffloadPipeline(
                            jobId = jobId,
                            jobType = "merge",
                            pcHost = pc,
                            localSourcePaths = clips.map { it.path },
                            localDestDir = firstDir,
                            overwriteOriginal = false,
                            payload = offloadMeta,
                        )
                    } else {
                        runRemoteToPcOffloadPipeline(
                            jobId = jobId,
                            jobType = "merge",
                            srcHost = srcHost,
                            remotePaths = clips.map { it.path },
                            pcHost = pc,
                            payload = offloadMeta,
                        )
                    }
                }
                runningCoroutines[jobId] = job
            }
        }
        _ui.update {
            it.copy(
                showQueueSheet = runNow,
                toastMessage = if (runNow) "Started ${item.title}" else "Added to Queue: ${item.title}",
            )
        }
    }

    private fun enqueueUnifiedJob(
        type: String,
        title: String,
        target: JobExecutionTarget,
        offloadHost: SavedHost?,
        sourcePath: String,
        overwriteLocalOriginal: Boolean,
        localSpec: LocalTaskSpec?,
        remotePayload: JsonObject,
        runNow: Boolean,
    ) {
        val srcHost = activeHost()
        val jobId = "q_${UUID.randomUUID().toString().take(10)}"
        val localDir = if (!LocalStorageRepository.isContentUriPath(sourcePath) && !LocalStorageRepository.isVirtualMediaStorePath(sourcePath)) {
            File(sourcePath).parent.orEmpty()
        } else ""

        val effectiveTarget = if (target == JobExecutionTarget.PC_REMOTE && srcHost.isAndroidDevice) {
            JobExecutionTarget.ON_DEVICE
        } else {
            target
        }

        val item = RenderQueueItem(
            id = jobId,
            type = type,
            title = title,
            target = effectiveTarget,
            status = "queued",
            message = "Queued",
            sourceHostId = srcHost.hostId,
            sourcePath = sourcePath,
            outputHostId = if (effectiveTarget == JobExecutionTarget.PC_REMOTE) srcHost.hostId else SavedHost.LOCAL_DEVICE_ID,
            offloadHost = offloadHost,
            localDestDir = if (srcHost.isLocalDevice) localDir else defaultLocalMoviesDir().absolutePath,
            overwriteLocalOriginal = if (srcHost.isLocalDevice) overwriteLocalOriginal else false,
        )
        prependQueueJob(item)

        when (effectiveTarget) {
            JobExecutionTarget.ON_DEVICE -> {
                if (srcHost.isLocalDevice) {
                    if (localSpec != null) {
                        localTasks[jobId] = localSpec
                        localQueueChannel.trySend(jobId)
                    } else {
                        updateQueueJob(jobId) { it.copy(status = "error", error = "This action requires a paired PC host") }
                    }
                } else {
                    val job = viewModelScope.launch {
                        runRemotePullAndLocalProcess(jobId, srcHost, type, listOf(sourcePath), localSpec)
                    }
                    runningCoroutines[jobId] = job
                }
            }
            JobExecutionTarget.PC_REMOTE -> {
                val job = viewModelScope.launch {
                    runRemoteHostJob(jobId, srcHost, type, remotePayload, listOf(sourcePath), localSpec)
                }
                runningCoroutines[jobId] = job
            }
            JobExecutionTarget.PC_OFFLOAD -> {
                val pc = offloadHost ?: pairedPcHosts().firstOrNull()
                if (pc == null) {
                    updateQueueJob(jobId) { it.copy(status = "error", error = "No paired PC selected for offload") }
                    return
                }
                val job = viewModelScope.launch {
                    if (srcHost.isLocalDevice) {
                        runPcOffloadPipeline(
                            jobId = jobId,
                            jobType = type,
                            pcHost = pc,
                            localSourcePaths = listOf(sourcePath),
                            localDestDir = localDir,
                            overwriteOriginal = overwriteLocalOriginal,
                            payload = remotePayload,
                        )
                    } else {
                        runRemoteToPcOffloadPipeline(
                            jobId = jobId,
                            jobType = type,
                            srcHost = srcHost,
                            remotePaths = listOf(sourcePath),
                            pcHost = pc,
                            payload = remotePayload,
                        )
                    }
                }
                runningCoroutines[jobId] = job
            }
        }
        _ui.update {
            it.copy(
                showQueueSheet = runNow,
                toastMessage = if (runNow) "Started: $title" else "Added to Queue: $title",
            )
        }
    }

    private fun defaultLocalMoviesDir(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "DeckVault").apply { mkdirs() }

    private fun remotePathFileName(remotePath: String, fallback: String): String =
        remotePath.substringAfterLast('/').substringAfterLast('\\').ifBlank { fallback }

    private fun remotePathParentDir(remotePath: String): String {
        val slash = maxOf(remotePath.lastIndexOf('/'), remotePath.lastIndexOf('\\'))
        return if (slash > 0) remotePath.substring(0, slash) else ""
    }

    private suspend fun executeLocalMediaSpec(
        jobId: String,
        spec: LocalTaskSpec,
        safMap: Map<String, String>,
        onProgress: (pct: Double, msg: String) -> Unit,
    ): MediaOpResponse = when (spec) {
        is LocalTaskSpec.Edit -> localMedia.editVideo(jobId, spec.req, safMap, onProgress)
        is LocalTaskSpec.Compress -> localMedia.compressMedia(jobId, spec.req, safMap, onProgress)
        is LocalTaskSpec.Upscale -> localMedia.upscaleMedia(jobId, spec.req, safMap, onProgress)
        is LocalTaskSpec.ExtractAudio -> localMedia.extractAudio(jobId, spec.req, safMap, onProgress)
        is LocalTaskSpec.Merge -> localMedia.mergeVideos(jobId, spec.req, safMap, onProgress)
    }

    private suspend fun runLocalTask(jobId: String, spec: LocalTaskSpec) {
        updateQueueJob(jobId) { it.copy(status = "running", progressPct = 2.0, message = "Starting on device...") }
        val safMap = preferences.value.safSdTreeUris
        try {
            val res = executeLocalMediaSpec(jobId, spec, safMap) { p, msg ->
                updateQueueJob(jobId) { it.copy(progressPct = p, message = msg) }
            }
            val outFile = File(res.path)
            updateQueueJob(jobId) {
                it.copy(
                    status = "done",
                    progressPct = 100.0,
                    message = "Completed",
                    outputHostId = SavedHost.LOCAL_DEVICE_ID,
                    outputPath = res.path,
                    outputDir = outFile.parent.orEmpty(),
                    outputName = res.name.ifBlank { outFile.name },
                    outputMediaType = LocalStorageRepository.classifyMedia(res.name.ifBlank { outFile.name }),
                    newSizeBytes = res.newSizeBytes,
                    savedBytes = res.savedBytes,
                )
            }
            if (res.savedBytes > 0L) {
                _ui.update { it.copy(spaceSavedBytes = it.spaceSavedBytes + res.savedBytes, cacheVersion = System.currentTimeMillis()) }
            }
            if (activeHost().isLocalDevice) refreshAfterMutation(activeHost())
        } catch (_: CancellationException) {
            updateQueueJob(jobId) { it.copy(status = "cancelled", message = "Cancelled") }
        } catch (err: SdCardWritePermissionRequiredException) {
            _ui.update { it.copy(pendingSdCardAccess = err.volumeRoot to err.volumeName) }
            updateQueueJob(jobId) { it.copy(status = "error", message = "SD permission needed", error = err.message ?: "SD permission needed") }
        } catch (err: Exception) {
            updateQueueJob(jobId) { it.copy(status = "error", message = "Failed", error = err.message ?: "Processing error") }
        }
    }

    private suspend fun runRemoteHostJob(
        jobId: String,
        host: SavedHost,
        jobType: String,
        payload: JsonObject,
        remotePaths: List<String> = emptyList(),
        localSpecFallback: LocalTaskSpec? = null,
    ) {
        if (host.isAndroidDevice) {
            runRemotePullAndLocalProcess(jobId, host, jobType, remotePaths, localSpecFallback)
            return
        }
        try {
            updateQueueJob(jobId) { it.copy(status = "running", progressPct = 2.0, message = "Queuing on ${host.displayName}...") }
            val finished = try {
                val enqueued = api.enqueueRemoteJob(host, jobType, payload)
                val remoteId = enqueued.job?.id ?: throw IllegalStateException("Server did not return job id")
                updateQueueJob(jobId) { it.copy(remoteJobId = remoteId) }
                pollRemoteJobToCompletion(jobId, host, remoteId, 0.0, 1.0)
            } catch (httpErr: HttpStatusException) {
                if (httpErr.code != 404) throw httpErr
                // Companion host < v1.4 lacks /api/jobs/enqueue — fall back cleanly to synchronous v1.2/v1.3 endpoints
                runLegacySyncRemoteJob(jobId, host, jobType, payload)
            }
            updateQueueJob(jobId) {
                it.copy(
                    status = "done",
                    progressPct = 100.0,
                    message = "Completed",
                    outputHostId = host.hostId,
                    outputPath = finished.outputPath,
                    outputDir = finished.outputDir,
                    outputName = finished.outputName,
                    outputMediaType = finished.outputMediaType,
                    newSizeBytes = finished.newSizeBytes,
                    savedBytes = finished.savedBytes,
                )
            }
            if (activeHost().hostId == host.hostId) refreshAfterMutation(host)
        } catch (_: CancellationException) {
            updateQueueJob(jobId) { it.copy(status = "cancelled", message = "Cancelled") }
        } catch (httpErr: HttpStatusException) {
            if (httpErr.code == 404 && localSpecFallback != null && remotePaths.isNotEmpty() && jobType != "gif") {
                runRemotePullAndLocalProcess(jobId, host, jobType, remotePaths, localSpecFallback)
            } else {
                val msg = if (httpErr.code == 404) {
                    "Host ${host.displayName} does not support $jobType (try updating DeckVault Desktop or pick Save on Device)"
                } else {
                    httpErr.message ?: "Remote job error"
                }
                updateQueueJob(jobId) { it.copy(status = "error", message = "Failed", error = msg) }
            }
        } catch (err: Exception) {
            updateQueueJob(jobId) { it.copy(status = "error", message = "Failed", error = err.message ?: "Remote job error") }
        }
    }

    private suspend fun runLegacySyncRemoteJob(
        jobId: String,
        host: SavedHost,
        jobType: String,
        payload: JsonObject,
    ): com.deckvault.app.model.JobRecord {
        updateQueueJob(jobId) { it.copy(status = "running", progressPct = 20.0, message = "Rendering on ${host.displayName}...") }
        val res: MediaOpResponse = when (jobType) {
            "edit" -> api.editStudio(host, api.json.decodeFromJsonElement(EditStudioRequest.serializer(), payload))
            "compress" -> api.compressMedia(host, api.json.decodeFromJsonElement(CompressRequest.serializer(), payload))
            "upscale" -> api.upscaleMedia(host, api.json.decodeFromJsonElement(UpscaleRequest.serializer(), payload))
            "extract_audio" -> api.extractAudio(host, api.json.decodeFromJsonElement(ExtractAudioRequest.serializer(), payload))
            "gif" -> api.exportGif(host, api.json.decodeFromJsonElement(GifExportRequest.serializer(), payload))
            "merge" -> api.mergeVideos(host, api.json.decodeFromJsonElement(MergeRequest.serializer(), payload))
            else -> throw HttpStatusException(404, "HTTP 404: Unknown job type $jobType")
        }
        val outName = res.name.ifBlank { remotePathFileName(res.path, "DeckVault_Out.mp4") }
        return com.deckvault.app.model.JobRecord(
            id = jobId,
            type = jobType,
            status = "done",
            progressPct = 100.0,
            message = "Completed",
            outputPath = res.path,
            outputDir = remotePathParentDir(res.path),
            outputName = outName,
            outputMediaType = LocalStorageRepository.classifyMedia(outName),
            newSizeBytes = res.newSizeBytes,
            savedBytes = res.savedBytes,
        )
    }

    private suspend fun stageRemotePathsToCache(
        jobId: String,
        srcHost: SavedHost,
        remotePaths: List<String>,
        stageDir: File,
        basePct: Double,
        weightPct: Double,
    ): List<String> {
        val stagedFiles = mutableListOf<String>()
        remotePaths.forEachIndexed { idx, rPath ->
            if (cancelledIds.contains(jobId)) throw CancellationException("Cancelled")
            val origName = remotePathFileName(rPath, "media_${idx + 1}.mp4")
            val dest = File(stageDir, origName).let { candidate ->
                if (!candidate.exists()) candidate
                else File(stageDir, "${idx}_$origName")
            }
            api.downloadFile(srcHost, rPath, openOutput = { FileOutputStream(dest) }) { bytes ->
                val frac = ((idx.toDouble() + 0.5) / remotePaths.size.coerceAtLeast(1).toDouble()).coerceIn(0.0, 1.0)
                updateQueueJob(jobId) {
                    it.copy(
                        status = "downloading",
                        progressPct = basePct + frac * weightPct,
                        message = "Pulling from ${srcHost.displayName} (${Formatters.formatBytes(bytes)})...",
                    )
                }
            }
            stagedFiles.add(dest.absolutePath)
        }
        return stagedFiles
    }

    private suspend fun runRemotePullAndLocalProcess(
        jobId: String,
        srcHost: SavedHost,
        jobType: String,
        remotePaths: List<String>,
        spec: LocalTaskSpec?,
    ) {
        if (spec == null || jobType == "gif") {
            updateQueueJob(jobId) { it.copy(status = "error", message = "Failed", error = "GIF export requires a paired PC host") }
            return
        }
        val stageDir = File(getApplication<Application>().cacheDir, "remote_local_$jobId").apply { mkdirs() }
        val safMap = preferences.value.safSdTreeUris
        try {
            val stagedLocalPaths = stageRemotePathsToCache(jobId, srcHost, remotePaths, stageDir, basePct = 2.0, weightPct = 28.0)
            if (cancelledIds.contains(jobId)) throw CancellationException("Cancelled")

            val destDir = defaultLocalMoviesDir()
            val rewrittenSpec = when (spec) {
                is LocalTaskSpec.Edit -> LocalTaskSpec.Edit(spec.req.copy(path = stagedLocalPaths.first(), saveMode = "copy"))
                is LocalTaskSpec.Compress -> LocalTaskSpec.Compress(spec.req.copy(path = stagedLocalPaths.first(), saveMode = "copy"))
                is LocalTaskSpec.Upscale -> LocalTaskSpec.Upscale(spec.req.copy(path = stagedLocalPaths.first(), saveMode = "copy"))
                is LocalTaskSpec.ExtractAudio -> LocalTaskSpec.ExtractAudio(spec.req.copy(path = stagedLocalPaths.first()))
                is LocalTaskSpec.Merge -> LocalTaskSpec.Merge(spec.req.copy(paths = stagedLocalPaths, outDir = destDir.absolutePath))
            }

            updateQueueJob(jobId) { it.copy(status = "running", progressPct = 32.0, message = "Rendering on this device...") }
            val res = executeLocalMediaSpec(jobId, rewrittenSpec, safMap) { p, msg ->
                updateQueueJob(jobId) { it.copy(progressPct = 32.0 + (p * 0.62), message = msg) }
            }

            val produced = File(res.path)
            val finalFile = if (produced.parentFile?.absolutePath == stageDir.absolutePath) {
                val targetName = res.name.ifBlank { produced.name }
                val outTarget = localRepo.resolveUniqueTargetFile(destDir, targetName, "rename")
                FileInputStream(produced).use { inp ->
                    localRepo.openOutputStreamWritable(outTarget, safMap).use { out ->
                        inp.copyTo(out, 256 * 1024)
                        out.flush()
                    }
                }
                localRepo.scanMediaPath(outTarget.absolutePath)
                outTarget
            } else {
                produced
            }
            val finalSize = finalFile.length().takeIf { it > 0L } ?: res.newSizeBytes
            updateQueueJob(jobId) {
                it.copy(
                    status = "done",
                    progressPct = 100.0,
                    message = "Saved to ${destDir.name}",
                    outputHostId = SavedHost.LOCAL_DEVICE_ID,
                    outputPath = finalFile.absolutePath,
                    outputDir = finalFile.parent.orEmpty(),
                    outputName = finalFile.name,
                    outputMediaType = LocalStorageRepository.classifyMedia(finalFile.name),
                    newSizeBytes = finalSize,
                    savedBytes = 0L,
                )
            }
            if (activeHost().isLocalDevice) refreshAfterMutation(activeHost())
        } catch (_: CancellationException) {
            updateQueueJob(jobId) { it.copy(status = "cancelled", message = "Cancelled") }
        } catch (err: Exception) {
            updateQueueJob(jobId) { it.copy(status = "error", message = "Failed", error = err.message ?: "Processing error") }
        } finally {
            runCatching { stageDir.deleteRecursively() }
        }
    }

    private suspend fun runRemoteToPcOffloadPipeline(
        jobId: String,
        jobType: String,
        srcHost: SavedHost,
        remotePaths: List<String>,
        pcHost: SavedHost,
        payload: JsonObject,
    ) {
        val stageDir = File(getApplication<Application>().cacheDir, "remote_offload_$jobId").apply { mkdirs() }
        try {
            val stagedLocalPaths = stageRemotePathsToCache(jobId, srcHost, remotePaths, stageDir, basePct = 1.0, weightPct = 18.0)
            runPcOffloadPipeline(
                jobId = jobId,
                jobType = jobType,
                pcHost = pcHost,
                localSourcePaths = stagedLocalPaths,
                localDestDir = defaultLocalMoviesDir().absolutePath,
                overwriteOriginal = false,
                payload = payload,
            )
        } catch (_: CancellationException) {
            updateQueueJob(jobId) { it.copy(status = "cancelled", message = "Cancelled") }
        } catch (err: Exception) {
            updateQueueJob(jobId) { it.copy(status = "error", message = "Failed", error = err.message ?: "Offload error") }
        } finally {
            runCatching { stageDir.deleteRecursively() }
        }
    }

    private suspend fun runPcOffloadPipeline(
        jobId: String,
        jobType: String,
        pcHost: SavedHost,
        localSourcePaths: List<String>,
        localDestDir: String,
        overwriteOriginal: Boolean,
        payload: JsonObject,
    ) {
        val stageId = "mob_${UUID.randomUUID().toString().replace("-", "").take(10)}"
        updateQueueJob(jobId) { it.copy(offloadStageId = stageId, status = "uploading", progressPct = 1.0, message = "Uploading to ${pcHost.displayName}...") }
        try {
            val totalUploadBytes = localSourcePaths.sumOf { localRepo.getFileLengthAny(it).coerceAtLeast(1L) }.coerceAtLeast(1L)
            var uploadedBefore = 0L
            val stagedNames = mutableListOf<String>()

            localSourcePaths.forEachIndexed { idx, srcPath ->
                if (cancelledIds.contains(jobId)) throw CancellationException("Cancelled")
                val fileLen = localRepo.getFileLengthAny(srcPath).coerceAtLeast(1L)
                val fname = if (LocalStorageRepository.isContentUriPath(srcPath)) {
                    "clip_${idx + 1}.mp4"
                } else {
                    File(srcPath).name
                }
                val stageRes = api.stageOffloadFile(
                    host = pcHost,
                    stageId = stageId,
                    fileName = fname,
                    contentLength = localRepo.getFileLengthAny(srcPath),
                    openStream = { localRepo.openInputStreamAny(srcPath) },
                ) { written ->
                    val frac = ((uploadedBefore + written).toDouble() / totalUploadBytes.toDouble()).coerceIn(0.0, 1.0)
                    val pct = (frac * 28.0)
                    updateQueueJob(jobId) {
                        it.copy(progressPct = pct, message = "Uploading to PC ${(frac * 100).roundToInt()}%")
                    }
                }
                stagedNames.add(stageRes.stagedName)
                uploadedBefore += fileLen
            }

            if (cancelledIds.contains(jobId)) throw CancellationException("Cancelled")
            updateQueueJob(jobId) { it.copy(status = "running", progressPct = 30.0, message = "Starting on ${pcHost.displayName}...") }

            val enqueued = api.enqueueOffloadJob(
                host = pcHost,
                stageId = stageId,
                jobType = jobType,
                stagedName = if (jobType != "merge") stagedNames.first() else null,
                stagedNames = if (jobType == "merge") stagedNames else emptyList(),
                payloadJson = payload,
            )
            val remoteId = enqueued.job?.id ?: throw IllegalStateException("Offload queue failed to start")
            updateQueueJob(jobId) { it.copy(remoteJobId = remoteId) }

            val finishedRemote = pollRemoteJobToCompletion(jobId, pcHost, remoteId, basePct = 30.0, scaleFactor = 0.50)
            if (cancelledIds.contains(jobId)) throw CancellationException("Cancelled")

            val outRemotePath = finishedRemote.outputPath
            if (outRemotePath.isBlank()) throw IllegalStateException("PC finished without an output file path")

            updateQueueJob(jobId) { it.copy(status = "downloading", progressPct = 80.0, message = "Returning result to Tablet...") }

            val targetFolder = if (localDestDir.isNotBlank() && !LocalStorageRepository.isVirtualMediaStorePath(localDestDir)) {
                File(localDestDir)
            } else {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "DeckVault").apply { mkdirs() }
            }
            val remoteFileName = finishedRemote.outputName.ifBlank { File(outRemotePath).name }.ifBlank { "DeckVault_Offload.mp4" }
            val safMap = preferences.value.safSdTreeUris
            val expectedOutBytes = finishedRemote.newSizeBytes.coerceAtLeast(1L)

            val desiredTarget = if (overwriteOriginal && localSourcePaths.size == 1 && !LocalStorageRepository.isContentUriPath(localSourcePaths.first())) {
                val orig = File(localSourcePaths.first())
                val stem = orig.nameWithoutExtension
                val ext = remoteFileName.substringAfterLast('.', "mp4")
                localRepo.resolveUniqueTargetFile(targetFolder, "$stem.$ext", "rename")
            } else {
                localRepo.resolveUniqueTargetFile(targetFolder, remoteFileName, "rename")
            }

            val finalBytes = api.downloadFile(
                host = pcHost,
                remotePath = outRemotePath,
                openOutput = { localRepo.openOutputStreamWritable(desiredTarget, safMap) },
            ) { read ->
                val frac = (read.toDouble() / expectedOutBytes.toDouble()).coerceIn(0.0, 1.0)
                updateQueueJob(jobId) { it.copy(progressPct = 80.0 + frac * 18.0, message = "Saving back ${(frac * 100).roundToInt()}%") }
            }

            if (overwriteOriginal && localSourcePaths.size == 1 && !LocalStorageRepository.isContentUriPath(localSourcePaths.first())) {
                runCatching { localRepo.deleteItems(listOf(localSourcePaths.first()), recycle = true, safTreeUris = safMap) }
            }
            localRepo.scanMediaPath(desiredTarget.absolutePath)
            val oldLocal = localSourcePaths.sumOf { localRepo.getFileLengthAny(it).coerceAtLeast(0L) }
            val saved = if (overwriteOriginal) (oldLocal - finalBytes).coerceAtLeast(0L) else 0L

            updateQueueJob(jobId) {
                it.copy(
                    status = "done",
                    progressPct = 100.0,
                    message = "Returned to Tablet",
                    outputHostId = SavedHost.LOCAL_DEVICE_ID,
                    outputPath = desiredTarget.absolutePath,
                    outputDir = desiredTarget.parent.orEmpty(),
                    outputName = desiredTarget.name,
                    outputMediaType = LocalStorageRepository.classifyMedia(desiredTarget.name),
                    newSizeBytes = finalBytes,
                    savedBytes = saved,
                )
            }
            _ui.update { it.copy(cacheVersion = System.currentTimeMillis(), toastMessage = "Offload complete: ${desiredTarget.name}") }
            if (activeHost().isLocalDevice) refreshAfterMutation(activeHost())
        } catch (_: CancellationException) {
            updateQueueJob(jobId) { it.copy(status = "cancelled", message = "Cancelled") }
        } catch (err: SdCardWritePermissionRequiredException) {
            _ui.update { it.copy(pendingSdCardAccess = err.volumeRoot to err.volumeName) }
            updateQueueJob(jobId) { it.copy(status = "error", message = "SD permission needed", error = err.message ?: "") }
        } catch (err: Exception) {
            updateQueueJob(jobId) { it.copy(status = "error", message = "Offload failed", error = err.message ?: "Offload error") }
        } finally {
            api.cleanupOffloadStage(pcHost, stageId)
        }
    }

    private suspend fun pollRemoteJobToCompletion(
        localJobId: String,
        host: SavedHost,
        remoteJobId: String,
        basePct: Double,
        scaleFactor: Double,
    ): com.deckvault.app.model.JobRecord {
        while (true) {
            if (cancelledIds.contains(localJobId)) throw CancellationException("Cancelled")
            val snap = api.getJobsSnapshot(host)
            val rec = snap.jobs.firstOrNull { it.id == remoteJobId }
                ?: throw IllegalStateException("Remote job disappeared")
            val scaledPct = (basePct + rec.progressPct * scaleFactor).coerceIn(0.0, 99.0)
            updateQueueJob(localJobId) {
                it.copy(
                    progressPct = scaledPct,
                    speed = rec.speed,
                    remainingSec = rec.remainingSec?.roundToInt(),
                    message = "PC: ${rec.message}",
                )
            }
            when (rec.status) {
                "done" -> return rec
                "error" -> throw IllegalStateException(rec.error.ifBlank { "PC FFmpeg failed" })
                "cancelled" -> throw CancellationException("Cancelled on PC")
            }
            delay(650L)
        }
    }

    fun cancelQueueJob(item: RenderQueueItem) {
        cancelledIds.add(item.id)
        localTasks.remove(item.id)
        localMedia.cancel(item.id)
        runningCoroutines.remove(item.id)?.cancel()
        val pc = item.offloadHost ?: preferences.value.hosts.firstOrNull { it.hostId == item.outputHostId && !it.isLocalDevice }
        if (item.remoteJobId != null && pc != null) {
            viewModelScope.launch {
                runCatching { api.cancelRemoteJob(pc, item.remoteJobId) }
                if (!item.offloadStageId.isNullOrBlank()) {
                    api.cleanupOffloadStage(pc, item.offloadStageId)
                }
            }
        }
        updateQueueJob(item.id) { it.copy(status = "cancelled", message = "Cancelled") }
    }

    fun clearFinishedQueueJobs() {
        _ui.update { state ->
            state.copy(
                queueJobs = state.queueJobs.filterNot { it.status in listOf("done", "error", "cancelled") }
            )
        }
    }

    fun playQueueOutput(item: RenderQueueItem) {
        if (item.outputPath.isBlank()) return
        val fakeFile = FileItem(
            name = item.outputName.ifBlank { File(item.outputPath).name },
            path = item.outputPath,
            sizeBytes = item.newSizeBytes,
            mediaType = item.outputMediaType.ifBlank { LocalStorageRepository.classifyMedia(item.outputName) },
            extension = "." + item.outputName.substringAfterLast('.', "mp4"),
        )
        _ui.update { it.copy(showQueueSheet = false) }
        viewModelScope.launch {
            if (item.outputHostId.isNotBlank() && activeHost().hostId != item.outputHostId) {
                pendingNavigatePath = item.outputDir
                prefsRepo.update { it.copy(activeHostId = item.outputHostId) }
            }
            openFile(fakeFile)
        }
    }

    fun openQueueOutputInStudio(item: RenderQueueItem) {
        if (item.outputPath.isBlank()) return
        val fakeFile = FileItem(
            name = item.outputName.ifBlank { File(item.outputPath).name },
            path = item.outputPath,
            sizeBytes = item.newSizeBytes,
            mediaType = "video",
            extension = "." + item.outputName.substringAfterLast('.', "mp4"),
        )
        _ui.update { it.copy(showQueueSheet = false) }
        openVideo(fakeFile, true)
    }

    fun goToQueueOutputFolder(item: RenderQueueItem) {
        val dir = item.outputDir.ifBlank { File(item.outputPath).parent.orEmpty() }
        if (dir.isBlank()) return
        _ui.update { it.copy(showQueueSheet = false) }
        if (item.outputHostId.isNotBlank() && activeHost().hostId != item.outputHostId) {
            pendingNavigatePath = dir
            viewModelScope.launch { prefsRepo.update { it.copy(activeHostId = item.outputHostId) } }
        } else {
            navigateFolder(dir)
        }
    }

    private fun prependQueueJob(item: RenderQueueItem) {
        _ui.update { it.copy(queueJobs = listOf(item) + it.queueJobs.take(49)) }
    }

    private fun updateQueueJob(id: String, transform: (RenderQueueItem) -> RenderQueueItem) {
        _ui.update { state ->
            state.copy(queueJobs = state.queueJobs.map { if (it.id == id) transform(it) else it })
        }
    }

    // EXTERNAL ACTION_VIEW LAUNCHER SUPPORT (DEFAULT VIDEO PLAYER / FILE VIEWER)
    fun openExternalViewUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val resolver = ctx.contentResolver
            var name = uri.lastPathSegment?.substringAfterLast('/') ?: "Media"
            var size = 0L
            runCatching {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val nIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sIdx = c.getColumnIndex(OpenableColumns.SIZE)
                        if (nIdx >= 0 && !c.isNull(nIdx)) name = c.getString(nIdx)
                        if (sIdx >= 0 && !c.isNull(sIdx)) size = c.getLong(sIdx)
                    }
                }
            }
            val mime = resolver.getType(uri) ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "")) ?: ""
            val pathStr = if (uri.scheme == "file" && !uri.path.isNullOrBlank()) uri.path!! else uri.toString()
            val mediaType = when {
                mime.startsWith("video/") -> "video"
                mime.startsWith("image/") -> "image"
                mime.startsWith("audio/") -> "audio"
                else -> LocalStorageRepository.classifyMedia(name)
            }
            val ext = "." + name.substringAfterLast('.', when (mediaType) {
                "video" -> "mp4"
                "image" -> "jpg"
                "audio" -> "mp3"
                else -> if (mime == "application/pdf") "pdf" else if (mime.startsWith("text/")) "txt" else ""
            })
            var meta: VideoMeta? = null
            if (mediaType == "video") {
                runCatching {
                    val mmr = MediaMetadataRetriever()
                    mmr.setDataSource(ctx, uri)
                    meta = VideoMeta(
                        durationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                        width = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                        height = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
                    )
                    mmr.release()
                }
            }
            val item = FileItem(
                name = name,
                path = pathStr,
                sizeBytes = size,
                mediaType = mediaType,
                extension = ext,
                meta = meta,
            )
            withContext(Dispatchers.Main) {
                openFile(item)
            }
        }
    }

    fun openImage(file: FileItem) {
        val imgs = _ui.value.files.filter { it.isImage }.ifEmpty { listOf(file) }
        val idx = imgs.indexOfFirst { it.path == file.path }.coerceAtLeast(0)
        _ui.update { it.copy(activeImageList = imgs, activeImageIndex = idx) }
    }

    fun dismissImageViewer() {
        _ui.update { it.copy(activeImageList = emptyList(), activeImageIndex = -1) }
    }

    fun deleteSingleImage(file: FileItem) {
        deleteSingleFileWithMode(file, recycle = preferences.value.recycleByDefault)
    }

    fun openTransferDialogForSelection(move: Boolean) {
        val selected = _ui.value.files.filter { it.path in _ui.value.selectedPaths }
        if (selected.isNotEmpty()) {
            _ui.update { it.copy(pendingTransferFiles = selected, pendingTransferMoveDefault = move) }
        }
    }

    fun openTransferDialogForFile(file: FileItem, move: Boolean) {
        _ui.update { it.copy(pendingTransferFiles = listOf(file), pendingTransferMoveDefault = move) }
    }

    fun closeTransferDialog() {
        _ui.update { it.copy(pendingTransferFiles = emptyList()) }
    }

    suspend fun fetchHostRoots(host: SavedHost): RootsResponse = resolveRoots(host)

    suspend fun fetchHostFolders(host: SavedHost, path: String): ListResponse {
        if (host.isLocalDevice) {
            return localRepo.listDirectory(path, com.deckvault.app.model.SortBy.NAME, false, com.deckvault.app.model.MediaFilter.ALL)
        }
        return api.listFolder(host, path = path, sort = "name", order = "asc", filter = "all")
    }

    suspend fun createRemoteFolder(host: SavedHost, parentDir: String, name: String): String {
        if (host.isLocalDevice) {
            return localRepo.createFolder(parentDir, name.trim(), preferences.value.safSdTreeUris)
        }
        val resp = api.createFolder(host, parentDir, name.trim())
        return resp.path
    }

    fun executeTransferToRemoteHost(
        items: List<FileItem>,
        targetHost: SavedHost,
        destinationDir: String,
        move: Boolean,
    ) {
        val sourceHost = activeHost()
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
                when {
                    // Case 1: Internal <-> SD Card <-> USB on This Device
                    sourceHost.isLocalDevice && targetHost.isLocalDevice -> {
                        val res = localRepo.transferLocalItems(
                            sourcePaths = items.map { it.path },
                            destDirRaw = destinationDir,
                            move = move,
                            conflict = "rename",
                            safTreeUris = preferences.value.safSdTreeUris,
                        ) { idx, total, name, copied, itemBytes ->
                            val frac = if (itemBytes > 0L) {
                                ((idx.toFloat() + (copied.toFloat() / itemBytes.toFloat()).coerceIn(0f, 1f)) / total.coerceAtLeast(1))
                            } else {
                                idx.toFloat() / total.coerceAtLeast(1)
                            }
                            _ui.update {
                                it.copy(
                                    transferStatusText = "$verb $name (${idx + 1}/$total)",
                                    transferProgressFraction = frac.coerceIn(0f, 1f),
                                )
                            }
                        }
                        val doneVerb = if (move) "Moved" else "Copied"
                        if (res.errors.isNotEmpty()) "$doneVerb ${res.transferredCount}/${items.size} (${res.errors.first()})"
                        else "$doneVerb ${res.transferredCount} item(s) (${Formatters.formatBytes(res.transferredBytes)})"
                    }
                    // Case 2: This Device (Internal/SD) -> Remote PC
                    sourceHost.isLocalDevice && !targetHost.isLocalDevice -> {
                        var doneBytes = 0L
                        val movedPaths = mutableListOf<String>()
                        items.forEachIndexed { idx, file ->
                            _ui.update {
                                it.copy(
                                    transferStatusText = "Sending ${file.name} (${idx + 1}/${items.size}) -> ${targetHost.displayName}",
                                    transferProgressFraction = (doneBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f),
                                )
                            }
                            val size = localRepo.getFileLengthAny(file.path).takeIf { it > 0L } ?: file.sizeBytes
                            api.uploadFromStream(
                                host = targetHost,
                                destDir = destinationDir,
                                fileName = file.name,
                                contentLength = size,
                                openStream = { localRepo.openInputStreamAny(file.path) },
                            ) { written ->
                                _ui.update {
                                    it.copy(transferProgressFraction = ((doneBytes + written).toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
                                }
                            }
                            doneBytes += size.coerceAtLeast(0L)
                            movedPaths.add(file.path)
                        }
                        if (move && movedPaths.isNotEmpty()) {
                            localRepo.deleteItems(movedPaths, preferences.value.recycleByDefault, preferences.value.safSdTreeUris)
                        }
                        "${if (move) "Moved" else "Sent"} ${movedPaths.size} item(s) to ${targetHost.displayName}"
                    }
                    // Case 3: Remote PC -> Chosen Folder on This Device (Internal / SD Card / USB)
                    !sourceHost.isLocalDevice && targetHost.isLocalDevice -> {
                        var doneBytes = 0L
                        val destFolder = localRepo.resolveSafePath(destinationDir)
                        val safMap = preferences.value.safSdTreeUris
                        val pulledPaths = mutableListOf<String>()
                        items.forEachIndexed { idx, file ->
                            _ui.update {
                                it.copy(
                                    transferStatusText = "Downloading ${file.name} (${idx + 1}/${items.size}) -> ${destFolder.name}",
                                    transferProgressFraction = (doneBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f),
                                )
                            }
                            val targetFile = localRepo.resolveUniqueTargetFile(destFolder, file.name, "rename")
                            val written = api.downloadFile(
                                host = sourceHost,
                                remotePath = file.path,
                                openOutput = { localRepo.openOutputStreamWritable(targetFile, safMap) },
                            ) { read ->
                                _ui.update {
                                    it.copy(transferProgressFraction = ((doneBytes + read).toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
                                }
                            }
                            localRepo.scanMediaPath(targetFile.absolutePath)
                            doneBytes += written.coerceAtLeast(file.sizeBytes)
                            pulledPaths.add(file.path)
                        }
                        if (move && pulledPaths.isNotEmpty()) {
                            api.deleteFiles(sourceHost, pulledPaths, recycle = preferences.value.recycleByDefault)
                        }
                        "${if (move) "Moved" else "Downloaded"} ${pulledPaths.size} item(s) to ${destFolder.name}"
                    }
                    // Case 4: PC <-> PC / Laptop
                    sourceHost.hostId == targetHost.hostId -> {
                        val res = api.transferLocal(sourceHost, items.map { it.path }, destinationDir, move)
                        _ui.update { it.copy(transferProgressFraction = 1f) }
                        val doneVerb = if (move) "Moved" else "Copied"
                        if (res.errors.isNotEmpty()) "$doneVerb ${res.transferredCount}/${items.size} (${res.errors.first()})"
                        else "$doneVerb ${res.transferredCount} item(s) (${Formatters.formatBytes(res.transferredBytes)})"
                    }
                    else -> {
                        val pullRes = runCatching {
                            api.pullRemote(targetHost, sourceHost, items.map { it.path }, destinationDir)
                        }.getOrNull()
                        val succeeded = mutableListOf<String>()
                        var copiedBytes = 0L
                        if (pullRes != null) {
                            pullRes.results.forEach { succeeded.add(it.src) }
                            copiedBytes += pullRes.transferredBytes
                        }
                        val remaining = items.filterNot { it.path in succeeded }
                        if (remaining.isNotEmpty()) {
                            var completedBefore = copiedBytes
                            remaining.forEachIndexed { idx, file ->
                                _ui.update {
                                    it.copy(
                                        transferStatusText = "Relaying ${file.name} (${idx + 1}/${remaining.size}) -> ${targetHost.displayName}",
                                        transferProgressFraction = (completedBefore.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f),
                                    )
                                }
                                api.relayFileBetweenHosts(sourceHost, file.path, file.name, file.sizeBytes, targetHost, destinationDir) { written ->
                                    _ui.update {
                                        it.copy(transferProgressFraction = ((completedBefore + written).toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
                                    }
                                }
                                completedBefore += file.sizeBytes
                                copiedBytes += file.sizeBytes
                                succeeded.add(file.path)
                            }
                        }
                        if (move && succeeded.isNotEmpty()) {
                            api.deleteFiles(sourceHost, succeeded, recycle = preferences.value.recycleByDefault)
                        }
                        "${if (move) "Moved" else "Copied"} ${succeeded.size} item(s) to ${targetHost.displayName}"
                    }
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
                handleStorageFailure(err, "Transfer failed: ${err.message}")
            }
        }
    }

    fun executeTransferToLocalDevice(items: List<FileItem>, move: Boolean) {
        val sourceHost = activeHost()
        if (items.isEmpty() || sourceHost.isLocalDevice) return
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
                    api.deleteFiles(sourceHost, savedPaths, recycle = preferences.value.recycleByDefault)
                }
                "${if (move) "Moved" else "Saved"} ${savedPaths.size} item(s) to device"
            }.onSuccess { msg ->
                _ui.update {
                    it.copy(isTransferring = false, transferProgressFraction = 0f, transferStatusText = "", selectedPaths = emptySet(), toastMessage = msg)
                }
                if (move) refreshAfterMutation(sourceHost)
            }.onFailure { err ->
                _ui.update {
                    it.copy(isTransferring = false, transferProgressFraction = 0f, transferStatusText = "", errorMessage = "Download failed: ${err.message}")
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
            val uri = resolver.insert(collection, values) ?: throw IllegalStateException("Could not create MediaStore entry")
            return try {
                val bytes = api.downloadFile(host, file.path, openOutput = { resolver.openOutputStream(uri)!! }, onProgress = onProgress)
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                bytes
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }
        } else {
            val dir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "DeckVault").apply { mkdirs() }
            val outFile = File(dir, file.name)
            return api.downloadFile(host, file.path, openOutput = { outFile.outputStream() }, onProgress = onProgress)
        }
    }

    fun uploadDeviceUrisToCurrentFolder(uris: List<Uri>, deleteLocalAfter: Boolean = false) {
        val host = activeHost()
        val destDir = _ui.value.currentPath
        if (uris.isEmpty() || destDir.isBlank()) return
        if (LocalStorageRepository.isVirtualMediaStorePath(destDir)) {
            _ui.update { it.copy(toastMessage = "Navigate to Internal Storage or SD Card folder first") }
            return
        }

        viewModelScope.launch {
            _ui.update {
                it.copy(isTransferring = true, transferProgressFraction = 0f, transferStatusText = "Importing ${uris.size} file(s)...")
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
                val safMap = preferences.value.safSdTreeUris
                metas.forEachIndexed { idx, (uri, name, size) ->
                    _ui.update {
                        it.copy(transferStatusText = "Importing $name (${idx + 1}/${metas.size})", transferProgressFraction = (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f))
                    }
                    if (host.isLocalDevice) {
                        val folder = localRepo.resolveSafePath(destDir)
                        val targetFile = localRepo.resolveUniqueTargetFile(folder, name, "rename")
                        withContext(Dispatchers.IO) {
                            resolver.openInputStream(uri)?.use { src ->
                                localRepo.openOutputStreamWritable(targetFile, safMap).use { dst ->
                                    val buf = ByteArray(128 * 1024)
                                    var w = 0L
                                    while (true) {
                                        val r = src.read(buf)
                                        if (r < 0) break
                                        if (r > 0) {
                                            dst.write(buf, 0, r)
                                            w += r
                                            if (size > 0L) {
                                                _ui.update { it.copy(transferProgressFraction = ((doneBytes + w).toFloat() / totalBytes).coerceIn(0f, 1f)) }
                                            }
                                        }
                                    }
                                }
                            }
                            localRepo.scanMediaPath(targetFile.absolutePath)
                        }
                    } else {
                        api.uploadFromStream(host, destDir, name, size, openStream = { resolver.openInputStream(uri) }) { w ->
                            if (size > 0L) {
                                _ui.update { it.copy(transferProgressFraction = ((doneBytes + w).toFloat() / totalBytes).coerceIn(0f, 1f)) }
                            }
                        }
                    }
                    doneBytes += size.coerceAtLeast(0L)
                    if (deleteLocalAfter) {
                        runCatching { DocumentsContract.deleteDocument(resolver, uri) }.onFailure { runCatching { resolver.delete(uri, null, null) } }
                    }
                }
                "${if (deleteLocalAfter) "Moved" else "Added"} ${metas.size} file(s)"
            }.onSuccess { msg ->
                _ui.update { it.copy(isTransferring = false, transferProgressFraction = 0f, transferStatusText = "", toastMessage = msg) }
                refreshAfterMutation(host)
            }.onFailure { err ->
                handleStorageFailure(err, "Import failed: ${err.message}")
            }
        }
    }

    fun rotatePeerPin() {
        val nextPin = DeckVaultPeerService.rotatePin()
        if (nextPin != null) _ui.update { it.copy(toastMessage = "New sharing PIN: $nextPin") }
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
            val localName = "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "This Device" }
            val remoteHosts = preferences.value.hosts.filter { !it.isLocalDevice }

            val remoteSnapshots = coroutineScope {
                remoteHosts.map { host ->
                    async(Dispatchers.IO) {
                        runCatching { api.getRoots(host) }.fold(
                            onSuccess = { roots ->
                                if (roots.os.isNotBlank() && !roots.os.equals(host.os, ignoreCase = true)) {
                                    prefsRepo.update { cur ->
                                        cur.copy(hosts = cur.hosts.map { if (it.hostId == host.hostId) it.copy(os = roots.os) else it })
                                    }
                                }
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

            val localRoots = resolveRoots(SavedHost.localDevice())
            val localSnap = HostDrivesSnapshot(
                host = SavedHost.localDevice(),
                isLocalDevice = true,
                deviceName = localName,
                os = "Android ${Build.VERSION.RELEASE}",
                online = true,
                drives = localRoots.drives,
                quickFolders = localRoots.quickFolders,
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
            val targetHost = snapshot.host ?: SavedHost.localDevice()
            val curActive = activeHost()
            if (curActive.hostId == targetHost.hostId) {
                navigateFolder(initialPath)
            } else {
                pendingNavigatePath = initialPath
                prefsRepo.update { it.copy(activeHostId = targetHost.hostId) }
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
        val audios = _ui.value.files.filter { it.isAudio }
        val idx = audios.indexOfFirst { it.path == current.path }
        val target = audios.getOrNull(idx + delta) ?: return
        _ui.update { it.copy(activeAudioFile = target) }
    }

    fun dismissAudioPlayer() { _ui.update { it.copy(activeAudioFile = null) } }
    fun dismissPdfViewer() { _ui.update { it.copy(activePdfFile = null) } }
    fun dismissTextViewer() { _ui.update { it.copy(activeTextFile = null) } }
    fun dismissOtherFileViewer() { _ui.update { it.copy(activeOtherFile = null) } }

    fun deleteSingleFile(file: FileItem) {
        deleteSingleFileWithMode(file, recycle = preferences.value.recycleByDefault)
    }

    fun showToast(message: String) { _ui.update { it.copy(toastMessage = message) } }
    fun consumeToast() { _ui.update { it.copy(toastMessage = null) } }
    fun clearError() { _ui.update { it.copy(errorMessage = null) } }

    private suspend fun refreshAfterMutation(host: SavedHost) {
        invalidateHostCache(host.hostId)
        runCatching {
            val roots = resolveRoots(host)
            _ui.update {
                it.copy(
                    drives = roots.drives,
                    quickFolders = roots.quickFolders,
                    spaceSavedBytes = maxOf(it.spaceSavedBytes, roots.spaceSavedBytes),
                )
            }
        }
        val path = _ui.value.currentPath
        if (_ui.value.isSpaceHogsMode && path.isNotBlank()) {
            val largest = runCatching {
                if (host.isLocalDevice) localRepo.findLargest(path, 80) else api.getLargest(host, path, "video", 80)
            }.getOrNull()
            if (largest != null) {
                _ui.update { it.copy(files = largest.files, isLoading = false) }
            }
        } else if (path.isNotBlank()) {
            loadFolder(host, path, preferences.value)
        }
    }
}
