// fork:shelf-home
package com.nuvio.app.features.home.shelves

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * fork:shelf-home - phase 5: which view each shelf is shown in (plan in design-notes/home-shelves.md).
 *
 * [ShelfPresentationRules] picks a default by the kind of row; [ShelfPresentationRepository] keeps the
 * user's "View as..." choices, per profile, in the fork's own store (`nuvio_shelf_home`, key "views"),
 * keyed by [Shelf.key] so a catalog that moves or is renamed keeps its view.
 */

object ShelfPresentationRules {
    /** The views the shelf home draws: Posters and Logos (Strip, Ledger, Spotlight and Wall went on Nov 26). */
    fun available(): List<ShelfViewMode> = ShelfViewMode.entries.toList()

    private val topN = Regex("""\btop\s*\d+\b""")
    private val rankedPhrases = listOf("best of", "best this week", "most watched")

    /** Streaming services and networks, matched as whole words (so "max" does not match "maxim"). */
    private val networkWords = listOf(
        "netflix", "disney", "prime", "apple tv", "appletv", "hbo", "max", "hulu", "paramount",
        "peacock", "now tv", "bbc", "itv", "channel 4",
    )

    /** Distinctive enough to find inside a run-together key word such as "netflixmovies" or "disneyplus". */
    private val networkFragments = listOf("netflix", "disney", "hulu", "hbo", "paramount", "peacock", "appletv")

    /** Streaming service and network rows read best as logos; everything else is a row of posters. */
    fun defaultView(shelfKey: String, title: String): ShelfViewMode = when {
        // Folders of a collection stay a row of posters, whatever they are called.
        '/' in shelfKey -> ShelfViewMode.Posters
        isNetwork(shelfKey) || isNetwork(title) -> ShelfViewMode.Logos
        else -> ShelfViewMode.Posters
    }

    /** The other view, for V. */
    fun toggled(mode: ShelfViewMode): ShelfViewMode =
        if (mode == ShelfViewMode.Logos) ShelfViewMode.Posters else ShelfViewMode.Logos

    fun isRanked(title: String): Boolean {
        val words = normalise(title)
        if (topN.containsMatchIn(words)) return true
        val padded = " $words "
        return rankedPhrases.any { " $it " in padded }
    }

    internal fun isNetwork(text: String): Boolean {
        val words = normalise(text)
        val padded = " $words "
        if (networkWords.any { " $it " in padded }) return true
        val joined = words.replace(" ", "")
        return networkFragments.any { it in joined }
    }

    /** Lower case, anything that is not a letter or digit as one space ("Most-Watched" -> "most watched"). */
    private fun normalise(text: String): String =
        text.lowercase().map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("")
            .split(' ').filter { it.isNotEmpty() }.joinToString(" ")
}

/**
 * The stored format, one override per line: `ModeName<TAB>escapedKey`. Mode names never hold a tab or a
 * newline; in the key a backslash, tab or newline is escaped as `\\`, `\t`, `\n`, so any key
 * (`manifest:type:catalog`, `collection_x/folder`) round-trips. Lines that do not parse are skipped,
 * as are mode names this build does not know. The views removed on Nov 26 (Strip, Ledger, Spotlight,
 * Wall) read back as Posters, so a shelf someone had set to one of them stays a row of pictures.
 */
internal object ShelfPresentationCodec {
    fun encode(views: Map<String, ShelfViewMode>): String =
        views.entries
            .sortedBy { it.key }
            .joinToString("\n") { (key, mode) -> "${mode.name}\t${escape(key)}" }

    fun decode(text: String?): Map<String, ShelfViewMode> {
        if (text.isNullOrEmpty()) return emptyMap()
        val out = LinkedHashMap<String, ShelfViewMode>()
        for (line in text.split('\n')) {
            val tab = line.indexOf('\t')
            if (tab <= 0) continue
            val mode = modeOf(line.substring(0, tab)) ?: continue
            val key = unescape(line.substring(tab + 1)) ?: continue
            if (key.isEmpty()) continue
            out[key] = mode
        }
        return out
    }

    /** The views that were removed, and what they read back as. */
    private val retired = setOf("Strip", "Ledger", "Spotlight", "Wall")

    fun modeOf(name: String): ShelfViewMode? =
        ShelfViewMode.entries.firstOrNull { it.name == name }
            ?: ShelfViewMode.Posters.takeIf { name in retired }

    fun escape(key: String): String = buildString(key.length) {
        for (c in key) {
            when (c) {
                '\\' -> append("\\\\")
                '\t' -> append("\\t")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(c)
            }
        }
    }

    /** Null when the text is not a valid escape (a lone or unknown backslash). */
    fun unescape(text: String): String? {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c != '\\') {
                sb.append(c)
                i++
                continue
            }
            if (i + 1 >= text.length) return null
            when (text[i + 1]) {
                '\\' -> sb.append('\\')
                't' -> sb.append('\t')
                'n' -> sb.append('\n')
                'r' -> sb.append('\r')
                else -> return null
            }
            i += 2
        }
        return sb.toString()
    }
}

object ShelfPresentationRepository {
    private const val STORE_KEY = "views"

    private val _overrides = MutableStateFlow<Map<String, ShelfViewMode>>(emptyMap())

    /** The user's choices by [Shelf.key]; a shelf not in it uses [ShelfPresentationRules.defaultView]. */
    val overrides: StateFlow<Map<String, ShelfViewMode>> = _overrides.asStateFlow()
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        _overrides.value = runCatching { ShelfPresentationCodec.decode(ShelfHomeStorage.getString(STORE_KEY)) }
            .getOrDefault(emptyMap())
    }

    /** Re-read after a profile switch (the store is profile-scoped). */
    fun reload() {
        loaded = false
        ensureLoaded()
    }

    /** The shelf's view: the user's choice if it is one that is built, else the default for its kind. */
    fun viewFor(shelfKey: String, title: String): ShelfViewMode {
        ensureLoaded()
        return resolve(_overrides.value, shelfKey, title)
    }

    /** Whether the shelf is on its default (no choice of the user's that is still available). */
    fun isDefault(shelfKey: String): Boolean {
        ensureLoaded()
        return _overrides.value[shelfKey]?.takeIf { it in ShelfPresentationRules.available() } == null
    }

    /** Pure lookup, for callers that already collect [overrides]. */
    fun resolve(overrides: Map<String, ShelfViewMode>, shelfKey: String, title: String): ShelfViewMode =
        overrides[shelfKey]?.takeIf { it in ShelfPresentationRules.available() }
            ?: ShelfPresentationRules.defaultView(shelfKey, title)

    /** Choose [mode] for the shelf; null goes back to the default. */
    fun set(shelfKey: String, mode: ShelfViewMode?) {
        ensureLoaded()
        val current = _overrides.value
        if (current[shelfKey] == mode) return
        val next = if (mode == null) current - shelfKey else current + (shelfKey to mode)
        _overrides.value = next
        runCatching { ShelfHomeStorage.putString(STORE_KEY, ShelfPresentationCodec.encode(next)) }
    }
}
