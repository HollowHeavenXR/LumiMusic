package com.lumisound.android

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okio.Path.Companion.toOkioPath

/**
 * The application, and the owner of the one image loader.
 *
 * Coil builds its own HTTP client by default, which has no idea this app has an account.
 * Cloud artwork (`/user/music/artwork`) is JWT-gated and answers 401 without a token, so
 * every thumbnail in a 3500-track library silently failed while the avatar -- served by the
 * one image route on the bridge that needs no auth -- loaded fine and made it look like
 * images worked in general. Handing Coil the app's own OkHttp client fixes that at the
 * root: the shared auth interceptor applies, and image requests show up in the HTTP
 * metrics like everything else.
 */
class LumiMusicApp : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.onAppStart()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                // The app's client, so artwork carries the session token.
                add(OkHttpNetworkFetcherFactory(callFactory = { container.http.client }))
                // ImageDecoder-backed (API 28+, always true at this app's minSdk 30) --
                // needed for GIF avatars (GifPickerSheet) to actually animate anywhere
                // they're shown, not just decode a static first frame.
                add(AnimatedImageDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder().maxSizePercent(context, 0.20).build()
            }
            .diskCache {
                // Worth having: the same few thousand thumbnails are otherwise refetched on
                // every scroll through the library, over someone's mobile connection.
                // Sized for a whole library's covers, which ArtworkWarmer fetches ahead.
                DiskCache.Builder()
                    .directory(cacheDir.resolve("artwork").toOkioPath())
                    .maxSizeBytes(512L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
}
