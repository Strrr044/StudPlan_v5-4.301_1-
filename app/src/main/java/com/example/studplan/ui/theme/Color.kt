package com.example.studplan.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class AppColors(
    val background: Color,
    val foreground: Color,
    val card: Color,
    val primary: Color,
    val onPrimary: Color,
    val primarySoft: Color,
    val muted: Color,
    val mutedForeground: Color,
    val border: Color,
    val danger: Color,
    val dangerSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val success: Color,
    val successSoft: Color
)

// Светлая тема: цвета из твоего дизайна
val LightAppColors = AppColors(
    background = Color(0xFFF4F7FB),
    foreground = Color(0xFF111B28),
    card = Color(0xFFFFFFFF),
    primary = Color(0xFF348DCF),
    onPrimary = Color(0xFFFFFFFF),
    primarySoft = Color(0xFFD3EBFF),
    muted = Color(0xFFE6ECF2),
    mutedForeground = Color(0xFF687380),
    border = Color(0xFFDCE2E8),
    danger = Color(0xFFD73337),
    dangerSoft = Color(0xFFFFE6E3),
    warning = Color(0xFF9A6A00),
    warningSoft = Color(0xFFFFF1CC),
    success = Color(0xFF2E7D32),
    successSoft = Color(0xFFDDF3DF)
)

// Тёмная тема: подобрана мной на основе светлой
// Тёмная тема: цвета сняты с твоих макетов
val DarkAppColors = AppColors(
    background = Color(0xFF091018),
    foreground = Color(0xFFF3F5F8),
    card = Color(0xFF121C26),
    primary = Color(0xFF58A5E4),
    onPrimary = Color(0xFFFFFFFF),
    primarySoft = Color(0xFF1B3550),
    muted = Color(0xFF1D2A37),
    mutedForeground = Color(0xFF93A1B0),
    border = Color(0xFF263443),
    danger = Color(0xFFF2676B),
    dangerSoft = Color(0xFF3A1D22),
    warning = Color(0xFFF2B93B),
    warningSoft = Color(0xFF38301A),
    success = Color(0xFF5CC265),
    successSoft = Color(0xFF1C3320)
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

object AppTheme {
    val colors: AppColors
        @Composable get() = LocalAppColors.current
}