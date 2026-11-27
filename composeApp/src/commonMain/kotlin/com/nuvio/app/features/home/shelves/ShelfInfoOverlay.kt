// fork:shelf-home
package com.nuvio.app.features.home.shelves

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.KeyRouteLayer
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.keyLayer
import com.nuvio.app.core.ui.requestFocusLogged
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.details.rankHeroTrailers
import com.nuvio.app.features.home.LIVE_TV_HOME_ITEM_TYPE
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.library.toLibraryItem
import com.nuvio.app.features.locallibrary.isLocalLibraryId
import com.nuvio.app.features.mdblist.MdbListMetadataService
import com.nuvio.app.features.playlist.PlaylistContentType
import com.nuvio.app.features.remote.RemoteEpisodePlay
import com.nuvio.app.features.remote.RemotePlayerHub
import com.nuvio.app.features.remote.RemoteYouTubeOpen
import com.nuvio.app.features.tonight.clockLabel
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import kotlin.math.roundToInt
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * fork:shelf-home - Info (mockup V2Details, keys and motion in V2Flow / V2Prototype): I or hold OK on a
 * title. The row drops away and the full story, scores, cast and four buttons rise in its place. The frame
 * under it draws the art and the clock; this draws the two scrims and everything else. It takes every key
 * and click while it is up and moves its own button selection (Compose Desktop moves focus only on Tab),
 * so nothing here is a focus target but the root. Fixed layout: every line has its place reserved, so
 * nothing moves when the full record (director, cast, scores) arrives after the overlay is shown.
 */

/** What the four buttons do; a null action hides its button. */
internal class ShelfInfoActions(
    val play: ((ShelfCard) -> Unit)? = null,
    val trailer: ((ShelfCard) -> Unit)? = null,
    val moreLikeThis: ((ShelfCard) -> Unit)? = null,
    val addToList: ((ShelfCard) -> Unit)? = null,
    val inList: (ShelfCard) -> Boolean = { false },
    /**
     * Continue watching's source shortcut (Nick, Nov 27: a pill in Info): the opposite of what Play does with streams.
     * [sourceShortcutLabel] is null for a card that offers none (not a Continue watching card, the shortcut set to Off
     * in Settings, a cloud file, or nothing played yet when the shortcut would replay the last link).
     */
    val sourceShortcut: ((ShelfCard) -> Unit)? = null,
    val sourceShortcutLabel: (ShelfCard) -> String? = { null },
    /**
     * v3 phase 3: plays one episode of a show (Info's episode list and its Play). Null: a show's Play falls back
     * to [play] (which opens its page).
     */
    val playEpisode: ((ShelfCard, ShelfEpisode) -> Unit)? = null,
)

internal enum class ShelfInfoButton { Play, SourceShortcut, Trailer, MoreLikeThis, AddToList }

internal data class ShelfInfoScore(val label: String, val value: String)

/** Every word Info shows for a card. */
internal data class ShelfInfoText(
    val title: String,
    /** "BRAD BIRD · 1999 · ANIMATION / FAMILY · 1H 26M · PG", or "S4 E1 · USS CALLISTER". */
    val credit: String,
    val description: String,
    val scores: List<ShelfInfoScore>,
    /** "Eli Marienthal · Jennifer Aniston · ..." (empty: the WITH lines keep their place, blank). */
    val cast: String,
)

/** The pure part of Info: which lines, in which words. Tested in ShelfInfoRulesTest. */
internal object ShelfInfoRules {
    const val GENRES = 2
    const val CAST_NAMES = 4
    private val seriesTypes = setOf("series", "tv")

    /** The title's type and id for the details record, or null for cards that have none (folders, Live TV, playlists, local files). */
    fun detailsKey(card: ShelfCard): Pair<String, String>? {
        val (type, id) = when (card) {
            is ShelfCard.Meta -> card.item.type to card.item.id
            is ShelfCard.Progress -> card.item.parentMetaType to card.item.parentMetaId
            is ShelfCard.Folder -> return null
        }
        if (type.isBlank() || id.isBlank()) return null
        if (type == LIVE_TV_HOME_ITEM_TYPE || type == PlaylistContentType || id.isLocalLibraryId()) return null
        return type to id
    }

    /** The buttons shown, in order. Trailer, More like this and Add to list need a title with a details record. */
    fun buttons(
        card: ShelfCard,
        play: Boolean,
        trailer: Boolean,
        moreLikeThis: Boolean,
        addToList: Boolean,
        sourceShortcut: Boolean = false,
    ): List<ShelfInfoButton> {
        val titled = detailsKey(card) != null
        return buildList {
            if (play) add(ShelfInfoButton.Play)
            // Beside Play, since it is the other way of playing (only Continue watching cards offer it).
            if (sourceShortcut && card is ShelfCard.Progress) add(ShelfInfoButton.SourceShortcut)
            if (trailer && titled) add(ShelfInfoButton.Trailer)
            if (moreLikeThis && titled) add(ShelfInfoButton.MoreLikeThis)
            if (addToList && titled) add(ShelfInfoButton.AddToList)
        }
    }

