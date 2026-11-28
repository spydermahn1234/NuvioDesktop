package com.nuvio.app.features.watchalong
// FORK (Spyder): Nick's watch-along companion, not in UmbraProjects. See CLAUDE.md.

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.discover.DiscoverAiClient
import com.nuvio.app.features.discover.DiscoverAiSettingsRepository
import com.nuvio.app.features.player.PlayerSubtitleCueParser
import com.nuvio.app.features.preroll.TriviaRepository
import com.nuvio.app.features.preroll.retryDelayMs
import com.nuvio.app.features.settings.ThemeSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What the player knows when it asks for a track. */
data class AlongRequest(
    val type: String,
    val metaId: String,
    val videoId: String?,
    val title: String,
    val season: Int?,
    val episode: Int?,
    val episodeTitle: String?,
    /** The add-on subtitle file to read times from, if the add-ons found one. */
    val subtitleUrl: String?,
    val durationMs: Long,
)

/** Where the track came from, for the status line on the phone and in the log. */
data class AlongBuilt(val track: AlongTrack, val fromCache: Boolean)

/**
 * fork:watch-along - builds one title's track before it is needed: the subtitle file read once, then either one
 * request to the user's own AI provider (Discover > AI, the same client and billing discipline as the pre-show's
 * trivia) or the offline track. An AI track is cached (WatchAlongCache) and never asked for twice; the offline one is
 * cheap and is rebuilt, so a provider set up later still gets its turn.
 */
object WatchAlongRepository {
    private val log = Logger.withTag("WatchAlong")

    suspend fun build(request: AlongRequest): AlongBuilt? {
        val meta = MetaDetailsRepository.peekAny(request.type, request.metaId)
            ?: withTimeoutOrNull(15_000) { runCatching { MetaDetailsRepository.fetch(request.type, request.metaId) }.getOrNull() }
        val cast = meta?.cast.orEmpty().map { WatchAlongRules.CastEntry(it.role.orEmpty(), it.name, it.photo) }
        val title = meta?.name ?: request.title
        val cues = request.subtitleUrl?.let { readCues(it) }.orEmpty()
        val ai = DiscoverAiSettingsRepository.snapshot()
        val language = ThemeSettingsRepository.selectedAppLanguage.value.code
        val wantsAi = WatchAlongSettingsRepository.state.value.useAi && ai.isConfigured
        val key = listOf(
            "along", "p$WATCH_ALONG_PROMPT_VERSION", request.metaId, request.videoId.orEmpty(), ai.effectiveModel, language,
        ).joinToString("|")

        if (wantsAi) {
            WatchAlongCache.get(key)?.let { cached ->
                log.i { "$title: cached track, ${cached.moments.size} moments" }
                return AlongBuilt(WatchAlongRules.withCast(cached, cast, cues), fromCache = true)
            }
            val asked = runCatching { ask(meta, title, request, cast, cues, language) }
                .onFailure { if (it is CancellationException) throw it }
                .getOrNull()
            if (asked != null) {
                WatchAlongCache.put(key, asked)
                return AlongBuilt(WatchAlongRules.withCast(asked, cast, cues), fromCache = false)
            }
            log.w { "$title: AI track failed; building the offline one" }
        }

        val facts = meta?.let { runCatching { TriviaRepository.peek(it) }.getOrNull() }?.slides?.map { it.text }.orEmpty()
        val offline = WatchAlongRules.offline(
            title = title,
            cast = cast,
            cues = cues,
            facts = facts,
            durationMs = request.durationMs,
        )
        log.i { "$title: offline track, ${offline.people.size} people, ${offline.moments.size} moments, ${cues.size} cues" }
        return if (offline.isEmpty) null else AlongBuilt(offline, fromCache = false)
    }

