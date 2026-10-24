package com.lumisound.android.library

import android.content.Context
import com.lumisound.android.AppContainer
import com.lumisound.android.bridge.BridgeUrls
import com.lumisound.android.bridge.model.StemStatusResponse
import com.lumisound.android.playback.LumisoundLockMask
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

sealed interface StemSeparationState {
    data object Idle : StemSeparationState
    data object Uploading : StemSeparationState
    data object Separating : StemSeparationState
    data class Done(val vocals: File, val instrumental: File) : StemSeparationState
    data class Failed(val message: String) : StemSeparationState
}

/**
 * One separation attempt -- a fresh instance per track, not an AppContainer
 * singleton, matching iOS's own `StemSeparationService` lifetime (one per
 * sheet, reset on dismiss). Real server-side AI source separation (Demucs),
 * routinely a minute or more.
 *
 * Always uploads this track's own stream bytes rather than passing a
 * `video_id` the bridge could fetch itself: the cloud library's `/user/music`
 * listing doesn't expose a track's source platform/video id to clients at
 * all (iOS gets it from a locked-file tag it sometimes has locally, which
 * has no equivalent here), so the raw-upload path is the only one that
 * works uniformly for every cloud track regardless of origin.
 */
class StemSeparationSession(private val context: Context, private val container: AppContainer) {
    private val _state = MutableStateFlow<StemSeparationState>(StemSeparationState.Idle)
    val state: StateFlow<StemSeparationState> = _state.asStateFlow()

    suspend fun start(serverPath: String, title: String, isLocked: Boolean) {
        _state.value = StemSeparationState.Uploading
        try {
            // isLocked=false even for a locked track: the server-side route this
            // flag otherwise affects is irrelevant to a plain bytes fetch, and the
            // unmasking below is done here instead, once, on the whole file --
            // not through ExoPlayer's streaming LumisoundLockDataSource.
            val streamUrl = BridgeUrls.stream(container.config.baseUrl, serverPath, isLocked = false)
            var bytes = withContext(Dispatchers.IO) {
                container.http.client.newCall(Request.Builder().url(streamUrl).build()).execute().use { response ->
                    if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code} fetching the track")
                    response.body?.bytes() ?: throw IllegalStateException("Empty response fetching the track")
                }
            }
            if (isLocked && LumisoundLockMask.hasMagic(bytes)) {
                val payload = bytes.copyOfRange(LumisoundLockMask.MAGIC.size, bytes.size)
                LumisoundLockMask.unmaskInPlace(payload, 0, payload.size, 0L)
                bytes = payload
            }
            // Matches the bridge's own 60MB cap on this endpoint -- fail fast with a
            // clear reason instead of a generic 400 after already uploading nothing.
            if (bytes.size > 60 * 1024 * 1024) {
                throw IllegalStateException("This track is too large for separation (over 60MB).")
            }
            val jobId = withContext(Dispatchers.IO) {
                container.http.stemSeparation.start(title, bytes.toRequestBody("audio/mp4".toMediaType())).jobId
            }
            _state.value = StemSeparationState.Separating

            var status = StemStatusResponse()
            var attempts = 0
            // ~6 minutes at 2s/poll -- Demucs on a real track has run a minute or
            // more in practice; generous ceiling for a slow one rather than a tight
            // timeout that cuts off a job that would have finished.
            while (attempts < 180) {
                status = withContext(Dispatchers.IO) { container.http.stemSeparation.status(jobId) }
                if (status.status != "pending" && status.status != "running") break
                attempts++
                delay(2000)
            }
            if (status.status == "pending" || status.status == "running") {
                throw IllegalStateException("Separation is taking longer than expected -- try again shortly.")
            }
            if (status.status == "error") {
                throw IllegalStateException(status.error ?: "Separation failed.")
            }

            val dir = File(context.cacheDir, "stems").apply { mkdirs() }
            val vocals = downloadStem(jobId, "vocals", File(dir, "$jobId-vocals.m4a"))
            val instrumental = downloadStem(jobId, "instrumental", File(dir, "$jobId-instrumental.m4a"))
            _state.value = StemSeparationState.Done(vocals, instrumental)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = StemSeparationState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private suspend fun downloadStem(jobId: String, stem: String, dest: File): File = withContext(Dispatchers.IO) {
        val url = BridgeUrls.stemResult(container.config.baseUrl, jobId, stem)
        container.http.client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code} fetching the $stem stem")
            val body = response.body ?: throw IllegalStateException("Empty response fetching the $stem stem")
            dest.outputStream().use { out -> body.byteStream().copyTo(out) }
        }
        dest
    }

    fun reset() {
        _state.value = StemSeparationState.Idle
    }
}
