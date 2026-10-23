package app.reader.looks.trace

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.reader.data.Book
import app.reader.looks.ChromeActions
import app.reader.looks.ChromeUi
import app.reader.looks.common.scrubber
import app.reader.looks.percent
import kotlin.math.roundToInt

// ---------------- pen work: every line is one pixel of ink, snapped to the pixel grid ----------------

private fun DrawScope.hair() = 1.dp.toPx().roundToInt().coerceAtLeast(1).toFloat()

/** A solid horizontal rule whose top edge is at [y]. */
private fun DrawScope.hRule(y: Float, color: Color, x0: Float = 0f, x1: Float = size.width) {
    val w = hair()
    drawRect(color, Offset(x0, y.roundToInt().toFloat()), Size(x1 - x0, w))
}

/** A solid vertical rule whose left edge is at [x]. */
private fun DrawScope.vRule(x: Float, color: Color, y0: Float = 0f, y1: Float = size.height) {
    val w = hair()
    drawRect(color, Offset(x.roundToInt().toFloat(), y0), Size(w, y1 - y0))
}

/** A dotted horizontal rule: [on] dp of ink every [period] dp (the mockup's `stroke-dasharray`). */
private fun DrawScope.hDots(y: Float, color: Color, on: Float = 1f, period: Float = 4f, x0: Float = 0f, x1: Float = size.width) {
    val w = hair(); val step = period.dp.toPx(); val len = on.dp.toPx().coerceAtLeast(w)
    val yy = y.roundToInt().toFloat()
    var x = x0
    while (x < x1) { drawRect(color, Offset(x.roundToInt().toFloat(), yy), Size(minOf(len, x1 - x), w)); x += step }
}

/** A dotted vertical rule. */
private fun DrawScope.vDots(x: Float, color: Color, on: Float = 1f, period: Float = 4f, y0: Float = 0f, y1: Float = size.height) {
    val w = hair(); val step = period.dp.toPx(); val len = on.dp.toPx().coerceAtLeast(w)
    val xx = x.roundToInt().toFloat()
    var y = y0
    while (y < y1) { drawRect(color, Offset(xx, y.roundToInt().toFloat()), Size(w, minOf(len, y1 - y))); y += step }
}

/** A filled square of side [s] dp with its top-left at ([x], [y]). */
private fun DrawScope.square(x: Float, y: Float, s: Float, color: Color, h: Float = s) =
    drawRect(color, Offset(x.roundToInt().toFloat(), y.roundToInt().toFloat()), Size(s.dp.toPx(), h.dp.toPx()))

/** The stepped pen line through unit-space [pts] (y up), stretched over [top]..[bottom]. */
private fun DrawScope.trace(pts: List<Offset>, color: Color, top: Float, bottom: Float, dash: PathEffect? = null) {
    if (pts.size < 2) return
    val h = bottom - top
    val half = hair() / 2
    val path = Path()
    pts.forEachIndexed { i, p ->
        val x = (p.x * size.width).coerceIn(half, size.width - half)
        val y = (top + (1f - p.y) * h).coerceIn(top + half, bottom - half)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color, style = Stroke(hair(), join = StrokeJoin.Miter, pathEffect = dash))
}

// ---------------- the index's tiny traces: one text line tall ----------------

@Composable
fun MiniTrace(b: Book, width: Dp) {
    val g = grid(); val c = colors()
    val h = remember(b) { history(b) }
    val color = if (b.isFinished) c.mute else c.ink
    Canvas(Modifier.width(width).height(g.lhDp)) {
        val top = 3.dp.toPx(); val bottom = size.height - 4.dp.toPx()
        val base = bottom - hair()
        if (h.p <= 0f) hDots(base, c.rule) else hRule(base, c.rule)
        trace(h.pts, color, top, bottom)
        if (h.p > 0f && h.p < 1f) square(size.width - 3.dp.toPx(), top + (1f - h.p) * (bottom - top) - 2.dp.toPx(), 4f, color)
    }
}

// ---------------- the record's channel: chapter gridlines, 4-week divisions, sittings, the now cursor and pen ----------------

@Composable
fun Channel(b: Book) {
    val g = grid(); val c = colors()
    val h = remember(b) { history(b) }
    val chapters = b.state.chapters ?: 0
    val wk = (h.week * 100).roundToInt()
    Column(Modifier.padding(top = g.lines(0.5f))) {
        Row {
            Canvas(Modifier.weight(1f).height(g.lines(5))) {
                val H = size.height; val W = size.width
                for (w in listOf(4, 8, 12)) vDots(xOf(w * 7f) * W, c.rule)
                if (chapters > 1 && H / chapters >= 4.dp.toPx()) for (k in 1 until chapters) hDots((1f - k.toFloat() / chapters) * H, c.rule, period = 3f)
                hDots(0f, c.rule)
                hRule(H - hair(), c.ink)
                trace(h.pts, c.ink, 0f, H)
                h.sittings.forEach { s ->
                    square(xOf(s.d1) * W - 1.dp.toPx(), (1f - s.v1) * H - 1.dp.toPx(), 3f, c.ink)
                    square(xOf(s.d0) * W, H - 4.dp.toPx(), 1f, c.ink, h = 4f)
                }
                vRule(W - hair(), c.ink)
                square(W - 4.dp.toPx(), (1f - h.p) * H - 3.dp.toPx(), 7f, c.ink)
            }
            Spacer(Modifier.width(g.ch(1)))
            BoxWithConstraints(Modifier.width(g.ch(5)).height(g.lines(5))) {
                val y = (maxHeight * (1f - h.p)).coerceIn(g.lines(0.5f), maxHeight - g.lines(0.5f)) - g.lines(0.5f)
                Text("${percent(b)}%", style = RT.title(g, c), maxLines = 1, softWrap = false, modifier = Modifier.offset(y = y))
            }
        }
        Row {
            Axis(Modifier.weight(1f))
            Spacer(Modifier.width(g.ch(6)))
        }
        Text(
            buildAnnotatedString {
                if (h.sittings.isEmpty()) append("no sittings in 16 weeks")
                else {
                    withStyle(SpanStyle(color = c.ink, fontWeight = FontWeight.Normal)) { append("${h.sittings.size}") }
                    append(if (h.sittings.size == 1) " sitting, " else " sittings, ")
                    if (wk > 0) {
                        withStyle(SpanStyle(color = c.ink, fontWeight = FontWeight.Normal)) { append("+$wk%") }
                        append(" this week")
                    } else append("none this week")
                }
            },
            style = RT.dim(g, c),
        )
    }
}

