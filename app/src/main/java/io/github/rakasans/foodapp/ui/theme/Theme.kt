package io.github.rakasans.foodapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Tokeny kolorów (docs/ARCHITECTURE.md, sekcja 5).
 * Akcent: zieleń (jasny) / rozjaśniona zieleń (ciemny) — decyzja 13.
 * Bursztyn zarezerwowany dla ostrzeżeń, niebieski — informacje, czerwony — błędy.
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F6B4F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3EFE7),
    onPrimaryContainer = Color(0xFF245A40),
    secondaryContainer = Color(0xFFE3EFE7),
    onSecondaryContainer = Color(0xFF245A40),
    background = Color(0xFFF4F5F1),
    onBackground = Color(0xFF1C211E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C211E),
    surfaceVariant = Color(0xFFECEEEA),
    onSurfaceVariant = Color(0xFF5A635D),
    surfaceContainer = Color(0xFFFFFFFF),
    outline = Color(0xFFB9C0B6),
    outlineVariant = Color(0xFFDDE1DA),
    error = Color(0xFF9B2626),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD0AE),
    onPrimary = Color(0xFF0F3A27),
    primaryContainer = Color(0xFF1F4A36),
    onPrimaryContainer = Color(0xFFBFE8D2),
    secondaryContainer = Color(0xFF1F4A36),
    onSecondaryContainer = Color(0xFFBFE8D2),
    background = Color(0xFF121512),
    onBackground = Color(0xFFE3E6E1),
    surface = Color(0xFF1B1F1C),
    onSurface = Color(0xFFE3E6E1),
    surfaceVariant = Color(0xFF262B27),
    onSurfaceVariant = Color(0xFFB5BDB6),
    surfaceContainer = Color(0xFF1B1F1C),
    outline = Color(0xFF6E776F),
    outlineVariant = Color(0xFF353B36),
    error = Color(0xFFF2B8B5),
)

@Immutable
data class StatusColors(
    val warnBg: Color, val warnFg: Color,
    val infoBg: Color, val infoFg: Color,
    val dangerBg: Color, val dangerFg: Color,
    val neutralBg: Color, val neutralFg: Color,
    val okBg: Color, val okFg: Color,
)

private val LightStatus = StatusColors(
    warnBg = Color(0xFFFCEFD9), warnFg = Color(0xFF7A410B),
    infoBg = Color(0xFFE3ECF5), infoFg = Color(0xFF24527F),
    dangerBg = Color(0xFFFBE5E3), dangerFg = Color(0xFF9B2626),
    neutralBg = Color(0xFFECEEEA), neutralFg = Color(0xFF3F4842),
    okBg = Color(0xFFE3EFE7), okFg = Color(0xFF245A40),
)

private val DarkStatus = StatusColors(
    warnBg = Color(0xFF3D2A10), warnFg = Color(0xFFF5C98A),
    infoBg = Color(0xFF16283A), infoFg = Color(0xFFA9CBEB),
    dangerBg = Color(0xFF3D1A1A), dangerFg = Color(0xFFF2B8B5),
    neutralBg = Color(0xFF262B27), neutralFg = Color(0xFFC9CFC8),
    okBg = Color(0xFF1F4A36), okFg = Color(0xFFBFE8D2),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

/** Czcionka: docelowo Figtree w res/font (patrz ARCHITECTURE.md); na razie systemowa. */
private val AppFont = FontFamily.Default

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 29.sp),
    titleLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontFamily = AppFont, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = AppFont, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = AppFont, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    labelMedium = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.6.sp),
    labelSmall = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 11.5.sp),
)

@Composable
fun FoodAppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            content = content,
        )
    }
}
