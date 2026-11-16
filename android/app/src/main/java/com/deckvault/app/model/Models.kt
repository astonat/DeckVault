package com.deckvault.app.model

import kotlinx.serialization.Serializable

@Serializable
data class SavedHost(
    val hostId: String,
    val name: String,
    val ip: String,
    val port: Int = 48720,
    val token: String,
    val label: String = "",
    val lastKnownFreeBytes: Long = 0L,
    val lastKnownTotalBytes: Long = 0L,
    val os: String = "Windows",
) {
    val displayName: String get() = label.ifBlank { name }
    val baseUrl: String get() = "http://$ip:$port"
}

@Serializable
data class DiscoveredHost(
    val service: String = "DeckVault",
    val version: String = "",
    val hostId: String,
    val deviceName: String,
    val port: Int = 48720,
    val ips: List<String> = emptyList(),
    val freeBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val ffmpeg: Boolean = false,
    val os: String = "Windows",
)

@Serializable
data class IndexerStatus(
    val scanning: Boolean = false,
    val indexedDirs: Int = 0,
    val lastScanEpoch: Long = 0L,
)

@Serializable
data class PingResponse(
    val service: String = "",
    val version: String = "",
    val hostId: String = "",
    val deviceName: String = "",
    val os: String = "Windows",
    val ffmpeg: Boolean = false,
    val spaceSavedBytes: Long = 0L,
    val indexer: IndexerStatus = IndexerStatus(),
)

@Serializable
data class PairRequest(
    val pin: String,
    val clientName: String,
)

@Serializable
data class PairResponse(
    val token: String,
    val hostId: String,
    val deviceName: String,
    val ffmpeg: Boolean = false,
    val spaceSavedBytes: Long = 0L,
    val os: String = "Windows",
)

@Serializable
data class DriveInfo(
    val name: String,
    val path: String,
    val totalBytes: Long = 0L,
    val usedBytes: Long = 0L,
    val freeBytes: Long = 0L,
    val indexedSizeBytes: Long = -1L,
    val fileCount: Int = -1,
    val mediaCount: Int = -1,
)

@Serializable
data class QuickFolder(
    val name: String,
    val path: String,
    val sizeBytes: Long = -1L,
    val fileCount: Int = -1,
    val mediaCount: Int = -1,
)

@Serializable
data class RootsResponse(
    val deviceName: String,
    val hostId: String,
    val os: String = "Windows",
    val ffmpeg: Boolean = false,
    val spaceSavedBytes: Long = 0L,
    val indexer: IndexerStatus = IndexerStatus(),
    val drives: List<DriveInfo> = emptyList(),
    val quickFolders: List<QuickFolder> = emptyList(),
)

data class HostDrivesSnapshot(
    val host: SavedHost? = null,
    val isLocalDevice: Boolean = false,
    val deviceName: String,
    val os: String = "Windows",
    val online: Boolean = true,
    val drives: List<DriveInfo> = emptyList(),
    val quickFolders: List<QuickFolder> = emptyList(),
    val error: String? = null,
)

@Serializable
data class FolderItem(
    val name: String,
    val path: String,
    val sizeBytes: Long = -1L,
    val percentOfParent: Double = 0.0,
    val fileCount: Int = -1,
    val mediaCount: Int = -1,
    val hasSubfolders: Boolean = true,
)

@Serializable
data class TreeResponse(
    val path: String,
    val sizeBytes: Long = 0L,
    val children: List<FolderItem> = emptyList(),
)

@Serializable
data class VideoMeta(
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val fps: Double = 0.0,
    val videoCodec: String = "",
    val audioCodec: String = "",
    val bitrate: Long = 0L,
)

@Serializable
data class FileItem(
    val name: String,
    val path: String,
    val sizeBytes: Long = 0L,
    val modifiedMs: Long = 0L,
    val mediaType: String = "other",
    val extension: String = "",
    val meta: VideoMeta? = null,
) {
    val isVideo: Boolean get() = mediaType == "video"
    val isImage: Boolean get() = mediaType == "image"
    val isAudio: Boolean get() = mediaType == "audio"
    val isPdf: Boolean get() = extension.trimStart('.').equals("pdf", ignoreCase = true)
    val isText: Boolean get() = extension.trimStart('.').lowercase() in TEXT_EXTS

    companion object {
        private val TEXT_EXTS = setOf(
            "txt", "md", "markdown", "json", "xml", "csv", "tsv", "log",
            "py", "kt", "kts", "java", "js", "ts", "tsx", "jsx", "html", "htm",
            "css", "scss", "ini", "cfg", "conf", "yaml", "yml", "toml",
            "bat", "cmd", "ps1", "sh", "bash", "sql", "c", "cpp", "h", "hpp", "rs", "go", "swift",
        )
    }
}

@Serializable
data class ListResponse(
    val path: String,
    val name: String,
    val parentPath: String = "",
    val totalSizeBytes: Long = 0L,
    val fileCount: Int = 0,
    val mediaCount: Int = 0,
    val diskFreeBytes: Long = 0L,
    val diskTotalBytes: Long = 0L,
    val indexer: IndexerStatus = IndexerStatus(),
    val folders: List<FolderItem> = emptyList(),
    val files: List<FileItem> = emptyList(),
)

