package com.lumisound.android.playback

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.lumisound.android.audio.AudioSessionHolder
import com.lumisound.android.diagnostics.AppLogger
import com.lumisound.android.diagnostics.traced
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * Runs two `ExoPlayer` instances ("A"/"B") so one can fade in while the other
 * fades out -- `ExoPlayer` has no multi-node mixer the way `AVAudioEngine`
 * does (see `AudioPlayerManager+Crossfade.swift`'s `primaryNode`/
 * `secondaryNode`), so genuine overlapping playback on this platform means
 * two full players running concurrently rather than two nodes on one engine.
 *
 * Deliberately narrower than iOS's crossfade: no beatmatching/tempo-nudging
 * and no Smart Auto Crossfade (BPM/live-level-driven duration) -- a fixed
 * user-set duration with an equal-power curve only (see
 * [CrossfadeController]). Also only auto-triggers on a plain, sequential,
 * non-repeating "next track" -- shuffle jumps, repeat-one, and the
 * end-of-queue repeat-all wrap are all skipped (checked via
 * `nextMediaItemIndex == currentMediaItemIndex + 1`) because the simple
 * "snapshot everything after the next item and move it across" swap this
 * class does only has one unambiguous meaning in that linear case; under
 * shuffle the engine's internal shuffle order and this class's plain index
 * range would disagree about what the "rest of the queue" even is.
 *
 * [activePlayer] is whichever engine is currently bound to the
 * `MediaSession` (and is what every other playback feature -- the widget,
 * loudness gain, Now Playing -- should read from). [onActivePlayerChanged]
 * fires the moment that changes, so the caller can re-point the session and
 * republish [AudioSessionHolder] accordingly.
 */
