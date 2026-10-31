package com.patrykwys.lyricsinit.media

import android.os.SystemClock

data class Track(
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
)

/** First credited artist; Spotify joins features as "A, B". */
val Track.primaryArtist: String
    get() = artist.substringBefore(",").trim()

/** Every credited artist, in Spotify's order. */
val Track.artists: List<String>
    get() = artist.split(",").map { it.trim() }.filter { it.isNotEmpty() }

/** Title without features or version suffixes: "Song (feat. X) - Remastered 2011" → "Song". */
val Track.cleanTitle: String
    get() = title.replace(featuring, "").replace(versionSuffix, "").trim().ifEmpty { title }

private val featuring = Regex("""\s*[(\[](feat\.?|ft\.?|with)\s[^)\]]*[)\]]""", RegexOption.IGNORE_CASE)
private val versionSuffix = Regex("""\s+-\s+.*$""")

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
