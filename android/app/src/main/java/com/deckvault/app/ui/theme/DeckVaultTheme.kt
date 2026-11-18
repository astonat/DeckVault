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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deckvault.app.model.AccentPalette
import com.deckvault.app.model.ThemeSurfaceStyle

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

@Immutable
private data class PalettePresetSpec(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val tonalBg: Color,
    val tonalPanel: Color,
    val tonalCard: Color,
    val tonalElevated: Color,
    val tonalHighest: Color,
    val tonalBorder: Color,
    val textPrimary: Color = Color(0xFFF0F2F8),
    val textSecondary: Color = Color(0xFFA2A8BC),
)

private val PureBlackFallbackTokens = DeckSurfaceTokens(
    background = Color(0xFF000000),
    panelSurface = Color(0xFF0A0A0D),
    cardSurface = Color(0xFF121218),
    elevatedSurface = Color(0xFF1A1B23),
    highestSurface = Color(0xFF242530),
    hairlineBorder = Color(0xFF2A2B36),
    textPrimary = Color(0xFFECECF1),
    textSecondary = Color(0xFF9C9CA8),
    successMuted = Color(0xFF7AD6A2),
    warningMuted = Color(0xFFF0C674),
    dangerMuted = Color(0xFFF28B96),
)

val LocalDeckSurfaces = staticCompositionLocalOf { PureBlackFallbackTokens }

/** Backwards-compatible accessor that reads current Material/OLED surface tokens. */
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

