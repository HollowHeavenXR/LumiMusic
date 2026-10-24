package com.lumisound.android.bridge.api

import com.lumisound.android.bridge.model.StemJobResponse
import com.lumisound.android.bridge.model.StemStatusResponse
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Real server-side AI source separation (Demucs) -- a track's own audio
 * bytes in, a vocals-only and an instrumental-only render out, a minute or
 * more later. Distinct from any on-device EQ-notch "karaoke" approximation
 * (none exists in this app).
 *
 * Always the raw-upload path, never `video_id`: that one only works for a
 * YouTube-sourced track, and the cloud library's own `/user/music` listing
 * doesn't expose a track's source platform/video id at all (unlike the
 * locked-file tag iOS can sometimes read locally) -- uploading this
 * track's own stream bytes works uniformly for every cloud track
 * regardless of origin.
 */
interface StemSeparationApi {
    @POST("api/stem-separation")
    suspend fun start(@Query("title") title: String, @Body body: RequestBody): StemJobResponse

    @GET("api/stem-separation/status")
    suspend fun status(@Query("job_id") jobId: String): StemStatusResponse
}
