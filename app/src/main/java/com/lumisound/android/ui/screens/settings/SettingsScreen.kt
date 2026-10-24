package com.lumisound.android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisound.android.AppContainer
import com.lumisound.android.BuildConfig
import com.lumisound.android.bridge.AccountState
import com.lumisound.android.bridge.BridgeHttp
import com.lumisound.android.bridge.BridgeUrls
import com.lumisound.android.bridge.UpdateResult
import com.lumisound.android.bridge.model.UpdateSettingsRequest
import com.lumisound.android.ui.Route
import com.lumisound.android.ui.aura.LocalAura
import com.lumisound.android.ui.components.AvatarPickerSheet
import com.lumisound.android.ui.components.GlassButton
import com.lumisound.android.ui.components.GlowButton
import com.lumisound.android.ui.components.Hairline
import com.lumisound.android.ui.components.OneLine
import com.lumisound.android.ui.components.Pill
import com.lumisound.android.ui.components.ScreenTitle
import com.lumisound.android.ui.components.SettingsGroup
import com.lumisound.android.ui.components.SettingsRow
import com.lumisound.android.ui.screens.social.Avatar
import com.lumisound.android.ui.theme.EyebrowStyle
import com.lumisound.android.ui.theme.LocalLumiPalette
import com.lumisound.android.ui.theme.SectionTint
import kotlinx.coroutines.launch

data class SettingsUiState(
    val displayName: String,
    val username: String?,
    val avatarModel: Any?,
    val canEditAvatar: Boolean = false,
    val officialBridge: Boolean,
    val bridgeUrl: String,
    val hasSelfHostedKey: Boolean,
    val offlineCount: Int,
    val scanLabel: String,
    val version: String,
    val galleryLabel: String = "Photos from Lumisound on iPhone",
    /** "m4a"/"mp3"/"flac"/"opus"/"best" -- what a search result is transcoded to when saved to the library. */
    val preferredAudioFormat: String = "m4a",
    val monoDownmixEnabled: Boolean = false,
)

data class SettingsCallbacks(
    val onBack: (() -> Unit)? = null,
    val onOpen: (Route) -> Unit = {},
    val onImport: () -> Unit = {},
    val onSignOut: () -> Unit = {},
    val onChangeUsername: suspend (String) -> UpdateResult = { UpdateResult.Failure("Not signed in") },
    val onSetAudioFormat: (String) -> Unit = {},
    val onSetMonoDownmix: (Boolean) -> Unit = {},
    val onAvatarUploaded: () -> Unit = {},
    val onRescan: () -> Unit = {},
    val onSaveBridge: (String) -> String = { it },
    val onSaveSelfHostedKey: (String?) -> Unit = {},
    // Returns the fresh official URL, same shape as onSaveBridge above --
    // container.config.baseUrl is a plain SharedPreferences-backed property,
    // not an observable StateFlow, so nothing recomposes this screen just
    // because it changed underneath it. Threading the fresh value back
    // through the return is how onSaveBridge already copes with that; this
    // needs the same treatment or the text field would keep showing the
    // just-abandoned custom URL until something unrelated forced a redraw.
    val onResetBridge: () -> String = { "" },
)

