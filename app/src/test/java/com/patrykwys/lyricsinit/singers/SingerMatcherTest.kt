package com.patrykwys.lyricsinit.singers

import com.patrykwys.lyricsinit.lyrics.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Test

class SingerMatcherTest {
    private val artists = listOf("Eminem", "Rihanna")

    // Trimmed from the real Genius page; note the verse's first line is one line here but two in LRCLIB.
    private val genius = """
        [Chorus: Rihanna]
        Just gonna stand there and watch me burn?
        Well, that's all right because I like the way it hurts

        [Verse 1: Eminem]
        I can't tell you what it really is, I can only tell you what it feels like
        And right now, there's a steel knife in my windpipe

        [Chorus]
        Just gonna stand there and hear me cry?

        [Outro: Both]
        I love the way you lie
    """.trimIndent()

    @Test
    fun `parses headers and carries a chorus credit to its repeats`() {
        val sections = GeniusSections.parse(genius, artists)
        assertEquals(
            listOf(setOf("Rihanna"), setOf("Eminem"), setOf("Rihanna"), setOf("Eminem", "Rihanna")),
            sections.map { it.singers },
        )
        assertEquals("And right now, there's a steel knife in my windpipe", sections[1].lines[1])
    }

    @Test
    fun `reads polish comma headers`() {
        val text = "[Refren, 730 Huncho]\nMami\n\n[Zwrotka 2, Malik Montana]\nJedziemy\n\n[Refren]\nMami"
        val sections = GeniusSections.parse(text, listOf("730 Huncho", "Malik Montana", "WIKTOR"))
        assertEquals(listOf(setOf("730 Huncho"), setOf("Malik Montana"), setOf("730 Huncho")), sections.map { it.singers })
    }

    @Test
    fun `all means the singers named in the song, not every credited act`() {
        val text = "[Intro: All]\nFire\n\n[Verse 1: SOYEON & Mimi Webb]\nLight it up\n\n[Pre-Chorus: MIYEON]\nBurn"
        val artists = listOf("K/DA", "League of Legends", "SOYEON", "MIYEON", "Mimi Webb")
        val sections = GeniusSections.parse(text, artists)
        assertEquals(setOf("SOYEON", "Mimi Webb", "MIYEON"), sections.first().singers)
    }

    @Test
    fun `credits synced lines even when the two sources split lines differently`() {
        val lines = listOf(
            "Just gonna stand there and watch me burn?",
            "Well, that's alright because I like the way it hurts",
            "♪",
            "I can't tell you what it really is",
            "I can only tell you what it feels like",
            "And right now, there's a steel knife in my windpipe",
            "Just gonna stand there and hear me cry?",
            "I love the way you lie",
        ).mapIndexed { i, text -> LyricLine(i * 1000L, text) }

        val credited = SingerMatcher.credit(lines, GeniusSections.parse(genius, artists))

        assertEquals(
            listOf(
                setOf("Rihanna"),
                setOf("Rihanna"),
                emptySet(), // between two different sections, so it stays uncredited
                setOf("Eminem"),
                setOf("Eminem"),
                setOf("Eminem"),
                setOf("Rihanna"),
                setOf("Eminem", "Rihanna"),
            ),
            credited.map { it.singers },
        )
    }

    @Test
    fun `keeps existing credits and leaves unmatched lines alone`() {
        val lines = listOf(
            LyricLine(0, "Just gonna stand there and watch me burn?", setOf("Eminem")),
            LyricLine(1, "Something Genius never wrote down"),
        )
        assertEquals(lines, SingerMatcher.credit(lines, GeniusSections.parse(genius, artists)))
    }
}
