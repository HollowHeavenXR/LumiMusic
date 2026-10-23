package com.lumisound.android.ui

import com.lumisound.android.ui.screens.settings.GalleryBackgroundScreen
import com.lumisound.android.ui.gallery.GalleryBackdrop
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisound.android.AppContainer
import com.lumisound.android.bridge.AccountState
import com.lumisound.android.ui.aura.Aura
import com.lumisound.android.ui.aura.AuraBackdrop
import com.lumisound.android.ui.aura.LocalAura
import com.lumisound.android.ui.aura.rememberTrackAura
import com.lumisound.android.ui.components.DockTab
import com.lumisound.android.ui.components.OrbitDock
import com.lumisound.android.ui.screens.cloud.CloudLibraryScreen
import com.lumisound.android.ui.screens.cloud.CloudView
import com.lumisound.android.ui.screens.device.DeviceLibraryScreen
import com.lumisound.android.ui.screens.diagnostics.DiagnosticsScreen
import com.lumisound.android.ui.screens.downloads.DownloadsScreen
import com.lumisound.android.ui.screens.eq.EqualizerScreen
import com.lumisound.android.ui.screens.home.HomeScreen
import com.lumisound.android.ui.screens.importer.ImportScreen
import com.lumisound.android.ui.screens.library.AddToPlaylistDialog
import com.lumisound.android.ui.screens.library.FavoritesScreen
import com.lumisound.android.ui.screens.library.LibraryHomeScreen
import com.lumisound.android.ui.screens.library.LibrarySection
import com.lumisound.android.ui.screens.library.PlaylistsScreen
import com.lumisound.android.ui.screens.library.SmartPlaylistsScreen
import com.lumisound.android.ui.screens.notifications.NotificationsScreen
import com.lumisound.android.ui.screens.nowplaying.NowPlayingSheet
import com.lumisound.android.ui.screens.podcasts.PodcastDetailScreen
import com.lumisound.android.ui.screens.podcasts.PodcastsScreen
import com.lumisound.android.ui.screens.search.SearchScreen
import com.lumisound.android.ui.screens.settings.ScrobblingScreen
import com.lumisound.android.ui.screens.settings.SettingsScreen
import com.lumisound.android.ui.screens.signin.SignInScreen
import com.lumisound.android.ui.screens.social.ProfileScreen
import com.lumisound.android.ui.screens.social.SocialScreen
import com.lumisound.android.ui.screens.stats.AchievementsScreen
import com.lumisound.android.ui.screens.stats.RewindScreen
import com.lumisound.android.ui.screens.stats.StatsScreen
import com.lumisound.android.ui.theme.LocalLumiPalette

/** A track waiting to be filed into a playlist. */
private data class PendingPlaylistAdd(
    val title: String,
    val artist: String?,
    val album: String?,
    val songId: String,
    val durationSeconds: Int,
)

/**
 * Sign-in gates everything: this app has no local-only mode yet, because nearly every
 * screen -- the dashboard, search, friends, the cloud library -- is the account's, and
 * pretending otherwise would mean a home screen with nothing in it.
 */
@Composable
fun LumiMusicRoot(container: AppContainer) {
    val accountState by container.account.state.collectAsStateWithLifecycle()

    when (accountState) {
        AccountState.Unknown -> SignInScreen(container, checkingSession = true)
        AccountState.SignedOut -> SignInScreen(container, checkingSession = false)
        is AccountState.SignedIn -> SignedInShell(container)
    }
}

/**
 * The signed-in app: the aura behind everything, the current screen, and the Orbit dock.
 *
 * The aura is computed once, here, from whatever is playing and handed down through
 * [LocalAura], so every screen glows with the same track without asking for it.
 */
