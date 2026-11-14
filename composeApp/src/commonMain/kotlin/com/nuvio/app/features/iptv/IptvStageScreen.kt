@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.nuvio.app.features.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.NuvioGlass
import com.nuvio.app.core.ui.NuvioTextField
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.navigationKey
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.smoothBorder
import com.nuvio.app.features.settings.settingsSkyBackground
import com.nuvio.app.features.tonight.TonightSettingsRepository
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * fork:live-tv-stage - Live TV as stages, one decision each, as the mockups have them: a centred heading, the strip of
 * stages under it, then two 296 dp glass side panels round a centre (or three equal columns). Every row, tile and card
 * has a fixed size; nothing moves or resizes with what it holds. One highlight, moved by the arrows across the panels
 * (IptvStageFocusRules); OK acts, holding OK opens a channel's menu, Back goes to the strip.
 */

internal class IptvStageChannel(val number: Int, val channel: IptvMergedChannel, val now: IptvEpgEntry?, val next: IptvEpgEntry?)

internal class IptvStageModel(
    val total: Int,
    val mergedCount: Int,
    val lineup: List<IptvStageChannel>,
    val lineupUids: Set<Int>,
    val usualKeys: Set<String>,
    val three: IptvThree,
    val canReshuffle: Boolean,
    val board: IptvMinutesBoard,
    val upcoming: List<IptvWallProgramme>,
    val earlier: List<IptvWallProgramme>,
    val follows: List<IptvFollowResult>,
    val followSuggestions: List<IptvWallProgramme>,
    val order: List<IptvWallProgramme>,
    val orderApproved: Boolean,
    val orderLeftOut: IptvWallProgramme?,
    val bedMs: Long,
    val ask: IptvAskAnswer?,
)

/** The stage's data, from the guide as it stands. Heavy (every channel's guide): run it off the UI thread. */
internal fun buildIptvStage(
    playlist: IptvPlaylist,
    content: IptvPlaylistContent,
    merged: List<IptvMergedChannel>,
    lineup: List<IptvMergedChannel>,
    settings: IptvSettings,
    now: Long,
    askText: String,
    askSkips: Int,
    orderExclude: Set<String>,
): IptvStageModel {
    val categoryNames = content.categoryNames
    val channels = merged.mapNotNull { channel ->
        val around = iptvGuideEntries(playlist, channel).filter { it.endMs > now - 5 * 60_000L && it.startMs < now + 12 * 3_600_000L }
        if (around.isEmpty()) null else IptvChannelNow(channel, around, categoryNames[channel.primary.categoryId].orEmpty())
    }
    val byRef = HashMap<String, IptvMergedChannel>()
    merged.forEach { channel -> channel.sources.forEach { byRef[IptvRepository.channelRef(playlist.id, it.key)] = channel } }
    val prefix = IptvRepository.channelRef(playlist.id, "")
    val likely = IptvUsuals.rank(settings.plays.filter { it.ref.startsWith(prefix) }, now) + settings.favourites + settings.recents
    val usuals = likely.asSequence().mapNotNull { byRef[it] }.distinctBy { it.primary.uid }.take(8).toList()
    val usualKeys = usuals.map { it.primary.key }.toHashSet()
    val lineupUids = lineup.mapTo(HashSet()) { it.primary.uid }
    val inLineup: ((IptvMergedChannel) -> Boolean)? = lineupUids.takeIf { it.isNotEmpty() }?.let { uids -> { channel: IptvMergedChannel -> channel.primary.uid in uids } }
    val ownUids = if (lineupUids.isNotEmpty()) lineupUids else (usuals + (settings.favourites + settings.recents).mapNotNull { byRef[it] }).mapTo(HashSet()) { it.primary.uid }
    val own = merged.filter { it.primary.uid in ownUids }

    val past = own.flatMap { channel ->
        iptvGuideEntries(playlist, channel).filter { it.endMs <= now && it.startMs >= now - 24 * 3_600_000L }.map { IptvWallProgramme(channel, it) }
    }.filter { IptvCatchup.available(playlist, it.channel, it.entry, now) }
    val night = IptvDecide.nightOf(now)
    val bedMs = IptvRunningOrder.bedtimeMs(now, TonightSettingsRepository.state.value.bedMinuteOfDay)
    val three = IptvDecide.three(channels, usuals, inLineup, now, IptvDecide.rotationFor(settings.shuffle, night), endsBy = bedMs)
    val board = IptvDecide.minutes(channels, past, now, settings.minutes, perColumn = 6)
    val upcoming = channels.flatMap { on ->
        on.entries.filter { it.startMs > now && it.startMs <= now + 60 * 60_000L }.map { IptvWallProgramme(on.channel, it) }
    }.sortedBy { it.entry.startMs }.take(400)
    val earlier = IptvDecide.earlier(past, settings.follows, now, limit = 40)
    val ahead = if (settings.follows.isEmpty()) emptyList() else merged.flatMap { channel ->
        iptvGuideEntries(playlist, channel).filter { it.endMs > now }.map { IptvWallProgramme(channel, it) }
    }
    val follows = IptvDecide.follows(settings.follows, ahead, inLineup, limit = 3)
    val ownNow = channels.filter { it.channel.primary.uid in ownUids }
    val suggestions = ownNow.mapNotNull { on -> on.current(now)?.let { IptvWallProgramme(on.channel, it) } }
        .filter { !IptvDecide.isFollowed(settings.follows, it.entry.title) && it.entry.endMs - it.entry.startMs >= 20 * 60_000L }
        .distinctBy { IptvDecide.normalise(it.entry.title) }
        .take(5)
    val ownGuide = ownNow.flatMap { on -> on.entries.map { IptvWallProgramme(on.channel, it) } }
    val order = IptvRunningOrder.build(ownGuide, settings.follows, usualKeys, now, bedMs, orderExclude)
    val allGuide = channels.flatMap { on -> on.entries.filter { it.startMs <= now + IptvOnNow.SOON_MS && it.endMs > now }.map { IptvWallProgramme(on.channel, it) } }
    val ask = if (askText.isBlank()) null else IptvAsk.answer(askText, allGuide, settings.follows, usualKeys + lineup.map { it.primary.key }, now, askSkips)
    val guideByUid = channels.associateBy { it.channel.primary.uid }
    return IptvStageModel(
        total = content.channels.size,
        mergedCount = merged.size,
        lineup = lineup.mapIndexed { index, channel ->
            val on = guideByUid[channel.primary.uid]
            IptvStageChannel(index + 1, channel, on?.current(now), on?.next(now))
        },
        lineupUids = lineupUids,
        usualKeys = usualKeys,
        three = three,
        canReshuffle = IptvDecide.canReshuffle(settings.shuffle, night),
        board = board,
        upcoming = upcoming,
        earlier = earlier,
        follows = follows,
        followSuggestions = suggestions,
        order = order,
        orderApproved = IptvRunningOrder.approved(playlist.id, order, settings.reminders),
        orderLeftOut = IptvRunningOrder.pastBedtime(ownGuide, settings.follows, usualKeys, now, bedMs),
        bedMs = bedMs,
        ask = ask,
    )
}

private fun count(n: Int): String = NumberFormat.getIntegerInstance(Locale.UK).format(n)

private val StageSidePanel = 296.dp
private val StageRowHeight = 58.dp
private val StageBackKeys = setOf(Key.Escape, Key.Back, Key.Backspace)
private val StageKeys = setOf(
    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.Enter, Key.NumPadEnter, Key.PageUp, Key.PageDown,
)
private const val HOLD_MS = 550L
private val BedChoices = listOf(22 * 60 + 30, 23 * 60 + 30, 30)

private fun digitOf(key: Key): Int? = when (key) {
    Key.Zero, Key.NumPad0 -> 0
    Key.One, Key.NumPad1 -> 1
    Key.Two, Key.NumPad2 -> 2
    Key.Three, Key.NumPad3 -> 3
    Key.Four, Key.NumPad4 -> 4
    Key.Five, Key.NumPad5 -> 5
    Key.Six, Key.NumPad6 -> 6
    Key.Seven, Key.NumPad7 -> 7
    Key.Eight, Key.NumPad8 -> 8
    Key.Nine, Key.NumPad9 -> 9
    else -> null
}

