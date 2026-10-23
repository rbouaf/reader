package app.reader.looks.plotter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.reader.data.Book
import app.reader.data.Format
import app.reader.data.Sort
import app.reader.data.formatSize
import app.reader.looks.LibraryActions
import app.reader.looks.LibraryUi
import app.reader.looks.openedLabel
import app.reader.looks.percent
import app.reader.theme.LocalColors
import java.time.LocalDate

/** The mockup's three layouts: phone, tablet portrait (≥700dp), tablet landscape (≥1000dp, landscape). */
@Immutable
data class PShape(val tablet: Boolean, val wide: Boolean) {
    val tabPortrait get() = tablet && !wide
}

val LocalShape = staticCompositionLocalOf { PShape(tablet = false, wide = false) }

@Composable
fun PlotterLibrary(ui: LibraryUi, on: LibraryActions) {
    val c = colors()
    BoxWithConstraints(Modifier.fillMaxSize().mmGrid(c)) {
        val shape = PShape(tablet = maxWidth >= 700.dp, wide = maxWidth >= 1000.dp && maxWidth > maxHeight)
        CompositionLocalProvider(LocalShape provides shape) {
            if (shape.wide) WideSheet(ui, on) else TallSheet(ui, on, maxHeight)
        }
    }
}

// ---------------- the sheet ----------------

/** Phone and tablet portrait: one scrolling drawing; the frame's top and bottom scroll with it. */
@Composable
private fun TallSheet(ui: LibraryUi, on: LibraryActions, viewH: Dp) {
    val c = colors(); val shape = LocalShape.current
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val ld = LocalLayoutDirection.current
    val side = if (shape.tablet) 34.dp else 18.dp
    val top = if (shape.tablet) 34.dp else 20.dp
    val bottom = if (shape.tablet) 34.dp else 22.dp
    val padX = if (shape.tablet) 22.dp else 13.dp
    val padTop = if (shape.tablet) 22.dp else 14.dp
    val padBottom = if (shape.tablet) 26.dp else 18.dp
    val cols = if (shape.tablet) 6 else 4
    val sStart = side + insets.calculateStartPadding(ld)
    val sEnd = side + insets.calculateEndPadding(ld)

    fun Modifier.slice(first: Boolean = false, last: Boolean = false) =
        fillMaxWidth().padding(start = sStart, end = sEnd)
            .edges(c.ink, start = HV, end = HV, top = if (first) HV else 0.dp, bottom = if (last) HV else 0.dp)
            .padding(start = HV + padX, end = HV + padX, top = if (first) HV + padTop else 0.dp, bottom = if (last) HV + padBottom else 0.dp)

    // like the mockup's min-height, a short drawing (empty states, a few books) still fills the screen:
    // the frame's foot grows by whatever space is left below the last item
    val state = rememberLazyListState()
    var extra by remember { mutableIntStateOf(0) }
    LaunchedEffect(state) {
        snapshotFlow { state.layoutInfo }.collect { info ->
            val vis = info.visibleItemsInfo
            if (vis.isEmpty()) return@collect
            val all = vis.first().index == 0 && vis.last().index == info.totalItemsCount - 1
            extra = if (!all) 0 else {
                val end = vis.last().offset + vis.last().size
                (extra + info.viewportEndOffset - info.afterContentPadding - end).coerceAtLeast(0)
            }
        }
    }
    val density = LocalDensity.current

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = state,
            contentPadding = PaddingValues(top = insets.calculateTopPadding(), bottom = insets.calculateBottomPadding()),
        ) {
            item(key = "zt") { ZoneStrip(cols, top = true, height = top, start = sStart, end = sEnd) }
            item(key = "head") { Box(Modifier.slice(first = true)) { TitleBlock(ui, on) } }
            item(key = "now") { Box(Modifier.slice()) { Now(ui, on) } }
            index(ui, on) { content -> Box(Modifier.slice()) { content() } }
            item(key = "foot") { Box(Modifier.slice(last = true).height(with(density) { extra.toDp() })) }
            item(key = "zb") { ZoneStrip(cols, top = false, height = bottom, start = sStart, end = sEnd) }
        }
        // zone letters in the side margins stay put while the drawing scrolls past them
        val zone = if (shape.tablet) viewH / 4.8f else 280.dp
        ZoneSides(Modifier.fillMaxSize(), y0 = insets.calculateTopPadding() + top, zone = zone, start = sStart, end = sEnd)
    }
}

