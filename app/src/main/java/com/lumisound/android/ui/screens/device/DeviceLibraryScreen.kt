package com.lumisound.android.ui.screens.device

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import com.lumisound.android.ui.components.Artwork
import com.lumisound.android.ui.components.ChipRow
import com.lumisound.android.ui.components.GlassButton
import com.lumisound.android.ui.components.ScreenTitle
import androidx.compose.material.icons.filled.Refresh
import com.lumisound.android.ui.components.EmptyState
import com.lumisound.android.ui.components.NavChip
import com.lumisound.android.ui.components.SearchField
import com.lumisound.android.ui.components.OneLine
import com.lumisound.android.ui.components.Pill
import com.lumisound.android.ui.components.TrackRow
import com.lumisound.android.ui.components.trackSubtitle
import com.lumisound.android.ui.theme.LocalLumiPalette
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisound.android.AppContainer
import com.lumisound.android.data.db.LocalTrackEntity
import com.lumisound.android.library.AudioPermission
import com.lumisound.android.playback.toPlayable
import kotlinx.coroutines.launch

private enum class Grouping(val label: String) { Songs("Songs"), Artists("Artists"), Albums("Albums"), Folders("Folders") }

/**
 * The music on the device itself. Separate tab from the cloud library rather than a
 * merged "everything" list: the two have different identity and different failure
 * modes, and a user looking for a file they just copied over should not have to guess
 * whether a missing track is a scan problem or a sync problem.
 */
