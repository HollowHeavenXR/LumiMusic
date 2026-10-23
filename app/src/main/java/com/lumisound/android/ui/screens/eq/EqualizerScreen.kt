package com.lumisound.android.ui.screens.eq

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisound.android.AppContainer
import com.lumisound.android.audio.EqState
import com.lumisound.android.ui.components.ChipRow
import com.lumisound.android.ui.components.EmptyState
import com.lumisound.android.ui.components.GlassPanel
import com.lumisound.android.ui.components.IconSectionHeader
import com.lumisound.android.ui.components.NavChip
import com.lumisound.android.ui.components.ScreenTitle
import com.lumisound.android.ui.theme.LocalLumiPalette

@Composable
fun EqualizerScreen(container: AppContainer, onBack: (() -> Unit)? = null) {
    val state by container.equalizer.state.collectAsStateWithLifecycle()
    val replayGainEnabled by container.replayGain.enabled.collectAsStateWithLifecycle()
    EqualizerContent(
        state = state,
        onBack = onBack,
        onEnabled = container.equalizer::setEnabled,
        onBand = container.equalizer::setBandLevel,
        onPreset = container.equalizer::applyPreset,
        onReset = container.equalizer::resetFlat,
        onBoost = container.equalizer::setBassBoost,
        onReverbEnabled = container.equalizer::setReverbEnabled,
        onReverbPreset = container.equalizer::setReverbPreset,
        replayGainEnabled = replayGainEnabled,
        onReplayGainEnabled = container.replayGain::setEnabled,
    )
}

/**
 * The device's own equalizer, as a mixing desk: one vertical fader per real hardware band,
 * with the resulting curve drawn through the fader caps so the shape of the sound is visible
 * at a glance. Band count and centre frequencies come from the hardware rather than a fixed
 * curve this app invents, so what is shown is what is actually being applied.
 */
@Composable
fun EqualizerContent(
    state: EqState,
    onBack: (() -> Unit)?,
    onEnabled: (Boolean) -> Unit,
    onBand: (Int, Int) -> Unit,
    onPreset: (Int) -> Unit,
    onReset: () -> Unit,
    onBoost: (Int) -> Unit,
    onReverbEnabled: (Boolean) -> Unit,
    onReverbPreset: (String) -> Unit,
    replayGainEnabled: Boolean,
    onReplayGainEnabled: (Boolean) -> Unit,
) {
    val palette = LocalLumiPalette.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        ScreenTitle(
            "Equalizer",
            subtitle = if (state.available) "${state.bands.size} hardware bands" else null,
            eyebrow = "Sound",
            onBack = onBack,
            trailing = {
                if (state.available) {
                    Switch(
                        checked = state.enabled,
                        onCheckedChange = onEnabled,
                        colors = SwitchDefaults.colors(checkedTrackColor = palette.accent),
                    )
                }
            },
        )
        if (!state.available) {
            EmptyState(
                icon = Icons.Filled.GraphicEq,
                title = "Equalizer unavailable",
                message = state.error?.let { "This device refused to provide one: $it" }
                    ?: "Start playing something — the equalizer attaches to the player's audio session, which only exists once playback has begun.",
                modifier = Modifier.height(420.dp),
            )
            return@Column
        }

        // Flat leads the presets and is the only reset: many devices also ship a preset named
        // "Flat", which is dropped so the row never offers it twice.
        val isFlat = state.bands.all { it.levelMillibel == 0 }
        ChipRow(
            listOf(NavChip("Flat", Icons.Filled.RestartAlt, selected = isFlat, onClick = onReset)) +
                state.presets.mapIndexedNotNull { index, preset ->
                    if (preset.trim().equals("flat", ignoreCase = true)) return@mapIndexedNotNull null
                    NavChip(preset, Icons.Filled.Tune, selected = !isFlat && state.currentPreset == index, onClick = { onPreset(index) })
                }
        )

        GlassPanel {
            MixingDesk(state, onBand, dimmed = !state.enabled)
        }

        IconSectionHeader(Icons.Filled.GraphicEq, "Volume leveling")
        GlassPanel {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Automatically turns down tracks the cloud library measured as too loud, so the whole library plays at a more even level.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = replayGainEnabled,
                    onCheckedChange = onReplayGainEnabled,
                    colors = SwitchDefaults.colors(checkedTrackColor = palette.accent),
                )
            }
        }

        IconSectionHeader(Icons.Filled.GraphicEq, "Extra gain")
        GlassPanel {
            Text(
                "The cloud library's loudness data can only ever turn a track down, so a quiet track is brought back up here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = state.bassBoostMillibel.toFloat(),
                    valueRange = 0f..1_500f,
                    onValueChange = { onBoost(it.toInt()) },
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = palette.accent),
                    modifier = Modifier.weight(1f),
                )
                Text("+%.1f dB".format(state.bassBoostMillibel / 100f), style = MaterialTheme.typography.labelLarge)
            }
        }

        IconSectionHeader(Icons.Filled.Spa, "Reverb")
        GlassPanel {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "A device's own room preset -- no continuous mix control here, unlike iPhone's.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = state.reverbEnabled,
                    onCheckedChange = onReverbEnabled,
                    colors = SwitchDefaults.colors(checkedTrackColor = palette.accent),
                )
            }
            ChipRow(
                REVERB_PRESET_LABELS.map { (name, label) ->
                    NavChip(label, Icons.Filled.Spa, selected = state.reverbPresetName == name, onClick = { onReverbPreset(name) })
                }
            )
        }
    }
}

