package com.patrykwys.lyricsinit.artwork

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import com.patrykwys.lyricsinit.media.SpotifyPlayback
import com.patrykwys.lyricsinit.media.primaryArtist
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class DeezerArtistSearch(val data: List<DeezerArtist> = emptyList())

@Serializable
private data class DeezerArtist(
    val name: String,
    @SerialName("picture_medium") val pictureMedium: String? = null,
)

/**
 * Thumbnail for the current song: the artist's photo from Deezer's public search API (no key),
 * falling back to the album art Spotify puts in its media session.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArtistArtwork(
    private val http: HttpClient,
    playback: SpotifyPlayback,
    scope: CoroutineScope,
) {
    // Wrapped so "this artist has no photo" is cached too; LruCache can't hold null.
    private class Entry(val photo: Bitmap?)

    private val photos = LruCache<String, Entry>(MAX_CACHED_ARTISTS)

    private val artistPhoto: Flow<Bitmap?> = playback.track
        .map { it?.primaryArtist }
        .distinctUntilChanged()
        .transformLatest { artist ->
            emit(null) // never show the previous artist's photo while the new one loads
            if (artist != null) emit(photoFor(artist))
        }

    val thumbnail: StateFlow<Bitmap?> = combine(artistPhoto, playback.albumArt) { photo, albumArt ->
        (photo ?: albumArt)?.scaledToFit(THUMBNAIL_PX)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, null)

    private suspend fun photoFor(artist: String): Bitmap? {
        val key = artist.lowercase()
        photos.get(key)?.let { return it.photo }
        return try {
            fetchPhoto(artist).also { photos.put(key, Entry(it)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Not cached, so the next song by this artist tries again.
            Log.w(TAG, "Artist photo lookup failed for $artist", e)
            null
        }
    }

    private suspend fun fetchPhoto(artist: String): Bitmap? {
        val results = http.get(SEARCH_URL) {
            parameter("q", artist)
            parameter("limit", SEARCH_LIMIT)
        }.body<DeezerArtistSearch>().data
        val match = results.firstOrNull { it.name.equals(artist, ignoreCase = true) } ?: results.firstOrNull()
        val url = match?.pictureMedium?.takeUnless { NO_PHOTO_MARKER in it } ?: return null
        val bytes = http.get(url).body<ByteArray>()
        return withContext(Dispatchers.Default) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.scaledToFit(THUMBNAIL_PX)
        }
    }

    private companion object {
        const val TAG = "ArtistArtwork"
        const val SEARCH_URL = "https://api.deezer.com/search/artist"
        const val SEARCH_LIMIT = 5

        // Deezer's generic silhouette for artists without a photo has an empty image hash.
        const val NO_PHOTO_MARKER = "/artist//"

        // Small enough to send to the car host on every screen refresh without hitting binder limits.
        const val THUMBNAIL_PX = 192
        const val MAX_CACHED_ARTISTS = 30
    }
}

private fun Bitmap.scaledToFit(maxPx: Int): Bitmap {
    val scale = maxPx.toFloat() / maxOf(width, height)
    if (scale >= 1f) return this
    return Bitmap.createScaledBitmap(
        this,
        (width * scale).toInt().coerceAtLeast(1),
        (height * scale).toInt().coerceAtLeast(1),
        true,
    )
}
