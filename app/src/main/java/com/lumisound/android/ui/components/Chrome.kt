package com.lumisound.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.lumisound.android.ui.aura.AuraBase
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lumisound.android.ui.theme.EyebrowStyle
import com.lumisound.android.ui.theme.LocalLumiPalette

/**
 * A cluster of round glass icon buttons. It used to be a centred pill above every title;
 * it now sits at the right of the header row, where the thumb already is.
 */
@Composable
fun CapsuleToolbar(
    actions: List<ToolbarAction>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        actions.forEach { action -> GlassIconButton(action.icon, action.label, selected = action.selected, onClick = action.onClick) }
    }
}

data class ToolbarAction(
    val icon: ImageVector,
    val label: String,
    val selected: Boolean = false,
    val onClick: () -> Unit = {},
)

/** A 42dp round glass button -- the one icon-button shape used across the app. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    badge: Boolean = false,
    onClick: () -> Unit,
) {
    val palette = LocalLumiPalette.current
    Box(modifier) {
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (selected) palette.accent.copy(alpha = 0.28f) else palette.elevatedSurface)
                .border(1.dp, palette.hairline, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(palette.accent)
                    .border(2.dp, AuraBase, CircleShape)
            )
        }
    }
}

/**
 * The screen heading: an optional back arrow and trailing actions on one row, then a small
 * spaced-capital eyebrow and a heavy title. Every screen opens the same way, which is most
 * of what makes thirty screens feel like one app.
 */
@Composable
fun ScreenTitle(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        if (onBack != null || trailing != null) {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
                Spacer(Modifier.weight(1f))
                trailing?.invoke()
            }
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = if (onBack != null || trailing != null) 10.dp else 16.dp, bottom = 12.dp)) {
            eyebrow?.let {
                Text(it.uppercase(), style = EyebrowStyle, color = LocalLumiPalette.current.accent)
                Spacer(Modifier.height(4.dp))
            }
            Text(title, style = MaterialTheme.typography.displaySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The glass search field used at the top of every searchable screen. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    /** When set, the keyboard shows a search key that calls this. */
    onSubmit: (() -> Unit)? = null,
) {
    val palette = LocalLumiPalette.current
    val shape = RoundedCornerShape(20.dp)
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingIcon = {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(21.dp),
            )
        },
        singleLine = true,
        keyboardOptions = if (onSubmit != null) KeyboardOptions(imeAction = ImeAction.Search) else KeyboardOptions.Default,
        keyboardActions = KeyboardActions(onSearch = { onSubmit?.invoke() }),
        shape = shape,
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = palette.elevatedSurface,
            unfocusedContainerColor = palette.elevatedSurface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = palette.accent,
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .border(1.dp, palette.hairline, shape)
            .height(56.dp),
    )
}

/** One entry in a scrolling chip row. */
data class NavChip(val label: String, val icon: ImageVector, val selected: Boolean, val onClick: () -> Unit)

@Composable
fun ChipRow(chips: List<NavChip>, modifier: Modifier = Modifier) {
    val palette = LocalLumiPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { chip ->
            Row(
                Modifier
                    .clip(CircleShape)
                    .then(
                        if (chip.selected) Modifier.background(palette.accentBrush)
                        else Modifier.background(palette.elevatedSurface).border(1.dp, palette.hairline, CircleShape)
                    )
                    .clickable(onClick = chip.onClick)
                    .padding(horizontal = 15.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    chip.icon,
                    contentDescription = null,
                    tint = if (chip.selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    chip.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (chip.selected) Color.White else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * A section heading: a short coloured bar, a bold title, and an optional "See all". The
 * tint still varies by section -- it is what lets a long page read as sections -- but as a
 * mark beside the words rather than another boxed icon.
 */
@Composable
fun IconSectionHeader(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    onSeeAll: (() -> Unit)? = null,
) {
    val palette = LocalLumiPalette.current
    val color = tint ?: palette.accent
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(9.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        onSeeAll?.let {
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(palette.elevatedSurface)
                    .clickable(onClick = it)
                    .padding(start = 11.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("See all", style = MaterialTheme.typography.labelMedium)
                Icon(Icons.Filled.ChevronRight, contentDescription = null, modifier = Modifier.size(15.dp))
            }
        }
    }
}

/** Small glass capsules of facts under a header. */
@Composable
fun StatPills(stats: List<Pair<ImageVector, String>>, modifier: Modifier = Modifier) {
    val palette = LocalLumiPalette.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        stats.forEach { (icon, text) ->
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(palette.elevatedSurface)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Text(text, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
