package com.dynamicisland.app.util

import android.os.Build
import android.util.DisplayMetrics
import android.view.View
import android.view.WindowManager

/**
 * Where the camera cutout actually is, in screen pixels.
 *
 * @param centerXOffsetPx horizontal shift of the cutout's centre from the screen centre (px);
 *        positive = right. Feeds directly into a `Gravity.CENTER_HORIZONTAL` window's `x`.
 * @param topPx the cutout's top edge from the top of the screen (px). Feeds a `Gravity.TOP` `y`.
 */
data class CutoutInfo(val centerXOffsetPx: Int, val topPx: Int)

/**
 * Detects the real display cutout so the island can hug the camera hole on centre- *and* corner-
 * punch devices — instead of assuming a centred notch. Auto-fit for off-centre cutouts is the #1
 * thing users complain the incumbents get wrong, so this is a core differentiator, not polish.
 *
 * Cutouts only exist on Android P (API 28)+; on older devices (or devices with no cutout) this
 * returns null and the caller falls back to manual positioning.
 */
object CutoutUtils {

    fun detect(windowManager: WindowManager, root: View): CutoutInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null

        val cutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.windowInsets.displayCutout
        } else {
            root.rootWindowInsets?.displayCutout
        } ?: return null

        // Prefer the dedicated top bounding rect (API 29+); otherwise take the highest rect.
        val rect = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) cutout.boundingRectTop else null)
            ?.takeIf { !it.isEmpty }
            ?: cutout.boundingRects.filter { !it.isEmpty }.minByOrNull { it.top }
            ?: return null

        val displayWidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.width()
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(dm)
            dm.widthPixels
        }

        return CutoutInfo(
            centerXOffsetPx = rect.centerX() - displayWidth / 2,
            topPx = rect.top,
        )
    }
}
