package br.com.cavaleiros.mineradores.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Paleta "Matrix": preto absoluto e verde fosforescente. */
object MatrixColors {
    val Green = Color(0xFF00FF41)
    val GreenBright = Color(0xFFB6FFC8)
    val GreenDark = Color(0xFF008F11)
    val GreenDim = Color(0xFF003B00)
    val GreenGhost = Color(0xFF0A1F0E)
    val Black = Color(0xFF000000)
    val Panel = Color(0xFF040A06)
    val PanelAlt = Color(0xFF071209)
    val Red = Color(0xFFFF3B3B)
    val Amber = Color(0xFFFFB000)
}

private val MatrixColorScheme = darkColorScheme(
    primary = MatrixColors.Green,
    onPrimary = MatrixColors.Black,
    primaryContainer = MatrixColors.GreenDim,
    onPrimaryContainer = MatrixColors.Green,
    secondary = MatrixColors.GreenDark,
    onSecondary = MatrixColors.Black,
    secondaryContainer = MatrixColors.GreenGhost,
    onSecondaryContainer = MatrixColors.Green,
    tertiary = MatrixColors.Amber,
    onTertiary = MatrixColors.Black,
    background = MatrixColors.Black,
    onBackground = MatrixColors.Green,
    surface = MatrixColors.Panel,
    onSurface = MatrixColors.Green,
    surfaceVariant = MatrixColors.PanelAlt,
    onSurfaceVariant = MatrixColors.GreenDark,
    surfaceContainer = MatrixColors.Panel,
    surfaceContainerHigh = MatrixColors.PanelAlt,
    surfaceContainerHighest = MatrixColors.GreenGhost,
    error = MatrixColors.Red,
    onError = MatrixColors.Black,
    errorContainer = Color(0xFF2A0000),
    onErrorContainer = MatrixColors.Red,
    outline = MatrixColors.GreenDim,
    outlineVariant = MatrixColors.GreenGhost,
    scrim = MatrixColors.Black
)

private val Mono = FontFamily.Monospace

val MatrixTypography = Typography(
    displayLarge = Typography().displayLarge.copy(fontFamily = Mono),
    displayMedium = Typography().displayMedium.copy(fontFamily = Mono),
    displaySmall = Typography().displaySmall.copy(fontFamily = Mono),
    headlineLarge = Typography().headlineLarge.copy(fontFamily = Mono, fontWeight = FontWeight.Bold),
    headlineMedium = Typography().headlineMedium.copy(fontFamily = Mono, fontWeight = FontWeight.Bold),
    headlineSmall = Typography().headlineSmall.copy(fontFamily = Mono, fontWeight = FontWeight.Bold),
    titleLarge = Typography().titleLarge.copy(fontFamily = Mono, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
    titleMedium = Typography().titleMedium.copy(fontFamily = Mono, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
    titleSmall = Typography().titleSmall.copy(fontFamily = Mono, fontWeight = FontWeight.Bold),
    bodyLarge = Typography().bodyLarge.copy(fontFamily = Mono),
    bodyMedium = Typography().bodyMedium.copy(fontFamily = Mono),
    bodySmall = Typography().bodySmall.copy(fontFamily = Mono),
    labelLarge = Typography().labelLarge.copy(fontFamily = Mono, letterSpacing = 1.sp),
    labelMedium = Typography().labelMedium.copy(fontFamily = Mono, letterSpacing = 1.sp),
    labelSmall = Typography().labelSmall.copy(fontFamily = Mono, letterSpacing = 1.sp)
)

@Composable
fun CavaleirosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MatrixColorScheme,
        typography = MatrixTypography,
        content = content
    )
}
