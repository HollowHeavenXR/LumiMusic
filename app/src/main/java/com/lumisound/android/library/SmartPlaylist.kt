package com.lumisound.android.library

import com.lumisound.android.data.db.CloudTrackEntity
import com.lumisound.android.data.db.PlayHistoryEntity
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * A dynamic, rules-based playlist -- iOS parity (`SmartPlaylist.swift`).
 * Unlike a normal playlist (a fixed list of track paths), this stores rules
 * evaluated live against the cloud library every time it's opened, so
 * "Favorites added recently", "120-135 BPM workout", etc. stay up to date on
 * their own. Local-only (SharedPreferences); no server dependency, same
 * choice iOS made.
 *
 * Evaluated over the cloud library only, not the device-local one: the two
 * have different schemas (no shared identity, no `year` field on either,
 * `source`/platform-of-origin isn't tracked for a cloud track the way iOS's
 * `Song.sourceTrackID` tracks it) -- the cloud library is the one users
 * actually curate and share across devices, so it's the one worth building
 * this against rather than guessing at a cross-schema union.
 *
 * Two of iOS's eleven fields have no equivalent here and are intentionally
 * absent rather than mapped to something misleading: `year` (not modeled on
 * `CloudTrackEntity` at all) and `source` (cloud tracks don't distinguish
 * platform of origin the way `Song.sourceTrackID` does). iOS's optional Lua
 * predicate (`luaScript`) also has no port -- a rarely-used escape hatch,
 * and embedding a Lua interpreter for it isn't worth the new dependency.
 */
enum class SmartField(val label: String, val kind: SmartFieldKind) {
    TITLE("Title", SmartFieldKind.TEXT),
    ARTIST("Artist", SmartFieldKind.TEXT),
    ALBUM("Album", SmartFieldKind.TEXT),
    GENRE("Genre", SmartFieldKind.TEXT),
    DURATION("Duration (sec)", SmartFieldKind.NUMBER),
    BPM("BPM", SmartFieldKind.NUMBER),
    FAVORITE("Favorite", SmartFieldKind.BOOLEAN),
    PLAY_COUNT("Play Count", SmartFieldKind.NUMBER),
    DAYS_SINCE_PLAYED("Days Since Played", SmartFieldKind.NUMBER),
    DAYS_SINCE_ADDED("Days Since Added", SmartFieldKind.NUMBER),
}

enum class SmartFieldKind { TEXT, NUMBER, BOOLEAN }

enum class SmartOp(val label: String) {
    CONTAINS("contains"),
    NOT_CONTAINS("does not contain"),
    EQUALS("is"),
    GREATER_THAN("greater than"),
    LESS_THAN("less than"),
    BETWEEN("between"),
    IS_TRUE("is yes"),
    IS_FALSE("is no");

    companion object {
        fun available(kind: SmartFieldKind): List<SmartOp> = when (kind) {
            SmartFieldKind.TEXT -> listOf(CONTAINS, NOT_CONTAINS, EQUALS)
            SmartFieldKind.NUMBER -> listOf(EQUALS, GREATER_THAN, LESS_THAN, BETWEEN)
            SmartFieldKind.BOOLEAN -> listOf(IS_TRUE, IS_FALSE)
        }
    }
}

data class SmartRule(
    val id: String = UUID.randomUUID().toString(),
    val field: SmartField = SmartField.TITLE,
    val op: SmartOp = SmartOp.CONTAINS,
    val text: String = "",
    val number: Double = 0.0,
    /** Upper bound for [SmartOp.BETWEEN]. */
    val number2: Double = 0.0,
)

enum class SmartMatch(val label: String) {
    ALL("Match ALL rules"),
    ANY("Match ANY rule"),
}

enum class SmartSort(val label: String) {
    TITLE_ASC("Title (A-Z)"),
    ARTIST_ASC("Artist (A-Z)"),
    BPM_ASC("BPM (low to high)"),
    DURATION_ASC("Duration (short to long)"),
    RANDOM("Random"),
    PLAY_COUNT_DESC("Most Played"),
    RECENTLY_PLAYED_DESC("Recently Played"),
    RECENTLY_ADDED_DESC("Recently Added"),
}

data class SmartPlaylist(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "New Smart Playlist",
    val match: SmartMatch = SmartMatch.ALL,
    val rules: List<SmartRule> = emptyList(),
    /** 0 = unlimited. */
    val limit: Int = 0,
    val sort: SmartSort = SmartSort.TITLE_ASC,
)

/** Per-track play stats, pre-aggregated once per evaluation rather than per-rule. */
data class PlayStats(val playCount: Int, val daysSincePlayed: Double?)

/**
 * Evaluates [playlist] against [tracks], using [favoritePaths] and
 * [statsByTrack] (keyed the same way the caller built it -- see
 * `SmartPlaylistsScreen`'s `(title, artist)` lowercase-pair key, matching
 * the bridge's own mood-playlist grouping convention for the identical
 * "no shared id between play history and a track" problem).
 */
fun SmartPlaylist.evaluate(
    tracks: List<CloudTrackEntity>,
    favoritePaths: Set<String>,
    statsByTrack: Map<Pair<String, String>, PlayStats>,
): List<CloudTrackEntity> {
    val filtered = tracks.filter { matches(it, favoritePaths, statsByTrack) }
    val sorted = sort(filtered, statsByTrack)
    return if (limit > 0) sorted.take(limit) else sorted
}

