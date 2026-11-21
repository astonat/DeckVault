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
    SQLiteOpenHelper(context.applicationContext, "deckvault_meta_cache.db", null, 2) {

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
        ensureSnapshotTables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            ensureSnapshotTables(db)
        }
    }

    private fun ensureSnapshotTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS folder_snapshots (
                cache_key TEXT PRIMARY KEY,
                host_id TEXT NOT NULL,
                payload_json TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tree_snapshots (
                cache_key TEXT PRIMARY KEY,
                host_id TEXT NOT NULL,
                payload_json TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS roots_snapshots (
                host_id TEXT PRIMARY KEY,
                payload_json TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

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

    fun getFolderSnapshot(cacheKey: String): String? = synchronized(lock) {
        runCatching {
            readableDatabase.rawQuery(
                "SELECT payload_json FROM folder_snapshots WHERE cache_key = ?",
                arrayOf(cacheKey),
            ).use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
    }

    fun putFolderSnapshot(cacheKey: String, hostId: String, payloadJson: String) {
        synchronized(lock) {
            runCatching {
                val cv = ContentValues(4).apply {
                    put("cache_key", cacheKey)
                    put("host_id", hostId)
                    put("payload_json", payloadJson)
                    put("updated_at", System.currentTimeMillis())
                }
                val db = writableDatabase
                db.insertWithOnConflict("folder_snapshots", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                db.execSQL(
                    "DELETE FROM folder_snapshots WHERE cache_key NOT IN (SELECT cache_key FROM folder_snapshots ORDER BY updated_at DESC LIMIT 120)",
                )
            }
        }
    }

    fun getTreeSnapshot(cacheKey: String): String? = synchronized(lock) {
        runCatching {
            readableDatabase.rawQuery(
                "SELECT payload_json FROM tree_snapshots WHERE cache_key = ?",
                arrayOf(cacheKey),
            ).use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
    }

    fun putTreeSnapshot(cacheKey: String, hostId: String, payloadJson: String) {
        synchronized(lock) {
            runCatching {
                val cv = ContentValues(4).apply {
                    put("cache_key", cacheKey)
                    put("host_id", hostId)
                    put("payload_json", payloadJson)
                    put("updated_at", System.currentTimeMillis())
                }
                val db = writableDatabase
                db.insertWithOnConflict("tree_snapshots", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                db.execSQL(
                    "DELETE FROM tree_snapshots WHERE cache_key NOT IN (SELECT cache_key FROM tree_snapshots ORDER BY updated_at DESC LIMIT 120)",
                )
            }
        }
    }

    fun getRootsSnapshot(hostId: String): String? = synchronized(lock) {
        runCatching {
            readableDatabase.rawQuery(
                "SELECT payload_json FROM roots_snapshots WHERE host_id = ?",
                arrayOf(hostId),
            ).use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
    }

    fun putRootsSnapshot(hostId: String, payloadJson: String) {
        synchronized(lock) {
            runCatching {
                val cv = ContentValues(3).apply {
                    put("host_id", hostId)
                    put("payload_json", payloadJson)
                    put("updated_at", System.currentTimeMillis())
                }
                writableDatabase.insertWithOnConflict("roots_snapshots", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
        }
    }

    fun clearSnapshotsForHost(hostId: String?) {
        synchronized(lock) {
            runCatching {
                val db = writableDatabase
                if (hostId.isNullOrBlank()) {
                    db.delete("folder_snapshots", null, null)
                    db.delete("tree_snapshots", null, null)
                    db.delete("roots_snapshots", null, null)
                } else {
                    db.delete("folder_snapshots", "host_id = ?", arrayOf(hostId))
                    db.delete("tree_snapshots", "host_id = ?", arrayOf(hostId))
                    db.delete("roots_snapshots", "host_id = ?", arrayOf(hostId))
                }
            }
        }
    }
}