@OptIn(UnstableApi::class)
class CrossfadeCoordinator(
    private val engineA: ExoPlayer,
    private val engineB: ExoPlayer,
    private val settings: CrossfadeController,
    private val scope: CoroutineScope,
    private val onActivePlayerChanged: (ExoPlayer) -> Unit,
) {
    @Volatile
    var activePlayer: ExoPlayer = engineA
        private set
    private var idlePlayer: ExoPlayer = engineB

    @Volatile
    var isCrossfading: Boolean = false
        private set

    /**
     * Fires once a crossfade hands control to a new active engine, AFTER
     * [isCrossfading] has already flipped back to false. `LoudnessListener`
     * owns per-track volume (the bridge's `X-Loudness-Gain-Db`) the rest of
     * the time, but it skips applying that gain while [isCrossfading] is
     * true -- the fade ramp needs exclusive control of `.volume` during the
     * overlap, or the loudness gain reapplying on `onIsPlayingChanged` the
     * instant `incoming.play()` is called would stomp the ramp's `0f` start
     * with a full-volume value, turning a silent fade-in into an audible
     * pop. This callback is PlaybackService's hook to re-apply the correct
     * loudness gain for the engine that just became current, now that the
     * ramp is done fighting over `.volume`.
     */
    var onCrossfadeFinished: ((ExoPlayer) -> Unit)? = null

    private var crossfadeJob: Job? = null
    private var pollJob: Job? = null

    fun start() {
        if (pollJob != null) return
        pollJob = scope.launch(Dispatchers.Main) {
            while (true) {
                delay(250)
                // Not a bare runCatching: that swallowed a crash in this loop with
                // zero trace left behind anywhere -- traced() logs it (with the
                // engines' live state, since that's exactly what a crossfade-timing
                // bug needs to be diagnosable from a report alone) before letting
                // the poll continue on the next tick.
                try {
                    traced("crossfade", "poll-tick", extra = { crossfadeDebugState() }) { maybeBeginCrossfade() }
                } catch (t: Throwable) {
                    // Logged by traced(); the poll loop itself must never die from it.
                }
            }
        }
    }

    private fun crossfadeDebugState(): Map<String, Any?> = mapOf(
        "isCrossfading" to isCrossfading,
        "activeIsA" to (activePlayer === engineA),
        "activeMediaItemCount" to runCatching { activePlayer.mediaItemCount }.getOrNull(),
        "activeIndex" to runCatching { activePlayer.currentMediaItemIndex }.getOrNull(),
    )

    /**
     * Called from a [Player.Listener] attached to both engines whenever the
     * ACTIVE one reports a manual seek/skip (not a natural end-of-track
     * advance) -- the user interrupting mid-fade. The idle engine is
     * abandoned and silenced; the active one continues exactly as if no
     * crossfade had been attempted, matching `AudioPlayerManager.
     * cancelCrossfade()`'s behavior.
     *
     * Called directly from `Player.Listener` callbacks (manual seek/skip, a
     * player error), so a failure here must never escape -- logged and
     * swallowed instead of risking a listener-dispatch crash over crossfade
     * bookkeeping.
     */
    fun cancelCrossfade() {
        if (!isCrossfading) return
        try {
            crossfadeJob?.cancel()
            crossfadeJob = null
            isCrossfading = false
            idlePlayer.stop()
            idlePlayer.clearMediaItems()
            idlePlayer.volume = 1f
            // Not hardcoded to 1f -- same reasoning as finishCrossfade: the correct
            // volume for the still-active engine is whatever its loudness gain says,
            // which this re-applies now that isCrossfading no longer blocks it.
            onCrossfadeFinished?.invoke(activePlayer)
            AppLogger.i("playback", "crossfade cancelled (manual seek/skip)")
        } catch (t: Throwable) {
            AppLogger.e("playback", "cancelCrossfade failed", t)
            isCrossfading = false
            crossfadeJob = null
        }
    }

    /**
     * Mirrors the active engine's play/pause state onto the idle one while a
     * crossfade is in progress -- without this, pressing pause mid-overlap
     * only reaches whichever engine the `MediaSession` is currently bound to
     * (the outgoing one); the incoming engine isn't attached to the session
     * yet and would keep playing audibly through the pause.
     */
    fun mirrorPlayState(source: Player, isPlaying: Boolean) {
        if (!isCrossfading || source !== activePlayer) return
        if (isPlaying) idlePlayer.play() else idlePlayer.pause()
    }

    /** Freezes the fade ramp -- `isPlaying` is checked every tick instead, this just lets a caller bail out a stalled fade (e.g. a player error) early. */
    fun abandonIfIdleEngineErrored(source: Player) {
        if (isCrossfading && source === idlePlayer) {
            AppLogger.w("playback", "crossfade abandoned: incoming engine errored")
            cancelCrossfade()
        }
    }

    private fun maybeBeginCrossfade() {
        if (isCrossfading) return
        if (!settings.enabled.value) return
        val player = activePlayer
        if (!player.isPlaying) return
        val duration = player.duration
        if (duration <= 0) return
        val currentIndex = player.currentMediaItemIndex
        // Only a plain sequential advance -- see class doc comment.
        if (player.nextMediaItemIndex != currentIndex + 1) return
        val fadeDurationMs = settings.durationSeconds.value * 1_000L
        if (fadeDurationMs <= 0) return
        val remaining = duration - player.currentPosition
        if (remaining in 0..fadeDurationMs) {
            beginCrossfade(currentIndex, fadeDurationMs)
        }
    }

    private fun beginCrossfade(currentIndex: Int, fadeDurationMs: Long) {
        val outgoing = activePlayer
        val incoming = idlePlayer
        val nextIndex = currentIndex + 1
        if (nextIndex >= outgoing.mediaItemCount) return
        val nextItem = outgoing.getMediaItemAt(nextIndex)
        val tail = (nextIndex + 1 until outgoing.mediaItemCount).map { outgoing.getMediaItemAt(it) }
        val repeatMode = outgoing.repeatMode

        isCrossfading = true
        incoming.clearMediaItems()
        incoming.repeatMode = repeatMode
        incoming.volume = 0f
        incoming.setMediaItem(nextItem)
        incoming.prepare()
        incoming.play()
        AppLogger.i(
            "playback",
            "crossfade begin",
            mapOf("fadeDurationMs" to fadeDurationMs, "tailSize" to tail.size),
        )

        val steps = (fadeDurationMs / 33L).toInt().coerceAtLeast(1)
        val stepDelayMs = fadeDurationMs / steps
        crossfadeJob = scope.launch(Dispatchers.Main) {
            // This coroutine is a direct top-level launch on an app-lifetime
            // SupervisorJob scope with no CoroutineExceptionHandler -- an
            // uncaught exception here doesn't just fail this one crossfade, it
            // propagates to the thread's default uncaught-exception handler,
            // which CrashReporter installs. That turns a bug in a feature that
            // defaults to OFF into the whole app crashing on every single track
            // transition for anyone who turns it on. Caught and logged here
            // instead, with a best-effort fallback to the same cleanup
            // cancelCrossfade() does, so a crossfade failing degrades to a
            // plain cut rather than taking playback down with it.
            try {
                var step = 0
                while (step < steps) {
                    delay(stepDelayMs)
                    // Frozen while paused -- same reasoning as `AudioPlayerManager`'s
                    // own crossfade timer: this ramp is wall-clock, and advancing it
                    // through silence would finish the fade (and stop the outgoing
                    // engine) while the user is still paused on the old track.
                    if (!outgoing.isPlaying && !incoming.isPlaying) continue
                    step++
                    val progress = step.toFloat() / steps
                    val theta = (progress * Math.PI / 2).toFloat()
                    outgoing.volume = cos(theta).coerceIn(0f, 1f)
                    incoming.volume = sin(theta).coerceIn(0f, 1f)
                }
                finishCrossfade(outgoing, incoming, tail)
            } catch (t: Throwable) {
                AppLogger.e(
                    "playback",
                    "crossfade ramp failed, falling back to a plain cut",
                    t,
                    mapOf("fadeDurationMs" to fadeDurationMs, "steps" to steps),
                )
                runCatching {
                    incoming.stop()
                    incoming.clearMediaItems()
                    incoming.volume = 1f
                    outgoing.volume = 1f
                }
                isCrossfading = false
                crossfadeJob = null
            }
        }
    }

    private fun finishCrossfade(outgoing: ExoPlayer, incoming: ExoPlayer, tail: List<MediaItem>) {
        outgoing.stop()
        outgoing.clearMediaItems()
        outgoing.volume = 1f
        if (tail.isNotEmpty()) incoming.addMediaItems(tail)
        activePlayer = incoming
        idlePlayer = outgoing
        isCrossfading = false
        crossfadeJob = null
        AppLogger.i("playback", "crossfade finished, active engine swapped")
        onActivePlayerChanged(incoming)
        if (incoming.audioSessionId != 0) AudioSessionHolder.publish(incoming.audioSessionId)
        // Not a hardcoded `incoming.volume = 1f` -- the correct steady-state
        // volume for the now-current track is whatever the bridge's loudness
        // gain says it should be, which LoudnessListener computes.
        onCrossfadeFinished?.invoke(incoming)
    }
}
