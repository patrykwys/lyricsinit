package com.patrykwys.lyricsinit.lyrics

import com.patrykwys.lyricsinit.media.Track
import com.patrykwys.lyricsinit.media.cleanTitle
import com.patrykwys.lyricsinit.media.primaryArtist
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

class LyricsRepository(
    private val client: LrclibClient,
    private val dao: LyricsDao,
) {
    /** Cached lyrics if we have them, otherwise LRCLIB. Throws only when offline with nothing cached. */
    suspend fun get(track: Track): Lyrics = withContext(Dispatchers.IO) {
        val id = track.cacheKey()
        val cached = dao.get(id)
        val now = System.currentTimeMillis()
        if (cached != null && (cached.hasContent || now - cached.fetchedAt < NOT_FOUND_RETRY_MS)) {
            return@withContext cached.toLyrics()
        }

        val found = try {
            fetch(track)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (cached != null) return@withContext cached.toLyrics()
            throw e
        }

        val entry = found.toEntity(id, fetchedAt = now)
        dao.upsert(entry)
        entry.toLyrics()
    }

    /**
     * Exact match first. If that has no synced lyrics, search with the raw and then the cleaned-up
     * title ("Song - Remastered 2011" → "Song"), keeping only results with a matching duration.
     */
    private suspend fun fetch(track: Track): LrclibTrack? {
        val durationSec = track.durationMs / 1000
        val calls = Calls()
        val direct = calls.attempt { client.get(track.title, track.artist, track.album, durationSec) }
        if (direct != null && (direct.hasSynced || direct.instrumental)) return direct

        val queries = listOf(
            track.title to track.artist,
            track.cleanTitle to track.primaryArtist,
        ).distinct()

        var fallback = direct?.takeIf { it.hasLyrics }
        for ((title, artist) in queries) {
            val match = calls.attempt { client.search(title, artist) }.orEmpty()
                .filter { it.hasLyrics && it.durationMatches(durationSec) }
                .minWithOrNull(compareBy<LrclibTrack>({ !it.hasSynced }, { abs((it.duration ?: 0.0) - durationSec) }))
            if (match?.hasSynced == true) return match
            fallback = fallback ?: match
        }
        calls.throwIfNoneAnswered()
        return fallback
    }

    /**
     * LRCLIB's exact lookup answers 503 "server busy" for songs it hasn't stored, for long stretches,
     * while search still works. So one failing call is remembered instead of thrown, and only
     * "nothing answered at all" (offline, LRCLIB down) is an error the engine retries.
     */
    private class Calls {
        private var failure: Exception? = null
        private var answered = false

        suspend fun <T> attempt(call: suspend () -> T): T? = try {
            call().also { answered = true }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (failure == null) failure = e
            null
        }

        fun throwIfNoneAnswered() {
            if (!answered) failure?.let { throw it }
        }
    }

    private fun LrclibTrack.durationMatches(durationSec: Long): Boolean =
        durationSec == 0L || duration == null || abs(duration - durationSec) <= DURATION_TOLERANCE_SEC

    private companion object {
        const val NOT_FOUND_RETRY_MS = 24 * 60 * 60 * 1000L
        const val DURATION_TOLERANCE_SEC = 5.0
    }
}

/** Shared by every cache table that is keyed by track. */
internal fun Track.cacheKey(): String = "${artist.lowercase().trim()}|${title.lowercase().trim()}|${durationMs / 1000}"

/** A null result still becomes a row, so "not found" is cached too. */
private fun LrclibTrack?.toEntity(id: String, fetchedAt: Long) = CachedLyrics(
    id = id,
    synced = this?.syncedLyrics,
    plain = this?.plainLyrics,
    instrumental = this?.instrumental == true,
    fetchedAt = fetchedAt,
)

private fun CachedLyrics.toLyrics(): Lyrics {
    synced?.let(LrcParser::parse)?.takeIf { it.isNotEmpty() }?.let { return Lyrics.Synced(it) }
    if (instrumental) return Lyrics.Instrumental
    plain?.takeIf { it.isNotBlank() }?.let { return Lyrics.Plain(it) }
    return Lyrics.NotFound
}
