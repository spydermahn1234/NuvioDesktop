// fork:shelf-home
package com.nuvio.app.features.home.shelves

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.details.effectiveEpisodeNumber
import com.nuvio.app.features.details.effectiveSeasonNumber
import com.nuvio.app.features.details.streamVideoIdForPlayback
import com.nuvio.app.features.watched.watchedItemKey
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import com.nuvio.app.features.watchprogress.buildPlaybackVideoId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/*
 * fork:shelf-home - v3 phase 3, seasons and episodes in Info (mockup V3Seasons, plan in
 * design-notes/home-shelves-v3.md). For a show, Info drops the scores and cast for the season strip
 * ("SEASON 1  2  3 ...") and a six-row episode list (number, name, length, watched / time left with a
 * progress line), opened on the episode you are up to. Up/Down move through the episodes, Left/Right change
 * season, OK plays the episode; Play reads "Resume S4 E2" / "Play S4 E3". ShelfInfoOverlay.kt owns the keys
 * and the layout around these pieces; the rules are pure and tested in ShelfInfoSeasonsRulesTest.
 */

/** One episode as Info lists it. [videoId] is the id the details page plays it by. */
internal data class ShelfEpisode(
    val season: Int,
    val number: Int,
    val title: String,
    /** Its length in minutes, when the record has one. */
    val minutes: Int?,
    val videoId: String,
    val thumbnail: String? = null,
    val overview: String? = null,
    val released: String? = null,
)

/** What you have seen of one episode. */
internal data class ShelfEpisodeState(
    val watched: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    /** 0..1 of the way through (from the progress record); 0 when not started. */
    val fraction: Float = 0f,
    val updatedMs: Long = 0L,
) {
    val partWatched: Boolean get() = !watched && (positionMs > 0L || fraction > 0f)
}

/** The pure part of Info's seasons and episodes. */
internal object ShelfInfoSeasonsRules {
    /** Rows the list shows at once (the board: six). */
    const val VISIBLE_ROWS = 6

    /** Season tabs shown at once; a longer show's strip slides with the current season. */
    const val VISIBLE_SEASONS = 14

    const val SPECIALS = 0
    private val seriesTypes = setOf("series", "tv")

    /** The show's type and id when [card] is a show (or an episode of one), else null: films keep their Info. */
    fun showKey(card: ShelfCard): Pair<String, String>? =
        ShelfInfoRules.detailsKey(card)?.takeIf { it.first.lowercase() in seriesTypes }

    /** The record's episodes, in order: seasons up from 1, specials (season 0) last, each season by number. */
    fun episodes(showId: String, videos: List<MetaVideo>): List<ShelfEpisode> =
        videos.mapNotNull { video ->
            val season = video.effectiveSeasonNumber() ?: return@mapNotNull null
            val number = video.effectiveEpisodeNumber() ?: return@mapNotNull null
            if (season < 0 || number < 0) return@mapNotNull null
            val playId = buildPlaybackVideoId(
                parentMetaId = showId,
                seasonNumber = season,
                episodeNumber = number,
                fallbackVideoId = video.id,
            )
            ShelfEpisode(
                season = season,
                number = number,
                title = video.title.trim(),
                minutes = video.runtime?.takeIf { it in 1..600 },
                videoId = video.streamVideoIdForPlayback(showId, playId),
                thumbnail = video.thumbnail,
                overview = video.overview,
                released = video.released,
            )
        }
            .distinctBy { it.season to it.number }
            .sortedWith(compareBy<ShelfEpisode>({ seasonSortKey(it.season) }, { it.number }))

    private fun seasonSortKey(season: Int): Int = if (season == SPECIALS) Int.MAX_VALUE else season

    /** The seasons for the strip, in the list's order (specials last). */
    fun seasons(episodes: List<ShelfEpisode>): List<Int> =
        episodes.map { it.season }.distinct().sortedBy(::seasonSortKey)

    /** How many real seasons (specials not counted). */
    fun seasonCount(episodes: List<ShelfEpisode>): Int = seasons(episodes).count { it != SPECIALS }

    fun inSeason(episodes: List<ShelfEpisode>, season: Int): List<ShelfEpisode> = episodes.filter { it.season == season }

    /** A tab's words: "SEASON 1" for the first one shown, the bare number after it, "SPECIALS" for season 0. */
    fun seasonLabel(season: Int, first: Boolean): String = when {
        season == SPECIALS -> "SPECIALS"
        first -> "SEASON $season"
        else -> "$season"
    }

