package com.patrykwys.lyricsinit.singers

import com.patrykwys.lyricsinit.lyrics.LyricLine

/**
 * Some LRC files credit lines inline, as `Rihanna: Just gonna stand there`. Only the track's own
 * artists count as credits, so a lyric like `Baby: come back` stays text.
 */
object SingerPrefixes {
    private val prefix = Regex("""^([^:]{1,40}):\s+(.+)$""")

    fun credit(lines: List<LyricLine>, artists: List<String>): List<LyricLine> = lines.map { line ->
        val match = prefix.matchEntire(line.text) ?: return@map line
        val (credit, text) = match.destructured
        val singers = resolveSingers(credit, artists)
        if (singers.isEmpty() || !artists.containsAll(singers)) line else line.copy(text = text, singers = singers)
    }
}
