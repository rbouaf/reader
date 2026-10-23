package app.reader.looks.radar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.reader.theme.Colors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------- geometry

private const val RAD = (PI / 180).toFloat()

/** A point at bearing [a] degrees (clockwise from north) and radius [r] from [c]. */
fun pol(c: Offset, r: Float, a: Float) = Offset(c.x + r * sin(a * RAD), c.y - r * cos(a * RAD))

/** A filled sector from bearing [a1] to [a2]. */
fun DrawScope.sector(color: Color, c: Offset, r: Float, a1: Float, a2: Float) {
    if (a2 <= a1) return
    drawArc(color, a1 - 90f, a2 - a1, useCenter = true, topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2))
}

/** The sweep's afterglow: [n] stacked sectors fading back from bearing [ac]. */
fun DrawScope.sweepWedge(color: Color, c: Offset, r: Float, ac: Float, span: Float, n: Int, op: (Int) -> Float, k: Float = 1f) {
    if (span <= 0f) return
    for (i in 0 until n) sector(color.copy(alpha = (op(i) * k).coerceIn(0f, 1f)), c, r, ac - span + span * i / n, ac)
}

private fun DrawScope.line(color: Color, a: Offset, b: Offset, w: Float, fx: PathEffect? = null) = drawLine(color, a, b, w, pathEffect = fx)

private fun DrawScope.triangle(color: Color, a: Offset, b: Offset, t: Offset) {
    drawPath(Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(t.x, t.y); close() }, color)
}

/** 03's target reticle: four corner ticks around ([x], [y]). */
fun DrawScope.reticle(color: Color, x: Float, y: Float, q: Float, a: Float, w: Float) {
    val p = Path().apply {
        moveTo(x - q, y - q + a); lineTo(x - q, y - q); lineTo(x - q + a, y - q)
        moveTo(x + q - a, y - q); lineTo(x + q, y - q); lineTo(x + q, y - q + a)
        moveTo(x + q, y + q - a); lineTo(x + q, y + q); lineTo(x + q - a, y + q)
        moveTo(x - q + a, y + q); lineTo(x - q, y + q); lineTo(x - q, y + q - a)
    }
    drawPath(p, color, style = Stroke(w))
}

private fun DrawScope.centeredText(tm: TextMeasurer, text: String, style: TextStyle, x: Float, baseline: Float) {
    val l = tm.measure(text, style)
    drawText(l, topLeft = Offset(x - l.size.width / 2f, baseline - l.firstBaseline))
}

// ---------------------------------------------------------------- scope parts (record dial and summary)

/** Disc, the holding band between [rm] and [ro], dashed range rings, dotted axes. */
private fun DrawScope.scopeGround(c: Colors, o: Offset, ro: Float, rm: Float, rings: List<Float>, ns: Float) {
    drawCircle(c.paper, ro, o)
    drawCircle(c.grid.copy(alpha = 0.55f), (ro + rm) / 2, o, style = Stroke(ro - rm))
    val dash = PathEffect.dashPathEffect(floatArrayOf(2 * ns, 3 * ns))
    rings.forEach { f -> drawCircle(c.rule, rm * f, o, style = Stroke(ns, pathEffect = dash)) }
    val dots = PathEffect.dashPathEffect(floatArrayOf(1 * ns, 3 * ns))
    listOf(0f, 90f, 180f, 270f).forEach { a -> line(c.rule, o, pol(o, rm, a), ns, dots) }
}

/** 100 graduations inside the main ring, the rim bearings outside, and both rings. */
private fun DrawScope.scopeScale(c: Colors, o: Offset, ro: Float, rm: Float, u: Float, ns: Float) {
    for (i in 0 until 100) {
        val a = i * 3.6f
        val (len, col, w) = when {
            i % 10 == 0 -> Triple(6.5f, c.ink, 1.3f)
            i % 5 == 0 -> Triple(4f, c.mute, 1f)
            else -> Triple(2.5f, c.rule, 1f)
        }
        line(col, pol(o, rm, a), pol(o, rm - len * u, a), w * u)
    }
    for (a in 0 until 360 step 10) {
        val major = a % 30 == 0
        line(if (major) c.mute else c.rule, pol(o, ro, a.toFloat()), pol(o, ro + (if (major) 5f else 2.5f) * u, a.toFloat()), (if (major) 1.1f else 1f) * u)
    }
    drawCircle(c.mute, rm, o, style = Stroke(ns))
    drawCircle(c.rule, ro, o, style = Stroke(ns))
}

