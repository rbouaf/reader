package app.reader.looks.editorial

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.reader.theme.Colors
import app.reader.theme.Fonts
import app.reader.theme.LocalColors

/**
 * Editorial's type: Bodoni Moda sets names, titles, big figures and every field label (in
 * italic); Fragment Mono prints the data. Bodoni comes in two optical sizes, so the display
 * cut (opsz 72) is used from 24sp up and the text cut (opsz 11) below.
 */
object ET {
    private val trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

    private fun bodoni(size: TextUnit) = if (size.value >= 24f) Fonts.bodoniDisplay else Fonts.bodoniText

    /** Fragment Mono: the printed data. */
    fun mono(c: Colors, size: TextUnit = 12.5.sp, lh: Float = 1.35f, color: Color = c.ink) = TextStyle(
        fontFamily = Fonts.fragmentMono, fontSize = size, lineHeight = size * lh, color = color,
        lineHeightStyle = trim, fontFeatureSettings = "tnum",
    )

    /** Bodoni roman, weight 500: titles and figures. */
    fun disp(c: Colors, size: TextUnit, lh: Float = 1.1f, color: Color = c.ink, weight: FontWeight = FontWeight.Medium, tracking: Float = 0f) = TextStyle(
        fontFamily = bodoni(size), fontSize = size, lineHeight = size * lh, color = color, fontWeight = weight,
        letterSpacing = tracking.em, lineHeightStyle = trim,
    )

    /** Bodoni italic: the voice of every field name, column head and tag. */
    fun it(c: Colors, size: TextUnit = 17.sp, lh: Float = 1.1f, color: Color = c.mute) = TextStyle(
        fontFamily = bodoni(size), fontSize = size, lineHeight = size * lh, color = color,
        fontStyle = FontStyle.Italic, fontWeight = FontWeight.Normal, lineHeightStyle = trim,
    )
}

@Composable fun colors() = LocalColors.current

/** Pressed elements invert: ink ground, paper type and lines. */
fun Colors.inverted() = copy(ink = paper, mute = paper.copy(alpha = 0.75f), rule = paper.copy(alpha = 0.5f), grid = paper.copy(alpha = 0.25f), paper = ink)

// ---------------- line work ----------------

private fun DrawScope.hair() = 1.dp.toPx().coerceAtLeast(1f)

fun DrawScope.dashedH(color: Color, y: Float, x0: Float = 0f, x1: Float = size.width, on: Dp = 3.dp, off: Dp = 3.dp, width: Float = hair()) {
    drawLine(color, Offset(x0, y), Offset(x1, y), width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(on.toPx(), off.toPx())))
}

fun DrawScope.dashedV(color: Color, x: Float, y0: Float = 0f, y1: Float = size.height, on: Dp = 3.dp, off: Dp = 3.dp, width: Float = hair()) {
    drawLine(color, Offset(x, y0), Offset(x, y1), width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(on.toPx(), off.toPx())))
}

/** Solid hairlines on chosen edges, each with its own colour and width. */
fun Modifier.edges(
    top: Color? = null, bottom: Color? = null, left: Color? = null, right: Color? = null,
    topW: Dp = 1.dp, bottomW: Dp = 1.dp, leftW: Dp = 1.dp, rightW: Dp = 1.dp,
) = drawBehind {
    if (top != null) drawRect(top, Offset.Zero, Size(size.width, topW.toPx()))
    if (bottom != null) drawRect(bottom, Offset(0f, size.height - bottomW.toPx()), Size(size.width, bottomW.toPx()))
    if (left != null) drawRect(left, Offset.Zero, Size(leftW.toPx(), size.height))
    if (right != null) drawRect(right, Offset(size.width - rightW.toPx(), 0f), Size(rightW.toPx(), size.height))
}

