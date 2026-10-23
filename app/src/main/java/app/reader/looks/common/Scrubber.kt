package app.reader.looks.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.reader.looks.ChromeActions
import app.reader.looks.ChromeUi
import app.reader.theme.Hx
import app.reader.theme.LocalHaptics
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Turns any element into the page scrubber: touch or drag along it to move through the book.
 * It plays the scrubber's haptics: a grab, a fine tick per page, a firmer tick at chapter
 * starts, and a thud with a magnetic detent at the page you started from.
 *
 * [trackStart]/[trackEnd] inset the active track inside the element (e.g. for end labels).
 * For a radial scrubber, pass [toFraction] mapping a touch point to 0..1 instead.
 */
fun Modifier.scrubber(
    ui: ChromeUi,
    on: ChromeActions,
    trackStart: Dp = 0.dp,
    trackEnd: Dp = 0.dp,
    toFraction: ((x: Float, y: Float, width: Int, height: Int) -> Float)? = null,
): Modifier = composed {
    val haptics = LocalHaptics.current
    val density = LocalDensity.current
    val state by rememberUpdatedState(ui)
    val actions by rememberUpdatedState(on)
    pointerInput(Unit) {
        val startPx = with(density) { trackStart.toPx() }
        val endPx = with(density) { trackEnd.toPx() }
        val detentPx = with(density) { 10.dp.toPx() }
        awaitEachGesture {
            val down = awaitFirstDown()
            val ui0 = state
            if (ui0.pageCount <= 1) return@awaitEachGesture
            val home = ui0.returnPage ?: ui0.page
            var current = ui0.page
            down.consume()
            haptics(Hx.ScrubGrab)
            actions.scrubStart()

            fun pageAt(x: Float, y: Float): Int {
                val s = state
                val w = size.width - startPx - endPx
                val f = toFraction?.invoke(x, y, size.width, size.height)
                    ?: ((x - startPx) / w.coerceAtLeast(1f))
                val homeX = startPx + s.fractionOf(home) * w
                if (toFraction == null && abs(x - homeX) < detentPx) return home
                return (1 + f.coerceIn(0f, 1f) * (s.pageCount - 1)).roundToInt()
            }

            fun moveTo(p: Int) {
                if (p == current) return
                val s = state
                val crossed = chapterOf(s, p) != chapterOf(s, current)
                current = p
                actions.scrubTo(p)
                haptics(if (p == home) Hx.ScrubReturn else if (crossed) Hx.ScrubChapter else Hx.ScrubPage)
            }

            moveTo(pageAt(down.position.x, down.position.y))
            while (true) {
                val ev = awaitPointerEvent()
                val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (!c.pressed) break
                if (c.positionChange() != androidx.compose.ui.geometry.Offset.Zero) {
                    c.consume()
                    moveTo(pageAt(c.position.x, c.position.y))
                }
            }
            haptics(Hx.ScrubRelease)
            actions.scrubEnd()
        }
    }
}

private fun chapterOf(ui: ChromeUi, page: Int) = ui.chapterPages.count { it <= page }

/** "12" or "12–13" for a spread. */
fun ChromeUi.pageLabel() = if (pageLast > page) "$page–$pageLast" else "$page"

@Composable
fun rememberScrubLabel(ui: ChromeUi) = ui.pageLabel()
