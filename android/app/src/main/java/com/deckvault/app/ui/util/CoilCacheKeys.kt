package com.deckvault.app.ui.util

import android.content.Context
import coil3.request.ImageRequest

/**
 * Builds stable Coil 3 memory & disk cache keys that are independent of volatile
 * LAN IP addresses and pairing tokens so Wi-Fi IP changes never wipe cached thumbnails.
 */
object CoilCacheKeys {

    fun mediaKey(
        hostId: String,
        kind: String,
        path: String,
        width: Int,
        sizeBytes: Long,
        modifiedMs: Long,
        cacheVersion: Long,
    ): String = "dv|$hostId|$kind|$path|w=$width|sz=$sizeBytes|m=$modifiedMs|v=$cacheVersion"

    fun request(
        context: Context,
        url: String,
        cacheKey: String,
    ): ImageRequest = ImageRequest.Builder(context)
        .data(url)
        .memoryCacheKey(cacheKey)
        .diskCacheKey(cacheKey)
        .build()
}
