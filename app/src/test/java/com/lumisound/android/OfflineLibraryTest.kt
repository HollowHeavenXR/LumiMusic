package com.lumisound.android

import com.google.gson.JsonParser
import com.lumisound.android.data.db.CloudTrackEntity
import com.lumisound.android.download.OfflineStorage
import com.lumisound.android.download.TrackFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OfflineLibraryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun track(
        title: String = "Midnight City",
        artist: String = "M83",
        album: String = "Hurry Up, We're Dreaming",
        trackNumber: String = "3/22",
        ext: String = "m4a",
        isLocked: Boolean = false,
        serverPath: String = "M83/Hurry Up/03 Midnight City.m4a",
    ) = CloudTrackEntity(
        serverPath = serverPath, remoteId = "id", title = title, artist = artist, album = album,
        durationSeconds = 244.0, genre = "Electronic", trackNumber = trackNumber, hasArtwork = false,
        isLocked = isLocked, ext = ext, filename = serverPath.substringAfterLast('/'), bpm = 105.0,
        uploadedAt = "2026-09-01T10:00:00", sampleRate = 44_100, syncedAt = 0L,
    )

    // --- Names -------------------------------------------------------------------------

    @Test
    fun `tracks are filed as artist, album, numbered title`() {
        val t = track()
        assertEquals(listOf("M83", "Hurry Up, We're Dreaming"), TrackFiles.folders(t))
        assertEquals("03 Midnight City", TrackFiles.baseName(t))
        assertEquals("m4a", TrackFiles.extension(t))
    }

    @Test
    fun `missing tags fall back to readable names`() {
        val t = track(title = "", artist = " ", album = "", trackNumber = "", serverPath = "uploads/demo take.flac", ext = "")
        assertEquals(listOf(TrackFiles.UNKNOWN_ARTIST, TrackFiles.UNKNOWN_ALBUM), TrackFiles.folders(t))
        assertEquals("demo take", TrackFiles.baseName(t))
        assertEquals("flac", TrackFiles.extension(t))
    }

    @Test
    fun `a locked track is always saved as lms`() {
        assertEquals("lms", TrackFiles.extension(track(isLocked = true, ext = "m4a")))
    }

    @Test
    fun `names any filesystem accepts`() {
        assertEquals("AC_DC", TrackFiles.clean("AC/DC"))
        assertEquals("What_ Why_", TrackFiles.clean("What? Why*"))
        assertEquals("_", TrackFiles.clean(" ... "))
        assertEquals("Trailing", TrackFiles.clean("Trailing..."))
        assertTrue(TrackFiles.clean("x".repeat(300)).length <= 80)
    }

    @Test
    fun `a clash gets a short stable tag`() {
        val tag = TrackFiles.shortHash("a/b.m4a")
        assertEquals(6, tag.length)
        assertEquals(tag, TrackFiles.shortHash("a/b.m4a"))
        assertEquals("03 Midnight City [$tag]", TrackFiles.baseName(track(), tag))
    }

    // --- Metadata ---------------------------------------------------------------------

    @Test
    fun `the sidecar carries every field and says a locked track is locked`() {
        val json = JsonParser.parseString(
            TrackFiles.sidecarJson(track(isLocked = true), "03 Midnight City.jpg", "2026-09-30T00:00:00Z", "0.8.0")
        ).asJsonObject
        assertEquals("Midnight City", json["title"].asString)
        assertEquals("M83", json["artist"].asString)
        assertEquals("3/22", json["track_number"].asString)
        assertEquals(105.0, json["bpm"].asDouble, 0.0)
        assertEquals("lms", json["format"].asString)
        assertTrue(json["locked"].asBoolean)
        assertTrue(json["lock_note"].asString.contains("LMSLOCK1"))
        assertEquals("03 Midnight City.jpg", json["artwork"].asString)
        assertEquals("LumiMusic 0.8.0", json["saved_by"].asString)
    }

    @Test
    fun `the sidecar leaves out what the library doesn't know`() {
        val json = JsonParser.parseString(TrackFiles.sidecarJson(track(trackNumber = ""), null, "now", "0.8.0")).asJsonObject
        assertFalse(json.has("track_number"))
        assertFalse(json.has("artwork"))
        assertFalse(json.has("lock_note"))
    }

    // --- Phone folder -----------------------------------------------------------------

    @Test
    fun `a saved file lands whole, with no part file left behind`() {
        val storage = OfflineStorage.PhoneFolder(temp.root)
        val location = storage.save(listOf("M83", "Album"), "03 Song.m4a", "audio/mp4") { it.write(ByteArray(1000) { 7 }) }
        assertTrue(storage.exists(location))
        assertEquals(location, storage.find(listOf("M83", "Album"), "03 Song.m4a"))
        assertEquals(1000L, java.io.File(location).length())
        assertEquals(listOf("03 Song.m4a"), java.io.File(temp.root, "M83/Album").list()!!.toList())
        assertTrue(storage.delete(location))
        assertNull(storage.find(listOf("M83", "Album"), "03 Song.m4a"))
    }

    @Test
    fun `a failed write leaves nothing that looks finished`() {
        val storage = OfflineStorage.PhoneFolder(temp.root)
        runCatching {
            storage.save(listOf("A"), "x.lms", "application/octet-stream") { out ->
                out.write(ByteArray(10))
                throw java.io.IOException("connection reset")
            }
        }
        assertNull(storage.find(listOf("A"), "x.lms"))
        assertEquals(0, java.io.File(temp.root, "A").list()!!.size)
    }
}
