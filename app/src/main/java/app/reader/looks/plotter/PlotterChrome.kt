package app.reader.looks.plotter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
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
import kotlin.math.round

@Composable
fun PlotterChrome(ui: ChromeUi, on: ChromeActions) {
    val c = colors()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val tablet = maxWidth >= 700.dp
        val wideInfo = maxWidth >= 600.dp
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val ld = LocalLayoutDirection.current
        val sx = insets.calculateStartPadding(ld); val ex = insets.calculateEndPadding(ld)

        // top: back, then the title
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().background(c.paper).edges(c.ink, bottom = HV)
                .padding(top = insets.calculateTopPadding() + 10.dp, bottom = 10.dp + HV, start = 14.dp + sx, end = 14.dp + ex),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PenButton("Library", on.back, back = true)
            Text(
                ui.title, style = PT.serif(21.sp, c.ink, italic = true, lh = 23.sp), textAlign = TextAlign.End,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
        }

        // bottom: tools, position, the drafting scale
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(c.paper).edges(c.ink, top = HV)
                .padding(top = 12.dp + HV, bottom = 12.dp + insets.calculateBottomPadding(), start = 16.dp + sx, end = 16.dp + ex),
        ) {
            Tools(ui, on)
            Info(ui, on, wide = wideInfo)
            DraftingScale(ui, on, Modifier.padding(top = 10.dp))
        }

        if (ui.scrubbing) {
            val fs = if (tablet) 150.sp else (maxWidth.value * 0.22f).coerceIn(76f, 150f).sp
            val fdp = with(LocalDensity.current) { fs.toDp() }
            Box(Modifier.align(Alignment.Center)) {
                Box(
                    Modifier
                        .drawBehind {
                            val hl = pen(HL)
                            val g = round(5.dp.toPx()) + hl
                            drawRect(c.paper, Offset(-g, -g), Size(size.width + 2 * g, size.height + 2 * g))
                            drawRect(c.ink, Offset(-g, -g), Size(size.width + 2 * g, size.height + 2 * g), style = Stroke(hl))
                        }
                        .background(c.paper).penBox(c.ink, HV)
                        .widthIn(min = fdp * 2.6f)
                        .padding(start = fdp * 0.5f, end = fdp * 0.5f, top = fdp * 0.3f, bottom = fdp * 0.25f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(ui.pageLabel(), style = PT.serif(fs, c.ink, lh = fs * 0.9f), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tools(ui: ChromeUi, on: ChromeActions) {
    FlowRow(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PenButton("Contents", on.contents, size = 12.5.sp, padX = 12.dp)
        if (ui.canSearch) PenButton("Search", on.search, size = 12.5.sp, padX = 12.dp)
        if (ui.canReadAloud) PenButton(if (ui.tts == TtsState.Off) "Read aloud" else "Stop reading", on.readAloud, size = 12.5.sp, padX = 12.dp)
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
        Text(
            ui.chapter ?: ui.title, style = PT.serif(18.sp, c.mute, italic = true, lh = 20.7.sp),
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        val ret = ui.returnPage?.takeIf { it != ui.page }
        if (ret != null && wide) {
            PenButton("Back to page $ret", on.goBackToMark, size = 12.5.sp, dashed = true, boxHeight = 36.dp, padX = 10.dp)
        }
        Text(
            buildAnnotatedString {
                withStyle(PT.serif(26.sp, c.ink).toSpanStyle()) { append(ui.pageLabel()) }
                append(" of ${ui.pageCount}")
            },
            style = PT.mono(12.sp, c.mute), maxLines = 1,
        )
    }
    // on phones the return button wraps onto its own line, as in the mockup's flex row
    val ret = ui.returnPage?.takeIf { it != ui.page }
    if (ret != null && !wide) {
        PenButton("Back to page $ret", on.goBackToMark, size = 12.5.sp, dashed = true, boxHeight = 36.dp, padX = 10.dp)
    }
}

/**
 * The scrubber as a drafting scale: graduations every 2% and 10%, a heavy pen line for what's
 * read, dashed leaders with arrowheads at chapter starts, a diamond where you were, and a
 * target for where you are.
 */
@Composable
private fun DraftingScale(ui: ChromeUi, on: ChromeActions, modifier: Modifier) {
    val c = colors()
    val tm = rememberTextMeasurer()
    Box(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(58.dp + 14.dp).scrubber(ui, on)) {
            val hl = pen(HL); val hv = pen(HV)
            val w = size.width
            val h = 58.dp.toPx()
            val base = round(0.7f * h)
            fun x(f: Float) = f.coerceIn(0f, 1f) * (w - hl) + hl / 2

            // baseline and graduations
            hLine(c.ink, 0f, w, base, hl)
            for (i in 0..50) {
                val xx = x(i / 50f)
                if (i % 5 == 0) vLine(c.ink, xx, h - 22.dp.toPx(), h, hl)
                else vLine(c.ink, xx, base - 0.7f * 8.dp.toPx(), base + 0.3f * 8.dp.toPx(), hl)
            }
            // what's read: a heavy bar sitting on the baseline
            val p = ui.progress
            drawRect(c.ink, Offset(0f, base - hl / 2 - 2 * hv), Size(round(x(p)), 2 * hv))

            // chapter starts: dashed leaders down from an arrowhead
            for (cp in ui.chapterPages) {
                if (cp <= 1) continue
                val xx = x(ui.fractionOf(cp))
                vDash(c.ink, xx, 0f, base, hl, 3.dp.toPx(), 3.dp.toPx())
                val tri = Path().apply {
                    moveTo(xx - 4.dp.toPx(), 0f); lineTo(xx + 4.dp.toPx(), 0f); lineTo(xx, 6.dp.toPx()); close()
                }
                drawPath(tri, c.ink)
            }

            // where you were: a diamond on the baseline
            ui.returnPage?.takeIf { it != ui.page }?.let { r ->
                val xx = x(ui.fractionOf(r))
                val d = 7.dp.toPx()
                val dia = Path().apply { moveTo(xx, base - d); lineTo(xx + d, base); lineTo(xx, base + d); lineTo(xx - d, base); close() }
                drawPath(dia, c.paper)
                drawPath(dia, c.ink, style = Stroke(hl))
            }

            // where you are: a heavy line through a target
            val tx = x(p)
            vLine(c.ink, tx, -4.dp.toPx(), h + 4.dp.toPx(), hv)
            val rr = (if (ui.scrubbing) 12.dp else 9.dp).toPx()
            drawCircle(c.paper, rr, Offset(tx, base))
            drawCircle(c.ink, rr - hv / 2, Offset(tx, base), style = Stroke(hv))
            hLine(c.ink, tx - rr + hv, tx + rr - hv, base, hl)

            // 0 and 100 under the ends
            val st = PT.mono(9.5.sp, c.mute)
            val z = tm.measure("0", st); val hd = tm.measure("100", st)
            drawText(z, topLeft = Offset(0f, h + 14.dp.toPx() - z.size.height))
            drawText(hd, topLeft = Offset(w - hd.size.width, h + 14.dp.toPx() - hd.size.height))
        }
    }
}
