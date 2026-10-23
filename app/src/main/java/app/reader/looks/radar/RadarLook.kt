package app.reader.looks.radar

import androidx.compose.runtime.Composable
import app.reader.looks.ChromeActions
import app.reader.looks.ChromeUi
import app.reader.looks.Kit
import app.reader.looks.LibraryActions
import app.reader.looks.LibraryUi
import app.reader.looks.Look
import app.reader.looks.readout.ReadoutChrome
import app.reader.looks.readout.ReadoutKit
import app.reader.looks.readout.ReadoutLibrary
import app.reader.looks.readout.ReadoutLook
import app.reader.theme.Mode

/** Placeholder until this look is ported: draws Readout. */
object RadarLook : Look {
    override val id = "radar"
    override val name = "Radar"
    override val sub = "Polar dials"
    override val palette = ReadoutLook.palette
    override val defaultMode = Mode.Dark
    override val bookFonts = ReadoutLook.bookFonts
    override val kit: Kit = ReadoutKit

    @Composable override fun Library(ui: LibraryUi, on: LibraryActions) = ReadoutLibrary(ui, on, "radar")
    @Composable override fun ReaderChrome(ui: ChromeUi, on: ChromeActions) = ReadoutChrome(ui, on)
}