    /**
     * What you have seen of each episode, keyed by (season, number): a progress record for it (matched by
     * season and number under the show, or by its video id), or a watched mark.
     */
    fun states(
        episodes: List<ShelfEpisode>,
        showType: String,
        showId: String,
        entries: List<WatchProgressEntry>,
        watchedKeys: Set<String>,
    ): Map<Pair<Int, Int>, ShelfEpisodeState> {
        val byCoordinate = entries
            .filter { it.parentMetaId == showId && it.seasonNumber != null && it.episodeNumber != null }
            .groupBy { it.seasonNumber!! to it.episodeNumber!! }
            .mapValues { (_, list) -> list.maxBy { it.lastUpdatedEpochMs } }
        val byVideo = entries.associateBy { it.videoId }
        return episodes.associate { ep ->
            val entry = byCoordinate[ep.season to ep.number] ?: byVideo[ep.videoId]
            val marked = watchedKeys.contains(watchedItemKey(showType, showId, ep.season, ep.number))
            val state = when {
                entry == null -> ShelfEpisodeState(watched = marked)
                entry.isEffectivelyCompleted -> ShelfEpisodeState(watched = true, updatedMs = entry.lastUpdatedEpochMs)
                else -> ShelfEpisodeState(
                    // Marked watched while progress was left behind: the mark wins.
                    watched = marked,
                    positionMs = entry.lastPositionMs.coerceAtLeast(0L),
                    durationMs = entry.durationMs.coerceAtLeast(0L),
                    fraction = entry.progressFraction,
                    updatedMs = entry.lastUpdatedEpochMs,
                )
            }
            (ep.season to ep.number) to state
        }
    }

    /** True when the episode has come out by [today] ("yyyy-MM-dd"); an episode with no date counts as out. */
    fun aired(episode: ShelfEpisode, today: String?): Boolean {
        val date = episode.released?.trim()?.take(10)?.takeIf { it.length == 10 } ?: return true
        if (today.isNullOrBlank()) return true
        return date <= today
    }

    /**
     * The episode Info opens on:
     *  1. a Continue watching card's own episode ([cardSeason]/[cardEpisode]);
     *  2. else the part-watched episode you touched last;
     *  3. else the first aired episode after the furthest one watched (in the list's order, specials aside);
     *  4. else the first episode (S1 E1), which is also where a show watched to the end starts again.
     */
    fun openOn(
        episodes: List<ShelfEpisode>,
        states: Map<Pair<Int, Int>, ShelfEpisodeState>,
        cardSeason: Int? = null,
        cardEpisode: Int? = null,
        today: String? = null,
    ): ShelfEpisode? {
        if (episodes.isEmpty()) return null
        if (cardSeason != null && cardEpisode != null) {
            episodes.firstOrNull { it.season == cardSeason && it.number == cardEpisode }?.let { return it }
        }
        episodes
            .filter { states[it.season to it.number]?.partWatched == true }
            .maxByOrNull { states[it.season to it.number]?.updatedMs ?: 0L }
            ?.let { return it }
        val regular = episodes.filter { it.season != SPECIALS }.ifEmpty { episodes }
        val furthest = regular.indexOfLast { states[it.season to it.number]?.watched == true }
        if (furthest >= 0) {
            regular.drop(furthest + 1)
                .firstOrNull { states[it.season to it.number]?.watched != true && aired(it, today) }
                ?.let { return it }
        }
        return regular.first()
    }

    /** Where Left/Right lands in [season]: its part-watched episode, else its first unwatched one, else its first. */
    fun entryFor(
        episodes: List<ShelfEpisode>,
        season: Int,
        states: Map<Pair<Int, Int>, ShelfEpisodeState>,
        today: String? = null,
    ): ShelfEpisode? {
        val list = inSeason(episodes, season)
        if (list.isEmpty()) return null
        list.filter { states[it.season to it.number]?.partWatched == true }
            .maxByOrNull { states[it.season to it.number]?.updatedMs ?: 0L }
            ?.let { return it }
        // Only when some of the season is watched does it skip ahead; an untouched season opens on its first.
        if (list.any { states[it.season to it.number]?.watched == true }) {
            list.firstOrNull { states[it.season to it.number]?.watched != true && aired(it, today) }?.let { return it }
        }
        return list.first()
    }

