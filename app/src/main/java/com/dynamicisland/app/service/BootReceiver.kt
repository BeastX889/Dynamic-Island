package com.dynamicisland.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dynamicisland.app.util.PermissionUtils

/**
 * Re-launches the overlay after a reboot, but only if the user previously enabled it and the
 * overlay permission is still granted. (A persistence flag for "was running" is added in Phase 5;
 * for now we gate purely on the permission being present.)
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (PermissionUtils.canDrawOverlays(context)) {
            IslandOverlayService.start(context)
        }
    }
}
