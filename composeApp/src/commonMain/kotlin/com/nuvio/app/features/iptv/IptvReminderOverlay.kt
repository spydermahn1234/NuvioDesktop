package com.nuvio.app.features.iptv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioGlass
import com.nuvio.app.core.ui.nuvio

/**
 * fork:live-tv-departure - the reminder card, bottom left (the score pop-ups use the right and the top). A fixed size
 * and place: nothing moves or resizes with the text. A switching reminder's card takes no input; a plain reminder's
 * card (fork:live-tv-reminder-card) asks Switch now, At the start or Not now, in a focused popup of the same place.
 * Over a playing video the HUD says it instead.
 */
@Composable
fun IptvReminderOverlay(modifier: Modifier = Modifier) {
    val shown by IptvReminderEngine.showing.collectAsStateWithLifecycle()
    val asking = shown?.takeIf { it.options.isNotEmpty() }
    if (asking != null) {
        IptvReminderQuestion(asking)
        return
    }
    // Keep the last card while it fades out.
    var last by remember { mutableStateOf<IptvReminderEngine.Card?>(null) }
    LaunchedEffect(shown) { if (shown != null) last = shown }
    val card = shown ?: last
    val tokens = MaterialTheme.nuvio
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = shown != null,
            enter = fadeIn() + slideInVertically { it / 6 },
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 32.dp, bottom = 32.dp),
        ) {
            Column(
                modifier = Modifier
                    .width(380.dp)
                    .height(78.dp)
                    .iptvGlass(NuvioGlass.Shape)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            ) {
                Text(
                    text = card?.title.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = card?.line.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** fork:live-tv-reminder-card - the card that asks: three equal buttons, left and right to move, OK to pick, Back for Not now. */
@Composable
private fun IptvReminderQuestion(card: IptvReminderEngine.Card) {
    val tokens = MaterialTheme.nuvio
    var at by remember(card.id) { mutableIntStateOf(card.focused.coerceIn(0, card.options.lastIndex)) }
    val focus = remember(card.id) { FocusRequester() }
    LaunchedEffect(card.id) { runCatching { focus.requestFocus() } }
    Popup(
        alignment = Alignment.BottomStart,
        offset = with(LocalDensity.current) { IntOffset(32.dp.roundToPx(), -32.dp.roundToPx()) },
        properties = PopupProperties(focusable = true),
        onDismissRequest = { IptvReminderEngine.answer(-1) },
    ) {
        com.nuvio.app.core.ui.KeyRouteLayer("reminder card") // the focus router leaves the open card alone
        Column(
            modifier = Modifier
                .width(440.dp)
                .height(140.dp)
                .iptvGlass(NuvioGlass.Shape)
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> at = (at - 1).coerceAtLeast(0)
                        Key.DirectionRight -> at = (at + 1).coerceAtMost(card.options.lastIndex)
                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter, Key.Spacebar -> IptvReminderEngine.answer(at)
                        Key.Escape, Key.Back, Key.Backspace -> IptvReminderEngine.answer(-1)
                        else -> return@onPreviewKeyEvent false
                    }
                    true
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(card.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = tokens.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(card.line, style = MaterialTheme.typography.bodyMedium, color = tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                card.options.forEachIndexed { i, label ->
                    val on = i == at
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (on) tokens.colors.textPrimary.copy(alpha = 0.92f) else tokens.colors.textPrimary.copy(alpha = 0.08f))
                            .clickable { IptvReminderEngine.answer(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (on) tokens.colors.background else tokens.colors.textPrimary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