@Composable
private fun SignedInShell(container: AppContainer) {
    val palette = LocalLumiPalette.current
    var tab by rememberSaveable { mutableStateOf(DockTab.Home) }
    // Screens pushed over the current tab; back pops one, and switching tabs clears them.
    var stack by remember { mutableStateOf(listOf<Route>()) }
    val push: (Route) -> Unit = { stack = stack + it }
    val pop: () -> Unit = { stack = stack.dropLast(1) }
    BackHandler(enabled = stack.isNotEmpty(), onBack = pop)
    BackHandler(enabled = stack.isEmpty() && tab != DockTab.Home) { tab = DockTab.Home }
    var showNowPlaying by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var pendingAdd by remember { mutableStateOf<PendingPlaylistAdd?>(null) }
    val rawPlayback by container.player.state.collectAsStateWithLifecycle()
    val favoriteIds by container.database.favorites().observeIds()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val playback = rawPlayback.copy(isFavorite = rawPlayback.serverPath in favoriteIds)

    val aura = rememberTrackAura(
        artworkModel = playback.artworkUrl,
        fallbackKey = if (playback.hasQueue) playback.mediaId ?: playback.title.orEmpty() else "",
        idle = Aura.forAccent(palette.accent),
    )

    // The gallery background is imported from Lumisound on iPhone: pulled on every return
    // to the foreground, so a photo added there appears here the next time the app opens.
    val gallery by container.gallery.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { container.gallery.syncIfStale() }
    }
    // The account accent (theme_color) used to be pulled only once, at process start --
    // unlike the gallery above, it never re-checked on a return to the foreground. An
    // accent changed on another device sat stale here until the app was force-killed and
    // relaunched. Same trigger and cadence as the gallery sync above.
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { container.cloudImport.refreshAccentIfStale() }
    }
    // Speed/equalizer/bass-boost set on another device (iOS, or a reinstall here)
    // used to sit in `audio_settings_json` completely unread by this app -- see
    // AudioSettingsSyncService's doc comment for which fields actually apply here
    // and why the rest can't. Same trigger/cadence as the two syncs above; the
    // push side (local changes going back out) runs continuously from the
    // service's own init, not from this lifecycle hook.
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { container.audioSync.pullAndApplyIfStale() }
    }

    CompositionLocalProvider(LocalAura provides aura) {
        AuraBackdrop(
            aura,
            Modifier.fillMaxSize(),
            // Over a photo the glows only tint it; at full strength they would wash it out.
            intensity = if (gallery.showing) 0.55f else 1f,
            backdrop = { GalleryBackdrop(gallery.photos, gallery.settings) },
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    OrbitDock(
                        selected = tab.takeIf { stack.isEmpty() },
                        onSelect = { tab = it; stack = emptyList() },
                        playback = playback,
                        onOpenPlayer = { showNowPlaying = true },
                        onTogglePlay = container.player::togglePlayPause,
                        modifier = Modifier.navigationBarsPadding(),
                    )
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    val screenKey: Any = stack.lastOrNull() ?: tab
                    AnimatedContent(
                        targetState = screenKey,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "screen",
                    ) { key ->
                        Box(Modifier.fillMaxSize()) {
                            Screen(
                                key = key,
                                container = container,
                                push = push,
                                pop = pop,
                                openTab = { tab = it; stack = emptyList() },
                                openImport = { showImport = true },
                                addToPlaylist = { pendingAdd = it },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNowPlaying) {
        NowPlayingSheet(container = container, onDismiss = { showNowPlaying = false })
    }
    if (showImport) {
        ImportScreen(container, onDismiss = { showImport = false })
    }
    pendingAdd?.let { pending ->
        AddToPlaylistDialog(
            container = container,
            title = pending.title,
            artist = pending.artist,
            album = pending.album,
            songId = pending.songId,
            durationSeconds = pending.durationSeconds,
            onDismiss = { pendingAdd = null },
        )
    }
}

@Composable
private fun Screen(
    key: Any,
    container: AppContainer,
    push: (Route) -> Unit,
    pop: () -> Unit,
    openTab: (DockTab) -> Unit,
    openImport: () -> Unit,
    addToPlaylist: (PendingPlaylistAdd) -> Unit,
) {
    when (key) {
        DockTab.Home -> HomeScreen(container = container, onOpen = push, onOpenFriends = { openTab(DockTab.Friends) })
        DockTab.Search -> SearchScreen(container)
        DockTab.Library -> LibraryHomeScreen(
            container = container,
            onOpen = { push(Route.Library(it)) },
            onOpenPlaylist = { push(Route.Library(LibrarySection.Playlists, it)) },
        )
        DockTab.Friends -> SocialScreen(container, onOpen = push)
        Route.Settings -> SettingsScreen(container = container, onOpenImport = openImport, onOpen = push, onBack = pop)
        is Route.Library -> when (key.section) {
            LibrarySection.Cloud, LibrarySection.Offline -> CloudLibraryScreen(
                container = container,
                onOpenImport = openImport,
                onAddToPlaylist = { title, artist, album, songId, duration ->
                    addToPlaylist(PendingPlaylistAdd(title, artist, album, songId, duration))
                },
                initialView = if (key.section == LibrarySection.Offline) CloudView.Offline else CloudView.All,
                onBack = pop,
            )
            LibrarySection.Device -> DeviceLibraryScreen(container, onBack = pop)
            LibrarySection.Favorites -> FavoritesScreen(container, onBack = pop)
            LibrarySection.Playlists -> PlaylistsScreen(container, onBack = pop, initialPlaylistId = key.playlistId)
            LibrarySection.SmartPlaylists -> SmartPlaylistsScreen(container, onBack = pop)
            LibrarySection.Podcasts -> PodcastsScreen(container, onOpen = push, onBack = pop)
        }
        Route.Equalizer -> EqualizerScreen(container, onBack = pop)
        Route.GalleryBackground -> GalleryBackgroundScreen(container, onBack = pop)
        Route.Downloads -> DownloadsScreen(container, onBack = pop)
        Route.Diagnostics -> DiagnosticsScreen(container, onBack = pop)
        Route.Stats -> StatsScreen(container, onBack = pop)
        Route.Rewind -> RewindScreen(container, onBack = pop)
        Route.Achievements -> AchievementsScreen(container, onBack = pop)
        Route.Notifications -> NotificationsScreen(container, onBack = pop)
        Route.Scrobbling -> ScrobblingScreen(container, onBack = pop)
        Route.Podcasts -> PodcastsScreen(container, onOpen = push, onBack = pop)
        is Route.Podcast -> PodcastDetailScreen(container, key.feedUrl, key.title, key.artworkUrl, onBack = pop)
        is Route.Profile -> ProfileScreen(container, key.userId, key.name, onBack = pop)
    }
}
