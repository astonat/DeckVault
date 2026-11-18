package com.deckvault.app

import com.deckvault.app.model.AccentPalette
import com.deckvault.app.model.CompressEstimateRequest
import com.deckvault.app.model.CompressRequest
import com.deckvault.app.model.CropBoxSpec
import com.deckvault.app.model.EditStudioRequest
import com.deckvault.app.model.JobsSnapshotResponse
import com.deckvault.app.model.OffloadStageResponse
import com.deckvault.app.model.SavedHost
import com.deckvault.app.model.ThemeSurfaceStyle
import com.deckvault.app.model.UpscaleRequest
import com.deckvault.app.model.UserPreferences
import com.deckvault.app.ui.util.Formatters
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckVaultModelsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    @Test
    fun localHostIdentityFlagsWorkCorrectly() {
        val local = SavedHost.localDevice(freeBytes = 1024L, totalBytes = 4096L)
        val pc = SavedHost(hostId = "win-1", name = "Desktop", ip = "192.168.1.50", token = "abc")
        assertTrue(local.isLocalDevice)
        assertFalse(pc.isLocalDevice)
        assertEquals("This Device", local.displayName)

        // Verify alternateIps failover + backward-compatible JSON deserialization
        val legacyJson = """{"hostId":"phone-1","name":"Fold 5","ip":"192.168.1.40","token":"tok"}"""
        val decoded = json.decodeFromString(SavedHost.serializer(), legacyJson)
        assertEquals(emptyList<String>(), decoded.alternateIps)
        val healed = decoded.withPrimaryIp("192.168.1.99", listOf("192.168.1.40", "127.0.0.1"))
        assertEquals("192.168.1.99", healed.ip)
        assertEquals(listOf("192.168.1.99", "192.168.1.40"), healed.candidateIps())
    }

    @Test
    fun studioAndOffloadModelsRoundTripJson() {
        val editReq = EditStudioRequest(
            path = "/storage/emulated/0/Movies/clip.mp4",
            startMs = 1500L,
            endMs = 9500L,
            crop = CropBoxSpec(0.1f, 0.2f, 0.8f, 0.6f),
            rotate = 90,
            flipH = true,
            speed = 1.5,
            brightness = 0.1f,
            contrast = 1.2f,
            saturation = 1.3f,
        )
        val encoded = json.encodeToString(EditStudioRequest.serializer(), editReq)
        val decoded = json.decodeFromString(EditStudioRequest.serializer(), encoded)
        assertEquals(editReq, decoded)

        val compReq = CompressRequest(path = "a.mp4", targetHeight = 720, crf = 26, codec = "hevc", targetMb = 25.0)
        val compDec = json.decodeFromString(CompressRequest.serializer(), json.encodeToString(CompressRequest.serializer(), compReq))
        assertEquals(compReq, compDec)

        val upReq = UpscaleRequest(path = "a.mp4", preset = "4k", mode = "restore", denoiseMode = "hq", fps60 = true)
        val upDec = json.decodeFromString(UpscaleRequest.serializer(), json.encodeToString(UpscaleRequest.serializer(), upReq))
        assertEquals(upReq, upDec)

        val estReq = CompressEstimateRequest(path = "a.mp4", targetMb = 15.0)
        val estDec = json.decodeFromString(CompressEstimateRequest.serializer(), json.encodeToString(CompressEstimateRequest.serializer(), estReq))
        assertEquals(estReq, estDec)

        val stageJson = """{"ok":true,"stageId":"mob_123","stagedName":"sample.mp4","path":"C:\\Users\\STO\\.deckvault\\offload_staging\\mob_123\\sample.mp4","sizeBytes":2048}"""
        val stage = json.decodeFromString(OffloadStageResponse.serializer(), stageJson)
        assertTrue(stage.ok)
        assertEquals("mob_123", stage.stageId)

        val jobsJson = """{"jobs":[{"id":"job_1","type":"compress","title":"Compress: sample.mp4","status":"running","progressPct":42.5}],"runningCount":1,"queuedCount":0,"activeCount":1,"overallPct":42.5}"""
        val snap = json.decodeFromString(JobsSnapshotResponse.serializer(), jobsJson)
        assertEquals(1, snap.activeCount)
        assertEquals("job_1", snap.jobs.first().id)
    }

    @Test
    fun formattersProduceExpectedStrings() {
        assertEquals("1.00 KB", Formatters.formatBytes(1024L))
        assertEquals("01:05", Formatters.formatDuration(65_000L))
        assertEquals("4K", Formatters.formatResolution(3840, 2160))
    }

    @Test
    fun materialThemesAndPreferencesRoundTripBackwardsCompatibly() {
        val legacyPrefsJson = """{"accentPalette":"MATTE_SLATE","pureBlackBackground":true,"gridColumns":4}"""
        val decodedLegacy = json.decodeFromString(UserPreferences.serializer(), legacyPrefsJson)
        assertEquals(AccentPalette.MATTE_SLATE, decodedLegacy.accentPalette)
        assertEquals(ThemeSurfaceStyle.HYBRID_OLED, decodedLegacy.surfaceStyle)
        assertEquals(4, decodedLegacy.gridColumns)

        val richPrefs = UserPreferences(
            accentPalette = AccentPalette.COBALT,
            surfaceStyle = ThemeSurfaceStyle.OLED_NEON,
            pureBlackBackground = true,
        )
        val roundTripped = json.decodeFromString(
            UserPreferences.serializer(),
            json.encodeToString(UserPreferences.serializer(), richPrefs),
        )
        assertEquals(richPrefs, roundTripped)
        assertTrue(AccentPalette.entries.size >= 11)
        assertEquals(3, ThemeSurfaceStyle.entries.size)
    }
}
