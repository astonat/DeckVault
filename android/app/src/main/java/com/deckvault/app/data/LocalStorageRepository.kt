package com.deckvault.app.data

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.deckvault.app.model.DeleteResponse
import com.deckvault.app.model.DriveInfo
import com.deckvault.app.model.FileItem
import com.deckvault.app.model.FolderItem
import com.deckvault.app.model.LargestResponse
import com.deckvault.app.model.ListResponse
import com.deckvault.app.model.MediaFilter
import com.deckvault.app.model.QuickFolder
import com.deckvault.app.model.SearchResponse
import com.deckvault.app.model.SortBy
import com.deckvault.app.model.TransferResponse
import com.deckvault.app.model.TransferredItemResult
import com.deckvault.app.model.TreeResponse
import com.deckvault.app.model.VideoMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Semaphore
import kotlin.math.max
import kotlin.math.min

class AllFilesPermissionRequiredException(val targetPath: String) :
    IOException("All Files Access permission is needed to browse $targetPath directly.")

class SdCardWritePermissionRequiredException(
    val volumeRoot: String,
    val volumeName: String,
) : IOException("Grant SD Card / USB folder write permission for $volumeName to modify files there.")

class LocalStorageRepository(private val context: Context) {

    companion object {
        const val MEDIASTORE_VIDEOS = "mediastore://videos"
        const val MEDIASTORE_IMAGES = "mediastore://images"
        const val MEDIASTORE_AUDIO = "mediastore://audio"
        const val TRASH_DIR_NAME = ".deckvault_trash"

        private val VIDEO_EXTS = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "mpg", "mpeg", "3gp", "ts", "mts")
        private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "tiff", "svg")
        private val AUDIO_EXTS = setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "wma", "opus", "alac")

        fun isVirtualMediaStorePath(path: String): Boolean = path.startsWith("mediastore://")
        fun isContentUriPath(path: String): Boolean = path.startsWith("content://")

        fun classifyMedia(name: String): String {
            val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
            return when {
                ext in VIDEO_EXTS -> "video"
                ext in IMAGE_EXTS -> "image"
                ext in AUDIO_EXTS -> "audio"
                else -> "other"
            }
        }
    }

    private val thumbDir = File(context.cacheDir, "deckvault_local_thumbs").apply { mkdirs() }
    private val storyboardDir = File(context.cacheDir, "deckvault_local_storyboards").apply { mkdirs() }
    private val metaCache = LruCache<String, VideoMeta>(1200)
    val metaDb = AndroidMetaCache(context)
    private val mmrSemaphore = Semaphore(2)
    private val drivesLock = Any()

    @Volatile
    private var cachedDrives: List<DriveInfo> = emptyList()

    @Volatile
    private var cachedDrivesAtMs: Long = 0L

    @Volatile
    private var lastMediaStorePrefetchMs: Long = 0L

    fun hasMediaReadAccess(): Boolean {
        if (hasAllFilesAccess()) return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val readOk = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            val writeOk = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            readOk && writeOk
        }
    }

    fun detectStorageDrives(forceRefresh: Boolean = false): List<DriveInfo> {
        val now = System.currentTimeMillis()
        val cached = cachedDrives
        if (!forceRefresh && cached.isNotEmpty() && (now - cachedDrivesAtMs) < 5_000L) {
            return cached
        }
        synchronized(drivesLock) {
            val recheckNow = System.currentTimeMillis()
            val cur = cachedDrives
            if (!forceRefresh && cur.isNotEmpty() && (recheckNow - cachedDrivesAtMs) < 5_000L) {
                return cur
            }
            data class VolumeCandidate(val canon: String, val file: File, val label: String, val isPrimary: Boolean)

            val candidates = linkedMapOf<String, VolumeCandidate>()
            val primaryDir = Environment.getExternalStorageDirectory()
            val primaryCanon = runCatching { primaryDir?.canonicalPath }.getOrNull()
            val primaryDevId = primaryCanon?.let { runCatching { android.system.Os.stat(it).st_dev }.getOrNull() }

            fun isGenuineRemovableExternal(dir: File, canon: String): Boolean {
                if (canon == primaryCanon) return false
                val lower = canon.lowercase(Locale.US)
                if (lower.startsWith("/storage/emulated") ||
                    lower.startsWith("/storage/self") ||
                    lower.startsWith("/storage/knox") ||
                    lower.startsWith("/storage/enc_emulated")
                ) return false
                // Require Android OS removable check + distinct block device st_dev so Dual Messenger / Work Profile clones never look like an SD Card
                val osRemovable = runCatching {
                    Environment.isExternalStorageRemovable(dir) && !Environment.isExternalStorageEmulated(dir)
                }.getOrDefault(false)
                if (!osRemovable) return false
                if (primaryDevId != null) {
                    val devId = runCatching { android.system.Os.stat(canon).st_dev }.getOrNull()
                    if (devId != null && devId == primaryDevId) return false
                }
                return true
            }

            fun addCandidate(dir: File?, labelHint: String?, isPrimary: Boolean) {
                if (dir == null || !dir.exists()) return
                val canon = runCatching { dir.canonicalPath }.getOrElse { dir.absolutePath }
                if (canon == "/storage" || canon == "/storage/emulated" || canon == "/storage/self") return
                if (!isPrimary && !isGenuineRemovableExternal(dir, canon)) return
                val existing = candidates[canon]
                if (existing == null || (isPrimary && !existing.isPrimary)) {
                    val label = when {
                        isPrimary -> "Internal Storage"
                        !labelHint.isNullOrBlank() -> labelHint
                        else -> "SD / External (${dir.name})"
                    }
                    candidates[canon] = VolumeCandidate(canon, dir, label, isPrimary)
                }
            }

            // 1. StorageManager.storageVolumes gives authoritative Internal + truly removable SD Card / USB drives
            runCatching {
                val sm = context.getSystemService(StorageManager::class.java)
                sm?.storageVolumes?.forEach { vol ->
                    val state = vol.state
                    if (state == Environment.MEDIA_MOUNTED || state == Environment.MEDIA_MOUNTED_READ_ONLY) {
                        // Skip Samsung Dual Messenger / Work Profile / Private Space emulated secondary volumes
                        if (!vol.isPrimary && (vol.isEmulated || !vol.isRemovable)) return@forEach
                        val volDir: File? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            vol.directory
                        } else {
                            if (vol.isPrimary) Environment.getExternalStorageDirectory()
                            else vol.uuid?.let { File("/storage/$it") }
                        }
                        val desc = runCatching { vol.getDescription(context) }.getOrNull().orEmpty()
                        val label = when {
                            vol.isPrimary -> "Internal Storage"
                            desc.contains("USB", ignoreCase = true) -> desc
                            desc.isNotBlank() && !desc.equals("External storage", ignoreCase = true) ->
                                if (desc.contains("SD", true)) desc else "SD Card ($desc)"
                            else -> "SD Card (${volDir?.name ?: vol.uuid ?: "External"})"
                        }
                        addCandidate(volDir, label, vol.isPrimary)
                    }
                }
            }

            // 2. Primary storage fallback
            addCandidate(primaryDir, "Internal Storage", true)

            // 3. getExternalFilesDirs ancestors (detects external SD & USB roots even on older OEMs; verified removable)
            context.getExternalFilesDirs(null).filterNotNull().forEach { appDir ->
                var curDir: File? = appDir
                while (curDir != null && curDir.parentFile != null) {
                    if (curDir.name.equals("Android", ignoreCase = true)) {
                        val volRoot = curDir.parentFile
                        if (volRoot != null) {
                            val isPrim = runCatching { volRoot.canonicalPath == primaryCanon }.getOrDefault(false)
                            addCandidate(volRoot, if (isPrim) "Internal Storage" else "SD Card (${volRoot.name})", isPrim)
                        }
                        break
                    }
                    curDir = curDir.parentFile
                }
            }

            // 4. Direct /storage/<uuid> scan fallback for mounted physical SD/USB volumes
            runCatching {
                File("/storage").listFiles()?.forEach { entry ->
                    val name = entry.name
                    if (entry.isDirectory && name != "self" && name != "emulated" && entry.exists()) {
                        val sf = runCatching { StatFs(entry.absolutePath) }.getOrNull()
                        if (sf != null && sf.totalBytes > 0L) {
                            addCandidate(entry, "SD / USB ($name)", false)
                        }
                    }
                }
            }

            val result = candidates.values
                .sortedWith(compareBy<VolumeCandidate>({ if (it.isPrimary) 0 else 1 }, { it.label.lowercase(Locale.US) }))
                .map { cand ->
                    val (total, free) = statVolume(cand.file)
                    DriveInfo(
                        name = cand.label,
                        path = cand.canon,
                        totalBytes = total,
                        usedBytes = (total - free).coerceAtLeast(0L),
                        freeBytes = free,
                        indexedSizeBytes = -1L,
                    )
                }
            cachedDrives = result
            cachedDrivesAtMs = System.currentTimeMillis()
            return result
        }
    }

    fun detectQuickFolders(): List<QuickFolder> {
        val drives = detectStorageDrives(forceRefresh = true)
        val out = mutableListOf<QuickFolder>()
        val allFilesOk = hasAllFilesAccess()

        if (!allFilesOk) {
            // Put MediaStore collections FIRST when All Files Access isn't granted so tablet boots with zero errors!
            out.add(QuickFolder(name = "All Device Videos", path = MEDIASTORE_VIDEOS))
            out.add(QuickFolder(name = "All Device Photos", path = MEDIASTORE_IMAGES))
            out.add(QuickFolder(name = "All Device Audio", path = MEDIASTORE_AUDIO))
        }

        val primary = drives.firstOrNull()?.let { File(it.path) } ?: Environment.getExternalStorageDirectory()
        if (primary != null && primary.exists()) {
            val primaryFolders = listOf(
                "Camera / DCIM" to File(primary, "DCIM"),
                "Movies" to File(primary, "Movies"),
                "Pictures" to File(primary, "Pictures"),
                "Downloads" to File(primary, "Download"),
                "Documents" to File(primary, "Documents"),
                "Music" to File(primary, "Music"),
            )
            primaryFolders.forEach { (label, dir) ->
                if (dir.exists() && dir.isDirectory) {
                    val canon = runCatching { dir.canonicalPath }.getOrElse { dir.absolutePath }
                    out.add(QuickFolder(name = label, path = canon))
                }
            }
        }

        // Surface removable SD / USB roots & media folders explicitly
        drives.drop(1).forEach { ext ->
            val extFile = File(ext.path)
            if (extFile.exists()) {
                out.add(QuickFolder(name = ext.name, path = ext.path))
                listOf("DCIM", "Movies", "Pictures", "Download").forEach { sub ->
                    val sdSub = File(extFile, sub)
                    if (sdSub.exists() && sdSub.isDirectory) {
                        val canon = runCatching { sdSub.canonicalPath }.getOrElse { sdSub.absolutePath }
                        out.add(QuickFolder(name = "${ext.name.substringBefore(" (")} $sub", path = canon))
                    }
                }
            }
        }

        if (allFilesOk) {
            out.add(QuickFolder(name = "All Device Videos", path = MEDIASTORE_VIDEOS))
            out.add(QuickFolder(name = "All Device Photos", path = MEDIASTORE_IMAGES))
            out.add(QuickFolder(name = "All Device Audio", path = MEDIASTORE_AUDIO))
        }

        return out
    }

    fun volumeRootFor(target: File): DriveInfo? {
        val targetCanon = runCatching { target.canonicalPath }.getOrElse { target.absolutePath }
        return detectStorageDrives()
            .sortedByDescending { it.path.length }
            .firstOrNull { drv ->
                targetCanon == drv.path || targetCanon.startsWith("${drv.path}${File.separator}")
            }
    }

    fun resolveSafePath(raw: String): File {
        if (raw.isBlank()) throw IllegalArgumentException("Empty path")
        val normalizedRaw = if (raw == "/sdcard" || raw.startsWith("/sdcard/")) {
            val primary = Environment.getExternalStorageDirectory()?.absolutePath ?: raw
            raw.replaceFirst("/sdcard", primary)
        } else raw
        val target = File(normalizedRaw).canonicalFile
        val targetCanon = target.canonicalPath
        val roots = detectStorageDrives().map { it.path }
        val allowed = roots.any { root ->
            targetCanon == root || targetCanon.startsWith("$root${File.separator}")
        }
        if (!allowed) {
            throw SecurityException("Path outside device storage roots: $raw")
        }
        return target
    }

    fun prefetchMediaStoreVideoMeta(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && (now - lastMediaStorePrefetchMs) < 10_000L) return
        lastMediaStorePrefetchMs = now
        val cols = arrayOf(
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED,
        )
        runCatching {
            val batch = ArrayList<CachedVideoMetaRow>(128)
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                cols,
                null,
                null,
                "${MediaStore.Video.Media.DATE_MODIFIED} DESC",
            )?.use { c ->
                val dataIdx = c.getColumnIndex(MediaStore.Video.Media.DATA)
                val durIdx = c.getColumnIndex(MediaStore.Video.Media.DURATION)
                val wIdx = c.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val hIdx = c.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                val szIdx = c.getColumnIndex(MediaStore.Video.Media.SIZE)
                val mIdx = c.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                var count = 0
                while (c.moveToNext() && count < 1500) {
                    val p = if (dataIdx >= 0) c.getString(dataIdx) else null
                    if (p.isNullOrBlank()) continue
                    val dur = if (durIdx >= 0) c.getLong(durIdx) else 0L
                    val w = if (wIdx >= 0) c.getInt(wIdx) else 0
                    val h = if (hIdx >= 0) c.getInt(hIdx) else 0
                    val sz = if (szIdx >= 0) c.getLong(szIdx) else 0L
                    val mtime = if (mIdx >= 0) c.getLong(mIdx) * 1000L else 0L
                    if (dur > 0L || w > 0) {
                        val meta = VideoMeta(durationMs = dur, width = w, height = h)
                        metaCache.put(p, meta)
                        batch.add(CachedVideoMetaRow(p, mtime, sz, meta))
                    }
                    count++
                }
            }
            if (batch.isNotEmpty()) metaDb.putBatch(batch)
        }
    }

    fun getCachedVideoMeta(rawPath: String, sizeBytes: Long = -1L): VideoMeta? {
        metaCache.get(rawPath)?.let { return it }
        val fromDb = if (sizeBytes >= 0L) metaDb.getByPathAndSize(rawPath, sizeBytes) else metaDb.getByPath(rawPath)
        if (fromDb != null) metaCache.put(rawPath, fromDb)
        return fromDb
    }

    suspend fun listDirectory(
        rawPath: String,
        sortBy: SortBy = SortBy.SIZE,
        descending: Boolean = true,
        filter: MediaFilter = MediaFilter.ALL,
    ): ListResponse = withContext(Dispatchers.IO) {
        if (isVirtualMediaStorePath(rawPath)) {
            return@withContext queryMediaStoreCollection(rawPath, sortBy, descending, filter)
        }

        val drives = detectStorageDrives()
        val defaultRoot = drives.firstOrNull()?.path
            ?: Environment.getExternalStorageDirectory()?.canonicalPath
            ?: throw IOException("No device storage detected")
        val safe = resolveSafePath(rawPath.ifBlank { defaultRoot })
        if (!safe.exists() || !safe.isDirectory) {
            throw IOException("Folder not found: ${safe.absolutePath}")
        }

        val safeCanon = runCatching { safe.canonicalPath }.getOrElse { safe.absolutePath }
        val entries = safe.listFiles()
        if (entries == null) {
            // Android 11–16 without All Files Access cannot call listFiles() on raw folders; synthesize root folders + MediaStore cleanly
            if (!hasAllFilesAccess()) {
                val isRootVol = drives.any { it.path == safeCanon }
                val synthFolders = if (isRootVol) {
                    listOf("DCIM", "Movies", "Pictures", "Download", "Documents", "Music")
                        .map { File(safe, it) }
                        .filter { it.exists() && it.isDirectory }
                        .map { sub ->
                            FolderItem(
                                name = sub.name,
                                path = runCatching { sub.canonicalPath }.getOrElse { sub.absolutePath },
                                sizeBytes = -1L,
                                fileCount = -1,
                                mediaCount = -1,
                                hasSubfolders = true,
                            )
                        }
                } else emptyList()
                val fallbackItems = queryMediaStoreByFolderHint(safeCanon, sortBy, descending, filter)
                if (synthFolders.isNotEmpty() || fallbackItems.isNotEmpty() || hasMediaReadAccess()) {
                    val (tot, free) = statVolume(safe)
                    return@withContext ListResponse(
                        path = safeCanon,
                        name = drives.firstOrNull { it.path == safeCanon }?.name ?: safe.name.ifBlank { safeCanon },
                        parentPath = computeParentPath(safe),
                        totalSizeBytes = fallbackItems.sumOf { it.sizeBytes },
                        fileCount = fallbackItems.size,
                        mediaCount = fallbackItems.count { it.isVideo || it.isImage },
                        diskFreeBytes = free,
                        diskTotalBytes = tot,
                        folders = synthFolders,
                        files = fallbackItems,
                    )
                }
                throw AllFilesPermissionRequiredException(safeCanon)
            }
            throw IOException("Cannot read directory: $safeCanon")
        }

        // Batch-load MediaStore metadata once (~5-15ms) instead of spawning synchronous MediaMetadataRetriever per file!
        prefetchMediaStoreVideoMeta()

        val folders = mutableListOf<FolderItem>()
        val files = mutableListOf<FileItem>()
        var totalFileBytes = 0L
        var mediaCount = 0
        val fastFolderMode = entries.size > 40

        entries.forEach { entry ->
            if (entry.name == "." || entry.name == "..") return@forEach
            if (entry.name.startsWith(".") && entry.name != TRASH_DIR_NAME) return@forEach
            val canon = runCatching { entry.canonicalPath }.getOrElse { entry.absolutePath }
            if (entry.isDirectory) {
                var immediateBytes = -1L
                var immediateFiles = -1
                var hasSubs = true
                if (!fastFolderMode) {
                    val children = entry.listFiles()
                    if (children != null) {
                        var sum = 0L
                        var cnt = 0
                        var subFlag = false
                        children.forEach { child ->
                            if (!child.name.startsWith(".")) {
                                if (child.isDirectory) subFlag = true
                                else {
                                    sum += child.length()
                                    cnt++
                                }
                            }
                        }
                        immediateBytes = sum
                        immediateFiles = cnt
                        hasSubs = subFlag
                    }
                }
                folders.add(
                    FolderItem(
                        name = if (entry.name == TRASH_DIR_NAME) "Recycle Bin (.deckvault_trash)" else entry.name,
                        path = canon,
                        sizeBytes = immediateBytes,
                        fileCount = immediateFiles,
                        mediaCount = -1,
                        hasSubfolders = hasSubs,
                    )
                )
            } else if (entry.isFile) {
                val sz = entry.length()
                totalFileBytes += sz
                val mtype = classifyMedia(entry.name)
                if (mtype == "video" || mtype == "image") mediaCount++
                if (matchesMediaFilter(mtype, sz, filter)) {
                    val meta = if (mtype == "video") getCachedVideoMeta(canon, sz) ?: getCachedVideoMeta(entry.absolutePath, sz) else null
                    files.add(
                        FileItem(
                            name = entry.name,
                            path = canon,
                            sizeBytes = sz,
                            modifiedMs = entry.lastModified(),
                            mediaType = mtype,
                            extension = "." + entry.name.substringAfterLast('.', ""),
                            meta = meta,
                        )
                    )
                }
            }
        }

        val sortedFolders = folders.sortedWith(compareByDescending<FolderItem> { it.sizeBytes }.thenBy { it.name.lowercase(Locale.US) })
        val sortedFiles = sortFileItems(files, sortBy, descending)
        val (totalDisk, freeDisk) = statVolume(safe)

        ListResponse(
            path = safeCanon,
            name = drives.firstOrNull { it.path == safeCanon }?.name ?: safe.name.ifBlank { safeCanon },
            parentPath = computeParentPath(safe),
            totalSizeBytes = totalFileBytes,
            fileCount = sortedFiles.size,
            mediaCount = mediaCount,
            diskFreeBytes = freeDisk,
            diskTotalBytes = totalDisk,
            folders = sortedFolders,
            files = sortedFiles,
        )
    }

    suspend fun getTree(rawPath: String): TreeResponse = withContext(Dispatchers.IO) {
        if (isVirtualMediaStorePath(rawPath)) {
            return@withContext TreeResponse(path = rawPath, sizeBytes = 0L, children = emptyList())
        }
        val safe = resolveSafePath(rawPath)
        val safeCanon = runCatching { safe.canonicalPath }.getOrElse { safe.absolutePath }
        val entries = safe.listFiles()
        if (entries == null) {
            val drives = detectStorageDrives()
            val isRootVol = drives.any { it.path == safeCanon }
            val fallbackDirs = if (!hasAllFilesAccess() && isRootVol) {
                listOf("DCIM", "Movies", "Pictures", "Download", "Documents", "Music")
                    .map { File(safe, it) }
                    .filter { it.exists() && it.isDirectory }
                    .map { sub ->
                        FolderItem(
                            name = sub.name,
                            path = runCatching { sub.canonicalPath }.getOrElse { sub.absolutePath },
                            sizeBytes = -1L,
                            hasSubfolders = true,
                        )
                    }
            } else emptyList()
            return@withContext TreeResponse(path = safeCanon, sizeBytes = 0L, children = fallbackDirs)
        }
        val childrenDirs = entries
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .map { dir ->
                FolderItem(
                    name = dir.name,
                    path = runCatching { dir.canonicalPath }.getOrElse { dir.absolutePath },
                    sizeBytes = -1L,
                    hasSubfolders = true,
                )
            }
            .sortedBy { it.name.lowercase(Locale.US) }
        TreeResponse(path = safeCanon, sizeBytes = 0L, children = childrenDirs)
    }

    suspend fun findLargest(rootPath: String?, limit: Int = 80): LargestResponse = withContext(Dispatchers.IO) {
        if (!hasAllFilesAccess()) {
            val ms = queryMediaStoreCollection(MEDIASTORE_VIDEOS, SortBy.SIZE, true, MediaFilter.ALL)
            return@withContext LargestResponse(path = MEDIASTORE_VIDEOS, files = ms.files.take(limit))
        }
        val roots = if (!rootPath.isNullOrBlank() && !isVirtualMediaStorePath(rootPath)) {
            listOf(resolveSafePath(rootPath))
        } else {
            detectStorageDrives().map { File(it.path) }
        }
        val collected = mutableListOf<FileItem>()
        roots.forEach { r -> scanLargestRecursive(r, 0, collected) }
        LargestResponse(
            path = roots.firstOrNull()?.canonicalPath.orEmpty(),
            files = collected.sortedByDescending { it.sizeBytes }.take(limit),
        )
    }

    suspend fun searchFiles(
        query: String,
        rootPath: String?,
        filter: MediaFilter = MediaFilter.ALL,
        limit: Int = 100,
    ): SearchResponse = withContext(Dispatchers.IO) {
        val q = query.trim().lowercase(Locale.US)
        if (q.isBlank()) return@withContext SearchResponse()
        if (!hasAllFilesAccess()) {
            val allVideos = queryMediaStoreCollection(MEDIASTORE_VIDEOS, SortBy.DATE, true, filter).files
            val allPhotos = queryMediaStoreCollection(MEDIASTORE_IMAGES, SortBy.DATE, true, filter).files
            val allAudio = queryMediaStoreCollection(MEDIASTORE_AUDIO, SortBy.DATE, true, filter).files
            return@withContext SearchResponse(
                files = (allVideos + allPhotos + allAudio)
                    .distinctBy { it.path }
                    .filter { it.name.lowercase(Locale.US).contains(q) }
                    .take(limit),
            )
        }
        val roots = if (!rootPath.isNullOrBlank() && !isVirtualMediaStorePath(rootPath)) {
            listOf(resolveSafePath(rootPath))
        } else {
            detectStorageDrives().map { File(it.path) }
        }
        val found = mutableListOf<FileItem>()
        roots.forEach { r -> scanSearchRecursive(r, q, filter, limit, found) }
        SearchResponse(files = found.take(limit))
    }

    fun getOrBuildThumbnailFile(rawPath: String, width: Int = 420): File? {
        if (isContentUriPath(rawPath) || isVirtualMediaStorePath(rawPath)) {
            return buildThumbForUri(rawPath, width)
        }
        val file = runCatching { resolveSafePath(rawPath) }.getOrNull() ?: return null
        if (!file.exists() || !file.isFile) return null
        val mediaType = classifyMedia(file.name)
        if (mediaType == "image") return file
        if (mediaType != "video") return null

        val key = sha256("${file.canonicalPath}:${file.length()}:${file.lastModified()}:$width")
        val outFile = File(thumbDir, "$key.jpg")
        if (outFile.exists() && outFile.length() > 0L) return outFile

        mmrSemaphore.acquire()
        return try {
            if (outFile.exists() && outFile.length() > 0L) return outFile
            val tmpFile = File(thumbDir, "$key.tmp.jpg")
            runCatching {
                val mmr = MediaMetadataRetriever()
                try {
                    mmr.setDataSource(file.canonicalPath)
                    val durMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    val frameUs = if (durMs > 1500L) min(durMs * 150L, 4_000_000L) else 0L
                    val rawBmp = mmr.getFrameAtTime(frameUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: mmr.frameAtTime
                        ?: return@runCatching null
                    saveScaledJpeg(rawBmp, tmpFile, width, 84)
                    if (!tmpFile.renameTo(outFile)) tmpFile.copyTo(outFile, overwrite = true)
                    tmpFile.delete()
                    outFile
                } finally {
                    runCatching { mmr.release() }
                    tmpFile.delete()
                }
            }.getOrNull()
        } finally {
            mmrSemaphore.release()
        }
    }

    private fun buildThumbForUri(uriStr: String, width: Int): File? {
        val key = sha256("uri:$uriStr:$width")
        val outFile = File(thumbDir, "$key.jpg")
        if (outFile.exists() && outFile.length() > 0L) return outFile
        mmrSemaphore.acquire()
        return try {
            if (outFile.exists() && outFile.length() > 0L) return outFile
            val tmpFile = File(thumbDir, "$key.tmp.jpg")
            val uri = Uri.parse(uriStr)
            // Fast Android Q+ hardware thumbnail lookup first (~10ms!)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val quickBmp = runCatching { context.contentResolver.loadThumbnail(uri, Size(width, width), null) }.getOrNull()
                if (quickBmp != null) {
                    saveScaledJpeg(quickBmp, tmpFile, width, 84)
                    if (!tmpFile.renameTo(outFile)) tmpFile.copyTo(outFile, overwrite = true)
                    tmpFile.delete()
                    return outFile
                }
            }
            runCatching {
                val mmr = MediaMetadataRetriever()
                try {
                    mmr.setDataSource(context, uri)
                    val rawBmp = mmr.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: mmr.frameAtTime
                        ?: return@runCatching null
                    saveScaledJpeg(rawBmp, tmpFile, width, 84)
                    if (!tmpFile.renameTo(outFile)) tmpFile.copyTo(outFile, overwrite = true)
                    outFile
                } finally {
                    runCatching { mmr.release() }
                    tmpFile.delete()
                }
            }.getOrNull()
        } finally {
            mmrSemaphore.release()
        }
    }

    fun getOrBuildStoryboardFile(rawPath: String, cols: Int = 4, rows: Int = 4): File? {
        val uriMode = isContentUriPath(rawPath)
        val file = if (!uriMode) runCatching { resolveSafePath(rawPath) }.getOrNull() else null
        if (!uriMode && (file == null || !file.isFile || classifyMedia(file.name) != "video")) return null

        val keySrc = if (file != null) "${file.canonicalPath}:${file.length()}:${file.lastModified()}:$cols:$rows" else "uri:$rawPath:$cols:$rows"
        val outFile = File(storyboardDir, "${sha256(keySrc)}.jpg")
        if (outFile.exists() && outFile.length() > 0L) return outFile

        return runCatching {
            val mmr = MediaMetadataRetriever()
            try {
                if (file != null) mmr.setDataSource(file.canonicalPath) else mmr.setDataSource(context, Uri.parse(rawPath))
                val durMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val total = (cols * rows).coerceIn(4, 24)
                val tileW = 240
                val tileH = 135
                val sheet = Bitmap.createBitmap(cols * tileW, rows * tileH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(sheet)
                canvas.drawColor(android.graphics.Color.BLACK)
                for (i in 0 until total) {
                    val us = if (durMs > 500L) ((durMs * (i + 0.5) / total).toLong() * 1000L) else 0L
                    val frame = mmr.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: continue
                    val scaled = Bitmap.createScaledBitmap(frame, tileW, tileH, true)
                    val cx = (i % cols) * tileW
                    val cy = (i / cols) * tileH
                    canvas.drawBitmap(scaled, cx.toFloat(), cy.toFloat(), null)
                    if (scaled !== frame) scaled.recycle()
                    frame.recycle()
                }
                FileOutputStream(outFile).use { fos ->
                    sheet.compress(Bitmap.CompressFormat.JPEG, 82, fos)
                }
                sheet.recycle()
                outFile
            } finally {
                runCatching { mmr.release() }
            }
        }.getOrNull()
    }

    fun getOrBuildVideoFrameFile(rawPath: String, ms: Long, width: Int = 360): File? {
        val uriMode = isContentUriPath(rawPath)
        val file = if (!uriMode) runCatching { resolveSafePath(rawPath) }.getOrNull() else null
        if (!uriMode && (file == null || !file.isFile)) return null
        val outFile = File(thumbDir, "frame_${sha256("$rawPath:$ms:$width")}.jpg")
        if (outFile.exists() && outFile.length() > 0L) return outFile
        return runCatching {
            val mmr = MediaMetadataRetriever()
            try {
                if (file != null) mmr.setDataSource(file.canonicalPath) else mmr.setDataSource(context, Uri.parse(rawPath))
                val frame = mmr.getFrameAtTime(ms.coerceAtLeast(0L) * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: mmr.frameAtTime
                    ?: return@runCatching null
                saveScaledJpeg(frame, outFile, width, 84)
                outFile
            } finally {
                runCatching { mmr.release() }
            }
        }.getOrNull()
    }

    fun probeVideoMeta(rawPath: String): VideoMeta? {
        metaCache.get(rawPath)?.let { return it }
        val file = if (!isContentUriPath(rawPath)) runCatching { resolveSafePath(rawPath) }.getOrNull() else null
        val keyPath = file?.canonicalPath ?: rawPath
        val sz = file?.length() ?: -1L
        if (sz >= 0L) {
            metaDb.getByPathAndSize(keyPath, sz)?.let {
                metaCache.put(rawPath, it)
                metaCache.put(keyPath, it)
                return it
            }
        }
        return runCatching {
            val mmr = MediaMetadataRetriever()
            try {
                if (file == null) mmr.setDataSource(context, Uri.parse(rawPath))
                else mmr.setDataSource(keyPath)
                val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val br = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
                val meta = VideoMeta(
                    durationMs = dur,
                    width = w,
                    height = h,
                    bitrate = br,
                )
                metaCache.put(rawPath, meta)
                metaCache.put(keyPath, meta)
                if (file != null) metaDb.put(keyPath, file.lastModified(), sz, meta)
                meta
            } finally {
                runCatching { mmr.release() }
            }
        }.getOrNull()
    }

    private fun getCachedOrQuickVideoMeta(path: String): VideoMeta? = metaCache.get(path) ?: probeVideoMeta(path)

    suspend fun createFolder(
        parentDir: String,
        name: String,
        safTreeUris: Map<String, String> = emptyMap(),
    ): String = withContext(Dispatchers.IO) {
        val cleanName = File(name).name.trim()
        if (cleanName.isBlank() || cleanName == "." || cleanName == "..") {
            throw IllegalArgumentException("Invalid folder name")
        }
        val parent = resolveSafePath(parentDir)
        if (!parent.exists() || !parent.isDirectory) {
            throw IOException("Parent folder not found: ${parent.absolutePath}")
        }
        val target = File(parent, cleanName)
        if (target.exists()) return@withContext target.canonicalPath

        if (target.mkdirs()) {
            return@withContext target.canonicalPath
        }
        val docParent = resolveSafDirectory(parent, safTreeUris, createMissing = true)
            ?: throwSafRequired(parent)
        docParent.findFile(cleanName) ?: docParent.createDirectory(cleanName)
            ?: throw IOException("Could not create folder $cleanName on SD card")
        target.absolutePath
    }

    suspend fun renameItem(
        rawPath: String,
        newName: String,
        safTreeUris: Map<String, String> = emptyMap(),
    ): String = withContext(Dispatchers.IO) {
        val cleanName = File(newName).name.trim()
        if (cleanName.isBlank()) throw IllegalArgumentException("File name cannot be empty")
        if (isContentUriPath(rawPath)) {
            val doc = DocumentFile.fromSingleUri(context, Uri.parse(rawPath))
                ?: throw IOException("Cannot rename read-only item")
            if (!doc.renameTo(cleanName)) throw IOException("Rename failed")
            return@withContext doc.uri.toString()
        }
        val src = resolveSafePath(rawPath)
        val dst = File(src.parentFile, cleanName)
        resolveSafePath(dst.absolutePath)
        if (src.renameTo(dst)) {
            scanMediaPath(dst.canonicalPath)
            return@withContext dst.canonicalPath
        }
        val doc = resolveSafFile(src, safTreeUris) ?: throwSafRequired(src)
        if (!doc.renameTo(cleanName)) throw IOException("Could not rename ${src.name} on SD card")
        dst.absolutePath
    }

    suspend fun deleteItems(
        paths: List<String>,
        recycle: Boolean,
        safTreeUris: Map<String, String> = emptyMap(),
    ): DeleteResponse = withContext(Dispatchers.IO) {
        var deletedCount = 0
        var freedBytes = 0L
        val errors = mutableListOf<String>()

        for (raw in paths) {
            if (raw.isBlank()) continue
            try {
                if (isContentUriPath(raw)) {
                    val rows = context.contentResolver.delete(Uri.parse(raw), null, null)
                    if (rows > 0) deletedCount++ else errors.add("Could not delete $raw")
                    continue
                }
                val target = resolveSafePath(raw)
                if (!target.exists()) {
                    errors.add("${target.name}: File not found")
                    continue
                }
                val sz = if (target.isFile) target.length() else computeDirSize(target)
                if (recycle && !target.absolutePath.contains(TRASH_DIR_NAME)) {
                    moveToTrash(target, safTreeUris)
                    freedBytes += sz
                    deletedCount++
                } else {
                    deleteRecursivelyWithSaf(target, safTreeUris)
                    freedBytes += sz
                    deletedCount++
                }
            } catch (e: SdCardWritePermissionRequiredException) {
                throw e
            } catch (e: Exception) {
                errors.add("${File(raw).name}: ${e.message ?: "Delete failed"}")
            }
        }
        DeleteResponse(
            deletedCount = deletedCount,
            freedBytes = freedBytes,
            errors = errors,
        )
    }

    suspend fun transferLocalItems(
        sourcePaths: List<String>,
        destDirRaw: String,
        move: Boolean,
        conflict: String = "rename",
        safTreeUris: Map<String, String> = emptyMap(),
        onProgress: (index: Int, total: Int, name: String, copiedBytes: Long, itemBytes: Long) -> Unit = { _, _, _, _, _ -> },
    ): TransferResponse = withContext(Dispatchers.IO) {
        val destDir = resolveSafePath(destDirRaw)
        if (!destDir.exists() && !destDir.mkdirs()) {
            resolveSafDirectory(destDir, safTreeUris, createMissing = true) ?: throwSafRequired(destDir)
        }

        val results = mutableListOf<TransferredItemResult>()
        val errors = mutableListOf<String>()
        var totalTransferredBytes = 0L

        sourcePaths.forEachIndexed { idx, rawSrc ->
            try {
                if (isContentUriPath(rawSrc)) {
                    val uri = Uri.parse(rawSrc)
                    val metaName = queryDisplayName(uri) ?: "media_${System.currentTimeMillis()}.mp4"
                    val targetFile = uniqueDestFile(destDir, metaName, conflict)
                    if (targetFile == null) {
                        results.add(TransferredItemResult(src = rawSrc, dest = File(destDir, metaName).absolutePath, sizeBytes = 0L))
                        return@forEachIndexed
                    }
                    val copied = copyStreamToTargetFile(
                        openIn = { context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $rawSrc") },
                        expectedLen = -1L,
                        targetFile = targetFile,
                        safTreeUris = safTreeUris,
                        onBytes = { b -> onProgress(idx, sourcePaths.size, targetFile.name, b, 0L) },
                    )
                    if (move) {
                        runCatching { context.contentResolver.delete(uri, null, null) }
                    }
                    scanMediaPath(targetFile.absolutePath)
                    totalTransferredBytes += copied
                    results.add(TransferredItemResult(src = rawSrc, dest = targetFile.absolutePath, sizeBytes = copied))
                } else {
                    val srcFile = resolveSafePath(rawSrc)
                    if (!srcFile.exists()) throw IOException("Source missing: ${srcFile.name}")
                    val targetFile = uniqueDestFile(destDir, srcFile.name, conflict)
                    if (targetFile == null) {
                        results.add(TransferredItemResult(src = srcFile.canonicalPath, dest = File(destDir, srcFile.name).absolutePath, sizeBytes = 0L))
                        return@forEachIndexed
                    }
                    if (srcFile.canonicalPath == targetFile.canonicalPath) {
                        throw IllegalArgumentException("Source and destination are the same")
                    }
                    if (srcFile.isDirectory && targetFile.canonicalPath.startsWith(srcFile.canonicalPath + File.separator)) {
                        throw IllegalArgumentException("Cannot move a folder into itself")
                    }

                    val transferredBytes = copyOrMoveRecursive(
                        src = srcFile,
                        dst = targetFile,
                        move = move,
                        safTreeUris = safTreeUris,
                        onBytes = { b, total -> onProgress(idx, sourcePaths.size, srcFile.name, b, total) },
                    )
                    scanMediaPath(targetFile.absolutePath)
                    totalTransferredBytes += transferredBytes
                    results.add(TransferredItemResult(src = rawSrc, dest = targetFile.absolutePath, sizeBytes = transferredBytes))
                }
            } catch (e: SdCardWritePermissionRequiredException) {
                throw e
            } catch (e: Exception) {
                errors.add("${File(rawSrc).name}: ${e.message ?: "Transfer failed"}")
            }
        }

        TransferResponse(
            transferredCount = results.size,
            transferredBytes = totalTransferredBytes,
            results = results,
            errors = errors,
        )
    }

    fun openOutputStreamWritable(target: File, safTreeUris: Map<String, String> = emptyMap()): OutputStream {
        target.parentFile?.mkdirs()
        val direct = runCatching { FileOutputStream(target) }.getOrNull()
        if (direct != null) return direct
        val parentDir = target.parentFile ?: throw IOException("Missing parent directory")
        val docDir = resolveSafDirectory(parentDir, safTreeUris, createMissing = true) ?: throwSafRequired(target)
        docDir.findFile(target.name)?.delete()
        val mime = guessMime(target.name)
        val newDoc = docDir.createFile(mime, target.name)
            ?: throw IOException("Could not create ${target.name} via SD card DocumentFile")
        return context.contentResolver.openOutputStream(newDoc.uri, "wt")
            ?: throw IOException("Could not open SD card output stream for ${target.name}")
    }

    fun openInputStreamAny(rawPath: String): InputStream? {
        return if (isContentUriPath(rawPath)) {
            context.contentResolver.openInputStream(Uri.parse(rawPath))
        } else {
            val file = resolveSafePath(rawPath)
            FileInputStream(file)
        }
    }

    fun getFileLengthAny(rawPath: String): Long {
        if (isContentUriPath(rawPath)) {
            return runCatching {
                context.contentResolver.openAssetFileDescriptor(Uri.parse(rawPath), "r")?.use { it.length }
            }.getOrNull() ?: -1L
        }
        return runCatching { resolveSafePath(rawPath).length() }.getOrDefault(-1L)
    }

    fun saveBitmapSnapshot(rawPath: String, ms: Long, safTreeUris: Map<String, String> = emptyMap()): File {
        val mmr = MediaMetadataRetriever()
        val srcFile = if (!isContentUriPath(rawPath)) resolveSafePath(rawPath) else null
        try {
            if (srcFile != null) mmr.setDataSource(srcFile.canonicalPath) else mmr.setDataSource(context, Uri.parse(rawPath))
            val bmp = mmr.getFrameAtTime(ms.coerceAtLeast(0L) * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: mmr.frameAtTime
                ?: throw IOException("Unable to grab frame")
            val outDir = srcFile?.parentFile
                ?: Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                ?: context.filesDir
            val stem = (srcFile?.nameWithoutExtension ?: "DeckVault_Frame").take(40)
            val target = uniqueDestFile(outDir, "${stem}_frame_${ms}ms.png", "rename")!!
            openOutputStreamWritable(target, safTreeUris).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bmp.recycle()
            scanMediaPath(target.absolutePath)
            return target
        } finally {
            runCatching { mmr.release() }
        }
    }

    fun resolveUniqueTargetFile(dir: File, desiredName: String, conflict: String = "rename"): File =
        uniqueDestFile(dir, desiredName, conflict) ?: File(dir, desiredName)

    fun scanMediaPath(path: String) {
        if (path.isBlank() || isContentUriPath(path)) return
        runCatching {
            MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
        }
    }

    private fun copyOrMoveRecursive(
        src: File,
        dst: File,
        move: Boolean,
        safTreeUris: Map<String, String>,
        onBytes: (copied: Long, total: Long) -> Unit,
    ): Long {
        if (src.isDirectory) {
            if (!dst.exists() && !dst.mkdirs()) {
                resolveSafDirectory(dst, safTreeUris, createMissing = true) ?: throwSafRequired(dst)
            }
            var total = 0L
            src.listFiles()?.forEach { child ->
                total += copyOrMoveRecursive(child, File(dst, child.name), move, safTreeUris, onBytes)
            }
            if (move) {
                deleteRecursivelyWithSaf(src, safTreeUris)
            }
            return total
        }

        val srcVol = volumeRootFor(src)?.path
        val dstVol = volumeRootFor(dst)?.path
        if (move && srcVol != null && srcVol == dstVol && src.renameTo(dst)) {
            onBytes(dst.length(), dst.length())
            return dst.length()
        }

        val srcSize = src.length()
        val copied = copyStreamToTargetFile(
            openIn = { FileInputStream(src) },
            expectedLen = srcSize,
            targetFile = dst,
            safTreeUris = safTreeUris,
            onBytes = { b -> onBytes(b, srcSize) },
        )
        if (copied != srcSize && srcSize > 0L) {
            runCatching { deleteRecursivelyWithSaf(dst, safTreeUris) }
            throw IOException("Size mismatch during transfer (${copied}/${srcSize} bytes)")
        }
        if (move) {
            deleteRecursivelyWithSaf(src, safTreeUris)
        }
        return copied
    }

    private fun copyStreamToTargetFile(
        openIn: () -> InputStream,
        expectedLen: Long,
        targetFile: File,
        safTreeUris: Map<String, String>,
        onBytes: (Long) -> Unit,
    ): Long {
        var total = 0L
        openIn().use { input ->
            openOutputStreamWritable(targetFile, safTreeUris).use { out ->
                val buf = ByteArray(256 * 1024)
                while (true) {
                    val read = input.read(buf)
                    if (read < 0) break
                    if (read > 0) {
                        out.write(buf, 0, read)
                        total += read
                        onBytes(total)
                    }
                }
                out.flush()
            }
        }
        if (expectedLen > 0 && total < expectedLen) {
            throw IOException("Incomplete copy ($total/$expectedLen)")
        }
        return total
    }

    private fun moveToTrash(target: File, safTreeUris: Map<String, String>) {
        val volRoot = volumeRootFor(target)?.let { File(it.path) }
            ?: Environment.getExternalStorageDirectory()
            ?: throw IOException("Cannot locate storage root for Trash")
        val trashDir = File(volRoot, TRASH_DIR_NAME)
        val trashDest = uniqueDestFile(trashDir, "${System.currentTimeMillis()}_${target.name}", "rename")!!
        copyOrMoveRecursive(target, trashDest, move = true, safTreeUris = safTreeUris) { _, _ -> }
    }

    fun deleteRecursivelyWithSaf(target: File, safTreeUris: Map<String, String>) {
        if (target.deleteRecursively() && !target.exists()) return
        val doc = resolveSafFile(target, safTreeUris) ?: throwSafRequired(target)
        if (!doc.delete()) throw IOException("Could not delete ${target.name} on SD card")
    }

    private fun resolveSafDirectory(
        dir: File,
        safTreeUris: Map<String, String>,
        createMissing: Boolean,
    ): DocumentFile? {
        val vol = volumeRootFor(dir) ?: return null
        val treeStr = safTreeUris[vol.path] ?: return null
        var cur = DocumentFile.fromTreeUri(context, Uri.parse(treeStr)) ?: return null
        val volCanon = vol.path.trimEnd(File.separatorChar)
        val dirCanon = runCatching { dir.canonicalPath }.getOrElse { dir.absolutePath }
        val rel = dirCanon.removePrefix(volCanon).trim(File.separatorChar)
        if (rel.isBlank()) return cur
        rel.split(File.separatorChar).forEach { seg ->
            if (seg.isBlank()) return@forEach
            val existing = cur.findFile(seg)
            cur = when {
                existing != null && existing.isDirectory -> existing
                createMissing -> cur.createDirectory(seg) ?: return null
                else -> return null
            }
        }
        return cur
    }

    private fun resolveSafFile(file: File, safTreeUris: Map<String, String>): DocumentFile? {
        val parent = file.parentFile ?: return null
        val dirDoc = resolveSafDirectory(parent, safTreeUris, createMissing = false) ?: return null
        return dirDoc.findFile(file.name)
    }

    private fun throwSafRequired(file: File): Nothing {
        val vol = volumeRootFor(file)
        if (vol != null && !vol.name.equals("Internal Storage", ignoreCase = true)) {
            throw SdCardWritePermissionRequiredException(vol.path, vol.name)
        }
        throw AllFilesPermissionRequiredException(file.absolutePath)
    }

    private fun uniqueDestFile(dir: File, desiredName: String, conflict: String): File? {
        val clean = File(desiredName).name.ifBlank { "file_${System.currentTimeMillis()}" }
        val initial = File(dir, clean)
        if (!initial.exists()) return initial
        if (conflict == "skip") return null
        if (conflict == "overwrite") return initial
        val dot = clean.lastIndexOf('.')
        val stem = if (dot > 0) clean.substring(0, dot) else clean
        val ext = if (dot > 0) clean.substring(dot) else ""
        for (i in 1..999) {
            val cand = File(dir, "$stem ($i)$ext")
            if (!cand.exists()) return cand
        }
        return File(dir, "${stem}_${System.currentTimeMillis()}$ext")
    }

    private fun queryMediaStoreCollection(
        virtualPath: String,
        sortBy: SortBy,
        descending: Boolean,
        filter: MediaFilter,
    ): ListResponse {
        val uri: Uri
        val defaultType: String
        val title: String
        when (virtualPath) {
            MEDIASTORE_IMAGES -> {
                uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                defaultType = "image"
                title = "All Device Photos"
            }
            MEDIASTORE_AUDIO -> {
                uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                defaultType = "audio"
                title = "All Device Audio"
            }
            else -> {
                uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                defaultType = "video"
                title = "All Device Videos"
            }
        }

        val items = queryMediaStoreUri(uri, defaultType, selection = null, selectionArgs = null, filter = filter)
        val sorted = sortFileItems(items, sortBy, descending)
        val primary = detectStorageDrives().firstOrNull()
        return ListResponse(
            path = virtualPath,
            name = title,
            parentPath = "",
            totalSizeBytes = sorted.sumOf { it.sizeBytes },
            fileCount = sorted.size,
            mediaCount = sorted.count { it.isVideo || it.isImage },
            diskFreeBytes = primary?.freeBytes ?: 0L,
            diskTotalBytes = primary?.totalBytes ?: 0L,
            folders = emptyList(),
            files = sorted,
        )
    }

    private fun queryMediaStoreByFolderHint(
        folderPath: String,
        sortBy: SortBy,
        descending: Boolean,
        filter: MediaFilter,
    ): List<FileItem> {
        val folderName = File(folderPath).name
        val cleanPath = folderPath.trimEnd('/')
        if (folderName.isBlank()) return emptyList()
        val sel = "${MediaStore.MediaColumns.DATA} LIKE ? OR ${MediaStore.MediaColumns.BUCKET_DISPLAY_NAME} = ?"
        val args = arrayOf("$cleanPath/%", folderName)
        val videos = queryMediaStoreUri(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video", sel, args, filter)
        val images = queryMediaStoreUri(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image", sel, args, filter)
        val audio = queryMediaStoreUri(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "audio", sel, args, filter)
        return sortFileItems((videos + images + audio).distinctBy { it.path }, sortBy, descending)
    }

    private fun queryMediaStoreUri(
        baseUri: Uri,
        defaultMediaType: String,
        selection: String?,
        selectionArgs: Array<String>?,
        filter: MediaFilter,
    ): List<FileItem> {
        val items = mutableListOf<FileItem>()
        val isVideo = defaultMediaType == "video"
        val isImage = defaultMediaType == "image"
        val cols = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.MediaColumns.DATA)
            if (isVideo || defaultMediaType == "audio") add(MediaStore.MediaColumns.DURATION)
            if (isVideo || isImage) {
                add(MediaStore.MediaColumns.WIDTH)
                add(MediaStore.MediaColumns.HEIGHT)
            }
        }.toTypedArray()

        runCatching {
            context.contentResolver.query(
                baseUri,
                cols,
                selection,
                selectionArgs,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val dateIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                val durIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)
                val wIdx = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                val hIdx = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)

                while (cursor.moveToNext() && items.size < 1200) {
                    val id = if (idIdx >= 0) cursor.getLong(idIdx) else continue
                    val name = (if (nameIdx >= 0) cursor.getString(nameIdx) else null) ?: "media_$id"
                    val sz = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                    val mtime = if (dateIdx >= 0) cursor.getLong(dateIdx) * 1000L else 0L
                    val diskPath = if (dataIdx >= 0) cursor.getString(dataIdx) else null
                    val dur = if (durIdx >= 0) cursor.getLong(durIdx) else 0L
                    val w = if (wIdx >= 0) cursor.getInt(wIdx) else 0
                    val h = if (hIdx >= 0) cursor.getInt(hIdx) else 0

                    if (!matchesMediaFilter(defaultMediaType, sz, filter)) continue

                    val contentUri = ContentUris.withAppendedId(baseUri, id).toString()
                    val resolvedPath = if (hasAllFilesAccess() && !diskPath.isNullOrBlank()) {
                        diskPath
                    } else {
                        contentUri
                    }

                    val meta = if (isVideo && (dur > 0L || w > 0)) {
                        VideoMeta(durationMs = dur, width = w, height = h)
                    } else null

                    items.add(
                        FileItem(
                            name = name,
                            path = resolvedPath,
                            sizeBytes = sz,
                            modifiedMs = mtime,
                            mediaType = defaultMediaType,
                            extension = "." + name.substringAfterLast('.', ""),
                            meta = meta,
                        )
                    )
                }
            }
        }
        return items
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private fun computeParentPath(dir: File): String {
        val canon = runCatching { dir.canonicalPath }.getOrElse { dir.absolutePath }
        val isDriveRoot = detectStorageDrives().any { it.path == canon }
        if (isDriveRoot) return ""
        val parent = dir.parentFile ?: return ""
        val parentCanon = runCatching { parent.canonicalPath }.getOrElse { parent.absolutePath }
        return if (detectStorageDrives().any { root ->
                parentCanon == root.path || parentCanon.startsWith("${root.path}${File.separator}")
            }) {
            parentCanon
        } else ""
    }

    private fun statVolume(dir: File): Pair<Long, Long> = runCatching {
        val sf = StatFs(dir.absolutePath)
        sf.totalBytes to sf.availableBytes
    }.getOrDefault(0L to 0L)

    private fun scanLargestRecursive(dir: File, depth: Int, out: MutableList<FileItem>) {
        if (depth > 8 || out.size > 4000) return
        val children = dir.listFiles() ?: return
        for (entry in children) {
            if (entry.name.startsWith(".")) continue
            if (entry.isDirectory) {
                if (!entry.name.equals("Android", ignoreCase = true) || depth == 0) {
                    scanLargestRecursive(entry, depth + 1, out)
                }
            } else if (entry.isFile && entry.length() > 2 * 1024 * 1024L) {
                val canon = runCatching { entry.canonicalPath }.getOrElse { entry.absolutePath }
                val mtype = classifyMedia(entry.name)
                out.add(
                    FileItem(
                        name = entry.name,
                        path = canon,
                        sizeBytes = entry.length(),
                        modifiedMs = entry.lastModified(),
                        mediaType = mtype,
                        extension = "." + entry.name.substringAfterLast('.', ""),
                    )
                )
            }
        }
    }

    private fun scanSearchRecursive(
        dir: File,
        queryLower: String,
        filter: MediaFilter,
        limit: Int,
        out: MutableList<FileItem>,
        depth: Int = 0,
    ) {
        if (depth > 8 || out.size >= limit) return
        val children = dir.listFiles() ?: return
        for (entry in children) {
            if (out.size >= limit) return
            if (entry.name.startsWith(".")) continue
            if (entry.isDirectory) {
                scanSearchRecursive(entry, queryLower, filter, limit, out, depth + 1)
            } else if (entry.isFile && entry.name.lowercase(Locale.US).contains(queryLower)) {
                val sz = entry.length()
                val mtype = classifyMedia(entry.name)
                if (matchesMediaFilter(mtype, sz, filter)) {
                    out.add(
                        FileItem(
                            name = entry.name,
                            path = runCatching { entry.canonicalPath }.getOrElse { entry.absolutePath },
                            sizeBytes = sz,
                            modifiedMs = entry.lastModified(),
                            mediaType = mtype,
                            extension = "." + entry.name.substringAfterLast('.', ""),
                        )
                    )
                }
            }
        }
    }

    private fun computeDirSize(dir: File): Long {
        var total = 0L
        dir.walkTopDown().maxDepth(6).forEach { f ->
            if (f.isFile) total += f.length()
        }
        return total
    }

    private fun matchesMediaFilter(mediaType: String, sizeBytes: Long, filter: MediaFilter): Boolean = when (filter) {
        MediaFilter.ALL -> true
        MediaFilter.VIDEO -> mediaType == "video"
        MediaFilter.IMAGE -> mediaType == "image"
        MediaFilter.MEDIA -> mediaType == "video" || mediaType == "image" || mediaType == "audio"
        MediaFilter.LARGE -> sizeBytes >= 100L * 1024L * 1024L
    }

    private fun sortFileItems(list: List<FileItem>, sortBy: SortBy, descending: Boolean): List<FileItem> {
        val comparator: Comparator<FileItem> = when (sortBy) {
            SortBy.SIZE -> compareBy { it.sizeBytes }
            SortBy.NAME -> compareBy { it.name.lowercase(Locale.US) }
            SortBy.DATE -> compareBy { it.modifiedMs }
            SortBy.DURATION -> compareBy { it.meta?.durationMs ?: 0L }
            SortBy.TYPE -> compareBy<FileItem> { it.mediaType }.thenBy { it.extension }
        }
        return if (descending) list.sortedWith(comparator.reversed()) else list.sortedWith(comparator)
    }

    private fun saveScaledJpeg(bmp: Bitmap, dest: File, maxW: Int, quality: Int) {
        val w = bmp.width.coerceAtLeast(1)
        val h = bmp.height.coerceAtLeast(1)
        val scaled = if (w > maxW) {
            val targetH = max(1, (h * (maxW.toDouble() / w)).toInt())
            Bitmap.createScaledBitmap(bmp, maxW, targetH, true)
        } else bmp
        FileOutputStream(dest).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        if (scaled !== bmp) scaled.recycle()
        bmp.recycle()
    }

    private fun guessMime(name: String): String = when (classifyMedia(name)) {
        "video" -> "video/${name.substringAfterLast('.', "mp4").lowercase(Locale.US)}"
        "image" -> "image/${name.substringAfterLast('.', "jpeg").lowercase(Locale.US)}"
        "audio" -> "audio/${name.substringAfterLast('.', "mpeg").lowercase(Locale.US)}"
        else -> "application/octet-stream"
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
}
