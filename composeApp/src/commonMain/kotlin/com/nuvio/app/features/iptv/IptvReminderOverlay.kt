package com.nuvio.app.features.iptv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
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
 * and place: nothing moves or resizes with the text. Takes no input. Over a playing video the HUD pill says it instead.
 */
@Composable
fun IptvReminderOverlay(modifier: Modifier = Modifier) {
    val shown by IptvReminderEngine.showing.collectAsStateWithLifecycle()
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
