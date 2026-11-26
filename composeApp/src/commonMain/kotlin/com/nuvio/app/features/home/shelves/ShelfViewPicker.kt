// fork:shelf-home
package com.nuvio.app.features.home.shelves

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.KeyRouteLayer
import com.nuvio.app.core.ui.keyLayer
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.requestFocusLogged

/*
 * fork:shelf-home - phase 5: "View <shelf> as": one centred card over a dark scrim, a row per built view
 * (ShelfPresentationRules.available: Posters and Logos) plus "Use the default" when the shelf has a
 * choice of its own, and "Edit this shelf" for a shelf the user built. Opened from the Shelves overlay's
 * Menu ("View as"); V on a row toggles Posters and Logos without it.
 * Fixed sizes: the card is the same width whatever the shelf is called, every row the same height.
 * Up/Down move between the rows and never leave the card; OK picks; Back/Escape or a click outside closes.
 */

private val PickerFill: Color
    @Composable @ReadOnlyComposable get() = LocalShelfPalette.current.pickerFill
private val PickerScrim: Color
    @Composable @ReadOnlyComposable get() = LocalShelfPalette.current.pickerScrim
private val PickerShape = RoundedCornerShape(18.dp)
private val RowShape = RoundedCornerShape(12.dp)
private val CloseKeys = setOf(Key.Back, Key.Escape)

internal fun shelfViewDescription(mode: ShelfViewMode): String = when (mode) {
    ShelfViewMode.Posters -> "A row of posters"
    ShelfViewMode.Logos -> "A row of title logos"
}

@Composable
internal fun ShelfViewPicker(
    shelfTitle: String,
    current: ShelfViewMode,
    isDefault: Boolean,
    onPick: (ShelfViewMode) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    /** Set for a shelf the user built (a mix): adds "Edit this shelf" after the views. */
    onEdit: (() -> Unit)? = null,
) {
    KeyRouteLayer("ShelfViewPicker")
    val tokens = MaterialTheme.nuvio
    val modes = remember { ShelfPresentationRules.available() }
    // The rows: each view, then "Use the default" (null) when the shelf has a choice of its own.
    val rows: List<ShelfViewMode?> = remember(isDefault) { if (isDefault) modes else modes + null }
    val rowCount = rows.size + if (onEdit != null) 1 else 0
    val requesters = remember(rowCount) { List(rowCount) { FocusRequester() } }
    var focusedIndex by remember { mutableIntStateOf(0) }
    val dismiss by rememberUpdatedState(onDismiss)

    LaunchedEffect(Unit) { requesters.first().requestFocusLogged() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PickerScrim)
            // Takes every pointer event so nothing behind can be clicked; a click outside the card closes it.
            .pointerInput(Unit) { detectTapGestures { dismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(460.dp)
                .shadow(32.dp, PickerShape, clip = false)
                .background(PickerFill, PickerShape)
                .border(1.dp, LocalShelfPalette.current.pickerBorder, PickerShape)
                // A click on the card's own background stays in the card.
                .pointerInput(Unit) { detectTapGestures { } }
                .keyLayer("ShelfViewPicker") { requesters[focusedIndex.coerceIn(0, requesters.lastIndex)].requestFocusLogged() }
                .onPreviewKeyEvent { event ->
                    when {
                        event.key in CloseKeys -> {
                            if (event.type == KeyEventType.KeyUp) dismiss()
                            true
                        }
                        event.type != KeyEventType.KeyDown -> false
                        event.key == Key.DirectionUp -> {
                            if (focusedIndex > 0) requesters[focusedIndex - 1].requestFocusLogged()
                            true
                        }
                        event.key == Key.DirectionDown -> {
                            if (focusedIndex < requesters.lastIndex) requesters[focusedIndex + 1].requestFocusLogged()
                            true
                        }
                        // Left/Right would let focus wander to the shelf behind the card.
                        event.key == Key.DirectionLeft || event.key == Key.DirectionRight -> true
                        else -> false
                    }
                }
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "View $shelfTitle as",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = LocalShelfPalette.current.pickerTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            rows.forEachIndexed { index, mode ->
                PickerRow(
                    title = mode?.name ?: "Use the default",
                    description = mode?.let(::shelfViewDescription) ?: "Let Nuvio choose for this shelf",
                    ticked = mode != null && mode == current,
                    requester = requesters[index],
                    onFocused = { focusedIndex = index },
                    onClick = { if (mode == null) onReset() else onPick(mode) },
                )
            }
            if (onEdit != null) {
                PickerRow(
                    title = "Edit this shelf",
                    description = "Change its rows and rules",
                    ticked = false,
                    requester = requesters[rows.size],
                    onFocused = { focusedIndex = rows.size },
                    onClick = onEdit,
                )
            }
        }
    }
}

@Composable
private fun PickerRow(
    title: String,
    description: String,
    ticked: Boolean,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(
                when {
                    focused -> tokens.colors.accent.copy(alpha = 0.42f)
                    hovered -> LocalShelfPalette.current.pickerRowHover
                    else -> LocalShelfPalette.current.pickerRow
                },
                RowShape,
            )
            .border(1.dp, if (focused) tokens.colors.accent.copy(alpha = 0.9f) else LocalShelfPalette.current.pickerRowBorder, RowShape)
            .clip(RowShape)
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The tick's column is always there, so the text never shifts when the tick moves.
        Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.CenterStart) {
            if (ticked) {
                Text(text = "✓", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = LocalShelfPalette.current.accentInk)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = LocalShelfPalette.current.pickerTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = LocalShelfPalette.current.pickerMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
