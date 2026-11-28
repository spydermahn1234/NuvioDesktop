package com.nuvio.app.features.auditorium
// FORK (Spyder): Nick's Auditorium, not in UmbraProjects. See CLAUDE.md.

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuditoriumSettingsTest {
    // The repository is a global object: every test starts from the defaults and leaves nothing behind.
    @BeforeTest
    fun reset() {
        AuditoriumSettingsRepository.persist = null
        AuditoriumSettingsRepository.load("{}")
    }

    @AfterTest
    fun tidy() {
        AuditoriumSettingsRepository.persist = null
        AuditoriumSettingsRepository.load("{}")
    }

    @Test
    fun defaults() {
        val d = AuditoriumSettings()
        assertFalse(d.enabled)
        assertEquals(AuditoriumThemeChoice.Random, d.theme)
        assertTrue(d.preshowInHall)
        assertTrue(d.closing)
        assertFalse(d.includeSeries)
        assertEquals(AuditoriumLength.Normal, d.length)
        assertEquals(d, AuditoriumSettingsRepository.state.value)
    }

    @Test
    fun everyChangeIsPersistedAndLoadsBack() {
        var saved: String? = null
        var saves = 0
        AuditoriumSettingsRepository.persist = { saved = it; saves++ }

        AuditoriumSettingsRepository.setEnabled(true)
        AuditoriumSettingsRepository.setTheme(AuditoriumThemeChoice.DriveIn)
        AuditoriumSettingsRepository.setPreshowInHall(false)
        AuditoriumSettingsRepository.setClosing(false)
        AuditoriumSettingsRepository.setIncludeSeries(true)
        AuditoriumSettingsRepository.setLength(AuditoriumLength.Short)
        assertEquals(6, saves)

        val expected = AuditoriumSettings(
            enabled = true,
            theme = AuditoriumThemeChoice.DriveIn,
            preshowInHall = false,
            closing = false,
            includeSeries = true,
            length = AuditoriumLength.Short,
        )
        assertEquals(expected, AuditoriumSettingsRepository.state.value)
        val payload = assertNotNull(saved)
        assertTrue(payload.contains("\"enabled\":true"), payload)
        assertTrue(payload.contains("\"DriveIn\""), payload)

        // Back to the defaults, then load what was saved.
        AuditoriumSettingsRepository.persist = null
        AuditoriumSettingsRepository.load("{}")
        assertEquals(AuditoriumSettings(), AuditoriumSettingsRepository.state.value)
        AuditoriumSettingsRepository.load(payload)
        assertEquals(expected, AuditoriumSettingsRepository.state.value)
    }

    @Test
    fun defaultsAreWrittenOutToo() {
        var saved: String? = null
        AuditoriumSettingsRepository.persist = { saved = it }
        AuditoriumSettingsRepository.setEnabled(false)
        val payload = assertNotNull(saved)
        for (key in listOf("enabled", "theme", "preshowInHall", "closing", "includeSeries", "length")) {
            assertTrue(payload.contains("\"$key\""), "$key missing from $payload")
        }
    }

    @Test
    fun unknownKeysAreIgnored() {
        AuditoriumSettingsRepository.load("""{"enabled":true,"someFutureSwitch":5,"length":"Short"}""")
        assertEquals(
            AuditoriumSettings(enabled = true, length = AuditoriumLength.Short),
            AuditoriumSettingsRepository.state.value,
        )
    }

    @Test
    fun blankOrBrokenPayloadKeepsWhatIsThere() {
        AuditoriumSettingsRepository.load(null)
        AuditoriumSettingsRepository.load("")
        AuditoriumSettingsRepository.load("   ")
        assertEquals(AuditoriumSettings(), AuditoriumSettingsRepository.state.value)

        AuditoriumSettingsRepository.load("""{"enabled":true}""")
        AuditoriumSettingsRepository.load("")
        AuditoriumSettingsRepository.load("not json")
        assertEquals(AuditoriumSettings(enabled = true), AuditoriumSettingsRepository.state.value)
    }

    @Test
    fun loadingDoesNotPersist() {
        var saves = 0
        AuditoriumSettingsRepository.persist = { saves++ }
        AuditoriumSettingsRepository.load("""{"enabled":true}""")
        assertEquals(0, saves)
    }
}
