package com.nuvio.app.features.home.shelves

import com.nuvio.app.features.collection.Collection
import com.nuvio.app.features.collection.CollectionFolder
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.watchprogress.ContinueWatchingItem

/*
 * fork:shelf-home - the cards of a shelf and the words the fan-art-first frame shows for them
 * (mockups V2Browse, V2Logos, V2Idle). ShelfHome.kt draws one row at a time over the focused card's
 * fan-art; its two row styles are Posters (ShelfPosterRow.kt) and Logos (ShelfLogoStripView.kt), and
 * the Shelves and Info overlays get the same cards. See design-notes/home-shelves.md.
 */

/** One card on a shelf, whatever the shelf's source. */
internal sealed interface ShelfCard {
    data class Meta(val item: MetaPreview) : ShelfCard
    data class Progress(val item: ContinueWatchingItem) : ShelfCard
    data class Folder(val collectionId: String, val folder: CollectionFolder) : ShelfCard
}

internal fun ShelfCard.title(): String = when (this) {
    is ShelfCard.Meta -> item.name
    is ShelfCard.Progress -> item.title
    is ShelfCard.Folder -> folder.title
}

internal fun ShelfCard.posterUrl(): String? = when (this) {
    is ShelfCard.Meta -> item.poster ?: item.posterFallback
    is ShelfCard.Progress -> item.poster ?: item.imageUrl
    is ShelfCard.Folder -> folder.coverImageUrl
}?.takeIf { it.isNotBlank() }

/** Any wide picture for the card, falling back to its poster (kept for callers that want something). */
internal fun ShelfCard.backdropUrl(): String? = when (this) {
    is ShelfCard.Meta -> item.banner ?: item.landscapePoster ?: item.poster
    is ShelfCard.Progress -> item.background ?: item.customLandscape ?: item.imageUrl ?: item.poster
    is ShelfCard.Folder -> folder.heroBackdropUrl ?: folder.coverImageUrl
}?.takeIf { it.isNotBlank() }

/**
 * The card's fan-art only: a real backdrop, never a poster or art with the title drawn into it. Null
 * when it has none; the frame then shows the poster enlarged and blurred.
 */
internal fun ShelfCard.fanartUrl(): String? = when (this) {
    is ShelfCard.Meta -> item.banner
    is ShelfCard.Progress -> item.background ?: item.episodeThumbnail
    is ShelfCard.Folder -> folder.heroBackdropUrl
}?.takeIf { it.isNotBlank() }

/** A key that is the same for the same title, for remembering and comparing cards. */
internal fun ShelfCard.stableId(): String = when (this) {
    is ShelfCard.Meta -> "m:${item.type}:${item.id}"
    is ShelfCard.Progress -> "p:${item.parentMetaId}:${item.videoId}"
    is ShelfCard.Folder -> "f:$collectionId/${folder.id}"
}

/** How a shelf's row is drawn: a row of posters, or a row of title logos (clearlogos). */
enum class ShelfViewMode { Posters, Logos }

/**
 * A shelf's cards from what the home has loaded. [folderItems] are a folder shelf's loaded titles
 * (null or empty: the folder's own card stands in); [mixItems] a built mix's titles.
 */
internal fun shelfCardsFor(
    shelf: Shelf?,
    sections: Map<String, HomeCatalogSection>,
    collections: Map<String, Collection>,
    continueWatching: List<ContinueWatchingItem>,
    upNext: List<ContinueWatchingItem>,
    upcoming: List<ContinueWatchingItem>,
    folderItems: List<MetaPreview>? = null,
    mixItems: List<MetaPreview>? = null,
): List<ShelfCard> = when (val source = shelf?.source) {
    null -> emptyList()
    is ShelfSource.Catalog -> sections[source.sectionKey]?.items.orEmpty().map(ShelfCard::Meta)
    ShelfSource.ContinueWatching -> continueWatching.map(ShelfCard::Progress)
    ShelfSource.UpNext -> upNext.map(ShelfCard::Progress)
    ShelfSource.Upcoming -> upcoming.map(ShelfCard::Progress)
    is ShelfSource.CollectionFolders ->
        collections[ShelfHomeRules.collectionTabId(source.collectionId)]?.folders.orEmpty()
            .map { ShelfCard.Folder(source.collectionId, it) }
    is ShelfSource.Mix -> mixItems.orEmpty().map(ShelfCard::Meta)
    // The folder card stands in while the titles load, or if every source fails.
    is ShelfSource.CollectionFolder ->
        folderItems?.takeIf { it.isNotEmpty() }?.map(ShelfCard::Meta)
            ?: collections[ShelfHomeRules.collectionTabId(source.collectionId)]?.folders.orEmpty()
                .filter { it.id == source.folderId }
                .map { ShelfCard.Folder(source.collectionId, it) }
}

/** The frame's words for a card, pure so they are tested (ShelfFrameRulesTest). */
internal object ShelfFrameRules {
    private val yearPattern = Regex("""\b(\d{4})\b""")

    /** Cards from the loaded end at which the focused row asks for its next page. */
    const val PAGE_AHEAD = 6

    /** The first four-digit year in a release line ("2019", "2019-2023", "2019-05-01"), or null. */
    fun year(text: String?): String? = text?.let { yearPattern.find(it)?.groupValues?.get(1) }

