package app.reader.looks.radar

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
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
import app.reader.data.sortedBy
import app.reader.looks.LibraryActions
import app.reader.looks.LibraryUi
import app.reader.looks.common.FirstScreen
import app.reader.looks.common.LocalStretch
import app.reader.looks.openedLabel
import app.reader.looks.percent
import app.reader.theme.LocalColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** The mockup's three breakpoints, and the sizes that change with them. */
@Immutable
data class Shape(val tablet: Boolean, val twoColumn: Boolean, val portrait: Boolean) {
    val pad get() = if (tablet) 20.dp else 16.dp
    val dial get() = when { twoColumn -> 140.dp; tablet && portrait -> 184.dp; tablet -> 150.dp; else -> 136.dp }
}

val LocalShape = staticCompositionLocalOf { Shape(false, false, true) }

@Composable
fun RadarLibrary(ui: LibraryUi, on: LibraryActions) {
    val c = colors()
    BoxWithConstraints(Modifier.fillMaxSize().background(c.paper)) {
        val shape = Shape(maxWidth >= 700.dp, maxWidth >= 1000.dp && maxWidth > maxHeight, maxHeight >= maxWidth)
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val orders = remember(ui.current, ui.others) { orders(ui) }
        CompositionLocalProvider(LocalShape provides shape) {
            if (shape.twoColumn) {
                Row(Modifier.fillMaxSize().padding(horizontal = 40.dp), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                    BoxWithConstraints(Modifier.width(470.dp).fillMaxHeight()) {
                        val top = insets.calculateTopPadding() + 16.dp
                        val screen = maxHeight - top
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                .padding(top = top, bottom = insets.calculateBottomPadding() + 64.dp),
                        ) {
                            FirstScreen(
                                screen = screen, bottomInset = insets.calculateBottomPadding(), breathing = 20.dp, levels = 1,
                                first = { Head(ui, on); Record(ui, on, orders) },
                                rest = { if (ui.loaded && ui.all.isNotEmpty()) Summary(ui, on, orders) },
                            )
                        }
                    }
                    LazyColumn(
                        Modifier.weight(1f).fillMaxHeight(),
                        contentPadding = PaddingValues(top = insets.calculateTopPadding() + 16.dp, bottom = insets.calculateBottomPadding() + 64.dp),
                    ) { index(ui, on, orders, 8.dp) }
                }
            } else {
                val side = if (shape.tablet) maxOf(40.dp, (maxWidth - 720.dp) / 2) else 16.dp
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = side, end = side,
                        top = insets.calculateTopPadding() + if (shape.tablet) 30.dp else 14.dp,
                        bottom = insets.calculateBottomPadding() + if (shape.tablet) 64.dp else 56.dp,
                    ),
                ) {
                    item { Head(ui, on) }
                    item { Record(ui, on, orders) }
                    index(ui, on, orders, 34.dp)
                }
            }
        }
    }
}

/** Record numbers: the current book is 01, then the rest by recency, so they stay put across sorts. */
private fun orders(ui: LibraryUi): Map<String, String> {
    val list = listOfNotNull(ui.current) + ui.others.sortedBy(Sort.Recent)
    val width = maxOf(2, list.size.toString().length)
    return list.mapIndexed { i, b -> b.key to (i + 1).toString().padStart(width, '0') }.toMap()
}

private fun stateOf(b: Book) = when { b.isFinished -> DialState.Done; b.isNew -> DialState.New; else -> DialState.Read }

// ---------------------------------------------------------------- header

