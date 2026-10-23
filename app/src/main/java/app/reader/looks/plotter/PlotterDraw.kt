package app.reader.looks.plotter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.reader.theme.Colors
import app.reader.theme.Fonts
import app.reader.theme.LocalColors
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Plotter draws with exactly two pens, like the mockup's --hl / --hv: a fine one (0.5dp, never
 * thinner than one device pixel) and a heavy one (1.5dp). Straight lines are drawn as filled
 * rects on whole device pixels so the hairlines stay crisp; only the protractor's arcs and
 * diagonals are antialiased strokes.
 */

/** Fine pen. */
val HL = 0.5.dp
/** Heavy pen. */
val HV = 1.5.dp

/** A pen width in whole device pixels (at least one). */
fun Density.pen(w: Dp): Float = if (w <= 0.dp) 0f else max(1f, w.toPx().roundToInt().toFloat())

@Composable fun colors() = LocalColors.current

// ---------------- type ----------------

object PT {
    private val trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    private val platform = PlatformTextStyle(includeFontPadding = false)

    /** DM Mono: labels, fields, the table, annotations. */
    fun mono(size: TextUnit, color: Color, italic: Boolean = false, weight: Int = 400, lh: TextUnit = TextUnit.Unspecified) = TextStyle(
        fontFamily = Fonts.dmMono, fontSize = size, lineHeight = lh, color = color,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal, fontWeight = FontWeight(weight),
        lineHeightStyle = trim, platformStyle = platform, fontFeatureSettings = "tnum",
    )

    /** Instrument Serif: titles, italic for the current book and section names. */
    fun serif(size: TextUnit, color: Color, italic: Boolean = false, lh: TextUnit = TextUnit.Unspecified) = TextStyle(
        fontFamily = Fonts.instrumentSerif, fontSize = size, lineHeight = lh, color = color,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal, fontWeight = FontWeight.Normal,
        lineHeightStyle = trim, platformStyle = platform, letterSpacing = (-0.005).em,
    )

    /** `.lb`: the italic, lowercase, muted label. */
    fun label(c: Colors) = mono(10.5.sp, c.mute, italic = true)
}

/**
 * Trims a display line's box by fractions of its font size, like the mockup's tight
 * `line-height: .92` and negative margins: Instrument Serif's descent otherwise pads the
 * big titles far more than the browser does.
 */