    /** The pill's words: what it does instead of Play. */
    fun sourceShortcutLabel(kind: com.nuvio.app.features.home.components.ContinueWatchingSourceShortcutKind?): String? =
        when (kind) {
            com.nuvio.app.features.home.components.ContinueWatchingSourceShortcutKind.OpenSourcePicker -> "Choose a source"
            com.nuvio.app.features.home.components.ContinueWatchingSourceShortcutKind.ReuseLastLink -> "Play last source"
            null -> null
        }

    fun buttonLabel(
        button: ShelfInfoButton,
        inList: Boolean,
        sourceShortcutLabel: String? = null,
        /** A show's Play names its episode ("Resume S4 E2", from ShelfInfoSeasonsRules.playLabel). */
        playLabel: String? = null,
    ): String = when (button) {
        ShelfInfoButton.Play -> playLabel ?: "Play"
        ShelfInfoButton.SourceShortcut -> sourceShortcutLabel ?: "Choose a source"
        ShelfInfoButton.Trailer -> "Trailer"
        ShelfInfoButton.MoreLikeThis -> "More like this"
        ShelfInfoButton.AddToList -> if (inList) "In your list" else "Add to list"
    }

    fun text(card: ShelfCard, details: MetaDetails?, nowMinute: Int, clock: (Int) -> String = { clockLabel(it) }): ShelfInfoText =
        when (card) {
            is ShelfCard.Meta -> metaText(card.item, details, nowMinute, clock)
            is ShelfCard.Progress -> progressText(card.item, details, nowMinute, clock)
            is ShelfCard.Folder -> ShelfInfoText(card.folder.title, "", "", emptyList(), "")
        }

    private fun metaText(item: MetaPreview, details: MetaDetails?, nowMinute: Int, clock: (Int) -> String): ShelfInfoText {
        val series = item.type.lowercase() in seriesTypes
        val minutes = (ShelfTime.runtimeMinutes(item.runtime) ?: ShelfTime.runtimeMinutes(details?.runtime))
            ?.takeIf { !series }
        val credit = credit(
            people = people(details),
            year = year(item.releaseInfo) ?: year(details?.releaseInfo),
            genres = item.genres.ifEmpty { details?.genres.orEmpty() },
            minutes = minutes,
            age = item.ageRating?.takeIf { it.isNotBlank() } ?: details?.ageRating,
        )
        val scores = buildList {
            imdb(item.imdbRating, details)?.let { add(ShelfInfoScore("IMDB", it)) }
            rating(details, MdbListMetadataService.PROVIDER_TOMATOES)?.let { add(ShelfInfoScore("CRITICS", percent(it))) }
            rating(details, MdbListMetadataService.PROVIDER_AUDIENCE)?.let { add(ShelfInfoScore("AUDIENCE", percent(it))) }
            minutes?.let { add(ShelfInfoScore("ENDS", clock(ShelfTime.endsAt(nowMinute, it)))) }
        }
        val names = item.cast.map { it.name }.ifEmpty { details?.cast.orEmpty().map { it.name } }
        return ShelfInfoText(
            title = item.name,
            credit = credit,
            description = item.description?.takeIf { it.isNotBlank() } ?: details?.description.orEmpty(),
            scores = scores,
            cast = castLine(names),
        )
    }

    private fun progressText(item: ContinueWatchingItem, details: MetaDetails?, nowMinute: Int, clock: (Int) -> String): ShelfInfoText {
        val episode = episodeLine(item.seasonNumber, item.episodeNumber, item.episodeTitle)
        val left = ShelfTime.minutesLeft(item.durationMs, item.resumePositionMs)
        val credit = episode.ifEmpty {
            // A film in Continue Watching: the film's own credit line.
            credit(
                people = people(details),
                year = year(details?.releaseInfo),
                genres = details?.genres.orEmpty(),
                minutes = ShelfTime.runtimeMinutes(details?.runtime) ?: ShelfTime.minutesLeft(item.durationMs, 0L),
                age = details?.ageRating,
            )
        }
        val scores = buildList {
            imdb(null, details)?.let { add(ShelfInfoScore("IMDB", it)) }
            if (left != null) {
                if (item.progressFraction > 0f && item.resumePositionMs > 0L) {
                    add(ShelfInfoScore("LEFT", ShelfTime.length(left)))
                    add(ShelfInfoScore("FINISH BY", clock(ShelfTime.endsAt(nowMinute, left))))
                } else {
                    // Not started (Up next): it starts now and ends after its full length.
                    add(ShelfInfoScore("ENDS", clock(ShelfTime.endsAt(nowMinute, left))))
                }
            }
        }
        return ShelfInfoText(
            title = item.title,
            credit = credit,
            description = item.pauseDescription?.takeIf { it.isNotBlank() } ?: details?.description.orEmpty(),
            scores = scores,
            cast = castLine(details?.cast.orEmpty().map { it.name }),
        )
    }

