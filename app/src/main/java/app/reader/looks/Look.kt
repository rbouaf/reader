package app.reader.looks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import app.reader.looks.editorial.EditorialLook
import app.reader.looks.plotter.PlotterLook
import app.reader.looks.radar.RadarLook
import app.reader.looks.readout.ReadoutLook
import app.reader.looks.trace.TraceLook
import app.reader.theme.LookPalette
import app.reader.theme.Mode

/**
 * One of the five switchable looks. A look owns its colours, its UI typefaces, the four
 * reading faces it offers, and how every screen is drawn. What the screens *contain* and how
 * they behave is shared (see `looks/common`), so switching looks never changes what you can do.
 */
interface Look {
    val id: String
    val name: String
    val sub: String
    val palette: LookPalette
    /** Used until the user picks a theme. */
    val defaultMode: Mode
    /** Four reading faces from [app.reader.theme.BookFaces]; the first is the default. */
    val bookFonts: List<String>
    /** Primitives the shared screens (settings, sheets, panels) are built from. */
    val kit: Kit

    /** The whole library screen: header, current book, index, and (tablet landscape) summary. */
    @Composable fun Library(ui: LibraryUi, on: LibraryActions)

    /** The overlay over the book page, shown on a tap in the middle: top bar, bottom bar, scrubber. */
    @Composable fun ReaderChrome(ui: ChromeUi, on: ChromeActions)
}

object Looks {
    val all: List<Look> = listOf(ReadoutLook, TraceLook, EditorialLook, RadarLook, PlotterLook)
    fun byId(id: String?): Look = all.firstOrNull { it.id == id } ?: ReadoutLook
}

val LocalLook = staticCompositionLocalOf<Look> { ReadoutLook }

@Immutable
data class LookInfo(val id: String, val name: String, val sub: String)
