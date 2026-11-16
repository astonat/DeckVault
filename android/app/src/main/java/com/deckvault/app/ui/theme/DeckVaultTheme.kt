package com.deckvault.app.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deckvault.app.model.AccentPalette

@Immutable
data class DeckSurfaceTokens(
    val background: Color,
    val panelSurface: Color,
    val cardSurface: Color,
    val elevatedSurface: Color,
    val highestSurface: Color,
    val hairlineBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val successMuted: Color,
    val warningMuted: Color,
    val dangerMuted: Color,
)

private val PureBlackTokens = DeckSurfaceTokens(
    background = Color(0xFF000000),
    panelSurface = Color(0xFF08080A),
    cardSurface = Color(0xFF101014),
    elevatedSurface = Color(0xFF18181E),
    highestSurface = Color(0xFF22222A),
    hairlineBorder = Color(0xFF26262F),
    textPrimary = Color(0xFFECECF1),
    textSecondary = Color(0xFF9C9CA8),
    successMuted = Color(0xFF8FB8A0),
    warningMuted = Color(0xFFD1BC95),
    dangerMuted = Color(0xFFCF8C92),
)

private val SoftMatteTokens = DeckSurfaceTokens(
    background = Color(0xFF0E0E12),
    panelSurface = Color(0xFF14151A),
    cardSurface = Color(0xFF1A1B21),
    elevatedSurface = Color(0xFF22232B),
    highestSurface = Color(0xFF2B2C36),
    hairlineBorder = Color(0xFF2E303A),
    textPrimary = Color(0xFFEDEDEF),
    textSecondary = Color(0xFFA0A1AC),
    successMuted = Color(0xFF8FB8A0),
    warningMuted = Color(0xFFD1BC95),
    dangerMuted = Color(0xFFCF8C92),
)

val LocalDeckSurfaces = staticCompositionLocalOf { PureBlackTokens }

/** Backwards-compatible accessor that reads matte Material tones. */
object OledColors {
    val PitchBlack: Color @Composable get() = LocalDeckSurfaces.current.background
    val PanelSurface: Color @Composable get() = LocalDeckSurfaces.current.panelSurface
    val CardSurface: Color @Composable get() = LocalDeckSurfaces.current.cardSurface
    val ElevatedSurface: Color @Composable get() = LocalDeckSurfaces.current.elevatedSurface
    val HighestSurface: Color @Composable get() = LocalDeckSurfaces.current.highestSurface
    val HairlineBorder: Color @Composable get() = LocalDeckSurfaces.current.hairlineBorder
    val TextPrimary: Color @Composable get() = LocalDeckSurfaces.current.textPrimary
    val TextSecondary: Color @Composable get() = LocalDeckSurfaces.current.textSecondary
    val SuccessGreen: Color @Composable get() = LocalDeckSurfaces.current.successMuted
    val WarningAmber: Color @Composable get() = LocalDeckSurfaces.current.warningMuted
    val DangerRed: Color @Composable get() = LocalDeckSurfaces.current.dangerMuted
}

private fun buildMatteColorScheme(
    surfaces: DeckSurfaceTokens,
    primary: Color,
    onPrimary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    secondary: Color,
    onSecondary: Color,
    secondaryContainer: Color,
    onSecondaryContainer: Color,
    tertiary: Color,
    onTertiary: Color,
    tertiaryContainer: Color,
    onTertiaryContainer: Color,
): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary,
    onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    background = surfaces.background,
    onBackground = surfaces.textPrimary,
    surface = surfaces.background,
    onSurface = surfaces.textPrimary,
    surfaceVariant = surfaces.elevatedSurface,
    onSurfaceVariant = surfaces.textSecondary,
    surfaceDim = surfaces.background,
    surfaceBright = surfaces.highestSurface,
    surfaceContainerLowest = surfaces.background,
    surfaceContainerLow = surfaces.panelSurface,
    surfaceContainer = surfaces.cardSurface,
    surfaceContainerHigh = surfaces.elevatedSurface,
    surfaceContainerHighest = surfaces.highestSurface,
    outline = Color(0xFF454652),
    outlineVariant = surfaces.hairlineBorder,
    error = surfaces.dangerMuted,
    onError = Color(0xFF281013),
    errorContainer = Color(0xFF3E1E22),
    onErrorContainer = Color(0xFFF3D5D8),
)

