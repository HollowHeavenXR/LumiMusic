package com.lumisound.android.ui.screens.social

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lumisound.android.AppContainer
import com.lumisound.android.bridge.BridgeUrls
import com.lumisound.android.bridge.model.FriendActivityDto
import com.lumisound.android.bridge.model.FriendDto
import com.lumisound.android.bridge.model.FriendRequestCreate
import com.lumisound.android.bridge.model.FriendRequestDto
import com.lumisound.android.bridge.model.FriendRequestsResponse
import com.lumisound.android.bridge.model.PresenceDto
import com.lumisound.android.bridge.model.PublicUserDto
import com.lumisound.android.ui.Route
import com.lumisound.android.ui.aura.Aura
import com.lumisound.android.ui.components.EmptyState
import com.lumisound.android.ui.components.EqualizerBars
import com.lumisound.android.ui.components.Eyebrow
import com.lumisound.android.ui.components.FallbackArt
import com.lumisound.android.ui.components.GlassButton
import com.lumisound.android.ui.components.GlassIconButton
import com.lumisound.android.ui.components.GlowButton
import com.lumisound.android.ui.components.Loadable
import com.lumisound.android.ui.components.LoadableSection
import com.lumisound.android.ui.components.LumiCard
import com.lumisound.android.ui.components.OneLine
import com.lumisound.android.ui.components.Pill
import com.lumisound.android.ui.components.ScreenTitle
import com.lumisound.android.ui.components.SearchField
import com.lumisound.android.ui.components.SegmentedPill
import com.lumisound.android.ui.components.friendlyError
import com.lumisound.android.ui.components.rememberLoadable
import com.lumisound.android.ui.searchAndPlay
import com.lumisound.android.ui.theme.LocalLumiPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class SocialTab(val label: String) { Friends("Friends"), Activity("Activity"), Requests("Requests"), Find("Find") }

/** A friend with whatever presence the bridge reported for them. */
data class FriendWithPresence(val friend: FriendDto, val presence: PresenceDto?)

data class SocialUiState(
    val tab: SocialTab = SocialTab.Friends,
    val friends: Loadable<List<FriendWithPresence>> = Loadable.Loading,
    val requests: Loadable<FriendRequestsResponse> = Loadable.Loading,
    val activity: Loadable<List<FriendActivityDto>> = Loadable.Loading,
    val query: String = "",
    val searchResults: Loadable<List<PublicUserDto>> = Loadable.Ready(emptyList()),
    val pendingIds: Set<String> = emptySet(),
    val now: OffsetDateTime = OffsetDateTime.now(),
)

data class SocialCallbacks(
    val onTab: (SocialTab) -> Unit = {},
    val onQuery: (String) -> Unit = {},
    val onOpenProfile: (String, String) -> Unit = { _, _ -> },
    val onPlayTheirs: (String, String?) -> Unit = { _, _ -> },
    val onAccept: (FriendRequestDto) -> Unit = {},
    val onDecline: (FriendRequestDto) -> Unit = {},
    val onCancel: (FriendRequestDto) -> Unit = {},
    val onAdd: (PublicUserDto) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRetrySearch: () -> Unit = {},
    val avatarFor: (String) -> Any? = { null },
)

/**
 * Friends, the same friend graph Lumisound uses, rebuilt around "who is listening right now".
 * Presence is refreshed every thirty seconds while this screen is open -- the bridge's own
 * freshness window is ninety.
 */
