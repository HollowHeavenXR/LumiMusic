package com.lumisound.android.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lumisound.android.bridge.model.StreamTrackDto
import com.lumisound.android.data.db.CloudTrackEntity
import com.lumisound.android.ui.aura.Aura
import com.lumisound.android.ui.components.Artwork
import com.lumisound.android.ui.components.BigTile
import com.lumisound.android.ui.components.EqualizerBars
import com.lumisound.android.ui.components.Eyebrow
import com.lumisound.android.ui.components.GlassButton
import com.lumisound.android.ui.components.GlowButton
import com.lumisound.android.ui.components.PlayOrb
import com.lumisound.android.ui.components.SegmentedPill
import com.lumisound.android.ui.components.streamSubtitle
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.lumisound.android.ui.components.EmptyState
import com.lumisound.android.ui.components.IconSectionHeader
import com.lumisound.android.ui.components.Loadable
import com.lumisound.android.ui.components.LoadableSection
import com.lumisound.android.ui.components.NavChip
import com.lumisound.android.ui.components.OneLine
import com.lumisound.android.ui.components.ScreenTitle
import com.lumisound.android.ui.components.SearchField
import com.lumisound.android.ui.components.StreamTrackActions
import com.lumisound.android.ui.components.StreamTrackRow
import com.lumisound.android.ui.components.TrackRow
import com.lumisound.android.ui.components.trackSubtitle
import com.lumisound.android.ui.screens.cloud.asClock
import com.lumisound.android.ui.theme.LocalLumiPalette
import com.lumisound.android.ui.theme.SectionTint

/** Where a search looks. The first two go through the bridge's yt-dlp catalogue. */
enum class SearchSource(val label: String, val bridgeSource: String?) {
    YouTube("YouTube", "youtube"),
    SoundCloud("SoundCloud", "soundcloud"),
    Library("Your cloud", null),
}

data class SearchUiState(
    val query: String = "",
    val source: SearchSource = SearchSource.YouTube,
    /** The query the current results are for; null before the first search. */
    val submitted: String? = null,
    val streamResults: Loadable<List<StreamTrackDto>> = Loadable.Ready(emptyList()),
    val libraryResults: List<CloudTrackEntity> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val recent: List<String> = emptyList(),
    val trending: List<String> = emptyList(),
    val playingId: String? = null,
)

