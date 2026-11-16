package com.deckvault.app.ui.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

object Formatters {

    fun formatBytes(bytes: Long): String {
        if (bytes < 0L) return "Indexing..."
        if (bytes == 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
        val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
        val value = bytes / 1024.0.pow(digitGroups.toDouble())
        val pattern = when {
            digitGroups == 0 -> "%.0f %s"
            value >= 100.0 -> "%.0f %s"
            value >= 10.0 -> "%.1f %s"
            else -> "%.2f %s"
        }
        return String.format(Locale.US, pattern, value, units[digitGroups])
    }

    fun formatDuration(ms: Long): String {
        if (ms <= 0L) return "--:--"
        val totalSec = ms / 1000L
        val h = totalSec / 3600L
        val m = (totalSec % 3600L) / 60L
        val s = totalSec % 60L
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%02d:%02d", m, s)
        }
    }

    fun formatDurationPrecise(ms: Long): String {
        if (ms <= 0L) return "0:00.0"
        val totalSec = ms / 1000L
        val tenths = (ms % 1000L) / 100L
        val h = totalSec / 3600L
        val m = (totalSec % 3600L) / 60L
        val s = totalSec % 60L
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d.%d", h, m, s, tenths)
        } else {
            String.format(Locale.US, "%02d:%02d.%d", m, s, tenths)
        }
    }

    fun formatResolution(width: Int, height: Int): String {
        if (width <= 0 || height <= 0) return ""
        val maxDim = maxOf(width, height)
        val minDim = minOf(width, height)
        return when {
            maxDim >= 3800 || minDim >= 2100 -> "4K"
            maxDim >= 2500 || minDim >= 1400 -> "1440p"
            maxDim >= 1900 || minDim >= 1050 -> "1080p"
            maxDim >= 1250 || minDim >= 700 -> "720p"
            else -> "${minDim}p"
        }
    }

    fun formatShortDate(epochMs: Long): String {
        if (epochMs <= 0L) return ""
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.US)
        return sdf.format(Date(epochMs))
    }
}