    fun year(card: ShelfCard): String? = when (card) {
        is ShelfCard.Meta -> year(card.item.releaseInfo) ?: year(card.item.rawReleaseDate)
        is ShelfCard.Progress -> year(card.item.released)
        is ShelfCard.Folder -> null
    }

    private fun episodeOf(item: ContinueWatchingItem): String? =
        if (item.seasonNumber != null && item.episodeNumber != null) "S${item.seasonNumber} E${item.episodeNumber}" else null

    /** Minutes left of a part-watched item; the whole length of one not started (Up next). */
    private fun leftOf(item: ContinueWatchingItem): Int? = ShelfTime.minutesLeft(item.durationMs, item.resumePositionMs)

    /**
     * The tracked line under the title, in capitals:
     *  - a film or show: "1999 · ANIMATION / FAMILY · 1H 26M · IMDB 8.1" (year, up to two genres, length, rating; any may be missing);
     *  - a Continue Watching card: "S4 E1 · USS CALLISTER · 1H 16M LEFT";
     *  - a folder: nothing.
     */
    fun facts(card: ShelfCard): String = when (card) {
        is ShelfCard.Meta -> {
            val item = card.item
            listOfNotNull(
                year(card),
                item.genres.map { it.trim() }.filter { it.isNotEmpty() }.take(2).takeIf { it.isNotEmpty() }?.joinToString(" / "),
                ShelfTime.runtimeMinutes(item.runtime)?.let { ShelfTime.length(it) },
                item.imdbRating?.trim()?.takeIf { it.isNotEmpty() }?.let { "IMDb $it" },
            ).joinToString(" · ").uppercase()
        }
        is ShelfCard.Progress -> {
            val item = card.item
            val episode = episodeOf(item)
            val left = leftOf(item)?.let { ShelfTime.length(it) + if (item.resumePositionMs > 0L) " left" else "" }
            listOfNotNull(
                episode,
                item.episodeTitle?.trim()?.takeIf { it.isNotEmpty() } ?: item.subtitle.trim().takeIf { episode == null && it.isNotEmpty() },
                left,
            ).joinToString(" · ").uppercase()
        }
        is ShelfCard.Folder -> ""
    }

    /**
     * The "ends" line as (lead, time): ("Starts now, ends", 21:23) for a title with a length,
     * ("Finish by", 21:23) for a part-watched one; null without a length (or for a folder).
     * [clock] formats a minute of the day (the screen passes Tonight's clockLabel).
     */
    fun ends(card: ShelfCard, nowMinute: Int, clock: (Int) -> String): Pair<String, String>? = when (card) {
        is ShelfCard.Meta -> ShelfTime.runtimeMinutes(card.item.runtime)?.let {
            "Starts now, ends" to clock(ShelfTime.endsAt(nowMinute, it))
        }
        is ShelfCard.Progress -> leftOf(card.item)?.let {
            (if (card.item.resumePositionMs > 0L) "Finish by" else "Starts now, ends") to clock(ShelfTime.endsAt(nowMinute, it))
        }
        is ShelfCard.Folder -> null
    }

    /** The one-line description, or null. */
    fun description(card: ShelfCard): String? = when (card) {
        is ShelfCard.Meta -> card.item.description
        is ShelfCard.Progress -> card.item.pauseDescription
        is ShelfCard.Folder -> null
    }?.trim()?.takeIf { it.isNotEmpty() }

    /** The gallery's small line: "2007 · 2H 38M"; a Continue Watching card: "S4 E1 · 1H 16M LEFT". */
    fun yearLength(card: ShelfCard): String = when (card) {
        is ShelfCard.Meta -> listOfNotNull(
            year(card),
            ShelfTime.runtimeMinutes(card.item.runtime)?.let { ShelfTime.length(it) },
        ).joinToString(" · ").uppercase()
        is ShelfCard.Progress -> listOfNotNull(
            episodeOf(card.item) ?: year(card),
            leftOf(card.item)?.let { ShelfTime.length(it) + if (card.item.resumePositionMs > 0L) " left" else "" },
        ).joinToString(" · ").uppercase()
        is ShelfCard.Folder -> ""
    }

    /** "9 OF 50 · ROW 7 OF 85"; without cards just the row. Indices are 0-based. */
    fun position(index: Int, count: Int, row: Int, rows: Int): String {
        val rowPart = "ROW ${row + 1} OF $rows"
        return if (count <= 0) rowPart else "${index.coerceIn(0, count - 1) + 1} OF $count · $rowPart"
    }

    /** The Logos row's right-hand count: "1 OF 50", empty without cards. */
    fun count(index: Int, count: Int): String =
        if (count <= 0) "" else "${index.coerceIn(0, count - 1) + 1} OF $count"

    /** [index] moved by [delta], kept inside the row (0 for an empty row). */
    fun move(index: Int, delta: Int, count: Int): Int = if (count <= 0) 0 else (index + delta).coerceIn(0, count - 1)

    /** The gallery's next card: one on, back to the first after the last loaded one. */
    fun galleryNext(index: Int, count: Int): Int = if (count <= 0) 0 else (index + 1).let { if (it >= count) 0 else it }

    /** Whether the row should ask for its next page with [index] in focus. */
    fun nearEnd(index: Int, loaded: Int): Boolean = loaded > 0 && index >= loaded - PAGE_AHEAD
}
