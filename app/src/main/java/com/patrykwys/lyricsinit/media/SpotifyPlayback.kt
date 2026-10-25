package com.patrykwys.lyricsinit.media

import android.graphics.Bitmap
import android.media.session.MediaController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What Spotify is playing right now, fed by [SpotifyListenerService]. */
class SpotifyPlayback {
    private val _track = MutableStateFlow<Track?>(null)
    val track: StateFlow<Track?> = _track.asStateFlow()

    // Kept out of Track: Bitmap equality is by reference, which would defeat StateFlow de-duplication.
    private val _albumArt = MutableStateFlow<Bitmap?>(null)
    val albumArt: StateFlow<Bitmap?> = _albumArt.asStateFlow()

    private val _state = MutableStateFlow(PlaybackSnapshot.STOPPED)
    val state: StateFlow<PlaybackSnapshot> = _state.asStateFlow()

    @Volatile
    private var controller: MediaController? = null

    fun updateController(controller: MediaController?) {
        this.controller = controller
    }

    fun updateMetadata(track: Track?, albumArt: Bitmap?) {
        _track.value = track
        _albumArt.value = albumArt
    }

    fun updateState(state: PlaybackSnapshot) {
        _state.value = state
    }

    fun playPause() {
        val controls = controller?.transportControls ?: return
        if (_state.value.isPlaying) controls.pause() else controls.play()
    }

    fun next() {
        controller?.transportControls?.skipToNext()
    }
}
