package com.deckvault.app.data

import android.net.Uri
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File
import java.io.IOException

object DeckVaultLocalMediaUris {
    const val SCHEME = "deckvault-local"

    fun thumbUri(path: String, width: Int = 420, cacheBust: Long = 0L): String =
        Uri.Builder()
            .scheme(SCHEME)
            .authority("thumb")
            .appendQueryParameter("path", path)
            .appendQueryParameter("w", width.toString())
            .apply { if (cacheBust > 0L) appendQueryParameter("v", cacheBust.toString()) }
            .build()
            .toString()

    fun storyboardUri(path: String, cols: Int = 4, rows: Int = 4, cacheBust: Long = 0L): String =
        Uri.Builder()
            .scheme(SCHEME)
            .authority("storyboard")
            .appendQueryParameter("path", path)
            .appendQueryParameter("cols", cols.toString())
            .appendQueryParameter("rows", rows.toString())
            .apply { if (cacheBust > 0L) appendQueryParameter("v", cacheBust.toString()) }
            .build()
            .toString()

    fun frameUri(path: String, ms: Long, width: Int = 360): String =
        Uri.Builder()
            .scheme(SCHEME)
            .authority("frame")
            .appendQueryParameter("path", path)
            .appendQueryParameter("ms", ms.toString())
            .appendQueryParameter("w", width.toString())
            .build()
            .toString()

    fun localImageUri(path: String): String =
        if (LocalStorageRepository.isContentUriPath(path)) path else Uri.fromFile(File(path)).toString()
}

class DeckVaultLocalFetcherFactory(
    private val localRepo: LocalStorageRepository,
) : Fetcher.Factory<coil3.Uri> {

    override fun create(
        data: coil3.Uri,
        options: Options,
        imageLoader: ImageLoader,
    ): Fetcher? {
        if (data.scheme != DeckVaultLocalMediaUris.SCHEME) return null
        val androidUri = Uri.parse(data.toString())
        val kind = androidUri.authority ?: return null
        val rawPath = androidUri.getQueryParameter("path") ?: return null
        return LocalFetcher(androidUri, kind, rawPath, localRepo)
    }

    private class LocalFetcher(
        private val uri: Uri,
        private val kind: String,
        private val rawPath: String,
        private val localRepo: LocalStorageRepository,
    ) : Fetcher {
        override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
            val outFile = when (kind) {
                "thumb" -> {
                    val w = uri.getQueryParameter("w")?.toIntOrNull() ?: 420
                    localRepo.getOrBuildThumbnailFile(rawPath, w)
                }
                "storyboard" -> {
                    val cols = uri.getQueryParameter("cols")?.toIntOrNull() ?: 4
                    val rows = uri.getQueryParameter("rows")?.toIntOrNull() ?: 4
                    localRepo.getOrBuildStoryboardFile(rawPath, cols, rows)
                }
                "frame" -> {
                    val ms = uri.getQueryParameter("ms")?.toLongOrNull() ?: 0L
                    val w = uri.getQueryParameter("w")?.toIntOrNull() ?: 360
                    localRepo.getOrBuildVideoFrameFile(rawPath, ms, w)
                }
                else -> null
            } ?: throw IOException("No preview frame available for $rawPath")

            SourceFetchResult(
                source = ImageSource(
                    file = outFile.toOkioPath(),
                    fileSystem = FileSystem.SYSTEM,
                ),
                mimeType = "image/jpeg",
                dataSource = DataSource.DISK,
            )
        }
    }
}
