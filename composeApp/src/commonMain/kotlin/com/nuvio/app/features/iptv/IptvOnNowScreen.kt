package com.nuvio.app.features.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.NuvioGlass
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.smoothBorder
import com.nuvio.app.features.livescores.LiveMatch
import com.nuvio.app.features.livescores.LiveScoreEngine
import com.nuvio.app.features.livescores.MatchPhase
import com.nuvio.app.features.tmdb.TmdbService
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * fork:live-tv-on-now - what IptvOnNow.kt works out, drawn in the Live TV tab's own glass: the wall of tiles
 * (live sport with scores, films, the usual channels) and the page a film or a recorded programme opens, with
 * Start from the beginning. IptvScreen owns the highlight and the keys; these only draw and report clicks.
 */

/** The programme page that is open: a film, or anything the provider can play from its start. */
internal data class IptvWallDetail(
    val channel: IptvMergedChannel,
    val entry: IptvEpgEntry?,
    val film: Boolean,
    val match: LiveMatch?,
)

/** One line of the programme page, top to bottom; the arrows walk them in this order. */
internal sealed interface IptvDetailAction {
    data class StartOver(val entry: IptvEpgEntry) : IptvDetailAction
    data object WatchLive : IptvDetailAction

    /** fork:live-tv-departure - tell me when [entry] starts; or [Switch] to the channel by itself; or take the reminder away. */
    data class Remind(val entry: IptvEpgEntry) : IptvDetailAction
    data class Switch(val entry: IptvEpgEntry) : IptvDetailAction
    data class CancelReminder(val reminder: IptvReminder) : IptvDetailAction

    /** A programme that has finished, still in the provider's recordings. */
    data class Earlier(val entry: IptvEpgEntry) : IptvDetailAction
}

/** The guide's programmes for a merged channel: the first copy the guide knows. */
internal fun iptvGuideEntries(playlist: IptvPlaylist, channel: IptvMergedChannel): List<IptvEpgEntry> =
    channel.sources.firstNotNullOfOrNull { source -> IptvEpgRepository.guideEntries(playlist, source).takeIf { it.isNotEmpty() } }.orEmpty()

/** How many finished programmes the page offers, newest first. */
private const val EARLIER_SHOWN = 5

internal fun iptvDetailActions(
    playlist: IptvPlaylist,
    detail: IptvWallDetail,
    now: Long,
    /** fork:live-tv-departure - null with the switch off. */
    reminders: List<IptvReminder>? = null,
): List<IptvDetailAction> = buildList {
    val entry = detail.entry
    if (reminders != null && entry != null && entry.startMs > now) {
        val set = IptvReminderRules.find(reminders, playlist.id, detail.channel, entry.startMs)
        if (set != null) {
            add(IptvDetailAction.CancelReminder(set))
        } else {
            add(IptvDetailAction.Remind(entry))
            add(IptvDetailAction.Switch(entry))
        }
    }
    if (entry != null && IptvCatchup.available(playlist, detail.channel, entry, now)) add(IptvDetailAction.StartOver(entry))
    add(IptvDetailAction.WatchLive)
    val source = IptvCatchup.source(playlist, detail.channel) ?: return@buildList
    val from = now - minOf(source.archiveDays * 24 * 3_600_000L, 12 * 3_600_000L)
    iptvGuideEntries(playlist, detail.channel)
        .filter { it.endMs <= now && it.startMs >= from && it != entry }
        .takeLast(EARLIER_SHOWN)
        .asReversed()
        .forEach { add(IptvDetailAction.Earlier(it)) }
}