fun Modifier.trimText(fontSize: TextUnit, top: Float, bottom: Float) = layout { m, c ->
    // measure unconstrained in height so the text never clips itself; the box shrinks, the glyphs don't
    val p = m.measure(c.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
    val px = fontSize.toPx()
    val t = (top * px).roundToInt(); val b = (bottom * px).roundToInt()
    layout(p.width, (p.height - t - b).coerceAtLeast(0)) { p.place(0, -t) }
}

// ---------------- line work ----------------

/**
 * Pen lines along the inside of an element's edges, on whole pixels. [dashed] draws the
 * bottom edge as a dashed line (the settings rows and summary use dashed hairlines).
 */
fun Modifier.edges(
    color: Color, start: Dp = 0.dp, top: Dp = 0.dp, end: Dp = 0.dp, bottom: Dp = 0.dp, dashedBottom: Boolean = false,
) = drawBehind {
    val l = pen(start); val t = pen(top); val r = pen(end); val b = pen(bottom)
    val w = size.width; val h = size.height
    if (t > 0) drawRect(color, Offset.Zero, Size(w, t))
    if (l > 0) drawRect(color, Offset.Zero, Size(l, h))
    if (r > 0) drawRect(color, Offset(w - r, 0f), Size(r, h))
    if (b > 0) {
        if (dashedBottom) hDash(color, 0f, w, h - b, b) else drawRect(color, Offset(0f, h - b), Size(w, b))
    }
}

/** A box drawn with one pen. */
fun Modifier.penBox(color: Color, w: Dp) = edges(color, w, w, w, w)

/** A dashed horizontal line from [x0] to [x1] with its top at [y]. */
fun DrawScope.hDash(color: Color, x0: Float, x1: Float, y: Float, w: Float, dash: Float = 3.dp.toPx(), gap: Float = 3.dp.toPx()) {
    val d = dash.roundToInt().toFloat(); val g = gap.roundToInt().toFloat()
    var x = x0
    val yy = y.roundToInt().toFloat()
    while (x < x1) { drawRect(color, Offset(x, yy), Size(minOf(d, x1 - x), w)); x += d + g }
}

/** A dashed vertical line from [y0] to [y1] centred on [x]. */
fun DrawScope.vDash(color: Color, x: Float, y0: Float, y1: Float, w: Float, dash: Float, gap: Float) {
    var y = y0
    val xx = snap(x, w)
    while (y < y1) { drawRect(color, Offset(xx, y), Size(w, minOf(dash, y1 - y))); y += dash + gap }
}

/** The left/top pixel edge for a line of width [w] centred on [at]. */
fun snap(at: Float, w: Float) = (at - w / 2f).roundToInt().toFloat()

fun DrawScope.hLine(color: Color, x0: Float, x1: Float, y: Float, w: Float) {
    val a = x0.roundToInt().toFloat(); val b = x1.roundToInt().toFloat()
    drawRect(color, Offset(a, snap(y, w)), Size(b - a, w))
}

fun DrawScope.vLine(color: Color, x: Float, y0: Float, y1: Float, w: Float) {
    val a = y0.roundToInt().toFloat(); val b = y1.roundToInt().toFloat()
    drawRect(color, Offset(snap(x, w), a), Size(w, b - a))
}

/** A filled arrowhead with its tip at ([x], [y]) pointing along [a] radians. */
fun DrawScope.arrow(color: Color, x: Float, y: Float, a: Float, len: Float, half: Float) {
    val bx = x - len * cos(a); val by = y - len * sin(a)
    val nx = -sin(a) * half; val ny = cos(a) * half
    val p = Path().apply { moveTo(x, y); lineTo(bx + nx, by + ny); lineTo(bx - nx, by - ny); close() }
    drawPath(p, color)
}

/**
 * Millimetre paper: a 6dp minor and 30dp major grid of one-pixel lines (the mockup's 1px
 * gradients at -1px), fixed to the element so it doesn't scroll with the content.
 */
fun Modifier.mmGrid(c: Colors) = drawBehind {
    drawRect(c.paper)
    val w = max(1f, floor(1.dp.toPx()))
    val step = 6.dp.toPx()
    val off = -1.dp.toPx()
    fun lines(major: Boolean) {
        val color = if (major) c.grid else c.gridMinor
        var k = 0
        while (true) {
            val x = off + k * step
            if (x > size.width) break
            if ((k % 5 == 0) == major) drawRect(color, Offset(x.roundToInt().toFloat(), 0f), Size(w, size.height))
            k++
        }
        k = 0
        while (true) {
            val y = off + k * step
            if (y > size.height) break
            if ((k % 5 == 0) == major) drawRect(color, Offset(0f, y.roundToInt().toFloat()), Size(size.width, w))
            k++
        }
    }
    lines(false); lines(true)
}

// ---------------- small parts ----------------

/** Section callout: a letter in a heavy-pen circle, as on a drawing. */
@Composable
fun Callout(letter: String) {
    val c = colors()
    Box(
        Modifier.size(22.dp).drawBehind {
            val w = pen(HV)
            drawCircle(c.paper, size.minDimension / 2)
            drawCircle(c.ink, size.minDimension / 2 - w / 2, style = Stroke(w))
        },
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, style = PT.serif(14.sp, c.ink, lh = 14.sp), modifier = Modifier.padding(top = 1.dp))
    }
}

