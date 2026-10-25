package app.reader.ui

import android.app.Activity
import android.content.Context
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import app.reader.data.AppSettings
import app.reader.theme.Haptics
import app.reader.theme.LocalColors
import app.reader.theme.Hx
import app.reader.theme.LocalHaptics
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** Haptics bound to the current view and settings, plus status/navigation bar icon colours. */
@Composable
fun AppChrome(settings: AppSettings, content: @Composable () -> Unit) {
    val view = LocalView.current
    val context = LocalContext.current
    val s by rememberUpdatedState(settings)
    val haptics = remember(view) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        Haptics(view, vm?.defaultVibrator, { s.haptics }, { s.pageTick })
    }
    val colors = LocalColors.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !colors.isDark
                isAppearanceLightNavigationBars = !colors.isDark
            }
            window.decorView.setBackgroundColor(android.graphics.Color.argb(
                (colors.paper.alpha * 255).toInt(), (colors.paper.red * 255).toInt(), (colors.paper.green * 255).toInt(), (colors.paper.blue * 255).toInt()))
        }
    }
    // Compose's own haptics (long-press on a book, text-selection handles) follow the app's level too
    val compose = remember(haptics) { ComposeHaptics(haptics) }
    CompositionLocalProvider(LocalHaptics provides haptics, LocalHapticFeedback provides compose, content = content)
}

private class ComposeHaptics(private val hx: Haptics) : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        when (hapticFeedbackType) {
            HapticFeedbackType.LongPress -> hx(Hx.LongPress)
            HapticFeedbackType.TextHandleMove -> hx(Hx.TextHandle)
            HapticFeedbackType.Confirm -> hx(Hx.Confirm)
            HapticFeedbackType.Reject -> hx(Hx.Reject)
            HapticFeedbackType.ToggleOn -> hx(Hx.ToggleOn)
            HapticFeedbackType.ToggleOff -> hx(Hx.ToggleOff)
            HapticFeedbackType.SegmentTick -> hx(Hx.Select)
            HapticFeedbackType.SegmentFrequentTick -> hx(Hx.Detent)
            HapticFeedbackType.GestureThresholdActivate -> hx(Hx.ThresholdOn)
            else -> {}
        }
    }
}
