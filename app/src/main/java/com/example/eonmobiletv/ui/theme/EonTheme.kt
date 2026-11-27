package com.example.eonmobiletv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object EonColors {
    val Page = Color(0xFF0D1117)
    val Card = Color(0xFF161B22)
    val CardActive = Color(0xFF10232E)
    val Border = Color(0xFF21262D)
    val Accent = Color(0xFF00D4E6)
    val OnAccent = Color(0xFF0A0D12)
    val Live = Color(0xFFFF3B4E)
    val Favorite = Color(0xFFFFB800)
    val TextPrimary = Color(0xFFF0F6FC)
    val TextMuted = Color(0xFF8B949E)
    val TextSoft = Color(0xFF9BA3AF)
    val KeypadKey = Color(0xFF21262D)
    val KeypadKeyActive = Color(0xFF30363D)
    val ScrimTop = Color(0xE00D1117)
    val ScrimBottom = Color(0xEB0D1117)
    val HudBubble = Color(0xE0161B22)
    val DangerBubble = Color(0xE0311017)
    val EpgCatchupBadge = Color(0xFF1F3A4B)
    val EpgNowBg = Color(0xFF122530)
}

private val EonColorScheme = darkColorScheme(
    primary = EonColors.Accent,
    onPrimary = EonColors.OnAccent,
    secondary = EonColors.Live,
    background = EonColors.Page,
    onBackground = EonColors.TextPrimary,
    surface = EonColors.Card,
    onSurface = EonColors.TextPrimary,
    surfaceVariant = EonColors.KeypadKey,
    onSurfaceVariant = EonColors.TextMuted,
    outline = EonColors.Border,
    error = EonColors.Live
)

@Composable
fun EonTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = EonColorScheme, content = content)
}
