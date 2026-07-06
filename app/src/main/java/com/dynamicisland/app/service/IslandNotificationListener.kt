package com.dynamicisland.app.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.dynamicisland.app.feature.FeatureFlags
import com.dynamicisland.app.island.IslandController
import com.dynamicisland.app.island.IslandState

/**
 * Notification access serves two purposes for the island:
 *  1. Its [android.content.ComponentName] is the key that lets
 *     [com.dynamicisland.app.feature.media.NowPlayingRepository] read active media sessions without
 *     the restricted MEDIA_CONTENT_CONTROL permission.
 *  2. Ongoing *progress* notifications (food delivery, rides, downloads, navigation) are mapped to
 *     a live activity on the island — the closest real Android analogue to iOS Live Activities.
 *
 * Multiple concurrent candidates are held in a keyed map and ranked by category priority
 * (call > navigation > transport > progress > other), then recency — not last-writer-wins — so a
 * background download can't hijack the island from turn-by-turn navigation, and removing one
 * candidate falls back to the next instead of blanking the pill.
 */
class IslandNotificationListener : NotificationListenerService() {

    private data class Candidate(
        val key: String,
        val priority: Int,
        val postTime: Long,
        val state: IslandState.LiveActivity,
    )

    /** All currently-eligible live-activity notifications, keyed by notification key. */
    private val candidates = LinkedHashMap<String, Candidate>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        // Re-scan existing notifications so a live activity that started before us is picked up.
        runCatching { activeNotifications?.forEach { onNotificationPosted(it) } }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        synchronized(candidates) { candidates.clear() }
        IslandController.submit(IslandController.Source.LIVE_ACTIVITY, null)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName == packageName) return

        val activity = if (FeatureFlags.liveActivity) sbn.toLiveActivity() else null
        synchronized(candidates) {
            if (activity != null) {
                candidates[sbn.key] = Candidate(
                    key = sbn.key,
                    priority = priorityFor(sbn.notification?.category),
                    postTime = sbn.postTime,
                    state = activity,
                )
            } else {
                candidates.remove(sbn.key)
            }
        }
        publishBest()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        val removed = synchronized(candidates) { candidates.remove(sbn.key) != null }
        if (removed) publishBest()
    }

    /** Highest priority wins; ties go to the most recently posted. */
    private fun publishBest() {
        val best = synchronized(candidates) {
            candidates.values.maxWithOrNull(compareBy({ it.priority }, { it.postTime }))
        }
        IslandController.submit(IslandController.Source.LIVE_ACTIVITY, best?.state)
    }

    private fun priorityFor(category: String?): Int = when (category) {
        Notification.CATEGORY_CALL -> 40
        Notification.CATEGORY_NAVIGATION -> 30
        Notification.CATEGORY_TRANSPORT -> 20
        Notification.CATEGORY_PROGRESS -> 10
        else -> 0
    }

    /**
     * Recognises a notification as a live activity when it is ongoing and carries determinate
     * progress, or is explicitly categorised as progress/call/navigation. Media notifications are
     * excluded — they are already rendered richer through the MediaSession pipeline, and showing
     * them here would double-report the same song.
     */
    private fun StatusBarNotification.toLiveActivity(): IslandState.LiveActivity? {
        val n = notification ?: return null
        val extras = n.extras ?: return null

        // Media-style notifications ride the Now Playing pipeline instead.
        if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return null

        val max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val current = extras.getInt(Notification.EXTRA_PROGRESS, 0)
        val ongoing = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0
        val hasProgress = max > 0
        val isProgressCategory = n.category in PROGRESS_CATEGORIES

        if (!(hasProgress || (ongoing && isProgressCategory))) return null

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null

        val progress = if (hasProgress) (current.toFloat() / max).coerceIn(0f, 1f) else 0f
        return IslandState.LiveActivity(
            title = title.ifBlank { text },
            status = text,
            progress = progress,
            leadingEmoji = emojiFor(n.category),
            trailingLabel = text.ifBlank { title },
        )
    }

    private fun emojiFor(category: String?): String = when (category) {
        Notification.CATEGORY_CALL -> "📞"
        Notification.CATEGORY_NAVIGATION -> "🧭"
        Notification.CATEGORY_TRANSPORT -> "🚗"
        Notification.CATEGORY_PROGRESS -> "⏳"
        else -> "📦"
    }

    companion object {
        @Volatile
        var isConnected: Boolean = false
            private set

        private val PROGRESS_CATEGORIES = setOf(
            Notification.CATEGORY_PROGRESS,
            Notification.CATEGORY_NAVIGATION,
            Notification.CATEGORY_TRANSPORT,
            Notification.CATEGORY_CALL,
        )
    }
}
