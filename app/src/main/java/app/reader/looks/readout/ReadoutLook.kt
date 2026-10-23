package app.reader.looks.readout

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

/** 07 Readout: Telemetry as a ship's-computer text terminal (mockups/v8/readout.html). */
object ReadoutLook : Look {
    override val id = "readout"
    override val name = "Readout"
    override val sub = "Terminal records"

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
    override val kit: Kit = ReadoutKit

    @Composable override fun Library(ui: LibraryUi, on: LibraryActions) = ReadoutLibrary(ui, on)
    @Composable override fun ReaderChrome(ui: ChromeUi, on: ChromeActions) = ReadoutChrome(ui, on)
}
