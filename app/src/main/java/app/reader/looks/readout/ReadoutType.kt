package app.reader.looks.readout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.reader.theme.Colors
import app.reader.theme.Fonts
import app.reader.theme.LocalColors

/**
 * Readout sets everything on a character grid: one family (Victor Mono), every vertical step
 * one line, every horizontal step one cell (0.6em, Victor Mono's advance). Hierarchy comes only
 * from weight (200 / 300 / 400 / 700), the cursive italic for labels, and double height.
 */
@Immutable
data class Grid(val fs: TextUnit, val lh: TextUnit, val chDp: Dp, val lhDp: Dp) {
    fun ch(n: Float) = chDp * n
    fun ch(n: Int) = chDp * n
    fun lines(n: Float) = lhDp * n
    fun lines(n: Int) = lhDp * n

    companion object {
        /** 14/21 on phones, 15/23 from 700dp wide (as the mockup's media query). */
        @Composable
        fun of(widthDp: Dp): Grid {
            val big = widthDp >= 700.dp
            val fs = if (big) 15.sp else 14.sp
            val lh = if (big) 23.sp else 21.sp
            val d = LocalDensity.current
            return with(d) { Grid(fs, lh, (fs * 0.6f).toDp(), lh.toDp()) }
        }
    }
}

val LocalGrid = staticCompositionLocalOf { Grid(14.sp, 21.sp, 8.4.dp, 21.dp) }

object RT {
    private val trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

    fun base(g: Grid, c: Colors) = TextStyle(
        fontFamily = Fonts.victorMono,
        fontSize = g.fs,
        lineHeight = g.lh,
        color = c.ink,
        fontWeight = FontWeight.Normal,
        lineHeightStyle = trim,
        fontFeatureSettings = "calt 0, liga 0",
    )

    /** Field labels and column heads: the cursive italic. */
    fun label(g: Grid, c: Colors) = base(g, c).copy(fontStyle = FontStyle.Italic, color = c.mute)
    fun title(g: Grid, c: Colors) = base(g, c).copy(fontWeight = FontWeight.Bold)
    fun dim(g: Grid, c: Colors) = base(g, c).copy(fontWeight = FontWeight.Light, color = c.mute)

    /** Double height, double-struck: Victor Mono tops out at 700, which goes pale at this size. */
    fun double(g: Grid, c: Colors) = base(g, c).copy(
        fontSize = g.fs * 2, lineHeight = g.lh * 2, fontWeight = FontWeight.Bold, letterSpacing = 0.02.em,
        shadow = Shadow(c.ink, Offset(g.fs.value * 0.07f, 0f), 0f),
    )
}

@Composable fun grid() = LocalGrid.current
@Composable fun colors() = LocalColors.current

/** A text-drawn progress bar: dots for unread, slashes for read, filling whole cells only. */
@Composable
fun TextBar(p: Float, modifier: Modifier = Modifier) {
    val g = grid(); val c = colors()
    BoxWithConstraints(modifier) {
        val n = (maxWidth / g.chDp).toInt().coerceAtLeast(1)
        val filled = Math.round(p.coerceIn(0f, 1f) * n)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = c.ink)) { append("/".repeat(filled)) }
                withStyle(SpanStyle(color = c.rule)) { append(".".repeat(n - filled)) }
            },
            style = RT.base(g, c), maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
        )
    }
}

/** A rule of repeated characters: `-----` or `=====`. */
@Composable
fun TextRule(modifier: Modifier = Modifier, ch: Char = '-', color: Color = colors().rule) {
    val g = grid()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val n = (maxWidth / g.chDp).toInt().coerceAtLeast(1)
        Text(ch.toString().repeat(n), style = RT.base(g, colors()).copy(color = color), maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
    }
}

/** The blinking block cursor: the only motion on Readout's screens. */
@Composable
fun BlockCursor(modifier: Modifier = Modifier) {
    val g = grid(); val c = colors()
    val t = rememberInfiniteTransition(label = "cursor")
    val a by t.animateFloat(
        1f, 1f,
        infiniteRepeatable(keyframes { durationMillis = 1100; 1f at 0; 0f at 550 }, RepeatMode.Restart, StartOffset(0)),
        label = "blink",
    )
    Box(modifier.size(g.chDp, with(LocalDensity.current) { (g.fs * 1.15f).toDp() }).alpha(a).background(c.ink))
}

/** The 3px double rule ( ═ ) along the top or bottom of an element. */
fun Modifier.doubleRule(color: Color, top: Boolean = false, bottom: Boolean = false) = drawBehind {
    val w = 1.dp.toPx().coerceAtLeast(1f)
    if (top) { drawRect(color, Offset(0f, 0f), androidx.compose.ui.geometry.Size(size.width, w)); drawRect(color, Offset(0f, w * 2), androidx.compose.ui.geometry.Size(size.width, w)) }
    if (bottom) { drawRect(color, Offset(0f, size.height - w), androidx.compose.ui.geometry.Size(size.width, w)); drawRect(color, Offset(0f, size.height - w * 3), androidx.compose.ui.geometry.Size(size.width, w)) }
}

/** A box drawn with a 3px double border ( ╔═╗ ) or a 1px single one. */
fun Modifier.ruledBox(color: Color, double: Boolean) = drawBehind {
    val w = 1.dp.toPx().coerceAtLeast(1f)
    fun rect(inset: Float) {
        drawRect(color, Offset(inset, inset), androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(w))
    }
    rect(w / 2)
    if (double) rect(w * 2.5f)
}

/** `[ Label ]` text with the brackets in the mute colour. */
fun bracketed(label: String, c: Colors, pre: String = ""): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(color = c.mute)) { append("[ $pre") }
    append(label)
    withStyle(SpanStyle(color = c.mute)) { append(" ]") }
}
