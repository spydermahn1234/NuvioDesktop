// fork:shelf-home live-tv
package com.nuvio.app.features.home.shelves

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.home.LiveTvHomeCard
import com.nuvio.app.features.home.HomeLiveTvRow
import com.nuvio.app.features.home.isLiveTvHomeCard
import com.nuvio.app.features.iptv.IptvEpgEntry
import com.nuvio.app.features.iptv.IptvFilmArt
import com.nuvio.app.features.iptv.IptvGuideText
import com.nuvio.app.features.iptv.IptvLivePreview
import com.nuvio.app.features.iptv.IptvPlaybackSession
import com.nuvio.app.features.iptv.IptvPreviewCover
import com.nuvio.app.features.iptv.IptvProgrammeKind
import com.nuvio.app.features.iptv.IptvShowArt
import com.nuvio.app.features.tonight.clockLabel
import kotlinx.coroutines.delay

/*
 * fork:shelf-home live-tv - the hero behind a Live TV favourite on the shelf home (Nick, Nov 28: "what can live tv have
 * as a backdrop"). Four layers, best on top:
 *  1. the sky: a gradient that follows the time of day (night navy, dawn rose, day blue, dusk amber), always there;
 *  2. the channel's colour: the accent is taken from the channel's logo (not the art), so the slashes, the glow over
 *     the sky and the art lights wear Sky Cinema red, Sky Sports blue;
 *  3. what is on now: a film's TMDB backdrop, the guide's own picture, or a series' TMDB backdrop (exact name only),
 *     with the programme's name, a progress bar, its end time and what is next in the title block;
 *  4. the channel itself (opt-in, `ShelfExtrasSettings.livePreview`): after 0.7 s still on a channel it plays muted
 *     in the open part of the art, as the trailers do (one stream at a time, stopped when the card changes).
 */

/** The pure rules, tested by ShelfLiveRulesTest. */
internal object ShelfLiveRules {
    /** How long a channel has to stay focused before its live picture is asked for (on top of the art's 250 ms). */
    const val PREVIEW_WAIT_MS = 700L

    /** The glow of the channel's colour over the sky. */
    const val GLOW_ALPHA = 0.30f

    /** The progress bar in the "until" line. */
    const val BAR_W = 140f
    const val BAR_H = 4f

    fun isLive(card: ShelfCard?): Boolean = (card as? ShelfCard.Meta)?.item?.isLiveTvHomeCard() == true

    /** The favourite's `playlistId:channelKey`, or null for any other card. */
    fun ref(card: ShelfCard?): String? = (card as? ShelfCard.Meta)?.item?.takeIf { it.isLiveTvHomeCard() }?.id

    /** The channel's logo, which the accent is taken from. */
    fun logo(card: ShelfCard?): String? = (card as? ShelfCard.Meta)?.item?.takeIf { it.isLiveTvHomeCard() }?.logo?.takeIf { it.isNotBlank() }

    /** One stop of the sky: [minute] of the day, its [top] and [bottom] colours (ARGB). */
    data class SkyStop(val minute: Int, val top: Long, val bottom: Long)

    /** Dark enough everywhere for the words; the scrims do the rest. Wraps from the last stop back to the first. */
    val SKY = listOf(
        SkyStop(0, 0xFF0A0F24, 0xFF04050B), // night
        SkyStop(5 * 60, 0xFF0E1530, 0xFF05060D), // before dawn
        SkyStop(6 * 60 + 30, 0xFF2E2C57, 0xFF3A2131), // dawn: violet into rose
        SkyStop(8 * 60, 0xFF234260, 0xFF182433), // morning blue
        SkyStop(12 * 60, 0xFF1F4A6E, 0xFF12202C), // day
        SkyStop(17 * 60, 0xFF2A3552, 0xFF1A1A26), // late afternoon
        SkyStop(18 * 60 + 30, 0xFF4C2B2C, 0xFF2A140F), // dusk amber
        SkyStop(20 * 60 + 30, 0xFF1A1838, 0xFF0A0A16), // evening
        SkyStop(24 * 60, 0xFF0A0F24, 0xFF04050B), // midnight again
    )

