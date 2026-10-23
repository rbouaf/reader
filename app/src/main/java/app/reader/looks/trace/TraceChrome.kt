package app.reader.looks.trace

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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
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

@Composable
fun TraceChrome(ui: ChromeUi, on: ChromeActions) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val g = Grid.of(maxWidth)
        val tablet = maxWidth >= 700.dp
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        CompositionLocalProvider(LocalGrid provides g) {
            val c = colors()
            val side = if (tablet) g.ch(4) else g.ch(2)

            // top: back, then the title
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().background(c.paper).doubleRule(c.ink, bottom = true)
                    .padding(top = insets.calculateTopPadding() + 4.dp, bottom = 4.dp, start = side, end = side),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(g.ch(2)),
            ) {
                TextButton("Library", on.back, pre = "< ")
                Text(
                    ui.title, style = RT.label(g, c), textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            // bottom: tools, position, scrubber
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(c.paper).doubleRule(c.ink, top = true)
                    .padding(start = side, end = side, top = g.lines(0.5f), bottom = g.lines(0.5f) + insets.calculateBottomPadding()),
            ) {
                Tools(ui, on)
                Info(ui, on)
                StripScrubber(ui, on)
            }

            if (ui.scrubbing) {
                Box(Modifier.align(Alignment.Center)) {
                    Text(
                        ui.pageLabel(),
                        style = RT.base(g, c).copy(fontWeight = FontWeight.Bold, fontSize = if (tablet) 96.sp else 56.sp, lineHeight = if (tablet) 106.sp else 62.sp),
                        modifier = Modifier.background(c.paper).ruledBox(c.ink, double = true).padding(horizontal = g.ch(3), vertical = g.lines(1)),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tools(ui: ChromeUi, on: ChromeActions) {
    val g = grid()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(g.ch(2))) {
        TextButton("Contents", on.contents)
        if (ui.canSearch) TextButton("Search", on.search)
        if (ui.canReadAloud) TextButton(if (ui.tts == TtsState.Off) "Read aloud" else "Stop reading", on.readAloud)
    }
}

@Composable
private fun Info(ui: ChromeUi, on: ChromeActions) {
    val g = grid(); val c = colors()
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(g.ch(2))) {
        Text(ui.chapter ?: ui.title, style = RT.title(g, c), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = c.ink, fontStyle = FontStyle.Normal, fontWeight = FontWeight.Bold)) { append(ui.pageLabel()) }
                append(" of ${ui.pageCount}")
            },
            style = RT.label(g, c), maxLines = 1,
        )
        ui.returnPage?.takeIf { it != ui.page }?.let { r -> TextButton("Back to page $r", on.goBackToMark) }
    }
}
