package com.lumisound.android.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.lumisound.android.ui.aura.AuraBase
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lumisound.android.bridge.model.CommunityTrackDto
import com.lumisound.android.bridge.model.DailyPickResponse
import com.lumisound.android.bridge.model.EpisodeProgressDto
import com.lumisound.android.bridge.model.HistoryEntryDto
import com.lumisound.android.bridge.model.OnThisDayGroupDto
import com.lumisound.android.bridge.model.StreamTrackDto
import com.lumisound.android.bridge.model.TwinDto
import com.lumisound.android.bridge.model.TwinResponse
import com.lumisound.android.bridge.model.WeeklyMixResponse
import com.lumisound.android.ui.aura.Aura
import com.lumisound.android.ui.components.Artwork
import com.lumisound.android.ui.components.CollageArt
import com.lumisound.android.ui.components.EqualizerBars
import com.lumisound.android.ui.components.Eyebrow
import com.lumisound.android.ui.components.FallbackArt
import com.lumisound.android.ui.components.GlassIconButton
import com.lumisound.android.ui.components.IconSectionHeader
import com.lumisound.android.ui.components.Loadable
import com.lumisound.android.ui.components.LumiCard
import com.lumisound.android.ui.components.OneLine
import com.lumisound.android.ui.components.PagerDots
import com.lumisound.android.ui.components.PlayOrb
import com.lumisound.android.ui.components.ShelfCard
import com.lumisound.android.ui.components.trackSubtitle
import com.lumisound.android.ui.theme.LocalLumiPalette
import com.lumisound.android.ui.theme.SectionTint
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** A friend playing something right now, for the live circle on Home. */
data class LiveFriend(
    val userId: String,
    val name: String,
    val title: String,
    val artist: String?,
    val avatarModel: Any?,
)

/** Everything the dashboard shows, each section loading on its own. */
data class HomeData(
    val displayName: String?,
    val unreadNotifications: Int = 0,
    val avatarModel: Any? = null,
    val dailyPick: Loadable<DailyPickResponse> = Loadable.Loading,
    val weeklyMix: Loadable<WeeklyMixResponse> = Loadable.Loading,
    val discoverMix: Loadable<List<StreamTrackDto>> = Loadable.Loading,
    val continueListening: Loadable<List<EpisodeProgressDto>> = Loadable.Loading,
    val onThisDay: Loadable<List<OnThisDayGroupDto>> = Loadable.Loading,
    val recentlyPlayed: Loadable<List<HistoryEntryDto>> = Loadable.Loading,
    val trending: Loadable<List<CommunityTrackDto>> = Loadable.Loading,
    val twin: Loadable<TwinResponse> = Loadable.Loading,
    val circle: Loadable<List<LiveFriend>> = Loadable.Loading,
    /** Fixed in tests so a render does not depend on the hour it ran. */
    val now: LocalTime = LocalTime.now(),
    val today: LocalDate = LocalDate.now(),
)

/** Where the dashboard's shortcuts and cards lead. */
enum class HomeShortcut { Stats, Rewind, Achievements, Podcasts, Friends, Notifications, Settings }

data class HomeCallbacks(
    val onShortcut: (HomeShortcut) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onPlayStreams: (List<StreamTrackDto>, Int) -> Unit = { _, _ -> },
    val onPlayWeeklyMix: (Int, Boolean) -> Unit = { _, _ -> },
    val onResumeEpisode: (EpisodeProgressDto) -> Unit = {},
    val onPlayHistory: (HistoryEntryDto) -> Unit = {},
    val onPlayByName: (String, String?) -> Unit = { _, _ -> },
    val onPlayTwinMix: () -> Unit = {},
    val weeklyMixArtwork: (String) -> Any? = { null },
    /** The cover for a history entry that is a cloud track, or null for anything else. */
    val historyArtwork: (HistoryEntryDto) -> Any? = { null },
)

