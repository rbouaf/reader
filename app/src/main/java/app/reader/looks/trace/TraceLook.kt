package app.reader.looks.trace

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
 * 01 Readout + trace (mockups/v8/trace.html): the Readout terminal with the strip chart's
 * progress language. Reading activity is drawn as stepped pen traces: the current record's
 * channel, a tiny trace per index row, the folder's channel in the summary, and a strip-chart
 * scrubber in the book.
 */
object TraceLook : Look {
    override val id = "trace"
    override val name = "Readout + trace"
    override val sub = "Activity graphs"

    private val dark = Swatch(
        paper = tone(0xFF0F1010, 0xFF130F0A),
        ink = tone(0xFFD5D8D2, 0xFFF0BC70),
        mute = tone(0xFF6E726C, 0xFF9A7848),
        rule = tone(0xFF3A3D3B, 0xFF4C3B24),
        tint = tone(0xFFFFFFFF, 0xFFFFB35C),
    )
    override val palette = LookPalette(
        light = Swatch(
            paper = tone(0xFFE6E7E2, 0xFFECE3CF),
            ink = tone(0xFF141515, 0xFF2C2115),
            mute = tone(0xFF62665F, 0xFF76603F),
            rule = tone(0xFFA3A69F, 0xFFB9A27E),
        ),
        dark = dark,
        black = dark.blackened(),
    )
    override val defaultMode = Mode.Dark
    override val bookFonts = listOf("IBM Plex Sans", "Literata", "Atkinson Hyperlegible Next", "Victor Mono")
    override val kit: Kit = TraceKit

    @Composable override fun Library(ui: LibraryUi, on: LibraryActions) = TraceLibrary(ui, on)
    @Composable override fun ReaderChrome(ui: ChromeUi, on: ChromeActions) = TraceChrome(ui, on)
}
