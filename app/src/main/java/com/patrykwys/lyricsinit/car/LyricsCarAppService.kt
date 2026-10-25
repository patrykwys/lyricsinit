package com.patrykwys.lyricsinit.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

class LyricsCarAppService : CarAppService() {
    // Personal sideloaded app, so any Android Auto host is fine.
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = LyricsSession()
}

class LyricsSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen = LyricsScreen(carContext)
}