@Serializable
data class LargestResponse(
    val path: String = "",
    val files: List<FileItem> = emptyList(),
)

@Serializable
data class SearchResponse(
    val files: List<FileItem> = emptyList(),
)

@Serializable
data class TrimRequest(
    val path: String,
    val startMs: Long,
    val endMs: Long,
    val mode: String = "lossless",
    val crf: Int = 23,
    val recycleOriginal: Boolean = true,
)

@Serializable
data class TrimResponse(
    val ok: Boolean,
    val path: String,
    val oldSizeBytes: Long,
    val newSizeBytes: Long,
    val savedBytes: Long,
    val sizeDeltaBytes: Long,
    val newDurationMs: Long,
    val recycledOriginal: Boolean,
    val totalSpaceSavedBytes: Long = 0L,
)

@Serializable
data class DeleteRequest(
    val paths: List<String>,
    val recycle: Boolean = true,
)

@Serializable
data class DeleteResponse(
    val deletedCount: Int = 0,
    val freedBytes: Long = 0L,
    val totalSpaceSavedBytes: Long = 0L,
    val errors: List<String> = emptyList(),
)

@Serializable
data class RenameRequest(
    val path: String,
    val newName: String,
)

@Serializable
data class CreateFolderRequest(
    val parentDir: String,
    val name: String,
)

@Serializable
data class CreateFolderResponse(
    val ok: Boolean = false,
    val path: String = "",
    val name: String = "",
)

@Serializable
data class TransferRequest(
    val paths: List<String>,
    val destinationDir: String,
    val mode: String = "move",
    val conflict: String = "rename",
)

@Serializable
data class RemotePullRequest(
    val sourceBaseUrl: String,
    val sourceToken: String,
    val paths: List<String>,
    val destinationDir: String,
    val conflict: String = "rename",
)

@Serializable
data class TransferredItemResult(
    val src: String = "",
    val dest: String = "",
    val sizeBytes: Long = 0L,
)

@Serializable
data class TransferResponse(
    val transferredCount: Int = 0,
    val transferredBytes: Long = 0L,
    val results: List<TransferredItemResult> = emptyList(),
    val errors: List<String> = emptyList(),
)

@Serializable
data class UploadResponse(
    val ok: Boolean = false,
    val path: String = "",
    val name: String = "",
    val sizeBytes: Long = 0L,
    val skipped: Boolean = false,
)

enum class ViewMode(val label: String) {
    GALLERY("Gallery"),
    LIST("List"),
}

enum class SortBy(val apiKey: String, val label: String) {
    SIZE("size", "Size"),
    NAME("name", "Name"),
    DATE("date", "Modified"),
    DURATION("duration", "Duration"),
    TYPE("type", "Type"),
}

enum class MediaFilter(val apiKey: String, val label: String) {
    ALL("all", "All"),
    VIDEO("video", "Videos"),
    IMAGE("image", "Photos"),
    MEDIA("media", "Media"),
    LARGE("large", ">100 MB"),
}

enum class CardAspect(val label: String, val ratio: Float) {
    WIDESCREEN("16:9", 16f / 9f),
    CLASSIC("4:3", 4f / 3f),
    SQUARE("1:1", 1f),
    PORTRAIT("3:4", 0.75f),
}

enum class AccentPalette(val label: String) {
    PLAIN_BLACK("Plain Black"),
    MATTE_SLATE("Matte Slate"),
    MATTE_SAGE("Matte Sage"),
    MATTE_CLAY("Matte Clay"),
    DYNAMIC_YOU("Dynamic You"),
}

enum class FoldLayoutMode(val label: String) {
    AUTO("Adaptive"),
    SPLIT("Side-by-Side"),
    DRAWER("Slide Drawer"),
}

@Serializable
data class UserPreferences(
    val hosts: List<SavedHost> = emptyList(),
    val activeHostId: String? = null,
    val viewMode: ViewMode = ViewMode.GALLERY,
    val gridColumns: Int = 0, // 0 = Auto adaptive
    val cardAspect: CardAspect = CardAspect.WIDESCREEN,
    val showFileSize: Boolean = true,
    val showDuration: Boolean = true,
    val showResolutionBadge: Boolean = true,
    val showFileName: Boolean = true,
    val showModifiedDate: Boolean = true,
    val enableHoverScrub: Boolean = true,
    val compactListMode: Boolean = false,
    val sortBy: SortBy = SortBy.SIZE,
    val sortDescending: Boolean = true,
    val mediaFilter: MediaFilter = MediaFilter.ALL,
    val recycleByDefault: Boolean = true,
    val accentPalette: AccentPalette = AccentPalette.MATTE_SLATE,
    val pureBlackBackground: Boolean = true,
    val foldLayoutMode: FoldLayoutMode = FoldLayoutMode.AUTO,
    val folderPaneWidthFraction: Float = 0.34f,
    val showDriveGaugesInPane: Boolean = true,
    val showQuickFoldersInPane: Boolean = true,
    val showSubfolderCounts: Boolean = true,
    val defaultTrimMode: String = "lossless",
    val defaultCompressCrf: Int = 23,
    val defaultPlaybackSpeed: Float = 1.0f,
    val seekStepSeconds: Int = 10,
    val autoPlayVideos: Boolean = true,
    val deviceShareEnabled: Boolean = true,
)
