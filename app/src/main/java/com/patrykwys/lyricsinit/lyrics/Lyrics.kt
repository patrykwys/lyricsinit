package com.patrykwys.lyricsinit.lyrics

/** [singers] is who sings this line; empty when nobody is credited. */
data class LyricLine(val timeMs: Long, val text: String, val singers: Set<String> = emptySet())

sealed interface Lyrics {
    /** [singers] is everyone credited on any line, in the order screens hand out colors. */
    data class Synced(val lines: List<LyricLine>, val singers: List<String> = emptyList()) : Lyrics {
        val hasMultipleSingers: Boolean get() = singers.size > 1
    }

    data class Plain(val text: String) : Lyrics
    data object Instrumental : Lyrics
    data object NotFound : Lyrics
}

/** Index of the line that should be showing at [positionMs], or -1 before the first line. */
fun List<LyricLine>.indexAt(positionMs: Long): Int {
    var low = 0
    var high = size - 1
    var found = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (this[mid].timeMs <= positionMs) {
            found = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return found
}
