package com.deckvault.app.ui.components

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.AssetFileDescriptor
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.TextureView
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media as VlcMedia
import org.videolan.libvlc.MediaPlayer as VlcMediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.interfaces.IVLCVout
import com.deckvault.app.R
import com.deckvault.app.data.DeckVaultApi
import com.deckvault.app.data.LocalStorageRepository
import com.deckvault.app.model.CropBoxSpec
import com.deckvault.app.model.EditStudioRequest
import com.deckvault.app.model.ExtractAudioRequest
import com.deckvault.app.model.FileItem
import com.deckvault.app.model.GifExportRequest
import com.deckvault.app.model.JobExecutionTarget
import com.deckvault.app.model.SavedHost
import com.deckvault.app.ui.theme.LocalDeckSurfaces
import com.deckvault.app.ui.util.Formatters
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

enum class PlayerAspectMode(
    val label: String,
    val fixedRatio: Float?,
    val resizeMode: Int,
    val vlcScale: VlcMediaPlayer.ScaleType,
) {
    FIT("Fit", null, AspectRatioFrameLayout.RESIZE_MODE_FIT, VlcMediaPlayer.ScaleType.SURFACE_BEST_FIT),
    FILL("Fill", null, AspectRatioFrameLayout.RESIZE_MODE_ZOOM, VlcMediaPlayer.ScaleType.SURFACE_FILL),
    STRETCH("Stretch", null, AspectRatioFrameLayout.RESIZE_MODE_FILL, VlcMediaPlayer.ScaleType.SURFACE_FIT_SCREEN),
    RATIO_16_9("16:9", 16f / 9f, AspectRatioFrameLayout.RESIZE_MODE_FILL, VlcMediaPlayer.ScaleType.SURFACE_16_9),
    RATIO_9_16("9:16", 9f / 16f, AspectRatioFrameLayout.RESIZE_MODE_FILL, VlcMediaPlayer.ScaleType.SURFACE_BEST_FIT),
    RATIO_4_3("4:3", 4f / 3f, AspectRatioFrameLayout.RESIZE_MODE_FILL, VlcMediaPlayer.ScaleType.SURFACE_4_3),
}

private enum class VideoEngineMode(val label: String) {
    EXO("Exo"),
    VLC("VLC"),
}

private val VLC_NATIVE_CONTAINERS = setOf(
    "avi", "wmv", "asf", "flv", "vob", "mpg", "mpeg", "divx",
    "rm", "rmvb", "ts", "mts", "m2ts", "mxf", "ogv", "3g2",
)

enum class ScreenRotationMode(val label: String, val orientationConst: Int) {
    AUTO("Auto", ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED),
    LANDSCAPE("Land", ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE),
    PORTRAIT("Port", ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT),
}

