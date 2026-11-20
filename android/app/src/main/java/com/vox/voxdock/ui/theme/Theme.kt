package com.vox.voxdock.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

data class VoxPalette(
    val bg: Color,
    val card: Color,
    val cardAlt: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val alertNoneBg: Color,
    val alertNoneBorder: Color,
    val alertNoneText: Color,
    val alertPartialBg: Color,
    val alertPartialBorder: Color,
    val alertPartialText: Color,
    val alertActiveBg: Color,
    val alertActiveBorder: Color,
    val alertActiveText: Color,
    val weatherBg: Color,
    val weatherBorder: Color,
    val unseenBg: Color,
    val unseenBorder: Color,
    val mcpBg: Color,
    val mcpBorder: Color,
    val powerBg: Color,
    val powerBorder: Color
)

val DarkVoxPalette = VoxPalette(
    bg = Color(0xFF111116),
    card = Color(0xFF181820),
    cardAlt = Color(0xFF1F1F2A),
    border = Color(0xFF2B2B38),
    text = Color(0xFFF4F4F5),
    muted = Color(0xFF9CA3AF),
    alertNoneBg = Color(0xFF14241B),
    alertNoneBorder = Color(0xFF22C55E),
    alertNoneText = Color(0xFFDCFCE7),
    alertPartialBg = Color(0xFF2C2312),
    alertPartialBorder = Color(0xFFF59E0B),
    alertPartialText = Color(0xFFFEF3C7),
    alertActiveBg = Color(0xFF3E1218),
    alertActiveBorder = Color(0xFFEF4444),
    alertActiveText = Color(0xFFFECACA),
    weatherBg = Color(0xFF161F2E),
    weatherBorder = Color(0xFF3B82F6),
    unseenBg = Color(0xFF141D25),
    unseenBorder = Color(0xFF06B6D4),
    mcpBg = Color(0xFF1D1827),
    mcpBorder = Color(0xFFA855F7),
    powerBg = Color(0xFF211E15),
    powerBorder = Color(0xFFEAB308)
)

val LightVoxPalette = VoxPalette(
    bg = Color(0xFFF4F3EF),
    card = Color(0xFFFFFFFF),
    cardAlt = Color(0xFFF5F5F7),
    border = Color(0xFFE2E2E8),
    text = Color(0xFF18181B),
    muted = Color(0xFF52525B),
    alertNoneBg = Color(0xFFDCFCE7),
    alertNoneBorder = Color(0xFF16A34A),
    alertNoneText = Color(0xFF14532D),
    alertPartialBg = Color(0xFFFEF3C7),
    alertPartialBorder = Color(0xFFD97706),
    alertPartialText = Color(0xFF78350F),
    alertActiveBg = Color(0xFFFEE2E2),
    alertActiveBorder = Color(0xFFDC2626),
    alertActiveText = Color(0xFF991B1B),
    weatherBg = Color(0xFFE0F2FE),
    weatherBorder = Color(0xFF0284C7),
    unseenBg = Color(0xFFECFEFF),
    unseenBorder = Color(0xFF0891B2),
    mcpBg = Color(0xFFF3E8FF),
    mcpBorder = Color(0xFF9333EA),
    powerBg = Color(0xFFFEF9C3),
    powerBorder = Color(0xFFCA8A04)
)

val LocalVoxPalette = staticCompositionLocalOf { DarkVoxPalette }

private val MonoTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 32.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
)

@Composable
fun VoxDockTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkVoxPalette else LightVoxPalette
    val scheme = if (darkTheme) {
        darkColorScheme(
            background = palette.bg,
            surface = palette.card,
            onBackground = palette.text,
            onSurface = palette.text,
            primary = Color(0xFF6366F1)
        )
    } else {
        lightColorScheme(
            background = palette.bg,
            surface = palette.card,
            onBackground = palette.text,
            onSurface = palette.text,
            primary = Color(0xFF4F46E5)
        )
    }

    CompositionLocalProvider(LocalVoxPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = MonoTypography,
            content = content
        )
    }
}