data class SearchCallbacks(
    val onQueryChange: (String) -> Unit = {},
    val onSubmit: (String) -> Unit = {},
    val onSourceChange: (SearchSource) -> Unit = {},
    val onPlayStream: (List<StreamTrackDto>, Int) -> Unit = { _, _ -> },
    val onShuffleStreams: (List<StreamTrackDto>) -> Unit = {},
    val streamActions: StreamTrackActions = StreamTrackActions(),
    val onPlayLibrary: (List<CloudTrackEntity>, Int) -> Unit = { _, _ -> },
    val libraryArtwork: (CloudTrackEntity) -> Any? = { null },
    val onClearRecent: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

/** A mood tile on the idle Search screen and the query it runs. */
data class Mood(val label: String, val query: String, val icon: ImageVector)

val MOODS = listOf(
    Mood("Late night", "late night chill mix", Icons.Filled.NightsStay),
    Mood("Focus", "deep focus instrumental", Icons.Filled.Psychology),
    Mood("Workout", "workout motivation mix", Icons.Filled.FitnessCenter),
    Mood("Lo-fi", "lofi hip hop beats", Icons.Filled.Headphones),
    Mood("Throwback", "2000s throwback hits", Icons.Filled.History),
    Mood("Road trip", "road trip songs", Icons.Filled.DirectionsCar),
    Mood("Rainy day", "rainy day acoustic", Icons.Filled.WaterDrop),
    Mood("Party", "party dance hits", Icons.Filled.Celebration),
)

/**
 * Search, for the whole catalogue rather than just what is already in the library.
 *
 * Before anything is typed the screen is a canvas rather than a blank field: recent queries
 * as chips, what everyone is searching as a ranked ticker, and a grid of moods that each run
 * a search. Results lead with one big "top result" you can play in a tap, then the rest.
 * YouTube and SoundCloud go through the bridge; "Your cloud" searches the local mirror.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchContent(state: SearchUiState, callbacks: SearchCallbacks) {
    val palette = LocalLumiPalette.current
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Search", eyebrow = "Find anything")
        SearchField(
            value = state.query,
            onValueChange = callbacks.onQueryChange,
            placeholder = when (state.source) {
                SearchSource.Library -> "Songs, artists, albums in your cloud"
                else -> "What do you want to hear?"
            },
            onSubmit = { callbacks.onSubmit(state.query) },
        )
        Spacer(Modifier.height(12.dp))
        SegmentedPill(
            options = SearchSource.entries,
            selected = state.source,
            label = { it.label },
            onSelect = callbacks.onSourceChange,
        )
        Spacer(Modifier.height(6.dp))

        val showingResults = state.source == SearchSource.Library && state.query.isNotBlank() ||
            state.source != SearchSource.Library && state.submitted != null && state.submitted == state.query.trim()

        if (!showingResults) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (state.suggestions.isNotEmpty() && state.query.isNotBlank()) {
                    items(state.suggestions, key = { "s-$it" }) { suggestion ->
                        QueryRow(Icons.Filled.Search, suggestion, onClick = { callbacks.onSubmit(suggestion) })
                    }
                    return@LazyColumn
                }
                if (state.recent.isNotEmpty()) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Eyebrow("Recent", modifier = Modifier.weight(1f))
                            TextButton(onClick = callbacks.onClearRecent) { Text("Clear") }
                        }
                    }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.recent, key = { "r-$it" }) { query ->
                                Row(
                                    Modifier
                                        .clip(CircleShape)
                                        .background(palette.elevatedSurface)
                                        .border(1.dp, palette.hairline, CircleShape)
                                        .clickable { callbacks.onSubmit(query) }
                                        .padding(horizontal = 14.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(query, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
                if (state.trending.isNotEmpty()) {
                    item { IconSectionHeader(Icons.AutoMirrored.Filled.TrendingUp, "Everyone's searching", tint = SectionTint.Playlists) }
                    item {
                        FlowRow(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.trending.take(10).forEachIndexed { index, query ->
                                Row(
                                    Modifier
                                        .clip(CircleShape)
                                        .background(palette.elevatedSurface)
                                        .clickable { callbacks.onSubmit(query) }
                                        .padding(start = 5.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier.size(24.dp).clip(CircleShape).background(if (index < 3) palette.accent else Color.White.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(query, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
                item { IconSectionHeader(Icons.Filled.Mood, "Browse by mood", tint = SectionTint.Offline) }
                item {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MOODS.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                pair.forEach { mood ->
                                    BigTile(
                                        title = mood.label,
                                        subtitle = null,
                                        icon = mood.icon,
                                        key = "mood-${mood.label}",
                                        onClick = { callbacks.onSubmit(mood.query) },
                                        modifier = Modifier.weight(1f),
                                        height = 96.dp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            return@Column
        }

        if (state.source == SearchSource.Library) {
            LibraryResults(state, callbacks)
            return@Column
        }

        LoadableSection(state.streamResults, onRetry = callbacks.onRetry) { results ->
            if (results.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Search,
                    title = "No results",
                    message = "Nothing on ${state.source.label} matched “${state.submitted}”.",
                )
                return@LoadableSection
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item { TopResult(results.first(), state.playingId, onPlay = { callbacks.onPlayStream(results, 0) }, onRadio = callbacks.streamActions.onRadio) }
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Songs", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        GlassButton("Shuffle", Icons.Filled.Shuffle, onClick = { callbacks.onShuffleStreams(results) })
                        GlowButton("Play all", Icons.Filled.PlayArrow, onClick = { callbacks.onPlayStream(results, 0) })
                    }
                }
                itemsIndexed(results.drop(1), key = { i, t -> "${t.source}:${t.id}#$i" }) { index, track ->
                    StreamTrackRow(
                        track = track,
                        isPlaying = state.playingId == "${track.source}:${track.id}",
                        onClick = { callbacks.onPlayStream(results, index + 1) },
                        actions = callbacks.streamActions,
                    )
                }
            }
        }
    }
}

/** The first result, big: the one most people wanted, playable without reading the list. */
@Composable
private fun TopResult(track: StreamTrackDto, playingId: String?, onPlay: () -> Unit, onRadio: ((StreamTrackDto) -> Unit)?) {
    val aura = Aura.forKey("${track.source}:${track.id}")
    val playing = playingId == "${track.source}:${track.id}"
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Eyebrow("Top result", modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(aura.primary.copy(alpha = 0.55f), aura.secondary.copy(alpha = 0.25f), Color(0x2207080F))))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(26.dp))
                .clickable(onClick = onPlay)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Artwork(track.thumbnailUrl?.takeIf { it.isNotBlank() }, "${track.source}:${track.id}", 104.dp, corner = 20.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    OneLine(streamSubtitle(track), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PlayOrb(onClick = onPlay, size = 44.dp)
                        if (playing) EqualizerBars(playing = true, color = LocalLumiPalette.current.accent)
                        if (onRadio != null && track.source == "youtube") {
                            GlassButton("Radio", Icons.Filled.Radio, onClick = { onRadio(track) })
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun LibraryResults(state: SearchUiState, callbacks: SearchCallbacks) {
    if (state.libraryResults.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.CloudQueue,
            title = "Not in your cloud",
            message = "Nothing in your cloud library matches that. Try YouTube or SoundCloud instead.",
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        itemsIndexed(state.libraryResults, key = { _, t -> t.serverPath }) { index, track ->
            TrackRow(
                title = track.title,
                subtitle = trackSubtitle(track.title, track.artist, track.album),
                artworkModel = callbacks.libraryArtwork(track),
                fallbackKey = track.serverPath,
                duration = track.durationSeconds.asClock(),
                isPlaying = state.playingId == track.serverPath,
                isLocked = track.isLocked,
                formatExt = track.ext,
                sampleRate = track.sampleRate,
                onClick = { callbacks.onPlayLibrary(state.libraryResults, index) },
            )
        }
    }
}

@Composable
private fun QueryRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        OneLine(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.NorthWest, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
}
