package com.lumisound.android.ui.screens.library

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisound.android.AppContainer
import com.lumisound.android.cloud.ImportStage
import com.lumisound.android.data.db.CloudTrackEntity
import com.lumisound.android.bridge.model.DuplicateFileDto
import com.lumisound.android.ui.components.EmptyState
import com.lumisound.android.ui.components.GlassButton
import com.lumisound.android.ui.components.Loadable
import com.lumisound.android.ui.components.LoadableSection
import com.lumisound.android.ui.components.OneLine
import com.lumisound.android.ui.components.ScreenTitle
import com.lumisound.android.ui.components.friendlyError
import androidx.compose.material.icons.filled.Refresh
import kotlinx.coroutines.launch

/**
 * Files that are acoustically the same recording, regardless of metadata --
 * same filename-matching limitation iOS's own duplicate finder has (the
 * server only returns bare filenames, ambiguous across two folders with the
 * same name), not something new to this port.
 */
@Composable
fun DuplicateFinderScreen(container: AppContainer, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cloudTracks by container.database.cloudTracks().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val tracksByFilename = remember(cloudTracks) { cloudTracks.associateBy { it.filename } }
    var groups by remember { mutableStateOf<Loadable<List<List<DuplicateFileDto>>>>(Loadable.Loading) }
    var pendingDelete by remember { mutableStateOf<CloudTrackEntity?>(null) }

    suspend fun scan(forceRescan: Boolean) {
        groups = Loadable.Loading
        groups = try {
            Loadable.Ready(container.http.cloudMusic.acousticDuplicates(forceRescan).duplicateGroups)
        } catch (e: Exception) {
            Loadable.Failed(friendlyError(e))
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) { scan(forceRescan = false) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(
            "Duplicate Finder",
            subtitle = "Files that sound identical, regardless of their name",
            eyebrow = "Cloud library",
            onBack = onBack,
            trailing = { GlassButton("Rescan", Icons.Filled.Refresh, onClick = { scope.launch { scan(forceRescan = true) } }) },
        )
        LoadableSection(groups, onRetry = { scope.launch { scan(forceRescan = true) } }) { duplicateGroups ->
            if (duplicateGroups.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.ContentCopy,
                    title = "No duplicates found",
                    message = "Nothing in your cloud library sounds like a repeat of anything else.",
                )
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(duplicateGroups, key = { group -> group.joinToString { it.file } }) { group ->
                        DuplicateGroupCard(
                            group = group,
                            tracksByFilename = tracksByFilename,
                            onDelete = { pendingDelete = it },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { track ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this copy?") },
            text = { Text("\"${track.filename}\" will be permanently deleted from your cloud library.") },
            confirmButton = {
                Button(onClick = {
                    pendingDelete = null
                    scope.launch {
                        try {
                            container.http.cloudMusic.deleteTrack(encodePathSegments(track.serverPath))
                            container.cloudImport.import(setOf(ImportStage.CloudTracks))
                            Toast.makeText(context, "Deleted \"${track.filename}\"", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, friendlyError(e), Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DuplicateGroupCard(
    group: List<DuplicateFileDto>,
    tracksByFilename: Map<String, CloudTrackEntity>,
    onDelete: (CloudTrackEntity) -> Unit,
) {
    val matched = group.mapNotNull { tracksByFilename[it.file] }
    // Heuristic tie-break, same ordering iOS's on-device picker uses (longest
    // duration, then a known tempo) with sample rate folded in first -- a
    // win iOS's own heuristic doesn't have to weigh since it has no cheap
    // sample-rate signal for a cloud-only track the way this app's
    // /user/music sample_rate field now gives it.
    val suggestedKeep = matched.maxWithOrNull(
        compareBy({ it.sampleRate ?: 0 }, { it.bpm != null }, { it.durationSeconds }),
    )
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(14.dp)) {
            matched.forEach { track ->
                val isSuggested = track.serverPath == suggestedKeep?.serverPath
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (isSuggested) {
                        Icon(Icons.Filled.Star, contentDescription = "Suggested keep", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Spacer(Modifier.width(26.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        OneLine(track.filename, style = MaterialTheme.typography.bodyMedium)
                        OneLine(
                            buildString {
                                append(track.ext.uppercase())
                                track.sampleRate?.let { append(" · ${it / 1000}kHz") }
                                append(" · ${"%d:%02d".format(track.durationSeconds.toInt() / 60, track.durationSeconds.toInt() % 60)}")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!isSuggested) {
                        IconButton(onClick = { onDelete(track) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete this copy", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            // A member the client's own library sync hasn't caught up with yet
            // (or a filename collision across two folders -- see the screen's
            // own doc comment) just doesn't render a row, rather than a
            // confusing row with no real data behind it.
            if (matched.size < group.size) {
                Text(
                    "${group.size - matched.size} more file(s) in this group couldn't be matched to your library.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private fun encodePathSegments(path: String): String =
    path.split("/").joinToString("/") { Uri.encode(it) }
