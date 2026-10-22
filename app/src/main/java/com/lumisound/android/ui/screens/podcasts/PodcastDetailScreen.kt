package com.lumisound.android.ui.screens.podcasts

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.lumisound.android.ui.aura.AuraBase
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisound.android.AppContainer
import com.lumisound.android.bridge.model.EpisodeProgressDto
import com.lumisound.android.bridge.model.PodcastEpisodeDto
import com.lumisound.android.bridge.model.PodcastSubscribeRequest
import com.lumisound.android.bridge.model.PodcastSubscriptionDto
import com.lumisound.android.playback.toPlayable
import com.lumisound.android.ui.components.Artwork
import com.lumisound.android.ui.components.EqualizerBars
import com.lumisound.android.ui.components.Eyebrow
import com.lumisound.android.ui.components.GlassButton
import com.lumisound.android.ui.components.GlassIconButton
import com.lumisound.android.ui.components.GlowButton
import com.lumisound.android.ui.components.Loadable
import com.lumisound.android.ui.components.LoadableSection
import com.lumisound.android.ui.components.LumiCard
import com.lumisound.android.ui.components.RingProgress
import com.lumisound.android.ui.components.friendlyError
import com.lumisound.android.ui.components.rememberLoadable
import com.lumisound.android.ui.screens.social.parseInstant
import com.lumisound.android.ui.theme.LocalLumiPalette
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * One show: follow or unfollow it, and its episodes with how far into each this account
 * has got. Tapping an episode resumes it where it was left, on this device or the phone.
 */
@Composable
fun PodcastDetailScreen(
    container: AppContainer,
    feedUrl: String,
    title: String,
    artworkUrl: String?,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val api = container.http.podcasts
    val playback by container.player.state.collectAsStateWithLifecycle()

    val episodes = rememberLoadable(feedUrl, "podcast.episodes") { api.episodes(feedUrl) }
    val progress = rememberLoadable(feedUrl, "podcast.progress") {
        api.progress(feedUrl = feedUrl, limit = 200).associateBy { it.episodeGuid }
    }
    val subscription = rememberLoadable(feedUrl, "podcast.sub") { api.subscriptions().firstOrNull { it.feedUrl == feedUrl } }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    fun play(list: List<PodcastEpisodeDto>, episode: PodcastEpisodeDto, fromStart: Boolean) {
        val playables = list.mapNotNull { it.toPlayable(feedUrl, title, artworkUrl) }
        val guid = episode.guid ?: episode.audioUrl
        val index = playables.indexOfFirst { it.episodeGuid == guid }
        if (index < 0) {
            toast("This episode has no audio file.")
            return
        }
        val saved = guid?.let { progress.state.valueOrNull?.get(it) }
        val resumeAt = if (fromStart || saved == null || saved.completed) 0L else (saved.positionSeconds * 1000).toLong()
        container.player.setShuffle(false)
        container.player.play(playables, index, resumeAt)
    }

    PodcastDetailContent(
        title = title,
        feedUrl = feedUrl,
        artworkUrl = artworkUrl,
        episodes = episodes.state,
        progress = progress.state.valueOrNull.orEmpty(),
        subscription = subscription.state,
        playingGuid = playback.episodeGuid,
        onBack = onBack,
        onRetry = episodes::reload,
        onPlay = ::play,
        onPlayNext = { episode -> episode.toPlayable(feedUrl, title, artworkUrl)?.let { container.player.playNext(it) } },
        onToggleFollow = { sub ->
            scope.launch {
                try {
                    if (sub != null) api.unsubscribe(sub.id) else api.subscribe(PodcastSubscribeRequest(feedUrl))
                    subscription.reload()
                    toast(if (sub != null) "Unfollowed $title" else "Following $title")
                } catch (e: Exception) {
                    toast(friendlyError(e))
                }
            }
        },
    )
}

/**
 * A show's page: its cover blown up and blurred into the whole top of the screen, the sharp
 * cover over it, Follow and Play latest, then every episode as a card whose play button is
 * ringed by how far through it this account has got.
 */
