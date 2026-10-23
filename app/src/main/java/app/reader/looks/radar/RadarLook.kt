package app.reader.looks.radar

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

/** 07 Readout's report layout drawn on 03's radar scope (mockups/v8/radar.html). Progress is a bearing. */
object RadarLook : Look {
    override val id = "radar"
    override val name = "Radar"
    override val sub = "Polar dials"

    private val dark = Swatch(
        paper = tone(0xFF101112, 0xFF130F0A),
        ink = tone(0xFFD9DCD6, 0xFFF0BC70),
        mute = tone(0xFF7C807A, 0xFF9C7A49),
        rule = tone(0xFF3A3D40, 0xFF4A3923),
        grid = tone(0xFF24272A, 0xFF2C2216),
        tint = tone(0xFFFFFFFF, 0xFFFFB35C),
    )
    override val palette = LookPalette(
        light = Swatch(
            paper = tone(0xFFE7E8E3, 0xFFECE3CF),
            ink = tone(0xFF1B1C1D, 0xFF2C2115),
            mute = tone(0xFF696C67, 0xFF7A6547),
            rule = tone(0xFFA6A9A3, 0xFFBBA582),
            grid = tone(0xFFCFD1CB, 0xFFD9CBAE),
        ),
        dark = dark,
        black = dark.blackened(),
    )
    override val defaultMode = Mode.Dark
    override val bookFonts = listOf("Geist", "Atkinson Hyperlegible Next", "Literata", "Azeret Mono")
    override val kit: Kit = RadarKit

    @Composable override fun Library(ui: LibraryUi, on: LibraryActions) = RadarLibrary(ui, on)
    @Composable override fun ReaderChrome(ui: ChromeUi, on: ChromeActions) = RadarChrome(ui, on)
}