private fun presetSpecFor(palette: AccentPalette): PalettePresetSpec = when (palette) {
    AccentPalette.COBALT -> PalettePresetSpec(
        primary = Color(0xFF60A5FA),
        onPrimary = Color(0xFF051C3D),
        primaryContainer = Color(0xFF1A438A),
        onPrimaryContainer = Color(0xFFDBEAFE),
        secondary = Color(0xFF38BDF8),
        onSecondary = Color(0xFF042233),
        secondaryContainer = Color(0xFF123B59),
        onSecondaryContainer = Color(0xFFD6F0FF),
        tertiary = Color(0xFFA5B4FC),
        onTertiary = Color(0xFF1A1F4B),
        tertiaryContainer = Color(0xFF2B316E),
        onTertiaryContainer = Color(0xFFE6EAFF),
        tonalBg = Color(0xFF091324),
        tonalPanel = Color(0xFF0F1D35),
        tonalCard = Color(0xFF142544),
        tonalElevated = Color(0xFF1B3157),
        tonalHighest = Color(0xFF243F6E),
        tonalBorder = Color(0xFF2B4B80),
        textPrimary = Color(0xFFEFF6FF),
        textSecondary = Color(0xFF93B7EA),
    )
    AccentPalette.EMERALD -> PalettePresetSpec(
        primary = Color(0xFF34D399),
        onPrimary = Color(0xFF02291A),
        primaryContainer = Color(0xFF0A5C41),
        onPrimaryContainer = Color(0xFFD1FAE5),
        secondary = Color(0xFF2DD4BF),
        onSecondary = Color(0xFF032924),
        secondaryContainer = Color(0xFF0F4C45),
        onSecondaryContainer = Color(0xFFCCFBF1),
        tertiary = Color(0xFFA7F3D0),
        onTertiary = Color(0xFF082E1E),
        tertiaryContainer = Color(0xFF184A35),
        onTertiaryContainer = Color(0xFFE6FFF2),
        tonalBg = Color(0xFF071712),
        tonalPanel = Color(0xFF0C221A),
        tonalCard = Color(0xFF112E24),
        tonalElevated = Color(0xFF173C2F),
        tonalHighest = Color(0xFF1E4D3C),
        tonalBorder = Color(0xFF25614C),
        textPrimary = Color(0xFFECFDF5),
        textSecondary = Color(0xFF8CD9B5),
    )
    AccentPalette.AMETHYST -> PalettePresetSpec(
        primary = Color(0xFFC084FC),
        onPrimary = Color(0xFF2B094F),
        primaryContainer = Color(0xFF561FA6),
        onPrimaryContainer = Color(0xFFF3E8FF),
        secondary = Color(0xFFF472B6),
        onSecondary = Color(0xFF3B0825),
        secondaryContainer = Color(0xFF661A45),
        onSecondaryContainer = Color(0xFFFCE7F3),
        tertiary = Color(0xFF93C5FD),
        onTertiary = Color(0xFF0C2245),
        tertiaryContainer = Color(0xFF1E3766),
        onTertiaryContainer = Color(0xFFDBEAFE),
        tonalBg = Color(0xFF120C20),
        tonalPanel = Color(0xFF1B132E),
        tonalCard = Color(0xFF23183C),
        tonalElevated = Color(0xFF2E204E),
        tonalHighest = Color(0xFF3B2963),
        tonalBorder = Color(0xFF4A347B),
        textPrimary = Color(0xFFF5F3FF),
        textSecondary = Color(0xFFC4B5FD),
    )
    AccentPalette.CRIMSON -> PalettePresetSpec(
        primary = Color(0xFFFB7185),
        onPrimary = Color(0xFF450614),
        primaryContainer = Color(0xFF9F1239),
        onPrimaryContainer = Color(0xFFFFE4E6),
        secondary = Color(0xFFFBBF24),
        onSecondary = Color(0xFF382002),
        secondaryContainer = Color(0xFF633A08),
        onSecondaryContainer = Color(0xFFFEF3C7),
        tertiary = Color(0xFFFDA4AF),
        onTertiary = Color(0xFF450E19),
        tertiaryContainer = Color(0xFF661A29),
        onTertiaryContainer = Color(0xFFFFF1F2),
        tonalBg = Color(0xFF1B0A10),
        tonalPanel = Color(0xFF280F17),
        tonalCard = Color(0xFF33141D),
        tonalElevated = Color(0xFF421A26),
        tonalHighest = Color(0xFF542231),
        tonalBorder = Color(0xFF6A2A3D),
        textPrimary = Color(0xFFFFF1F2),
        textSecondary = Color(0xFFF9A8B8),
    )
    AccentPalette.AMBER -> PalettePresetSpec(
        primary = Color(0xFFFBBF24),
        onPrimary = Color(0xFF362002),
        primaryContainer = Color(0xFF8C4808),
        onPrimaryContainer = Color(0xFFFEF3C7),
        secondary = Color(0xFFFB923C),
        onSecondary = Color(0xFF3B1904),
        secondaryContainer = Color(0xFF6E2F0A),
        onSecondaryContainer = Color(0xFFFFEDD5),
        tertiary = Color(0xFFFDE68A),
        onTertiary = Color(0xFF332904),
        tertiaryContainer = Color(0xFF52410C),
        onTertiaryContainer = Color(0xFFFEF9C3),
        tonalBg = Color(0xFF191207),
        tonalPanel = Color(0xFF241A0B),
        tonalCard = Color(0xFF2E220E),
        tonalElevated = Color(0xFF3C2C13),
        tonalHighest = Color(0xFF4C3818),
        tonalBorder = Color(0xFF63481F),
        textPrimary = Color(0xFFFFFBEB),
        textSecondary = Color(0xFFF6C768),
    )
    AccentPalette.CYBER_TEAL -> PalettePresetSpec(
        primary = Color(0xFF2DD4BF),
        onPrimary = Color(0xFF022923),
        primaryContainer = Color(0xFF0D5E57),
        onPrimaryContainer = Color(0xFFCCFBF1),
        secondary = Color(0xFFF472B6),
        onSecondary = Color(0xFF3B0825),
        secondaryContainer = Color(0xFF661A45),
        onSecondaryContainer = Color(0xFFFCE7F3),
        tertiary = Color(0xFF38BDF8),
        onTertiary = Color(0xFF032333),
        tertiaryContainer = Color(0xFF0C3E57),
        onTertiaryContainer = Color(0xFFE0F2FE),
        tonalBg = Color(0xFF071619),
        tonalPanel = Color(0xFF0D2024),
        tonalCard = Color(0xFF122B30),
        tonalElevated = Color(0xFF18393F),
        tonalHighest = Color(0xFF204A52),
        tonalBorder = Color(0xFF265A64),
        textPrimary = Color(0xFFF0FDFA),
        textSecondary = Color(0xFF7BE3D4),
    )
    AccentPalette.MATTE_SLATE -> PalettePresetSpec(
        primary = Color(0xFFB9C3DE),
        onPrimary = Color(0xFF1C2233),
        primaryContainer = Color(0xFF2B344B),
        onPrimaryContainer = Color(0xFFE1E6F5),
        secondary = Color(0xFFA0BAC2),
        onSecondary = Color(0xFF15252B),
        secondaryContainer = Color(0xFF202E34),
        onSecondaryContainer = Color(0xFFD5E4E9),
        tertiary = Color(0xFFCBB6BF),
        onTertiary = Color(0xFF2E1E25),
        tertiaryContainer = Color(0xFF382930),
        onTertiaryContainer = Color(0xFFEBDEE3),
        tonalBg = Color(0xFF0D1017),
        tonalPanel = Color(0xFF141822),
        tonalCard = Color(0xFF1A1F2C),
        tonalElevated = Color(0xFF222838),
        tonalHighest = Color(0xFF2B3245),
        tonalBorder = Color(0xFF353D54),
        textPrimary = Color(0xFFEDF0F7),
        textSecondary = Color(0xFF9EA6BA),
    )
    AccentPalette.MATTE_SAGE -> PalettePresetSpec(
        primary = Color(0xFFA8C2B2),
        onPrimary = Color(0xFF13261B),
        primaryContainer = Color(0xFF23382C),
        onPrimaryContainer = Color(0xFFD6E6DC),
        secondary = Color(0xFFACBDC7),
        onSecondary = Color(0xFF17252C),
        secondaryContainer = Color(0xFF212E35),
        onSecondaryContainer = Color(0xFFD8E3EA),
        tertiary = Color(0xFFC7BEA8),
        onTertiary = Color(0xFF292316),
        tertiaryContainer = Color(0xFF342E21),
        onTertiaryContainer = Color(0xFFE8E2D4),
        tonalBg = Color(0xFF0C120E),
        tonalPanel = Color(0xFF131B16),
        tonalCard = Color(0xFF19231D),
        tonalElevated = Color(0xFF202D25),
        tonalHighest = Color(0xFF28382F),
        tonalBorder = Color(0xFF31443A),
        textPrimary = Color(0xFFEDF2EE),
        textSecondary = Color(0xFF9BAEA1),
    )
    AccentPalette.MATTE_CLAY -> PalettePresetSpec(
        primary = Color(0xFFD1B3A7),
        onPrimary = Color(0xFF2F1D17),
        primaryContainer = Color(0xFF3F2B23),
        onPrimaryContainer = Color(0xFFF0DDD6),
        secondary = Color(0xFFB5BCC7),
        onSecondary = Color(0xFF1B2028),
        secondaryContainer = Color(0xFF252A34),
        onSecondaryContainer = Color(0xFFDCE1E9),
        tertiary = Color(0xFFBEBFA7),
        onTertiary = Color(0xFF232417),
        tertiaryContainer = Color(0xFF2F3022),
        onTertiaryContainer = Color(0xFFE2E3D4),
        tonalBg = Color(0xFF130E0D),
        tonalPanel = Color(0xFF1B1513),
        tonalCard = Color(0xFF231B18),
        tonalElevated = Color(0xFF2D231F),
        tonalHighest = Color(0xFF382C27),
        tonalBorder = Color(0xFF473731),
        textPrimary = Color(0xFFF3EFEA),
        textSecondary = Color(0xFFB3A29B),
    )
    AccentPalette.PLAIN_BLACK,
    AccentPalette.DYNAMIC_YOU,
    -> PalettePresetSpec(
        primary = Color(0xFFE2E2E7),
        onPrimary = Color(0xFF141418),
        primaryContainer = Color(0xFF282830),
        onPrimaryContainer = Color(0xFFF2F2F5),
        secondary = Color(0xFFB3B3BC),
        onSecondary = Color(0xFF17171C),
        secondaryContainer = Color(0xFF1F1F26),
        onSecondaryContainer = Color(0xFFDFDFE5),
        tertiary = Color(0xFFC8C8D0),
        onTertiary = Color(0xFF1A1A20),
        tertiaryContainer = Color(0xFF24242C),
        onTertiaryContainer = Color(0xFFE6E6EC),
        tonalBg = Color(0xFF0E0E12),
        tonalPanel = Color(0xFF14151A),
        tonalCard = Color(0xFF1A1B21),
        tonalElevated = Color(0xFF22232B),
        tonalHighest = Color(0xFF2B2C36),
        tonalBorder = Color(0xFF2E303A),
        textPrimary = Color(0xFFEDEDEF),
        textSecondary = Color(0xFFA0A1AC),
    )
}

