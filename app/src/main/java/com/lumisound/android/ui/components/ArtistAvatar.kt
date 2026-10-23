package com.lumisound.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lumisound.android.bridge.api.ArtistApi
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * An artist's real profile picture (`GET /api/artist/image`, resolved server-
 * side from Deezer/Wikipedia/YouTube), falling back to a monogram on a
 * stable name-derived gradient -- iOS parity (`ArtistAvatar.swift`).
 * Deliberately does not fall back to a track's album art: showing whichever
 * song's cover happens to come first for an artist isn't the artist's own
 * picture.
 *
 * Resolved URLs are cached in memory for the process lifetime (same
 * reasoning as the bridge's own indefinite cache -- an artist's photo
 * doesn't change often enough to re-ask every time a row scrolls into view).
 */
@Composable
fun ArtistAvatar(artist: String, size: Dp, api: ArtistApi, modifier: Modifier = Modifier) {
    var imageUrl by remember(artist) { mutableStateOf(ArtistImageCache.cached(artist)) }
    LaunchedEffect(artist) {
        if (imageUrl == null && !ArtistImageCache.isKnownMissing(artist)) {
            imageUrl = ArtistImageCache.resolve(api, artist)
        }
    }

    if (imageUrl != null) {
        AsyncImage(
            model = imageUrl,
            contentDescription = artist,
            modifier = modifier.size(size).clip(CircleShape),
        )
    } else {
        val (monogram, colors) = remember(artist) { monogramAndColors(artist) }
        Box(
            modifier.size(size).clip(CircleShape).background(Brush.linearGradient(colors)),
            contentAlignment = Alignment.Center,
        ) {
            Text(monogram, color = Color.White, fontSize = (size.value / 2.4f).sp, style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun monogramAndColors(artist: String): Pair<String, List<Color>> {
    val trimmed = artist.trim()
    val monogram = trimmed.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "♪"
    var hash = 5381
    for (b in trimmed.toByteArray()) hash = ((hash shl 5) + hash) + b
    val hue = (abs(hash) % 360).toFloat()
    return monogram to listOf(
        Color.hsv(hue, 0.55f, 0.78f),
        Color.hsv((hue + 29f) % 360f, 0.68f, 0.52f),
    )
}

private object ArtistImageCache {
    private val cache = ConcurrentHashMap<String, String?>()

    fun cached(artist: String): String? = cache[normalize(artist)]
    fun isKnownMissing(artist: String): Boolean = cache.containsKey(normalize(artist)) && cache[normalize(artist)] == null

    suspend fun resolve(api: ArtistApi, artist: String): String? {
        val key = normalize(artist)
        cache[key]?.let { return it }
        if (cache.containsKey(key)) return null
        val url = try {
            api.image(artist).takeIf { it.found }?.imageUrl
        } catch (e: Exception) {
            null
        }
        cache[key] = url
        return url
    }

    private fun normalize(artist: String) = artist.trim().lowercase()
}
