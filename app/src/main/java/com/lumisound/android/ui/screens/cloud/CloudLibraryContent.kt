package com.lumisound.android.ui.screens.cloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lumisound.android.data.db.CloudTrackEntity
import com.lumisound.android.ui.components.CapsuleToolbar
import com.lumisound.android.ui.components.ChipRow
import com.lumisound.android.ui.components.EmptyState
import com.lumisound.android.ui.components.GlassButton
import com.lumisound.android.ui.components.GlowButton
import com.lumisound.android.ui.components.IconSectionHeader
import com.lumisound.android.ui.components.NavChip
import com.lumisound.android.ui.components.ScreenTitle
import com.lumisound.android.ui.components.SearchField
import com.lumisound.android.ui.components.ShelfCard
import com.lumisound.android.ui.components.ToolbarAction
import com.lumisound.android.ui.components.TrackRow
import com.lumisound.android.ui.components.trackSubtitle
import com.lumisound.android.ui.theme.LocalLumiPalette
import com.lumisound.android.ui.theme.SectionTint

/** What a row's overflow menu can do. */
data class TrackActions(
    val onPlayNext: (CloudTrackEntity) -> Unit,
    val onEnqueue: (CloudTrackEntity) -> Unit,
    val onToggleFavorite: (CloudTrackEntity) -> Unit,
    val onAddToPlaylist: (CloudTrackEntity) -> Unit,
    val onDownload: (CloudTrackEntity) -> Unit,
    val onRemoveDownload: (CloudTrackEntity) -> Unit,
)

/** Which library view the chip row is on. */
enum class CloudView(val label: String) { All("All"), Recent("Recently added"), Offline("Offline"), Favorites("Favorites") }

/**
 * The cloud library, with no container behind it.
 *
 * Laid out the way Lumisound lays out a library screen: a floating cluster of actions, a
 * large title, a rounded search field, a row of view chips, then sections with their own
 * tinted icon. A flat list of three thousand rows is complete and useless as a way in.
 */
