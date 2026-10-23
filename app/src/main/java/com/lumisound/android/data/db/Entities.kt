package com.lumisound.android.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A track in the account's cloud library, mirrored locally so the library is
 * browsable offline and a cold start doesn't wait on a 5000-row listing.
 *
 * Keyed by `serverPath`, not the server's `id`: the id is a hash of the file's
 * ABSOLUTE path, so it changes if the operator ever moves the storage root,
 * while the relative path is what every stream/artwork call actually takes.
 */
@Entity(tableName = "cloud_tracks", indices = [Index("title"), Index("artist"), Index("album")])
data class CloudTrackEntity(
    @PrimaryKey val serverPath: String,
    val remoteId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationSeconds: Double,
    val genre: String,
    val trackNumber: String,
    val hasArtwork: Boolean,
    val isLocked: Boolean,
    val ext: String,
    val filename: String,
    val bpm: Double?,
    val uploadedAt: String?,
    /** Hz, e.g. 44100/48000/96000 -- drives the hi-res badge (iOS parity: >44.1kHz). */
    val sampleRate: Int?,
    /** Millis, local clock -- when this row was last confirmed present on the server. */
    val syncedAt: Long,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val songId: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val addedAt: String?,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val folder: String?,
    val updatedAt: String?,
    val trackCount: Int,
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "position"],
    indices = [Index("playlistId")],
)
data class PlaylistTrackEntity(
    val playlistId: String,
    val position: Int,
    val remoteId: String?,
    val trackUrl: String?,
    val localSongId: String?,
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationSeconds: Double?,
)

@Entity(tableName = "play_history", indices = [Index("playedAt")])
data class PlayHistoryEntity(
    @PrimaryKey val id: String,
    val trackUrl: String?,
    val localSongId: String?,
    val title: String?,
    val artist: String?,
    val playedAt: String?,
    val listenSeconds: Double?,
)
