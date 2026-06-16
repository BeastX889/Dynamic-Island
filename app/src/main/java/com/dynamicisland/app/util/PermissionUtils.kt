package com.dynamicisland.app.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.dynamicisland.app.service.IslandNotificationListener

/** Helpers for the special, user-granted permissions the island depends on. */
object PermissionUtils {

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Intent that opens the system "Draw over other apps" screen for this app. */
    fun overlaySettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )

    /** True if the user has enabled our [IslandNotificationListener] in system settings. */
    fun isNotificationListenerEnabled(context: Context): Boolean {
        val component = ComponentName(context, IslandNotificationListener::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        return enabled.split(":").any {
            ComponentName.unflattenFromString(it) == component
        }
    }

    fun notificationListenerSettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    /** Whether the foreground-service notification will actually be shown to the user. */
    fun canPostNotifications(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()
}
