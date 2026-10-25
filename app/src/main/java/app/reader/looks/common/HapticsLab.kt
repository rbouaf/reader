package app.reader.looks.common

import android.os.VibrationEffect
import android.os.VibrationEffect.Composition as C
import android.provider.Settings
import android.view.HapticFeedbackConstants as H
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import app.reader.looks.LocalLook
import app.reader.theme.Hx
import app.reader.theme.LocalHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Settings → Haptics → Try every effect: what this device's motor supports, the app's own
 * haptics, a few ideas not yet in use, and every system effect, each played on a tap.
 * Effects play whatever the haptics level is, so they can be felt before choosing one.
 */
@Composable
fun HapticsLab(onClose: () -> Unit) {
    val kit = LocalLook.current.kit
    val hx = LocalHaptics.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val v = hx.device

    val primitives = PRIMITIVES.map { it.second }.toIntArray()
    val supported = remember(v) { v?.arePrimitivesSupported(*primitives) ?: BooleanArray(primitives.size) }
    val durations = remember(v) { v?.getPrimitiveDurations(*primitives) ?: IntArray(primitives.size) }
    val envelopes = remember(v) { v?.areEnvelopeEffectsSupported() == true }
    val allPrimitives = supported.all { it }
    val report = remember(v) {
        if (v == null || !v.hasVibrator()) "This device has no vibration motor."
        else buildList {
            val n = supported.count { it }
            add(when (n) { supported.size -> "Plays all $n primitives"; 0 -> "Plays no primitives, so the app uses system effects"; else -> "Plays $n of ${supported.size} primitives" } + ".")
            add(if (v.hasAmplitudeControl()) "Has strength control." else "No strength control: everything plays at full strength.")
            add(if (envelopes) "Plays envelopes." else "No envelopes.")
            v.resonantFrequency.takeIf { !it.isNaN() && it > 0 }?.let { add("Motor resonance ${it.toInt()} Hz.") }
            if (Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0)
                add("Touch feedback is off in system settings, so system effects won't play.")
        }.joinToString(" ")
    }

    fun primitivesOr(effect: () -> VibrationEffect, fallback: suspend () -> Unit) {
        if (allPrimitives) hx.previewEffect(effect()) else scope.launch { fallback() }
    }

    kit.Panel("Haptics", onClose, top = { kit.PanelMessage(report) }, backLabel = "Settings") {
        item { kit.PanelMessage("In this app") }
        APP.forEach { (e, name, where) ->
            item { kit.PanelRow(name, where, "play", false, 0) { hx.preview(e) } }
        }

        item { kit.PanelMessage("Ideas, not in use yet") }
        item {
            kit.PanelRow("Scrubber pull", "Ticks grow as the scrubber nears the page you came from, then the thud.", "play", false, 0) {
                primitivesOr({
                    VibrationEffect.startComposition().apply {
                        listOf(0.15f, 0.3f, 0.5f, 0.7f).forEachIndexed { i, s -> addPrimitive(C.PRIMITIVE_TICK, s, if (i == 0) 0 else 70) }
                        addPrimitive(C.PRIMITIVE_THUD, 0.8f, 60)
                    }.compose()
                }) {
                    repeat(4) { hx.previewConstant(H.SEGMENT_FREQUENT_TICK); delay(70) }
                    hx.previewConstant(H.LONG_PRESS)
                }
            }
        }
        item {
            kit.PanelRow(
                "Book end swell", if (envelopes) "A smooth swell instead of rise-then-click." else "Needs envelope support, which this device lacks.",
                if (envelopes) "play" else "–", false, 0,
            ) { if (envelopes) hx.previewEffect(swell()) }
        }
        item { kit.PanelRow("Swipe far enough", "Full level: a PDF swipe passing the point where it turns the page.", "play", false, 0) { hx.previewConstant(H.GESTURE_THRESHOLD_ACTIVATE) } }
        item { kit.PanelRow("Swipe backed out", "Full level: letting the swipe fall back.", "play", false, 0) { hx.previewConstant(H.GESTURE_THRESHOLD_DEACTIVATE) } }

        item { kit.PanelMessage("System effects, tuned by the maker for this motor") }
        CONSTANTS.forEach { (name, c, use) ->
            item { kit.PanelRow(name, use, "play", false, 0) { hx.previewConstant(c) } }
        }

        item { kit.PanelMessage("Primitives") }
        PRIMITIVES.forEachIndexed { i, (name, p, feel) ->
            item {
                kit.PanelRow(
                    name, if (supported[i]) "$feel ${durations[i]} ms." else "$feel Not supported here.",
                    if (supported[i]) "play" else "–", false, 0,
                ) { if (supported[i]) hx.previewEffect(VibrationEffect.startComposition().addPrimitive(p).compose()) }
            }
        }

        item { kit.PanelMessage("Ready-made effects") }
        PREDEFINED.forEach { (name, e) ->
            item { kit.PanelRow(name, null, "play", false, 0) { hx.previewEffect(VibrationEffect.createPredefined(e)) } }
        }

        if (envelopes) {
            item { kit.PanelMessage("Envelopes") }
            item { kit.PanelRow("Swell", "Builds over 400 ms and fades.", "play", false, 0) { hx.previewEffect(swell()) } }
            item { kit.PanelRow("Spring", "A sharp hit that bounces once.", "play", false, 0) { hx.previewEffect(spring()) } }
        }
    }
}

private fun swell() = VibrationEffect.BasicEnvelopeBuilder()
    .setInitialSharpness(0.2f)
    .addControlPoint(0.7f, 0.4f, 400)
    .addControlPoint(0f, 0.4f, 200)
    .build()

