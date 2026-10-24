package com.lumisound.android.playback

import android.app.Service
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.lumisound.android.LumiMusicApp
import com.google.common.util.concurrent.MoreExecutors
import com.lumisound.android.audio.AudioSessionHolder
import com.lumisound.android.audio.EightDAudioHolder
import com.lumisound.android.audio.EightDAudioProcessor
import com.lumisound.android.audio.MonoDownmixAudioProcessor
import com.lumisound.android.audio.MonoDownmixHolder
import com.lumisound.android.widget.NowPlayingWidgetUpdater
import java.util.concurrent.Executors
import kotlin.math.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * The single place audio actually plays. A `MediaSessionService` so playback
 * survives the UI being gone and shows up in the system notification, on
 * Bluetooth controls and on a watch face without any of those being wired up
 * individually.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    /**
     * The home-screen widget's buttons target this service directly (not a
     * `MediaController`, which a plain `AppWidgetProvider` has no simple way
     * to build/await a connection for) -- same component that already owns
     * the real `Player` instance these three actions call straight into.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_WIDGET_PLAY_PAUSE -> session?.player?.let { if (it.isPlaying) it.pause() else it.play() }
            ACTION_WIDGET_NEXT -> session?.player?.seekToNext()
            ACTION_WIDGET_PREVIOUS -> session?.player?.seekToPrevious()
            else -> return super.onStartCommand(intent, flags, startId)
        }
        return Service.START_NOT_STICKY
    }

    private var crossfade: CrossfadeCoordinator? = null
    private var engineA: ExoPlayer? = null
    private var engineB: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()
        val container = (application as LumiMusicApp).container

        // Every cloud stream is an authenticated request, so the player shares the
        // app's OkHttp client (and therefore its auth interceptor) rather than
        // carrying credentials of its own. Locked `.lms` tracks unmask in flight --
        // see LumisoundLockDataSource. Shared across both engines below -- a
        // `DataSource.Factory` is stateless, it just produces a fresh `DataSource`
        // per request, so there's nothing per-engine to isolate here.
        val httpFactory = OkHttpDataSource.Factory(container.http.client)
        val dataSourceFactory = LumisoundLockDataSource.Factory(
            DefaultDataSource.Factory(this, httpFactory)
        )

        // Two full engines, not two nodes on one -- see CrossfadeCoordinator's doc
        // comment. Each gets its OWN mono-downmix/8D processor instance (sharing
        // one `BaseAudioProcessor` between two concurrently-running renderer
        // threads during the overlap would corrupt both signal paths), but share
        // everything stateless (the data source factory, bitmap loader).
        val engineA = buildExoPlayer(dataSourceFactory, MonoDownmixHolder.processorA, EightDAudioHolder.processorA)
        val engineB = buildExoPlayer(dataSourceFactory, MonoDownmixHolder.processorB, EightDAudioHolder.processorB)
        this.engineA = engineA
        this.engineB = engineB

        // Bind the effects chain to a real session id rather than 0 -- see
        // AudioSessionHolder. Only the initially-active engine's id is published;
        // CrossfadeCoordinator republishes the other engine's id the moment a
        // crossfade hands control over to it.
        val audioManager = getSystemService(android.media.AudioManager::class.java)
        val sessionIdA = audioManager?.generateAudioSessionId() ?: 0
        val sessionIdB = audioManager?.generateAudioSessionId() ?: 0
        if (sessionIdA != 0) engineA.audioSessionId = sessionIdA
        if (sessionIdB != 0) engineB.audioSessionId = sessionIdB
        if (sessionIdA != 0) AudioSessionHolder.publish(sessionIdA)

        val coordinator = CrossfadeCoordinator(
            engineA = engineA,
            engineB = engineB,
            settings = container.crossfade,
            scope = container.scope,
            onActivePlayerChanged = { newActive -> session?.setPlayer(newActive) },
        )
        crossfade = coordinator

        // One listener instance per engine -- LoudnessListener/WidgetRefreshListener
        // each close over a specific `Player` reference, so a single shared
        // instance attached to both would always act on whichever engine it was
        // built with, not whichever one actually fired the callback.
        val loudnessA = LoudnessListener(engineA, container.loudnessGains, container.replayGain, coordinator)
        val loudnessB = LoudnessListener(engineB, container.loudnessGains, container.replayGain, coordinator)
        engineA.addListener(loudnessA)
        engineB.addListener(loudnessB)
        engineA.addListener(WidgetRefreshListener(this, engineA))
        engineB.addListener(WidgetRefreshListener(this, engineB))
        coordinator.onCrossfadeFinished = { active -> if (active === engineA) loudnessA.apply(engineA.currentMediaItem) else loudnessB.apply(engineB.currentMediaItem) }
        // The listener only re-evaluates on a track/play-state change -- toggling
        // this setting mid-track needs its own trigger so the volume it already
        // set actually changes immediately rather than waiting for the next track.
        container.scope.launch(Dispatchers.Main) {
            container.replayGain.enabled.drop(1).collect {
                loudnessA.apply(engineA.currentMediaItem)
                loudnessB.apply(engineB.currentMediaItem)
            }
        }

        coordinator.start()
        engineA.addListener(CrossfadeCancelListener(coordinator, engineA))
        engineB.addListener(CrossfadeCancelListener(coordinator, engineB))

        // The media notification loads artwork through its own BitmapLoader, which by
        // default uses an unauthenticated data source -- the same mistake that hid artwork in
        // the app itself. Cloud artwork is JWT-gated, so the notification gets the app's
        // client too, or it would quietly show a blank square for every cloud track.
        val bitmapLoader = CacheBitmapLoader(
            DataSourceBitmapLoader(
                MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()),
                httpFactory,
            )
        )

        session = MediaSession.Builder(this, engineA)
            .setBitmapLoader(bitmapLoader)
            .build()
    }

    /** Identical construction for both crossfade engines, differing only in which processor instances get spliced into the audio-processor chain. */
    private fun buildExoPlayer(
        dataSourceFactory: LumisoundLockDataSource.Factory,
        monoProcessor: MonoDownmixAudioProcessor,
        eightDProcessor: EightDAudioProcessor,
    ): ExoPlayer {
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(monoProcessor, eightDProcessor))
                    .build()
        }
        return ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                // Both engines request focus rather than only the active one --
                // `handleAudioFocus` is fixed for a player's lifetime and which
                // engine is "active" flips on every crossfade. Android tolerates a
                // redundant re-request from the same process without an audible
                // glitch, which is a simpler tradeoff than dynamically retoggling.
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away while paused should end the session rather than leave
        // a dead notification behind; while playing, playback continues.
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        // Release BOTH engines explicitly rather than just `session.player` --
        // whichever one is currently idle (not bound to the session) would
        // otherwise leak, since a crossfade swap only ever points the session at
        // one of the two.
        session?.release()
        session = null
        engineA?.release()
        engineB?.release()
        engineA = null
        engineB = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_WIDGET_PLAY_PAUSE = "com.lumisound.android.widget.PLAY_PAUSE"
        const val ACTION_WIDGET_NEXT = "com.lumisound.android.widget.NEXT"
        const val ACTION_WIDGET_PREVIOUS = "com.lumisound.android.widget.PREVIOUS"
    }
}

