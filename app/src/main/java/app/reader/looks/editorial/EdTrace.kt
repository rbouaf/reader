package app.reader.looks.editorial

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.reader.data.Book
import app.reader.looks.ChromeUi
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** Every trace in the library shares one time axis: the last sixteen weeks, today at the right. */
const val WINDOW_DAYS = 112

/** One day with reading in it: the pen climbs across the day from [x0] to [x1], ending at [v]. */
@Immutable
data class Sitting(val x0: Float, val x1: Float, val v: Float)

/**
 * A book's progress over the window as a recorder trace. x runs 0..1 over the window,
 * v is the share of the book read (0..1).
 *
 * The app records seconds read per day, not progress per day, so progress is spread over the
 * days in proportion to the time spent on each: the trace climbs on every day you read and stays
 * flat on the others, starting at whatever share was read before the window and ending exactly
 * at the book's progress today.
 */
@Immutable
data class History(
    val pts: List<Offset>,
    val sittings: List<Sitting>,
    val p: Float,
    val idle: Boolean,
    /** Progress gained in the last seven days (0..1). */
    val week: Float,
) {
    fun at(x: Float): Float {
        for (i in 1 until pts.size) {
            val a = pts[i - 1]; val b = pts[i]
            if (x <= b.x) return if (b.x == a.x) b.y else a.y + (b.y - a.y) * (x - a.x) / (b.x - a.x)
        }
        return pts.last().y
    }
}

fun history(b: Book, today: LocalDate = LocalDate.now()): History {
    val p = b.progress
    if (p <= 0f) return History(listOf(Offset(0f, 0f), Offset(1f, 0f)), emptyList(), 0f, idle = true, week = 0f)
    val days = b.activity(WINDOW_DAYS, today)
    val inWindow = days.sum()
    val total = max(b.state.activity.values.sum(), inWindow).toFloat()
    if (inWindow <= 0) return History(listOf(Offset(0f, p), Offset(1f, p)), emptyList(), p, idle = false, week = 0f)
    var v = p * (1f - inWindow / total)
    val pts = mutableListOf(Offset(0f, v))
    val sittings = mutableListOf<Sitting>()
    days.forEachIndexed { i, s ->
        if (s <= 0) return@forEachIndexed
        val x0 = i / WINDOW_DAYS.toFloat(); val x1 = (i + 1) / WINDOW_DAYS.toFloat()
        pts += Offset(x0, v)
        v = (v + p * s / total).coerceAtMost(p)
        pts += Offset(x1, v)
        sittings += Sitting(x0, x1, v)
    }
    pts += Offset(1f, p)
    return History(pts, sittings, p, idle = false, week = p * days.takeLast(7).sum() / total)
}

/** The whole library as one summed channel: books read since the start of the window. */
@Immutable
data class Aggregate(val trace: History, val read: Float)

fun aggregate(books: List<Book>, today: LocalDate = LocalDate.now()): Aggregate {
    val hs = books.map { history(it, today) }
    val xs = (0..WINDOW_DAYS).map { it / WINDOW_DAYS.toFloat() }
    val sums = xs.map { x -> hs.sumOf { it.at(x).toDouble() }.toFloat() }
    val gain = sums.map { it - sums.first() }
    val last = gain.last()
    val top = max(0.5f, ceil(last * 2f) / 2f)
    val pts = xs.mapIndexed { i, x -> Offset(x, gain[i] / top) }
    return Aggregate(History(pts, emptyList(), last / top, idle = false, week = 0f), last)
}

/**
 * `.tr`: chart paper with a recorder trace. Vertical rules every quarter ([majors]) and
 * sixteenth ([minors]) of the window, a base line, the trace, sitting marks, the 1.5px
 * "now" edge on the right and the pen head on it at today's value.
 */
