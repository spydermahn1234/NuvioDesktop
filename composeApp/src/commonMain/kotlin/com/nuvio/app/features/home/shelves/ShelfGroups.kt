// fork:shelf-home
package com.nuvio.app.features.home.shelves

/*
 * fork:shelf-home - the Shelves overlay's four groups (mockup V2Shelves): every shelf of a tab sorted into
 * YOURS, FOR YOU, CHARTS and CATALOGS, in that order. The first three keep the tab's own order; Catalogs
 * are A-Z and, when there are many, folded into seven letter buckets. Pure, so it is tested on its own.
 */

internal data class ShelfGroup(
    /** "YOURS", "FOR YOU", "CHARTS" or "CATALOGS". */
    val label: String,
    /** How many shelves the group holds (a bucket counts its shelves, not itself). */
    val total: Int,
    val entries: List<ShelfGroupEntry>,
)

internal sealed interface ShelfGroupEntry {
    data class Row(val shelf: Shelf) : ShelfGroupEntry

    /** A letter range of catalogs, such as "A – C"; opening it lists [shelves]. */
    data class Bucket(val label: String, val shelves: List<Shelf>) : ShelfGroupEntry
}

internal object ShelfGroupRules {
    const val YOURS = "YOURS"
    const val FOR_YOU = "FOR YOU"
    const val CHARTS = "CHARTS"
    const val CATALOGS = "CATALOGS"

    /** More catalogs than this and they are bucketed by letter. */
    const val BUCKET_ABOVE = 8

    /** The mockup's seven buckets, by the first letter of the title; digits and symbols go to the first. */
    val BUCKETS: List<Pair<String, CharRange>> = listOf(
        "A – C" to 'a'..'c',
        "D – F" to 'd'..'f',
        "G – K" to 'g'..'k',
        "L – N" to 'l'..'n',
        "O – R" to 'o'..'r',
        "S – T" to 's'..'t',
        "U – Z" to 'u'..'z',
    )

    private val progressKeys = setOf(
        ShelfHomeRules.CONTINUE_WATCHING_KEY,
        ShelfHomeRules.UP_NEXT_KEY,
        ShelfHomeRules.UPCOMING_KEY,
    )

    private val yoursPhrases = listOf("live tv", "watchlist", "watch list", "library", "my list")
    private val forYouPhrases = listOf("for you", "because", "recommended", "ai", "random", "picked")
    private val chartPhrases = listOf(
        "trending", "popular", "top rated", "best of", "most watched", "new releases", "this week",
    )
    private val topN = Regex("""\btop\s*\d+\b""")

    fun group(tab: ShelfTab): List<ShelfGroup> {
        val yours = mutableListOf<Shelf>()
        val forYou = mutableListOf<Shelf>()
        val charts = mutableListOf<Shelf>()
        val catalogs = mutableListOf<Shelf>()
        for (shelf in tab.shelves) {
            when {
                isYours(shelf) -> yours += shelf
                isForYou(shelf.title) -> forYou += shelf
                isChart(shelf.title) -> charts += shelf
                else -> catalogs += shelf
            }
        }
        val sortedCatalogs = catalogs.sortedWith(compareBy<Shelf>({ it.title.lowercase() }, { it.title }))
        return buildList {
            if (yours.isNotEmpty()) add(ShelfGroup(YOURS, yours.size, yours.map { ShelfGroupEntry.Row(it) }))
            if (forYou.isNotEmpty()) add(ShelfGroup(FOR_YOU, forYou.size, forYou.map { ShelfGroupEntry.Row(it) }))
            if (charts.isNotEmpty()) add(ShelfGroup(CHARTS, charts.size, charts.map { ShelfGroupEntry.Row(it) }))
            if (sortedCatalogs.isNotEmpty()) add(ShelfGroup(CATALOGS, sortedCatalogs.size, catalogEntries(sortedCatalogs)))
        }
    }

    /** Which bucket label a title falls in. */
    fun bucketOf(title: String): String {
        val first = normalise(title).firstOrNull() ?: return BUCKETS.first().first
        return BUCKETS.firstOrNull { first in it.second }?.first ?: BUCKETS.first().first
    }

    private fun catalogEntries(sorted: List<Shelf>): List<ShelfGroupEntry> {
        if (sorted.size <= BUCKET_ABOVE) return sorted.map { ShelfGroupEntry.Row(it) }
        val byBucket = sorted.groupBy { bucketOf(it.title) }
        return BUCKETS.mapNotNull { (label, _) ->
            byBucket[label]?.takeIf { it.isNotEmpty() }?.let { ShelfGroupEntry.Bucket(label, it) }
        }
    }

    private fun isYours(shelf: Shelf): Boolean = when {
        shelf.key in progressKeys -> true
        shelf.source is ShelfSource.ContinueWatching ||
            shelf.source is ShelfSource.UpNext ||
            shelf.source is ShelfSource.Upcoming -> true
        shelf.source is ShelfSource.Mix -> true
        shelf.source is ShelfSource.Evening -> true
        shelf.source is ShelfSource.CollectionFolders -> true
        else -> hasPhrase(shelf.title, yoursPhrases)
    }

    private fun isForYou(title: String): Boolean = hasPhrase(title, forYouPhrases)

    private fun isChart(title: String): Boolean =
        topN.containsMatchIn(normalise(title)) || hasPhrase(title, chartPhrases)

    private fun hasPhrase(title: String, phrases: List<String>): Boolean {
        val padded = " ${normalise(title)} "
        return phrases.any { " $it " in padded }
    }

    /** As ShelfPresentationRules does: lower case, anything not a letter or digit as one space. */
    private fun normalise(text: String): String =
        text.lowercase().map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("")
            .split(' ').filter { it.isNotEmpty() }.joinToString(" ")
}
