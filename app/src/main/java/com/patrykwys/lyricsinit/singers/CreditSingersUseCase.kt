package com.patrykwys.lyricsinit.singers

import com.patrykwys.lyricsinit.lyrics.LyricLine
import com.patrykwys.lyricsinit.lyrics.Lyrics
import com.patrykwys.lyricsinit.media.Track
import com.patrykwys.lyricsinit.media.artists
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Works out who sings each synced line. Inline `Name: line` credits in the LRC win, since they
 * cost nothing; otherwise Genius sections are matched onto the lines. The uncredited lyrics are
 * emitted first, so the screen never waits on Genius.
 */
class CreditSingersUseCase(private val repository: SungSectionsRepository) {

    operator fun invoke(track: Track, lyrics: Lyrics.Synced): Flow<Lyrics.Synced> = flow {
        val artists = track.artists
        val prefixed = SingerPrefixes.credit(lyrics.lines, artists)
        if (prefixed.any { it.singers.isNotEmpty() }) {
            emit(prefixed.toSynced(artists))
            return@flow
        }
        emit(lyrics)
        val sections = repository.sections(track) ?: return@flow
        emit(SingerMatcher.credit(lyrics.lines, sections).toSynced(artists))
    }

    private fun List<LyricLine>.toSynced(artists: List<String>) = Lyrics.Synced(this, singerOrder(this, artists))
}