@Composable
fun SocialScreen(container: AppContainer, onOpen: (Route) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val api = container.http.social
    var tab by rememberSaveable { mutableStateOf(SocialTab.Friends) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<Loadable<List<PublicUserDto>>>(Loadable.Ready(emptyList())) }
    var sentTo by remember { mutableStateOf(setOf<String>()) }
    var searchGeneration by remember { mutableIntStateOf(0) }

    val friends = rememberLoadable(Unit, "social.friends") { api.friends().friends }
    val presence = rememberLoadable(Unit, "social.presence") { api.friendsPresence().presence }
    val requests = rememberLoadable(Unit, "social.requests") { api.requests() }
    val activity = rememberLoadable(Unit, "social.activity") { api.friendsActivity().activity }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            presence.reload()
        }
    }
    LaunchedEffect(query, searchGeneration) {
        val q = query.trim()
        if (q.length < 2) {
            searchResults = Loadable.Ready(emptyList())
            return@LaunchedEffect
        }
        delay(350)
        searchResults = Loadable.Loading
        searchResults = try {
            Loadable.Ready(api.searchUsers(q).users)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Loadable.Failed(friendlyError(e))
        }
    }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    fun act(done: String, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                toast(done)
                requests.reload()
                friends.reload()
                presence.reload()
            } catch (e: Exception) {
                toast(friendlyError(e))
            }
        }
    }

    val presenceById = presence.state.valueOrNull.orEmpty().associateBy { it.userId }
    val merged: Loadable<List<FriendWithPresence>> = when (val f = friends.state) {
        is Loadable.Ready -> Loadable.Ready(f.value.map { FriendWithPresence(it, presenceById[it.userId]) })
        is Loadable.Failed -> f
        Loadable.Loading -> Loadable.Loading
    }

    SocialContent(
        state = SocialUiState(
            tab = tab,
            friends = merged,
            requests = requests.state,
            activity = activity.state,
            query = query,
            searchResults = searchResults,
            pendingIds = requests.state.valueOrNull?.outgoing.orEmpty().map { it.userId }.toSet() + sentTo,
        ),
        callbacks = SocialCallbacks(
            onTab = { tab = it },
            onQuery = { query = it },
            onOpenProfile = { id, name -> onOpen(Route.Profile(id, name)) },
            onPlayTheirs = { title, artist -> scope.launch { if (!container.searchAndPlay(title, artist)) toast("No match for $title.") } },
            onAccept = { r -> act("You're now friends with ${r.shownName}") { api.accept(r.requestId) } },
            onDecline = { r -> act("Declined") { api.decline(r.requestId) } },
            onCancel = { r -> act("Request cancelled") { api.cancel(r.requestId) } },
            onAdd = { user ->
                sentTo = sentTo + user.userId
                act("Request sent to ${user.shownName}") { api.sendRequest(FriendRequestCreate(toUserId = user.userId)) }
            },
            onRefresh = { listOf(friends, presence, requests, activity).forEach { it.reload() } },
            onRetrySearch = { searchGeneration++ },
            avatarFor = { BridgeUrls.avatar(container.config.baseUrl, it) },
        ),
    )
}

/**
 * Circle: friends as a live room rather than a contact list.
 *
 * Whoever is playing something this minute gets a big card at the top, in the colour of
 * what they are playing, with a Listen button that plays the same track here. Everyone else
 * is below, online first. Activity is a timeline grouped by day; requests and search each
 * have their own face of the same screen.
 */
