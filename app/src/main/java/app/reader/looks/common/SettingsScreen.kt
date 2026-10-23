package app.reader.looks.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import app.reader.data.AppSettings
import app.reader.data.Margins
import app.reader.data.NightChoice
import app.reader.data.PageAnim
import app.reader.data.Spread
import app.reader.data.TapSide
import app.reader.data.ThemeChoice
import app.reader.looks.ChoiceOption
import app.reader.looks.LibraryActions
import app.reader.looks.LocalLook
import app.reader.looks.Look
import app.reader.looks.LookCard
import app.reader.looks.Looks
import app.reader.theme.Fonts
import app.reader.theme.HapticLevel
import app.reader.theme.Hx
import app.reader.theme.LocalColors
import app.reader.theme.LocalHaptics
import app.reader.theme.ReaderTheme
import app.reader.theme.bookFontFor
import app.reader.theme.resolveMode
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Settings, the same for every look (only the kit's drawing differs). Opened from the library only.
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    systemDark: Boolean,
    folder: String?,
    update: ((AppSettings) -> AppSettings) -> Unit,
    onChooseFolder: () -> Unit,
    onBack: () -> Unit,
) {
    val look = LocalLook.current
    val kit = look.kit
    val hx = LocalHaptics.current

    fun <T> choose(cur: T, next: T, f: (AppSettings) -> AppSettings) {
        if (cur != next) { hx(Hx.Select); update(f) }
    }

    fun toggle(on: Boolean, f: (AppSettings) -> AppSettings) {
        hx(if (on) Hx.ToggleOn else Hx.ToggleOff); update(f)
    }

    kit.SettingsFrame(
        title = "Settings",
        backLabel = "Library",
        onBack = onBack,
        preview = { kit.PreviewFrame { BookPreview(settings) } },
    ) {
        kit.Group("Style", "Changes the look of the whole app. Your books, reading positions and settings stay as they are.") {
            val cards = remember(settings.theme, settings.night, settings.warmth, systemDark) {
                Looks.all.map { l ->
                    LookCard(l.id, l.name, l.sub) { LookThumb(l, settings, systemDark) }
                }
            }
            kit.LookPicker(cards, look.id) { id -> choose(look.id, id) { it.copy(look = id) } }
        }

        kit.Group("Library", null) {
            kit.Action("Books folder", folder ?: "None chosen yet", if (folder == null) "Choose" else "Change", onChooseFolder)
        }

        kit.Group("Light and paper", null) {
            val themes = listOf(ThemeChoice.System, ThemeChoice.Light, ThemeChoice.Dark, ThemeChoice.Black)
            val shown = settings.theme ?: if (look.defaultMode.isDark) ThemeChoice.Dark else ThemeChoice.Light
            kit.Choice(
                "Theme",
                listOf("System", "Light", "Dark", "Black").map { ChoiceOption(it) },
                themes.indexOf(shown),
                when (shown) {
                    ThemeChoice.System -> "Follows the system's dark mode."
                    ThemeChoice.Black -> "Pure black, for OLED screens."
                    else -> null
                },
            ) { i -> choose(settings.theme, themes[i]) { it.copy(theme = themes[i]) } }
            if (shown == ThemeChoice.System) {
                val nights = NightChoice.entries
                kit.Choice("At night", listOf("Dark", "Black").map { ChoiceOption(it) }, nights.indexOf(settings.night), null) { i ->
                    choose(settings.night, nights[i]) { it.copy(night = nights[i]) }
                }
            }
            DetentSlider(
                label = "Warmth",
                value = settings.warmth.toFloat(), min = 0f, max = 100f, step = 1f, detent = 10f, home = 0f,
                text = { if (it == 0f) "Neutral" else "${it.toInt()}%" },
                stepper = false,
            ) { v -> update { it.copy(warmth = v.roundToInt()) } }
        }

        kit.Group("Text", "For EPUB, TXT and Markdown. PDFs keep their own layout.") {
            val fonts = look.bookFonts
            val font = bookFontFor(look, settings)
            kit.Choice("Typeface", fonts.map { ChoiceOption(it, Fonts.book(it)) }, fonts.indexOf(font), null) { i ->
                choose(font, fonts[i]) { it.copy(font = fonts[i]) }
            }
            DetentSlider(
                label = "Size", value = settings.size.toFloat(), min = 14f, max = 28f, step = 1f, detent = 1f, home = 18f,
                text = { "${it.toInt()} px" }, stepper = true,
            ) { v -> update { it.copy(size = v.roundToInt()) } }
            DetentSlider(
                label = "Line spacing", value = settings.leading, min = 1.3f, max = 2f, step = 0.05f, detent = 0.1f, home = 1.6f,
                text = { "%.2f".format(it) }, stepper = false,
            ) { v -> update { it.copy(leading = v) } }
            val margins = Margins.entries
            kit.Choice("Margins", listOf("Narrow", "Normal", "Wide").map { ChoiceOption(it) }, margins.indexOf(settings.margins), null) { i ->
                choose(settings.margins, margins[i]) { it.copy(margins = margins[i]) }
            }
            kit.Toggle("Justify text", null, settings.justify) { on -> toggle(on) { it.copy(justify = on) } }
            kit.Toggle("Hyphenate", null, settings.hyphens) { on -> toggle(on) { it.copy(hyphens = on) } }
        }

        kit.Group("Pages", null) {
            val spreads = Spread.entries
            kit.Choice("Layout", listOf("Auto", "One page", "Two pages").map { ChoiceOption(it) }, spreads.indexOf(settings.spread),
                "Auto shows two pages when a tablet is held sideways.") { i ->
                choose(settings.spread, spreads[i]) { it.copy(spread = spreads[i]) }
            }
            val anims = PageAnim.entries
            kit.Choice("Page turn", listOf("Instant", "Slide").map { ChoiceOption(it) }, anims.indexOf(settings.anim), null) { i ->
                choose(settings.anim, anims[i]) { it.copy(anim = anims[i]) }
            }
            val sides = TapSide.entries
            kit.Choice("Tap this side to go forward", listOf("Right", "Left").map { ChoiceOption(it) }, sides.indexOf(settings.tapSide), null) { i ->
                choose(settings.tapSide, sides[i]) { it.copy(tapSide = sides[i]) }
            }
            kit.Toggle("Volume keys turn pages", null, settings.volumeKeys) { on -> toggle(on) { it.copy(volumeKeys = on) } }
        }

        kit.Group("PDF", null) {
            kit.Toggle("Recolour pages", "Tints pages to match the theme and warmth. Photos turn monochrome.", settings.pdfRecolor) { on ->
                toggle(on) { it.copy(pdfRecolor = on) }
            }
            kit.Toggle("Trim white margins", "Zooms in so pages fill the screen.", settings.pdfTrim) { on -> toggle(on) { it.copy(pdfTrim = on) } }
        }

        kit.Group("Haptics", null) {
            val levels = HapticLevel.entries
            kit.Choice(
                null, listOf("Off", "Quiet", "Full").map { ChoiceOption(it) }, levels.indexOf(settings.haptics),
                when (settings.haptics) {
                    HapticLevel.Off -> "No vibration."
                    HapticLevel.Quiet -> "Feel chapter breaks, the scrubber, and the first and last page. Ordinary page turns stay silent."
                    HapticLevel.Full -> "Everything in Quiet, plus a soft tick on each page turn and when a swipe is far enough to turn."
                },
            ) { i -> choose(settings.haptics, levels[i]) { it.copy(haptics = levels[i]) } }
            if (settings.haptics == HapticLevel.Quiet) {
                kit.Toggle("Tick on every page turn", null, settings.pageTick) { on -> toggle(on) { it.copy(pageTick = on) } }
            }
        }
    }
}

