package com.dynamicisland.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dynamicisland.app.data.SettingsRepository
import com.dynamicisland.app.util.PermissionUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Re-launches the overlay after a reboot, but only if the user had it running (persisted flag) and
 * the overlay permission is still granted.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!PermissionUtils.canDrawOverlays(context)) return

        val wasRunning = runBlocking { SettingsRepository(context).flow.first().wasRunning }
        if (wasRunning) {
            IslandOverlayService.start(context)
        }
    }
}
