package com.nuvio.app.features.home

import co.touchlab.kermit.Logger
import com.nuvio.app.features.iptv.IPTV_DEFAULT_HEADERS
import com.nuvio.app.features.iptv.IptvChannel
import com.nuvio.app.features.iptv.IptvEpgEntry
import com.nuvio.app.features.iptv.IptvEpgRepository
import com.nuvio.app.features.iptv.IptvPlaybackRequests
import com.nuvio.app.features.iptv.IptvPlaylist
import com.nuvio.app.features.iptv.IptvPlaylistContent
import com.nuvio.app.features.iptv.IptvPlaylistType
import com.nuvio.app.features.iptv.IptvRepository
import com.nuvio.app.features.iptv.iptvStreamCandidates
import com.nuvio.app.features.iptv.requestHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * fork:home-live-tv - Live TV favourites as a row on Home: one card per favourite channel, with what is on now from
 * the guide. Selecting a card plays the channel straight away (the favourites are its channel up/down list), so a
 * live channel is two presses from Home. Off with Settings › Live TV › Favourites on Home.
 */
internal const val LIVE_TV_HOME_SECTION_KEY = "nuvio:live-tv-favourites"
internal const val LIVE_TV_HOME_ITEM_TYPE = "live_tv_channel"

/** A Live TV favourite card; its id is the favourite's `playlistId:channelKey`. */
internal fun MetaPreview.isLiveTvHomeCard(): Boolean = type == LIVE_TV_HOME_ITEM_TYPE

/** One favourite as the row shows it. */
internal data class LiveTvHomeCard(
    val ref: String,
    val name: String,
    val logo: String?,
    val nowTitle: String?,
    val nowEndMs: Long?,
    val nowImage: String?,
    /** fork:shelf-home live-tv - the guide's programme on now and the one after it, for the shelf home's hero. */
    val now: IptvEpgEntry? = null,
    val next: IptvEpgEntry? = null,
)

internal object LiveTvHomeRules {
    /** A card per favourite still in its playlist, in the favourites' order; favourites of a playlist not loaded yet are skipped. */
    fun cards(
        favourites: List<String>,
        contents: Map<String, IptvPlaylistContent>,
        guide: (IptvPlaylist, IptvChannel) -> List<IptvEpgEntry>,
        now: Long,
    ): List<LiveTvHomeCard> = favourites.mapNotNull { ref ->
        val colon = ref.indexOf(':')
        if (colon <= 0) return@mapNotNull null
        val content = contents[ref.substring(0, colon)] ?: return@mapNotNull null
        val channel = content.byKey[ref.substring(colon + 1)] ?: return@mapNotNull null
        card(
            ref = ref,
            name = channel.name.ifBlank { "Channel ${channel.num}" },
            logo = channel.logo,
            entries = guide(content.playlist, channel),
            now = now,
        )
    }

    /** One favourite's card from its programmes: what is on at [now] and the one after it. */
    fun card(ref: String, name: String, logo: String?, entries: List<IptvEpgEntry>, now: Long): LiveTvHomeCard {
        val onNow = entries.firstOrNull { it.startMs <= now && it.endMs > now }
        val after = entries.filter { it.startMs >= (onNow?.endMs ?: now) }.minByOrNull { it.startMs }
        return LiveTvHomeCard(
            ref = ref,
            name = name,
            logo = logo?.takeIf { it.isNotBlank() },
            nowTitle = onNow?.title?.takeIf { it.isNotBlank() },
            nowEndMs = onNow?.endMs,
            nowImage = onNow?.image?.takeIf { it.isNotBlank() },
            now = onNow,
            next = after,
        )
    }

    /** The programmes worth keeping for a favourite: not over yet, starting within [KEEP_AHEAD_MS]. */
    fun keepable(entries: List<IptvEpgEntry>, now: Long): List<IptvEpgEntry> =
        entries.filter { it.endMs > now && it.startMs < now + KEEP_AHEAD_MS }.sortedBy { it.startMs }

    const val KEEP_AHEAD_MS = 12 * 60 * 60 * 1000L

