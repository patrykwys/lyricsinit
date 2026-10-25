package com.patrykwys.lyricsinit

import android.app.Application
import android.content.Context

class LyricsApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}

val Context.graph: AppGraph
    get() = (applicationContext as LyricsApp).graph
