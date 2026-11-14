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

fun contrastRatio(a: Color, b: Color): Float {
    val l1 = a.luminance()
    val l2 = b.luminance()
    val lighter = maxOf(l1, l2)
    val darker = minOf(l1, l2)
    return (lighter + 0.05f) / (darker + 0.05f)
}

fun bestOnColor(background: Color): Color {
    val darkInk = Color(0xFF111114)
    val lightInk = Color(0xFFFFFFFF)
    return if (contrastRatio(lightInk, background) >= contrastRatio(darkInk, background)) lightInk else darkInk
}

fun ensureContrast(foreground: Color, background: Color, minRatio: Float): Color {
    if (contrastRatio(foreground, background) >= minRatio) return foreground
    val target = if (background.luminance() < 0.45f) Color.White else Color(0xFF0A0A0D)
    if (contrastRatio(target, background) < minRatio) {
        return bestOnColor(background)
    }
    var low = 0f
    var high = 1f
    repeat(16) {
        val mid = (low + high) * 0.5f
        val candidate = lerp(foreground, target, mid)
        if (contrastRatio(candidate, background) >= minRatio) {
            high = mid
        } else {
            low = mid
        }
    }
    return lerp(foreground, target, high)
}

private fun enforceWcagContrast(scheme: ColorScheme): ColorScheme {
    val safePrimary = ensureContrast(
        ensureContrast(scheme.primary, scheme.surface, 4.5f),
        scheme.surfaceContainer,
        4.5f
    )
    val safeOnPrimary = ensureContrast(scheme.onPrimary, safePrimary, 4.6f)
    val safeOnPrimaryContainer = ensureContrast(scheme.onPrimaryContainer, scheme.primaryContainer, 4.6f)

    val safeSecondary = ensureContrast(
        ensureContrast(scheme.secondary, scheme.surface, 4.5f),
        scheme.surfaceContainer,
        4.5f
    )
    val safeOnSecondary = ensureContrast(scheme.onSecondary, safeSecondary, 4.6f)
    val safeOnSecondaryContainer = ensureContrast(scheme.onSecondaryContainer, scheme.secondaryContainer, 4.6f)

    val safeTertiary = ensureContrast(scheme.tertiary, scheme.surface, 4.5f)
    val safeOnTertiary = ensureContrast(scheme.onTertiary, safeTertiary, 4.6f)

    val safeOnBackground = ensureContrast(scheme.onBackground, scheme.background, 7.0f)
    val safeOnSurface = ensureContrast(
        ensureContrast(scheme.onSurface, scheme.surface, 7.0f),
        scheme.surfaceContainerHigh,
        6.5f
    )
    val safeOnSurfaceVariant = ensureContrast(
        ensureContrast(scheme.onSurfaceVariant, scheme.surface, 4.6f),
        scheme.surfaceContainerHigh,
        4.5f
    )
    val safeOutline = ensureContrast(scheme.outline, scheme.surfaceContainer, 2.4f)
    val safeOutlineVariant = ensureContrast(scheme.outlineVariant, scheme.surfaceContainer, 1.45f)
    val safeError = ensureContrast(scheme.error, scheme.surfaceContainer, 4.6f)
    val safeOnError = ensureContrast(scheme.onError, safeError, 4.6f)

    return scheme.copy(
        primary = safePrimary,
        onPrimary = safeOnPrimary,
        onPrimaryContainer = safeOnPrimaryContainer,
        secondary = safeSecondary,
        onSecondary = safeOnSecondary,
        onSecondaryContainer = safeOnSecondaryContainer,
        tertiary = safeTertiary,
        onTertiary = safeOnTertiary,
        onBackground = safeOnBackground,
        onSurface = safeOnSurface,
        onSurfaceVariant = safeOnSurfaceVariant,
        outline = safeOutline,
        outlineVariant = safeOutlineVariant,
        error = safeError,
        onError = safeOnError
    )
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
    } ?: return enforceWcagContrast(base)

    val safeAccent = ensureContrast(
        ensureContrast(accent, base.surface, 4.5f),
        base.surfaceContainer,
        4.5f
    )
    val onAccent = bestOnColor(safeAccent)
    val accentContainer = if (darkTheme) lerp(base.surfaceContainerHigh, safeAccent, 0.28f) else lerp(Color.White, safeAccent, 0.16f)
    val onAccentContainer = if (darkTheme) lerp(safeAccent, Color.White, 0.72f) else lerp(safeAccent, Color.Black, 0.58f)

    return enforceWcagContrast(
        base.copy(
            primary = safeAccent,
            onPrimary = onAccent,
            primaryContainer = accentContainer,
            onPrimaryContainer = onAccentContainer,
            secondary = lerp(base.secondary, safeAccent, 0.35f),
            tertiary = lerp(base.tertiary, safeAccent, 0.4f)
        )
    )
}