    /** "Match of the Day · until 11:20 PM", the card's line; null when the guide has nothing on now. */
    fun nowLine(card: LiveTvHomeCard): String? {
        val title = card.nowTitle ?: return null
        val end = card.nowEndMs ?: return title
        val time = java.time.Instant.ofEpochMilli(end).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
        return "$title · until ${time.format(CLOCK)}"
    }

    private val CLOCK = java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.ENGLISH)

    fun section(cards: List<LiveTvHomeCard>, title: String, subtitle: String): HomeCatalogSection? {
        if (cards.isEmpty()) return null
        return HomeCatalogSection(
            key = LIVE_TV_HOME_SECTION_KEY,
            title = title,
            subtitle = subtitle,
            addonName = "Live TV",
            items = cards.map { card ->
                MetaPreview(
                    id = card.ref,
                    type = LIVE_TV_HOME_ITEM_TYPE,
                    name = card.name,
                    // The logo is drawn by LiveTvChannelArtwork, not as a cropped poster.
                    logo = card.logo,
                    banner = card.nowImage,
                    posterShape = PosterShape.Landscape,
                    description = nowLine(card),
                )
            },
            inlineOnly = true,
        )
    }
}

/** Builds and keeps the row current: on favourite changes, as channel lists and guides land, and every minute. */
object HomeLiveTvRow {
    private val log = Logger.withTag("HomeLiveTv")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _cards = MutableStateFlow<List<LiveTvHomeCard>>(emptyList())
    internal val cards: StateFlow<List<LiveTvHomeCard>> = _cards.asStateFlow()
    private var job: Job? = null

    private const val TICK_MS = 60_000L

    /*
     * fork:shelf-home live-tv - Nick, Nov 28: "no tmdb look up working for live channels". The row only read guides
     * already in memory, so on Home (Live TV never opened, or its guides released five minutes after leaving the tab)
     * a favourite had no programme on now: the hero said LIVE TV and never asked TMDB for the film. Now the row loads
     * the favourites' guides itself (disk first), keeps each favourite's next twelve hours so the idle release does
     * not blank it, and asks the provider's short EPG for an Xtream/Stalker channel the XMLTV guide has nothing for.
     */
    private class Kept(val name: String, val logo: String?, val entries: List<IptvEpgEntry>)

    private val kept = ConcurrentHashMap<String, Kept>()
    private val askedAt = ConcurrentHashMap<String, Long>()
    private const val ASK_AGAIN_MS = 30 * 60 * 1000L

    fun start() {
        if (job != null) return
        job = scope.launch {
            IptvRepository.ensureLoaded()
            launch {
                // Load the channel lists (cache first) of every playlist that has a favourite, once per change.
                IptvRepository.settings
                    .map { s -> if (s.homeRow) s.favourites.mapNotNull { it.substringBefore(':', "").ifBlank { null } }.toSet() else emptySet() }
                    .distinctUntilChanged()
                    .collect { ids ->
                        ids.forEach { id ->
                            runCatching { IptvRepository.refresh(id) }
                                .onFailure { if (it is CancellationException) throw it; log.w { "Live TV row: $id did not load: ${it.message}" } }
                            loadGuide(id)
                            rebuild()
                        }
                    }
            }
            launch { IptvRepository.settings.collect { rebuild() } }
            launch { IptvEpgRepository.version.collect { rebuild() } }
            while (true) {
                delay(TICK_MS)
                rebuild()
            }
        }
    }

    private fun rebuild() {
        val settings = IptvRepository.settings.value
        if (!settings.homeRow || settings.favourites.isEmpty()) {
            _cards.value = emptyList()
            return
        }
        val contents = settings.playlists.mapNotNull { playlist ->
            IptvRepository.contentState(playlist.id).value.content?.let { playlist.id to it }
        }.toMap()
        val now = System.currentTimeMillis()
        val built = LiveTvHomeRules.cards(
            favourites = settings.favourites,
            contents = contents,
            guide = { playlist, channel ->
                val ref = "${playlist.id}:${channel.key}"
                val fresh = LiveTvHomeRules.keepable(IptvEpgRepository.cached(playlist, channel).orEmpty(), now)
                if (fresh.isNotEmpty()) {
                    kept[ref] = Kept(channel.name.ifBlank { "Channel ${channel.num}" }, channel.logo, fresh)
                    fresh
                } else {
                    kept[ref]?.entries?.let { LiveTvHomeRules.keepable(it, now) }.orEmpty()
                }
            },
            now = now,
        ).associateBy { it.ref }
        // A playlist released from memory (off the Live TV tab) keeps its favourites from what was kept.
        _cards.value = settings.favourites.mapNotNull { ref ->
            built[ref] ?: kept[ref]?.let { LiveTvHomeRules.card(ref, it.name, it.logo, LiveTvHomeRules.keepable(it.entries, now), now) }
        }
        _cards.value.filter { it.now == null }.forEach { askFor(it.ref, contents, now) }
    }

