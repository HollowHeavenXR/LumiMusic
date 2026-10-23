package com.lumisound.android

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import com.lumisound.android.download.LibraryDownloadWorker
import com.lumisound.android.cloud.ArtworkWarmer
import com.lumisound.android.gallery.GalleryBackgroundStore
import android.content.Context
import com.lumisound.android.audio.AudioSessionHolder
import com.lumisound.android.audio.AudioSettingsSyncService
import com.lumisound.android.audio.EqualizerController
import com.lumisound.android.audio.MonoDownmixController
import com.lumisound.android.bridge.AccountRepository
import com.lumisound.android.bridge.BridgeConfig
import com.lumisound.android.bridge.BridgeHttp
import com.lumisound.android.bridge.LoudnessGainStore
import com.lumisound.android.bridge.TokenStore
import com.lumisound.android.cloud.CloudImportService
import com.lumisound.android.data.AppearanceStore
import com.lumisound.android.data.SearchHistoryStore
import com.lumisound.android.data.db.LumiDatabase
import com.lumisound.android.data.repo.LibraryRepository
import com.lumisound.android.diagnostics.AppLogger
import com.lumisound.android.diagnostics.CrashReporter
import com.lumisound.android.diagnostics.DiagnosticsSnapshotService
import com.lumisound.android.diagnostics.HttpMetrics
import com.lumisound.android.diagnostics.MainThreadWatchdog
import com.lumisound.android.diagnostics.RemoteLogger
import com.lumisound.android.diagnostics.TelemetryUploader
import com.lumisound.android.download.DownloadManager
import com.lumisound.android.library.LibraryScanner
import com.lumisound.android.lyrics.LyricsRepository
import com.lumisound.android.playback.PlayHistoryLogger
import com.lumisound.android.playback.PlayerController
import com.lumisound.android.playback.PodcastProgressTracker
import com.lumisound.android.social.PresenceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Hand-rolled dependency graph, deliberately: one app-scoped object with lazy
 * singletons is the whole requirement here, and an annotation processor buys
 * nothing at this size while costing a build-time dependency on every module.
 */
class AppContainer(private val context: Context) {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val config by lazy { BridgeConfig(context) }
    val tokenStore by lazy { TokenStore(context) }
    val loudnessGains by lazy { LoudnessGainStore() }
    val httpMetrics by lazy { HttpMetrics() }
    val http by lazy { BridgeHttp(config, tokenStore, loudnessGains, httpMetrics) }
    val database by lazy { LumiDatabase.build(context) }

    val remoteLogger by lazy { RemoteLogger(http, http.gson, scope) }
    val telemetry by lazy { TelemetryUploader(context, http, tokenStore, http.gson, scope) }
    val crashReporter by lazy { CrashReporter(context, telemetry) }

    val appearance by lazy { AppearanceStore(context) }
    val account by lazy { AccountRepository(http, tokenStore) }
    val cloudImport by lazy { CloudImportService(http, database, appearance) }
    val libraryRepository by lazy { LibraryRepository(http, database, remoteLogger) }
    val libraryScanner by lazy { LibraryScanner(context, database, remoteLogger) }
    val downloads by lazy { DownloadManager(context, http, config, database, remoteLogger, scope) }
    val artworkWarmer by lazy { ArtworkWarmer(context, database, config, scope) }
    val equalizer by lazy { EqualizerController(context) }
    val monoDownmix by lazy { MonoDownmixController(context) }
    val player by lazy { PlayerController(context, config, scope) }
    val audioSync by lazy { AudioSettingsSyncService(http, equalizer, player, monoDownmix, scope) }
    val lyrics by lazy { LyricsRepository(http) }
    val searchHistory by lazy { SearchHistoryStore(context) }
    val presence by lazy { PresenceService(http, tokenStore, player, scope) }
    val gallery by lazy { GalleryBackgroundStore(context, http, config, tokenStore, http.gson) }

    val diagnostics by lazy {
        DiagnosticsSnapshotService(
            context = context,
            remote = remoteLogger,
            account = account.state,
            tokenStore = tokenStore,
            config = config,
            database = database,
            player = player,
            httpMetrics = httpMetrics,
            scope = scope,
        )
    }

    private val historyLogger by lazy { PlayHistoryLogger(http, tokenStore, player, scope) }
    private val podcastProgress by lazy { PodcastProgressTracker(http, tokenStore, player, scope) }
    private val watchdog by lazy { MainThreadWatchdog(scope) }

    fun onAppStart() {
        // Diagnostics come up first so that anything failing during the rest of
        // startup is already being recorded when it does.
        crashReporter.install()
        telemetry.start()
        watchdog.start()
        crashReporter.consumePreviousCrash()?.let { crash ->
            AppLogger.e("crash", "previous run ended in a crash")
            remoteLogger.log(
                "crash",
                "previous_run_crashed",
                level = "error",
                message = crash.lineSequence().take(6).joinToString(" | "),
                detail = mapOf("report" to crash.take(4_000)),
            )
        }

        player.connect()
        historyLogger.start()
        podcastProgress.start()
        diagnostics.start()

        scope.launch {
            account.restoreSession()
            // Only worth asking once there is a session to ask with.
            if (account.isSignedIn) {
                cloudImport.refreshAccent()
                artworkWarmer.warm()
                scheduleLibraryDownload()
                gallery.sync()
            }
        }
        scope.launch {
            // Every finished import brings a fresh track list: fetch the new covers, and
            // download any new tracks if the whole library is kept on the phone.
            cloudImport.progress.map { it.finishedAt }.filterNotNull().distinctUntilChanged().collect {
                artworkWarmer.warm()
                scheduleLibraryDownload()
            }
        }
        scope.launch {
            // The equalizer can only bind once the player has a real session id.
            AudioSessionHolder.sessionId.collectLatest { id -> if (id != 0) equalizer.attach(id) }
        }
    }

    /** Starts (or keeps running) the whole-library download when it is switched on. */
    fun scheduleLibraryDownload(restart: Boolean = false) {
        val prefs = downloads.settings.state.value
        if (prefs.downloadWholeLibrary) LibraryDownloadWorker.schedule(context, prefs.wifiOnly, restart)
        else LibraryDownloadWorker.cancel(context)
    }
}
