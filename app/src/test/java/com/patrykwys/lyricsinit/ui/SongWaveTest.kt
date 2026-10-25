package com.patrykwys.lyricsinit.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SongWaveTest {
    private val samples = (0..200).map { it / 200f }

    private fun SongWave.shape(layer: Int, seconds: Float) = samples.map { heightAt(layer, it, seconds) }

    @Test
    fun `stays between flat and full height`() {
        val wave = SongWave(seed = 42)
        for (layer in 0 until wave.layerCount) {
            for (seconds in listOf(0f, 1.3f, 97f, 240f)) {
                assertTrue(wave.shape(layer, seconds).all { it in 0f..1f })
            }
        }
    }

    @Test
    fun `the same song always gets the same hills`() {
        assertEquals(SongWave(seed = 7).shape(0, 12f), SongWave(seed = 7).shape(0, 12f))
    }

    @Test
    fun `different songs get different hills`() {
        assertNotEquals(SongWave(seed = 7).shape(0, 12f), SongWave(seed = 8).shape(0, 12f))
    }

    @Test
    fun `hills breathe as the song plays`() {
        val wave = SongWave(seed = 7)
        assertNotEquals(wave.shape(1, 10f), wave.shape(1, 10.5f))
    }

    @Test
    fun `clamps positions outside the song`() {
        val wave = SongWave(seed = 7)
        assertEquals(wave.heightAt(2, 0f, 5f), wave.heightAt(2, -0.5f, 5f))
        assertEquals(wave.heightAt(2, 1f, 5f), wave.heightAt(2, 1.5f, 5f))
    }
}
