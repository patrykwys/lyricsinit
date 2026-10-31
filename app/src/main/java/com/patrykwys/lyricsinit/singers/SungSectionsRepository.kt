package com.patrykwys.lyricsinit.singers

import android.util.Log
import com.patrykwys.lyricsinit.lyrics.CachedGeniusLyrics
import com.patrykwys.lyricsinit.lyrics.GeniusLyricsDao
import com.patrykwys.lyricsinit.lyrics.cacheKey
import com.patrykwys.lyricsinit.media.Track
import com.patrykwys.lyricsinit.media.artists
import com.patrykwys.lyricsinit.media.cleanTitle
import com.patrykwys.lyricsinit.media.primaryArtist
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/** Who sings which part of a track. Null when nobody knows. */
interface SungSectionsRepository {
    suspend fun sections(track: Track): List<SungSection>?
}

/**
 * Genius sections, cached like the lyrics so they work without signal. A failed lookup is not
 * cached, so the next play tries again; the lyrics still show meanwhile, just without singers.
 */
class GeniusSectionsRepository(
    private val client: GeniusClient,
    private val dao: GeniusLyricsDao,
) : SungSectionsRepository {

    override suspend fun sections(track: Track): List<SungSection>? = withContext(Dispatchers.IO) {
        val id = track.cacheKey()
        val cached = dao.get(id)
        val now = System.currentTimeMillis()
        if (cached != null && (cached.text != null || now - cached.fetchedAt < NOT_FOUND_RETRY_MS)) {
            return@withContext cached.toSections(track)
        }

        val entry = try {
            CachedGeniusLyrics(id, fetchPatiently(track), fetchedAt = now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Genius lookup failed for ${track.artist} - ${track.title}", e)
            return@withContext cached?.toSections(track)
        }
        dao.upsert(entry)
        entry.toSections(track)
    }

    /** Genius's false 404s come in stretches, so a failed lookup waits and tries again while the song plays. */
    private suspend fun fetchPatiently(track: Track): String? {
        for (wait in RETRY_DELAYS_MS) {
            try {
                return fetch(track)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.i(TAG, "Genius lookup failed, retrying in ${wait / 1000}s: ${e.message}")
            }
            delay(wait.milliseconds)
        }
        return fetch(track)
    }

    /**
     * Only hits with the same title and artist count, which skips Genius's translation pages.
     * A page that fails moves on to the next matching hit; if all of them fail, the error stands,
     * so nothing is cached and the next play tries again.
     */
    private suspend fun fetch(track: Track): String? {
        val title = track.cleanTitle.matchKey()
        val artist = track.primaryArtist.matchKey()
        val songs = client.search("${track.primaryArtist} ${track.cleanTitle}").filter {
            !it.instrumental && it.title.matchKey() == title && artist in it.artistNames.matchKey()
        }
        var failure: Exception? = null
        for (song in songs) {
            try {
                return client.lyrics(song)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = failure ?: e
            }
        }
        failure?.let { throw it }
        return null
    }

    private companion object {
        const val TAG = "GeniusSections"
        const val NOT_FOUND_RETRY_MS = 24 * 60 * 60 * 1000L
        val RETRY_DELAYS_MS = listOf(5_000L, 20_000L, 60_000L)
    }
}

private fun CachedGeniusLyrics.toSections(track: Track): List<SungSection>? =
    text?.let { GeniusSections.parse(it, track.artists) }
