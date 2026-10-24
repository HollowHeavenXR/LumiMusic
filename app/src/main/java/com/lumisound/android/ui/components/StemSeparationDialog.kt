package com.lumisound.android.ui.components

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisound.android.AppContainer
import com.lumisound.android.library.StemSeparationSession
import com.lumisound.android.library.StemSeparationState
import kotlinx.coroutines.launch
import java.io.File

/**
 * One in-flight preview of a stem's audio -- a plain `MediaPlayer`, not the
 * app's main ExoPlayer pipeline, same reasoning as iOS using a separate
 * `AVAudioPlayer` for this rather than its main AudioPlayerManager: a
 * transient preview of a file outside the queue, not a thing to route
 * through effects/session/now-playing state.
 */
private class StemPreview {
    private var player: MediaPlayer? = null
    var isPlaying by mutableStateOf(false)

    fun toggle(file: File) {
        val existing = player
        if (existing != null) {
            if (isPlaying) existing.pause() else existing.start()
            isPlaying = !isPlaying
            return
        }
        val created = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            // Unqualified `isPlaying` here would resolve to MediaPlayer's own
            // (read-only, Java getter-derived) property of the same name, not this
            // class's -- "val cannot be reassigned" is that collision, not a typo.
            setOnCompletionListener { this@StemPreview.isPlaying = false }
            prepare()
            start()
        }
        player = created
        isPlaying = true
    }

    fun pause() {
        if (isPlaying) player?.pause()
        isPlaying = false
    }

    fun release() {
        player?.release()
        player = null
        isPlaying = false
    }
}

/**
 * Separate Vocals / Instrumental -- iOS parity (`StemSeparationView`). Real
 * server-side AI source separation (Demucs), routinely a minute or more;
 * the result is a transient remix source to preview and optionally share,
 * not something added to the library.
 */
@Composable
fun StemSeparationDialog(
    container: AppContainer,
    serverPath: String,
    title: String,
    isLocked: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = remember { StemSeparationSession(context, container) }
    val state by session.state.collectAsStateWithLifecycle()
    val vocalsPreview = remember { StemPreview() }
    val instrumentalPreview = remember { StemPreview() }

    LaunchedEffect(Unit) {
        if (state is StemSeparationState.Idle) session.start(serverPath, title, isLocked)
    }
    DisposableEffect(Unit) {
        onDispose {
            vocalsPreview.release()
            instrumentalPreview.release()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 1) },
        text = {
            when (val current = state) {
                is StemSeparationState.Idle, is StemSeparationState.Uploading, is StemSeparationState.Separating -> {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(14.dp))
                        Text(
                            if (current is StemSeparationState.Uploading) "Sending audio…"
                            else "Separating — this can take a few minutes…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is StemSeparationState.Done -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StemRow("Vocals", Icons.Filled.Mic, current.vocals, vocalsPreview, instrumentalPreview, context)
                        StemRow("Instrumental", Icons.Filled.MusicNote, current.instrumental, instrumentalPreview, vocalsPreview, context)
                    }
                }
                is StemSeparationState.Failed -> {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(current.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(10.dp))
                        TextButton(onClick = {
                            session.reset()
                            scope.launch { session.start(serverPath, title, isLocked) }
                        }) { Text("Try again") }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun StemRow(
    label: String,
    icon: ImageVector,
    file: File,
    preview: StemPreview,
    other: StemPreview,
    context: Context,
) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { other.pause(); preview.toggle(file) }) {
                Icon(if (preview.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Preview $label")
            }
            IconButton(onClick = { shareStem(context, file, label) }) {
                Icon(Icons.Filled.Share, contentDescription = "Share $label", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun shareStem(context: Context, file: File, label: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "audio/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share $label"))
}
