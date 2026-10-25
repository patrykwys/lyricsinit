package com.patrykwys.lyricsinit

import android.content.Context
import androidx.room.Room
import com.patrykwys.lyricsinit.artwork.TrackArtwork
import com.patrykwys.lyricsinit.lyrics.LrclibClient
import com.patrykwys.lyricsinit.lyrics.LyricsDatabase
import com.patrykwys.lyricsinit.lyrics.LyricsRepository
import com.patrykwys.lyricsinit.media.SpotifyPlayback
import com.patrykwys.lyricsinit.sync.LyricsEngine
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json

/** App-wide singletons, shared by the phone UI, the notification listener and the car screen. */
class AppGraph(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val http = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) { requestTimeoutMillis = 10_000 }
        install(UserAgent) { agent = "LyricsInit/1.0 (personal Android Auto app)" }
    }

    private val database = Room.databaseBuilder(context, LyricsDatabase::class.java, "lyrics.db")
        .addMigrations(LyricsDatabase.MIGRATION_1_2)
        .build()

    val playback = SpotifyPlayback()
    val settings = AppSettings(context)
    val repository = LyricsRepository(LrclibClient(http), database.lyricsDao())
    val engine = LyricsEngine(playback, repository, settings, scope)
    val artwork = TrackArtwork(http, playback, scope)
}
