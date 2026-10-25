package com.patrykwys.lyricsinit.sync

import com.patrykwys.lyricsinit.AppSettings
import com.patrykwys.lyricsinit.lyrics.LyricLine
import com.patrykwys.lyricsinit.lyrics.Lyrics
import com.patrykwys.lyricsinit.lyrics.LyricsRepository
import com.patrykwys.lyricsinit.lyrics.indexAt
import com.patrykwys.lyricsinit.media.SpotifyPlayback
import com.patrykwys.lyricsinit.media.Track
import com.patrykwys.lyricsinit.singers.CreditSingersUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlin.time.Duration.Companion.milliseconds

sealed interface LyricsStatus {
    val track: Track?

    data object Idle : LyricsStatus {
        override val track: Track? get() = null
    }

    data class Loading(override val track: Track) : LyricsStatus
    data class Ready(override val track: Track, val lyrics: Lyrics) : LyricsStatus
    data class Failed(override val track: Track, val reason: String) : LyricsStatus
}

/** Everything a screen needs: the lyrics status and which synced line is current (-1 = before the first). */
data class LyricsView(val status: LyricsStatus, val index: Int) {
    val syncedLines: List<LyricLine>?
        get() = ((status as? LyricsStatus.Ready)?.lyrics as? Lyrics.Synced)?.lines
    val currentLine: LyricLine? get() = syncedLines?.getOrNull(index)
    val nextLine: LyricLine? get() = syncedLines?.getOrNull(index + 1)

    companion object {
        val EMPTY = LyricsView(LyricsStatus.Idle, -1)
    }
}

/** Text to show instead of lyrics, or null when there are synced lines to show. */
fun LyricsStatus.message(): String? = when (this) {
    LyricsStatus.Idle -> "Play something on Spotify"
    is LyricsStatus.Loading -> "Finding lyrics…"
    is LyricsStatus.Failed -> "Couldn't load lyrics, retrying…"
    is LyricsStatus.Ready -> when (lyrics) {
        is Lyrics.Synced -> null
        is Lyrics.Plain -> "Only unsynced lyrics for this song"
        Lyrics.Instrumental -> "Instrumental"
        Lyrics.NotFound -> "No lyrics found"
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LyricsEngine(
    playback: SpotifyPlayback,
    private val repository: LyricsRepository,
    private val creditSingers: CreditSingersUseCase,
    settings: AppSettings,
    scope: CoroutineScope,
) {
    val status: StateFlow<LyricsStatus> = playback.track
        .transformLatest { track ->
            if (track == null) {
                emit(LyricsStatus.Idle)
                return@transformLatest
            }
            emit(LyricsStatus.Loading(track))
            val lyrics = load(track) { reason -> emit(LyricsStatus.Failed(track, reason)) }
            if (lyrics is Lyrics.Synced) {
                emitAll(creditSingers(track, lyrics).map { LyricsStatus.Ready(track, it) })
            } else {
                emit(LyricsStatus.Ready(track, lyrics))
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, LyricsStatus.Idle)

    /** Keeps retrying while this track plays; signal drops out a lot in a car. */
    private suspend fun load(track: Track, onFailure: suspend (String) -> Unit): Lyrics {
        while (true) {
            try {
                return repository.get(track)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onFailure(e.message ?: e.javaClass.simpleName)
                delay(RETRY_DELAY_MS.milliseconds)
            }
        }
    }

    /** Sleeps until the next line is due instead of polling, and restarts on any seek/pause/offset change. */
    private val index: StateFlow<Int> = combine(status, playback.state, settings.offsetMs) { status, state, offset ->
        Triple(status, state, offset)
    }
        .transformLatest { (status, state, offsetMs) ->
            val lines = ((status as? LyricsStatus.Ready)?.lyrics as? Lyrics.Synced)?.lines
            if (lines == null) {
                emit(-1)
                return@transformLatest
            }
            while (true) {
                val position = state.positionNow() + offsetMs
                val current = lines.indexAt(position)
                emit(current)
                if (!state.isPlaying) break
                val next = lines.getOrNull(current + 1) ?: break
                delay(((next.timeMs - position) / state.speed).toLong().coerceAtLeast(MIN_TICK_MS).milliseconds)
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, -1)

    val view: StateFlow<LyricsView> = combine(status, index) { status, index -> LyricsView(status, index) }
        .stateIn(scope, SharingStarted.Eagerly, LyricsView.EMPTY)

    private companion object {
        const val RETRY_DELAY_MS = 15_000L
        const val MIN_TICK_MS = 20L
    }
}