/** Tablet landscape: the frame is fixed to the screen; the record column and the index scroll inside it. */
@Composable
private fun WideSheet(ui: LibraryUi, on: LibraryActions) {
    val c = colors()
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val tm = rememberTextMeasurer()
    val ld = LocalLayoutDirection.current
    // the mockup's 34dp margin, but the system bars eat into it rather than add to it (the zone marks need ~20dp)
    fun margin(inset: Dp) = maxOf(34.dp, inset + 20.dp)
    Box(
        Modifier.fillMaxSize().padding(
            start = margin(insets.calculateStartPadding(ld)), top = margin(insets.calculateTopPadding()),
            end = margin(insets.calculateEndPadding(ld)), bottom = margin(insets.calculateBottomPadding()),
        ),
    ) {
        Box(
            Modifier.fillMaxSize()
                .drawBehind { zones(tm, c.mute, c.ink, cols = 8, rows = 6) }
                .penBox(c.ink, HV).padding(HV),
        ) {
            Row(Modifier.fillMaxSize()) {
                BoxWithConstraints(Modifier.width(450.dp + 22.dp).fillMaxHeight()) {
                    // the title block and the record fill the first screen at most (the protractor gives way),
                    // so Continue is never below the fold; the summary follows on scroll. The scroll clips
                    // 18dp above the frame, so the summary's tag never shows cut through by the frame line.
                    val first = maxHeight - 22.dp - 26.dp
                    Column(
                        Modifier.fillMaxSize().padding(bottom = 18.dp).verticalScroll(rememberScrollState())
                            .padding(start = 22.dp, top = 22.dp, bottom = 8.dp),
                    ) {
                        Column(Modifier.heightIn(max = first)) {
                            TitleBlock(ui, on)
                            Now(ui, on, Modifier.weight(1f, fill = false))
                        }
                        if (ui.loaded && !ui.isEmpty && ui.folder != null) Summary(ui)
                    }
                }
                Spacer(Modifier.width(30.dp))
                LazyColumn(
                    Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = PaddingValues(top = 22.dp, end = 22.dp, bottom = 26.dp),
                ) { index(ui, on) { it() } }
            }
        }
    }
}

/** Zone numbers along the top or bottom of the frame, with a short tick at each boundary. */
@Composable
private fun ZoneStrip(cols: Int, top: Boolean, height: Dp, start: Dp, end: Dp) {
    val c = colors()
    val tm = rememberTextMeasurer()
    Canvas(Modifier.fillMaxWidth().height(height).padding(start = start, end = end)) {
        val w = size.width
        val hl = pen(HL)
        val band = 18.dp.toPx()
        val y0 = if (top) size.height - 1.dp.toPx() - band else 1.dp.toPx()
        val style = PT.mono(10.sp, c.mute)
        for (i in 0 until cols) {
            val x0 = w * i / cols; val x1 = w * (i + 1) / cols
            val r = tm.measure("${i + 1}", style)
            drawText(r, topLeft = Offset((x0 + x1) / 2 - r.size.width / 2, y0 + band / 2 - r.size.height / 2))
            if (i > 0) {
                val tick = 7.dp.toPx()
                if (top) vLine(c.ink, x0, size.height - tick, size.height, hl) else vLine(c.ink, x0, 0f, tick, hl)
            }
        }
    }
}

/** Zone letters A–F in the side margins, [zone] apart, starting at [y0]. */
@Composable
private fun ZoneSides(modifier: Modifier, y0: Dp, zone: Dp, start: Dp, end: Dp) {
    val c = colors()
    val tm = rememberTextMeasurer()
    Canvas(modifier) {
        val hl = pen(HL)
        val style = PT.mono(10.sp, c.mute)
        val z = zone.toPx(); val top = y0.toPx()
        val lx = start.toPx(); val rx = size.width - end.toPx()
        val tick = 7.dp.toPx()
        for (i in 0 until 6) {
            val a = top + i * z
            if (a > size.height) break
            val r = tm.measure("${'A' + i}", style)
            val cy = a + z / 2 - r.size.height / 2
            drawText(r, topLeft = Offset(lx - 9.dp.toPx() - r.size.width / 2, cy))
            drawText(r, topLeft = Offset(rx + 9.dp.toPx() - r.size.width / 2, cy))
            if (i > 0) { hLine(c.ink, lx - tick, lx, a, hl); hLine(c.ink, rx, rx + tick, a, hl) }
        }
    }
}