@Composable
private fun Head(ui: LibraryUi, on: LibraryActions) {
    val c = colors(); val shape = LocalShape.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Glyph(Glyphs.Contact, c.mute, 11.dp)
            Text("reader / radar", style = RS.label(c).copy(letterSpacing = 0.sp))
        }
        Blk("Settings", on.settings, icon = { Glyph(Glyphs.Gear, it, 16.dp) })
    }
    val h1 = when { shape.twoColumn -> 38.sp; shape.tablet -> 42.sp; else -> 32.sp }
    Text(
        disp("Library"), style = RS.display(c, h1),
        modifier = Modifier.padding(top = if (shape.twoColumn) 4.dp else 16.dp).offset(x = (-1).dp),
    )
    if (ui.folder == null) return
    val lw = if (shape.tablet) 72.dp else 60.dp
    Column(Modifier.padding(top = if (shape.twoColumn) 12.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        HeadField("folder", lw) { Text(ui.folder, style = dd(c)) }
        HeadField("status", lw) {
            val n = ui.counts
            Text(
                when {
                    ui.error != null -> ui.error
                    !ui.loaded -> "scanning…"
                    else -> "${n.total} files, ${n.open} open, ${n.new} new, ${n.finished} done" + if (ui.scanning) ", rescanning" else ""
                },
                style = dd(c),
            )
        }
        HeadField("sort by", lw) {
            // 44dp targets that overlap the rows around them, as the mockup's margin: -12px 0
            Row(
                Modifier.layout { m, k ->
                    val p = m.measure(k.copy(minHeight = 0, maxHeight = 44.dp.roundToPx()))
                    layout(p.width, 20.dp.roundToPx()) { p.place(0, (20.dp.roundToPx() - p.height) / 2) }
                },
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                listOf(Sort.Recent to "recent", Sort.Title to "title", Sort.Author to "author").forEach { (s, label) ->
                    val sel = ui.sort == s
                    Row(
                        Modifier.height(44.dp).clickable(remember { MutableInteractionSource() }, null) { on.sort(s) },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        SortBlip(sel)
                        Text(label, style = RS.mono(c, 12.5.sp, if (sel) 500 else 400, if (sel) c.ink else c.mute, ls = (-0.02).em))
                    }
                }
            }
        }
    }
}

private fun dd(c: app.reader.theme.Colors) = RS.mono(c, 12.5.sp, ls = (-0.02).em)

@Composable
private fun HeadField(k: String, lw: Dp, v: @Composable () -> Unit) {
    val c = colors()
    Row {
        Text(k, style = RS.label(c), modifier = Modifier.width(lw))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) { v() }
    }
}

// ---------------------------------------------------------------- the record

private fun chapterName(s: String) = s.replace(Regex("^[IVXLC]+\\.\\s*|^Chapter \\d+\\.?\\s*", RegexOption.IGNORE_CASE), "")

