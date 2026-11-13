package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.VelunaUserPrefs
import com.example.model.ThemeMode
import com.example.model.VelunaThemePreset

val OxaniumFontFamily = FontFamily(
    Font(R.font.oxanium, FontWeight.Normal),
    Font(R.font.oxanium, FontWeight.Medium),
    Font(R.font.oxanium, FontWeight.SemiBold),
    Font(R.font.oxanium, FontWeight.Bold)
)

val IbmPlexSansFontFamily = FontFamily(
    Font(R.font.ibm_plex_sans, FontWeight.Normal),
    Font(R.font.ibm_plex_sans, FontWeight.Medium),
    Font(R.font.ibm_plex_sans, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans, FontWeight.Bold)
)

val VelunaTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = OxaniumFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = OxaniumFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = OxaniumFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = OxaniumFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = IbmPlexSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = IbmPlexSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = IbmPlexSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = IbmPlexSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = IbmPlexSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = IbmPlexSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = IbmPlexSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = IbmPlexSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

fun parseHexColorOrNull(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    val cleaned = hex.trim().removePrefix("#")
    val value = cleaned.toLongOrNull(16) ?: return null
    return when (cleaned.length) {
        6 -> Color(0xFF000000L or value)
        8 -> Color(value)
        else -> null
    }
}

fun buildVelunaColorScheme(
    preset: VelunaThemePreset,
    darkTheme: Boolean,
    customAccentHex: String? = null,
    artworkAccent: Color? = null,
    adaptToArtwork: Boolean = false
): ColorScheme {
    val base = if (darkTheme) darkPresetScheme(preset) else lightPresetScheme(preset)
    val custom = parseHexColorOrNull(customAccentHex)
    val accent = when {
        custom != null -> custom
        adaptToArtwork && artworkAccent != null -> lerp(base.primary, artworkAccent, 0.55f)
        else -> null
    } ?: return base

    val onAccent = if (accent.luminance() > 0.45f) Color(0xFF12100E) else Color(0xFFFDFBF9)
    val accentContainer = if (darkTheme) lerp(base.surface, accent, 0.24f) else lerp(Color.White, accent, 0.16f)
    val onAccentContainer = if (darkTheme) lerp(accent, Color.White, 0.7f) else lerp(accent, Color.Black, 0.55f)

    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentContainer,
        onPrimaryContainer = onAccentContainer,
        secondary = lerp(base.secondary, accent, 0.35f),
        tertiary = lerp(base.tertiary, accent, 0.4f)
    )
}

