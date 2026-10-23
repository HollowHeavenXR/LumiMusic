package com.lumisound.android.ui.screens.nowplaying

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisound.android.AppContainer
import com.lumisound.android.bridge.model.StreamTrackDto
import com.lumisound.android.lyrics.LrcParser
import com.lumisound.android.lyrics.LyricsResult
import com.lumisound.android.playback.PlaybackUiState
import com.lumisound.android.playback.QueueItem
import com.lumisound.android.ui.aura.Aura
import com.lumisound.android.ui.aura.AuraBackdrop
import com.lumisound.android.ui.aura.LocalAura
import com.lumisound.android.ui.components.Artwork
import com.lumisound.android.ui.components.EqualizerBars
import com.lumisound.android.ui.components.Eyebrow
import com.lumisound.android.ui.components.GlassButton
import com.lumisound.android.ui.components.GlassIconButton
import com.lumisound.android.ui.components.LumiCard
import com.lumisound.android.ui.components.OneLine
import com.lumisound.android.ui.components.RingProgress
import com.lumisound.android.ui.components.SegmentedPill
import com.lumisound.android.ui.components.VinylDisc
import com.lumisound.android.ui.components.trackSubtitle
import com.lumisound.android.ui.startRadio
import com.lumisound.android.ui.theme.LocalLumiPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The four faces of the Stage. */
enum class StagePage(val label: String) { Record("Record"), Lyrics("Lyrics"), Queue("Up next"), Details("Details") }

/** Sleep-timer choices in minutes; [SLEEP_END_OF_TRACK] stops when the current track does. */
val SLEEP_CHOICES = listOf(15, 30, 45, 60, 90)
const val SLEEP_END_OF_TRACK = -1

/** Everything the Stage can ask the player to do. */
data class StageActions(
    val onDismiss: () -> Unit = {},
    val onSeek: (Long) -> Unit = {},
    val onToggle: () -> Unit = {},
    val onPrevious: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onShuffle: () -> Unit = {},
    val onRepeat: () -> Unit = {},
    val onSpeed: (Float) -> Unit = {},
    val onPitch: (Float) -> Unit = {},
    /** Minutes, [SLEEP_END_OF_TRACK], or null to cancel. */
    val onSleepTimer: (Int?) -> Unit = {},
    val onFavorite: (() -> Unit)? = null,
    val onSkipTo: (Int) -> Unit = {},
    val onRemoveFromQueue: (Int) -> Unit = {},
    val onMoveInQueue: (Int, Int) -> Unit = { _, _ -> },
    val onStartRadio: (() -> Unit)? = null,
)

/**
 * Full-screen playback, opened from the record in the dock.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(container: AppContainer, onDismiss: () -> Unit) {
    val state by container.player.state.collectAsStateWithLifecycle()
    val favoriteIds by container.database.favorites().observeIds().collectAsStateWithLifecycle(initialValue = emptyList())
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableStateOf(StagePage.Record) }
    var queue by remember { mutableStateOf<List<QueueItem>>(emptyList()) }
    val title = state.title

    // Only fetched once the lyrics face is shown, and never for an episode -- podcasts have none.
    val lyrics by produceState<LyricsResult?>(null, title, state.artist, page) {
        value = null
        if (page == StagePage.Lyrics && title != null && state.podcastFeedUrl == null) {
            value = container.lyrics.lyricsFor(title, state.artist, state.durationMs)
        }
    }
    // The session is the single source of truth for the queue; re-read it while it is shown,
    // since a reorder changes the session without necessarily producing a player event.
    LaunchedEffect(page, state.queueIndex, state.queueSize) {
        if (page != StagePage.Queue) return@LaunchedEffect
        while (true) {
            queue = container.player.queueSnapshot()
            delay(1_000)
        }
    }

    val streamId = state.mediaId?.takeIf { it.startsWith("youtube:") }?.removePrefix("youtube:")
    val favoritePath = state.serverPath
    val onFavorite: (() -> Unit)? = if (favoritePath == null) null else {
        { scope.launch { container.libraryRepository.toggleFavorite(favoritePath, state.title, state.artist, null) } }
    }
    val onStartRadio: (() -> Unit)? = if (streamId == null) null else {
        {
            scope.launch {
                container.startRadio(
                    StreamTrackDto(id = streamId, title = state.title.orEmpty(), artist = state.artist.orEmpty(), source = "youtube", youtubeUrl = state.trackUrl)
                )
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        dragHandle = null,
        shape = RoundedCornerShape(0.dp),
    ) {
        NowPlayingContent(
            state = state.copy(isFavorite = state.serverPath in favoriteIds),
            page = page,
            onPageChange = { page = it },
            lyrics = lyrics,
            queue = queue,
            actions = StageActions(
                onDismiss = onDismiss,
                onSeek = container.player::seekTo,
                onToggle = container.player::togglePlayPause,
                onPrevious = { container.player.previous() },
                onNext = { container.player.next() },
                onShuffle = { container.player.setShuffle(!state.shuffleEnabled) },
                onRepeat = container.player::cycleRepeatMode,
                onSpeed = { container.player.setSpeed(it) },
                onPitch = { container.player.setPitchSemitones(it) },
                onSleepTimer = { choice ->
                    when (choice) {
                        null -> container.player.cancelSleepTimer()
                        SLEEP_END_OF_TRACK -> container.player.setSleepTimer(null, endOfTrack = true)
                        else -> container.player.setSleepTimer(choice)
                    }
                },
                onFavorite = onFavorite,
                onSkipTo = { container.player.skipTo(it); queue = container.player.queueSnapshot() },
                onRemoveFromQueue = { container.player.removeFromQueue(it); queue = container.player.queueSnapshot() },
                onMoveInQueue = { from, to -> container.player.moveInQueue(from, to); queue = container.player.queueSnapshot() },
                onStartRadio = onStartRadio,
            ),
        )
    }
}

/**
 * The Stage: full-screen playback with four faces.
 *
 * - **Record** -- the track as a turning vinyl record, its cover as the label, ringed by a
 *   progress arc you can drag round to seek. A square of artwork is what every player shows;
 *   a record says "this is playing" even when the cover is a generated one.
 * - **Lyrics** -- synced, lit line by line, tap a line to jump to it.
 * - **Up next** -- the live queue, reorderable in place rather than in another sheet.
 * - **Details** -- where it is coming from and what is set: source, speed, sleep, radio.
 *
 * The whole screen sits in the track's own aura, stronger here than anywhere else.
 */
