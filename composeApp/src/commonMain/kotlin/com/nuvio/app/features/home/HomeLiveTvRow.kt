package com.nuvio.app.features.home

import co.touchlab.kermit.Logger
import com.nuvio.app.features.iptv.IptvChannel
import com.nuvio.app.features.iptv.IptvEpgEntry
import com.nuvio.app.features.iptv.IptvEpgRepository
import com.nuvio.app.features.iptv.IptvPlaybackRequests
import com.nuvio.app.features.iptv.IptvPlaylist
import com.nuvio.app.features.iptv.IptvPlaylistContent
import com.nuvio.app.features.iptv.IptvRepository
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
        val onNow = guide(content.playlist, channel).firstOrNull { it.startMs <= now && it.endMs > now }
        LiveTvHomeCard(
            ref = ref,
            name = channel.name.ifBlank { "Channel ${channel.num}" },
            logo = channel.logo?.takeIf { it.isNotBlank() },
            nowTitle = onNow?.title?.takeIf { it.isNotBlank() },
            nowEndMs = onNow?.endMs,
            nowImage = onNow?.image?.takeIf { it.isNotBlank() },
        )
    }

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
        _cards.value = LiveTvHomeRules.cards(
            favourites = settings.favourites,
            contents = contents,
            guide = { playlist, channel -> IptvEpgRepository.cached(playlist, channel).orEmpty() },
            now = System.currentTimeMillis(),
        )
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
