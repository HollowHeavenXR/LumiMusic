package com.lumisound.android.bridge.api

import com.lumisound.android.bridge.model.AcousticDuplicatesResponse
import com.lumisound.android.bridge.model.CloudMusicResponse
import com.lumisound.android.bridge.model.CloudSearchResponse
import com.lumisound.android.bridge.model.MusicUploadResponse
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The signed-in user's personal cloud music library -- the same
 * `/user/music` endpoint family Lumisound uploads to and streams from, so a track
 * uploaded from an iPhone shows up here with no migration step at all.
 *
 * Streaming and artwork are deliberately NOT modeled as Retrofit calls: both
 * are handed to ExoPlayer/Coil as URLs so they can be range-requested and
 * cached by the player itself. See `BridgeUrls`.
 */
interface CloudMusicApi {
    @GET("user/music")
    suspend fun listTracks(
        @Query("search") search: String = "",
        @Query("limit") limit: Int = 5000,
    ): CloudMusicResponse

    @GET("user/music/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("limit") limit: Int = 50,
    ): CloudSearchResponse

    /**
     * Raw audio bytes as the body, destination/tag metadata as query params. Max 100MB.
     * Not an unlocked file's `sample_rate`/`bitrate`/etc -- this endpoint doesn't ffprobe
     * the upload itself, but [listTracks] does (for every unlocked file, on every call),
     * so those fill in on their own by the time anything reads this track back.
     */
    @POST("user/music/upload")
    suspend fun uploadMusic(
        @Query("filename") filename: String,
        @Query("title") title: String?,
        @Query("artist") artist: String?,
        @Query("duration") durationSeconds: Double?,
        @Body body: RequestBody,
    ): MusicUploadResponse

    /**
     * Groups files that are the same recording acoustically, regardless of
     * metadata -- Chromaprint fingerprint match, server-side. Serves a
     * cached result from a periodic background scan unless [forceRescan].
     * Each group member is a bare filename, not a full relative path --
     * ambiguous if the same name exists in two different folders, a
     * pre-existing limitation this shares with iOS's own duplicate finder,
     * not something new here.
     */
    @GET("user/library/acoustic-duplicates")
    suspend fun acousticDuplicates(@Query("force_rescan") forceRescan: Boolean = false): AcousticDuplicatesResponse

    /**
     * [filepath] is the relative path with each segment already percent-encoded
     * individually (not the whole string) and slashes left bare -- the route is
     * FastAPI's `{filepath:path}` converter, which expects literal `/` between
     * segments; `encoded = true` stops Retrofit from re-encoding them away.
     */
    @DELETE("user/music/{filepath}")
    suspend fun deleteTrack(@Path(value = "filepath", encoded = true) filepath: String)
}
