package app.reader.looks.plotter

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
import app.reader.theme.tone

/**
 * 07 Readout's report layout in 02 Plotter's aesthetic (mockups/v8/plotter.html): an engineering
 * sheet drawn with two pens on millimetre paper. The header is the title block, the current book
 * a dimensioned detail with a protractor, the index the parts list, the scrubber a drafting scale.
 */
object PlotterLook : Look {
    override val id = "plotter"
    override val name = "Plotter"
    override val sub = "Drafting paper"

    private val dark = Swatch(
        paper = tone(0xFF1C1E21, 0xFF1E1A15),
        ink = tone(0xFFE9EBE6, 0xFFF0BC70),
        mute = tone(0xFF8F9491, 0xFFA3814F),
        grid = tone(0xFF33383E, 0xFF3D3222),
        gridMinor = tone(0xFF25282C, 0xFF28221A),
        tint = tone(0xFFFFFFFF, 0xFFFFB35C),
    )

    override val palette = LookPalette(
        light = Swatch(
            paper = tone(0xFFEDEDE6, 0xFFF1E7CF),
            ink = tone(0xFF121314, 0xFF26190B),
            mute = tone(0xFF5B5E5C, 0xFF75603F),
            grid = tone(0xFFC3C9CF, 0xFFD9C7A2),
            gridMinor = tone(0xFFDDE1E3, 0xFFE8DCC0),
            tint = tone(0xFFFFFFFF, 0xFFFFD9A6),
        ),
        dark = dark,
        // Black: the dark grid would glow on pure black, so it's pulled down to a faint
        // blueprint ruling that keeps the same step between major and minor lines.
        black = dark.copy(
            paper = tone(0xFF000000, 0xFF000000),
            grid = tone(0xFF202428, 0xFF261E13),
            gridMinor = tone(0xFF111315, 0xFF140F09),
        ),
    )
    override val defaultMode = Mode.Light
    override val bookFonts = listOf("Newsreader", "Instrument Sans", "Literata", "Atkinson Hyperlegible Next")
    override val kit: Kit = PlotterKit

    @Composable override fun Library(ui: LibraryUi, on: LibraryActions) = PlotterLibrary(ui, on)
    @Composable override fun ReaderChrome(ui: ChromeUi, on: ChromeActions) = PlotterChrome(ui, on)
}
