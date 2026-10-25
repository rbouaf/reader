package app.reader.looks.trace

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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.reader.data.Book
import app.reader.data.Format
import app.reader.data.Sort
import app.reader.data.formatSize
import app.reader.looks.LibraryActions
import app.reader.looks.LibraryUi
import app.reader.looks.common.FitHeight
import app.reader.looks.openedLabel
import app.reader.looks.percent
import app.reader.theme.Colors

/** How the library lays out at the current size (the mockup's three breakpoints). */
data class Shape(val tablet: Boolean, val twoColumn: Boolean)

@Composable
fun TraceLibrary(ui: LibraryUi, on: LibraryActions, appName: String = "trace") {
    val c = colors()
    BoxWithConstraints(Modifier.fillMaxSize().background(c.paper)) {
        val g = Grid.of(maxWidth)
        val shape = Shape(tablet = maxWidth >= 700.dp, twoColumn = maxWidth >= 1000.dp && maxWidth > maxHeight)
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        CompositionLocalProvider(LocalGrid provides g) {
            val side = if (shape.tablet) g.ch(4) else g.ch(2)
            if (shape.twoColumn) {
                Row(Modifier.fillMaxSize().padding(horizontal = side), horizontalArrangement = Arrangement.spacedBy(g.ch(5))) {
                    BoxWithConstraints(Modifier.width(g.ch(50)).fillMaxHeight()) {
                        val top = insets.calculateTopPadding() + g.lines(1.5f)
                        // the header and the whole record fit the first screen; the summary may need a scroll
                        val budget = maxHeight - top - insets.calculateBottomPadding() - g.lines(0.5f)
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = top, bottom = g.lines(3))) {
                            FitHeight(budget, levels = 6) { t ->
                                Head(ui, on, appName, t)
                                Record(ui, on, t)
                            }
                            if (ui.loaded && !ui.isEmpty) Summary(ui)
                        }
                    }
                    LazyColumn(
                        Modifier.weight(1f).fillMaxSize(),
                        contentPadding = PaddingValues(top = insets.calculateTopPadding() + g.lines(1) + 10.dp, bottom = insets.calculateBottomPadding() + g.lines(3)),
                    ) { index(ui, on, shape) }
                }
            } else {
                // tablet portrait: a 96ch column, centred
                val inset = if (shape.tablet) maxOf(side, (maxWidth - g.ch(96)) / 2) else side
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = inset, end = inset,
                        top = insets.calculateTopPadding() + if (shape.tablet) g.lines(2) else g.lines(1),
                        bottom = insets.calculateBottomPadding() + g.lines(3),
                    ),
                ) {
                    item { Head(ui, on, appName) }
                    item { Record(ui, on) }
                    index(ui, on, shape)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Head(ui: LibraryUi, on: LibraryActions, appName: String, t: Int = 0) {
    val g = grid(); val c = colors()
    Row(Modifier.fillMaxWidth().height(44.dp).offset(y = (-12).dp), verticalAlignment = Alignment.CenterVertically) {
        Text("reader / $appName", style = RT.label(g, c), modifier = Modifier.weight(1f))
        TextButton("Settings", on.settings)
    }
    // tighter levels: the sort choice moves up beside the title, and the gaps close
    val sortable = ui.loaded && !ui.isEmpty && (ui.error == null || ui.current != null)
    val sortInline = t >= 1 && ui.folder != null && sortable
    if (sortInline) {
        Row(Modifier.fillMaxWidth().padding(top = g.lines(0.5f) - 12.dp)) {
            Text("Library", style = RT.double(g, c), modifier = Modifier.weight(1f).alignByBaseline())
            SortChoice(ui, on, Modifier.alignByBaseline())
        }
    } else Text("Library", style = RT.double(g, c), modifier = Modifier.padding(top = g.lines(1) - 12.dp))
    if (ui.folder == null) return
    Fields(Modifier.padding(top = if (t >= 1) g.lines(0.5f) else g.lines(1))) {
        field("folder") { Text(ui.folder, style = RT.base(g, c)) }
        field("status") {
            val n = ui.counts
            Text(
                when {
                    // the record box below spells the error out; the status line just names it
                    ui.error != null && ui.current == null -> "read error"
                    ui.error != null -> ui.error
                    !ui.loaded -> "scanning…"
                    else -> "${n.total} files, ${n.open} open, ${n.new} new, ${n.finished} done" + if (ui.scanning) ", rescanning" else ""
                },
                style = RT.base(g, c),
            )
        }
        if (sortable && !sortInline) field("sort by") { SortChoice(ui, on, Modifier.offset(y = (-12).dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SortChoice(ui: LibraryUi, on: LibraryActions, modifier: Modifier = Modifier) {
    val g = grid(); val c = colors()
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(g.ch(1))) {
        listOf(Sort.Recent to "recent", Sort.Title to "title", Sort.Author to "author").forEach { (s, label) ->
            val sel = ui.sort == s
            Box(Modifier.heightIn(min = 44.dp).clickable { on.sort(s) }, contentAlignment = Alignment.CenterStart) {
                Text(
                    (if (sel) "(*) " else "( ) ") + label,
                    style = if (sel) RT.title(g, c) else RT.base(g, c).copy(color = c.mute),
                )
            }
        }
    }
}

/** The two-column label/value list: 8ch italic labels, values wrapping in the rest. */
class FieldsScope { val rows = mutableListOf<Pair<String, @Composable () -> Unit>>(); fun field(k: String, v: @Composable () -> Unit) { rows += k to v } }

@Composable
fun Fields(modifier: Modifier = Modifier, labelWidth: Int = 8, build: FieldsScope.() -> Unit) {
    val g = grid(); val c = colors()
    val scope = FieldsScope().apply(build)
    Column(modifier) {
        scope.rows.forEach { (k, v) ->
            Row {
                Text(k, style = RT.label(g, c), modifier = Modifier.width(g.ch(labelWidth)))
                Spacer(Modifier.width(g.ch(1)))
                Box(Modifier.weight(1f)) { v() }
            }
        }
    }
}

@Composable
fun TextButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, pre: String = "") {
    val g = grid(); val c = colors()
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    Box(
        modifier.heightIn(min = 44.dp).clickable(src, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            bracketed(label, c, pre),
            style = RT.base(g, c).copy(color = if (pressed) c.paper else c.ink),
            modifier = if (pressed) Modifier.background(c.ink) else Modifier,
        )
    }
}

private fun chapterName(s: String) = s.replace(Regex("^[IVXLC]+\\.\\s*|^Chapter \\d+\\.?\\s*", RegexOption.IGNORE_CASE), "")

@Composable
private fun Record(ui: LibraryUi, on: LibraryActions, t: Int = 0) {
    val g = grid(); val c = colors()
    when {
        ui.folder == null -> RecordBox("record 000", "no folder", onClick = on.chooseFolder, go = "> Choose a folder", goRight = "") {
            Text("No library yet.", style = RT.title(g, c))
            Text(
                "Choose the folder on this device that holds your books. Everything in it and its subfolders (EPUB, PDF, TXT, Markdown) is listed here. Reading positions are saved inside that folder, so they sync with it.",
                style = RT.dim(g, c), modifier = Modifier.padding(top = g.lines(1)),
            )
        }
        ui.error != null && ui.current == null -> RecordBox("record 000", "read error", onClick = on.chooseFolder, go = "> Choose another folder", goRight = "") {
            Text("Can't read this folder.", style = RT.title(g, c))
            Text(ui.error, style = RT.dim(g, c), modifier = Modifier.padding(top = g.lines(1)))
        }
        !ui.loaded -> Scanning(ui.folder)
        ui.isEmpty -> RecordBox("record 000", "empty folder", onClick = on.chooseFolder, go = "> Choose another folder", goRight = "") {
            Text("Nothing to read here.", style = RT.title(g, c))
            Text("No EPUB, PDF, TXT or MD files in ${ui.folder}.", style = RT.dim(g, c), modifier = Modifier.padding(top = g.lines(1)))
            IdleChannel(Modifier.padding(top = g.lines(1)))
        }
        ui.current != null -> {
            val b = ui.current
            val st = b.state
            RecordBox(
                "record 001", "now reading", onClick = { on.open(b) }, onLongClick = { on.hold(b) },
                top = if (t >= 1) g.lines(1.5f) else g.lines(2), gap = if (t >= 1) g.lines(0.5f) else g.lines(1),
                go = "> Continue reading",
                goRight = st.page?.let { p -> st.pages?.let { "page $p of $it" } ?: "page $p" } ?: "",
            ) {
                // tighter levels: 1 closes gaps, 2 shrinks the chart, 3 puts the path on one line,
                // 4 caps the title at two lines, 5 sets it smaller
                Text(
                    b.title, style = (if (t >= 5) RT.title(g, c) else RT.double(g, c)).copy(lineBreak = LineBreak.Heading),
                    maxLines = if (t >= 4) 2 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis,
                )
                b.author?.let { Text(it, style = RT.dim(g, c), maxLines = if (t >= 4) 1 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis) }
                Fields(Modifier.padding(top = if (t >= 1) g.lines(0.5f) else g.lines(1))) {
                    field("file") { Text(b.path, style = RT.base(g, c), maxLines = if (t >= 3) 1 else Int.MAX_VALUE, overflow = TextOverflow.MiddleEllipsis) }
                    field("size") { Text("${formatSize(b.size)} ${b.format.label}", style = RT.base(g, c)) }
                    field("opened") { Text(openedLabel(st.openedAt) ?: "never", style = RT.base(g, c)) }
                    field("chapter") {
                        Text(
                            if (st.chapterIndex != null && st.chapters != null && st.chapter != null) "${st.chapterIndex} of ${st.chapters}, ${chapterName(st.chapter)}"
                            else st.chapter?.let(::chapterName) ?: "not indexed",
                            style = RT.base(g, c),
                        )
                    }
                    field("read") { Channel(b, rows = when { t >= 5 -> 2f; t >= 2 -> 3f; else -> 5f }) }
                }
            }
        }
    }
}

/** The double-ruled record box with its tag cut into the top border and the inverted Continue bar. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordBox(
    tag: String, tagNote: String, onClick: () -> Unit, onLongClick: (() -> Unit)? = null,
    go: String, goRight: String, modifier: Modifier = Modifier,
    top: Dp = grid().lines(2), gap: Dp = grid().lines(1), body: @Composable () -> Unit,
) {
    val g = grid(); val c = colors()
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val border = 3.dp
    Box(modifier.fillMaxWidth().padding(top = top)) {
        Column(
            Modifier.fillMaxWidth().ruledBox(c.ink, double = true)
                .combinedClickable(src, null, onLongClick = onLongClick, onClick = onClick)
                .padding(top = g.lines(1) - border, start = border, end = border, bottom = border),
        ) {
            Column(Modifier.padding(horizontal = g.ch(2) - border)) { body() }
            Row(
                Modifier.padding(top = gap).fillMaxWidth().heightIn(min = g.lines(2.5f))
                    .background(if (pressed) c.paper else c.ink).padding(horizontal = g.ch(2) - border),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val fg = if (pressed) c.ink else c.paper
                Text(go, style = RT.title(g, c).copy(color = fg), modifier = Modifier.weight(1f))
                Text(goRight, style = RT.label(g, c).copy(color = fg.copy(alpha = 0.7f)))
            }
        }
        Text(
            buildAnnotatedString {
                append(tag)
                withStyle(SpanStyle(fontWeight = FontWeight.Light, color = c.mute, fontStyle = androidx.compose.ui.text.font.FontStyle.Normal)) { append(" $tagNote") }
            },
            style = RT.label(g, c).copy(color = c.ink),
            modifier = Modifier.offset(x = g.ch(1), y = -(g.lines(0.5f) + 2.dp)).background(c.paper).padding(horizontal = g.ch(1)),
        )
    }
}

private fun LazyListScope.index(ui: LibraryUi, on: LibraryActions, shape: Shape) {
    if (!ui.loaded || ui.isEmpty || ui.folder == null) return
    item { IndexHead(ui, shape) }
    items(ui.others, key = { it.key }) { b -> IndexRow(b, shape, on) }
    item {
        val g = grid(); val c = colors()
        Column {
            TextRule()
            Row(Modifier.padding(top = g.lines(1)), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildAnnotatedString {
                        append("${ui.counts.total} records listed. ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = c.ink)) { append(if (ui.scanning) "Scanning." else "Ready.") }
                    },
                    style = RT.dim(g, c),
                )
                BlockCursor(Modifier.padding(start = 1.dp))
            }
        }
    }
}

@Composable
private fun IndexHead(ui: LibraryUi, shape: Shape) {
    val g = grid(); val c = colors()
    Column(Modifier.padding(top = if (shape.twoColumn) 0.dp else g.lines(2))) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Index", style = RT.title(g, c), modifier = Modifier.weight(1f))
            Text("${ui.others.size} records, by ${ui.sort.name.lowercase()}", style = RT.dim(g, c))
        }
        Columns(shape, Modifier.padding(top = g.lines(1))) { col ->
            val st = RT.label(g, c)
            when (col) {
                Col.Fmt -> Text("fmt", style = st)
                Col.Title -> Text("title, author", style = st)
                Col.Meta -> Text("size, opened", style = st)
                Col.Bar -> Text("progress", style = st)
                Col.Pct -> Text("read", style = st, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
            }
        }
        TextRule()
    }
}

enum class Col { Fmt, Title, Meta, Bar, Pct }

/** The index's column grid: 4ch · 1fr · 7ch · 4ch on phones; 4 · 1fr · 12 · 16 (or 12) · 4 on tablets. */
@Composable
fun Columns(shape: Shape, modifier: Modifier = Modifier, cell: @Composable (Col) -> Unit) {
    val g = grid()
    val gap = if (shape.tablet) g.ch(2) else g.ch(1)
    Row(modifier.fillMaxWidth()) {
        Box(Modifier.width(g.ch(4))) { cell(Col.Fmt) }
        Spacer(Modifier.width(gap))
        Box(Modifier.weight(1f)) { cell(Col.Title) }
        if (shape.tablet) {
            Spacer(Modifier.width(gap)); Box(Modifier.width(g.ch(12))) { cell(Col.Meta) }
            Spacer(Modifier.width(gap)); Box(Modifier.width(g.ch(if (shape.twoColumn) 12 else 16))) { cell(Col.Bar) }
        } else {
            Spacer(Modifier.width(gap)); Box(Modifier.width(g.ch(7))) { }
        }
        Spacer(Modifier.width(gap))
        Box(Modifier.width(g.ch(4))) { cell(Col.Pct) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IndexRow(b: Book, shape: Shape, on: LibraryActions) {
    val g = grid(); val c0 = colors()
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    // pressed rows invert: ink ground, paper text
    val c = if (pressed) c0.copy(ink = c0.paper, mute = c0.paper, rule = c0.paper.copy(alpha = 0.5f)) else c0
    val gap = if (shape.tablet) g.ch(2) else g.ch(1)
    val titleStyle = if (b.isFinished) RT.base(g, c).copy(fontWeight = FontWeight.ExtraLight, color = c.mute) else RT.title(g, c)
    val authorStyle = if (b.isFinished) RT.base(g, c).copy(fontWeight = FontWeight.ExtraLight, color = c.mute) else RT.dim(g, c)
    val state = when { b.isFinished -> "done"; b.isNew -> "new"; else -> "${percent(b)}%" }
    val pctStyle = RT.base(g, c).copy(color = if (b.isNew) c.mute else c.ink, textAlign = TextAlign.End)
    CompositionLocalProvider(app.reader.theme.LocalColors provides c) {
        Column(
            Modifier.fillMaxWidth().background(if (pressed) c0.ink else c0.paper)
                .combinedClickable(src, null, onLongClick = { on.hold(b) }) { on.open(b) }
                .padding(vertical = 10.dp),
        ) {
            if (shape.tablet) {
                Row {
                    Text(b.format.label, style = RT.dim(g, c), modifier = Modifier.width(g.ch(4)))
                    Spacer(Modifier.width(gap))
                    Column(Modifier.weight(1f)) {
                        Text(b.title, style = titleStyle)
                        Text(b.author ?: "unknown", style = authorStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(gap))
                    Column(Modifier.width(g.ch(12))) {
                        Text(formatSize(b.size), style = RT.base(g, c))
                        Text(openedLabel(b.state.openedAt) ?: "never", style = RT.dim(g, c), maxLines = 1, overflow = TextOverflow.Clip)
                    }
                    Spacer(Modifier.width(gap))
                    MiniTrace(b, g.ch(if (shape.twoColumn) 12 else 16))
                    Spacer(Modifier.width(gap))
                    Text(state, style = pctStyle, modifier = Modifier.width(g.ch(4)))
                }
            } else {
                Row {
                    Text(b.format.label, style = RT.dim(g, c), modifier = Modifier.width(g.ch(4)))
                    Spacer(Modifier.width(gap))
                    Text(b.title, style = titleStyle, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(gap))
                    Text(state, style = pctStyle, modifier = Modifier.width(g.ch(4)))
                }
                Row {
                    Spacer(Modifier.width(g.ch(4) + gap))
                    Text(b.author ?: "unknown", style = authorStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(gap))
                    MiniTrace(b, g.ch(11) + gap)
                }
            }
        }
    }
}

@Composable
private fun Summary(ui: LibraryUi) {
    val g = grid(); val c = colors()
    val n = ui.counts
    // the whole folder as one channel: every day of reading in every book, summed into one climb
    val act = remember(ui.all) { folderActivity(ui.all) }
    val booksRead = if (act.booksRead < 10f) "%.1f".format(act.booksRead) else "%.0f".format(act.booksRead)
    val formats = Format.entries.mapNotNull { f -> n.byFormat[f]?.let { f.label to it } }
    // labels fit the longest ("folder size"); values right-align in a column as wide as the widest count
    val kw = g.ch(11)
    val vw = g.ch(maxOf(3, (listOf(n.open, n.new, n.finished, act.sittings) + formats.map { it.second }).maxOf { "$it".length }, booksRead.length))
    Column(Modifier.padding(top = g.lines(2))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Summary", style = RT.title(g, c))
            Spacer(Modifier.width(g.ch(1)))
            TextRule(Modifier.weight(1f))
        }
        Spacer(Modifier.height(g.lines(0.5f)))
        @Composable fun key(k: String) = Text(k, style = RT.label(g, c), maxLines = 1, softWrap = false, modifier = Modifier.width(kw))
        @Composable fun value(v: String) = Text(v, style = RT.title(g, c), textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.width(vw))
        @Composable fun line(k: String, v: Int) {
            Row {
                key(k)
                Spacer(Modifier.width(g.ch(1)))
                value("$v")
                Spacer(Modifier.width(g.ch(1)))
                // one slash per book while they fit; past that, scaled to the width so the bars still compare
                BoxWithConstraints(Modifier.weight(1f)) {
                    val cells = (maxWidth / g.chDp).toInt().coerceAtLeast(1)
                    val (on, off) = if (n.total <= cells) v to (n.total - v).coerceAtLeast(0)
                        else Math.round(v.toFloat() / n.total * cells).let { it to cells - it }
                    Text(
                        buildAnnotatedString {
                            append("/".repeat(on))
                            withStyle(SpanStyle(color = c.mute)) { append(".".repeat(off)) }
                        },
                        style = RT.base(g, c), maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                    )
                }
            }
        }
        line("open", n.open); line("new", n.new); line("finished", n.finished)
        Spacer(Modifier.height(g.lines(0.5f)))
        Row {
            Column {
                @Composable fun count(k: String, v: String) = Row {
                    key(k)
                    Spacer(Modifier.width(g.ch(1)))
                    value(v)
                }
                count("sittings", "${act.sittings}")
                count("books read", booksRead)
            }
            Spacer(Modifier.width(g.ch(1)))
            FolderTrace(act, Modifier.weight(1f))
        }
        Spacer(Modifier.height(g.lines(0.5f)))
        formats.forEach { (k, v) -> line(k, v) }
        Spacer(Modifier.height(g.lines(0.5f)))
        Row {
            key("folder size")
            Spacer(Modifier.width(g.ch(1)))
            Text(formatSize(n.bytes), style = RT.title(g, c), maxLines = 1, softWrap = false)
        }
    }
}

/** The first scan: an idle channel, no pen yet, and the cursor. */
@Composable
private fun Scanning(folder: String) {
    val g = grid(); val c = colors()
    Column(Modifier.fillMaxWidth().padding(top = g.lines(2))) {
        Text(
            buildAnnotatedString {
                append("record 000")
                withStyle(SpanStyle(fontWeight = FontWeight.Light, color = c.mute, fontStyle = androidx.compose.ui.text.font.FontStyle.Normal)) { append(" scanning") }
            },
            style = RT.label(g, c).copy(color = c.ink),
        )
        IdleChannel(Modifier.padding(top = g.lines(0.5f)))
        Row(Modifier.padding(top = g.lines(0.5f)), verticalAlignment = Alignment.CenterVertically) {
            Text("Reading $folder", style = RT.dim(g, c), modifier = Modifier.weight(1f, fill = false))
            BlockCursor(Modifier.padding(start = 2.dp))
        }
    }
}
