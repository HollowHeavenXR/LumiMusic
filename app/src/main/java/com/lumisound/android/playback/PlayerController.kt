package com.lumisound.android.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.lumisound.android.bridge.BridgeConfig
import com.lumisound.android.bridge.BridgeUrls
import com.lumisound.android.diagnostics.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the UI needs to render a mini player and a Now Playing screen. */
data class QueueItem(
    val index: Int,
    val mediaId: String,
    val title: String,
    val artist: String,
    val isCurrent: Boolean,
    val artworkUri: String? = null,
)

data class PlaybackUiState(
    val connected: Boolean = false,
    val title: String? = null,
    val artist: String? = null,
    val artworkUrl: String? = null,
    val serverPath: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val hasQueue: Boolean = false,
    val queueSize: Int = 0,
    val queueIndex: Int = 0,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = 0,
    val speed: Float = 1f,
    val isLocalSource: Boolean = false,
    val playbackError: String? = null,
    /** The queue entry's id: a server path, `local:…`, `youtube:…` or `podcast:…`. */
    val mediaId: String? = null,
    /** Canonical page URL of a streamed track -- see `PlayableTrack.trackUrl`. */
    val trackUrl: String? = null,
    val podcastFeedUrl: String? = null,
    val episodeGuid: String? = null,
    /** Wall-clock millis the sleep timer fires at, or null when none is set. */
    val sleepAtMs: Long? = null,
    /** Sleep once the current track finishes instead of at a fixed time. */
    val sleepAtEndOfTrack: Boolean = false,
    /**
     * Filled in by the UI, not the player: whether the current track is a favorite is
     * account state, and the session has no idea about it.
     */
    val isFavorite: Boolean = false,
)

/**
 * The app's handle on [PlaybackService]. Everything the UI does to playback goes
 * through a `MediaController`, never a player instance of its own, so there is
 * exactly one player in the process and the notification, Bluetooth controls and
 * the in-app UI can never disagree about what is playing.
 */
