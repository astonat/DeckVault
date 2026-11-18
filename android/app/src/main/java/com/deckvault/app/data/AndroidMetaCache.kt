package com.deckvault.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.deckvault.app.model.VideoMeta

data class CachedVideoMetaRow(
    val path: String,
    val mtimeMs: Long,
    val sizeBytes: Long,
    val meta: VideoMeta,
)

/**
 * Persistent WAL SQLite metadata cache for Android local storage and AndroidPeerServer,
 * mirroring the PC companion's MetadataCache so durations/resolutions survive restarts.
 */
class AndroidMetaCache(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "deckvault_meta_cache.db", null, 1) {

    private val lock = Any()

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        runCatching { db.enableWriteAheadLogging() }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS video_meta (
                path TEXT PRIMARY KEY,
                mtime_ms INTEGER NOT NULL,
                size_bytes INTEGER NOT NULL,
                duration_ms INTEGER NOT NULL,
                width INTEGER NOT NULL,
                height INTEGER NOT NULL,
                bitrate INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun get(path: String, mtimeMs: Long, sizeBytes: Long): VideoMeta? = synchronized(lock) {
        runCatching {
            readableDatabase.rawQuery(
                "SELECT duration_ms, width, height, bitrate FROM video_meta WHERE path = ? AND mtime_ms = ? AND size_bytes = ?",
                arrayOf(path, mtimeMs.toString(), sizeBytes.toString()),
            ).use { c ->
                if (c.moveToFirst()) {
                    VideoMeta(
                        durationMs = c.getLong(0),
                        width = c.getInt(1),
                        height = c.getInt(2),
                        bitrate = c.getLong(3),
                    )
                } else null
            }
        }.getOrNull()
    }

    fun getByPath(path: String): VideoMeta? = synchronized(lock) {
        runCatching {
            readableDatabase.rawQuery(
                "SELECT duration_ms, width, height, bitrate FROM video_meta WHERE path = ?",
                arrayOf(path),
            ).use { c ->
                if (c.moveToFirst()) {
                    VideoMeta(
                        durationMs = c.getLong(0),
                        width = c.getInt(1),
                        height = c.getInt(2),
                        bitrate = c.getLong(3),
                    )
                } else null
            }
        }.getOrNull()
    }

    fun getByPathAndSize(path: String, sizeBytes: Long): VideoMeta? = synchronized(lock) {
        runCatching {
            readableDatabase.rawQuery(
                "SELECT duration_ms, width, height, bitrate FROM video_meta WHERE path = ? AND (size_bytes = ? OR size_bytes = 0)",
                arrayOf(path, sizeBytes.toString()),
            ).use { c ->
                if (c.moveToFirst()) {
                    VideoMeta(
                        durationMs = c.getLong(0),
                        width = c.getInt(1),
                        height = c.getInt(2),
                        bitrate = c.getLong(3),
                    )
                } else null
            }
        }.getOrNull()
    }

    fun put(path: String, mtimeMs: Long, sizeBytes: Long, meta: VideoMeta) {
        synchronized(lock) {
            runCatching {
                val cv = ContentValues(7).apply {
                    put("path", path)
                    put("mtime_ms", mtimeMs)
                    put("size_bytes", sizeBytes)
                    put("duration_ms", meta.durationMs)
                    put("width", meta.width)
                    put("height", meta.height)
                    put("bitrate", meta.bitrate)
                }
                writableDatabase.insertWithOnConflict("video_meta", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
        }
    }

    fun putBatch(rows: List<CachedVideoMetaRow>) {
        if (rows.isEmpty()) return
        synchronized(lock) {
            runCatching {
                val db = writableDatabase
                db.beginTransaction()
                try {
                    val cv = ContentValues(7)
                    rows.forEach { row ->
                        cv.clear()
                        cv.put("path", row.path)
                        cv.put("mtime_ms", row.mtimeMs)
                        cv.put("size_bytes", row.sizeBytes)
                        cv.put("duration_ms", row.meta.durationMs)
                        cv.put("width", row.meta.width)
                        cv.put("height", row.meta.height)
                        cv.put("bitrate", row.meta.bitrate)
                        db.insertWithOnConflict("video_meta", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        }
    }

    fun invalidate(path: String) {
        synchronized(lock) {
            runCatching { writableDatabase.delete("video_meta", "path = ?", arrayOf(path)) }
        }
    }
}