@Composable
fun TraceStrip(
    h: History,
    modifier: Modifier,
    pad: Dp,
    majors: Boolean = true,
    minors: Boolean = true,
    topLine: Boolean = false,
    chapters: Int? = null,
    marks: Boolean = false,
    pen: Dp = 7.dp,
    stroke: Dp = 1.4.dp,
) {
    val c = colors()
    Canvas(modifier) {
        val w = size.width
        val hair = 1.dp.toPx().coerceAtLeast(1f)
        val top = pad.toPx(); val bottom = size.height - pad.toPx()
        fun x(f: Float) = f * w
        fun y(v: Float) = top + (1f - v.coerceIn(0f, 1f)) * (bottom - top)
        if (minors) for (i in 0 until 16) drawRect(c.grid, Offset(x(i / 16f), 0f), Size(hair, size.height))
        // without the sixteenths (phone index rows) the quarters drop to the grid colour, as in the mockup
        if (majors) for (i in 0 until 4) drawRect(if (minors) c.rule else c.grid, Offset(x(i / 4f), 0f), Size(hair, size.height))
        drawLine(c.rule, Offset(0f, y(0f)), Offset(w, y(0f)), hair)
        if (topLine) dashedH(c.rule, y(1f), on = 2.dp, off = 4.dp)
        if (chapters != null && chapters > 1) for (k in 1 until chapters) dashedH(c.rule, y(k / chapters.toFloat()))
        val path = Path().apply {
            h.pts.forEachIndexed { i, p -> if (i == 0) moveTo(x(p.x), y(p.y)) else lineTo(x(p.x), y(p.y)) }
        }
        drawPath(
            path, if (h.idle) c.mute else c.ink,
            style = Stroke(
                stroke.toPx(), join = StrokeJoin.Round,
                pathEffect = if (h.idle) PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 3.dp.toPx())) else null,
            ),
        )
        if (marks) {
            val s = 5.dp.toPx()
            h.sittings.forEach { st ->
                drawRect(c.ink, Offset(x(st.x1) - s / 2, y(st.v) - s / 2), Size(s, s))
                drawRect(c.ink, Offset(x(st.x0), size.height - 6.dp.toPx()), Size(hair, 6.dp.toPx()))
            }
        }
        // the now edge and the pen
        val edge = 1.5.dp.toPx()
        drawRect(c.ink, Offset(w - edge + hair, 0f), Size(edge, size.height))
        val ps = pen.toPx()
        val pw = 1.5.dp.toPx()
        val cx = w + hair - edge / 2
        val cy = y(h.p)
        drawRect(c.paper, Offset(cx - ps / 2, cy - ps / 2), Size(ps, ps))
        drawRect(c.ink, Offset(cx - ps / 2 + pw / 2, cy - ps / 2 + pw / 2), Size(ps - pw, ps - pw), style = Stroke(pw))
    }
}

/** `.axis`: an ink rule with the week marks under a trace, and the inverted "Now" tag at its right end. */
@Composable
fun Axis(short: Boolean, modifier: Modifier = Modifier) {
    val c = colors()
    val weeks = listOf(16, 12, 8, 4)
    Layout(
        content = {
            weeks.forEach { wk -> Text(if (short) "${wk}w" else "$wk wk", style = ET.mono(c, 10.5.sp, 1f, c.mute), maxLines = 1, softWrap = false) }
            Box(Modifier.background(c.ink).padding(horizontal = 5.dp, vertical = 4.dp)) {
                Text("Now", style = ET.mono(c, 10.5.sp, 1f, c.paper), maxLines = 1, softWrap = false)
            }
        },
        modifier = modifier.height(22.dp).edges(top = c.ink),
    ) { ms, cs ->
        val w = cs.maxWidth
        val ps = ms.map { it.measure(cs.copy(minWidth = 0, minHeight = 0)) }
        layout(w, 22.dp.roundToPx()) {
            weeks.forEachIndexed { i, wk ->
                val f = 1f - wk * 7f / WINDOW_DAYS
                val px = (f * w).roundToInt() - if (i == 0) 0 else ps[i].width / 2
                ps[i].place(px, 5.dp.roundToPx())
            }
            ps.last().place(w - ps.last().width / 2, -1.dp.roundToPx())
        }
    }
}

// ---------------- the scrubber ----------------

/**
 * The open book as a mini strip chart: x is the page, y how much of the book lies behind it,
 * so the pen rides the diagonal. Behind the reading position the climb is stepped, one step per
 * day read in the last four weeks, each as wide as the time spent that day; ahead of it the
 * line runs straight. Points are (x, v) in 0..1.
 */
fun scrubSteps(ui: ChromeUi): List<Offset> {
    val readTo = ui.fractionOf(ui.returnPage ?: ui.page).coerceIn(0f, 1f)
    val days = ui.activity.filter { it > 0 }
    val pts = mutableListOf(Offset(0f, 0f))
    if (days.isEmpty() || readTo <= 0f) {
        pts += Offset(readTo, readTo)
    } else {
        val tot = days.sum().toFloat()
        var a = 0f
        days.forEachIndexed { i, s ->
            val b = if (i == days.lastIndex) readTo else a + readTo * s / tot
            pts += Offset(a + (b - a) * 0.7f, a)
            pts += Offset(b, b)
            a = b
        }
    }
    pts += Offset(1f, 1f)
    return pts
}