/** The contact: a blip with two ping rings and a reticle. */
private fun DrawScope.contact(c: Colors, p: Offset, u: Float, ns: Float, big: Float = 10f, mid: Float = 6.5f, q: Float = 9f, a: Float = 3.5f) {
    drawCircle(c.ink.copy(alpha = 0.22f), big * u, p, style = Stroke(ns))
    drawCircle(c.ink.copy(alpha = 0.5f), mid * u, p, style = Stroke(ns))
    drawCircle(c.ink, 3.4f * u, p)
    reticle(c.ink, p.x, p.y, q * u, a * u, 1.3f * ns)
}

// ---------------------------------------------------------------- the record's scope

/**
 * The current record's scope (viewBox −70…70): range rings, graduations, rim bearings, chapter
 * starts across the holding band, and the sweep to the progress bearing with its contact.
 * With [p] null there is no contact (no book yet). [scan] draws a free sweep at that bearing
 * instead (first scan in progress).
 */
@Composable
fun RecordDial(p: Float?, pct: String, chapters: List<Float>, size: Dp, modifier: Modifier = Modifier, scan: Float? = null) {
    val c = colors()
    val tm = androidx.compose.ui.text.rememberTextMeasurer()
    val unit = size.value / 140f
    val brgStyle = RS.display(c, (10f * unit).sp(), ls = 0f)
    val bigStyle = RS.display(c, (15f * unit).sp(), ls = -0.053f)
    Canvas(modifier.size(size)) {
        val u = this.size.width / 140f
        val ns = 1.dp.toPx()
        val o = center
        val ro = 52 * u; val rm = 45 * u
        scopeGround(c, o, ro, rm, listOf(0.36f, 0.68f), ns)
        val light = !c.isDark
        if (p != null) {
            val ac = p.coerceIn(0f, 1f) * 360f
            sector(c.ink.copy(alpha = if (light) 0.08f else 0.06f), o, rm, 0f, maxOf(0.5f, ac))
            sweepWedge(c.ink, o, rm, ac, minOf(56f, ac), 28, { 0.004f + 0.0012f * it }, if (light) 0.7f else 1f)
        } else if (scan != null) {
            sweepWedge(c.ink, o, rm, scan, 56f, 28, { 0.004f + 0.0012f * it }, if (light) 0.7f else 1f)
        }
        scopeScale(c, o, ro, rm, u, ns)
        chapters.forEach { f -> line(c.ink, pol(o, rm + u, f * 360f), pol(o, ro, f * 360f), 1.3f * u) }
        if (p != null) {
            val ac = p.coerceIn(0f, 1f) * 360f
            line(c.ink, o, pol(o, ro + u, ac), 1.3f * u)
            triangle(c.ink, pol(o, ro + 9 * u, ac - 3.4f), pol(o, ro + 9 * u, ac + 3.4f), pol(o, ro + 2 * u, ac))
            val bt = pol(o, ro + 17 * u, ac)
            centeredText(tm, bearing(p), brgStyle, bt.x, bt.y + 3.6f * u)
            contact(c, pol(o, rm * 0.68f, ac), u, ns)
        } else if (scan != null) {
            line(c.ink, o, pol(o, ro + u, scan), 1.3f * u)
        }
        drawCircle(c.paper, 15 * u, o)
        drawCircle(c.rule, 15 * u, o, style = Stroke(ns))
        centeredText(tm, pct, bigStyle.copy(color = if (p == null) c.mute else c.ink), o.x, o.y + 5 * u)
    }
}

/** "148°": progress as a bearing. */
fun bearing(p: Float) = (Math.round(p.coerceIn(0f, 1f) * 360)).toString().padStart(3, '0') + "°"

// ---------------------------------------------------------------- the row dial

