package com.patrykwys.lyricsinit.singers

/** A block of lyrics and who sings it, from a Genius header like `[Chorus: Rihanna]`. */
data class SungSection(val singers: Set<String>, val lines: List<String>)

/** Splits Genius lyrics text into [SungSection]s at its `[Section: Singer]` headers. */
object GeniusSections {
    private val header = Regex("""^\[([^\]:]+?)\s*(?::\s*([^\]]*))?]$""")
    private val sectionNumber = Regex("""\s*\d+$""")

    /**
     * A header without a credit, like a later plain `[Chorus]`, takes the singers of the last
     * credited section of the same kind; Genius only credits a repeated chorus the first time.
     */
    fun parse(text: String, artists: List<String>): List<SungSection> {
        val sections = mutableListOf<SungSection>()
        val creditsByKind = mutableMapOf<String, Set<String>>()
        var singers = emptySet<String>()
        var lines = mutableListOf<String>()

        fun close() {
            if (lines.isNotEmpty()) sections += SungSection(singers, lines)
            lines = mutableListOf()
        }

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val match = header.matchEntire(line)
            if (match == null) {
                lines += line
                continue
            }
            close()
            val (name, credit) = match.destructured
            val kind = name.replace(sectionNumber, "").lowercase()
            singers = if (credit.isBlank()) {
                creditsByKind[kind].orEmpty()
            } else {
                resolveSingers(credit, artists).also { creditsByKind[kind] = it }
            }
        }
        close()
        return sections
    }
}