/** The wall for [playlist], from the guide as it stands; pure once the guide and the settings are read. */
internal fun buildIptvWall(
    playlist: IptvPlaylist,
    content: IptvPlaylistContent,
    merged: List<IptvMergedChannel>,
    settings: IptvSettings,
    matches: List<LiveMatch>,
    now: Long,
    /** fork:live-tv-triage - the categories the triage kept; null = all. The viewer's own channels are never left out. */
    shownCategories: Set<String>? = null,
): IptvWallModel {
    val categoryNames = content.categoryNames
    val channels = merged.mapNotNull { channel ->
        if (shownCategories != null && channel.sources.none { it.categoryId in shownCategories }) return@mapNotNull null
        val around = iptvGuideEntries(playlist, channel).filter { it.endMs > now - 5 * 60_000L && it.startMs < now + 12 * 3_600_000L }
        if (around.isEmpty()) null else IptvChannelNow(channel, around, categoryNames[channel.primary.categoryId].orEmpty())
    }
    val byRef = HashMap<String, IptvMergedChannel>()
    merged.forEach { channel -> channel.sources.forEach { byRef[IptvRepository.channelRef(playlist.id, it.key)] = channel } }
    val prefix = IptvRepository.channelRef(playlist.id, "")
    // What is usually on at this hour, then the starred channels, then the last ones watched.
    val likely = IptvUsuals.rank(settings.plays.filter { it.ref.startsWith(prefix) }, now) + settings.favourites + settings.recents
    val usuals = likely.asSequence().mapNotNull { byRef[it] }.distinctBy { it.primary.uid }.take(8).toList()
    // fork:live-tv-departure - the board looks at the lineup's channels when there is one, else at every channel.
    val soon = if (settings.departure) {
        val lineupKeys = settings.lineup[playlist.id]?.toHashSet()?.takeIf { settings.lineupOn && it.isNotEmpty() }
        IptvDeparture.buckets(channels, now, only = lineupKeys?.let { keys -> { channel: IptvMergedChannel -> channel.sources.any { it.key in keys } } })
    } else {
        emptyList()
    }
    return IptvWall.build(channels, matches, usuals, now, soon)
}

/**
 * The wall's model, rebuilt off the UI thread when the guide, the scores or the viewer's lists change, and every
 * couple of minutes as programmes start and end. Null until the first one is ready.
 */
@Composable
internal fun rememberIptvWall(
    playlist: IptvPlaylist,
    content: IptvPlaylistContent?,
    merged: List<IptvMergedChannel>?,
    settings: IptvSettings,
    now: Long,
    guideVersion: Int,
    shownCategories: Set<String>? = null,
): IptvWallModel? {
    val matches by LiveScoreEngine.followed.collectAsStateWithLifecycle()
    val tick = now / 120_000L
    val model = produceState<IptvWallModel?>(
        null,
        playlist.id, merged, guideVersion, tick, matches, settings.plays.size, settings.favourites, settings.recents, shownCategories,
        settings.departure, settings.lineup[playlist.id],
    ) {
        value = if (content == null || merged == null) {
            null
        } else {
            withContext(Dispatchers.Default) { buildIptvWall(playlist, content, merged, settings, matches, System.currentTimeMillis(), shownCategories) }
        }
    }
    return model.value
}

/** Film artwork and blurb from TMDB by title; nothing without a TMDB key, and the card then shows the channel's logo. */
internal object IptvFilmArt {
    data class Art(val poster: String?, val backdrop: String?, val overview: String?, val year: String?)

    private val none = Art(null, null, null, null)
    private val found = ConcurrentHashMap<String, Art>()

    private fun key(title: String): String = IptvOnNow.filmQuery(title).first.lowercase()

    /** What is already known, without a request. */
    fun peek(title: String): Art? = found[key(title)]?.takeIf { it !== none }

    suspend fun find(title: String): Art? {
        val key = key(title)
        found[key]?.let { return it.takeIf { art -> art !== none } }
        val (query, year) = IptvOnNow.filmQuery(title)
        if (query.isBlank()) return null
        val results = try {
            withContext(Dispatchers.Default) { TmdbService.searchTitles(query, mediaType = "movie", year = year) }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Throwable) {
            return null // not remembered: the next look may have a connection
        }
        val best = results.firstOrNull { it.title.equals(query, ignoreCase = true) } ?: results.firstOrNull()
        val art = if (best == null) {
            none
        } else {
            Art(
                poster = best.posterPath?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/w342$it" },
                backdrop = best.backdropPath?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/w780$it" },
                overview = best.overview?.takeIf { it.isNotBlank() },
                year = best.releaseDate?.take(4)?.takeIf { it.length == 4 },
            )
        }
        found[key] = art
        return art.takeIf { it !== none }
    }
}

