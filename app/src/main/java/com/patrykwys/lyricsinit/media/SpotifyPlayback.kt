package com.patrykwys.lyricsinit.media

import android.media.session.MediaController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What Spotify is playing right now, fed by [SpotifyListenerService]. */
class SpotifyPlayback {
    private val _track = MutableStateFlow<Track?>(null)
    val track: StateFlow<Track?> = _track.asStateFlow()

    private val _state = MutableStateFlow(PlaybackSnapshot.STOPPED)
    val state: StateFlow<PlaybackSnapshot> = _state.asStateFlow()

    @Volatile
    private var controller: MediaController? = null

    fun update(controller: MediaController?, track: Track?, state: PlaybackSnapshot) {
        this.controller = controller
        _track.value = track
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
