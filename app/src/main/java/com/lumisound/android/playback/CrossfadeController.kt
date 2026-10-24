package com.lumisound.android.playback

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persisted on/off + duration for crossfade -- iOS parity
 * (`AudioSettings.crossfadeEnabled`/`crossfadeDuration`). Device-local only,
 * same as iOS: neither field appears in `audio_settings_json`'s cross-platform
 * sync payload, confirmed against `AudioPlayerManager+ApplySettings.swift`.
 *
 * Deliberately narrower than iOS's own feature: no Smart Auto Crossfade
 * (BPM beatmatching + live-level-driven duration) and no curve picker --
 * just a fixed-duration equal-power fade. Those are a materially larger,
 * separate project layered on top of an already large one (see
 * `CrossfadeCoordinator`'s doc comment for why even this much already
 * touches every track transition in the app).
 */
class CrossfadeController(context: Context) {
    private val prefs = context.getSharedPreferences("lumimusic_crossfade", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** 1..10 seconds -- iOS's own `Stepper` range in `SettingsView+PlaybackAudioSection`. */
    private val _durationSeconds = MutableStateFlow(prefs.getInt(KEY_DURATION, 2).coerceIn(1, 10))
    val durationSeconds: StateFlow<Int> = _durationSeconds.asStateFlow()

    fun setEnabled(value: Boolean) {
        if (value == _enabled.value) return
        _enabled.value = value
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun setDurationSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(1, 10)
        if (clamped == _durationSeconds.value) return
        _durationSeconds.value = clamped
        prefs.edit().putInt(KEY_DURATION, clamped).apply()
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_DURATION = "duration_seconds"
    }
}
