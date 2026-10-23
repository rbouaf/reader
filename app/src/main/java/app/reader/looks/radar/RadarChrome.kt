package app.reader.looks.radar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.reader.looks.ChromeActions
import app.reader.looks.ChromeUi
import app.reader.looks.TtsState
import app.reader.looks.common.pageLabel
import app.reader.looks.common.scrubber

/**
 * Book chrome: a top bar (back, title) and a bottom bar with the tools, then a round position
 * dial beside a linear range-scale scrubber. The dial is a display: at 78dp a radial drag would
 * be too coarse for a 900-page book, so the long linear scale does the scrubbing.
 */
@Composable
fun RadarChrome(ui: ChromeUi, on: ChromeActions) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val c = colors()
        val tablet = maxWidth >= 700.dp
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val side = if (tablet) 20.dp else 14.dp

        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().background(c.paper).hairline(c.ink, bottom = true)
                .padding(top = insets.calculateTopPadding() + 10.dp, bottom = 10.dp, start = side, end = side),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BackBtn("Library", on.back)
            Text(
                ui.title, style = RS.mono(c, 12.sp, 500, lh = 17.sp, ls = (-0.02).em), textAlign = TextAlign.End,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(c.paper).hairline(c.ink, top = true)
                .padding(start = if (tablet) 20.dp else 16.dp, end = if (tablet) 20.dp else 16.dp, top = 10.dp, bottom = 12.dp + insets.calculateBottomPadding()),
        ) {
            Tools(ui, on)
            val chs = ui.chapterPages.filter { it > 1 }.map { ui.fractionOf(it) }
            if (tablet) {
                // the mockup's grid: the dial spans the info line and the scale
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    PolarDial(ui.progress, chs)
                    Column(Modifier.weight(1f)) {
                        Info(ui, on)
                        ScaleScrubber(ui, on, chs, Modifier.padding(top = 4.dp))
                    }
                }
            } else {
                // phones: the chapter gets its own line; the dial spans page count + return and the scale
                Chapter(ui, Modifier.padding(top = 8.dp))
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    PolarDial(ui.progress, chs)
                    Column(Modifier.weight(1f)) {
                        Info(ui, on, chapter = false)
                        ScaleScrubber(ui, on, chs, Modifier.padding(top = 4.dp))
                    }
                }
            }
        }

        if (ui.scrubbing) BigPage(ui, maxWidth.value,Modifier.align(Alignment.Center))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tools(ui: ChromeUi, on: ChromeActions) {
    FlowRow(
        Modifier.fillMaxWidth().padding(bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Blk("Contents", on.contents, padH = 12.dp, icon = { Glyph(Glyphs.Contents, it, 14.dp) })
        if (ui.canSearch) Blk("Search", on.search, padH = 12.dp, icon = { Glyph(Glyphs.Search, it, 14.dp) })
        if (ui.canReadAloud) Blk(
            if (ui.tts == TtsState.Off) "Read aloud" else "Stop reading", on.readAloud, padH = 12.dp,
            icon = { Glyph(if (ui.tts == TtsState.Off) Glyphs.Aloud else Glyphs.Stop, it, 14.dp) },
        )
    }
}

@Composable
private fun Chapter(ui: ChromeUi, modifier: Modifier = Modifier) {
    val c = colors()
    Text(
        ui.chapter ?: ui.title, style = RS.mono(c, 12.5.sp, 300, c.mute, lh = 18.sp, ls = (-0.015).em),
        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier,
    )
}

/** One line, never wrapping: the chapter (ellipsized, unless it has its own line), page x of y, the return button. */
@Composable
private fun Info(ui: ChromeUi, on: ChromeActions, chapter: Boolean = true) {
    val c = colors()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (chapter) Chapter(ui, Modifier.weight(1f))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontFamily = app.reader.theme.Fonts.majorMono, fontSize = 18.sp, color = c.ink, letterSpacing = (-0.04).em, fontWeight = FontWeight.Normal)) {
                    append(ui.pageLabel())
                }
                append(" of ${ui.pageCount}")
            },
            style = RS.mono(c, 12.sp, 300, c.mute, lh = 22.sp), maxLines = 1,
        )
        ui.returnPage?.takeIf { it != ui.page }?.let { r ->
            if (chapter) ReturnBtn("Back to page $r", on.goBackToMark)
            else BoxWithConstraints(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                // narrow phones: drop to "Page N" rather than clip the label
                val style = RS.mono(c, 12.sp, lh = 16.sp)
                val full = "Back to page $r"
                val needed = with(LocalDensity.current) { rememberTextMeasurer().measure(full, style).size.width.toDp() } + 22.dp
                ReturnBtn(if (needed <= maxWidth) full else "Page $r", on.goBackToMark)
            }
        }
    }
}

