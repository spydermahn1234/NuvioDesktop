package com.nuvio.app.features.remote

import com.nuvio.app.features.home.shelves.ShelfCard
import com.nuvio.app.features.home.shelves.ShelfFrameRules
import com.nuvio.app.features.home.shelves.ShelfGroupEntry
import com.nuvio.app.features.home.shelves.ShelfGroupRules
import com.nuvio.app.features.home.shelves.ShelfHomeRules
import com.nuvio.app.features.home.shelves.ShelfTab
import com.nuvio.app.features.home.shelves.ShelfViewMode
import com.nuvio.app.features.home.shelves.posterUrl
import com.nuvio.app.features.home.shelves.title
import com.nuvio.app.features.tonight.clockLabel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * fork:phone-remote shelf-home - the shelf home as the phone mirrors it (v3 phase 9, board V3Phone). While the TV is on
 * the shelf home it publishes where it is: the tab and row, a window of the row's posters around the focused card, the
 * focused title's words and the art behind it, and every row grouped as the Shelves overlay groups them. The phone's
 * taps come back as [RemoteShelfHomeAction]s on [RemoteShelfHome.actions] and do what the TV's keys would.
 */

/** One poster of the row's window. */
data class RemoteShelfHomeCard(val title: String, val poster: String?)

/** A row in the phone's Shelves list. [bucket] is the letter range a catalog sits in ("A – C"), or null. */
data class RemoteShelfRow(
    val tabId: String,
    val tabTitle: String,
    /** "YOURS", "FOR YOU", "CHARTS" or "CATALOGS" (ShelfGroupRules). */
    val group: String,
    val bucket: String?,
    val key: String,
    val title: String,
)

data class RemoteShelfHomeScreen(
    /** [BROWSE], [SHELVES] or [INFO]: what the TV shows over the home. */
    val mode: String,
    val tabId: String,
    val tabTitle: String,
    val shelfKey: String?,
    val rowTitle: String,
    /** The row's place among the tab's rows, from 0, and how many there are. */
    val row: Int,
    val rows: Int,
    /** The focused card, from 0, and how many the row has loaded. */
    val index: Int,
    val count: Int,
    /** The index of [cards]' first card in the row. */
    val from: Int,
    val cards: List<RemoteShelfHomeCard>,
    val title: String,
    /** "2007 · DRAMA · ENDS 23:42". */
    val facts: String,
    /** The backdrop on the TV. */
    val art: String?,
    /** The row's view: "Posters" or "Logos" ([ShelfViewMode] names). */
    val view: String,
    /** Every row of every tab, in the Shelves overlay's groups. Sent as its own SHELVES line. */
    val shelves: List<RemoteShelfRow> = emptyList(),
) {
    companion object {
        const val BROWSE = "browse"
        const val SHELVES = "shelves"
        const val INFO = "info"
    }
}

/** A phone's tap on the shelf home mirror (the words after HOME_DO). */
sealed interface RemoteShelfHomeAction {
    /** Along the row by [delta] cards. */
    data class Move(val delta: Int) : RemoteShelfHomeAction

    /** Focus card [index] (from 0) of the row. */
    data class Pick(val index: Int) : RemoteShelfHomeAction

    /** To the row [delta] rows away. */
    data class Row(val delta: Int) : RemoteShelfHomeAction
    data object Play : RemoteShelfHomeAction
    data object Info : RemoteShelfHomeAction
    data object Shelves : RemoteShelfHomeAction
    data object View : RemoteShelfHomeAction
    data class PickShelf(val tabId: String, val shelfKey: String) : RemoteShelfHomeAction

    companion object {
        /**
         * `move <delta>`, `pick <index>`, `row <delta>`, `play`, `info`, `shelves`, `view` or `shelf <tabId> <shelfKey>`,
         * already URL-decoded.
         */
        fun parse(words: List<String>): RemoteShelfHomeAction? {
            fun number(at: Int, range: IntRange): Int? = words.getOrNull(at)?.toIntOrNull()?.takeIf { it in range }
            return when (words.getOrNull(0)?.lowercase()) {
                "move" -> number(1, -999..999)?.takeIf { it != 0 }?.let(::Move)
                "pick" -> number(1, 0..9999)?.let(::Pick)
                "row" -> number(1, -999..999)?.takeIf { it != 0 }?.let(::Row)
                "play" -> Play
                "info" -> Info
                "shelves" -> Shelves
                "view" -> View
                "shelf" -> {
                    val tab = words.getOrNull(1)?.takeIf { it.isNotBlank() && it != "-" && it.length <= 200 }
                    val key = words.getOrNull(2)?.takeIf { it.isNotBlank() && it != "-" && it.length <= 400 }
                    if (tab != null && key != null) PickShelf(tab, key) else null
                }
                else -> null
            }
        }
    }
}

