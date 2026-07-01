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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.dynamicisland.app.MainActivity
import com.dynamicisland.app.R
import com.dynamicisland.app.data.IslandSettings
import com.dynamicisland.app.data.SettingsRepository
import com.dynamicisland.app.feature.FeatureFlags
import com.dynamicisland.app.feature.call.CallStateMonitor
import com.dynamicisland.app.feature.media.NowPlayingRepository
import com.dynamicisland.app.island.IslandController
import com.dynamicisland.app.overlay.ComposeOverlayHost
import com.dynamicisland.app.overlay.IslandView
import com.dynamicisland.app.util.CutoutInfo
import com.dynamicisland.app.util.CutoutUtils
import com.dynamicisland.app.util.PermissionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
    private var params: WindowManager.LayoutParams? = null
    private lateinit var nowPlaying: NowPlayingRepository
    private lateinit var callMonitor: CallStateMonitor
    private lateinit var settings: SettingsRepository
    private val scaleState = mutableFloatStateOf(1f)

    private var currentSettings = IslandSettings()
    // Detected camera-cutout position (null until measured / no cutout on device).
    private var cutout: CutoutInfo? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        nowPlaying = NowPlayingRepository(this)
        callMonitor = CallStateMonitor(this)
        settings = SettingsRepository(this)
        observeSettings()
    }

    /** Mirror persisted settings into feature flags, the pill scale, and the window position. */
    private fun observeSettings() {
        lifecycleScope.launch {
            settings.flow.collect { s ->
                currentSettings = s
                FeatureFlags.media = s.mediaEnabled
                FeatureFlags.call = s.callEnabled
                FeatureFlags.timer = s.timerEnabled
                FeatureFlags.liveActivity = s.liveActivityEnabled
                scaleState.floatValue = s.scalePercent / 100f
                applyLayout()
            }
        }
    }

    /**
     * Positions the window. When auto-align is on and a cutout was detected, the pill is centred
     * over the real camera hole (works for corner + off-centre punch-holes, not just centred
     * notches); the manual sliders then fine-tune on top. Otherwise the manual offsets are absolute.
     */
    private fun applyLayout() {
        val p = params ?: return
        val density = resources.displayMetrics.density
        val manualX = (currentSettings.horizontalOffsetDp * density).toInt()
        val manualY = (currentSettings.verticalOffsetDp * density).toInt()

        val c = cutout
        if (currentSettings.autoAlign && c != null) {
            p.x = c.centerXOffsetPx + manualX
            p.y = c.topPx + manualY
        } else {
            p.x = manualX
            p.y = manualY
        }
        host?.let { runCatching { windowManager.updateViewLayout(it.composeView, p) } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent?.action == ACTION_STOP) {
            persistRunning(false)
            stopSelf()
            return START_NOT_STICKY
        }

        // startForegroundService requires a startForeground() call within a few seconds, so we
        // foreground first and then decide.
        startInForeground()

        // If the overlay permission was revoked while we were away, don't leave a phantom "active"
        // notification with nothing on screen — clear the running flag and shut down cleanly.
        if (!PermissionUtils.canDrawOverlays(this)) {
            persistRunning(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        addOverlayIfNeeded()
        IslandController.setEnabled(true)
        isRunning = true
        persistRunning(true)
        // (Re)attach the real feature monitors with whatever permissions are now granted.
        // Both calls are idempotent, so refreshing after a permission grant is safe.
        if (PermissionUtils.isNotificationListenerEnabled(this)) {
            nowPlaying.start()
        }
        callMonitor.start()
        return START_STICKY
    }

    /** Persist the boot-restore flag off the main thread (DataStore does disk I/O). */
    private fun persistRunning(value: Boolean) {
        CoroutineScope(Dispatchers.IO).launch { settings.setWasRunning(value) }
    }

    private fun addOverlayIfNeeded() {
        if (host != null) return

        val overlayHost = ComposeOverlayHost(this) {
            val state by IslandController.state.collectAsState()
            IslandView(
                state = state,
                onTap = { IslandController.toggleExpanded() },
                onLongPress = { IslandController.setExpanded(true) },
                onMusicAction = { nowPlaying.onAction(it) },
                scale = scaleState.floatValue,
            )
        }
        host = overlayHost

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            // Let the window extend into the cutout region so it can sit over the camera hole.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    } else {
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
            }
            // Refined by applyLayout() once the cutout is measured + settings applied.
            y = 0
        }
        params = layoutParams

        // Re-measure the cutout whenever insets change (attach, rotation) and re-position.
        overlayHost.composeView.setOnApplyWindowInsetsListener { v, insets ->
            cutout = CutoutUtils.detect(windowManager, v)
            applyLayout()
            insets
        }

        windowManager.addView(overlayHost.composeView, layoutParams)
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
        isRunning = false
        nowPlaying.stop()
        callMonitor.stop()
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

        /** True while the overlay service is live; used to refresh monitors after a permission grant. */
        @Volatile
        var isRunning: Boolean = false
            private set

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
