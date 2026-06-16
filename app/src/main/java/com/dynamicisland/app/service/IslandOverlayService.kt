package com.dynamicisland.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.LifecycleService
import com.dynamicisland.app.MainActivity
import com.dynamicisland.app.R
import com.dynamicisland.app.island.IslandController
import com.dynamicisland.app.overlay.ComposeOverlayHost
import com.dynamicisland.app.overlay.IslandView
import com.dynamicisland.app.util.PermissionUtils

/**
 * Foreground service that owns the floating island window.
 *
 * It adds a single [ComposeOverlayHost] view to the [WindowManager] and keeps it there for the
 * lifetime of the service. The actual content is driven reactively from [IslandController]; the
 * service never tears the window down on state changes — it just lets Compose animate.
 */
class IslandOverlayService : LifecycleService() {

    private lateinit var windowManager: WindowManager
    private var host: ComposeOverlayHost? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startInForeground()

        // Bail out (without the overlay) if the permission was revoked while we were away.
        if (PermissionUtils.canDrawOverlays(this)) {
            addOverlayIfNeeded()
            IslandController.setEnabled(true)
        }
        return START_STICKY
    }

    private fun addOverlayIfNeeded() {
        if (host != null) return

        val overlayHost = ComposeOverlayHost(this) {
            val state by IslandController.state.collectAsState()
            IslandView(
                state = state,
                onTap = { IslandController.toggleExpanded() },
                onLongPress = { IslandController.setExpanded(true) },
            )
        }
        host = overlayHost

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            // Sits flush with the top so the pill wraps the camera cutout.
            // Fine-grained calibration arrives with the settings screen (Phase 5).
            y = 0
        }

        windowManager.addView(overlayHost.composeView, params)
        overlayHost.onAttached()
    }

    private fun removeOverlay() {
        host?.let {
            it.onDetached()
            runCatching { windowManager.removeView(it.composeView) }
        }
        host = null
    }

    override fun onDestroy() {
        IslandController.setEnabled(false)
        removeOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun startInForeground() {
        val channelId = ensureChannel()
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle(getString(R.string.fgs_notification_title))
            .setContentText(getString(R.string.fgs_notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.fgs_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = getString(R.string.fgs_channel_desc) }
            manager.createNotificationChannel(channel)
        }
        return CHANNEL_ID
    }

    companion object {
        private const val CHANNEL_ID = "island_overlay"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.dynamicisland.app.STOP"

        fun start(context: Context) {
            val intent = Intent(context, IslandOverlayService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, IslandOverlayService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
