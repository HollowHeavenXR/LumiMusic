package com.lumisound.android.ui.screens.social

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.lumisound.android.ui.aura.AuraBase
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lumisound.android.AppContainer
import com.lumisound.android.bridge.BridgeUrls
import com.lumisound.android.bridge.model.CompatibilityDto
import com.lumisound.android.bridge.model.FriendRequestCreate
import com.lumisound.android.bridge.model.ProfileDto
import com.lumisound.android.ui.aura.Aura
import com.lumisound.android.ui.components.Eyebrow
import com.lumisound.android.ui.components.GlassButton
import com.lumisound.android.ui.components.GlassIconButton
import com.lumisound.android.ui.components.GlassPanel
import com.lumisound.android.ui.components.GlowButton
import com.lumisound.android.ui.components.IconSectionHeader
import com.lumisound.android.ui.components.Loadable
import com.lumisound.android.ui.components.LoadableSection
import com.lumisound.android.ui.components.Pill
import com.lumisound.android.ui.components.friendlyError
import com.lumisound.android.ui.components.rememberLoadable
import com.lumisound.android.ui.components.trackSubtitle
import com.lumisound.android.ui.screens.stats.RankedRow
import com.lumisound.android.ui.searchAndPlay
import com.lumisound.android.ui.theme.LocalLumiPalette
import com.lumisound.android.ui.theme.SectionTint
import com.lumisound.android.ui.theme.parseAccent
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(container: AppContainer, userId: String, name: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val api = container.http.social
    val profile = rememberLoadable(userId, "social.profile") { api.profile(userId) }
    // Friends only server-side; a stranger's 403 just leaves the section out.
    val match = rememberLoadable(userId, "social.match") { api.compatibility(userId) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    ProfileContent(
        name = name,
        profile = profile.state,
        match = match.state,
        avatarModel = BridgeUrls.avatar(container.config.baseUrl, userId),
        onBack = onBack,
        onRetry = profile::reload,
        onAdd = { p ->
            scope.launch {
                try {
                    api.sendRequest(FriendRequestCreate(toUserId = p.userId))
                    toast("Request sent to ${p.shownName}")
                } catch (e: Exception) {
                    toast(friendlyError(e))
                }
            }
        },
        onRemove = { p ->
            scope.launch {
                try {
                    api.removeFriend(p.userId)
                    toast("Removed ${p.shownName} from your circle")
                    profile.reload()
                } catch (e: Exception) {
                    toast(friendlyError(e))
                }
            }
        },
        onPlay = { title, artist -> scope.launch { if (!container.searchAndPlay(title, artist)) toast("No match for $title.") } },
    )
}

/**
 * Another listener's page: a banner in their own accent, their avatar breaking out of it,
 * and -- between friends -- a Music Match dial. What they are into (artists, genres, pinned
 * tracks, badges) follows, each in its own block.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileContent(
    name: String,
    profile: Loadable<ProfileDto>,
    match: Loadable<CompatibilityDto>,
    avatarModel: Any?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAdd: (ProfileDto) -> Unit,
    onRemove: (ProfileDto) -> Unit,
    onPlay: (String, String?) -> Unit,
) {
    val palette = LocalLumiPalette.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
        val p = profile.valueOrNull
        val accent = p?.mainAccentHex?.let { parseAccent(it) }
        val aura = if (accent != null) Aura.forAccent(accent) else Aura.forKey(p?.username ?: name)
        Box(Modifier.fillMaxWidth().height(250.dp)) {
            // The banner: their colours as two overlapping glows on dark.
            Canvas(Modifier.fillMaxWidth().height(190.dp)) {
                drawRect(Brush.linearGradient(listOf(aura.primary, aura.secondary, Color(0xFF10111C))))
                drawCircle(Color.White.copy(alpha = 0.10f), size.minDimension * 0.55f, Offset(size.width * 0.85f, size.height * 0.1f))
                drawCircle(Color.Black.copy(alpha = 0.18f), size.minDimension * 0.7f, Offset(size.width * 0.1f, size.height * 1.1f))
            }
            Box(Modifier.fillMaxWidth().height(190.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xAA07080F)))))
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", Modifier.padding(14.dp), onClick = onBack)
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .size(116.dp)
                    .clip(CircleShape)
                    .background(AuraBase)
                    .padding(5.dp)
            ) {
                Avatar(avatarModel, p?.username ?: name, 106.dp)
            }
        }

        LoadableSection(profile, onRetry) { profileData ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(profileData.shownName, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
                Text(
                    listOfNotNull("@${profileData.username}", profileData.pronouns?.takeIf { it.isNotBlank() }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val status = listOfNotNull(profileData.statusEmoji, profileData.statusText).joinToString(" ").trim()
                if (status.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Pill(status, tint = accent ?: palette.accent)
                }
                profileData.bio?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(16.dp))
                if (profileData.isFriend) {
                    GlassButton("In your circle", Icons.Filled.PersonRemove, onClick = { onRemove(profileData) })
                } else {
                    GlowButton("Add to circle", Icons.Filled.PersonAdd, onClick = { onAdd(profileData) })
                }
            }

            // Three facts in a row.
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Fact("${profileData.listeningStreak?.currentStreakDays ?: 0}", "day streak", Modifier.weight(1f))
                Fact("${profileData.badges.size}", "badges", Modifier.weight(1f))
                Fact(profileData.memberSince?.take(4) ?: "—", "joined", Modifier.weight(1f))
            }

            match.valueOrNull?.let { MatchDial(it) }

            if (profileData.topArtists.isNotEmpty()) {
                IconSectionHeader(Icons.Filled.Person, "On repeat", tint = SectionTint.Favorites)
                profileData.topArtists.take(5).forEachIndexed { index, artist -> RankedRow(index + 1, artist, null, "") }
            }
            if (profileData.topGenres.isNotEmpty()) {
                IconSectionHeader(Icons.Filled.Tag, "Their sound", tint = SectionTint.Offline)
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) { profileData.topGenres.forEach { Pill(it) } }
            }
            if (profileData.badges.isNotEmpty()) {
                IconSectionHeader(Icons.Filled.MilitaryTech, "Badges", tint = SectionTint.Device)
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) { profileData.badges.forEach { badge -> Pill(badge.label, tint = badgeTint(badge.tier)) } }
            }
            if (profileData.pinnedTracks.isNotEmpty()) {
                IconSectionHeader(Icons.Filled.PushPin, "Pinned", tint = SectionTint.Playlists)
                profileData.pinnedTracks.forEachIndexed { index, pin ->
                    val title = pin.title ?: return@forEachIndexed
                    RankedRow(index + 1, title, trackSubtitle(title, pin.artist, pin.album), "Play", onClick = { onPlay(title, pin.artist) })
                }
            }
        }
    }
}

@Composable
private fun Fact(value: String, label: String, modifier: Modifier) {
    Column(
        modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
            .background(LocalLumiPalette.current.elevatedSurface)
            .border(1.dp, LocalLumiPalette.current.hairline, androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Eyebrow(label)
    }
}

/**
 * Music Match as a dial: a half-circle gauge filled to the score, the number in the middle,
 * and the reasons underneath -- a percentage alone cannot be agreed or disagreed with.
 */
@Composable
private fun MatchDial(match: CompatibilityDto) {
    val palette = LocalLumiPalette.current
    IconSectionHeader(Icons.Filled.Favorite, "Music match", tint = SectionTint.Favorites)
    GlassPanel {
        if (match.insufficientData) {
            Text("Not enough listening yet on one side to compare.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@GlassPanel
        }
        Box(Modifier.fillMaxWidth().height(130.dp), contentAlignment = Alignment.BottomCenter) {
            Canvas(Modifier.size(width = 240.dp, height = 120.dp)) {
                val stroke = 16.dp.toPx()
                val arcSize = Size(size.width - stroke, (size.height - stroke / 2) * 2)
                val topLeft = Offset(stroke / 2, stroke / 2)
                drawArc(Color.White.copy(alpha = 0.10f), 180f, 180f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(
                    Brush.horizontalGradient(listOf(Color(0xFF7F5AF0), palette.accent)),
                    180f,
                    180f * (match.score.coerceIn(0, 100) / 100f),
                    false,
                    topLeft,
                    arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = 4.dp)) {
                Text("${match.score}%", style = MaterialTheme.typography.displayMedium)
                Eyebrow("taste match")
            }
        }
        // The arc's round caps hang below the canvas; keep the reasons clear of them.
        Spacer(Modifier.height(14.dp))
        match.reasons.forEach {
            Spacer(Modifier.height(6.dp))
            Text("• $it", style = MaterialTheme.typography.bodyMedium)
        }
        if (match.sharedArtists.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                "You both play " + match.sharedArtists.take(6).joinToString(", "),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun badgeTint(tier: String?) = when (tier) {
    "gold" -> Color(0xFFF6C744)
    "silver" -> Color(0xFFCBD5E0)
    "bronze" -> Color(0xFFDD9A5B)
    else -> null
}
