package com.nuvio.app.features.settings

import com.nuvio.app.features.settings.DesktopSettingsGroup.Account
import com.nuvio.app.features.settings.DesktopSettingsGroup.Playback
import com.nuvio.app.features.settings.DesktopSettingsGroup.Sources
import com.nuvio.app.features.settings.DesktopSettingsGroup.System
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopSettingsGroupHeadingsTest {
    private fun heads(vararg groups: DesktopSettingsGroup) =
        desktopSettingsGroupHeadings(groups.toList()) { it }

    @Test
    fun headsTheFirstEntryOfEachGroup() {
        assertEquals(setOf(0, 2, 3), heads(Sources, Sources, Playback, Account, Account))
    }

    @Test
    fun keepsHeadingsWhenAGroupIsMovedWhole() {
        assertEquals(setOf(0, 2), heads(Playback, Playback, Sources, Sources))
    }

    @Test
    fun dropsAllHeadingsOnceAGroupIsSplit() {
        assertEquals(emptySet(), heads(Sources, Playback, Sources))
    }

    @Test
    fun aHiddenGroupLeavesNoGap() {
        assertEquals(setOf(0, 1), heads(Sources, System))
    }

    @Test
    fun emptyListHasNoHeadings() {
        assertEquals(emptySet(), heads())
    }
}
