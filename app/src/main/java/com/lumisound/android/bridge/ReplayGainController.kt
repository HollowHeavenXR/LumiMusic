package com.lumisound.android.bridge

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * On/off for the server-driven loudness attenuation already applied by
 * `PlaybackService`'s `LoudnessListener` (the stream response's
 * `X-Loudness-Gain-Db` header) -- iOS parity for `replayGainEnabled`.
 *
 * Attenuation-only, same as it always was: a too-loud track gets turned
 * down, but a quiet one is never boosted above unity the way iOS's own
 * ReplayGain can (its separate `globalGain` EQ stage goes positive; the
 * ExoPlayer volume this applies through is hard-capped at 1.0). This
 * controller only adds the missing ability to turn the existing attenuation
 * off entirely -- it doesn't add the boost half iOS has.
 *
 * Defaults to on, matching the behavior every build before this toggle
 * existed (the attenuation always applied unconditionally).
 */
class ReplayGainController(context: Context) {
    private val prefs = context.getSharedPreferences("lumimusic_replaygain", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(value: Boolean) {
        if (value == _enabled.value) return
        _enabled.value = value
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
    }
}
