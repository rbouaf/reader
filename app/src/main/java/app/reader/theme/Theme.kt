package app.reader.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import app.reader.data.AppSettings
import app.reader.data.NightChoice
import app.reader.data.ThemeChoice
import app.reader.looks.LocalLook
import app.reader.looks.Look

val LocalColors = staticCompositionLocalOf {
    Colors(Mode.Dark, androidx.compose.ui.graphics.Color.Black, androidx.compose.ui.graphics.Color.White,
        androidx.compose.ui.graphics.Color.Gray, androidx.compose.ui.graphics.Color.DarkGray,
        androidx.compose.ui.graphics.Color.DarkGray, androidx.compose.ui.graphics.Color.DarkGray,
        androidx.compose.ui.graphics.Color.White)
}

/** The mode a look shows in, given the settings and whether the system is in dark mode. */
fun resolveMode(look: Look, s: AppSettings, systemDark: Boolean): Mode = when (s.theme) {
    null -> look.defaultMode
    ThemeChoice.Light -> Mode.Light
    ThemeChoice.Dark -> Mode.Dark
    ThemeChoice.Black -> Mode.Black
    ThemeChoice.System -> if (!systemDark) Mode.Light else if (s.night == NightChoice.Black) Mode.Black else Mode.Dark
}

/** Provides the look, its resolved colours and the reading font for everything inside. */
@Composable
fun ReaderTheme(look: Look, mode: Mode, warmth: Float, content: @Composable () -> Unit) {
    val colors = remember(look, mode, warmth) { look.palette.resolve(mode, warmth) }
    CompositionLocalProvider(LocalLook provides look, LocalColors provides colors, content = content)
}

@Composable
fun ReaderTheme(look: Look, settings: AppSettings, content: @Composable () -> Unit) =
    ReaderTheme(look, resolveMode(look, settings, isSystemInDarkTheme()), settings.warmth / 100f, content)

/** The reading face a look shows: the saved one if the look offers it, else the look's default. */
fun bookFontFor(look: Look, s: AppSettings): String = s.font?.takeIf { it in look.bookFonts } ?: look.bookFonts.first()