/** The mockup's .ret: a 36dp dashed box inside a 44dp touch target. */
@Composable
private fun ReturnBtn(label: String, onClick: () -> Unit) {
    val c = colors()
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    Box(Modifier.height(44.dp).clickable(src, null, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(
            Modifier.height(36.dp).background(if (pressed) c.ink else c.paper).dashedBox(c.ink).padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) { Text(label, style = RS.mono(c, 12.sp, color = if (pressed) c.paper else c.ink, lh = 16.sp), maxLines = 1, softWrap = false) }
    }
}

/**
 * The scrubber as a range scale: graduations top and bottom, a dotted rail with the read part
 * filled, chapter bearings as dashed lines with a marker, the return mark, and a target thumb.
 */
@Composable
private fun ScaleScrubber(ui: ChromeUi, on: ChromeActions, chs: List<Float>, modifier: Modifier) {
    val c = colors()
    Box(modifier.fillMaxWidth().height(46.dp).scrubber(ui, on)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val px = 1.dp.toPx()
            drawRect(c.rule, Offset(px / 2, px / 2), Size(w - px, h - px), style = Stroke(px))
            // graduations: every 2% top and bottom (5dp), every 10% bottom (9dp)
            for (i in 0..50) {
                val x = (i / 50f) * (w - px) + px / 2
                drawLine(c.rule, Offset(x, 0f), Offset(x, 5.dp.toPx()), px)
                drawLine(c.rule, Offset(x, h - 5.dp.toPx()), Offset(x, h), px)
                if (i % 5 == 0) drawLine(c.mute, Offset(x, h - 9.dp.toPx()), Offset(x, h), px)
            }
            val cy = h / 2
            drawLine(c.mute, Offset(0f, cy), Offset(w, cy), px, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())))
            val tx = ui.progress * w
            drawRect(c.paper, Offset(0f, cy - 1.dp.toPx()), Size(tx, 2.dp.toPx()))
            drawRect(c.ink, Offset(0f, cy - 0.75.dp.toPx()), Size(tx, 1.5.dp.toPx()))
            chs.forEach { f ->
                val x = f * w
                drawLine(c.mute, Offset(x, 0f), Offset(x, h), px, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
                drawPath(Path().apply {
                    moveTo(x - 4.5.dp.toPx(), 0f); lineTo(x + 4.5.dp.toPx(), 0f); lineTo(x, 6.dp.toPx()); close()
                }, c.ink)
            }
            ui.returnPage?.takeIf { it != ui.page }?.let { r ->
                val x = ui.fractionOf(r) * w
                drawCircle(c.paper, 5.dp.toPx(), Offset(x, cy))
                drawCircle(c.ink, 4.25.dp.toPx(), Offset(x, cy), style = Stroke(1.5.dp.toPx()))
            }
            // the thumb: a full-height hairline, a target, and a reticle (larger while scrubbing)
            drawLine(c.ink, Offset(tx, -px), Offset(tx, h + px), 1.5.dp.toPx())
            val t = Offset(tx, cy)
            drawCircle(c.paper, 11.dp.toPx(), t)
            drawCircle(c.ink, 4.dp.toPx(), t)
            drawCircle(c.ink, 8.dp.toPx(), t, style = Stroke(1.dp.toPx()))
            val q = (if (ui.scrubbing) 19 else 15).dp.toPx()
            reticle(c.ink, tx, cy, q, 6.dp.toPx(), 1.3.dp.toPx())
        }
    }
}

/** The page number while scrubbing, on a round scope face with bearing ticks. */
@Composable
private fun BigPage(ui: ChromeUi, widthDp: Float, modifier: Modifier) {
    val c = colors()
    val fs = (widthDp * 0.11f).coerceIn(40f, 80f)
    val label = ui.pageLabel()
    val em = with(LocalDensity.current) { fs.sp.toPx() }
    // min-width 3.2em, square; long spreads grow it
    val d = maxOf(3.2f, 0.62f * label.length + 0.9f) * fs
    Box(
        modifier.size(d.dp).drawBehind { scopeFace(c, em) },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = RS.display(c, fs.sp, 1f, ls = -0.06f))
    }
}
