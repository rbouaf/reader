package app.reader.looks.editorial

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.reader.data.Book
import app.reader.data.Format
import app.reader.data.Sort
import app.reader.data.formatSize
import app.reader.looks.LibraryActions
import app.reader.looks.LibraryUi
import app.reader.looks.common.FitHeight
import app.reader.looks.openedLabel
import app.reader.looks.percent
import app.reader.theme.Fonts
import app.reader.theme.LocalColors
import kotlin.math.roundToInt

/**
 * The mockup's three breakpoints, resolved to the sizes its CSS uses at each. [t] tightens the
 * landscape left column, one level at a time, until the now-reading record fits (see FitHeight):
 * 1 closes the gaps and puts the sort choice beside the title, 2 shrinks the chart, 3 puts the
 * path on one line and drops the sittings line, 4 caps the title at two lines, 5 sets the title,
 * figure and chart smaller.
 */
data class Dims(val tablet: Boolean, val twoColumn: Boolean, val t: Int = 0) {
    private val portraitTablet get() = tablet && !twoColumn
    val gut = if (tablet) 40.dp else 16.dp
    val lw = if (tablet) 96.dp else 84.dp
    val h1 = when { t >= 5 -> 40.sp; twoColumn -> 48.sp; tablet -> 56.sp; else -> 44.sp }
    val h1Top = when { t >= 1 -> 2.dp; twoColumn -> 8.dp; else -> 14.dp }
    val fieldsTop = when { t >= 1 -> 6.dp; twoColumn -> 12.dp; else -> 16.dp }
    val fieldPadTop = when { t >= 1 -> 2.dp; twoColumn -> 6.dp; else -> 8.dp }
    val fieldPadBottom = when { t >= 1 -> 1.dp; twoColumn -> 5.dp; else -> 7.dp }
    val dt = if (tablet) 18.sp else 17.sp
    val dd = if (tablet) 13.sp else 12.5.sp
    val recTop = when { t >= 1 -> 26.dp; twoColumn -> 34.dp; else -> 40.dp }
    val recPadTop = when { t >= 1 -> 16.dp; twoColumn -> 22.dp; tablet -> 26.dp; else -> 24.dp }
    val recSide = if (tablet) 22.dp else 16.dp
    val recTitle = when { t >= 5 -> 27.sp; twoColumn -> 34.sp; tablet -> 38.sp; else -> 33.sp }
    val recAuth = if (tablet) 21.sp else 19.sp
    val recFieldsTop = when { t >= 1 -> 8.dp; twoColumn -> 12.dp; else -> 18.dp }
    val goTop = when { t >= 1 -> 10.dp; twoColumn -> 14.dp; tablet -> 22.dp; else -> 20.dp }
    val goH = if (twoColumn) 50.dp else 54.dp
    val fig = when { t >= 5 -> 42.sp; twoColumn -> 54.sp; tablet -> 60.sp; else -> 50.sp }
    val chartH = when { t >= 5 -> 40.dp; t >= 2 -> 52.dp; t >= 1 -> 64.dp; twoColumn -> 78.dp; portraitTablet -> 120.dp; else -> 96.dp }
    val sortInline get() = t >= 1
    val clipPath get() = t >= 3
    val clipTitle get() = t >= 4
    val colF = when { twoColumn -> 40.dp; tablet -> 44.dp; else -> 38.dp }
    val colX = if (twoColumn) 92.dp else 96.dp
    val colTr = when { twoColumn -> 132.dp; tablet -> 150.dp; else -> 74.dp }
    val colPc = when { twoColumn -> 50.dp; tablet -> 54.dp; else -> 40.dp }
    val colGap = when { twoColumn -> 16.dp; tablet -> 18.dp; else -> 10.dp }
    val rowTitle = if (tablet) 21.sp else 19.sp
    val pc = if (tablet) 15.sp else 13.sp
    val pcSmall = if (tablet) 11.sp else 10.sp
}