@Composable
private fun Record(ui: LibraryUi, on: LibraryActions, orders: Map<String, String>) {
    val c = colors(); val shape = LocalShape.current
    val body = RS.mono(c, 12.5.sp, 300, c.mute, lh = 19.sp, ls = (-0.01).em)
    when {
        ui.folder == null -> RecordBox("record 00", "no contact", on.chooseFolder, null, "Choose a folder", null, stretch = 0.dp) {
            RecTitle("No library yet")
            RecBody(
                {},
                fields = {
                    Quiet("Choose the folder on this device that holds your books. Everything in it and its subfolders (EPUB, PDF, TXT, Markdown) is listed here. Reading positions are saved inside that folder, so they sync with it.")
                },
                dial = { RecordDial(null, "--", emptyList(), it) },
            )
        }
        !ui.loaded && ui.current == null -> RecordBox("record 00", "scanning", null, null, null, null, stretch = 0.dp) {
            RecTitle("Scanning")
            // the only motion in the look: a slow sweep while the first scan runs
            val t = rememberInfiniteTransition(label = "scan")
            val a by t.animateFloat(0f, 360f, infiniteRepeatable(tween(4800, easing = LinearEasing)), label = "sweep")
            RecBody(
                {},
                fields = { Quiet("Looking for EPUB, PDF, TXT and Markdown files in ${ui.folder}.") },
                dial = { RecordDial(null, "··", emptyList(), it, scan = 128f + a) },
            )
        }
        ui.error != null && ui.all.isEmpty() -> RecordBox("record 00", "signal lost", on.chooseFolder, null, "Choose another folder", null) {
            RecTitle("Can’t read the folder")
            Quiet(ui.error, Modifier.padding(top = 7.dp))
        }
        ui.isEmpty -> RecordBox("record 00", "no contacts", on.chooseFolder, null, "Choose another folder", null, stretch = 0.dp) {
            RecTitle("Nothing to read here")
            RecBody(
                {},
                fields = { Quiet("No EPUB, PDF, TXT or MD files in ${ui.folder}. Add some, or choose another folder.") },
                dial = { RecordDial(null, "--", emptyList(), it) },
            )
        }
        ui.current != null -> {
            val b = ui.current
            val st = b.state
            val chs = st.chapters?.takeIf { it > 1 }?.let { n -> (1 until n).map { it / n.toFloat() } } ?: emptyList()
            RecordBox(
                "record ${orders[b.key] ?: "01"}", "now reading", { on.open(b) }, { on.hold(b) },
                "Continue reading", st.page?.let { p -> st.pages?.let { "page $p of $it" } ?: "page $p" },
                stretch = 0.dp, // the dial takes it
            ) {
                RecTitle(b.title)
                Text(b.author ?: "unknown", style = body, modifier = Modifier.padding(top = 7.dp))
                RecBody(
                    file = { RecField("file") { Text(b.fileName, style = recDd(c)) } },
                    fields = {
                        RecField("size") { Text("${formatSize(b.size)} ${b.format.label}", style = recDd(c)) }
                        RecField("opened") { Text(openedLabel(st.openedAt) ?: "never", style = recDd(c)) }
                        RecField("chapter") {
                            Text(
                                if (st.chapterIndex != null && st.chapters != null && st.chapter != null) "${st.chapterIndex} of ${st.chapters}, ${chapterName(st.chapter)}"
                                else st.chapter?.let(::chapterName) ?: "not indexed",
                                style = recDd(c),
                            )
                        }
                        RecField("read") {
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(fontWeight = FontWeight(600))) { append("${percent(b)}%") }
                                    withStyle(SpanStyle(fontWeight = FontWeight(300), color = c.mute)) { append(" at ${bearing(b.progress)}") }
                                },
                                style = recDd(c),
                            )
                        }
                    },
                    dial = { RecordDial(b.progress, "${percent(b)}", chs, it) },
                )
            }
        }
    }
}

private fun recDd(c: app.reader.theme.Colors) = RS.mono(c, 12.5.sp, lh = 18.sp, ls = (-0.02).em)

@Composable
private fun Quiet(text: String, modifier: Modifier = Modifier) {
    val c = colors()
    Text(text, style = RS.mono(c, 12.sp, 300, c.mute, lh = 18.sp, ls = (-0.01).em), modifier = modifier)
}

@Composable
private fun RecTitle(t: String) {
    val c = colors(); val shape = LocalShape.current
    val fs = if (shape.tablet) 30.sp else 25.sp
    Text(disp(t), style = RS.display(c, fs, 1.12f).copy(lineBreak = LineBreak.Heading))
}

@Composable
private fun RecField(k: String, v: @Composable () -> Unit) {
    val c = colors(); val shape = LocalShape.current
    Row {
        Text(
            k, style = RS.label(c).copy(lineHeight = 18.sp), softWrap = false, maxLines = 1, overflow = TextOverflow.Visible,
            modifier = Modifier.width(if (shape.tablet) 64.dp else 50.dp).padding(top = 1.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).padding(top = 1.dp)) { v() }
    }
}