enum class DialState { New, Read, Done }

/** The index's 26dp dial: a ring, a sector to the progress bearing, a blip on the rim. */
@Composable
fun RowDial(p: Float, state: DialState, color: Color, modifier: Modifier = Modifier) {
    val c = colors()
    Canvas(modifier.size(26.dp)) {
        val u = this.size.width / 26f
        val o = center
        val r = 10 * u
        if (state == DialState.New) drawCircle(c.mute, r, o, style = Stroke(u, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.6f * u, 2.2f * u))))
        else drawCircle(c.rule, r, o, style = Stroke(u))
        line(c.mute, Offset(o.x, o.y - 13 * u), Offset(o.x, o.y - 9.5f * u), u)
        when (state) {
            DialState.Done -> {
                drawCircle(color.copy(alpha = 0.2f), r, o)
                drawCircle(color, r, o, style = Stroke(2 * u))
                val d = 3.4f * u
                line(c.mute, Offset(o.x - d, o.y - d), Offset(o.x + d, o.y + d), 1.2f * u)
                line(c.mute, Offset(o.x + d, o.y - d), Offset(o.x - d, o.y + d), 1.2f * u)
            }
            DialState.Read -> {
                val a = p * 360f
                val e = pol(o, r, a)
                sector(color.copy(alpha = 0.2f), o, r, 0f, a)
                drawArc(color, -90f, a, false, Offset(o.x - r, o.y - r), Size(r * 2, r * 2), style = Stroke(2 * u))
                line(color, o, e, u)
                drawCircle(color, 2.3f * u, e)
            }
            DialState.New -> {}
        }
    }
}

// ---------------------------------------------------------------- the chrome's polar dial

/** The round position readout beside the scrubber (78dp): ticks, chapter bearings, a hand. */
@Composable
fun PolarDial(p: Float, chapters: List<Float>, modifier: Modifier = Modifier, size: Dp = 78.dp) {
    val c = colors()
    val tm = androidx.compose.ui.text.rememberTextMeasurer()
    val pctStyle = RS.display(c, (13f * size.value / 78f).sp(), ls = -0.06f)
    Canvas(modifier.size(size)) {
        val u = this.size.width / 78f
        val o = center
        val r = 33 * u
        drawCircle(c.rule, r, o, style = Stroke(u))
        for (i in 0 until 50) {
            val a = i * 7.2f
            line(c.rule, pol(o, r, a), pol(o, r - (if (i % 5 == 0) 4.5f else 2.5f) * u, a), u)
        }
        (listOf(0f) + chapters).forEach { f ->
            val a = f * 360f
            line(c.ink, pol(o, r + u, a), pol(o, r - 8 * u, a), 1.4f * u)
            triangle(c.ink, pol(o, r + 6 * u, a - 3.6f), pol(o, r + 6 * u, a + 3.6f), pol(o, r + 2 * u, a))
        }
        val ac = p.coerceIn(0f, 1f) * 360f
        sweepWedge(c.ink, o, r - 5 * u, ac, minOf(52f, ac), 18, { 0.026f })
        val h = pol(o, r - 2 * u, ac)
        line(c.ink, o, h, 1.3f * u)
        drawCircle(c.ink, 2.6f * u, h)
        drawCircle(c.paper, 12.5f * u, o)
        centeredText(tm, "${Math.round(p * 100)}", pctStyle, o.x, o.y + 4 * u)
    }
}

/** The face behind the big page number while scrubbing: a ring of 50 bearing ticks and a dashed inner ring. */
fun DrawScope.scopeFace(c: Colors, em: Float) {
    val o = center
    val r = size.minDimension / 2
    drawCircle(c.paper, r, o)
    drawCircle(c.mute, r - 0.5.dp.toPx(), o, style = Stroke(1.dp.toPx()))
    val outer = r - 0.12f * em
    val inner = outer - 0.14f * em
    val w = (0.6f * RAD) * outer
    for (i in 0 until 50) line(c.mute, pol(o, inner, i * 7.2f), pol(o, outer, i * 7.2f), w)
    drawCircle(c.rule, r - 0.3f * em, o, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))))
}

