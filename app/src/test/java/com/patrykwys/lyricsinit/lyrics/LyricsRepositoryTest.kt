package com.patrykwys.lyricsinit.lyrics

import com.patrykwys.lyricsinit.media.Track
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class LyricsRepositoryTest {
    private val track = Track("Mami", "730 Huncho, Malik Montana", "Mami", 180_000)
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val busy = """{"message":"The server is busy, please retry in a moment","name":"ServerOverloaded","statusCode":503}"""
    private val found = """[{"id":1,"duration":181.0,"syncedLyrics":"[00:01.00]Mami"}]"""

    private class FakeDao : LyricsDao {
        val rows = mutableMapOf<String, CachedLyrics>()
        override suspend fun get(id: String): CachedLyrics? = rows[id]
        override suspend fun upsert(entry: CachedLyrics) {
            rows[entry.id] = entry
        }
    }

    private fun repository(dao: LyricsDao = FakeDao(), respond: (path: String) -> Pair<HttpStatusCode, String>) =
        LyricsRepository(
            LrclibClient(
                HttpClient(MockEngine { request ->
                    val (status, body) = respond(request.url.encodedPath)
                    respond(body, status, json)
                }) {
                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                }
            ),
            dao,
        )

    @Test
    fun `a busy exact lookup still finds lyrics through search`() = runBlocking {
        val lyrics = repository { path ->
            if (path.endsWith("/get")) HttpStatusCode.ServiceUnavailable to busy else HttpStatusCode.OK to found
        }.get(track)
        assertEquals(Lyrics.Synced(listOf(LyricLine(1000, "Mami"))), lyrics)
    }

    @Test
    fun `a busy exact lookup with nothing in search is a cached not found`() = runBlocking {
        val dao = FakeDao()
        val lyrics = repository(dao) { path ->
            if (path.endsWith("/get")) HttpStatusCode.ServiceUnavailable to busy else HttpStatusCode.OK to "[]"
        }.get(track)
        assertEquals(Lyrics.NotFound, lyrics)
        assertTrue(dao.rows.isNotEmpty())
    }

    @Test
    fun `nothing answering is an error so the engine retries`() = runBlocking {
        val dao = FakeDao()
        try {
            repository(dao) { HttpStatusCode.ServiceUnavailable to busy }.get(track)
            fail("expected an error")
        } catch (e: IOException) {
            assertTrue(dao.rows.isEmpty())
        }
    }
}