private val REVERB_PRESET_LABELS = listOf(
    "smallRoom" to "Small Room",
    "mediumRoom" to "Medium Room",
    "largeRoom" to "Large Room",
    "mediumHall" to "Medium Hall",
    "largeHall" to "Large Hall",
    "plate" to "Plate",
)

/**
 * Vertical faders drawn on one canvas, so the curve through their caps can be drawn on the
 * same coordinates. Drag or tap anywhere in a fader's column to set it.
 */
@Composable
private fun MixingDesk(state: EqState, onBand: (Int, Int) -> Unit, dimmed: Boolean) {
    val palette = LocalLumiPalette.current
    val min = state.minLevelMillibel.toFloat()
    val max = state.maxLevelMillibel.toFloat()
    val bands = state.bands
    val alpha = if (dimmed) 0.4f else 1f

    fun levelAt(y: Float, height: Float, top: Float, usable: Float): Int {
        val fraction = 1f - ((y - top) / usable).coerceIn(0f, 1f)
        return (min + (max - min) * fraction).toInt()
    }

    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(240.dp)
                .pointerInput(bands.size, min, max) {
                    val top = 16.dp.toPx()
                    val usable = size.height - top * 2
                    fun apply(offset: Offset) {
                        if (bands.isEmpty()) return
                        val column = ((offset.x / size.width) * bands.size).toInt().coerceIn(0, bands.lastIndex)
                        onBand(bands[column].index, levelAt(offset.y, size.height.toFloat(), top, usable))
                    }
                    detectDragGestures(onDragStart = ::apply, onDrag = { change, _ -> apply(change.position) })
                }
                .pointerInput(bands.size, min, max) {
                    val top = 16.dp.toPx()
                    val usable = size.height - top * 2
                    detectTapGestures { offset ->
                        if (bands.isEmpty()) return@detectTapGestures
                        val column = ((offset.x / size.width) * bands.size).toInt().coerceIn(0, bands.lastIndex)
                        onBand(bands[column].index, levelAt(offset.y, size.height.toFloat(), top, usable))
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                if (bands.isEmpty()) return@Canvas
                val top = 16.dp.toPx()
                val usable = size.height - top * 2
                val columnWidth = size.width / bands.size
                val zeroY = top + usable * (max / (max - min))
                // The 0 dB line.
                drawLine(Color.White.copy(alpha = 0.18f), Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 1.dp.toPx())
                val caps = bands.mapIndexed { i, band ->
                    val x = columnWidth * (i + 0.5f)
                    val fraction = (band.levelMillibel - min) / (max - min)
                    Offset(x, top + usable * (1f - fraction))
                }
                bands.forEachIndexed { i, _ ->
                    val x = caps[i].x
                    // The fader slot, then the lit part between 0 dB and the cap.
                    drawRoundRect(
                        Color.White.copy(alpha = 0.08f),
                        topLeft = Offset(x - 3.dp.toPx(), top),
                        size = Size(6.dp.toPx(), usable),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                    )
                    val from = minOf(zeroY, caps[i].y)
                    val to = maxOf(zeroY, caps[i].y)
                    drawRoundRect(
                        Brush.verticalGradient(listOf(palette.accent.copy(alpha = alpha), palette.accent.copy(alpha = 0.35f * alpha)), startY = from, endY = to),
                        topLeft = Offset(x - 3.dp.toPx(), from),
                        size = Size(6.dp.toPx(), (to - from).coerceAtLeast(1f)),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                    )
                }
                // The curve through every cap.
                val path = Path().apply {
                    moveTo(caps.first().x, caps.first().y)
                    for (i in 1 until caps.size) {
                        val prev = caps[i - 1]
                        val cur = caps[i]
                        val midX = (prev.x + cur.x) / 2
                        cubicTo(midX, prev.y, midX, cur.y, cur.x, cur.y)
                    }
                }
                drawPath(path, Color.White.copy(alpha = 0.55f * alpha), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                caps.forEach { cap ->
                    drawCircle(Color.White.copy(alpha = alpha), 9.dp.toPx(), cap)
                    drawCircle(palette.accent.copy(alpha = alpha), 4.dp.toPx(), cap)
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            bands.forEach { band ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "%+.0f".format(band.levelMillibel / 100f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        band.centerFrequencyHz.asFrequencyLabel(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun Int.asFrequencyLabel(): String = if (this >= 1_000) "${this / 1_000}k" else "$this"
