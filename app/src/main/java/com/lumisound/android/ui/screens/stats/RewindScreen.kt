package com.lumisound.android.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.lumisound.android.ui.aura.AuraBase
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumisound.android.AppContainer
import com.lumisound.android.bridge.model.LifetimeStatsDto
import com.lumisound.android.bridge.model.ReviewDto
import com.lumisound.android.ui.aura.Aura
import com.lumisound.android.ui.components.Eyebrow
import com.lumisound.android.ui.components.SegmentedPill
import com.lumisound.android.ui.components.storyTaps
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.text.style.TextOverflow
import com.lumisound.android.ui.components.Loadable
import com.lumisound.android.ui.components.LoadableSection
import com.lumisound.android.ui.components.NavChip
import com.lumisound.android.ui.components.OneLine
import com.lumisound.android.ui.components.rememberLoadable
import com.lumisound.android.ui.theme.LocalLumiPalette
import com.lumisound.android.ui.theme.SectionTint
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

enum class RewindPeriod(val label: String) { AllTime("All time"), Month("This month"), Year("This year") }

/**
 * A recap in one card, after Lumisound's Rewind: All Time from the lifetime totals, This
 * Month and This Year from the review endpoints, which add distinct counts, average BPM
 * and the peak day. Shareable as text, since the numbers are the point.
 */
data class RewindSummary(
    val heading: String,
    val plays: Int,
    val listenSeconds: Long,
    val topArtists: List<String>,
    val topTracks: List<Pair<String, String?>>,
    val distinctArtists: Int? = null,
    val distinctTracks: Int? = null,
    val averageBpm: Double? = null,
    val peakDay: String? = null,
)

fun LifetimeStatsDto.toSummary() = RewindSummary(
    heading = "All time",
    plays = totalPlays,
    listenSeconds = totalListenSeconds,
    topArtists = topArtists.map { it.artist },
    topTracks = topTracks.map { it.title to it.artist },
)

fun ReviewDto.toSummary(): RewindSummary {
    val heading = month?.let { "${Month.of(it).getDisplayName(TextStyle.FULL, Locale.getDefault())} $year" } ?: "$year"
    return RewindSummary(
        heading = heading,
        plays = totalPlays,
        listenSeconds = totalListenSeconds,
        topArtists = topArtists.map { it.artist },
        topTracks = topTracks.map { it.title to it.artist },
        distinctArtists = distinctArtists,
        distinctTracks = distinctTracks,
        averageBpm = averageBpm,
        peakDay = peakDay?.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let {
            "${it.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}, ${it.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${it.dayOfMonth}"
        },
    )
}

/** The plain-text version for the share sheet. */
fun RewindSummary.asShareText(): String = buildString {
    appendLine("My LumiMusic Rewind — $heading")
    appendLine("$plays plays · ${listenSeconds.asListeningTime()} listened")
    if (topArtists.isNotEmpty()) appendLine("Top artists: ${topArtists.take(3).joinToString(", ")}")
    topTracks.firstOrNull()?.let { (title, artist) ->
        appendLine("Top track: $title${artist?.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()}")
    }
    peakDay?.let { appendLine("Peak day: $it") }
}.trim()

@Composable
fun RewindScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    var period by rememberSaveable { mutableStateOf(RewindPeriod.Month) }
    var page by rememberSaveable(period) { mutableIntStateOf(0) }
    val api = container.http.discovery
    val summary = rememberLoadable(period, "rewind.$period") {
        when (period) {
            RewindPeriod.AllTime -> api.lifetimeStats().toSummary()
            RewindPeriod.Month -> api.monthInReview().toSummary()
            RewindPeriod.Year -> api.yearInReview().toSummary()
        }
    }
    RewindContent(
        period = period,
        summary = summary.state,
        page = page,
        onPageChange = { page = it },
        onPeriodChange = { period = it },
        onBack = onBack,
        onRetry = summary::reload,
        onShare = { text ->
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, text)
            }
            context.startActivity(android.content.Intent.createChooser(send, "Share your Rewind"))
        },
    )
}

/** One page of the Rewind story. */
private sealed interface Story {
    data class Hours(val summary: RewindSummary) : Story
    data class TopArtist(val name: String, val others: List<String>) : Story
    data class TopTracks(val tracks: List<Pair<String, String?>>) : Story
    data class Numbers(val summary: RewindSummary) : Story
    data class Peak(val day: String) : Story
    data class Wrap(val summary: RewindSummary) : Story
}

private fun storiesFor(s: RewindSummary): List<Story> = buildList {
    add(Story.Hours(s))
    s.topArtists.firstOrNull()?.let { add(Story.TopArtist(it, s.topArtists.drop(1).take(4))) }
    if (s.topTracks.isNotEmpty()) add(Story.TopTracks(s.topTracks.take(5)))
    if (s.distinctArtists != null || s.distinctTracks != null || s.averageBpm != null) add(Story.Numbers(s))
    s.peakDay?.let { add(Story.Peak(it)) }
    add(Story.Wrap(s))
}

