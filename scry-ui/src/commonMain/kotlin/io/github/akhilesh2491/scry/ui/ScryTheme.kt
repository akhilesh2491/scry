package io.github.akhilesh2491.scry.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Scry's design system.
 *
 * Deliberately independent of the host app's theme. A debugging overlay that
 * inherits the app's colours becomes unreadable the moment you are debugging a
 * theming bug — which is exactly when you need it most.
 *
 * The visual language is a developer tool, not a consumer app: dense rows, a
 * near-neutral surface so status colour is the only thing that draws the eye,
 * and monospace for anything you might compare character by character.
 */
@Composable
public fun ScryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalScrySpacing provides ScrySpacing(),
        LocalScryPalette provides if (darkTheme) DarkPalette else LightPalette,
    ) {
        ScrySystemBars(darkTheme)
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = ScryTypography,
            content = content,
        )
    }
}

// --- Colour -----------------------------------------------------------------

/**
 * Semantic colours Scry uses beyond Material's scheme.
 *
 * Status colour carries meaning here, so it is defined once and per-mode rather
 * than tinted ad hoc at call sites — a 500 has to look the same in every screen
 * that shows one.
 */
public class ScryPalette internal constructor(
    public val success: Color,
    public val info: Color,
    public val warning: Color,
    public val danger: Color,
    public val muted: Color,
    /** Background for code, bodies and hex dumps. */
    public val codeSurface: Color,
    /** Hairline used between dense rows. */
    public val divider: Color,
    /** Fill for neutral badges such as HTTP methods. */
    public val badgeSurface: Color,
)

private val DarkPalette = ScryPalette(
    success = Color(0xFF4ADE80),
    info = Color(0xFF60A5FA),
    warning = Color(0xFFFBBF24),
    danger = Color(0xFFF87171),
    muted = Color(0xFF8B93A7),
    codeSurface = Color(0xFF11141B),
    divider = Color(0xFF232833),
    badgeSurface = Color(0xFF232833),
)

private val LightPalette = ScryPalette(
    success = Color(0xFF15803D),
    info = Color(0xFF1D4ED8),
    warning = Color(0xFFB45309),
    danger = Color(0xFFB91C1C),
    muted = Color(0xFF6B7280),
    codeSurface = Color(0xFFF3F4F6),
    divider = Color(0xFFE5E7EB),
    badgeSurface = Color(0xFFECEEF2),
)

public val LocalScryPalette: ProvidableCompositionLocal<ScryPalette> =
    staticCompositionLocalOf { DarkPalette }

/** Scry's semantic colours for the current theme. */
public val scryPalette: ScryPalette
    @Composable get() = LocalScryPalette.current

private val Teal = Color(0xFF2DD4BF)

private val DarkColors = darkColorScheme(
    primary = Teal,
    onPrimary = Color(0xFF06231F),
    secondary = Color(0xFF60A5FA),
    background = Color(0xFF0B0E14),
    onBackground = Color(0xFFE6E9EF),
    surface = Color(0xFF0F131A),
    onSurface = Color(0xFFE6E9EF),
    surfaceVariant = Color(0xFF1A1F29),
    onSurfaceVariant = Color(0xFF8B93A7),
    error = Color(0xFFF87171),
    outline = Color(0xFF2A3140),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    secondary = Color(0xFF1D4ED8),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFF0F2F5),
    onSurfaceVariant = Color(0xFF6B7280),
    error = Color(0xFFB91C1C),
    outline = Color(0xFFD1D5DB),
)

// --- Typography -------------------------------------------------------------

/**
 * A tighter scale than Material's default.
 *
 * Scry shows lists of hundreds of rows; Material's default sizes and line
 * heights waste vertical space that is better spent on more rows.
 */
private val ScryTypography = Typography().run {
    copy(
        titleLarge = titleLarge.copy(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        bodyMedium = bodyMedium.copy(fontSize = 14.sp, lineHeight = 19.sp),
        bodySmall = bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
        labelSmall = labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
    )
}

/** Monospace style for payloads, headers and anything compared character by character. */
public val scryMonoStyle: TextStyle
    @Composable get() = MaterialTheme.typography.bodySmall.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    )

// --- Spacing ----------------------------------------------------------------

/** One spacing scale, so density is consistent across plugins. */
public class ScrySpacing internal constructor(
    public val xs: Dp = 4.dp,
    public val sm: Dp = 8.dp,
    public val md: Dp = 12.dp,
    public val lg: Dp = 16.dp,
    public val xl: Dp = 24.dp,
)

public val LocalScrySpacing: ProvidableCompositionLocal<ScrySpacing> =
    staticCompositionLocalOf { ScrySpacing() }

public val scrySpacing: ScrySpacing
    @Composable get() = LocalScrySpacing.current

/** Colours for HTTP status codes, shared by any plugin showing request outcomes. */
public object ScryStatusColors {

    /** Maps an HTTP status code to its semantic colour. `null` means in flight. */
    @Composable
    public fun forStatus(code: Int?): Color {
        val palette = scryPalette
        return when {
            code == null -> palette.muted
            code >= 500 -> palette.danger
            code >= 400 -> palette.warning
            code >= 300 -> palette.info
            else -> palette.success
        }
    }
}
