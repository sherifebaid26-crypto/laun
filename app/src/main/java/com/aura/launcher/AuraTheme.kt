package com.aura.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

data class AuraPalette(
    val panel: Color,        // الدوك والمجلدات
    val drawerBg: Color,     // الدروار والإعدادات
    val dialog: Color,       // خلفية الحوارات (معتمة)
    val border: Color,
    val onPanel: Color,
    val homeText: Color,
    val homeShadow: Color,
    val field: Color,
    val accent: Color,
    val isLight: Boolean,
)

val AccentChoices = listOf(
    0xFF00E5FF, 0xFF7C4DFF, 0xFFFF4081, 0xFFFF9100, 0xFF00E676, 0xFFFF5252,
)

fun paletteFor(mode: ThemeMode, accentValue: Long): AuraPalette {
    val accent = Color(accentValue)
    return when (mode) {
        ThemeMode.DARK -> AuraPalette(
            panel = Color(0xE6101820), drawerBg = Color(0xF2101820), dialog = Color(0xFF16202C),
            border = Color(0x22FFFFFF), onPanel = Color.White, homeText = Color.White,
            homeShadow = Color(0x99000000), field = Color(0x33FFFFFF), accent = accent, isLight = false,
        )
        ThemeMode.LIGHT -> AuraPalette(
            panel = Color(0xF2F6F9FC), drawerBg = Color(0xF5F6F9FC), dialog = Color(0xFFFFFFFF),
            border = Color(0x22000000), onPanel = Color(0xFF111827), homeText = Color(0xFF111827),
            homeShadow = Color(0x99FFFFFF), field = Color(0x14000000), accent = accent, isLight = true,
        )
        ThemeMode.GLASS -> AuraPalette(
            panel = Color(0x40FFFFFF), drawerBg = Color(0x990B1220), dialog = Color(0xFF1B2735),
            border = Color(0x66FFFFFF), onPanel = Color.White, homeText = Color.White,
            homeShadow = Color(0x99000000), field = Color(0x33FFFFFF), accent = accent, isLight = false,
        )
        ThemeMode.COLOR -> {
            val tint = lerp(Color(0xFF0B0F2A), accent, 0.35f)
            AuraPalette(
                panel = tint.copy(alpha = 0.9f), drawerBg = tint.copy(alpha = 0.96f),
                dialog = lerp(Color(0xFF0B0F2A), accent, 0.25f),
                border = Color(0x33FFFFFF), onPanel = Color.White, homeText = Color.White,
                homeShadow = Color(0x99000000), field = Color(0x33FFFFFF), accent = accent, isLight = false,
            )
        }
    }
}

@Composable
fun AuraTheme(palette: AuraPalette, content: @Composable () -> Unit) {
    val scheme = if (palette.isLight) {
        lightColorScheme(primary = palette.accent)
    } else {
        darkColorScheme(primary = palette.accent)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
