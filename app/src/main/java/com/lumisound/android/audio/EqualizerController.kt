package com.lumisound.android.audio

import android.content.Context
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import com.lumisound.android.diagnostics.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqBand(
    val index: Int,
    val centerFrequencyHz: Int,
    val levelMillibel: Int,
)

data class EqState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val bands: List<EqBand> = emptyList(),
    val minLevelMillibel: Int = -1_500,
    val maxLevelMillibel: Int = 1_500,
    val presets: List<String> = emptyList(),
    val currentPreset: Int = -1,
    val bassBoostMillibel: Int = 0,
    val reverbEnabled: Boolean = false,
    /** iOS's `ReverbRoomPreset` raw value -- see REVERB_PRESETS for the mapping. */
    val reverbPresetName: String = "mediumRoom",
    val error: String? = null,
)

/**
 * The system equalizer, attached to the app's own audio session.
 *
 * Android's `Equalizer` reports its own band count and centre frequencies, which
 * vary by device -- so this exposes whatever the device actually has instead of
 * pretending to a fixed ten-band curve and quietly dropping half of it. A preset
 * applied here is the device's own, not a reimplementation.
 *
 * `LoudnessEnhancer` provides the gain stage the cloud library's own
 * `X-Loudness-Gain-Db` cannot: that header only ever attenuates (the player's volume
 * is capped at 1.0), so a quiet track can be brought up here.
 */
class EqualizerController(private val context: Context) {

    private var equalizer: Equalizer? = null
    private var enhancer: LoudnessEnhancer? = null
    private var reverb: PresetReverb? = null
    private var sessionId: Int = 0

    private val _state = MutableStateFlow(EqState())
    val state: StateFlow<EqState> = _state.asStateFlow()

    private val prefs = context.getSharedPreferences("lumimusic_eq", Context.MODE_PRIVATE)

    /**
     * Called when the player's audio session id becomes known. An effect bound to
     * session 0 applies to the whole output mix on some devices and to nothing at all
     * on others, so a real session id is worth waiting for.
     */
    fun attach(audioSessionId: Int) {
        if (audioSessionId == 0 || audioSessionId == sessionId) return
        release()
        sessionId = audioSessionId
        try {
            val eq = Equalizer(0, audioSessionId)
            equalizer = eq
            enhancer = try {
                LoudnessEnhancer(audioSessionId)
            } catch (e: Exception) {
                // Not on every device; the equalizer is still usable without it.
                AppLogger.w("audio", "LoudnessEnhancer unavailable: ${e.javaClass.simpleName}")
                null
            }
            reverb = try {
                PresetReverb(0, audioSessionId).also { rv ->
                    rv.preset = REVERB_PRESETS[prefs.getString(KEY_REVERB_PRESET, null)] ?: PresetReverb.PRESET_MEDIUMROOM
                    rv.enabled = prefs.getBoolean(KEY_REVERB_ENABLED, false)
                }
            } catch (e: Exception) {
                AppLogger.w("audio", "PresetReverb unavailable: ${e.javaClass.simpleName}")
                null
            }
            val enabled = prefs.getBoolean(KEY_ENABLED, false)
            eq.enabled = enabled
            restoreSavedCurve(eq)
            publish()
            AppLogger.i(
                "audio",
                "equalizer attached",
                mapOf("session" to audioSessionId, "bands" to eq.numberOfBands.toInt(), "enabled" to enabled),
            )
        } catch (e: Exception) {
            // A device that refuses to give us an Equalizer is a real, reportable
            // state -- not something to crash or silently show dead sliders over.
            AppLogger.e("audio", "equalizer unavailable", e)
            _state.value = EqState(available = false, error = "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        runCatching { equalizer?.enabled = enabled }
        publish()
    }

    fun setBandLevel(bandIndex: Int, millibel: Int) {
        val eq = equalizer ?: return
        runCatching {
            val clamped = millibel.coerceIn(eq.bandLevelRange[0].toInt(), eq.bandLevelRange[1].toInt())
            eq.setBandLevel(bandIndex.toShort(), clamped.toShort())
            prefs.edit().putInt("$KEY_BAND$bandIndex", clamped).apply()
            // A manual edit leaves preset territory, and saying otherwise would make the
            // preset chips lie about what is actually applied.
            prefs.edit().putInt(KEY_PRESET, -1).apply()
        }
        publish()
    }

    fun applyPreset(presetIndex: Int) {
        val eq = equalizer ?: return
        runCatching {
            eq.usePreset(presetIndex.toShort())
            prefs.edit().putInt(KEY_PRESET, presetIndex).apply()
            for (band in 0 until eq.numberOfBands) {
                prefs.edit().putInt("$KEY_BAND$band", eq.getBandLevel(band.toShort()).toInt()).apply()
            }
        }
        publish()
    }

    fun resetFlat() {
        val eq = equalizer ?: return
        runCatching {
            for (band in 0 until eq.numberOfBands) eq.setBandLevel(band.toShort(), 0)
            prefs.edit().clear().putBoolean(KEY_ENABLED, eq.enabled).apply()
        }
        publish()
    }

    /** Extra gain in millibels, 0..1500. Off at 0. */
    fun setBassBoost(millibel: Int) {
        val clamped = millibel.coerceIn(0, 1_500)
        runCatching {
            enhancer?.setTargetGain(clamped)
            enhancer?.enabled = clamped > 0
            prefs.edit().putInt(KEY_BOOST, clamped).apply()
        }
        publish()
    }

    fun setReverbEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REVERB_ENABLED, enabled).apply()
        runCatching { reverb?.enabled = enabled }
        publish()
    }

