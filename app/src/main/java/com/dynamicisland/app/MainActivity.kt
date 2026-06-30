package com.dynamicisland.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import com.dynamicisland.app.data.IslandSettings
import com.dynamicisland.app.data.SettingsRepository
import com.dynamicisland.app.feature.timer.IslandTimer
import com.dynamicisland.app.island.CallPhase
import com.dynamicisland.app.island.IslandController
import com.dynamicisland.app.island.IslandState
import com.dynamicisland.app.service.IslandOverlayService
import com.dynamicisland.app.ui.theme.Accent
import com.dynamicisland.app.ui.theme.DynamicIslandTheme
import com.dynamicisland.app.ui.theme.Hairline
import com.dynamicisland.app.ui.theme.HairlineInset
import com.dynamicisland.app.ui.theme.Ink
import com.dynamicisland.app.ui.theme.IslandElevated
import com.dynamicisland.app.ui.theme.IslandSurface
import com.dynamicisland.app.ui.theme.LiveAccent
import com.dynamicisland.app.ui.theme.OnAccent
import com.dynamicisland.app.ui.theme.Success
import com.dynamicisland.app.ui.theme.SuccessTint
import com.dynamicisland.app.ui.theme.TextHi
import com.dynamicisland.app.ui.theme.TextLo
import com.dynamicisland.app.ui.theme.Warning
import com.dynamicisland.app.util.PermissionUtils
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DynamicIslandTheme {
                HomeScreen()
            }
        }
    }
}

// ---- Demo state builders (shared by the hero tap-to-cycle and the demo chips) ----
private fun demoMusic() = IslandState.Music(
    title = "Midnight City", artist = "M83", isPlaying = true,
    positionMs = 62_000, durationMs = 244_000,
)
private fun demoCall() = IslandState.Call(displayName = "Alex", phase = CallPhase.INCOMING)
private fun demoTimer() = IslandState.Timer(label = "Pasta", remainingMs = 305_000, totalMs = 600_000)
private fun demoLive() = IslandState.LiveActivity(
    title = "Pizza Palace", status = "Out for delivery", progress = 0.72f, trailingLabel = "12 min",
)

private fun clearDemos() {
    IslandController.submit(IslandController.Source.MEDIA, null)
    IslandController.submit(IslandController.Source.CALL, null)
    IslandController.submit(IslandController.Source.TIMER, null)
    IslandController.submit(IslandController.Source.LIVE_ACTIVITY, null)
}

