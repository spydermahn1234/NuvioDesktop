package com.nuvio.app.features.player
// FORK (Spyder): Nick's watch-along companion, not in UmbraProjects. See CLAUDE.md.

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import com.nuvio.app.features.settings.ThemeSettingsRepository
import com.nuvio.app.features.watchalong.AlongRequest
import com.nuvio.app.features.watchalong.WatchAlongLive
import com.nuvio.app.features.watchalong.WatchAlongRepository
import com.nuvio.app.features.watchalong.WatchAlongRules
import com.nuvio.app.features.watchalong.WatchAlongSettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * fork:watch-along - feeds the companion (WatchAlongLive) for a film or an episode: builds its track once the
 * add-ons' subtitles are in (or after a wait, without them), then reports every position so the TV cards, the pause
 * panel and the phone follow the film. Not for Live TV, trailers, the YouTube tab or diagnostics.
 */
@Composable
internal fun PlayerScreenRuntime.BindWatchAlongEffect() {
    val settings by WatchAlongSettingsRepository.state.collectAsState()
    val isTrailer = disableProgressTracking && providerName.equals("YouTube", ignoreCase = true)
    if (!settings.enabled || isIptvPlayback || isYouTubeVideoPlayback || isTrailer || isProviderDiagnosticVideoPlayback) return

    val key = "$parentMetaType|$parentMetaId|$activeVideoId|$activeSeasonNumber|$activeEpisodeNumber"
    val runtime = this
    LaunchedEffect(key) {
        WatchAlongLive.begin(title)
        // The length is needed to spread notes without subtitles; it arrives with the first frames.
        val durationMs = withTimeoutOrNull(30_000) {
            snapshotFlow { runtime.playbackSnapshot.durationMs }.filter { it > 0L }.first()
        } ?: 0L
        val subtitle = withTimeoutOrNull(SUBTITLE_WAIT_MS) { runtime.awaitAlongSubtitle() }
        val built = WatchAlongRepository.build(
            AlongRequest(
                type = parentMetaType,
                metaId = parentMetaId,
                videoId = activeVideoId,
                title = title,
                season = activeSeasonNumber,
                episode = activeEpisodeNumber,
                episodeTitle = activeEpisodeTitle,
                subtitleUrl = subtitle?.url,
                durationMs = durationMs,
            ),
        )
        WatchAlongLive.ready(built?.track)
    }
    LaunchedEffect(key) {
        snapshotFlow { runtime.playbackSnapshot.let { it.positionMs to (it.isPlaying && !it.isLoading) } }
            .distinctUntilChanged()
            .collect { (position, playing) -> WatchAlongLive.tick(position, playing) }
    }
    DisposableEffect(key) {
        onDispose { WatchAlongLive.end() }
    }
}

/**
 * The add-on subtitle to read times from, once the add-ons have answered. The player fetches them at start unless
 * Fast startup is set; then they are asked for once here. Waits until cancelled (the caller bounds it).
 */
private suspend fun PlayerScreenRuntime.awaitAlongSubtitle(): AddonSubtitle {
    var asked = false
    while (true) {
        val found = SubtitleRepository.addonSubtitles.value
        val pick = WatchAlongRules.pickSubtitle(
            found,
            language = { it.language },
            wanted = ThemeSettingsRepository.selectedAppLanguage.value.code.substringBefore('-').lowercase(),
        )
        if (pick != null) return pick
        if (!asked && !SubtitleRepository.isLoading.value) {
            delay(6_000)
            if (SubtitleRepository.addonSubtitles.value.isEmpty() && !SubtitleRepository.isLoading.value) {
                fetchAddonSubtitlesForActiveItem()
            }
            asked = true
        }
        delay(1_000)
    }
}

private const val SUBTITLE_WAIT_MS = 45_000L