@Composable
private fun rememberFilmArt(title: String): IptvFilmArt.Art? =
    produceState<IptvFilmArt.Art?>(IptvFilmArt.peek(title), title) { value = IptvFilmArt.find(title) }.value

private val SportTileHeight = 116.dp
private val FilmTileHeight = 164.dp
private val ChannelTileHeight = 76.dp

private fun tileHeight(item: IptvWallItem): Dp = when (item) {
    is IptvWallItem.Match -> SportTileHeight
    is IptvWallItem.Programme -> if (item.film) FilmTileHeight else SportTileHeight
    is IptvWallItem.Channel -> ChannelTileHeight
}

private fun lengthText(ms: Long): String {
    val minutes = (ms / 60_000L).toInt()
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
}

private fun minutesText(ms: Long): String = "${(ms / 60_000L).coerceAtLeast(1)} min"

/** "71 min in" for something on, "Starts in 19 min" for something about to be. */
private fun stateText(entry: IptvEpgEntry, now: Long): String =
    if (entry.startMs <= now) "${minutesText(now - entry.startMs)} in" else "Starts in ${minutesText(entry.startMs - now)}"

/**
 * The wall: titled rows of tiles. [focusRow] and [focusCol] are the keyboard's highlight (row -1 = none);
 * [onActivate] reports a click with the tile's place, so the highlight can follow the mouse.
 */
