package com.patrykwys.lyricsinit

import android.content.Context
import androidx.room.Room
import com.patrykwys.lyricsinit.lyrics.LrclibClient
import com.patrykwys.lyricsinit.lyrics.LyricsDatabase
import com.patrykwys.lyricsinit.lyrics.LyricsRepository
import com.patrykwys.lyricsinit.media.SpotifyPlayback
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** App-wide singletons, shared by the phone UI, the notification listener and the car screen. */
class AppGraph(context: Context) {
    private val http = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) { requestTimeoutMillis = 10_000 }
        install(UserAgent) { agent = "LyricsInit/1.0 (personal Android Auto app)" }
    }

    private val database = Room.databaseBuilder(context, LyricsDatabase::class.java, "lyrics.db").build()

    val playback = SpotifyPlayback()
    val repository = LyricsRepository(LrclibClient(http), database.lyricsDao())
}
