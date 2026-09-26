package `in`.sajag.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/*
 * A light theme: white cards on a pale grey page, navy for the brand and
 * amber for the one action that matters on each screen. Light screens are
 * easier to read outdoors at a pit head, and look like the rest of the phone.
 *
 * Contrast (WCAG), measured:
 *   Ink on Card 15.7:1, Muted on Card 5.8:1, Muted on Bg 5.3:1
 *   black on Amber 11.7:1, AmberText on Card 7.8:1
 *   DangerText on Card 7.8:1, SafeText on Card 7.9:1 (safety meaning: 7:1 or more)
 *   white on Danger 5.6:1, white on Safe 5.1:1, white on Navy 14.5:1
 *
 * The drill is the one dark place: it is the live camera, so its controls sit
 * on a dark scrim and its answer sheet is a white card.
 */
val Bg = Color(0xFFF4F6F8)
val Card = Color(0xFFFFFFFF)
val CardAlt = Color(0xFFEEF1F4)
val Line = Color(0xFFDDE2E8)
val Ink = Color(0xFF1B2430)
val Muted = Color(0xFF5B6776)

val Navy = Color(0xFF0F2A4A)
val NavySoft = Color(0xFFB3C3D6)

val Amber = Color(0xFFFFB300)
val AmberBg = Color(0xFFFFF4DC)
val AmberText = Color(0xFF6D4C00)

/** Fills and strokes. Text uses [DangerText], [SafeText]. */
val Danger = Color(0xFFC62828)
val DangerText = Color(0xFFA4161A)
val DangerBg = Color(0xFFFDECEA)

val Safe = Color(0xFF2E7D32)
val SafeText = Color(0xFF1B5E20)
val SafeBg = Color(0xFFE8F5E9)

val InfoText = Color(0xFF0D47A1)
val InfoBg = Color(0xFFE3F2FD)

/** Over the camera: controls sit on this scrim. */
val Scrim = Color(0xB3000000)

@Composable
fun SajagTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Navy,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD6E4F5),
            onPrimaryContainer = Navy,
            inversePrimary = Amber,
            secondary = Amber,
            onSecondary = Color.Black,
            secondaryContainer = AmberBg,
            onSecondaryContainer = Ink,
            tertiary = Safe,
            onTertiary = Color.White,
            tertiaryContainer = SafeBg,
            onTertiaryContainer = SafeText,
            background = Bg,
            onBackground = Ink,
            surface = Card,
            onSurface = Ink,
            surfaceVariant = CardAlt,
            onSurfaceVariant = Muted,
            surfaceTint = Card,
            inverseSurface = Color(0xFF2B3440),
            inverseOnSurface = Bg,
            error = DangerText,
            onError = Color.White,
            errorContainer = DangerBg,
            onErrorContainer = DangerText,
            outline = Color(0xFF8A96A3),
            outlineVariant = Line,
            scrim = Color.Black,
            // Material's defaults for these are tinted violet; keep every
            // surface neutral so cards, dialogs and menus match.
            surfaceBright = Card,
            surfaceContainer = Color(0xFFF1F3F6),
            surfaceContainerHigh = Card,
            surfaceContainerHighest = Color(0xFFE9EDF1),
            surfaceContainerLow = Color(0xFFF8F9FB),
            surfaceContainerLowest = Card,
            surfaceDim = Line,
        ),
    ) {
        CompositionLocalProvider(LocalContentColor provides Ink, content = content)
    }
}