/** The record body under its rule: fields on the left, the scope on the right. */
@Composable
private fun RecBody(file: @Composable () -> Unit, fields: @Composable () -> Unit = {}, dial: @Composable (size: Dp) -> Unit) {
    val c = colors(); val shape = LocalShape.current
    Column(Modifier.padding(top = 14.dp).fillMaxWidth().hairline(c.rule, top = true).padding(top = if (shape.twoColumn) 10.dp else 12.dp)) {
        if (shape.tablet) {
            // the dial beside the fields; filling the first screen (LocalStretch), it moves below
            // them and grows instead, if that makes it markedly bigger. Any height left over
            // stays at the foot, above the Continue bar.
            val stretch = LocalStretch.current
            SubcomposeLayout(Modifier.fillMaxWidth()) { cs ->
                val w = cs.maxWidth
                val base = shape.dial.roundToPx()
                val gap = 10.dp.roundToPx(); val endPad = 2.dp.roundToPx()
                val padT = 8.dp.roundToPx(); val padB = 12.dp.roundToPx()
                val extra = stretch.roundToPx().coerceAtLeast(0)
                val col: @Composable () -> Unit = { Column(verticalArrangement = Arrangement.spacedBy(3.dp)) { file(); fields() } }
                fun fieldsAt(key: String, width: Int) = subcompose(key, col).single().measure(Constraints.fixedWidth(width.coerceAtLeast(0)))
                fun dialAt(key: String, px: Int) = subcompose(key) { dial(px.toDp()) }.single().measure(Constraints())
                val side = fieldsAt("side", w - base - gap - endPad)
                val h0 = maxOf(side.height, base + padT + padB)
                if (extra > 0) {
                    val full = fieldsAt("full", w)
                    val big = minOf((w * 0.8f).toInt(), h0 + extra - full.height - padT - padB)
                    if (big > base * 1.25f) {
                        val d = dialAt("big", big)
                        return@SubcomposeLayout layout(w, h0 + extra) {
                            full.place(0, 0)
                            d.place((w - d.width) / 2, full.height + padT)
                        }
                    }
                }
                val d = dialAt("dial", base)
                layout(w, h0 + extra) {
                    side.place(0, (h0 - side.height) / 2)
                    d.place(w - endPad - d.width, (h0 - d.height - padT - padB) / 2 + padT)
                }
            }
        } else {
            Box(Modifier.padding(bottom = 4.dp)) { file() }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) { fields() }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.padding(end = 2.dp, top = 6.dp, bottom = 12.dp)) { dial(shape.dial) }
            }
        }
    }
}