@Composable
fun SocialContent(state: SocialUiState, callbacks: SocialCallbacks) {
    val incoming = state.requests.valueOrNull?.incoming.orEmpty()
    val friendList = state.friends.valueOrNull.orEmpty()
    val live = friendList.filter { it.presence?.isPlaying == true && it.presence?.nowPlayingTitle != null }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle(
            "Circle",
            subtitle = if (state.friends is Loadable.Ready) "${friendList.size} friends · ${live.size} listening now" else "Listening together, apart",
            eyebrow = "Friends",
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassIconButton(Icons.Filled.Refresh, "Refresh", onClick = callbacks.onRefresh)
                    GlassIconButton(Icons.Filled.PersonAdd, "Find people", selected = state.tab == SocialTab.Find, onClick = { callbacks.onTab(SocialTab.Find) })
                }
            },
        )
        SegmentedPill(
            options = SocialTab.entries,
            selected = state.tab,
            label = { if (it == SocialTab.Requests && incoming.isNotEmpty()) "${it.label} ${incoming.size}" else it.label },
            onSelect = callbacks.onTab,
        )
        Spacer(Modifier.height(8.dp))

        when (state.tab) {
            SocialTab.Friends -> LoadableSection(state.friends, onRetry = callbacks.onRefresh) { list ->
                if (list.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.People,
                        title = "Your circle is empty",
                        message = "Find people by username to see what they're listening to, compare your taste, and play what they're playing.",
                        action = { GlowButton("Find people", Icons.Filled.PersonAdd, onClick = { callbacks.onTab(SocialTab.Find) }) },
                    )
                    return@LoadableSection
                }
                val rest = list.filterNot { it in live }
                    .sortedWith(compareByDescending<FriendWithPresence> { it.presence?.online == true }.thenBy { it.friend.shownName.lowercase() })
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (live.isNotEmpty()) {
                        item { Eyebrow("Listening now", Modifier.padding(start = 20.dp, top = 10.dp, bottom = 10.dp), color = LocalLumiPalette.current.accent) }
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(live, key = { it.friend.userId }) { entry ->
                                    LiveCard(entry, callbacks.avatarFor(entry.friend.userId), callbacks)
                                }
                            }
                        }
                    }
                    item { Eyebrow("Everyone", Modifier.padding(start = 20.dp, top = 22.dp, bottom = 6.dp)) }
                    items(rest, key = { it.friend.userId }) { entry ->
                        FriendRow(entry, callbacks.avatarFor(entry.friend.userId), state.now, onClick = {
                            callbacks.onOpenProfile(entry.friend.userId, entry.friend.shownName)
                        })
                    }
                }
            }

            SocialTab.Activity -> LoadableSection(state.activity, onRetry = callbacks.onRefresh) { rows ->
                if (rows.isEmpty()) {
                    EmptyState(Icons.Filled.Timeline, "Quiet in here", "When friends play or favorite something, it shows up here.")
                    return@LoadableSection
                }
                val grouped = rows.groupBy { dayLabel(it.at, state.now) }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    grouped.forEach { (day, events) ->
                        item(key = "day-$day") { Eyebrow(day, Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp)) }
                        events.forEachIndexed { index, row ->
                            item(key = "ev-$day-$index") {
                                TimelineRow(row, callbacks.avatarFor(row.userId), isLast = index == events.lastIndex, now = state.now) {
                                    row.title?.let { callbacks.onPlayTheirs(it, row.artist) }
                                }
                            }
                        }
                    }
                }
            }

            SocialTab.Requests -> LoadableSection(state.requests, onRetry = callbacks.onRefresh) { data ->
                if (data.incoming.isEmpty() && data.outgoing.isEmpty()) {
                    EmptyState(Icons.Filled.Inbox, "No pending requests", "Requests you send and receive show up here.")
                    return@LoadableSection
                }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (data.incoming.isNotEmpty()) item { Eyebrow("Wants to be friends", Modifier.padding(start = 20.dp, top = 8.dp)) }
                    items(data.incoming, key = { "in-${it.requestId}" }) { request ->
                        RequestCard(request, callbacks.avatarFor(request.userId)) {
                            GlassButton("Decline", null, onClick = { callbacks.onDecline(request) })
                            GlowButton("Accept", null, onClick = { callbacks.onAccept(request) })
                        }
                    }
                    if (data.outgoing.isNotEmpty()) item { Eyebrow("Sent", Modifier.padding(start = 20.dp, top = 8.dp)) }
                    items(data.outgoing, key = { "out-${it.requestId}" }) { request ->
                        RequestCard(request, callbacks.avatarFor(request.userId)) {
                            GlassButton("Cancel", null, onClick = { callbacks.onCancel(request) })
                        }
                    }
                }
            }

            SocialTab.Find -> Column(Modifier.fillMaxSize()) {
                SearchField(state.query, callbacks.onQuery, "Search by username")
                LoadableSection(state.searchResults, onRetry = callbacks.onRetrySearch) { users ->
                    val friendIds = friendList.map { it.friend.userId }.toSet()
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(users, key = { it.userId }) { user ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { callbacks.onOpenProfile(user.userId, user.shownName) }
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Avatar(callbacks.avatarFor(user.userId), user.username, 48.dp)
                                Column(Modifier.weight(1f)) {
                                    OneLine(user.shownName, style = MaterialTheme.typography.titleSmall)
                                    OneLine("@${user.username}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                when {
                                    user.userId in friendIds -> Pill("Friends")
                                    user.userId in state.pendingIds -> Pill("Requested")
                                    else -> GlowButton("Add", Icons.Filled.PersonAdd, onClick = { callbacks.onAdd(user) })
                                }
                            }
                        }
                    }
                    if (users.isEmpty() && state.query.trim().length >= 2) {
                        Text("No one by that name.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
                    }
                }
            }
        }
    }
}

/** A friend playing something now: a card in the colour of the track, with Listen. */
@Composable
private fun LiveCard(entry: FriendWithPresence, avatar: Any?, callbacks: SocialCallbacks) {
    val title = entry.presence?.nowPlayingTitle.orEmpty()
    val artist = entry.presence?.nowPlayingArtist
    val aura = Aura.forKey(title + artist.orEmpty())
    Column(
        Modifier
            .width(250.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(aura.primary.copy(alpha = 0.85f), aura.secondary.copy(alpha = 0.55f), Color(0xFF10111C))))
            .clickable { callbacks.onOpenProfile(entry.friend.userId, entry.friend.shownName) }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(avatar, entry.friend.username, 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                OneLine(entry.friend.shownName, style = MaterialTheme.typography.titleSmall, color = Color.White)
                Text("is listening to", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
            }
            EqualizerBars(playing = true, color = Color.White)
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
        artist?.let { OneLine(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f)) }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .clip(CircleShape)
                .background(Color.White)
                .clickable { callbacks.onPlayTheirs(title, artist) }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = AuraBase, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Listen too", style = MaterialTheme.typography.labelLarge, color = AuraBase)
        }
    }
}

