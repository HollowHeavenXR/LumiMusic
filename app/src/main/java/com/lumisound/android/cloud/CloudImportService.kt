package com.lumisound.android.cloud

import android.util.Log
import com.lumisound.android.bridge.BridgeHttp
import com.lumisound.android.bridge.model.CloudTrackDto
import com.lumisound.android.bridge.model.FavoriteDto
import com.lumisound.android.bridge.model.HistoryEntryDto
import com.lumisound.android.bridge.model.PlaylistDto
import com.lumisound.android.data.AppearanceStore
import com.lumisound.android.data.db.CloudTrackEntity
import com.lumisound.android.data.db.FavoriteEntity
import com.lumisound.android.data.db.LumiDatabase
import com.lumisound.android.data.db.PlayHistoryEntity
import com.lumisound.android.data.db.PlaylistEntity
import com.lumisound.android.data.db.PlaylistTrackEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One importable kind of account data, in the order the import runs them. */
enum class ImportStage(val label: String) {
    CloudTracks("Cloud tracks"),
    Favorites("Favorites"),
    Playlists("Playlists"),
    History("Play history"),
    Settings("Settings"),
}

/**
 * Per-stage outcome. A stage that fails does NOT fail the import: the stages are
 * independent server-side, and an import that already pulled 900 tracks should
 * not throw them away because the history call timed out.
 */
sealed interface StageResult {
    data object Pending : StageResult
    data object Running : StageResult
    data class Done(val count: Int, val note: String? = null) : StageResult
    data class Failed(val message: String) : StageResult
}

data class ImportProgress(
    val running: Boolean = false,
    val stages: Map<ImportStage, StageResult> = ImportStage.entries.associateWith { StageResult.Pending },
    val finishedAt: Long? = null,
) {
    val failures: List<Pair<ImportStage, String>>
        get() = stages.mapNotNull { (stage, result) ->
            (result as? StageResult.Failed)?.let { stage to it.message }
        }

    val importedCount: Int
        get() = stages.values.sumOf { (it as? StageResult.Done)?.count ?: 0 }
}

/**
 * Pulls an existing Lumisound account's cloud data onto this device.
 *
 * There is nothing to migrate and nothing to export/import by file: the data
 * already lives on the bridge, so "importing" is reading the same endpoints iOS
 * writes and mirroring them into the local database. Re-running it is safe --
 * every stage replaces its own table wholesale, and nothing is ever pushed back
 * up as a side effect of an import (which is what keeps a fresh install from
 * overwriting a mature account with its own empty state).
 */
