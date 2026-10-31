package com.patrykwys.lyricsinit.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Blue, red, green, yellow: the same hues as the car's built-in colors, so a singer looks the same on both.
private val light = listOf(Color(0xFF1565C0), Color(0xFFC62828), Color(0xFF2E7D32), Color(0xFF9A6700))
private val dark = listOf(Color(0xFF90CAF9), Color(0xFFEF9A9A), Color(0xFFA5D6A7), Color(0xFFFFE082))

/** One color per voice, in [com.patrykwys.lyricsinit.sync.LyricsView.voices] order. */
@Composable
fun singerPalette(): List<Color> = if (isSystemInDarkTheme()) dark else light

/** Colors for [singers], in voice order; empty when none of them is a voice. */
fun List<Color>.forSingers(singers: Set<String>, voices: List<String>): List<Color> =
    voices.indices.filter { voices[it] in singers }.map { this[it % size] }