    /** The status column: "watched", "24m left", or nothing. */
    fun status(episode: ShelfEpisode, state: ShelfEpisodeState?): String {
        if (state == null) return ""
        if (state.watched) return "watched"
        if (!state.partWatched) return ""
        val left = ShelfTime.minutesLeft(state.durationMs, state.positionMs)
            ?: episode.minutes?.let { m ->
                val f = state.fraction.coerceIn(0f, 1f)
                if (f <= 0f) null else (m * (1f - f)).toInt().coerceAtLeast(1)
            }
        return if (left != null) "${ShelfTime.length(left)} left" else "started"
    }

    /** How far the progress line is drawn: full when watched, the fraction when part-watched. */
    fun progress(state: ShelfEpisodeState?): Float = when {
        state == null -> 0f
        state.watched -> 1f
        state.partWatched -> when {
            state.durationMs > 0L -> (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
            else -> state.fraction.coerceIn(0f, 1f)
        }
        else -> 0f
    }

    /** Play's words for [episode]: "Resume S4 E2" when part-watched, else "Play S4 E3" ("Play Special 2" for season 0). */
    fun playLabel(episode: ShelfEpisode, state: ShelfEpisodeState?): String {
        val code = if (episode.season == SPECIALS) "Special ${episode.number}" else "S${episode.season} E${episode.number}"
        return if (state?.partWatched == true) "Resume $code" else "Play $code"
    }

    /** "E2"; "SP2" for a special. */
    fun episodeCode(episode: ShelfEpisode): String = if (episode.season == SPECIALS) "SP${episode.number}" else "E${episode.number}"

    /** "1h 16m", "52m", or nothing. */
    fun length(episode: ShelfEpisode): String = episode.minutes?.let { ShelfTime.length(it) }.orEmpty()

    /**
     * The first row shown so that [focused] stays in view with a row of context above and below it (where
     * there is one); [current] is the window's start before the move, so the list scrolls only when it must.
     */
    fun windowStart(current: Int, focused: Int, count: Int, visible: Int = VISIBLE_ROWS): Int {
        if (count <= visible) return 0
        var start = current
        if (focused < start + 1) start = focused - 1
        if (focused > start + visible - 2) start = focused - visible + 2
        return start.coerceIn(0, count - visible)
    }

    /** The slice of tabs shown: all of them, or [VISIBLE_SEASONS] around the current season. */
    fun seasonWindow(seasons: List<Int>, current: Int, visible: Int = VISIBLE_SEASONS): List<Int> {
        if (seasons.size <= visible) return seasons
        val index = seasons.indexOf(current).coerceAtLeast(0)
        val start = (index - visible / 2).coerceIn(0, seasons.size - visible)
        return seasons.subList(start, start + visible)
    }

    private val yearPattern = Regex("""\d{4}""")
    private val runningStatuses = setOf("continuing", "returning series", "in production", "running", "ongoing")

    /** "2011 –" for a running show, "2011 – 2019" for an ended one, "2011" when that is all we know. */
    fun years(releaseInfo: String?, status: String? = null): String? {
        val text = releaseInfo?.trim().orEmpty()
        val found = yearPattern.findAll(text).map { it.value }.toList()
        val first = found.firstOrNull() ?: return null
        val last = found.drop(1).firstOrNull { it != first }
        if (last != null) return "$first – $last"
        val openRange = text.substringAfter(first).trim().let { it.startsWith("–") || it.startsWith("-") || it.startsWith("—") }
        val running = status?.trim()?.lowercase()?.let { it in runningStatuses } == true
        return if (openRange || running) "$first –" else first
    }

    /** "CHARLIE BROOKER · 2011 – · DRAMA / SCI-FI · 7 SEASONS"; the parts it has. */
    fun credit(people: List<String>, years: String?, genres: List<String>, seasons: Int): String =
        listOfNotNull(
            people.map { it.trim() }.filter { it.isNotEmpty() }.take(1).firstOrNull(),
            years?.takeIf { it.isNotBlank() },
            genres.map { it.trim() }.filter { it.isNotEmpty() }.take(ShelfInfoRules.GENRES).joinToString(" / ")
                .takeIf { it.isNotEmpty() },
            when {
                seasons == 1 -> "1 SEASON"
                seasons > 1 -> "$seasons SEASONS"
                else -> null
            },
        ).joinToString(" · ").uppercase()

    /** The show's lines above the strip: title, credit and its one-line story. */
    fun text(card: ShelfCard, details: MetaDetails?, seasonCount: Int): ShelfInfoText {
        val (title, releaseInfo, genres, story) = when (card) {
            is ShelfCard.Meta -> Quad(
                card.item.name,
                card.item.releaseInfo?.takeIf { it.isNotBlank() } ?: details?.releaseInfo,
                card.item.genres.ifEmpty { details?.genres.orEmpty() },
                card.item.description?.takeIf { it.isNotBlank() } ?: details?.description,
            )
            is ShelfCard.Progress -> Quad(
                card.item.title,
                details?.releaseInfo,
                details?.genres.orEmpty(),
                // The show's story; the episode's only when the record has none.
                details?.description?.takeIf { it.isNotBlank() } ?: card.item.pauseDescription,
            )
            is ShelfCard.Folder -> Quad(card.folder.title, null, emptyList(), null)
        }
        val people = details?.creator.orEmpty().ifEmpty { details?.director.orEmpty() }
        return ShelfInfoText(
            title = title,
            credit = credit(people, years(releaseInfo, details?.status), genres, seasonCount),
            description = story?.trim().orEmpty(),
            scores = emptyList(),
            cast = "",
        )
    }

    private data class Quad(val a: String, val b: String?, val c: List<String>, val d: String?)
}

/**
 * The show's episodes for Info: [base] (the record Info already has) with the shown [season]'s episodes
 * replaced by a TMDB-decorated copy (names and lengths), fetched 300 ms after the season settles and kept
 * for the overlay's life. When the record Info got has no episode list at all ([baseSettled] and empty),
 * the addon's plain list is fetched once.
 */
@Composable
internal fun rememberShelfShowVideos(
    key: Pair<String, String>?,
    base: List<MetaVideo>,
    baseSettled: Boolean,
    season: Int?,
): List<MetaVideo> {
    var plain by remember(key) { mutableStateOf<List<MetaVideo>>(emptyList()) }
    var decorated by remember(key) { mutableStateOf<Map<Int, List<MetaVideo>>>(emptyMap()) }
    val baseEmpty = base.isEmpty()
    LaunchedEffect(key, baseSettled, baseEmpty) {
        val k = key ?: return@LaunchedEffect
        if (!baseSettled || !baseEmpty || plain.isNotEmpty()) return@LaunchedEffect
        plain = fetchVideos { MetaDetailsRepository.fetch(type = k.first, id = k.second, enrichTmdb = false) }
    }
    LaunchedEffect(key, season) {
        val k = key ?: return@LaunchedEffect
        val s = season ?: return@LaunchedEffect
        if (decorated.containsKey(s)) return@LaunchedEffect
        delay(300)
        val videos = fetchVideos {
            MetaDetailsRepository.fetch(type = k.first, id = k.second, episodeSeasons = setOf(s))
        }.filter { it.effectiveSeasonNumber() == s }
        decorated = decorated + (s to videos)
    }
    val source = base.ifEmpty { plain }
    if (decorated.values.all { it.isEmpty() }) return source
    val replaced = decorated.filterValues { it.isNotEmpty() }
    return source.filter { it.effectiveSeasonNumber() !in replaced.keys } + replaced.values.flatten()
}

private suspend fun fetchVideos(fetch: suspend () -> MetaDetails?): List<MetaVideo> =
    try {
        fetch()?.videos.orEmpty()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        emptyList()
    }

/** The season strip: 20sp tracked, the current one in the ink with a 3 dp accent underline. */
@Composable
internal fun ShelfSeasonStrip(
    seasons: List<Int>,
    current: Int?,
    ink: Color,
    accent: Color,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pick by rememberUpdatedState(onPick)
    val shown = if (current != null) ShelfInfoSeasonsRules.seasonWindow(seasons, current) else seasons
    val cutBefore = shown.isNotEmpty() && shown.first() != seasons.firstOrNull()
    val cutAfter = shown.isNotEmpty() && shown.last() != seasons.lastOrNull()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(30.dp), verticalAlignment = Alignment.Bottom) {
        if (cutBefore) SeasonTab("…", selected = false, ink = ink, accent = accent, onClick = {})
        shown.forEachIndexed { index, season ->
            SeasonTab(
                label = ShelfInfoSeasonsRules.seasonLabel(season, first = index == 0),
                selected = season == current,
                ink = ink,
                accent = accent,
                onClick = { pick(season) },
            )
        }
        if (cutAfter) SeasonTab("…", selected = false, ink = ink, accent = accent, onClick = {})
    }
}

