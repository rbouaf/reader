package app.reader.theme

import android.os.SystemClock
import android.os.VibrationAttributes
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
    /** Refused: a name already taken, a search with no results, a PDF with no text layer. */
    Reject,
    /** A gesture crossed the point where it takes effect (pinching a PDF page into its text view)… */
    ThresholdOn,
    /** …or went back out of it. */
    ThresholdOff,
    /** Compose's own text-selection handles. */
    TextHandle(throttleMs = 18),
}

/**
 * Plays [Hx] events. System haptic constants come first: the OEM tunes those per actuator.
 * Composition primitives (thud, low tick, slow rise) are used only where the device supports
 * them, otherwise the nearest constant. Some motors (the Tab S9's) support neither and Android
 * drops every constant on them, so those get plain buzzes told apart by length and rhythm.
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

    /** No primitives and no predefined effects: only plain on/off buzzes reach the motor. */
    val buzzOnly: Boolean = vibrator?.let {
        it.hasVibrator() && !primitives &&
            it.areAllEffectsSupported(VibrationEffect.EFFECT_CLICK, VibrationEffect.EFFECT_TICK) == Vibrator.VIBRATION_EFFECT_SUPPORT_NO
    } ?: false

    private var lastBuzz = 0L

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
        if (buzzOnly) return buzz(e)
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
            Hx.Reject -> constant(H.REJECT)
            Hx.ThresholdOn -> constant(H.GESTURE_THRESHOLD_ACTIVATE)
            Hx.ThresholdOff -> constant(H.GESTURE_THRESHOLD_DEACTIVATE)
            Hx.TextHandle -> constant(H.TEXT_HANDLE_MOVE)
        }
    }

    /**
     * Pulse lengths in ms, alternating with gaps. The motor has no strength control, so lighter
     * events are shorter, not softer.
     */
    private fun buzz(e: Hx) {
        val pulses = when (e) {
            // Too frequent for a motor that can only buzz: it would hum through a scrub.
            Hx.ScrubPage, Hx.TextHandle -> return
            Hx.PageTurn, Hx.Detent, Hx.DetentHome, Hx.Select, Hx.ScrubChapter, Hx.ScrubRelease,
            Hx.ToggleOff, Hx.ThresholdOff, Hx.SwipeCancel -> longArrayOf(20)
            Hx.Chapter, Hx.ScrubGrab, Hx.ToggleOn, Hx.ThresholdOn, Hx.SwipeCommit -> longArrayOf(30)
            Hx.Edge, Hx.ScrubReturn, Hx.LongPress -> longArrayOf(50)
            Hx.Confirm -> longArrayOf(25, 70, 25)
            Hx.Reject -> longArrayOf(30, 50, 30, 50, 30)
            Hx.BookEnd -> longArrayOf(25, 90, 25, 90, 60)
        }
        val now = SystemClock.uptimeMillis()
        if (pulses.size == 1 && now - lastBuzz < 60) return
        lastBuzz = now
        effect(pulse(*pulses))
    }

    private fun constant(c: Int) {
        view?.performHapticFeedback(c)
    }

    private fun compose(vararg parts: Pair<Int, Float>): Unit? {
        if (!primitives) return null
        val c = VibrationEffect.startComposition()
        parts.forEachIndexed { i, (p, scale) -> c.addPrimitive(p, scale, if (i == 0) 0 else 40) }
        return effect(c.compose())
    }

    /** Marked as touch feedback, so the system's touch-vibration strength and off switch apply. */
    private fun effect(e: VibrationEffect): Unit? {
        val v = vibrator ?: return null
        runCatching { v.vibrate(e, touch) }
        return Unit
    }

    // ---------------- the haptics test page ----------------

    /** Plays [e] whatever the haptics level, for the test page. */
    fun preview(e: Hx) = play(e)

    /** Plays a system constant directly; false if the view refused (touch feedback off in system settings). */
    fun previewConstant(c: Int): Boolean = view?.performHapticFeedback(c) ?: false

    /** Plays a raw effect as touch feedback; false without a vibrator. */
    fun previewEffect(e: VibrationEffect): Boolean = effect(e) != null

    val device: Vibrator? get() = vibrator

    companion object {
        /** A buzz of [ms] on, then off, then on…, at the motor's one strength. */
        fun pulse(vararg ms: Long): VibrationEffect =
            if (ms.size == 1) VibrationEffect.createOneShot(ms[0], VibrationEffect.DEFAULT_AMPLITUDE)
            else VibrationEffect.createWaveform(longArrayOf(0) + ms, -1)

        private val touch: VibrationAttributes = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
        val None = Haptics(null, null, { HapticLevel.Off }, { false })
    }
}

val LocalHaptics = staticCompositionLocalOf { Haptics.None }