@Composable
fun EditorialLibrary(ui: LibraryUi, on: LibraryActions) {
    val c = colors()
    BoxWithConstraints(Modifier.fillMaxSize().background(c.paper)) {
        val d = Dims(tablet = maxWidth >= 700.dp, twoColumn = maxWidth >= 1000.dp && maxWidth > maxHeight)
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val agg = remember(ui.all) { aggregate(ui.all) }
        if (d.twoColumn) {
            Row(Modifier.fillMaxSize().padding(horizontal = d.gut), horizontalArrangement = Arrangement.spacedBy(56.dp)) {
                BoxWithConstraints(Modifier.width(440.dp).fillMaxHeight()) {
                    val top = insets.calculateTopPadding() + 22.dp
                    // the header and the whole record fit the first screen; the summary may need a scroll
                    val budget = maxHeight - top - insets.calculateBottomPadding() - 12.dp
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(top = top, bottom = insets.calculateBottomPadding() + 64.dp),
                    ) {
                        FitHeight(budget, levels = 6) { t ->
                            val dt = d.copy(t = t)
                            Head(ui, on, dt)
                            Record(ui, on, dt)
                        }
                        if (ui.loaded && ui.all.isNotEmpty()) Summary(ui, agg, d)
                    }
                }
                LazyColumn(
                    Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = PaddingValues(top = insets.calculateTopPadding() + 22.dp, bottom = insets.calculateBottomPadding() + 64.dp),
                ) { index(ui, on, d) }
            }
        } else {
            val inset = if (d.tablet) maxOf(d.gut, (maxWidth - 800.dp) / 2 + d.gut) else d.gut
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = inset, end = inset,
                    top = insets.calculateTopPadding() + if (d.tablet) 34.dp else 14.dp,
                    bottom = insets.calculateBottomPadding() + if (d.tablet) 64.dp else 56.dp,
                ),
            ) {
                item { Head(ui, on, d) }
                item { Record(ui, on, d) }
                index(ui, on, d)
            }
        }
    }
}

// ---------------- header ----------------

@Composable
private fun Head(ui: LibraryUi, on: LibraryActions, d: Dims) {
    val c = colors()
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Reader, a report", style = ET.it(c, 18.sp), modifier = Modifier.weight(1f))
            EdButton("Settings", on.settings, gear = true)
        }
        val sortable = ui.folder != null && ui.loaded && ui.all.isNotEmpty()
        if (d.sortInline && sortable) {
            Row(Modifier.fillMaxWidth().padding(top = d.h1Top)) {
                Text("Library", style = ET.disp(c, d.h1, 1f, tracking = -0.012f), modifier = Modifier.weight(1f).alignByBaseline())
                SortChoice(ui, on, Modifier.alignByBaseline())
            }
        } else Text(
            "Library", style = ET.disp(c, d.h1, 1f, tracking = -0.012f),
            modifier = Modifier.padding(top = d.h1Top),
        )
        Fields(d, Modifier.padding(top = d.fieldsTop), ruleBelow = true) {
            if (ui.folder == null) {
                field("folder") { Value(d, "none chosen", c.mute) }
                return@Fields
            }
            field("folder") { Value(d, ui.folder) }
            field("status") {
                val n = ui.counts
                when {
                    ui.error != null -> Value(d, ui.error)
                    !ui.loaded -> Value(d, "first scan in progress…", c.mute)
                    else -> Text(
                        buildAnnotatedString {
                            val sl = SpanStyle(color = c.mute)
                            append("${n.total} files "); withStyle(sl) { append("/") }
                            append(" ${n.open} open "); withStyle(sl) { append("/") }
                            append(" ${n.new} new "); withStyle(sl) { append("/") }
                            append(" ${n.finished} done")
                            if (ui.scanning) withStyle(sl) { append(", rescanning") }
                        },
                        style = ET.mono(c, d.dd),
                    )
                }
            }
            if (sortable && !d.sortInline) field("sort by", center = true) { SortChoice(ui, on) }
        }
    }
}

