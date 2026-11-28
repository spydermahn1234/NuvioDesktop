package com.nuvio.app.features.settings
// FORK (Spyder): Nick's watch-along companion, not in UmbraProjects. See CLAUDE.md.

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.discover.DiscoverAiSettingsRepository
import com.nuvio.app.features.watchalong.AlongDensity
import com.nuvio.app.features.watchalong.WatchAlongSettingsRepository

/** fork:watch-along - Settings > Pre-show > Watch-along (called from PreshowSettingsPage). */
@Composable
internal fun WatchAlongSettingsGroup(isTablet: Boolean) {
    val settings by WatchAlongSettingsRepository.state.collectAsStateWithLifecycle()
    val ai by DiscoverAiSettingsRepository.uiState.collectAsStateWithLifecycle()
    var consentPromptFor by remember { mutableStateOf<AiConsentTarget?>(null) }
    SettingsGroup(isTablet = isTablet, modifier = Modifier.settingsSearchAnchors("watch-along")) {
        SettingsSwitchRow(
            title = "Watch-along",
            description = "A companion that knows where you are in the film. Notes turn up at their scene (how it was " +
                "shot, the song playing, who that actor is), pausing shows who's who, and Nuvio Remote's Along tab follows " +
                "on your phone. Nothing is shown before the film reaches it, so it never spoils what's coming.",
            checked = settings.enabled,
            isTablet = isTablet,
            onCheckedChange = WatchAlongSettingsRepository::setEnabled,
        )
        if (settings.enabled) {
            SettingsGroupDivider(isTablet)
            SettingsSwitchRow(
                title = "Write it with AI",
                description = if (ai.apiKey.isBlank()) {
                    "No AI provider is set up (Discover, then AI), so you get the cast as they turn up and the pre-show's " +
                        "facts. With one, each film's notes are written once from its subtitles before it starts and kept."
                } else {
                    "Your AI provider writes each film's notes once, from its subtitles, before it starts; they are kept, " +
                        "so a film is never asked for twice. Off uses the cast and the pre-show's facts only."
                },
                checked = settings.useAi,
                isTablet = isTablet,
                onCheckedChange = { wanted ->
                    when {
                        !wanted -> WatchAlongSettingsRepository.setUseAi(false)
                        ai.apiKey.isNotBlank() && !ai.consentGiven -> consentPromptFor = AiConsentTarget.WatchAlong
                        else -> WatchAlongSettingsRepository.setUseAi(true)
                    }
                },
            )
            SettingsGroupDivider(isTablet)
            SettingsSwitchRow(
                title = "Cards on the TV",
                description = "Notes pop up top right, in a quiet moment between lines. Off keeps the picture clean; " +
                    "the phone and the pause panel still have everything.",
                checked = settings.tvCards,
                isTablet = isTablet,
                onCheckedChange = WatchAlongSettingsRepository::setTvCards,
            )
            if (settings.tvCards) {
                SettingsGroupDivider(isTablet)
                SettingsChoiceRow(
                    title = "How many cards",
                    description = "The phone always gets every note; this only thins the ones on the TV.",
                    options = AlongDensity.entries.map { SettingsChoiceOption(it, it.label) },
                    selectedValue = settings.density,
                    isTablet = isTablet,
                    onSelected = WatchAlongSettingsRepository::setDensity,
                )
            }
            SettingsGroupDivider(isTablet)
            SettingsSwitchRow(
                title = "Around now on pause",
                description = "Beside the pause screen: who's in the scene (photo, character, actor) and the last few notes.",
                checked = settings.pausePanel,
                isTablet = isTablet,
                onCheckedChange = WatchAlongSettingsRepository::setPausePanel,
            )
        }
    }
    AiConsentDialog(target = consentPromptFor, onDismiss = { consentPromptFor = null })
}