/** All four zone margins around a fixed frame (drawn outside the element's bounds). */
private fun DrawScope.zones(tm: TextMeasurer, mute: Color, ink: Color, cols: Int, rows: Int) {
    val hl = pen(HL)
    val style = PT.mono(10.sp, mute)
    val w = size.width; val h = size.height
    val off = 10.dp.toPx(); val tick = 7.dp.toPx()
    for (i in 0 until cols) {
        val x0 = w * i / cols; val x1 = w * (i + 1) / cols
        val r = tm.measure("${i + 1}", style)
        val x = (x0 + x1) / 2 - r.size.width / 2
        drawText(r, topLeft = Offset(x, -off - r.size.height / 2))
        drawText(r, topLeft = Offset(x, h + off - r.size.height / 2))
        if (i > 0) { vLine(ink, x0, -tick, 0f, hl); vLine(ink, x0, h, h + tick, hl) }
    }
    for (i in 0 until rows) {
        val y0 = h * i / rows; val y1 = h * (i + 1) / rows
        val r = tm.measure("${'A' + i}", style)
        val y = (y0 + y1) / 2 - r.size.height / 2
        drawText(r, topLeft = Offset(-off - r.size.width / 2, y))
        drawText(r, topLeft = Offset(w + off - r.size.width / 2, y))
        if (i > 0) { hLine(ink, -tick, 0f, y0, hl); hLine(ink, w, w + tick, y0, hl) }
    }
}

// ---------------- title block ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TitleBlock(ui: LibraryUi, on: LibraryActions) {
    val c = colors(); val shape = LocalShape.current
    val n = ui.counts
    val status = when {
        ui.error != null -> ui.error
        !ui.loaded -> "scanning…"
        ui.isEmpty -> "no books found"
        else -> "${n.open} open, ${n.new} new, ${n.finished} done" + if (ui.scanning) ", rescanning" else ""
    }
    val files = if (ui.loaded) "${n.total} files" else "—"
    val date = LocalDate.now().toString()
    Column(Modifier.fillMaxWidth().background(c.paper).penBox(c.ink, HV).padding(HV)) {
        if (shape.tabPortrait) {
            Row(Modifier.height(IntrinsicSize.Min)) {
                TitleCell(Modifier.weight(1f))
                SetCell(on.settings, Modifier.width(150.dp).fillMaxHeight().edges(c.ink, start = HL))
            }
            if (ui.folder != null) {
                Row(Modifier.height(IntrinsicSize.Min).edges(c.ink, top = HL)) {
                    Cell("folder", ui.folder, Modifier.weight(1.3f).fillMaxHeight(), padEnd = 16.dp)
                    Cell("files", files, Modifier.fillMaxHeight().edges(c.ink, start = HL), padEnd = 16.dp)
                    Cell("status", status, Modifier.weight(1f).fillMaxHeight().edges(c.ink, start = HL))
                    Cell("date", date, Modifier.width(150.dp).fillMaxHeight().edges(c.ink, start = HL))
                }
            }
        } else {
            Row(Modifier.height(IntrinsicSize.Min)) {
                TitleCell(Modifier.weight(2.45f))
                SetCell(on.settings, Modifier.weight(1f).fillMaxHeight().edges(c.ink, start = HL))
            }
            if (ui.folder != null) {
                Cell("folder", ui.folder, Modifier.fillMaxWidth().edges(c.ink, top = HL))
                Row(Modifier.height(IntrinsicSize.Min).edges(c.ink, top = HL)) {
                    Cell("files", files, Modifier.weight(1f).fillMaxHeight())
                    Cell("status", status, Modifier.weight(1.45f).fillMaxHeight().edges(c.ink, start = HL))
                    Cell("date", date, Modifier.weight(1f).fillMaxHeight().edges(c.ink, start = HL))
                }
            }
        }
        if (ui.folder == null) {
            Cell("folder", "none chosen", Modifier.fillMaxWidth().edges(c.ink, top = HL), quiet = true)
        } else if (ui.loaded && !ui.isEmpty) {
            FlowRow(
                Modifier.fillMaxWidth().edges(c.ink, top = HL).padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.Center,
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text("sort by", style = PT.label(c), modifier = Modifier.padding(end = 0.dp))
                listOf(Sort.Recent to "recent", Sort.Title to "title", Sort.Author to "author").forEach { (s, label) ->
                    val sel = ui.sort == s
                    Row(
                        Modifier.heightIn(min = 44.dp).clickable { on.sort(s) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Radio(sel, if (sel) c.ink else c.mute)
                        Spacer(Modifier.width(7.dp))
                        Text(label, style = PT.mono(12.5.sp, if (sel) c.ink else c.mute))
                    }
                }
            }
        }
    }
}