@Composable
private fun Value(d: Dims, text: String, color: androidx.compose.ui.graphics.Color = colors().ink, oneLine: Boolean = false) {
    Text(
        text, style = ET.mono(colors(), d.dd, color = color),
        maxLines = if (oneLine) 1 else Int.MAX_VALUE, overflow = TextOverflow.MiddleEllipsis,
    )
}

/** Sort as a printed choice: a filled pen head marks the order in force. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SortChoice(ui: LibraryUi, on: LibraryActions, modifier: Modifier = Modifier) {
    val c = colors()
    FlowRow(modifier.bleedV(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf(Sort.Recent to "recent", Sort.Title to "title", Sort.Author to "author").forEach { (s, label) ->
            val sel = ui.sort == s
            Row(
                Modifier.heightIn(min = 44.dp).clickable { on.sort(s) },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Canvas(Modifier.size(6.dp)) {
                    val w = 1.5.dp.toPx()
                    if (sel) drawRect(c.ink)
                    else drawRect(c.rule, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w))
                }
                Text(label, style = ET.mono(c, 12.5.sp, 1f, if (sel) c.ink else c.mute))
            }
        }
    }
}

/** Lays a child out at full size but reports [bleed] less on top and bottom (CSS negative margins). */
fun Modifier.bleedV(bleed: Dp) = layout { m, cs ->
    val p = m.measure(cs.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
    val b = bleed.roundToPx()
    val h = (p.height - 2 * b).coerceAtLeast(0)
    layout(p.width, h) { p.place(0, -b) }
}

// ---------------- field tables ----------------

class FieldsScope {
    data class Row(val label: String, val center: Boolean, val content: @Composable () -> Unit)
    val rows = mutableListOf<Row>()
    fun field(label: String, center: Boolean = false, content: @Composable () -> Unit) { rows += Row(label, center, content) }
}

/** `.fields`: italic labels against printed values, an ink rule above and dashed hairlines between rows. */
@Composable
fun Fields(d: Dims, modifier: Modifier = Modifier, ruleBelow: Boolean, build: FieldsScope.() -> Unit) {
    val c = colors()
    val rows = FieldsScope().apply(build).rows
    Column(modifier.fillMaxWidth().edges(top = c.ink, bottom = if (ruleBelow) c.rule else null)) {
        rows.forEachIndexed { i, r ->
            Row(
                Modifier.fillMaxWidth()
                    .drawBehind { if (i > 0) dashedH(c.rule, 0.5f) }
                    .padding(top = d.fieldPadTop, bottom = d.fieldPadBottom),
                verticalAlignment = if (r.center) Alignment.CenterVertically else Alignment.Top,
            ) {
                if (r.center) {
                    Text(r.label, style = ET.it(c, d.dt), modifier = Modifier.width(d.lw))
                    Box(Modifier.weight(1f)) { r.content() }
                } else {
                    Text(r.label, style = ET.it(c, d.dt), modifier = Modifier.width(d.lw).alignByBaseline())
                    Box(Modifier.weight(1f).alignByBaseline()) { r.content() }
                }
            }
        }
    }
}

// ---------------- the record ----------------

private fun chapterName(s: String) = s.replace(Regex("^[IVXLC]+\\.\\s*|^Chapter \\d+\\.?\\s*", RegexOption.IGNORE_CASE), "")

@Composable
private fun Record(ui: LibraryUi, on: LibraryActions, d: Dims) {
    val c = colors()
    when {
        ui.folder == null -> RecordBox(d, "No library", "record 000", onClick = on.chooseFolder, go = "Choose a folder") {
            Text("No folder chosen yet.", style = ET.disp(c, d.recTitle, 1.02f, tracking = -0.006f).copy(lineBreak = LineBreak.Heading))
            Text(
                "Choose the folder on this device that holds your books. Everything in it and its subfolders (EPUB, PDF, TXT, Markdown) is listed here. Reading positions are saved inside that folder, so they travel with it.",
                style = ET.mono(c, 12.sp, 1.5f, c.mute), modifier = Modifier.padding(top = 14.dp),
            )
            IdleChannel(d)
        }
        ui.error != null && ui.all.isEmpty() -> RecordBox(d, "Fault", "record 000", onClick = on.chooseFolder, go = "Choose another folder") {
            Text("The folder can’t be read.", style = ET.disp(c, d.recTitle, 1.02f, tracking = -0.006f).copy(lineBreak = LineBreak.Heading))
            Text(
                "The fault is noted in the status line above. Check that the app can still open ${ui.folder}, or choose another folder.",
                style = ET.mono(c, 12.sp, 1.5f, c.mute), modifier = Modifier.padding(top = 14.dp),
            )
            IdleChannel(d)
        }
        !ui.loaded -> RecordBox(d, "First scan", "record 000", onClick = null, go = null) {
            Text("Listing your books…", style = ET.it(c, d.recAuth, 1.2f, c.ink))
            Text(ui.folder, style = ET.mono(c, 12.sp, 1.5f, c.mute), modifier = Modifier.padding(top = 6.dp))
            IdleChannel(d)
            Spacer(Modifier.height(d.goTop))
        }
        ui.isEmpty -> RecordBox(d, "Empty folder", "record 000", onClick = on.chooseFolder, go = "Choose another folder") {
            Text("Nothing to read here.", style = ET.disp(c, d.recTitle, 1.02f, tracking = -0.006f).copy(lineBreak = LineBreak.Heading))
            Text("No EPUB, PDF, TXT or Markdown files in ${ui.folder}.", style = ET.mono(c, 12.sp, 1.5f, c.mute), modifier = Modifier.padding(top = 14.dp))
            IdleChannel(d)
        }
        ui.current == null -> RecordBox(d, "Now reading", "no record", onClick = null, go = null) {
            Text("Nothing open.", style = ET.it(c, d.recAuth, 1.2f, c.ink))
            Text("Every book is new or finished. Choose one from the index to start a record.", style = ET.mono(c, 12.sp, 1.5f, c.mute), modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(d.goTop))
        }
        else -> CurrentRecord(ui.current, on, d)
    }
}

/** A calm, empty channel for records with no reading yet. */
@Composable
private fun IdleChannel(d: Dims) {
    val c = colors()
    Column(Modifier.padding(top = 18.dp)) {
        TraceStrip(
            History(listOf(Offset(0f, 0f), Offset(1f, 0f)), emptyList(), 0f, idle = true, week = 0f),
            Modifier.fillMaxWidth().height(d.chartH * 0.6f).drawBehind { dashedH(c.rule, 0.5f) }, pad = 6.dp,
        )
        Axis(short = !d.tablet, modifier = Modifier.fillMaxWidth())
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordBox(
    d: Dims, tag: String, note: String, onClick: (() -> Unit)?, go: String?, goRight: String = "",
    onLongClick: (() -> Unit)? = null, body: @Composable () -> Unit,
) {
    val c = colors()
    val (src, pressed) = rememberPress()
    Column(Modifier.fillMaxWidth().padding(top = d.recTop)) {
        Box {
            Column(
                Modifier.fillMaxWidth()
                    .edges(top = c.ink, topW = 1.5.dp, left = c.rule, right = c.rule, bottom = c.rule)
                    .then(if (onClick != null) Modifier.combinedClickable(src, null, onLongClick = onLongClick, onClick = onClick) else Modifier),
            ) {
                Column(Modifier.padding(top = d.recPadTop, start = d.recSide, end = d.recSide)) { body() }
                if (go != null) {
                    Row(
                        Modifier.padding(top = d.goTop).fillMaxWidth().heightIn(min = d.goH)
                            .background(if (pressed) c.paper else c.ink)
                            .then(if (pressed) Modifier.outline(c.ink, 1.5.dp) else Modifier)
                            .padding(horizontal = d.recSide),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val fg = if (pressed) c.ink else c.paper
                        Text(go, style = ET.it(c, 22.sp, 1f, fg), modifier = Modifier.weight(1f).padding(bottom = 2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (goRight.isNotEmpty()) Text(goRight, style = ET.mono(c, 11.5.sp, 1f, fg.copy(alpha = 0.75f)))
                            Arrow(fg.copy(alpha = 0.75f))
                        }
                    }
                }
            }
            // the tag, set into the top rule
            Row(
                Modifier.offset(x = 10.dp, y = (-14).dp).background(c.paper).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tag, style = ET.it(c, 21.sp, 24f / 21f, c.ink), modifier = Modifier.alignByBaseline())
                Text(note, style = ET.mono(c, 11.sp, 1f, c.mute), modifier = Modifier.padding(start = 8.dp).alignByBaseline().offset(y = (-2).dp))
            }
        }
    }
}

@Composable
private fun CurrentRecord(b: Book, on: LibraryActions, d: Dims) {
    val c = colors()
    val st = b.state
    val h = remember(b) { history(b) }
    RecordBox(
        d, "Now reading", "record 001", onClick = { on.open(b) }, onLongClick = { on.hold(b) },
        go = "Continue reading",
        goRight = st.page?.let { p -> st.pages?.let { "page $p of $it" } ?: "page $p" } ?: "",
    ) {
        Text(
            b.title, style = ET.disp(c, d.recTitle, 1.02f, tracking = -0.006f).copy(lineBreak = LineBreak.Heading),
            maxLines = if (d.clipTitle) 2 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis,
        )
        b.author?.let {
            Text(
                it, style = ET.it(c, d.recAuth, 1.2f), modifier = Modifier.padding(top = if (d.t >= 1) 2.dp else 6.dp),
                maxLines = if (d.clipTitle) 1 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis,
            )
        }
        Fields(d, Modifier.padding(top = d.recFieldsTop), ruleBelow = false) {
            field("file") { Value(d, b.path, oneLine = d.clipPath) }
            field("size") {
                Text(
                    buildAnnotatedString { append(formatSize(b.size)); withStyle(SpanStyle(color = c.mute)) { append(" " + b.format.label.uppercase()) } },
                    style = ET.mono(c, d.dd),
                )
            }
            field("opened") { Value(d, openedLabel(st.openedAt) ?: "Never") }
            field("chapter") {
                Value(
                    d,
                    if (st.chapterIndex != null && st.chapters != null && st.chapter != null) "${st.chapterIndex} of ${st.chapters}, ${chapterName(st.chapter)}"
                    else st.chapter?.let(::chapterName) ?: "not indexed",
                )
            }
            if (!d.clipPath) field("sittings") { Value(d, "${h.sittings.size} in 16 weeks") }
        }
        // the read row: the big figure under its label, the channel beside it
        Row(Modifier.fillMaxWidth().drawBehind { dashedH(c.rule, 0.5f) }.padding(top = d.fieldPadTop)) {
            Column(Modifier.width(d.lw)) {
                Text("read", style = ET.it(c, d.dt))
                Spacer(Modifier.height(if (d.t >= 1) 4.dp else 10.dp))
                Figure(percent(b), d.fig)
            }
            Column(Modifier.weight(1f).padding(top = 3.dp, start = 26.dp)) {
                Box {
                    TraceStrip(
                        h, Modifier.fillMaxWidth().height(d.chartH).drawBehind { dashedH(c.rule, 0.5f) },
                        pad = 8.dp, topLine = true, chapters = st.chapters, marks = true, pen = 9.dp, stroke = 1.7.dp,
                    )
                    YScale(d.chartH, 8.dp, Modifier.offset(x = (-26).dp))
                }
                Axis(short = true, modifier = Modifier.fillMaxWidth())
                val pts = (h.week * 100).roundToInt()
                Text(
                    if (pts > 0) "+$pts points in the last 7 days" else "No reading in the last 7 days",
                    style = ET.mono(c, 11.sp, 1.45f, c.mute), modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** `.fig`: the big Bodoni percentage with an italic % hung at its cap height. */
@Composable
private fun Figure(value: Int, size: TextUnit) {
    val c = colors()
    Text(
        buildAnnotatedString {
            append("$value")
            withStyle(
                SpanStyle(
                    fontFamily = Fonts.bodoniText, fontStyle = FontStyle.Italic, fontSize = 19.sp, color = c.mute,
                    baselineShift = BaselineShift(0.4f), letterSpacing = 0.em,
                ),
            ) { append(" %") }
        },
        style = ET.disp(c, size, 0.82f, tracking = -0.02f), maxLines = 1, softWrap = false,
    )
}

/** `.yscale`: 100 / 50 / 0 with little ticks, in the gutter left of the record's channel. */
@Composable
private fun YScale(height: Dp, pad: Dp, modifier: Modifier) {
    val c = colors()
    Layout(
        content = { listOf("100", "50", "0").forEach { Text(it, style = ET.mono(c, 9.5.sp, 1f, c.mute), maxLines = 1, softWrap = false) } },
        modifier = modifier.width(22.dp).height(height).drawBehind {
            val top = pad.toPx(); val bot = size.height - pad.toPx()
            listOf(0f, 0.5f, 1f).forEach { f ->
                val y = top + f * (bot - top)
                drawRect(c.mute, Offset(size.width - 3.dp.toPx(), y), Size(3.dp.toPx(), 1.dp.toPx().coerceAtLeast(1f)))
            }
        },
    ) { ms, cs ->
        val ps = ms.map { it.measure(cs.copy(minWidth = 0, minHeight = 0)) }
        layout(cs.maxWidth, cs.maxHeight) {
            val top = pad.toPx(); val bot = cs.maxHeight - pad.toPx()
            ps.forEachIndexed { i, p ->
                val y = top + i * 0.5f * (bot - top)
                p.place(cs.maxWidth - 4.dp.roundToPx() - p.width - 1, (y - p.height / 2f).roundToInt())
            }
        }
    }
}

// ---------------- the index ----------------

private fun LazyListScope.index(ui: LibraryUi, on: LibraryActions, d: Dims) {
    if (ui.folder == null || !ui.loaded || ui.isEmpty) return
    item { IndexHead(ui, d) }
    items(ui.others, key = { it.key }) { b -> IndexRow(b, d, on, first = b == ui.others.first()) }
    item { IndexEnd(ui) }
}

@Composable
private fun IndexHead(ui: LibraryUi, d: Dims) {
    val c = colors()
    Column(Modifier.padding(top = if (d.twoColumn) 6.dp else 40.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text("Index", style = ET.it(c, 25.sp, 1.1f, c.ink), modifier = Modifier.weight(1f).alignByBaseline())
            Text("${ui.others.size} records, by ${ui.sort.name.lowercase()}", style = ET.mono(c, 11.sp, 1.45f, c.mute), modifier = Modifier.alignByBaseline())
        }
        val head = ET.it(c, 16.sp)
        Row(
            Modifier.padding(top = 12.dp).fillMaxWidth().edges(bottom = c.ink).padding(bottom = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text("fmt", style = head, modifier = Modifier.width(d.colF))
            Spacer(Modifier.width(d.colGap))
            Text("title, author", style = head, modifier = Modifier.weight(1f))
            if (d.tablet) {
                Spacer(Modifier.width(d.colGap))
                Text("size, opened", style = head, maxLines = 1, softWrap = false, modifier = Modifier.width(d.colX))
                Spacer(Modifier.width(d.colGap))
                Text("16 weeks", style = head, modifier = Modifier.width(d.colTr))
            } else {
                Spacer(Modifier.width(d.colGap))
                Text("16 weeks", style = head, textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.width(d.colTr).padding(end = 2.dp))
            }
            Spacer(Modifier.width(d.colGap))
            Text("read", style = head, textAlign = TextAlign.End, modifier = Modifier.width(d.colPc))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IndexRow(b: Book, d: Dims, on: LibraryActions, first: Boolean) {
    val c0 = colors()
    val (src, pressed) = rememberPress()
    val c = if (pressed) c0.inverted() else c0
    val h = remember(b) { history(b) }
    CompositionLocalProvider(LocalColors provides c) {
        val titleStyle = ET.disp(c, d.rowTitle, 1.1f, color = if (b.isFinished) c.mute else c.ink).copy(lineBreak = LineBreak.Paragraph)
        val fmt: @Composable () -> Unit = {
            Text(b.format.label.uppercase(), style = ET.mono(c, 10.5.sp, 1.2f, c.mute).copy(letterSpacing = 0.02.em), modifier = Modifier.width(d.colF).padding(top = 4.dp))
        }
        val author: @Composable (Modifier) -> Unit = { m ->
            Text(b.author ?: "Unknown author", style = ET.mono(c, 11.sp, 1.3f, c.mute), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = m.padding(top = 4.dp))
        }
        Row(
            Modifier.fillMaxWidth()
                .drawBehind { if (!first) drawRect(c0.rule, Offset.Zero, Size(size.width, 1.dp.toPx().coerceAtLeast(1f))) }
                .background(if (pressed) c0.ink else androidx.compose.ui.graphics.Color.Transparent)
                .combinedClickable(src, null, onLongClick = { on.hold(b) }) { on.open(b) }
                .height(IntrinsicSize.Min)
                .padding(top = 11.dp, bottom = 10.dp),
        ) {
            if (d.tablet) {
                Column(Modifier.weight(1f)) {
                    Row {
                        fmt()
                        Spacer(Modifier.width(d.colGap))
                        Text(b.title, style = titleStyle, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(d.colGap))
                        Text(formatSize(b.size), style = ET.mono(c, 11.sp, 1.3f), maxLines = 1, modifier = Modifier.width(d.colX).padding(top = 4.dp))
                    }
                    Row {
                        Spacer(Modifier.width(d.colF + d.colGap))
                        author(Modifier.weight(1f))
                        Spacer(Modifier.width(d.colGap))
                        Text(
                            openedLabel(b.state.openedAt) ?: "never", style = ET.mono(c, 11.sp, 1.3f, c.mute), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(d.colX).padding(top = 4.dp),
                        )
                    }
                }
            } else {
                fmt()
                Spacer(Modifier.width(d.colGap))
                Column(Modifier.weight(1f)) {
                    Text(b.title, style = titleStyle)
                    author(Modifier)
                }
            }
            Spacer(Modifier.width(d.colGap))
            TraceStrip(
                h,
                Modifier.width(d.colTr).then(if (d.tablet) Modifier.heightIn(min = 40.dp) else Modifier.heightIn(min = 34.dp, max = 64.dp)).fillMaxHeight(),
                pad = 6.dp, majors = true, minors = d.tablet, pen = 6.dp,
            )
            Spacer(Modifier.width(d.colGap))
            Box(Modifier.width(d.colPc).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
                Text(
                    buildAnnotatedString {
                        when {
                            b.isFinished -> withStyle(ET.it(c, 16.sp).toSpanStyle()) { append("done") }
                            b.isNew -> withStyle(ET.it(c, 16.sp).toSpanStyle()) { append("new") }
                            else -> {
                                append("${percent(b)}")
                                withStyle(SpanStyle(fontSize = d.pcSmall, color = c.mute)) { append("%") }
                            }
                        }
                    },
                    style = ET.mono(c, d.pc, 1.2f), textAlign = TextAlign.End, maxLines = 1, softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun IndexEnd(ui: LibraryUi) {
    val c = colors()
    val read = ui.all.sumOf { it.progress.toDouble() }
    Row(
        Modifier.padding(top = 12.dp).fillMaxWidth().edges(top = c.ink).padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val st = ET.mono(c, 11.sp, 1.45f, c.mute)
        Text(if (ui.scanning) "${ui.counts.total} records listed, scanning…" else "${ui.counts.total} records listed.", style = st, modifier = Modifier.weight(1f))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = c.ink)) { append("%.1f".format(read)) }
                append(" of ${ui.counts.total} books read in all")
            },
            style = st, textAlign = TextAlign.End,
        )
    }
}

// ---------------- summary (tablet landscape) ----------------

@Composable
private fun Summary(ui: LibraryUi, agg: Aggregate, d: Dims) {
    val c = colors()
    val n = ui.counts
    Column(Modifier.padding(top = 40.dp)) {
        Text("Summary", style = ET.it(c, 25.sp, 1.1f, c.ink), modifier = Modifier.fillMaxWidth().edges(bottom = c.ink).padding(bottom = 8.dp))
        @Composable fun line(k: String, v: Int) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(k, style = ET.it(c, 17.sp), modifier = Modifier.width(d.lw).padding(vertical = 6.dp))
                Spacer(Modifier.width(12.dp))
                Text("$v", style = ET.disp(c, 24.sp, 1f), textAlign = TextAlign.End, modifier = Modifier.width(40.dp))
                Spacer(Modifier.width(12.dp))
                Ticks(v, n.total, Modifier.weight(1f).height(18.dp))
            }
        }
        @Composable fun gap() = Box(Modifier.padding(vertical = 6.dp).fillMaxWidth().height(1.dp).drawBehind { dashedH(c.rule, 0.5f) })
        line("open", n.open); line("new", n.new); line("finished", n.finished)
        gap()
        Format.entries.forEach { f -> n.byFormat[f]?.let { line(f.label, it) } }
        gap()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("folder size", style = ET.it(c, 17.sp), modifier = Modifier.width(d.lw).padding(vertical = 6.dp))
            Spacer(Modifier.width(12.dp))
            Text("${formatSize(n.bytes)} on device", style = ET.mono(c, 12.5.sp))
        }
        // the whole library as one channel
        Column(Modifier.padding(top = 22.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text("Whole library, 16 weeks", style = ET.it(c, 17.sp), modifier = Modifier.weight(1f).alignByBaseline())
                Text(
                    buildAnnotatedString {
                        append("%.1f".format(agg.read))
                        withStyle(ET.it(c, 15.sp).toSpanStyle()) { append("  books read") }
                    },
                    style = ET.disp(c, 30.sp, 1f), modifier = Modifier.alignByBaseline(),
                )
            }
            TraceStrip(
                agg.trace, Modifier.padding(top = 10.dp).fillMaxWidth().height(74.dp).drawBehind { dashedH(c.rule, 0.5f) },
                pad = 6.dp, topLine = true,
            )
            Axis(short = false, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** One tick per file, inked for the ones counted. Falls back to a filled scale when there are too many to tick. */
@Composable
private fun Ticks(on: Int, total: Int, modifier: Modifier) {
    val c = colors()
    Canvas(modifier) {
        if (total <= 0) return@Canvas
        val w = 1.5.dp.toPx()
        val step = minOf(12.5.dp.toPx(), size.width / total)
        if (step >= 3.dp.toPx()) {
            for (i in 0 until total) drawRect(if (i < on) c.ink else c.rule, Offset(i * step, 0f), Size(w, size.height))
        } else {
            drawRect(c.rule, Offset(0f, size.height / 2), Size(size.width, w))
            drawRect(c.ink, Offset(0f, 0f), Size(size.width * on / total, size.height))
        }
    }
}