@Composable
fun NowPlayingContent(
    state: PlaybackUiState,
    page: StagePage,
    onPageChange: (StagePage) -> Unit,
    lyrics: LyricsResult?,
    queue: List<QueueItem>,
    actions: StageActions,
    nowMs: Long = System.currentTimeMillis(),
    aura: Aura = LocalAura.current,
) {
    val palette = LocalLumiPalette.current
    val key = state.mediaId ?: state.serverPath ?: state.title.orEmpty()
    AuraBackdrop(aura, Modifier.fillMaxSize(), intensity = 1.35f) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top bar: close, where it is playing from, where in the queue.
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.Filled.KeyboardArrowDown, "Close", onClick = actions.onDismiss)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Eyebrow("Now playing")
                    Text(sourceLabel(state), style = MaterialTheme.typography.labelLarge)
                }
                Box(
                    Modifier.size(width = 58.dp, height = 42.dp).clip(CircleShape).background(palette.elevatedSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${state.queueIndex + 1}/${state.queueSize}", style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(6.dp))
            SegmentedPill(StagePage.entries, page, { it.label }, onPageChange)
            Spacer(Modifier.height(14.dp))

            Box(Modifier.fillMaxWidth().height(340.dp), contentAlignment = Alignment.Center) {
                AnimatedContent(targetState = page, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "stage") { face ->
                    when (face) {
                        StagePage.Record -> RecordFace(state, key, actions.onSeek)
                        StagePage.Lyrics -> LyricsPanel(lyrics, state.positionMs, actions.onSeek)
                        StagePage.Queue -> QueueFace(queue, actions)
                        StagePage.Details -> DetailsFace(state, nowMs, actions)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        state.title ?: "Nothing playing",
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    OneLine(
                        trackSubtitle(state.title.orEmpty(), state.artist, null),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
                actions.onFavorite?.let { onFavorite ->
                    IconButton(onClick = onFavorite) {
                        Icon(
                            if (state.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (state.isFavorite) palette.accent else Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }

            // The line scrubber stays on every face but the record, which is its own scrubber.
            val known = state.durationMs > 0
            val duration = state.durationMs.coerceAtLeast(1)
            if (page != StagePage.Record) {
                Slider(
                    // An unknown duration shows an empty track rather than a full one: a bar
                    // pinned to the end while the track is eleven seconds in says something
                    // false about the track instead of admitting what is not known.
                    value = if (known) (state.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                    enabled = known,
                    onValueChange = { actions.onSeek((it * duration).toLong()) },
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.18f)),
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            } else {
                Spacer(Modifier.height(12.dp))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 26.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(state.positionMs.asClock(), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                Text(
                    if (known) "-" + (state.durationMs - state.positionMs).coerceAtLeast(0).asClock() else "--:--",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                IconButton(onClick = actions.onShuffle) {
                    Icon(Icons.Filled.Shuffle, "Shuffle", tint = if (state.shuffleEnabled) palette.accent else Color.White.copy(alpha = 0.6f))
                }
                IconButton(onClick = actions.onPrevious, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(40.dp))
                }
                Box(
                    Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(onClick = actions.onToggle),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = AuraBase,
                        modifier = Modifier.size(40.dp),
                    )
                }
                IconButton(onClick = actions.onNext, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = actions.onRepeat) {
                    Icon(
                        // REPEAT_MODE_ONE == 1; the icon says which mode is on.
                        if (state.repeatMode == 1) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        "Repeat",
                        tint = if (state.repeatMode != 0) palette.accent else Color.White.copy(alpha = 0.6f),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            ToolRow(state, nowMs, actions)

            state.playbackError?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 24.dp))
            }
        }
    }
}

@Composable
private fun RecordFace(state: PlaybackUiState, key: String, onSeek: (Long) -> Unit) {
    val progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f
    Box(Modifier.size(330.dp), contentAlignment = Alignment.Center) {
        // A glow under the record in the track's own colour.
        Box(
            Modifier
                .size(330.dp)
                .background(Brush.radialGradient(listOf(LocalAura.current.primary.copy(alpha = 0.45f), Color.Transparent)), CircleShape)
        )
        RingProgress(
            progress = progress,
            modifier = Modifier.size(318.dp),
            stroke = 5.dp,
            color = Color.White,
            track = Color.White.copy(alpha = 0.14f),
            showThumb = true,
            onSeek = if (state.durationMs > 0) ({ fraction: Float -> onSeek((fraction * state.durationMs).toLong()) }) else null,
        )
        VinylDisc(
            artworkModel = state.artworkUrl,
            fallbackKey = key,
            size = 276.dp,
            spinning = state.isPlaying,
        )
    }
}

@Composable
private fun QueueFace(queue: List<QueueItem>, actions: StageActions) {
    val palette = LocalLumiPalette.current
    if (queue.isEmpty()) {
        Text("The queue is empty.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val current = queue.indexOfFirst { it.isCurrent }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = current)
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.22f)),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(queue, key = { it.index }) { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { actions.onSkipTo(item.index) }
                    .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Artwork(item.artworkUri, item.mediaId, 40.dp, corner = 10.dp)
                    if (item.isCurrent) EqualizerBars(playing = true, color = Color.White, height = 14.dp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    OneLine(item.title, style = MaterialTheme.typography.titleSmall, color = if (item.isCurrent) palette.accent else Color.White)
                    OneLine(item.artist, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(enabled = item.index > 0, onClick = { actions.onMoveInQueue(item.index, item.index - 1) }) {
                    Icon(Icons.Filled.ArrowUpward, "Move up", modifier = Modifier.size(18.dp))
                }
                IconButton(enabled = item.index < queue.lastIndex, onClick = { actions.onMoveInQueue(item.index, item.index + 1) }) {
                    Icon(Icons.Filled.ArrowDownward, "Move down", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { actions.onRemoveFromQueue(item.index) }) {
                    Icon(Icons.Filled.Close, "Remove", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun DetailsFace(state: PlaybackUiState, nowMs: Long, actions: StageActions) {
    val facts = listOf(
        "Source" to sourceLabel(state),
        "Speed" to "${state.speed}×",
        "Sleep" to (sleepLabel(state, nowMs) ?: "Off"),
        "In queue" to "${state.queueIndex + 1} of ${state.queueSize}",
        "Shuffle" to if (state.shuffleEnabled) "On" else "Off",
        "Repeat" to when (state.repeatMode) { 1 -> "One"; 2 -> "All"; else -> "Off" },
    )
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        facts.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { (label, value) ->
                    LumiCard(Modifier.weight(1f), corner = 18.dp) {
                        Column(Modifier.padding(14.dp)) {
                            Eyebrow(label)
                            Spacer(Modifier.height(4.dp))
                            OneLine(value, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
        actions.onStartRadio?.let { radio ->
            GlassButton("Start a radio from this track", Icons.Filled.Radio, onClick = radio, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Speed and sleep as two pills with their own menus, under the transport. */
@Composable
private fun ToolRow(state: PlaybackUiState, nowMs: Long, actions: StageActions) {
    var speedMenu by remember { mutableStateOf(false) }
    var pitchMenu by remember { mutableStateOf(false) }
    var sleepMenu by remember { mutableStateOf(false) }
    val timerOn = state.sleepAtMs != null || state.sleepAtEndOfTrack
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box {
            ToolPill(Icons.Filled.Speed, if (state.speed == 1f) "1×" else "${state.speed}×", active = state.speed != 1f) { speedMenu = true }
            DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                    DropdownMenuItem(text = { Text("${speed}×") }, onClick = { speedMenu = false; actions.onSpeed(speed) })
                }
            }
        }
        // Independent of speed -- iOS parity (AudioSettings.pitchSemitones).
        // Fixed menu choices rather than a slider, same presentation as Speed above.
        Box {
            val pitchLabel = if (state.pitchSemitones == 0f) "Pitch" else "%+.1f st".format(state.pitchSemitones)
            ToolPill(Icons.Filled.MusicNote, pitchLabel, active = state.pitchSemitones != 0f) { pitchMenu = true }
            DropdownMenu(expanded = pitchMenu, onDismissRequest = { pitchMenu = false }) {
                listOf(-12f, -7f, -5f, -3f, 0f, 3f, 5f, 7f, 12f).forEach { semitones ->
                    DropdownMenuItem(
                        text = { Text(if (semitones == 0f) "Normal" else "%+.0f semitones".format(semitones)) },
                        onClick = { pitchMenu = false; actions.onPitch(semitones) },
                    )
                }
            }
        }
        Box {
            ToolPill(Icons.Filled.Bedtime, sleepLabel(state, nowMs) ?: "Sleep", active = timerOn) { sleepMenu = true }
            DropdownMenu(expanded = sleepMenu, onDismissRequest = { sleepMenu = false }) {
                SLEEP_CHOICES.forEach { minutes ->
                    DropdownMenuItem(text = { Text("$minutes minutes") }, onClick = { sleepMenu = false; actions.onSleepTimer(minutes) })
                }
                DropdownMenuItem(text = { Text("End of this track") }, onClick = { sleepMenu = false; actions.onSleepTimer(SLEEP_END_OF_TRACK) })
                if (timerOn) {
                    DropdownMenuItem(text = { Text("Turn off timer") }, onClick = { sleepMenu = false; actions.onSleepTimer(null) })
                }
            }
        }
    }
}

@Composable
private fun ToolPill(icon: ImageVector, text: String, active: Boolean, onClick: () -> Unit) {
    val palette = LocalLumiPalette.current
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (active) palette.accent.copy(alpha = 0.3f) else palette.elevatedSurface)
            .border(1.dp, palette.hairline, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Where the current track is coming from, in words. */
fun sourceLabel(state: PlaybackUiState): String = when {
    state.podcastFeedUrl != null -> "Podcast"
    state.trackUrl?.contains("soundcloud", ignoreCase = true) == true -> "SoundCloud"
    state.trackUrl != null -> "YouTube"
    state.isLocalSource -> "This phone"
    state.serverPath != null -> "Your cloud"
    else -> "LumiMusic"
}

/** "Sleeping in 23 min" / "Stopping after this track", or null when no timer is set. */
fun sleepLabel(state: PlaybackUiState, nowMs: Long): String? = when {
    state.sleepAtEndOfTrack -> "Stopping after this track"
    state.sleepAtMs != null -> {
        val minutes = ((state.sleepAtMs - nowMs + 59_999) / 60_000).coerceAtLeast(1)
        "Sleeping in $minutes min"
    }
    else -> null
}

/**
 * Synced lyrics with the current line lit and kept in view, or plain lyrics scrolling
 * freely. Tapping a synced line seeks to it.
 */
@Composable
private fun LyricsPanel(lyrics: LyricsResult?, positionMs: Long, onSeek: (Long) -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        when (lyrics) {
            null -> CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            LyricsResult.None -> Text(
                "No lyrics for this track.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is LyricsResult.Plain -> Text(
                lyrics.text,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
            )
            is LyricsResult.Synced -> {
                val active = LrcParser.activeIndex(lyrics.lines, positionMs)
                val listState = rememberLazyListState()
                LaunchedEffect(active) {
                    // Keep the live line about a third of the way down, with context above it.
                    if (active >= 0) listState.animateScrollToItem((active - 2).coerceAtLeast(0))
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(lyrics.lines) { index, line ->
                        val isActive = index == active
                        Text(
                            line.text,
                            style = if (isActive) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                            color = when {
                                isActive -> Color.White
                                index < active -> Color.White.copy(alpha = 0.35f)
                                else -> Color.White.copy(alpha = 0.6f)
                            },
                            modifier = Modifier.fillMaxWidth().clickable { onSeek(line.timeMs) },
                        )
                    }
                }
            }
        }
    }
}

private fun Long.asClock(): String {
    if (this <= 0) return "0:00"
    val seconds = this / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