    /** The director, or a series' creator. */
    private fun people(details: MetaDetails?): List<String> =
        details?.director.orEmpty().ifEmpty { details?.creator.orEmpty() }.take(1)

    /** "BRAD BIRD · 1999 · ANIMATION / FAMILY · 1H 26M · PG"; the parts it has, in that order. */
    fun credit(people: List<String>, year: String?, genres: List<String>, minutes: Int?, age: String?): String =
        listOfNotNull(
            people.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ").takeIf { it.isNotEmpty() },
            year?.takeIf { it.isNotBlank() },
            genres.map { it.trim() }.filter { it.isNotEmpty() }.take(GENRES).joinToString(" / ").takeIf { it.isNotEmpty() },
            minutes?.let { ShelfTime.length(it) },
            age?.trim()?.takeIf { it.isNotEmpty() },
        ).joinToString(" · ").uppercase()

    /** "S4 E1 · USS CALLISTER"; "S4 E1" without a title; empty without both numbers. */
    fun episodeLine(season: Int?, episode: Int?, title: String?): String {
        if (season == null || episode == null) return ""
        val name = title?.trim()?.takeIf { it.isNotEmpty() }
        return (if (name != null) "S$season E$episode · $name" else "S$season E$episode").uppercase()
    }

    private val yearPattern = Regex("""\b(\d{4})\b""")

    /** The first four-digit year of a release line ("1999", "2011–2019" gives 2011). */
    fun year(releaseInfo: String?): String? = releaseInfo?.let { yearPattern.find(it)?.groupValues?.get(1) }

    /** The IMDb score: the card's own, else the record's, else MDBList's; "8.1". */
    fun imdb(cardRating: String?, details: MetaDetails?): String? {
        val text = cardRating?.takeIf { it.isNotBlank() } ?: details?.imdbRating?.takeIf { it.isNotBlank() }
        val value = text?.trim()?.toDoubleOrNull() ?: rating(details, MdbListMetadataService.PROVIDER_IMDB)
        return value?.takeIf { it > 0.0 }?.let(::tenths)
    }

    private fun rating(details: MetaDetails?, source: String): Double? =
        details?.externalRatings?.firstOrNull { it.source == source }?.value?.takeIf { it > 0.0 }

    fun tenths(value: Double): String {
        val t = (value * 10).roundToInt()
        return "${t / 10}.${t % 10}"
    }

    fun percent(value: Double): String = "${value.roundToInt()}%"

    fun castLine(names: List<String>): String =
        names.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(CAST_NAMES).joinToString(" · ")
}

private val InfoBackKeys = setOf(Key.Back, Key.Escape, Key.Backspace)
private val InfoEnterKeys = setOf(Key.Enter, Key.NumPadEnter, Key.DirectionCenter)

/** An OK press must come this long after the overlay opened, or after the last OK press, when no release came between. */
private const val HELD_OK_GAP_MS = 600L

