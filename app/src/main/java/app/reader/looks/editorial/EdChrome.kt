package app.reader.looks.editorial

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.reader.looks.ChromeActions
import app.reader.looks.ChromeUi
import app.reader.looks.TtsState
import app.reader.looks.common.pageLabel
import app.reader.looks.common.scrubber
import app.reader.theme.Fonts

@Composable
fun EditorialChrome(ui: ChromeUi, on: ChromeActions) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val c = colors()
        val tablet = maxWidth >= 700.dp
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val bigSize = (maxWidth.value * 0.18f).coerceIn(64f, 128f).sp

        // top: back, (tablet: the tools), the title in italic
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().background(c.paper).edges(bottom = c.ink)
                .padding(top = insets.calculateTopPadding() + 10.dp, bottom = 10.dp, start = 14.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            EdButton("Library", on.back, back = true)
            if (tablet) Tools(ui, on)
            Text(
                ui.title, style = ET.it(c, 19.sp, 1.2f, c.ink), textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        // bottom: (phone: the tools), chapter and page, the strip chart
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(c.paper).edges(top = c.ink)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp + insets.calculateBottomPadding()),
        ) {
            if (!tablet) Tools(ui, on, Modifier.padding(bottom = 10.dp))
            Info(ui, on, tablet)
            ScrubStrip(ui, on, Modifier.padding(top = 10.dp))
        }

        if (ui.scrubbing) {
            Box(Modifier.align(Alignment.Center)) {
                Text(
                    ui.pageLabel(),
                    style = ET.disp(c, bigSize, 1f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.background(c.paper).edges(top = c.rule, right = c.rule, bottom = c.rule, left = c.ink, leftW = 2.dp)
                        .widthIn(min = (bigSize.value * 2.6f).dp)
                        .padding(start = (bigSize.value * 0.5f).dp, end = (bigSize.value * 0.5f).dp, top = (bigSize.value * 0.3f).dp, bottom = (bigSize.value * 0.25f).dp),
                )
            }
        }
    }
}

@Composable
private fun Tools(ui: ChromeUi, on: ChromeActions, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EdButton("Contents", on.contents)
        if (ui.canSearch) EdButton("Search", on.search)
        if (ui.canReadAloud) EdButton(if (ui.tts == TtsState.Off) "Read aloud" else "Stop reading", on.readAloud)
    }
}

@Composable
private fun Info(ui: ChromeUi, on: ChromeActions, wide: Boolean) {
    val c = colors()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(ui.chapter ?: ui.title, style = ET.mono(c, 12.sp, 1.2f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontFamily = Fonts.bodoniText, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = c.ink)) { append(ui.pageLabel()) }
                append("  of ${ui.pageCount}")
            },
            style = ET.mono(c, 11.5.sp, 1f, c.mute), maxLines = 1, softWrap = false,
        )
        if (wide) ui.returnPage?.takeIf { it != ui.page }?.let { r -> EdButton("Back to page $r", on.goBackToMark, dashed = true, size = 12.sp) }
    }
    // on phones the return button wraps under the chapter line, as the mockup's flex row does
    if (!wide) ui.returnPage?.takeIf { it != ui.page }?.let { r ->
        EdButton("Back to page $r", on.goBackToMark, Modifier.padding(top = 6.dp), dashed = true, size = 12.sp)
    }
}

/**
 * 04's mini strip chart of this book: x is the page, y how much of the book lies behind it.
 * Grid paper, the diagonal rail, chapter starts as dashed verticals with an ink foot, the
 * stepped trace up to the thumb (see [scrubSteps]), a triangle at the page you came from,
 * and the thumb as a full-height pen line with its square head on the trace.
 */
@Composable
private fun ScrubStrip(ui: ChromeUi, on: ChromeActions, modifier: Modifier) {
    val c = colors()
    val steps = remember(ui.activity, ui.returnPage ?: ui.page, ui.pageCount) { scrubSteps(ui) }
    Canvas(modifier.fillMaxWidth().height(68.dp).scrubber(ui, on)) {
        val w = size.width; val h = size.height
        val hair = 1.dp.toPx().coerceAtLeast(1f)
        fun y(v: Float) = h * (102f - v * 100f) / 104f
        // grid paper
        for (k in 1..3) drawRect(c.grid, Offset(0f, h * k / 4f), Size(w, hair))
        for (k in 0 until 20) drawRect(c.grid, Offset(w * k / 20f, 0f), Size(hair, h))
        // the rail: the whole book on the diagonal
        drawLine(c.rule, Offset(0f, h), Offset(w, 0f), hair)
        // chapter starts
        ui.chapterPages.filter { it > 1 }.forEach { p ->
            val x = ui.fractionOf(p) * w
            dashedV(c.mute, x, 0f, h, on = 2.dp, off = 3.dp)
            drawRect(c.ink, Offset(x - 3.dp.toPx(), h - 0.5.dp.toPx()), Size(7.dp.toPx(), 1.5.dp.toPx()))
        }
        // the trace, up to the thumb
        val tx = ui.progress * w
        clipRect(right = tx.coerceAtLeast(0.5f)) {
            val path = Path().apply { steps.forEachIndexed { i, p -> if (i == 0) moveTo(p.x * w, y(p.y)) else lineTo(p.x * w, y(p.y)) } }
            drawPath(path, c.ink, style = Stroke(1.6.dp.toPx(), join = StrokeJoin.Round))
        }
        // frame: rule top and right, ink left and bottom
        drawRect(c.rule, Offset(0f, 0f), Size(w, hair))
        drawRect(c.rule, Offset(w - hair, 0f), Size(hair, h))
        drawRect(c.ink, Offset(0f, 0f), Size(hair, h))
        drawRect(c.ink, Offset(0f, h - hair), Size(w, hair))
        // where you came from
        ui.returnPage?.takeIf { it != ui.page }?.let { r ->
            val x = ui.fractionOf(r) * w
            val tri = Path().apply {
                moveTo(x - 6.dp.toPx(), h + 1.dp.toPx()); lineTo(x + 6.dp.toPx(), h + 1.dp.toPx()); lineTo(x, h + 1.dp.toPx() - 9.dp.toPx()); close()
            }
            drawPath(tri, c.ink)
        }
        // the thumb
        val lw = 1.5.dp.toPx()
        drawRect(c.ink, Offset(tx - lw / 2, -hair), Size(lw, h + 2 * hair))
        val s = (if (ui.scrubbing) 17.dp else 11.dp).toPx()
        val cy = y(ui.progress)
        drawRect(c.paper, Offset(tx - s / 2, cy - s / 2), Size(s, s))
        drawRect(c.ink, Offset(tx - s / 2 + lw / 2, cy - s / 2 + lw / 2), Size(s - lw, s - lw), style = Stroke(lw))
    }
}