private fun darkPresetScheme(preset: VelunaThemePreset): ColorScheme = when (preset) {
    VelunaThemePreset.DEFAULT -> darkColorScheme(
        primary = Color(0xFFFB7228),
        onPrimary = Color(0xFF1F0C03),
        primaryContainer = Color(0xFF472312),
        onPrimaryContainer = Color(0xFFFFDFCD),
        secondary = Color(0xFFF59E0B),
        onSecondary = Color(0xFF1A1209),
        secondaryContainer = Color(0xFF33261C),
        onSecondaryContainer = Color(0xFFF7ECE3),
        tertiary = Color(0xFFE98A6F),
        onTertiary = Color(0xFF230E08),
        background = Color(0xFF120F0D),
        onBackground = Color(0xFFF6EDE6),
        surface = Color(0xFF181411),
        onSurface = Color(0xFFF6EDE6),
        surfaceVariant = Color(0xFF27201C),
        onSurfaceVariant = Color(0xFFCBBDB2),
        surfaceContainerLowest = Color(0xFF0E0B0A),
        surfaceContainerLow = Color(0xFF1A1512),
        surfaceContainer = Color(0xFF221B18),
        surfaceContainerHigh = Color(0xFF2B231E),
        surfaceContainerHighest = Color(0xFF362C27),
        outline = Color(0xFF6A5B51),
        outlineVariant = Color(0xFF3C322C),
        error = Color(0xFFFF6B6B),
        onError = Color(0xFF2A0505)
    )
    VelunaThemePreset.CATPPUCCIN -> darkColorScheme(
        primary = Color(0xFFCBA6F7),
        onPrimary = Color(0xFF1E1E2E),
        primaryContainer = Color(0xFF3D3054),
        onPrimaryContainer = Color(0xFFF1E5FF),
        secondary = Color(0xFFF5C2E7),
        onSecondary = Color(0xFF1E1E2E),
        secondaryContainer = Color(0xFF313247),
        onSecondaryContainer = Color(0xFFDCE3FA),
        tertiary = Color(0xFF94E2D5),
        onTertiary = Color(0xFF0C221E),
        background = Color(0xFF1E1E2E),
        onBackground = Color(0xFFDCE3FA),
        surface = Color(0xFF242438),
        onSurface = Color(0xFFDCE3FA),
        surfaceVariant = Color(0xFF313244),
        onSurfaceVariant = Color(0xFFB8C0DE),
        surfaceContainerLowest = Color(0xFF181825),
        surfaceContainerLow = Color(0xFF222235),
        surfaceContainer = Color(0xFF29293F),
        surfaceContainerHigh = Color(0xFF33344A),
        surfaceContainerHighest = Color(0xFF3D3E58),
        outline = Color(0xFF646782),
        outlineVariant = Color(0xFF3D3F54),
        error = Color(0xFFF38BA8),
        onError = Color(0xFF2B0914)
    )
    VelunaThemePreset.TOKYO_NIGHT -> darkColorScheme(
        primary = Color(0xFF7AA2F7),
        onPrimary = Color(0xFF0F1320),
        primaryContainer = Color(0xFF26375C),
        onPrimaryContainer = Color(0xFFE0E9FF),
        secondary = Color(0xFFBB9AF7),
        onSecondary = Color(0xFF16161E),
        secondaryContainer = Color(0xFF282D44),
        onSecondaryContainer = Color(0xFFD5DDFC),
        tertiary = Color(0xFF7DCFFF),
        onTertiary = Color(0xFF091B26),
        background = Color(0xFF1A1B26),
        onBackground = Color(0xFFD5DDFC),
        surface = Color(0xFF202230),
        onSurface = Color(0xFFD5DDFC),
        surfaceVariant = Color(0xFF2A2F44),
        onSurfaceVariant = Color(0xFFAEB9DF),
        surfaceContainerLowest = Color(0xFF14151E),
        surfaceContainerLow = Color(0xFF1F2231),
        surfaceContainer = Color(0xFF25293C),
        surfaceContainerHigh = Color(0xFF2F354F),
        surfaceContainerHighest = Color(0xFF3A4161),
        outline = Color(0xFF5D668F),
        outlineVariant = Color(0xFF353C57),
        error = Color(0xFFF7768E),
        onError = Color(0xFF2C0810)
    )
    VelunaThemePreset.CAFFEINE -> darkColorScheme(
        primary = Color(0xFFE4A562),
        onPrimary = Color(0xFF1A120A),
        primaryContainer = Color(0xFF422C18),
        onPrimaryContainer = Color(0xFFFCE5CB),
        secondary = Color(0xFFD8AA7B),
        onSecondary = Color(0xFF171311),
        secondaryContainer = Color(0xFF2B2622),
        onSecondaryContainer = Color(0xFFF0EAE2),
        tertiary = Color(0xFFC29B7B),
        onTertiary = Color(0xFF1E140C),
        background = Color(0xFF11100F),
        onBackground = Color(0xFFF2EDE7),
        surface = Color(0xFF191715),
        onSurface = Color(0xFFF2EDE7),
        surfaceVariant = Color(0xFF26221E),
        onSurfaceVariant = Color(0xFFC0B6AA),
        surfaceContainerLowest = Color(0xFF0C0B0A),
        surfaceContainerLow = Color(0xFF1B1917),
        surfaceContainer = Color(0xFF211E1B),
        surfaceContainerHigh = Color(0xFF2C2723),
        surfaceContainerHighest = Color(0xFF37312C),
        outline = Color(0xFF61574F),
        outlineVariant = Color(0xFF38322D),
        error = Color(0xFFEC7A83),
        onError = Color(0xFF2B080B)
    )
    VelunaThemePreset.NEON -> darkColorScheme(
        primary = Color(0xFF00F5D4),
        onPrimary = Color(0xFF041C19),
        primaryContainer = Color(0xFF0D443D),
        onPrimaryContainer = Color(0xFFC2FFF5),
        secondary = Color(0xFFFF5C93),
        onSecondary = Color(0xFF220510),
        secondaryContainer = Color(0xFF22263F),
        onSecondaryContainer = Color(0xFFEAF0FF),
        tertiary = Color(0xFFB380FF),
        onTertiary = Color(0xFF180A2E),
        background = Color(0xFF090A10),
        onBackground = Color(0xFFEAF0FF),
        surface = Color(0xFF11131E),
        onSurface = Color(0xFFEAF0FF),
        surfaceVariant = Color(0xFF1E2236),
        onSurfaceVariant = Color(0xFFB3BCD9),
        surfaceContainerLowest = Color(0xFF06070B),
        surfaceContainerLow = Color(0xFF121522),
        surfaceContainer = Color(0xFF171B2C),
        surfaceContainerHigh = Color(0xFF21263D),
        surfaceContainerHighest = Color(0xFF2C324F),
        outline = Color(0xFF515B87),
        outlineVariant = Color(0xFF2D3352),
        error = Color(0xFFFF6680),
        onError = Color(0xFF29050B)
    )
    VelunaThemePreset.BREEZE -> darkColorScheme(
        primary = Color(0xFF4BB8F0),
        onPrimary = Color(0xFF061822),
        primaryContainer = Color(0xFF1A425C),
        onPrimaryContainer = Color(0xFFD8F1FE),
        secondary = Color(0xFF24E3A3),
        onSecondary = Color(0xFF071F17),
        secondaryContainer = Color(0xFF26303B),
        onSecondaryContainer = Color(0xFFE8EFF5),
        tertiary = Color(0xFFC084DA),
        onTertiary = Color(0xFF210B2B),
        background = Color(0xFF14191F),
        onBackground = Color(0xFFE8EFF5),
        surface = Color(0xFF1B222A),
        onSurface = Color(0xFFE8EFF5),
        surfaceVariant = Color(0xFF26303B),
        onSurfaceVariant = Color(0xFFB4C1CC),
        surfaceContainerLowest = Color(0xFF0F1318),
        surfaceContainerLow = Color(0xFF1D242D),
        surfaceContainer = Color(0xFF222B35),
        surfaceContainerHigh = Color(0xFF2C3744),
        surfaceContainerHighest = Color(0xFF374454),
        outline = Color(0xFF5B6D80),
        outlineVariant = Color(0xFF333F4D),
        error = Color(0xFFEE6875),
        onError = Color(0xFF2B070B)
    )
    VelunaThemePreset.AMOLED -> darkColorScheme(
        primary = Color(0xFFFA6A1E),
        onPrimary = Color(0xFF1D0900),
        primaryContainer = Color(0xFF3B1B0D),
        onPrimaryContainer = Color(0xFFFFDFCD),
        secondary = Color(0xFFF59E0B),
        onSecondary = Color(0xFF140C00),
        secondaryContainer = Color(0xFF1B1B21),
        onSecondaryContainer = Color(0xFFF4F4F6),
        tertiary = Color(0xFFFF8847),
        onTertiary = Color(0xFF1E0A00),
        background = Color(0xFF000000),
        onBackground = Color(0xFFF5F5F7),
        surface = Color(0xFF08080A),
        onSurface = Color(0xFFF5F5F7),
        surfaceVariant = Color(0xFF16161B),
        onSurfaceVariant = Color(0xFFB8B8C2),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF0C0C10),
        surfaceContainer = Color(0xFF121217),
        surfaceContainerHigh = Color(0xFF1B1B22),
        surfaceContainerHighest = Color(0xFF24242D),
        outline = Color(0xFF4D4D5C),
        outlineVariant = Color(0xFF292933),
        error = Color(0xFFFF6666),
        onError = Color(0xFF280404)
    )
}

