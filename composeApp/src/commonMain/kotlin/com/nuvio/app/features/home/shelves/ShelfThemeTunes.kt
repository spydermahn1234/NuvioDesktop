package com.nuvio.app.features.home.shelves

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.LocalScreenCovered
import com.nuvio.app.features.music.MusicPlayback
import com.nuvio.app.features.player.AnimeContentCache
import com.nuvio.app.features.player.isAnimeFromGenres
import com.nuvio.app.features.remote.RemotePlayerHub
import com.nuvio.app.features.themesong.ThemeSongPlayer
import com.nuvio.app.features.themesong.ThemeSongRepository
import com.nuvio.app.features.themesong.ThemeSongSettings
import com.nuvio.app.features.trailer.TrailerPlaybackSource
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/*
 * fork:shelf-home - v3 phase 8, theme tunes (board V3Tunes, plan in design-notes/home-shelves-v3.md): a show focused
 * for 1.5 s plays its theme quietly through the details page's ThemeSongPlayer at the theme-song volume, fading in
 * over 1 s and out over 1 s when you move on. Films get none. Never over a trailer's sound, the film player or music,
 * and never while the home is covered. Off by default (ShelfExtrasSettings.themeTunes).
 */

/** What to look a card's theme up by. [key] is the show, so its Continue watching card and its catalog card match. */
internal data class ShelfThemeTarget(
    val key: String,
    val type: String,
    val title: String,
    val releaseInfo: String?,
    val metaId: String,
    val genres: List<String>,
)

internal object ShelfThemeTunesRules {
    /** How long a show must stay focused before its theme is looked up. */
    const val WAIT_MS = 1_500L

    /** Fade in and fade out. */
    const val FADE_MS = 1_000L

    /** How often the fade moves the level. */
    const val FADE_STEP_MS = 50L

    private val showTypes = setOf("series", "show", "anime")

    /** A show's catalogue type. "tv" and "channel" are live channels in Stremio, not shows. */
    fun isShowType(type: String?): Boolean = type?.trim()?.lowercase() in showTypes

    /** The show behind [card], or null for a film, a folder, a live channel or a card with no name. */
    fun target(card: ShelfCard?): ShelfThemeTarget? = when (card) {
        null, is ShelfCard.Folder -> null
        is ShelfCard.Meta -> card.item.takeIf { isShowType(it.type) && it.name.isNotBlank() && it.id.isNotBlank() }
            ?.let {
                ShelfThemeTarget(
                    key = it.id,
                    type = it.type,
                    title = it.name.trim(),
                    releaseInfo = it.releaseInfo ?: it.rawReleaseDate,
                    metaId = it.id,
                    genres = it.genres,
                )
            }
        is ShelfCard.Progress -> card.item.let { item ->
            // An episode number makes it a show whatever its type says; a film's progress never has one.
            val show = isShowType(item.parentMetaType) ||
                (item.seasonNumber != null && item.episodeNumber != null && !item.parentMetaType.equals("movie", true))
            if (!show || item.title.isBlank() || item.parentMetaId.isBlank()) {
                null
            } else {
                ShelfThemeTarget(
                    key = item.parentMetaId,
                    type = item.parentMetaType.ifBlank { "series" },
                    title = item.title.trim(),
                    // `released` is the episode's date, not the show's first year: search without a year.
                    releaseInfo = null,
                    metaId = item.parentMetaId,
                    genres = emptyList(),
                )
            }
        }
    }

    /** Whether a theme may sound now: nothing else making sound and the home on screen and asked for. */
    fun mayPlay(active: Boolean, covered: Boolean, trailerAudible: Boolean, filmPlaying: Boolean, musicPlaying: Boolean): Boolean =
        active && !covered && !trailerAudible && !filmPlaying && !musicPlaying

    /** The fade's level after [elapsedMs] of a fade from [from] to [to] (0..1), linear over [FADE_MS]. */
    fun fade(from: Float, to: Float, elapsedMs: Long): Float {
        if (elapsedMs >= FADE_MS) return to
        if (elapsedMs <= 0L) return from
        return from + (to - from) * (elapsedMs.toFloat() / FADE_MS)
    }

