package com.lumisound.android.audio

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sums stereo 16-bit PCM to mono, written to both output channels (L=R=(L+R)/2)
 * -- iOS parity for `monoAudioEnabled` in `audio_settings_json`. Only handles
 * plain 16-bit stereo; anything else (already-mono, 5.1, float PCM) passes
 * through unmodified rather than being rejected, since most of this app's
 * audio never leaves that shape but a locked/transcoded edge case might.
 *
 * Always present in ExoPlayer's audio-processor chain rather than added/
 * removed at runtime -- media3 only re-queries which processors are active
 * on a format change or seek, not on an arbitrary setting flip mid-playback
 * -- so [enabled] is checked per-buffer instead of via [isActive].
 *
 * UNVERIFIED ON A REAL DEVICE: written against the decompiled media3 1.11.1
 * API (javap against the resolved AAR) with no Android device or emulator
 * available in this environment to confirm actual audio output. The PCM
 * math itself (interleaved 16-bit little-endian stereo -> averaged mono in
 * both channels) is the standard approach, but this needs a real listen
 * before being trusted blind.
 */
@OptIn(UnstableApi::class)
class MonoDownmixAudioProcessor : BaseAudioProcessor() {
    @Volatile var enabled: Boolean = false

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        canDownmix = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT && inputAudioFormat.channelCount == 2
        return inputAudioFormat
    }

    private var canDownmix = false

    override fun queueInput(inputBuffer: ByteBuffer) {
        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val bytesPerFrame = inputAudioFormat.bytesPerFrame
        val output = replaceOutputBuffer(limit - position)

        if (!enabled || !canDownmix) {
            // CONFIRMED CRASH (production stack trace, every track, while
            // disabled -- the actual cause of a prior "exception on every
            // track" report): `replaceOutputBuffer` can return THIS SAME
            // `ByteBuffer` instance as `inputBuffer` -- observed specifically
            // for a zero-length flush/marker buffer -- and `ByteBuffer.put`
            // throws `IllegalArgumentException("The source buffer is this
            // buffer")` outright when source and destination are identical,
            // never mind the length. When aliased, the bytes are already
            // exactly where they need to be (same backing storage); nothing
            // to copy, so skip straight to returning rather than touching it.
            if (output !== inputBuffer) {
                output.put(inputBuffer)
                output.flip()
            }
            return
        }

        output.order(ByteOrder.LITTLE_ENDIAN)
        val input = inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        var pos = position
        while (pos + bytesPerFrame <= limit) {
            val left = input.getShort(pos)
            val right = input.getShort(pos + 2)
            val mono = ((left + right) / 2).toShort()
            output.putShort(mono)
            output.putShort(mono)
            pos += bytesPerFrame
        }
        inputBuffer.position(limit)
        output.flip()
    }
}

/**
 * One processor per playback engine -- `PlaybackService`'s crossfade coordinator
 * runs two `ExoPlayer`s concurrently during a crossfade, and a `BaseAudioProcessor`
 * carries per-pipeline mutable state (`canDownmix`, the output buffer inherited
 * from the base class), so a single shared instance would be driven by two
 * renderer threads at once and corrupt both signal paths. [MonoDownmixController]
 * pushes every setting change to both.
 */
@OptIn(UnstableApi::class)
object MonoDownmixHolder {
    val processorA = MonoDownmixAudioProcessor()
    val processorB = MonoDownmixAudioProcessor()
}

/**
 * Persisted on/off for mono downmix, synced cross-platform via
 * `audio_settings_json`'s `monoAudioEnabled` key (see
 * `AudioSettingsSyncService`) -- same shape as `AppearanceStore`.
 */
@OptIn(UnstableApi::class)
class MonoDownmixController(context: Context) {
    private val prefs = context.getSharedPreferences("lumimusic_mono", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    init {
        MonoDownmixHolder.processorA.enabled = _enabled.value
        MonoDownmixHolder.processorB.enabled = _enabled.value
    }

    fun setEnabled(value: Boolean) {
        if (value == _enabled.value) return
        _enabled.value = value
        MonoDownmixHolder.processorA.enabled = value
        MonoDownmixHolder.processorB.enabled = value
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
    }
}
