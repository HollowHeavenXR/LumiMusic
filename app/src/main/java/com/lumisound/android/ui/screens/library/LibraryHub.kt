package com.lumisound.android.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisound.android.AppContainer
import com.lumisound.android.bridge.BridgeUrls
import com.lumisound.android.data.db.CloudTrackEntity
import com.lumisound.android.data.db.PlaylistEntity
import com.lumisound.android.playback.toPlayable
import com.lumisound.android.ui.components.BigTile
import com.lumisound.android.ui.components.CollageArt
import com.lumisound.android.ui.components.IconSectionHeader
import com.lumisound.android.ui.components.OneLine
import com.lumisound.android.ui.components.ScreenTitle
import com.lumisound.android.ui.components.ShelfCard
import com.lumisound.android.ui.components.rememberLoadable
import com.lumisound.android.ui.components.trackSubtitle
import com.lumisound.android.ui.theme.SectionTint
import kotlinx.coroutines.launch

/** The crates in the Library, each a full screen of its own. */
enum class LibrarySection(val label: String, val icon: ImageVector) {
    Cloud("Cloud", Icons.Filled.CloudQueue),
    Favorites("Favorites", Icons.Filled.Favorite),
    Playlists("Playlists", Icons.Filled.QueueMusic),
    SmartPlaylists("Smart Playlists", Icons.Filled.AutoAwesome),
    Offline("Offline", Icons.Filled.DownloadDone),
    Device("This phone", Icons.Filled.PhoneAndroid),
    Podcasts("Podcasts", Icons.Filled.Podcasts),
}

data class LibraryHomeData(
    val counts: Map<LibrarySection, Int> = emptyMap(),
    val recentlyAdded: List<CloudTrackEntity> = emptyList(),
    val playlists: List<PlaylistEntity> = emptyList(),
)

/**
 * Library as crates rather than tabs. The old text switcher put five different collections
 * behind one row of words and showed only one at a time; here all six are visible at once as
 * big coloured tiles with their counts, and what is new sits right under them.
 */
@Composable
fun LibraryHomeContent(
    data: LibraryHomeData,
    onOpen: (LibrarySection) -> Unit,
    onPlayRecent: (List<CloudTrackEntity>, Int) -> Unit,
    onOpenPlaylist: (PlaylistEntity) -> Unit,
    artworkFor: (CloudTrackEntity) -> Any?,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            val total = (data.counts[LibrarySection.Cloud] ?: 0) + (data.counts[LibrarySection.Device] ?: 0)
            ScreenTitle("Library", subtitle = "$total tracks across your cloud and this phone", eyebrow = "Your collection")
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LibrarySection.entries.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { section ->
                            val count = data.counts[section]
                            BigTile(
                                title = section.label,
                                subtitle = count?.let { countLabel(section, it) },
                                icon = section.icon,
                                key = "crate-${section.name}",
                                onClick = { onOpen(section) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
        if (data.recentlyAdded.isNotEmpty()) {
            item { IconSectionHeader(Icons.Filled.NewReleases, "Recently added", tint = SectionTint.Recent, onSeeAll = { onOpen(LibrarySection.Cloud) }) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                    items(data.recentlyAdded, key = { it.serverPath }) { track ->
                        ShelfCard(
                            title = track.title,
                            subtitle = trackSubtitle(track.title, track.artist, track.album),
                            artworkModel = artworkFor(track),
                            fallbackKey = track.serverPath,
                            onClick = { onPlayRecent(data.recentlyAdded, data.recentlyAdded.indexOf(track)) },
                        )
                    }
                }
            }
        }
        if (data.playlists.isNotEmpty()) {
            item { IconSectionHeader(Icons.Filled.QueueMusic, "Your playlists", tint = SectionTint.Playlists, onSeeAll = { onOpen(LibrarySection.Playlists) }) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(data.playlists, key = { it.id }) { playlist ->
                        Column(Modifier.width(140.dp).clip(RoundedCornerShape(8.dp)).clickable { onOpenPlaylist(playlist) }) {
                            CollageArt(
                                keys = listOf(playlist.id, playlist.name, "${playlist.id}-2", "${playlist.name}-3"),
                                size = 140.dp,
                                corner = 20.dp,
                            )
                            Spacer(Modifier.height(8.dp))
                            OneLine(playlist.name, style = MaterialTheme.typography.titleSmall)
                            OneLine(
                                "${playlist.trackCount} tracks",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun countLabel(section: LibrarySection, count: Int): String = when (section) {
    LibrarySection.Podcasts -> "$count show${if (count == 1) "" else "s"}"
    LibrarySection.Playlists, LibrarySection.SmartPlaylists -> "$count playlist${if (count == 1) "" else "s"}"
    else -> "$count track${if (count == 1) "" else "s"}"
}

/** The container-bound Library home. */
@Composable
fun LibraryHomeScreen(container: AppContainer, onOpen: (LibrarySection) -> Unit, onOpenPlaylist: (String) -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val db = container.database
    val cloud by db.cloudTracks().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val recent by db.cloudTracks().observeRecentlyAdded(16).collectAsStateWithLifecycle(initialValue = emptyList())
    val favorites by db.favorites().observeIds().collectAsStateWithLifecycle(initialValue = emptyList())
    val playlists by db.playlists().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val smartPlaylists by container.smartPlaylists.playlists.collectAsStateWithLifecycle()
    val offline by db.downloads().observePaths().collectAsStateWithLifecycle(initialValue = emptyList())
    val device by db.localTracks().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val shows = rememberLoadable(Unit, "library.shows") { container.http.podcasts.subscriptions().size }

    LibraryHomeContent(
        data = LibraryHomeData(
            counts = buildMap {
                put(LibrarySection.Cloud, cloud.size)
                put(LibrarySection.Favorites, favorites.size)
                put(LibrarySection.Playlists, playlists.size)
                put(LibrarySection.SmartPlaylists, smartPlaylists.size)
                put(LibrarySection.Offline, offline.size)
                put(LibrarySection.Device, device.size)
                shows.state.valueOrNull?.let { put(LibrarySection.Podcasts, it) }
            },
            recentlyAdded = recent,
            playlists = playlists,
        ),
        onOpen = onOpen,
        onPlayRecent = { list, index ->
            scope.launch {
                container.player.setShuffle(false)
                container.player.play(
                    list.map { it.toPlayable(container.config.baseUrl, container.downloads.isDownloaded(it.serverPath)) },
                    index,
                )
            }
        },
        onOpenPlaylist = { onOpenPlaylist(it.id) },
        artworkFor = { track -> BridgeUrls.cloudArtwork(container.config.baseUrl, track.serverPath) },
    )
}