@Composable
fun SettingsScreen(
    container: AppContainer,
    onOpenImport: () -> Unit,
    onOpen: (Route) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var avatarCacheBust by remember { mutableStateOf(0L) }
    val account by container.account.state.collectAsStateWithLifecycle()
    val scanState by container.libraryScanner.state.collectAsStateWithLifecycle()
    val gallery by container.gallery.state.collectAsStateWithLifecycle()
    val downloads by container.database.downloads().observePaths()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val user = (account as? AccountState.SignedIn)?.user
    val monoDownmixEnabled by container.monoDownmix.enabled.collectAsStateWithLifecycle()
    // Not a Flow -- this is a single remote field with no local cache of its
    // own, same reasoning as onSaveBridge's comment above about container.config.
    var preferredAudioFormat by remember { mutableStateOf("m4a") }
    LaunchedEffect(Unit) {
        preferredAudioFormat = try {
            container.http.libraryData.settings().preferredAudioFormat ?: "m4a"
        } catch (e: Exception) {
            "m4a"
        }
    }

    SettingsContent(
        http = container.http,
        state = SettingsUiState(
            displayName = user?.displayName?.takeIf { it.isNotBlank() } ?: user?.username ?: "Not signed in",
            username = user?.username,
            avatarModel = user?.id?.let { BridgeUrls.avatar(container.config.baseUrl, it, avatarCacheBust.takeIf { it > 0 }) },
            canEditAvatar = user != null,
            officialBridge = container.config.isOfficial,
            bridgeUrl = container.config.baseUrl,
            hasSelfHostedKey = container.config.hasSelfHostedKey,
            offlineCount = downloads.size,
            scanLabel = when {
                scanState.running -> "Scanning…"
                scanState.found > 0 -> "${scanState.found} tracks found"
                scanState.lastRunAt != null -> "Last scan found nothing"
                else -> "Not scanned yet"
            },
            version = "LumiMusic ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            galleryLabel = when {
                gallery.photos.isEmpty() -> "Photos from Lumisound on iPhone"
                gallery.settings.enabled -> "On · ${gallery.photos.size} photos from your iPhone"
                else -> "Off · ${gallery.photos.size} photos from your iPhone"
            },
            preferredAudioFormat = preferredAudioFormat,
            monoDownmixEnabled = monoDownmixEnabled,
        ),
        callbacks = SettingsCallbacks(
            onBack = onBack,
            onOpen = onOpen,
            onImport = onOpenImport,
            onSignOut = {
                scope.launch {
                    container.account.signOut()
                    container.appearance.clear()
                    container.gallery.clear()
                }
            },
            onChangeUsername = { newUsername -> container.account.updateUsername(newUsername) },
            onSetMonoDownmix = { on -> container.monoDownmix.setEnabled(on) },
            onAvatarUploaded = { avatarCacheBust = System.currentTimeMillis() },
            onSetAudioFormat = { format ->
                preferredAudioFormat = format
                scope.launch {
                    try {
                        container.http.libraryData.updateSettings(UpdateSettingsRequest(preferredAudioFormat = format))
                    } catch (e: Exception) {
                        // Stays selected locally either way; worst case a future
                        // save from this screen retries the same value.
                    }
                }
            },
            onRescan = { scope.launch { container.libraryScanner.scan() } },
            onSaveBridge = { url ->
                container.config.baseUrl = url
                container.config.baseUrl
            },
            onSaveSelfHostedKey = { key -> container.config.setSelfHostedKey(key) },
            onResetBridge = {
                container.config.resetToOfficial()
                container.config.baseUrl
            },
        ),
    )
}

private data class Control(val label: String, val detail: String?, val icon: ImageVector, val tint: Color, val route: Route)

/**
 * Rate-limited to twice per rolling 30 days server-side -- the error text
 * on a rejected save IS that rate-limit message (or "already taken"),
 * verbatim from the bridge, not a generic failure string.
 */