/**
 * A slider with detents: a fine tick at each detent, a distinct one at the neutral point,
 * and a thud when a stepper button hits the end.
 */
@Composable
private fun DetentSlider(
    label: String, value: Float, min: Float, max: Float, step: Float, detent: Float, home: Float,
    text: (Float) -> String, stepper: Boolean, set: (Float) -> Unit,
) {
    val kit = LocalLook.current.kit
    val hx = LocalHaptics.current
    fun snap(v: Float) = ((v.coerceIn(min, max) - min) / step).roundToInt() * step + min
    fun apply(next: Float, fromStep: Boolean) {
        val n = snap(next)
        if (abs(n - value) < step / 2) { if (fromStep) hx(Hx.Edge); return }
        val crossed = (n / detent).roundToInt() != (value / detent).roundToInt() || fromStep
        if (crossed) hx(if (abs(n - home) < detent / 2 && (abs(n - home) < step / 2)) Hx.DetentHome else Hx.Detent)
        set(n)
    }
    kit.Slider(
        label = label,
        valueText = text(value),
        fraction = (value - min) / (max - min),
        home = (home - min) / (max - min),
        stepper = stepper,
        onFraction = { f -> apply(min + f * (max - min), false) },
        onStep = { d -> apply(value + d * detent, true) },
    )
}