@Composable
internal fun ShelfInfoOverlay(
    card: ShelfCard,
    rowTitle: String,
    nowMinute: Int,
    actions: ShelfInfoActions,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KeyRouteLayer("ShelfInfo")
    val palette = LocalShelfPalette.current
    val ink = palette.ink
    val accent = palette.accentInk
    val dismiss by rememberUpdatedState(onDismiss)
    val currentActions by rememberUpdatedState(actions)

    // The card's record: whatever is already cached at once, then the hero's summary (cached, shared with
    // the Home hero) for the director, cast and anything the card lacks.
    val key = remember(card) { ShelfInfoRules.detailsKey(card) }
    val peeked = remember(key) { key?.let { MetaDetailsRepository.peekAny(it.first, it.second) } }
    var fetched by remember(key) { mutableStateOf<MetaDetails?>(null) }
    var fetchDone by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        val k = key ?: return@LaunchedEffect
        fetched = try {
            MetaDetailsRepository.fetchHeroSummary(type = k.first, id = k.second)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }
        fetchDone = true
    }
    // The summary carries no MDBList scores; keep the ones a visit to the details page left in the cache.
    val details = fetched?.let { f ->
        if (f.externalRatings.isEmpty() && !peeked?.externalRatings.isNullOrEmpty()) f.copy(externalRatings = peeked!!.externalRatings) else f
    } ?: peeked

    // v3 phase 3 (mockup V3Seasons): a show gets the season strip and the episode list instead of scores and cast.
    val showKey = remember(card) { ShelfInfoSeasonsRules.showKey(card) }
    val series = showKey != null
    val progressState by WatchProgressRepository.uiState.collectAsStateWithLifecycle()
    val watchedState by WatchedRepository.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(showKey) {
        if (showKey != null) {
            WatchProgressRepository.ensureLoaded()
            WatchedRepository.ensureLoaded()
        }
    }
    var season by remember(card) { mutableStateOf<Int?>(null) }
    var selectedKey by remember(card) { mutableStateOf<Pair<Int, Int>?>(null) }
    var listStart by remember(card) { mutableIntStateOf(0) }
    var inList by remember(card) { mutableStateOf(true) }
    val videos = rememberShelfShowVideos(showKey, details?.videos.orEmpty(), baseSettled = fetchDone || key == null, season = season)
    val episodes = remember(showKey, videos) {
        if (showKey == null) emptyList() else ShelfInfoSeasonsRules.episodes(showKey.second, videos)
    }
    val states = remember(episodes, progressState, watchedState) {
        if (showKey == null) {
            emptyMap()
        } else {
            ShelfInfoSeasonsRules.states(episodes, showKey.first, showKey.second, progressState.entries, watchedState.watchedKeys)
        }
    }
    val seasons = remember(episodes) { ShelfInfoSeasonsRules.seasons(episodes) }
    val today = remember { runCatching { CurrentDateProvider.todayIsoDate() }.getOrNull() }
    // Opens on the episode you are up to, once the list first arrives.
    LaunchedEffect(episodes.isNotEmpty()) {
        if (selectedKey != null || episodes.isEmpty()) return@LaunchedEffect
        val cw = (card as? ShelfCard.Progress)?.item
        val open = ShelfInfoSeasonsRules.openOn(episodes, states, cw?.seasonNumber, cw?.episodeNumber, today)
            ?: return@LaunchedEffect
        val inSeason = ShelfInfoSeasonsRules.inSeason(episodes, open.season)
        val index = inSeason.indexOf(open)
        season = open.season
        selectedKey = open.season to open.number
        listStart = ShelfInfoSeasonsRules.windowStart(index - 1, index, inSeason.size)
    }
    val seasonEpisodes = season?.let { ShelfInfoSeasonsRules.inSeason(episodes, it) }.orEmpty()
    val selected = seasonEpisodes.firstOrNull { (it.season to it.number) == selectedKey }
    val selectedIndex = selected?.let { seasonEpisodes.indexOf(it) } ?: -1
    val listUsable = series && selected != null
    val listFocused = listUsable && inList

    fun select(episode: ShelfEpisode, list: List<ShelfEpisode>, fromStart: Int) {
        val index = list.indexOf(episode)
        season = episode.season
        selectedKey = episode.season to episode.number
        listStart = ShelfInfoSeasonsRules.windowStart(fromStart, index, list.size)
    }

    fun changeSeason(to: Int) {
        val entry = ShelfInfoSeasonsRules.entryFor(episodes, to, states, today) ?: return
        val list = ShelfInfoSeasonsRules.inSeason(episodes, to)
        select(entry, list, list.indexOf(entry) - 1)
    }

    val text = if (series) {
        ShelfInfoSeasonsRules.text(card, details, ShelfInfoSeasonsRules.seasonCount(episodes))
    } else {
        ShelfInfoRules.text(card, details, nowMinute)
    }

    val buttons = ShelfInfoRules.buttons(
        card,
        play = actions.play != null,
        trailer = actions.trailer != null,
        moreLikeThis = actions.moreLikeThis != null,
        addToList = actions.addToList != null,
        sourceShortcut = actions.sourceShortcut != null && actions.sourceShortcutLabel(card) != null,
    )
    var focusedButton by remember { mutableIntStateOf(0) }
    val focused = focusedButton.coerceIn(0, (buttons.size - 1).coerceAtLeast(0))

    fun playEpisode(episode: ShelfEpisode) {
        val a = currentActions
        val play = a.playEpisode
        if (play != null) play(card, episode) else a.play?.invoke(card)
    }

    fun press(button: ShelfInfoButton) {
        val a = currentActions
        when (button) {
            ShelfInfoButton.Play -> {
                val episode = selected
                if (series && episode != null) playEpisode(episode) else a.play?.invoke(card)
            }
            ShelfInfoButton.SourceShortcut -> a.sourceShortcut?.invoke(card)
            ShelfInfoButton.Trailer -> a.trailer?.invoke(card)
            ShelfInfoButton.MoreLikeThis -> a.moreLikeThis?.invoke(card)
            ShelfInfoButton.AddToList -> a.addToList?.invoke(card)
        }
    }

    // A key acts on its release only when its press came here too: the I or OK that opened Info must not also
    // close it or press Play. Holding OK opens Info while the key repeats, so an OK press counts only after a
    // release, or after a pause no key repeat leaves.
    val pressed = remember { mutableSetOf<Key>() }
    var lastOkDown by remember { mutableStateOf(TimeSource.Monotonic.markNow()) }
    var okReleased by remember { mutableStateOf(false) }
    fun onKey(event: KeyEvent): Boolean {
        val k = event.key
        val down = event.type == KeyEventType.KeyDown
        val up = event.type == KeyEventType.KeyUp
        fun onRelease(action: () -> Unit): Boolean {
            if (down) pressed += k
            if (up && pressed.remove(k)) action()
            return true
        }
        return when (k) {
            in InfoBackKeys, Key.I -> onRelease { dismiss() }
            in InfoEnterKeys -> {
                if (down) {
                    val fresh = okReleased || lastOkDown.elapsedNow().inWholeMilliseconds > HELD_OK_GAP_MS
                    lastOkDown = TimeSource.Monotonic.markNow()
                    okReleased = false
                    if (fresh) pressed += k
                }
                if (up) {
                    okReleased = true
                    if (pressed.remove(k)) {
                        val episode = selected
                        if (listFocused && episode != null) playEpisode(episode) else buttons.getOrNull(focused)?.let { press(it) }
                    }
                }
                true
            }
            // In the episode list (a show): Up/Down move through the season's episodes, Down from the last one (or
            // Tab) reaches the buttons; Left/Right change season.
            Key.DirectionLeft, Key.DirectionRight -> {
                if (down) {
                    val step = if (k == Key.DirectionLeft) -1 else 1
                    if (listFocused) {
                        val at = season?.let { seasons.indexOf(it) } ?: -1
                        if (at >= 0) seasons.getOrNull(at + step)?.let { changeSeason(it) }
                    } else {
                        // A key on the buttons before the episodes arrive keeps the focus there when they do.
                        inList = false
                        val next = focused + step
                        if (next in buttons.indices) focusedButton = next
                    }
                }
                true
            }
            Key.DirectionUp -> {
                if (down) {
                    if (listFocused) {
                        if (selectedIndex > 0) select(seasonEpisodes[selectedIndex - 1], seasonEpisodes, listStart)
                    } else if (listUsable) {
                        inList = true
                    }
                }
                true
            }
            Key.DirectionDown -> {
                if (down) {
                    if (listFocused) {
                        if (selectedIndex in 0 until seasonEpisodes.size - 1) {
                            select(seasonEpisodes[selectedIndex + 1], seasonEpisodes, listStart)
                        } else {
                            inList = false
                            focusedButton = 0
                        }
                    } else {
                        inList = false
                    }
                }
                true
            }
            // Tab switches between the list and the buttons (never to the frame underneath).
            Key.Tab -> {
                if (down) {
                    if (listFocused) {
                        inList = false
                        focusedButton = 0
                    } else if (listUsable) {
                        inList = true
                    }
                }
                true
            }
            else -> false
        }
    }

    val root = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        for (attempt in 0 until 6) {
            if (root.requestFocusLogged()) break
            delay(32)
        }
    }

    // Rises in 300 ms: fades in and comes up 24 dp.
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(300)) }

    val scrim = if (palette.backdropAlpha > 0f) Color(0xFF06090A) else palette.scrim
    val onFilled = if (palette.backdropAlpha > 0f) Color(0xFF0D1417) else palette.page
    val labelSpacing = 3.64.sp // .28em at 13
    val rise = Modifier.graphicsLayer { translationY = (1f - appear.value) * 24.dp.toPx() }

    Box(
        modifier
            .fillMaxSize()
            // Every click lands here: nothing under the overlay can be clicked.
            .pointerInput(Unit) { detectTapGestures { } }
            .keyLayer("ShelfInfo") { root.requestFocusLogged() }
            .focusRequester(root)
            .onPreviewKeyEvent { onKey(it) }
            .focusable()
            .graphicsLayer { alpha = appear.value },
    ) {
        Box(
            Modifier.fillMaxSize().background(
                if (series) {
                    // V3Seasons: darker and wider, for the episode list.
                    Brush.horizontalGradient(
                        0f to scrim.copy(alpha = 0.92f),
                        0.45f to scrim.copy(alpha = 0.78f),
                        0.75f to scrim.copy(alpha = 0.2f),
                        1f to scrim.copy(alpha = 0.2f),
                    )
                } else {
                    Brush.horizontalGradient(
                        0f to scrim.copy(alpha = 0.9f),
                        0.4f to scrim.copy(alpha = 0.7f),
                        0.7f to scrim.copy(alpha = 0.1f),
                        1f to scrim.copy(alpha = 0.1f),
                    )
                },
            ),
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(420.dp)
                .background(
                    Brush.verticalGradient(
                        0f to scrim.copy(alpha = 0f),
                        1f to scrim.copy(alpha = 0.92f),
                    ),
                ),
        )

        // ROW NAME / INFO
        Row(
            Modifier.padding(start = 96.dp, top = 64.dp, end = 420.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                rowTitle.uppercase(),
                fontSize = 17.sp,
                letterSpacing = 4.76.sp,
                color = ink.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text("/", fontSize = 17.sp, letterSpacing = 4.76.sp, color = accent, maxLines = 1)
            Text("INFO", fontSize = 17.sp, letterSpacing = 4.76.sp, color = ink, maxLines = 1)
        }

        if (series) {
            // V3Seasons: title, credit and a one-line story, the strip at 440 and six rows from 500, every place
            // fixed so nothing moves while the episodes load.
            Column(rise.padding(start = 96.dp, top = 170.dp).width(900.dp)) {
                Text(
                    text.title,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1.44).sp,
                    lineHeight = 72.sp,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text.credit,
                    fontSize = 18.sp,
                    letterSpacing = 4.32.sp,
                    color = ink.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text.description,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Light,
                    lineHeight = 32.55.sp,
                    color = ink.copy(alpha = 0.88f),
                    minLines = 1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(rise.padding(start = 96.dp, top = 440.dp)) {
                if (seasons.isEmpty()) {
                    ShelfSeasonStripPlaceholder(ink)
                } else {
                    ShelfSeasonStrip(
                        seasons = seasons,
                        current = season,
                        ink = ink,
                        accent = accent,
                        onPick = { picked ->
                            inList = true
                            if (picked != season) changeSeason(picked)
                        },
                    )
                }
            }
            ShelfEpisodeList(
                episodes = seasonEpisodes,
                states = states,
                selected = selected,
                start = listStart,
                listFocused = listFocused,
                ink = ink,
                onClick = { episode ->
                    if (listFocused && episode == selected) {
                        playEpisode(episode)
                    } else {
                        inList = true
                        select(episode, seasonEpisodes, listStart)
                    }
                },
                modifier = rise.padding(start = 96.dp, top = 500.dp),
            )
        } else Column(rise.padding(start = 96.dp, top = 190.dp).width(960.dp)) {
            Text(
                text.title,
                fontSize = 84.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1.68).sp,
                lineHeight = 84.sp,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text.credit,
                fontSize = 19.sp,
                letterSpacing = 4.56.sp,
                color = ink.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(22.dp))
            // Five lines reserved whatever the length, so the scores and cast never move.
            Text(
                text.description,
                fontSize = 22.sp,
                fontWeight = FontWeight.Light,
                lineHeight = 35.2.sp,
                color = ink.copy(alpha = 0.88f),
                minLines = INFO_DESCRIPTION_LINES,
                maxLines = INFO_DESCRIPTION_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(34.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                if (text.scores.isEmpty()) {
                    // Keeps the row's height so WITH stays where it is.
                    ScoreCell(ShelfInfoScore("IMDB", "0"), ink, labelSpacing, Modifier.alpha(0f))
                }
                for (score in text.scores) ScoreCell(score, ink, labelSpacing)
            }
            Spacer(Modifier.height(34.dp))
            val castShown = if (text.cast.isNotEmpty()) 1f else 0f
            Text(
                "WITH",
                fontSize = 13.sp,
                letterSpacing = labelSpacing,
                color = ink.copy(alpha = 0.6f),
                maxLines = 1,
                modifier = Modifier.alpha(castShown),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text.cast,
                fontSize = 21.sp,
                fontWeight = FontWeight.Light,
                color = ink.copy(alpha = 0.88f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(castShown),
            )
        }

        Row(
            rise.align(Alignment.BottomStart).padding(start = 96.dp, bottom = if (series) 70.dp else 96.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val episode = selected
            val playLabel = if (series && episode != null) {
                ShelfInfoSeasonsRules.playLabel(episode, states[episode.season to episode.number])
            } else {
                null
            }
            buttons.forEachIndexed { index, button ->
                InfoPill(
                    label = ShelfInfoRules.buttonLabel(
                        button,
                        button == ShelfInfoButton.AddToList && actions.inList(card),
                        actions.sourceShortcutLabel(card),
                        playLabel,
                    ),
                    play = button == ShelfInfoButton.Play,
                    // While the episode list has focus Play stays filled (the board): OK on a row does what it says.
                    focused = if (listFocused) button == ShelfInfoButton.Play else index == focused,
                    ink = ink,
                    onFilled = onFilled,
                    onClick = {
                        inList = false
                        focusedButton = index
                        press(button)
                    },
                )
            }
        }

        Text(
            if (listFocused) SHELF_SEASONS_HINT else "BACK RETURNS TO THE ROW",
            fontSize = 14.sp,
            letterSpacing = 3.64.sp,
            color = ink.copy(alpha = 0.5f),
            maxLines = 1,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 96.dp, bottom = if (series) 86.dp else 110.dp),
        )
    }
}

private const val INFO_DESCRIPTION_LINES = 5

@Composable
private fun ScoreCell(score: ShelfInfoScore, ink: Color, labelSpacing: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(score.label, fontSize = 13.sp, letterSpacing = labelSpacing, color = ink.copy(alpha = 0.6f), maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Text(score.value, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1)
    }
}

/** The mockup's pill: 64 high; the selected one filled with the ink, the others a 2 dp outline at 55%. */
@Composable
private fun InfoPill(label: String, play: Boolean, focused: Boolean, ink: Color, onFilled: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(32.dp)
    val click by rememberUpdatedState(onClick)
    Row(
        Modifier
            .height(64.dp)
            .clip(shape)
            .background(if (focused) ink else Color.Transparent, shape)
            .border(2.dp, if (focused) ink else ink.copy(alpha = 0.55f), shape)
            .pointerInput(Unit) { detectTapGestures { click() } }
            .padding(horizontal = if (play) 34.dp else 30.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (focused) onFilled else ink
        if (play) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(
            label,
            fontSize = 21.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            color = color,
            maxLines = 1,
        )
    }
}

/**
 * Info's four buttons on Nuvio's own actions, for the shelf home's call in HomeScreen:
 *  - Play: a film starts as the details page's Play does (its sources, then the player, resuming where it
 *    stopped) through [RemotePlayerHub.playEpisode]; Live TV and playlist cards open as OK does; a Continue
 *    Watching card plays as the classic home's Play does; a folder opens. A show plays the episode picked in
 *    Info's list (playEpisode below); only when its episodes cannot be listed does Play open its page.
 *  - Trailer: the title's best trailer (the heroes' choice, first season only) in the player, through the
 *    YouTube path the phone remote's Send to Nuvio uses.
 *  - More like this: the title's details page (its More like this rail).
 *  - Add to list: the poster menu's plain Add (the active library's default list); pressing "In your list" takes it out.
 */
@Composable
internal fun rememberShelfInfoActions(
    onPosterClick: ((MetaPreview) -> Unit)?,
    onContinueWatchingClick: ((ContinueWatchingItem) -> Unit)?,
    onContinueWatchingPlay: ((ContinueWatchingItem) -> Unit)?,
    onFolderClick: ((collectionId: String, folderId: String) -> Unit)?,
    onContinueWatchingSourceShortcut: ((ContinueWatchingItem) -> Unit)? = null,
): ShelfInfoActions {
    val library by LibraryRepository.uiState.collectAsStateWithLifecycle()
    // The same rule as the classic Continue watching card's hover button (continueWatchingSourceShortcutKind).
    val cwPreferences by com.nuvio.app.features.watchprogress.ContinueWatchingPreferencesRepository.uiState
        .collectAsStateWithLifecycle()
    val playerSettings by com.nuvio.app.features.player.PlayerSettingsRepository.uiState.collectAsStateWithLifecycle()
    val sourceShortcutClick by rememberUpdatedState(onContinueWatchingSourceShortcut)
    val scope = rememberCoroutineScope()
    val posterClick by rememberUpdatedState(onPosterClick)
    val cwClick by rememberUpdatedState(onContinueWatchingPlay ?: onContinueWatchingClick)
    val folderClick by rememberUpdatedState(onFolderClick)

    fun previewOf(card: ShelfCard): MetaPreview? = when (card) {
        is ShelfCard.Meta -> card.item
        is ShelfCard.Progress -> card.item.let {
            MetaPreview(
                id = it.parentMetaId,
                type = it.parentMetaType,
                name = it.title,
                poster = it.poster,
                banner = it.background,
                logo = it.logo,
            )
        }
        is ShelfCard.Folder -> null
    }

    return ShelfInfoActions(
        playEpisode = { card, episode -> playShowEpisode(card, episode, previewOf(card), cwClick) },
        sourceShortcut = onContinueWatchingSourceShortcut?.let {
            { card -> (card as? ShelfCard.Progress)?.let { sourceShortcutClick?.invoke(it.item) } }
        },
        sourceShortcutLabel = { card ->
            (card as? ShelfCard.Progress)?.let {
                ShelfInfoRules.sourceShortcutLabel(
                    com.nuvio.app.features.home.components.continueWatchingSourceShortcutKind(
                        item = it.item,
                        mode = cwPreferences.sourceShortcut,
                        reuseLastLinkEnabled = playerSettings.streamReuseLastLinkEnabled,
                    ),
                )
            }
        },
        play = { card ->
            when (card) {
                is ShelfCard.Meta -> {
                    val item = card.item
                    if (item.type == "movie" && ShelfInfoRules.detailsKey(card) != null) {
                        val saved = WatchProgressRepository.uiState.value.entries
                            .firstOrNull { it.videoId == item.id }
                            ?.takeUnless { it.isCompleted }
                        RemotePlayerHub.playEpisode(
                            RemoteEpisodePlay(
                                type = item.type,
                                videoId = item.id,
                                parentMetaId = item.id,
                                parentMetaType = item.type,
                                title = item.name,
                                logo = item.logo,
                                poster = item.poster,
                                background = item.banner,
                                seasonNumber = null,
                                episodeNumber = null,
                                episodeTitle = null,
                                episodeThumbnail = null,
                                pauseDescription = item.description,
                                resumePositionMs = saved?.lastPositionMs,
                            ),
                        )
                    } else {
                        posterClick?.invoke(item)
                    }
                }
                is ShelfCard.Progress -> cwClick?.invoke(card.item)
                is ShelfCard.Folder -> folderClick?.invoke(card.collectionId, card.folder.id)
            }
        },
        trailer = { card ->
            val key = ShelfInfoRules.detailsKey(card)
            if (key != null) {
                scope.launch {
                    val meta = try {
                        MetaDetailsRepository.peek(key.first, key.second)
                            ?: MetaDetailsRepository.fetchHeroSummary(type = key.first, id = key.second)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Throwable) {
                        null
                    }
                    val trailer = meta?.trailers
                        ?.filter { it.seasonNumber == null || it.seasonNumber == 1 }
                        ?.let(::rankHeroTrailers)
                        ?.firstOrNull()
                    val videoId = trailer?.key?.let(::youTubeVideoId)
                    if (videoId == null) {
                        NuvioToastController.show("No trailer for ${card.title()}")
                    } else {
                        RemotePlayerHub.openYouTube(RemoteYouTubeOpen(videoId, fromPhone = false))
                    }
                }
            }
        },
        moreLikeThis = { card -> previewOf(card)?.let { posterClick?.invoke(it) } },
        addToList = { card ->
            previewOf(card)?.let { LibraryRepository.toggleSaved(it.toLibraryItem(savedAtEpochMs = 0L)) }
        },
        inList = { card ->
            val key = ShelfInfoRules.detailsKey(card)
            key != null && library.contains(key.second, key.first)
        },
    )
}

/**
 * One episode of a show from Info. The Continue watching card's own episode plays as the card does (the classic
 * home's Play: its resume and source rules); any other episode starts as the film Play does, through
 * [RemotePlayerHub.playEpisode], resuming where it stopped.
 */
private fun playShowEpisode(
    card: ShelfCard,
    episode: ShelfEpisode,
    preview: MetaPreview?,
    continueWatchingPlay: ((ContinueWatchingItem) -> Unit)?,
) {
    val own = (card as? ShelfCard.Progress)?.item
    if (own != null && continueWatchingPlay != null &&
        own.seasonNumber == episode.season && own.episodeNumber == episode.number
    ) {
        continueWatchingPlay(own)
        return
    }
    val key = ShelfInfoSeasonsRules.showKey(card) ?: return
    val saved = WatchProgressRepository.uiState.value.entries
        .filter {
            it.videoId == episode.videoId ||
                (it.parentMetaId == key.second && it.seasonNumber == episode.season && it.episodeNumber == episode.number)
        }
        .maxByOrNull { it.lastUpdatedEpochMs }
        ?.takeUnless { it.isEffectivelyCompleted }
    RemotePlayerHub.playEpisode(
        RemoteEpisodePlay(
            type = key.first,
            videoId = episode.videoId,
            parentMetaId = key.second,
            parentMetaType = key.first,
            title = preview?.name ?: card.title(),
            logo = preview?.logo,
            poster = preview?.poster,
            background = preview?.banner,
            seasonNumber = episode.season,
            episodeNumber = episode.number,
            episodeTitle = episode.title.ifEmpty { null },
            episodeThumbnail = episode.thumbnail,
            pauseDescription = episode.overview,
            resumePositionMs = saved?.lastPositionMs?.takeIf { it > 0L },
        ),
    )
}

/** A trailer's YouTube id: the key itself, or the v= of a watch link, or the last part of a youtu.be link. */
internal fun youTubeVideoId(key: String): String? {
    val k = key.trim()
    if (k.isEmpty()) return null
    if (!k.startsWith("http://") && !k.startsWith("https://")) return k
    Regex("""[?&]v=([A-Za-z0-9_-]{6,})""").find(k)?.let { return it.groupValues[1] }
    Regex("""youtu\.be/([A-Za-z0-9_-]{6,})""").find(k)?.let { return it.groupValues[1] }
    Regex("""/(?:embed|shorts)/([A-Za-z0-9_-]{6,})""").find(k)?.let { return it.groupValues[1] }
    return null
}
