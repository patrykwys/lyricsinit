package com.patrykwys.lyricsinit.singers

import com.patrykwys.lyricsinit.lyrics.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Test

class SingersTest {
    private val duet = listOf("Eminem", "Rihanna")

    @Test
    fun `splits a joint credit into the track's artists`() {
        assertEquals(setOf("Eminem", "Rihanna"), resolveSingers("Eminem & Rihanna", duet))
    }

    @Test
    fun `uses spotify's spelling for an artist`() {
        assertEquals(setOf("Beyoncé"), resolveSingers("beyonce", listOf("JAY-Z", "Beyoncé")))
    }

    @Test
    fun `both means every artist`() {
        assertEquals(duet.toSet(), resolveSingers("Both", duet))
    }

    @Test
    fun `keeps artists with separators in their name whole`() {
        val artists = listOf("Florence + the Machine", "Calvin Harris")
        assertEquals(artists.toSet(), resolveSingers("Florence + the Machine & Calvin Harris", artists))
    }

    @Test
    fun `keeps uncredited singers and drops ad-lib parentheses`() {
        assertEquals(setOf("Eminem", "Skylar Grey"), resolveSingers("Eminem & Skylar Grey (Rihanna)", duet))
    }

    @Test
    fun `orders track artists first`() {
        val lines = listOf(
            LyricLine(0, "a", setOf("Skylar Grey")),
            LyricLine(1, "b", setOf("Rihanna")),
            LyricLine(2, "c", setOf("Eminem")),
        )
        assertEquals(listOf("Eminem", "Rihanna", "Skylar Grey"), singerOrder(lines, duet))
    }

    @Test
    fun `reads inline prefixes for the track's artists only`() {
        val lines = listOf(
            LyricLine(0, "Rihanna: Just gonna stand there"),
            LyricLine(1, "Baby: come back"),
            LyricLine(2, "Both: I love the way you lie"),
        )
        val credited = SingerPrefixes.credit(lines, duet)
        assertEquals(LyricLine(0, "Just gonna stand there", setOf("Rihanna")), credited[0])
        assertEquals(lines[1], credited[1])
        assertEquals(LyricLine(2, "I love the way you lie", duet.toSet()), credited[2])
    }
}
