package com.lumisound.android.bridge

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.lumisound.android.BuildConfig
import com.lumisound.android.bridge.api.ArtistApi
import com.lumisound.android.bridge.api.AuthApi
import com.lumisound.android.bridge.api.GifApi
import com.lumisound.android.bridge.api.CloudMusicApi
import com.lumisound.android.bridge.api.DiagnosticsApi
import com.lumisound.android.bridge.api.DiscoveryApi
import com.lumisound.android.bridge.api.GalleryApi
import com.lumisound.android.bridge.api.LibraryDataApi
import com.lumisound.android.bridge.api.PodcastApi
import com.lumisound.android.bridge.api.SocialApi
import com.lumisound.android.bridge.api.StreamingApi
import com.lumisound.android.diagnostics.HttpMetrics
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * One OkHttp client and one Retrofit instance for the whole app, rebuilt only
 * when the base URL changes.
 *
 * The auth interceptor is the single place a credential is attached, so there is
 * exactly one implementation of "which credential does this route want" -- the
 * mistake that cost Lumisound every stream once was a hand-built header missing
 * the `"Bearer "` prefix, and a lone interceptor is how that stays impossible.
 */
class BridgeHttp(
    private val config: BridgeConfig,
    private val tokenStore: TokenStore,
    /** Populated from the stream response's `X-Loudness-Gain-Db` header. */
    private val loudnessStore: LoudnessGainStore,
    /** Counts every request's outcome per route for the diagnostics snapshot. */
    private val httpMetrics: HttpMetrics,
) {

    val gson: Gson = GsonBuilder().setLenient().create()

    /**
     * Shared with ExoPlayer and Coil so stream/artwork requests get the same
     * credential handling (and connection pool) as the JSON calls.
     */
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(AuthInterceptor(config, tokenStore))
        .addInterceptor(httpMetrics.interceptor())
        .addNetworkInterceptor(LoudnessHeaderInterceptor(loudnessStore))
        .apply {
            if (BuildConfig.DEBUG) {
                // BASIC, never HEADERS: the Authorization value must not reach logcat.
                addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
            }
        }
        .build()

    @Volatile
    private var retrofitFor: String = ""

    @Volatile
    private var retrofit: Retrofit = build()

    private fun build(): Retrofit {
        val base = BridgeConfig.normalise(config.baseUrl) + "/"
        retrofitFor = base
        return Retrofit.Builder()
            .baseUrl(base)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    private fun current(): Retrofit {
        val wanted = BridgeConfig.normalise(config.baseUrl) + "/"
        if (wanted != retrofitFor) {
            synchronized(this) {
                if (wanted != retrofitFor) retrofit = build()
            }
        }
        return retrofit
    }

    val auth: AuthApi get() = current().create(AuthApi::class.java)
    val cloudMusic: CloudMusicApi get() = current().create(CloudMusicApi::class.java)
    val libraryData: LibraryDataApi get() = current().create(LibraryDataApi::class.java)
    val diagnostics: DiagnosticsApi get() = current().create(DiagnosticsApi::class.java)
    val streaming: StreamingApi get() = current().create(StreamingApi::class.java)
    val discovery: DiscoveryApi get() = current().create(DiscoveryApi::class.java)
    val social: SocialApi get() = current().create(SocialApi::class.java)
    val podcasts: PodcastApi get() = current().create(PodcastApi::class.java)
    val gallery: GalleryApi get() = current().create(GalleryApi::class.java)
    val artist: ArtistApi get() = current().create(ArtistApi::class.java)
    val gif: GifApi get() = current().create(GifApi::class.java)
}

/**
 * Attaches the right credential for the route: the shared bridge key on
 * `check_auth()`-gated legacy routes, the account session token everywhere else.
 * A request that already carries an Authorization header (the login call itself,
 * or a stream URL built with its own) is left alone.
 */
private class AuthInterceptor(
    private val config: BridgeConfig,
    private val tokenStore: TokenStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header("Authorization") != null) return chain.proceed(request)

        // This client is shared with the player and the image loader, which now also fetch
        // from other hosts: YouTube and SoundCloud thumbnails, podcast artwork and episode
        // audio. A credential goes to the bridge and nowhere else -- anything else would
        // hand the account's session token to whichever CDN served a thumbnail.
        if (!BridgeConfig.isBridgeHost(request.url.host, config.baseUrl)) return chain.proceed(request)

        // The log-ingest route is unauthenticated server-side and does its own
        // per-row attribution from the payload, so the session token has no reason
        // to be sent there at all. (`/api/log-event` and `/bug-report` DO read it,
        // optionally, to attach a user id -- those keep it.)
        if (request.url.encodedPath.trimStart('/') == "internal/logs") return chain.proceed(request)

        val sharedKeyRoute = BridgeConfig.isSharedKeyRoute(request.url.encodedPath)
        val credential = if (sharedKeyRoute) config.sharedApiKey else tokenStore.token
        val builder = request.newBuilder()
        if (!credential.isNullOrEmpty()) builder.header("Authorization", "Bearer $credential")
        // The shared-key routes also read the session token, as `X-Account-Token`: it is
        // how a search uses the account's own YouTube API key and cookies, and
        // `/api/stream/proxy` accepts it as authentication outright -- so a stream still
        // plays on a build with no shared key compiled in.
        if (sharedKeyRoute) tokenStore.token?.let { builder.header("X-Account-Token", it) }
        return chain.proceed(builder.build())
    }
}

/**
 * Captures `X-Loudness-Gain-Db` off a `/user/music/stream` response. The server
 * computes it from the file's analyzed LUFS, and it is the only place that value
 * is ever published -- the track listing does not carry it -- so it has to be
 * read from the playback response itself as a side effect.
 */
private class LoudnessHeaderInterceptor(
    private val store: LoudnessGainStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val gain = response.header("X-Loudness-Gain-Db")?.toFloatOrNull()
        if (gain != null) {
            store.put(chain.request().url.serverPathParam(), gain)
        }
        return response
    }
}

private fun HttpUrl.serverPathParam(): String = queryParameter("path") ?: encodedPath