private fun surfacesForSpec(
    spec: PalettePresetSpec,
    surfaceStyle: ThemeSurfaceStyle,
): DeckSurfaceTokens = when (surfaceStyle) {
    ThemeSurfaceStyle.TONAL_DARK -> DeckSurfaceTokens(
        background = spec.tonalBg,
        panelSurface = spec.tonalPanel,
        cardSurface = spec.tonalCard,
        elevatedSurface = spec.tonalElevated,
        highestSurface = spec.tonalHighest,
        hairlineBorder = spec.tonalBorder,
        textPrimary = spec.textPrimary,
        textSecondary = spec.textSecondary,
        successMuted = Color(0xFF7AD6A2),
        warningMuted = Color(0xFFF0C674),
        dangerMuted = Color(0xFFF28B96),
    )
    ThemeSurfaceStyle.HYBRID_OLED -> DeckSurfaceTokens(
        background = Color(0xFF000000),
        panelSurface = lerp(Color(0xFF000000), spec.tonalPanel, 0.72f),
        cardSurface = spec.tonalCard,
        elevatedSurface = spec.tonalElevated,
        highestSurface = spec.tonalHighest,
        hairlineBorder = lerp(spec.tonalBorder, spec.primary, 0.24f),
        textPrimary = spec.textPrimary,
        textSecondary = spec.textSecondary,
        successMuted = Color(0xFF7AD6A2),
        warningMuted = Color(0xFFF0C674),
        dangerMuted = Color(0xFFF28B96),
    )
    ThemeSurfaceStyle.OLED_NEON -> DeckSurfaceTokens(
        background = Color(0xFF000000),
        panelSurface = lerp(Color(0xFF000000), spec.tonalPanel, 0.45f),
        cardSurface = lerp(Color(0xFF000000), spec.tonalCard, 0.62f),
        elevatedSurface = lerp(Color(0xFF000000), spec.tonalElevated, 0.72f),
        highestSurface = lerp(Color(0xFF000000), spec.tonalHighest, 0.80f),
        hairlineBorder = lerp(spec.tonalBorder, spec.primary, 0.56f),
        textPrimary = spec.textPrimary,
        textSecondary = lerp(spec.textSecondary, spec.primary, 0.14f),
        successMuted = Color(0xFF5EE89A),
        warningMuted = Color(0xFFFBBF24),
        dangerMuted = Color(0xFFFB7185),
    )
}