    /**
     * [presetName] is one of iOS's `ReverbRoomPreset` raw values -- see
     * [REVERB_PRESETS]. Android's `PresetReverb` has no equivalent to
     * `"cathedral"` (iOS's only preset without a 1:1 match); an unrecognized
     * name falls back to medium room rather than being rejected, same as
     * [restoreSavedCurve] falling back for an out-of-range stored preset.
     */
    fun setReverbPreset(presetName: String) {
        val preset = REVERB_PRESETS[presetName] ?: PresetReverb.PRESET_MEDIUMROOM
        prefs.edit().putString(KEY_REVERB_PRESET, presetName).apply()
        runCatching { reverb?.preset = preset }
        publish()
    }

    /**
     * Approximates a curve defined at [sourceFrequenciesHz] (dB values in
     * [sourceLevelsDb], same length/order) onto this device's own hardware
     * bands by nearest-frequency lookup -- used to apply iOS's fixed 10-band
     * AudioSettings.eqBands curve (see AudioSettingsSyncService) onto
     * whatever bands this specific device actually has, which rarely match
     * iOS's set exactly (Android's Equalizer reports its own band count and
     * centre frequencies per device). Read-only application of an external
     * curve: this device's resulting band levels are never translated back
     * into [sourceFrequenciesHz]'s shape anywhere, because they have no
     * fixed, cross-device meaning the way iOS's 10 bands do.
     */
    fun applyMappedCurve(sourceFrequenciesHz: List<Int>, sourceLevelsDb: List<Float>) {
        val eq = equalizer ?: return
        if (sourceFrequenciesHz.isEmpty() || sourceFrequenciesHz.size != sourceLevelsDb.size) return
        runCatching {
            for (band in 0 until eq.numberOfBands) {
                val centerHz = eq.getCenterFreq(band.toShort()) / 1000
                val nearest = sourceFrequenciesHz.indices
                    .minByOrNull { kotlin.math.abs(sourceFrequenciesHz[it] - centerHz) } ?: continue
                val millibel = (sourceLevelsDb[nearest] * 100).toInt()
                    .coerceIn(eq.bandLevelRange[0].toInt(), eq.bandLevelRange[1].toInt())
                eq.setBandLevel(band.toShort(), millibel.toShort())
                prefs.edit().putInt("$KEY_BAND$band", millibel).apply()
            }
            // A synced curve isn't any locally-named preset.
            prefs.edit().putInt(KEY_PRESET, -1).apply()
        }
        publish()
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { enhancer?.release() }
        runCatching { reverb?.release() }
        equalizer = null
        enhancer = null
        reverb = null
    }

    private fun restoreSavedCurve(eq: Equalizer) {
        val preset = prefs.getInt(KEY_PRESET, -1)
        if (preset >= 0 && preset < eq.numberOfPresets) {
            runCatching { eq.usePreset(preset.toShort()) }
            return
        }
        for (band in 0 until eq.numberOfBands) {
            val saved = prefs.getInt("$KEY_BAND$band", Int.MIN_VALUE)
            if (saved != Int.MIN_VALUE) runCatching { eq.setBandLevel(band.toShort(), saved.toShort()) }
        }
        val boost = prefs.getInt(KEY_BOOST, 0)
        if (boost > 0) setBassBoost(boost)
    }

    private fun publish() {
        val eq = equalizer
        if (eq == null) {
            _state.value = _state.value.copy(available = false)
            return
        }
        _state.value = try {
            EqState(
                available = true,
                enabled = eq.enabled,
                bands = (0 until eq.numberOfBands).map { band ->
                    EqBand(
                        index = band,
                        centerFrequencyHz = eq.getCenterFreq(band.toShort()) / 1000,
                        levelMillibel = eq.getBandLevel(band.toShort()).toInt(),
                    )
                },
                minLevelMillibel = eq.bandLevelRange[0].toInt(),
                maxLevelMillibel = eq.bandLevelRange[1].toInt(),
                presets = (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) },
                currentPreset = prefs.getInt(KEY_PRESET, -1),
                bassBoostMillibel = prefs.getInt(KEY_BOOST, 0),
                reverbEnabled = prefs.getBoolean(KEY_REVERB_ENABLED, false),
                reverbPresetName = prefs.getString(KEY_REVERB_PRESET, null) ?: "mediumRoom",
            )
        } catch (e: Exception) {
            EqState(available = false, error = e.javaClass.simpleName)
        }
    }

    private companion object {
        const val KEY_ENABLED = "eq_enabled"
        const val KEY_PRESET = "eq_preset"
        const val KEY_BAND = "eq_band_"
        const val KEY_BOOST = "eq_boost"
        const val KEY_REVERB_ENABLED = "reverb_enabled"
        const val KEY_REVERB_PRESET = "reverb_preset"

        /**
         * iOS's `ReverbRoomPreset` raw values that have a real Android
         * `PresetReverb` equivalent -- 6 of iOS's 7 (iOS's "cathedral" has
         * no Android counterpart at all; callers fall back to medium room
         * for it). Exposed as the names an Android-side preset picker
         * should offer, same names iOS already uses in its own UI.
         */
        val REVERB_PRESETS: Map<String, Short> = mapOf(
            "smallRoom" to PresetReverb.PRESET_SMALLROOM,
            "mediumRoom" to PresetReverb.PRESET_MEDIUMROOM,
            "largeRoom" to PresetReverb.PRESET_LARGEROOM,
            "mediumHall" to PresetReverb.PRESET_MEDIUMHALL,
            "largeHall" to PresetReverb.PRESET_LARGEHALL,
            "plate" to PresetReverb.PRESET_PLATE,
        )
    }
}