/** The tag cut into a box's top border: callout, italic name, and a muted note. */
@Composable
fun Tag(letter: String?, name: String, note: String?, modifier: Modifier = Modifier) {
    val c = colors()
    Row(
        modifier.background(c.paper).padding(start = 6.dp, end = 8.dp).heightIn(min = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (letter != null) { Callout(letter); Spacer(Modifier.width(8.dp)) }
        Text(name, style = PT.serif(20.sp, c.ink, italic = true, lh = 24.sp), maxLines = 1)
        if (note != null) {
            Spacer(Modifier.width(8.dp))
            Text(note, style = PT.mono(11.sp, c.mute), maxLines = 1)
        }
    }
}

/** The back chevron: an L of the heavy pen turned 45 degrees. */
@Composable
fun Chevron(color: Color) {
    Canvas(Modifier.size(7.dp, 11.dp)) {
        val w = pen(HV)
        val arm = 7.dp.toPx() * 0.7071f
        val x0 = w / 2 + 1f; val cy = size.height / 2
        val p = Path().apply { moveTo(x0 + arm, cy - arm); lineTo(x0, cy); lineTo(x0 + arm, cy + arm) }
        drawPath(p, color, style = Stroke(w))
    }
}

/**
 * `.btn`: a fine-pen box, 44dp tall, DM Mono; pressed it fills with ink.
 * [back] adds the chevron. [dashed] draws the dashed "Back to page" variant.
 */
@Composable
fun PenButton(
    label: String, onClick: () -> Unit, modifier: Modifier = Modifier, back: Boolean = false,
    size: TextUnit = 13.5.sp, dashed: Boolean = false, boxHeight: Dp = 44.dp, padX: Dp = 14.dp,
) {
    val c = colors()
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val fg = if (pressed) c.paper else c.ink
    Box(modifier.heightIn(min = 44.dp).clickable(src, null, onClick = onClick), contentAlignment = Alignment.Center) {
        Row(
            Modifier.heightIn(min = boxHeight)
                .background(if (pressed) c.ink else c.paper)
                .then(if (dashed) Modifier.dashedBox(c.ink) else Modifier.penBox(c.ink, HL))
                .padding(horizontal = padX),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (back) { Spacer(Modifier.width(2.dp)); Chevron(fg); Spacer(Modifier.width(10.dp)) }
            Text(label, style = PT.mono(size, fg), maxLines = 1)
        }
    }
}

/** A fine dashed box (the mockup's `border-style: dashed`). */
fun Modifier.dashedBox(color: Color) = drawBehind {
    val w = pen(HL)
    val d = 3.dp.toPx(); val g = 3.dp.toPx()
    hDash(color, 0f, size.width, 0f, w, d, g)
    hDash(color, 0f, size.width, size.height - w, w, d, g)
    vDash(color, w / 2, 0f, size.height, w, d, g)
    vDash(color, size.width - w / 2, 0f, size.height, w, d, g)
}

/** 07's ( ) / (*) drawn: an open fine ring, or a heavy ring with a dot. */
@Composable
fun Radio(selected: Boolean, color: Color) {
    Canvas(Modifier.size(11.dp)) {
        val r = size.minDimension / 2
        if (selected) {
            val w = pen(HV)
            drawCircle(color, r - w / 2, style = Stroke(w))
            drawCircle(color, 2.4.dp.toPx())
        } else {
            val w = pen(HL)
            drawCircle(color, r - w / 2, style = Stroke(w))
        }
    }
}

/** The settings target: a fine inner ring, a heavy outer ring, and crosshair stubs. */
@Composable
fun SettingsMark(color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val u = size.width / 22f
        val hl = pen(HL); val hv = pen(HV)
        val c = center
        drawCircle(color, 3.2f * u, c, style = Stroke(hl))
        drawCircle(color, 7.5f * u, c, style = Stroke(hv))
        vLine(color, c.x, 0f, 5 * u, hl); vLine(color, c.x, 17 * u, 22 * u, hl)
        hLine(color, 0f, 5 * u, c.y, hl); hLine(color, 17 * u, 22 * u, c.y, hl)
    }
}

/** A crosshair registration mark (the preview page's corners). */
fun DrawScope.crosshair(color: Color, cx: Float, cy: Float, half: Float, w: Float) {
    hLine(color, cx - half, cx + half, cy, w)
    vLine(color, cx, cy - half, cy + half, w)
}
