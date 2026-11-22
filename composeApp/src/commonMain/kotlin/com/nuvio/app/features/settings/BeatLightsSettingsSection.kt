package com.nuvio.app.features.settings
// FORK (Spyder): Nick's beat lights, not in UmbraProjects. See CLAUDE.md.

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.nuvio.app.features.music.beatlights.BeatLightsOn
import com.nuvio.app.features.music.beatlights.BeatLightsSettings
import com.nuvio.app.features.music.beatlights.BeatLightsSettingsRepository
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_beat_lights_address
import nuvio.composeapp.generated.resources.settings_beat_lights_address_description
import nuvio.composeapp.generated.resources.settings_beat_lights_early
import nuvio.composeapp.generated.resources.settings_beat_lights_early_description
import nuvio.composeapp.generated.resources.settings_beat_lights_enable
import nuvio.composeapp.generated.resources.settings_beat_lights_enable_description
import nuvio.composeapp.generated.resources.settings_beat_lights_flash
import nuvio.composeapp.generated.resources.settings_beat_lights_on
import nuvio.composeapp.generated.resources.settings_beat_lights_on_beat
import nuvio.composeapp.generated.resources.settings_beat_lights_on_description
import nuvio.composeapp.generated.resources.settings_beat_lights_on_downbeat
import nuvio.composeapp.generated.resources.settings_beat_lights_on_kick
import nuvio.composeapp.generated.resources.settings_beat_lights_rest
import nuvio.composeapp.generated.resources.settings_beat_lights_section
import org.jetbrains.compose.resources.stringResource

/** fork:beat-lights - Settings > Lights > Beat lights (music). The engine is BeatLightsEngine.desktop.kt. */
@Composable
internal fun BeatLightsSettingsSection(isTablet: Boolean) {
    val settings by BeatLightsSettingsRepository.state.collectAsState()
    SettingsSection(
        title = stringResource(Res.string.settings_beat_lights_section),
        isTablet = isTablet,
    ) {
        SettingsGroup(
            isTablet = isTablet,
            modifier = Modifier.settingsSearchAnchors("beat-lights"),
        ) {
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_beat_lights_enable),
                description = stringResource(Res.string.settings_beat_lights_enable_description),
                checked = settings.enabled,
                isTablet = isTablet,
                onCheckedChange = BeatLightsSettingsRepository::setEnabled,
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsTextInputRow(
                title = stringResource(Res.string.settings_beat_lights_address),
                description = stringResource(Res.string.settings_beat_lights_address_description),
                value = settings.wizAddress,
                placeholder = "192.168.1.40",
                keyboardType = KeyboardType.Uri,
                isTablet = isTablet,
                onSave = BeatLightsSettingsRepository::setWizAddress,
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsChoiceRow(
                title = stringResource(Res.string.settings_beat_lights_on),
                description = stringResource(Res.string.settings_beat_lights_on_description),
                options = listOf(
                    SettingsChoiceOption(BeatLightsOn.Kick, stringResource(Res.string.settings_beat_lights_on_kick)),
                    SettingsChoiceOption(BeatLightsOn.Beat, stringResource(Res.string.settings_beat_lights_on_beat)),
                    SettingsChoiceOption(BeatLightsOn.Downbeat, stringResource(Res.string.settings_beat_lights_on_downbeat)),
                ),
                selectedValue = settings.on,
                isTablet = isTablet,
                onSelected = BeatLightsSettingsRepository::setOn,
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsSliderRow(
                title = stringResource(Res.string.settings_beat_lights_early),
                description = stringResource(Res.string.settings_beat_lights_early_description),
                value = settings.earlyMs,
                valueText = "${settings.earlyMs} ms",
                valueTextForValue = { "$it ms" },
                valueRange = 0..BeatLightsSettings.MAX_EARLY_MS,
                step = 10,
                isTablet = isTablet,
                onValueChange = BeatLightsSettingsRepository::setEarlyMs,
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsSliderRow(
                title = stringResource(Res.string.settings_beat_lights_flash),
                value = settings.flashMs,
                valueText = "${settings.flashMs} ms",
                valueTextForValue = { "$it ms" },
                valueRange = 40..400,
                step = 10,
                isTablet = isTablet,
                onValueChange = BeatLightsSettingsRepository::setFlashMs,
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsSliderRow(
                title = stringResource(Res.string.settings_beat_lights_rest),
                value = settings.restPercent,
                valueText = "${settings.restPercent}%",
                valueTextForValue = { "$it%" },
                valueRange = 10..100,
                step = 5,
                isTablet = isTablet,
                onValueChange = BeatLightsSettingsRepository::setRestPercent,
            )
        }
    }
}