@Composable
fun PodcastDetailContent(
    title: String,
    feedUrl: String,
    artworkUrl: String?,
    episodes: Loadable<List<PodcastEpisodeDto>>,
    progress: Map<String, EpisodeProgressDto>,
    subscription: Loadable<PodcastSubscriptionDto?>,
    playingGuid: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPlay: (List<PodcastEpisodeDto>, PodcastEpisodeDto, Boolean) -> Unit,
    onPlayNext: (PodcastEpisodeDto) -> Unit,
    onToggleFollow: (PodcastSubscriptionDto?) -> Unit,
) {
    val list = episodes.valueOrNull.orEmpty()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(380.dp)) {
                Box(Modifier.fillMaxWidth().height(300.dp).blur(48.dp)) {
                    Artwork(artworkUrl, feedUrl, 600.dp, corner = 0.dp)
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), AuraBase)))
                )
                GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", Modifier.padding(14.dp), onClick = onBack)
                Column(Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Artwork(artworkUrl, feedUrl, 180.dp, corner = 28.dp)
                    Spacer(Modifier.height(14.dp))
                    Eyebrow("Podcast", color = LocalLumiPalette.current.accent)
                    Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        item {
            val sub = subscription.valueOrNull
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (list.isNotEmpty()) {
                    GlowButton("Play latest", Icons.Filled.PlayArrow, onClick = { onPlay(list, list.first(), false) }, modifier = Modifier.weight(1f))
                }
                if (sub != null) {
                    GlassButton("Following", Icons.Filled.Check, onClick = { onToggleFollow(sub) }, modifier = Modifier.weight(1f))
                } else {
                    GlassButton("Follow", Icons.Filled.Add, onClick = { onToggleFollow(null) }, modifier = Modifier.weight(1f))
                }
            }
        }
        item {
            LoadableSection(episodes, onRetry = onRetry) { eps ->
                Text(
                    "${eps.size} episodes",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
        }
        itemsIndexed(list, key = { index, it -> "$index|${it.guid ?: it.audioUrl}" }) { _, episode ->
            val guid = episode.guid ?: episode.audioUrl
            EpisodeCard(
                episode = episode,
                progress = guid?.let { progress[it] },
                isPlaying = guid != null && playingGuid == guid,
                onPlay = { onPlay(list, episode, false) },
                onFromStart = { onPlay(list, episode, true) },
                onPlayNext = { onPlayNext(episode) },
            )
        }
    }
}

@Composable
private fun EpisodeCard(
    episode: PodcastEpisodeDto,
    progress: EpisodeProgressDto?,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onFromStart: () -> Unit,
    onPlayNext: () -> Unit,
) {
    val palette = LocalLumiPalette.current
    var menuOpen by remember { mutableStateOf(false) }
    val fraction = progress?.takeIf { !it.completed && it.durationSeconds > 0 }?.let { (it.positionSeconds / it.durationSeconds).toFloat() } ?: 0f
    LumiCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), onClick = onPlay, corner = 20.dp) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val date = parseInstant(episode.publishedAt)?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                val length = episode.durationSeconds?.takeIf { it > 0 }?.let { "${(it + 59) / 60} min" }
                Eyebrow(listOfNotNull(date, length).joinToString(" · "))
                Spacer(Modifier.height(3.dp))
                Text(
                    episode.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isPlaying) palette.accent else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val summary = stripHtml(episode.description)
                if (summary.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable(onClick = onPlay), contentAlignment = Alignment.Center) {
                when {
                    isPlaying -> EqualizerBars(playing = true, color = palette.accent)
                    progress?.completed == true -> Icon(Icons.Filled.CheckCircle, contentDescription = "Played", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> {
                        RingProgress(fraction, Modifier.size(46.dp), stroke = 3.dp)
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play", modifier = Modifier.size(22.dp))
                    }
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Play from the start") }, onClick = { menuOpen = false; onFromStart() })
                    DropdownMenuItem(text = { Text("Play next") }, onClick = { menuOpen = false; onPlayNext() })
                }
            }
        }
    }
}

/** Feed descriptions are HTML; a teaser wants the words only. */
fun stripHtml(html: String): String =
    html.replace(Regex("<[^>]*>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(Regex("\\s+"), " ")
        .trim()