@Composable
internal fun IptvStageScreen(
    playlist: IptvPlaylist,
    content: IptvPlaylistContent,
    merged: List<IptvMergedChannel>,
    lineup: List<IptvMergedChannel>,
    settings: IptvSettings,
    alternates: Map<Int, List<IptvChannel>>,
    guideVersion: Int,
    modifier: Modifier = Modifier,
    topChromePadding: Dp? = null,
    onAllChannels: () -> Unit,
    onTriage: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    var stage by rememberSaveable(playlist.id) { mutableStateOf(IptvStage.Three.name) }
    val current = IptvStage.entries.firstOrNull { it.name == stage } ?: IptvStage.Three
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    var askText by rememberSaveable(playlist.id) { mutableStateOf("") }
    var askSkips by remember(askText) { mutableIntStateOf(0) }
    var orderExclude by remember { mutableStateOf(emptySet<String>()) }
    var followText by remember { mutableStateOf("") }
    var soonKinds by remember { mutableStateOf(IptvSoonKind.entries.toSet()) }
    var soonLineupOnly by remember { mutableStateOf(true) }
    var soonWindow by remember { mutableIntStateOf(30) }
    var earlierDay by remember { mutableIntStateOf(0) }
    var soonSel by remember { mutableIntStateOf(0) }
    var earlierSel by remember { mutableIntStateOf(0) }
    var orderSel by remember { mutableIntStateOf(0) }
    remember { TonightSettingsRepository.ensureLoaded() }
    val tonightSettings by TonightSettingsRepository.state.collectAsStateWithLifecycle()
    val bedMinute = tonightSettings.bedMinuteOfDay

    val model by produceState<IptvStageModel?>(
        null,
        playlist.id, merged, lineup, guideVersion, now / 60_000L, settings.plays.size, settings.favourites, settings.recents,
        settings.follows, settings.shuffle, settings.minutes, settings.reminders, askText, askSkips, orderExclude, bedMinute,
    ) {
        value = withContext(Dispatchers.Default) {
            buildIptvStage(playlist, content, merged, lineup, settings, System.currentTimeMillis(), askText, askSkips, orderExclude)
        }
    }

    val zapList = remember(lineup, merged) { (lineup.ifEmpty { merged }).map { it.primary } }
    fun play(channel: IptvMergedChannel) {
        IptvPlaybackRequests.play(playlist, channel.primary, zapList = zapList, alternates = alternates)
    }
    fun playFromStart(p: IptvWallProgramme) {
        if (!IptvPlaybackRequests.playCatchup(playlist, p.channel, p.entry, zapList, alternates)) play(p.channel)
    }
    fun remind(p: IptvWallProgramme, autoSwitch: Boolean) {
        IptvRepository.addReminder(
            IptvReminder(
                playlistId = playlist.id,
                channelKey = p.channel.primary.key,
                channelName = p.channel.primary.name.ifBlank { "Channel ${p.channel.primary.num}" },
                title = p.entry.title,
                startMs = p.entry.startMs,
                endMs = p.entry.endMs,
                autoSwitch = autoSwitch,
            ),
        )
        NuvioToastController.show(
            message = if (autoSwitch) "Nuvio switches to ${p.channel.primary.name} at ${IptvStageRules.clock(p.entry.startMs)}" else "Reminder set for ${p.entry.title}",
            durationMillis = 3500L,
        )
    }
    /** The one thing OK does with a programme: on now plays, not begun tunes in at its start, over plays from the start. */
    fun take(p: IptvWallProgramme) {
        val t = System.currentTimeMillis()
        when {
            p.entry.endMs <= t -> playFromStart(p)
            p.entry.startMs > t + 60_000L -> remind(p, autoSwitch = true)
            else -> play(p.channel)
        }
    }

    // Your 40's channel menu (hold OK), and typed channel numbers.
    var menuChannel by remember { mutableStateOf<IptvMergedChannel?>(null) }
    var menuIndex by remember { mutableIntStateOf(0) }
    var typed by remember { mutableStateOf("") }
    LaunchedEffect(typed) {
        if (typed.isEmpty()) return@LaunchedEffect
        delay(1_200)
        model?.lineup?.getOrNull(typed.toInt() - 1)?.let { play(it.channel) }
        typed = ""
    }
    val storedLineup = settings.lineup[playlist.id].orEmpty()
    val menuActions = remember(menuChannel, storedLineup) {
        menuChannel?.let { IptvLineupAction.forChannel(IptvLineupRules.position(storedLineup, it), storedLineup.size) }.orEmpty()
    }
    fun runMenu(action: IptvLineupAction) {
        val whole = menuChannel ?: return
        when (action) {
            IptvLineupAction.MoveUp -> IptvRepository.setLineup(playlist.id, IptvLineupRules.move(storedLineup, whole, -1))
            IptvLineupAction.MoveDown -> IptvRepository.setLineup(playlist.id, IptvLineupRules.move(storedLineup, whole, 1))
            IptvLineupAction.Add -> IptvLineupRules.add(storedLineup, whole)?.let { IptvRepository.setLineup(playlist.id, it) }
            IptvLineupAction.Remove -> IptvRepository.setLineup(playlist.id, IptvLineupRules.remove(storedLineup, whole))
            IptvLineupAction.Never -> IptvRepository.neverAgain(playlist.id, whole)
        }
        menuChannel = null
    }

    // ---- What can be highlighted on each stage --------------------------------------------------------------
    val m = model
    val soonLists = remember(m, soonKinds, soonLineupOnly, soonWindow, now) {
        if (m == null) emptyList<IptvWallProgramme>() to emptyList() else
            IptvStageRules.departures(m.upcoming, now, soonWindow, soonKinds, soonLineupOnly, m.lineupUids)
    }
    val soonRows = soonLists.first + soonLists.second
    val earlierDays = remember(m, now) { m?.let { IptvStageRules.byDay(it.earlier, now) }.orEmpty() }
    val earlierRows = earlierDays.getOrNull(earlierDay)?.second.orEmpty()
    val weekRows = remember(m) {
        m?.follows.orEmpty().flatMap { it.matches }.distinctBy { it.channel.primary.uid to it.entry.startMs }.sortedBy { it.entry.startMs }.take(8)
    }
    val threeCards = 3
    val askPick = m?.ask?.programme
    val layout: List<List<IptvStageGroup>> = when (current) {
        IptvStage.Three -> listOf(listOf(IptvStageGroup("cards", threeCards, columns = 3), IptvStageGroup("deal", 1)))
        IptvStage.Soon -> listOf(
            listOf(IptvStageGroup("kinds", 1 + IptvSoonKind.entries.size), IptvStageGroup("window", 3, columns = 3)),
            listOf(IptvStageGroup("departures", soonRows.size)),
            listOf(IptvStageGroup("soonActions", if (soonRows.isEmpty()) 0 else 2)),
        )
        IptvStage.Minutes -> listOf(
            listOf(IptvStageGroup("on", m?.board?.alreadyOn?.size ?: 0)),
            listOf(IptvStageGroup("dial", IptvDecide.MINUTE_CHOICES.size, columns = IptvDecide.MINUTE_CHOICES.size), IptvStageGroup("starting", m?.board?.starting?.size ?: 0)),
            listOf(IptvStageGroup("catchup", m?.board?.catchUp?.size ?: 0)),
        )
        IptvStage.Order -> listOf(
            listOf(IptvStageGroup("builtFrom", 0)),
            listOf(IptvStageGroup("order", m?.order?.size ?: 0)),
            listOf(IptvStageGroup("orderActions", 3), IptvStageGroup("bed", BedChoices.size, columns = BedChoices.size)),
        )
        IptvStage.Earlier -> listOf(
            listOf(IptvStageGroup("days", earlierDays.size)),
            listOf(IptvStageGroup("earlier", earlierRows.size)),
            listOf(IptvStageGroup("earlierActions", if (earlierRows.isEmpty()) 0 else 1)),
        )
        IptvStage.Following -> listOf(
            listOf(IptvStageGroup("followInput", 1), IptvStageGroup("suggest", m?.followSuggestions?.size ?: 0)),
            listOf(IptvStageGroup("follows", m?.follows?.size ?: 0)),
            listOf(IptvStageGroup("week", weekRows.size)),
        )
        IptvStage.Ask -> listOf(
            listOf(IptvStageGroup("asked", settings.asks.size)),
            listOf(IptvStageGroup("askInput", 1), IptvStageGroup("askActions", if (askPick == null) 0 else 2)),
            listOf(IptvStageGroup("why", 0)),
        )
        IptvStage.Lineup -> listOf(
            listOf(IptvStageGroup("lineupActions", 1)),
            listOf(IptvStageGroup("lineup", m?.lineup?.size ?: 0, columns = 5)),
            listOf(IptvStageGroup("keptOut", 0)),
        )
    }
    val strip = IptvStage.entries.map { it.name } + "all"
    var onStrip by remember { mutableStateOf(false) }
    var stripIndex by remember { mutableIntStateOf(0) }
    var keyboardActive by remember { mutableStateOf(false) }
    var focus by remember(current) { mutableStateOf<IptvStageFocus?>(null) }
    val defaultColumn = if (layout.size == 3) 1 else 0
    val at = (focus ?: IptvStageFocusRules.first(layout, defaultColumn))?.let { IptvStageFocusRules.clamp(layout, it) }
    fun isFocused(group: String, index: Int): Boolean {
        if (!keyboardActive || onStrip || at == null) return false
        val g = layout.getOrNull(at.column)?.getOrNull(at.group) ?: return false
        return g.id == group && at.item == index
    }
    // The side panels follow the highlighted row of the centre, and stay on it when the highlight moves to them.
    LaunchedEffect(at, onStrip) {
        if (at == null || onStrip) return@LaunchedEffect
        when (layout.getOrNull(at.column)?.getOrNull(at.group)?.id) {
            "departures" -> soonSel = at.item
            "earlier" -> earlierSel = at.item
            "order" -> orderSel = at.item
        }
    }

    var editing by remember { mutableStateOf<String?>(null) }
    val askFocus = remember { FocusRequester() }
    val followFocus = remember { FocusRequester() }
    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(10) {
            if (runCatching { rootFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(48)
        }
    }
    fun leaveEditing() {
        when (editing) {
            "ask" -> IptvRepository.rememberAsk(askText)
            "follow" -> if (followText.isNotBlank()) {
                IptvRepository.follow(followText)
                NuvioToastController.show(message = "Following ${followText.trim()}", durationMillis = 3000L)
                followText = ""
            }
        }
        editing = null
        runCatching { rootFocus.requestFocus() }
    }

    fun pickStage(name: String) {
        if (name == "all") {
            onAllChannels()
            return
        }
        stage = name
        focus = null
    }

    fun activate(group: String, index: Int, held: Boolean) {
        val md = model ?: return
        when (group) {
            "cards" -> listOf(md.three.safe, md.three.soon, md.three.wildcard).getOrNull(index)?.let(::take)
            "deal" -> if (md.canReshuffle) IptvRepository.reshuffleThree(IptvDecide.nightOf(System.currentTimeMillis()))
            "kinds" -> if (index == 0) soonLineupOnly = !soonLineupOnly else IptvSoonKind.entries.getOrNull(index - 1)?.let { kind ->
                soonKinds = if (kind in soonKinds && soonKinds.size > 1) soonKinds - kind else soonKinds + kind
            }
            "window" -> soonWindow = IptvStageRules.SOON_WINDOWS[index]
            "departures" -> soonRows.getOrNull(index)?.let { remind(it, autoSwitch = true) }
            "soonActions" -> soonRows.getOrNull(soonSel)?.let { p -> if (index == 0) remind(p, autoSwitch = true) else play(p.channel) }
            "on" -> md.board.alreadyOn.getOrNull(index)?.let { take(it.programme) }
            "starting" -> md.board.starting.getOrNull(index)?.let { take(it.programme) }
            "catchup" -> md.board.catchUp.getOrNull(index)?.let { playFromStart(it.programme) }
            "dial" -> IptvDecide.MINUTE_CHOICES.getOrNull(index)?.let(IptvRepository::setMinutes)
            "order" -> md.order.getOrNull(index)?.let(::take)
            "orderActions" -> when (index) {
                0 -> if (md.orderApproved) {
                    md.order.forEach { IptvRepository.removeReminder(IptvRunningOrder.reminderId(playlist.id, it)) }
                } else {
                    val t = System.currentTimeMillis()
                    md.order.forEach { if (it.entry.startMs > t + 60_000L) remind(it, autoSwitch = true) }
                    md.order.firstOrNull()?.takeIf { it.onNow(t) }?.let { play(it.channel) }
                }
                1 -> md.order.getOrNull(orderSel)?.let { orderExclude = orderExclude + IptvDecide.normalise(it.entry.title) }
                else -> {
                    md.order.forEach { IptvRepository.removeReminder(IptvRunningOrder.reminderId(playlist.id, it)) }
                    pickStage(IptvStage.Three.name)
                }
            }
            "bed" -> TonightSettingsRepository.setBedtime(BedChoices[index])
            "days" -> {
                earlierDay = index
                earlierSel = 0
            }
            "earlier" -> earlierRows.getOrNull(index)?.let(::playFromStart)
            "earlierActions" -> earlierRows.getOrNull(earlierSel)?.let(::playFromStart)
            "followInput" -> {
                editing = "follow"
                runCatching { followFocus.requestFocus() }
            }
            "suggest" -> md.followSuggestions.getOrNull(index)?.let {
                IptvRepository.follow(it.entry.title)
                NuvioToastController.show(message = "Following ${it.entry.title}", durationMillis = 3000L)
            }
            "follows" -> md.follows.getOrNull(index)?.let { result ->
                val first = result.matches.firstOrNull()
                when {
                    held -> IptvRepository.unfollow(result.text)
                    first == null -> Unit
                    first.onNow(System.currentTimeMillis()) -> play(first.channel)
                    else -> remind(first, autoSwitch = false)
                }
            }
            "week" -> weekRows.getOrNull(index)?.let(::take)
            "asked" -> settings.asks.getOrNull(index)?.let { askText = it.text }
            "askInput" -> {
                editing = "ask"
                runCatching { askFocus.requestFocus() }
            }
            "askActions" -> askPick?.let { p -> if (index == 0) take(p) else askSkips++ }
            "lineupActions" -> onTriage()
            "lineup" -> md.lineup.getOrNull(index)?.let { item ->
                if (held) {
                    menuChannel = item.channel
                    menuIndex = 0
                } else {
                    play(item.channel)
                }
            }
        }
    }

    var enterDownAt by remember { mutableLongStateOf(0L) }
    fun handleKey(key: Key): Boolean {
        if (onStrip) {
            when (key) {
                Key.DirectionLeft -> stripIndex = (stripIndex - 1).coerceAtLeast(0)
                Key.DirectionRight -> stripIndex = (stripIndex + 1).coerceAtMost(strip.lastIndex)
                Key.DirectionDown -> onStrip = false
                else -> return false
            }
            // The strip switches stages as the highlight moves over it, like tabs; "All channels" waits for OK.
            strip[stripIndex].takeIf { it != "all" }?.let { if (it != stage) pickStage(it) }
            return true
        }
        val start = at ?: run {
            if (key == Key.DirectionUp) {
                onStrip = true
                stripIndex = strip.indexOf(stage).coerceAtLeast(0)
            }
            return true
        }
        val move = when (key) {
            Key.DirectionUp -> IptvStageMove.Up
            Key.DirectionDown -> IptvStageMove.Down
            Key.DirectionLeft -> IptvStageMove.Left
            Key.DirectionRight -> IptvStageMove.Right
            Key.PageDown -> {
                repeat(5) { focus = IptvStageFocusRules.move(layout, focus ?: start, IptvStageMove.Down) ?: focus }
                return true
            }
            Key.PageUp -> {
                repeat(5) { IptvStageFocusRules.move(layout, focus ?: start, IptvStageMove.Up)?.let { focus = it } }
                return true
            }
            else -> return false
        }
        val next = IptvStageFocusRules.move(layout, start, move)
        if (next == null) {
            onStrip = true
            stripIndex = strip.indexOf(stage).coerceAtLeast(0)
        } else {
            focus = next
        }
        return true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .settingsSkyBackground(tokens.colors.background, tokens.colors.accent)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (editing != null) {
                    // The box types; only the keys that leave it are taken. Raw keys, so WASD does not swallow letters.
                    if (event.type == KeyEventType.KeyDown && event.key in setOf(Key.Escape, Key.Enter, Key.NumPadEnter, Key.DirectionDown)) {
                        leaveEditing()
                        keyboardActive = true
                        return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent false
                }
                val menuOpen = menuChannel != null
                if (event.key in StageBackKeys) {
                    if (!menuOpen && onStrip) return@onPreviewKeyEvent false
                    // Back acts as the key comes up, as it does across the app.
                    if (event.type == KeyEventType.KeyUp) {
                        when {
                            menuOpen -> menuChannel = null
                            else -> {
                                onStrip = true
                                stripIndex = strip.indexOf(stage).coerceAtLeast(0)
                            }
                        }
                    }
                    return@onPreviewKeyEvent true
                }
                if (current == IptvStage.Lineup && event.type == KeyEventType.KeyDown) {
                    digitOf(event.key)?.let { digit ->
                        if (typed.length < 3 && !(typed.isEmpty() && digit == 0)) typed += digit
                        return@onPreviewKeyEvent true
                    }
                }
                val navKey = event.navigationKey()
                val enter = navKey == Key.Enter || navKey == Key.NumPadEnter
                if (menuOpen) {
                    if (event.type == KeyEventType.KeyDown) {
                        when (navKey) {
                            Key.DirectionUp -> menuIndex = (menuIndex - 1).coerceAtLeast(0)
                            Key.DirectionDown -> menuIndex = (menuIndex + 1).coerceAtMost(menuActions.lastIndex)
                            Key.Enter, Key.NumPadEnter -> menuActions.getOrNull(menuIndex)?.let(::runMenu)
                            else -> Unit
                        }
                    }
                    return@onPreviewKeyEvent navKey in StageKeys
                }
                if (enter) {
                    when (event.type) {
                        KeyEventType.KeyDown -> if (enterDownAt == 0L) enterDownAt = System.currentTimeMillis()
                        KeyEventType.KeyUp -> {
                            val held = enterDownAt != 0L && System.currentTimeMillis() - enterDownAt >= HOLD_MS
                            enterDownAt = 0L
                            if (!keyboardActive) {
                                keyboardActive = true
                            } else if (onStrip) {
                                if (strip[stripIndex] == "all") onAllChannels() else onStrip = false
                            } else {
                                at?.let { f -> layout.getOrNull(f.column)?.getOrNull(f.group)?.let { activate(it.id, f.item, held) } }
                            }
                        }
                        else -> Unit
                    }
                    return@onPreviewKeyEvent true
                }
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent navKey in StageKeys
                if (!keyboardActive && navKey in StageKeys) {
                    // The first press only shows where the highlight is.
                    keyboardActive = true
                    return@onPreviewKeyEvent true
                }
                handleKey(navKey)
            }
            .padding(start = 32.dp, end = 32.dp, top = topChromePadding ?: 24.dp, bottom = 24.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            val (title, subtitle) = stageHeading(current, m, settings, now)
            Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = tokens.colors.textPrimary, maxLines = 1)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            StageStrip(
                labels = strip.map { name ->
                    when (name) {
                        "all" -> "All channels"
                        IptvStage.Minutes.name -> "${settings.minutes} minutes"
                        else -> IptvStage.valueOf(name).label
                    }
                },
                selected = strip.indexOf(stage),
                focused = if (keyboardActive && onStrip) stripIndex else -1,
                onPick = { index ->
                    keyboardActive = false
                    pickStage(strip[index])
                },
            )
            Spacer(Modifier.height(14.dp))
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // The side panels are always a pair: the same width, narrower on a narrow window.
                val side = (maxWidth * 0.24f).coerceIn(200.dp, StageSidePanel)
                if (m == null) {
                    StageMessage("Reading the guide...")
                } else {
                    when (current) {
                        IptvStage.Three -> ThreeStage(m, now, ::isFocused) { group, i -> keyboardActive = false; activate(group, i, false) }
                        IptvStage.Soon -> SoonStage(
                            m, now, side, soonLists, soonSel, soonKinds, soonLineupOnly, soonWindow, ::isFocused,
                        ) { group, i -> keyboardActive = false; if (group == "departures") soonSel = i; activate(group, i, false) }
                        IptvStage.Minutes -> MinutesStage(m, now, settings.minutes, ::isFocused) { group, i -> keyboardActive = false; activate(group, i, false) }
                        IptvStage.Order -> OrderStage(m, now, side, orderSel, settings.follows, bedMinute, ::isFocused) { group, i ->
                            keyboardActive = false
                            if (group == "order") orderSel = i
                            activate(group, i, false)
                        }
                        IptvStage.Earlier -> EarlierStage(m, now, side, earlierDays, earlierDay, earlierRows, earlierSel, ::isFocused) { group, i ->
                            keyboardActive = false
                            if (group == "earlier") earlierSel = i
                            activate(group, i, false)
                        }
                        IptvStage.Following -> FollowingStage(
                            m, now, side, weekRows, followText, { followText = it }, followFocus,
                            onEditing = { editing = "follow" }, isFocused = ::isFocused,
                            onLongClick = { i -> activate("follows", i, true) },
                        ) { group, i -> keyboardActive = false; activate(group, i, false) }
                        IptvStage.Ask -> AskStage(
                            m, now, side, settings.asks, askText, { askText = it }, askFocus,
                            onEditing = { editing = "ask" }, onSubmit = { leaveEditing() }, isFocused = ::isFocused,
                        ) { group, i -> keyboardActive = false; activate(group, i, false) }
                        IptvStage.Lineup -> LineupStage(
                            m, now, side, playlist, settings, content, typed, ::isFocused,
                            onLongClick = { i -> activate("lineup", i, true) },
                        ) { group, i -> keyboardActive = false; activate(group, i, false) }
                    }
                }
            }
        }
        val menuFor = menuChannel
        if (menuFor != null) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.TopCenter) {
                IptvLineupMenu(
                    channelName = menuFor.primary.name.ifBlank { "Channel ${menuFor.primary.num}" },
                    note = IptvLineupRules.position(storedLineup, menuFor).let { if (it >= 0) "Number ${it + 1} in Your 40" else "Not in Your 40" },
                    actions = menuActions,
                    focused = menuIndex,
                    onAction = ::runMenu,
                    onBack = { menuChannel = null },
                )
            }
        }
    }
}

