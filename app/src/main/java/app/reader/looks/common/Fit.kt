package app.reader.looks.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.reader.looks.LookCard

/**
 * How much taller than its natural height the now-reading record should draw itself, so it
 * fills the first screen (see [FirstScreen]). Each look puts it where it reads best: into its
 * reading chart, or above the Continue bar.
 */
val LocalStretch = compositionLocalOf { 0.dp }

/**
 * The library's left column on tablets in landscape: [first] (the header and the now-reading
 * record) on the first screen, [rest] (the summary) after it. Two rules:
 *
 * - The record is always whole. [first] is drawn at the roomiest of [levels] (0 = as designed,
 *   each tighter) that fits; each look decides what a tighter level gives up.
 * - The summary is never cut by the bottom of the screen. If it fits whole below the record,
 *   it shows there. If not, it starts just past the bottom of the screen, a scroll away, and the
 *   record grows (by [LocalStretch]) to fill the first screen instead of leaving a gap,
 *   stopping [breathing] short of the bottom.
 *
 * [screen] is the height from the column's top to the bottom of the screen, [bottomInset] the
 * part of it under the navigation bar.
 */
@Composable
fun FirstScreen(
    screen: Dp, bottomInset: Dp, breathing: Dp, levels: Int,
    modifier: Modifier = Modifier,
    first: @Composable (level: Int) -> Unit,
    rest: @Composable () -> Unit,
) {
    SubcomposeLayout(modifier) { cs ->
        val loose = cs.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val usable = (screen - bottomInset).roundToPx()
        val budget = usable - breathing.roundToPx()
        fun measureFirst(level: Int, stretch: Int, key: Any) = subcompose(key) {
            CompositionLocalProvider(LocalStretch provides stretch.toDp()) { Column { first(level) } }
        }.single().measure(loose)

        var level = 0
        var p = measureFirst(0, 0, 0)
        while (p.height > budget && level < levels - 1) { level++; p = measureFirst(level, 0, level) }
        val r = subcompose("rest") { Column { rest() } }.single().measure(loose)

        if (p.height + r.height <= usable) {
            layout(cs.maxWidth, p.height + r.height) { p.place(0, 0); r.place(0, p.height) }
        } else {
            val extra = budget - p.height
            val s = if (extra > 0) measureFirst(level, extra, "stretched") else p
            val y = maxOf(screen.roundToPx(), s.height)
            layout(cs.maxWidth, y + r.height) { s.place(0, 0); r.place(0, y) }
        }
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