private fun darkPresetScheme(preset: VelunaThemePreset): ColorScheme = when (preset) {
    VelunaThemePreset.DEFAULT -> darkColorScheme(
        primary = Color(0xFFEA580C),
        onPrimary = Color(0xFFFFF8F4),
        primaryContainer = Color(0xFF3E2012),
        onPrimaryContainer = Color(0xFFFFD8C4),
        secondary = Color(0xFFF59E0B),
        onSecondary = Color(0xFF1A1209),
        secondaryContainer = Color(0xFF2D231B),
        onSecondaryContainer = Color(0xFFF4EAE1),
        tertiary = Color(0xFFE07A5F),
        background = Color(0xFF120F0D),
        onBackground = Color(0xFFF4EAE1),
        surface = Color(0xFF181411),
        onSurface = Color(0xFFF4EAE1),
        surfaceVariant = Color(0xFF241E1A),
        onSurfaceVariant = Color(0xFFB5A89E),
        surfaceContainerLowest = Color(0xFF0E0B0A),
        surfaceContainerLow = Color(0xFF181411),
        surfaceContainer = Color(0xFF1F1916),
        surfaceContainerHigh = Color(0xFF27201C),
        surfaceContainerHighest = Color(0xFF312823),
        outline = Color(0xFF473C35),
        outlineVariant = Color(0xFF2D2520),
        error = Color(0xFFEF4444)
    )
    VelunaThemePreset.CATPPUCCIN -> darkColorScheme(
        primary = Color(0xFFCBA6F7),
        onPrimary = Color(0xFF1E1E2E),
        primaryContainer = Color(0xFF382C4C),
        onPrimaryContainer = Color(0xFFEDDFFE),
        secondary = Color(0xFFF5C2E7),
        onSecondary = Color(0xFF1E1E2E),
        secondaryContainer = Color(0xFF2E2E42),
        onSecondaryContainer = Color(0xFFCDD6F4),
        tertiary = Color(0xFF94E2D5),
        background = Color(0xFF1E1E2E),
        onBackground = Color(0xFFCDD6F4),
        surface = Color(0xFF242438),
        onSurface = Color(0xFFCDD6F4),
        surfaceVariant = Color(0xFF313244),
        onSurfaceVariant = Color(0xFFA6ADC8),
        surfaceContainer = Color(0xFF28283D),
        surfaceContainerHigh = Color(0xFF313244),
        outline = Color(0xFF45475A),
        outlineVariant = Color(0xFF313244),
        error = Color(0xFFF38BA8)
    )
    VelunaThemePreset.TOKYO_NIGHT -> darkColorScheme(
        primary = Color(0xFF7AA2F7),
        onPrimary = Color(0xFF0F1320),
        primaryContainer = Color(0xFF233254),
        onPrimaryContainer = Color(0xFFDCE6FF),
        secondary = Color(0xFFBB9AF7),
        onSecondary = Color(0xFF16161E),
        secondaryContainer = Color(0xFF24283B),
        onSecondaryContainer = Color(0xFFC0CAF5),
        tertiary = Color(0xFF7DCFFF),
        background = Color(0xFF1A1B26),
        onBackground = Color(0xFFC0CAF5),
        surface = Color(0xFF202230),
        onSurface = Color(0xFFC0CAF5),
        surfaceVariant = Color(0xFF292E42),
        onSurfaceVariant = Color(0xFF9AA5CE),
        surfaceContainer = Color(0xFF24283B),
        surfaceContainerHigh = Color(0xFF2E344D),
        outline = Color(0xFF414868),
        outlineVariant = Color(0xFF292E42),
        error = Color(0xFFF7768E)
    )
    VelunaThemePreset.CAFFEINE -> darkColorScheme(
        primary = Color(0xFFE09F5A),
        onPrimary = Color(0xFF1A120A),
        primaryContainer = Color(0xFF3B2816),
        onPrimaryContainer = Color(0xFFFBE1C2),
        secondary = Color(0xFFD4A373),
        onSecondary = Color(0xFF171311),
        secondaryContainer = Color(0xFF26221F),
        onSecondaryContainer = Color(0xFFEBE4DC),
        tertiary = Color(0xFFA98467),
        background = Color(0xFF11100F),
        onBackground = Color(0xFFEFEAE4),
        surface = Color(0xFF191715),
        onSurface = Color(0xFFEFEAE4),
        surfaceVariant = Color(0xFF25211E),
        onSurfaceVariant = Color(0xFFA89F95),
        surfaceContainer = Color(0xFF1E1B19),
        surfaceContainerHigh = Color(0xFF292521),
        outline = Color(0xFF403934),
        outlineVariant = Color(0xFF292521),
        error = Color(0xFFE06C75)
    )
    VelunaThemePreset.NEON -> darkColorScheme(
        primary = Color(0xFF00F5D4),
        onPrimary = Color(0xFF041C19),
        primaryContainer = Color(0xFF0B3A35),
        onPrimaryContainer = Color(0xFFB7FFF3),
        secondary = Color(0xFFFF3D7F),
        onSecondary = Color(0xFF220510),
        secondaryContainer = Color(0xFF1C1F33),
        onSecondaryContainer = Color(0xFFEAF0FF),
        tertiary = Color(0xFF9B5DE5),
        background = Color(0xFF090A10),
        onBackground = Color(0xFFEAF0FF),
        surface = Color(0xFF11131E),
        onSurface = Color(0xFFEAF0FF),
        surfaceVariant = Color(0xFF1C2032),
        onSurfaceVariant = Color(0xFF9BA4C4),
        surfaceContainer = Color(0xFF151827),
        surfaceContainerHigh = Color(0xFF1E2236),
        outline = Color(0xFF343B5C),
        outlineVariant = Color(0xFF1E2236),
        error = Color(0xFFFF4D6D)
    )
    VelunaThemePreset.BREEZE -> darkColorScheme(
        primary = Color(0xFF3DAEE9),
        onPrimary = Color(0xFF071924),
        primaryContainer = Color(0xFF183B52),
        onPrimaryContainer = Color(0xFFD2EEFD),
        secondary = Color(0xFF1CDC9A),
        onSecondary = Color(0xFF071F17),
        secondaryContainer = Color(0xFF232B34),
        onSecondaryContainer = Color(0xFFE6ECF2),
        tertiary = Color(0xFF9B59B6),
        background = Color(0xFF14191F),
        onBackground = Color(0xFFE6ECF2),
        surface = Color(0xFF1B222A),
        onSurface = Color(0xFFE6ECF2),
        surfaceVariant = Color(0xFF252E38),
        onSurfaceVariant = Color(0xFFA0AEB9),
        surfaceContainer = Color(0xFF1F2730),
        surfaceContainerHigh = Color(0xFF29333E),
        outline = Color(0xFF3E4B59),
        outlineVariant = Color(0xFF26303A),
        error = Color(0xFFDA4453)
    )
    VelunaThemePreset.AMOLED -> darkColorScheme(
        primary = Color(0xFFEA580C),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF33180B),
        onPrimaryContainer = Color(0xFFFFD8C4),
        secondary = Color(0xFFF59E0B),
        onSecondary = Color(0xFF000000),
        secondaryContainer = Color(0xFF16161A),
        onSecondaryContainer = Color(0xFFF4F4F6),
        tertiary = Color(0xFFFF7A33),
        background = Color(0xFF000000),
        onBackground = Color(0xFFF4F4F6),
        surface = Color(0xFF08080A),
        onSurface = Color(0xFFF4F4F6),
        surfaceVariant = Color(0xFF141418),
        onSurfaceVariant = Color(0xFFA1A1AA),
        surfaceContainer = Color(0xFF0E0E12),
        surfaceContainerHigh = Color(0xFF17171D),
        outline = Color(0xFF2D2D36),
        outlineVariant = Color(0xFF1A1A20),
        error = Color(0xFFEF4444)
    )
}