    /**
     * The volume handed to the player for a fade [level] (0..1) of the setting [volume]. Never 0 while a track is
     * mounted: ThemeSongPlayer drops its surface at 0 and would not come back when the level rises again.
     */
    fun playerVolume(volume: Int, level: Float): Int =
        (volume.coerceIn(0, 100) * level.coerceIn(0f, 1f)).roundToInt().coerceIn(1, 100)
}

/**
 * The focused show's theme, played quietly. Returns whether one is playing (for "♪ THEME · QUIET" under the clock).
 * [active] is the setting and the home being browsed or idle; the film player, music and a covered home stop it too.
 */
@Composable
internal fun ShelfThemeTune(card: ShelfCard?, active: Boolean, trailerAudible: Boolean = false): Boolean {
    val volume by ThemeSongSettings.volume.collectAsStateWithLifecycle()
    val film by RemotePlayerHub.film.collectAsStateWithLifecycle()
    val music by MusicPlayback.player.state.collectAsStateWithLifecycle()
    val covered = LocalScreenCovered.current
    val allowed = ShelfThemeTunesRules.mayPlay(
        active = active,
        covered = covered,
        trailerAudible = trailerAudible,
        filmPlaying = film != null,
        musicPlaying = music.isPlaying,
    )
    val target = remember(card?.stableId()) { ShelfThemeTunesRules.target(card) }

    var source by remember { mutableStateOf<TrailerPlaybackSource?>(null) }
    var sourceKey by remember { mutableStateOf<String?>(null) }
    var level by remember { mutableFloatStateOf(0f) }
    // A show whose theme was not found or would not play: not tried again until another card is focused.
    var gaveUpKey by remember { mutableStateOf<String?>(null) }

    suspend fun fadeTo(to: Float) {
        val from = level
        var elapsed = 0L
        while (elapsed < ShelfThemeTunesRules.FADE_MS) {
            delay(ShelfThemeTunesRules.FADE_STEP_MS)
            elapsed += ShelfThemeTunesRules.FADE_STEP_MS
            level = ShelfThemeTunesRules.fade(from, to, elapsed)
        }
        level = to
    }

    LaunchedEffect(target?.key, allowed) {
        val wanted = target?.takeIf { allowed }
        if (wanted == null || wanted.key != gaveUpKey) gaveUpKey = null
        // Moving on (or something else wanting quiet): fade the current theme out first.
        if (source != null && (wanted == null || wanted.key != sourceKey)) {
            fadeTo(0f)
            source = null
            sourceKey = null
        }
        if (wanted == null) return@LaunchedEffect
        if (source != null && sourceKey == wanted.key) {
            // Back on the same show before its fade out finished: bring it back up.
            fadeTo(1f)
            return@LaunchedEffect
        }
        if (gaveUpKey == wanted.key) return@LaunchedEffect
        delay(ShelfThemeTunesRules.WAIT_MS)
        val isAnime = wanted.type.equals("anime", ignoreCase = true) ||
            AnimeContentCache.isAnime(wanted.metaId) ||
            (wanted.genres.isNotEmpty() && isAnimeFromGenres(genres = wanted.genres))
        val found = runCatching {
            ThemeSongRepository.resolve(wanted.type, wanted.title, wanted.releaseInfo, wanted.metaId, isAnime)
        }.getOrNull()
        if (found == null) {
            gaveUpKey = wanted.key
            return@LaunchedEffect
        }
        level = 0f
        source = found
        sourceKey = wanted.key
        fadeTo(1f)
    }

    ThemeSongPlayer(
        source = source,
        volume = ShelfThemeTunesRules.playerVolume(volume, level),
        onFinished = {
            gaveUpKey = sourceKey
            source = null
            sourceKey = null
            level = 0f
        },
    )
    return source != null
}