/** `16w  12w  8w  4w  now` under a sixteen-week trace. */
@Composable
fun Axis(modifier: Modifier = Modifier) {
    val g = grid(); val c = colors()
    val labels = listOf("16w", "12w", "8w", "4w", "now")
    Layout(
        content = {
            labels.forEachIndexed { i, s ->
                Text(s, style = if (i == labels.lastIndex) RT.base(g, c) else RT.dim(g, c), maxLines = 1, softWrap = false)
            }
        },
        modifier = modifier.height(g.lhDp),
    ) { ms, cs ->
        val ps = ms.map { it.measure(cs.copy(minWidth = 0, maxWidth = Int.MAX_VALUE, minHeight = 0)) }
        val w = cs.maxWidth
        layout(w, cs.maxHeight) {
            ps.forEachIndexed { i, p ->
                val x = when (i) {
                    0 -> 0
                    ps.lastIndex -> w - p.width
                    else -> (xOf((16 - i * 4) * 7f) * w - p.width / 2f).roundToInt()
                }
                p.place(x, 0)
            }
        }
    }
}

/** A channel with nothing recorded yet: the frame, the 4-week divisions and a flat floor. */
@Composable
fun IdleChannel(modifier: Modifier = Modifier) {
    val g = grid(); val c = colors()
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(g.lines(3))) {
            val H = size.height; val W = size.width
            for (w in listOf(4, 8, 12)) vDots(xOf(w * 7f) * W, c.rule)
            hDots(0f, c.rule)
            hRule(H - hair(), c.ink)
        }
        Axis(Modifier.fillMaxWidth())
    }
}

// ---------------- the folder's channel (summary, tablet landscape) ----------------

@Composable
fun FolderTrace(a: FolderActivity, modifier: Modifier = Modifier) {
    val g = grid(); val c = colors()
    Column(modifier) {
        Canvas(Modifier.padding(top = g.lines(0.25f)).fillMaxWidth().height(g.lines(2.5f))) {
            hDots(0f, c.rule)
            hRule(size.height - hair(), c.ink)
            trace(a.pts, c.ink, 0f, size.height)
        }
        Axis(Modifier.fillMaxWidth())
    }
}

// ---------------- the scrubber: a mini strip chart of this book ----------------

/**
 * x is the page, y how much of the book lies behind it. Chapter starts are dotted gridlines,
 * the recorded trace steps up to a square pen, the dotted diagonal ahead is the rest of the book.
 */
@Composable
fun StripScrubber(ui: ChromeUi, on: ChromeActions) {
    val g = grid(); val c = colors()
    val st = remember(ui.returnPage ?: ui.page, ui.pageCount, ui.activity) { scrubTrace(ui) }
    val thumb = ui.progress
    val ret = ui.returnPage?.takeIf { it != ui.page }?.let(ui::fractionOf)
    BoxWithConstraints(
        Modifier.padding(top = g.lines(0.25f)).fillMaxWidth().height(g.lines(3)).scrubber(ui, on)
            .drawBehind {
                val W = size.width; val H = size.height; val top = 6.dp.toPx()
                hRule(H - hair(), c.ink)
                hDots(0f, c.rule)
                // the rest of the book: a dotted diagonal
                drawLine(
                    c.rule, Offset(0f, H), Offset(W, top), strokeWidth = hair(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 3.dp.toPx())),
                )
                ui.chapterPages.filter { it > 1 }.forEach { p ->
                    val x = ui.fractionOf(p) * W
                    vDots(x, c.rule)
                    vRule(x, c.ink, H - 5.dp.toPx(), H)
                }
                clipRect(right = minOf(thumb, st.end) * W + hair()) { trace(st.pts, c.ink, top, H) }
                ret?.let { r ->
                    val x = r * W
                    vDots(x, c.ink, on = 3f, period = 6f)
                }
                val x = thumb * W
                vRule(x - hair() / 2, c.ink)
                val y = top + (1f - st.yAt(thumb)) * (H - top)
                val s = 9.dp.toPx()
                drawRect(c.ink, Offset((x - s / 2).roundToInt().toFloat(), (y - s / 2).roundToInt().toFloat()), Size(s, s))
                if (ui.scrubbing) {
                    val o = 2.5.dp.toPx()
                    drawRect(c.ink, Offset((x - s / 2 - o).roundToInt().toFloat(), (y - s / 2 - o).roundToInt().toFloat()), Size(s + o * 2, s + o * 2), style = Stroke(hair()))
                }
            },
    ) {
        ret?.let { r ->
            Text(
                "<", style = RT.title(g, c).copy(lineHeight = g.fs),
                modifier = Modifier.offset(x = maxWidth * r + 3.dp, y = (-2).dp).background(c.paper),
            )
        }
    }
}