private fun lightPresetScheme(preset: VelunaThemePreset): ColorScheme = when (preset) {
    VelunaThemePreset.DEFAULT, VelunaThemePreset.AMOLED -> lightColorScheme(
        primary = Color(0xFFC23E0A),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFE2D2),
        onPrimaryContainer = Color(0xFF461500),
        secondary = Color(0xFF8F5400),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFEDE3DA),
        onSecondaryContainer = Color(0xFF2B211A),
        tertiary = Color(0xFF9C422B),
        onTertiary = Color.White,
        background = Color(0xFFFAF6F2),
        onBackground = Color(0xFF1C1612),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1C1612),
        surfaceVariant = Color(0xFFF0E8E0),
        onSurfaceVariant = Color(0xFF564A41),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF9F3ED),
        surfaceContainer = Color(0xFFF2EAE3),
        surfaceContainerHigh = Color(0xFFEBE1D8),
        surfaceContainerHighest = Color(0xFFE3D7CC),
        outline = Color(0xFF8B7C70),
        outlineVariant = Color(0xFFD6C8BC),
        error = Color(0xFFBA1A1A),
        onError = Color.White
    )
    VelunaThemePreset.CATPPUCCIN -> lightColorScheme(
        primary = Color(0xFF7526DF),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFEDE0FE),
        onPrimaryContainer = Color(0xFF290059),
        secondary = Color(0xFFAD2E83),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE3E0F5),
        onSecondaryContainer = Color(0xFF2A2347),
        tertiary = Color(0xFF147568),
        onTertiary = Color.White,
        background = Color(0xFFEFF1F5),
        onBackground = Color(0xFF25283B),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF25283B),
        surfaceVariant = Color(0xFFE4E7F0),
        onSurfaceVariant = Color(0xFF4E526B),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF6F7FA),
        surfaceContainer = Color(0xFFEAECEF),
        surfaceContainerHigh = Color(0xFFE0E3EA),
        surfaceContainerHighest = Color(0xFFD6DAE4),
        outline = Color(0xFF7D8299),
        outlineVariant = Color(0xFFC5C9D6),
        error = Color(0xFFC62244),
        onError = Color.White
    )
    VelunaThemePreset.TOKYO_NIGHT -> lightColorScheme(
        primary = Color(0xFF2C4D87),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD7E4FA),
        onPrimaryContainer = Color(0xFF102447),
        secondary = Color(0xFF6747A6),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFDFE2EE),
        onSecondaryContainer = Color(0xFF22273D),
        tertiary = Color(0xFF0E6C8C),
        onTertiary = Color.White,
        background = Color(0xFFF0F1F5),
        onBackground = Color(0xFF1D2030),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1D2030),
        surfaceVariant = Color(0xFFE1E3EB),
        onSurfaceVariant = Color(0xFF495175),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF5F6FA),
        surfaceContainer = Color(0xFFE9EBF2),
        surfaceContainerHigh = Color(0xFFDFE2EC),
        surfaceContainerHighest = Color(0xFFD4D8E5),
        outline = Color(0xFF7A8099),
        outlineVariant = Color(0xFFC2C6D6),
        error = Color(0xFFC42343),
        onError = Color.White
    )
    VelunaThemePreset.CAFFEINE -> lightColorScheme(
        primary = Color(0xFF965214),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFF7E4CF),
        onPrimaryContainer = Color(0xFF3A1C00),
        secondary = Color(0xFF7F5632),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFECE4DC),
        onSecondaryContainer = Color(0xFF2E2218),
        tertiary = Color(0xFF6E533F),
        onTertiary = Color.White,
        background = Color(0xFFFAF8F5),
        onBackground = Color(0xFF211A14),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF211A14),
        surfaceVariant = Color(0xFFEFE8DF),
        onSurfaceVariant = Color(0xFF574C41),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF8F4EE),
        surfaceContainer = Color(0xFFF1EBE3),
        surfaceContainerHigh = Color(0xFFE9E1D7),
        surfaceContainerHighest = Color(0xFFE0D6CA),
        outline = Color(0xFF8A7E71),
        outlineVariant = Color(0xFFD5C9BC),
        error = Color(0xFFBA1A1A),
        onError = Color.White
    )
    VelunaThemePreset.NEON -> lightColorScheme(
        primary = Color(0xFF007668),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFC6F5ED),
        onPrimaryContainer = Color(0xFF002E27),
        secondary = Color(0xFFC21656),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE2E8F4),
        onSecondaryContainer = Color(0xFF15192B),
        tertiary = Color(0xFF6B36BA),
        onTertiary = Color.White,
        background = Color(0xFFF4F7FB),
        onBackground = Color(0xFF11131E),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF11131E),
        surfaceVariant = Color(0xFFE4EAF4),
        onSurfaceVariant = Color(0xFF424B66),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF6F9FC),
        surfaceContainer = Color(0xFFECF1F8),
        surfaceContainerHigh = Color(0xFFE2E9F3),
        surfaceContainerHighest = Color(0xFFD6DFEC),
        outline = Color(0xFF727C99),
        outlineVariant = Color(0xFFC5CEE0),
        error = Color(0xFFC4183C),
        onError = Color.White
    )
    VelunaThemePreset.BREEZE -> lightColorScheme(
        primary = Color(0xFF156BA3),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD2ECFC),
        onPrimaryContainer = Color(0xFF04263E),
        secondary = Color(0xFF0C7B56),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFDFE8F2),
        onSecondaryContainer = Color(0xFF19232E),
        tertiary = Color(0xFF793D94),
        onTertiary = Color.White,
        background = Color(0xFFEFF3F7),
        onBackground = Color(0xFF182029),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF182029),
        surfaceVariant = Color(0xFFE1E8F0),
        onSurfaceVariant = Color(0xFF455362),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF4F7FB),
        surfaceContainer = Color(0xFFE8EEF5),
        surfaceContainerHigh = Color(0xFFDEE6EF),
        surfaceContainerHighest = Color(0xFFD2DCE8),
        outline = Color(0xFF748494),
        outlineVariant = Color(0xFFC1CCD8),
        error = Color(0xFFB82433),
        onError = Color.White
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
