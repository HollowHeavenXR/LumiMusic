package com.lumisound.android.audio

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteOrder
import kotlin.math.sin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "8D audio" -- iOS parity (`AudioPlayerManager+SpecialEffects.start8DRotation`):
 * a slow left/right balance oscillation that reads as sound moving around the
 * listener's head. iOS drives this from a 60Hz `CADisplayLink` (a UI-cadence
 * timer, not sample-accurate) adjusting an `AVAudioMixerNode.pan` property;
 * this does the equivalent math per audio FRAME instead, which is actually
 * more precise, not an approximation of one.
 *
 * The pan law matches iOS's own comment exactly ("~-6dB on the receding
 * side" at its peak): linear balance, not equal-power -- the louder channel
 * is left alone and only the quieter one is attenuated, by `1 - |pan|`.
 * `pan` itself oscillates at `sin(phase) * 0.5`, i.e. -0.5..0.5, so the
 * attenuated channel never drops below half gain (-6dB), same ceiling iOS's
 * own `pan = sin(angle) * 0.5` line enforces.
 *
 * Always present in the chain and gated by [enabled] per-buffer, same
 * reasoning as `MonoDownmixAudioProcessor`: media3 only re-queries which
 * processors are active on a format change, not an arbitrary setting flip
 * mid-playback.
 *
 * UNVERIFIED ON A REAL DEVICE -- same caveat as `MonoDownmixAudioProcessor`,
 * written against the decompiled media3 1.11.1 API with no device/emulator
 * available in this environment to confirm actual audio output.
 */
class EightDAudioProcessor : BaseAudioProcessor() {
    @Volatile var enabled: Boolean = false

    /** 0.02..2.0 Hz -- iOS's own clamp range (`set8DSpeed`), default matches its 0.18Hz default. */
    @Volatile var rotationHz: Double = 0.18

    private var phase = 0.0
    private var canPan = false
    private var sampleRate = 44_100

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        canPan = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT && inputAudioFormat.channelCount == 2
        sampleRate = inputAudioFormat.sampleRate
        return inputAudioFormat
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onFlush() {
        // Matches iOS resetting rotationAngle to 0 at the start of every
        // start8DRotation call -- a seek or track change shouldn't carry
        // over a mid-rotation phase from whatever was playing before.
        phase = 0.0
    }

    override fun queueInput(inputBuffer: java.nio.ByteBuffer) {
        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val bytesPerFrame = inputAudioFormat.bytesPerFrame
        val output = replaceOutputBuffer(limit - position)

        if (!enabled || !canPan) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        output.order(ByteOrder.LITTLE_ENDIAN)
        val input = inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val phaseStep = 2.0 * Math.PI * rotationHz / sampleRate
        var pos = position
        while (pos + bytesPerFrame <= limit) {
            val left = input.getShort(pos)
            val right = input.getShort(pos + 2)
            val pan = (sin(phase) * 0.5)
            val leftGain = if (pan > 0) 1.0 - pan else 1.0
            val rightGain = if (pan < 0) 1.0 + pan else 1.0
            output.putShort((left * leftGain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            output.putShort((right * rightGain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            phase += phaseStep
            if (phase >= 2.0 * Math.PI) phase -= 2.0 * Math.PI
            pos += bytesPerFrame
        }
        inputBuffer.position(limit)
        output.flip()
    }
}

/**
 * One processor per playback engine -- same reasoning as [MonoDownmixHolder]:
 * the crossfade coordinator runs two `ExoPlayer`s concurrently during a
 * crossfade, and sharing one processor instance between two renderer threads
 * would corrupt both signal paths' phase/format state.
 */
object EightDAudioHolder {
    val processorA = EightDAudioProcessor()
    val processorB = EightDAudioProcessor()
}

/**
 * Persisted on/off + rotation speed -- iOS parity for `eightDAudioEnabled`
 * (synced via `audio_settings_json`, see `AudioSettingsSyncService`) and
 * `rotationHz`/`set8DSpeed` (device-local only on iOS too -- its own doc
 * comment says "Persists to UserDefaults", never synced, so this doesn't
 * sync it either).
 */
class EightDAudioController(context: Context) {
    private val prefs = context.getSharedPreferences("lumimusic_8d", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _rotationHz = MutableStateFlow(prefs.getFloat(KEY_ROTATION_HZ, 0.18f).toDouble())
    val rotationHz: StateFlow<Double> = _rotationHz.asStateFlow()

    init {
        EightDAudioHolder.processorA.enabled = _enabled.value
        EightDAudioHolder.processorB.enabled = _enabled.value
        EightDAudioHolder.processorA.rotationHz = _rotationHz.value
        EightDAudioHolder.processorB.rotationHz = _rotationHz.value
    }

    fun setEnabled(value: Boolean) {
        if (value == _enabled.value) return
        _enabled.value = value
        EightDAudioHolder.processorA.enabled = value
        EightDAudioHolder.processorB.enabled = value
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun setRotationHz(hz: Double) {
        val clamped = hz.coerceIn(0.02, 2.0)
        _rotationHz.value = clamped
        EightDAudioHolder.processorA.rotationHz = clamped
        EightDAudioHolder.processorB.rotationHz = clamped
        prefs.edit().putFloat(KEY_ROTATION_HZ, clamped.toFloat()).apply()
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_ROTATION_HZ = "rotation_hz"
    }
}
