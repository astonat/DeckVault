package com.deckvault.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.deckvault.app.ui.util.Formatters
import kotlin.math.roundToInt

/**
 * Interactive OLED media thumbnail supporting:
 * - Instant S Pen / mouse hover scrubbing across the video card
 * - Long-press + horizontal drag scrubbing anywhere on the thumbnail
 * - Direct horizontal drag along the bottom scrub strip (never fights vertical grid scroll)
 */
@Composable
fun VideoScrubPreview(
    thumbnailUrl: String,
    storyboardUrl: String,
    durationMs: Long,
    enableScrub: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
    cols: Int = 4,
    rows: Int = 4,
) {
    val context = LocalContext.current
    var isScrubbing by remember(storyboardUrl) { mutableStateOf(false) }
    var scrubFraction by remember(storyboardUrl) { mutableFloatStateOf(0f) }
    var boxWidthPx by remember { mutableFloatStateOf(1f) }
    var spriteBitmap by remember(storyboardUrl) { mutableStateOf<ImageBitmap?>(null) }
    var loadingSprite by remember(storyboardUrl) { mutableStateOf(false) }

    // Fetch 4x4 storyboard sprite sheet lazily on first scrub interaction
    LaunchedEffect(isScrubbing, storyboardUrl) {
        if (isScrubbing && spriteBitmap == null && !loadingSprite && storyboardUrl.isNotBlank()) {
            loadingSprite = true
            runCatching {
                val loader = SingletonImageLoader.get(context)
                val request = ImageRequest.Builder(context)
                    .data(storyboardUrl)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    spriteBitmap = result.image.toBitmap().asImageBitmap()
                }
            }
            loadingSprite = false
        }
    }

    val scrubPointerMod = if (enableScrub) {
        Modifier
            .pointerInput(storyboardUrl) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        isScrubbing = true
                        scrubFraction = (offset.x / boxWidthPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        scrubFraction = (change.position.x / boxWidthPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
                    },
                    onDragEnd = { isScrubbing = false },
                    onDragCancel = { isScrubbing = false },
                )
            }
            .pointerInput(storyboardUrl) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val first = event.changes.firstOrNull() ?: continue
                        val isHoverDevice = first.type == PointerType.Stylus ||
                            first.type == PointerType.Eraser ||
                            first.type == PointerType.Mouse
                        if (!isHoverDevice) continue
                        when (event.type) {
                            PointerEventType.Enter, PointerEventType.Move -> {
                                isScrubbing = true
                                scrubFraction = (first.position.x / boxWidthPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
                            }
                            PointerEventType.Exit -> {
                                isScrubbing = false
                            }
                        }
                    }
                }
            }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .onSizeChanged { boxWidthPx = it.width.toFloat().coerceAtLeast(1f) }
            .then(scrubPointerMod)
            .background(Color.Black),
    ) {
        // Resting thumbnail
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Storyboard sprite frame renderer during active scrub
        val sprite = spriteBitmap
        if (isScrubbing && sprite != null) {
            val totalCells = cols * rows
            val cellIndex = (scrubFraction * (totalCells - 1)).roundToInt().coerceIn(0, totalCells - 1)
            val col = cellIndex % cols
            val row = cellIndex / cols
            val cellW = (sprite.width / cols).coerceAtLeast(1)
            val cellH = (sprite.height / rows).coerceAtLeast(1)

            Canvas(modifier = Modifier.fillMaxSize()) {
                drawImage(
                    image = sprite,
                    srcOffset = IntOffset(col * cellW, row * cellH),
                    srcSize = IntSize(cellW, cellH),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                )
            }
        }

        // Subtle dark gradient scrim at bottom for contrast
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(44.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                ),
        )

        // Active scrub overlay: live timestamp pill + spinner if sprite loading
        AnimatedVisibility(
            visible = isScrubbing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            val currentMs = (durationMs * scrubFraction).toLong()
            Surface(
                color = Color.Black.copy(alpha = 0.82f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    if (loadingSprite) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Text(
                        text = if (durationMs > 0) {
                            "${Formatters.formatDuration(currentMs)} / ${Formatters.formatDuration(durationMs)}"
                        } else {
                            "Scrubbing ${(scrubFraction * 100).roundToInt()}%"
                        },
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                }
            }
        }

        // Dedicated bottom scrub bar: drag directly left/right without stealing vertical grid scroll!
        if (enableScrub) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(24.dp)
                    .pointerInput(storyboardUrl) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                isScrubbing = true
                                scrubFraction = (offset.x / boxWidthPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
                            },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                scrubFraction = (change.position.x / boxWidthPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
                            },
                            onDragEnd = { isScrubbing = false },
                            onDragCancel = { isScrubbing = false },
                        )
                    },
                contentAlignment = Alignment.BottomCenter,
            ) {
                val trackHeight = if (isScrubbing) 5.dp else 3.dp
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .height(trackHeight)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = if (isScrubbing) 0.28f else 0.18f)),
                ) {
                    val fillFrac = if (isScrubbing) scrubFraction.coerceAtLeast(0.02f) else 0f
                    if (fillFrac > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fillFrac)
                                .height(trackHeight)
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Renders an evenly-spaced visual filmstrip across the width of the video trimmer timeline
 * from a single 4x4 storyboard sprite sheet.
 */
@Composable
fun StoryboardFilmstripRow(
    storyboardUrl: String,
    modifier: Modifier = Modifier,
    cols: Int = 4,
    rows: Int = 4,
    slices: Int = 10,
) {
    val context = LocalContext.current
    var spriteBitmap by remember(storyboardUrl) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(storyboardUrl) {
        if (storyboardUrl.isNotBlank()) {
            runCatching {
                val loader = SingletonImageLoader.get(context)
                val req = ImageRequest.Builder(context)
                    .data(storyboardUrl)
                    .allowHardware(false)
                    .build()
                val res = loader.execute(req)
                if (res is SuccessResult) {
                    spriteBitmap = res.image.toBitmap().asImageBitmap()
                }
            }
        }
    }

    val sprite = spriteBitmap
    if (sprite == null) {
        Box(
            modifier = modifier.background(Color(0xFF14141F)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Loading video timeline frames...",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        Canvas(modifier = modifier) {
            val totalCells = cols * rows
            val cellW = (sprite.width / cols).coerceAtLeast(1)
            val cellH = (sprite.height / rows).coerceAtLeast(1)
            val sliceWidth = size.width / slices.toFloat()
            for (i in 0 until slices) {
                val cellIdx = ((i.toFloat() / (slices - 1).coerceAtLeast(1)) * (totalCells - 1))
                    .roundToInt()
                    .coerceIn(0, totalCells - 1)
                val col = cellIdx % cols
                val row = cellIdx / cols
                val dstLeft = (i * sliceWidth).roundToInt()
                val dstRight = ((i + 1) * sliceWidth).roundToInt()
                drawImage(
                    image = sprite,
                    srcOffset = IntOffset(col * cellW, row * cellH),
                    srcSize = IntSize(cellW, cellH),
                    dstOffset = IntOffset(dstLeft, 0),
                    dstSize = IntSize((dstRight - dstLeft).coerceAtLeast(1), size.height.roundToInt()),
                )
            }
        }
    }
}