private fun paletteColorScheme(
    palette: AccentPalette,
    surfaces: DeckSurfaceTokens,
    dynamicBase: ColorScheme?,
): ColorScheme = when (palette) {
    AccentPalette.PLAIN_BLACK -> buildMatteColorScheme(
        surfaces = surfaces,
        primary = Color(0xFFE2E2E7),
        onPrimary = Color(0xFF141418),
        primaryContainer = Color(0xFF26262C),
        onPrimaryContainer = Color(0xFFF2F2F5),
        secondary = Color(0xFFB3B3BC),
        onSecondary = Color(0xFF17171C),
        secondaryContainer = Color(0xFF1E1E24),
        onSecondaryContainer = Color(0xFFDFDFE5),
        tertiary = Color(0xFFC8C8D0),
        onTertiary = Color(0xFF1A1A20),
        tertiaryContainer = Color(0xFF23232A),
        onTertiaryContainer = Color(0xFFE6E6EC),
    )
    AccentPalette.MATTE_SLATE -> buildMatteColorScheme(
        surfaces = surfaces,
        primary = Color(0xFFB9C3DE),
        onPrimary = Color(0xFF1C2233),
        primaryContainer = Color(0xFF293145),
        onPrimaryContainer = Color(0xFFE1E6F5),
        secondary = Color(0xFFA0BAC2),
        onSecondary = Color(0xFF15252B),
        secondaryContainer = Color(0xFF202E34),
        onSecondaryContainer = Color(0xFFD5E4E9),
        tertiary = Color(0xFFCBB6BF),
        onTertiary = Color(0xFF2E1E25),
        tertiaryContainer = Color(0xFF382930),
        onTertiaryContainer = Color(0xFFEBDEE3),
    )
    AccentPalette.MATTE_SAGE -> buildMatteColorScheme(
        surfaces = surfaces,
        primary = Color(0xFFA8C2B2),
        onPrimary = Color(0xFF13261B),
        primaryContainer = Color(0xFF213328),
        onPrimaryContainer = Color(0xFFD6E6DC),
        secondary = Color(0xFFACBDC7),
        onSecondary = Color(0xFF17252C),
        secondaryContainer = Color(0xFF212E35),
        onSecondaryContainer = Color(0xFFD8E3EA),
        tertiary = Color(0xFFC7BEA8),
        onTertiary = Color(0xFF292316),
        tertiaryContainer = Color(0xFF342E21),
        onTertiaryContainer = Color(0xFFE8E2D4),
    )
    AccentPalette.MATTE_CLAY -> buildMatteColorScheme(
        surfaces = surfaces,
        primary = Color(0xFFD1B3A7),
        onPrimary = Color(0xFF2F1D17),
        primaryContainer = Color(0xFF3B2922),
        onPrimaryContainer = Color(0xFFF0DDD6),
        secondary = Color(0xFFB5BCC7),
        onSecondary = Color(0xFF1B2028),
        secondaryContainer = Color(0xFF252A34),
        onSecondaryContainer = Color(0xFFDCE1E9),
        tertiary = Color(0xFFBEBFA7),
        onTertiary = Color(0xFF232417),
        tertiaryContainer = Color(0xFF2F3022),
        onTertiaryContainer = Color(0xFFE2E3D4),
    )
    AccentPalette.DYNAMIC_YOU -> {
        if (dynamicBase != null) {
            dynamicBase.copy(
                background = surfaces.background,
                surface = surfaces.background,
                surfaceDim = surfaces.background,
                surfaceContainerLowest = surfaces.background,
                surfaceContainerLow = surfaces.panelSurface,
                surfaceContainer = surfaces.cardSurface,
                surfaceContainerHigh = surfaces.elevatedSurface,
                surfaceContainerHighest = surfaces.highestSurface,
                outlineVariant = surfaces.hairlineBorder,
            )
        } else {
            paletteColorScheme(AccentPalette.MATTE_SLATE, surfaces, null)
        }
    }
}

val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

val ExpressiveTypography = Typography(
    displaySmall = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    headlineSmall = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 13.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 11.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 11.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 10.5.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DeckVaultTheme(
    accentPalette: AccentPalette = AccentPalette.MATTE_SLATE,
    pureBlackBackground: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val surfaces = if (pureBlackBackground) PureBlackTokens else SoftMatteTokens
    val dynamicBase = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && accentPalette == AccentPalette.DYNAMIC_YOU) {
        dynamicDarkColorScheme(context)
    } else {
        null
    }

    CompositionLocalProvider(LocalDeckSurfaces provides surfaces) {
        MaterialExpressiveTheme(
            colorScheme = paletteColorScheme(accentPalette, surfaces, dynamicBase),
            motionScheme = MotionScheme.expressive(),
            shapes = ExpressiveShapes,
            typography = ExpressiveTypography,
            content = content,
        )
    }
}
