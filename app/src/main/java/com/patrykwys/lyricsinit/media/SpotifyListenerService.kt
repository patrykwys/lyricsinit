package com.patrykwys.lyricsinit.media

import android.content.ComponentName
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import com.patrykwys.lyricsinit.graph

/**
 * Notification access is what lets a third-party app read other apps' media sessions.
 * We never look at notifications themselves, only at Spotify's session.
 */
class SpotifyListenerService : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private var sessionManager: MediaSessionManager? = null
    private var controller: MediaController? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
        attach(sessions.orEmpty())
    }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publishMetadata(metadata)
        override fun onPlaybackStateChanged(state: PlaybackState?) = graph.playback.updateState(state.toSnapshot())
        override fun onSessionDestroyed() = detach()
    }

    override fun onListenerConnected() {
        val manager = getSystemService(MediaSessionManager::class.java)
        val component = ComponentName(this, SpotifyListenerService::class.java)
        manager.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
        sessionManager = manager
        attach(manager.getActiveSessions(component))
    }

    override fun onListenerDisconnected() {
        sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        sessionManager = null
        detach()
    }

    private fun attach(sessions: List<MediaController>) {
        // Keep the current controller when Spotify drops out of the active list; it may just be paused.
        val spotify = sessions.firstOrNull { it.packageName == SPOTIFY_PACKAGE } ?: return
        if (spotify.sessionToken == controller?.sessionToken) return
        detach()
        spotify.registerCallback(callback, handler)
        controller = spotify
        publish()
    }

    private fun detach() {
        controller?.unregisterCallback(callback)
        controller = null
        publish()
    }

    private fun publish() {
        val current = controller
        graph.playback.updateController(current)
        publishMetadata(current?.metadata)
        graph.playback.updateState(current?.playbackState.toSnapshot())
    }

    // Only on metadata changes: every controller.metadata read unparcels a fresh album-art bitmap.
    private fun publishMetadata(metadata: MediaMetadata?) =
        graph.playback.updateMetadata(metadata?.toTrack(), metadata?.albumArt())

    private companion object {
        const val SPOTIFY_PACKAGE = "com.spotify.music"
    }
}

private fun MediaMetadata.toTrack(): Track? {
    val title = getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: return null
    return Track(
        title = title,
        artist = getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty(),
        album = getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
        durationMs = getLong(MediaMetadata.METADATA_KEY_DURATION),
    )
}

private fun MediaMetadata.albumArt(): Bitmap? =
    getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        ?: getBitmap(MediaMetadata.METADATA_KEY_ART)
        ?: getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

private fun PlaybackState?.toSnapshot(): PlaybackSnapshot {
    if (this == null) return PlaybackSnapshot.STOPPED
    return PlaybackSnapshot(
        isPlaying = state == PlaybackState.STATE_PLAYING,
        positionMs = position,
        updatedAt = lastPositionUpdateTime.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
        speed = playbackSpeed.takeIf { it > 0f } ?: 1f,
    )
}
