// fork:shelf-home
package com.nuvio.app.features.home.shelves

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.PersonDetail
import com.nuvio.app.features.home.HeroCastMember
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.watched.WatchedItem
import com.nuvio.app.features.watched.watchedItemKey
import com.nuvio.app.features.watchprogress.WatchProgressEntry

/*
 * fork:shelf-home - v3 phase 6, cast and collections in Info (mockup V3Cast, plan in
 * design-notes/home-shelves-v3.md). For a film, Info shows the cast as photo circles ("WITH"; initials until
 * the photo loads); focusing one shows "MORE WITH <NAME> · OK OPENS THE ROW" and a few of their other titles,
 * and OK opens that person. A film in a TMDB collection gets the right-hand column "PART 2 OF 3 · <COLLECTION>"
 * with the parts' posters (the current one outlined), watched ticks and "Watched part 1 · 3h 21m for part 3";
 * OK on a part opens its Info. Up from the buttons reaches the parts, then the cast. ShelfInfoOverlay.kt owns
 * the keys and the page; the rules are pure and tested in ShelfInfoPeopleRulesTest.
 */

/** One cast member as Info shows them. */
internal data class ShelfPerson(
    val name: String,
    val role: String? = null,
    val photo: String? = null,
    val tmdbId: Int? = null,
) {
    fun asHeroCastMember(): HeroCastMember = HeroCastMember(name = name, photo = photo, role = role, tmdbId = tmdbId)
}

/** A film's collection: its name (without " Collection"), its parts in release order, and which part this is (-1: unknown). */
internal data class ShelfCollection(
    val name: String,
    val parts: List<MetaPreview>,
    val current: Int,
)

/** Where Info's keys are: the four buttons, the collection's parts, or the cast. */
internal enum class ShelfInfoZone { Buttons, Parts, Cast }

internal object ShelfInfoPeopleRules {
    /** Circles shown (the board: six). */
    const val CAST_MAX = 6

    /** Small posters under "MORE WITH" (the board: seven). */
    const val WORKS_SHOWN = 7

    /** Titles handed to the row OK opens. */
    const val ROW_MAX = 40

    /** Part posters the right-hand column has room for. */
    const val PARTS_VISIBLE = 3

    private val crewRoles = setOf(
        "director", "co-director", "writer", "screenplay", "story", "novel", "author", "characters",
        "producer", "executive producer", "co-producer", "creator", "composer", "original music composer",
        "music", "director of photography", "cinematography", "editor", "casting",
    )