/**
 * Rewind told as a story: one fact per page, each on its own colour, with the progress
 * segments across the top. Tap the right of the card to go on, the left to go back; the
 * last page is the whole recap on one card with Share. A single long card said everything
 * at once and so made nothing land.
 */
@Composable
fun RewindContent(
    period: RewindPeriod,
    summary: Loadable<RewindSummary>,
    page: Int,
    onPageChange: (Int) -> Unit,
    onPeriodChange: (RewindPeriod) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onShare: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(bottom = 12.dp)) {
        DetailHeader("Rewind", null, onBack, eyebrow = "Your listening, wrapped")
        SegmentedPill(RewindPeriod.entries, period, { it.label }, onPeriodChange)
        Spacer(Modifier.height(14.dp))
        LoadableSection(summary, onRetry) { s ->
            val stories = storiesFor(s)
            val current = page.coerceIn(0, stories.lastIndex)
            val aura = Aura.forKey("rewind-${period.name}-$current")
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(540.dp)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Brush.linearGradient(listOf(aura.primary, aura.secondary, Color(0xFF12101E))))
                    .storyTaps(
                        onBack = { if (current > 0) onPageChange(current - 1) },
                        onForward = { if (current < stories.lastIndex) onPageChange(current + 1) },
                    )
            ) {
                // Progress segments.
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    stories.indices.forEach { i ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = if (i <= current) 0.95f else 0.3f))
                        )
                    }
                }
                Column(Modifier.align(Alignment.BottomStart).padding(24.dp)) {
                    StoryPage(stories[current], s, period, onShare)
                }
            }
            Text(
                "Tap the right side to continue · ${current + 1} of ${stories.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StoryPage(story: Story, s: RewindSummary, period: RewindPeriod, onShare: (String) -> Unit) {
    val white = Color.White
    val soft = Color.White.copy(alpha = 0.8f)
    val huge = MaterialTheme.typography.displayLarge.copy(fontSize = 96.sp, lineHeight = 92.sp)
    when (story) {
        is Story.Hours -> {
            Eyebrow(if (period == RewindPeriod.AllTime) "Since you started" else s.heading, color = soft)
            Text("You listened for", style = MaterialTheme.typography.headlineSmall, color = white)
            Text("${story.summary.listenSeconds / 3600}", style = huge, color = white)
            Text("hours", style = MaterialTheme.typography.displaySmall, color = white)
            Spacer(Modifier.height(8.dp))
            Text("across ${"%,d".format(story.summary.plays)} plays.", style = MaterialTheme.typography.titleMedium, color = soft)
        }
        is Story.TopArtist -> {
            Eyebrow("Your number one", color = soft)
            Text(story.name, style = MaterialTheme.typography.displayLarge, color = white, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(14.dp))
            if (story.others.isNotEmpty()) {
                Text("followed by", style = MaterialTheme.typography.labelLarge, color = soft)
                story.others.forEachIndexed { i, name ->
                    Text("${i + 2}. $name", style = MaterialTheme.typography.titleLarge, color = white, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        is Story.TopTracks -> {
            Eyebrow("On repeat", color = soft)
            Text("Your top tracks", style = MaterialTheme.typography.displaySmall, color = white)
            Spacer(Modifier.height(14.dp))
            story.tracks.forEachIndexed { i, (title, artist) ->
                Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", style = MaterialTheme.typography.headlineSmall, color = white, modifier = Modifier.width(34.dp))
                    Column {
                        Text(title, style = MaterialTheme.typography.titleMedium, color = white, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        artist?.takeIf { it.isNotBlank() && it != title }?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = soft, maxLines = 1) }
                    }
                }
            }
        }
        is Story.Numbers -> {
            Eyebrow("By the numbers", color = soft)
            story.summary.distinctArtists?.let { BigFact("$it", "different artists") }
            story.summary.distinctTracks?.let { BigFact("$it", "different tracks") }
            story.summary.averageBpm?.let { BigFact("${it.toInt()}", "beats a minute, on average") }
        }
        is Story.Peak -> {
            Eyebrow("Your biggest day", color = soft)
            Text(story.day, style = MaterialTheme.typography.displayMedium, color = white)
            Spacer(Modifier.height(8.dp))
            Text("More music than any other day.", style = MaterialTheme.typography.titleMedium, color = soft)
        }
        is Story.Wrap -> {
            Eyebrow("That's a wrap · ${story.summary.heading}", color = soft)
            Text("${story.summary.listenSeconds.asListeningTime()} · ${"%,d".format(story.summary.plays)} plays", style = MaterialTheme.typography.headlineSmall, color = white)
            Spacer(Modifier.height(10.dp))
            story.summary.topArtists.take(3).forEachIndexed { i, a -> Text("${i + 1}. $a", style = MaterialTheme.typography.titleMedium, color = white) }
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .clickable { onShare(story.summary.asShareText()) }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, tint = AuraBase, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Share your Rewind", style = MaterialTheme.typography.labelLarge, color = AuraBase)
            }
        }
    }
}

@Composable
private fun BigFact(value: String, label: String) {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(value, style = MaterialTheme.typography.displayMedium, color = Color.White)
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(bottom = 8.dp))
    }
}
