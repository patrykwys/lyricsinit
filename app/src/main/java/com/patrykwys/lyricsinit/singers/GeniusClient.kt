package com.patrykwys.lyricsinit.singers

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode
import java.io.IOException

@Serializable
private data class GeniusSearch(val response: Body) {
    @Serializable
    data class Body(val sections: List<Section> = emptyList())

    @Serializable
    data class Section(val hits: List<Hit> = emptyList())

    @Serializable
    data class Hit(val result: GeniusSong)
}

@Serializable
data class GeniusSong(
    val title: String,
    val url: String,
    @SerialName("artist_names") val artistNames: String = "",
    val instrumental: Boolean = false,
)

/**
 * Genius's own web endpoints (no key). Only the `[Section: Singer]` headers are used;
 * LRCLIB still provides the timing.
 */
class GeniusClient(private val http: HttpClient) {

    suspend fun search(query: String): List<GeniusSong> {
        val response = http.get(SEARCH_URL) {
            parameter("q", query)
            parameter("per_page", SEARCH_LIMIT)
        }
        if (response.status != HttpStatusCode.OK) throw IOException("Genius returned ${response.status}")
        return response.body<GeniusSearch>().response.sections.flatMap { it.hits }.map { it.result }
    }

    /** The song page's lyrics as text, headers included, or null when the page has none. */
    suspend fun lyrics(song: GeniusSong): String? {
        val response = http.get(song.url)
        if (response.status != HttpStatusCode.OK) throw IOException("Genius returned ${response.status}")
        val html = response.bodyAsText()
        return withContext(Dispatchers.Default) { extractLyrics(html) }
    }

    private companion object {
        const val SEARCH_URL = "https://genius.com/api/search/song"
        const val SEARCH_LIMIT = 5
    }
}

/**
 * Lyrics sit in `data-lyrics-container` divs, mixed with page furniture (the translations menu,
 * the "Lyrics" heading) that Genius marks as excluded from selection.
 */
internal fun extractLyrics(html: String): String? {
    val containers = Jsoup.parse(html).select("[data-lyrics-container=true]")
    containers.select("[data-exclude-from-selection=true]").remove()
    containers.select("br").forEach { it.replaceWith(TextNode("\n")) }
    return containers.joinToString("\n") { it.wholeText() }.trim().ifEmpty { null }
}