private fun stageHeading(stage: IptvStage, m: IptvStageModel?, settings: IptvSettings, now: Long): Pair<String, String> {
    val total = m?.total?.let(::count) ?: "all the"
    val clock = IptvStageRules.clock(now)
    return when (stage) {
        IptvStage.Three -> "Three things" to "Out of $total channels, at $clock. Pick one; there is never a fourth."
        IptvStage.Soon -> "Starting soon" to "Things you can watch from the first minute. It is $clock."
        IptvStage.Minutes -> "I have ${settings.minutes} minutes" to "Only what finishes inside it."
        IptvStage.Order -> "Tonight" to "One evening from $total channels. Approve it once and Nuvio changes channel for you."
        IptvStage.Earlier -> "Earlier today, ready to play" to "Already shown on your channels and still in the provider's catch-up. Each plays from its start."
        IptvStage.Following -> "Following" to "Star a programme once. Nuvio finds it on whichever of the $total channels has it."
        IptvStage.Ask -> "Just ask" to "Typed or spoken. It answers with one thing, not a list."
        IptvStage.Lineup -> "Your 40" to "The whole of Live TV, numbered like a television. The other ${m?.let { count((it.mergedCount - it.lineup.size).coerceAtLeast(0)) } ?: ""} are in search."
    }
}

