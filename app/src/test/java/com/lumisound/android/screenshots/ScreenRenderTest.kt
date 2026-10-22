package com.lumisound.android.screenshots

import androidx.compose.foundation.layout.Column
import com.lumisound.android.ui.components.ScreenTitle
import com.lumisound.android.ui.screens.downloads.LibraryDownloadPanel
import com.lumisound.android.ui.screens.downloads.LibraryDownloadCallbacks
import com.lumisound.android.download.OfflinePrefs
import com.lumisound.android.download.DownloadManager
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.asImageBitmap
import com.lumisound.android.ui.screens.settings.GalleryCallbacks
import com.lumisound.android.ui.screens.settings.GalleryBackgroundContent
import com.lumisound.android.ui.gallery.LocalGalleryPhotoPainter
import com.lumisound.android.ui.gallery.GalleryBackdrop
import com.lumisound.android.gallery.GalleryTransition
import com.lumisound.android.gallery.GalleryState
import com.lumisound.android.gallery.GallerySettings
import com.lumisound.android.gallery.GalleryPhoto
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import com.lumisound.android.audio.EqBand
import com.lumisound.android.audio.EqState
import com.lumisound.android.bridge.model.AchievementsDto
import com.lumisound.android.bridge.model.CommunityTrackDto
import com.lumisound.android.bridge.model.CompatibilityDto
import com.lumisound.android.bridge.model.DailyPickResponse
import com.lumisound.android.bridge.model.DayStatDto
import com.lumisound.android.bridge.model.EpisodeProgressDto
import com.lumisound.android.bridge.model.FriendActivityDto
import com.lumisound.android.bridge.model.FriendDto
import com.lumisound.android.bridge.model.FriendRequestDto
import com.lumisound.android.bridge.model.FriendRequestsResponse
import com.lumisound.android.bridge.model.HistoryEntryDto
import com.lumisound.android.bridge.model.LifetimeStatsDto
import com.lumisound.android.bridge.model.NotificationDto
import com.lumisound.android.bridge.model.OnThisDayGroupDto
import com.lumisound.android.bridge.model.PinnedTrackDto
import com.lumisound.android.bridge.model.PodcastEpisodeDto
import com.lumisound.android.bridge.model.PodcastShowDto
import com.lumisound.android.bridge.model.PodcastSubscriptionDto
import com.lumisound.android.bridge.model.PresenceDto
import com.lumisound.android.bridge.model.ProfileBadgeDto
import com.lumisound.android.bridge.model.ProfileDto
import com.lumisound.android.bridge.model.ReviewDto
import com.lumisound.android.bridge.model.StreakDto
import com.lumisound.android.bridge.model.StreamTrackDto
import com.lumisound.android.bridge.model.TopArtistDto
import com.lumisound.android.bridge.model.TopTrackDto
import com.lumisound.android.bridge.model.TwinDto
import com.lumisound.android.bridge.model.TwinResponse
import com.lumisound.android.bridge.model.WeeklyMixResponse
import com.lumisound.android.bridge.model.WeeklyMixTrackDto
import com.lumisound.android.data.db.CloudTrackEntity
import com.lumisound.android.data.db.PlaylistEntity
import com.lumisound.android.data.db.PlaylistTrackEntity
import com.lumisound.android.lyrics.LrcParser
import com.lumisound.android.lyrics.LyricsResult
import com.lumisound.android.playback.PlaybackUiState
import com.lumisound.android.playback.QueueItem
import com.lumisound.android.ui.aura.Aura
import com.lumisound.android.ui.aura.AuraBackdrop
import com.lumisound.android.ui.aura.LocalAura
import com.lumisound.android.ui.aura.LocalMotion
import com.lumisound.android.ui.components.DockTab
import com.lumisound.android.ui.components.Loadable
import com.lumisound.android.ui.components.OrbitDock
import com.lumisound.android.ui.screens.cloud.CloudLibraryContent
import com.lumisound.android.ui.screens.cloud.CloudView
import com.lumisound.android.ui.screens.cloud.TrackActions
import com.lumisound.android.ui.screens.eq.EqualizerContent
import com.lumisound.android.ui.screens.home.HomeCallbacks
import com.lumisound.android.ui.screens.home.HomeContent
import com.lumisound.android.ui.screens.home.HomeData
import com.lumisound.android.ui.screens.home.LiveFriend
import com.lumisound.android.ui.screens.library.LibraryHomeContent
import com.lumisound.android.ui.screens.library.LibraryHomeData
import com.lumisound.android.ui.screens.library.LibrarySection
import com.lumisound.android.ui.screens.library.PlaylistDetailContent
import com.lumisound.android.ui.screens.library.PlaylistGridContent
import com.lumisound.android.ui.screens.notifications.NotificationsContent
import com.lumisound.android.ui.screens.nowplaying.NowPlayingContent
import com.lumisound.android.ui.screens.nowplaying.StageActions
import com.lumisound.android.ui.screens.nowplaying.StagePage
import com.lumisound.android.ui.screens.podcasts.PodcastDetailContent
import com.lumisound.android.ui.screens.podcasts.PodcastsCallbacks
import com.lumisound.android.ui.screens.podcasts.PodcastsContent
import com.lumisound.android.ui.screens.podcasts.PodcastsUiState
import com.lumisound.android.ui.screens.search.SearchCallbacks
import com.lumisound.android.ui.screens.search.SearchContent
import com.lumisound.android.ui.screens.search.SearchUiState
import com.lumisound.android.ui.screens.settings.SettingsContent
import com.lumisound.android.ui.screens.settings.SettingsCallbacks
import com.lumisound.android.ui.screens.settings.SettingsUiState
import com.lumisound.android.ui.screens.signin.SignInContent
import com.lumisound.android.ui.screens.signin.SignInForm
import com.lumisound.android.ui.screens.social.FriendWithPresence
import com.lumisound.android.ui.screens.social.ProfileContent
import com.lumisound.android.ui.screens.social.SocialCallbacks
import com.lumisound.android.ui.screens.social.SocialContent
import com.lumisound.android.ui.screens.social.SocialTab
import com.lumisound.android.ui.screens.social.SocialUiState
import com.lumisound.android.ui.screens.stats.AchievementsContent
import com.lumisound.android.ui.screens.stats.RewindContent
import com.lumisound.android.ui.screens.stats.RewindPeriod
import com.lumisound.android.ui.screens.stats.StatsContent
import com.lumisound.android.ui.screens.stats.StatsData
import com.lumisound.android.ui.screens.stats.toSummary
import com.lumisound.android.ui.theme.LumiMusicTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

