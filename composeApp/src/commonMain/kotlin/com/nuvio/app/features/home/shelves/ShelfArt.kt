// fork:shelf-home
package com.nuvio.app.features.home.shelves

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nuvio.app.features.preroll.tmdbExtraBackdrops
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlin.time.TimeSource

/*
 * fork:shelf-home - v3 phase 1, the art set (mockups V3Art, V3ArtRules; plan in design-notes/home-shelves-v3.md).
 * Per title an ordered list of backdrops: TMDB's textless extras first (the same look-up as the pre-show's
 * TriviaArtwork, shared through `tmdbExtraBackdrops`), the card's own (addon) background after. A card with no
 * backdrop at all keeps its one wide picture as a fallback, which the frame draws as the blurred poster. While the
 * same card stays, the next backdrop comes in every 8 s; "ART 2 OF 5" goes in the row header. Setting: Textless
 * art first (`ShelfExtrasSettings.textlessArt`); off, the card's own backdrop only, as before.
 */

/** One card's pictures, in order. [fallback]: no real backdrop, [urls] holds only its wide picture or poster. */
internal data class ShelfArtSet(val urls: List<String>, val fallback: Boolean = false)

/** What the frame's art cross-fades between: the card (for its blurred poster) and the backdrop over it, if any. */
internal data class ShelfArtFrame(val card: ShelfCard?, val backdrop: String?)

internal object ShelfArtRules {
    /** How long one backdrop is held before the next comes in, while the same card stays. */
    const val HOLD_MS = 8_000L

    /**
     * A set that arrives within this long of the card being shown starts on its first (textless) picture; one
     * that arrives later keeps the picture already on screen, so the art never changes twice in a few seconds.
     */
    const val SWAP_WINDOW_MS = 1_500L

    /** At most this many pictures per title (the pre-show's own cap). */
    const val MAX = 10

    /** "ART 2 OF 5", or null when there is nothing to step through. */
    fun label(index: Int, count: Int): String? =
        if (count <= 1) null else "ART ${index.coerceIn(0, count - 1) + 1} OF $count"

    /** The picture after [index], back to the first after the last. */
    fun next(index: Int, count: Int): Int = if (count <= 0) 0 else (index + 1).let { if (it >= count) 0 else it }

    /**
     * The ordered set: [textless] (TMDB's extras) first when [textlessFirst], then the card's own [fanart];
     * trimmed, blank and repeated urls dropped, at most [MAX]. With no backdrop at all, [wide] (the card's any
     * wide picture) or else its [poster] as a [ShelfArtSet.fallback]; empty only when the card has no picture.
     */
    fun merge(
        textless: List<String>,
        fanart: String?,
        wide: String?,
        poster: String?,
        textlessFirst: Boolean,
    ): ShelfArtSet {
        val backdrops = ((if (textlessFirst) textless else emptyList()) + listOfNotNull(fanart))
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .take(MAX)
        if (backdrops.isNotEmpty()) return ShelfArtSet(backdrops)
        val last = listOfNotNull(wide, poster).map(String::trim).firstOrNull(String::isNotEmpty)
        return ShelfArtSet(listOfNotNull(last), fallback = last != null)
    }

    /** Where to start in a set that has just arrived (see [SWAP_WINDOW_MS]). */
    fun indexAfterArrival(shown: String?, urls: List<String>, elapsedMs: Long): Int {
        if (urls.isEmpty()) return 0
        if (elapsedMs <= SWAP_WINDOW_MS || shown == null) return 0
        return urls.indexOf(shown).takeIf { it >= 0 } ?: 0
    }

    /** The id and type TMDB is asked with, or null for a card that is not a title (a folder). */
    fun lookup(card: ShelfCard): Pair<String, String>? = when (card) {
        is ShelfCard.Meta -> card.item.id to card.item.type
        is ShelfCard.Progress -> card.item.parentMetaId to card.item.parentMetaType
        is ShelfCard.Folder -> null
    }?.takeIf { (id, type) -> id.isNotBlank() && type.isNotBlank() }
}

/** The card's set from what it carries, with [textless] when they are known. */
internal fun ShelfCard.artSet(textless: List<String>, textlessFirst: Boolean): ShelfArtSet =
    ShelfArtRules.merge(textless, fanartUrl(), backdropUrl(), posterUrl(), textlessFirst)

