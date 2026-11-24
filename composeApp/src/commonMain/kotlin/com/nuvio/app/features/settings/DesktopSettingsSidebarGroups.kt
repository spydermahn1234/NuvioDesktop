package com.nuvio.app.features.settings

import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.settings_group_account
import nuvio.composeapp.generated.resources.settings_group_home_look
import nuvio.composeapp.generated.resources.settings_group_home_theatre
import nuvio.composeapp.generated.resources.settings_group_more_to_watch
import nuvio.composeapp.generated.resources.settings_group_playback
import nuvio.composeapp.generated.resources.settings_group_sources
import nuvio.composeapp.generated.resources.settings_group_system
import org.jetbrains.compose.resources.StringResource

/**
 * fork:settings-groups - the headings the desktop Settings sidebar is split under, in their shipped
 * order. Each sidebar entry belongs to one group (see `desktopSettingsSidebarDefaultItems`).
 */
internal enum class DesktopSettingsGroup(val labelRes: StringResource) {
    Sources(Res.string.settings_group_sources),
    HomeAndLook(Res.string.settings_group_home_look),
    Playback(Res.string.settings_group_playback),
    HomeTheatre(Res.string.settings_group_home_theatre),
    MoreToWatch(Res.string.settings_group_more_to_watch),
    Account(Res.string.settings_group_account),
    System(Res.string.settings_group_system),
}

/**
 * The indices in [items] that get a group heading above them: the first entry of each group.
 *
 * The sidebar can be reordered by dragging and entries can be hidden, so the shown order may no
 * longer keep a group together. Headings only help while every group is one unbroken run; once any
 * group is split, no headings are shown at all rather than repeating a heading or labelling an
 * entry with the wrong group.
 */
internal fun <T> desktopSettingsGroupHeadings(
    items: List<T>,
    groupOf: (T) -> DesktopSettingsGroup,
): Set<Int> {
    val heads = mutableSetOf<Int>()
    val seen = mutableSetOf<DesktopSettingsGroup>()
    var previous: DesktopSettingsGroup? = null
    items.forEachIndexed { index, item ->
        val group = groupOf(item)
        if (group != previous) {
            if (!seen.add(group)) return emptySet()
            heads += index
            previous = group
        }
    }
    return heads
}
