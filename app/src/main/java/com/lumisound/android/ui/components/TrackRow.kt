package com.lumisound.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.lumisound.android.ui.theme.LocalLumiPalette

/**
 * The one track row every list uses.
 *
 * Deliberately one implementation rather than one per screen: the cloud library, the
 * device library, favorites and playlists all show the same thing, and the first build's
 * rows drifted apart immediately because each screen drew its own.
 */
@Composable
fun TrackRow(
    title: String,
    subtitle: String,
    artworkModel: Any?,
    fallbackKey: String,
    modifier: Modifier = Modifier,
    duration: String? = null,
    isPlaying: Boolean = false,
    isFavorite: Boolean = false,
    isDownloaded: Boolean = false,
    isLocked: Boolean = false,
    /** File extension (e.g. "flac"), when known -- drives [TrackFormatBadge]. */
    formatExt: String? = null,
    /** Hz, when known -- drives [TrackFormatBadge]'s hi-res tier. */
    sampleRate: Int? = null,
    onClick: () -> Unit = {},
    onMenu: (() -> Unit)? = null,
) {
    val palette = LocalLumiPalette.current
    val background by animateColorAsState(
        if (isPlaying) palette.accent.copy(alpha = 0.16f) else Color.Transparent,
        label = "rowBackground",
    )

    // The playing row is an inset glass pill rather than a full-bleed band, so it reads as
    // "this one" without cutting the list in half.
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = if (onMenu == null) 12.dp else 2.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Artwork(model = artworkModel, fallbackKey = fallbackKey, size = 52.dp, corner = 14.dp)
            if (isPlaying) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    EqualizerBars(playing = true, color = Color.White, height = 18.dp)
                }
            }
            TrackFormatBadge(
                formatExt = formatExt,
                sampleRate = sampleRate,
                modifier = Modifier.align(Alignment.TopEnd).padding(2.dp),
            )
        }

        Column(Modifier.weight(1f)) {
            OneLine(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isPlaying) palette.accent else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (isFavorite) {
                    Icon(Icons.Filled.Favorite, contentDescription = "Favorite", tint = palette.accent, modifier = Modifier.size(12.dp))
                }
                if (isDownloaded) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Offline",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp),
                    )
                }
                if (isLocked) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = "Lumisound-locked",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp),
                    )
                }
                OneLine(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }

        duration?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        onMenu?.let {
            IconButton(onClick = it) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "More",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val LOSSLESS_EXTS = setOf("flac", "wav", "aiff", "aif", "alac")

/**
 * Small outlined tag calling out lossless/hi-res files -- deliberately silent for the
 * common case (compressed, standard-rate audio) rather than labeling every row, so it only
 * draws attention to the notable ones. Mirrors iOS's `SongFormatBadge` thresholds exactly
 * (lossless container by extension, hi-res by sample rate >44.1kHz) for parity, including
 * its one real quirk: a 48kHz Opus download reads as "HI-RES" here too, same as there --
 * this is iOS's own existing behavior, not something to diverge on during a parity port.
 */
@Composable
fun TrackFormatBadge(formatExt: String?, sampleRate: Int?, modifier: Modifier = Modifier) {
    val ext = formatExt?.lowercase()
    val isLossless = ext != null && ext in LOSSLESS_EXTS
    val isHiRes = sampleRate != null && sampleRate > 44_100
    if (!isLossless && !isHiRes) return

    val palette = LocalLumiPalette.current
    val label = when {
        isLossless && isHiRes -> "HI-RES LOSSLESS"
        isLossless -> ext.uppercase()
        else -> "HI-RES"
    }
    Text(
        label,
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .border(1.dp, palette.accent.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
        color = palette.accent,
        fontSize = 8.sp,
        fontWeight = FontWeight.Bold,
    )
}

/**
 * The card used in horizontal shelves: big rounded art with a soft reflection of its own
 * colour underneath, then two lines.
 */
@Composable
fun ShelfCard(
    title: String,
    subtitle: String,
    artworkModel: Any?,
    fallbackKey: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 148.dp,
) {
    val glow = com.lumisound.android.ui.aura.Aura.forKey(fallbackKey).primary
    Column(
        modifier
            .width(size + 8.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box {
            Box(
                Modifier
                    .matchParentSize()
                    .padding(top = 18.dp)
                    .background(Brush.radialGradient(listOf(glow.copy(alpha = 0.35f), Color.Transparent)))
            )
            Artwork(model = artworkModel, fallbackKey = fallbackKey, size = size, corner = 20.dp)
        }
        Spacer(Modifier.height(9.dp))
        OneLine(title, style = MaterialTheme.typography.titleSmall)
        OneLine(
            subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
