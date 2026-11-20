package app.gridlauncher.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import app.gridlauncher.R

/** Dark-only palette from the design. */
object Palette {
    val Ground = Color(0xFF0F1012)
    val Surface = Color(0xFF17191C)
    val Surface2 = Color(0xFF1F2226)
    val Surface3 = Color(0xFF1B1E22)
    val Line = Color(0xFF2A2E33)
    val LineStrong = Color(0xFF3A3F46)
    val Fg = Color(0xFFECEDEF)
    val FgSoft = Color(0xFFC9CDD3)
    val Muted = Color(0xFF9CA1A9)
    val Subtle = Color(0xFF80868F)
    /** The user's accent color. Backed by snapshot state, so everything drawn with it updates. */
    var Accent: Color by mutableStateOf(Color(0xFF9DB8FF))
        internal set

    /** Accent choices offered in Settings, as (ARGB, name). */
    val accentChoices = listOf(
        0xFF9DB8FF.toInt() to "Blue",
        0xFF8FD6A8.toInt() to "Mint",
        0xFFF2B880.toInt() to "Peach",
        0xFFF2A0B8.toInt() to "Rose",
        0xFFC3A8F2.toInt() to "Lavender",
        0xFFE8D57A.toInt() to "Lemon",
        0xFF7FD3E0.toInt() to "Aqua",
        0xFFECEDEF.toInt() to "Mono",
    )
    val Danger = Color(0xFFFF9C8F)
}

/** Google Fonts offered in Settings. Any other family name can also be typed in. */
object FontCatalog {
    const val DEFAULT = "Geist"
    const val SYSTEM = ""

    val families = listOf(
        "Geist", "Inter", "Manrope", "DM Sans", "Plus Jakarta Sans", "Figtree", "Outfit",
        "Onest", "Instrument Sans", "Work Sans", "IBM Plex Sans", "Space Grotesk", "Sora",
        "Urbanist", "Lexend", "Rubik", "Nunito", "Poppins", "Montserrat", "Roboto",
        "Geist Mono", "JetBrains Mono", "Roboto Mono", "Lora", "Fraunces", "Playfair Display",
    )

    val provider = GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = R.array.com_google_android_gms_fonts_certs,
    )

    fun displayName(name: String): String = name.ifBlank { "System default" }

    /** Falls back to the system font if the family cannot be downloaded. */
    fun family(name: String): FontFamily {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return FontFamily.Default
        val font = GoogleFont(trimmed)
        return FontFamily(
            Font(googleFont = font, fontProvider = provider, weight = FontWeight.Light),
            Font(googleFont = font, fontProvider = provider, weight = FontWeight.Normal),
            Font(googleFont = font, fontProvider = provider, weight = FontWeight.Medium),
            Font(googleFont = font, fontProvider = provider, weight = FontWeight.SemiBold),
            Font(googleFont = font, fontProvider = provider, weight = FontWeight.Bold),
        )
    }
}

private fun launcherColors(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = Palette.Ground,
    primaryContainer = Palette.Surface2,
    onPrimaryContainer = Palette.Fg,
    secondary = accent,
    onSecondary = Palette.Ground,
    background = Palette.Ground,
    onBackground = Palette.Fg,
    surface = Palette.Surface,
    onSurface = Palette.Fg,
    surfaceVariant = Palette.Surface2,
    onSurfaceVariant = Palette.Muted,
    surfaceContainerLowest = Palette.Ground,
    surfaceContainerLow = Palette.Surface,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.Surface3,
    surfaceContainerHighest = Palette.Surface2,
    outline = Palette.Line,
    outlineVariant = Palette.Line,
    error = Palette.Danger,
    onError = Palette.Ground,
)

private fun TextStyle.with(family: FontFamily) = copy(fontFamily = family)

private fun Typography.withFamily(f: FontFamily) = Typography(
    displayLarge = displayLarge.with(f), displayMedium = displayMedium.with(f), displaySmall = displaySmall.with(f),
    headlineLarge = headlineLarge.with(f), headlineMedium = headlineMedium.with(f), headlineSmall = headlineSmall.with(f),
    titleLarge = titleLarge.with(f), titleMedium = titleMedium.with(f), titleSmall = titleSmall.with(f),
    bodyLarge = bodyLarge.with(f), bodyMedium = bodyMedium.with(f), bodySmall = bodySmall.with(f),
    labelLarge = labelLarge.with(f), labelMedium = labelMedium.with(f), labelSmall = labelSmall.with(f),
)

/** Corner radius of widget cards, set in Settings. */
val LocalWidgetCorner = staticCompositionLocalOf { 22.dp }

@Composable
fun LauncherTheme(
    fontName: String,
    accent: Int = 0xFF9DB8FF.toInt(),
    widgetCorner: Float = 22f,
    content: @Composable () -> Unit,
) {
    val family = remember(fontName) { FontCatalog.family(fontName) }
    val typography = remember(family) { Typography().withFamily(family) }
    val accentColor = Color(accent)
    // Set before any child reads it, so the first frame already uses the saved accent.
    remember(accentColor) {
        Palette.Accent = accentColor
        accentColor
    }
    val colors = remember(accentColor) { launcherColors(accentColor) }
    MaterialTheme(colorScheme = colors, typography = typography) {
        CompositionLocalProvider(
            LocalContentColor provides Palette.Fg,
            LocalTextStyle provides typography.bodyMedium,
            LocalWidgetCorner provides widgetCorner.dp,
            content = content,
        )
    }
}