private fun SmartPlaylist.matches(
    track: CloudTrackEntity,
    favoritePaths: Set<String>,
    statsByTrack: Map<Pair<String, String>, PlayStats>,
): Boolean {
    if (rules.isEmpty()) return true
    val results = rules.map { evaluateRule(it, track, favoritePaths, statsByTrack) }
    return if (match == SmartMatch.ALL) results.all { it } else results.any { it }
}

private fun evaluateRule(
    rule: SmartRule,
    track: CloudTrackEntity,
    favoritePaths: Set<String>,
    statsByTrack: Map<Pair<String, String>, PlayStats>,
): Boolean = when (rule.field.kind) {
    SmartFieldKind.TEXT -> {
        val value = textValue(rule.field, track)
        when (rule.op) {
            SmartOp.CONTAINS -> value.contains(rule.text, ignoreCase = true)
            SmartOp.NOT_CONTAINS -> !value.contains(rule.text, ignoreCase = true)
            SmartOp.EQUALS -> value.equals(rule.text, ignoreCase = true)
            else -> false
        }
    }
    SmartFieldKind.NUMBER -> {
        val value = numberValue(rule.field, track, statsByTrack)
        if (value == null) false else when (rule.op) {
            SmartOp.EQUALS -> kotlin.math.abs(value - rule.number) < 0.5
            SmartOp.GREATER_THAN -> value > rule.number
            SmartOp.LESS_THAN -> value < rule.number
            SmartOp.BETWEEN -> value >= minOf(rule.number, rule.number2) && value <= maxOf(rule.number, rule.number2)
            else -> false
        }
    }
    SmartFieldKind.BOOLEAN -> {
        val isFavorite = track.serverPath in favoritePaths
        if (rule.op == SmartOp.IS_TRUE) isFavorite else !isFavorite
    }
}

private fun textValue(field: SmartField, track: CloudTrackEntity): String = when (field) {
    SmartField.TITLE -> track.title
    SmartField.ARTIST -> track.artist
    SmartField.ALBUM -> track.album
    SmartField.GENRE -> track.genre
    else -> ""
}

private fun numberValue(
    field: SmartField,
    track: CloudTrackEntity,
    statsByTrack: Map<Pair<String, String>, PlayStats>,
): Double? = when (field) {
    SmartField.DURATION -> track.durationSeconds.takeIf { it > 0 }
    SmartField.BPM -> track.bpm
    SmartField.PLAY_COUNT -> (statsByTrack[statsKey(track)]?.playCount ?: 0).toDouble()
    SmartField.DAYS_SINCE_PLAYED -> statsByTrack[statsKey(track)]?.daysSincePlayed ?: Double.MAX_VALUE
    SmartField.DAYS_SINCE_ADDED -> daysSinceAdded(track.uploadedAt)
    else -> null
}

/** Same (lowercased title, artist) grouping key the bridge's own mood-playlist endpoint uses for the identical problem: play history has no track id to join on. */
fun statsKey(track: CloudTrackEntity): Pair<String, String> =
    track.title.trim().lowercase() to track.artist.trim().lowercase()

/** Groups [history] by (title, artist) once per evaluation rather than scanning it per rule/per track. */
fun buildPlayStats(history: List<PlayHistoryEntity>): Map<Pair<String, String>, PlayStats> =
    history.groupBy { (it.title ?: "").trim().lowercase() to (it.artist ?: "").trim().lowercase() }
        .mapValues { (_, entries) ->
            val mostRecent = entries.mapNotNull { it.playedAt?.let(::parseServerInstant) }.maxOrNull()
            val daysSince = mostRecent?.let { (Instant.now().epochSecond - it.epochSecond) / 86_400.0 }
            PlayStats(playCount = entries.size, daysSincePlayed = daysSince)
        }

/**
 * The bridge's own `.isoformat()` timestamps have no trailing `Z`/offset for
 * a naive (always-UTC, in practice) datetime -- `Instant.parse` alone
 * rejects those outright, so this falls back to parsing as a bare
 * [LocalDateTime] and assuming UTC when the strict parse fails.
 */
fun parseServerInstant(raw: String): Instant? =
    runCatching { Instant.parse(raw) }
        .recoverCatching { LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC) }
        .getOrNull()

private fun daysSinceAdded(uploadedAt: String?): Double? {
    val instant = uploadedAt?.let(::parseServerInstant) ?: return null
    return (Instant.now().epochSecond - instant.epochSecond) / 86_400.0
}

private fun SmartPlaylist.sort(tracks: List<CloudTrackEntity>, statsByTrack: Map<Pair<String, String>, PlayStats>): List<CloudTrackEntity> =
    when (sort) {
        SmartSort.TITLE_ASC -> tracks.sortedBy { it.title.lowercase() }
        SmartSort.ARTIST_ASC -> tracks.sortedBy { it.artist.lowercase() }
        SmartSort.BPM_ASC -> tracks.sortedBy { it.bpm ?: Double.MAX_VALUE }
        SmartSort.DURATION_ASC -> tracks.sortedBy { it.durationSeconds }
        SmartSort.RANDOM -> tracks.shuffled()
        SmartSort.PLAY_COUNT_DESC -> tracks.sortedByDescending { statsByTrack[statsKey(it)]?.playCount ?: 0 }
        SmartSort.RECENTLY_PLAYED_DESC -> tracks.sortedBy { statsByTrack[statsKey(it)]?.daysSincePlayed ?: Double.MAX_VALUE }
        SmartSort.RECENTLY_ADDED_DESC -> tracks.sortedByDescending { daysSinceAdded(it.uploadedAt) ?: -1.0 }
    }
