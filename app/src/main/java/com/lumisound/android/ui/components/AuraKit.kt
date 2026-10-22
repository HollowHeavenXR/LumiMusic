package com.lumisound.android.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.lumisound.android.ui.aura.AuraBase
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lumisound.android.ui.theme.EyebrowStyle
import com.lumisound.android.ui.theme.LocalLumiPalette
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The small spaced capitals above a heading. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    Text(
        text.uppercase(),
        style = EyebrowStyle,
        color = color ?: MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * A row of mutually exclusive options in one glass capsule, the selected one lifted onto
 * the accent. Used wherever a screen switches between views of the same thing.
 */
@Composable
fun <T> SegmentedPill(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalLumiPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(CircleShape)
            .background(palette.elevatedSurface)
            .border(1.dp, palette.hairline, CircleShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .then(if (isSelected) Modifier.background(palette.accentBrush) else Modifier)
                    .clickable { onSelect(option) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * A circular progress arc with a thumb. With [onSeek] it is a scrubber: drag anywhere along
 * the ring and the position follows the angle, twelve o'clock being the start.
 */
@Composable
fun RingProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    stroke: Dp = 4.dp,
    color: Color = LocalLumiPalette.current.accent,
    track: Color = Color.White.copy(alpha = 0.10f),
    showThumb: Boolean = false,
    onSeek: ((Float) -> Unit)? = null,
) {
    Canvas(
        modifier.then(
            if (onSeek == null) Modifier else Modifier.pointerInput(onSeek) {
                fun angleFraction(offset: Offset): Float {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    // atan2 from twelve o'clock, clockwise.
                    val angle = atan2(offset.x - cx, cy - offset.y)
                    val normalised = if (angle < 0) angle + 2 * PI.toFloat() else angle
                    return (normalised / (2 * PI.toFloat())).coerceIn(0f, 1f)
                }
                detectDragGestures(
                    onDragStart = { onSeek(angleFraction(it)) },
                    onDrag = { change, _ -> onSeek(angleFraction(change.position)) },
                )
            }
        )
    ) {
        val strokePx = stroke.toPx()
        val inset = strokePx / 2 + if (showThumb) strokePx else 0f
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(strokePx))
        val sweep = 360f * progress.coerceIn(0f, 1f)
        drawArc(color, -90f, sweep, false, Offset(inset, inset), arcSize, style = Stroke(strokePx, cap = StrokeCap.Round))
        if (showThumb) {
            val radius = arcSize.width / 2
            val theta = Math.toRadians((sweep - 90f).toDouble())
            val center = Offset(size.width / 2 + radius * cos(theta).toFloat(), size.height / 2 + radius * sin(theta).toFloat())
            drawCircle(Color.White, strokePx * 1.6f, center)
            drawCircle(color, strokePx * 0.9f, center)
        }
    }
}

/**
 * The now-playing record: a black disc with pressed grooves, the cover (or its generated
 * art) as the centre label, turning while music plays and stopping where it is on pause --
 * it does not snap back, because a record would not.
 */
@Composable
fun VinylDisc(
    artworkModel: Any?,
    fallbackKey: String,
    size: Dp,
    spinning: Boolean,
    modifier: Modifier = Modifier,
    labelFraction: Float = 0.46f,
) {
    var angle by remember { mutableFloatStateOf(0f) }
    val motion = com.lumisound.android.ui.aura.LocalMotion.current
    LaunchedEffect(spinning, motion) {
        if (!spinning || !motion) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            // 33⅓ rpm is one turn every 1.8 seconds; a third of that reads calmer on screen.
            angle = (angle + (now - last) * 360f / 5_400f) % 360f
            last = now
        }
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().rotate(angle)) {
            val r = min(this.size.width, this.size.height) / 2
            drawCircle(Brush.radialGradient(listOf(Color(0xFF1C1C22), Color(0xFF050507)), radius = r), r)
            // Grooves: fine concentric rings, a few catching more light than the rest.
            var ring = r * 0.96f
            var i = 0
            while (ring > r * (labelFraction + 0.04f)) {
                drawCircle(
                    Color.White.copy(alpha = if (i % 7 == 0) 0.07f else 0.025f),
                    ring,
                    style = Stroke(1.2f),
                )
                ring -= r * 0.022f
                i++
            }
            // The sheen: a light wedge that turns with the disc.
            drawArc(
                Brush.sweepGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent, Color.Transparent, Color.White.copy(alpha = 0.06f), Color.Transparent),
                ),
                0f, 360f, true,
            )
        }
        Box(Modifier.size(size * labelFraction).rotate(angle).clip(CircleShape)) {
            Artwork(model = artworkModel, fallbackKey = fallbackKey, size = size * labelFraction, corner = size)
        }
        // Spindle hole.
        Box(Modifier.size(size * 0.045f).clip(CircleShape).background(AuraBase))
    }
}

