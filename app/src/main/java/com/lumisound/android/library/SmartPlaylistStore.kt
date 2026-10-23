package com.lumisound.android.library

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Local persistence for smart playlists -- iOS parity (`SmartPlaylistStore`).
 * No server sync: like iOS, these are derived/computed data (a rule set),
 * not a fixed list of tracks, so there's nothing to merge beyond what the
 * rules themselves already describe.
 */
class SmartPlaylistStore(context: Context) {
    private val prefs = context.getSharedPreferences("lumimusic_smart_playlists", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _playlists = MutableStateFlow(load())
    val playlists: StateFlow<List<SmartPlaylist>> = _playlists.asStateFlow()

    init {
        seedDefaultsIfNeeded()
    }

    fun upsert(playlist: SmartPlaylist) {
        val next = _playlists.value.toMutableList()
        val index = next.indexOfFirst { it.id == playlist.id }
        if (index >= 0) next[index] = playlist else next.add(playlist)
        save(next)
    }

    fun remove(id: String) {
        save(_playlists.value.filterNot { it.id == id })
    }

    private fun save(next: List<SmartPlaylist>) {
        _playlists.value = next
        prefs.edit().putString(KEY_PLAYLISTS, gson.toJson(next)).apply()
    }

    private fun load(): List<SmartPlaylist> {
        val json = prefs.getString(KEY_PLAYLISTS, null) ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<SmartPlaylist>>() {}.type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Four starter smart playlists the first time this ever runs on a
     * device, built from ordinary rules so they're editable/deletable like
     * anything else -- iOS parity. Gated on a flag rather than
     * "playlists.isEmpty" so deleting all of them later doesn't bring them back.
     */
    private fun seedDefaultsIfNeeded() {
        if (prefs.getBoolean(KEY_SEEDED, false)) return
        prefs.edit().putBoolean(KEY_SEEDED, true).apply()
        if (_playlists.value.isNotEmpty()) return
        save(
            listOf(
                SmartPlaylist(
                    name = "Recently Added",
                    rules = listOf(SmartRule(field = SmartField.DAYS_SINCE_ADDED, op = SmartOp.LESS_THAN, number = 30.0)),
                    sort = SmartSort.RECENTLY_ADDED_DESC,
                ),
                SmartPlaylist(
                    name = "Most Played",
                    rules = listOf(SmartRule(field = SmartField.PLAY_COUNT, op = SmartOp.GREATER_THAN, number = 0.0)),
                    limit = 100,
                    sort = SmartSort.PLAY_COUNT_DESC,
                ),
                SmartPlaylist(
                    name = "Forgotten Favorites",
                    rules = listOf(
                        SmartRule(field = SmartField.FAVORITE, op = SmartOp.IS_TRUE),
                        SmartRule(field = SmartField.DAYS_SINCE_PLAYED, op = SmartOp.GREATER_THAN, number = 60.0),
                    ),
                    sort = SmartSort.RECENTLY_PLAYED_DESC,
                ),
                SmartPlaylist(
                    name = "Quick Listens",
                    rules = listOf(SmartRule(field = SmartField.DURATION, op = SmartOp.LESS_THAN, number = 180.0)),
                    sort = SmartSort.DURATION_ASC,
                ),
            ),
        )
    }

    private companion object {
        const val KEY_PLAYLISTS = "playlists_v1"
        const val KEY_SEEDED = "seeded_defaults_v1"
    }
}