/**
 * TMDB's extra backdrops per card, for the session (by [stableId]). An answer is remembered even when empty, so a
 * title TMDB does not know costs its look-up once. Called from the UI's coroutines only (LaunchedEffect), so the
 * maps need no lock; two callers asking for one card share the request.
 */
internal object ShelfArtSets {
    private val done = HashMap<String, List<String>>()
    private val pending = HashMap<String, CompletableDeferred<List<String>>>()

    fun cached(card: ShelfCard): List<String>? = done[card.stableId()]

    /** The extras for [card], fetching them once; empty for a folder, without a TMDB key, or on failure. */
    suspend fun load(card: ShelfCard): List<String> {
        val key = card.stableId()
        done[key]?.let { return it }
        pending[key]?.let { return it.await() }
        val (id, type) = ShelfArtRules.lookup(card) ?: return emptyList()
        val waiter = CompletableDeferred<List<String>>()
        pending[key] = waiter
        try {
            val urls = try {
                tmdbExtraBackdrops(id, type)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                emptyList()
            }
            done[key] = urls
            waiter.complete(urls)
            return urls
        } finally {
            // Left by a cancel: whoever shares it gets nothing this time and may ask again.
            if (!waiter.isCompleted) waiter.complete(emptyList())
            pending.remove(key)
        }
    }
}

/** The art for one card: its ordered pictures and the one on screen. A new one per card (and per setting). */
@Stable
internal class ShelfArtState(initial: ShelfArtSet) {
    var urls: List<String> by mutableStateOf(initial.urls)
        private set

    /** True while [urls] holds only the card's poster or wide picture: the frame draws its blurred poster. */
    var fallback: Boolean by mutableStateOf(initial.fallback)
        private set

    var index: Int by mutableIntStateOf(0)
        private set

    /** Bumped by [advance], so the 8 s hold starts again after a manual step. */
    internal var steps: Int by mutableIntStateOf(0)
        private set

    /** The picture on screen; null only when the card has no picture at all. */
    val current: String? get() = urls.getOrNull(index)

    /** The backdrop to draw, or null when the frame should show the blurred poster instead. */
    val backdrop: String? get() = if (fallback) null else current

    /** "ART 2 OF 5" for the row header, or null with one picture. */
    val label: String? get() = if (fallback) null else ShelfArtRules.label(index, urls.size)

    /** The next picture (the gallery; also every [ShelfArtRules.HOLD_MS] by itself). */
    fun advance() {
        if (urls.size <= 1) return
        index = ShelfArtRules.next(index, urls.size)
        steps++
    }

    internal fun tick() {
        if (urls.size > 1) index = ShelfArtRules.next(index, urls.size)
    }

    internal fun arrive(set: ShelfArtSet, start: Int) {
        if (set.urls.isEmpty()) return
        urls = set.urls
        fallback = set.fallback
        index = start.coerceIn(0, set.urls.size - 1)
    }
}

/**
 * The art set for [card] (pass the debounced art card, so only a settled title is fetched). The card's own
 * backdrop shows at once (or the cached set); TMDB's textless extras are fetched without blocking and swapped
 * in when they come. With [enabled] off: the card's own backdrop only, nothing fetched, nothing stepping.
 */
@Composable
internal fun rememberShelfArt(card: ShelfCard?, enabled: Boolean): ShelfArtState {
    val key = card?.stableId()
    val state = remember(key, enabled) {
        val known = if (enabled && card != null) ShelfArtSets.cached(card) else null
        ShelfArtState(card?.artSet(known.orEmpty(), textlessFirst = enabled) ?: ShelfArtSet(emptyList()))
    }
    LaunchedEffect(state) {
        if (!enabled || card == null || ShelfArtSets.cached(card) != null) return@LaunchedEffect
        val shownAt = TimeSource.Monotonic.markNow()
        val extras = ShelfArtSets.load(card)
        if (extras.isEmpty()) return@LaunchedEffect
        val set = card.artSet(extras, textlessFirst = true)
        state.arrive(set, ShelfArtRules.indexAfterArrival(state.current, set.urls, shownAt.elapsedNow().inWholeMilliseconds))
    }
    // The hold: restarted when the set arrives and after a manual step.
    LaunchedEffect(state, state.urls, state.steps) {
        if (state.urls.size <= 1) return@LaunchedEffect
        while (true) {
            delay(ShelfArtRules.HOLD_MS)
            state.tick()
        }
    }
    return state
}
