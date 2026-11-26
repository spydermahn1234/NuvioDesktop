// fork:shelf-home
package com.nuvio.app.features.home.shelves

import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.tonight.tonightMinutesUntilBed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * fork:shelf-home - phase 10: mixed shelves (plan in design-notes/home-shelves.md), such as Nick's
 * "The Hang": a shelf the user builds from several of the home's rows, merged round-robin,
 * de-duplicated and filtered (runtime, unwatched, finishes before bedtime, genres).
 *
 * [ShelfMixRules] is pure and tested (ShelfMixRulesTest). [ShelfMixRepository] keeps the mixes, per
 * profile, in the shelf home's own store under one key ("mixes"). A mix's shelf key is "mix:<id>",
 * so its per-shelf view choice (ShelfPresentationRepository) is kept like any other shelf's.
 */

data class ShelfMix(
    val id: String,
    val title: String,
    /** Home section keys (HomeCatalogSection.key), merged in this order. */
    val sourceKeys: List<String>,
    /** Titles with a known runtime above this are left out; null = no limit. */
    val maxRuntime: Int? = null,
    val hideWatched: Boolean = false,
    /** Titles with a known runtime that would end after Tonight's bedtime (starting now) are left out. */
    val fitsBeforeBed: Boolean = false,
    /** Match any (case-insensitive); empty = every genre. */
    val genres: List<String> = emptyList(),
)

object ShelfMixRules {
    const val KEY_PREFIX = "mix:"

    /** A mix asks its sources for more pages until it shows this many titles... */
    const val TARGET = 24

    /** ...but never pages one source more than this many times on its own (the user scrolling may ask for more). */
    const val MAX_AUTO_PAGES = 5

    /** Minutes past bedtime a title may still end (as Tonight's fitsBedtime). */
    const val BED_GRACE_MINUTES = 10

    /** The runtime limits the editor offers; null is "None". */
    val RUNTIME_CHOICES: List<Int?> = listOf(null, 90, 100, 120)

    fun shelfKey(mixId: String): String = "$KEY_PREFIX$mixId"

    fun idOf(shelfKey: String): String? = shelfKey.takeIf { it.startsWith(KEY_PREFIX) }?.removePrefix(KEY_PREFIX)?.takeIf { it.isNotEmpty() }

    /** "m1", "m2", ...: the first not in [taken]. */
    fun newId(taken: Collection<String>): String {
        var n = 1
        while ("m$n" in taken) n++
        return "m$n"
    }

    /** Trimmed title, sources and genres without blanks or repeats. */
    fun tidy(mix: ShelfMix): ShelfMix = mix.copy(
        title = mix.title.trim(),
        sourceKeys = mix.sourceKeys.filter { it.isNotBlank() }.distinct(),
        maxRuntime = mix.maxRuntime?.takeIf { it > 0 },
        genres = mix.genres.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() },
    )

    /** Whether the editor may save it: a name and at least one row. */
    fun canSave(mix: ShelfMix): Boolean = mix.title.isNotBlank() && mix.sourceKeys.any { it.isNotBlank() }

    /**
     * The mix's titles: its sources' loaded items round-robin (first item of each source, then the
     * second of each, ...), the first copy of each title (by type:id) kept, then filtered:
     *  - [ShelfMix.maxRuntime]: a known runtime above it is out; an unknown runtime passes.
     *  - [ShelfMix.hideWatched]: [isWatched] titles are out.
     *  - [ShelfMix.fitsBeforeBed]: a known runtime that, starting at [nowMinute], would end more than
     *    [BED_GRACE_MINUTES] after [bedMinute] is out (bedtime read as Tonight reads it, so a bedtime
     *    after midnight is tonight's); an unknown runtime passes. A series' runtime is one episode's.
     *  - [ShelfMix.genres]: the title must carry one of them; a title with no genres at all is out.
     * Sources with no loaded section are skipped.
     */
    fun build(
        mix: ShelfMix,
        sections: Map<String, HomeCatalogSection>,
        isWatched: (MetaPreview) -> Boolean,
        nowMinute: Int,
        bedMinute: Int,
    ): List<MetaPreview> {
        val lists = mix.sourceKeys.distinct().mapNotNull { sections[it]?.items }
        val untilBed = tonightMinutesUntilBed(nowMinute, bedMinute)
        val wanted = mix.genres.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        val out = mutableListOf<MetaPreview>()
        val seen = HashSet<String>()
        val longest = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until longest) {
            for (list in lists) {
                val item = list.getOrNull(i) ?: continue
                if (!seen.add(item.stableKey())) continue
                if (passes(item, mix, wanted, untilBed, isWatched)) out += item
            }
        }
        return out
    }

    private fun passes(
        item: MetaPreview,
        mix: ShelfMix,
        wanted: Set<String>,
        untilBed: Int,
        isWatched: (MetaPreview) -> Boolean,
    ): Boolean {
        val minutes = ShelfTime.runtimeMinutes(item.runtime)
        val max = mix.maxRuntime
        if (max != null && minutes != null && minutes > max) return false
        if (mix.fitsBeforeBed && minutes != null && minutes > untilBed + BED_GRACE_MINUTES) return false
        if (wanted.isNotEmpty() && item.genres.none { it.trim().lowercase() in wanted }) return false
        if (mix.hideWatched && isWatched(item)) return false
        return true
    }

    /**
     * The sources worth asking for another page: while [built] has fewer than [target] titles, each
     * of the mix's sources whose section has a next page, is not already loading one, and has been
     * asked fewer than [maxPages] times by this mix ([pagesAsked], kept by the caller per mix and
     * source key, counts the asks). Empty once the shelf has enough.
     */
    fun needsMore(
        mix: ShelfMix,
        built: List<MetaPreview>,
        sections: Map<String, HomeCatalogSection>,
        pagesAsked: Map<String, Int> = emptyMap(),
        target: Int = TARGET,
        maxPages: Int = MAX_AUTO_PAGES,
    ): List<String> {
        if (built.size >= target) return emptyList()
        return mix.sourceKeys.distinct().filter { key ->
            val section = sections[key] ?: return@filter false
            section.nextSkip != null && !section.isLoadingMore && (pagesAsked[key] ?: 0) < maxPages
        }
    }
}

