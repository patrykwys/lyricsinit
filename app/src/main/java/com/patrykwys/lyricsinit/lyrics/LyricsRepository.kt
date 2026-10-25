package com.patrykwys.lyricsinit.lyrics

import com.patrykwys.lyricsinit.media.Track
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

        val entry = CachedLyrics(id, found?.syncedLyrics, found?.plainLyrics, found?.instrumental == true, now)
        dao.upsert(entry)
        entry.toLyrics()
    }

    /**
     * Exact match first. If that has no synced lyrics, search with the raw and then the cleaned-up
     * title ("Song - Remastered 2011" → "Song"), keeping only results with a matching duration.
     */
    private suspend fun fetch(track: Track): LrclibTrack? {
        val durationSec = track.durationMs / 1000
        val direct = client.get(track.title, track.artist, track.album, durationSec)
        if (direct != null && (direct.hasSynced || direct.instrumental)) return direct

        val queries = listOf(
            track.title to track.artist,
            cleanTitle(track.title) to track.primaryArtist,
        ).distinct()

        var fallback = direct?.takeIf { it.hasLyrics }
        for ((title, artist) in queries) {
            val match = client.search(title, artist)
                .filter { it.hasLyrics && it.durationMatches(durationSec) }
                .minWithOrNull(compareBy<LrclibTrack>({ !it.hasSynced }, { abs((it.duration ?: 0.0) - durationSec) }))
            if (match?.hasSynced == true) return match
            fallback = fallback ?: match
        }
        return fallback
    }

    private fun LrclibTrack.durationMatches(durationSec: Long): Boolean =
        durationSec == 0L || duration == null || abs(duration - durationSec) <= DURATION_TOLERANCE_SEC

    private companion object {
        const val NOT_FOUND_RETRY_MS = 24 * 60 * 60 * 1000L
        const val DURATION_TOLERANCE_SEC = 5.0
    }
}

private fun Track.cacheKey(): String = "${artist.lowercase().trim()}|${title.lowercase().trim()}|${durationMs / 1000}"

private fun CachedLyrics.toLyrics(): Lyrics {
    synced?.let(LrcParser::parse)?.takeIf { it.isNotEmpty() }?.let { return Lyrics.Synced(it) }
    if (instrumental) return Lyrics.Instrumental
    plain?.takeIf { it.isNotBlank() }?.let { return Lyrics.Plain(it) }
    return Lyrics.NotFound
}

private val featuring = Regex("""\s*[(\[](feat\.?|ft\.?|with)\s[^)\]]*[)\]]""", RegexOption.IGNORE_CASE)
private val versionSuffix = Regex("""\s+-\s+.*$""")

private fun cleanTitle(title: String): String =
    title.replace(featuring, "").replace(versionSuffix, "").trim().ifEmpty { title }
