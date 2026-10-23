package app.reader.looks.radar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.reader.theme.Colors
import app.reader.theme.Fonts
import app.reader.theme.LocalColors

/**
 * Radar's type: Azeret Mono for everything (tabular, slashed zero), Major Mono Display for
 * display lines only. Major Mono's lowercase are light geometric capitals, so display text
 * is always lowercased before it is set.
 */
object RS {
    private val trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

    fun mono(c: Colors, size: TextUnit = 13.sp, weight: Int = 400, color: Color = c.ink, lh: TextUnit = 20.sp, ls: TextUnit = 0.sp) = TextStyle(
        fontFamily = Fonts.azeretMono,
        fontSize = size,
        lineHeight = lh,
        fontWeight = FontWeight(weight),
        color = color,
        letterSpacing = ls,
        lineHeightStyle = trim,
        // Azeret Mono's fi/fl ligatures break the monospace rhythm
        fontFeatureSettings = "tnum, zero, liga 0, calt 0",
    )

    /** Display face. Pass text through [disp]. */
    fun display(c: Colors, size: TextUnit, lh: Float = 1.05f, color: Color = c.ink, ls: Float = -0.03f) = TextStyle(
        fontFamily = Fonts.majorMono,
        fontSize = size,
        lineHeight = size * lh,
        color = color,
        letterSpacing = ls.em,
        lineHeightStyle = trim,
        fontFeatureSettings = "liga 0, calt 0",
    )

    /** Small light labels (field names, counts). */
    fun label(c: Colors, size: TextUnit = 11.5.sp) = mono(c, size, 300, c.mute, ls = (-0.01).em)
}

fun disp(s: String) = s.lowercase()

@Composable fun colors() = LocalColors.current

/** The four corner brackets of 03's reticle frame. */
fun Modifier.brackets(color: Color, len: Dp = 12.dp, width: Dp = 1.5.dp) = drawBehind { cornerBrackets(color, Offset.Zero, size, len.toPx(), width.toPx()) }

fun DrawScope.cornerBrackets(color: Color, at: Offset, sz: Size, len: Float, w: Float) {
    val (x, y) = at
    val r = x + sz.width; val b = y + sz.height
    drawRect(color, Offset(x, y), Size(len, w)); drawRect(color, Offset(x, y), Size(w, len))
    drawRect(color, Offset(r - len, y), Size(len, w)); drawRect(color, Offset(r - w, y), Size(w, len))
    drawRect(color, Offset(x, b - w), Size(len, w)); drawRect(color, Offset(x, b - len), Size(w, len))
    drawRect(color, Offset(r - len, b - w), Size(len, w)); drawRect(color, Offset(r - w, b - len), Size(w, len))
}

/** A 1px line along the top or bottom edge. */
fun Modifier.hairline(color: Color, top: Boolean = false, bottom: Boolean = false, dashed: Boolean = false) = drawBehind {
    val w = 1.dp.toPx()
    val fx = if (dashed) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())) else null
    if (top) drawLine(color, Offset(0f, w / 2), Offset(size.width, w / 2), w, pathEffect = fx)
    if (bottom) drawLine(color, Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w, pathEffect = fx)
}

/** A dashed 1px border box. */
fun Modifier.dashedBox(color: Color) = drawBehind {
    val w = 1.dp.toPx()
    drawRect(
        color, Offset(w / 2, w / 2), Size(size.width - w, size.height - w),
        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
}

/**
 * The `.blk` / `.btn` button: a 1px ink box, 44dp tall, inverting while pressed.
 * [icon] draws a small glyph before the label in the current text colour.
 */
@Composable
fun Blk(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dashed: Boolean = false,
    padH: Dp = 16.dp,
    size: TextUnit = 13.sp,
    icon: (@Composable (Color) -> Unit)? = null,
) {
    val c = colors()
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val fg = if (pressed) c.paper else c.ink
    Row(
        modifier.heightIn(min = 44.dp)
            .background(if (pressed) c.ink else c.paper)
            .then(if (dashed) Modifier.dashedBox(c.ink) else Modifier.border(1.dp, c.ink))
            .clickable(src, null, onClick = onClick)
            .padding(horizontal = padH),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) icon(fg)
        Text(label, style = RS.mono(c, size, color = fg, lh = 16.sp), maxLines = 1)
    }
}

/** A back button: a chevron, then the label. */
@Composable
fun BackBtn(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Blk(label, onClick, modifier, padH = 14.dp, icon = { col -> Glyph(Glyphs.Back, col, 12.dp) })
}

/** 44dp tap area around a smaller visual. */
@Composable
fun Tap(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.heightIn(min = 44.dp).clickable(remember { MutableInteractionSource() }, null, onClick = onClick), contentAlignment = Alignment.Center) { content() }
}
