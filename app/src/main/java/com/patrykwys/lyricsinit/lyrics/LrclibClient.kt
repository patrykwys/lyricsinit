package com.patrykwys.lyricsinit.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import java.io.IOException

@Serializable
data class LrclibTrack(
    val id: Long,
    val trackName: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
) {
    val hasSynced: Boolean get() = !syncedLyrics.isNullOrBlank()
    val hasLyrics: Boolean get() = instrumental || hasSynced || !plainLyrics.isNullOrBlank()
}

/** Client for https://lrclib.net — free, keyless, community-run synced lyrics. */
class LrclibClient(private val http: HttpClient) {

    /** Exact lookup. Returns null when LRCLIB has no match. */
    suspend fun get(title: String, artist: String, album: String, durationSec: Long): LrclibTrack? {
        val response = http.get("$BASE_URL/get") {
            parameter("track_name", title)
            parameter("artist_name", artist)
            parameter("album_name", album)
            parameter("duration", durationSec)
        }
        return when (response.status) {
            HttpStatusCode.OK -> response.body<LrclibTrack>()
            HttpStatusCode.NotFound -> null
            else -> throw IOException("LRCLIB returned ${response.status}")
        }
    }

    suspend fun search(title: String, artist: String): List<LrclibTrack> {
        val response = http.get("$BASE_URL/search") {
            parameter("track_name", title)
            parameter("artist_name", artist)
        }
        if (response.status != HttpStatusCode.OK) throw IOException("LRCLIB returned ${response.status}")
        return response.body()
    }

    private companion object {
        const val BASE_URL = "https://lrclib.net/api"
    }
}
