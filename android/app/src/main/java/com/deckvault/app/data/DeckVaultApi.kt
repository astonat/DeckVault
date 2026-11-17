package com.deckvault.app.data

import com.deckvault.app.model.CompressEstimateRequest
import com.deckvault.app.model.CompressEstimateResponse
import com.deckvault.app.model.CompressRequest
import com.deckvault.app.model.CreateFolderRequest
import com.deckvault.app.model.CreateFolderResponse
import com.deckvault.app.model.DeleteRequest
import com.deckvault.app.model.DeleteResponse
import com.deckvault.app.model.EditStudioRequest
import com.deckvault.app.model.EnqueueJobResponse
import com.deckvault.app.model.ExtractAudioRequest
import com.deckvault.app.model.GifExportRequest
import com.deckvault.app.model.JobsSnapshotResponse
import com.deckvault.app.model.LargestResponse
import com.deckvault.app.model.ListResponse
import com.deckvault.app.model.MediaOpResponse
import com.deckvault.app.model.MergeRequest
import com.deckvault.app.model.OffloadStageResponse
import com.deckvault.app.model.PairRequest
import com.deckvault.app.model.PairResponse
import com.deckvault.app.model.PingResponse
import com.deckvault.app.model.RemotePullRequest
import com.deckvault.app.model.RenameRequest
import com.deckvault.app.model.RootsResponse
import com.deckvault.app.model.SavedHost
import com.deckvault.app.model.SearchResponse
import com.deckvault.app.model.TransferRequest
import com.deckvault.app.model.TransferResponse
import com.deckvault.app.model.TreeResponse
import com.deckvault.app.model.TrimRequest
import com.deckvault.app.model.TrimResponse
import com.deckvault.app.model.UploadResponse
import com.deckvault.app.model.UpscaleRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

class DeckVaultApi {

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val trimClient: OkHttpClient = okHttpClient.newBuilder()
        .readTimeout(15, TimeUnit.MINUTES)
        .writeTimeout(2, TimeUnit.MINUTES)
        .build()