    /** The sky's two colours at [minuteOfDay], blended between the stops either side. */
    fun sky(minuteOfDay: Int): Pair<Color, Color> {
        val m = ((minuteOfDay % (24 * 60)) + 24 * 60) % (24 * 60)
        val after = SKY.indexOfFirst { it.minute > m }.coerceAtLeast(1)
        val a = SKY[after - 1]
        val b = SKY[after]
        val t = (m - a.minute).toFloat() / (b.minute - a.minute).toFloat()
        return lerp(Color(a.top), Color(b.top), t) to lerp(Color(a.bottom), Color(b.bottom), t)
    }

    /** The programme's name without the guide's marks ("New: ", "(Live)", " S2 E5"). */
    fun title(entry: IptvEpgEntry?): String? = entry?.title?.takeIf { it.isNotBlank() }?.let { IptvGuideText.clean(it).name }

    private fun kindLabel(kind: String?): String? = when (kind) {
        IptvProgrammeKind.FILM -> "Film"
        IptvProgrammeKind.SPORT -> "Sport"
        IptvProgrammeKind.NEWS -> "News"
        IptvProgrammeKind.KIDS -> "Kids"
        else -> null
    }

    /**
     * The tracked line: "ON NOW · FILM · 2014", "ON NOW · S2 E5 · NEW", "LIVE · SPORT"; "LIVE TV" when the guide has
     * nothing for the channel.
     */
    fun facts(now: IptvEpgEntry?): String {
        if (now == null) return "LIVE TV"
        val clean = IptvGuideText.clean(now.title)
        return listOfNotNull(
            if (now.isLive || clean.isLive) "Live" else "On now",
            kindLabel(now.kind),
            now.episode ?: clean.episode ?: now.year?.toString(),
            "New".takeIf { now.isNew || clean.isNew },
        ).joinToString(" · ").uppercase()
    }

    /** "Until 09:10 · Next: Heat"; null without a programme on now. [clock] formats epoch ms. */
    fun until(now: IptvEpgEntry?, next: IptvEpgEntry?, clock: (Long) -> String): String? {
        if (now == null) return null
        val nextTitle = title(next)
        return buildString {
            append("Until ")
            append(clock(now.endMs))
            if (nextTitle != null) {
                append(" · Next: ")
                append(nextTitle)
                if (next != null && next.startMs > now.endMs) append(" at ${clock(next.startMs)}")
            }
        }
    }

    /** True when the programme deserves a look-up of a series' art (not news, sport or shopping). */
    fun wantsShowArt(entry: IptvEpgEntry): Boolean =
        entry.kind != IptvProgrammeKind.NEWS && entry.kind != IptvProgrammeKind.SPORT && !IptvGuideText.isShopping(entry.title)
}

/** "09:10" (or "9:10 PM", as Tonight's clock is set) for an epoch time, in the PC's zone. */
internal fun shelfLiveClock(epochMs: Long): String {
    val time = java.time.Instant.ofEpochMilli(epochMs).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
    return clockLabel(time.hour * 60 + time.minute)
}

/** The favourites as the Live TV row knows them, by ref. */
@Composable
internal fun rememberShelfLiveCards(): Map<String, LiveTvHomeCard> {
    val cards by HomeLiveTvRow.cards.collectAsStateWithLifecycle()
    return remember(cards) { cards.associateBy { it.ref } }
}

/**
 * The backdrop for what [live] has on now: a film's TMDB backdrop, else the guide's own picture, else a series'
 * TMDB backdrop. Null while nothing is known (the sky shows) or for a card that is not live.
 */
@Composable
internal fun rememberShelfLiveBackdrop(live: LiveTvHomeCard?): String? {
    val entry = live?.now
    val title = ShelfLiveRules.title(entry).orEmpty()
    val film = entry?.kind == IptvProgrammeKind.FILM
    val guidePicture = entry?.image?.takeIf { it.isNotBlank() }
    val initial = when {
        entry == null -> null
        film -> IptvFilmArt.peek(title)?.backdrop ?: guidePicture
        guidePicture != null -> guidePicture
        else -> IptvShowArt.peek(title)?.picture
    }
    return produceState(initial, title, film, guidePicture) {
        if (entry == null || title.isBlank()) return@produceState
        value = when {
            film -> IptvFilmArt.find(title)?.backdrop ?: guidePicture
            guidePicture != null -> guidePicture
            ShelfLiveRules.wantsShowArt(entry) -> IptvShowArt.find(title)?.picture
            else -> null
        }
    }.value
}