class CloudImportService(
    private val http: BridgeHttp,
    private val db: LumiDatabase,
    private val appearance: AppearanceStore,
) {

    private val _progress = MutableStateFlow(ImportProgress())
    val progress: StateFlow<ImportProgress> = _progress.asStateFlow()

    @Volatile
    private var lastAccentRefreshAt: Long = 0L

    suspend fun import(stages: Set<ImportStage> = ImportStage.entries.toSet()): ImportProgress {
        _progress.value = ImportProgress(
            running = true,
            stages = ImportStage.entries.associateWith {
                if (it in stages) StageResult.Pending else StageResult.Done(0, "skipped")
            },
        )

        for (stage in ImportStage.entries) {
            if (stage !in stages) continue
            mark(stage, StageResult.Running)
            val result = try {
                when (stage) {
                    ImportStage.CloudTracks -> importCloudTracks()
                    ImportStage.Favorites -> importFavorites()
                    ImportStage.Playlists -> importPlaylists()
                    ImportStage.History -> importHistory()
                    ImportStage.Settings -> importSettings()
                }
            } catch (e: Exception) {
                Log.w(TAG, "stage ${stage.name} failed", e)
                StageResult.Failed(e.userMessage())
            }
            mark(stage, result)
        }

        _progress.value = _progress.value.copy(running = false, finishedAt = System.currentTimeMillis())
        return _progress.value
    }

    private suspend fun importCloudTracks(): StageResult {
        val response = http.cloudMusic.listTracks()
        if (!response.configured) {
            return StageResult.Done(0, "This bridge has no personal cloud storage configured.")
        }
        val syncedAt = System.currentTimeMillis()
        db.cloudTracks().replaceAll(response.tracks.map { it.toEntity(syncedAt) }, syncedAt)
        val locked = response.tracks.count { it.isLocked }
        return StageResult.Done(
            response.tracks.size,
            if (locked > 0) "$locked locked (.lms) track${if (locked == 1) "" else "s"} included" else null,
        )
    }

    private suspend fun importFavorites(): StageResult {
        val favorites = http.libraryData.favorites()
        db.favorites().replaceAll(favorites.map(FavoriteDto::toEntity))
        return StageResult.Done(favorites.size)
    }

    private suspend fun importPlaylists(): StageResult {
        val playlists = http.libraryData.playlists()
        val rows = playlists.map(PlaylistDto::toEntity)
        val tracks = playlists.flatMap { playlist ->
            // The server orders tracks by `position`, but a playlist edited across
            // clients can hold duplicate or sparse positions; index defensively so
            // the (playlistId, position) primary key can never collide and silently
            // drop a track.
            playlist.tracks.mapIndexed { index, track -> track.toEntity(playlist.id, index) }
        }
        db.playlists().replaceAll(rows, tracks)
        return StageResult.Done(playlists.size, "${tracks.size} tracks")
    }

    private suspend fun importHistory(): StageResult {
        val history = http.libraryData.history()
        db.history().replaceAll(history.mapIndexed { index, entry -> entry.toEntity(index) })
        return StageResult.Done(history.size, "most recent 200")
    }

    /**
     * Settings are pulled but deliberately applied narrowly: the row holds
     * iOS-shaped audio blobs this app has no equivalent for. `theme_color` is the
     * one genuinely cross-platform field, so the two clients at least start out
     * visually aligned.
     */
    private suspend fun importSettings(): StageResult {
        val settings = http.libraryData.settings()
        val accent = settings.themeColor?.takeIf { it.startsWith("#") && (it.length == 7 || it.length == 9) }
        return if (accent != null) {
            appearance.setAccent(accent)
            StageResult.Done(1, "accent $accent")
        } else {
            StageResult.Done(0, "no shared settings to apply")
        }
    }

    /**
     * Pulls just the shared settings row. Called on launch so the account's accent is
     * current without waiting for the user to run a full import.
     */
    suspend fun refreshAccent() {
        try {
            appearance.setAccent(http.libraryData.settings().themeColor)
            lastAccentRefreshAt = System.currentTimeMillis()
        } catch (e: Exception) {
            // Keeping the stored colour is the right failure: it was correct last time.
        }
    }

    /**
     * [refreshAccent], unless the last pull was under [minIntervalMs] ago.
     *
     * The accent used to be refreshed only once, at process start -- so an account whose
     * `theme_color` changed on another device (iOS, or a reinstall here) never picked it up
     * on this phone until the app was fully killed and relaunched, even though the gallery
     * background re-syncs on every return to the foreground. Called from the same
     * foreground trigger as [com.lumisound.android.gallery.GalleryBackgroundStore.syncIfStale]
     * so the two settle on the same cadence.
     */
    suspend fun refreshAccentIfStale(minIntervalMs: Long = ACCENT_STALE_AFTER_MS) {
        if (System.currentTimeMillis() - lastAccentRefreshAt < minIntervalMs) return
        refreshAccent()
    }

    private fun mark(stage: ImportStage, result: StageResult) {
        _progress.value = _progress.value.copy(
            stages = _progress.value.stages.toMutableMap().apply { put(stage, result) }
        )
    }

    private companion object {
        const val TAG = "LumiImport"
        const val ACCENT_STALE_AFTER_MS = 60_000L
    }
}

private fun Exception.userMessage(): String = when (this) {
    is retrofit2.HttpException -> "HTTP ${code()}"
    else -> message ?: javaClass.simpleName
}

fun CloudTrackDto.toEntity(syncedAt: Long) = CloudTrackEntity(
    serverPath = serverPath,
    remoteId = id,
    title = title,
    artist = artist,
    album = album,
    durationSeconds = duration,
    genre = genre,
    trackNumber = trackNumber,
    hasArtwork = hasArtwork,
    isLocked = isLocked,
    ext = ext,
    filename = filename,
    bpm = bpm,
    uploadedAt = uploadedAt,
    sampleRate = sampleRate,
    syncedAt = syncedAt,
)

private fun FavoriteDto.toEntity() = FavoriteEntity(
    songId = songId,
    title = title,
    artist = artist,
    album = album,
    addedAt = addedAt,
)

private fun PlaylistDto.toEntity() = PlaylistEntity(
    id = id,
    name = name,
    description = description,
    folder = folder,
    updatedAt = updatedAt,
    trackCount = tracks.size,
)

private fun com.lumisound.android.bridge.model.PlaylistTrackDto.toEntity(playlistId: String, index: Int) =
    PlaylistTrackEntity(
        playlistId = playlistId,
        position = index,
        remoteId = id,
        trackUrl = trackUrl,
        localSongId = localSongId,
        title = title,
        artist = artist,
        album = album,
        durationSeconds = durationSeconds,
    )

private fun HistoryEntryDto.toEntity(index: Int) = PlayHistoryEntity(
    // `id` is present on /user/history rows, but fall back to the ordinal so a
    // row without one still survives the insert instead of colliding on "".
    id = id ?: "history-$index-${playedAt ?: ""}",
    trackUrl = trackUrl,
    localSongId = localSongId,
    title = title,
    artist = artist,
    playedAt = playedAt,
    listenSeconds = listenSeconds,
)