    private val transferClient: OkHttpClient = okHttpClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.MINUTES)
        .writeTimeout(30, TimeUnit.MINUTES)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val octetMediaType = "application/octet-stream".toMediaType()

    suspend fun ping(ip: String, port: Int): PingResponse = withContext(Dispatchers.IO) {
        val url = "http://$ip:$port/api/ping"
        val req = Request.Builder().url(url).get().build()
        executeJson(okHttpClient, req, PingResponse.serializer())
    }

    suspend fun pair(ip: String, port: Int, pin: String, clientName: String): PairResponse = withContext(Dispatchers.IO) {
        val url = "http://$ip:$port/api/pair"
        val body = json.encodeToString(PairRequest.serializer(), PairRequest(pin = pin, clientName = clientName))
        val req = Request.Builder().url(url).post(body.toRequestBody(jsonMediaType)).build()
        executeJson(okHttpClient, req, PairResponse.serializer())
    }

    suspend fun getRoots(host: SavedHost): RootsResponse = withContext(Dispatchers.IO) {
        val url = buildUrl(host, "/api/roots").build()
        executeAuthedGet(host, url, RootsResponse.serializer())
    }

    suspend fun getTree(host: SavedHost, path: String, sort: String = "size"): TreeResponse = withContext(Dispatchers.IO) {
        val url = buildUrl(host, "/api/tree")
            .addQueryParameter("path", path)
            .addQueryParameter("sort", sort)
            .build()
        executeAuthedGet(host, url, TreeResponse.serializer())
    }

    suspend fun listFolder(
        host: SavedHost,
        path: String,
        sort: String,
        order: String,
        filter: String,
        query: String = "",
    ): ListResponse = withContext(Dispatchers.IO) {
        val url = buildUrl(host, "/api/list")
            .addQueryParameter("path", path)
            .addQueryParameter("sort", sort)
            .addQueryParameter("order", order)
            .addQueryParameter("filter", filter)
            .apply { if (query.isNotBlank()) addQueryParameter("q", query) }
            .build()
        executeAuthedGet(host, url, ListResponse.serializer())
    }

    suspend fun getLargest(host: SavedHost, path: String, filter: String = "video", limit: Int = 80): LargestResponse =
        withContext(Dispatchers.IO) {
            val url = buildUrl(host, "/api/largest")
                .addQueryParameter("path", path)
                .addQueryParameter("filter", filter)
                .addQueryParameter("limit", limit.toString())
                .build()
            executeAuthedGet(host, url, LargestResponse.serializer())
        }

    suspend fun search(host: SavedHost, path: String, query: String): SearchResponse = withContext(Dispatchers.IO) {
        val url = buildUrl(host, "/api/search")
            .addQueryParameter("path", path)
            .addQueryParameter("q", query)
            .build()
        executeAuthedGet(host, url, SearchResponse.serializer())
    }

    suspend fun rescan(host: SavedHost, path: String? = null) = withContext(Dispatchers.IO) {
        val url = buildUrl(host, "/api/rescan").build()
        val bodyStr = if (path != null) """{"path":${json.encodeToString(kotlinx.serialization.serializer(), path)}}""" else "{}"
        val req = Request.Builder()
            .url(url)
            .header("X-DeckVault-Token", host.token)
            .post(bodyStr.toRequestBody(jsonMediaType))
            .build()
        okHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Rescan failed: HTTP ${resp.code}")
        }
    }

    suspend fun trimVideo(host: SavedHost, request: TrimRequest): TrimResponse = withContext(Dispatchers.IO) {
        val url = buildUrl(host, "/api/video/trim").build()
        val body = json.encodeToString(TrimRequest.serializer(), request)
        val req = Request.Builder()
            .url(url)
            .header("X-DeckVault-Token", host.token)
            .post(body.toRequestBody(jsonMediaType))
            .build()
        executeJson(trimClient, req, TrimResponse.serializer())
    }

    suspend fun deleteFiles(host: SavedHost, paths: List<String>, recycle: Boolean): DeleteResponse =
        withContext(Dispatchers.IO) {
            val url = buildUrl(host, "/api/file/delete").build()
            val body = json.encodeToString(DeleteRequest.serializer(), DeleteRequest(paths, recycle))
            val req = Request.Builder()
                .url(url)
                .header("X-DeckVault-Token", host.token)
                .post(body.toRequestBody(jsonMediaType))
                .build()
            executeJson(okHttpClient, req, DeleteResponse.serializer())
        }

    suspend fun renameFile(host: SavedHost, path: String, newName: String) = withContext(Dispatchers.IO) {
        val url = buildUrl(host, "/api/file/rename").build()
        val body = json.encodeToString(RenameRequest.serializer(), RenameRequest(path, newName))
        val req = Request.Builder()
            .url(url)
            .header("X-DeckVault-Token", host.token)
            .post(body.toRequestBody(jsonMediaType))
            .build()
        okHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string().orEmpty()
                throw IOException("Rename failed (${resp.code}): $err")
            }
        }
    }

    suspend fun createFolder(host: SavedHost, parentDir: String, name: String): CreateFolderResponse =
        withContext(Dispatchers.IO) {
            val url = buildUrl(host, "/api/folder/create").build()
            val body = json.encodeToString(CreateFolderRequest.serializer(), CreateFolderRequest(parentDir, name))
            val req = Request.Builder()
                .url(url)
                .header("X-DeckVault-Token", host.token)
                .post(body.toRequestBody(jsonMediaType))
                .build()
            executeJson(okHttpClient, req, CreateFolderResponse.serializer())
        }

    suspend fun transferLocal(
        host: SavedHost,
        paths: List<String>,
        destinationDir: String,
        move: Boolean,
        conflict: String = "rename",
    ): TransferResponse = withContext(Dispatchers.IO) {
        val url = buildUrl(host, "/api/file/transfer").build()
        val body = json.encodeToString(
            TransferRequest.serializer(),
            TransferRequest(paths, destinationDir, if (move) "move" else "copy", conflict),
        )
        val req = Request.Builder()
            .url(url)
            .header("X-DeckVault-Token", host.token)
            .post(body.toRequestBody(jsonMediaType))
            .build()
        executeJson(transferClient, req, TransferResponse.serializer())
    }

    suspend fun pullRemote(
        targetHost: SavedHost,
        sourceHost: SavedHost,
        paths: List<String>,
        destinationDir: String,
        conflict: String = "rename",
    ): TransferResponse = withContext(Dispatchers.IO) {
        val url = buildUrl(targetHost, "/api/file/pull").build()
        val body = json.encodeToString(
            RemotePullRequest.serializer(),
            RemotePullRequest(
                sourceBaseUrl = sourceHost.baseUrl,
                sourceToken = sourceHost.token,
                paths = paths,
                destinationDir = destinationDir,
                conflict = conflict,
            ),
        )
        val req = Request.Builder()
            .url(url)
            .header("X-DeckVault-Token", targetHost.token)
            .post(body.toRequestBody(jsonMediaType))
            .build()
        executeJson(transferClient, req, TransferResponse.serializer())
    }

    suspend fun uploadFromStream(
        host: SavedHost,
        destDir: String,
        fileName: String,
        contentLength: Long,
        openStream: () -> InputStream?,
        onProgress: (bytesWritten: Long) -> Unit = {},
    ): UploadResponse = withContext(Dispatchers.IO) {
        val url = buildUrl(host, "/api/file/upload")
            .addQueryParameter("dir", destDir)
            .addQueryParameter("name", fileName)
            .addQueryParameter("conflict", "rename")
            .build()

        val body = streamingRequestBody(fileName, contentLength, openStream, onProgress)

        val req = Request.Builder()
            .url(url)
            .header("X-DeckVault-Token", host.token)
            .post(body)
            .build()
        executeJson(transferClient, req, UploadResponse.serializer())
    }

    suspend fun stageOffloadFile(
        host: SavedHost,
        stageId: String,
        fileName: String,
        contentLength: Long,
        openStream: () -> InputStream?,
        onProgress: (bytesWritten: Long) -> Unit = {},
    ): OffloadStageResponse = withContext(Dispatchers.IO) {
        val url = buildUrl(host, "/api/offload/stage")
            .addQueryParameter("stageId", stageId)
            .addQueryParameter("name", fileName)
            .build()
        val body = streamingRequestBody(fileName, contentLength, openStream, onProgress)
        val req = Request.Builder()
            .url(url)
            .header("X-DeckVault-Token", host.token)
            .post(body)
            .build()
        executeJson(transferClient, req, OffloadStageResponse.serializer())
    }

    private fun streamingRequestBody(
        fileName: String,
        contentLength: Long,
        openStream: () -> InputStream?,
        onProgress: (bytesWritten: Long) -> Unit,
    ): RequestBody = object : RequestBody() {
        override fun contentType(): MediaType = octetMediaType
        override fun contentLength(): Long = contentLength.coerceAtLeast(-1L)
        override fun isOneShot(): Boolean = true
        override fun writeTo(sink: BufferedSink) {
            val input = openStream() ?: throw IOException("Could not open input stream for $fileName")
            input.use { stream ->
                val buf = ByteArray(128 * 1024)
                var total = 0L
                while (true) {
                    val read = stream.read(buf)
                    if (read < 0) break
                    if (read > 0) {
                        sink.write(buf, 0, read)
                        total += read
                        onProgress(total)
                    }
                }
            }
        }
    }

    suspend fun relayFileBetweenHosts(
        sourceHost: SavedHost,
        sourcePath: String,
        fileName: String,
        expectedBytes: Long,
        targetHost: SavedHost,
        destinationDir: String,
        onProgress: (bytesCopied: Long) -> Unit = {},
    ): UploadResponse = withContext(Dispatchers.IO) {
        val getUrl = buildUrl(sourceHost, "/api/stream")
            .addQueryParameter("path", sourcePath)
            .build()
        val getReq = Request.Builder()
            .url(getUrl)
            .header("X-DeckVault-Token", sourceHost.token)
            .get()
            .build()
        transferClient.newCall(getReq).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("Stream read failed (${resp.code})")
            }
            val bodyStream = resp.body?.byteStream() ?: throw IOException("Empty stream body")
            val len = resp.body?.contentLength()?.takeIf { it >= 0 } ?: expectedBytes.coerceAtLeast(-1L)
            uploadFromStream(
                host = targetHost,
                destDir = destinationDir,
                fileName = fileName,
                contentLength = len,
                openStream = { bodyStream },
                onProgress = onProgress,
            )
        }
    }

    suspend fun downloadFile(
        host: SavedHost,
        remotePath: String,
        openOutput: () -> OutputStream,
        onProgress: (bytesRead: Long) -> Unit = {},
    ): Long = withContext(Dispatchers.IO) {
        val url = buildUrl(host, "/api/stream")
            .addQueryParameter("path", remotePath)
            .build()
        val req = Request.Builder()
            .url(url)
            .header("X-DeckVault-Token", host.token)
            .get()
            .build()
        transferClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("Download failed (${resp.code})")
            }
            val inStream = resp.body?.byteStream() ?: throw IOException("Empty response body")
            var total = 0L
            inStream.use { src ->
                openOutput().use { dst ->
                    val buf = ByteArray(128 * 1024)
                    while (true) {
                        val read = src.read(buf)
                        if (read < 0) break
                        if (read > 0) {
                            dst.write(buf, 0, read)
                            total += read
                            onProgress(total)
                        }
                    }
                }
            }
            total
        }
    }

    fun videoThumbnailUrl(host: SavedHost, path: String, width: Int = 480, cacheBust: Long = 0L): String {
        if (host.isLocalDevice) return DeckVaultLocalMediaUris.thumbUri(path, width, cacheBust)
        return buildUrl(host, "/api/video/thumbnail")
            .addQueryParameter("path", path)
            .addQueryParameter("w", width.toString())
            .addQueryParameter("token", host.token)
            .apply { if (cacheBust > 0) addQueryParameter("v", cacheBust.toString()) }
            .build().toString()
    }

    fun videoStoryboardUrl(host: SavedHost, path: String, cols: Int = 4, rows: Int = 4, cacheBust: Long = 0L): String {
        if (host.isLocalDevice) return DeckVaultLocalMediaUris.storyboardUri(path, cols, rows, cacheBust)
        return buildUrl(host, "/api/video/storyboard")
            .addQueryParameter("path", path)
            .addQueryParameter("cols", cols.toString())
            .addQueryParameter("rows", rows.toString())
            .addQueryParameter("token", host.token)
            .apply { if (cacheBust > 0) addQueryParameter("v", cacheBust.toString()) }
            .build().toString()
    }

    fun videoFrameUrl(host: SavedHost, path: String, ms: Long, width: Int = 480): String {
        if (host.isLocalDevice) return DeckVaultLocalMediaUris.frameUri(path, ms, width)
        return buildUrl(host, "/api/video/frame")
            .addQueryParameter("path", path)
            .addQueryParameter("ms", ms.toString())
            .addQueryParameter("w", width.toString())
            .addQueryParameter("token", host.token)
            .build().toString()
    }

    fun imageUrl(host: SavedHost, path: String, width: Int = 0, cacheBust: Long = 0L): String {
        if (host.isLocalDevice) return DeckVaultLocalMediaUris.localImageUri(path)
        return buildUrl(host, "/api/image")
            .addQueryParameter("path", path)
            .apply { if (width > 0) addQueryParameter("w", width.toString()) }
            .addQueryParameter("token", host.token)
            .apply { if (cacheBust > 0) addQueryParameter("v", cacheBust.toString()) }
            .build().toString()
    }

    fun streamUrl(host: SavedHost, path: String, cacheBust: Long = 0L): String {
        if (host.isLocalDevice) {
            return if (LocalStorageRepository.isContentUriPath(path)) path else android.net.Uri.fromFile(java.io.File(path)).toString()
        }
        return buildUrl(host, "/api/stream")
            .addQueryParameter("path", path)
            .addQueryParameter("token", host.token)
            .apply { if (cacheBust > 0) addQueryParameter("v", cacheBust.toString()) }
            .build().toString()
    }

    fun playableStreamUrlOrNull(host: SavedHost, path: String, startSec: Double = 0.0, cacheBust: Long = 0L): String? =
        if (host.isLocalDevice || host.os.equals("Android", ignoreCase = true)) null
        else playableStreamUrl(host, path, startSec, cacheBust)

    fun playableStreamUrl(host: SavedHost, path: String, startSec: Double = 0.0, cacheBust: Long = 0L): String =
        buildUrl(host, "/api/stream/playable")
            .addQueryParameter("path", path)
            .apply { if (startSec > 0.1) addQueryParameter("start", startSec.toString()) }
            .addQueryParameter("token", host.token)
            .apply { if (cacheBust > 0) addQueryParameter("v", cacheBust.toString()) }
            .build().toString()

    suspend fun editStudio(host: SavedHost, request: EditStudioRequest): MediaOpResponse =
        executeAuthedPostJson(host, "/api/video/edit", json.encodeToString(EditStudioRequest.serializer(), request), MediaOpResponse.serializer(), trimClient)

    suspend fun estimateCompression(host: SavedHost, request: CompressEstimateRequest): CompressEstimateResponse =
        executeAuthedPostJson(host, "/api/media/compress-estimate", json.encodeToString(CompressEstimateRequest.serializer(), request), CompressEstimateResponse.serializer(), okHttpClient)

    suspend fun compressMedia(host: SavedHost, request: CompressRequest): MediaOpResponse =
        executeAuthedPostJson(host, "/api/media/compress", json.encodeToString(CompressRequest.serializer(), request), MediaOpResponse.serializer(), trimClient)

    suspend fun upscaleMedia(host: SavedHost, request: UpscaleRequest): MediaOpResponse =
        executeAuthedPostJson(host, "/api/media/upscale", json.encodeToString(UpscaleRequest.serializer(), request), MediaOpResponse.serializer(), trimClient)

    suspend fun extractAudio(host: SavedHost, request: ExtractAudioRequest): MediaOpResponse =
        executeAuthedPostJson(host, "/api/media/extract-audio", json.encodeToString(ExtractAudioRequest.serializer(), request), MediaOpResponse.serializer(), trimClient)

    suspend fun exportGif(host: SavedHost, request: GifExportRequest): MediaOpResponse =
        executeAuthedPostJson(host, "/api/media/gif", json.encodeToString(GifExportRequest.serializer(), request), MediaOpResponse.serializer(), trimClient)

    suspend fun mergeVideos(host: SavedHost, request: MergeRequest): MediaOpResponse =
        executeAuthedPostJson(host, "/api/media/merge", json.encodeToString(MergeRequest.serializer(), request), MediaOpResponse.serializer(), trimClient)

    suspend fun getJobsSnapshot(host: SavedHost): JobsSnapshotResponse = withContext(Dispatchers.IO) {
        executeAuthedGet(host, buildUrl(host, "/api/jobs").build(), JobsSnapshotResponse.serializer())
    }

    suspend fun enqueueRemoteJob(host: SavedHost, jobType: String, payloadJson: JsonObject): EnqueueJobResponse {
        val body = buildJsonObject {
            put("type", jobType)
            put("payload", payloadJson)
        }
        return executeAuthedPostJson(host, "/api/jobs/enqueue", json.encodeToString(JsonObject.serializer(), body), EnqueueJobResponse.serializer(), okHttpClient)
    }

    suspend fun cancelRemoteJob(host: SavedHost, jobId: String): EnqueueJobResponse {
        val body = buildJsonObject { put("id", jobId) }
        return executeAuthedPostJson(host, "/api/jobs/cancel", json.encodeToString(JsonObject.serializer(), body), EnqueueJobResponse.serializer(), okHttpClient)
    }

    suspend fun clearRemoteJobs(host: SavedHost): JobsSnapshotResponse {
        val url = buildUrl(host, "/api/jobs/clear").build()
        return withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url(url)
                .header("X-DeckVault-Token", host.token)
                .post("{}".toRequestBody(jsonMediaType))
                .build()
            executeJson(okHttpClient, req, JsonObject.serializer())
            getJobsSnapshot(host)
        }
    }

    suspend fun enqueueOffloadJob(
        host: SavedHost,
        stageId: String,
        jobType: String,
        stagedName: String? = null,
        stagedNames: List<String> = emptyList(),
        payloadJson: JsonObject,
    ): EnqueueJobResponse {
        val body = buildJsonObject {
            put("type", jobType)
            put("stageId", stageId)
            if (!stagedName.isNullOrBlank()) put("stagedName", stagedName)
            if (stagedNames.isNotEmpty()) {
                put("stagedNames", buildJsonArray { stagedNames.forEach { add(it) } })
            }
            put("payload", payloadJson)
        }
        return executeAuthedPostJson(host, "/api/offload/enqueue", json.encodeToString(JsonObject.serializer(), body), EnqueueJobResponse.serializer(), okHttpClient)
    }

    suspend fun cleanupOffloadStage(host: SavedHost, stageId: String) = withContext(Dispatchers.IO) {
        val body = buildJsonObject { put("stageId", stageId) }
        val req = Request.Builder()
            .url(buildUrl(host, "/api/offload/cleanup").build())
            .header("X-DeckVault-Token", host.token)
            .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody(jsonMediaType))
            .build()
        runCatching { okHttpClient.newCall(req).execute().close() }
    }

    private suspend fun <T> executeAuthedPostJson(
        host: SavedHost,
        route: String,
        jsonBody: String,
        serializer: KSerializer<T>,
        client: OkHttpClient,
    ): T = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(buildUrl(host, route).build())
            .header("X-DeckVault-Token", host.token)
            .post(jsonBody.toRequestBody(jsonMediaType))
            .build()
        executeJson(client, req, serializer)
    }

    private fun buildUrl(host: SavedHost, route: String): HttpUrl.Builder =
        "${host.baseUrl}$route".toHttpUrl().newBuilder()

    private fun <T> executeAuthedGet(
        host: SavedHost,
        url: HttpUrl,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): T {
        val req = Request.Builder()
            .url(url)
            .header("X-DeckVault-Token", host.token)
            .get()
            .build()
        return executeJson(okHttpClient, req, serializer)
    }

    private fun <T> executeJson(
        client: OkHttpClient,
        req: Request,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): T {
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException("HTTP ${resp.code}: ${text.take(240)}")
            }
            return json.decodeFromString(serializer, text)
        }
    }
}
