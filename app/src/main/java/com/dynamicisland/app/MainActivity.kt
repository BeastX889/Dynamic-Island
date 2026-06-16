package com.dynamicisland.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import com.dynamicisland.app.data.IslandSettings
import com.dynamicisland.app.data.SettingsRepository
import com.dynamicisland.app.feature.timer.IslandTimer
import com.dynamicisland.app.island.CallPhase
import com.dynamicisland.app.island.IslandController
import com.dynamicisland.app.island.IslandState
import com.dynamicisland.app.service.IslandOverlayService
import com.dynamicisland.app.ui.theme.DynamicIslandTheme
import com.dynamicisland.app.util.PermissionUtils
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DynamicIslandTheme {
                Scaffold { padding ->
                    HomeScreen(Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Re-read permission state whenever the screen resumes (user may have toggled it in Settings).
    var refreshKey by remember { mutableStateOf(0) }
    LifecycleResumeEffect { refreshKey++ }

    val overlayGranted = remember(refreshKey) { PermissionUtils.canDrawOverlays(context) }
    val listenerEnabled = remember(refreshKey) { PermissionUtils.isNotificationListenerEnabled(context) }
    val notifsEnabled = remember(refreshKey) { PermissionUtils.canPostNotifications(context) }

    val requestPostNotifs = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshKey++ }
    val requestPhone = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshKey++ }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text("Dynamic Island", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        PermissionCard(
            title = stringRes(R.string.perm_overlay_title),
            desc = stringRes(R.string.perm_overlay_desc),
            granted = overlayGranted,
        ) { context.startActivity(PermissionUtils.overlaySettingsIntent(context)) }

        PermissionCard(
            title = stringRes(R.string.perm_notif_listener_title),
            desc = stringRes(R.string.perm_notif_listener_desc),
            granted = listenerEnabled,
        ) { context.startActivity(PermissionUtils.notificationListenerSettingsIntent()) }

        PermissionCard(
            title = stringRes(R.string.perm_post_notif_title),
            desc = stringRes(R.string.perm_post_notif_desc),
            granted = notifsEnabled,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPostNotifs.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        PermissionCard(
            title = stringRes(R.string.perm_phone_title),
            desc = stringRes(R.string.perm_phone_desc),
            granted = false,
        ) { requestPhone.launch(Manifest.permission.READ_PHONE_STATE) }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { IslandOverlayService.start(context) },
                enabled = overlayGranted,
            ) { Text(stringRes(R.string.action_start)) }
            OutlinedButton(onClick = { IslandOverlayService.stop(context) }) {
                Text(stringRes(R.string.action_stop))
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        Text("Demo states", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        DemoButtons()

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        Text("Timer", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        TimerControls()

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        Text("Settings", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        SettingsSection()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimerControls() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { IslandTimer.start("Timer", 60_000) }) { Text("Start 1 min") }
        OutlinedButton(onClick = { IslandTimer.start("Timer", 10_000) }) { Text("Start 10 s") }
        OutlinedButton(onClick = { IslandTimer.cancel() }) { Text("Cancel") }
    }
}

@Composable
private fun SettingsSection() {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()
    val settings by repo.flow.collectAsState(initial = IslandSettings())

    LabeledSlider("Vertical position", settings.verticalOffsetDp.toFloat(), 0f..120f) { v ->
        scope.launch { repo.setVerticalOffset(v.toInt()) }
    }
    LabeledSlider("Horizontal position", settings.horizontalOffsetDp.toFloat(), -80f..80f) { v ->
        scope.launch { repo.setHorizontalOffset(v.toInt()) }
    }
    LabeledSlider("Size (%)", settings.scalePercent.toFloat(), 70f..130f) { v ->
        scope.launch { repo.setScalePercent(v.toInt()) }
    }
    FeatureSwitch("Now playing", settings.mediaEnabled) { scope.launch { repo.setMediaEnabled(it) } }
    FeatureSwitch("Calls", settings.callEnabled) { scope.launch { repo.setCallEnabled(it) } }
    FeatureSwitch("Timers", settings.timerEnabled) { scope.launch { repo.setTimerEnabled(it) } }
    FeatureSwitch("Live activities", settings.liveActivityEnabled) {
        scope.launch { repo.setLiveActivityEnabled(it) }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("$label: ${value.toInt()}", style = MaterialTheme.typography.bodySmall)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun FeatureSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DemoButtons() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            IslandController.submit(
                IslandController.Source.MEDIA,
                IslandState.Music(title = "Midnight City", artist = "M83", isPlaying = true, positionMs = 60_000, durationMs = 240_000),
            )
        }) { Text("Music") }

        OutlinedButton(onClick = {
            IslandController.submit(
                IslandController.Source.CALL,
                IslandState.Call(displayName = "Alex", phase = CallPhase.INCOMING),
            )
        }) { Text("Call") }

        OutlinedButton(onClick = {
            IslandController.submit(
                IslandController.Source.TIMER,
                IslandState.Timer(label = "Pasta", remainingMs = 305_000, totalMs = 600_000),
            )
        }) { Text("Timer") }

        OutlinedButton(onClick = {
            IslandController.submit(
                IslandController.Source.LIVE_ACTIVITY,
                IslandState.LiveActivity(title = "Pizza Palace", status = "Out for delivery", progress = 0.7f, trailingLabel = "12 min"),
            )
        }) { Text("Live activity") }

        OutlinedButton(onClick = {
            IslandController.submit(IslandController.Source.MEDIA, null)
            IslandController.submit(IslandController.Source.CALL, null)
            IslandController.submit(IslandController.Source.TIMER, null)
            IslandController.submit(IslandController.Source.LIVE_ACTIVITY, null)
        }) { Text("Clear") }
    }
}

@Composable
private fun PermissionCard(title: String, desc: String, granted: Boolean, onGrant: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.fillMaxWidth(0.7f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(desc, style = MaterialTheme.typography.bodySmall)
            }
            if (granted) {
                Text(stringRes(R.string.action_granted), color = MaterialTheme.colorScheme.primary)
            } else {
                Button(onClick = onGrant) { Text(stringRes(R.string.action_grant)) }
            }
        }
    }
}

@Composable
private fun stringRes(id: Int): String = androidx.compose.ui.res.stringResource(id)

/** Runs [onResume] every time the composition's lifecycle owner reaches RESUMED. */
@Composable
private fun LifecycleResumeEffect(onResume: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onResume()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
