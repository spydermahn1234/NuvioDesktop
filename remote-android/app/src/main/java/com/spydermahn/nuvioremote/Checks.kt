/*
 * DEV ONLY - NOT FOR END USERS. Part of Nuvio Remote's Checks tab, Nick's own testing tool for this fork's TESTING.md.
 * Do not merge or cherry-pick it into UmbraProjects/NuvioDesktop or any release for other people (see CLAUDE.md).
 */
package com.spydermahn.nuvioremote

/*
 * Checks (Nov 16): the open lines of TESTING.md, answered one at a time on the phone. This file is the plain rules
 * (no Android), tested by ChecksTest: reading the checklist, and writing answers back into it the way its header says.
 *
 *   - [x] check (2026-11-16)                               Works
 *   - [ ] **BROKEN** check (2026-11-16 21:04): note        Broken (the time helps find it in nuvio.log)
 *   - [~] check (2026-11-16): works, but note              Works, with a follow-up
 *
 * "Not tried" writes nothing. Lines already marked **BROKEN** are records, not questions; their re-checks (the
 * indented `- [ ]` under them) are the questions.
 *
 * Must test (Nov 22): an open line starting `(must) ` is also listed in a "Must test" section first, so the few checks
 * that matter are asked before the long tail. Its answer still goes to the line in its own section, tag kept.
 * Everything under a `# Archived` heading is not asked at all (checks set aside, kept for the record).
 */

enum class Verdict(val label: String) { Works("Works"), Broken("Broken"), Followup("Works, but…"), NotTried("Not tried") }

/**
 * One open check: its section, the words after `- [ ] `, how far it is indented, the line it re-checks, if any, and
 * its section's intro (where to turn the feature on), which the card shows even when it is asked under Must test.
 */
data class CheckItem(val section: String, val text: String, val indent: String, val parent: String?, val intro: String = "") {
    val key: String get() = "$section\u0001$text"
    val must: Boolean get() = Checks.isMust(text)
}

/** A heading of the checklist (`## Area`, or `## Area › Sub` for a `###`), what it says before its first check, and its open checks. */
data class CheckSection(val title: String, val intro: String, val items: List<CheckItem>)

data class CheckAnswer(
    val section: String,
    val text: String,
    val verdict: Verdict,
    val note: String = "",
    /** "21:04", when it was answered: written on Broken lines. */
    val time: String = "",
) {
    val key: String get() = "$section\u0001$text"
}

data class AppliedChecks(val markdown: String, val applied: List<CheckAnswer>, val missing: List<CheckAnswer>)

object Checks {
    private val heading = Regex("""^(#{2,3})\s+(.+?)\s*$""")
    private val open = Regex("""^(\s*)- \[ ] (.+?)\s*$""")
    private val anyItem = Regex("""^(\s*)- (?:\[[ x~]] )?(.+?)\s*$""")
    private const val INTRO_MAX = 280
    const val MUST_SECTION = "Must test"
    private const val MUST_TAG = "(must) "
    private val archived = Regex("""^#\s+Archived\b.*$""", RegexOption.IGNORE_CASE)

    fun isMust(text: String): Boolean = text.startsWith(MUST_TAG)

    /** Every heading with at least one open check, in the file's order. */
    fun parse(markdown: String): List<CheckSection> {
        val out = mutableListOf<CheckSection>()
        var area = ""
        var areaIntro = ""
        var title: String? = null
        var sub = false
        val intro = StringBuilder()
        var introDone = false
        var items = mutableListOf<CheckItem>()
        // The list items seen so far in this section, as (indent, text), for a re-check's parent.
        val seen = mutableListOf<Pair<Int, String>>()

        fun flush() {
            val t = title ?: return
            val own = intro.toString().trim()
            if (!sub) areaIntro = own
            // A ### with nothing of its own to say borrows its ##'s (where to turn the feature on, usually).
            val said = if (sub && own.isEmpty()) areaIntro else own
            if (items.isNotEmpty()) out += CheckSection(t, said.take(INTRO_MAX), items)
        }

        for (raw in markdown.split('\n')) {
            val line = raw.removeSuffix("\r")
            if (archived.matches(line)) break
            val h = heading.matchEntire(line)
            if (h != null) {
                flush()
                val name = h.groupValues[2]
                sub = h.groupValues[1].length == 3
                if (!sub) {
                    area = name
                    title = name
                } else {
                    title = subTitle(area, name)
                }
                intro.setLength(0)
                introDone = false
                items = mutableListOf()
                seen.clear()
                continue
            }
            if (title == null) continue
            val item = anyItem.matchEntire(line)
            if (item == null) {
                if (!introDone && line.isNotBlank()) {
                    if (intro.isNotEmpty()) intro.append(' ')
                    intro.append(plain(line.trim()))
                }
                continue
            }
            introDone = true
            val indent = item.groupValues[1]
            val o = open.matchEntire(line)
            if (o != null && !o.groupValues[2].startsWith("**BROKEN**")) {
                val parent = seen.lastOrNull { it.first < indent.length }?.second?.takeIf { indent.isNotEmpty() }
                val own = intro.toString().trim()
                val said = (if (sub && own.isEmpty()) areaIntro else own).take(INTRO_MAX)
                items += CheckItem(title!!, o.groupValues[2], indent, parent, said)
            }
            seen += indent.length to plain(item.groupValues[2].removePrefix("**BROKEN** "))
        }
        flush()
        val must = out.flatMap { s -> s.items.filter { it.must } }
        if (must.isEmpty()) return out
        val why = "The checks that matter most, oldest first. Each is also in its own area; answering it here answers it there."
        return listOf(CheckSection(MUST_SECTION, why, must)) + out
    }