// ---- The stages ----------------------------------------------------------------------------------------------

private typealias Focused = (String, Int) -> Boolean
private typealias Act = (String, Int) -> Unit

@Composable
private fun ThreeStage(m: IptvStageModel, now: Long, isFocused: Focused, act: Act) {
    val tokens = MaterialTheme.nuvio
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            val cards = listOf(
                Triple("SAFE", m.three.safe, "What you put on at this hour."),
                Triple("STARTS SOON", m.three.soon, "From the first minute, and done before bedtime."),
                Triple("WILDCARD", m.three.wildcard, "Off your usual channels."),
            )
            cards.forEachIndexed { index, (label, p, why) ->
                StageCard(
                    label = label,
                    programme = p,
                    now = now,
                    empty = when (index) {
                        0 -> "None of your usual channels has a guide."
                        1 -> "Nothing starts in the next half hour."
                        else -> "No film to suggest right now."
                    },
                    reason = why,
                    focused = isFocused("cards", index),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = { act("cards", index) },
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            StageButton(
                label = if (m.canReshuffle) "Deal again (one left tonight)" else "Dealt again tonight",
                accent = false,
                enabled = m.canReshuffle,
                focused = isFocused("deal", 0),
                modifier = Modifier.width(300.dp),
            ) { act("deal", 0) }
            Spacer(Modifier.width(16.dp))
            Text("There is never a fourth.", style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted)
        }
    }
}

