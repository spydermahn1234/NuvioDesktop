/*
 * DEV ONLY - NOT FOR END USERS. Part of Nuvio Remote's Checks tab, Nick's own testing tool for this fork's TESTING.md.
 * Do not merge or cherry-pick it into UmbraProjects/NuvioDesktop or any release for other people (see CLAUDE.md).
 */
package com.spydermahn.nuvioremote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecksTest {
    private val sample = """
        # Testing checklist

        Rules up here are not a section.

        - [ ] not under a heading, ignored

        ## Phone remote

        - [x] Pairing works (2026-11-08)
        - [ ] Long titles scroll on the **film** card
        - [ ] **BROKEN** Settings ring (2026-11-08): no ring
          - [ ] Re-check: a ring sits on the open category

        ## Tonight picker (Nov 10)

        Turn it on in Settings > Random Play > `Open on Tonight`.
        It needs a library.

        ### Three Cards

        - [ ] The cards keep their size
        - [~] The reshuffle (2026-11-10): works, but slow

        ## All done

        - [x] Nothing left (2026-11-09)
    """.trimIndent()

    @Test
    fun `open checks are read by section, with intros and re-check parents`() {
        val sections = Checks.parse(sample)
        assertEquals(listOf("Phone remote", "Tonight picker (Nov 10) › Three Cards"), sections.map { it.title })
        val remote = sections[0]
        assertEquals(listOf("Long titles scroll on the **film** card", "Re-check: a ring sits on the open category"), remote.items.map { it.text })
        assertEquals(null, remote.items[0].parent)
        assertEquals("Settings ring (2026-11-08): no ring", remote.items[1].parent)
        assertEquals("  ", remote.items[1].indent)
        // A ### with no intro of its own borrows its ##'s.
        assertEquals("Turn it on in Settings > Random Play > Open on Tonight. It needs a library.", sections[1].intro)
    }

    @Test
    fun `the intro is the plain text between the heading and the first item`() {
        val s = Checks.parse("## A\n\nTurn on `X` in **Settings**.\nThen look.\n\n- [ ] one\n\nAfter, not intro.\n- [ ] two")
        assertEquals("Turn on X in Settings. Then look.", s[0].intro)
        assertEquals(2, s[0].items.size)
    }

    @Test
    fun `a sub-heading that names its area is not prefixed twice`() {
        val s = Checks.parse("## Phone remote (Nov 8)\n\n### Phone remote: wake the PC\n\n- [ ] wakes\n\n### Sources\n\n- [ ] picks")
        assertEquals(listOf("Phone remote: wake the PC", "Phone remote (Nov 8) › Sources"), s.map { it.title })
        val out = Checks.apply("## Phone remote (Nov 8)\n### Phone remote: wake the PC\n- [ ] wakes", listOf(CheckAnswer("Phone remote: wake the PC", "wakes", Verdict.Works)), "2026-11-16")
        assertTrue(out.markdown.endsWith("- [x] wakes (2026-11-16)"))
    }

    @Test
    fun `answers are written the way the header says`() {
        val answers = listOf(
            CheckAnswer("Phone remote", "Long titles scroll on the **film** card", Verdict.Works),
            CheckAnswer("Phone remote", "Re-check: a ring sits on the open category", Verdict.Broken, "still\nno ring", "21:04"),
            CheckAnswer("Tonight picker (Nov 10) › Three Cards", "The cards keep their size", Verdict.Followup, "the last one is taller"),
        )
        val out = Checks.apply(sample, answers, "2026-11-16")
        assertEquals(3, out.applied.size)
        assertTrue(out.missing.isEmpty())
        val lines = out.markdown.lines()
        assertTrue("- [x] Long titles scroll on the **film** card (2026-11-16)" in lines)
        assertTrue("  - [ ] **BROKEN** Re-check: a ring sits on the open category (2026-11-16 21:04): still no ring" in lines)
        assertTrue("- [~] The cards keep their size (2026-11-16): works, but the last one is taller" in lines)
        // Everything else is untouched.
        assertEquals(sample.lines().size, lines.size)
        assertTrue("- [ ] not under a heading, ignored" in lines)
    }

    @Test
    fun `not tried writes nothing, and a vanished line is reported`() {
        val out = Checks.apply(
            sample,
            listOf(
                CheckAnswer("Phone remote", "Long titles scroll on the **film** card", Verdict.NotTried),
                CheckAnswer("Phone remote", "A check Claude has since reworded", Verdict.Works),
            ),
            "2026-11-16",
        )
        assertEquals(sample, out.markdown)
        assertEquals(listOf("A check Claude has since reworded"), out.missing.map { it.text })
    }

    @Test
    fun `a heading renamed since the phone read the file still finds the line`() {
        val renamed = sample.replace("## Phone remote", "## Phone remote (Nov 8)")
        val out = Checks.apply(renamed, listOf(CheckAnswer("Phone remote", "Long titles scroll on the **film** card", Verdict.Works)), "2026-11-16")
        assertEquals(1, out.applied.size)
    }

    @Test
    fun `windows line endings stay windows line endings`() {
        val crlf = sample.replace("\n", "\r\n")
        assertEquals(1, Checks.parse(crlf).first().items.size.coerceAtMost(1))
        val out = Checks.apply(crlf, listOf(CheckAnswer("Phone remote", "Long titles scroll on the **film** card", Verdict.Works)), "2026-11-16")
        assertTrue(out.markdown.contains("- [x] Long titles scroll on the **film** card (2026-11-16)\r\n"))
        assertEquals(crlf.count { it == '\n' }, out.markdown.count { it == '\n' })
    }

    @Test
    fun `the share text groups lines under their headings and skips not tried`() {
        val text = Checks.shareText(
            listOf(
                CheckAnswer("Phone remote", "One", Verdict.Works),
                CheckAnswer("Phone remote", "Two", Verdict.NotTried),
                CheckAnswer("Live TV", "Three", Verdict.Broken, "it froze", "20:10"),
            ),
            "2026-11-16",
        )
        assertTrue(text.contains("## Phone remote\n- [x] One (2026-11-16)\n\n## Live TV\n- [ ] **BROKEN** Three (2026-11-16 20:10): it froze"))
        assertTrue(!text.contains("Two"))
        assertEquals("", Checks.shareText(listOf(CheckAnswer("A", "x", Verdict.NotTried)), "2026-11-16"))
    }

    @Test
    fun `the real checklist parses`() {
        val file = java.io.File("../../TESTING.md").takeIf { it.exists() } ?: return
        val sections = Checks.parse(file.readText())
        assertTrue(sections.sumOf { it.items.size } > 100)
        assertTrue(sections.none { s -> s.items.any { it.text.startsWith("**BROKEN**") } })
    }
}