// ---------------------------------------------------------------- the summary scope

data class Blip(val key: String, val order: String, val a: Float, val r: Float, val state: DialState, val cur: Boolean)

/**
 * Every book as a contact on one scope (size [w]×[w] dp): angle is progress, range is when it was
 * last opened. Unread and finished books wait in the holding band. Draws in dp units.
 */
fun DrawScope.summaryScope(c: Colors, tm: TextMeasurer, blips: List<Blip>, curP: Float?, w: Float, tagStyle: TextStyle, pctStyle: TextStyle) {
    val u = size.width / w
    val ns = 1.dp.toPx()
    val o = center
    val ro = (w / 2 - 17) * u
    val rm = ro - 14 * u
    scopeGround(c, o, ro, rm, BANDS.take(3), ns)
    if (curP != null) sweepWedge(c.ink, o, rm, curP * 360f, 50f, 30, { 0.003f + 0.0006f * it }, if (c.isDark) 1f else 0.7f)
    scopeScale(c, o, ro, rm, u, ns)
    listOf(0, 25, 50, 75).forEach { v ->
        val q = pol(o, rm - 14 * u, v * 3.6f)
        centeredText(tm, "$v", pctStyle, q.x, q.y + 3 * u)
    }
    if (curP != null) {
        val ac = curP * 360f
        line(c.ink, o, pol(o, ro + u, ac), 1.3f * u)
        triangle(c.ink, pol(o, ro + 9 * u, ac - 2.6f), pol(o, ro + 9 * u, ac + 2.6f), pol(o, ro + 2 * u, ac))
    }
    line(c.ink, Offset(o.x - 4 * u, o.y), Offset(o.x + 4 * u, o.y), ns)
    line(c.ink, Offset(o.x, o.y - 4 * u), Offset(o.x, o.y + 4 * u), ns)

    val pts = blips.map { it to pol(Offset(w / 2, w / 2), it.r * (w / 2 - 31), it.a) }
    val tags = placeTags(pts, w)
    pts.forEach { (b, q0) ->
        val q = Offset(q0.x * u, q0.y * u)
        when {
            b.cur -> contact(c, q, u, ns, big = 9f, mid = 6f, q = 8.5f, a = 3f)
            b.state == DialState.Read -> drawCircle(c.ink.copy(alpha = 0.82f), 2.8f * u, q)
            b.state == DialState.New -> { drawCircle(c.paper, 2.8f * u, q); drawCircle(c.mute, 2.8f * u, q, style = Stroke(1.2f * u)) }
            else -> {
                val d = 3 * u
                line(c.mute, Offset(q.x - d, q.y - d), Offset(q.x + d, q.y + d), 1.4f * u)
                line(c.mute, Offset(q.x + d, q.y - d), Offset(q.x - d, q.y + d), 1.4f * u)
            }
        }
    }
    tags.forEach { (b, box) ->
        val st = when {
            b.cur -> tagStyle.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(600))
            b.state != DialState.Read -> tagStyle.copy(color = c.mute)
            else -> tagStyle
        }
        centeredText(tm, b.order, st, (box.x + box.w / 2) * u, (box.y + box.h / 2 + 3.5f) * u)
    }
}

/** Range rings: today, this week, last week, earlier. */
val BANDS = listOf(0.3f, 0.55f, 0.78f, 1f)

private data class Box(val x: Float, val y: Float, val w: Float, val h: Float)

private fun over(a: Box, b: Box) =
    maxOf(0f, minOf(a.x + a.w, b.x + b.w) - maxOf(a.x, b.x)) * maxOf(0f, minOf(a.y + a.h, b.y + b.h) - maxOf(a.y, b.y))

