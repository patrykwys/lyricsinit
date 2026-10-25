package com.patrykwys.lyricsinit.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.patrykwys.lyricsinit.AppSettings
import com.patrykwys.lyricsinit.CarLayout
import com.patrykwys.lyricsinit.LyricsApp
import com.patrykwys.lyricsinit.artwork.TrackArtwork
import com.patrykwys.lyricsinit.media.PlaybackSnapshot
import com.patrykwys.lyricsinit.media.SpotifyPlayback
import com.patrykwys.lyricsinit.sync.LyricsEngine
import com.patrykwys.lyricsinit.sync.LyricsView
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val view: LyricsView = LyricsView.EMPTY,
    val playback: PlaybackSnapshot = PlaybackSnapshot.STOPPED,
    val artistThumbnail: Bitmap? = null,
    val albumThumbnail: Bitmap? = null,
    val offsetMs: Long = 0L,
    val carLayout: CarLayout = CarLayout.MESSAGE,
)

class MainViewModel(
    engine: LyricsEngine,
    private val playback: SpotifyPlayback,
    artwork: TrackArtwork,
    private val settings: AppSettings,
) : ViewModel() {

    // combine() takes at most five typed flows, so the two thumbnails travel as a pair.
    private val thumbnails = combine(artwork.artistThumbnail, artwork.albumThumbnail, ::Pair)

    val state: StateFlow<MainUiState> = combine(
        engine.view,
        playback.state,
        thumbnails,
        settings.offsetMs,
        settings.carLayout,
    ) { view, playbackState, (artist, album), offsetMs, carLayout ->
        MainUiState(view, playbackState, artist, album, offsetMs, carLayout)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MainUiState())

    fun seekTo(positionMs: Long) = playback.seekTo(positionMs)

    fun adjustOffset(deltaMs: Long) {
        viewModelScope.launch { settings.adjustOffset(deltaMs) }
    }

    fun resetOffset() {
        viewModelScope.launch { settings.resetOffset() }
    }

    fun setCarLayout(layout: CarLayout) {
        viewModelScope.launch { settings.setCarLayout(layout) }
    }

    companion object {
        // Keeps the upstream alive across a rotation instead of restarting it.
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory = viewModelFactory {
            initializer {
                val graph = (this[APPLICATION_KEY] as LyricsApp).graph
                MainViewModel(graph.engine, graph.playback, graph.artwork, graph.settings)
            }
        }
    }
}
