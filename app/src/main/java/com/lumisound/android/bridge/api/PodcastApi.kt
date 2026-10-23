package com.lumisound.android.bridge.api

import com.lumisound.android.bridge.model.EpisodeProgressDto
import com.lumisound.android.bridge.model.EpisodeProgressRequest
import com.lumisound.android.bridge.model.PodcastChapterDto
import com.lumisound.android.bridge.model.PodcastEpisodeDto
import com.lumisound.android.bridge.model.PodcastShowDto
import com.lumisound.android.bridge.model.PodcastSubscribeRequest
import com.lumisound.android.bridge.model.PodcastSubscriptionDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Podcasts, entirely through the bridge: it searches Apple's directory, fetches and
 * parses the RSS, and stores subscriptions and per-episode progress on the account.
 * The app only ever streams the episode's own enclosure URL.
 */
interface PodcastApi {
    @GET("podcasts/search")
    suspend fun search(@Query("q") query: String, @Query("limit") limit: Int = 20): List<PodcastShowDto>

    /** Apple's chart, minus shows this account already subscribes to. */
    @GET("podcasts/trending")
    suspend fun trending(@Query("limit") limit: Int = 20): List<PodcastShowDto>

    @GET("user/podcasts/subscriptions")
    suspend fun subscriptions(): List<PodcastSubscriptionDto>

    @POST("user/podcasts/subscriptions")
    suspend fun subscribe(@Body body: PodcastSubscribeRequest): PodcastSubscriptionDto

    @DELETE("user/podcasts/subscriptions/{id}")
    suspend fun unsubscribe(@Path("id") subscriptionId: String)

    @GET("user/podcasts/episodes")
    suspend fun episodes(@Query("feed_url") feedUrl: String, @Query("limit") limit: Int = 100): List<PodcastEpisodeDto>

    /**
     * With [feedUrl], every tracked episode of that show; without it, the in-progress
     * episodes across all shows -- the Continue Listening shelf.
     */
    @GET("user/podcasts/episode-progress")
    suspend fun progress(@Query("feed_url") feedUrl: String? = null, @Query("limit") limit: Int = 20): List<EpisodeProgressDto>

    @PUT("user/podcasts/episode-progress")
    suspend fun saveProgress(@Body body: EpisodeProgressRequest)

    /** On-demand, not inlined into [episodes] -- most episodes never get this opened. */
    @GET("user/podcasts/chapters")
    suspend fun chapters(@Query("chapters_url") chaptersUrl: String): List<PodcastChapterDto>
}
