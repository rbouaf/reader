package app.reader.looks.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.reader.looks.LookCard

/**
 * Draws [content] at the roomiest level (0 = as designed, each level tighter) whose height is
 * at most [budget], or at the tightest when none fits. On tablets in landscape the library's
 * left column uses it so the now-reading record is always whole on the first screen, whatever
 * the screen height, font size or title length; each look decides what a tighter level gives up
 * (gaps first, then lines of text, then size).
 */
@Composable
fun FitHeight(budget: Dp, levels: Int, modifier: Modifier = Modifier, content: @Composable (level: Int) -> Unit) {
    SubcomposeLayout(modifier) { cs ->
        val max = budget.roundToPx()
        val loose = cs.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        var p = subcompose(0) { Column { content(0) } }.single().measure(loose)
        var level = 1
        while (p.height > max && level < levels) {
            val l = level
            p = subcompose(l) { Column { content(l) } }.single().measure(loose)
            level++
        }
        layout(p.width, p.height) { p.place(0, 0) }
    }
}

/**
 * The look picker's cards in rows of equal width: all on one row when each gets at least
 * [minWidth], otherwise wrapping onto more rows, so every look is visible without scrolling.
 */
@Composable
fun CardRows(
    cards: List<LookCard>, gap: Dp, modifier: Modifier = Modifier, minWidth: Dp = 100.dp,
    card: @Composable (LookCard, Modifier) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val perRow = ((maxWidth + gap) / (minWidth + gap)).toInt().coerceIn(1, cards.size.coerceAtLeast(1))
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            cards.chunked(perRow).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { card(it, Modifier.weight(1f)) }
                    repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
