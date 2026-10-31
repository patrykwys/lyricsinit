package com.patrykwys.lyricsinit.singers

import com.patrykwys.lyricsinit.lyrics.LyricLine
import com.patrykwys.lyricsinit.lyrics.Lyrics
import com.patrykwys.lyricsinit.media.Track
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditSingersUseCaseTest {
    private val track = Track("Love The Way You Lie", "Eminem, Rihanna", "Recovery", 263_000)
    private val sections = listOf(
        SungSection(setOf("Rihanna"), listOf("Just gonna stand there and watch me burn?")),
        SungSection(setOf("Eminem"), listOf("I can't tell you what it really is")),
    )

    private class FakeRepository(private val sections: List<SungSection>?) : SungSectionsRepository {
        var calls = 0
        override suspend fun sections(track: Track): List<SungSection>? = sections.also { calls++ }
    }

    @Test
    fun `emits the plain lyrics first, then the genius credits`() = runBlocking {
        val lyrics = Lyrics.Synced(
            listOf(
                LyricLine(0, "Just gonna stand there and watch me burn?"),
                LyricLine(1, "I can't tell you what it really is"),
            )
        )
        val emitted = CreditSingersUseCase(FakeRepository(sections))(track, lyrics).toList()

        assertEquals(lyrics, emitted[0])
        assertEquals(listOf(setOf("Rihanna"), setOf("Eminem")), emitted[1].lines.map { it.singers })
        // Spotify's artist order, not who sings first, so Eminem keeps the same color on every song.
        assertEquals(listOf("Eminem", "Rihanna"), emitted[1].singers)
        assertTrue(emitted[1].hasMultipleSingers)
    }

    @Test
    fun `inline prefixes skip genius`() = runBlocking {
        val repository = FakeRepository(sections)
        val lyrics = Lyrics.Synced(listOf(LyricLine(0, "Rihanna: Just gonna stand there")))
        val emitted = CreditSingersUseCase(repository)(track, lyrics).toList()

        assertEquals(listOf(Lyrics.Synced(listOf(LyricLine(0, "Just gonna stand there", setOf("Rihanna"))), listOf("Rihanna"))), emitted)
        assertFalse(emitted.single().hasMultipleSingers)
        assertEquals(0, repository.calls)
    }

    @Test
    fun `nothing on genius leaves the lyrics as they were`() = runBlocking {
        val lyrics = Lyrics.Synced(listOf(LyricLine(0, "I can't tell you what it really is")))
        assertEquals(listOf(lyrics), CreditSingersUseCase(FakeRepository(null))(track, lyrics).toList())
    }
}
