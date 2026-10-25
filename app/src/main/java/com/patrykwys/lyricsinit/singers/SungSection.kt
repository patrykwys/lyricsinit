package com.patrykwys.lyricsinit.singers

/** A block of lyrics and who sings it, from a Genius header like `[Chorus: Rihanna]`. */
data class SungSection(val singers: Set<String>, val lines: List<String>)

/** Splits Genius lyrics text into [SungSection]s at its `[Section: Singer]` headers. */
object GeniusSections {
    private val header = Regex("""^\[([^\]]+)]$""")
    private val sectionNumber = Regex("""\s*\d+$""")

    private class Block(val kind: String, val credit: String, val lines: MutableList<String> = mutableListOf())

    /**
     * A header without a credit, like a later plain `[Chorus]`, takes the singers of the last
     * credited section of the same kind; Genius only credits a repeated chorus the first time.
     * "All" means everyone named somewhere in the song (K/DA's members, not "League of Legends"),
     * or the track's artists when nobody is named.
     */
    fun parse(text: String, artists: List<String>): List<SungSection> {
        val blocks = mutableListOf(Block(kind = "", credit = ""))
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val match = header.matchEntire(line)
            if (match == null) {
                blocks.last().lines += line
            } else {
                val (kind, credit) = match.groupValues[1].splitHeader()
                blocks += Block(kind.replace(sectionNumber, "").lowercase(), credit)
            }
        }

        val named = blocks.flatMapTo(linkedSetOf()) { resolveSingers(it.credit, artists, everyone = emptyList()) }
        val everyone = named.ifEmpty { artists }
        val creditsByKind = mutableMapOf<String, Set<String>>()
        return blocks.mapNotNull { block ->
            val singers = if (block.credit.isBlank()) {
                creditsByKind[block.kind].orEmpty()
            } else {
                resolveSingers(block.credit, artists, everyone).also { creditsByKind[block.kind] = it }
            }
            SungSection(singers, block.lines).takeIf { block.lines.isNotEmpty() }
        }
    }

    /** "Verse 1: Eminem" → ("Verse 1", "Eminem"). Polish pages use a comma: "Refren, 730 Huncho". */
    private fun String.splitHeader(): Pair<String, String> {
        val at = indexOf(':').takeIf { it >= 0 } ?: indexOf(',').takeIf { it >= 0 } ?: return trim() to ""
        return substring(0, at).trim() to substring(at + 1).trim()
    }
}
