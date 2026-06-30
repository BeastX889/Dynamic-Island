package com.dynamicisland.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ---- Obsidian palette: near-black canvas, one restrained accent, color only in signature dots. ----
val Ink = Color(0xFF000000)
val IslandSurface = Color(0xFF0E0E10)
val IslandElevated = Color(0xFF161618)
val Accent = Color(0xFF5E9EFF)
val OnAccent = Color(0xFF04060B)
val TextHi = Color(0xFFF2F2F4)
val TextLo = Color(0xFF8A8A8F)
val Success = Color(0xFF34C759)
val Warning = Color(0xFFFF9F0A)
val LiveAccent = Color(0xFF8AB4F8)
val Hairline = Color(0x14FFFFFF)
val HairlineInset = Color(0x0FFFFFFF)
val SuccessTint = Color(0x1A34C759)

private val ObsidianColors = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    background = Ink,
    onBackground = TextHi,
    surface = IslandSurface,
    onSurface = TextHi,
    surfaceVariant = IslandElevated,
    onSurfaceVariant = TextLo,
    outline = Hairline,
)

/** Always-dark by design — the product itself is a black pill on a near-black field. */
@Composable
fun DynamicIslandTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ObsidianColors, content = content)
}
