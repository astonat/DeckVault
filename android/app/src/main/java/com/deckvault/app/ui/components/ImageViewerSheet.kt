package com.deckvault.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.deckvault.app.data.DeckVaultApi
import com.deckvault.app.model.FileItem
import com.deckvault.app.model.SavedHost
import com.deckvault.app.ui.theme.OledColors
import com.deckvault.app.ui.util.Formatters

@Composable
fun ImageViewerOverlay(
    host: SavedHost,
    api: DeckVaultApi,
    images: List<FileItem>,
    initialIndex: Int,
    cacheVersion: Long,
    onDismiss: () -> Unit,
    onDeleteImage: (FileItem) -> Unit,
) {
    var index by remember(initialIndex, images.size) { mutableStateOf(initialIndex.coerceIn(0, (images.size - 1).coerceAtLeast(0))) }
    val current = images.getOrNull(index) ?: return

    var scale by remember(current.path) { mutableFloatStateOf(1f) }
    var offset by remember(current.path) { mutableStateOf(Offset.Zero) }

    Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = api.imageUrl(host, current.path, width = 0, cacheBust = cacheVersion),
                contentDescription = current.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(current.path) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val nextScale = (scale * zoom).coerceIn(1f, 5f)
                            scale = nextScale
                            offset = if (nextScale > 1.02f) offset + pan else Offset.Zero
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    ),
            )

            // Top bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(current.name, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${Formatters.formatBytes(current.sizeBytes)}  •  ${index + 1} / ${images.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                IconButton(onClick = { onDeleteImage(current) }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = OledColors.DangerRed)
                }
            }

            if (index > 0) {
                FilledTonalIconButton(
                    onClick = { index-- },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.Black.copy(alpha = 0.7f)),
                    modifier = Modifier.align(Alignment.CenterStart).padding(14.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous", tint = Color.White)
                }
            }
            if (index < images.lastIndex) {
                FilledTonalIconButton(
                    onClick = { index++ },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.Black.copy(alpha = 0.7f)),
                    modifier = Modifier.align(Alignment.CenterEnd).padding(14.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next", tint = Color.White)
                }
            }
        }
    }
}
