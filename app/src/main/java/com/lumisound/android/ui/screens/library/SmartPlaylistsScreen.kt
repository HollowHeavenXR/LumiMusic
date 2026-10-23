package com.lumisound.android.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisound.android.AppContainer
import com.lumisound.android.bridge.BridgeUrls
import com.lumisound.android.data.db.CloudTrackEntity
import com.lumisound.android.library.SmartField
import com.lumisound.android.library.SmartFieldKind
import com.lumisound.android.library.SmartMatch
import com.lumisound.android.library.SmartOp
import com.lumisound.android.library.SmartPlaylist
import com.lumisound.android.library.SmartRule
import com.lumisound.android.library.SmartSort
import com.lumisound.android.library.buildPlayStats
import com.lumisound.android.library.evaluate
import com.lumisound.android.playback.toPlayable
import com.lumisound.android.ui.components.EmptyState
import com.lumisound.android.ui.components.OneLine
import com.lumisound.android.ui.components.ScreenTitle
import com.lumisound.android.ui.components.TrackRow
import com.lumisound.android.ui.components.trackSubtitle
import kotlinx.coroutines.launch

@Composable
fun SmartPlaylistsScreen(container: AppContainer, onBack: (() -> Unit)? = null) {
    val scope = rememberCoroutineScope()
    val playlists by container.smartPlaylists.playlists.collectAsStateWithLifecycle()
    val cloudTracks by container.database.cloudTracks().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val favorites by container.database.favorites().observeIds().collectAsStateWithLifecycle(initialValue = emptyList())
    val history by container.database.history().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val playback by container.player.state.collectAsStateWithLifecycle()
    val favoriteSet = remember(favorites) { favorites.toSet() }
    val stats = remember(history) { buildPlayStats(history) }

    var viewing by remember { mutableStateOf<SmartPlaylist?>(null) }
    var editing by remember { mutableStateOf<SmartPlaylist?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }

    val open = viewing
    if (open != null) {
        val evaluated = remember(open, cloudTracks, favoriteSet, stats) { open.evaluate(cloudTracks, favoriteSet, stats) }
        Column(Modifier.fillMaxSize()) {
            ScreenTitle(
                open.name,
                subtitle = "${evaluated.size} track${if (evaluated.size == 1) "" else "s"}",
                eyebrow = "Smart Playlist",
                onBack = { viewing = null },
                trailing = { IconButton(onClick = { editing = open }) { Icon(Icons.Filled.Edit, contentDescription = "Edit") } },
            )
            if (evaluated.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.AutoAwesome,
                    title = "No matches",
                    message = "Nothing in your cloud library matches this playlist's rules right now.",
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(evaluated, key = { it.serverPath }) { track ->
                        TrackRow(
                            title = track.title,
                            subtitle = trackSubtitle(track.title, track.artist, track.album),
                            artworkModel = BridgeUrls.cloudArtwork(container.config.baseUrl, track.serverPath),
                            fallbackKey = track.serverPath,
                            isPlaying = playback.serverPath == track.serverPath,
                            formatExt = track.ext,
                            sampleRate = track.sampleRate,
                            onClick = {
                                scope.launch {
                                    container.player.setShuffle(false)
                                    container.player.play(
                                        evaluated.map { it.toPlayable(container.config.baseUrl, container.downloads.isDownloaded(it.serverPath)) },
                                        evaluated.indexOf(track),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    } else {
        Scaffold(
            floatingActionButton = {
                FloatingActionButton(onClick = { editing = SmartPlaylist() }) { Icon(Icons.Filled.Add, contentDescription = "New smart playlist") }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                ScreenTitle("Smart Playlists", subtitle = "Rules-based, always up to date", eyebrow = "Library", onBack = onBack)
                if (playlists.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.AutoAwesome,
                        title = "No smart playlists",
                        message = "Build a playlist from rules -- BPM, favorites, how recently you played something -- and it stays current on its own.",
                    )
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(playlists, key = { it.id }) { playlist ->
                            Row(
                                Modifier.fillMaxWidth().clickable { viewing = playlist }.padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    OneLine(playlist.name, style = MaterialTheme.typography.bodyLarge)
                                    OneLine(
                                        "${playlist.rules.size} rule${if (playlist.rules.size == 1) "" else "s"} · ${playlist.sort.label}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Box {
                                    IconButton(onClick = { menuFor = playlist.id }) {
                                        Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    DropdownMenu(expanded = menuFor == playlist.id, onDismissRequest = { menuFor = null }) {
                                        DropdownMenuItem(text = { Text("Edit") }, onClick = { menuFor = null; editing = playlist })
                                        DropdownMenuItem(
                                            text = { Text("Delete") },
                                            onClick = { menuFor = null; container.smartPlaylists.remove(playlist.id) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { playlist ->
        SmartPlaylistEditorDialog(
            playlist = playlist,
            onSave = { saved -> container.smartPlaylists.upsert(saved); editing = null; viewing = saved },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun SmartPlaylistEditorDialog(
    playlist: SmartPlaylist,
    onSave: (SmartPlaylist) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(playlist.name) }
    var match by remember { mutableStateOf(playlist.match) }
    var sort by remember { mutableStateOf(playlist.sort) }
    var limitText by remember { mutableStateOf(if (playlist.limit > 0) playlist.limit.toString() else "") }
    var rules by remember { mutableStateOf(playlist.rules.ifEmpty { listOf(SmartRule()) }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (playlist.rules.isEmpty() && playlist.name == "New Smart Playlist") "New Smart Playlist" else "Edit Smart Playlist") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmartMatch.entries.forEach { option ->
                        FilterChip(selected = match == option, onClick = { match = option }, label = { Text(option.label) })
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Rules", style = MaterialTheme.typography.labelLarge)
                rules.forEachIndexed { index, rule ->
                    Spacer(Modifier.height(8.dp))
                    SmartRuleRow(
                        rule = rule,
                        onChange = { updated -> rules = rules.toMutableList().also { it[index] = updated } },
                        onRemove = { rules = rules.filterIndexed { i, _ -> i != index } }.takeIf { rules.size > 1 },
                    )
                }
                TextButton(onClick = { rules = rules + SmartRule() }) { Text("+ Add rule") }
                Spacer(Modifier.height(10.dp))
                var sortMenuOpen by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { sortMenuOpen = true }) { Text("Sort: ${sort.label}") }
                    DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                        SmartSort.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(option.label) }, onClick = { sort = option; sortMenuOpen = false })
                        }
                    }
                }
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it.filter { c -> c.isDigit() } },
                    label = { Text("Limit (blank = unlimited)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    playlist.copy(
                        name = name.trim().ifBlank { "Smart Playlist" },
                        match = match,
                        rules = rules,
                        sort = sort,
                        limit = limitText.toIntOrNull() ?: 0,
                    ),
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SmartRuleRow(rule: SmartRule, onChange: (SmartRule) -> Unit, onRemove: (() -> Unit)?) {
    var fieldMenuOpen by remember { mutableStateOf(false) }
    var opMenuOpen by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box {
            TextButton(onClick = { fieldMenuOpen = true }) { Text(rule.field.label) }
            DropdownMenu(expanded = fieldMenuOpen, onDismissRequest = { fieldMenuOpen = false }) {
                SmartField.entries.forEach { field ->
                    DropdownMenuItem(
                        text = { Text(field.label) },
                        onClick = {
                            fieldMenuOpen = false
                            val defaultOp = SmartOp.available(field.kind).first()
                            onChange(rule.copy(field = field, op = defaultOp))
                        },
                    )
                }
            }
        }
        Box {
            TextButton(onClick = { opMenuOpen = true }) { Text(rule.op.label) }
            DropdownMenu(expanded = opMenuOpen, onDismissRequest = { opMenuOpen = false }) {
                SmartOp.available(rule.field.kind).forEach { op ->
                    DropdownMenuItem(text = { Text(op.label) }, onClick = { onChange(rule.copy(op = op)); opMenuOpen = false })
                }
            }
        }
        when (rule.field.kind) {
            SmartFieldKind.TEXT -> OutlinedTextField(
                value = rule.text,
                onValueChange = { onChange(rule.copy(text = it)) },
                singleLine = true,
                modifier = Modifier.width(100.dp),
            )
            SmartFieldKind.NUMBER -> {
                OutlinedTextField(
                    value = if (rule.number == 0.0) "" else rule.number.toString(),
                    onValueChange = { onChange(rule.copy(number = it.toDoubleOrNull() ?: 0.0)) },
                    singleLine = true,
                    modifier = Modifier.width(70.dp),
                )
                if (rule.op == SmartOp.BETWEEN) {
                    Text("–")
                    OutlinedTextField(
                        value = if (rule.number2 == 0.0) "" else rule.number2.toString(),
                        onValueChange = { onChange(rule.copy(number2 = it.toDoubleOrNull() ?: 0.0)) },
                        singleLine = true,
                        modifier = Modifier.width(70.dp),
                    )
                }
            }
            SmartFieldKind.BOOLEAN -> {}
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, contentDescription = "Remove rule") }
        }
    }
}
