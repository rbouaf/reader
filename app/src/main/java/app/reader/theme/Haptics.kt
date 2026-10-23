package app.reader.theme

import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants as H
import android.view.View
import androidx.compose.runtime.staticCompositionLocalOf

enum class HapticLevel { Off, Quiet, Full }

/**
 * The haptic grammar from the mockups (mockups/v8/core.js). Haptics mark *structure*
 * (chapter breaks, the first and last page, detents, the place you came from), never mere touch.
 * Ordinary page turns are silent unless the level is Full or the page tick is on.
 */
enum class Hx(val full: Boolean = false, val throttleMs: Long = 0) {
    PageTurn(full = true),
    SwipeCommit(full = true),
    SwipeCancel(full = true),
    Chapter,
    Edge(throttleMs = 400),
    BookEnd,
    ScrubGrab,
    ScrubPage(throttleMs = 28),
    ScrubChapter,
    ScrubReturn,
    ScrubRelease(full = true),
    Detent(throttleMs = 18),
    DetentHome,
    Select,
    ToggleOn,
    ToggleOff,
    LongPress,
    Confirm,
}

/**
 * Plays [Hx] events. System haptic constants come first: the OEM tunes those per actuator,
 * which matters on the Tab S9's weaker motor. Composition primitives (thud, low tick,
 * slow rise) are used only where the device supports them, otherwise the nearest constant.
 */
class Haptics(
    private val view: View?,
    private val vibrator: Vibrator?,
    private val level: () -> HapticLevel,
    private val pageTick: () -> Boolean,
) {
    private val last = LongArray(Hx.entries.size)

    private val primitives: Boolean = vibrator?.let {
        it.hasVibrator() && it.areAllPrimitivesSupported(
            VibrationEffect.Composition.PRIMITIVE_THUD,
            VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
            VibrationEffect.Composition.PRIMITIVE_TICK,
            VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
            VibrationEffect.Composition.PRIMITIVE_CLICK,
        )
    } ?: false

    operator fun invoke(e: Hx) {
        val lvl = level()
        if (lvl == HapticLevel.Off) return
        if (e.full && lvl != HapticLevel.Full && !(e == Hx.PageTurn && pageTick())) return
        val now = SystemClock.uptimeMillis()
        if (e.throttleMs > 0 && now - last[e.ordinal] < e.throttleMs) return
        last[e.ordinal] = now
        play(e)
    }

    private fun play(e: Hx) {
        when (e) {
            Hx.PageTurn -> compose(VibrationEffect.Composition.PRIMITIVE_LOW_TICK to 0.3f) ?: constant(H.SEGMENT_FREQUENT_TICK)
            Hx.SwipeCommit -> constant(H.GESTURE_THRESHOLD_ACTIVATE)
            Hx.SwipeCancel -> constant(H.GESTURE_THRESHOLD_DEACTIVATE)
            Hx.Chapter -> compose(VibrationEffect.Composition.PRIMITIVE_TICK to 0.7f) ?: constant(H.SEGMENT_TICK)
            Hx.Edge -> compose(VibrationEffect.Composition.PRIMITIVE_THUD to 0.4f) ?: constant(H.CONTEXT_CLICK)
            Hx.BookEnd -> compose(
                VibrationEffect.Composition.PRIMITIVE_SLOW_RISE to 0.5f,
                VibrationEffect.Composition.PRIMITIVE_CLICK to 0.8f,
            ) ?: constant(H.CONFIRM)
            Hx.ScrubGrab -> constant(H.GESTURE_START)
            Hx.ScrubPage -> constant(H.SEGMENT_FREQUENT_TICK)
            Hx.ScrubChapter -> constant(H.SEGMENT_TICK)
            Hx.ScrubReturn -> compose(VibrationEffect.Composition.PRIMITIVE_THUD to 0.8f) ?: constant(H.LONG_PRESS)
            Hx.ScrubRelease -> constant(H.GESTURE_END)
            Hx.Detent -> constant(H.SEGMENT_FREQUENT_TICK)
            Hx.DetentHome -> constant(H.CLOCK_TICK)
            Hx.Select -> constant(H.SEGMENT_TICK)
            Hx.ToggleOn -> constant(H.TOGGLE_ON)
            Hx.ToggleOff -> constant(H.TOGGLE_OFF)
            Hx.LongPress -> constant(H.LONG_PRESS)
            Hx.Confirm -> constant(H.CONFIRM)
        }
    }

    private fun constant(c: Int) {
        view?.performHapticFeedback(c)
    }

    private fun compose(vararg parts: Pair<Int, Float>): Unit? {
        val v = vibrator ?: return null
        if (!primitives) return null
        val c = VibrationEffect.startComposition()
        parts.forEachIndexed { i, (p, scale) -> c.addPrimitive(p, scale, if (i == 0) 0 else 40) }
        v.vibrate(c.compose())
        return Unit
    }

    companion object {
        val None = Haptics(null, null, { HapticLevel.Off }, { false })
    }
}

val LocalHaptics = staticCompositionLocalOf { Haptics.None }