/** Number tags: try eight spots around each blip and keep the emptiest (the mockup's placement). */
private fun placeTags(pts: List<Pair<Blip, Offset>>, w: Float): List<Pair<Blip, Box>> {
    val c = Offset(w / 2, w / 2)
    val ro = w / 2 - 17
    val rm = ro - 14
    val obst = mutableListOf<Box>()
    listOf(0, 25, 50, 75).forEach { v -> val q = pol(c, rm - 14, v * 3.6f); obst += Box(q.x - 7, q.y - 5, 14f, 10f) }
    obst += Box(c.x - 5, c.y - 5, 10f, 10f)
    pts.forEach { (b, p) -> val q = if (b.cur) 11f else 5f; obst += Box(p.x - q, p.y - q, q * 2, q * 2) }
    val placed = mutableListOf<Box>()
    val out = mutableListOf<Pair<Blip, Box>>()
    pts.sortedWith(compareByDescending<Pair<Blip, Offset>> { it.first.cur }.thenBy { it.first.order }).forEach { (b, p) ->
        val tw = if (b.cur) 15f else 13f; val th = 11f; val d = if (b.cur) 14f else 7f
        if (b.state != DialState.Read) {
            val q = pol(c, ro + 11, b.a)
            val bx = Box(q.x - tw / 2, q.y - th / 2, tw, th)
            placed += bx; out += b to bx; return@forEach
        }
        var best: Pair<Float, Box>? = null
        listOf(45, 135, 315, 225, 90, 270, 0, 180).forEachIndexed { di, dir ->
            val s = sin(dir * RAD); val co = cos(dir * RAD)
            val ex = p.x + d * s * 1.3f; val ey = p.y - d * co
            val bx = Box(if (s > 0.2f) ex else if (s < -0.2f) ex - tw else ex - tw / 2, ey - th / 2, tw, th)
            var pen = di * 2f
            placed.forEach { pen += over(bx, it) * 8 }
            obst.forEach { q -> if (kotlin.math.abs(q.x + q.w / 2 - p.x) < 0.5f && kotlin.math.abs(q.y + q.h / 2 - p.y) < 0.5f) return@forEach; pen += over(bx, q) * 3 }
            if (best == null || pen < best!!.first) best = pen to bx
        }
        placed += best!!.second; out += b to best!!.second
    }
    return out
}

/** Where blip [b] sits, in dp from the scope's top-left, for hit testing. */
fun blipAt(b: Blip, w: Float) = pol(Offset(w / 2, w / 2), b.r * (w / 2 - 31), b.a)

// ---------------------------------------------------------------- small glyphs

enum class Glyphs { Gear, Back, Contact, End, Contents, Search, Aloud, Stop, Play, Pause, Prev, Next, Close }

/** A small line glyph drawn in a 16-unit box. */
@Composable
fun Glyph(g: Glyphs, color: Color, size: Dp = 16.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        scale(this.size.width / 16f, pivot = Offset.Zero) { drawGlyph(g, color) }
    }
}

