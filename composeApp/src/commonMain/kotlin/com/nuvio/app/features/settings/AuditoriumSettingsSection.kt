package com.nuvio.app.features.settings
// FORK (Spyder): Nick's Auditorium, not in UmbraProjects. See CLAUDE.md.

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.auditorium.AuditoriumLength
import com.nuvio.app.features.auditorium.AuditoriumSettingsRepository
import com.nuvio.app.features.auditorium.AuditoriumThemeChoice

/** fork:auditorium - Settings > Pre-show > The Auditorium (called from PreshowSettingsPage). */
@Composable
internal fun AuditoriumSettingsGroup(isTablet: Boolean) {
    val settings by AuditoriumSettingsRepository.state.collectAsStateWithLifecycle()
    SettingsGroup(isTablet = isTablet, modifier = Modifier.settingsSearchAnchors("auditorium")) {
        SettingsSwitchRow(
            title = "The Auditorium",
            description = "Pressing Play on a film opens a cinema hall: the house lights dim, the curtains part and " +
                "the camera glides into the screen before the film starts. When the film plays to its end, it all " +
                "happens again in reverse.",
            checked = settings.enabled,
            isTablet = isTablet,
            onCheckedChange = AuditoriumSettingsRepository::setEnabled,
        )
        if (settings.enabled) {
            SettingsGroupDivider(isTablet)
            SettingsChoiceRow(
                title = "Hall",
                description = "Which cinema you sit in. A different hall per film picks one for each film and keeps " +
                    "to it, so the same film always opens in the same place.",
                options = AuditoriumThemeChoice.entries.map { SettingsChoiceOption(it, it.label) },
                selectedValue = settings.theme,
                isTablet = isTablet,
                onSelected = AuditoriumSettingsRepository::setTheme,
            )
            SettingsGroupDivider(isTablet)
            SettingsSwitchRow(
                title = "Pre-show on the hall's screen",
                description = "The trailers, clips and trivia play on the hall's screen with the curtains open, " +
                    "rather than full screen. Off, the hall plays first and the pre-show follows full screen as usual.",
                checked = settings.preshowInHall,
                isTablet = isTablet,
                onCheckedChange = AuditoriumSettingsRepository::setPreshowInHall,
            )
            SettingsGroupDivider(isTablet)
            SettingsSwitchRow(
                title = "Closing at the end",
                description = "When a film plays right to the end, the camera pulls back out, the curtains close and " +
                    "the house lights come up. Pressing Back skips it.",
                checked = settings.closing,
                isTablet = isTablet,
                onCheckedChange = AuditoriumSettingsRepository::setClosing,
            )
            SettingsGroupDivider(isTablet)
            SettingsSwitchRow(
                title = "Series episodes too",
                description = "Off keeps the hall for films. On, a fresh episode of a series gets it as well; " +
                    "resuming something never does.",
                checked = settings.includeSeries,
                isTablet = isTablet,
                onCheckedChange = AuditoriumSettingsRepository::setIncludeSeries,
            )
            SettingsGroupDivider(isTablet)
            SettingsChoiceRow(
                title = "Length",
                description = "How long the lights, curtains and camera take. Short gets you to the film sooner.",
                options = AuditoriumLength.entries.map { SettingsChoiceOption(it, it.label) },
                selectedValue = settings.length,
                isTablet = isTablet,
                onSelected = AuditoriumSettingsRepository::setLength,
            )
        }
    }
}
