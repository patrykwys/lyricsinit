package com.patrykwys.lyricsinit.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import com.patrykwys.lyricsinit.graph

class MainActivity : ComponentActivity() {

    private var hasAccess by mutableStateOf(false)
    private var batteryUnrestricted by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LyricsTheme {
                MainScreen(
                    graph = graph,
                    hasAccess = hasAccess,
                    batteryUnrestricted = batteryUnrestricted,
                    onGrantAccess = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                    onFixBattery = {
                        startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                        )
                    },
                )
            }
        }
    }

    // Re-checked on every resume because both are granted in system settings screens.
    override fun onResume() {
        super.onResume()
        hasAccess = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        batteryUnrestricted = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
    }
}