/** One card in the spotlight carousel at the top of Home. */
private sealed interface Spotlight {
    val key: String
    data class Pick(val track: StreamTrackDto, val reason: String?) : Spotlight { override val key = "pick" }
    data class Mix(val mix: WeeklyMixResponse) : Spotlight { override val key = "weekly" }
    data class Episode(val progress: EpisodeProgressDto) : Spotlight { override val key = "episode" }
    data class Discover(val tracks: List<StreamTrackDto>) : Spotlight { override val key = "discover" }
    data class Twin(val twin: TwinDto) : Spotlight { override val key = "twin" }
}

/**
 * Home, rebuilt around "what should I play right now" rather than a column of shelves.
 *
 * The top of the screen is a swipeable spotlight -- one big card each for Aria's pick, the
 * Weekly Mix, the podcast episode in progress, Discover and the listening twin -- so the
 * first thing seen is always a single, playable suggestion. Under it, the people: friends
 * listening this minute, with their records turning. Then quick ways back into recent music,
 * and a "memory" from this day in an earlier year. Every section is independent; one that
 * fails or comes back empty simply is not there.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeContent(data: HomeData, callbacks: HomeCallbacks) {
    val spotlights = buildList {
        data.dailyPick.valueOrNull?.pick?.let { add(Spotlight.Pick(it, data.dailyPick.valueOrNull?.reason)) }
        data.weeklyMix.valueOrNull?.takeIf { it.tracks.isNotEmpty() }?.let { add(Spotlight.Mix(it)) }
        data.continueListening.valueOrNull?.firstOrNull()?.let { add(Spotlight.Episode(it)) }
        data.discoverMix.valueOrNull?.takeIf { it.isNotEmpty() }?.let { add(Spotlight.Discover(it)) }
        data.twin.valueOrNull?.twin?.let { add(Spotlight.Twin(it)) }
    }
    val stillLoading = listOf(data.dailyPick, data.weeklyMix, data.discoverMix).any { it is Loadable.Loading }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item { Header(data, callbacks.onShortcut) }

        item {
            when {
                spotlights.isNotEmpty() -> SpotlightPager(spotlights, callbacks)
                stillLoading -> Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LocalLumiPalette.current.accent, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                }
            }
        }

        data.circle.valueOrNull?.takeIf { it.isNotEmpty() }?.let { live ->
            item { IconSectionHeader(Icons.Filled.People, "Live in your circle", tint = SectionTint.Recent, onSeeAll = { callbacks.onShortcut(HomeShortcut.Friends) }) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(live, key = { it.userId }) { friend -> LiveFriendBubble(friend) { callbacks.onPlayByName(friend.title, friend.artist) } }
                }
            }
        }

        item { QuickRow(callbacks.onShortcut) }

        data.recentlyPlayed.valueOrNull?.takeIf { it.isNotEmpty() }?.let { history ->
            item { IconSectionHeader(Icons.Filled.History, "Jump back in", tint = SectionTint.Favorites) }
            item { JumpBackGrid(history.take(6), callbacks.historyArtwork, callbacks.onPlayHistory) }
        }

        data.discoverMix.valueOrNull?.takeIf { it.isNotEmpty() }?.let { tracks ->
            item { IconSectionHeader(Icons.Filled.Explore, "Discover", tint = SectionTint.Recent) }
            item { StreamShelf(tracks, callbacks.onPlayStreams) }
        }

        data.weeklyMix.valueOrNull?.takeIf { it.tracks.isNotEmpty() }?.let { mix ->
            item { IconSectionHeader(Icons.Filled.ViewWeek, "Inside your Weekly Mix", tint = SectionTint.Library) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                    itemsIndexed(mix.tracks, key = { i, t -> "${t.relativePath}#$i" }) { index, track ->
                        ShelfCard(
                            title = track.title,
                            subtitle = trackSubtitle(track.title, track.artist, track.album),
                            artworkModel = callbacks.weeklyMixArtwork(track.relativePath),
                            fallbackKey = track.relativePath,
                            onClick = { callbacks.onPlayWeeklyMix(index, false) },
                            size = 128.dp,
                        )
                    }
                }
            }
        }

        data.onThisDay.valueOrNull?.firstOrNull { it.tracks.isNotEmpty() }?.let { group ->
            item { IconSectionHeader(Icons.Filled.History, "A memory", tint = SectionTint.Device) }
            item { MemoryCard(group) { callbacks.onPlayStreams(group.tracks, 0) } }
        }

        data.trending.valueOrNull?.takeIf { it.isNotEmpty() }?.let { tracks ->
            item { IconSectionHeader(Icons.AutoMirrored.Filled.TrendingUp, "The room right now", tint = SectionTint.Playlists) }
            item { TrendingBoard(tracks.take(6), callbacks.onPlayByName) }
        }
    }
}

@Composable
private fun Header(data: HomeData, onShortcut: (HomeShortcut) -> Unit) {
    val part = when (data.now.hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Up late"
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Eyebrow(data.today.format(DateTimeFormatter.ofPattern("EEEE · d MMMM")), color = LocalLumiPalette.current.accent)
            Spacer(Modifier.height(4.dp))
            Text(part + ",", style = MaterialTheme.typography.displaySmall)
            Text(
                data.displayName ?: "listener",
                style = MaterialTheme.typography.displaySmall,
                color = Color.White.copy(alpha = 0.62f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(
                Icons.Filled.Notifications,
                "Inbox",
                badge = data.unreadNotifications > 0,
                onClick = { onShortcut(HomeShortcut.Notifications) },
            )
            // The avatar is the way into Settings, as it is in most apps people already use.
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .border(2.dp, LocalLumiPalette.current.accent, CircleShape)
                    .clickable { onShortcut(HomeShortcut.Settings) },
            ) {
                FallbackArt(data.displayName ?: "me", Modifier.fillMaxSize())
                data.avatarModel?.let {
                    AsyncImage(model = it, contentDescription = "Settings", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpotlightPager(spotlights: List<Spotlight>, callbacks: HomeCallbacks) {
    val pagerState = rememberPagerState { spotlights.size }
    Column {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
            key = { spotlights[it].key },
        ) { page ->
            SpotlightCard(spotlights[page], callbacks)
        }
        if (spotlights.size > 1) {
            PagerDots(spotlights.size, pagerState.currentPage, Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp))
        }
    }
}

@Composable
private fun SpotlightCard(spotlight: Spotlight, callbacks: HomeCallbacks) {
    val colourKey = when (spotlight) {
        is Spotlight.Pick -> "${spotlight.track.source}:${spotlight.track.id}"
        is Spotlight.Mix -> "weekly-mix"
        is Spotlight.Episode -> spotlight.progress.feedUrl
        is Spotlight.Discover -> "discover-mix"
        is Spotlight.Twin -> spotlight.twin.username
    }
    val aura = Aura.forKey(colourKey)
    val (eyebrow, title, subtitle) = when (spotlight) {
        is Spotlight.Pick -> Triple("Aria's pick for today", spotlight.track.title, spotlight.reason ?: spotlight.track.artist)
        is Spotlight.Mix -> Triple("Weekly Mix", "${spotlight.mix.tracks.size} songs from your cloud", "Built from what you've played most, refreshed every week")
        is Spotlight.Episode -> Triple("Continue listening", spotlight.progress.title ?: "Your episode", remainingText(spotlight.progress))
        is Spotlight.Discover -> Triple("Discover Mix", "${spotlight.tracks.size} songs you don't have yet", spotlight.tracks.take(3).joinToString(" · ") { it.artist })
        is Spotlight.Twin -> Triple("Your listening twin", "${spotlight.twin.similarity}% match with ${spotlight.twin.displayName ?: "@" + spotlight.twin.username}", "Play a mix from their favourites")
    }
    val onPlay: () -> Unit = when (spotlight) {
        is Spotlight.Pick -> ({ callbacks.onPlayStreams(listOf(spotlight.track), 0) })
        is Spotlight.Mix -> ({ callbacks.onPlayWeeklyMix(0, true) })
        is Spotlight.Episode -> ({ callbacks.onResumeEpisode(spotlight.progress) })
        is Spotlight.Discover -> ({ callbacks.onPlayStreams(spotlight.tracks, 0) })
        is Spotlight.Twin -> callbacks.onPlayTwinMix
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(248.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Brush.linearGradient(listOf(aura.primary, aura.secondary.copy(alpha = 0.85f), Color(0xFF10111C))))
            .clickable(onClick = onPlay)
    ) {
        // The art leans out of the top-right corner, a little rotated -- a sleeve pulled halfway
        // out of the crate.
        Box(Modifier.align(Alignment.TopEnd).offset(x = 22.dp, y = (-10).dp).rotate(12f)) {
            when (spotlight) {
                is Spotlight.Pick -> Artwork(spotlight.track.thumbnailUrl?.takeIf { it.isNotBlank() }, colourKey, 150.dp, corner = 24.dp)
                is Spotlight.Mix -> CollageArt(spotlight.mix.tracks.take(4).map { it.relativePath }, 150.dp, corner = 24.dp)
                is Spotlight.Episode -> Artwork(null, colourKey, 150.dp, corner = 24.dp)
                is Spotlight.Discover -> CollageArt(spotlight.tracks.take(4).map { "${it.source}:${it.id}" }, 150.dp, corner = 24.dp)
                is Spotlight.Twin -> Artwork(null, colourKey, 150.dp, corner = 75.dp)
            }
        }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp).padding(end = 70.dp)) {
            Eyebrow(eyebrow, color = Color.White.copy(alpha = 0.85f))
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.82f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        PlayOrb(onClick = onPlay, modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp), size = 54.dp)
    }
}

private fun remainingText(progress: EpisodeProgressDto): String {
    val remaining = (progress.durationSeconds - progress.positionSeconds).toInt()
    return if (remaining > 0) "${(remaining + 59) / 60} minutes left — pick up where you stopped" else "Pick up where you stopped"
}

@Composable
private fun LiveFriendBubble(friend: LiveFriend, onClick: () -> Unit) {
    val palette = LocalLumiPalette.current
    Column(
        Modifier.width(92.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Brush.sweepGradient(listOf(palette.accent, Color(0xFF7F5AF0), palette.accent)))
            )
            Box(Modifier.size(66.dp).clip(CircleShape).background(AuraBase))
            Box(Modifier.size(60.dp).clip(CircleShape)) {
                FallbackArt(friend.name, Modifier.fillMaxSize())
                friend.avatarModel?.let { AsyncImage(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(palette.accent)
                    .border(2.dp, AuraBase, CircleShape),
                contentAlignment = Alignment.Center,
            ) { EqualizerBars(playing = true, color = Color.White, barWidth = 2.dp, height = 10.dp) }
        }
        Spacer(Modifier.height(6.dp))
        OneLine(friend.name, style = MaterialTheme.typography.labelLarge)
        OneLine(friend.title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private data class Quick(val shortcut: HomeShortcut, val label: String, val icon: ImageVector, val tint: Color)

@Composable
private fun QuickRow(onShortcut: (HomeShortcut) -> Unit) {
    val quick = listOf(
        Quick(HomeShortcut.Stats, "Stats", Icons.Filled.BarChart, SectionTint.Library),
        Quick(HomeShortcut.Rewind, "Rewind", Icons.Filled.Replay, SectionTint.Favorites),
        Quick(HomeShortcut.Achievements, "Badges", Icons.Filled.EmojiEvents, SectionTint.Device),
        Quick(HomeShortcut.Podcasts, "Podcasts", Icons.Filled.Podcasts, SectionTint.Offline),
        Quick(HomeShortcut.Friends, "Circle", Icons.Filled.People, SectionTint.Recent),
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(quick, key = { it.shortcut }) { q ->
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(LocalLumiPalette.current.elevatedSurface)
                    .border(1.dp, LocalLumiPalette.current.hairline, CircleShape)
                    .clickable { onShortcut(q.shortcut) }
                    .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(q.tint.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                    Icon(q.icon, contentDescription = null, tint = q.tint, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(q.label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Six recent things as a two-column grid of compact tiles -- the fastest way back in. */