@Composable
fun CloudLibraryContent(
    tracks: List<CloudTrackEntity>,
    recentlyAdded: List<CloudTrackEntity>,
    favoriteIds: Set<String>,
    downloadedPaths: Set<String>,
    playingPath: String?,
    query: String,
    onQueryChange: (String) -> Unit,
    view: CloudView,
    onViewChange: (CloudView) -> Unit,
    artworkModelFor: (CloudTrackEntity) -> Any?,
    onPlay: (List<CloudTrackEntity>, Int) -> Unit,
    onShuffle: (List<CloudTrackEntity>) -> Unit,
    onImport: () -> Unit,
    onRefresh: () -> Unit,
    actions: TrackActions,
    onBack: (() -> Unit)? = null,
) {
    val palette = LocalLumiPalette.current
    var menuFor by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(
            "Cloud",
            subtitle = "${tracks.size} tracks · ${downloadedPaths.size} offline",
            eyebrow = "Your server library",
            onBack = onBack,
            trailing = {
                CapsuleToolbar(
                    listOf(
                        ToolbarAction(Icons.Filled.Refresh, "Refresh", onClick = onRefresh),
                        ToolbarAction(Icons.Filled.Add, "Import", onClick = onImport),
                    )
                )
            },
        )
        SearchField(query, onQueryChange, "Search songs, artists, albums…")
        ChipRow(
            CloudView.entries.map { entry ->
                NavChip(
                    label = entry.label,
                    icon = when (entry) {
                        CloudView.All -> Icons.Filled.LibraryMusic
                        CloudView.Recent -> Icons.Filled.NewReleases
                        CloudView.Offline -> Icons.Filled.Download
                        CloudView.Favorites -> Icons.Filled.CloudQueue
                    },
                    selected = view == entry,
                    onClick = { onViewChange(entry) },
                )
            }
        )

        if (tracks.isEmpty() && query.isBlank() && view == CloudView.All) {
            EmptyState(
                icon = Icons.Filled.CloudQueue,
                title = "Your cloud library is empty here",
                message = "Import this account's cloud data to pull its tracks, favorites and playlists onto this device.",
                action = { Button(onClick = onImport) { Text("Import cloud data") } },
            )
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (query.isBlank() && view == CloudView.All) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        GlowButton("Play all", Icons.Filled.PlayArrow, onClick = { onPlay(tracks, 0) }, modifier = Modifier.weight(1f))
                        GlassButton("Shuffle", Icons.Filled.Shuffle, onClick = { onShuffle(tracks) }, modifier = Modifier.weight(1f))
                    }
                }

                if (recentlyAdded.isNotEmpty()) {
                    item {
                        IconSectionHeader(
                            Icons.Filled.NewReleases,
                            "Recently added",
                            tint = SectionTint.Recent,
                            onSeeAll = { onViewChange(CloudView.Recent) },
                        )
                    }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 14.dp)) {
                            items(recentlyAdded, key = { it.serverPath }) { track ->
                                ShelfCard(
                                    title = track.title,
                                    subtitle = trackSubtitle(track.title, track.artist, track.album),
                                    artworkModel = artworkModelFor(track),
                                    fallbackKey = track.serverPath,
                                    onClick = { onPlay(recentlyAdded, recentlyAdded.indexOf(track)) },
                                )
                            }
                        }
                    }
                }

                item { IconSectionHeader(Icons.Filled.LibraryMusic, "All tracks", tint = SectionTint.Library) }
            }

            items(tracks, key = { it.serverPath }) { track ->
                Box {
                    TrackRow(
                        title = track.title,
                        subtitle = trackSubtitle(track.title, track.artist, track.album),
                        artworkModel = artworkModelFor(track),
                        fallbackKey = track.serverPath,
                        duration = track.durationSeconds.asClock(),
                        isPlaying = playingPath == track.serverPath,
                        isFavorite = track.serverPath in favoriteIds,
                        isDownloaded = track.serverPath in downloadedPaths,
                        isLocked = track.isLocked,
                        formatExt = track.ext,
                        sampleRate = track.sampleRate,
                        onClick = { onPlay(tracks, tracks.indexOfFirst { it.serverPath == track.serverPath }) },
                        onMenu = { menuFor = track.serverPath },
                    )
                    DropdownMenu(expanded = menuFor == track.serverPath, onDismissRequest = { menuFor = null }) {
                        val downloaded = track.serverPath in downloadedPaths
                        val favorite = track.serverPath in favoriteIds
                        DropdownMenuItem(
                            text = { Text("Play next") },
                            onClick = { menuFor = null; actions.onPlayNext(track) },
                        )
                        DropdownMenuItem(
                            text = { Text("Add to queue") },
                            onClick = { menuFor = null; actions.onEnqueue(track) },
                        )
                        DropdownMenuItem(
                            text = { Text(if (favorite) "Remove favorite" else "Add favorite") },
                            onClick = { menuFor = null; actions.onToggleFavorite(track) },
                        )
                        DropdownMenuItem(
                            text = { Text("Add to playlist…") },
                            onClick = { menuFor = null; actions.onAddToPlaylist(track) },
                        )
                        DropdownMenuItem(
                            text = { Text(if (downloaded) "Remove download" else "Download for offline") },
                            onClick = {
                                menuFor = null
                                if (downloaded) actions.onRemoveDownload(track) else actions.onDownload(track)
                            },
                        )
                    }
                }
            }

            if (tracks.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            when (view) {
                                CloudView.Offline -> "Nothing saved offline yet."
                                CloudView.Favorites -> "No favorites in your cloud library yet."
                                else -> "Nothing matches that."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Unspecified,
                        )
                        if (query.isNotBlank()) {
                            TextButton(onClick = { onQueryChange("") }) { Text("Clear search") }
                        }
                    }
                }
            }
        }
    }
}