private fun buildThemeTokensAndScheme(
    palette: AccentPalette,
    surfaceStyle: ThemeSurfaceStyle,
    dynamicBase: ColorScheme?,
): Pair<DeckSurfaceTokens, ColorScheme> {
    if (palette == AccentPalette.DYNAMIC_YOU) {
        if (dynamicBase != null) {
            val dynamicSurfaces = when (surfaceStyle) {
                ThemeSurfaceStyle.TONAL_DARK -> DeckSurfaceTokens(
                    background = dynamicBase.background,
                    panelSurface = dynamicBase.surfaceContainerLow,
                    cardSurface = dynamicBase.surfaceContainer,
                    elevatedSurface = dynamicBase.surfaceContainerHigh,
                    highestSurface = dynamicBase.surfaceContainerHighest,
                    hairlineBorder = dynamicBase.outlineVariant,
                    textPrimary = dynamicBase.onBackground,
                    textSecondary = dynamicBase.onSurfaceVariant,
                    successMuted = Color(0xFF7AD6A2),
                    warningMuted = Color(0xFFF0C674),
                    dangerMuted = dynamicBase.error,
                )
                ThemeSurfaceStyle.HYBRID_OLED -> DeckSurfaceTokens(
                    background = Color(0xFF000000),
                    panelSurface = lerp(Color(0xFF000000), dynamicBase.surfaceContainerLow, 0.78f),
                    cardSurface = dynamicBase.surfaceContainer,
                    elevatedSurface = dynamicBase.surfaceContainerHigh,
                    highestSurface = dynamicBase.surfaceContainerHighest,
                    hairlineBorder = lerp(dynamicBase.outlineVariant, dynamicBase.primary, 0.24f),
                    textPrimary = dynamicBase.onBackground,
                    textSecondary = dynamicBase.onSurfaceVariant,
                    successMuted = Color(0xFF7AD6A2),
                    warningMuted = Color(0xFFF0C674),
                    dangerMuted = dynamicBase.error,
                )
                ThemeSurfaceStyle.OLED_NEON -> DeckSurfaceTokens(
                    background = Color(0xFF000000),
                    panelSurface = lerp(Color(0xFF000000), dynamicBase.surfaceContainerLow, 0.45f),
                    cardSurface = lerp(Color(0xFF000000), dynamicBase.surfaceContainer, 0.65f),
                    elevatedSurface = lerp(Color(0xFF000000), dynamicBase.surfaceContainerHigh, 0.74f),
                    highestSurface = lerp(Color(0xFF000000), dynamicBase.surfaceContainerHighest, 0.82f),
                    hairlineBorder = lerp(dynamicBase.outlineVariant, dynamicBase.primary, 0.58f),
                    textPrimary = dynamicBase.onBackground,
                    textSecondary = lerp(dynamicBase.onSurfaceVariant, dynamicBase.primary, 0.14f),
                    successMuted = Color(0xFF5EE89A),
                    warningMuted = Color(0xFFFBBF24),
                    dangerMuted = dynamicBase.error,
                )
            }
            val dynamicScheme = dynamicBase.copy(
                background = dynamicSurfaces.background,
                surface = dynamicSurfaces.background,
                surfaceDim = dynamicSurfaces.background,
                surfaceBright = dynamicSurfaces.highestSurface,
                surfaceContainerLowest = dynamicSurfaces.background,
                surfaceContainerLow = dynamicSurfaces.panelSurface,
                surfaceContainer = dynamicSurfaces.cardSurface,
                surfaceContainerHigh = dynamicSurfaces.elevatedSurface,
                surfaceContainerHighest = dynamicSurfaces.highestSurface,
                outlineVariant = dynamicSurfaces.hairlineBorder,
            )
            return dynamicSurfaces to dynamicScheme
        }
        return buildThemeTokensAndScheme(AccentPalette.COBALT, surfaceStyle, null)
    }

    val spec = presetSpecFor(palette)
    val surfaces = surfacesForSpec(spec, surfaceStyle)
    val scheme = darkColorScheme(
        primary = if (surfaceStyle == ThemeSurfaceStyle.OLED_NEON && palette != AccentPalette.PLAIN_BLACK) {
            lerp(spec.primary, Color.White, 0.10f)
        } else {
            spec.primary
        },
        onPrimary = spec.onPrimary,
        primaryContainer = spec.primaryContainer,
        onPrimaryContainer = spec.onPrimaryContainer,
        secondary = spec.secondary,
        onSecondary = spec.onSecondary,
        secondaryContainer = spec.secondaryContainer,
        onSecondaryContainer = spec.onSecondaryContainer,
        tertiary = spec.tertiary,
        onTertiary = spec.onTertiary,
        tertiaryContainer = spec.tertiaryContainer,
        onTertiaryContainer = spec.onTertiaryContainer,
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
        outline = lerp(surfaces.hairlineBorder, spec.primary, 0.25f),
        outlineVariant = surfaces.hairlineBorder,
        error = surfaces.dangerMuted,
        onError = Color(0xFF281013),
        errorContainer = Color(0xFF3E1E22),
        onErrorContainer = Color(0xFFF3D5D8),
    )
    return surfaces to scheme
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
    accentPalette: AccentPalette = AccentPalette.DYNAMIC_YOU,
    pureBlackBackground: Boolean = true,
    surfaceStyle: ThemeSurfaceStyle = if (pureBlackBackground) ThemeSurfaceStyle.HYBRID_OLED else ThemeSurfaceStyle.TONAL_DARK,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dynamicBase = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && accentPalette == AccentPalette.DYNAMIC_YOU) {
        dynamicDarkColorScheme(context)
    } else {
        null
    }

    val (surfaces, colorScheme) = buildThemeTokensAndScheme(
        palette = accentPalette,
        surfaceStyle = surfaceStyle,
        dynamicBase = dynamicBase,
    )

    CompositionLocalProvider(LocalDeckSurfaces provides surfaces) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = ExpressiveShapes,
            typography = ExpressiveTypography,
            content = content,
        )
    }
}
