package com.lumisound.android.bridge.model

import com.google.gson.annotations.SerializedName

/** A show from `/podcasts/search` or `/podcasts/trending` (Apple's public directory). */
data class PodcastShowDto(
    val title: String? = null,
    val artist: String? = null,
    @SerializedName("feed_url") val feedUrl: String = "",
    @SerializedName("artwork_url") val artworkUrl: String? = null,
)

data class PodcastSubscriptionDto(
    val id: String = "",
    @SerializedName("feed_url") val feedUrl: String = "",
    val title: String? = null,
    @SerializedName("artwork_url") val artworkUrl: String? = null,
    @SerializedName("added_at") val addedAt: String? = null,
    @SerializedName("notifications_muted") val notificationsMuted: Boolean = false,
)

data class PodcastSubscribeRequest(@SerializedName("feed_url") val feedUrl: String)

/** One `<item>` of a feed, parsed server-side so the app never fetches RSS itself. */
data class PodcastEpisodeDto(
    val guid: String? = null,
    val title: String = "",
    val description: String = "",
    @SerializedName("audio_url") val audioUrl: String? = null,
    @SerializedName("duration_seconds") val durationSeconds: Int? = null,
    @SerializedName("published_at") val publishedAt: String? = null,
    @SerializedName("chapters_url") val chaptersUrl: String? = null,
)

/**
 * `GET`/`PUT /user/podcasts/episode-progress`. Shared with iOS, so an episode started on
 * the phone resumes at the same second here and vice versa.
 */
data class EpisodeProgressDto(
    @SerializedName("episode_guid") val episodeGuid: String = "",
    @SerializedName("feed_url") val feedUrl: String = "",
    val title: String? = null,
    @SerializedName("position_seconds") val positionSeconds: Double = 0.0,
    @SerializedName("duration_seconds") val durationSeconds: Double = 0.0,
    val completed: Boolean = false,
    @SerializedName("updated_at") val updatedAt: String? = null,
)

/** `GET /user/podcasts/chapters` -- the bridge fetches+reshapes the episode's Podcasting 2.0 chapters file. */
data class PodcastChapterDto(
    @SerializedName("start_time_seconds") val startTimeSeconds: Double = 0.0,
    val title: String = "",
    @SerializedName("image_url") val imageUrl: String? = null,
)

data class EpisodeProgressRequest(
    @SerializedName("feed_url") val feedUrl: String,
    @SerializedName("episode_guid") val episodeGuid: String,
    val title: String? = null,
    @SerializedName("position_seconds") val positionSeconds: Double = 0.0,
    @SerializedName("duration_seconds") val durationSeconds: Double = 0.0,
    val completed: Boolean = false,
)