    private fun normal(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

    /**
     * The cast for the circles: the record's cast (TMDB's, with photos), else the card's own, without crew
     * (a role like "Director", or a director / writer / creator listed with no role), each person once.
     */
    fun cast(details: MetaDetails?, cardCast: List<HeroCastMember>): List<ShelfPerson> {
        val fromRecord = details?.cast.orEmpty().map { ShelfPerson(it.name, it.role, it.photo, it.tmdbId) }
        val people = fromRecord.ifEmpty { cardCast.map { ShelfPerson(it.name, it.role, it.photo, it.tmdbId) } }
        val crewNames = (details?.director.orEmpty() + details?.writer.orEmpty() + details?.creator.orEmpty())
            .map(::normal)
            .toSet()
        return people
            .map { it.copy(name = it.name.trim(), role = it.role?.trim()?.takeIf { r -> r.isNotEmpty() }, photo = it.photo?.trim()?.takeIf { p -> p.isNotEmpty() }) }
            .filter { it.name.isNotEmpty() }
            .filterNot { it.role?.lowercase()?.let { r -> r in crewRoles } == true }
            .filterNot { it.role == null && normal(it.name) in crewNames }
            .distinctBy { normal(it.name) }
            .take(CAST_MAX)
    }

    /** "IM" for Ian McKellen, "C" for Cher; letters only. */
    fun initials(name: String): String {
        val words = name.trim().split(Regex("""\s+""")).filter { w -> w.any { it.isLetter() } }
        if (words.isEmpty()) return ""
        val first = words.first().first { it.isLetter() }
        val last = if (words.size > 1) words.last().first { it.isLetter() } else null
        return (if (last != null) "$first$last" else "$first").uppercase()
    }

    fun moreWithLabel(name: String): String = "MORE WITH ${name.trim().uppercase()} · OK OPENS THE ROW"

    /**
     * A person's other titles: films and shows together, the most popular first (their own order where
     * TMDB gives no popularity), each once, without the title Info is showing.
     */
    fun works(detail: PersonDetail?, excludeTmdbId: Int?, excludeName: String?, limit: Int = ROW_MAX): List<MetaPreview> {
        if (detail == null) return emptyList()
        val excludeId = excludeTmdbId?.let { "tmdb:$it" }
        val skipName = excludeName?.let(::normal)?.takeIf { it.isNotEmpty() }
        return (detail.movieCredits + detail.tvCredits)
            .withIndex()
            .sortedWith(compareByDescending<IndexedValue<MetaPreview>> { it.value.popularity ?: -1.0 }.thenBy { it.index })
            .map { it.value }
            .distinctBy { "${it.type}:${it.id}" }
            .filterNot { excludeId != null && it.id == excludeId && it.type == "movie" }
            .filterNot { skipName != null && normal(it.name) == skipName }
            .take(limit)
    }

    private val collectionSuffix = Regex("""\s+collection\s*$""", RegexOption.IGNORE_CASE)

    /** "The Lord of the Rings Collection" reads "The Lord of the Rings". */
    fun collectionTitle(name: String): String = name.trim().replace(collectionSuffix, "").trim().ifEmpty { name.trim() }

    /** The film's collection, when it has one of two parts or more; the current part found by TMDB id, else by name. */
    fun collection(details: MetaDetails?, title: String): ShelfCollection? {
        val name = details?.collectionName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val parts = details.collectionItems.distinctBy { it.id }
        if (parts.size < 2) return null
        val byId = details.tmdbId?.let { id -> parts.indexOfFirst { it.id == "tmdb:$id" } } ?: -1
        val current = if (byId >= 0) byId else {
            val n = normal(title)
            parts.indexOfFirst { n.isNotEmpty() && normal(it.name) == n }
        }
        return ShelfCollection(collectionTitle(name), parts, current)
    }

    /** "PART 2 OF 3 · THE LORD OF THE RINGS", or "3 FILMS · ..." when this film is not found among them. */
    fun collectionHeader(collection: ShelfCollection): String {
        val count = collection.parts.size
        val lead = if (collection.current >= 0) "PART ${collection.current + 1} OF $count" else "$count FILMS"
        return "$lead · ${collection.name}".uppercase()
    }

    fun partLabel(index: Int): String = "PART ${index + 1}"

    /** The first part shown so that [focused] is in view (the current part centred where it can be). */
    fun partsWindowStart(count: Int, focused: Int, visible: Int = PARTS_VISIBLE): Int {
        if (count <= visible) return 0
        return (focused - visible / 2).coerceIn(0, count - visible)
    }

    private val yearPattern = Regex("""\d{4}""")

    private fun year(text: String?): String? = text?.let { yearPattern.find(it)?.value }

    /** A watched film as its name and year (null when unknown), for matching a part whose id differs. */
    data class WatchedTitle(val name: String, val year: String?)

    /** Films you have watched, by name: watched marks, and finished progress records. */
    fun watchedTitles(items: List<WatchedItem>, entries: List<WatchProgressEntry>): List<WatchedTitle> =
        items.filter { it.type == "movie" && it.season == null }.map { WatchedTitle(it.name, year(it.releaseInfo)) } +
            entries.filter { it.parentMetaType == "movie" && it.isEffectivelyCompleted }.map { WatchedTitle(it.title, null) }

    /** The parts you have watched (indices): by a watched mark on the part's own id, else by name and year. */
    fun watchedParts(parts: List<MetaPreview>, watchedKeys: Set<String>, titles: List<WatchedTitle>): Set<Int> {
        val byName = titles.groupBy { normal(it.name) }
        return parts.indices.filter { i ->
            val part = parts[i]
            if (watchedKeys.contains(watchedItemKey("movie", part.id))) return@filter true
            val matches = byName[normal(part.name)] ?: return@filter false
            val partYear = year(part.releaseInfo)
            matches.any { it.year == null || partYear == null || it.year == partYear }
        }.toSet()
    }

    /** The part to watch next: the first unwatched one after this film; null when there is none. */
    fun nextPart(collection: ShelfCollection, watched: Set<Int>): Int? {
        if (collection.current < 0) return null
        return (collection.current + 1 until collection.parts.size).firstOrNull { it !in watched }
    }

    /** "part 1", "parts 1 and 2", "parts 1, 2 and 4". */
    private fun partList(indices: List<Int>): String {
        val numbers = indices.sorted().map { "${it + 1}" }
        return when (numbers.size) {
            0 -> ""
            1 -> "part ${numbers[0]}"
            else -> "parts ${numbers.dropLast(1).joinToString(", ")} and ${numbers.last()}"
        }
    }

    /**
     * The line under the parts: "Watched part 1 · 3h 21m for part 3". The watched parts first ("Watched them
     * all" when every part is), then the next part's length ("1h 2m left of part 3" when started; "Part 3
     * next" while its length is unknown). Empty when there is nothing to say.
     */
    fun collectionLine(
        collection: ShelfCollection,
        watched: Set<Int>,
        nextMinutes: Int?,
        nextLeftMinutes: Int? = null,
    ): String {
        val count = collection.parts.size
        val seen = watched.filter { it in 0 until count }
        val first = when {
            seen.size == count -> "Watched them all"
            seen.isNotEmpty() -> "Watched ${partList(seen)}"
            else -> null
        }
        val next = nextPart(collection, watched)
        val second = next?.let { n ->
            when {
                nextLeftMinutes != null -> "${ShelfTime.length(nextLeftMinutes)} left of part ${n + 1}"
                nextMinutes != null -> "${ShelfTime.length(nextMinutes)} for part ${n + 1}"
                else -> "Part ${n + 1} next"
            }
        }
        return listOfNotNull(first, second).joinToString(" · ")
    }

    /** Up from [zone]: the buttons go to the parts (when there are any), then the cast; the cast stays. */
    fun up(zone: ShelfInfoZone, hasParts: Boolean, hasCast: Boolean): ShelfInfoZone = when (zone) {
        ShelfInfoZone.Buttons -> when {
            hasParts -> ShelfInfoZone.Parts
            hasCast -> ShelfInfoZone.Cast
            else -> ShelfInfoZone.Buttons
        }
        ShelfInfoZone.Parts -> if (hasCast) ShelfInfoZone.Cast else ShelfInfoZone.Parts
        ShelfInfoZone.Cast -> ShelfInfoZone.Cast
    }

    /** Down from [zone]: the way Up came. */
    fun down(zone: ShelfInfoZone, hasParts: Boolean): ShelfInfoZone = when (zone) {
        ShelfInfoZone.Cast -> if (hasParts) ShelfInfoZone.Parts else ShelfInfoZone.Buttons
        ShelfInfoZone.Parts, ShelfInfoZone.Buttons -> ShelfInfoZone.Buttons
    }

    /** Tab: round the zones there are, buttons, parts, cast, then the buttons again. */
    fun tab(zone: ShelfInfoZone, hasParts: Boolean, hasCast: Boolean): ShelfInfoZone {
        val order = listOfNotNull(
            ShelfInfoZone.Buttons,
            ShelfInfoZone.Parts.takeIf { hasParts },
            ShelfInfoZone.Cast.takeIf { hasCast },
        )
        val at = order.indexOf(zone)
        return if (at < 0) ShelfInfoZone.Buttons else order[(at + 1) % order.size]
    }

    /** A zone that has gone (nothing in it) falls back to the buttons. */
    fun settle(zone: ShelfInfoZone, hasParts: Boolean, hasCast: Boolean): ShelfInfoZone = when {
        zone == ShelfInfoZone.Parts && !hasParts -> ShelfInfoZone.Buttons
        zone == ShelfInfoZone.Cast && !hasCast -> ShelfInfoZone.Buttons
        else -> zone
    }

    /** Info's scores in one line under the story: "IMDB 8.1 · CRITICS 95% · ENDS 23:14". */
    fun scoresLine(scores: List<ShelfInfoScore>): String = scores.joinToString(" · ") { "${it.label} ${it.value}" }

    /** The hint bottom-right. */
    fun hint(zone: ShelfInfoZone, hasParts: Boolean, hasCast: Boolean): String = when (zone) {
        ShelfInfoZone.Buttons -> when {
            hasParts && hasCast -> "UP FROM THE BUTTONS: THE SERIES, THEN THE CAST"
            hasParts -> "UP FROM THE BUTTONS: THE SERIES"
            hasCast -> "UP FROM THE BUTTONS: THE CAST"
            else -> "BACK RETURNS TO THE ROW"
        }
        ShelfInfoZone.Parts -> "OK OPENS THAT PART · DOWN: THE BUTTONS"
        ShelfInfoZone.Cast -> "LEFT / RIGHT: THE CAST · DOWN RETURNS"
    }
}

/** One slot of the cast row: 140 wide (the board's 120 plus its 26 gap, less the row's 6), so names fit on one line. */
private val CastSlotWidth = 140.dp

/** The focused circle's ring: 104 + 2 × 4 offset + 2 × 3 ring. Every slot keeps this height, so nothing moves. */
private val CastRingSize = 118.dp

/** Height of the cast row: ring, gap, name, gap, role. */
internal val ShelfCastRowHeight = 180.dp

/**
 * The cast as photo circles: 88 dp at 75%, the focused one 104 dp with a white ring (only while the cast has
 * the keys, [rowFocused]). Initials sit under the photo until it loads.
 */
@Composable
internal fun ShelfCastRow(
    people: List<ShelfPerson>,
    focused: Int,
    rowFocused: Boolean,
    ink: Color,
    placeholder: Color,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val click by rememberUpdatedState(onClick)
    Row(modifier.height(ShelfCastRowHeight), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        people.forEachIndexed { index, person ->
            val on = rowFocused && index == focused
            Column(
                Modifier.width(CastSlotWidth).pointerInput(index) { detectTapGestures { click(index) } },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(CastRingSize), contentAlignment = Alignment.Center) {
                    if (on) Box(Modifier.matchParentSize().border(3.dp, ink, CircleShape))
                    Box(
                        Modifier
                            .size(if (on) 104.dp else 88.dp)
                            .alpha(if (on) 1f else 0.75f)
                            .clip(CircleShape)
                            .background(placeholder),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            ShelfInfoPeopleRules.initials(person.name),
                            fontSize = 24.sp,
                            color = ink.copy(alpha = 0.6f),
                            maxLines = 1,
                        )
                        val photo = person.photo
                        if (photo != null) {
                            NuvioAsyncImage(
                                model = photo,
                                contentDescription = person.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    person.name,
                    fontSize = 17.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Light,
                    color = ink,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    person.role.orEmpty(),
                    fontSize = 14.sp,
                    color = ink.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    minLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * "MORE WITH <NAME> · OK OPENS THE ROW" and up to seven 68 × 102 posters at 55%. [works] null: still loading
 * (seven empty frames); empty: the label only. [name] null hides it all and keeps its place.
 */
@Composable
internal fun ShelfPersonWorks(
    name: String?,
    works: List<MetaPreview>?,
    ink: Color,
    placeholder: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier.alpha(if (name != null) 1f else 0f)) {
        Text(
            name?.let(ShelfInfoPeopleRules::moreWithLabel).orEmpty(),
            fontSize = 13.sp,
            letterSpacing = 3.64.sp, // .28em
            color = ink.copy(alpha = 0.6f),
            maxLines = 1,
            minLines = 1,
        )
        Spacer(Modifier.height(18.dp))
        Row(Modifier.height(102.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (name != null) {
                val shown = works?.take(ShelfInfoPeopleRules.WORKS_SHOWN)
                val slots = shown?.size ?: ShelfInfoPeopleRules.WORKS_SHOWN
                for (i in 0 until slots) {
                    val item = shown?.getOrNull(i)
                    Box(Modifier.size(68.dp, 102.dp).alpha(0.55f).background(placeholder)) {
                        val poster = item?.poster
                        if (poster != null) {
                            NuvioAsyncImage(
                                model = poster,
                                contentDescription = item?.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The collection column (420 wide): the header, three part posters (110 × 165; the current part outlined in
 * the ink at 90%, the focused part, while the parts have the keys, in the accent; the rest at 60%), "PART n"
 * under each with a tick when watched, and the line under them. Every line keeps its place.
 */
@Composable
internal fun ShelfCollectionColumn(
    collection: ShelfCollection,
    watched: Set<Int>,
    focused: Int,
    partsFocused: Boolean,
    line: String,
    ink: Color,
    accent: Color,
    placeholder: Color,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val click by rememberUpdatedState(onClick)
    val start = ShelfInfoPeopleRules.partsWindowStart(collection.parts.size, focused)
    Column(modifier.width(420.dp)) {
        Text(
            ShelfInfoPeopleRules.collectionHeader(collection),
            modifier = Modifier.padding(start = 7.dp),
            fontSize = 13.sp,
            letterSpacing = 3.64.sp,
            color = ink.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(13.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (index in start until (start + ShelfInfoPeopleRules.PARTS_VISIBLE).coerceAtMost(collection.parts.size)) {
                val part = collection.parts[index]
                val isFocused = partsFocused && index == focused
                val isCurrent = index == collection.current
                Column(Modifier.width(124.dp).pointerInput(index) { detectTapGestures { click(index) } }) {
                    // 7 dp round the poster: the outline's 4 dp offset and its 3 dp, kept by every slot.
                    Box(Modifier.size(124.dp, 179.dp), contentAlignment = Alignment.Center) {
                        when {
                            isFocused -> Box(Modifier.matchParentSize().border(3.dp, accent))
                            isCurrent -> Box(Modifier.matchParentSize().border(3.dp, ink.copy(alpha = 0.9f)))
                        }
                        Box(
                            Modifier
                                .size(110.dp, 165.dp)
                                .alpha(if (isCurrent || isFocused) 1f else 0.6f)
                                .background(placeholder),
                        ) {
                            val poster = part.poster
                            if (poster != null) {
                                NuvioAsyncImage(
                                    model = poster,
                                    contentDescription = part.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(1.dp))
                    Row(
                        // Lines the label up with the poster inside its 7 dp frame.
                        Modifier.padding(start = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            ShelfInfoPeopleRules.partLabel(index),
                            fontSize = 12.sp,
                            letterSpacing = 2.16.sp, // .18em
                            color = ink.copy(alpha = 0.7f),
                            maxLines = 1,
                        )
                        if (index in watched) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Watched",
                                tint = ink.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            line,
            modifier = Modifier.padding(start = 7.dp),
            fontSize = 17.sp,
            fontWeight = FontWeight.Light,
            color = ink.copy(alpha = 0.75f),
            maxLines = 1,
            minLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
