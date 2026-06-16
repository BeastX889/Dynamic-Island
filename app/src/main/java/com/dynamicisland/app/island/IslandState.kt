package com.dynamicisland.app.island

import android.graphics.Bitmap

/**
 * Everything the island can show. The pill morphs between these; [IslandController] holds the
 * current value as a [kotlinx.coroutines.flow.StateFlow] and the UI animates toward it.
 *
 * Each state carries the data the corresponding composable needs to render. Keeping the data in
 * the state (rather than reading repositories from the composable) makes transitions pure and
 * easy to drive from debug buttons or real event sources alike.
 */
sealed interface IslandState {

    /** The presentation size the pill should adopt for this state. */
    val presentation: Presentation

    /** Hidden entirely (no overlay drawn). */
    data object Hidden : IslandState {
        override val presentation = Presentation.HIDDEN
    }

    /** The idle pill that simply hugs the camera cutout. */
    data object Idle : IslandState {
        override val presentation = Presentation.COMPACT
    }

    /** Now Playing. [expanded] toggles between the compact glance and the full player. */
    data class Music(
        val title: String,
        val artist: String,
        val artwork: Bitmap? = null,
        val isPlaying: Boolean = true,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val expanded: Boolean = false,
    ) : IslandState {
        override val presentation = if (expanded) Presentation.EXPANDED else Presentation.COMPACT
    }

    /** Incoming or ongoing call. */
    data class Call(
        val displayName: String,
        val phase: CallPhase,
        val expanded: Boolean = false,
    ) : IslandState {
        override val presentation = if (expanded) Presentation.EXPANDED else Presentation.COMPACT
    }

    /** Countdown timer. [remainingMs] / [totalMs] drive the progress ring. */
    data class Timer(
        val label: String,
        val remainingMs: Long,
        val totalMs: Long,
        val expanded: Boolean = false,
    ) : IslandState {
        override val presentation = if (expanded) Presentation.EXPANDED else Presentation.COMPACT
    }

    /**
     * A generic live activity (delivery, ride, etc.). Renders split when [trailingLabel] is set,
     * showing a leading glyph on the left bubble and status on the right.
     */
    data class LiveActivity(
        val title: String,
        val status: String,
        val progress: Float,
        val leadingEmoji: String = "📦",
        val trailingLabel: String? = null,
        val expanded: Boolean = false,
    ) : IslandState {
        override val presentation = when {
            expanded -> Presentation.EXPANDED
            trailingLabel != null -> Presentation.SPLIT
            else -> Presentation.COMPACT
        }
    }
}

enum class CallPhase { INCOMING, ACTIVE, ENDED }

/** Transport actions the user can trigger from the expanded music view. */
enum class MusicAction { PLAY_PAUSE, NEXT, PREVIOUS }

/** The coarse geometry the pill animates between. Actual dp values live in the UI layer. */
enum class Presentation { HIDDEN, COMPACT, SPLIT, EXPANDED }
