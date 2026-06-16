package com.dynamicisland.app.feature.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.dynamicisland.app.feature.FeatureFlags
import com.dynamicisland.app.island.CallPhase
import com.dynamicisland.app.island.IslandController
import com.dynamicisland.app.island.IslandState

/**
 * Reflects the phone's call state on the island: ringing -> incoming, off-hook -> active,
 * idle -> cleared.
 *
 * Uses [TelephonyCallback] on API 31+ and falls back to [PhoneStateListener] below that. Requires
 * READ_PHONE_STATE; if it isn't granted, [start] is a no-op. We intentionally do not read the
 * caller number (that needs the restricted READ_CALL_LOG / READ_PHONE_NUMBERS permissions, which
 * complicate Play review), so the island shows a generic label.
 */
class CallStateMonitor(private val context: Context) {

    private val telephony =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private var modernCallback: TelephonyCallback? = null
    private var legacyListener: PhoneStateListener? = null
    private var started = false

    fun start() {
        if (started) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        started = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) = handleState(state)
            }
            modernCallback = callback
            telephony.registerTelephonyCallback(context.mainExecutor, callback)
        } else {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) =
                    handleState(state)
            }
            legacyListener = listener
            @Suppress("DEPRECATION")
            telephony.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            modernCallback?.let { telephony.unregisterTelephonyCallback(it) }
            modernCallback = null
        } else {
            @Suppress("DEPRECATION")
            legacyListener?.let { telephony.listen(it, PhoneStateListener.LISTEN_NONE) }
            legacyListener = null
        }
        started = false
        IslandController.submit(IslandController.Source.CALL, null)
    }

    private fun handleState(state: Int) {
        if (!FeatureFlags.call) {
            IslandController.submit(IslandController.Source.CALL, null)
            return
        }
        val islandState = when (state) {
            TelephonyManager.CALL_STATE_RINGING ->
                IslandState.Call(displayName = "Incoming call", phase = CallPhase.INCOMING)
            TelephonyManager.CALL_STATE_OFFHOOK ->
                IslandState.Call(displayName = "On call", phase = CallPhase.ACTIVE)
            else -> null
        }
        IslandController.submit(IslandController.Source.CALL, islandState)
    }
}
