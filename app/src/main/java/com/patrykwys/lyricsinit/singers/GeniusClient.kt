package com.patrykwys.lyricsinit.singers

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
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
    val id: Long,
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

    /**
     * The song's lyrics as text, headers included, or null when Genius has none. Comes from the
     * embed script other sites use (about 20 KB); the song page (up to 600 KB) is the fallback.
     * Genius intermittently answers the app with a 404 page for songs that exist, so a failure
     * here is worth retrying later rather than meaning "no lyrics".
     */
    suspend fun lyrics(song: GeniusSong): String? {
        val embed = try {
            extractEmbedLyrics(fetchText("$EMBED_URL/${song.id}/embed.js"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        return embed ?: extractPageLyrics(fetchText(song.url, timeoutMs = PAGE_TIMEOUT_MS))
    }

    /** Any status but 200 is an error, a 404 included, since Genius's 404s aren't always true. */
    private suspend fun fetchText(url: String, timeoutMs: Long? = null): String {
        val response = http.get(url) { if (timeoutMs != null) timeout { requestTimeoutMillis = timeoutMs } }
        if (response.status != HttpStatusCode.OK) throw IOException("Genius returned ${response.status} for $url")
        return response.bodyAsText()
    }

    private companion object {
        const val SEARCH_URL = "https://genius.com/api/search/song"
        const val EMBED_URL = "https://genius.com/songs"
        const val SEARCH_LIMIT = 5

        // Pages run to 600 KB, too much for the app-wide 10 s limit on a weak signal.
        const val PAGE_TIMEOUT_MS = 30_000L
    }
}

/**
 * The embed script writes its HTML with `document.write(JSON.parse('…'))`: a JS string holding a
 * JSON string holding the HTML, with the lyrics in `.rg_embed_body`.
 */
internal fun extractEmbedLyrics(script: String): String? {
    val start = script.indexOf(EMBED_HTML_START).takeIf { it >= 0 } ?: return null
    val json = script.jsStringFrom(start + EMBED_HTML_START.length) ?: return null
    val html = Json.decodeFromString<String>(json)
    return Jsoup.parse(html).selectFirst(".rg_embed_body")?.lyricsText()
}

/**
 * Lyrics sit in `data-lyrics-container` divs, mixed with page furniture (the translations menu,
 * the "Lyrics" heading) that Genius marks as excluded from selection.
 */
internal fun extractPageLyrics(html: String): String? {
    val containers = Jsoup.parse(html).select("[data-lyrics-container=true]")
    containers.select("[data-exclude-from-selection=true]").remove()
    return containers.joinToString("\n") { it.lyricsText().orEmpty() }.trim().ifEmpty { null }
}

private const val EMBED_HTML_START = "JSON.parse('"

/** Line breaks and paragraphs become newlines, so a header that opens a paragraph stays on its own line. */
private fun Element.lyricsText(): String? {
    select("br").forEach { it.replaceWith(TextNode("\n")) }
    select("p").forEach { it.after(TextNode("\n\n")) }
    return wholeText().trim().ifEmpty { null }
}

/** Reads a single-quoted JS string literal starting at [start], up to its closing quote. */
private fun String.jsStringFrom(start: Int): String? {
    val out = StringBuilder()
    var i = start
    while (i < length) {
        val c = this[i]
        if (c == '\'') return out.toString()
        if (c == '\\' && i + 1 < length) {
            i++
            when (val escaped = this[i]) {
                'n' -> out.append('\n')
                't' -> out.append('\t')
                'r' -> out.append('\r')
                'u' -> {
                    out.append(substring(i + 1, i + 5).toInt(16).toChar())
                    i += 4
                }
                else -> out.append(escaped)
            }
        } else {
            out.append(c)
        }
        i++
    }
    return null
}