/** A circular avatar from the bridge, with the generated art underneath for anyone without one. */
@Composable
fun Avatar(url: Any?, fallbackKey: String, size: Dp, online: Boolean? = null) {
    val palette = LocalLumiPalette.current
    Box {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .then(if (online == true) Modifier.border(2.dp, Color(0xFF48BB78), CircleShape) else Modifier)
        ) {
            FallbackArt(fallbackKey, Modifier.fillMaxSize())
            if (url != null) {
                AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        if (online == true) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(size / 4)
                    .clip(CircleShape)
                    .background(palette.pageBottom)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF48BB78))
            )
        }
    }
}

@Composable
private fun FriendRow(entry: FriendWithPresence, avatar: Any?, now: OffsetDateTime, onClick: () -> Unit) {
    val presence = entry.presence
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Avatar(avatar, entry.friend.username, 50.dp, online = presence?.online)
        Column(Modifier.weight(1f)) {
            OneLine(entry.friend.shownName, style = MaterialTheme.typography.titleSmall)
            OneLine(
                if (presence?.online == true) "Online" else lastSeen(presence?.lastSeenAt, now),
                style = MaterialTheme.typography.labelMedium,
                color = if (presence?.online == true) Color(0xFF48BB78) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (entry.friend.tags.isNotEmpty()) Pill(entry.friend.tags.first())
    }
}

@Composable
private fun RequestCard(request: FriendRequestDto, avatar: Any?, actions: @Composable () -> Unit) {
    LumiCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(avatar, request.username, 48.dp)
            Column(Modifier.weight(1f)) {
                OneLine(request.shownName, style = MaterialTheme.typography.titleSmall)
                OneLine("@${request.username}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
        }
    }
}

/** One event on the activity timeline: a rail with a dot, the friend, what they did. */
@Composable
private fun TimelineRow(row: FriendActivityDto, avatar: Any?, isLast: Boolean, now: OffsetDateTime, onClick: () -> Unit) {
    val palette = LocalLumiPalette.current
    val favorited = row.kind == "favorited"
    Row(Modifier.fillMaxWidth().height(76.dp).clickable(onClick = onClick).padding(start = 26.dp, end = 20.dp)) {
        Box(Modifier.width(16.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            if (!isLast) Box(Modifier.padding(top = 16.dp).width(2.dp).fillMaxHeight().background(palette.hairline))
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (favorited) palette.accent else Color.White.copy(alpha = 0.6f))
            )
        }
        Spacer(Modifier.width(12.dp))
        Row(Modifier.weight(1f).padding(top = 2.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Avatar(avatar, row.username, 34.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    "${row.shownName} ${if (favorited) "loved" else "played"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OneLine(row.title.orEmpty(), style = MaterialTheme.typography.titleSmall)
                row.artist?.takeIf { it.isNotBlank() }?.let {
                    OneLine(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (favorited) Icon(Icons.Filled.Favorite, contentDescription = null, tint = palette.accent, modifier = Modifier.size(16.dp))
            Text(timeAgo(row.at, now), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "Today", "Yesterday", or a date -- the activity timeline's day headings. */
fun dayLabel(iso: String?, now: OffsetDateTime): String {
    val then = parseInstant(iso) ?: return "Earlier"
    val zone = ZoneId.systemDefault()
    val day = then.atZoneSameInstant(zone).toLocalDate()
    val today: LocalDate = now.atZoneSameInstant(zone).toLocalDate()
    return when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(DateTimeFormatter.ofPattern("EEEE d MMM"))
    }
}

/** "5m", "3h", "2d" from an ISO timestamp; blank when there is none or it will not parse. */
fun timeAgo(iso: String?, now: OffsetDateTime = OffsetDateTime.now()): String {
    val then = parseInstant(iso) ?: return ""
    val minutes = Duration.between(then, now).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 60 * 24 -> "${minutes / 60}h"
        minutes < 60 * 24 * 30 -> "${minutes / (60 * 24)}d"
        else -> "${minutes / (60 * 24 * 30)}mo"
    }
}

private fun lastSeen(iso: String?, now: OffsetDateTime): String =
    timeAgo(iso, now).let { if (it.isEmpty()) "Offline" else if (it == "now") "Just now" else "Seen $it ago" }

/**
 * The bridge writes timestamps with Python's `isoformat()`, which leaves off the offset
 * for naive (UTC) values. Both shapes are accepted, the naive one read as UTC.
 */
fun parseInstant(iso: String?): OffsetDateTime? {
    if (iso.isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(iso) }.getOrNull()
        ?: runCatching { java.time.LocalDateTime.parse(iso).atOffset(java.time.ZoneOffset.UTC) }.getOrNull()
}