object RemoteShelfHome {
    private val _current = MutableStateFlow<RemoteShelfHomeScreen?>(null)

    /** The shelf home on screen; null while it is not showing. */
    val current: StateFlow<RemoteShelfHomeScreen?> = _current.asStateFlow()

    private val _actions = MutableSharedFlow<RemoteShelfHomeAction>(extraBufferCapacity = 16)

    /** The phone's taps, for the shelf home to collect while it shows. */
    val actions: SharedFlow<RemoteShelfHomeAction> = _actions.asSharedFlow()

    /** What the shelf home shows now; null when it leaves the screen. */
    fun publish(screen: RemoteShelfHomeScreen?) {
        _current.value = screen
    }

    /** Hands a phone's tap to the shelf home; false when it is not on screen. */
    fun act(action: RemoteShelfHomeAction): Boolean {
        if (_current.value == null) return false
        return _actions.tryEmit(action)
    }
}

/** Builds the published screen from what ShelfHome already holds; pure, so it is tested. */
internal object RemoteShelfHomeRules {
    /** How many posters the phone gets around the focused one. */
    const val WINDOW = 20

    /** The first card of the window around [index] in a row of [count]. */
    fun windowStart(index: Int, count: Int, size: Int = WINDOW): Int =
        (index - size / 2).coerceAtMost(count - size).coerceAtLeast(0)

    /** "2007 · DRAMA · ENDS 23:42"; a Continue Watching card: "S4 E1 · 1H 16M LEFT · ENDS 23:42". */
    fun facts(card: ShelfCard?, nowMinute: Int, clock: (Int) -> String): String {
        if (card == null) return ""
        val head = when (card) {
            is ShelfCard.Meta -> listOfNotNull(
                ShelfFrameRules.year(card),
                card.item.genres.map { it.trim() }.firstOrNull { it.isNotEmpty() },
            ).joinToString(" · ")
            is ShelfCard.Progress -> ShelfFrameRules.facts(card)
            is ShelfCard.Folder -> ""
        }
        val ends = ShelfFrameRules.ends(card, nowMinute, clock)?.let { "ends ${it.second}" }
        return listOf(head, ends.orEmpty()).filter { it.isNotEmpty() }.joinToString(" · ").uppercase()
    }

    /** Every row of [tabs] (content tabs only), in the Shelves overlay's groups and order. */
    fun shelves(tabs: List<ShelfTab>): List<RemoteShelfRow> = tabs.flatMap { tab ->
        ShelfGroupRules.group(tab).flatMap { group ->
            group.entries.flatMap { entry ->
                when (entry) {
                    is ShelfGroupEntry.Row -> listOf(RemoteShelfRow(tab.id, tab.title, group.label, null, entry.shelf.key, entry.shelf.title))
                    is ShelfGroupEntry.Bucket -> entry.shelves.map {
                        RemoteShelfRow(tab.id, tab.title, group.label, entry.label, it.key, it.title)
                    }
                }
            }
        }
    }

    fun screen(
        mode: String,
        tabs: List<ShelfTab>,
        tab: ShelfTab?,
        rowIndex: Int,
        cards: List<ShelfCard>,
        cardIndex: Int,
        viewMode: ShelfViewMode,
        artUrl: String?,
        nowMinute: Int,
        clock: (Int) -> String = { clockLabel(it) },
    ): RemoteShelfHomeScreen {
        val shelves = tab?.shelves.orEmpty()
        val shelf = shelves.getOrNull(rowIndex)
        val index = if (cards.isEmpty()) 0 else cardIndex.coerceIn(0, cards.lastIndex)
        val from = windowStart(index, cards.size)
        val focused = cards.getOrNull(index)
        return RemoteShelfHomeScreen(
            mode = mode,
            tabId = tab?.id ?: ShelfHomeRules.HOME_TAB,
            tabTitle = tab?.title.orEmpty(),
            shelfKey = shelf?.key,
            rowTitle = shelf?.title.orEmpty(),
            row = rowIndex,
            rows = shelves.size,
            index = index,
            count = cards.size,
            from = from,
            cards = cards.drop(from).take(WINDOW).map { RemoteShelfHomeCard(it.title(), it.posterUrl()) },
            title = focused?.title().orEmpty(),
            facts = facts(focused, nowMinute, clock),
            art = artUrl,
            view = viewMode.name,
            shelves = shelves(tabs),
        )
    }
}
