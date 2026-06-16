package com.dynamicisland.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** User-tunable island settings, persisted with DataStore. */
data class IslandSettings(
    val verticalOffsetDp: Int = 8,
    val horizontalOffsetDp: Int = 0,
    val scalePercent: Int = 100,
    val mediaEnabled: Boolean = true,
    val callEnabled: Boolean = true,
    val timerEnabled: Boolean = true,
    val liveActivityEnabled: Boolean = true,
    /** Whether the overlay was running, so we can restore it after a reboot. */
    val wasRunning: Boolean = false,
)

// Top-level delegate guarantees a single DataStore instance per process (shared by the activity,
// the overlay service, and the boot receiver).
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "island_settings")

class SettingsRepository(private val context: Context) {

    val flow: Flow<IslandSettings> = context.dataStore.data.map { p ->
        IslandSettings(
            verticalOffsetDp = p[VERTICAL_OFFSET] ?: 8,
            horizontalOffsetDp = p[HORIZONTAL_OFFSET] ?: 0,
            scalePercent = p[SCALE_PERCENT] ?: 100,
            mediaEnabled = p[MEDIA] ?: true,
            callEnabled = p[CALL] ?: true,
            timerEnabled = p[TIMER] ?: true,
            liveActivityEnabled = p[LIVE] ?: true,
            wasRunning = p[WAS_RUNNING] ?: false,
        )
    }

    suspend fun setVerticalOffset(dp: Int) = edit { it[VERTICAL_OFFSET] = dp }
    suspend fun setHorizontalOffset(dp: Int) = edit { it[HORIZONTAL_OFFSET] = dp }
    suspend fun setScalePercent(percent: Int) = edit { it[SCALE_PERCENT] = percent }
    suspend fun setMediaEnabled(value: Boolean) = edit { it[MEDIA] = value }
    suspend fun setCallEnabled(value: Boolean) = edit { it[CALL] = value }
    suspend fun setTimerEnabled(value: Boolean) = edit { it[TIMER] = value }
    suspend fun setLiveActivityEnabled(value: Boolean) = edit { it[LIVE] = value }
    suspend fun setWasRunning(value: Boolean) = edit { it[WAS_RUNNING] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private companion object {
        val VERTICAL_OFFSET = intPreferencesKey("vertical_offset_dp")
        val HORIZONTAL_OFFSET = intPreferencesKey("horizontal_offset_dp")
        val SCALE_PERCENT = intPreferencesKey("scale_percent")
        val MEDIA = booleanPreferencesKey("media_enabled")
        val CALL = booleanPreferencesKey("call_enabled")
        val TIMER = booleanPreferencesKey("timer_enabled")
        val LIVE = booleanPreferencesKey("live_activity_enabled")
        val WAS_RUNNING = booleanPreferencesKey("was_running")
    }
}
