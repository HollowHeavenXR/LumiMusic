package com.lumisound.android.bridge.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * `GET /api/artist/image` -- a resolved photo for artist rows/grids (Deezer,
 * falling back to Wikipedia/YouTube server-side). Cached indefinitely on the
 * bridge once found; a name nobody's asked about yet resolves on this same
 * request rather than waiting on the background backfill job.
 */
data class ArtistImageResponse(
    val found: Boolean = false,
    @SerializedName("image_url") val imageUrl: String? = null,
    val source: String? = null,
)

interface ArtistApi {
    @GET("api/artist/image")
    suspend fun image(@Query("name") name: String): ArtistImageResponse
}