@OptIn(UnstableApi::class)
class PlayerController(
    private val context: Context,
    private val config: BridgeConfig,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private var controller: MediaController? = null

    private var sleepJob: kotlinx.coroutines.Job? = null
    private var sleepAtMs: Long? = null
    private var sleepAtEndOfTrack = false

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish()

        /**
         * "End of track" sleep: an automatic advance means the track that was playing has
         * finished, so the new one is paused before it gets going. A skip by hand is the
         * listener still being awake, and does not count.
         */
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (sleepAtEndOfTrack && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                controller?.pause()
                controller?.seekTo(0L)
                sleepAtEndOfTrack = false
                AppLogger.i("playback", "sleep timer fired at end of track")
                publish()
            }
        }

        /**
         * A stream that dies is the failure mode with the least visible cause -- the
         * track simply stops -- so it is logged with the error code and the track's own
         * path, which is what tells a locked-file problem apart from a dead session.
         */
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            val item = controller?.currentMediaItem
            AppLogger.e(
                "playback",
                "player error ${error.errorCodeName}",
                error,
                mapOf(
                    "mediaId" to item?.mediaId,
                    "isLocked" to (item?.mediaMetadata?.extras?.getBoolean(EXTRA_IS_LOCKED) == true),
                    "isLocal" to (item?.mediaMetadata?.extras?.getBoolean(EXTRA_IS_LOCAL) == true),
                ),
            )
        }
    }

    fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            controller = try {
                future.get().also { it.addListener(listener) }
            } catch (e: Exception) {
                Log.w(TAG, "could not bind playback service: ${e.javaClass.simpleName}")
                null
            }
            publish()
            startTicking()
        }, MoreExecutors.directExecutor())
    }

    fun release() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    /**
     * Plays [tracks] starting at [startIndex], replacing whatever queue exists.
     * [startPositionMs] resumes part-way in -- a podcast episode picked up where it was left.
     */
    fun play(tracks: List<PlayableTrack>, startIndex: Int, startPositionMs: Long = 0L) {
        val player = controller ?: return
        if (tracks.isEmpty()) return
        player.setMediaItems(
            tracks.map { it.toMediaItem() },
            startIndex.coerceIn(0, tracks.lastIndex),
            startPositionMs.coerceAtLeast(0L),
        )
        player.prepare()
        player.play()
        AppLogger.i(
            "playback",
            "queue replaced",
            mapOf("count" to tracks.size, "startIndex" to startIndex, "locked" to tracks.count { it.isLocked }),
        )
    }

    /** Appends to the end of the current queue, starting playback if nothing is queued. */
    fun enqueue(tracks: List<PlayableTrack>) {
        val player = controller ?: return
        if (tracks.isEmpty()) return
        val wasEmpty = player.mediaItemCount == 0
        player.addMediaItems(tracks.map { it.toMediaItem() })
        if (wasEmpty) {
            player.prepare()
            player.play()
        }
    }

    /** Inserts directly after whatever is playing. */
    fun playNext(track: PlayableTrack) {
        val player = controller ?: return
        if (player.mediaItemCount == 0) {
            play(listOf(track), 0)
            return
        }
        player.addMediaItem(player.currentMediaItemIndex + 1, track.toMediaItem())
    }

    fun removeFromQueue(index: Int) {
        val player = controller ?: return
        if (index in 0 until player.mediaItemCount) player.removeMediaItem(index)
    }

    fun moveInQueue(from: Int, to: Int) {
        val player = controller ?: return
        if (from in 0 until player.mediaItemCount && to in 0 until player.mediaItemCount) {
            player.moveMediaItem(from, to)
        }
    }

    fun skipTo(index: Int) {
        val player = controller ?: return
        if (index in 0 until player.mediaItemCount) player.seekTo(index, 0L)
    }

    fun setShuffle(enabled: Boolean) {
        controller?.shuffleModeEnabled = enabled
    }

    /** Cycles off -> all -> one, matching what the button shows. */
    fun cycleRepeatMode() {
        val player = controller ?: return
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** 0.5x to 2.0x; pitch follows speed, as every other player on the platform does. */
    fun setSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed.coerceIn(0.5f, 2.0f))
    }

    /**
     * 0..1 -- ExoPlayer's native stream-volume range. iOS's AudioSettings.volume
     * allows up to 4.0 via a separate `AVAudioUnitEQ.globalGain` boost stage this
     * app has no equivalent of; a value above 1.0 synced from there is simply not
     * representable here (see AudioSettingsSyncService, which only ever calls
     * this with values already clamped to the overlapping range).
     */
    fun setVolume(volume: Float) {
        controller?.volume = volume.coerceIn(0f, 1f)
    }

    /**
     * Pauses after [minutes], or when the current track ends if [minutes] is null and
     * [endOfTrack] is set. Either call replaces whatever timer was running; [cancelSleepTimer]
     * clears it. The timer lives in the app process with the controller, not the service:
     * if the process is gone there is no UI that could have set one anyway.
     */
    fun setSleepTimer(minutes: Int?, endOfTrack: Boolean = false) {
        cancelSleepTimer()
        if (endOfTrack) {
            sleepAtEndOfTrack = true
        } else if (minutes != null && minutes > 0) {
            val fireAt = System.currentTimeMillis() + minutes * 60_000L
            sleepAtMs = fireAt
            sleepJob = scope.launch(Dispatchers.Main) {
                kotlinx.coroutines.delay(fireAt - System.currentTimeMillis())
                controller?.pause()
                sleepAtMs = null
                AppLogger.i("playback", "sleep timer fired", mapOf("minutes" to minutes))
                publish()
            }
        }
        publish()
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        sleepAtMs = null
        sleepAtEndOfTrack = false
        publish()
    }

    /** The queue as the session currently holds it, for the Queue screen. */
    fun queueSnapshot(): List<QueueItem> {
        val player = controller ?: return emptyList()
        return (0 until player.mediaItemCount).map { index ->
            val item = player.getMediaItemAt(index)
            QueueItem(
                index = index,
                mediaId = item.mediaId,
                title = item.mediaMetadata.title?.toString() ?: "Unknown",
                artist = item.mediaMetadata.artist?.toString().orEmpty(),
                isCurrent = index == player.currentMediaItemIndex,
                artworkUri = item.mediaMetadata.artworkUri?.toString(),
            )
        }
    }

    fun togglePlayPause() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun next() = controller?.seekToNextMediaItem()

    fun previous() = controller?.seekToPreviousMediaItem()

    fun seekTo(positionMs: Long) = controller?.seekTo(positionMs)

    private fun startTicking() {
        // The controller only reports position on events, so a visible seek bar needs
        // its own tick. Half a second matches Lumisound's own position timer.
        scope.launch(Dispatchers.Main) {
            while (controller != null) {
                publish()
                kotlinx.coroutines.delay(500)
            }
        }
    }

    private fun publish() {
        val player = controller
        if (player == null) {
            _state.value = PlaybackUiState(sleepAtMs = sleepAtMs, sleepAtEndOfTrack = sleepAtEndOfTrack)
            return
        }
        val item = player.currentMediaItem
        val metadata = item?.mediaMetadata
        val knownDuration = metadata?.extras?.getLong(EXTRA_DURATION_MS) ?: 0L
        val playerDuration = player.duration.takeIf { it > 0 } ?: 0L
        _state.value = PlaybackUiState(
            connected = true,
            title = metadata?.title?.toString(),
            artist = metadata?.artist?.toString(),
            artworkUrl = metadata?.artworkUri?.toString(),
            serverPath = item?.localConfiguration?.uri?.getQueryParameter("path"),
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = if (playerDuration > 0) playerDuration else knownDuration,
            hasQueue = player.mediaItemCount > 0,
            queueSize = player.mediaItemCount,
            queueIndex = player.currentMediaItemIndex,
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            speed = player.playbackParameters.speed,
            isLocalSource = metadata?.extras?.getBoolean(EXTRA_IS_LOCAL) == true,
            playbackError = player.playerError?.let { "${it.errorCodeName}: ${it.message}" },
            mediaId = item?.mediaId,
            trackUrl = metadata?.extras?.getString(EXTRA_TRACK_URL),
            podcastFeedUrl = metadata?.extras?.getString(EXTRA_PODCAST_FEED),
            episodeGuid = metadata?.extras?.getString(EXTRA_EPISODE_GUID),
            sleepAtMs = sleepAtMs,
            sleepAtEndOfTrack = sleepAtEndOfTrack,
        )
    }

    private companion object {
        const val TAG = "LumiPlayer"
    }
}

const val EXTRA_SERVER_PATH = "lumi.serverPath"
const val EXTRA_IS_LOCKED = "lumi.isLocked"