@Composable
private fun TitleCell(modifier: Modifier) {
    val c = colors(); val shape = LocalShape.current
    val size = when { shape.wide -> 44.sp; shape.tablet -> 58.sp; else -> 48.sp }
    Column(modifier.padding(start = 12.dp, end = 12.dp, top = 9.dp, bottom = 11.dp)) {
        Text("reader / plotter", style = PT.label(c), modifier = Modifier.cssLines(10.5.sp, 1f))
        Text(
            "Library", style = PT.serif(size, c.ink, lh = size * 0.92f),
            modifier = Modifier.padding(top = 7.dp).cssLines(size, 0.92f, marginBottom = 4.dp),
        )
    }
}

@Composable
private fun SetCell(onClick: () -> Unit, modifier: Modifier) {
    val c = colors()
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val fg = if (pressed) c.paper else c.ink
    Column(
        modifier.heightIn(min = 64.dp).background(if (pressed) c.ink else Color.Transparent)
            .clickable(src, null, onClick = onClick).padding(7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SettingsMark(fg)
        Spacer(Modifier.height(6.dp))
        Text("Settings", style = PT.mono(12.5.sp, fg))
    }
}

/** A title-block cell: italic label over a value. */
@Composable
private fun Cell(label: String, value: String, modifier: Modifier, padEnd: Dp = 10.dp, quiet: Boolean = false) {
    val c = colors()
    Column(
        modifier.heightIn(min = 46.dp).padding(start = 10.dp, end = padEnd, top = 7.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, style = PT.label(c), modifier = Modifier.cssLines(10.5.sp, 1f))
        Spacer(Modifier.height(5.dp))
        Text(value, style = PT.mono(12.5.sp, if (quiet) c.mute else c.ink, lh = 16.25.sp).copy(lineBreak = LineBreak.Heading))
    }
}

// ---------------- the current book: a drafted detail ----------------

private fun chapterName(s: String) = s.replace(Regex("^[IVXLC]+\\.\\s*|^Chapter \\d+\\.?\\s*", RegexOption.IGNORE_CASE), "")

@Composable
private fun Now(ui: LibraryUi, on: LibraryActions, modifier: Modifier = Modifier) {
    val c = colors(); val shape = LocalShape.current
    val mt = if (shape.wide) 28.dp else 34.dp
    when {
        ui.folder == null -> Detail(
            "A", "No folder", "record 000", mt, modifier, onClick = on.chooseFolder, go = "Choose a folder", goRight = null,
        ) {
            StateBody(
                "No library yet.",
                "Choose the folder on this device that holds your books. Everything in it and its subfolders (EPUB, PDF, TXT, Markdown) is listed here. Reading positions are saved inside that folder, so they sync with it.",
                "no folder",
            )
        }
        ui.error != null && ui.current == null && ui.others.isEmpty() -> Detail(
            "A", "Fault", "record 000", mt, modifier, onClick = on.chooseFolder, go = "Choose another folder", goRight = null,
        ) { StateBody("Can’t read this folder.", ui.error, "unreadable") }
        !ui.loaded -> Construction(mt, modifier)
        ui.isEmpty -> Detail(
            "A", "Empty folder", "record 000", mt, modifier, onClick = on.chooseFolder, go = "Choose another folder", goRight = null,
        ) { StateBody("Nothing to read here.", "No EPUB, PDF, TXT or MD files in ${ui.folder}.", "0 files") }
        ui.current != null -> Current(ui.current, on, mt, modifier)
    }
}

@Composable
private fun Current(b: Book, on: LibraryActions, mt: Dp, modifier: Modifier) {
    val c = colors(); val shape = LocalShape.current
    val st = b.state
    val goRight = st.page?.let { p -> st.pages?.let { "page $p of $it" } ?: "page $p" }
    val length = when {
        st.pages != null -> "${st.pages} pages"
        st.chapters != null -> "${st.chapters} chapters"
        else -> "${formatSize(b.size)} ${b.format.label}"
    }
    val titleSize = when { shape.wide -> 36.sp; shape.tablet -> 54.sp; else -> 40.sp }
    Detail(
        "A", "Now reading", "record 001", mt, modifier, onClick = { on.open(b) }, onLongClick = { on.hold(b) },
        go = "Continue reading", goRight = goRight,
    ) {
        Text(
            b.title, style = PT.serif(titleSize, c.ink, italic = true, lh = titleSize * 1.02f).copy(lineBreak = LineBreak.Heading),
            modifier = Modifier.cssLines(titleSize, 1.02f),
        )
        Text(
            b.author ?: "unknown author", style = PT.mono(if (shape.tabPortrait) 13.5.sp else 12.5.sp, c.mute),
            modifier = Modifier.padding(top = 7.dp),
        )
        val gauge = @Composable { m: Modifier -> Gauge(b.progress, percent(b), st.chapters, length, m) }
        when {
            shape.tabPortrait -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1.1f).padding(top = 8.dp)) { gauge(Modifier) }
                Spacer(Modifier.width(26.dp))
                Box(Modifier.weight(1f).padding(top = 26.dp)) { Fields(b, threeColumns = false) }
            }
            shape.wide -> {
                // 300dp wide as in the mockup, smaller when the screen is short (system bars on a real tablet)
                BoxWithConstraints(Modifier.weight(1f, fill = false).fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                    val w = if (maxHeight == Dp.Infinity) 300.dp else (maxHeight * (376f / 226f)).coerceIn(180.dp, 300.dp)
                    gauge(Modifier.width(w))
                }
                Box(Modifier.padding(top = 8.dp)) { Fields(b, threeColumns = true) }
            }
            else -> {
                gauge(Modifier.padding(top = 4.dp))
                Box(Modifier.padding(top = 10.dp)) { Fields(b, threeColumns = false) }
            }
        }
    }
}