@Composable
private fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Re-read permission state on resume, and re-poke a running service so freshly granted
    // permissions attach without a manual restart.
    var refreshKey by remember { mutableStateOf(0) }
    LifecycleResumeEffect {
        refreshKey++
        if (IslandOverlayService.isRunning && PermissionUtils.canDrawOverlays(context)) {
            IslandOverlayService.start(context)
        }
    }

    val overlayGranted = remember(refreshKey) { PermissionUtils.canDrawOverlays(context) }
    val listenerEnabled = remember(refreshKey) { PermissionUtils.isNotificationListenerEnabled(context) }
    val notifsEnabled = remember(refreshKey) { PermissionUtils.canPostNotifications(context) }
    val phoneGranted = remember(refreshKey) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
    }

    val requestPostNotifs = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshKey++ }
    val requestPhone = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshKey++ }

    val settingsRepo = remember { SettingsRepository(context) }
    val settings by settingsRepo.flow.collectAsState(initial = IslandSettings())

    val islandState by IslandController.state.collectAsState()

    val running = IslandOverlayService.isRunning

    val perms = listOf(
        PermissionItem(
            stringResource(R.string.perm_overlay_title), stringResource(R.string.perm_overlay_desc),
            overlayGranted,
        ) { context.startActivity(PermissionUtils.overlaySettingsIntent(context)) },
        PermissionItem(
            stringResource(R.string.perm_notif_listener_title), stringResource(R.string.perm_notif_listener_desc),
            listenerEnabled,
        ) { context.startActivity(PermissionUtils.notificationListenerSettingsIntent()) },
        PermissionItem(
            stringResource(R.string.perm_post_notif_title), stringResource(R.string.perm_post_notif_desc),
            notifsEnabled,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPostNotifs.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        PermissionItem(
            stringResource(R.string.perm_phone_title), stringResource(R.string.perm_phone_desc),
            phoneGranted,
        ) { requestPhone.launch(Manifest.permission.READ_PHONE_STATE) },
    )
    val readyCount = perms.count { it.granted }

    // Hero tap cycles through demo states so the screen is a live, interactive preview.
    var demoIdx by remember { mutableIntStateOf(-1) }
    val demoCycle = listOf<() -> Unit>(
        { IslandController.submit(IslandController.Source.MEDIA, demoMusic()) },
        { IslandController.submit(IslandController.Source.CALL, demoCall()) },
        { IslandController.submit(IslandController.Source.TIMER, demoTimer()) },
        { IslandController.submit(IslandController.Source.LIVE_ACTIVITY, demoLive()) },
        { clearDemos() },
    )

    Box(Modifier.fillMaxSize().background(Ink)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IslandHero(
                state = islandState,
                running = running,
                scale = settings.scalePercent / 100f,
                onTap = {
                    demoIdx = (demoIdx + 1) % demoCycle.size
                    demoCycle[demoIdx]()
                },
            )

            Spacer(Modifier.height(8.dp))

            Eyebrow("Permissions", trailing = "$readyCount of 4 ready")
            GroupCard {
                perms.forEachIndexed { i, p ->
                    PermissionRow(p)
                    if (i < perms.lastIndex) InsetDivider()
                }
            }

            Spacer(Modifier.height(8.dp))
            PrimaryCta(
                running = running,
                enabled = overlayGranted,
                onClick = {
                    if (running) IslandOverlayService.stop(context) else IslandOverlayService.start(context)
                    refreshKey++
                },
            )
            if (!overlayGranted) {
                Text(
                    "Enable “Draw over other apps” to start.",
                    color = TextLo, fontSize = 13.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                )
            }

            Spacer(Modifier.height(8.dp))
            Eyebrow("Demo states")
            DemoChips()

            Spacer(Modifier.height(8.dp))
            Eyebrow("Timer")
            TimerTrack()

            Spacer(Modifier.height(8.dp))
            Eyebrow("Settings")
            GroupCard {
                LabeledSlider("Vertical position", settings.verticalOffsetDp.toFloat(), 0f..120f,
                    format = { "${it.toInt()}" }) { v -> scope.launch { settingsRepo.setVerticalOffset(v.toInt()) } }
                InsetDivider()
                LabeledSlider("Horizontal position", settings.horizontalOffsetDp.toFloat(), -80f..80f,
                    format = { val n = it.toInt(); if (n > 0) "+$n" else "$n" }) { v -> scope.launch { settingsRepo.setHorizontalOffset(v.toInt()) } }
                InsetDivider()
                LabeledSlider("Size", settings.scalePercent.toFloat(), 70f..130f,
                    format = { "${it.toInt()}%" }) { v -> scope.launch { settingsRepo.setScalePercent(v.toInt()) } }
                InsetDivider()
                SwitchRow("Now playing", settings.mediaEnabled) { scope.launch { settingsRepo.setMediaEnabled(it) } }
                InsetDivider()
                SwitchRow("Calls", settings.callEnabled) { scope.launch { settingsRepo.setCallEnabled(it) } }
                InsetDivider()
                SwitchRow("Timers", settings.timerEnabled) { scope.launch { settingsRepo.setTimerEnabled(it) } }
                InsetDivider()
                SwitchRow("Live activities", settings.liveActivityEnabled) { scope.launch { settingsRepo.setLiveActivityEnabled(it) } }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Hero
// ---------------------------------------------------------------------------------------------

@Composable
private fun IslandHero(state: IslandState, running: Boolean, scale: Float, onTap: () -> Unit) {
    val heroState = if (state is IslandState.Hidden) IslandState.Idle else state
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 232.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(IslandSurface)
            .drawWithCache {
                val sheen = Brush.radialGradient(
                    colors = listOf(Color(0xFF1C1C20), Color.Transparent),
                    center = Offset(size.width / 2f, 70.dp.toPx()),
                    radius = size.width * 0.55f,
                )
                onDrawBehind { drawRect(sheen) }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 80.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                com.dynamicisland.app.overlay.IslandView(
                    state = heroState,
                    onTap = onTap,
                    onLongPress = { IslandController.setExpanded(true) },
                    scale = scale.coerceIn(0.7f, 1.1f),
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "Dynamic Island",
                color = TextHi, fontSize = 28.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.5).sp,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(if (running) Success else TextLo)
                Spacer(Modifier.width(7.dp))
                Text(
                    if (running) "Active" else "Off",
                    color = TextLo, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Tap the pill to preview states",
                color = TextLo.copy(alpha = 0.6f), fontSize = 12.sp,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Building blocks
// ---------------------------------------------------------------------------------------------

@Composable
private fun Eyebrow(text: String, trailing: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text.uppercase(),
            color = TextLo, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp,
        )
        if (trailing != null) {
            Text(trailing, color = TextLo, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun GroupCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(IslandSurface)
            .border(BorderStroke(1.dp, Hairline), RoundedCornerShape(20.dp)),
    ) { content() }
}

@Composable
private fun InsetDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(1.dp)
            .background(HairlineInset),
    )
}

@Composable
private fun StatusDot(color: Color, size: Dp = 6.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

private class PermissionItem(
    val title: String,
    val desc: String,
    val granted: Boolean,
    val onGrant: () -> Unit,
)

@Composable
private fun PermissionRow(item: PermissionItem) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (item.granted) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (item.granted) Success else TextLo,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(item.desc, color = TextLo, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        if (item.granted) {
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(SuccessTint)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text("Granted", color = Success, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Box(
                Modifier.clip(RoundedCornerShape(50)).clickable { item.onGrant() }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text("Grant", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PrimaryCta(running: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "cta")

    val bg = if (!enabled || running) IslandElevated else Accent
    val label = if (running) "Stop Island" else "Start Island"
    val labelColor = when {
        !enabled -> TextLo
        running -> TextHi
        else -> OnAccent
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(pressScale)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
            ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (running) {
                StatusDot(Success, 7.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(label, color = labelColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private class DemoChip(val label: String, val icon: ImageVector, val tint: Color, val onClick: () -> Unit)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DemoChips() {
    val chips = listOf(
        DemoChip("Music", Icons.Rounded.MusicNote, Accent) {
            IslandController.submit(IslandController.Source.MEDIA, demoMusic())
        },
        DemoChip("Call", Icons.Rounded.Phone, Success) {
            IslandController.submit(IslandController.Source.CALL, demoCall())
        },
        DemoChip("Timer", Icons.Rounded.Timer, Warning) {
            IslandController.submit(IslandController.Source.TIMER, demoTimer())
        },
        DemoChip("Live", Icons.Rounded.LocalShipping, LiveAccent) {
            IslandController.submit(IslandController.Source.LIVE_ACTIVITY, demoLive())
        },
        DemoChip("Clear", Icons.Rounded.Close, TextLo) { clearDemos() },
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.forEach { chip ->
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            Row(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(IslandElevated)
                    .clickable(interactionSource = interaction, indication = null) { chip.onClick() }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    chip.icon, contentDescription = null,
                    tint = if (pressed) chip.tint else TextLo,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(chip.label, color = if (chip.label == "Clear") TextLo else TextHi, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun TimerTrack() {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(IslandSurface)
            .border(BorderStroke(1.dp, Hairline), RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Segment("Start 1 min", Modifier.weight(1f), TextHi) { IslandTimer.start("Timer", 60_000) }
        Segment("Start 10 s", Modifier.weight(1f), TextHi) { IslandTimer.start("Timer", 10_000) }
        Segment("Cancel", Modifier.weight(1f), TextLo) { IslandTimer.cancel() }
    }
}

@Composable
private fun Segment(label: String, modifier: Modifier, color: Color, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(11.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Box(
                Modifier.clip(RoundedCornerShape(8.dp)).background(IslandElevated)
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            ) {
                Text(format(value), color = TextHi, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Accent,
                activeTrackColor = Accent,
                inactiveTrackColor = Color(0x1FFFFFFF),
            ),
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Accent,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFF2A2A2E),
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

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
