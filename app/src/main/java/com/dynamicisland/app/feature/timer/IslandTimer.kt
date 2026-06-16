package com.dynamicisland.app.feature.timer

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.dynamicisland.app.feature.FeatureFlags
import com.dynamicisland.app.island.IslandController
import com.dynamicisland.app.island.IslandState

/**
 * A simple in-app countdown surfaced on the island. Ticks once per second, updates the
 * [IslandState.Timer] (preserving the user's expanded/collapsed choice), and clears itself a moment
 * after reaching zero.
 *
 * Process-wide singleton: the foreground service keeps the process alive, so the countdown keeps
 * running even after the launcher activity is closed.
 */
object IslandTimer {

    private val handler = Handler(Looper.getMainLooper())
    private var endAt: Long = 0L
    private var totalMs: Long = 0L
    private var label: String = ""

    private val tick = object : Runnable {
        override fun run() {
            val remaining = endAt - SystemClock.elapsedRealtime()
            if (remaining <= 0L || !FeatureFlags.timer) {
                IslandController.submit(IslandController.Source.TIMER, null)
                return
            }
            val expanded = (IslandController.state.value as? IslandState.Timer)?.expanded ?: false
            IslandController.submit(
                IslandController.Source.TIMER,
                IslandState.Timer(label = label, remainingMs = remaining, totalMs = totalMs, expanded = expanded),
            )
            handler.postDelayed(this, 1000L)
        }
    }

    fun start(label: String, durationMs: Long) {
        this.label = label
        this.totalMs = durationMs
        this.endAt = SystemClock.elapsedRealtime() + durationMs
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    fun cancel() {
        handler.removeCallbacks(tick)
        IslandController.submit(IslandController.Source.TIMER, null)
    }
}
