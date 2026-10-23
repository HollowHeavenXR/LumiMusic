package com.lumisound.android.ui.screens.search

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisound.android.AppContainer
import com.lumisound.android.bridge.BridgeUrls
import com.lumisound.android.bridge.model.StreamTrackDto
import com.lumisound.android.diagnostics.AppLogger
import com.lumisound.android.playback.toPlayable
import com.lumisound.android.ui.components.Loadable
import com.lumisound.android.ui.components.StreamTrackActions
import com.lumisound.android.ui.components.friendlyError
import com.lumisound.android.ui.SaveStreamResult
import com.lumisound.android.ui.enqueueStream
import com.lumisound.android.ui.playStreamNext
import com.lumisound.android.ui.playStreams
import com.lumisound.android.ui.saveStreamToLibrary
import com.lumisound.android.ui.startRadio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf(SearchSource.YouTube) }
    var submitted by rememberSaveable { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<Loadable<List<StreamTrackDto>>>(Loadable.Ready(emptyList())) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var trending by remember { mutableStateOf<List<String>>(emptyList()) }
    var searchGeneration by remember { mutableIntStateOf(0) }
    val recent by container.searchHistory.recent.collectAsStateWithLifecycle()
    val playback by container.player.state.collectAsStateWithLifecycle()

    val library by remember(query, source) {
        if (source == SearchSource.Library && query.isNotBlank()) container.database.cloudTracks().search(query)
        else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    // What everyone has been searching, for the empty state. Failure just leaves it out.
    LaunchedEffect(Unit) {
        trending = try {
            container.http.streaming.trendingQueries().map { it.query }.filter { it.isNotBlank() }.distinct()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Autocomplete from the bridge's query log, debounced so typing is not a request a key.
    LaunchedEffect(query, source) {
        suggestions = emptyList()
        val prefix = query.trim()
        if (source == SearchSource.Library || prefix.length < 2 || prefix == submitted) return@LaunchedEffect
        delay(300)
        suggestions = try {
            container.http.streaming.suggestions(prefix).map { it.query }.filterNot { it.equals(prefix, ignoreCase = true) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    // The search itself: re-run whenever the submitted query, the source, or a retry changes.
    LaunchedEffect(submitted, source, searchGeneration) {
        val q = submitted ?: return@LaunchedEffect
        val bridgeSource = source.bridgeSource ?: return@LaunchedEffect
        results = Loadable.Loading
        results = try {
            Loadable.Ready(container.http.streaming.search(q, bridgeSource))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w("search", "search failed: ${e.javaClass.simpleName}", mapOf("source" to bridgeSource))
            Loadable.Failed(friendlyError(e))
        }
    }

    fun submit(text: String) {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return
        query = cleaned
        submitted = cleaned
        suggestions = emptyList()
        container.searchHistory.record(cleaned)
    }

    SearchContent(
        state = SearchUiState(
            query = query,
            source = source,
            submitted = submitted,
            streamResults = results,
            libraryResults = library,
            suggestions = suggestions,
            recent = recent,
            trending = trending,
            playingId = playback.mediaId,
        ),
        callbacks = SearchCallbacks(
            onQueryChange = { query = it },
            onSubmit = ::submit,
            onSourceChange = { source = it },
            onPlayStream = { tracks, index -> container.playStreams(tracks, index) },
            onShuffleStreams = { tracks -> container.playStreams(tracks, tracks.indices.randomOrNull() ?: 0, shuffle = true) },
            streamActions = StreamTrackActions(
                onPlayNext = { container.playStreamNext(it) },
                onEnqueue = { container.enqueueStream(it) },
                onRadio = { seed ->
                    scope.launch {
                        Toast.makeText(context, "Building a radio from ${seed.title}…", Toast.LENGTH_SHORT).show()
                        if (!container.startRadio(seed)) {
                            Toast.makeText(context, "Couldn't build a radio for that track.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onSave = { track ->
                    scope.launch {
                        Toast.makeText(context, "Saving \"${track.title}\"…", Toast.LENGTH_SHORT).show()
                        val message = when (val result = container.saveStreamToLibrary(track)) {
                            is SaveStreamResult.Saved -> "Saved to your library."
                            is SaveStreamResult.AlreadyOwned -> "Already in your library."
                            is SaveStreamResult.Failed -> result.message
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                },
            ),
            onPlayLibrary = { tracks, index ->
                scope.launch {
                    container.player.setShuffle(false)
                    container.player.play(
                        tracks.map { it.toPlayable(container.config.baseUrl, container.downloads.isDownloaded(it.serverPath)) },
                        index,
                    )
                }
            },
            libraryArtwork = { track ->
                BridgeUrls.cloudArtwork(container.config.baseUrl, track.serverPath)
            },
            onClearRecent = container.searchHistory::clear,
            onRetry = { searchGeneration++ },
        ),
    )
}