@Composable
private fun SeasonTab(label: String, selected: Boolean, ink: Color, accent: Color, onClick: () -> Unit) {
    val click by rememberUpdatedState(onClick)
    Column(Modifier.width(IntrinsicSize.Max).pointerInput(Unit) { detectTapGestures { click() } }) {
        Text(
            label,
            fontSize = 20.sp,
            letterSpacing = 2.sp, // .1em
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) ink else ink.copy(alpha = 0.55f),
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        // Every tab keeps the underline's height, so the selected one does not stand taller.
        Box(
            Modifier
                .height(3.dp)
                .fillMaxWidth()
                .background(if (selected) accent else Color.Transparent),
        )
    }
}

/** Height of the list's six rows and five gaps (the board: 60 high, 4 apart). */
internal val ShelfEpisodeListHeight = (60 * ShelfInfoSeasonsRules.VISIBLE_ROWS + 4 * (ShelfInfoSeasonsRules.VISIBLE_ROWS - 1)).dp

/**
 * The episode list: [ShelfInfoSeasonsRules.VISIBLE_ROWS] rows from [start]. The selected row is outlined in
 * the ink and filled 12% while the list has focus ([listFocused]); with the buttons focused it keeps only the
 * fill, so you can see which episode Play plays. Always [ShelfEpisodeListHeight] high: empty slots stay empty.
 */
