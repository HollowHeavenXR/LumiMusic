package com.lumisound.android.ui

import com.lumisound.android.AppContainer
import com.lumisound.android.bridge.BridgeUrls
import com.lumisound.android.bridge.model.DownloadStatusResponse
import com.lumisound.android.bridge.model.StreamTrackDto
import com.lumisound.android.cloud.ImportStage
import com.lumisound.android.diagnostics.AppLogger
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** What came of trying to save a search result into the account's cloud library. */
sealed interface SaveStreamResult {
    data object Saved : SaveStreamResult
    /** The bridge already had this track (by source:id) and skipped the work entirely. */
    data object AlreadyOwned : SaveStreamResult
    data class Failed(val message: String) : SaveStreamResult
}

private val CONTENT_TYPE_EXTENSIONS = mapOf(
    "audio/mpeg" to "mp3",
    "audio/mp4" to "m4a",
    "audio/flac" to "flac",
    "audio/ogg" to "opus",
    "audio/wav" to "wav",
    "audio/webm" to "webm",
)

/**
 * Runs a YouTube/SoundCloud search result through the same download+transcode job
 * iOS uses (`GET /api/download` -> poll `/api/download/status` -> fetch
 * `/api/download/result`), then uploads the result into the account's cloud
 * library (`POST /user/music/upload`) and refreshes the local mirror so it shows
 * up immediately. There was previously no way to do this on Android at all --
 * search results could only be streamed transiently, never kept.
 */
suspend fun AppContainer.saveStreamToLibrary(track: StreamTrackDto): SaveStreamResult {
    val format = try {
        http.libraryData.settings().preferredAudioFormat ?: "m4a"
    } catch (e: Exception) {
        "m4a"
    }

    val started = try {
        http.streaming.startDownload(
            id = track.id,
            source = track.source,
            url = track.youtubeUrl,
            format = format,
            title = track.title,
            fullTitle = track.title,
            artist = track.artist,
            thumbnail = track.thumbnailUrl,
            duration = track.durationSeconds.takeIf { it > 0 },
        )
    } catch (e: Exception) {
        AppLogger.w("save", "startDownload failed: ${e.javaClass.simpleName}")
        return SaveStreamResult.Failed("Could not reach the bridge: ${e.message ?: e.javaClass.simpleName}")
    }

    if (started.code() == 204) return SaveStreamResult.AlreadyOwned
    val jobId = started.body()?.jobId
    if (!started.isSuccessful || jobId == null) {
        return SaveStreamResult.Failed("The server rejected that (HTTP ${started.code()}).")
    }

    // Polled rather than pushed: the job itself has no completion callback, and
    // a single track transcode has historically finished in single-digit
    // seconds (see the ios-bridge "download_track complete... elapsed=4.2s"
    // logging) -- 90 tries at 1.5s is a generous ceiling for a slow/throttled
    // one without hanging forever on a job that will never finish.
    var status = DownloadStatusResponse()
    var attempts = 0
    while (attempts < 90) {
        status = try {
            http.streaming.downloadStatus(jobId)
        } catch (e: Exception) {
            return SaveStreamResult.Failed("Lost contact with the bridge mid-download.")
        }
        if (status.status != "pending") break
        attempts++
        delay(1500)
    }
    if (status.status == "pending") return SaveStreamResult.Failed("Download timed out.")
    if (status.status == "error") return SaveStreamResult.Failed(status.detail ?: "Download failed.")

    val resultUrl = BridgeUrls.downloadResult(config.baseUrl, jobId)
    val (bytes, contentType) = try {
        http.client.newCall(Request.Builder().url(resultUrl).build()).execute().use { response ->
            if (!response.isSuccessful) return SaveStreamResult.Failed("HTTP ${response.code} fetching the result.")
            val body = response.body ?: return SaveStreamResult.Failed("Empty response fetching the result.")
            body.bytes() to response.header("Content-Type")
        }
    } catch (e: Exception) {
        return SaveStreamResult.Failed("Could not fetch the downloaded file: ${e.message ?: e.javaClass.simpleName}")
    }

    val ext = CONTENT_TYPE_EXTENSIONS[contentType?.substringBefore(";")?.trim()] ?: format
    val safeTitle = track.title.ifBlank { track.id }.replace(Regex("""[/\\:*?"<>|]"""), "_").take(120)

    try {
        http.cloudMusic.uploadMusic(
            filename = "$safeTitle.$ext",
            title = track.title.ifBlank { null },
            artist = track.artist.ifBlank { null },
            durationSeconds = track.durationSeconds.takeIf { it > 0 }?.toDouble(),
            body = bytes.toRequestBody(contentType?.toMediaType()),
        )
    } catch (e: Exception) {
        return SaveStreamResult.Failed("Downloaded, but the upload failed: ${e.message ?: e.javaClass.simpleName}")
    }

    AppLogger.i("save", "saved stream to library", mapOf("source" to track.source, "id" to track.id))
    try {
        cloudImport.import(setOf(ImportStage.CloudTracks))
    } catch (e: Exception) {
        // The upload itself already succeeded; a failed refresh just means the
        // next natural sync picks it up instead of it appearing instantly.
    }
    return SaveStreamResult.Saved
}