/** The labelled fields as a ruled cell grid. */
@Composable
private fun Fields(b: Book, threeColumns: Boolean) {
    val c = colors()
    val st = b.state
    val chapter = if (st.chapterIndex != null && st.chapters != null && st.chapter != null) "${st.chapterIndex} of ${st.chapters}, ${chapterName(st.chapter)}"
    else st.chapter?.let(::chapterName) ?: "not indexed"
    val file = b.path
    val size = "${formatSize(b.size)} ${b.format.label}"
    val opened = openedLabel(st.openedAt) ?: "never"
    @Composable fun RowScope.f(k: String, v: String, w: Float) =
        FieldCell(k, v, Modifier.weight(w).fillMaxHeight().edges(c.ink, end = HL, bottom = HL))
    Column(Modifier.fillMaxWidth().edges(c.ink, start = HL, top = HL)) {
        if (threeColumns) {
            Row(Modifier.height(IntrinsicSize.Min)) { f("file", file, 2f); f("size", size, 1f) }
            Row(Modifier.height(IntrinsicSize.Min)) { f("opened", opened, 1f); f("chapter", chapter, 2f) }
        } else {
            Row(Modifier.height(IntrinsicSize.Min)) { f("file", file, 1f) }
            Row(Modifier.height(IntrinsicSize.Min)) { f("size", size, 1f); f("opened", opened, 1f) }
            Row(Modifier.height(IntrinsicSize.Min)) { f("chapter", chapter, 1f) }
        }
    }
}

@Composable
private fun FieldCell(k: String, v: String, modifier: Modifier) {
    val c = colors()
    Column(modifier.padding(start = 9.dp, end = 9.dp, top = 7.dp, bottom = 8.dp)) {
        Text(k, style = PT.label(c), modifier = Modifier.cssLines(10.5.sp, 1f))
        Spacer(Modifier.height(4.dp))
        Text(v, style = PT.mono(12.5.sp, c.ink, lh = 16.25.sp))
    }
}

