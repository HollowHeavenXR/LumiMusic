package com.lumisound.android.bridge.model

import com.google.gson.annotations.SerializedName

/**
 * One track in the signed-in user's personal server music directory
 * (`GET /user/music`). The server derives most of this from ffprobe for plain
 * files and from `ios_user_music_metadata` for Lumisound-locked (`.lms`) ones,
 * which it cannot probe -- see [isLocked].
 */
data class CloudTrackDto(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val duration: Double = 0.0,
    val genre: String = "",
    @SerializedName("track_number") val trackNumber: String = "",
    @SerializedName("has_artwork") val hasArtwork: Boolean = false,
    val bpm: Double? = null,
    @SerializedName("trailing_silence_s") val trailingSilenceS: Double? = null,
    @SerializedName("outro_slope_db") val outroSlopeDb: Double? = null,
    @SerializedName("outro_cold_stop") val outroColdStop: Boolean? = null,
    @SerializedName("intro_lead_in_s") val introLeadInS: Double? = null,
    @SerializedName("intro_onset_hardness") val introOnsetHardness: Double? = null,
    @SerializedName("spectral_profile") val spectralProfile: List<Double>? = null,
    /** Path relative to the user's music dir -- the `path=` query every stream/artwork call takes. */
    @SerializedName("server_path") val serverPath: String = "",
    val filename: String = "",
    val ext: String = "",
    /**
     * True for a `.lms` file, whose bytes on the server are XOR-masked behind an
     * "LMSLOCK1" header and are NOT a decodable container until reversed. The
     * player has to unmask these in flight -- see `LumisoundLockDataSource`.
     */
    @SerializedName("is_locked") val isLocked: Boolean = false,
    @SerializedName("uploaded_at") val uploadedAt: String? = null,
    /** Hz -- drives the hi-res badge (iOS parity: >44.1kHz). Null for tracks synced before this field existed. */
    @SerializedName("sample_rate") val sampleRate: Int? = null,
)

data class CloudMusicResponse(
    val tracks: List<CloudTrackDto> = emptyList(),
    val total: Int = 0,
    /** False when the operator hasn't configured per-user music storage at all. */
    val configured: Boolean = true,
    /** Ten-band tonal target computed over the WHOLE library, not just this page. */
    @SerializedName("eq_target") val eqTarget: List<Double>? = null,
)

data class CloudSearchResponse(
    val tracks: List<CloudTrackDto> = emptyList(),
    val total: Int = 0,
)

/** `POST /user/music/upload`'s 201 body. [duplicate] means a byte-identical file was already there -- not an error. */
data class MusicUploadResponse(
    val filename: String = "",
    val path: String = "",
    val id: String = "",
    @SerializedName("metadata_id") val metadataId: String = "",
    val size: Long = 0,
    val duplicate: Boolean = false,
)