/** The bracketed record: tag cut into the top rule, the body, and the inverted Continue bar. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordBox(
    tag: String, note: String, onClick: (() -> Unit)?, onLongClick: (() -> Unit)?,
    go: String?, goRight: String?, stretch: Dp = LocalStretch.current, body: @Composable () -> Unit,
) {
    val c = colors(); val shape = LocalShape.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val pad = shape.pad
    Box(Modifier.fillMaxWidth().padding(top = if (shape.twoColumn) 24.dp else 30.dp)) {
        Column(
            Modifier.fillMaxWidth().background(c.paper).border(1.dp, c.rule).brackets(c.ink)
                .then(if (onClick != null) Modifier.combinedClickable(src, null, onLongClick = onLongClick, onClick = onClick) else Modifier)
                .padding(top = 22.dp),
        ) {
            Column(Modifier.padding(horizontal = pad).padding(bottom = if (go == null) pad else 0.dp)) { body() }
            // filling the first screen: the Continue bar keeps to the foot of the box
            if (stretch > 0.dp) Spacer(Modifier.height(stretch))
            if (go != null) {
                val fg = if (pressed) c.ink else c.paper
                Row(
                    Modifier.padding(top = if (shape.twoColumn) 10.dp else 14.dp).fillMaxWidth()
                        .heightIn(min = if (shape.twoColumn) 46.dp else 52.dp)
                        .background(if (pressed) c.paper else c.ink)
                        .then(if (pressed) Modifier.border(1.5.dp, c.ink) else Modifier)
                        .padding(horizontal = pad),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Arrow(fg)
                        Text(go, style = RS.mono(c, 13.5.sp, 500, fg, ls = (-0.02).em))
                    }
                    if (goRight != null) Text(goRight, style = RS.mono(c, 12.sp, 300, fg.copy(alpha = 0.75f)))
                }
            }
        }
        Text(
            buildAnnotatedString {
                append(tag)
                withStyle(SpanStyle(fontWeight = FontWeight(300), color = c.mute)) { append("  $note") }
            },
            style = RS.mono(c, 11.5.sp, lh = 18.sp),
            modifier = Modifier.offset(x = 14.dp, y = (-9).dp).background(c.paper).padding(horizontal = 8.dp),
        )
    }
}

// ---------------------------------------------------------------- the index

private fun LazyListScope.index(ui: LibraryUi, on: LibraryActions, orders: Map<String, String>, top: Dp) {
    if (!ui.loaded || ui.folder == null || ui.all.isEmpty()) return
    item { IndexHead(ui, top) }
    itemsIndexed(ui.others, key = { _, b -> b.key }) { i, b -> IndexRow(b, orders[b.key] ?: "", i > 0, on) }
    item {
        val c = colors()
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Glyph(Glyphs.End, c.mute, 14.dp)
            Text(
                buildAnnotatedString {
                    append("${ui.counts.total} records listed. ")
                    withStyle(SpanStyle(fontWeight = FontWeight(400), color = c.ink)) { append(if (ui.scanning) "Scanning." else "Ready.") }
                },
                style = RS.label(c),
            )
        }
    }
}

@Composable
private fun IndexHead(ui: LibraryUi, top: Dp) {
    val c = colors(); val shape = LocalShape.current
    Column(Modifier.padding(top = if (ui.current == null && !shape.twoColumn) 30.dp else top)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(disp("Index"), style = RS.display(c, 23.sp), modifier = Modifier.weight(1f))
            Text("${ui.others.size} records, by ${ui.sort.name.lowercase()}", style = RS.label(c))
        }
        Columns(Modifier.padding(top = 16.dp).hairline(c.ink, bottom = true).padding(start = 4.dp, end = 4.dp, bottom = 6.dp)) { col ->
            val st = RS.mono(c, 11.sp, 300, c.mute, lh = 16.sp)
            when (col) {
                Col.No -> Text("no.", style = st)
                Col.Title -> Text("title, author", style = st)
                Col.Meta -> Text("size, opened", style = st)
                Col.Progress -> Text("progress", style = st, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private enum class Col { No, Title, Meta, Progress }

/** Header columns: 38 · 1fr · (26+40) on phones; 40 · 1fr · 104 · (26+44) on tablets. */
@Composable
private fun Columns(modifier: Modifier, cell: @Composable (Col) -> Unit) {
    val shape = LocalShape.current
    val gap = if (shape.tablet) 16.dp else 10.dp
    Row(modifier.fillMaxWidth()) {
        Box(Modifier.width(if (shape.tablet) 40.dp else 38.dp)) { cell(Col.No) }
        Spacer(Modifier.width(gap))
        Box(Modifier.weight(1f)) { cell(Col.Title) }
        if (shape.tablet) { Spacer(Modifier.width(gap)); Box(Modifier.width(104.dp)) { cell(Col.Meta) } }
        Spacer(Modifier.width(gap))
        Box(Modifier.width(26.dp + gap + if (shape.tablet) 44.dp else 40.dp)) { cell(Col.Progress) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IndexRow(b: Book, order: String, ruled: Boolean, on: LibraryActions) {
    val c0 = colors(); val shape = LocalShape.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val c = if (pressed) c0.copy(ink = c0.paper, mute = c0.paper, rule = c0.paper) else c0
    val st = stateOf(b)
    val gap = if (shape.tablet) 16.dp else 10.dp
    val quiet = st == DialState.Done
    CompositionLocalProvider(LocalColors provides c) {
        Row(
            Modifier.fillMaxWidth()
                .then(if (ruled) Modifier.hairline(c0.rule, top = true) else Modifier)
                .background(if (pressed) c0.ink else androidx.compose.ui.graphics.Color.Transparent)
                .combinedClickable(src, null, onLongClick = { on.hold(b) }) { on.open(b) }
                .heightIn(min = if (shape.twoColumn) 56.dp else 58.dp)
                .padding(horizontal = 4.dp, vertical = if (shape.twoColumn) 8.dp else 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(order, style = RS.mono(c, 12.sp, color = if (st == DialState.Read) c.ink else c.mute, lh = 17.sp), modifier = Modifier.width(if (shape.tablet) 40.dp else 38.dp))
                    Spacer(Modifier.width(gap))
                    Text(
                        b.title,
                        style = RS.mono(c, 13.5.sp, if (quiet) 300 else 500, if (quiet) c.mute else c.ink, lh = 17.5.sp, ls = (-0.025).em),
                        modifier = Modifier.weight(1f),
                    )
                    if (shape.tablet) {
                        Spacer(Modifier.width(gap))
                        Text(formatSize(b.size), style = RS.mono(c, 11.5.sp, 300, c.ink, lh = 17.sp), modifier = Modifier.width(104.dp))
                    }
                }
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.width(if (shape.tablet) 40.dp else 38.dp)) {
                        Text(
                            b.format.label, style = RS.mono(c, 9.5.sp, color = c.mute, lh = 13.sp, ls = 0.02.em),
                            modifier = Modifier.padding(top = 2.dp).border(1.dp, c.rule).padding(horizontal = 4.dp),
                        )
                    }
                    Spacer(Modifier.width(gap))
                    Text(b.author ?: "unknown", style = RS.label(c).copy(lineHeight = 17.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (shape.tablet) {
                        Spacer(Modifier.width(gap))
                        Text(openedLabel(b.state.openedAt) ?: "never", style = RS.label(c).copy(lineHeight = 17.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(104.dp))
                    }
                }
            }
            Spacer(Modifier.width(gap))
            RowDial(b.progress, st, if (quiet) c.mute else c.ink)
            Spacer(Modifier.width(gap))
            Text(
                when (st) { DialState.Done -> "done"; DialState.New -> "new"; else -> "${percent(b)}%" },
                style = RS.mono(c, 12.sp, color = if (st == DialState.Read) c.ink else c.mute),
                textAlign = TextAlign.End,
                modifier = Modifier.width(if (shape.tablet) 44.dp else 40.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- the summary scope (tablet landscape)

private fun bucket(millis: Long?, now: LocalDate = LocalDate.now()): Int {
    millis ?: return 3
    val d = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    val days = ChronoUnit.DAYS.between(d, now)
    return when { days <= 0 -> 0; days < 7 -> 1; days < 14 -> 2; else -> 3 }
}

/** Every book as a blip: angle is progress, ring is when it was last opened. */
private fun blips(ui: LibraryUi, orders: Map<String, String>, w: Float): List<Blip> {
    val all = ui.all.sortedBy { orders[it.key] }
    val out = mutableListOf<Blip>()
    val groups = List(4) { mutableListOf<Book>() }
    all.filter { stateOf(it) == DialState.Read }.forEach { groups[bucket(it.state.openedAt)] += it }
    groups.forEachIndexed { k, g ->
        val lo = if (k == 0) 0f else BANDS[k - 1]; val hi = BANDS[k]
        g.forEachIndexed { j, b ->
            val t = if (k == 0 && g.size == 1) 0.55f else (j + 1f) / (g.size + 1f)
            out += Blip(b.key, orders[b.key] ?: "", b.progress * 360f, lo + (hi - lo) * t, DialState.Read, b == ui.current)
        }
    }
    val rm = w / 2 - 31
    val hold = (rm + 7) / rm
    val fresh = all.filter { stateOf(it) == DialState.New }
    val done = all.filter { stateOf(it) == DialState.Done }
    // the holding band: unread from the top going clockwise, finished going back; squeeze if crowded
    val step = minOf(13f, 160f / maxOf(1, maxOf(fresh.size, done.size)))
    fresh.forEachIndexed { i, b -> out += Blip(b.key, orders[b.key] ?: "", 14 + i * step, hold, DialState.New, false) }
    done.forEachIndexed { i, b -> out += Blip(b.key, orders[b.key] ?: "", 346 - i * step, hold, DialState.Done, false) }
    return out
}

@Composable
private fun Summary(ui: LibraryUi, on: LibraryActions, orders: Map<String, String>) {
    val c = colors()
    val n = ui.counts
    Column(Modifier.padding(top = 22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(disp("Summary"), style = RS.display(c, 20.sp))
            Box(Modifier.weight(1f).height(1.dp).background(c.rule))
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            SummaryScope(ui, on, orders, 196.dp)
            Column(Modifier.weight(1f).padding(top = 4.dp)) {
                @Composable fun row(k: String, v: Int) {
                    Row(Modifier.padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(k, style = RS.label(c), maxLines = 1, modifier = Modifier.width(76.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("$v", style = RS.mono(c, 12.5.sp, 500), textAlign = TextAlign.End, modifier = Modifier.width(20.dp))
                        Spacer(Modifier.width(10.dp))
                        TickBar(v, n.total, Modifier.weight(1f))
                    }
                }
                row("open", n.open); row("new", n.new); row("finished", n.finished)
                Spacer(Modifier.height(8.dp))
                Format.entries.forEach { f -> n.byFormat[f]?.let { row(f.label, it) } }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("folder size", style = RS.label(c), softWrap = false)
                    Spacer(Modifier.width(8.dp))
                    Text(formatSize(n.bytes), style = RS.mono(c, 12.5.sp, 500))
                }
                Text(
                    "Angle is progress, clockwise from the top. Rings show when you last opened it. Numbers match the index.",
                    style = RS.mono(c, 11.sp, 300, c.mute, lh = 16.sp), modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

/** Counts as bearing ticks: one per file, ink for the counted ones, rule for the rest. */
@Composable
private fun TickBar(v: Int, total: Int, modifier: Modifier) {
    val c = colors()
    Canvas(modifier.height(9.dp)) {
        if (total <= 0) return@Canvas
        val step = minOf(7.dp.toPx(), size.width / total)
        for (i in 0 until total) {
            val x = i * step
            if (i < v) drawRect(c.ink, Offset(x, 0f), androidx.compose.ui.geometry.Size(minOf(1.5.dp.toPx(), step * 0.6f), size.height))
            else drawRect(c.rule, Offset(x, size.height / 2 - 2.5.dp.toPx()), androidx.compose.ui.geometry.Size(1.dp.toPx(), 5.dp.toPx()))
        }
    }
}

@Composable
private fun SummaryScope(ui: LibraryUi, on: LibraryActions, orders: Map<String, String>, size: Dp) {
    val c = colors()
    val w = size.value
    val list = remember(ui.current, ui.others, orders) { blips(ui, orders, w) }
    val byKey = remember(ui.current, ui.others) { ui.all.associateBy { it.key } }
    val tm = rememberTextMeasurer()
    val tag = RS.mono(c, 10.sp, lh = 12.sp, ls = (-0.02).em)
    val pct = RS.display(c, 8.5.sp, color = c.mute, ls = 0f)
    val actions by rememberUpdatedState(on)
    fun hit(p: Offset, density: Float): Book? {
        val q = Offset(p.x / density, p.y / density)
        return list.minByOrNull { (blipAt(it, w) - q).getDistance() }
            ?.takeIf { (blipAt(it, w) - q).getDistance() <= 22f }?.let { byKey[it.key] }
    }
    Canvas(
        Modifier.size(size).pointerInput(list) {
            detectTapGestures(
                onTap = { p -> hit(p, density)?.let { actions.open(it) } },
                onLongPress = { p -> hit(p, density)?.let { actions.hold(it) } },
            )
        },
    ) {
        summaryScope(c, tm, list, ui.current?.progress, w, tag, pct)
    }
}
