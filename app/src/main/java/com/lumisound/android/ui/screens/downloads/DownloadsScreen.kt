package com.lumisound.android.ui.screens.downloads

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisound.android.AppContainer
import com.lumisound.android.bridge.BridgeUrls
import com.lumisound.android.ui.components.Artwork
import com.lumisound.android.ui.components.EmptyState
import com.lumisound.android.ui.components.ScreenTitle
import com.lumisound.android.ui.theme.LocalLumiPalette
import kotlinx.coroutines.launch

/**
 * Tracks saved for offline playback. A locked track's file stays masked on disk
 * exactly as the server sent it -- the player unmasks it on the way to the decoder,
 * so an offline copy is no more playable outside this app than a streamed one.
 */
@Composable
fun DownloadsScreen(container: AppContainer, onBack: (() -> Unit)? = null) {
    val scope = rememberCoroutineScope()
    val downloads by container.database.downloads().observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    // DownloadEntity itself carries no title/artist (it's just the on-disk
    // transfer record) -- the matching cloud_tracks row, keyed by the same
    // serverPath, is where that metadata actually lives. Without this, every
    // row below rendered `serverPath.substringAfterLast('/')` -- a raw
    // filename -- as if it were the track title.
    val cloudTracksByPath by container.database.cloudTracks().observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val cloudTrackIndex = remember(cloudTracksByPath) { cloudTracksByPath.associateBy { it.serverPath } }
    val active by container.downloads.active.collectAsStateWithLifecycle()
    val queued by container.downloads.queued.collectAsStateWithLifecycle()
    val offlinePrefs by container.downloads.settings.state.collectAsStateWithLifecycle()
    val librarySync by container.downloads.library.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { container.downloads.refreshCounts() }
    val folderLabel = remember(offlinePrefs.folderUri) { container.downloads.settings.storage().label }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            container.downloads.settings.setFolder(uri)
            container.scheduleLibraryDownload()
        }
    }

    val totalMb = downloads.sumOf { it.sizeBytes } / 1_048_576
    Column(Modifier.fillMaxSize()) {
        ScreenTitle(
            "Offline",
            subtitle = "${downloads.size} tracks · $totalMb MB on this phone",
            eyebrow = "Downloads",
            onBack = onBack,
        )
        LibraryDownloadPanel(
            prefs = offlinePrefs,
            sync = librarySync,
            folderLabel = folderLabel,
            callbacks = LibraryDownloadCallbacks(
                onWholeLibrary = { on ->
                    container.downloads.settings.setDownloadWholeLibrary(on)
                    container.scheduleLibraryDownload()
                },
                onWifiOnly = { on ->
                    container.downloads.settings.setWifiOnly(on)
                    // The network constraint changes, so the waiting run is replaced.
                    container.scheduleLibraryDownload(restart = true)
                },
                onSyncNow = { container.scheduleLibraryDownload(restart = true) },
                onChooseFolder = { pickFolder.launch(null) },
                onUsePhoneFolder = { container.downloads.settings.setFolder(null) },
            ),
            modifier = Modifier.padding(bottom = 8.dp),
        )
        active?.let { progress ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).then(
                Modifier.background(LocalLumiPalette.current.elevatedSurface, RoundedCornerShape(22.dp))
            ).padding(16.dp)) {
                Text(
                    "Downloading ${progress.title}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(6.dp))
                if (progress.totalBytes > 0) {
                    LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
                } else {
                    // The server does not always send a length; a spinner is honest about
                    // not knowing how far along this is.
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${progress.bytesRead / 1_048_576} MB" +
                        if (progress.totalBytes > 0) " of ${progress.totalBytes / 1_048_576} MB" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (queued.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${queued.size} waiting",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = container.downloads::cancelAll) { Text("Cancel queue") }
            }
        }

        if (downloads.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.DownloadForOffline,
                title = "Nothing offline yet",
                message = "Download tracks from your cloud library and they play here with no connection — still locked to this app.",
            )
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(downloads, key = { it.serverPath }) { download ->
                Row(
                    Modifier.fillMaxWidth().padding(start = 18.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(
                        model = BridgeUrls.cloudArtwork(container.config.baseUrl, download.serverPath),
                        fallbackKey = download.serverPath,
                        size = 48.dp,
                        corner = 13.dp,
                    )
                    Spacer(Modifier.padding(start = 12.dp))
                    val track = cloudTrackIndex[download.serverPath]
                    Column(Modifier.weight(1f)) {
                        Text(
                            // Falls back to the raw filename only if the cloud
                            // library entry is gone (e.g. deleted server-side
                            // since this was downloaded) -- same fallback
                            // shape as everywhere else that resolves a track
                            // by serverPath.
                            track?.title?.takeIf { it.isNotBlank() } ?: download.serverPath.substringAfterLast('/'),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            buildString {
                                if (track != null && track.artist.isNotBlank()) {
                                    append(track.artist)
                                    append(" · ")
                                }
                                append("${download.sizeBytes / 1_048_576} MB")
                                if (download.isLocked) append(" · locked")
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { scope.launch { container.downloads.remove(download.serverPath) } }) {
                        Text("Remove")
                    }
                }
            }
        }
    }
}