@Composable
internal fun IptvWallContent(
    playlist: IptvPlaylist,
    rows: List<IptvWallRow>,
    now: Long,
    listState: LazyListState,
    focusRow: Int,
    focusCol: Int,
    onActivate: (row: Int, col: Int, item: IptvWallItem) -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(rows, key = { _, row -> row.items.first().key }) { rowIndex, row ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (row.title != null) {
                    Row(
                        modifier = Modifier.padding(top = if (rowIndex == 0) 0.dp else 6.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = row.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = tokens.colors.textPrimary,
                        )
                        if (row.note != null) {
                            Text(text = row.note, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.items.forEachIndexed { col, item ->
                        Box(modifier = Modifier.weight(1f)) {
                            IptvWallTile(
                                playlist = playlist,
                                item = item,
                                now = now,
                                focused = rowIndex == focusRow && col == focusCol,
                                onClick = { onActivate(rowIndex, col, item) },
                            )
                        }
                    }
                    // A short last row keeps its tiles the width of the rows above.
                    repeat(row.columns - row.items.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun IptvWallTile(playlist: IptvPlaylist, item: IptvWallItem, now: Long, focused: Boolean, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = NuvioGlass.Shape
    // A match the guide has no channel for can be read but not played.
    val dead = item is IptvWallItem.Match && item.programme == null
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(tileHeight(item))
            .iptvGlass(shape, raised = focused || (hovered && !dead))
            // Clipped after the edge: a clip is not anti-aliased here and stair-steps an edge drawn inside it.
            .clip(shape)
            .then(if (focused) Modifier.smoothBorder(1.5.dp, tokens.colors.borderFocus, shape) else Modifier)
            .clickable(interactionSource = interaction, indication = null, enabled = !dead, onClick = onClick),
    ) {
        when (item) {
            is IptvWallItem.Match -> MatchTile(item, now)
            is IptvWallItem.Programme -> if (item.film) {
                FilmTile(item.programme, now, canStartOver = IptvCatchup.available(playlist, item.programme.channel, item.programme.entry, now))
            } else {
                SportProgrammeTile(item.programme, now)
            }
            is IptvWallItem.Channel -> ChannelTile(item)
        }
    }
}

@Composable
private fun LiveDot() {
    Box(Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.nuvio.colors.accent))
}

@Composable
internal fun ProgressLine(progress: Float, height: Dp = 3.dp) {
    val tokens = MaterialTheme.nuvio
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(2.dp)),
        color = tokens.colors.accent,
        trackColor = tokens.colors.borderSubtle,
        drawStopIndicator = {},
    )
}

/** Where a match stands: its clock while it is on, its kick-off time before. Second: whether it is on. */
private fun matchState(match: LiveMatch): Pair<String, Boolean> = when (match.phase) {
    MatchPhase.Live -> (match.clock?.takeIf { it.isNotBlank() } ?: "Live") to true
    MatchPhase.HalfTime -> "Half-time" to true
    MatchPhase.Before -> (match.startEpochMs?.let(::formatTime) ?: "Today") to false
    MatchPhase.Finished -> "Full-time" to false
    MatchPhase.Postponed -> "Postponed" to false
}

@Composable
private fun ScoreLine(name: String, score: Int?) {
    val tokens = MaterialTheme.nuvio
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = tokens.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (score != null) {
            Text(
                text = score.toString(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = tokens.colors.textPrimary,
            )
        }
    }
}

@Composable
private fun TileTopLine(left: String, right: String, live: Boolean) {
    val tokens = MaterialTheme.nuvio
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = left,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = tokens.colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (live) LiveDot()
        Text(
            text = right,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = tokens.colors.textPrimary,
            maxLines = 1,
        )
    }
}

@Composable
private fun MatchTile(item: IptvWallItem.Match, now: Long) {
    val tokens = MaterialTheme.nuvio
    val match = item.match
    val (state, live) = matchState(match)
    val showScore = match.phase != MatchPhase.Before && match.phase != MatchPhase.Postponed
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        TileTopLine(match.competitionName ?: match.sport.label, state, live)
        ScoreLine(match.home.shortName, match.homeScore.takeIf { showScore })
        ScoreLine(match.away.shortName, match.awayScore.takeIf { showScore })
        Text(
            text = item.programme?.channel?.primary?.name ?: "Not found in the guide",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SportProgrammeTile(programme: IptvWallProgramme, now: Long) {
    val tokens = MaterialTheme.nuvio
    val entry = programme.entry
    val onNow = programme.onNow(now)
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // fork:live-tv-departure - inside the hour a coming programme says how long it is to the start.
        val soonText = if (!onNow && entry.startMs - now <= 60 * 60_000L) IptvDeparture.countdown(entry.startMs - now) else null
        TileTopLine(programme.channel.primary.name, if (onNow) "On now" else soonText ?: formatTime(entry.startMs), live = false)
        Text(
            text = entry.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = tokens.colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (onNow) {
            ProgressLine(entry.progress(now))
        } else {
            Text(
                text = "${formatTime(entry.startMs)} to ${formatTime(entry.endMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textMuted,
            )
        }
    }
}

/** A channel's logo in the dark well the channel list uses; an icon when there is none. */
@Composable
private fun LogoWell(logo: String?, modifier: Modifier, icon: ImageVector = Icons.Rounded.LiveTv) {
    val tokens = MaterialTheme.nuvio
    Box(
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(tokens.colors.background.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        if (logo != null) {
            NuvioAsyncImage(model = logo, contentDescription = null, modifier = Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
        } else {
            Icon(icon, contentDescription = null, tint = tokens.colors.textMuted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun StartOverLabel(text: String, strong: Boolean) {
    val tokens = MaterialTheme.nuvio
    val colour = if (strong) tokens.colors.textPrimary else tokens.colors.textSecondary
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(Icons.Rounded.Replay, contentDescription = null, tint = colour, modifier = Modifier.size(14.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = colour, maxLines = 1)
    }
}

@Composable
private fun FilmTile(programme: IptvWallProgramme, now: Long, canStartOver: Boolean) {
    val tokens = MaterialTheme.nuvio
    val entry = programme.entry
    val art = rememberFilmArt(entry.title)
    val title = remember(entry.title) { IptvOnNow.filmQuery(entry.title).first.ifBlank { entry.title } }
    Row(modifier = Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier
                .width(92.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(12.dp))
                .background(tokens.colors.background.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                art?.poster != null -> NuvioAsyncImage(
                    model = art.poster,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                programme.channel.primary.logo != null -> NuvioAsyncImage(
                    model = programme.channel.primary.logo,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(10.dp),
                    contentScale = ContentScale.Fit,
                )
                else -> Icon(Icons.Rounded.Movie, contentDescription = null, tint = tokens.colors.textMuted, modifier = Modifier.size(24.dp))
            }
        }
        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = tokens.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(art?.year, lengthText(entry.endMs - entry.startMs)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textMuted,
                maxLines = 1,
            )
            Text(
                text = programme.channel.primary.name,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            Text(text = stateText(entry, now), style = MaterialTheme.typography.bodySmall, color = tokens.colors.textSecondary, maxLines = 1)
            if (programme.onNow(now)) {
                ProgressLine(entry.progress(now))
                if (canStartOver) StartOverLabel("Start over", strong = false)
            } else {
                Text(text = "Ends ${formatTime(entry.endMs)}", style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ChannelTile(item: IptvWallItem.Channel) {
    val tokens = MaterialTheme.nuvio
    val channel = item.channel.primary
    val name = channel.name.ifBlank { "Channel ${channel.num}" }
    val now = System.currentTimeMillis()
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LogoWell(channel.logo, Modifier.size(width = 56.dp, height = 36.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = item.entry?.title ?: name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = tokens.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.entry != null) ProgressLine(item.entry.progress(now))
            Text(
                text = listOfNotNull(name.takeIf { item.entry != null }, item.channel.sourcesLabel()).joinToString(" · ").ifEmpty { "No guide information" },
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One line of the programme page: accent glass for the thing the page is for, plain glass for the rest. */
@Composable
private fun DetailActionRow(icon: ImageVector, label: String, note: String?, accent: Boolean, focused: Boolean, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = NuvioGlass.Shape
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .iptvGlass(shape, raised = focused || hovered, selected = accent && !hovered, selectedTint = tokens.colors.accent.copy(alpha = 0.42f))
            .then(if (accent) Modifier.border(1.dp, tokens.colors.accent.copy(alpha = 0.85f), shape) else Modifier)
            // Clipped after the edge: a clip is not anti-aliased here and stair-steps an edge drawn inside it.
            .clip(shape)
            .then(if (focused) Modifier.smoothBorder(1.5.dp, tokens.colors.borderFocus, shape) else Modifier)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tokens.colors.textPrimary, modifier = Modifier.size(18.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = tokens.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (note != null) {
            Text(text = note, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textSecondary, maxLines = 1)
        }
    }
}

/**
 * The page a film or a recorded programme opens: what it is and how far in, then Start from the beginning, Watch
 * live, and the channel's earlier programmes when the provider still has them. [focusedAction]: the keyboard's
 * highlight in [actions], -1 for none.
 */
@Composable
internal fun IptvProgrammeDetail(
    playlist: IptvPlaylist,
    detail: IptvWallDetail,
    actions: List<IptvDetailAction>,
    now: Long,
    focusedAction: Int,
    onAction: (IptvDetailAction) -> Unit,
    onBack: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val entry = detail.entry
    val channel = detail.channel.primary
    val channelName = channel.name.ifBlank { "Channel ${channel.num}" }
    val art = if (detail.film && entry != null) rememberFilmArt(entry.title) else null
    val match = detail.match
    val title = when {
        match != null -> "${match.home.shortName} v ${match.away.shortName}"
        entry == null -> channelName
        detail.film -> IptvOnNow.filmQuery(entry.title).first.ifBlank { entry.title }
        else -> entry.title
    }
    val onNow = entry != null && entry.startMs <= now && entry.endMs > now
    val canStartOver = actions.any { it is IptvDetailAction.StartOver }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IptvHeaderButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", focused = false, onClick = onBack)
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = tokens.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(modifier = Modifier.weight(0.42f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .iptvGlass(NuvioGlass.Shape)
                        .clip(NuvioGlass.Shape),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        art?.backdrop != null -> NuvioAsyncImage(
                            model = art.backdrop,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        channel.logo != null -> NuvioAsyncImage(
                            model = channel.logo,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().padding(40.dp),
                            contentScale = ContentScale.Fit,
                        )
                        else -> Icon(Icons.Rounded.LiveTv, contentDescription = null, tint = tokens.colors.textMuted, modifier = Modifier.size(40.dp))
                    }
                }
                art?.overview?.let { overview ->
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.colors.textSecondary,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(0.58f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = listOfNotNull(channelName, detail.channel.sourcesLabel(), art?.year, entry?.let { lengthText(it.endMs - it.startMs) })
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (match != null && match.homeScore != null && match.awayScore != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val (state, live) = matchState(match)
                        if (live) LiveDot()
                        Text(
                            text = "${match.homeScore} - ${match.awayScore}  ·  $state",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = tokens.colors.textPrimary,
                        )
                    }
                }
                if (entry != null) {
                    Text(
                        text = if (onNow) {
                            "Started ${formatTime(entry.startMs)} · ${stateText(entry, now)} · ends ${formatTime(entry.endMs)}"
                        } else {
                            "${formatTime(entry.startMs)} to ${formatTime(entry.endMs)}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.colors.textPrimary,
                    )
                    if (onNow) ProgressLine(entry.progress(now), height = 4.dp)
                }
                Spacer(Modifier.height(4.dp))
                var earlierTitled = false
                actions.forEachIndexed { index, action ->
                    val focused = index == focusedAction
                    when (action) {
                        is IptvDetailAction.StartOver -> DetailActionRow(
                            icon = Icons.Rounded.Replay,
                            label = "Start from the beginning",
                            note = formatTime(action.entry.startMs),
                            accent = true,
                            focused = focused,
                            onClick = { onAction(action) },
                        )
                        is IptvDetailAction.Remind -> DetailActionRow(
                            icon = Icons.Rounded.Notifications,
                            label = "Remind me when it starts",
                            note = "a card ${IptvReminderRules.LEAD_MS / 60_000L} min before ${formatTime(action.entry.startMs)}",
                            accent = true,
                            focused = focused,
                            onClick = { onAction(action) },
                        )
                        is IptvDetailAction.Switch -> DetailActionRow(
                            icon = Icons.Rounded.PlayArrow,
                            label = "Switch to it at ${formatTime(action.entry.startMs)}",
                            note = "Nuvio puts the channel on by itself",
                            accent = false,
                            focused = focused,
                            onClick = { onAction(action) },
                        )
                        is IptvDetailAction.CancelReminder -> DetailActionRow(
                            icon = Icons.Rounded.NotificationsOff,
                            label = "Cancel the ${if (action.reminder.autoSwitch) "switch" else "reminder"} for ${formatTime(action.reminder.startMs)}",
                            note = if (action.reminder.autoSwitch) "set to switch by itself" else "set to remind you",
                            accent = true,
                            focused = focused,
                            onClick = { onAction(action) },
                        )
                        IptvDetailAction.WatchLive -> DetailActionRow(
                            icon = Icons.Rounded.PlayArrow,
                            label = if (onNow) "Watch live" else "Go to the channel",
                            note = if (onNow && entry != null) stateText(entry, now) else null,
                            accent = !canStartOver,
                            focused = focused,
                            onClick = { onAction(action) },
                        )
                        is IptvDetailAction.Earlier -> {
                            if (!earlierTitled) {
                                earlierTitled = true
                                Text(
                                    text = "Earlier on $channelName",
                                    modifier = Modifier.padding(top = 8.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = tokens.colors.textPrimary,
                                )
                            }
                            DetailActionRow(
                                icon = Icons.Rounded.PlayArrow,
                                label = action.entry.title,
                                note = formatTime(action.entry.startMs),
                                accent = false,
                                focused = focused,
                                onClick = { onAction(action) },
                            )
                        }
                    }
                }
                if (onNow && !canStartOver) {
                    Text(
                        text = if (playlist.type == IptvPlaylistType.Xtream) {
                            "This provider keeps no catch-up for $channelName, so it can only be joined live."
                        } else {
                            "Start over needs an Xtream account with catch-up; this playlist can only be joined live."
                        },
                        modifier = Modifier.padding(top = 4.dp).alpha(0.9f),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.colors.textMuted,
                    )
                }
            }
        }
    }
}