@Composable
internal fun ShelfEpisodeList(
    episodes: List<ShelfEpisode>,
    states: Map<Pair<Int, Int>, ShelfEpisodeState>,
    selected: ShelfEpisode?,
    start: Int,
    listFocused: Boolean,
    ink: Color,
    onClick: (ShelfEpisode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val click by rememberUpdatedState(onClick)
    Column(modifier.width(980.dp).height(ShelfEpisodeListHeight), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val shown = episodes.drop(start).take(ShelfInfoSeasonsRules.VISIBLE_ROWS)
        for (episode in shown) {
            val state = states[episode.season to episode.number]
            EpisodeRow(
                episode = episode,
                state = state,
                selected = episode == selected,
                listFocused = listFocused,
                ink = ink,
                onClick = { click(episode) },
            )
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: ShelfEpisode,
    state: ShelfEpisodeState?,
    selected: Boolean,
    listFocused: Boolean,
    ink: Color,
    onClick: () -> Unit,
) {
    val click by rememberUpdatedState(onClick)
    val watched = state?.watched == true
    val partWatched = state?.partWatched == true
    var row = Modifier
        .fillMaxWidth()
        .height(60.dp)
    if (selected) row = row.background(ink.copy(alpha = 0.12f))
    if (selected && listFocused) row = row.border(3.dp, ink)
    Row(
        row
            .pointerInput(Unit) { detectTapGestures { click() } }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            ShelfInfoSeasonsRules.episodeCode(episode),
            fontSize = 17.sp,
            letterSpacing = 3.4.sp, // .2em
            color = ink.copy(alpha = 0.6f),
            maxLines = 1,
            modifier = Modifier.width(70.dp),
        )
        Text(
            episode.title.ifEmpty { "Episode ${episode.number}" },
            fontSize = 24.sp,
            fontWeight = if (selected && listFocused) FontWeight.SemiBold else FontWeight.Light,
            color = when {
                selected && listFocused -> ink
                watched -> ink.copy(alpha = 0.55f)
                else -> ink
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 16.dp),
        )
        Text(
            ShelfInfoSeasonsRules.length(episode),
            fontSize = 18.sp,
            fontWeight = FontWeight.Light,
            color = ink.copy(alpha = 0.7f),
            maxLines = 1,
            modifier = Modifier.width(110.dp),
        )
        Column(Modifier.width(150.dp)) {
            Text(
                ShelfInfoSeasonsRules.status(episode, state),
                fontSize = 15.sp,
                color = ink.copy(alpha = 0.65f),
                maxLines = 1,
                // An empty status keeps its line, so every row's progress line sits at the same height.
                minLines = 1,
            )
            Spacer(Modifier.height(6.dp))
            val fraction = ShelfInfoSeasonsRules.progress(state)
            Box(Modifier.fillMaxWidth().height(3.dp).background(ink.copy(alpha = 0.18f))) {
                if (fraction > 0f) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(3.dp)
                            .background(if (partWatched) ink else ink.copy(alpha = 0.5f)),
                    )
                }
            }
        }
    }
}

/** The hint under the clock's corner while the list has focus. */
internal const val SHELF_SEASONS_HINT = "UP / DOWN: EPISODES · LEFT / RIGHT: SEASONS"

/** Keeps the strip's line when there is nothing to show yet, so nothing moves when the episodes arrive. */
@Composable
internal fun ShelfSeasonStripPlaceholder(ink: Color, modifier: Modifier = Modifier) {
    Text("SEASON", fontSize = 20.sp, letterSpacing = 2.sp, color = ink, maxLines = 1, modifier = modifier.alpha(0f))
}