/** Layers 1 and 2: the time-of-day sky with a glow of the channel's [glow] colour from the right. */
@Composable
internal fun ShelfLiveSky(nowMinute: Int, glow: Color, modifier: Modifier = Modifier) {
    val (top, bottom) = remember(nowMinute) { ShelfLiveRules.sky(nowMinute) }
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top, bottom)))) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = constraints.maxWidth.toFloat()
            val h = constraints.maxHeight.toFloat()
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(
                        colors = listOf(glow.copy(alpha = ShelfLiveRules.GLOW_ALPHA), glow.copy(alpha = 0f)),
                        center = androidx.compose.ui.geometry.Offset(w * 0.72f, h * 0.42f),
                        radius = (maxOf(w, h) * 0.55f).coerceAtLeast(1f),
                    ),
                ),
            )
        }
    }
}

/**
 * fork:shelf-home live-tv - Paper has no backdrop layer, so there what is on now is a picture printed on the page: the
 * programme's art in the trailers' window, with a hairline frame (Nick chose this on Nov 28). Nothing when there is no
 * art. Place it full size inside ShelfHome's frame, before [ShelfLivePreviewLayer].
 */
@Composable
internal fun ShelfLivePaperPicture(url: String?, modifier: Modifier = Modifier, topInset: Dp = 0.dp) {
    val ink = LocalShelfPalette.current.ink
    BoxWithConstraints(modifier.fillMaxSize()) {
        val window = ShelfTrailerRules.window(maxWidth.value, maxHeight.value, topInset.value) ?: return@BoxWithConstraints
        androidx.compose.animation.Crossfade(
            targetState = url,
            animationSpec = tween(ShelfTrailerRules.FADE_MS),
            modifier = Modifier.offset(window.left.dp, window.top.dp).size(window.width.dp, window.height.dp),
            label = "shelfLivePaper",
        ) { shown ->
            if (shown != null) {
                Box(Modifier.fillMaxSize().border(1.dp, ink.copy(alpha = 0.18f)).padding(1.dp)) {
                    com.nuvio.app.core.ui.NuvioAsyncImage(
                        model = shown,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/**
 * Layer 4: the focused channel playing, muted, in the open part of the art (the trailers' window). Place it full size
 * inside ShelfHome's frame, beside the trailer layer. Returns true while the picture is up ("LIVE · MUTED").
 */
@Composable
internal fun ShelfLivePreviewLayer(
    card: ShelfCard?,
    active: Boolean,
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
): Boolean {
    val ref = ShelfLiveRules.ref(card)
    val playing by IptvPlaybackSession.active.collectAsStateWithLifecycle()
    val menuCover by IptvPreviewCover.covered.collectAsStateWithLifecycle()
    val wanted = active && ref != null && playing == null && !menuCover
    var armedRef by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(ref, wanted) {
        armedRef = null
        if (!wanted) return@LaunchedEffect
        delay(ShelfLiveRules.PREVIEW_WAIT_MS)
        armedRef = ref
    }
    val source = remember(armedRef) { armedRef?.let(HomeLiveTvRow::previewSource) }
    var showing by remember(source?.first) { mutableStateOf(false) }
    val ink = LocalShelfPalette.current.ink

    BoxWithConstraints(modifier.fillMaxSize()) {
        val window = ShelfTrailerRules.window(maxWidth.value, maxHeight.value, topInset.value) ?: return@BoxWithConstraints
        val mounted = wanted && source != null && armedRef == ref
        val shade by animateFloatAsState(
            targetValue = if (mounted && showing) 1f else 0f,
            animationSpec = tween(if (mounted) ShelfTrailerRules.FADE_MS else 0),
            label = "shelfLiveShade",
        )
        if (shade > 0f) {
            Box(
                Modifier
                    .offset((window.left - 1f).dp, (window.top - 1f).dp)
                    .size((window.width + 2f).dp, (window.height + 2f).dp)
                    .alpha(shade)
                    .border(1.dp, ink.copy(alpha = 0.16f)),
            ) {
                Box(Modifier.padding(1.dp).fillMaxSize().background(Color(0xFF06090A)))
            }
        }
        if (mounted && source != null) {
            IptvLivePreview(
                url = source.first,
                headers = source.second,
                modifier = Modifier.offset(window.left.dp, window.top.dp).size(window.width.dp, window.height.dp),
                onShowing = { showing = it },
            )
        }
    }
    return showing && wanted
}