/**
 * Three bars that dance while [playing] -- the "this is live" signal on a playing row, a
 * friend listening right now, and the dock.
 */
@Composable
fun EqualizerBars(playing: Boolean, color: Color, modifier: Modifier = Modifier, barWidth: Dp = 3.dp, height: Dp = 16.dp) {
    val resting = listOf(0.45f, 0.8f, 0.6f)
    val fractions: List<Float> = if (playing && com.lumisound.android.ui.aura.LocalMotion.current) {
        val transition = rememberInfiniteTransition(label = "eqBars")
        listOf(0, 180, 360).map { delay ->
            transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(520, delayMillis = delay, easing = LinearEasing), RepeatMode.Reverse),
                label = "eqBar$delay",
            ).value
        }
    } else {
        resting
    }
    Row(modifier.height(height), horizontalArrangement = Arrangement.spacedBy(barWidth * 0.8f), verticalAlignment = Alignment.Bottom) {
        fractions.forEachIndexed { index, fraction ->
            Box(
                Modifier
                    .width(barWidth)
                    .height(height * fraction)
                    .clip(RoundedCornerShape(barWidth))
                    .background(color)
            )
        }
    }
}

/** The primary action: a gradient capsule with an icon and a word. */
@Composable
fun GlowButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalLumiPalette.current
    Row(
        modifier
            .clip(CircleShape)
            .background(palette.accentBrush)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

/** The secondary action: the same capsule in glass. */
@Composable
fun GlassButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalLumiPalette.current
    Row(
        modifier
            .clip(CircleShape)
            .background(palette.elevatedSurface)
            .border(1.dp, palette.hairline, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** A round gradient play button, sized for wherever it sits. */
@Composable
fun PlayOrb(onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 48.dp, icon: ImageVector = Icons.Filled.PlayArrow) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(LocalLumiPalette.current.accentBrush)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(size * 0.5f))
    }
}

/**
 * A large tile with its own generated colour field: the Library's crates, the mood grid in
 * Search. The colour comes from [key], so a tile keeps its colour from one launch to the next.
 */
@Composable
fun BigTile(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    key: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 116.dp,
) {
    val palette = fallbackPaletteFor(key)
    val base = Color.hsl(palette.hue, 0.62f, 0.42f)
    val light = Color.hsl((palette.hue + 28f) % 360f, 0.70f, 0.58f)
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(light, base, base.copy(alpha = 0.85f).compositeOnDark())))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.22f),
            modifier = Modifier.size(78.dp).align(Alignment.BottomEnd).padding(start = 8.dp),
        )
        Column(Modifier.align(Alignment.TopStart)) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun Color.compositeOnDark(): Color = Color(
    red = red * alpha + 0.03f * (1 - alpha),
    green = green * alpha + 0.03f * (1 - alpha),
    blue = blue * alpha + 0.06f * (1 - alpha),
    alpha = 1f,
)

/** Page dots for a pager. */
@Composable
fun PagerDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val palette = LocalLumiPalette.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            Box(
                Modifier
                    .height(6.dp)
                    .width(if (index == current) 20.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (index == current) palette.accent else Color.White.copy(alpha = 0.25f))
            )
        }
    }
}

/** Taps on the left third go back, anywhere else forward -- the stories gesture. */
fun Modifier.storyTaps(onBack: () -> Unit, onForward: () -> Unit): Modifier = pointerInput(onBack, onForward) {
    detectTapGestures { offset -> if (offset.x < size.width / 3f) onBack() else onForward() }
}

/** A full-width glass panel with a heading, for grouping a block of content on a page. */
@Composable
fun GlassPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    LumiCard(modifier.fillMaxWidth().padding(horizontal = 16.dp), corner = 26.dp) {
        Column(Modifier.padding(18.dp)) { content() }
    }
}
