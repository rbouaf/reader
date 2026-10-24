package app.reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.fragment.compose.AndroidFragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import app.reader.data.Book
import app.reader.data.TapSide
import app.reader.looks.ChromeActions
import app.reader.looks.ChromeUi
import app.reader.looks.LocalLook
import app.reader.looks.Looks
import app.reader.looks.TtsState
import app.reader.reader.ContentsPanel
import app.reader.reader.Doc
import app.reader.reader.Panel
import app.reader.reader.ReaderViewModel
import app.reader.reader.SearchPanel
import app.reader.reader.epub.EpubHost
import app.reader.reader.epub.ReadingStyle
import app.reader.reader.epub.ReadingStyles
import app.reader.reader.pdf.PdfViewer
import app.reader.theme.LocalColors
import app.reader.theme.LocalHaptics
import app.reader.theme.ReaderTheme
import app.reader.theme.bookFontFor
import app.reader.ui.AppChrome

/** The book view: only the book, and minimal controls on a tap in the middle. */
class ReaderActivity : FragmentActivity() {
    private val vm: ReaderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        vm.open(intent.getStringExtra(EXTRA_KEY), intent.data.takeIf { intent.action == Intent.ACTION_VIEW })
        setContent { ReaderScreen(vm, onClose = { finish() }) }
        debugHooks()
    }

    /** Debug builds only: `adb shell am start … --ef debug.pinch 1.5` / `--ez debug.textview true` stand in for two-finger gestures. */
    private fun debugHooks() {
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val pinch = intent.getFloatExtra("debug.pinch", 0f)
        val text = intent.getBooleanExtra("debug.textview", false)
        if (pinch == 0f && !text) return
        lifecycleScope.launch {
            kotlinx.coroutines.delay(4000)
            if (text) vm.enterTextView()
            if (pinch != 0f) { vm.pinchStart(); for (i in 1..10) { vm.pinchBy(1 + (pinch - 1) * i / 10f); kotlinx.coroutines.delay(60) }; vm.pinchEnd() }
        }
    }

    override fun onResume() { super.onResume(); vm.setActive(true); vm.tts.catchUp() }
    override fun onPause() { vm.setActive(false); super.onPause() }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val volume = event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
        if (volume && app.settings.current.volumeKeys && vm.ui.value.panel == Panel.None && vm.ui.value.tts == TtsState.Off) {
            if (event.action == KeyEvent.ACTION_DOWN) vm.turn(forward = event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN) when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_SPACE -> { vm.turn(true); return true }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_PAGE_UP -> { vm.turn(false); return true }
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        const val EXTRA_KEY = "book.key"
        fun intent(context: Context, book: Book) = Intent(context, ReaderActivity::class.java).putExtra(EXTRA_KEY, book.key)
    }
}

@Composable
private fun ReaderScreen(vm: ReaderViewModel, onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.app
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val doc by vm.doc.collectAsStateWithLifecycle()
    val lib by app.library.state.collectAsStateWithLifecycle()
    val look = Looks.byId(settings.look)

    ReaderTheme(look, settings) {
        AppChrome(settings) {
            val colors = LocalColors.current
            val haptics = LocalHaptics.current
            val kit = LocalLook.current.kit
            LaunchedEffect(Unit) { vm.hx.collect { haptics(it) } }

            val style = ReadingStyle(
                paper = colors.paper, ink = colors.ink, highlight = colors.ink.copy(alpha = 0.28f), dark = colors.isDark,
                font = bookFontFor(look, settings), size = settings.size, leading = settings.leading,
                margins = settings.margins.readium, justify = settings.justify, hyphens = settings.hyphens,
                spread = settings.spread, anim = settings.anim, forwardOnRight = settings.tapSide == TapSide.Right,
            )
            SideEffect { ReadingStyles.current.value = style }

            BackHandler {
                when {
                    ui.panel != Panel.None -> vm.closePanel()
                    ui.chrome -> vm.hideChrome()
                    ui.textView -> vm.exitTextView()
                    else -> onClose()
                }
            }

            Box(Modifier.fillMaxSize().background(colors.paper)) {
                when (val d = doc) {
                    is Doc.Reflow -> {
                        val safe = WindowInsets.safeDrawing.asPaddingValues()
                        AndroidFragment<EpubHost>(Modifier.fillMaxSize().padding(top = safe.calculateTopPadding(), bottom = safe.calculateBottomPadding()))
                        // the strips beside the camera and the gesture bar belong to the menu bands too
                        val strip = Modifier.fillMaxWidth().clickable(remember { MutableInteractionSource() }, null) { vm.toggleChrome() }
                        Box(strip.height(safe.calculateTopPadding()).align(Alignment.TopCenter))
                        Box(strip.height(safe.calculateBottomPadding()).align(Alignment.BottomCenter))
                    }
                    is Doc.Pdf -> PdfViewer(d, vm, settings, colors)
                    null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        kit.PanelMessage(ui.error ?: "Opening ${ui.title}")
                    }
                }

                if (ui.chrome && doc != null) {
                    // a tap anywhere on the page closes the controls
                    Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { vm.hideChrome() })
                    val book = vm.key?.let { k -> lib.books.firstOrNull { it.key == k } }
                    look.ReaderChrome(
                        ChromeUi(
                            title = ui.title, author = ui.author, format = ui.format,
                            chapter = ui.chapter,
                            chapterIndex = ui.topLevel.indexOfLast { e -> e.page != null && e.page <= ui.page }.takeIf { it >= 0 }?.plus(1),
                            chapterCount = ui.topLevel.size.takeIf { it > 0 },
                            page = ui.page, pageLast = ui.pageLast, pageCount = ui.pageCount,
                            chapterPages = ui.chapterPages,
                            returnPage = ui.returnPage, scrubbing = ui.scrubbing,
                            activity = book?.activity(28) ?: List(28) { 0 },
                            tts = ui.tts, canSearch = true, canReadAloud = ui.canReadAloud,
                        ),
                        ChromeActions(
                            back = onClose,
                            contents = { vm.openPanel(Panel.Contents) },
                            search = { vm.openPanel(Panel.Search) },
                            readAloud = { vm.hideChrome(); vm.tts.toggle() },
                            goBackToMark = vm::goBackToMark,
                            scrubStart = vm::scrubStart,
                            scrubTo = vm::scrubTo,
                            scrubEnd = vm::scrubEnd,
                        ),
                    )
                }

                ui.notice?.let { n ->
                    Box(Modifier.align(Alignment.Center).background(colors.paper)) { kit.PanelMessage(n) }
                }
                ui.zoomSize?.let { z ->
                    Box(Modifier.align(Alignment.Center).background(colors.paper)) { kit.PanelMessage("$z px") }
                }

                if (ui.tts != TtsState.Off && !ui.chrome) {
                    Box(Modifier.align(Alignment.BottomCenter)) {
                        kit.TtsBar(
                            playing = ui.tts == TtsState.Playing,
                            label = ui.chapter ?: ui.title,
                            onPlayPause = vm.tts::playPause,
                            onPrevious = vm.tts::previous,
                            onNext = vm.tts::next,
                            onStop = vm.tts::stop,
                        )
                    }
                }

                when (ui.panel) {
                    Panel.Contents -> ContentsPanel(ui.toc, ui.tocIndex, onPick = vm::jumpTo, onClose = vm::closePanel)
                    Panel.Search -> SearchPanel(ui.search, vm.search::query, vm.search::run, vm.search::pick, vm::closePanel)
                    Panel.None -> {}
                }
            }
        }
    }
}

