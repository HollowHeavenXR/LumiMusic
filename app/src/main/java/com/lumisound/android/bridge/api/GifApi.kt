package com.lumisound.android.bridge.api

import com.lumisound.android.bridge.model.GifSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * The bridge's GIPHY proxy -- the key lives server-side, never in the app
 * bundle. Same endpoints iOS's `GifSearchService` uses for avatar/banner
 * GIF picking.
 */
interface GifApi {
    @GET("api/gif-search")
    suspend fun search(
        @Query("q") query: String,
        @Query("limit") limit: Int = 30,
        @Query("offset") offset: Int = 0,
    ): GifSearchResponse

    @GET("api/gif-search/trending")
    suspend fun trending(
        @Query("limit") limit: Int = 30,
        @Query("offset") offset: Int = 0,
    ): GifSearchResponse
}
