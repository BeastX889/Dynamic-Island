package com.dynamicisland.app.overlay

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Hosts Compose content inside a window added via [android.view.WindowManager], where there is no
 * Activity to supply the lifecycle/owner tree that Compose requires.
 *
 * Create one per overlay, call [onAttached] right after `WindowManager.addView`, and [onDetached]
 * before `removeView`. The [composeView] is what you actually add to the window manager.
 */
class ComposeOverlayHost(
    context: Context,
    content: @Composable () -> Unit,
) : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    val composeView: ComposeView = ComposeView(context).apply {
        setViewTreeLifecycleOwner(this@ComposeOverlayHost)
        setViewTreeViewModelStoreOwner(this@ComposeOverlayHost)
        setViewTreeSavedStateRegistryOwner(this@ComposeOverlayHost)
        setContent(content)
    }

    init {
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    /** Move to RESUMED so Compose starts drawing/animating. Call after the view is added. */
    fun onAttached() {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    /** Tear down. Call before removing the view from the window manager. */
    fun onDetached() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}