@Composable
fun DeviceLibraryScreen(container: AppContainer, onBack: (() -> Unit)? = null) {
    val scope = rememberCoroutineScope()
    val scanState by container.libraryScanner.state.collectAsStateWithLifecycle()
    var grouping by remember { mutableStateOf(Grouping.Songs) }
    var query by remember { mutableStateOf("") }
    var openGroup by remember { mutableStateOf<String?>(null) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scope.launch { container.libraryScanner.scan() }
    }

    val playback by container.player.state.collectAsStateWithLifecycle()

    val tracks by remember(query, grouping, openGroup) {
        val dao = container.database.localTracks()
        when {
            query.isNotBlank() -> dao.search(query)
            openGroup != null && grouping == Grouping.Artists -> dao.observeByArtist(openGroup!!)
            openGroup != null && grouping == Grouping.Albums -> dao.observeByAlbum(openGroup!!)
            openGroup != null && grouping == Grouping.Folders -> dao.observeByFolder(openGroup!!)
            else -> dao.observeAll()
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val groups by remember(grouping) {
        val dao = container.database.localTracks()
        when (grouping) {
            Grouping.Artists -> dao.observeArtists()
            Grouping.Albums -> dao.observeAlbums()
            Grouping.Folders -> dao.observeFolders()
            Grouping.Songs -> kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    fun play(index: Int) {
        container.player.play(tracks.map(LocalTrackEntity::toPlayable), index)
    }

    val palette = LocalLumiPalette.current

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(
            "This phone",
            subtitle = "${tracks.size} tracks" + (scanState.lastDurationMs?.let { " · scanned in ${it}ms" } ?: ""),
            eyebrow = "On-device music",
            onBack = onBack,
            trailing = {
                // The permission launcher runs the scan itself when granted, so a denied
                // prompt never leaves a scan half-started.
                GlassButton(
                    if (scanState.running) "Scanning…" else "Rescan",
                    Icons.Filled.Refresh,
                    onClick = { if (!scanState.running) permission.launch(AudioPermission.required) },
                )
            },
        )

        ChipRow(
            Grouping.entries.map { entry ->
                NavChip(
                    label = entry.label,
                    icon = when (entry) {
                        Grouping.Songs -> Icons.Filled.MusicNote
                        Grouping.Artists -> Icons.Filled.Person
                        Grouping.Albums -> Icons.Filled.Album
                        Grouping.Folders -> Icons.Filled.Folder
                    },
                    selected = grouping == entry,
                    onClick = { grouping = entry; openGroup = null },
                )
            }
        )

        SearchField(query, { query = it }, "Search this device")

        if (scanState.running && tracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        scanState.error?.let { error ->
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (tracks.isEmpty() && groups.isEmpty()) {
            // Three different situations look identical from a list with nothing in it, so
            // the scan reports which one this is rather than leaving the user to guess.
            val granted = AudioPermission.isGranted(LocalContext.current)
            EmptyState(
                icon = Icons.Filled.LibraryMusic,
                title = when {
                    !granted -> "Permission needed"
                    scanState.hasNonMusicAudioOnly -> "Audio found, but no music"
                    else -> "No music on this device"
                },
                message = when {
                    !granted ->
                        "LumiMusic needs permission to read audio files before it can scan this device."
                    scanState.hasNonMusicAudioOnly ->
                        "${scanState.audioRowsSeen} audio file" +
                            (if (scanState.audioRowsSeen == 1) " is" else "s are") +
                            " on this device, but Android does not classify " +
                            (if (scanState.audioRowsSeen == 1) "it" else "any of them") +
                            " as music — ringtones, notifications and podcasts are excluded. " +
                            "Your cloud library is unaffected."
                    else ->
                        "The scan looked at ${scanState.volumes.size} storage volume" +
                            (if (scanState.volumes.size == 1) "" else "s") +
                            " and found no audio files at all. Your cloud library is unaffected."
                },
                action = {
                    Button(onClick = { permission.launch(AudioPermission.required) }) {
                        Text(if (granted) "Scan again" else "Grant permission")
                    }
                },
            )
            return@Column
        }

        val showingGroupList = grouping != Grouping.Songs && openGroup == null && query.isBlank()
        if (showingGroupList) {
            if (grouping == Grouping.Folders) {
                val coverChangeCount by container.folderCoverArt.changeCount.collectAsStateWithLifecycle()
                var pickingCoverFor by remember { mutableStateOf<String?>(null) }
                val pickCover = rememberLauncherForActivityResult(
                    ActivityResultContracts.PickVisualMedia(),
                ) { uri ->
                    val folder = pickingCoverFor
                    pickingCoverFor = null
                    if (uri != null && folder != null) container.folderCoverArt.setCover(folder, uri)
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(groups, key = { it }) { group ->
                        val cover = remember(group, coverChangeCount) { container.folderCoverArt.cover(group) }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { openGroup = group }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                if (cover != null) {
                                    Artwork(cover, group, 44.dp, corner = 10.dp)
                                } else {
                                    Icon(Icons.Filled.Folder, contentDescription = null, tint = palette.accent)
                                }
                            }
                            Spacer(Modifier.padding(start = 6.dp))
                            OneLine(
                                group.ifBlank { "Unknown" },
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f).padding(start = 8.dp),
                            )
                            TextButton(onClick = {
                                pickingCoverFor = group
                                pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }) { Text(if (cover != null) "Change cover" else "Set cover") }
                            if (cover != null) {
                                TextButton(onClick = { container.folderCoverArt.removeCover(group) }) { Text("Remove") }
                            }
                        }
                    }
                }
                return@Column
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(groups, key = { it }) { group ->
                    OneLine(
                        group.ifBlank { "Unknown" },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openGroup = group }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    )
                }
            }
            return@Column
        }

        openGroup?.let { group ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { openGroup = null }) { Text("‹ ${grouping.label}") }
                OneLine(group, style = MaterialTheme.typography.titleMedium)
            }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(tracks, key = { it.contentUri }) { track ->
                TrackRow(
                    title = track.title,
                    subtitle = trackSubtitle(track.title, track.artist, track.album),
                    // MediaStore serves album art straight off the album id.
                    artworkModel = "content://media/external/audio/albumart/${track.albumId}",
                    fallbackKey = track.contentUri,
                    duration = (track.durationMs / 1000).takeIf { it > 0 }
                        ?.let { "%d:%02d".format(it / 60, it % 60) },
                    isPlaying = playback.isLocalSource && playback.title == track.title,
                    onClick = { play(tracks.indexOfFirst { it.contentUri == track.contentUri }) },
                )
            }
        }
    }
}
