package com.patrykwys.lyricsinit

import android.content.Context
import com.patrykwys.lyricsinit.media.SpotifyPlayback

/** App-wide singletons, shared by the phone UI, the notification listener and the car screen. */
class AppGraph(context: Context) {
    val playback = SpotifyPlayback()
}
