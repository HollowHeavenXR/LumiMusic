package com.lumisound.android.bridge.model

import com.google.gson.annotations.SerializedName

/**
 * The bridge's `StreamTrack` -- what `_parse_track` builds from a yt-dlp entry, and the
 * shape every "here is something to stream" route shares: search, radio, discover mix,
 * on-this-day and Aria's daily pick all return this.
 *
 * `youtube_url` is the canonical page URL for any source, SoundCloud included; it is what
 * a play is logged against (`track_url`) and what `/api/stream/proxy` needs as `url=` for
 * anything that is not YouTube.
 */
data class StreamTrackDto(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    @SerializedName("duration_seconds") val durationSeconds: Int = 0,
    @SerializedName("thumbnail_url") val thumbnailUrl: String? = null,
    val source: String = "youtube",
    @SerializedName("youtube_url") val youtubeUrl: String? = null,
    @SerializedName("is_topic_channel") val isTopicChannel: Boolean = false,
)

/** `GET /api/search/trending` and `/api/search/suggestions`: past queries by popularity. */
data class SearchQueryDto(
    val query: String = "",
    val count: Int = 0,
)

/** `GET /api/download`'s 202 body -- poll [DownloadStatusResponse] with this. */
data class DownloadStartResponse(
    @SerializedName("job_id") val jobId: String = "",
)

/**
 * `GET /api/download/status`. `status` is "pending", "done", or "error" --
 * [code]/[detail]/[permanent] are only populated for "error" (see the
 * bridge's `download_status`: [permanent] is true only when the video
 * itself is gone/private, as opposed to a transient bot-wall/network issue
 * worth retrying).
 */
data class DownloadStatusResponse(
    val status: String = "pending",
    val code: Int? = null,
    val detail: String? = null,
    val permanent: Boolean? = null,
)