@Composable
private fun SoonStage(
    m: IptvStageModel,
    now: Long,
    side: Dp,
    lists: Pair<List<IptvWallProgramme>, List<IptvWallProgramme>>,
    selected: Int,
    kinds: Set<IptvSoonKind>,
    lineupOnly: Boolean,
    window: Int,
    isFocused: Focused,
    act: Act,
) {
    val rows = lists.first + lists.second
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        StagePanel("Show me", null, Modifier.width(side).fillMaxHeight()) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StageChip(if (m.lineupUids.isEmpty()) "My channels only" else "My 40 only", lineupOnly, isFocused("kinds", 0)) { act("kinds", 0) }
                IptvSoonKind.entries.forEachIndexed { i, kind ->
                    StageChip(kind.label, kind in kinds, isFocused("kinds", i + 1)) { act("kinds", i + 1) }
                }
                Spacer(Modifier.height(4.dp))
                StageSubheading("Leaving within")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IptvStageRules.SOON_WINDOWS.forEachIndexed { i, minutes ->
                        StageChip(if (i == 0) "$minutes min" else "$minutes", minutes == window, isFocused("window", i)) { act("window", i) }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            StageNote("Nothing here has started. Times are the start, not the end.")
        }
        StagePanel("Departures", "${lists.first.size} in the next $window minutes", Modifier.weight(1f).fillMaxHeight()) {
            StageList(rows.size, focusedIndex = rows.indices.firstOrNull { isFocused("departures", it) }, empty = "Nothing starts in the next hour on these channels.") { i ->
                val p = rows[i]
                StageRow(
                    lead = IptvStageRules.untilText(p.entry, now).removePrefix("in "),
                    title = p.entry.title,
                    sub = listOfNotNull(p.channel.primary.name, IptvStageRules.length(p.entry.endMs - p.entry.startMs), kindLabel(p.entry)).joinToString(" · "),
                    trailing = IptvStageRules.clock(p.entry.startMs),
                    dim = i >= lists.first.size,
                    selected = i == selected,
                    focused = isFocused("departures", i),
                ) { act("departures", i) }
            }
        }
        val p = rows.getOrNull(selected) ?: rows.firstOrNull()
        StagePanel(p?.entry?.title ?: "Nothing to show", null, Modifier.width(side).fillMaxHeight()) {
            Column(Modifier.padding(horizontal = 16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (p != null) {
                    StageArt(p, 150.dp)
                    StageMeta(listOfNotNull(kindLabel(p.entry), IptvStageRules.length(p.entry.endMs - p.entry.startMs)).joinToString(" · "))
                    StageSentence("Starts ${IptvStageRules.clock(p.entry.startMs)} on ${p.channel.primary.name}, ends ${IptvStageRules.clock(p.entry.endMs)}.")
                    Spacer(Modifier.weight(1f))
                    StageButton("Tune in at ${IptvStageRules.clock(p.entry.startMs)}", accent = true, focused = isFocused("soonActions", 0), icon = Icons.Rounded.Notifications) { act("soonActions", 0) }
                    StageButton("Go to ${p.channel.primary.name} now", accent = false, focused = isFocused("soonActions", 1)) { act("soonActions", 1) }
                    StageNoteInline("Carry on with what is on. Nuvio switches over at the start.")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MinutesStage(m: IptvStageModel, now: Long, minutes: Int, isFocused: Focused, act: Act) {
    val tokens = MaterialTheme.nuvio
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            IptvDecide.MINUTE_CHOICES.forEachIndexed { i, choice ->
                StageChip(if (choice == 60) "1 hour" else "$choice min", choice == minutes, isFocused("dial", i)) { act("dial", i) }
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            fun fitSub(fit: IptvFit, extra: String) = fit.programme.channel.primary.name + " · " + extra +
                if (fit.overMin > 0) ", ${fit.overMin} over" else ""
            StagePanel("Ends in time", "already on", Modifier.weight(1f).fillMaxHeight()) {
                val list = m.board.alreadyOn
                StageList(list.size, list.indices.firstOrNull { isFocused("on", it) }, "Nothing on now ends inside $minutes minutes.") { i ->
                    val f = list[i]
                    StageRow(
                        lead = IptvStageRules.clock(f.programme.entry.endMs),
                        title = f.programme.entry.title,
                        sub = fitSub(f, "${((f.programme.entry.endMs - now) / 60_000L).coerceAtLeast(0)} min left"),
                        trailing = null, dim = f.overMin > 0, focused = isFocused("on", i),
                    ) { act("on", i) }
                }
            }
            StagePanel("Starts and ends in time", "from the top", Modifier.weight(1f).fillMaxHeight()) {
                val list = m.board.starting
                StageList(list.size, list.indices.firstOrNull { isFocused("starting", it) }, "Nothing starting soon fits.") { i ->
                    val f = list[i]
                    StageRow(
                        lead = IptvStageRules.clock(f.programme.entry.startMs),
                        title = f.programme.entry.title,
                        sub = fitSub(f, IptvStageRules.untilText(f.programme.entry, now).let { if (it == "On now") it else "starts $it" }),
                        trailing = null, dim = f.overMin > 0, focused = isFocused("starting", i),
                    ) { act("starting", i) }
                }
            }
            StagePanel("From earlier, fits", "catch-up", Modifier.weight(1f).fillMaxHeight()) {
                val list = m.board.catchUp
                StageList(list.size, list.indices.firstOrNull { isFocused("catchup", it) }, "No catch-up is short enough, or the provider keeps none.") { i ->
                    val f = list[i]
                    StageRow(
                        lead = IptvStageRules.length(f.programme.entry.endMs - f.programme.entry.startMs),
                        title = f.programme.entry.title,
                        sub = fitSub(f, "shown ${IptvStageRules.clock(f.programme.entry.startMs)}"),
                        trailing = null, dim = f.overMin > 0, focused = isFocused("catchup", i),
                    ) { act("catchup", i) }
                }
            }
        }
        Text(
            text = "It is ${IptvStageRules.clock(now)}. Everything here is over by ${IptvStageRules.clock(now + minutes * 60_000L)}, and nothing plays after it.",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = tokens.colors.textMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun OrderStage(
    m: IptvStageModel,
    now: Long,
    side: Dp,
    selected: Int,
    follows: List<String>,
    bedMinute: Int,
    isFocused: Focused,
    act: Act,
) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        StagePanel("Built from", null, Modifier.width(side).fillMaxHeight()) {
            StageInfoRow("Your usuals", "What you watch at each hour", null)
            StageDivider()
            StageInfoRow("Programmes you follow", if (follows.isEmpty()) "None yet" else "${follows.size} followed", null)
            StageDivider()
            StageInfoRow("Films in the guide", "Best of the evening", null)
            StageDivider()
            StageInfoRow("Bedtime", "Nothing may run past ${IptvStageRules.clock(m.bedMs)}", null)
            Spacer(Modifier.weight(1f))
            m.orderLeftOut?.let { StageNote("${it.entry.title} runs past ${IptvStageRules.clock(m.bedMs)}, so it is left for another night.") }
                ?: StageNote("Only your channels are used, so the evening stays yours.")
        }
        val total = m.order.sumOf { it.entry.endMs - maxOf(it.entry.startMs, now) }
        StagePanel(
            "Running order",
            if (m.order.isEmpty()) null else "${m.order.size} things, ${IptvStageRules.length(total)}",
            Modifier.weight(1f).fillMaxHeight(),
        ) {
            run {
                StageList(m.order.size, m.order.indices.firstOrNull { isFocused("order", it) }, "Nothing on your channels fits before bedtime.") { i ->
                    val p = m.order[i]
                    StageRow(
                        lead = IptvStageRules.clock(p.entry.startMs),
                        title = p.entry.title,
                        sub = p.channel.primary.name + " · " + IptvRunningOrder.reason(p, follows, m.usualKeys),
                        trailing = IptvStageRules.length(p.entry.endMs - p.entry.startMs),
                        selected = i == selected,
                        focused = isFocused("order", i),
                        live = p.onNow(now),
                    ) { act("order", i) }
                }
            }
            m.order.lastOrNull()?.let { last ->
                StageInfoRow("Ends ${IptvStageRules.clock(last.entry.endMs)}", "Then nothing starts by itself.", null, Modifier.padding(bottom = 8.dp))
            }
        }
        StagePanel("Your call", null, Modifier.width(side).fillMaxHeight()) {
            Column(Modifier.padding(horizontal = 16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StageButton(if (m.orderApproved) "Approved: cancel" else "Approve and start", accent = !m.orderApproved, focused = isFocused("orderActions", 0), icon = if (m.orderApproved) Icons.Rounded.Check else null) { act("orderActions", 0) }
                StageButton("Swap one", accent = false, focused = isFocused("orderActions", 1)) { act("orderActions", 1) }
                StageButton("Not tonight", accent = false, focused = isFocused("orderActions", 2)) { act("orderActions", 2) }
                Spacer(Modifier.height(4.dp))
                StageSubheading("Bedtime")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BedChoices.forEachIndexed { i, minute ->
                        StageChip("%02d:%02d".format(minute / 60, minute % 60), minute == bedMinute, isFocused("bed", i)) { act("bed", i) }
                    }
                }
                Spacer(Modifier.weight(1f))
                StageNoteInline("Any key takes over. Swap one replaces the highlighted slot.")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EarlierStage(
    m: IptvStageModel,
    now: Long,
    side: Dp,
    days: List<Pair<String, List<IptvWallProgramme>>>,
    day: Int,
    rows: List<IptvWallProgramme>,
    selected: Int,
    isFocused: Focused,
    act: Act,
) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        StagePanel("Day", null, Modifier.width(side).fillMaxHeight()) {
            StageList(days.size, days.indices.firstOrNull { isFocused("days", it) }, "Nothing kept yet.") { i ->
                StageRow(lead = null, title = days[i].first, sub = "${days[i].second.size} worth watching", trailing = null, selected = i == day, focused = isFocused("days", i)) { act("days", i) }
            }
        }
        StagePanel(days.getOrNull(day)?.first ?: "Earlier", "best first, not by the clock", Modifier.weight(1f).fillMaxHeight()) {
            StageList(rows.size, rows.indices.firstOrNull { isFocused("earlier", it) }, "Nothing from earlier is kept for your channels. This needs an Xtream account whose provider keeps catch-up.") { i ->
                val p = rows[i]
                StageRow(
                    lead = IptvStageRules.clock(p.entry.startMs),
                    title = p.entry.title,
                    sub = listOfNotNull(p.channel.primary.name, IptvStageRules.length(p.entry.endMs - p.entry.startMs), kindLabel(p.entry)).joinToString(" · "),
                    trailing = "Play",
                    trailingIcon = Icons.Rounded.PlayArrow,
                    selected = i == selected,
                    focused = isFocused("earlier", i),
                ) { act("earlier", i) }
            }
        }
        val p = rows.getOrNull(selected) ?: rows.firstOrNull()
        StagePanel(p?.entry?.title ?: "Nothing to show", null, Modifier.width(side).fillMaxHeight()) {
            Column(Modifier.padding(horizontal = 16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (p != null) {
                    StageArt(p, 150.dp)
                    val year = if (p.entry.kind == IptvProgrammeKind.FILM) rememberFilmArt(p.entry.title)?.year else null
                    StageMeta(listOfNotNull(year, IptvStageRules.length(p.entry.endMs - p.entry.startMs), kindLabel(p.entry)).joinToString(" · "))
                    StageSentence("Shown ${IptvStageRules.clock(p.entry.startMs)} on ${p.channel.primary.name}.")
                    Spacer(Modifier.weight(1f))
                    StageButton("Play from the start", accent = true, focused = isFocused("earlierActions", 0), icon = Icons.Rounded.PlayArrow) { act("earlierActions", 0) }
                    StageNoteInline("Kept by the provider's catch-up.")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FollowingStage(
    m: IptvStageModel,
    now: Long,
    side: Dp,
    week: List<IptvWallProgramme>,
    text: String,
    onText: (String) -> Unit,
    fieldFocus: FocusRequester,
    onEditing: () -> Unit,
    isFocused: Focused,
    onLongClick: (Int) -> Unit,
    act: Act,
) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        StagePanel("Follow something", null, Modifier.width(side).fillMaxHeight()) {
            StageField(
                value = text,
                onValueChange = onText,
                placeholder = "A programme, team or film",
                focusRequester = fieldFocus,
                voiceOwner = "iptv-follow",
                focused = isFocused("followInput", 0),
                onEditing = onEditing,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            StageSubheading("On your channels now", Modifier.padding(horizontal = 16.dp))
            StageList(m.followSuggestions.size, m.followSuggestions.indices.firstOrNull { isFocused("suggest", it) }, "Nothing on your channels to suggest.") { i ->
                val p = m.followSuggestions[i]
                StageRow(lead = null, title = p.entry.title, sub = p.channel.primary.name, trailing = "Follow", focused = isFocused("suggest", i)) { act("suggest", i) }
            }
        }
        StagePanel("Programmes", "${m.follows.size} followed · hold OK to stop", Modifier.weight(1f).fillMaxHeight()) {
            StageList(m.follows.size, m.follows.indices.firstOrNull { isFocused("follows", it) }, "Nothing followed yet. Type one on the left, or pick something on your channels.") { i ->
                val result = m.follows[i]
                val first = result.matches.firstOrNull()
                StageRow(
                    lead = null,
                    leadIcon = Icons.Rounded.Notifications,
                    title = result.text,
                    sub = first?.let { "Found: " + IptvStageRules.foundText(it, now) + if (!it.entry.title.equals(result.text, true)) ", ${it.entry.title}" else "" }
                        ?: "Nothing in the guide yet",
                    trailing = first?.let { IptvStageRules.untilText(it.entry, now) },
                    live = first?.onNow(now) == true,
                    dim = first == null,
                    focused = isFocused("follows", i),
                    onLongClick = { onLongClick(i) },
                ) { act("follows", i) }
            }
        }
        StagePanel("Coming up", null, Modifier.width(side).fillMaxHeight()) {
            run {
                StageList(week.size, week.indices.firstOrNull { isFocused("week", it) }, "Nothing followed is in the guide.") { i ->
                    val p = week[i]
                    StageRow(lead = IptvStageRules.weekLabel(p.entry.startMs), leadWidth = 76.dp, title = p.entry.title, sub = p.channel.primary.name, trailing = null, focused = isFocused("week", i)) { act("week", i) }
                }
            }
            StageNote("OK on one that has not begun tunes in at its start.")
        }
    }
}

@Composable
private fun AskStage(
    m: IptvStageModel,
    now: Long,
    side: Dp,
    asks: List<IptvAsked>,
    text: String,
    onText: (String) -> Unit,
    fieldFocus: FocusRequester,
    onEditing: () -> Unit,
    onSubmit: () -> Unit,
    isFocused: Focused,
    act: Act,
) {
    val tokens = MaterialTheme.nuvio
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        StagePanel("Asked before", null, Modifier.width(side).fillMaxHeight()) {
            StageList(asks.size, asks.indices.firstOrNull { isFocused("asked", it) }, "What you ask is kept here.") { i ->
                StageRow(lead = null, title = asks[i].text, sub = IptvStageRules.dayLabel(asks[i].at, now), trailing = null, focused = isFocused("asked", i)) { act("asked", i) }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            StageField(
                value = text,
                onValueChange = onText,
                placeholder = "something funny, half an hour",
                focusRequester = fieldFocus,
                voiceOwner = "iptv-ask",
                focused = isFocused("askInput", 0),
                onEditing = onEditing,
                onSubmit = onSubmit,
                height = 52.dp,
            )
            val p = m.ask?.programme
            Column(
                Modifier.weight(1f).fillMaxWidth().iptvGlass(NuvioGlass.Shape).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StageLabel(if (p == null) "ONE ANSWER" else "ONE ANSWER · ${(m.ask?.others ?: 0)} MORE IF YOU SAY NOT THAT")
                when {
                    text.isBlank() -> {
                        StageArt(null, 170.dp)
                        StageSentence("Say what you are in the mood for and how long you have: \"a film, an hour\", \"the football\", \"news, ten minutes\".")
                    }
                    p == null -> {
                        StageArt(null, 170.dp)
                        Text("Nothing fits", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tokens.colors.textPrimary)
                        StageSentence(m.ask?.why ?: "")
                    }
                    else -> {
                        StageArt(p, 170.dp)
                        Text(p.entry.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tokens.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        StageMeta(IptvProgrammeLine.text(p.channel.primary.name, p.entry, now, 0, IptvStageRules::clock))
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val primary = when {
                        p == null -> "Watch"
                        p.entry.startMs > now + 60_000L -> "Tune in at ${IptvStageRules.clock(p.entry.startMs)}"
                        else -> "Watch now"
                    }
                    StageButton(primary, accent = true, enabled = p != null, focused = isFocused("askActions", 0), icon = Icons.Rounded.PlayArrow, modifier = Modifier.weight(1f)) { act("askActions", 0) }
                    StageButton("Not that", accent = false, enabled = p != null && (m.ask?.others ?: 0) > 0, focused = isFocused("askActions", 1), modifier = Modifier.weight(1f)) { act("askActions", 1) }
                }
            }
        }
        StagePanel("Why this", null, Modifier.width(side).fillMaxHeight()) {
            val reasons = m.ask?.reasons.orEmpty()
            if (reasons.isEmpty()) {
                StageNote("The reasons for the answer show here.")
            } else {
                reasons.forEachIndexed { i, (head, detail) ->
                    if (i > 0) StageDivider()
                    StageInfoRow(head, detail, null)
                }
            }
        }
    }
}

@Composable
private fun LineupStage(
    m: IptvStageModel,
    now: Long,
    side: Dp,
    playlist: IptvPlaylist,
    settings: IptvSettings,
    content: IptvPlaylistContent,
    typed: String,
    isFocused: Focused,
    onLongClick: (Int) -> Unit,
    act: Act,
) {
    val tokens = MaterialTheme.nuvio
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        StagePanel("How it got here", null, Modifier.width(side).fillMaxHeight()) {
            val triage = settings.triage[playlist.id]
            val prefix = IptvRepository.channelRef(playlist.id, "")
            val played = settings.plays.mapTo(HashSet()) { it.ref }
            fun inLineup(refs: Collection<String>) = m.lineup.count { item -> item.channel.sources.any { IptvRepository.channelRef(playlist.id, it.key) in refs } }
            StageInfoRow("Triage", if (triage == null) "Not done yet" else "${triage.regions.size} countries, ${triage.kinds.size} kinds", null)
            StageDivider()
            StageInfoRow("Copies merged", "FHD, HD, SD and backups", count((m.total - m.mergedCount).coerceAtLeast(0)))
            StageDivider()
            StageInfoRow("What you watch", "From your viewing", count(inLineup(played.filter { it.startsWith(prefix) })))
            StageDivider()
            StageInfoRow("Starred", "Your favourites", count(inLineup(settings.favourites)))
            Spacer(Modifier.weight(1f))
            StageButton("Redo the triage", accent = false, focused = isFocused("lineupActions", 0), modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) { act("lineupActions", 0) }
            Spacer(Modifier.height(16.dp))
        }
        val focusedIndex = m.lineup.indices.firstOrNull { isFocused("lineup", it) }
        val shown = focusedIndex?.let { m.lineup[it] }
        val note = when {
            typed.isNotEmpty() -> "Channel $typed..."
            shown != null -> "${shown.channel.primary.name} is on ${shown.number}" + (shown.now?.let { " · ${it.title} until ${IptvStageRules.clock(it.endMs)}" } ?: "")
            else -> "${m.lineup.size} channels"
        }
        StagePanel("Lineup", note, Modifier.weight(1f).fillMaxHeight()) {
            val grid = rememberLazyGridState()
            LaunchedEffect(focusedIndex) {
                if (focusedIndex != null && grid.layoutInfo.visibleItemsInfo.none { it.index == focusedIndex }) grid.scrollToItem(focusedIndex)
            }
            if (m.lineup.isEmpty()) {
                StageNote("Your 40 is built from what you watch and star. Watch a few channels, or star them, and they appear here.")
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    state = grid,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(m.lineup, key = { _, item -> item.channel.primary.uid }) { i, item ->
                        LineupTile(item, now, isFocused("lineup", i), onLongClick = { onLongClick(i) }) { act("lineup", i) }
                    }
                }
            }
            Text(
                text = "OK watches. Hold OK to move or drop a channel. Type a number to go straight to it.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textMuted,
            )
        }
        StagePanel("Kept out", null, Modifier.width(side).fillMaxHeight()) {
            val never = settings.never[playlist.id].orEmpty()
            val records = settings.weeds[playlist.id].orEmpty()
            val dead = IptvWeedRules.dead(records)
            StageInfoRow("Never again", "Channels you threw out", count(never.size))
            StageDivider()
            StageInfoRow("Dead", "Failed three nights running", count(dead.size))
            StageDivider()
            StageInfoRow("Unwatched", "On no list, still in search", count((m.mergedCount - m.lineup.size).coerceAtLeast(0)))
            if (dead.isNotEmpty()) {
                StageSubheading("Found dead", Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
                val names = content.channels.filter { it.key in dead }.map { it.name }.distinct().take(3)
                names.forEach { StageInfoRow(it, "Dropped after three nights offline", null, Modifier.alpha(0.6f)) }
            }
        }
    }
}

// ---- Pieces ---------------------------------------------------------------------------------------------------

private fun kindLabel(entry: IptvEpgEntry): String? = when (entry.kind) {
    IptvProgrammeKind.FILM -> "Film"
    IptvProgrammeKind.SPORT -> "Sport"
    IptvProgrammeKind.NEWS -> "News"
    IptvProgrammeKind.KIDS -> "Children's"
    else -> null
}

@Composable
private fun StageStrip(labels: List<String>, selected: Int, focused: Int, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { i, label ->
            StageChip(label, i == selected, i == focused, check = false) { onPick(i) }
        }
    }
}

/** A glass section card with a heading, like a settings section; the body fills the rest. */
@Composable
private fun StagePanel(title: String, note: String?, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val tokens = MaterialTheme.nuvio
    Column(modifier = modifier.iptvGlass(NuvioGlass.Shape).clip(NuvioGlass.Shape)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = tokens.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (note != null) {
                Text(text = note, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        content()
    }
}

/** A list of fixed-height rows with a hairline between them, scrolled to keep the highlight in view. */
@Composable
private fun ColumnScope.StageList(size: Int, focusedIndex: Int?, empty: String, row: @Composable (Int) -> Unit) {
    if (size == 0) {
        StageNote(empty)
        Spacer(Modifier.weight(1f))
        return
    }
    val state = rememberLazyListState()
    LaunchedEffect(focusedIndex) {
        if (focusedIndex != null) {
            val visible = state.layoutInfo.visibleItemsInfo
            val first = visible.firstOrNull()?.index ?: 0
            val last = visible.lastOrNull()?.index ?: 0
            if (focusedIndex < first) state.scrollToItem(focusedIndex)
            else if (focusedIndex >= last) state.scrollToItem((focusedIndex - (visible.size - 2)).coerceAtLeast(0))
        }
    }
    LazyColumn(state = state, modifier = Modifier.weight(1f).fillMaxWidth()) {
        itemsIndexed((0 until size).toList()) { i, _ ->
            if (i > 0) StageDivider()
            row(i)
        }
    }
}

@Composable
private fun StageDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.10f)))
}

@Composable
private fun StageRow(
    lead: String?,
    title: String,
    sub: String?,
    trailing: String?,
    dim: Boolean = false,
    selected: Boolean = false,
    focused: Boolean,
    leadWidth: Dp = 56.dp,
    leadIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    live: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(StageRowHeight)
            .alpha(if (dim) 0.6f else 1f)
            .background(
                when {
                    focused || hovered -> Color.White.copy(alpha = 0.09f)
                    selected -> tokens.colors.accent.copy(alpha = 0.18f)
                    else -> Color.Transparent
                },
                if (focused || hovered) shape else RoundedCornerShape(0.dp),
            )
            .then(if (focused) Modifier.smoothBorder(1.5.dp, tokens.colors.borderFocus, shape) else Modifier)
            .combinedClickable(interactionSource = interaction, indication = null, onLongClick = onLongClick, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (leadIcon != null) {
            Icon(leadIcon, contentDescription = null, tint = tokens.colors.textPrimary, modifier = Modifier.size(16.dp))
        } else if (lead != null) {
            Text(lead, modifier = Modifier.width(leadWidth), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = tokens.colors.textPrimary, maxLines = 1)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = tokens.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (live) Box(Modifier.size(7.dp).clip(CircleShape).background(tokens.colors.accent))
                if (trailingIcon != null) Icon(trailingIcon, contentDescription = null, tint = tokens.colors.textSecondary, modifier = Modifier.size(14.dp))
                Text(trailing, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textSecondary, maxLines = 1)
            }
        }
    }
}

/** A row of words only, for the side panels' facts. */
@Composable
private fun StageInfoRow(title: String, sub: String, trailing: String?, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = modifier.fillMaxWidth().height(StageRowHeight).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = tokens.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textSecondary, maxLines = 1)
    }
}

@Composable
private fun StageChip(label: String, on: Boolean, focused: Boolean, check: Boolean = true, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = Modifier
            .height(36.dp)
            .iptvGlass(shape, raised = focused || hovered, selected = on && !hovered, selectedTint = tokens.colors.accent.copy(alpha = 0.42f))
            .then(if (on) Modifier.border(1.dp, tokens.colors.accent.copy(alpha = 0.85f), shape) else Modifier)
            .clip(shape)
            .then(if (focused) Modifier.smoothBorder(1.5.dp, tokens.colors.borderFocus, shape) else Modifier)
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (on && check) Icon(Icons.Rounded.Check, contentDescription = null, tint = tokens.colors.textPrimary, modifier = Modifier.size(14.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
            color = if (on) tokens.colors.textPrimary else tokens.colors.textSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun StageButton(
    label: String,
    accent: Boolean,
    focused: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = NuvioGlass.Shape
    val lit = accent && enabled
    Row(
        modifier = modifier
            .height(44.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .iptvGlass(shape, raised = focused || (hovered && enabled), selected = lit && !hovered, selectedTint = tokens.colors.accent.copy(alpha = 0.42f))
            .then(if (lit) Modifier.border(1.dp, tokens.colors.accent.copy(alpha = 0.85f), shape) else Modifier)
            .clip(shape)
            .then(if (focused) Modifier.smoothBorder(1.5.dp, tokens.colors.borderFocus, shape) else Modifier)
            .combinedClickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = tokens.colors.textPrimary, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = tokens.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** One of the Three things: label, artwork, title, when, and why it was picked. All three are the same size. */
@Composable
private fun StageCard(
    label: String,
    programme: IptvWallProgramme?,
    now: Long,
    empty: String,
    reason: String,
    focused: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = NuvioGlass.Shape
    Column(
        modifier = modifier
            .iptvGlass(shape, raised = focused || (hovered && programme != null))
            .clip(shape)
            .then(if (focused) Modifier.smoothBorder(1.5.dp, tokens.colors.borderFocus, shape) else Modifier)
            .combinedClickable(interactionSource = interaction, indication = null, enabled = programme != null, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StageLabel(label)
        StageArt(programme, 190.dp)
        if (programme == null) {
            StageSentence(empty)
            Spacer(Modifier.weight(1f))
        } else {
            Text(programme.entry.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tokens.colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            StageMeta(IptvProgrammeLine.text(programme.channel.primary.name, programme.entry, now, 0, IptvStageRules::clock))
            Spacer(Modifier.weight(1f))
            if (programme.onNow(now)) ProgressLine(programme.entry.progress(now))
            Text(reason, style = MaterialTheme.typography.bodySmall, color = tokens.colors.textMuted, maxLines = 2)
        }
    }
}

/** A film's backdrop from TMDB, else the channel's logo on a tint of its own. Always the same height. */
@Composable
private fun StageArt(programme: IptvWallProgramme?, height: Dp) {
    val tokens = MaterialTheme.nuvio
    val shape = RoundedCornerShape(10.dp)
    val name = programme?.channel?.primary?.name.orEmpty()
    val hue = ((name.hashCode() % 360) + 360) % 360
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(if (programme == null) Color.White.copy(alpha = 0.04f) else Color.hsv(hue.toFloat(), 0.45f, 0.28f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape),
        contentAlignment = Alignment.Center,
    ) {
        val art = if (programme?.entry?.kind == IptvProgrammeKind.FILM) rememberFilmArt(programme.entry.title) else null
        val picture = art?.backdrop ?: art?.poster
        val logo = programme?.channel?.primary?.logo
        when {
            picture != null -> NuvioAsyncImage(model = picture, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            logo != null -> NuvioAsyncImage(model = logo, contentDescription = null, modifier = Modifier.fillMaxWidth(0.5f).height(height * 0.5f), contentScale = ContentScale.Fit)
            programme != null -> Icon(Icons.Rounded.LiveTv, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(40.dp))
            else -> Icon(Icons.Rounded.Search, contentDescription = null, tint = tokens.colors.textMuted, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun LineupTile(item: IptvStageChannel, now: Long, focused: Boolean, onLongClick: () -> Unit, onClick: () -> Unit) {
    val tokens = MaterialTheme.nuvio
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .iptvGlass(shape, raised = focused || hovered)
            .clip(shape)
            .then(if (focused) Modifier.smoothBorder(1.5.dp, tokens.colors.borderFocus, shape) else Modifier)
            .combinedClickable(interactionSource = interaction, indication = null, onLongClick = onLongClick, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(item.number.toString(), style = MaterialTheme.typography.labelSmall, color = tokens.colors.textMuted, maxLines = 1)
        Text(
            item.channel.primary.name.ifBlank { "Channel ${item.channel.primary.num}" },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = tokens.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)).background(Color.White.copy(alpha = 0.08f))) {
            val progress = item.now?.progress(now) ?: 0f
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(tokens.colors.accent))
        }
    }
}

@Composable
private fun StageField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    focusRequester: FocusRequester,
    voiceOwner: String,
    focused: Boolean,
    onEditing: () -> Unit,
    modifier: Modifier = Modifier,
    onSubmit: (() -> Unit)? = null,
    height: Dp = 44.dp,
) {
    val tokens = MaterialTheme.nuvio
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .then(if (focused) Modifier.smoothBorder(1.5.dp, tokens.colors.borderFocus, NuvioGlass.Shape) else Modifier),
    ) {
        NuvioTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxSize().onFocusChanged { if (it.hasFocus) onEditing() },
            placeholder = placeholder,
            focusRequester = focusRequester,
            onImeAction = onSubmit,
            leadingContent = { Icon(Icons.Rounded.Search, contentDescription = null, tint = tokens.colors.textMuted, modifier = Modifier.size(18.dp)) },
            trailingContent = {
                com.nuvio.app.core.ui.VoiceSearchButton(
                    owner = voiceOwner,
                    focusField = { runCatching { focusRequester.requestFocus() } },
                    tint = tokens.colors.textMuted,
                    size = 26.dp,
                    iconSize = 18.dp,
                )
            },
        )
        com.nuvio.app.core.ui.VoiceSearchCommands(owner = voiceOwner, text = value, onTextChange = onValueChange)
    }
}

@Composable
private fun StageLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = MaterialTheme.nuvio.colors.textSecondary, maxLines = 1)
}

@Composable
private fun StageSubheading(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.nuvio.colors.textPrimary)
}

@Composable
private fun StageMeta(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.nuvio.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun StageSentence(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.nuvio.colors.textPrimary, maxLines = 3, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun StageNote(text: String) {
    Text(text, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.nuvio.colors.textMuted)
}

@Composable
private fun StageNoteInline(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.nuvio.colors.textMuted)
}

@Composable
private fun StageMessage(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.nuvio.colors.textMuted)
    }
}
