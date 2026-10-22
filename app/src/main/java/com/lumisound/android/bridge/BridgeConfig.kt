package com.lumisound.android.bridge

import android.content.Context
import com.lumisound.android.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Where this install talks to, and with what shared key.
 *
 * The default is the official bridge -- the same host Lumisound ships pointed
 * at, which is the whole point: one account, one bridge, both clients. A
 * self-hoster can override the URL (and supply their own key) from Settings.
 */
class BridgeConfig(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString(KEY_URL, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_URL
        set(value) = prefs.edit().putString(KEY_URL, normalise(value)).apply()

    /**
     * Bearer key for the bridge's `check_auth()`-gated legacy routes (see
     * [isSharedKeyRoute]). Returns the build-injected official key ONLY while
     * still pointed at the official bridge: a self-hoster who hasn't configured
     * their own key must get "", never someone else's secret.
     */
    val sharedApiKey: String
        get() = prefs.getString(KEY_SELF_HOSTED_KEY, null)?.takeIf { it.isNotBlank() }
            ?: if (isOfficialBridge(baseUrl)) BuildConfig.BRIDGE_API_KEY else ""

    fun setSelfHostedKey(value: String?) {
        prefs.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_SELF_HOSTED_KEY) else putString(KEY_SELF_HOSTED_KEY, value)
        }.apply()
    }

    /** Whether a self-hosted key is currently stored -- for the Settings UI to show a state, not the key itself. */
    val hasSelfHostedKey: Boolean
        get() = !prefs.getString(KEY_SELF_HOSTED_KEY, null).isNullOrBlank()

    fun resetToOfficial() = prefs.edit().remove(KEY_URL).apply()

    val isOfficial: Boolean get() = isOfficialBridge(baseUrl)

    companion object {
        private const val PREFS = "lumimusic_bridge"
        private const val KEY_URL = "bridge_url"
        private const val KEY_SELF_HOSTED_KEY = "self_hosted_api_key"

        val DEFAULT_URL: String = BuildConfig.DEFAULT_BRIDGE_URL

        /**
         * Compared NORMALISED, not as raw strings. Lumisound shipped this as an
         * exact `==` once, which meant a stored URL differing only by a trailing
         * slash or a capitalised host silently sent no key at all and every
         * gated route answered 401 with nothing on screen explaining why.
         */
        fun isOfficialBridge(url: String): Boolean = normalise(url) == normalise(DEFAULT_URL)

        fun normalise(url: String): String {
            var text = url.trim().trimEnd('/')
            if (text.isEmpty()) return text
            if (!text.startsWith("http://") && !text.startsWith("https://")) text = "https://$text"
            val schemeEnd = text.indexOf("://") + 3
            val hostEnd = text.indexOf('/', schemeEnd).let { if (it == -1) text.length else it }
            return text.substring(0, schemeEnd).lowercase() +
                text.substring(schemeEnd, hostEnd).lowercase() +
                text.substring(hostEnd).trimEnd('/')
        }

        /** Whether [host] is the configured bridge's own host -- the only one given credentials. */
        fun isBridgeHost(host: String, baseUrl: String): Boolean {
            // Parsed the way OkHttp parses the request's own URL, so the two hosts compare
            // like for like (IPv6 brackets, IDN, case) rather than as hand-split strings.
            val bridgeHost = normalise(baseUrl).toHttpUrlOrNull()?.host ?: return false
            return host.equals(bridgeHost, ignoreCase = true)
        }

        /**
         * The bridge's legacy yt-dlp-backed routes, which `check_auth()` gates
         * with the shared bridge key rather than a per-account JWT. Everything
         * else -- including several `/api/` paths like `/api/artist/bio` and the
         * whole `/api/social/` family -- is JWT-gated, so this is an explicit
         * allowlist and NOT a "starts with /api/" rule.
         */
        fun isSharedKeyRoute(path: String): Boolean {
            val p = path.trimStart('/')
            return p == "api/search" ||
                p.startsWith("api/stream") ||
                p.startsWith("api/download") ||
                p == "api/track" ||
                p == "api/resolve" ||
                p == "api/spotify/resolve" ||
                p.startsWith("api/playlist/") ||
                p == "api/lyrics" ||
                p == "api/lyrics/search" ||
                p == "api/radio" ||
                p == "api/search/trending" ||
                p == "api/search/suggestions"
        }
    }
}
