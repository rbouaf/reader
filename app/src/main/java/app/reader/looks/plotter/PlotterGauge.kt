package app.reader.looks.plotter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.reader.theme.Colors
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The protractor for the current book (the mockup's `gauge()`): a 180 degree drafted scale with
 * a hatched sector for what's read, one mark inside per chapter, an angular dimension, a leader
 * from the title, and the length as the overall dimension. Drawn in the mockup's viewBox
 * (-8 0 376 226) scaled to the width; strokes keep the two pen weights, text scales.
 *
 * [chapters] draws the chapter marks when known; [length] is the overall dimension's label.
 */
@Composable
fun Gauge(progress: Float, percent: Int, chapters: Int?, length: String, modifier: Modifier = Modifier) {
    val c = colors()
    val tm = rememberTextMeasurer()
    Canvas(modifier.fillMaxWidth().aspectRatio(376f / 226f)) { drawGauge(c, tm, progress, percent, chapters, length) }
}

private fun DrawScope.drawGauge(c: Colors, tm: TextMeasurer, progress: Float, percent: Int, chapters: Int?, length: String) {
    val s = size.width / 376f
    fun X(v: Float) = (v + 8f) * s
    fun Y(v: Float) = v * s
    val hl = pen(HL); val hv = pen(HV)
    val ink = c.ink
    val p = progress.coerceIn(0f, 0.999f)
    val cx = 180f; val cy = 196f; val R = 150f; val r2 = 134f; val r1 = 112f
    fun ang(f: Float) = (PI * (1 + f)).toFloat()
    fun pt(f: Float, r: Float) = Offset(X(cx + r * cos(ang(f))), Y(cy + r * sin(ang(f))))
    fun ln(f: Float, ra: Float, rb: Float, w: Float) = drawLine(ink, pt(f, ra), pt(f, rb), w, StrokeCap.Butt)
    fun arcPath(path: Path, r: Float, fa: Float, fb: Float) {
        // SVG sweep from fa to fb along the top half; Compose arcs are in degrees, clockwise from +x
        val rr = r * s
        path.arcTo(
            androidx.compose.ui.geometry.Rect(X(cx) - rr, Y(cy) - rr, X(cx) + rr, Y(cy) + rr),
            180f + fa * 180f, (fb - fa) * 180f, false,
        )
    }
    fun arc(r: Float, fa: Float, fb: Float, w: Float) {
        val path = Path().apply { val a = pt(fa, r); moveTo(a.x, a.y); arcPath(this, r, fa, fb) }
        drawPath(path, ink, style = Stroke(w))
    }

    // hatched sector between the two inner rings, for what's read
    if (p > 0f) {
        val sector = Path().apply {
            val a = pt(0f, r2); moveTo(a.x, a.y)
            arcPath(this, r2, 0f, p)
            val b = pt(p, r1); lineTo(b.x, b.y)
            arcPath(this, r1, p, 0f)
            close()
        }
        clipPath(sector) {
            // 45 degree hatch every 5 units ( / ), fine pen
            val step = 5f * s * sqrt(2f)
            val span = size.width + size.height
            var k = -size.height
            while (k < span) {
                drawLine(ink, Offset(k, size.height), Offset(k + size.height, 0f), hl)
                k += step
            }
        }
    }
    arc(r2, 0f, 1f, hl); arc(r1, 0f, 1f, hl); arc(R, 0f, 1f, hl)

    // graduations: every 1, longer at 5, heavy at 10 with figures
    val sc = PT.mono(fs(10f * s), ink)
    for (i in 0..100) {
        val f = i / 100f
        val inner = if (i % 10 != 0) (if (i % 5 != 0) R - 5 else R - 9) else R - 14
        ln(f, inner, R, if (i % 10 != 0) hl else hv)
        if (i % 10 == 0) {
            val at = pt(f, R + 12)
            centredText(tm, "$i", sc, at.x, at.y + 3.5f * s, c, halo = 7f * s)
        }
    }
    if (chapters != null && chapters > 1) for (k in 1 until chapters) ln(k / chapters.toFloat(), r1 - 7, r1, hl)

    // the needle: fine to the centre, heavy across the scale
    ln(p, 6f, r1, hl); ln(p, r1, R + 3, hv)

    // angular dimension for what's read
    if (p > 0.07f) {
        val rd = 96f
        arc(rd, 0f, p, hl)
        val a0 = pt(0f, rd); val a1 = pt(p, rd)
        arrow(ink, a0.x, a0.y, ang(0f) - (PI / 2).toFloat(), 8f * s, 2.6f * s)
        arrow(ink, a1.x, a1.y, ang(p) + (PI / 2).toFloat(), 8f * s, 2.6f * s)
    }

    // baseline, centre mark
    drawLine(ink, Offset(X(8f), Y(cy)), Offset(X(352f), Y(cy)), hv)
    drawLine(ink, Offset(X(cx), Y(cy - 9)), Offset(X(cx), Y(cy + 9)), hl)
    drawCircle(ink, 4f * s, Offset(X(cx), Y(cy)), style = Stroke(hl))

    // overall dimension: the book's length
    val dy = cy + 22f; val L = cx - R; val Rx = cx + R
    val dim = PT.mono(fs(11.5f * s), ink)
    val labelW = tm.measure(length, dim).size.width / s
    val gap = labelW / 2f + 8f
    drawLine(ink, Offset(X(L), Y(cy + 4)), Offset(X(L), Y(dy + 6)), hl)
    drawLine(ink, Offset(X(Rx), Y(cy + 4)), Offset(X(Rx), Y(dy + 6)), hl)
    drawLine(ink, Offset(X(L), Y(dy)), Offset(X(cx - gap), Y(dy)), hl)
    drawLine(ink, Offset(X(cx + gap), Y(dy)), Offset(X(Rx), Y(dy)), hl)
    arrow(ink, X(L), Y(dy), PI.toFloat(), 8f * s, 2.6f * s)
    arrow(ink, X(Rx), Y(dy), 0f, 8f * s, 2.6f * s)
    centredText(tm, length, dim, X(cx), Y(dy + 4), c, halo = 0f)

    // leader from the title to the scale
    val t = pt(0.25f, R + 1)
    val k0 = Offset(X(-4f), Y(6f)); val k1 = Offset(X(22f), Y(6f))
    drawPath(Path().apply { moveTo(k0.x, k0.y); lineTo(k1.x, k1.y); lineTo(t.x, t.y) }, ink, style = Stroke(hl, join = StrokeJoin.Miter))
    arrow(ink, t.x, t.y, atan2(t.y - k1.y, t.x - k1.x), 8f * s, 2.6f * s)

    // the figure: 41 with a raised italic %, and "read"
    val num = PT.serif(fs(72f * s), ink)
    val unit = PT.serif(fs(26f * s), ink, italic = true)
    val n = tm.measure("$percent", num)
    val u = tm.measure("%", unit)
    val total = n.size.width + 2f * s + u.size.width
    val x0 = X(cx) - total / 2f
    val base = Y(cy - 30f)
    val haloW = 7f * s
    drawHalo(tm, "$percent", num, Offset(x0, base - n.firstBaseline), c, haloW)
    drawText(n, topLeft = Offset(x0, base - n.firstBaseline))
    val ux = x0 + n.size.width + 2f * s
    drawHalo(tm, "%", unit, Offset(ux, base - 30f * s - u.firstBaseline), c, haloW)
    drawText(u, topLeft = Offset(ux, base - 30f * s - u.firstBaseline))
    val lbl = PT.serif(fs(15f * s), c.mute, italic = true)
    centredText(tm, "read", lbl, X(cx), Y(cy - 13f), c, halo = haloW)
}

/** Font size from a pixel size, ignoring font scale (text in the gauge scales with the drawing). */
private fun DrawScope.fs(px: Float) = px.toSp()

private fun DrawScope.drawHalo(tm: TextMeasurer, text: String, style: TextStyle, at: Offset, c: Colors, w: Float) {
    if (w <= 0f) return
    drawText(tm, text, at, style.copy(color = c.paper, drawStyle = Stroke(w, join = StrokeJoin.Round)))
}

/** Text centred on [x] with its baseline on [baseline], over a paper halo like the SVG's paint-order stroke. */
private fun DrawScope.centredText(tm: TextMeasurer, text: String, style: TextStyle, x: Float, baseline: Float, c: Colors, halo: Float) {
    val r = tm.measure(text, style)
    val at = Offset(x - r.size.width / 2f, baseline - r.firstBaseline)
    drawHalo(tm, text, style, at, c, halo)
    drawText(r, topLeft = at)
}

/**
 * The index's little drafting scale: a fine baseline with eleven graduations, a heavy line for
 * what's read, and a filled marker where you are.
 */
@Composable
fun RowScale(progress: Float, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = colors().ink) {
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val hl = pen(HL); val hv = pen(HV)
        val w = size.width
        val u = size.height / 14f
        val base = 12.5f * u
        hLine(color, 0f, w, base, hl)
        for (i in 0..10) {
            val x = (w - hl) * i / 10f + hl / 2
            vLine(color, x, (if (i % 5 != 0) 9.5f else 6.5f) * u, base + hl / 2, hl)
        }
        val p = progress.coerceIn(0f, 1f)
        if (p > 0f) {
            val y = (base - hl / 2 - hv).let { kotlin.math.round(it) }
            drawRect(color, Offset(0f, y), Size(kotlin.math.round(w * p), hv))
        }
        if (p > 0f && p < 1f) {
            val x = w * p
            val tri = Path().apply { moveTo(x - 3.5f * u, 2f * u); lineTo(x + 3.5f * u, 2f * u); lineTo(x, 7.5f * u); close() }
            drawPath(tri, color)
        }
    }
}

/** One tick per file: heavy and tall for the counted ones, fine and short for the rest. */
@Composable
fun Tally(count: Int, of: Int, modifier: Modifier = Modifier) {
    val c = colors()
    val n = max(of, 1)
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val hl = pen(HL); val hv = pen(HV)
        // 9dp apart, closer when there are too many files to fit
        val step = min(9.dp.toPx(), max(2.dp.toPx(), (size.width - 2.dp.toPx()) / max(n - 1, 1)))
        val base = 13.5f / 14f * size.height
        hLine(c.ink, 0f, min(size.width, (n - 1) * step + 1.dp.toPx()), base, hl)
        for (i in 0 until n) {
            val x = i * step + 0.75.dp.toPx()
            if (x > size.width) break
            if (i < count) vLine(c.ink, x, 1.dp.toPx(), base + hl / 2, hv)
            else vLine(c.ink, x, 9.dp.toPx(), base + hl / 2, hl)
        }
    }
}
