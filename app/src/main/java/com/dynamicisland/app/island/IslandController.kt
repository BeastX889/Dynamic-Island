package com.dynamicisland.app.island

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Single source of truth for what the island is currently showing.
 *
 * Feature sources (media, calls, timer, live activity) push their state in via [submit]; the
 * overlay observes [state] and animates. A simple priority model decides which source wins when
 * several are active at once — calls beat everything, then timers, media, live activities.
 *
 * This is a process-wide singleton because both the foreground service (which renders the overlay)
 * and the activity (debug triggers / settings) talk to the same island.
 */
object IslandController {

    private val _state = MutableStateFlow<IslandState>(IslandState.Idle)
    val state: StateFlow<IslandState> = _state.asStateFlow()

    /** Per-source latest value, keyed by [Source]. Highest-priority non-null wins. */
    private val sources = HashMap<Source, IslandState>()

    enum class Source(val priority: Int) {
        CALL(40),
        TIMER(30),
        MEDIA(20),
        LIVE_ACTIVITY(10),
    }

    /** Publish (or clear, when [state] is null) the contribution from a given source. */
    @Synchronized
    fun submit(source: Source, state: IslandState?) {
        if (state == null || state is IslandState.Hidden) {
            sources.remove(source)
        } else {
            sources[source] = state
        }
        recompute()
    }

    /** Toggle the expanded flag on whatever is currently winning (tap / long-press from the UI). */
    @Synchronized
    fun toggleExpanded() {
        val current = _state.value
        val expanded = current.isExpanded()
        val updated = current.withExpanded(!expanded) ?: return
        // Reflect the toggle back into its owning source so recompute() keeps it.
        ownerOf(updated)?.let { sources[it] = updated }
        _state.value = updated
    }

    @Synchronized
    fun setExpanded(expanded: Boolean) {
        val updated = _state.value.withExpanded(expanded) ?: return
        ownerOf(updated)?.let { sources[it] = updated }
        _state.value = updated
    }

    /** Show or hide the idle pill (used when no feature source is active). */
    @Synchronized
    fun setEnabled(enabled: Boolean) {
        _state.update { if (!enabled) IslandState.Hidden else if (sources.isEmpty()) IslandState.Idle else it }
    }

    private fun recompute() {
        val winner = sources.entries.maxByOrNull { it.key.priority }?.value
        _state.value = winner ?: IslandState.Idle
    }

    private fun ownerOf(state: IslandState): Source? = when (state) {
        is IslandState.Call -> Source.CALL
        is IslandState.Timer -> Source.TIMER
        is IslandState.Music -> Source.MEDIA
        is IslandState.LiveActivity -> Source.LIVE_ACTIVITY
        else -> null
    }
}

private fun IslandState.isExpanded(): Boolean = when (this) {
    is IslandState.Music -> expanded
    is IslandState.Call -> expanded
    is IslandState.Timer -> expanded
    is IslandState.LiveActivity -> expanded
    else -> false
}

private fun IslandState.withExpanded(value: Boolean): IslandState? = when (this) {
    is IslandState.Music -> copy(expanded = value)
    is IslandState.Call -> copy(expanded = value)
    is IslandState.Timer -> copy(expanded = value)
    is IslandState.LiveActivity -> copy(expanded = value)
    else -> null
}
