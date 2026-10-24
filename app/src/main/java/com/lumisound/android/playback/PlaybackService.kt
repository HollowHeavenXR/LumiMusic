package com.lumisound.android.playback

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
import com.lumisound.android.audio.MonoDownmixHolder
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

    override fun onCreate() {
        super.onCreate()
        val container = (application as LumiMusicApp).container

        // Every cloud stream is an authenticated request, so the player shares the
        // app's OkHttp client (and therefore its auth interceptor) rather than
        // carrying credentials of its own. Locked `.lms` tracks unmask in flight --
        // see LumisoundLockDataSource.
        val httpFactory = OkHttpDataSource.Factory(container.http.client)
        val dataSourceFactory = LumisoundLockDataSource.Factory(
            DefaultDataSource.Factory(this, httpFactory)
        )

        // Only to splice MonoDownmixHolder.processor into the audio-processor chain --
        // everything else about renderer construction stays the default behavior.
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(MonoDownmixHolder.processor, EightDAudioHolder.processor))
                    .build()
        }

        val player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Bind the effects chain to a real session id rather than 0 -- see
        // AudioSessionHolder. Generated here because the player is created here.
        val audioManager = getSystemService(android.media.AudioManager::class.java)
        val sessionId = audioManager?.generateAudioSessionId() ?: 0
        if (sessionId != 0) {
            player.audioSessionId = sessionId
            AudioSessionHolder.publish(sessionId)
        }

        val loudnessListener = LoudnessListener(player, container.loudnessGains, container.replayGain)
        player.addListener(loudnessListener)
        // The listener only re-evaluates on a track/play-state change -- toggling
        // this setting mid-track needs its own trigger so the volume it already
        // set actually changes immediately rather than waiting for the next track.
        container.scope.launch(Dispatchers.Main) {
            container.replayGain.enabled.drop(1).collect { loudnessListener.apply(player.currentMediaItem) }
        }

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

        session = MediaSession.Builder(this, player)
            .setBitmapLoader(bitmapLoader)
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
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
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
) : Player.Listener {

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = apply(mediaItem)

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) apply(player.currentMediaItem)
    }

    fun apply(mediaItem: MediaItem?) {
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
