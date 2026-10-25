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
import kotlinx.coroutines.withContext

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
            CachedGeniusLyrics(id, fetch(track), fetchedAt = now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Genius lookup failed for ${track.artist} - ${track.title}", e)
            return@withContext cached?.toSections(track)
        }
        dao.upsert(entry)
        entry.toSections(track)
    }

    /** Only a hit with the same title and artist counts, which skips Genius's translation pages. */
    private suspend fun fetch(track: Track): String? {
        val title = track.cleanTitle.matchKey()
        val artist = track.primaryArtist.matchKey()
        val song = client.search("${track.primaryArtist} ${track.cleanTitle}").firstOrNull {
            !it.instrumental && it.title.matchKey() == title && artist in it.artistNames.matchKey()
        } ?: return null
        return client.lyrics(song)
    }

    private companion object {
        const val TAG = "GeniusSections"
        const val NOT_FOUND_RETRY_MS = 24 * 60 * 60 * 1000L
    }
}

private fun CachedGeniusLyrics.toSections(track: Track): List<SungSection>? =
    text?.let { GeniusSections.parse(it, track.artists) }