    /** The playlist's guides into memory (disk first, a download only when due), then released again off the tab. */
    private suspend fun loadGuide(playlistId: String) {
        val content = IptvRepository.contentState(playlistId).value.content ?: return
        runCatching { IptvEpgRepository.refresh(content) }
            .onFailure { if (it is CancellationException) throw it; log.w { "Live TV row: guide for $playlistId did not load: ${it.message}" } }
        IptvRepository.scheduleIdleEvictionIfOffTab()
    }

    /**
     * A favourite with nothing on now: load its playlist's guide if it is not in memory, and for Xtream/Stalker ask
     * the provider's short EPG. At most once per [ASK_AGAIN_MS] per favourite; an answer bumps the guide's version,
     * which rebuilds the row.
     */
    private fun askFor(ref: String, contents: Map<String, IptvPlaylistContent>, now: Long) {
        val last = askedAt[ref]
        if (last != null && now - last < ASK_AGAIN_MS) return
        askedAt[ref] = now
        val playlistId = ref.substringBefore(':', "")
        scope.launch {
            val content = contents[playlistId] ?: IptvRepository.contentForBackgroundUse(playlistId) ?: return@launch
            val channel = content.byKey[ref.substringAfter(':')] ?: return@launch
            if (IptvEpgRepository.guideState(playlistId).value.guide == null) loadGuide(playlistId)
            if (IptvEpgRepository.cached(content.playlist, channel).isNullOrEmpty() && content.playlist.type != IptvPlaylistType.M3u) {
                val entries = IptvEpgRepository.nowAndNext(content.playlist, channel)
                if (entries.isEmpty()) log.i { "Live TV row: no guide for ${channel.name}" }
            }
            IptvRepository.scheduleIdleEvictionIfOffTab()
            rebuild()
        }
    }

    /**
     * fork:shelf-home live-tv - the favourite [ref]'s first stream address and its headers, for the muted live picture
     * on the shelf home (the front door's preview does the same); null when it cannot be found or is not http.
     */
    fun previewSource(ref: String): Pair<String, Map<String, String>>? {
        val settings = IptvRepository.settings.value
        val playlistId = ref.substringBefore(':', "")
        val playlist = settings.playlists.firstOrNull { it.id == playlistId } ?: return null
        val content = IptvRepository.contentState(playlistId).value.content ?: return null
        val channel = content.byKey[ref.substringAfter(':')] ?: return null
        val allowed = content.account?.allowedFormats.orEmpty()
        return iptvStreamCandidates(playlist, channel, settings.preferredFormat, allowed).firstOrNull()
            ?.url?.takeIf { it.startsWith("http", ignoreCase = true) }
            ?.let { it to (IPTV_DEFAULT_HEADERS + channel.requestHeaders()) }
    }

    /** Plays the favourite [ref] with the other favourites as its channel up/down list. False when it cannot be found. */
    fun play(ref: String): Boolean {
        val settings = IptvRepository.settings.value
        val playlistId = ref.substringBefore(':', "")
        val playlist = settings.playlists.firstOrNull { it.id == playlistId } ?: return false
        val content = IptvRepository.contentState(playlistId).value.content ?: return false
        val channel = content.byKey[ref.substringAfter(':')] ?: return false
        val zapList = settings.favourites
            .filter { it.startsWith("$playlistId:") }
            .mapNotNull { content.byKey[it.substringAfter(':')] }
        log.i { "Live TV row: playing ${channel.name}" }
        return IptvPlaybackRequests.play(playlist, channel, zapList = zapList)
    }
}
