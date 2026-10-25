package com.patrykwys.lyricsinit

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** How lyrics are laid out on the car screen. Two options in case the host throttles one of them. */
enum class CarLayout(val label: String) {
    MESSAGE("Big line"),
    PANE("Line + next"),
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class AppSettings(context: Context) {
    private val store = context.dataStore

    /** Positive values show lyrics earlier, negative values later. */
    val offsetMs: Flow<Long> = store.data
        .map { it[OFFSET_MS] ?: 0L }
        .distinctUntilChanged()

    val carLayout: Flow<CarLayout> = store.data
        .map { prefs -> CarLayout.entries.firstOrNull { it.name == prefs[CAR_LAYOUT] } ?: CarLayout.MESSAGE }
        .distinctUntilChanged()

    suspend fun adjustOffset(deltaMs: Long) {
        store.edit { it[OFFSET_MS] = (it[OFFSET_MS] ?: 0L) + deltaMs }
    }

    suspend fun resetOffset() {
        store.edit { it.remove(OFFSET_MS) }
    }

    suspend fun setCarLayout(layout: CarLayout) {
        store.edit { it[CAR_LAYOUT] = layout.name }
    }

    private companion object {
        val OFFSET_MS = longPreferencesKey("offset_ms")
        val CAR_LAYOUT = stringPreferencesKey("car_layout")
    }
}