private enum class ActiveDragGesture { NONE, SEEK, BRIGHTNESS, VOLUME, ZOOM_PAN }

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerAndTrimmerOverlay(
    file: FileItem,
    playlist: List<FileItem>,
    streamUrl: String,
    playableFallbackUrl: String?,
    framePreviewUrls: List<String>,
    api: DeckVaultApi,
    isLocalSource: Boolean,
    sourceHostName: String = "",
    sourceHostIsAndroid: Boolean = false,
    pairedPcHosts: List<SavedHost>,
    defaultOffloadHost: SavedHost?,
    initialResumePositionMs: Long,
    initialSpeed: Float,
    seekStepSeconds: Int,
    autoPlay: Boolean,
    initialOpenStudio: Boolean,
    onSelectVideo: (FileItem) -> Unit,
    onSaveResumePosition: (path: String, positionMs: Long) -> Unit,
    onSaveSnapshot: (path: String, ms: Long) -> Unit,
    onOpenOptimizer: (FileItem) -> Unit,
    onSubmitStudioEdit: (EditStudioRequest, JobExecutionTarget, SavedHost?, runNow: Boolean) -> Unit,
    onSubmitExtractAudio: (ExtractAudioRequest, JobExecutionTarget, SavedHost?) -> Unit,
    onSubmitExportGif: (GifExportRequest, SavedHost?) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) { context.findActivity() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val surfaces = LocalDeckSurfaces.current

    val lowerExt = remember(file.name) { file.name.substringAfterLast('.', "").lowercase() }
    val defaultEngine = remember(file.path, lowerExt) {
        if (lowerExt in VLC_NATIVE_CONTAINERS) VideoEngineMode.VLC else VideoEngineMode.EXO
    }

    // Playback & HUD state
    var activeEngine by remember(file.path) { mutableStateOf(defaultEngine) }
    var pendingEngineResumeMs by remember(file.path) { mutableLongStateOf(initialResumePositionMs) }
    var showControls by remember(file.path) { mutableStateOf(true) }
    var interactionNonce by remember { mutableIntStateOf(0) }
    var isFullscreen by remember { mutableStateOf(false) }
    var isLocked by remember { mutableStateOf(false) }
    var showUnlockPill by remember { mutableStateOf(false) }

    var activeUri by remember(streamUrl) { mutableStateOf(streamUrl) }
    var externalSubUri by remember(file.path) { mutableStateOf<Uri?>(null) }
    var externalSubLabel by remember(file.path) { mutableStateOf("") }
    var usedCompatibilityFallback by remember(file.path) { mutableStateOf(false) }
    var playbackErrorText by remember(file.path) { mutableStateOf<String?>(null) }

    var isPlaying by remember(file.path) { mutableStateOf(autoPlay && !initialOpenStudio) }
    var isBuffering by remember(file.path) { mutableStateOf(true) }
    var durationMs by remember(file.path) { mutableLongStateOf(file.meta?.durationMs ?: 0L) }
    var currentPosMs by remember(file.path) { mutableLongStateOf(initialResumePositionMs) }
    var vlcPositionFrac by remember(file.path) { mutableFloatStateOf(0f) }
    var resumeApplied by remember(file.path) { mutableStateOf(false) }
    var vlcAudioTracks by remember(file.path) { mutableStateOf<List<PlayerTrackOption>>(emptyList()) }
    var vlcSubTracks by remember(file.path) { mutableStateOf<List<PlayerTrackOption>>(emptyList()) }

    // Scrub & gesture HUD feedback
    var isSliderDragging by remember { mutableStateOf(false) }
    var sliderDragMs by remember { mutableLongStateOf(0L) }
    var sliderDragFrac by remember { mutableFloatStateOf(0f) }
    var gestureHudTitle by remember { mutableStateOf<String?>(null) }
    var gestureHudSub by remember { mutableStateOf<String?>(null) }
    var gestureHudFraction by remember { mutableFloatStateOf(-1f) }
    var hudNonce by remember { mutableIntStateOf(0) }

    // Pro controls state
    var playbackSpeed by remember { mutableFloatStateOf(initialSpeed.coerceIn(0.25f, 4.0f)) }
    var isBoost2x by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var aspectMode by remember { mutableStateOf(PlayerAspectMode.FIT) }
    var rotationMode by remember { mutableStateOf(ScreenRotationMode.AUTO) }
    var mirrorPreview by remember { mutableStateOf(false) }

    // Zoom & pan
    var zoomScale by remember(file.path) { mutableFloatStateOf(1f) }
    var panX by remember(file.path) { mutableFloatStateOf(0f) }
    var panY by remember(file.path) { mutableFloatStateOf(0f) }

    // Brightness (0f..1f) & Volume (0..200 where >100 = LoudnessEnhancer)
    var brightnessFrac by remember {
        mutableFloatStateOf(
            activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.55f
        )
    }
    var volumePct by remember {
        mutableIntStateOf(
            run {
                val maxV = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                val curV = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                ((curV.toFloat() / maxV) * 100f).roundToInt().coerceIn(0, 100)
            }
        )
    }

    // A-B loop & sleep timer & tracks
    var loopStartMs by remember(file.path) { mutableStateOf<Long?>(null) }
    var loopEndMs by remember(file.path) { mutableStateOf<Long?>(null) }
    var sleepMinutes by remember { mutableStateOf<Int?>(null) }
    var showTracksDialog by remember { mutableStateOf(false) }
    var currentTracks by remember { mutableStateOf(Tracks.EMPTY) }

    // Edit Studio state
    var studioExpanded by remember(file.path) { mutableStateOf(initialOpenStudio) }
    var studioTab by remember(file.path) { mutableIntStateOf(0) }
    var studioPeekCollapsed by remember(file.path) { mutableStateOf(false) }
    var studioCropActive by remember(file.path) { mutableStateOf(false) }
    var naturalVideoWidth by remember(file.path) { mutableIntStateOf(file.meta?.width ?: 0) }
    var naturalVideoHeight by remember(file.path) { mutableIntStateOf(file.meta?.height ?: 0) }
    var studioStartMs by remember(file.path) { mutableLongStateOf(0L) }
    var studioEndMs by remember(file.path) { mutableLongStateOf(file.meta?.durationMs ?: 0L) }
    var studioCrop by remember(file.path) { mutableStateOf(CropBoxSpec()) }
    var studioRotate by remember(file.path) { mutableIntStateOf(0) }
    var studioFlipH by remember(file.path) { mutableStateOf(false) }
    var studioFlipV by remember(file.path) { mutableStateOf(false) }
    var studioSpeed by remember(file.path) { mutableFloatStateOf(1.0f) }
    var studioMute by remember(file.path) { mutableStateOf(false) }
    var studioBrightness by remember(file.path) { mutableFloatStateOf(0f) }
    var studioContrast by remember(file.path) { mutableFloatStateOf(1f) }
    var studioSaturation by remember(file.path) { mutableFloatStateOf(1f) }
    var studioSharpen by remember(file.path) { mutableFloatStateOf(0f) }
    var studioDeband by remember(file.path) { mutableStateOf(false) }
    var studioStabilize by remember(file.path) { mutableStateOf(false) }
    var studioNormalize by remember(file.path) { mutableStateOf(false) }
    var studioSaveMode by remember(file.path) { mutableStateOf("copy") }
    var studioOffloadHost by remember { mutableStateOf(defaultOffloadHost ?: pairedPcHosts.firstOrNull()) }
    var studioTarget by remember(file.path, isLocalSource, sourceHostIsAndroid) {
        mutableStateOf(
            if (isLocalSource || sourceHostIsAndroid) JobExecutionTarget.ON_DEVICE
            else JobExecutionTarget.PC_REMOTE
        )
    }

    // Subtitle picker launcher
    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            externalSubUri = uri
            externalSubLabel = uri.lastPathSegment?.substringAfterLast('/') ?: "External Subtitles"
        }
    }

    // Sibling .srt / .vtt auto-discovery for local videos
    val autoSubtitles = remember(file.path, isLocalSource) {
        if (!isLocalSource || LocalStorageRepository.isContentUriPath(file.path)) emptyList()
        else {
            val f = File(file.path)
            val parent = f.parentFile ?: return@remember emptyList()
            val stem = f.nameWithoutExtension
            listOfNotNull(
                File(parent, "$stem.srt").takeIf { it.isFile }?.let { sub ->
                    MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(sub))
                        .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .setLabel("${sub.name} (Auto)")
                        .build()
                },
                File(parent, "$stem.vtt").takeIf { it.isFile }?.let { sub ->
                    MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(sub))
                        .setMimeType(MimeTypes.TEXT_VTT)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .setLabel("${sub.name} (Auto)")
                        .build()
                },
            )
        }
    }

    // Build ExoPlayer (when in Exo mode) and LibVLC Native Engine (when in VLC mode for AVI/TS/WMV/FLV/universal codecs)
    val exoPlayer = remember(file.path, activeEngine) {
        if (activeEngine != VideoEngineMode.EXO) return@remember null
        val httpFactory = OkHttpDataSource.Factory(api.okHttpClient)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setConstantBitrateSeekingAlwaysEnabled(true)
            .setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                    DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS
            )
            .setTsExtractorTimestampSearchBytes(600 * 1024)
        val renderersFactory = NextRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)
        ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory))
            .build().apply {
                setSeekParameters(SeekParameters.CLOSEST_SYNC)
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = autoPlay && !initialOpenStudio
            }
    }

    val vlcSession = remember(file.path, activeEngine) {
        if (activeEngine != VideoEngineMode.VLC) return@remember null
        val libVlc = LibVLC(
            context.applicationContext,
            arrayListOf(
                "--no-drop-late-frames",
                "--no-skip-frames",
                "--http-reconnect",
                "--network-caching=1200",
                "--file-caching=500",
            ),
        )
        libVlc to VlcMediaPlayer(libVlc)
    }
    var vlcAssetFd by remember { mutableStateOf<AssetFileDescriptor?>(null) }
    var attachedVlcTexture by remember { mutableStateOf<TextureView?>(null) }
    var attachedVlcPlayer by remember { mutableStateOf<VlcMediaPlayer?>(null) }
    var vlcAutoPausedInitial by remember(file.path, activeEngine) { mutableStateOf(false) }

    var liveTranscodeBaseMs by remember(file.path) { mutableLongStateOf(0L) }

    fun engineCurrentRawPosMs(): Long = when (activeEngine) {
        VideoEngineMode.VLC -> vlcSession?.second?.time?.coerceAtLeast(0L) ?: currentPosMs
        VideoEngineMode.EXO -> exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: currentPosMs
    }

    fun engineCurrentLogicalPosMs(): Long =
        if (usedCompatibilityFallback) (liveTranscodeBaseMs + engineCurrentRawPosMs()) else engineCurrentRawPosMs()

    fun engineDurationMs(): Long = when (activeEngine) {
        VideoEngineMode.VLC -> vlcSession?.second?.length?.takeIf { it > 0L } ?: durationMs
        VideoEngineMode.EXO -> exoPlayer?.duration?.takeIf { it > 0L && it != C.TIME_UNSET } ?: durationMs
    }

    fun engineIsPlaying(): Boolean = when (activeEngine) {
        VideoEngineMode.VLC -> vlcSession?.second?.isPlaying == true
        VideoEngineMode.EXO -> exoPlayer?.isPlaying == true
    }

    fun enginePlay() {
        when (activeEngine) {
            VideoEngineMode.VLC -> vlcSession?.second?.play()
            VideoEngineMode.EXO -> exoPlayer?.play()
        }
        isPlaying = true
    }

    fun enginePause() {
        when (activeEngine) {
            VideoEngineMode.VLC -> vlcSession?.second?.pause()
            VideoEngineMode.EXO -> exoPlayer?.pause()
        }
        isPlaying = false
    }

    fun engineSeekRaw(targetMs: Long) {
        val safe = targetMs.coerceAtLeast(0L)
        when (activeEngine) {
            VideoEngineMode.VLC -> runCatching {
                val vp = vlcSession?.second ?: return@runCatching
                val len = vp.length
                if (len > 0L) {
                    vp.setTime(safe, false)
                } else if (durationMs > 0L && vp.isSeekable) {
                    val frac = (safe.toFloat() / durationMs.toFloat()).coerceIn(0f, 0.999f)
                    vp.setPosition(frac, false)
                    vlcPositionFrac = frac
                } else {
                    vp.setTime(safe, false)
                }
            }
            VideoEngineMode.EXO -> exoPlayer?.seekTo(safe)
        }
    }

    fun smartSeekToFraction(frac: Float) {
        val cleanFrac = frac.coerceIn(0f, 0.999f)
        if (durationMs > 0L) {
            val tgt = (cleanFrac * durationMs).toLong()
            engineSeekRaw(tgt)
            currentPosMs = tgt
            vlcPositionFrac = cleanFrac
        } else if (activeEngine == VideoEngineMode.VLC) {
            runCatching { vlcSession?.second?.setPosition(cleanFrac, false) }
            vlcPositionFrac = cleanFrac
        }
    }

    fun switchToVlcEngine(reason: String, resumeMs: Long) {
        if (activeEngine == VideoEngineMode.VLC) return
        pendingEngineResumeMs = resumeMs.coerceAtLeast(0L)
        resumeApplied = false
        usedCompatibilityFallback = false
        liveTranscodeBaseMs = 0L
        activeUri = streamUrl
        activeEngine = VideoEngineMode.VLC
        gestureHudTitle = "VLC Native Engine"
        gestureHudSub = reason
        gestureHudFraction = -1f
        hudNonce++
    }

    fun triggerPcCompatibilityFallback(reason: String, resumeMs: Long) {
        val base = playableFallbackUrl ?: return
        if (usedCompatibilityFallback) return
        usedCompatibilityFallback = true
        val cleanBase = base.replace(Regex("([?&])start=[^&]*"), "")
        val sec = (resumeMs.coerceAtLeast(0L) / 1000.0)
        liveTranscodeBaseMs = resumeMs.coerceAtLeast(0L)
        pendingEngineResumeMs = 0L
        activeUri = if (sec > 0.5) "$cleanBase&start=$sec" else cleanBase
        activeEngine = VideoEngineMode.EXO
        gestureHudTitle = "Compatibility Stream"
        gestureHudSub = reason
        gestureHudFraction = -1f
        hudNonce++
    }

    fun smartSeekTo(targetMs: Long) {
        val safeTarget = targetMs.coerceAtLeast(0L)
        val exo = exoPlayer
        if (usedCompatibilityFallback && !playableFallbackUrl.isNullOrBlank() && activeEngine == VideoEngineMode.EXO && exo?.isCurrentMediaItemSeekable != true) {
            val base = playableFallbackUrl.replace(Regex("([?&])start=[^&]*"), "")
            val sec = safeTarget / 1000.0
            liveTranscodeBaseMs = safeTarget
            activeUri = if (sec > 0.2) "$base&start=$sec" else base
            currentPosMs = safeTarget
        } else {
            engineSeekRaw(safeTarget)
            currentPosMs = safeTarget
        }
    }

    // LoudnessEnhancer for 100%-200% software volume boost on ExoPlayer
    var loudnessEnhancer by remember { mutableStateOf<LoudnessEnhancer?>(null) }
    DisposableEffect(exoPlayer) {
        val player = exoPlayer ?: return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    val dur = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: durationMs
                    if (dur > 0) {
                        durationMs = dur
                        if (studioEndMs <= 0L) studioEndMs = dur
                    }
                    if (!resumeApplied && pendingEngineResumeMs > 2000L && dur > pendingEngineResumeMs + 3000L) {
                        resumeApplied = true
                        player.seekTo(pendingEngineResumeMs)
                        gestureHudTitle = "Resumed"
                        gestureHudSub = Formatters.formatDuration(pendingEngineResumeMs)
                        gestureHudFraction = -1f
                        hudNonce++
                    } else {
                        resumeApplied = true
                    }
                    if (loudnessEnhancer == null && player.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                        runCatching { loudnessEnhancer = LoudnessEnhancer(player.audioSessionId) }
                    }
                } else if (playbackState == Player.STATE_ENDED && sleepMinutes == -1) {
                    onClose()
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val effW = (videoSize.width * videoSize.pixelWidthHeightRatio).roundToInt().coerceAtLeast(1)
                    naturalVideoWidth = effW
                    naturalVideoHeight = videoSize.height
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) interactionNonce++
            }

            override fun onTracksChanged(tracks: Tracks) {
                currentTracks = tracks
                if (!usedCompatibilityFallback) {
                    val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                    val hasAnySupportedAudio = audioGroups.any { g ->
                        (0 until g.length).any { idx -> g.isTrackSupported(idx) }
                    }
                    val expectsAudioFromMeta = file.meta?.audioCodec?.isNotBlank() == true
                    if ((audioGroups.isNotEmpty() && !hasAnySupportedAudio) || (audioGroups.isEmpty() && expectsAudioFromMeta)) {
                        switchToVlcEngine(
                            reason = "Native Audio Decoder",
                            resumeMs = player.currentPosition,
                        )
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (!usedCompatibilityFallback) {
                    switchToVlcEngine(
                        reason = "Native Container & Audio Decoder",
                        resumeMs = player.currentPosition,
                    )
                } else {
                    playbackErrorText = error.localizedMessage ?: "Unsupported video or audio format"
                }
            }
        }
        player.addListener(listener)
        onDispose {
            val savedPos = if (usedCompatibilityFallback) (liveTranscodeBaseMs + player.currentPosition) else player.currentPosition
            if (savedPos > 0L) onSaveResumePosition(file.path, savedPos)
            runCatching { loudnessEnhancer?.release() }
            loudnessEnhancer = null
            player.removeListener(listener)
            player.release()
        }
    }

    // LibVLC Native Engine lifecycle + event listener (plays AVI/MP2/ADPCM/WMA, TS, WMV, FLV, VOB, MKV natively)
    DisposableEffect(vlcSession) {
        val sess = vlcSession ?: return@DisposableEffect onDispose {}
        val libVlc = sess.first
        val vlcPlayer = sess.second

        fun refreshVlcTracks() {
            runCatching {
                val vt = vlcPlayer.currentVideoTrack
                if (vt != null && vt.width > 0 && vt.height > 0) {
                    val rotated = vt.orientation == IMedia.VideoTrack.Orientation.LeftTop ||
                        vt.orientation == IMedia.VideoTrack.Orientation.LeftBottom ||
                        vt.orientation == IMedia.VideoTrack.Orientation.RightTop ||
                        vt.orientation == IMedia.VideoTrack.Orientation.RightBottom
                    val rawW = if (rotated) vt.height else vt.width
                    val rawH = if (rotated) vt.width else vt.height
                    val sar = if (vt.sarNum > 0 && vt.sarDen > 0) vt.sarNum.toFloat() / vt.sarDen.toFloat() else 1f
                    naturalVideoWidth = (rawW * sar).roundToInt().coerceAtLeast(1)
                    naturalVideoHeight = rawH.coerceAtLeast(1)
                }
            }
            val curAudio = runCatching { vlcPlayer.audioTrack }.getOrDefault(-1)
            vlcAudioTracks = runCatching {
                vlcPlayer.audioTracks?.filter { it.id >= 0 }?.mapIndexed { idx, td ->
                    PlayerTrackOption(0, td.id, td.name ?: "Audio ${idx + 1}", "", td.id == curAudio)
                }
            }.getOrNull().orEmpty()
            val curSpu = runCatching { vlcPlayer.spuTrack }.getOrDefault(-1)
            vlcSubTracks = runCatching {
                vlcPlayer.spuTracks?.filter { it.id >= 0 }?.mapIndexed { idx, td ->
                    PlayerTrackOption(0, td.id, td.name ?: "Subtitle ${idx + 1}", "", td.id == curSpu)
                }
            }.getOrNull().orEmpty()
        }

        vlcPlayer.setEventListener { ev ->
            val evType = ev.type
            val evTime = ev.timeChanged
            val evLen = ev.lengthChanged
            val evPos = ev.positionChanged
            val evBuf = ev.buffering
            mainHandler.post {
                if (vlcPlayer.isReleased) return@post
                when (evType) {
                    VlcMediaPlayer.Event.Opening -> {
                        isBuffering = true
                    }
                    VlcMediaPlayer.Event.Buffering -> {
                        isBuffering = evBuf < 100f
                    }
                    VlcMediaPlayer.Event.Playing -> {
                        isPlaying = true
                        isBuffering = false
                        interactionNonce++
                        runCatching { vlcPlayer.rate = if (isBoost2x) 2.0f else playbackSpeed }
                        runCatching { vlcPlayer.volume = if (volumePct > 100) volumePct.coerceIn(100, 200) else 100 }
                        refreshVlcTracks()
                        if ((!autoPlay || initialOpenStudio) && !vlcAutoPausedInitial) {
                            vlcAutoPausedInitial = true
                            vlcPlayer.pause()
                            isPlaying = false
                        } else if (!resumeApplied && pendingEngineResumeMs > 2000L) {
                            val dur = vlcPlayer.length
                            if (dur <= 0L || dur > pendingEngineResumeMs + 3000L) {
                                resumeApplied = true
                                if (vlcPlayer.isSeekable) {
                                    vlcPlayer.setTime(pendingEngineResumeMs, false)
                                    gestureHudTitle = "Resumed"
                                    gestureHudSub = Formatters.formatDuration(pendingEngineResumeMs)
                                    gestureHudFraction = -1f
                                    hudNonce++
                                }
                            }
                        } else {
                            resumeApplied = true
                        }
                    }
                    VlcMediaPlayer.Event.Paused, VlcMediaPlayer.Event.Stopped -> {
                        isPlaying = false
                        isBuffering = false
                    }
                    VlcMediaPlayer.Event.LengthChanged -> {
                        if (evLen > 0L) {
                            durationMs = evLen
                            if (studioEndMs <= 0L) studioEndMs = evLen
                        }
                    }
                    VlcMediaPlayer.Event.PositionChanged -> {
                        if (!isSliderDragging && evPos in 0f..1f) {
                            vlcPositionFrac = evPos
                            if (durationMs > 0L && (vlcPlayer.length <= 0L || vlcPlayer.time <= 0L)) {
                                currentPosMs = (evPos * durationMs).toLong()
                            }
                        }
                    }
                    VlcMediaPlayer.Event.TimeChanged -> {
                        if (!isSliderDragging && evTime >= 0L) {
                            if (durationMs <= 0L || evTime <= durationMs + 3000L) {
                                currentPosMs = evTime
                            }
                            val p = runCatching { vlcPlayer.position }.getOrDefault(0f)
                            if (p in 0f..1f) {
                                vlcPositionFrac = p
                                if (durationMs <= 0L && p > 0.02f && evTime in 2_000L..43_200_000L) {
                                    val est = (evTime / p).toLong().coerceAtLeast(evTime)
                                    durationMs = est
                                    if (studioEndMs <= 0L) studioEndMs = est
                                }
                            }
                        }
                    }
                    VlcMediaPlayer.Event.ESAdded, VlcMediaPlayer.Event.ESDeleted, VlcMediaPlayer.Event.ESSelected, VlcMediaPlayer.Event.Vout -> {
                        refreshVlcTracks()
                    }
                    VlcMediaPlayer.Event.EndReached -> {
                        isPlaying = false
                        isBuffering = false
                        if (sleepMinutes == -1) onClose()
                    }
                    VlcMediaPlayer.Event.EncounteredError -> {
                        isBuffering = false
                        if (!usedCompatibilityFallback && !playableFallbackUrl.isNullOrBlank()) {
                            triggerPcCompatibilityFallback(
                                reason = "Live PC H.264 + AAC Transcode",
                                resumeMs = currentPosMs,
                            )
                        } else {
                            playbackErrorText = "Unable to decode video or audio stream"
                        }
                    }
                }
            }
        }

        onDispose {
            val savedPos = currentPosMs.coerceAtLeast(0L)
            if (savedPos > 0L) onSaveResumePosition(file.path, savedPos)
            runCatching { vlcPlayer.setEventListener(null) }
            runCatching { vlcPlayer.vlcVout.detachViews() }
            runCatching { vlcPlayer.detachViews() }
            attachedVlcTexture = null
            attachedVlcPlayer = null
            runCatching { vlcPlayer.stop() }
            runCatching { vlcPlayer.release() }
            runCatching { libVlc.release() }
            runCatching { vlcAssetFd?.close() }
            vlcAssetFd = null
        }
    }

    // Prepare / update ExoPlayer MediaItem whenever activeUri, engine, or external subtitle changes
    LaunchedEffect(exoPlayer, activeUri, externalSubUri) {
        val player = exoPlayer ?: return@LaunchedEffect
        playbackErrorText = null
        val resumeMs = if (usedCompatibilityFallback) 0L else (pendingEngineResumeMs.takeIf { it > 1500L } ?: 0L)
        val subs = buildList {
            addAll(autoSubtitles)
            externalSubUri?.let { sub ->
                val mime = if (sub.toString().endsWith(".vtt", true)) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
                add(
                    MediaItem.SubtitleConfiguration.Builder(sub)
                        .setMimeType(mime)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .setLabel(externalSubLabel.ifBlank { "External Subtitle" })
                        .build()
                )
            }
        }
        val mediaItem = MediaItem.Builder()
            .setUri(activeUri)
            .setSubtitleConfigurations(subs)
            .build()
        player.setMediaItem(mediaItem)
        if (resumeMs > 0L) player.seekTo(resumeMs)
        player.prepare()
    }

    // Prepare / update LibVLC Media whenever activeUri, engine, or external subtitle changes
    LaunchedEffect(vlcSession, activeUri, externalSubUri) {
        val sess = vlcSession ?: return@LaunchedEffect
        val libVlc = sess.first
        val vlcPlayer = sess.second
        playbackErrorText = null
        isBuffering = true
        resumeApplied = false
        runCatching { vlcAssetFd?.close() }
        vlcAssetFd = null
        val vlcMedia = when {
            activeUri.startsWith("content://", ignoreCase = true) -> {
                val afd = runCatching { context.contentResolver.openAssetFileDescriptor(Uri.parse(activeUri), "r") }.getOrNull()
                vlcAssetFd = afd
                if (afd != null) VlcMedia(libVlc, afd) else VlcMedia(libVlc, Uri.parse(activeUri))
            }
            activeUri.startsWith("/") -> VlcMedia(libVlc, activeUri)
            else -> VlcMedia(libVlc, Uri.parse(activeUri))
        }
        vlcMedia.setHWDecoderEnabled(true, false)
        vlcMedia.addOption(":http-reconnect")
        vlcPlayer.media = vlcMedia
        vlcMedia.release()
        externalSubUri?.let { sub ->
            runCatching { vlcPlayer.addSlave(IMedia.Slave.Type.Subtitle, sub, true) }
        }
        vlcPlayer.play()
    }

    // Apply volume boost (LoudnessEnhancer for ExoPlayer, native 0..200 software amplification for LibVLC)
    LaunchedEffect(volumePct, loudnessEnhancer, vlcSession) {
        val le = loudnessEnhancer
        if (le != null) {
            runCatching {
                if (volumePct > 100) {
                    le.enabled = true
                    le.setTargetGain(((volumePct - 100) * 14).coerceIn(0, 1400))
                } else {
                    le.enabled = false
                }
            }
        }
        vlcSession?.second?.let { vp ->
            runCatching { vp.volume = if (volumePct > 100) volumePct.coerceIn(100, 200) else 100 }
        }
    }

    // Apply playback speed
    LaunchedEffect(playbackSpeed, isBoost2x, exoPlayer, vlcSession) {
        val targetRate = if (isBoost2x) 2.0f else playbackSpeed
        exoPlayer?.setPlaybackSpeed(targetRate)
        vlcSession?.second?.let { vp -> runCatching { vp.rate = targetRate } }
    }

    // Keep screen on + immersive system bars + restore orientation/brightness on exit
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.let { win ->
                win.attributes = win.attributes.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
                WindowCompat.getInsetsController(win, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(rotationMode) {
        activity?.requestedOrientation = rotationMode.orientationConst
    }

    LaunchedEffect(isFullscreen, showControls) {
        val win = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(win, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (isFullscreen || !showControls) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Sync metadata when background FFprobe/MediaExtractor resolves missing duration/resolution (e.g. .ts)
    LaunchedEffect(file.path, file.meta?.durationMs, file.meta?.width, file.meta?.height) {
        val mDur = file.meta?.durationMs ?: 0L
        if (mDur > 0L && durationMs <= 0L) {
            durationMs = mDur
            if (studioEndMs <= 0L) studioEndMs = mDur
        }
        val mw = file.meta?.width ?: 0
        val mh = file.meta?.height ?: 0
        if (mw > 0 && naturalVideoWidth <= 0) naturalVideoWidth = mw
        if (mh > 0 && naturalVideoHeight <= 0) naturalVideoHeight = mh
    }

    // Poll position + A-B loop check
    LaunchedEffect(exoPlayer, vlcSession, activeEngine, loopStartMs, loopEndMs) {
        while (true) {
            if (!isSliderDragging) {
                val dur = engineDurationMs()
                if (dur > 0L) {
                    durationMs = dur
                    if (studioEndMs <= 0L) studioEndMs = dur
                }
                if (activeEngine == VideoEngineMode.VLC) {
                    val vp = vlcSession?.second
                    val rawLen = runCatching { vp?.length ?: 0L }.getOrDefault(0L)
                    val posFrac = runCatching { vp?.position ?: 0f }.getOrDefault(0f).coerceIn(0f, 1f)
                    if (posFrac > 0f || !isPlaying) vlcPositionFrac = posFrac
                    val rawTime = runCatching { vp?.time ?: -1L }.getOrDefault(-1L)
                    if (durationMs <= 0L && posFrac > 0.02f && rawTime in 2_000L..43_200_000L) {
                        val est = (rawTime / posFrac).toLong().coerceAtLeast(rawTime)
                        durationMs = est
                        if (studioEndMs <= 0L) studioEndMs = est
                    }
                    currentPosMs = when {
                        durationMs > 0L && rawLen > 0L && rawTime in 0L..(durationMs + 3000L) -> rawTime
                        durationMs > 0L && posFrac > 0f -> (posFrac * durationMs).toLong()
                        rawTime in 0L..43_200_000L && (durationMs <= 0L || rawTime <= durationMs + 3000L) -> rawTime
                        else -> currentPosMs
                    }
                } else {
                    currentPosMs = engineCurrentLogicalPosMs()
                }
                val a = loopStartMs
                val b = loopEndMs
                if (a != null && b != null && b > a + 300L && currentPosMs >= b && engineIsPlaying()) {
                    smartSeekTo(a)
                }
            }
            delay(200L)
        }
    }

    // Auto-hide controls after 2.8 seconds when playing!
    LaunchedEffect(showControls, interactionNonce, isPlaying, studioExpanded, showTracksDialog, showSpeedMenu, isSliderDragging) {
        if (showControls && isPlaying && !studioExpanded && !showTracksDialog && !showSpeedMenu && !isSliderDragging) {
            delay(2800L)
            showControls = false
        }
    }

    // Auto-hide gesture HUD & lock pill
    LaunchedEffect(hudNonce) {
        if (gestureHudTitle != null) {
            delay(1100L)
            gestureHudTitle = null
            gestureHudSub = null
            gestureHudFraction = -1f
        }
    }
    LaunchedEffect(showUnlockPill) {
        if (showUnlockPill) {
            delay(2200L)
            showUnlockPill = false
        }
    }

    // Sleep timer countdown
    LaunchedEffect(sleepMinutes) {
        val mins = sleepMinutes
        if (mins != null && mins > 0) {
            delay(60_000L)
            sleepMinutes = (mins - 1).takeIf { it > 0 }
            if (mins - 1 <= 0) {
                enginePause()
                onClose()
            }
        }
    }

    BackHandler {
        if (studioExpanded && studioPeekCollapsed) {
            studioPeekCollapsed = false
        } else if (studioExpanded) {
            studioExpanded = false
            studioCropActive = false
            studioPeekCollapsed = false
        } else {
            onClose()
        }
    }

    var boxWidthPx by remember { mutableFloatStateOf(1f) }
    var boxHeightPx by remember { mutableFloatStateOf(1f) }

    val colorRenderEffect = remember(studioBrightness, studioContrast, studioSaturation) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            (abs(studioBrightness) < 0.02f && abs(studioContrast - 1f) < 0.02f && abs(studioSaturation - 1f) < 0.02f)
        ) {
            null
        } else {
            val sat = android.graphics.ColorMatrix().apply { setSaturation(studioSaturation) }
            val c = studioContrast
            val b = studioBrightness * 255f
            val t = (1f - c) * 128f + b
            val contrastBrightness = android.graphics.ColorMatrix(
                floatArrayOf(
                    c, 0f, 0f, 0f, t,
                    0f, c, 0f, 0f, t,
                    0f, 0f, c, 0f, t,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
            sat.postConcat(contrastBrightness)
            android.graphics.RenderEffect.createColorFilterEffect(
                android.graphics.ColorMatrixColorFilter(sat)
            ).asComposeRenderEffect()
        }
    }

    val currentIdx = playlist.indexOfFirst { it.path == file.path }
    val prevItem = if (currentIdx > 0) playlist[currentIdx - 1] else null
    val nextItem = if (currentIdx >= 0 && currentIdx + 1 < playlist.size) playlist[currentIdx + 1] else null

    val renderVideoStage: @Composable (Modifier) -> Unit = { stageMod ->
        // VIDEO CANVAS WITH GESTURES & FITTED CROP OVERLAY (TextureView + non-reparented stage!)
        BoxWithConstraints(
            contentAlignment = Alignment.Center,
            modifier = stageMod
                .background(Color.Black)
                .clipToBounds()
                .onSizeChanged {
                    boxWidthPx = it.width.toFloat().coerceAtLeast(1f)
                    boxHeightPx = it.height.toFloat().coerceAtLeast(1f)
                }
                .pointerInput(isLocked, studioCropActive) {
                    detectTapGestures(
                        onPress = {
                            tryAwaitRelease()
                            if (isBoost2x) {
                                isBoost2x = false
                            }
                        },
                        onLongPress = {
                            if (!isLocked && !studioCropActive) {
                                isBoost2x = true
                            }
                        },
                        onTap = {
                            if (isLocked) {
                                showUnlockPill = true
                            } else {
                                showControls = !showControls
                                if (showControls) interactionNonce++
                            }
                        },
                        onDoubleTap = { offset ->
                            if (isLocked || studioCropActive) return@detectTapGestures
                            val dur = engineDurationMs().takeIf { it > 0L } ?: durationMs
                            val stepMs = seekStepSeconds.coerceAtLeast(5) * 1000L
                            val basePos = engineCurrentLogicalPosMs()
                            when {
                                offset.x < boxWidthPx * 0.34f -> {
                                    if (dur <= 0L && activeEngine == VideoEngineMode.VLC) {
                                        val nextF = (vlcPositionFrac - 0.05f).coerceIn(0f, 1f)
                                        smartSeekToFraction(nextF)
                                        gestureHudTitle = "<< -5%"
                                        gestureHudSub = "${(nextF * 100).roundToInt()}%"
                                    } else {
                                        val tgt = (basePos - stepMs).coerceAtLeast(0L)
                                        smartSeekTo(tgt)
                                        gestureHudTitle = "<< -${seekStepSeconds}s"
                                        gestureHudSub = Formatters.formatDuration(tgt)
                                    }
                                    gestureHudFraction = -1f
                                    hudNonce++
                                }
                                offset.x > boxWidthPx * 0.66f -> {
                                    if (dur <= 0L && activeEngine == VideoEngineMode.VLC) {
                                        val nextF = (vlcPositionFrac + 0.05f).coerceIn(0f, 0.99f)
                                        smartSeekToFraction(nextF)
                                        gestureHudTitle = "+5% >>"
                                        gestureHudSub = "${(nextF * 100).roundToInt()}%"
                                    } else {
                                        val tgt = (basePos + stepMs).let { if (dur > 0) it.coerceAtMost(dur) else it }
                                        smartSeekTo(tgt)
                                        gestureHudTitle = "+${seekStepSeconds}s >>"
                                        gestureHudSub = Formatters.formatDuration(tgt)
                                    }
                                    gestureHudFraction = -1f
                                    hudNonce++
                                }
                                else -> {
                                    if (engineIsPlaying()) {
                                        enginePause()
                                        gestureHudTitle = "Paused"
                                    } else {
                                        enginePlay()
                                        gestureHudTitle = "Playing"
                                    }
                                    gestureHudSub = null
                                    gestureHudFraction = -1f
                                    hudNonce++
                                }
                            }
                        },
                    )
                }
                .pointerInput(isLocked, studioCropActive, durationMs, boxWidthPx, boxHeightPx) {
                    val slop = viewConfiguration.touchSlop
                    val maxSysVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                    while (true) {
                        awaitPointerEventScope {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (isLocked || studioCropActive) return@awaitPointerEventScope
                        val startPos = down.position
                        var mode = ActiveDragGesture.NONE
                        var seekStartMs = engineCurrentLogicalPosMs()
                        var seekTargetMs = seekStartMs
                        var lastLiveSeekAt = 0L
                        var startBrightness = brightnessFrac
                        var startVolumePct = volumePct.toFloat()

                        while (true) {
                            val ev = awaitPointerEvent()
                            val pressed = ev.changes.filter { it.pressed }
                            if (pressed.isEmpty()) {
                                if (mode == ActiveDragGesture.SEEK) {
                                    smartSeekTo(seekTargetMs)
                                }
                                break
                            }

                            if (pressed.size >= 2) {
                                mode = ActiveDragGesture.ZOOM_PAN
                                val zChange = ev.calculateZoom()
                                val pChange = ev.calculatePan()
                                zoomScale = (zoomScale * zChange).coerceIn(1f, 5f)
                                if (zoomScale > 1.02f) {
                                    val maxX = boxWidthPx * (zoomScale - 1f) * 0.5f
                                    val maxY = boxHeightPx * (zoomScale - 1f) * 0.5f
                                    panX = (panX + pChange.x).coerceIn(-maxX, maxX)
                                    panY = (panY + pChange.y).coerceIn(-maxY, maxY)
                                } else {
                                    panX = 0f; panY = 0f
                                }
                                ev.changes.forEach { if (it.positionChanged()) it.consume() }
                                gestureHudTitle = "Zoom ${String.format("%.1fx", zoomScale)}"
                                gestureHudSub = null
                                gestureHudFraction = -1f
                                hudNonce++
                                continue
                            }

                            val ch = pressed.first()
                            val totalDx = ch.position.x - startPos.x
                            val totalDy = ch.position.y - startPos.y

                            if (mode == ActiveDragGesture.NONE) {
                                val distSq = totalDx * totalDx + totalDy * totalDy
                                if (distSq > slop * slop * 2.5f) {
                                    if (zoomScale > 1.05f) {
                                        mode = ActiveDragGesture.ZOOM_PAN
                                    } else if (abs(totalDx) > abs(totalDy) * 1.15f) {
                                        mode = ActiveDragGesture.SEEK
                                        seekStartMs = engineCurrentLogicalPosMs()
                                    } else if (startPos.x < boxWidthPx * 0.45f) {
                                        mode = ActiveDragGesture.BRIGHTNESS
                                        startBrightness = brightnessFrac
                                    } else {
                                        mode = ActiveDragGesture.VOLUME
                                        startVolumePct = volumePct.toFloat()
                                    }
                                }
                            }

                            when (mode) {
                                ActiveDragGesture.ZOOM_PAN -> {
                                    val d = ch.position - ch.previousPosition
                                    val maxX = boxWidthPx * (zoomScale - 1f) * 0.5f
                                    val maxY = boxHeightPx * (zoomScale - 1f) * 0.5f
                                    panX = (panX + d.x).coerceIn(-maxX, maxX)
                                    panY = (panY + d.y).coerceIn(-maxY, maxY)
                                    ch.consume()
                                }
                                ActiveDragGesture.SEEK -> {
                                    val dur = engineDurationMs().coerceAtLeast(1000L)
                                    val spanMs = dur.coerceIn(20_000L, 120_000L)
                                    val deltaMs = ((totalDx / boxWidthPx) * spanMs).toLong()
                                    seekTargetMs = (seekStartMs + deltaMs).coerceIn(0L, dur)
                                    val diffSec = (seekTargetMs - seekStartMs) / 1000L
                                    val sign = if (diffSec >= 0) "+" else ""
                                    gestureHudTitle = "${Formatters.formatDuration(seekTargetMs)} / ${Formatters.formatDuration(dur)}"
                                    gestureHudSub = "$sign${diffSec}s swipe seek"
                                    gestureHudFraction = seekTargetMs.toFloat() / dur.coerceAtLeast(1L).toFloat()
                                    hudNonce++
                                    val now = android.os.SystemClock.uptimeMillis()
                                    val exoSeekable = exoPlayer?.isCurrentMediaItemSeekable == true
                                    if (now - lastLiveSeekAt > 120L && (!usedCompatibilityFallback || exoSeekable)) {
                                        lastLiveSeekAt = now
                                        engineSeekRaw(seekTargetMs)
                                    }
                                    ch.consume()
                                }
                                ActiveDragGesture.BRIGHTNESS -> {
                                    val delta = (-totalDy / (boxHeightPx * 0.65f))
                                    val nextB = (startBrightness + delta).coerceIn(0.02f, 1.0f)
                                    brightnessFrac = nextB
                                    activity?.window?.let { win ->
                                        win.attributes = win.attributes.apply { screenBrightness = nextB }
                                    }
                                    val pct = (nextB * 100f).roundToInt()
                                    gestureHudTitle = "Brightness $pct%"
                                    gestureHudSub = null
                                    gestureHudFraction = nextB
                                    hudNonce++
                                    ch.consume()
                                }
                                ActiveDragGesture.VOLUME -> {
                                    val deltaPct = (-totalDy / (boxHeightPx * 0.65f)) * 180f
                                    val nextPct = (startVolumePct + deltaPct).roundToInt().coerceIn(0, 200)
                                    volumePct = nextPct
                                    val sysLevel = if (nextPct >= 100) maxSysVol else ((nextPct / 100f) * maxSysVol).roundToInt().coerceIn(0, maxSysVol)
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, sysLevel, 0)
                                    gestureHudTitle = if (nextPct > 100) "Boost Volume $nextPct%" else "Volume $nextPct%"
                                    gestureHudSub = if (nextPct > 100) "Software Loudness Boost" else null
                                    gestureHudFraction = (nextPct / 200f).coerceIn(0f, 1f)
                                    hudNonce++
                                    ch.consume()
                                }
                                ActiveDragGesture.NONE -> Unit
                            }
                        }
                        }
                    }
                },
        ) {
            val stageW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val stageH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
            val naturalRatio = if (naturalVideoWidth > 0 && naturalVideoHeight > 0) {
                (naturalVideoWidth.toFloat() / naturalVideoHeight.toFloat()).coerceIn(0.2f, 5f)
            } else {
                null
            }
            val cropOverlayActive = studioExpanded && studioCropActive
            val effectiveAspect = if (cropOverlayActive) PlayerAspectMode.FIT else aspectMode
            val (fitWPx, fitHPx) = when {
                effectiveAspect == PlayerAspectMode.FILL && naturalRatio != null -> {
                    if (stageW / stageH > naturalRatio) stageW to (stageW / naturalRatio)
                    else (stageH * naturalRatio) to stageH
                }
                effectiveAspect.fixedRatio != null -> {
                    val r = effectiveAspect.fixedRatio
                    if (stageW / stageH > r) (stageH * r) to stageH else stageW to (stageW / r)
                }
                effectiveAspect == PlayerAspectMode.FIT && naturalRatio != null -> {
                    if (stageW / stageH > naturalRatio) (stageH * naturalRatio) to stageH
                    else stageW to (stageW / naturalRatio)
                }
                else -> stageW to stageH
            }
            val density = LocalDensity.current
            val fitWDp = with(density) { fitWPx.toDp() }
            val fitHDp = with(density) { fitHPx.toDp() }

            val videoBoxMod = Modifier
                .align(Alignment.Center)
                .size(width = fitWDp, height = fitHDp)
                .graphicsLayer {
                    scaleX = zoomScale * (if (mirrorPreview || studioFlipH) -1f else 1f)
                    scaleY = zoomScale * (if (studioFlipV) -1f else 1f)
                    translationX = panX
                    translationY = panY
                    rotationZ = studioRotate.toFloat()
                    if (colorRenderEffect != null) {
                        renderEffect = colorRenderEffect
                    }
                }

            Box(modifier = videoBoxMod) {
                if (activeEngine == VideoEngineMode.VLC && vlcSession != null) {
                    val vp = vlcSession.second
                    val wInt = fitWPx.roundToInt().coerceAtLeast(1)
                    val hInt = fitHPx.roundToInt().coerceAtLeast(1)
                    key(vlcSession) {
                        AndroidView(
                            factory = { ctx ->
                                TextureView(ctx).apply {
                                    keepScreenOn = true
                                    addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
                                        val lw = r - l
                                        val lh = b - t
                                        if (lw > 0 && lh > 0) {
                                            runCatching { vp.vlcVout.setWindowSize(lw, lh) }
                                        }
                                    }
                                }
                            },
                            update = { tv ->
                                if (attachedVlcTexture !== tv || attachedVlcPlayer !== vp) {
                                    attachedVlcTexture = tv
                                    attachedVlcPlayer = vp
                                    runCatching { vp.vlcVout.detachViews() }
                                    runCatching { vp.detachViews() }
                                    val layoutListener = IVLCVout.OnNewVideoLayoutListener { _, vw, vh, visW, visH, sarNum, sarDen ->
                                        val rawW = if (visW > 0) visW else vw
                                        val rawH = if (visH > 0) visH else vh
                                        if (rawW > 0 && rawH > 0) {
                                            val sarAdjustedW = if (sarNum > 0 && sarDen > 0 && sarNum != sarDen) {
                                                ((rawW.toDouble() * sarNum.toDouble()) / sarDen.toDouble()).roundToInt().coerceAtLeast(1)
                                            } else {
                                                rawW
                                            }
                                            mainHandler.post {
                                                if (naturalVideoWidth != sarAdjustedW || naturalVideoHeight != rawH) {
                                                    naturalVideoWidth = sarAdjustedW
                                                    naturalVideoHeight = rawH
                                                }
                                                val curW = tv.width.takeIf { it > 0 } ?: wInt
                                                val curH = tv.height.takeIf { it > 0 } ?: hInt
                                                if (curW > 0 && curH > 0) {
                                                    runCatching { vp.vlcVout.setWindowSize(curW, curH) }
                                                }
                                            }
                                        }
                                    }
                                    vp.vlcVout.setVideoView(tv)
                                    vp.vlcVout.attachViews(layoutListener)
                                    if (tv.width > 0 && tv.height > 0) {
                                        runCatching { vp.vlcVout.setWindowSize(tv.width, tv.height) }
                                    }
                                }
                                if (wInt > 0 && hInt > 0) {
                                    runCatching { vp.vlcVout.setWindowSize(wInt, hInt) }
                                }
                                runCatching {
                                    vp.scale = 0f
                                    vp.aspectRatio = if (
                                        effectiveAspect == PlayerAspectMode.STRETCH ||
                                        effectiveAspect.fixedRatio != null ||
                                        naturalRatio != null
                                    ) {
                                        "$wInt:$hInt"
                                    } else {
                                        null
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else if (exoPlayer != null) {
                    val exoResizeMode = if (naturalRatio != null || effectiveAspect == PlayerAspectMode.STRETCH || effectiveAspect.fixedRatio != null) {
                        AspectRatioFrameLayout.RESIZE_MODE_FILL
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                    AndroidView(
                        factory = { ctx ->
                            (android.view.LayoutInflater.from(ctx)
                                .inflate(R.layout.deckvault_texture_player_view, null) as PlayerView).apply {
                                this.player = exoPlayer
                                useController = false
                                resizeMode = exoResizeMode
                                keepScreenOn = true
                            }
                        },
                        update = { pv ->
                            if (pv.player !== exoPlayer) pv.player = exoPlayer
                            pv.resizeMode = exoResizeMode
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                if (cropOverlayActive) {
                    VisualCropOverlay(
                        crop = studioCrop,
                        sourceWidth = naturalVideoWidth,
                        sourceHeight = naturalVideoHeight,
                        onCropChange = { studioCrop = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            if (isBuffering && playbackErrorText == null) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            playbackErrorText?.let { err ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = surfaces.panelSurface.copy(alpha = 0.94f),
                    border = BorderStroke(1.dp, surfaces.dangerMuted.copy(alpha = 0.7f)),
                    modifier = Modifier.padding(24.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(20.dp),
                    ) {
                        Text("Playback Issue", style = MaterialTheme.typography.titleMedium, color = surfaces.dangerMuted)
                        Text(err, style = MaterialTheme.typography.bodySmall, color = surfaces.textSecondary)
                    }
                }
            }

            // 2x Hold Boost Pill at Top Center
            AnimatedVisibility(
                visible = isBoost2x,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 18.dp),
            ) {
                Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.78f), border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)) {
                        Icon(Icons.Rounded.FastForward, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Text("2.0x Speed Boost", color = Color.White, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }

            // Center Gesture HUD (Seek / Brightness / Volume Boost / Double Tap)
            AnimatedVisibility(
                visible = gestureHudTitle != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.Black.copy(alpha = 0.80f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            when {
                                gestureHudTitle?.contains("Brightness") == true -> Icon(Icons.Rounded.BrightnessHigh, null, tint = Color.White)
                                gestureHudTitle?.contains("Volume") == true -> Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Text(
                                text = gestureHudTitle.orEmpty(),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                            )
                        }
                        gestureHudSub?.let {
                            Text(it, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
                        }
                        if (gestureHudFraction >= 0f) {
                            LinearProgressIndicator(
                                progress = { gestureHudFraction.coerceIn(0f, 1f) },
                                modifier = Modifier.width(150.dp).height(4.dp),
                            )
                        }
                    }
                }
            }

            // Unlock Pill when locked
            AnimatedVisibility(
                visible = isLocked && showUnlockPill,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 28.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.84f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier.clickable {
                        isLocked = false
                        showControls = true
                        interactionNonce++
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                        Icon(Icons.Rounded.LockOpen, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text("Screen Locked — Tap to Unlock", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }

    val renderTopBar: @Composable (Boolean) -> Unit = { docked ->
        val headerBg = if (docked) {
            Modifier.background(surfaces.panelSurface)
        } else {
            Modifier.background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.82f), Color.Transparent)))
        }
        val fgPrimary = if (docked) surfaces.textPrimary else Color.White
        val fgSecondary = if (docked) surfaces.textSecondary else Color.White.copy(alpha = 0.72f)
        Column(modifier = Modifier.fillMaxWidth().then(headerBg)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = if (docked) 6.dp else 10.dp),
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close", tint = fgPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = fgPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val infoLine = buildString {
                        append(Formatters.formatBytes(file.sizeBytes))
                        val w = naturalVideoWidth.takeIf { it > 0 } ?: file.meta?.width ?: 0
                        val h = naturalVideoHeight.takeIf { it > 0 } ?: file.meta?.height ?: 0
                        val res = Formatters.formatResolution(w, h)
                        if (res.isNotBlank()) append(" • $res")
                        if (usedCompatibilityFallback) {
                            append(" • Live Transcode")
                        } else if (activeEngine == VideoEngineMode.VLC) {
                            append(" • VLC Native")
                        }
                        if (loopStartMs != null && loopEndMs != null) {
                            append(" • Loop ${Formatters.formatDuration(loopStartMs!!)}–${Formatters.formatDuration(loopEndMs!!)}")
                        }
                    }
                    Text(infoLine, style = MaterialTheme.typography.labelSmall, color = fgSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    if (prevItem != null) {
                        IconButton(onClick = { interactionNonce++; onSelectVideo(prevItem) }) {
                            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous Video", tint = fgPrimary)
                        }
                    }
                    if (nextItem != null) {
                        IconButton(onClick = { interactionNonce++; onSelectVideo(nextItem) }) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = "Next Video", tint = fgPrimary)
                        }
                    }

                    AssistChip(
                        onClick = { interactionNonce++; onOpenOptimizer(file) },
                        leadingIcon = { Icon(Icons.Rounded.AutoFixHigh, null, Modifier.size(16.dp)) },
                        label = { Text("Optimize") },
                    )
                    AssistChip(
                        onClick = {
                            interactionNonce++
                            studioExpanded = !studioExpanded
                            studioPeekCollapsed = false
                            if (studioExpanded) enginePause() else studioCropActive = false
                        },
                        leadingIcon = { Icon(Icons.Rounded.ContentCut, null, Modifier.size(16.dp)) },
                        label = { Text(if (studioExpanded) "Hide Studio" else "Studio") },
                    )

                    if (!docked && activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        IconButton(
                            onClick = {
                                val params = PictureInPictureParams.Builder()
                                    .setAspectRatio(Rational(16, 9))
                                    .build()
                                activity.enterPictureInPictureMode(params)
                            },
                        ) {
                            Icon(Icons.Rounded.PictureInPictureAlt, contentDescription = "Picture in Picture", tint = fgPrimary)
                        }
                    }

                    if (!docked) {
                        IconButton(
                            onClick = {
                                interactionNonce++
                                val vals = ScreenRotationMode.entries
                                rotationMode = vals[(rotationMode.ordinal + 1) % vals.size]
                                gestureHudTitle = "Rotation: ${rotationMode.label}"
                                gestureHudSub = null
                                gestureHudFraction = -1f
                                hudNonce++
                            },
                        ) {
                            Icon(Icons.Rounded.ScreenRotation, contentDescription = "Rotate Screen", tint = fgPrimary)
                        }
                    }
                }
            }
            if (docked) {
                HorizontalDivider(color = surfaces.hairlineBorder)
            }
        }
    }

    val renderStudioPanel: @Composable (Boolean) -> Unit = { sideRail ->
        StudioEditorPanel(
            filePath = file.path,
            frameUrls = framePreviewUrls,
            durationMs = durationMs,
            currentPosMs = currentPosMs,
            startMs = studioStartMs,
            endMs = studioEndMs.coerceAtLeast(1L),
            crop = studioCrop,
            cropActive = studioCropActive,
            rotate = studioRotate,
            flipH = studioFlipH,
            flipV = studioFlipV,
            speed = studioSpeed,
            muteAudio = studioMute,
            brightness = studioBrightness,
            contrast = studioContrast,
            saturation = studioSaturation,
            sharpen = studioSharpen,
            deband = studioDeband,
            stabilize = studioStabilize,
            normalizeAudio = studioNormalize,
            saveMode = studioSaveMode,
            isLocalSource = isLocalSource,
            sourceHostName = sourceHostName,
            sourceHostIsAndroid = sourceHostIsAndroid,
            pairedPcHosts = pairedPcHosts,
            selectedOffloadHost = studioOffloadHost,
            executionTarget = studioTarget,
            activeTab = studioTab,
            onActiveTabChange = { studioTab = it },
            sideRailMode = sideRail,
            onCollapsePeek = { studioPeekCollapsed = true },
            onSetStartEnd = { s, e ->
                studioStartMs = s
                studioEndMs = e
                smartSeekTo(s)
            },
            onToggleCropActive = { studioCropActive = it },
            onCropChange = { studioCrop = it },
            onRotateChange = { studioRotate = it },
            onFlipHChange = { studioFlipH = it },
            onFlipVChange = { studioFlipV = it },
            onSpeedChange = { studioSpeed = it },
            onMuteChange = { studioMute = it },
            onColorChange = { b, c, s ->
                studioBrightness = b
                studioContrast = c
                studioSaturation = s
            },
            onProFlagsChange = { sh, db, st, nm ->
                studioSharpen = sh
                studioDeband = db
                studioStabilize = st
                studioNormalize = nm
                if ((sh > 0.2f || db || st || nm)) {
                    if (!isLocalSource && !sourceHostIsAndroid) {
                        studioTarget = JobExecutionTarget.PC_REMOTE
                    } else if (studioOffloadHost != null) {
                        studioTarget = JobExecutionTarget.PC_OFFLOAD
                    }
                }
            },
            onSaveModeChange = { studioSaveMode = it },
            onExecutionTargetChange = { tgt, host ->
                studioTarget = tgt
                if (host != null) studioOffloadHost = host
            },
            onExtractAudio = { req, tgt, host -> onSubmitExtractAudio(req, tgt, host) },
            onExportGif = { req, host -> onSubmitExportGif(req, host) },
            onSubmitEdit = { req, tgt, host, runNow ->
                onSubmitStudioEdit(req, tgt, host, runNow)
                if (runNow) {
                    studioExpanded = false
                    studioCropActive = false
                    studioPeekCollapsed = false
                }
            },
        )
    }

    val renderSlimStudioTransport: @Composable (Boolean) -> Unit = { showExpandBtn ->
        val hasDur = durationMs > 0L
        val safeDur = durationMs.coerceAtLeast(1000L).toFloat()
        val displayPos = if (isSliderDragging) sliderDragMs else currentPosMs
        val displayFrac = if (isSliderDragging) sliderDragFrac else vlcPositionFrac
        val tabName = when (studioTab) {
            1 -> "Crop"
            2 -> "Color"
            3 -> "Audio/GIF"
            else -> "Trim"
        }
        Surface(
            color = surfaces.panelSurface,
            border = BorderStroke(1.dp, surfaces.hairlineBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                IconButton(
                    onClick = {
                        enginePause()
                        if (hasDur) smartSeekTo((engineCurrentLogicalPosMs() - 33L).coerceAtLeast(0L))
                        else smartSeekToFraction((vlcPositionFrac - 0.02f).coerceIn(0f, 1f))
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    Text("-1f", color = surfaces.textSecondary, style = MaterialTheme.typography.labelSmall)
                }
                FilledIconButton(
                    onClick = {
                        if (engineIsPlaying()) enginePause() else enginePlay()
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = {
                        enginePause()
                        if (hasDur) smartSeekTo(engineCurrentLogicalPosMs() + 33L)
                        else smartSeekToFraction((vlcPositionFrac + 0.02f).coerceIn(0f, 0.999f))
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    Text("+1f", color = surfaces.textSecondary, style = MaterialTheme.typography.labelSmall)
                }
                if (hasDur) {
                    Slider(
                        value = displayPos.toFloat().coerceIn(0f, safeDur),
                        onValueChange = { nextVal ->
                            isSliderDragging = true
                            sliderDragMs = nextVal.roundToInt().toLong()
                        },
                        onValueChangeFinished = {
                            smartSeekTo(sliderDragMs)
                            isSliderDragging = false
                        },
                        valueRange = 0f..safeDur,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${Formatters.formatDuration(displayPos)}/${Formatters.formatDuration(durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = surfaces.textSecondary,
                    )
                } else {
                    Slider(
                        value = displayFrac.coerceIn(0f, 1f),
                        onValueChange = { nextVal ->
                            isSliderDragging = true
                            sliderDragFrac = nextVal.coerceIn(0f, 1f)
                        },
                        onValueChangeFinished = {
                            smartSeekToFraction(sliderDragFrac)
                            isSliderDragging = false
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${(displayFrac * 100f).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = surfaces.textSecondary,
                    )
                }
                if (showExpandBtn) {
                    AssistChip(
                        onClick = { studioPeekCollapsed = false },
                        leadingIcon = { Icon(Icons.Rounded.KeyboardArrowUp, null, Modifier.size(16.dp)) },
                        label = { Text("Expand $tabName") },
                    )
                }
            }
        }
    }

    val renderCinemaBottomBar: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f), Color.Black.copy(alpha = 0.96f))))
                .navigationBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                    val hasDur = durationMs > 0L
                    val safeDur = durationMs.coerceAtLeast(1000L).toFloat()
                    val displayPos = if (isSliderDragging) sliderDragMs else currentPosMs
                    val displayFrac = if (isSliderDragging) sliderDragFrac else vlcPositionFrac

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (hasDur) {
                            Text(Formatters.formatDuration(displayPos), style = MaterialTheme.typography.labelMedium, color = Color.White)
                            Slider(
                                value = displayPos.toFloat().coerceIn(0f, safeDur),
                                onValueChange = { nextVal ->
                                    isSliderDragging = true
                                    sliderDragMs = nextVal.roundToInt().toLong()
                                    interactionNonce++
                                },
                                onValueChangeFinished = {
                                    smartSeekTo(sliderDragMs)
                                    isSliderDragging = false
                                    interactionNonce++
                                },
                                valueRange = 0f..safeDur,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.26f),
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            Text(Formatters.formatDuration(durationMs), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.78f))
                        } else {
                            val leftLabel = if (displayPos > 0L) Formatters.formatDuration(displayPos) else "${(displayFrac * 100f).roundToInt()}%"
                            val rightLabel = if (vlcPositionFrac > 0f || isSliderDragging) "${(displayFrac * 100f).roundToInt()}%" else "Stream"
                            Text(leftLabel, style = MaterialTheme.typography.labelMedium, color = Color.White)
                            Slider(
                                value = displayFrac.coerceIn(0f, 1f),
                                onValueChange = { nextVal ->
                                    isSliderDragging = true
                                    sliderDragFrac = nextVal.coerceIn(0f, 1f)
                                    interactionNonce++
                                },
                                onValueChangeFinished = {
                                    smartSeekToFraction(sliderDragFrac)
                                    isSliderDragging = false
                                    interactionNonce++
                                },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.26f),
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            Text(rightLabel, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.78f))
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        // Left: Transport buttons
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = {
                                    interactionNonce++
                                    enginePause()
                                    val t = (engineCurrentLogicalPosMs() - 33L).coerceAtLeast(0L)
                                    smartSeekTo(t)
                                },
                            ) {
                                Text("-1f", color = Color.White, style = MaterialTheme.typography.labelMedium)
                            }
                            IconButton(
                                onClick = {
                                    interactionNonce++
                                    val t = (engineCurrentLogicalPosMs() - seekStepSeconds * 1000L).coerceAtLeast(0L)
                                    smartSeekTo(t)
                                },
                            ) {
                                Icon(Icons.Rounded.FastRewind, contentDescription = "Rewind", tint = Color.White)
                            }
                            FilledIconButton(
                                onClick = {
                                    interactionNonce++
                                    if (engineIsPlaying()) enginePause() else enginePlay()
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                )
                            }
                            IconButton(
                                onClick = {
                                    interactionNonce++
                                    val dur = engineDurationMs()
                                    val t = (engineCurrentLogicalPosMs() + seekStepSeconds * 1000L).let { if (dur > 0) it.coerceAtMost(dur) else it }
                                    smartSeekTo(t)
                                },
                            ) {
                                Icon(Icons.Rounded.FastForward, contentDescription = "Forward", tint = Color.White)
                            }
                            IconButton(
                                onClick = {
                                    interactionNonce++
                                    enginePause()
                                    val t = engineCurrentLogicalPosMs() + 33L
                                    smartSeekTo(t)
                                },
                            ) {
                                Text("+1f", color = Color.White, style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        // Right: Quick cinema utility pills
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            IconButton(onClick = { isLocked = true; showControls = false; showUnlockPill = true }) {
                                Icon(Icons.Rounded.Lock, contentDescription = "Lock Screen", tint = Color.White)
                            }

                            Box {
                                AssistChip(
                                    onClick = { interactionNonce++; showSpeedMenu = true },
                                    leadingIcon = { Icon(Icons.Rounded.Speed, null, Modifier.size(16.dp)) },
                                    label = { Text("${playbackSpeed}x") },
                                )
                                DropdownMenu(expanded = showSpeedMenu, onDismissRequest = { showSpeedMenu = false }) {
                                    listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f, 4.0f).forEach { spd ->
                                        DropdownMenuItem(
                                            text = { Text("${spd}x") },
                                            onClick = { playbackSpeed = spd; showSpeedMenu = false; interactionNonce++ },
                                        )
                                    }
                                }
                            }

                            AssistChip(
                                onClick = {
                                    interactionNonce++
                                    val modes = PlayerAspectMode.entries
                                    aspectMode = modes[(aspectMode.ordinal + 1) % modes.size]
                                    gestureHudTitle = "Aspect: ${aspectMode.label}"
                                    gestureHudSub = null
                                    gestureHudFraction = -1f
                                    hudNonce++
                                },
                                leadingIcon = { Icon(Icons.Rounded.AspectRatio, null, Modifier.size(16.dp)) },
                                label = { Text(aspectMode.label) },
                            )

                            AssistChip(
                                onClick = {
                                    interactionNonce++
                                    val nextEngine = if (activeEngine == VideoEngineMode.VLC) VideoEngineMode.EXO else VideoEngineMode.VLC
                                    pendingEngineResumeMs = engineCurrentLogicalPosMs()
                                    resumeApplied = false
                                    usedCompatibilityFallback = false
                                    liveTranscodeBaseMs = 0L
                                    activeUri = streamUrl
                                    activeEngine = nextEngine
                                    gestureHudTitle = "Decoder: ${nextEngine.label}"
                                    gestureHudSub = if (nextEngine == VideoEngineMode.VLC) "LibVLC Universal Engine" else "ExoPlayer + FFmpeg"
                                    gestureHudFraction = -1f
                                    hudNonce++
                                },
                                label = { Text(activeEngine.label) },
                            )

                            AssistChip(
                                onClick = {
                                    interactionNonce++
                                    val pos = engineCurrentLogicalPosMs()
                                    when {
                                        loopStartMs == null -> {
                                            loopStartMs = pos
                                            gestureHudTitle = "Loop A Set"
                                            gestureHudSub = Formatters.formatDuration(pos)
                                        }
                                        loopEndMs == null && pos > loopStartMs!! + 300L -> {
                                            loopEndMs = pos
                                            gestureHudTitle = "A-B Loop Active"
                                            gestureHudSub = "${Formatters.formatDuration(loopStartMs!!)} – ${Formatters.formatDuration(pos)}"
                                        }
                                        else -> {
                                            loopStartMs = null
                                            loopEndMs = null
                                            gestureHudTitle = "A-B Loop Cleared"
                                            gestureHudSub = null
                                        }
                                    }
                                    gestureHudFraction = -1f
                                    hudNonce++
                                },
                                leadingIcon = { Icon(Icons.Rounded.Loop, null, Modifier.size(16.dp)) },
                                label = { Text(if (loopStartMs != null && loopEndMs == null) "Set B" else if (loopEndMs != null) "A-B On" else "A-B") },
                            )

                            IconButton(onClick = { interactionNonce++; showTracksDialog = true }) {
                                Icon(Icons.Rounded.Subtitles, contentDescription = "Audio, Subtitles & Sleep Timer", tint = Color.White)
                            }

                            IconButton(onClick = { interactionNonce++; mirrorPreview = !mirrorPreview }) {
                                Icon(Icons.Rounded.Flip, contentDescription = "Mirror Flip Preview", tint = if (mirrorPreview) MaterialTheme.colorScheme.primary else Color.White)
                            }

                            IconButton(onClick = { interactionNonce++; onSaveSnapshot(file.path, engineCurrentLogicalPosMs()) }) {
                                Icon(Icons.Rounded.CameraAlt, contentDescription = "Save Frame Snapshot", tint = Color.White)
                            }

                            if (zoomScale > 1.05f) {
                                IconButton(onClick = { zoomScale = 1f; panX = 0f; panY = 0f }) {
                                    Icon(Icons.Rounded.ZoomOutMap, contentDescription = "Reset Zoom", tint = MaterialTheme.colorScheme.primary)
                                }
                            }

                            IconButton(onClick = { interactionNonce++; isFullscreen = !isFullscreen }) {
                                Icon(
                                    imageVector = if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                    contentDescription = "Fullscreen",
                                    tint = Color.White,
                                )
                            }
                        }
                    }
                }
            }
        }

    // Root Adaptive Player Container (Single Permanent Video Stage Slot — never reparents TextureView/PlayerView!):
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(if (studioExpanded) surfaces.background else Color.Black),
    ) {
        val rootMaxHeight = maxHeight
        val useSideRail = studioExpanded && !studioPeekCollapsed && maxWidth >= 840.dp && maxWidth > rootMaxHeight * 1.15f
        Column(modifier = Modifier.fillMaxSize()) {
            if (studioExpanded) {
                renderTopBar(true)
            }
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        renderVideoStage(Modifier.fillMaxSize())
                        if (!studioExpanded) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = showControls && !isLocked,
                                enter = fadeIn(),
                                exit = fadeOut(),
                                modifier = Modifier.align(Alignment.TopCenter),
                            ) {
                                renderTopBar(false)
                            }
                            androidx.compose.animation.AnimatedVisibility(
                                visible = showControls && !isLocked,
                                enter = fadeIn(),
                                exit = fadeOut(),
                                modifier = Modifier.align(Alignment.BottomCenter),
                            ) {
                                renderCinemaBottomBar()
                            }
                        }
                    }
                    if (studioExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (useSideRail) Modifier else Modifier.navigationBarsPadding()),
                        ) {
                            renderSlimStudioTransport(studioPeekCollapsed)
                            if (!studioPeekCollapsed && !useSideRail) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = (rootMaxHeight * 0.43f).coerceAtLeast(220.dp)),
                                ) {
                                    renderStudioPanel(false)
                                }
                            }
                        }
                    }
                }
                if (studioExpanded && !studioPeekCollapsed && useSideRail) {
                    Box(
                        modifier = Modifier
                            .width(356.dp)
                            .fillMaxHeight()
                            .navigationBarsPadding()
                            .padding(top = 6.dp, end = 8.dp, bottom = 8.dp),
                    ) {
                        renderStudioPanel(true)
                    }
                }
            }
        }
    }

    if (showTracksDialog) {
        val audioOpts = remember(activeEngine, currentTracks, vlcAudioTracks) {
            if (activeEngine == VideoEngineMode.VLC) vlcAudioTracks
            else extractTrackOptions(currentTracks, C.TRACK_TYPE_AUDIO)
        }
        val textOpts = remember(activeEngine, currentTracks, vlcSubTracks) {
            if (activeEngine == VideoEngineMode.VLC) vlcSubTracks
            else extractTrackOptions(currentTracks, C.TRACK_TYPE_TEXT)
        }
        TracksAndSleepDialog(
            audioTracks = audioOpts,
            subtitleTracks = textOpts,
            sleepMinutesRemaining = sleepMinutes,
            onSelectAudioTrack = { opt ->
                if (activeEngine == VideoEngineMode.VLC) {
                    val vp = vlcSession?.second
                    if (vp != null) {
                        vp.setAudioTrack(opt.trackIndex)
                        val cur = vp.audioTrack
                        vlcAudioTracks = vlcAudioTracks.map { it.copy(selected = it.trackIndex == cur) }
                    }
                } else if (exoPlayer != null) {
                    val grp = currentTracks.groups.getOrNull(opt.groupIndex)?.mediaTrackGroup
                    if (grp != null) {
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setOverrideForType(TrackSelectionOverride(grp, opt.trackIndex))
                            .build()
                    }
                }
            },
            onSelectSubtitleTrack = { opt ->
                if (activeEngine == VideoEngineMode.VLC) {
                    val vp = vlcSession?.second
                    if (vp != null) {
                        vp.setSpuTrack(opt?.trackIndex ?: -1)
                        val cur = vp.spuTrack
                        vlcSubTracks = vlcSubTracks.map { it.copy(selected = it.trackIndex == cur) }
                    }
                } else if (exoPlayer != null) {
                    val builder = exoPlayer.trackSelectionParameters.buildUpon()
                    if (opt == null) {
                        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    } else {
                        val grp = currentTracks.groups.getOrNull(opt.groupIndex)?.mediaTrackGroup
                        if (grp != null) {
                            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            builder.setOverrideForType(TrackSelectionOverride(grp, opt.trackIndex))
                        }
                    }
                    exoPlayer.trackSelectionParameters = builder.build()
                }
            },
            onPickExternalSubtitle = {
                subtitlePicker.launch(arrayOf("application/x-subrip", "text/vtt", "text/*", "*/*"))
            },
            onSetSleepMinutes = { mins -> sleepMinutes = mins },
            onDismiss = { showTracksDialog = false },
        )
    }
}

private fun extractTrackOptions(tracks: Tracks, trackType: Int): List<PlayerTrackOption> {
    val list = mutableListOf<PlayerTrackOption>()
    tracks.groups.forEachIndexed { gIdx, grp ->
        if (grp.type == trackType) {
            for (tIdx in 0 until grp.length) {
                val fmt = grp.getTrackFormat(tIdx)
                val label = fmt.label ?: fmt.sampleMimeType ?: "Track ${list.size + 1}"
                val lang = fmt.language.orEmpty()
                list.add(PlayerTrackOption(gIdx, tIdx, label, lang, grp.isTrackSelected(tIdx)))
            }
        }
    }
    return list
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