/**
 * The record: a heavy box with a paper gap and a fine outer ring (07's double rule), its tag cut
 * into the top border, and the solid ink "Continue" band. The whole box is the button.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Detail(
    letter: String, name: String, note: String, mt: Dp, modifier: Modifier, onClick: () -> Unit, onLongClick: (() -> Unit)? = null,
    go: String, goRight: String?, body: @Composable ColumnScope.() -> Unit,
) {
    val c = colors(); val shape = LocalShape.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val padX = if (shape.tabPortrait) 22.dp else 13.dp
    val padTop = when { shape.tabPortrait -> 30.dp; shape.wide -> 20.dp; else -> 22.dp }
    Box(modifier.fillMaxWidth().padding(top = mt)) {
        Column(
            Modifier.fillMaxWidth()
                .drawBehind {
                    // the outer ring, 3dp outside the heavy border
                    val hl = pen(HL)
                    val g = 3.dp.toPx().let { kotlin.math.round(it) } + hl
                    val col = c.ink
                    drawRect(c.paper, Offset(-g, -g), Size(size.width + 2 * g, size.height + 2 * g))
                    drawRect(col, Offset(-g, -g), Size(size.width + 2 * g, hl))
                    drawRect(col, Offset(-g, size.height + g - hl), Size(size.width + 2 * g, hl))
                    drawRect(col, Offset(-g, -g), Size(hl, size.height + 2 * g))
                    drawRect(col, Offset(size.width + g - hl, -g), Size(hl, size.height + 2 * g))
                }
                .penBox(c.ink, HV)
                .combinedClickable(src, null, onLongClick = onLongClick, onClick = onClick)
                .padding(HV),
        ) {
            // landscape: the body takes what the band leaves, so a weighted child (the protractor) can shrink
            Column(
                Modifier.then(if (shape.wide) Modifier.weight(1f, fill = false) else Modifier)
                    .fillMaxWidth().padding(start = padX - HV, end = padX - HV, top = padTop - HV),
                content = body,
            )
            GoBand(go, goRight, pressed, Modifier.padding(top = if (shape.tabPortrait) 22.dp else if (shape.wide) 12.dp else 14.dp))
        }
        Tag(letter, name, note, Modifier.offset(x = 9.5.dp, y = (-12).dp))
    }
}

@Composable
private fun GoBand(go: String, right: String?, pressed: Boolean, modifier: Modifier) {
    val c = colors(); val shape = LocalShape.current
    val bg = if (pressed) c.paper else c.ink
    val fg = if (pressed) c.ink else c.paper
    val h = when { shape.tabPortrait -> 60.dp; shape.wide -> 50.dp; else -> 54.dp }
    Row(
        modifier.fillMaxWidth().heightIn(min = h).background(bg)
            .then(if (pressed) Modifier.edges(c.ink, top = HV) else Modifier)
            .padding(horizontal = if (shape.tabPortrait) 22.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(go, style = PT.serif(23.sp, fg, italic = true), modifier = Modifier.weight(1f))
        if (right != null) {
            Spacer(Modifier.width(12.dp))
            Text(right, style = PT.mono(11.5.sp, fg.copy(alpha = 0.82f)), maxLines = 1)
        }
        if (shape.tablet) {
            Spacer(Modifier.width(10.dp))
            Canvas(Modifier.size(34.dp, 12.dp)) {
                val hl = pen(HL); val u = size.width / 34f
                val col = fg.copy(alpha = 0.82f)
                vLine(col, hl / 2, 1 * u, 11 * u, hl)
                hLine(col, 0f, 28 * u, 6 * u, hl)
                arrow(col, 34 * u, 6 * u, 0f, 9 * u, 3 * u)
            }
        }
    }
}

/** Body of the no-folder / empty / error records: a title, a note, and an empty detail. */
@Composable
private fun StateBody(title: String, text: String, void: String) {
    val c = colors(); val shape = LocalShape.current
    val size = when { shape.wide -> 32.sp; shape.tablet -> 44.sp; else -> 34.sp }
    Text(title, style = PT.serif(size, c.ink, italic = true, lh = size * 1.05f))
    Text(text, style = PT.mono(12.5.sp, c.mute, lh = 19.sp), modifier = Modifier.padding(top = 10.dp))
    Void(void, Modifier.padding(top = 16.dp))
}

/**
 * An empty detail: dashed construction lines crossed corner to corner (the drafting sign for
 * an opening), dimensioned with what's missing.
 */
@Composable
private fun Void(label: String, modifier: Modifier = Modifier) {
    val c = colors()
    val tm = rememberTextMeasurer()
    Canvas(modifier.fillMaxWidth().height(120.dp)) {
        val hl = pen(HL)
        val dimH = 22.dp.toPx()
        val boxH = size.height - dimH
        val inset = 2.dp.toPx()
        val d = 4.dp.toPx(); val g = 3.dp.toPx()
        hDash(c.ink, inset, size.width - inset, 0f, hl, d, g)
        hDash(c.ink, inset, size.width - inset, boxH - hl, hl, d, g)
        vDash(c.ink, inset, 0f, boxH, hl, d, g)
        vDash(c.ink, size.width - inset, 0f, boxH, hl, d, g)
        drawLine(c.ink, Offset(inset, 0f), Offset(size.width - inset, boxH), hl)
        drawLine(c.ink, Offset(inset, boxH), Offset(size.width - inset, 0f), hl)
        // dimension line under it
        val y = boxH + dimH / 2 + 2.dp.toPx()
        val style = PT.mono(11.5.sp, c.ink)
        val r = tm.measure(label, style)
        val gap = r.size.width / 2f + 8.dp.toPx()
        val cx = size.width / 2
        vLine(c.ink, inset, boxH + 3.dp.toPx(), y + 6.dp.toPx(), hl)
        vLine(c.ink, size.width - inset, boxH + 3.dp.toPx(), y + 6.dp.toPx(), hl)
        hLine(c.ink, inset, cx - gap, y, hl); hLine(c.ink, cx + gap, size.width - inset, y, hl)
        arrow(c.ink, inset, y, kotlin.math.PI.toFloat(), 8.dp.toPx(), 2.6.dp.toPx())
        arrow(c.ink, size.width - inset, y, 0f, 8.dp.toPx(), 2.6.dp.toPx())
        drawText(r, topLeft = Offset(cx - r.size.width / 2, y - r.size.height / 2))
    }
}