/**
 * Every screen, rendered from invented bridge responses in the shapes the real endpoints
 * return, with none of the network in between. Each sits on the aura it would have in the
 * app, so the renders show the design as it is actually seen.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    application = android.app.Application::class,
    qualifiers = "w411dp-h891dp-night-xxhdpi",
)
class ScreenRenderTest {

    private val aura = Aura.forKey("youtube:midnight-city")

    private fun capture(
        name: String,
        withDock: Boolean = false,
        dockTab: DockTab = DockTab.Home,
        gallery: GalleryState? = null,
        content: @Composable () -> Unit,
    ) {
        captureRoboImage(filePath = "build/outputs/roborazzi/$name.png") {
            LumiMusicTheme(accentHex = "#EC4079") {
                CompositionLocalProvider(
                    LocalAura provides aura,
                    LocalMotion provides false,
                    LocalGalleryPhotoPainter provides { photo -> fakePhoto(photo.id) },
                ) {
                    AuraBackdrop(
                        aura,
                        Modifier.fillMaxSize(),
                        intensity = if (gallery?.showing == true) 0.55f else 1f,
                        backdrop = { gallery?.let { GalleryBackdrop(it.photos, it.settings) } },
                    ) {
                        content()
                        if (withDock) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                                OrbitDock(dockTab, {}, playing, {}, {})
                            }
                        }
                    }
                }
            }
        }
    }

    private val playing = PlaybackUiState(
        connected = true,
        title = "Midnight City",
        artist = "M83",
        mediaId = "youtube:midnight-city",
        trackUrl = "https://youtube.com/watch?v=midnight-city",
        isPlaying = true,
        positionMs = 71_000,
        durationMs = 244_000,
        hasQueue = true,
        queueSize = 24,
        queueIndex = 2,
        shuffleEnabled = true,
        repeatMode = 2,
        speed = 1f,
        sleepAtMs = null,
    )

    private fun stream(title: String, artist: String, seconds: Int = 214, source: String = "youtube") =
        StreamTrackDto(id = title.lowercase().replace(' ', '-'), title = title, artist = artist, durationSeconds = seconds, source = source)

    private val streams = listOf(
        stream("Midnight City", "M83", 244),
        stream("Tidal", "Lumen Drift", 198, source = "soundcloud"),
        stream("Glass Hours", "Paper Satellites", 305),
        stream("Northern Static", "Sable Coast", 187),
        stream("Coastline", "Neon Harbor", 229),
        stream("Signal Fires", "Glass Meridian", 251),
    )

    private val today = LocalDate.of(2026, 9, 29)
    private val now = OffsetDateTime.parse("2026-09-29T15:30:00Z")

    // --- Sign in -------------------------------------------------------------------------

    @Test
    fun signIn() = capture("01-sign-in") {
        SignInContent(SignInForm(username = "xenus"), checkingSession = false, onChange = {}, onSubmit = {})
    }

    // --- Home ----------------------------------------------------------------------------

    private val homeData = HomeData(
        displayName = "Xenus",
        unreadNotifications = 3,
        dailyPick = Loadable.Ready(DailyPickResponse(streams[0], "Big synths and a night-drive feel, like the Neon Harbor you've had on repeat.")),
        weeklyMix = Loadable.Ready(
            WeeklyMixResponse(List(8) { i -> WeeklyMixTrackDto(title = "Mix Track ${i + 1}", artist = "Artist ${i % 3}", relativePath = "mix/track$i.opus") })
        ),
        discoverMix = Loadable.Ready(streams),
        continueListening = Loadable.Ready(listOf(EpisodeProgressDto("g1", "https://feed.example/a", "Ep. 212 — The Loudness War", 1_260.0, 3_540.0))),
        onThisDay = Loadable.Ready(listOf(OnThisDayGroupDto(2, 2024, streams.take(3)))),
        recentlyPlayed = Loadable.Ready(
            listOf(
                HistoryEntryDto(id = "1", title = "After Hours", artist = "Midnight Arcade", localSongId = "a"),
                HistoryEntryDto(id = "2", title = "Blue Room", artist = "Lumen Drift", localSongId = "b"),
                HistoryEntryDto(id = "3", title = "Chrome Sunset", artist = "Neon Harbor", localSongId = "c"),
                HistoryEntryDto(id = "4", title = "Cold Signal", artist = "Glass Meridian", localSongId = "d"),
            )
        ),
        trending = Loadable.Ready(
            listOf(
                CommunityTrackDto("Espresso", "Sabrina Carpenter", 41, 12),
                CommunityTrackDto("Midnight City", "M83", 22, 7),
                CommunityTrackDto("Tidal", "Lumen Drift", 9, 3),
            )
        ),
        twin = Loadable.Ready(TwinResponse(TwinDto("aurora", "Aurora", null, 78, listOf("M83", "Tycho", "Bonobo")))),
        circle = Loadable.Ready(
            listOf(
                LiveFriend("u1", "Aurora", "Awake", "Tycho", null),
                LiveFriend("u2", "Kai", "Kiara", "Bonobo", null),
                LiveFriend("u3", "Mira", "Midnight City", "M83", null),
            )
        ),
        now = LocalTime.of(15, 30),
        today = today,
    )

    @Test
    fun home() = capture("02-home", withDock = true) { HomeContent(homeData, HomeCallbacks()) }

    // --- Search --------------------------------------------------------------------------

    @Test
    fun searchIdle() = capture("03-search", withDock = true, dockTab = DockTab.Search) {
        SearchContent(
            SearchUiState(
                recent = listOf("m83 midnight city", "lofi beats", "tycho"),
                trending = listOf("espresso", "taylor swift", "phonk", "nightcore", "sleep music", "jazz"),
            ),
            SearchCallbacks(),
        )
    }

    @Test
    fun searchResults() = capture("04-search-results") {
        SearchContent(
            SearchUiState(query = "night drive", submitted = "night drive", streamResults = Loadable.Ready(streams), playingId = "youtube:glass-hours"),
            SearchCallbacks(),
        )
    }

    // --- Library -------------------------------------------------------------------------

    private fun cloudTrack(title: String, artist: String, album: String, seconds: Double, locked: Boolean = false) = CloudTrackEntity(
        serverPath = "$album/$title.opus${if (locked) ".lms" else ""}",
        remoteId = title, title = title, artist = artist, album = album, durationSeconds = seconds,
        genre = "", trackNumber = "", hasArtwork = false, isLocked = locked, ext = "opus",
        filename = "$title.opus", bpm = null, uploadedAt = null, syncedAt = 0,
    )

    private val library = listOf(
        cloudTrack("After Hours", "Midnight Arcade", "Midnight Arcade", 221.0),
        cloudTrack("Big Sky Radio", "Paper Satellites", "Paper Moons", 176.0),
        cloudTrack("Blue Room", "Lumen Drift", "Blue Room Sessions", 284.0, locked = true),
        cloudTrack("Chrome Sunset", "Neon Harbor", "Afterglow Avenue", 224.0),
        cloudTrack("Cold Signal", "Glass Meridian", "Glass Meridian", 247.0),
        cloudTrack("Fog Horn Lullaby", "Sable Coast", "Quiet Orbit", 195.0, locked = true),
    )

    private val playlists = listOf(
        PlaylistEntity("p1", "Night Drive", null, null, null, 42),
        PlaylistEntity("p2", "Focus Flow", null, null, null, 18),
        PlaylistEntity("p3", "Sunday Slow", null, null, null, 27),
        PlaylistEntity("p4", "Gym Rotation", null, null, null, 33),
    )

    @Test
    fun libraryHome() = capture("05-library", withDock = true, dockTab = DockTab.Library) {
        LibraryHomeContent(
            data = LibraryHomeData(
                counts = mapOf(
                    LibrarySection.Cloud to 3545, LibrarySection.Favorites to 212, LibrarySection.Playlists to 4,
                    LibrarySection.Offline to 86, LibrarySection.Device to 640, LibrarySection.Podcasts to 7,
                ),
                recentlyAdded = library,
                playlists = playlists,
            ),
            onOpen = {}, onPlayRecent = { _, _ -> }, onOpenPlaylist = {}, artworkFor = { null },
        )
    }

    @Test
    fun cloudLibrary() = capture("06-cloud-library") {
        CloudLibraryContent(
            tracks = library,
            recentlyAdded = library.take(4),
            favoriteIds = setOf(library[1].serverPath),
            downloadedPaths = setOf(library[2].serverPath),
            playingPath = library[0].serverPath,
            query = "",
            onQueryChange = {},
            view = CloudView.All,
            onViewChange = {},
            artworkModelFor = { null },
            onPlay = { _, _ -> },
            onShuffle = {},
            onImport = {},
            onRefresh = {},
            actions = TrackActions({}, {}, {}, {}, {}, {}),
            onBack = {},
        )
    }

    @Test
    fun playlists() = capture("07-playlists") {
        PlaylistGridContent(playlists, null, onBack = {}, onOpen = {}, onCreate = {}, onRename = {}, onDelete = {})
    }

    @Test
    fun playlistDetail() = capture("08-playlist-detail") {
        PlaylistDetailContent(
            name = "Night Drive",
            playlistId = "p1",
            tracks = library.mapIndexed { i, t -> PlaylistTrackEntity("p1", i, "r$i", null, t.serverPath, t.title, t.artist, t.album, t.durationSeconds) },
            playingTitle = "Blue Room",
            onBack = {}, onPlay = {}, onPlayAt = {}, onRefresh = {}, onRemove = {}, note = null,
        )
    }

    // --- Now Playing ---------------------------------------------------------------------

    private val queue = streams.mapIndexed { i, t -> QueueItem(i, "youtube:${t.id}", t.title, t.artist, isCurrent = i == 0) }

    private fun stage(name: String, page: StagePage, lyrics: LyricsResult? = null, state: PlaybackUiState = playing) = capture(name) {
        NowPlayingContent(
            state = state,
            page = page,
            onPageChange = {},
            lyrics = lyrics,
            queue = queue,
            actions = StageActions(onFavorite = {}, onStartRadio = {}),
            nowMs = 0L,
            aura = aura,
        )
    }

    @Test
    fun nowPlayingRecord() = stage("09-now-playing-record", StagePage.Record)

    @Test
    fun nowPlayingLyrics() = stage(
        "10-now-playing-lyrics",
        StagePage.Lyrics,
        LyricsResult.Synced(
            LrcParser.parse(
                """
                [00:55.00]Streetlights hum a borrowed tune
                [01:02.00]Every window paints the road
                [01:08.00]We drive until the static clears
                [01:14.00]And the radio finds our song
                [01:20.00]Hold the note a little longer
                [01:26.00]Let the city fade to blue
                """.trimIndent()
            )
        ),
    )

    @Test
    fun nowPlayingQueue() = stage("11-now-playing-queue", StagePage.Queue)

    @Test
    fun nowPlayingDetails() = stage("12-now-playing-details", StagePage.Details, state = playing.copy(sleepAtMs = 23 * 60_000L, speed = 1.25f))

    // --- Circle --------------------------------------------------------------------------

    private val friends = listOf(
        FriendWithPresence(FriendDto("u1", "aurora", "Aurora"), PresenceDto("u1", online = true, isPlaying = true, nowPlayingTitle = "Awake", nowPlayingArtist = "Tycho")),
        FriendWithPresence(FriendDto("u2", "kai", "Kai"), PresenceDto("u2", online = true, isPlaying = true, nowPlayingTitle = "Kiara", nowPlayingArtist = "Bonobo")),
        FriendWithPresence(FriendDto("u3", "mira", "Mira"), PresenceDto("u3", online = true)),
        FriendWithPresence(FriendDto("u4", "juno", "Juno", tags = listOf("gym")), PresenceDto("u4", lastSeenAt = "2026-09-29T12:10:00")),
        FriendWithPresence(FriendDto("u5", "theo", "Theo"), null),
    )

    @Test
    fun circle() = capture("13-circle", withDock = true, dockTab = DockTab.Friends) {
        SocialContent(
            SocialUiState(
                tab = SocialTab.Friends,
                friends = Loadable.Ready(friends),
                requests = Loadable.Ready(FriendRequestsResponse(incoming = listOf(FriendRequestDto("r1", "u9", "nova", "Nova")))),
                activity = Loadable.Ready(emptyList()),
                now = now,
            ),
            SocialCallbacks(),
        )
    }

    @Test
    fun circleActivity() = capture("14-circle-activity") {
        SocialContent(
            SocialUiState(
                tab = SocialTab.Activity,
                friends = Loadable.Ready(friends),
                requests = Loadable.Ready(FriendRequestsResponse()),
                activity = Loadable.Ready(
                    listOf(
                        FriendActivityDto("u1", "aurora", "Aurora", "played", "Awake", "Tycho", "2026-09-29T15:20:00"),
                        FriendActivityDto("u2", "kai", "Kai", "favorited", "Kiara", "Bonobo", "2026-09-29T13:02:00"),
                        FriendActivityDto("u3", "mira", "Mira", "played", "Midnight City", "M83", "2026-09-28T22:40:00"),
                        FriendActivityDto("u4", "juno", "Juno", "played", "Coastline", "Neon Harbor", "2026-09-28T09:15:00"),
                    )
                ),
                now = now,
            ),
            SocialCallbacks(),
        )
    }

    @Test
    fun profile() = capture("15-profile") {
        ProfileContent(
            name = "Aurora",
            profile = Loadable.Ready(
                ProfileDto(
                    userId = "u1", username = "aurora", displayName = "Aurora", bio = "Synths, rain, and long drives. Ask me about ambient.",
                    pronouns = "she/her", statusEmoji = "🎧", statusText = "deep in a Tycho phase", isFriend = true, memberSince = "2025-02-11",
                    topGenres = listOf("Ambient", "Synthwave", "Downtempo", "Indie"),
                    topArtists = listOf("Tycho", "M83", "Bonobo", "Boards of Canada"),
                    badges = listOf(ProfileBadgeDto("veteran", "6 Months+", tier = "silver"), ProfileBadgeDto("streak", "30-day streak", tier = "gold")),
                    pinnedTracks = listOf(PinnedTrackDto(title = "Awake", artist = "Tycho")),
                    listeningStreak = StreakDto(12, 41),
                )
            ),
            match = Loadable.Ready(CompatibilityDto(score = 78, sharedArtists = listOf("M83", "Tycho", "Bonobo"), reasons = listOf("You both lean slow and bright", "3 artists in common"))),
            avatarModel = null,
            onBack = {}, onRetry = {}, onAdd = {}, onRemove = {}, onPlay = { _, _ -> },
        )
    }

    // --- Podcasts ------------------------------------------------------------------------

    private val shows = listOf(
        PodcastShowDto("The Loudness War", "Audio Nerds", "https://feed.example/a"),
        PodcastShowDto("Song Exploder", "Hrishikesh Hirway", "https://feed.example/b"),
        PodcastShowDto("Switched On Pop", "Vulture", "https://feed.example/c"),
        PodcastShowDto("Dissect", "Spotify Studios", "https://feed.example/d"),
    )

    @Test
    fun podcasts() = capture("16-podcasts") {
        PodcastsContent(
            PodcastsUiState(
                subscriptions = Loadable.Ready(shows.take(3).mapIndexed { i, s -> PodcastSubscriptionDto("s$i", s.feedUrl, s.title) }),
                trending = Loadable.Ready(shows),
                upNext = Loadable.Ready(listOf(EpisodeProgressDto("g1", "https://feed.example/a", "Ep. 212 — Why everything is loud", 1_260.0, 3_540.0))),
            ),
            PodcastsCallbacks(onBack = {}),
        )
    }

    @Test
    fun podcastDetail() = capture("17-podcast-detail") {
        val episodes = listOf(
            PodcastEpisodeDto("e1", "Ep. 212 — Why everything is loud", "<p>How mastering got <b>louder</b> for thirty years.</p>", "https://a/1.mp3", 3540, "2026-09-24T08:00:00+00:00"),
            PodcastEpisodeDto("e2", "Ep. 211 — The 808", "The drum machine that failed, then won.", "https://a/2.mp3", 2710, "2026-09-17T08:00:00+00:00"),
            PodcastEpisodeDto("e3", "Ep. 210 — Silence", "What the gaps between songs are for.", "https://a/3.mp3", 1980, "2026-09-10T08:00:00+00:00"),
        )
        PodcastDetailContent(
            title = "The Loudness War",
            feedUrl = "https://feed.example/a",
            artworkUrl = null,
            episodes = Loadable.Ready(episodes),
            progress = mapOf(
                "e1" to EpisodeProgressDto("e1", "https://feed.example/a", null, 1_260.0, 3_540.0),
                "e3" to EpisodeProgressDto("e3", "https://feed.example/a", null, 1_980.0, 1_980.0, completed = true),
            ),
            subscription = Loadable.Ready(PodcastSubscriptionDto("s1", "https://feed.example/a", "The Loudness War")),
            playingGuid = null,
            onBack = {}, onRetry = {}, onPlay = { _, _, _ -> }, onPlayNext = {}, onToggleFollow = {},
        )
    }

    // --- Stats ---------------------------------------------------------------------------

    private fun daysBack(n: Int, plays: (Int) -> Int) = (0 until n).mapNotNull { i ->
        val count = plays(i)
        if (count == 0) null else DayStatDto(today.minusDays(i.toLong()).toString(), count, count * 200L)
    }

    private val lifetime = LifetimeStatsDto(
        totalPlays = 4_812,
        totalListenSeconds = 912_340,
        topArtists = listOf(TopArtistDto("M83", 310), TopArtistDto("Tycho", 204), TopArtistDto("Bonobo", 150), TopArtistDto("Neon Harbor", 96)),
        topTracks = listOf(TopTrackDto("Midnight City", "M83", 88), TopTrackDto("Awake", "Tycho", 61)),
    )

    @Test
    fun stats() = capture("18-stats") {
        StatsContent(
            data = StatsData(
                lifetime = Loadable.Ready(lifetime),
                achievements = Loadable.Ready(AchievementsDto(4_812, 912_340, 12, 41, listOf("plays_10"))),
                week = Loadable.Ready(daysBack(7) { i -> listOf(12, 0, 30, 8, 22, 5, 17)[i] }),
                heatmap = Loadable.Ready(daysBack(365) { i -> ((i * 7919) % 13).let { if (it < 4) 0 else it } }),
            ),
            today = today,
            onBack = {}, onRetry = {}, onPlay = { _, _ -> },
        )
    }

    private val month = ReviewDto(
        year = 2026, month = 9, totalPlays = 612, totalListenSeconds = 131_400, distinctArtists = 88, distinctTracks = 301, averageBpm = 118.4,
        topArtists = listOf(TopArtistDto("M83", 64), TopArtistDto("Tycho", 41), TopArtistDto("Bonobo", 33)),
        topTracks = listOf(TopTrackDto("Midnight City", "M83", 21), TopTrackDto("Awake", "Tycho", 14), TopTrackDto("Kiara", "Bonobo", 11)),
        peakDay = DayStatDto("2026-09-13", 58, 14_000),
    )

    @Test
    fun rewindStory() = capture("19-rewind") {
        RewindContent(RewindPeriod.Month, Loadable.Ready(month.toSummary()), page = 1, onPageChange = {}, onPeriodChange = {}, onBack = {}, onRetry = {}, onShare = {})
    }

    @Test
    fun rewindWrap() = capture("20-rewind-wrap") {
        RewindContent(RewindPeriod.Month, Loadable.Ready(month.toSummary()), page = 99, onPageChange = {}, onPeriodChange = {}, onBack = {}, onRetry = {}, onShare = {})
    }

    @Test
    fun achievements() = capture("21-trophy-case") {
        AchievementsContent(
            Loadable.Ready(AchievementsDto(120, 30_000, 3, 9, listOf("plays_10", "plays_50", "plays_100", "hours_1", "streak_3", "streak_7", "night_owl"))),
            onBack = {}, onRetry = {},
        )
    }

    // --- Inbox, settings, equalizer ------------------------------------------------------

    @Test
    fun inbox() = capture("22-inbox") {
        NotificationsContent(
            notifications = Loadable.Ready(
                listOf(
                    NotificationDto("n1", "friend_request", "New Friend Request", "Nova wants to be friends", "2026-09-29T14:02:00"),
                    NotificationDto("n2", "achievement_unlocked", "Badge unlocked: Week Streak", "Seven days in a row.", "2026-09-29T09:00:00"),
                    NotificationDto("n3", "subscription_upload", "New from Neon Harbor", "“Coastline (Live)” is up", "2026-09-26T18:30:00", readAt = "2026-09-26T19:00:00"),
                    NotificationDto("n4", "podcast_episode", "The Loudness War", "Ep. 212 — Why everything is loud", "2026-09-24T08:00:00", readAt = "2026-09-24T09:00:00"),
                    NotificationDto("n5", "playlist_collab", "Kai added 3 tracks", "to Night Drive", "2026-09-10T21:10:00", readAt = "2026-09-11T08:00:00"),
                )
            ),
            readLocally = emptySet(),
            onBack = {}, onRetry = {}, onOpen = {}, onReadAll = {},
            now = now,
        )
    }

    @Test
    fun settings() = capture("23-settings") {
        SettingsContent(
            SettingsUiState(
                displayName = "Xenus", username = "xenus", avatarModel = null, officialBridge = true,
                bridgeUrl = "https://bridge.example", hasSelfHostedKey = false, offlineCount = 86, scanLabel = "640 tracks found", version = "LumiMusic 0.6.0 (10)",
                galleryLabel = "On · 9 photos from your iPhone",
            ),
            SettingsCallbacks(onBack = {}),
        )
    }

    @Test
    fun equalizer() = capture("24-equalizer") {
        EqualizerContent(
            state = EqState(
                available = true,
                enabled = true,
                bands = listOf(EqBand(0, 60, 500), EqBand(1, 230, 250), EqBand(2, 910, -150), EqBand(3, 3_600, 300), EqBand(4, 14_000, 600)),
                presets = listOf("Normal", "Classical", "Dance", "Flat", "Folk", "Heavy Metal", "Hip Hop", "Jazz", "Pop", "Rock"),
                currentPreset = 2,
                bassBoostMillibel = 450,
            ),
            onBack = {}, onEnabled = {}, onBand = { _, _ -> }, onPreset = {}, onReset = {}, onBoost = {},
        )
    }

    // --- Gallery background ----------------------------------------------------------------

    private val galleryState = GalleryState(
        settings = GallerySettings(enabled = true, opacity = 0.55f, blurRadius = 6f, intervalSeconds = 30, transition = GalleryTransition.ZoomBlur, kenBurns = true),
        lumisoundSettings = GallerySettings(enabled = true, opacity = 0.55f, blurRadius = 6f, intervalSeconds = 30, transition = GalleryTransition.ZoomBlur, kenBurns = true),
        followLumisound = true,
        photos = List(9) { GalleryPhoto("photo-$it", "https://bridge.example/user/gallery/images/photo-$it") },
        lastSyncedAt = 1_000_000L,
    )

    @Test
    fun galleryBackground() = capture("25-gallery-background", gallery = galleryState) {
        GalleryBackgroundContent(galleryState, GalleryCallbacks(onBack = {}), nowMs = 1_000_000L + 5 * 60_000L)
    }

    @Test
    fun homeOverGallery() = capture("26-home-gallery", withDock = true, gallery = galleryState) {
        HomeContent(homeData, HomeCallbacks())
    }

    /**
     * A stand-in photo, different per id: a sky, a low sun and two ridges of hills, so the
     * renders show how a real photo sits under the glass rather than a flat colour.
     */
    private val photoCache = HashMap<String, BitmapPainter>()

    private fun fakePhoto(id: String): BitmapPainter = photoCache.getOrPut(id) {
        val palettes = listOf(
            intArrayOf(0xFF1B2A6B.toInt(), 0xFFE86A5B.toInt(), 0xFFFFC677.toInt(), 0xFF2B1B3F.toInt(), 0xFF120C22.toInt()),
            intArrayOf(0xFF0E3B4F.toInt(), 0xFF3FA7A3.toInt(), 0xFFF3E6B5.toInt(), 0xFF1D4A3A.toInt(), 0xFF0B2219.toInt()),
            intArrayOf(0xFF3A1650.toInt(), 0xFFC04B8E.toInt(), 0xFFFFA4C4.toInt(), 0xFF2A1033.toInt(), 0xFF12061A.toInt()),
            intArrayOf(0xFF102642.toInt(), 0xFF4C7BD9.toInt(), 0xFFB9D7FF.toInt(), 0xFF1C2F52.toInt(), 0xFF0A1426.toInt()),
        )
        val seed = id.hashCode().let { if (it < 0) -it else it }
        val c = palettes[seed % palettes.size]
        val w = 360
        val h = 480
        val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.shader = android.graphics.LinearGradient(0f, 0f, 0f, h * 0.7f, c[0], c[1], android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        val sunX = w * (0.25f + (seed % 50) / 100f)
        paint.shader = android.graphics.RadialGradient(sunX, h * 0.55f, w * 0.5f, c[2], 0x00000000, android.graphics.Shader.TileMode.CLAMP)
        canvas.drawCircle(sunX, h * 0.55f, w * 0.5f, paint)
        paint.shader = null
        fun ridge(base: Float, amp: Float, color: Int, phase: Float) {
            val path = android.graphics.Path()
            path.moveTo(0f, h.toFloat())
            var x = 0f
            while (x <= w) {
                path.lineTo(x, base + amp * kotlin.math.sin(x / w * 6.28f * 1.3f + phase))
                x += 6f
            }
            path.lineTo(w.toFloat(), h.toFloat())
            path.close()
            paint.color = color
            canvas.drawPath(path, paint)
        }
        ridge(h * 0.66f, 22f, c[3], seed % 7 / 2f)
        ridge(h * 0.78f, 16f, c[4], seed % 5 / 3f)
        BitmapPainter(bitmap.asImageBitmap())
    }

    // --- Offline ------------------------------------------------------------------------

    @Test
    fun offlineLibrary() = capture("27-offline-library") {
        Column {
            ScreenTitle("Offline", subtitle = "1,204 tracks · 8,930 MB on this phone", eyebrow = "Downloads", onBack = {})
            LibraryDownloadPanel(
                prefs = OfflinePrefs(downloadWholeLibrary = true, wifiOnly = true),
                sync = DownloadManager.LibrarySync(running = true, total = 2_310, saved = 1_204),
                folderLabel = "Android/media/com.lumisound.android/LumiMusic",
                callbacks = LibraryDownloadCallbacks(),
            )
        }
    }
}
