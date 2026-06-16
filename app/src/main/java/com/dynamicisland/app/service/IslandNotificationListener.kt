package com.dynamicisland.app.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Notification access serves two purposes for the island:
 *  1. Its [android.content.ComponentName] is the key that lets us call
 *     `MediaSessionManager.getActiveSessions(...)` without the restricted MEDIA_CONTENT_CONTROL
 *     permission (wired up in Phase 2).
 *  2. Posted notifications can drive live activities (Phase 4).
 *
 * For now it only tracks connection state; the feature wiring lands in later phases.
 */
class IslandNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // Phase 4: map relevant notifications (delivery/ride/etc.) to live activities.
    }

    companion object {
        @Volatile
        var isConnected: Boolean = false
            private set
    }
}
