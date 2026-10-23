package app.reader.reader

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import app.reader.looks.TtsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.readium.navigator.media.tts.AndroidTtsNavigator
import org.readium.navigator.media.tts.AndroidTtsNavigatorFactory
import org.readium.navigator.media.tts.TtsNavigator
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.getOrElse
import java.text.BreakIterator
import java.util.Locale

/**
 * Reads the book aloud with Android's text-to-speech, turning pages as it goes.
 * Reflowable books use Readium's TTS navigator (sentence by sentence, highlighted);
 * PDFs read each page's text layer.
 */
@OptIn(ExperimentalReadiumApi::class)
class ReadAloud(private val vm: ReaderViewModel, private val scope: CoroutineScope) {
    private val app get() = vm.getApplication<android.app.Application>()
    private var epub: AndroidTtsNavigator? = null
    private var epubJobs: List<Job> = emptyList()

    private val _utterance = MutableStateFlow<Locator?>(null)
    /** The sentence being read (reflowable books), for the highlight. */
    val utterance: StateFlow<Locator?> = _utterance.asStateFlow()

    /** Where to start: the first element on screen. Provided by the EPUB host. */
    var firstVisible: (suspend () -> Locator?)? = null

    fun supported(d: Doc.Reflow) = AndroidTtsNavigatorFactory(app, d.publication) != null

    fun toggle() = if (vm.ui.value.tts == TtsState.Off) start() else stop()

    fun start() {
        when (val d = vm.doc.value) {
            is Doc.Reflow -> startEpub(d)
            is Doc.Pdf -> startPdf(d)
            null -> {}
        }
    }

    // ---------------- reflowable ----------------

    private fun startEpub(d: Doc.Reflow) = scope.launch {
        val factory = AndroidTtsNavigatorFactory(app, d.publication) ?: return@launch
        val nav = factory.createNavigator(
            object : TtsNavigator.Listener { override fun onStopRequested() { stop() } },
            initialLocator = firstVisible?.invoke() ?: vm.currentLocator,
        ).getOrElse { Log.w(TAG, "tts init failed: $it"); return@launch }
        epub = nav
        vm.setTts(TtsState.Playing)
        epubJobs = listOf(
            scope.launch {
                nav.location.collect { loc ->
                    _utterance.value = loc.utteranceLocator
                    vm.follow(loc.utteranceLocator)
                }
            },
            scope.launch {
                nav.playback.collect { p ->
                    when (p.state) {
                        is TtsNavigator.State.Ended, is TtsNavigator.State.Failure -> stop()
                        else -> vm.setTts(if (p.playWhenReady) TtsState.Playing else TtsState.Paused)
                    }
                }
            },
        )
        nav.play()
    }

    // ---------------- PDF ----------------

    private var engine: TextToSpeech? = null
    private var ready = false
    private var page = 1
    private var sentences: List<String> = emptyList()
    private var index = 0
    private var emptyPages = 0

    private fun startPdf(d: Doc.Pdf) {
        page = vm.ui.value.page
        index = 0
        vm.setTts(TtsState.Playing)
        val e = engine
        if (e != null && ready) { speakPage(d) ; return }
        engine = TextToSpeech(app) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (!ready) { vm.setTts(TtsState.Off); return@TextToSpeech }
            engine?.language = Locale.getDefault()
            engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onError(id: String?) {}
                override fun onDone(id: String?) {
                    scope.launch {
                        if (vm.ui.value.tts != TtsState.Playing) return@launch
                        index++
                        if (index < sentences.size) speak() else { page++; index = 0; speakPage(d) }
                    }
                }
            })
            scope.launch { speakPage(d) }
        }
    }

    private fun speakPage(d: Doc.Pdf): Job = scope.launch {
        if (page > d.doc.pageCount) { stop(); return@launch }
        vm.follow(page)
        val text = d.doc.text(page - 1)
        sentences = split(text)
        if (sentences.isEmpty()) {
            // scanned pages have no text layer: skip a few, then give up
            if (++emptyPages > 3) { stop(); return@launch }
            page++; speakPage(d); return@launch
        }
        emptyPages = 0
        speak()
    }

    private fun speak() {
        val s = sentences.getOrNull(index) ?: return
        engine?.speak(s, TextToSpeech.QUEUE_FLUSH, null, "p$page-$index")
    }

    private fun split(text: String): List<String> {
        val clean = text.replace(Regex("-\\n(?=\\p{Ll})"), "").replace(Regex("\\s+"), " ").trim()
        if (clean.isEmpty()) return emptyList()
        val it = BreakIterator.getSentenceInstance()
        it.setText(clean)
        val out = mutableListOf<String>()
        var start = it.first()
        var end = it.next()
        while (end != BreakIterator.DONE) { clean.substring(start, end).trim().takeIf { s -> s.isNotEmpty() }?.let(out::add); start = end; end = it.next() }
        return out
    }

    // ---------------- controls ----------------

    fun playPause() {
        when (vm.ui.value.tts) {
            TtsState.Off -> start()
            TtsState.Playing -> { epub?.pause(); engine?.stop(); vm.setTts(TtsState.Paused) }
            TtsState.Paused -> {
                vm.setTts(TtsState.Playing)
                epub?.play() ?: speak()
            }
        }
    }

    fun previous() { epub?.skipToPreviousUtterance() ?: run { index = (index - 1).coerceAtLeast(0); speak() } }
    fun next() { epub?.skipToNextUtterance() ?: run { index++; if (index < sentences.size) speak() } }

    fun stop() {
        epubJobs.forEach { it.cancel() }
        epubJobs = emptyList()
        epub?.close()
        epub = null
        engine?.stop()
        _utterance.value = null
        vm.setTts(TtsState.Off)
    }

    fun release() {
        stop()
        engine?.shutdown()
        engine = null
    }

    companion object { private const val TAG = "ReadAloud" }
}