    /** The line an answer becomes, without its indent. */
    fun answeredLine(answer: CheckAnswer, date: String): String? {
        val note = oneLine(answer.note)
        return when (answer.verdict) {
            Verdict.Works -> "- [x] ${answer.text} ($date)"
            Verdict.Broken -> {
                val stamp = if (answer.time.isBlank()) date else "$date ${answer.time}"
                "- [ ] **BROKEN** ${answer.text} ($stamp)" + if (note.isEmpty()) "" else ": $note"
            }
            Verdict.Followup -> "- [~] ${answer.text} ($date): works, but " + note.ifEmpty { "something needs doing" }
            Verdict.NotTried -> null
        }
    }

    /**
     * Writes [answers] into [markdown]: each finds its open line by its words, in its own section first, then anywhere
     * (a heading renamed since the phone read the file). Answers whose line has gone are returned as [AppliedChecks.missing].
     */
    fun apply(markdown: String, answers: List<CheckAnswer>, date: String): AppliedChecks {
        val crlf = markdown.contains("\r\n")
        val lines = markdown.split('\n').map { it.removeSuffix("\r") }.toMutableList()
        // The section each line is in, by the same titles parse() gives.
        val sectionOf = arrayOfNulls<String>(lines.size)
        var area = ""
        var title: String? = null
        lines.forEachIndexed { i, line ->
            heading.matchEntire(line)?.let { h ->
                val name = h.groupValues[2]
                if (h.groupValues[1].length == 2) { area = name; title = name } else title = subTitle(area, name)
            }
            sectionOf[i] = title
        }
        val used = mutableSetOf<Int>()
        val applied = mutableListOf<CheckAnswer>()
        val missing = mutableListOf<CheckAnswer>()

        fun find(answer: CheckAnswer, inSection: Boolean): Int? = lines.indices.firstOrNull { i ->
            if (i in used) return@firstOrNull false
            if (inSection && sectionOf[i] != answer.section) return@firstOrNull false
            val m = open.matchEntire(lines[i]) ?: return@firstOrNull false
            m.groupValues[2] == answer.text
        }

        for (answer in answers) {
            val written = answeredLine(answer, date) ?: continue
            val at = find(answer, inSection = true) ?: find(answer, inSection = false)
            if (at == null) {
                missing += answer
                continue
            }
            val indent = open.matchEntire(lines[at])!!.groupValues[1]
            lines[at] = indent + written
            used += at
            applied += answer
        }
        return AppliedChecks(lines.joinToString(if (crlf) "\r\n" else "\n"), applied, missing)
    }

    /** The answers as text to paste to Claude when the phone can't save to GitHub: the same lines, under their headings. */
    fun shareText(answers: List<CheckAnswer>, date: String): String {
        val written = answers.filter { it.verdict != Verdict.NotTried }
        if (written.isEmpty()) return ""
        return buildString {
            append("Checks answered on the phone ($date). Please record these in TESTING.md on Spyder:\n")
            written.groupBy { it.section }.forEach { (section, list) ->
                append("\n## ").append(section).append('\n')
                list.forEach { append(answeredLine(it, date)).append('\n') }
            }
        }.trimEnd()
    }

    /** A `###`'s title: "Area › Sub", or the sub alone when it already names its area ("Phone remote: wake the PC"). */
    private fun subTitle(area: String, name: String): String {
        val areaWord = area.substringBefore(" (").trim()
        return if (area.isEmpty() || name.startsWith(areaWord, ignoreCase = true)) name else "$area › $name"
    }

    /** "Moving **Settings** with `Esc`" → "Moving Settings with Esc", for the cards. */
    fun plain(text: String): String = text.removePrefix(MUST_TAG).replace("**", "").replace("`", "")

    private fun oneLine(text: String) = text.replace(Regex("""\s*\n\s*"""), " ").trim()
}
