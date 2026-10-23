package com.lumisound.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.lumisound.android.bridge.model.StreamTrackDto

/** What a streamed track's overflow menu can do. Radio is YouTube-only on the bridge. */
data class StreamTrackActions(
    val onPlayNext: (StreamTrackDto) -> Unit = {},
    val onEnqueue: (StreamTrackDto) -> Unit = {},
    val onRadio: ((StreamTrackDto) -> Unit)? = null,
    /** Downloads+transcodes this result into the account's cloud library, same as iOS can. */
    val onSave: ((StreamTrackDto) -> Unit)? = null,
)

/** The "YouTube · 3:41" style secondary line, via the shared title-dedupe rules. */
fun streamSubtitle(track: StreamTrackDto): String {
    val source = when (track.source) {
        "soundcloud" -> "SoundCloud"
        "youtube" -> if (track.isTopicChannel) "YouTube Music" else "YouTube"
        else -> track.source.replaceFirstChar { it.uppercase() }
    }
    return trackSubtitle(track.title, track.artist, source)
}

fun Int.asTrackClock(): String? {
    if (this <= 0) return null
    val hours = this / 3600
    val minutes = (this % 3600) / 60
    val seconds = this % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

/**
 * [TrackRow] for a YouTube/SoundCloud result -- the same row every list uses, with the
 * remote thumbnail as artwork and a menu of queue actions.
 */
@Composable
fun StreamTrackRow(
    track: StreamTrackDto,
    isPlaying: Boolean,
    onClick: () -> Unit,
    actions: StreamTrackActions,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier) {
        TrackRow(
            title = track.title,
            subtitle = streamSubtitle(track),
            artworkModel = track.thumbnailUrl?.takeIf { it.isNotBlank() },
            fallbackKey = "${track.source}:${track.id}",
            duration = track.durationSeconds.asTrackClock(),
            isPlaying = isPlaying,
            onClick = onClick,
            onMenu = { menuOpen = true },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("Play next") }, onClick = { menuOpen = false; actions.onPlayNext(track) })
            DropdownMenuItem(text = { Text("Add to queue") }, onClick = { menuOpen = false; actions.onEnqueue(track) })
            if (actions.onRadio != null && track.source == "youtube") {
                DropdownMenuItem(
                    text = { Text("Start radio") },
                    onClick = { menuOpen = false; actions.onRadio.invoke(track) },
                )
            }
            if (actions.onSave != null) {
                DropdownMenuItem(
                    text = { Text("Save to library") },
                    onClick = { menuOpen = false; actions.onSave.invoke(track) },
                )
            }
        }
    }
}
