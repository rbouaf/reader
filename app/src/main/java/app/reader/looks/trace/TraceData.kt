package app.reader.looks.trace

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import app.reader.data.Book
import app.reader.looks.ChromeUi
import java.time.LocalDate
import kotlin.math.ceil

/*
 * The traces' data, from the strip chart (04): a book's reading drawn as one stepped pen line.
 * x is time over the last sixteen weeks, y is how much of the book lies behind you. Flat while
 * you're not reading, a step for every day you read, flat along the top once finished.
 *
 * Everything comes from the book's real record: `ReadingState.activity` (seconds per day) and
 * its progress. A day's step carries the share of progress that matches its share of all the
 * seconds ever spent in the book, so the line ends exactly at today's progress.
 *
 * Points are in unit space: x 0..1 (left to right), y 0..1 (bottom to top).
 */

/** Sixteen weeks on the chart. */
const val WIN = 112

/** Days ago to chart x. */
fun xOf(daysAgo: Float) = 1f - daysAgo / WIN

/** One day's reading as a sitting: it starts [d0] days ago and ends [d1] days ago, carrying progress [v0] to [v1]. */
@Immutable
data class Sitting(val d0: Float, val d1: Float, val v0: Float, val v1: Float)

@Immutable
data class History(
    val p: Float,
    /** The stepped line, empty while the book is unread. */
    val pts: List<Offset>,
    /** The sittings inside the window. */
    val sittings: List<Sitting>,
) {
    /** Progress gained in the last seven days. */
    val week get() = sittings.filter { it.d1 < 7 }.sumOf { (it.v1 - it.v0).toDouble() }.toFloat()
}

/** A sitting's width in days: longer reading draws a gentler riser (a quarter of a day to most of one). */
private fun duration(seconds: Int) = (0.25f + seconds / 10_800f * 0.6f).coerceAtMost(0.85f)

fun history(b: Book, today: LocalDate = LocalDate.now()): History {
    val p = b.progress
    if (p <= 0f) return History(0f, emptyList(), emptyList())
    val days = b.activity(WIN, today)
    val inWin = days.sumOf { it.toLong() }
    val total = maxOf(inWin, b.state.activity.values.sumOf { it.coerceAtLeast(0).toLong() })
    if (total == 0L) return History(p, listOf(Offset(0f, p), Offset(1f, p)), emptyList())

    var v = p * (total - inWin) / total
    val pts = mutableListOf(Offset(0f, v))
    val ses = mutableListOf<Sitting>()
    days.forEachIndexed { i, s ->
        if (s <= 0) return@forEachIndexed
        val ago = (WIN - 1 - i).toFloat()
        val d0 = (ago + duration(s)).coerceAtMost(WIN.toFloat())
        val v0 = v
        v = (v + p * s / total).coerceAtMost(p)
        ses += Sitting(d0, ago, v0, v)
        pts += Offset(xOf(d0), v0)
        pts += Offset(xOf(ago), v)
    }
    pts += Offset(1f, p)
    return History(p, pts, ses)
}

/** The whole folder as one channel: every sitting of every book summed into one climb. */
@Immutable
data class FolderActivity(val pts: List<Offset>, val sittings: Int, val booksRead: Float)

fun folderActivity(books: List<Book>, today: LocalDate = LocalDate.now()): FolderActivity {
    val ev = books.flatMap { history(it, today).sittings }.sortedByDescending { it.d1 }
    val gain = ev.sumOf { (it.v1 - it.v0).toDouble() }.toFloat()
    if (ev.isEmpty() || gain <= 0f) return FolderActivity(emptyList(), ev.size, 0f)
    var c = 0f
    var lx = 0f
    val pts = mutableListOf(Offset(0f, 0f))
    ev.forEach { s ->
        val x0 = maxOf(lx, xOf(s.d0))
        pts += Offset(x0, c / gain)
        c += s.v1 - s.v0
        lx = maxOf(x0, xOf(s.d1))
        pts += Offset(lx, c / gain)
    }
    pts += Offset(1f, 1f)
    return FolderActivity(pts, ev.size, gain)
}

/**
 * The scrubber's trace: x is the page, y how much of the book lies behind it, so the record
 * climbs the diagonal in steps, one per day read. The last 28 days ([ChromeUi.activity]) are
 * placed at a reading pace of a minute a page; whatever came before them is drawn as even steps.
 * The trace ends where you were reading ([ChromeUi.returnPage] while you scrub or after a jump).
 */
@Immutable
data class ScrubTrace(val pts: List<Offset>, val end: Float) {
    /** The trace's height at [x], or the diagonal past its end. */
    fun yAt(x: Float): Float {
        if (x >= end || pts.size < 2) return x
        for (i in 1 until pts.size) if (x <= pts[i].x) {
            val a = pts[i - 1]; val b = pts[i]
            return a.y + (b.y - a.y) * (if (b.x > a.x) (x - a.x) / (b.x - a.x) else 1f)
        }
        return pts.last().y
    }
}

fun scrubTrace(ui: ChromeUi): ScrubTrace {
    val end = ui.fractionOf(ui.returnPage ?: ui.page).coerceIn(0f, 1f)
    if (end <= 0f) return ScrubTrace(emptyList(), 0f)
    val pace = 1f / (60f * ui.pageCount.coerceAtLeast(1))
    val widths = ui.activity.filter { it > 0 }.map { it * pace }
    val recorded = widths.sum()
    val scale = if (recorded > end) end / recorded else 1f
    val before = (end - recorded * scale).coerceAtLeast(0f)

    val spans = mutableListOf<Float>()
    if (before > 0f) {
        val n = ceil(before / 0.06f).toInt().coerceAtLeast(1)
        repeat(n) { spans += before / n }
    }
    widths.forEach { spans += it * scale }

    val pts = mutableListOf(Offset(0f, 0f))
    var a = 0f
    spans.forEach { w ->
        val b = (a + w).coerceAtMost(end)
        pts += Offset(a + (b - a) * 0.7f, a)
        pts += Offset(b, b)
        a = b
    }
    return ScrubTrace(pts, end)
}
