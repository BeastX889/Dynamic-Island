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
 *     a live activity on the island — this is the closest real Android analogue to iOS Live
 *     Activities.
 */
class IslandNotificationListener : NotificationListenerService() {

    /** The notification key currently driving the live activity, so we can clear it on removal. */
    private var liveKey: String? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        // Re-scan existing notifications so a live activity that started before us is picked up.
        runCatching { activeNotifications?.forEach { onNotificationPosted(it) } }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (!FeatureFlags.liveActivity) return
        if (sbn.packageName == packageName) return

        val activity = sbn.toLiveActivity() ?: return
        liveKey = sbn.key
        IslandController.submit(IslandController.Source.LIVE_ACTIVITY, activity)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn != null && sbn.key == liveKey) {
            liveKey = null
            IslandController.submit(IslandController.Source.LIVE_ACTIVITY, null)
        }
    }

    /**
     * Recognises a notification as a live activity when it is ongoing and carries determinate
     * progress, or is explicitly categorised as progress/call/navigation. Returns null otherwise.
     */
    private fun StatusBarNotification.toLiveActivity(): IslandState.LiveActivity? {
        val n = notification ?: return null
        val extras = n.extras ?: return null

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