private fun DrawScope.drawGlyph(g: Glyphs, col: Color) {
    val s = Stroke(1.2f, cap = StrokeCap.Butt)
    fun path(block: Path.() -> Unit) = drawPath(Path().apply(block), col, style = s)
    when (g) {
        Glyphs.Gear -> {
            drawCircle(col, 2.6f, Offset(8f, 8f), style = s)
            drawCircle(col, 5.6f, Offset(8f, 8f), style = s)
            path {
                moveTo(8f, 0f); lineTo(8f, 2.4f); moveTo(8f, 13.6f); lineTo(8f, 16f)
                moveTo(0f, 8f); lineTo(2.4f, 8f); moveTo(13.6f, 8f); lineTo(16f, 8f)
                moveTo(2.3f, 2.3f); lineTo(4f, 4f); moveTo(12f, 12f); lineTo(13.7f, 13.7f)
                moveTo(2.3f, 13.7f); lineTo(4f, 12f); moveTo(12f, 4f); lineTo(13.7f, 2.3f)
            }
        }
        Glyphs.Back -> drawPath(Path().apply { moveTo(10.5f, 2.5f); lineTo(5f, 8f); lineTo(10.5f, 13.5f) }, col, style = Stroke(2f))
        Glyphs.Contact -> {
            val k = 16f / 12f
            drawCircle(col, 5f * k, Offset(8f, 8f), style = Stroke(1.4f))
            drawCircle(col, 1.6f * k, Offset(8f, 8f))
            drawLine(col, Offset(8f, 8f - 5f * k), Offset(8f, 8f - 2.6f * k), 1.4f)
        }
        Glyphs.End -> {
            val k = 16f / 14f
            drawCircle(col.copy(alpha = col.alpha * 0.4f), 6f * k, Offset(8f, 8f), style = Stroke(1.1f))
            drawCircle(col.copy(alpha = col.alpha * 0.7f), 3.6f * k, Offset(8f, 8f), style = Stroke(1.1f))
            drawCircle(col, 1.6f * k, Offset(8f, 8f))
        }
        Glyphs.Contents -> path {
            moveTo(1f, 3f); lineTo(3f, 3f); moveTo(5f, 3f); lineTo(15f, 3f)
            moveTo(1f, 8f); lineTo(3f, 8f); moveTo(7f, 8f); lineTo(15f, 8f)
            moveTo(1f, 13f); lineTo(3f, 13f); moveTo(7f, 13f); lineTo(15f, 13f)
        }
        Glyphs.Search -> {
            drawCircle(col, 4.8f, Offset(6.5f, 6.5f), style = s)
            drawCircle(col, 1f, Offset(6.5f, 6.5f))
            drawLine(col, Offset(10f, 10f), Offset(15f, 15f), 1.4f)
        }
        Glyphs.Aloud -> {
            drawCircle(col, 1.8f, Offset(3f, 8f))
            drawArc(col, -45f, 90f, false, Offset(3f - 5f, 3f), Size(10f, 10f), style = s)
            drawArc(col, -45f, 90f, false, Offset(3f - 9.5f, -1.5f), Size(19f, 19f), style = s)
        }
        Glyphs.Stop -> drawRect(col, Offset(3.5f, 3.5f), Size(9f, 9f), style = s)
        Glyphs.Close -> path { moveTo(3f, 3f); lineTo(13f, 13f); moveTo(13f, 3f); lineTo(3f, 13f) }
        Glyphs.Play -> drawPath(Path().apply { moveTo(4f, 2.5f); lineTo(13.5f, 8f); lineTo(4f, 13.5f); close() }, col, style = Fill)
        Glyphs.Pause -> { drawRect(col, Offset(4f, 3f), Size(2.4f, 10f)); drawRect(col, Offset(9.6f, 3f), Size(2.4f, 10f)) }
        Glyphs.Prev -> {
            drawPath(Path().apply { moveTo(8f, 3.5f); lineTo(1.5f, 8f); lineTo(8f, 12.5f); close() }, col)
            drawPath(Path().apply { moveTo(14.5f, 3.5f); lineTo(8f, 8f); lineTo(14.5f, 12.5f); close() }, col)
        }
        Glyphs.Next -> {
            drawPath(Path().apply { moveTo(1.5f, 3.5f); lineTo(8f, 8f); lineTo(1.5f, 12.5f); close() }, col)
            drawPath(Path().apply { moveTo(8f, 3.5f); lineTo(14.5f, 8f); lineTo(8f, 12.5f); close() }, col)
        }
    }
}

/** The Continue bar's arrow (26×12). */
@Composable
fun Arrow(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(26.dp, 12.dp)) {
        val k = this.size.width / 26f
        drawLine(color, Offset(0f, 6 * k), Offset(24 * k, 6 * k), 1.3f * k)
        drawPath(Path().apply { moveTo(19 * k, k); lineTo(24 * k, 6 * k); lineTo(19 * k, 11 * k) }, color, style = Stroke(1.3f * k))
    }
}

/** The sort control's radio blip: an empty ring, or a blip with a ping ring when selected. */
@Composable
fun SortBlip(selected: Boolean, modifier: Modifier = Modifier) {
    val c = colors()
    Canvas(modifier.size(13.dp)) {
        val u = this.size.width / 13f
        if (selected) {
            drawCircle(c.ink, 2.6f * u)
            drawCircle(c.ink, 5.55f * u, style = Stroke(0.9f * u))
        } else {
            drawCircle(c.rule, 6f * u, style = Stroke(u))
            drawCircle(c.rule, 1f * u)
        }
    }
}

/** Float → sp, for sizes computed from a dial's scale. */
fun Float.sp() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Sp)
