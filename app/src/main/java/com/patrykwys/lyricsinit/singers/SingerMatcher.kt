package com.patrykwys.lyricsinit.singers

import com.patrykwys.lyricsinit.lyrics.LyricLine

/**
 * Credits synced lines with the singers of Genius sections. The two sources break lines in
 * different places, so the texts are aligned word by word (longest common subsequence) and each
 * synced line gets the section most of its words landed in.
 */
object SingerMatcher {
    private const val NONE = -1
    private const val MATCH: Byte = 0
    private const val SKIP_LINE_WORD: Byte = 1
    private const val SKIP_SECTION_WORD: Byte = 2

    // The step table is one byte per word pair: about 4 MB at the cap, far above a normal song.
    private const val MAX_WORD_PAIRS = 4_000_000L

    private val wordPattern = Regex("""[\p{L}\p{N}]+""")

    /** A word reduced to an id for fast comparison, and the line or section it came from. */
    private class Word(val id: Int, val owner: Int)

    /** Lines that already have singers keep them. */
    fun credit(lines: List<LyricLine>, sections: List<SungSection>): List<LyricLine> {
        val ids = HashMap<String, Int>()
        val lineWords = lines.flatMapIndexed { i, line -> line.text.words(ids, owner = i) }
        val sectionWords = sections.flatMapIndexed { i, section ->
            section.lines.flatMap { it.words(ids, owner = i) }
        }
        if (lineWords.isEmpty() || sectionWords.isEmpty()) return lines
        if (lineWords.size.toLong() * sectionWords.size > MAX_WORD_PAIRS) return lines

        val sectionOfWord = align(lineWords, sectionWords)
        val sectionOfLine = IntArray(lines.size) { NONE }
        lineWords.indices.groupBy { lineWords[it].owner }.forEach { (line, wordIndices) ->
            val votes = wordIndices.map { sectionOfWord[it] }.filter { it != NONE }
            val (section, count) = votes.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: return@forEach
            // Half the words is enough to survive spelling differences ("alright" / "all right").
            if (count * 2 >= wordIndices.size) sectionOfLine[line] = section
        }
        sectionOfLine.fillGaps()

        return lines.mapIndexed { i, line ->
            val section = sectionOfLine[i]
            if (line.singers.isNotEmpty() || section == NONE) line else line.copy(singers = sections[section].singers)
        }
    }

    private fun String.words(ids: MutableMap<String, Int>, owner: Int): List<Word> =
        wordPattern.findAll(folded().replace("'", "").replace("’", ""))
            .map { Word(ids.getOrPut(it.value) { ids.size }, owner) }
            .toList()

    /** For each line word, the section of the Genius word it aligned with, or [NONE]. */
    private fun align(lineWords: List<Word>, sectionWords: List<Word>): IntArray {
        val n = lineWords.size
        val m = sectionWords.size
        val steps = ByteArray(n * m)
        var previous = IntArray(m + 1)
        var current = IntArray(m + 1)
        for (i in 1..n) {
            for (j in 1..m) {
                val step = (i - 1) * m + (j - 1)
                when {
                    lineWords[i - 1].id == sectionWords[j - 1].id -> {
                        current[j] = previous[j - 1] + 1
                        steps[step] = MATCH
                    }
                    previous[j] >= current[j - 1] -> {
                        current[j] = previous[j]
                        steps[step] = SKIP_LINE_WORD
                    }
                    else -> {
                        current[j] = current[j - 1]
                        steps[step] = SKIP_SECTION_WORD
                    }
                }
            }
            previous = current.also { current = previous }
        }

        val sectionOfWord = IntArray(n) { NONE }
        var i = n
        var j = m
        while (i > 0 && j > 0) {
            when (steps[(i - 1) * m + (j - 1)]) {
                MATCH -> {
                    sectionOfWord[i - 1] = sectionWords[j - 1].owner
                    i--
                    j--
                }
                SKIP_LINE_WORD -> i--
                else -> j--
            }
        }
        return sectionOfWord
    }

    /** An unmatched run between two lines of the same section (a "♪" break, an ad-lib) joins it. */
    private fun IntArray.fillGaps() {
        var last = NONE
        for (i in indices) {
            if (this[i] == NONE) continue
            if (last != NONE && this[last] == this[i]) fill(this[i], last + 1, i)
            last = i
        }
    }
}
