package com.dynamicisland.app.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicisland.app.island.CallPhase
import com.dynamicisland.app.island.IslandState
import com.dynamicisland.app.island.MusicAction
import com.dynamicisland.app.island.Presentation
import androidx.compose.foundation.Image as ComposeImage

/** Target geometry per presentation. Tuned to sit comfortably around a typical punch-hole. */
private data class Geometry(val width: Dp, val height: Dp, val corner: Dp)

private fun geometryFor(presentation: Presentation): Geometry = when (presentation) {
    Presentation.HIDDEN -> Geometry(0.dp, 0.dp, 0.dp)
    Presentation.COMPACT -> Geometry(150.dp, 37.dp, 20.dp)
    Presentation.SPLIT -> Geometry(240.dp, 37.dp, 20.dp)
    Presentation.EXPANDED -> Geometry(360.dp, 170.dp, 38.dp)
}

/**
 * The morphing pill. Animates its size and corner radius with spring physics as [state] changes,
 * and cross-fades its contents via [AnimatedContent]. Tap toggles the expanded view.
 *
 * The composable draws nothing for [IslandState.Hidden]; callers can keep it mounted and simply
 * flip the state to hide/show without tearing down the overlay.
 */
@Composable
fun IslandView(
    state: IslandState,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onMusicAction: (MusicAction) -> Unit = {},
    scale: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val geo = geometryFor(state.presentation)

    val springSpec = spring<Dp>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    val width by animateDpAsState(geo.width * scale, springSpec, label = "width")
    val height by animateDpAsState(geo.height * scale, springSpec, label = "height")
    val corner by animateDpAsState(geo.corner * scale, springSpec, label = "corner")

    if (state is IslandState.Hidden) return

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(corner))
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onLongPress() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = state.contentKey(),
            transitionSpec = { (fadeIn() togetherWith fadeOut()) },
            label = "island-content",
        ) { _ ->
            IslandContent(state, onMusicAction)
        }
    }
}

/** A stable key so [AnimatedContent] only cross-fades when the *kind* of content changes. */
private fun IslandState.contentKey(): String = when (this) {
    is IslandState.Hidden -> "hidden"
    is IslandState.Idle -> "idle"
    is IslandState.Music -> "music:$expanded"
    is IslandState.Call -> "call:$phase:$expanded"
    is IslandState.Timer -> "timer:$expanded"
    is IslandState.LiveActivity -> "live:${trailingLabel != null}:$expanded"
}

@Composable
private fun IslandContent(state: IslandState, onMusicAction: (MusicAction) -> Unit) {
    when (state) {
        is IslandState.Idle, is IslandState.Hidden -> Unit
        is IslandState.Music -> MusicContent(state, onMusicAction)
        is IslandState.Call -> CallContent(state)
        is IslandState.Timer -> TimerContent(state)
        is IslandState.LiveActivity -> LiveActivityContent(state)
    }
}

@Composable
private fun MusicContent(state: IslandState.Music, onMusicAction: (MusicAction) -> Unit) {
    if (state.expanded) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Artwork(state)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    PrimaryText(state.title)
                    SecondaryText(state.artist)
                }
            }
            Spacer(Modifier.height(12.dp))
            val progress = if (state.durationMs > 0) {
                (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
            } else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                trackColor = Color(0x33FFFFFF),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransportButton(Icons.Filled.SkipPrevious) { onMusicAction(MusicAction.PREVIOUS) }
                TransportButton(
                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                ) { onMusicAction(MusicAction.PLAY_PAUSE) }
                TransportButton(Icons.Filled.SkipNext) { onMusicAction(MusicAction.NEXT) }
            }
        }
    } else {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Artwork(state, size = 26.dp)
            EqualizerBars(isPlaying = state.isPlaying)
        }
    }
}

@Composable
private fun Artwork(state: IslandState.Music, size: Dp = 56.dp) {
    val bitmap = state.artwork
    if (bitmap != null) {
        ComposeImage(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(RoundedCornerShape(8.dp)),
        )
    } else {
        Box(Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(Color(0xFF8AB4F8)))
    }
}

/** Three bars that bounce while playing, settling flat when paused. */
@Composable
private fun EqualizerBars(isPlaying: Boolean) {
    val color = if (isPlaying) Color(0xFF8AB4F8) else Color(0x66FFFFFF)
    val transition = rememberInfiniteTransition(label = "eq")
    val phases = listOf(0, 180, 90)
    Row(verticalAlignment = Alignment.Bottom) {
        phases.forEach { offsetMs ->
            val h by transition.animateFloat(
                initialValue = 6f,
                targetValue = 20f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 500, delayMillis = offsetMs),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "bar",
            )
            val height = if (isPlaying) h.dp else 10.dp
            Box(Modifier.padding(horizontal = 1.dp).width(3.dp).height(height).background(color))
        }
    }
}

@Composable
private fun TransportButton(icon: ImageVector, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.White)
    }
}

@Composable
private fun CallContent(state: IslandState.Call) {
    val phaseLabel = when (state.phase) {
        CallPhase.INCOMING -> "Incoming call"
        CallPhase.ACTIVE -> "On call"
        CallPhase.ENDED -> "Call ended"
    }
    if (state.expanded) {
        Column(Modifier.padding(20.dp)) {
            PrimaryText(state.displayName)
            SecondaryText(phaseLabel)
        }
    } else {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(Color(0xFF34C759)))
            SecondaryText(state.displayName)
        }
    }
}

@Composable
private fun TimerContent(state: IslandState.Timer) {
    val text = formatRemaining(state.remainingMs)
    if (state.expanded) {
        Column(Modifier.padding(20.dp)) {
            SecondaryText(state.label)
            PrimaryText(text, size = 30.sp)
        }
    } else {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(Modifier.size(20.dp).clip(CircleShape).background(Color(0xFFFF9F0A)))
            PrimaryText(text)
        }
    }
}

@Composable
private fun LiveActivityContent(state: IslandState.LiveActivity) {
    if (state.expanded) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(state.leadingEmoji, fontSize = 28.sp)
                Spacer(Modifier.width(12.dp))
                Column {
                    PrimaryText(state.title)
                    SecondaryText(state.status)
                }
            }
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { state.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                trackColor = Color(0x33FFFFFF),
            )
        }
    } else {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(state.leadingEmoji, fontSize = 18.sp)
            state.trailingLabel?.let { SecondaryText(it) }
        }
    }
}

@Composable
private fun PrimaryText(text: String, size: androidx.compose.ui.unit.TextUnit = 15.sp) {
    Text(
        text = text,
        color = Color.White,
        fontSize = size,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun SecondaryText(text: String) {
    Text(
        text = text,
        color = Color(0xCCFFFFFF),
        fontSize = 13.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun formatRemaining(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
