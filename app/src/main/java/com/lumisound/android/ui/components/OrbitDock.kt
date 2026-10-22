package com.lumisound.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.lumisound.android.ui.aura.AuraBase
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lumisound.android.playback.PlaybackUiState
import com.lumisound.android.ui.theme.LocalLumiPalette

/** The four places the dock goes. Settings lives behind the avatar on Home. */
enum class DockTab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Search("Search", Icons.Filled.Search),
    Library("Library", Icons.Filled.LibraryMusic),
    Friends("Circle", Icons.Filled.People),
}

/**
 * The Orbit dock: navigation and the mini player as one object.
 *
 * A tab bar and a mini player stacked on top of it spent a fifth of the screen on chrome and
 * said the same thing twice -- "here is where you are" and "here is what is playing" -- in
 * two unrelated bars. Here the music sits at the centre of navigation, literally: the
 * record in the middle is what is playing, turning while it plays, ringed by its progress.
 * Tap it to open the player; tap its small badge to pause. When nothing is queued it is a
 * quiet mark that takes you to Search, where playing something starts.
 */
@Composable
fun OrbitDock(
    selected: DockTab?,
    onSelect: (DockTab) -> Unit,
    playback: PlaybackUiState,
    onOpenPlayer: () -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalLumiPalette.current
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (playback.hasQueue && playback.title != null) {
            // The one line of text the mini player used to be: what is on, kept short.
            Text(
                listOfNotNull(playback.title, playback.artist?.takeIf { it.isNotBlank() && it != playback.title }).joinToString("  ·  "),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 56.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(onClick = onOpenPlayer)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(4.dp))
        }
        Box(Modifier.fillMaxWidth().height(90.dp)) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(palette.glassStrong)
                    .border(1.dp, palette.hairline, RoundedCornerShape(32.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DockItem(DockTab.Home, selected, onSelect, Modifier.weight(1f))
                DockItem(DockTab.Search, selected, onSelect, Modifier.weight(1f))
                Spacer(Modifier.weight(1.25f))
                DockItem(DockTab.Library, selected, onSelect, Modifier.weight(1f))
                DockItem(DockTab.Friends, selected, onSelect, Modifier.weight(1f))
            }
            NowPlayingOrb(
                playback = playback,
                onOpen = if (playback.hasQueue) onOpenPlayer else ({ onSelect(DockTab.Search) }),
                onToggle = onTogglePlay,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
private fun DockItem(tab: DockTab, selected: DockTab?, onSelect: (DockTab) -> Unit, modifier: Modifier) {
    val palette = LocalLumiPalette.current
    val isSelected = tab == selected
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(24.dp))
            .clickable { onSelect(tab) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            tab.icon,
            contentDescription = tab.label,
            tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(23.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier
                .size(width = if (isSelected) 14.dp else 0.dp, height = 3.dp)
                .clip(CircleShape)
                .background(palette.accent)
        )
    }
}

@Composable
private fun NowPlayingOrb(playback: PlaybackUiState, onOpen: () -> Unit, onToggle: () -> Unit, modifier: Modifier) {
    val palette = LocalLumiPalette.current
    val progress = if (playback.durationMs > 0) playback.positionMs.toFloat() / playback.durationMs else 0f
    Box(modifier.size(78.dp)) {
        Box(
            Modifier
                .size(74.dp)
                .align(Alignment.Center)
                .clip(CircleShape)
                .background(AuraBase)
                .clickable(onClick = onOpen),
            contentAlignment = Alignment.Center,
        ) {
            if (playback.hasQueue) {
                RingProgress(progress, Modifier.fillMaxSize(), stroke = 3.dp)
                VinylDisc(
                    artworkModel = playback.artworkUrl,
                    fallbackKey = playback.mediaId ?: playback.serverPath ?: playback.title.orEmpty(),
                    size = 62.dp,
                    spinning = playback.isPlaying,
                    labelFraction = 0.5f,
                )
            } else {
                Box(
                    Modifier.size(62.dp).clip(CircleShape).background(palette.accentBrush),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.GraphicEq, contentDescription = "Find something to play", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }
        if (playback.hasQueue) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-2).dp, y = (-2).dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(palette.accent, palette.accent.copy(alpha = 0.8f))))
                    .border(2.dp, AuraBase, CircleShape)
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playback.isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}