/** First scan: a quiet box of construction lines, nothing to tap. */
@Composable
private fun Construction(mt: Dp, modifier: Modifier) {
    val c = colors()
    Box(modifier.fillMaxWidth().padding(top = mt)) {
        Column(Modifier.fillMaxWidth().background(c.paper).dashedBox(c.ink).padding(start = 13.dp, end = 13.dp, top = 24.dp, bottom = 18.dp)) {
            Text("Surveying the folder…", style = PT.serif(26.sp, c.mute, italic = true))
            Text("Books appear here as they are found.", style = PT.mono(12.5.sp, c.mute), modifier = Modifier.padding(top = 8.dp))
        }
        Tag("A", "Survey", "scanning", Modifier.offset(x = 8.dp, y = (-12).dp))
    }
}

// ---------------- index: the parts list ----------------

private enum class Col { Fmt, Title, Meta, Prog, Pct }

private class Cols(val fmt: Dp, val meta: Dp?, val prog: Dp, val pct: Dp?)

@Composable
private fun cols(): Cols {
    val s = LocalShape.current
    return when {
        s.wide -> Cols(52.dp, 100.dp, 120.dp, 54.dp)
        s.tablet -> Cols(54.dp, 104.dp, 128.dp, 58.dp)
        else -> Cols(46.dp, null, 94.dp, null)
    }
}

/** One row of the parts list's grid: fixed columns with fine rules between them. */
@Composable
private fun GridRow(modifier: Modifier = Modifier, minHeight: Dp = 0.dp, cell: @Composable (Col, Modifier) -> Unit) {
    val c = colors(); val k = cols()
    val rule = Modifier.fillMaxHeight().edges(c.ink, start = HL)
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = minHeight)) {
        cell(Col.Fmt, Modifier.width(k.fmt).fillMaxHeight())
        cell(Col.Title, Modifier.weight(1f).then(rule))
        if (k.meta != null) cell(Col.Meta, Modifier.width(k.meta).then(rule))
        cell(Col.Prog, Modifier.width(k.prog).then(rule))
        if (k.pct != null) cell(Col.Pct, Modifier.width(k.pct).then(rule))
    }
}

private fun LazyListScope.index(ui: LibraryUi, on: LibraryActions, wrap: @Composable (@Composable () -> Unit) -> Unit) {
    if (!ui.loaded || ui.isEmpty || ui.folder == null) return
    item(key = "ih") { wrap { IndexHead(ui) } }
    items(ui.others, key = { it.key }) { b -> wrap { IndexRow(b, on) } }
    item(key = "ie") { wrap { IndexEnd(ui) } }
}

