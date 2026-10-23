package com.lumisound.android.bridge.model

import com.google.gson.annotations.SerializedName

data class FavoriteDto(
    @SerializedName("song_id") val songId: String = "",
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    @SerializedName("added_at") val addedAt: String? = null,
)

data class AddFavoriteRequest(
    @SerializedName("song_id") val songId: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
)

data class PlaylistTrackDto(
    val id: String? = null,
    @SerializedName("track_url") val trackUrl: String? = null,
    @SerializedName("local_song_id") val localSongId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    @SerializedName("duration_seconds") val durationSeconds: Double? = null,
    val position: Int? = null,
)

data class PlaylistDto(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    val folder: String? = null,
    val tags: List<String> = emptyList(),
    val tracks: List<PlaylistTrackDto> = emptyList(),
)

data class CreatePlaylistRequest(
    val name: String,
    val description: String? = null,
    val folder: String? = null,
    val tags: List<String> = emptyList(),
)

/** Every field is preserve-if-null server-side, so a partial update is safe. */
data class UpdatePlaylistRequest(
    val name: String? = null,
    val description: String? = null,
    val folder: String? = null,
    val tags: List<String>? = null,
)

/** The bridge's `SyncTrack`: `title` is the only required field. */
data class PlaylistTrackRequest(
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    @SerializedName("local_song_id") val localSongId: String? = null,
    @SerializedName("track_url") val trackUrl: String? = null,
    @SerializedName("duration_seconds") val durationSeconds: Int? = 0,
    val position: Int? = 0,
)

data class HistoryEntryDto(
    val id: String? = null,
    @SerializedName("track_url") val trackUrl: String? = null,
    @SerializedName("local_song_id") val localSongId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    @SerializedName("played_at") val playedAt: String? = null,
    @SerializedName("listen_seconds") val listenSeconds: Double? = null,
)

/**
 * `POST /user/history`. The bridge fans this one call out to scrobbling
 * (Last.fm/Libre.fm/ListenBrainz), the Discord "now playing" webhook and the
 * achievements/stats aggregates, so logging a play is the single piece of
 * plumbing several server-side features hang off.
 */
data class LogPlayRequest(
    /** Required server-side -- `LogPlayRequest.title` has no default in main.py. */
    val title: String,
    val artist: String? = null,
    @SerializedName("track_url") val trackUrl: String? = null,
    @SerializedName("local_song_id") val localSongId: String? = null,
    @SerializedName("listen_seconds") val listenSeconds: Int = 0,
    val bpm: Double? = null,
)

/** `GET`/`PUT /user/settings` -- the small cross-platform settings row. */
data class UserSettingsDto(
    @SerializedName("audio_settings_json") val audioSettingsJson: String? = null,
    @SerializedName("track_audio_settings_json") val trackAudioSettingsJson: String? = null,
    @SerializedName("theme_color") val themeColor: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    /** "m4a"/"mp3"/"flac"/"opus"/"best" -- what format a new download (search -> save) is transcoded to. */
    @SerializedName("preferred_audio_format") val preferredAudioFormat: String? = null,
)

data class UpdateSettingsRequest(
    @SerializedName("audio_settings_json") val audioSettingsJson: String? = null,
    @SerializedName("track_audio_settings_json") val trackAudioSettingsJson: String? = null,
    @SerializedName("theme_color") val themeColor: String? = null,
    @SerializedName("preferred_audio_format") val preferredAudioFormat: String? = null,
)