/**
 * The stored format, one mix per line, fields split by tabs:
 * `mix1<TAB>id<TAB>title<TAB>sources<TAB>maxRuntime<TAB>hideWatched<TAB>fitsBeforeBed<TAB>genres`.
 * Text fields are escaped with [ShelfPresentationCodec.escape]; a list is its items, each escaped,
 * joined by newlines, then escaped again as one field. maxRuntime is empty for none; the switches are
 * 1 or 0. Lines that do not parse (or another version's) are skipped; extra trailing fields are ignored.
 */
internal object ShelfMixCodec {
    private const val VERSION = "mix1"

    fun encode(mixes: List<ShelfMix>): String = mixes.joinToString("\n") { mix ->
        listOf(
            VERSION,
            ShelfPresentationCodec.escape(mix.id),
            ShelfPresentationCodec.escape(mix.title),
            encodeList(mix.sourceKeys),
            mix.maxRuntime?.toString().orEmpty(),
            if (mix.hideWatched) "1" else "0",
            if (mix.fitsBeforeBed) "1" else "0",
            encodeList(mix.genres),
        ).joinToString("\t")
    }

    fun decode(text: String?): List<ShelfMix> {
        if (text.isNullOrEmpty()) return emptyList()
        val out = mutableListOf<ShelfMix>()
        val ids = HashSet<String>()
        for (line in text.split('\n')) {
            val f = line.split('\t')
            if (f.size < 8 || f[0] != VERSION) continue
            val id = ShelfPresentationCodec.unescape(f[1])?.takeIf { it.isNotEmpty() } ?: continue
            val title = ShelfPresentationCodec.unescape(f[2]) ?: continue
            val sources = decodeList(f[3]) ?: continue
            val max = if (f[4].isEmpty()) null else f[4].toIntOrNull() ?: continue
            val genres = decodeList(f[7]) ?: continue
            if (!ids.add(id)) continue
            out += ShelfMix(
                id = id,
                title = title,
                sourceKeys = sources,
                maxRuntime = max,
                hideWatched = f[5] == "1",
                fitsBeforeBed = f[6] == "1",
                genres = genres,
            )
        }
        return out
    }

    private fun encodeList(items: List<String>): String =
        ShelfPresentationCodec.escape(items.filter { it.isNotEmpty() }.joinToString("\n") { ShelfPresentationCodec.escape(it) })

    private fun decodeList(field: String): List<String>? {
        val joined = ShelfPresentationCodec.unescape(field) ?: return null
        if (joined.isEmpty()) return emptyList()
        return joined.split('\n').map { ShelfPresentationCodec.unescape(it) ?: return null }.filter { it.isNotEmpty() }
    }
}

object ShelfMixRepository {
    private const val STORE_KEY = "mixes"

    private val _mixes = MutableStateFlow<List<ShelfMix>>(emptyList())

    /** The user's mixes, in the order they were made. */
    val mixes: StateFlow<List<ShelfMix>> = _mixes.asStateFlow()
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        _mixes.value = runCatching { ShelfMixCodec.decode(ShelfHomeStorage.getString(STORE_KEY)) }.getOrDefault(emptyList())
    }

    /** Re-read after a profile switch (the store is profile-scoped). */
    fun reload() {
        loaded = false
        ensureLoaded()
    }

    fun mix(id: String): ShelfMix? {
        ensureLoaded()
        return _mixes.value.firstOrNull { it.id == id }
    }

    /** An id no mix has yet. */
    fun newId(): String {
        ensureLoaded()
        return ShelfMixRules.newId(_mixes.value.map { it.id })
    }

    /** Adds [mix], or replaces the one with its id in place. A mix that cannot be saved is ignored. */
    fun save(mix: ShelfMix) {
        ensureLoaded()
        val tidy = ShelfMixRules.tidy(mix)
        if (tidy.id.isEmpty() || !ShelfMixRules.canSave(tidy)) return
        val current = _mixes.value
        val index = current.indexOfFirst { it.id == tidy.id }
        val next = if (index >= 0) current.toMutableList().also { it[index] = tidy } else current + tidy
        if (next == current) return
        write(next)
    }

    fun delete(id: String) {
        ensureLoaded()
        val next = _mixes.value.filterNot { it.id == id }
        if (next.size == _mixes.value.size) return
        write(next)
        // Its view choice goes with it.
        ShelfPresentationRepository.set(ShelfMixRules.shelfKey(id), null)
    }

    private fun write(next: List<ShelfMix>) {
        _mixes.value = next
        runCatching { ShelfHomeStorage.putString(STORE_KEY, ShelfMixCodec.encode(next)) }
    }
}
