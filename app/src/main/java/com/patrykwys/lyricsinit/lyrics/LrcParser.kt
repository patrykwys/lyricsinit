package com.patrykwys.lyricsinit.lyrics

/**
 * Parses LRC text: `[mm:ss.xx]line`. Handles several timestamps on one line,
 * skips metadata tags like `[ar:...]`, and strips enhanced-LRC word timings `<mm:ss.xx>`.
 */
object LrcParser {
    private val timestamp = Regex("""^\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val wordTimestamp = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")

    fun parse(lrc: String): List<LyricLine> = buildList {
        for (raw in lrc.lineSequence()) {
            var rest = raw.trim()
            val times = mutableListOf<Long>()
            while (true) {
                val match = timestamp.find(rest) ?: break
                val (minutes, seconds, fraction) = match.destructured
                times += minutes.toLong() * 60_000 + seconds.toLong() * 1_000 + fraction.padEnd(3, '0').toLong()
                rest = rest.substring(match.range.last + 1).trimStart()
            }
            val text = rest.replace(wordTimestamp, "").trim()
            times.forEach { add(LyricLine(it, text)) }
        }
    }.sortedBy { it.timeMs }
}
