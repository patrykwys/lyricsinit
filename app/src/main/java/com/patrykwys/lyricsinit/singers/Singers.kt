package com.patrykwys.lyricsinit.singers

import com.patrykwys.lyricsinit.lyrics.LyricLine
import java.text.Normalizer

/** Credits meaning everyone on the track, as Genius writes them in headers like `[Outro: Both]`. */
private val everyoneWords = setOf("both", "all", "everyone", "together", "all artists")
private val nameSeparators = Regex("""\s*(?:&|,|/|\+|\band\b|\bwith\b)\s*""", RegexOption.IGNORE_CASE)
private val parenthetical = Regex("""\([^)]*\)""")

/**
 * Turns a credit like "Eminem & Rihanna" or "Both" into singer names. Names that match one of the
 * track's [artists] use Spotify's spelling, so the same person gets the same color everywhere.
 * Artists are found before splitting, so names like "Florence + the Machine" stay whole.
 * "Both" or "All" becomes [everyone], which defaults to the track's artists.
 */
internal fun resolveSingers(
    credit: String,
    artists: List<String>,
    everyone: Collection<String> = artists,
): Set<String> {
    val rest = credit.replace(parenthetical, " ").replace("*", "").toCharArray()
    val folded = String(rest).folded()
    val found = linkedSetOf<String>()
    for (artist in artists.sortedByDescending { it.length }) {
        val key = artist.folded()
        var start = folded.indexOfWord(key, from = 0)
        while (start != null) {
            found += artist
            rest.fill(',', start, start + key.length)
            start = folded.indexOfWord(key, from = start + key.length)
        }
    }
    String(rest).split(nameSeparators)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .forEach { name -> if (name.folded() in everyoneWords) found += everyone else found += name }
    return found
}

/** Everyone credited on [lines]: the track's [artists] first, in Spotify's order, then anyone else. */
internal fun singerOrder(lines: List<LyricLine>, artists: List<String>): List<String> {
    val credited = lines.flatMapTo(linkedSetOf()) { it.singers }
    return artists.filter { it in credited } + (credited - artists.toSet())
}

/** Lowercase without accents, one char per char so indices still line up with the original. */
internal fun String.folded(): String =
    map { Normalizer.normalize(it.toString(), Normalizer.Form.NFD).first().lowercaseChar() }.joinToString("")

/** Letters and digits only, for comparing titles and names across services. */
internal fun String.matchKey(): String = folded().filter { it.isLetterOrDigit() }

/** First whole-word occurrence of [word] at or after [from]. */
private fun String.indexOfWord(word: String, from: Int): Int? {
    if (word.isEmpty()) return null
    var searchFrom = from
    while (true) {
        val at = indexOf(word, searchFrom).takeIf { it >= 0 } ?: return null
        val end = at + word.length
        val bounded = (at == 0 || !this[at - 1].isLetterOrDigit()) && (end == length || !this[end].isLetterOrDigit())
        if (bounded) return at
        searchFrom = at + 1
    }
}