    private suspend fun ask(
        meta: MetaDetails?,
        title: String,
        request: AlongRequest,
        cast: List<WatchAlongRules.CastEntry>,
        cues: List<AlongCue>,
        language: String,
    ): AlongTrack? {
        val source = AlongSource(
            title = title,
            year = meta?.releaseInfo?.take(4),
            directors = meta?.director.orEmpty(),
            cast = cast,
            synopsis = meta?.description?.take(600),
            runtimeMs = request.durationMs,
            episode = if (request.season != null && request.episode != null) {
                "S${request.season}E${request.episode}" + request.episodeTitle?.let { " · $it" }.orEmpty()
            } else {
                null
            },
        )
        val prompt = buildWatchAlongPrompt(source, cues, language)
        val settings = DiscoverAiSettingsRepository.snapshot()
        log.i { "$title: asking ${settings.effectiveModel} for a track (${cues.size} cues)" }
        var attempt = 0
        while (true) {
            val outcome = DiscoverAiClient.complete(settings, prompt)
            outcome.getOrNull()?.let { raw ->
                val track = parseWatchAlong(raw, title, request.durationMs, timedBySubtitles = cues.isNotEmpty()) ?: return null
                log.i { "$title: ${track.moments.size} moments, ${track.people.size} people" }
                return WatchAlongRules.settle(track, cues)
            }
            val wait = retryDelayMs(outcome.exceptionOrNull(), attempt) ?: return null
            if (attempt >= 3) return null
            delay(wait)
            attempt++
        }
    }

    private suspend fun readCues(url: String): List<AlongCue> = runCatching {
        val text = withTimeoutOrNull(20_000) { httpGetText(url) } ?: return@runCatching emptyList()
        PlayerSubtitleCueParser.parse(text, url).map { AlongCue(it.startTimeMs, it.text) }
    }.onFailure { if (it is CancellationException) throw it; log.w { "subtitles unreadable: ${it.message}" } }
        .getOrDefault(emptyList())
}

@Serializable
internal data class WatchAlongCacheFile(
    val version: Int = VERSION,
    val entries: Map<String, WatchAlongCacheEntry> = emptyMap(),
) {
    companion object {
        const val VERSION = 1
    }
}

@Serializable
internal data class WatchAlongCacheEntry(val track: AlongTrack = AlongTrack(), val builtAtEpochMs: Long = 0L)

/** AI tracks already paid for, kept across launches. Every field has a default (see TriviaCache). */
internal object WatchAlongCache {
    private const val MAX_ENTRIES = 60
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Any()
    private var entries: MutableMap<String, WatchAlongCacheEntry>? = null

    private fun loaded(): MutableMap<String, WatchAlongCacheEntry> {
        entries?.let { return it }
        val restored = runCatching {
            WatchAlongCacheStorage.load()
                ?.let { json.decodeFromString<WatchAlongCacheFile>(it) }
                ?.takeIf { it.version == WatchAlongCacheFile.VERSION }
                ?.entries
        }.getOrNull().orEmpty()
        return LinkedHashMap(restored).also { entries = it }
    }

    fun get(key: String): AlongTrack? = synchronized(lock) { loaded()[key]?.track?.takeUnless { it.isEmpty } }

    fun put(key: String, track: AlongTrack) = synchronized(lock) {
        val map = loaded()
        map[key] = WatchAlongCacheEntry(track, System.currentTimeMillis())
        if (map.size > MAX_ENTRIES) {
            map.entries.sortedBy { it.value.builtAtEpochMs }.take(map.size - MAX_ENTRIES).map { it.key }.forEach { map.remove(it) }
        }
        runCatching { WatchAlongCacheStorage.save(json.encodeToString(WatchAlongCacheFile(entries = map))) }
    }

    fun clear() = synchronized(lock) {
        entries = LinkedHashMap()
        WatchAlongCacheStorage.save(null)
    }
}

/** Persists AI tracks across launches (desktop: the `nuvio_watch_along_cache` store). */
internal expect object WatchAlongCacheStorage {
    fun load(): String?
    fun save(json: String?)
}