@Composable
private fun IndexHead(ui: LibraryUi) {
    val c = colors(); val shape = LocalShape.current
    // landscape: the index's top border lines up with the title block's, its tag in the frame's padding
    val mt = if (shape.wide) 0.dp else 34.dp
    Box(Modifier.fillMaxWidth().padding(top = mt)) {
        Column(Modifier.fillMaxWidth().background(c.paper).edges(c.ink, start = HV, top = HV, end = HV).padding(start = HV, end = HV, top = HV)) {
            GridRow(Modifier.edges(c.ink, bottom = HL)) { col, m ->
                val lb = PT.label(c)
                Box(m.padding(start = 10.dp, end = 10.dp, top = 22.dp, bottom = 8.dp).cssLines(10.5.sp, 1f)) {
                    when (col) {
                        Col.Fmt -> Text("fmt", style = lb)
                        Col.Title -> Text("title, author", style = lb)
                        Col.Meta -> Text("size, opened", style = lb)
                        Col.Prog -> Text(if (shape.tablet) "progress" else "read", style = lb)
                        Col.Pct -> Text("read", style = lb, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        Tag("B", "Index", "${ui.others.size} records, by ${ui.sort.name.lowercase()}", Modifier.offset(x = 9.5.dp, y = (-12).dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IndexRow(b: Book, on: LibraryActions) {
    val c0 = colors(); val shape = LocalShape.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val c = if (pressed) c0.copy(ink = c0.paper, mute = c0.paper) else c0
    val state = when { b.isFinished -> "done"; b.isNew -> "new"; else -> "${percent(b)}%" }
    val stateColor = if (b.isNew || b.isFinished) c.mute else c.ink
    val vPad = if (shape.wide) 7.dp else 9.dp
    CompositionLocalProvider(LocalColors provides c) {
        Box(
            Modifier.fillMaxWidth().background(c0.paper).edges(c0.ink, start = HV, end = HV).padding(horizontal = HV)
                .background(if (pressed) c0.ink else c0.paper)
                .combinedClickable(src, null, onLongClick = { on.hold(b) }) { on.open(b) },
        ) {
            GridRow(Modifier.edges(c.ink, bottom = HL), minHeight = if (shape.wide) 54.dp else 60.dp) { col, m ->
                when (col) {
                    Col.Fmt -> Text(
                        b.format.label, style = PT.mono(11.5.sp, c.mute, lh = 14.4.sp),
                        modifier = m.padding(start = 10.dp, end = 4.dp, top = if (shape.wide) 11.dp else 13.dp, bottom = vPad),
                    )
                    Col.Title -> Column(m.padding(horizontal = 10.dp, vertical = vPad), verticalArrangement = Arrangement.Center) {
                        Text(
                            b.title, style = PT.serif(21.sp, if (b.isFinished) c.mute else c.ink, lh = 22.7.sp), modifier = Modifier.cssLines(21.sp, 1.08f),
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(b.author ?: "unknown", style = PT.mono(11.5.sp, c.mute), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Col.Meta -> Column(m.padding(horizontal = 10.dp, vertical = vPad), verticalArrangement = Arrangement.Center) {
                        Text(formatSize(b.size), style = PT.mono(12.sp, c.ink), maxLines = 1)
                        Spacer(Modifier.height(4.dp))
                        Text(openedLabel(b.state.openedAt) ?: "never", style = PT.mono(12.sp, c.mute), maxLines = 1, overflow = TextOverflow.Clip)
                    }
                    Col.Prog -> Column(
                        m.padding(start = 10.dp, end = if (shape.tablet) 14.dp else 10.dp, top = vPad, bottom = vPad),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        RowScale(b.progress, color = c.ink)
                        if (!shape.tablet) {
                            Spacer(Modifier.height(5.dp))
                            Text(state, style = PT.mono(11.5.sp, stateColor))
                        }
                    }
                    Col.Pct -> Box(m.padding(horizontal = 10.dp, vertical = vPad), contentAlignment = Alignment.CenterEnd) {
                        Text(state, style = PT.mono(12.sp, stateColor), textAlign = TextAlign.End)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IndexEnd(ui: LibraryUi) {
    val c = colors()
    Column(Modifier.fillMaxWidth().background(c.paper).edges(c.ink, start = HV, end = HV, bottom = HV).padding(start = HV, end = HV, bottom = HV)) {
        FlowRow(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 9.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = c.ink)) { append("${ui.counts.total}") }
                    append(" records listed")
                    if (ui.scanning) append(", scanning")
                },
                style = PT.mono(11.sp, c.mute), modifier = Modifier.padding(end = 12.dp),
            )
            Text("Hold a book to mark it finished or reset it.", style = PT.mono(11.sp, c.mute))
        }
    }
}

// ---------------- summary (tablet landscape) ----------------

@Composable
private fun Summary(ui: LibraryUi) {
    val c = colors()
    val n = ui.counts
    Box(Modifier.fillMaxWidth().padding(top = 30.dp)) {
        Column(Modifier.fillMaxWidth().background(c.paper).penBox(c.ink, HV).padding(HV).padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 8.dp)) {
            val lines = buildList<Pair<String, Int>?> {
                add("open" to n.open); add("new" to n.new); add("finished" to n.finished); add(null)
                Format.entries.forEach { f -> n.byFormat[f]?.let { add(f.label to it) } }
                add(null)
            }
            lines.forEachIndexed { i, l ->
                if (l == null) { Spacer(Modifier.height(8.dp)); return@forEachIndexed }
                val last = i + 1 < lines.size && lines[i + 1] == null
                Row(
                    Modifier.fillMaxWidth().edges(c.ink, bottom = if (last) 0.dp else HL, dashedBottom = true),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(l.first, style = PT.mono(12.sp, c.mute, italic = true), modifier = Modifier.width(125.dp).padding(vertical = 6.dp))
                    Text("${l.second}", style = PT.mono(12.5.sp, c.ink), textAlign = TextAlign.End, modifier = Modifier.width(38.dp).padding(end = 14.dp))
                    Tally(l.second, n.total, Modifier.weight(1f))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("folder size", style = PT.mono(12.sp, c.mute, italic = true), modifier = Modifier.width(125.dp).padding(vertical = 6.dp))
                Text(formatSize(n.bytes), style = PT.mono(12.5.sp, c.ink))
            }
        }
        Tag("C", "Summary", null, Modifier.offset(x = 9.5.dp, y = (-12).dp))
    }
}