@Composable
private fun UsernameDialog(
    initialValue: String,
    onDismiss: () -> Unit,
    onSave: (newUsername: String, onResult: (UpdateResult) -> Unit) -> Unit,
) {
    var value by remember { mutableStateOf(initialValue) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val changed = value.trim() != initialValue && value.trim().length >= 3

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Change username") },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it; error = null },
                    label = { Text("Username") },
                    singleLine = true,
                    enabled = !saving,
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            if (saving) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                TextButton(
                    enabled = changed,
                    onClick = {
                        saving = true
                        onSave(value.trim()) { result ->
                            saving = false
                            when (result) {
                                is UpdateResult.Success -> onDismiss()
                                is UpdateResult.Failure -> error = result.message
                            }
                        }
                    },
                ) { Text("Save") }
            }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * Settings as a control room. The account leads, as a card in your own colours; the six
 * things people actually open settings for sit under it as a grid of tiles; the rarer
 * switches are grouped lists below.
 */
@Composable
fun SettingsContent(state: SettingsUiState, callbacks: SettingsCallbacks, http: BridgeHttp) {
    val palette = LocalLumiPalette.current
    val aura = LocalAura.current
    var bridgeUrl by remember(state.bridgeUrl) { mutableStateOf(state.bridgeUrl) }
    var editingBridge by remember { mutableStateOf(false) }
    var editingAvatar by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    // The "Connection" group below (self-hosted bridge URL/key) used to be an
    // always-visible top-level settings group — confusing noise for the 99%
    // of users who will never touch it, and a stray "server" field reading
    // as an unfinished/debug leftover to anyone who does notice it. Unlike
    // iOS's equivalent override (hidden behind `#if DEBUG`, dev-only there),
    // self-hosting is a real, intentionally-supported end-user feature here
    // (see `BridgeConfig`'s own doc comment) — so it can't just be deleted
    // or hidden in release builds the same way. Instead it's revealed the
    // way Android's own system Settings reveals Developer Options: tap the
    // version string a few times. Someone who has ALREADY pointed this
    // install at a custom bridge sees it unconditionally, so a real
    // self-hoster never has to rediscover the tap each time they open
    // Settings — only someone on the default/official bridge needs to.
    var connectionTapCount by remember { mutableStateOf(0) }
    var connectionUnlocked by remember { mutableStateOf(false) }
    val showConnection = connectionUnlocked || !state.officialBridge || BuildConfig.DEBUG
    var selfHostedKey by remember { mutableStateOf("") }
    var editingUsername by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
        ScreenTitle("Settings", eyebrow = "You", onBack = callbacks.onBack)

        // The account, as a card in the colours of whatever is playing.
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(aura.primary.copy(alpha = 0.6f), aura.secondary.copy(alpha = 0.3f), Color(0x3307080F))))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(28.dp))
                .padding(18.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clickable(enabled = state.canEditAvatar) { editingAvatar = true }) {
                        Avatar(state.avatarModel, state.username ?: state.displayName, 68.dp)
                    }
                    Spacer(Modifier.size(14.dp))
                    Column(Modifier.weight(1f)) {
                        OneLine(state.displayName, style = MaterialTheme.typography.titleLarge)
                        OneLine(
                            state.username?.let { "@$it" } ?: "—",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.clickable(enabled = state.username != null) { editingUsername = true },
                        )
                        Spacer(Modifier.height(6.dp))
                        Pill(if (state.officialBridge) "Official bridge" else "Self-hosted bridge", tint = Color.White)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "The same account as Lumisound: favorites, playlists, history and cloud tracks are shared between both apps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlowButton("Import", Icons.Filled.CloudDownload, onClick = callbacks.onImport, modifier = Modifier.weight(1f))
                    GlassButton("Sign out", Icons.AutoMirrored.Filled.Logout, onClick = callbacks.onSignOut, modifier = Modifier.weight(1f))
                }
            }
        }

        if (editingUsername) {
            UsernameDialog(
                initialValue = state.username.orEmpty(),
                onDismiss = { editingUsername = false },
                onSave = { newUsername, onResult ->
                    coroutineScope.launch {
                        onResult(callbacks.onChangeUsername(newUsername))
                    }
                },
            )
        }

        if (editingAvatar) {
            AvatarPickerSheet(
                http = http,
                onDismiss = { editingAvatar = false },
                onUploaded = { editingAvatar = false; callbacks.onAvatarUploaded() },
            )
        }

        Text(
            "QUICK CONTROLS",
            style = EyebrowStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 10.dp),
        )
        // "Diagnostics" used to sit here too, with equal visual weight to
        // the five things people actually open Settings for — a raw-log/
        // HTTP-latency screen presented as if it were a normal feature on
        // par with the equalizer or stats. Moved to its own "Advanced"
        // group further down, still reachable in one tap, just no longer
        // implying it's everyday business.
        val controls = listOf(
            Control("Equalizer", "Hardware bands", Icons.Filled.Equalizer, SectionTint.Offline, Route.Equalizer),
            Control("Offline", "${state.offlineCount} saved", Icons.Filled.DownloadForOffline, SectionTint.Library, Route.Downloads),
            Control("Scrobbling", "Last.fm · ListenBrainz", Icons.Filled.Sync, SectionTint.Recent, Route.Scrobbling),
            Control("Inbox", "Notifications", Icons.Filled.Notifications, SectionTint.Favorites, Route.Notifications),
            Control("Stats", "Your report", Icons.Filled.BarChart, SectionTint.Playlists, Route.Stats),
        )
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            controls.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { control ->
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(22.dp))
                                .background(palette.elevatedSurface)
                                .border(1.dp, palette.hairline, RoundedCornerShape(22.dp))
                                .clickable { callbacks.onOpen(control.route) }
                                .padding(14.dp),
                        ) {
                            Box(
                                Modifier.size(38.dp).clip(CircleShape).background(control.tint.copy(alpha = 0.22f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(control.icon, contentDescription = null, tint = control.tint, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.height(10.dp))
                            OneLine(control.label, style = MaterialTheme.typography.titleSmall)
                            control.detail?.let { OneLine(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        SettingsGroup("Appearance") {
            SettingsRow(Icons.Filled.Wallpaper, "Gallery background", state.galleryLabel, onClick = { callbacks.onOpen(Route.GalleryBackground) })
        }

        Spacer(Modifier.height(22.dp))
        SettingsGroup("Your listening") {
            SettingsRow(Icons.Filled.Replay, "Rewind", "This month, this year and all time, as a story", onClick = { callbacks.onOpen(Route.Rewind) })
            Hairline()
            SettingsRow(Icons.Filled.EmojiEvents, "Trophy case", "Badges earned on every device", onClick = { callbacks.onOpen(Route.Achievements) })
        }

        Spacer(Modifier.height(22.dp))
        SettingsGroup("This phone") {
            SettingsRow(Icons.Filled.Refresh, "Rescan device library", state.scanLabel, onClick = callbacks.onRescan)
            Hairline()
            SettingsRow(
                Icons.Filled.Hearing,
                "Mono audio",
                "Sum both channels to one -- useful with a single earbud or mono hearing",
                trailing = { Switch(checked = state.monoDownmixEnabled, onCheckedChange = callbacks.onSetMonoDownmix) },
            )
            Hairline()
            var formatMenuOpen by remember { mutableStateOf(false) }
            Box {
                SettingsRow(
                    Icons.Filled.Dns,
                    "Download format",
                    "What a saved search result is transcoded to (${state.preferredAudioFormat.uppercase()})",
                    onClick = { formatMenuOpen = true },
                )
                DropdownMenu(expanded = formatMenuOpen, onDismissRequest = { formatMenuOpen = false }) {
                    listOf("m4a", "mp3", "flac", "opus", "best").forEach { format ->
                        DropdownMenuItem(
                            text = { Text(format.uppercase()) },
                            onClick = { formatMenuOpen = false; callbacks.onSetAudioFormat(format) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        SettingsGroup("Advanced") {
            SettingsRow(
                Icons.Filled.BugReport,
                "Diagnostics",
                "Logs, HTTP routes & reports",
                onClick = { callbacks.onOpen(Route.Diagnostics) },
            )
            Hairline()
            SettingsRow(
                Icons.Filled.ContentCopy,
                "Duplicate Finder",
                "Files that sound identical in your cloud library",
                onClick = { callbacks.onOpen(Route.DuplicateFinder) },
            )
        }

        if (showConnection) {
            Spacer(Modifier.height(22.dp))
            SettingsGroup("Connection") {
                SettingsRow(
                    icon = Icons.Filled.Dns,
                    title = "Bridge",
                    subtitle = if (state.officialBridge) "Official bridge" else state.bridgeUrl,
                    onClick = { editingBridge = !editingBridge },
                )
                if (editingBridge) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        OutlinedTextField(
                            value = bridgeUrl,
                            onValueChange = { bridgeUrl = it },
                            label = { Text("Bridge URL") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                bridgeUrl = callbacks.onSaveBridge(bridgeUrl)
                                editingBridge = false
                                note = "Saved. Sign out and back in if you changed servers."
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Save bridge URL") }
                        if (!state.officialBridge) {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "A self-hosted bridge never receives the official shared key — " +
                                    "its own key, if it has one, goes here. Without it, search, " +
                                    "streaming, downloads, lyrics and radio will 401.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = selfHostedKey,
                                onValueChange = { selfHostedKey = it },
                                label = { Text(if (state.hasSelfHostedKey) "Replace self-hosted key" else "Self-hosted key") },
                                placeholder = { Text(if (state.hasSelfHostedKey) "A key is set — leave blank to clear it" else "") },
                                singleLine = true,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    callbacks.onSaveSelfHostedKey(selfHostedKey.ifBlank { null })
                                    note = if (selfHostedKey.isBlank()) "Self-hosted key cleared." else "Self-hosted key saved."
                                    selfHostedKey = ""
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Save key") }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    bridgeUrl = callbacks.onResetBridge()
                                    editingBridge = false
                                    note = "Back to the official bridge. Sign out and back in."
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Reset to official bridge") }
                        }
                    }
                }
            }
        }

        note?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.labelSmall, color = palette.accent, modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp), textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(26.dp))
        Text(
            state.version,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
                .clickable(enabled = !showConnection) {
                    connectionTapCount += 1
                    note = when {
                        connectionTapCount >= 7 -> {
                            connectionUnlocked = true
                            "Connection settings unlocked."
                        }
                        connectionTapCount >= 4 -> "${7 - connectionTapCount} more taps to reveal connection settings."
                        else -> null
                    }
                },
            textAlign = TextAlign.Center,
        )
    }
}
