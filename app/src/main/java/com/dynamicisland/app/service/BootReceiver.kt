package com.dynamicisland.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dynamicisland.app.data.SettingsRepository
import com.dynamicisland.app.util.PermissionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Re-launches the overlay after a reboot, but only if the user had it running (persisted flag) and
 * the overlay permission is still granted.
 *
 * The persisted flag lives in DataStore (disk I/O), so we read it off the main thread via
 * [goAsync] + a coroutine — never `runBlocking` in `onReceive`, which would risk an ANR during the
 * already-contended BOOT_COMPLETED window.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!PermissionUtils.canDrawOverlays(context)) return

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val wasRunning = SettingsRepository(appContext).flow.first().wasRunning
                if (wasRunning) {
                    withContext(Dispatchers.Main) { IslandOverlayService.start(appContext) }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