private fun spring() = VibrationEffect.BasicEnvelopeBuilder()
    .setInitialSharpness(0.9f)
    .addControlPoint(1f, 0.9f, 20)
    .addControlPoint(0.3f, 0.5f, 80)
    .addControlPoint(0.6f, 0.6f, 60)
    .addControlPoint(0f, 0.5f, 120)
    .build()

private val APP = listOf(
    Triple(Hx.PageTurn, "Page turn", "Each page turn, at the Full level or with the page tick on."),
    Triple(Hx.Chapter, "Chapter", "Turning into a new chapter."),
    Triple(Hx.Edge, "Edge", "The first or last page, a size or zoom limit."),
    Triple(Hx.BookEnd, "Book end", "Reaching the end of the book."),
    Triple(Hx.ScrubGrab, "Scrubber grab", "Touching the scrubber."),
    Triple(Hx.ScrubPage, "Scrubber page", "Each page while scrubbing."),
    Triple(Hx.ScrubChapter, "Scrubber chapter", "A chapter start while scrubbing."),
    Triple(Hx.ScrubReturn, "Return mark", "The page you came from, and jumping back to it."),
    Triple(Hx.ScrubRelease, "Scrubber release", "Letting go of the scrubber (Full level)."),
    Triple(Hx.Detent, "Step", "Slider steps and text sizes while pinching."),
    Triple(Hx.DetentHome, "Home step", "A slider's neutral point; a PDF settling back on the whole page."),
    Triple(Hx.Select, "Select", "Choosing an option; skipping a sentence while reading aloud."),
    Triple(Hx.ToggleOn, "On", "Switching something on; starting read aloud."),
    Triple(Hx.ToggleOff, "Off", "Switching something off; pausing or stopping read aloud."),
    Triple(Hx.LongPress, "Hold", "Holding a book in the library."),
    Triple(Hx.Confirm, "Confirm", "Saving details, marking finished, choosing a folder."),
    Triple(Hx.Reject, "Refuse", "A name already taken, no search results, a PDF with no text."),
    Triple(Hx.ThresholdOn, "Into text view", "Pinching a PDF page into its text view."),
    Triple(Hx.ThresholdOff, "Back to the page", "Pinching back out to the page."),
)

private val CONSTANTS = listOf(
    Triple("CLOCK_TICK", H.CLOCK_TICK, "Picker wheels and clock hands."),
    Triple("CONFIRM", H.CONFIRM, "Something succeeded."),
    Triple("REJECT", H.REJECT, "Something was refused."),
    Triple("CONTEXT_CLICK", H.CONTEXT_CLICK, "A right-click or stylus-button press."),
    Triple("DRAG_START", H.DRAG_START, "Picking something up to drag."),
    Triple("GESTURE_START", H.GESTURE_START, "A gesture begins."),
    Triple("GESTURE_END", H.GESTURE_END, "A gesture ends."),
    Triple("GESTURE_THRESHOLD_ACTIVATE", H.GESTURE_THRESHOLD_ACTIVATE, "A gesture passes the point where it takes effect."),
    Triple("GESTURE_THRESHOLD_DEACTIVATE", H.GESTURE_THRESHOLD_DEACTIVATE, "The gesture backs out again."),
    Triple("KEYBOARD_TAP", H.KEYBOARD_TAP, "A key goes down."),
    Triple("KEYBOARD_RELEASE", H.KEYBOARD_RELEASE, "A key comes up."),
    Triple("LONG_PRESS", H.LONG_PRESS, "A long-press menu appears."),
    Triple("SEGMENT_TICK", H.SEGMENT_TICK, "A major mark on a slider."),
    Triple("SEGMENT_FREQUENT_TICK", H.SEGMENT_FREQUENT_TICK, "Fine steps on a slider."),
    Triple("TEXT_HANDLE_MOVE", H.TEXT_HANDLE_MOVE, "Dragging a text-selection handle."),
    Triple("TOGGLE_ON", H.TOGGLE_ON, "A switch turns on."),
    Triple("TOGGLE_OFF", H.TOGGLE_OFF, "A switch turns off."),
    Triple("VIRTUAL_KEY", H.VIRTUAL_KEY, "An on-screen button."),
    Triple("VIRTUAL_KEY_RELEASE", H.VIRTUAL_KEY_RELEASE, "An on-screen button released."),
)

private val PRIMITIVES = listOf(
    Triple("CLICK", C.PRIMITIVE_CLICK, "A crisp button."),
    Triple("TICK", C.PRIMITIVE_TICK, "Light and short."),
    Triple("LOW_TICK", C.PRIMITIVE_LOW_TICK, "Softer and deeper than a tick."),
    Triple("THUD", C.PRIMITIVE_THUD, "Heavy and dull, like a wall."),
    Triple("QUICK_RISE", C.PRIMITIVE_QUICK_RISE, "A fast swell."),
    Triple("SLOW_RISE", C.PRIMITIVE_SLOW_RISE, "A slow swell."),
    Triple("QUICK_FALL", C.PRIMITIVE_QUICK_FALL, "A fade-out."),
    Triple("SPIN", C.PRIMITIVE_SPIN, "A whirl."),
)

private val PREDEFINED = listOf(
    "EFFECT_CLICK" to VibrationEffect.EFFECT_CLICK,
    "EFFECT_TICK" to VibrationEffect.EFFECT_TICK,
    "EFFECT_DOUBLE_CLICK" to VibrationEffect.EFFECT_DOUBLE_CLICK,
    "EFFECT_HEAVY_CLICK" to VibrationEffect.EFFECT_HEAVY_CLICK,
)
