package com.dynamicisland.app.feature

/**
 * Process-wide on/off switches for each island feature, mirrored from persisted settings by
 * [com.dynamicisland.app.service.IslandOverlayService]. Feature sources check these before
 * publishing so the user can disable any category from the settings screen.
 */
object FeatureFlags {
    @Volatile var media: Boolean = true
    @Volatile var call: Boolean = true
    @Volatile var timer: Boolean = true
    @Volatile var liveActivity: Boolean = true
}