@Composable
private fun JumpBackGrid(history: List<HistoryEntryDto>, artworkFor: (HistoryEntryDto) -> Any?, onPlay: (HistoryEntryDto) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        history.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { entry ->
                    val title = entry.title ?: "Unknown"
                    LumiCard(Modifier.weight(1f), onClick = { onPlay(entry) }, corner = 16.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Artwork(artworkFor(entry), entry.localSongId ?: entry.trackUrl ?: title, 58.dp, corner = 0.dp)
                            Column(Modifier.padding(horizontal = 10.dp)) {
                                Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StreamShelf(tracks: List<StreamTrackDto>, onPlay: (List<StreamTrackDto>, Int) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
        itemsIndexed(tracks, key = { i, t -> "${t.source}:${t.id}#$i" }) { index, track ->
            ShelfCard(
                title = track.title,
                subtitle = trackSubtitle(track.title, track.artist, null),
                artworkModel = track.thumbnailUrl?.takeIf { it.isNotBlank() },
                fallbackKey = "${track.source}:${track.id}",
                onClick = { onPlay(tracks, index) },
            )
        }
    }
}

/**
 * On This Day as a keepsake: three sleeves fanned out on a card, the year set large. A plain
 * shelf of tracks said nothing about why they were there.
 */
@Composable
private fun MemoryCard(group: OnThisDayGroupDto, onPlay: () -> Unit) {
    LumiCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), onClick = onPlay, corner = 28.dp) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 130.dp, height = 110.dp)) {
                group.tracks.take(3).forEachIndexed { index, track ->
                    val angle = listOf(-12f, 0f, 12f)[index]
                    Box(Modifier.offset(x = (index * 22).dp, y = (if (index == 1) 0 else 8).dp).rotate(angle)) {
                        Artwork(track.thumbnailUrl?.takeIf { it.isNotBlank() }, "${track.source}:${track.id}", 84.dp, corner = 12.dp)
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("${group.year}", style = MaterialTheme.typography.displayMedium, color = LocalLumiPalette.current.accent)
                Text(
                    if (group.yearsAgo == 1) "One year ago today" else "${group.yearsAgo} years ago today",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "${group.tracks.size} track${if (group.tracks.size == 1) "" else "s"} you played",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Community trending as a scoreboard: each row's bar is its share of the room's listeners. */
@Composable
private fun TrendingBoard(tracks: List<CommunityTrackDto>, onPlay: (String, String?) -> Unit) {
    val palette = LocalLumiPalette.current
    val top = tracks.maxOf { it.listenerCount }.coerceAtLeast(1)
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tracks.forEachIndexed { index, track ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.elevatedSurface)
                    .clickable { onPlay(track.title, track.artist) }
            ) {
                Box(Modifier.matchParentSize()) {
                    Box(
                        Modifier
                            .fillMaxWidth(track.listenerCount.toFloat() / top)
                            .fillMaxHeight()
                            .background(Brush.horizontalGradient(listOf(palette.accent.copy(alpha = 0.30f), palette.accent.copy(alpha = 0.06f))))
                    )
                }
                Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(28.dp))
                    Column(Modifier.weight(1f)) {
                        OneLine(track.title, style = MaterialTheme.typography.titleSmall)
                        OneLine(track.artist.orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${track.listenerCount} listening", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