/** The sample page in the current reading face, size, spacing and alignment. */
@Composable
fun BookPreview(s: AppSettings, modifier: Modifier = Modifier) {
    val look = LocalLook.current
    val c = LocalColors.current
    val family = Fonts.book(bookFontFor(look, s))
    val style = TextStyle(
        fontFamily = family,
        fontSize = s.size.sp,
        lineHeight = (s.size * s.leading).sp,
        color = c.ink,
        textAlign = if (s.justify) TextAlign.Justify else TextAlign.Start,
        hyphens = if (s.hyphens) Hyphens.Auto else Hyphens.None,
        lineBreak = LineBreak.Paragraph,
    )
    val pad = when (s.margins) { Margins.Narrow -> 12.dp; Margins.Normal -> 22.dp; Margins.Wide -> 36.dp }
    Column(modifier.clipToBounds().padding(horizontal = pad)) {
        Text(Sample.previewHeading, style = style.copy(fontSize = (s.size * 0.85f).sp, fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.height((s.size * 0.9f).dp))
        Sample.previewParagraphs.forEach { p ->
            Text(p, style = style, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height((s.size * 0.5f).dp))
        }
    }
}

/** A live, scaled-down render of a look's library with the sample books. */
@Composable
fun LookThumb(look: Look, s: AppSettings, systemDark: Boolean) {
    ReaderTheme(look, resolveMode(look, s, systemDark), s.warmth / 100f) {
        Scaled(412.dp, 620.dp) { look.Library(Sample.library(), LibraryActions()) }
    }
}

/**
 * Lays [content] out at [w]×[h] and scales it down to the available width. Not interactive:
 * an empty pointer target sits on top, so taps reach the enclosing card instead of the content.
 */
@Composable
fun Scaled(w: Dp, h: Dp, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    Layout(
        content = {
            Box { content() }
            Box(Modifier.pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } })
        },
        modifier = Modifier.clipToBounds(),
    ) { measurables, constraints ->
        val vw = with(d) { w.roundToPx() }
        val vh = with(d) { h.roundToPx() }
        val scale = if (constraints.hasBoundedWidth) constraints.maxWidth / vw.toFloat() else 1f
        val placeables = measurables.map { it.measure(Constraints.fixed(vw, vh)) }
        layout((vw * scale).roundToInt(), (vh * scale).roundToInt()) {
            placeables.forEach {
                it.placeWithLayer(0, 0) {
                    scaleX = scale; scaleY = scale
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                }
            }
        }
    }
}