/** A 1px box outline. */
fun Modifier.outline(color: Color, width: Dp = 1.dp, dashed: Boolean = false) = drawBehind {
    val w = width.toPx()
    drawRect(
        color, Offset(w / 2, w / 2), Size(size.width - w, size.height - w),
        style = Stroke(w, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())) else null),
    )
}

/** The settings and chart paper: a square grid. */
fun Modifier.gridPaper(color: Color, step: Dp) = drawBehind {
    val s = step.toPx()
    val w = hair()
    var x = 0f
    while (x < size.width) { drawRect(color, Offset(x, 0f), Size(w, size.height)); x += s }
    var y = 0f
    while (y < size.height) { drawRect(color, Offset(0f, y), Size(size.width, w)); y += s }
}

// ---------------- buttons ----------------

@Composable
fun rememberPress(): Pair<MutableInteractionSource, Boolean> {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    return src to pressed
}

/**
 * `.btn`: a 1px ink box with Fragment Mono 13, inverting while pressed. [back] adds the chevron.
 * [dashed] is the "Back to page N" variant.
 */
@Composable
fun EdButton(
    label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    back: Boolean = false, gear: Boolean = false, dashed: Boolean = false, size: TextUnit = 13.sp,
) {
    val c = colors()
    val (src, pressed) = rememberPress()
    val fg = if (pressed) c.paper else c.ink
    Row(
        modifier.heightIn(min = 44.dp).background(if (pressed) c.ink else c.paper).outline(c.ink, dashed = dashed)
            .clickable(src, null, onClick = onClick).padding(horizontal = if (dashed) 10.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (back) Chevron(fg, Modifier.padding(start = 2.dp))
        if (gear) Gear(fg)
        Text(label, style = ET.mono(c, size, 1f, fg), maxLines = 1)
    }
}

/** `<`: two 1.5px strokes. */
@Composable
fun Chevron(color: Color, modifier: Modifier = Modifier, left: Boolean = true) {
    Canvas(modifier.size(8.dp, 12.dp)) {
        val w = 1.5.dp.toPx()
        val x0 = if (left) size.width * 0.75f else size.width * 0.25f
        val x1 = if (left) size.width * 0.25f else size.width * 0.75f
        drawLine(color, Offset(x0, size.height * 0.15f), Offset(x1, size.height / 2), w, StrokeCap.Square)
        drawLine(color, Offset(x1, size.height / 2), Offset(x0, size.height * 0.85f), w, StrokeCap.Square)
    }
}

/** The settings gear: two rings and eight spokes, on a 16-unit grid. */
@Composable
fun Gear(color: Color) {
    Canvas(Modifier.size(16.dp)) {
        val u = size.width / 16f
        val st = Stroke(1.2f * u)
        drawCircle(color, 2.6f * u, center, style = st)
        drawCircle(color, 5.6f * u, center, style = st)
        fun l(x0: Float, y0: Float, x1: Float, y1: Float) = drawLine(color, Offset(x0 * u, y0 * u), Offset(x1 * u, y1 * u), 1.2f * u)
        l(8f, 0f, 8f, 2.4f); l(8f, 13.6f, 8f, 16f); l(0f, 8f, 2.4f, 8f); l(13.6f, 8f, 16f, 8f)
        l(2.3f, 2.3f, 4f, 4f); l(12f, 12f, 13.7f, 13.7f); l(2.3f, 13.7f, 4f, 12f); l(12f, 4f, 13.7f, 2.3f)
    }
}

/** The Continue arrow: `→` drawn at 22×10. */
@Composable
fun Arrow(color: Color) {
    Canvas(Modifier.size(22.dp, 10.dp)) {
        val u = size.width / 22f
        val w = 1.3f * u
        drawLine(color, Offset(0f, 5 * u), Offset(20 * u, 5 * u), w)
        drawLine(color, Offset(16 * u, 1 * u), Offset(20 * u, 5 * u), w)
        drawLine(color, Offset(20 * u, 5 * u), Offset(16 * u, 9 * u), w)
    }
}
