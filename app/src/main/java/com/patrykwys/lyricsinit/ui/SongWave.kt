package com.patrykwys.lyricsinit.ui

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * The hills drawn over the played part of the timeline. The app can't hear Spotify's audio, so this
 * isn't a real waveform: each song gets its own hills from [seed]. They are laid out along the song,
 * so they stay put while the bar grows, and each one breathes with the song's clock.
 */
internal class SongWave(seed: Int) {
    private val layers: List<Layer> = Random(seed).let { random ->
        LAYER_SHAPES.map { (hills, speed) -> Layer(hillPoints(random, hills), speed) }
    }

    val layerCount: Int get() = layers.size

    /** Height of [layer] at [fraction] of the song, 0..1, at [seconds] into it. */
    fun heightAt(layer: Int, fraction: Float, seconds: Float): Float {
        val shape = layers[layer]
        val p = fraction.coerceIn(0f, 1f) * (shape.points.size - 1)
        val i = p.toInt().coerceAtMost(shape.points.size - 2)
        // Cosine easing between points rounds the hills instead of joining them with straight slopes.
        val k = (1 - cos((p - i) * PI.toFloat())) / 2
        val a = shape.breathing(i, seconds)
        return a + (shape.breathing(i + 1, seconds) - a) * k
    }

    private class Layer(val points: FloatArray, val speed: Float) {
        // Neighbouring points are out of phase, so hills rise and fall on their own instead of in unison.
        fun breathing(i: Int, seconds: Float): Float = points[i] * (0.65f + 0.35f * sin(seconds * speed + i * 2.4f))
    }

    private companion object {
        /** Back to front: few slow wide hills, then more, quicker, narrower ones. Speed is in radians per second. */
        val LAYER_SHAPES = listOf(9 to 1.6f, 14 to 2.3f, 22 to 3.1f)

        /** Alternating valley and peak heights, so every peak stands out as its own hill. */
        fun hillPoints(random: Random, hills: Int) = FloatArray(hills * 2 + 1) { i ->
            if (i % 2 == 0) random.nextFloat() * 0.25f else 0.55f + random.nextFloat() * 0.45f
        }
    }
}