private fun lightPresetScheme(preset: VelunaThemePreset): ColorScheme = when (preset) {
    VelunaThemePreset.DEFAULT, VelunaThemePreset.AMOLED -> lightColorScheme(
        primary = Color(0xFFD9480F),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFE4D6),
        onPrimaryContainer = Color(0xFF4A1600),
        secondary = Color(0xFFC27803),
        secondaryContainer = Color(0xFFEDE4DC),
        background = Color(0xFFFAF6F2),
        onBackground = Color(0xFF1E1814),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1E1814),
        surfaceVariant = Color(0xFFF0E8E1),
        onSurfaceVariant = Color(0xFF6B5D53),
        surfaceContainer = Color(0xFFF4EDE7),
        outline = Color(0xFFD7CCC2),
        outlineVariant = Color(0xFFE9DFD7)
    )
    VelunaThemePreset.CATPPUCCIN -> lightColorScheme(
        primary = Color(0xFF8839EF),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFEBD9FD),
        background = Color(0xFFEFF1F5),
        onBackground = Color(0xFF4C4F69),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF4C4F69),
        surfaceVariant = Color(0xFFE6E9EF),
        onSurfaceVariant = Color(0xFF6C6F85),
        surfaceContainer = Color(0xFFE6E9EF),
        outline = Color(0xFFBCC0CC)
    )
    VelunaThemePreset.TOKYO_NIGHT -> lightColorScheme(
        primary = Color(0xFF34548A),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD6E2F7),
        background = Color(0xFFF0F1F5),
        onBackground = Color(0xFF24283B),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF24283B),
        surfaceVariant = Color(0xFFE1E2E7),
        onSurfaceVariant = Color(0xFF565F89),
        surfaceContainer = Color(0xFFE6E7ED),
        outline = Color(0xFFC4C6D1)
    )
    VelunaThemePreset.CAFFEINE -> lightColorScheme(
        primary = Color(0xFFA45D1D),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFF6E3CE),
        background = Color(0xFFFAF8F5),
        onBackground = Color(0xFF231C16),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF231C16),
        surfaceVariant = Color(0xFFEFE9E1),
        onSurfaceVariant = Color(0xFF6F6256),
        surfaceContainer = Color(0xFFF3EEE7),
        outline = Color(0xFFD9CFC4)
    )
    VelunaThemePreset.NEON -> lightColorScheme(
        primary = Color(0xFF008B7A),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFC7F7EF),
        secondary = Color(0xFFD61C60),
        background = Color(0xFFF4F7FB),
        onBackground = Color(0xFF11131E),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF11131E),
        surfaceVariant = Color(0xFFE5EBF4),
        onSurfaceVariant = Color(0xFF4B5470),
        surfaceContainer = Color(0xFFEBF0F7),
        outline = Color(0xFFCBD4E4)
    )
    VelunaThemePreset.BREEZE -> lightColorScheme(
        primary = Color(0xFF1D78B4),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD3ECFC),
        background = Color(0xFFEFF3F7),
        onBackground = Color(0xFF1C232B),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1C232B),
        surfaceVariant = Color(0xFFE2E8F0),
        onSurfaceVariant = Color(0xFF52606E),
        surfaceContainer = Color(0xFFE7EDF4),
        outline = Color(0xFFC6D1DC)
    )
}

@Composable
fun VelunaTheme(
    prefs: VelunaUserPrefs,
    artworkAccent: Color? = null,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (prefs.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }
    val colorScheme = buildVelunaColorScheme(
        preset = prefs.themePreset,
        darkTheme = useDark,
        customAccentHex = prefs.customAccentHex,
        artworkAccent = artworkAccent,
        adaptToArtwork = prefs.adaptToArtwork
    )
    val r = prefs.cornerRadiusDp.dp
    val shapes = Shapes(
        extraSmall = RoundedCornerShape((r * 0.4f).coerceAtLeast(4.dp)),
        small = RoundedCornerShape((r * 0.7f).coerceAtLeast(6.dp)),
        medium = RoundedCornerShape(r),
        large = RoundedCornerShape((r * 1.4f).coerceAtMost(28.dp)),
        extraLarge = RoundedCornerShape((r * 1.8f).coerceAtMost(32.dp))
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VelunaTypography,
        shapes = shapes,
        content = content
    )
}
