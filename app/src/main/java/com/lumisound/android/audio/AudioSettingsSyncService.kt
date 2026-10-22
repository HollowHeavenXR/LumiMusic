package com.lumisound.android.audio

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lumisound.android.bridge.BridgeHttp
import com.lumisound.android.bridge.model.UpdateSettingsRequest
import com.lumisound.android.diagnostics.AppLogger
import com.lumisound.android.playback.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Round-trips the handful of `AudioSettings` fields this app can actually act
 * on (speed, volume, equalizer on/off, bass boost) through the same
 * `audio_settings_json` blob Lumisound (iOS) writes -- a free-form JSON
 * object this class neither owns nor fully understands the shape of: speed
 * aside, fields like pitch, reverb, 8D audio, spatial audio, and crossfade
 * have no Android equivalent at all. Every write here reads the CURRENT blob
 * first and only overwrites the handful of keys below (see [push]), so an
 * Android-side change never wipes an iOS-only field it has no way to
 * represent, and the reverse holds too: pulling a blob shaped by fields this
 * class has never heard of just ignores them rather than failing.
 *
 * `eqBands` is deliberately pull-only (see [pullAndApply]): iOS's curve is a
 * fixed 10-band array at fixed ISO frequencies, while Android's hardware
 * `Equalizer` reports its own band count and centre frequencies per device --
 * there is no way to translate this device's own curve back into that fixed
 * shape, so this class never writes to that key at all, only approximates it
 * onto whatever bands the device actually has (`EqualizerController.
 * applyMappedCurve`).
 */
@OptIn(FlowPreview::class)
class AudioSettingsSyncService(
    private val http: BridgeHttp,
    private val equalizer: EqualizerController,
    private val player: PlayerController,
    private val scope: CoroutineScope,
) {
    /** Suppresses [push] while [pullAndApply] is itself the one writing local state. */
    @Volatile private var applyingRemote = false
    @Volatile private var lastPullAt = 0L

    init {
        // Pushes a merged blob shortly after the user changes something locally.
        // `drop(1)` skips each flow's replayed current value on subscribe --
        // otherwise every launch would immediately push back whatever
        // pullAndApply (or a cold EqualizerController.attach) had just set,
        // undoing the "don't touch fields you didn't change" guarantee above
        // for the brief window before a real local edit happens.
        equalizer.state
            .drop(1)
            .debounce(800)
            .onEach { if (!applyingRemote) push() }
            .launchIn(scope)
        player.state
            .map { it.speed }
            .distinctUntilChanged()
            .drop(1)
            .debounce(800)
            .onEach { if (!applyingRemote) push() }
            .launchIn(scope)
    }

    /** [pullAndApply], unless the last pull was under [minIntervalMs] ago. */
    suspend fun pullAndApplyIfStale(minIntervalMs: Long = STALE_AFTER_MS) {
        if (System.currentTimeMillis() - lastPullAt < minIntervalMs) return
        pullAndApply()
    }

    suspend fun pullAndApply() {
        try {
            val json = http.libraryData.settings().audioSettingsJson
            lastPullAt = System.currentTimeMillis()
            if (json.isNullOrBlank()) return
            val obj = JsonParser.parseString(json).asJsonObject
            applyingRemote = true
            try {
                obj.floatOrNull("speed")?.let { player.setSpeed(it) }
                // Only the overlapping 0..1 range is honored -- see
                // PlayerController.setVolume's doc comment for why a value
                // above 1.0 (iOS's separate boost stage) can't be synced here.
                obj.floatOrNull("volume")?.let { v -> if (v in 0f..1f) player.setVolume(v) }
                obj.booleanOrNull("equalizerEnabled")?.let { equalizer.setEnabled(it) }
                obj.getAsJsonArray("eqBands")?.let { bands ->
                    val levels = bands.mapNotNull { runCatching { it.asFloat }.getOrNull() }
                    if (levels.size == ISO_BAND_FREQUENCIES_HZ.size) {
                        equalizer.applyMappedCurve(ISO_BAND_FREQUENCIES_HZ, levels)
                    }
                }
                val boostEnabled = obj.booleanOrNull("bassBoostEnabled") ?: false
                val boostGainDb = obj.floatOrNull("bassBoostGain") ?: 0f
                equalizer.setBassBoost(if (boostEnabled) (boostGainDb * 100).toInt() else 0)
            } finally {
                applyingRemote = false
            }
        } catch (e: Exception) {
            AppLogger.w("audio", "settings pull failed: ${e.javaClass.simpleName}")
        }
    }

    private suspend fun push() {
        try {
            val current = http.libraryData.settings().audioSettingsJson
            val obj = current?.let { runCatching { JsonParser.parseString(it).asJsonObject }.getOrNull() }
                ?: JsonObject()
            val eq = equalizer.state.value
            obj.addProperty("speed", player.state.value.speed)
            obj.addProperty("equalizerEnabled", eq.enabled)
            obj.addProperty("bassBoostEnabled", eq.bassBoostMillibel > 0)
            obj.addProperty("bassBoostGain", eq.bassBoostMillibel / 100f)
            http.libraryData.updateSettings(UpdateSettingsRequest(audioSettingsJson = obj.toString()))
        } catch (e: Exception) {
            AppLogger.w("audio", "settings push failed: ${e.javaClass.simpleName}")
        }
    }

    private fun JsonObject.floatOrNull(key: String): Float? =
        get(key)?.takeIf { !it.isJsonNull }?.let { runCatching { it.asFloat }.getOrNull() }

    private fun JsonObject.booleanOrNull(key: String): Boolean? =
        get(key)?.takeIf { !it.isJsonNull }?.let { runCatching { it.asBoolean }.getOrNull() }

    private companion object {
        const val STALE_AFTER_MS = 60_000L

        /** Matches AudioPlayerManager+SpatialAudio.swift's fixed 10-band ISO layout. */
        val ISO_BAND_FREQUENCIES_HZ = listOf(32, 64, 125, 250, 500, 1_000, 2_000, 4_000, 8_000, 16_000)
    }
}