/**
 * One instance per engine (captures [source] the same way [LoudnessListener]
 * does): cancels an in-flight crossfade the moment a manual seek/skip lands
 * on the ACTIVE engine (a natural end-of-track advance fires
 * `onMediaItemTransition` with `MEDIA_ITEM_TRANSITION_REASON_AUTO`, not a
 * discontinuity, so this only reacts to the user actually interrupting the
 * fade), mirrors the active engine's play/pause state onto the idle one
 * while a fade is in progress (see [CrossfadeCoordinator.mirrorPlayState]'s
 * doc comment for why), and abandons a fade if the incoming engine fails to
 * play the next track.
 */
@OptIn(UnstableApi::class)
private class CrossfadeCancelListener(
    private val coordinator: CrossfadeCoordinator,
    private val source: Player,
) : Player.Listener {
    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        if (source !== coordinator.activePlayer) return
        if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SKIP) {
            coordinator.cancelCrossfade()
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        coordinator.mirrorPlayState(source, isPlaying)
    }

    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
        coordinator.abandonIfIdleEngineErrored(source)
    }
}

/** Keeps the home-screen widget's title/artist/play-pause icon in sync with real playback. */
private class WidgetRefreshListener(private val context: android.content.Context, private val player: Player) : Player.Listener {
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = refresh(mediaItem)
    override fun onIsPlayingChanged(isPlaying: Boolean) = refresh(player.currentMediaItem)

    private fun refresh(mediaItem: MediaItem?) {
        val metadata = mediaItem?.mediaMetadata
        NowPlayingWidgetUpdater.update(
            context,
            title = metadata?.title?.toString(),
            artist = metadata?.artist?.toString(),
            isPlaying = player.isPlaying,
        )
    }
}

/**
 * Applies the bridge's per-track loudness gain.
 *
 * `X-Loudness-Gain-Db` only exists on the stream response, so the value for a
 * track is not known until its first bytes arrive -- the gain is therefore applied
 * on transition AND re-applied once the track starts reading, which is when the
 * header has actually been seen.
 */
@OptIn(UnstableApi::class)
private class LoudnessListener(
    private val player: Player,
    private val gains: com.lumisound.android.bridge.LoudnessGainStore,
    private val replayGain: com.lumisound.android.bridge.ReplayGainController,
    private val crossfade: CrossfadeCoordinator,
) : Player.Listener {

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = apply(mediaItem)

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) apply(player.currentMediaItem)
    }

    /**
     * No-op while a crossfade is in progress -- the fade ramp needs
     * exclusive control of `.volume` during the overlap (see
     * [CrossfadeCoordinator.onCrossfadeFinished]'s doc comment for why),
     * and is reapplied once that callback fires.
     */
    fun apply(mediaItem: MediaItem?) {
        if (crossfade.isCrossfading) return
        val serverPath = mediaItem?.localConfiguration?.uri?.getQueryParameter("path")
        val gainDb = gains.gainDb(serverPath)
        // 1.0 (no change) for an unanalyzed track, or when the user has turned
        // this off entirely: silently attenuating audio the server has no
        // measurement for would be worse than leaving it alone, same reasoning
        // either way.
        player.volume = if (gainDb == null || !replayGain.enabled.value) 1f else {
            10.0.pow(gainDb.toDouble() / 20.0).toFloat().coerceIn(0.05f, 1f)
        }
    }
}
