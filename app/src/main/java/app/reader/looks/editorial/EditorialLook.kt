package app.reader.looks.editorial

import androidx.compose.runtime.Composable
import app.reader.looks.ChromeActions
import app.reader.looks.ChromeUi
import app.reader.looks.Kit
import app.reader.looks.LibraryActions
import app.reader.looks.LibraryUi
import app.reader.looks.Look
import app.reader.theme.LookPalette
import app.reader.theme.Mode
import app.reader.theme.Swatch
import app.reader.theme.blackened
import app.reader.theme.tone

/**
 * Editorial (mockups/v8/editorial.html): 07 Readout's report layout (header block, a record for
 * the current book, an index table, a summary column) printed in 04 Strip chart's language.
 * Bodoni Moda sets names, titles, big figures and every field label in italic; Fragment Mono
 * prints the data; progress is always a recorder trace on one shared sixteen-week axis.
 */
object EditorialLook : Look {
    override val id = "editorial"
    override val name = "Editorial"
    override val sub = "Bodoni report"

    private val dark = Swatch(
        paper = tone(0xFF111213, 0xFF130F0A),
        ink = tone(0xFFDCDFD9, 0xFFF0BC70),
        mute = tone(0xFF7E827C, 0xFF9C7A49),
        rule = tone(0xFF3B3E41, 0xFF4A3923),
        grid = tone(0xFF212427, 0xFF2A2015),
        tint = tone(0xFFFFFFFF, 0xFFFFB35C),
    )
    override val palette = LookPalette(
        light = Swatch(
            paper = tone(0xFFE6E7E3, 0xFFECE3CF),
            ink = tone(0xFF18191A, 0xFF2C2115),
            mute = tone(0xFF666A65, 0xFF7A6547),
            rule = tone(0xFFA9ACA6, 0xFFBBA582),
            grid = tone(0xFFD2D4CE, 0xFFDCCFB3),
            tint = tone(0xFFFFFFFF, 0xFFFFD9A6),
        ),
        dark = dark,
        black = dark.blackened(),
    )
    override val defaultMode = Mode.Dark
    override val bookFonts = listOf("Spectral", "Newsreader", "Literata", "Atkinson Hyperlegible Next")
    override val kit: Kit = EditorialKit

    @Composable override fun Library(ui: LibraryUi, on: LibraryActions) = EditorialLibrary(ui, on)
    @Composable override fun ReaderChrome(ui: ChromeUi, on: ChromeActions) = EditorialChrome(ui, on)
}
