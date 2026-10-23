package app.reader.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Light, Dark or Black (AMOLED). Black is Dark with a pure black ground. */
enum class Mode { Light, Dark, Black;
    val isDark get() = this != Light
}

/** One colour as a neutral/warm pair; warmth mixes between them in sRGB, like the mockups. */
@Immutable
data class Tone(val neutral: Color, val warm: Color) {
    fun at(warmth: Float): Color = lerp(neutral, warm, warmth.coerceIn(0f, 1f))
}

fun tone(neutral: Long, warm: Long) = Tone(Color(neutral), Color(warm))

/**
 * A look's colours for one mode. `rule`, `grid` and `gridMinor` are optional in the
 * mockups; missing ones are derived from paper and ink so every look has all six.
 */
@Immutable
data class Swatch(
    val paper: Tone,
    val ink: Tone,
    val mute: Tone,
    val rule: Tone? = null,
    val grid: Tone? = null,
    val gridMinor: Tone? = null,
    /** Image tint for warm modes (white → this at full warmth). */
    val tint: Tone = tone(0xFFFFFFFF, 0xFFFFD9A6),
)

@Immutable
data class LookPalette(val light: Swatch, val dark: Swatch, val black: Swatch) {
    fun swatch(mode: Mode) = when (mode) { Mode.Light -> light; Mode.Dark -> dark; Mode.Black -> black }
}

/** Resolved colours for the current look, mode and warmth. */
@Immutable
data class Colors(
    val mode: Mode,
    val paper: Color,
    val ink: Color,
    val mute: Color,
    val rule: Color,
    val grid: Color,
    val gridMinor: Color,
    val tint: Color,
) {
    val isDark get() = mode.isDark
}

fun LookPalette.resolve(mode: Mode, warmth: Float): Colors {
    val s = swatch(mode)
    val paper = s.paper.at(warmth)
    val ink = s.ink.at(warmth)
    val rule = s.rule?.at(warmth) ?: lerp(paper, ink, 0.3f)
    val grid = s.grid?.at(warmth) ?: lerp(paper, ink, 0.12f)
    return Colors(
        mode = mode,
        paper = paper,
        ink = ink,
        mute = s.mute.at(warmth),
        rule = rule,
        grid = grid,
        gridMinor = s.gridMinor?.at(warmth) ?: lerp(paper, grid, 0.5f),
        tint = s.tint.at(warmth),
    )
}

/**
 * Black from Dark: pure black paper (warm stays black too, so OLED pixels stay off),
 * the same ink and mute, and the lines retuned so they keep their contrast against black.
 */
fun Swatch.blackened(): Swatch {
    fun Tone.down(k: Float) = Tone(lerp(neutral, Color.Black, k), lerp(warm, Color.Black, k))
    return copy(
        paper = tone(0xFF000000, 0xFF000000),
        rule = rule?.down(0.12f),
        grid = grid?.down(0.25f),
        gridMinor = gridMinor?.down(0.35f),
        tint = tone(0xFFFFFFFF, 0xFFFFB35C),
    )
}
