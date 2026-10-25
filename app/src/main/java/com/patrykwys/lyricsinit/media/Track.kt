package com.patrykwys.lyricsinit.media

import android.os.SystemClock

data class Track(
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
)

/**
 * Playback position as last reported by Spotify. Spotify only reports on changes,
 * so the live position is extrapolated from [updatedAt] (elapsedRealtime clock).
 */
data class PlaybackSnapshot(
    val isPlaying: Boolean,
    val positionMs: Long,
    val updatedAt: Long,
    val speed: Float,
) {
    fun positionNow(now: Long = SystemClock.elapsedRealtime()): Long =
        if (isPlaying) positionMs + ((now - updatedAt) * speed).toLong() else positionMs

    companion object {
        val STOPPED = PlaybackSnapshot(isPlaying = false, positionMs = 0, updatedAt = 0, speed = 1f)
    }
}
