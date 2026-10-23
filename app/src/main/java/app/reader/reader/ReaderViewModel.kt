package app.reader.reader

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.reader.app
import app.reader.data.Book
import app.reader.data.Format
import app.reader.data.Metadata
import app.reader.data.ReadingState
import app.reader.data.TextToEpub
import app.reader.looks.TtsState
import app.reader.looks.common.MAX_SIZE
import app.reader.looks.common.MIN_SIZE
import app.reader.reader.pdf.PdfDoc
import app.reader.reader.pdf.PdfOutline
import app.reader.reader.pdf.PdfText
import app.reader.theme.Hx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.indexOfFirstWithHref
import org.readium.r2.shared.publication.services.positionsByReadingOrder
import java.io.File

enum class Panel { None, Contents, Search }

/** Everything the book screen shows apart from the page itself. */
data class ReaderUi(
    val title: String = "",
    val author: String? = null,
    val format: Format = Format.Epub,
    val loading: Boolean = true,
    val error: String? = null,
    val page: Int = 1,
    val pageLast: Int = 1,
    val pageCount: Int = 1,
    val toc: List<TocEntry> = emptyList(),
    /** Index into [toc] of the entry we're in. */
    val tocIndex: Int? = null,
    val chapterPages: List<Int> = emptyList(),
    val returnPage: Int? = null,
    val scrubbing: Boolean = false,
    val chrome: Boolean = false,
    val panel: Panel = Panel.None,
    val search: SearchUi = SearchUi(),
    val tts: TtsState = TtsState.Off,
    val canReadAloud: Boolean = false,
    /** Text size while a pinch is resizing it, for the on-screen readout. */
    val zoomSize: Int? = null,
    /** The title inside the file (the shown [title] may be the user's edit). */
    val docTitle: String = "",
    /** A PDF shown as re-wrapped text (text wrap zoom). */
    val textView: Boolean = false,
    /** A short message over the page ("Preparing text view…"). */
    val notice: String? = null,
) {
    val chapter get() = tocIndex?.let { toc.getOrNull(it)?.title }
    /** Top-level chapters only, for "chapter 3 of 7": front and back matter don't count. */
    val topLevel get() = toc.filter { it.depth == 0 && !isMatter(it.title, title) && !isMatter(it.title, docTitle) }
}

private val MATTER = Regex(
    "^(contents|table of contents|title( page)?|cover|copyright.*|colophon|dedication|acknowledg(e)?ments|about the (author|publisher)|also by .*|index|imprint|.*project gutenberg.*|the full project gutenberg.*)$",
    RegexOption.IGNORE_CASE,
)

/** Contents entries that aren't chapters: the contents page itself, the title page, licences… */
fun isMatter(entry: String, bookTitle: String) = MATTER.matches(entry.trim().trimEnd('.')) || entry.trim().equals(bookTitle.trim(), ignoreCase = true)

/** The opened document, by engine. */
sealed interface Doc {
    class Reflow(
        val publication: Publication,
        val factory: EpubNavigatorFactory,
        /** Every position (≈ a page of 1024 characters) in reading order; page N is positions[N-1]. */
        val positions: List<Locator>,
        /** First page of each reading-order resource, 1-based. */
        val resourceStart: List<Int>,
        val initial: Locator?,
    ) : Doc

    class Pdf(val doc: PdfDoc, val initialPage: Int, val trim: Pair<android.graphics.RectF, android.graphics.RectF>) : Doc
}

@OptIn(ExperimentalReadiumApi::class)
class ReaderViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.app
    private val library = app.library

    private val _ui = MutableStateFlow(ReaderUi())
    val ui: StateFlow<ReaderUi> = _ui.asStateFlow()

    private val _doc = MutableStateFlow<Doc?>(null)
    val doc: StateFlow<Doc?> = _doc.asStateFlow()

    /** Haptic events for the screen to play (the view model has no view). */
    private val _hx = MutableSharedFlow<Hx>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val hx: SharedFlow<Hx> = _hx

    /** Engine commands: the EPUB host and the PDF viewer each collect these. */
    sealed interface Command {
        data class GoPage(val page: Int, val animated: Boolean) : Command
        data class GoLocator(val locator: Locator) : Command
        data class Turn(val forward: Boolean) : Command
    }
    private val _commands = MutableSharedFlow<Command>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val commands: SharedFlow<Command> = _commands

    var key: String? = null
        private set
    private var openedUri: Uri? = null
    private var opened = false
    private var lastLocator: Locator? = null
    private var ended = false
    /** Set while a position change was caused by a jump or scrub (no page-turn haptic). */
    private var quietUntilNextChange = false

    val tts = ReadAloud(this, viewModelScope)
    val search = BookSearch(this, viewModelScope)

    // ---------------- opening ----------------

    fun open(bookKey: String?, external: Uri?) {
        if (opened) return
        opened = true
        key = bookKey
        viewModelScope.launch {
            val book = bookKey?.let { awaitBook(it) }
            val uri = book?.uri ?: external
            if (uri == null) { fail("This book isn't in the library any more."); return@launch }
            openedUri = uri
            val format = book?.format ?: Format.of(external?.lastPathSegment ?: "") ?: guessFormat(uri)
            val title = book?.title ?: Metadata.fromFileName(external?.lastPathSegment?.substringAfterLast('/') ?: "Book").title ?: "Book"
            _ui.update { it.copy(title = title, author = book?.author, format = format) }
            try {
                val doc = withContext(Dispatchers.IO) {
                    when (format) {
                        Format.Pdf -> openPdf(uri, book?.state)
                        Format.Epub -> openReflow(app.readium.retrieve(uri), book?.state)
                        Format.Txt, Format.Md -> openReflow(app.readium.retrieveFile(convertText(uri, book, format, title)), book?.state)
                    }
                }
                _doc.value = doc
                if (doc is Doc.Reflow) {
                    val pubTitle = doc.publication.metadata.title
                    _ui.update { it.copy(title = book?.title ?: pubTitle ?: title, docTitle = pubTitle ?: "", canReadAloud = tts.supported(doc)) }
                } else {
                    _ui.update { it.copy(canReadAloud = true) }
                }
                if (book != null) library.save(book.key) { it.copy(openedAt = System.currentTimeMillis()) }
                startActivityClock()
            } catch (e: Exception) {
                Log.w(TAG, "open failed", e)
                fail(e.message ?: "Couldn't open this book.")
            }
        }
    }

    private suspend fun awaitBook(key: String): Book? {
        library.book(key)?.let { return it }
        // cold start straight into the reader: wait for the first scan
        repeat(100) { delay(100); library.book(key)?.let { return it } }
        return null
    }

    private fun guessFormat(uri: Uri): Format {
        val type = app.contentResolver.getType(uri) ?: ""
        return when {
            "pdf" in type -> Format.Pdf
            "markdown" in type -> Format.Md
            type.startsWith("text/") -> Format.Txt
            else -> Format.Epub
        }
    }

    private fun fail(msg: String) = _ui.update { it.copy(loading = false, error = msg) }

    private fun convertText(uri: Uri, book: Book?, format: Format, title: String): File {
        val stamp = "${book?.size ?: 0}-${book?.modified ?: 0}"
        val name = (book?.key ?: uri.toString().hashCode().toString()) + "-$stamp.epub"
        val out = File(app.cacheDir, "converted/$name")
        if (!out.exists()) {
            out.parentFile?.listFiles { f -> book != null && f.name.startsWith(book.key) }?.forEach { it.delete() }
            val bytes = app.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            TextToEpub.convert(bytes, format, title, book?.author, out)
        }
        return out
    }

    private suspend fun openReflow(publication: Publication, state: ReadingState?): Doc.Reflow {
        val byResource = publication.positionsByReadingOrder()
        val positions = byResource.flatten()
        val starts = mutableListOf<Int>()
        var n = 1
        byResource.forEach { starts += n; n += it.size }
        val initial = state?.locator?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }
            ?.takeIf { it.locations.position != null || it.locations.progression != null }
        val bytes = mutableMapOf<Int, ByteArray?>()
        val toc = flatten(publication.tableOfContents).mapNotNull { (link, depth) ->
            val url = link.url()
            val idx = publication.readingOrder.indexOfFirstWithHref(url) ?: return@mapNotNull null
            val start = starts.getOrNull(idx) ?: return@mapNotNull null
            // Chapters often share one file and differ only by #anchor: place them by where the
            // anchor sits in the file (Readium counts positions by bytes, so this lines up).
            val page = url.fragment?.let { frag ->
                val data = bytes.getOrPut(idx) { publication.get(publication.readingOrder[idx])?.read()?.getOrNull() }
                val at = data?.let { anchorOffset(it, frag) }
                if (data == null || at == null) null
                else start + (byResource[idx].size * at.toDouble() / data.size).toInt().coerceIn(0, maxOf(0, byResource[idx].size - 1))
            } ?: start
            TocEntry(link.title?.trim()?.ifEmpty { null } ?: "Untitled", depth, page, link)
        }
        rawToc = toc
        rawChapters = toc.filter { e -> e.depth == 0 && !isMatter(e.title, publication.metadata.title ?: "") }.mapNotNull { e -> e.page }.distinct().sorted()
            .ifEmpty { toc.mapNotNull { e -> e.page }.distinct().sorted() }
        positionCount = positions.size.coerceAtLeast(1)
        _ui.update { it.copy(loading = false) }
        rescale()
        return Doc.Reflow(publication, EpubNavigatorFactory(publication), positions, starts, initial)
    }

    private fun anchorOffset(data: ByteArray, id: String): Int? {
        val text = String(data, Charsets.ISO_8859_1)
        for (q in listOf("id=\"$id\"", "id='$id'", "name=\"$id\"")) {
            val i = text.indexOf(q)
            if (i >= 0) return i
        }
        return null
    }

    private fun flatten(links: List<Link>, depth: Int = 0): List<Pair<Link, Int>> =
        links.flatMap { listOf(it to depth) + flatten(it.children, depth + 1) }

    private suspend fun openPdf(uri: Uri, state: ReadingState?): Doc.Pdf {
        val fd = app.contentResolver.openFileDescriptor(uri, "r") ?: error("Can't read this file.")
        val doc = PdfDoc(fd)
        if (doc.pageCount == 0) { doc.close(); error("This PDF has no pages.") }
        val page = state?.locator?.let { runCatching { JSONObject(it).getInt("page") }.getOrNull() }
            ?: state?.page ?: 1
        val trim = if (app.settings.current.pdfTrim) doc.trimBoxes() else PdfDoc.FULL to PdfDoc.FULL
        _ui.update { it.copy(loading = false, pageCount = doc.pageCount, page = page.coerceIn(1, doc.pageCount), pageLast = page.coerceIn(1, doc.pageCount)) }
        // the outline can take a moment on big files: contents and chapter ticks arrive when ready
        viewModelScope.launch(Dispatchers.IO) {
            val toc = PdfOutline.read(app, uri)
            if (toc.isEmpty()) return@launch
            _ui.update { u ->
                u.copy(
                    toc = toc,
                    tocIndex = toc.indexOfLast { e -> e.page != null && e.page <= u.page }.takeIf { it >= 0 },
                    chapterPages = toc.filter { e -> e.depth == 0 && !isMatter(e.title, u.title) }.mapNotNull { it.page }.distinct().sorted(),
                )
            }
        }
        return Doc.Pdf(doc, page.coerceIn(1, doc.pageCount), trim)
    }

    // ---------------- screen pages ----------------
    //
    // Readium counts "positions" (about 1024 characters each). People count screens. The
    // navigator tells us how many screens the current resource takes, which gives screens per
    // position; everything shown (page x of y, contents, ticks, search, library) uses screens.
    // PDFs have real pages, so their scale stays 1.

    private var scale = 1.0
    private var measured = false
    private var positionCount = 1
    private var rawToc: List<TocEntry> = emptyList()
    private var rawChapters: List<Int> = emptyList()
    /** Last pagination report: resource index, page in it (0-based), its page count. */
    private var paging: Triple<Int, Int, Int>? = null

    private fun toScreen(pos: Int) = ((pos - 1) * scale).roundToInt() + 1
    /** The position (1-based) a screen page starts in. */
    fun positionFor(screen: Int) = (((screen - 1) / scale).toInt() + 1).coerceIn(1, positionCount)

    private fun rescale() {
        val count = maxOf(1, (positionCount * scale).roundToInt())
        _ui.update { u ->
            u.copy(
                pageCount = count,
                toc = rawToc.map { e -> e.copy(page = e.page?.let(::toScreen)?.coerceAtMost(count)) },
                chapterPages = rawChapters.map(::toScreen).map { it.coerceAtMost(count) }.distinct(),
            )
        }
    }

    /** From the EPUB navigator: the visible screen inside the current resource. */
    fun onPagination(pageIndex: Int, totalPages: Int, locator: Locator) {
        val d = _doc.value as? Doc.Reflow ?: return
        val r = d.publication.readingOrder.indexOfFirstWithHref(locator.href) ?: return
        paging = Triple(r, pageIndex, totalPages)
        val n = (d.resourceStart.getOrElse(r + 1) { positionCount + 1 }) - d.resourceStart[r]
        if (!measured && totalPages >= 3 && n >= 2) {
            measured = true
            scale = totalPages.toDouble() / n
            rescale()
        }
        lastLocator?.let { onLocator(it) }
    }

    /** Text size or layout changed: measure screens per position again. */
    fun onStyleChanged() { measured = false }

    // ---------------- position ----------------

    /** From the EPUB navigator whenever the visible location changes. */
    fun onLocator(locator: Locator) {
        val d = _doc.value as? Doc.Reflow ?: return
        lastLocator = locator
        val r = d.publication.readingOrder.indexOfFirstWithHref(locator.href)
        val p = paging
        val page = if (p != null && p.first == r) {
            // exact within the resource on screen
            (toScreen(d.resourceStart[r]) + p.second).coerceAtMost(_ui.value.pageCount)
        } else toScreen(pageOf(d, locator))
        onPage(page, page, locator)
    }

    /** From the PDF viewer (page..pageLast visible, 1-based). */
    fun onPdfPage(page: Int, pageLast: Int) = onPage(page, pageLast, null)

    private fun onPage(page: Int, pageLast: Int, locator: Locator?) {
        val before = _ui.value
        val tocIndex = before.toc.indexOfLast { e -> e.page != null && e.page <= page }.takeIf { it >= 0 }
        val moved = page != before.page
        _ui.update { it.copy(page = page, pageLast = pageLast, tocIndex = tocIndex) }
        if (moved && !quietUntilNextChange && !before.scrubbing) {
            val chapterChanged = before.chapterPages.count { it <= page } != before.chapterPages.count { it <= before.page }
            _hx.tryEmit(if (chapterChanged) Hx.Chapter else Hx.PageTurn)
        }
        if (moved) quietUntilNextChange = false
        val atEnd = pageLast >= before.pageCount && before.pageCount > 1
        if (atEnd && !ended) { ended = true; viewModelScope.launch { delay(160); _hx.tryEmit(Hx.BookEnd) } }
        touch()
        scheduleSave(locator)
    }

    /** The 1-based page of a locator in a reflowable book. */
    fun pageFor(l: Locator): Int? = (_doc.value as? Doc.Reflow)?.let { toScreen(pageOf(it, l)) }

    private fun pageOf(d: Doc.Reflow, l: Locator): Int {
        l.locations.position?.let { return it.coerceIn(1, d.positions.size.coerceAtLeast(1)) }
        val idx = d.publication.readingOrder.indexOfFirstWithHref(l.href) ?: return _ui.value.page
        val start = d.resourceStart.getOrElse(idx) { 1 }
        val end = d.resourceStart.getOrElse(idx + 1) { d.positions.size + 1 }
        return (start + ((end - start) * (l.locations.progression ?: 0.0)).toInt()).coerceIn(start, maxOf(start, end - 1))
    }

    // ---------------- navigation ----------------

    fun turn(forward: Boolean) { hideChrome(); _commands.tryEmit(Command.Turn(forward)) }

    /** The engine reports a turn past the first or last page. */
    fun onEdge() { _hx.tryEmit(Hx.Edge) }

    fun jumpTo(page: Int) {
        markReturn()
        quietUntilNextChange = true
        _commands.tryEmit(Command.GoPage(page, animated = false))
    }

    fun jumpTo(entry: TocEntry) {
        markReturn()
        quietUntilNextChange = true
        val d = _doc.value
        if (d is Doc.Reflow && entry.target is Link) {
            d.publication.locatorFromLink(entry.target)?.let { _commands.tryEmit(Command.GoLocator(it)) }
        } else entry.page?.let { _commands.tryEmit(Command.GoPage(it, false)) }
        _ui.update { it.copy(panel = Panel.None, chrome = false) }
    }

    fun jumpTo(locator: Locator) {
        markReturn()
        quietUntilNextChange = true
        _commands.tryEmit(Command.GoLocator(locator))
    }

    private fun markReturn() = _ui.update { it.copy(returnPage = it.returnPage ?: it.page) }

    fun goBackToMark() {
        val r = _ui.value.returnPage ?: return
        quietUntilNextChange = true
        _ui.update { it.copy(returnPage = null) }
        _commands.tryEmit(Command.GoPage(r, false))
        _hx.tryEmit(Hx.ScrubReturn)
    }

    fun scrubStart() = _ui.update { it.copy(scrubbing = true, returnPage = it.returnPage ?: it.page) }

    fun scrubTo(page: Int) {
        quietUntilNextChange = true
        _ui.update { it.copy(page = page, pageLast = page) }
        _commands.tryEmit(Command.GoPage(page, false))
    }

    fun scrubEnd() = _ui.update { it.copy(scrubbing = false, returnPage = it.returnPage?.takeIf { r -> r != it.page }) }

    // ---------------- text wrap zoom ----------------

    private var pinchStart = 0
    private var lastApplied = 0L

    fun pinchStart() {
        pinchStart = app.settings.current.size
        hideChrome()
        _ui.update { it.copy(zoomSize = pinchStart) }
    }

    /** [factor] is the pinch's total scale since it began. Sizes snap to whole pixels. */
    fun pinchBy(factor: Float) {
        // in a PDF's text view, pinching out goes back to the real page
        if (_ui.value.textView && factor < 0.75f) { _ui.update { it.copy(zoomSize = null) }; exitTextView(); return }
        val target = (pinchStart * factor).roundToInt().coerceIn(MIN_SIZE, MAX_SIZE)
        val shown = _ui.value.zoomSize ?: pinchStart
        if (target == shown) {
            if ((factor > 1 && target == MAX_SIZE) || (factor < 1 && target == MIN_SIZE)) _hx.tryEmit(Hx.Edge)
            return
        }
        _hx.tryEmit(if (target == 18) Hx.DetentHome else Hx.Detent)
        _ui.update { it.copy(zoomSize = target) }
        // re-wrapping the whole chapter is costly: apply at most a few times a second while pinching
        val now = System.currentTimeMillis()
        if (now - lastApplied > 250) { lastApplied = now; app.settings.update { it.copy(size = target) } }
    }

    fun pinchEnd() {
        val size = _ui.value.zoomSize ?: return
        app.settings.update { it.copy(size = size) }
        viewModelScope.launch { delay(600); _ui.update { it.copy(zoomSize = null) } }
    }

    // ---------------- PDF text view ----------------

    private var pdfDoc: Doc.Pdf? = null
    private var pdfToc: List<TocEntry> = emptyList()
    private var pdfChapters: List<Int> = emptyList()
    private var entering = false
    private var noticeJob: Job? = null

    private fun notice(text: String?, forMs: Long = 2600) {
        noticeJob?.cancel()
        _ui.update { it.copy(notice = text) }
        if (text != null && forMs > 0) noticeJob = viewModelScope.launch { delay(forMs); _ui.update { it.copy(notice = null) } }
    }

    /** The PDF page the text view is showing (its resources are the PDF's pages, in order). */
    private fun textViewPage(): Int {
        val d = _doc.value as? Doc.Reflow ?: return _ui.value.page
        val r = lastLocator?.let { d.publication.readingOrder.indexOfFirstWithHref(it.href) } ?: return _ui.value.page
        return r + 1
    }

    /** Pinching in on a PDF page: show the page's text re-wrapped, at the reading size. */
    fun enterTextView() {
        val pdf = _doc.value as? Doc.Pdf ?: return
        if (entering) return
        entering = true
        hideChrome()
        val page = _ui.value.page
        val book = key?.let { library.book(it) }
        viewModelScope.launch {
            try {
                notice("Preparing text view…", 0)
                // books outside the library (opened from a file manager) are named by their uri, never shared
                val name = if (book != null) "${book.key}-${book.size}-${book.modified}"
                    else "ext-" + Book.keyOf(openedUri.toString()) + "-" + pdf.doc.pageCount
                val out = File(app.cacheDir, "pdftext/$name.epub")
                val file = withContext(Dispatchers.IO) {
                    PdfText.build(pdf.doc, _ui.value.title, _ui.value.author, _ui.value.toc, out) { p ->
                        _ui.update { it.copy(notice = "Preparing text view… ${(p * 100).toInt()}%") }
                    }
                }
                if (file == null) { notice("This PDF is scanned images with no text layer, so it can't be re-wrapped.", 3500); return@launch }
                val pub = withContext(Dispatchers.IO) { app.readium.retrieveFile(file) }
                pdfDoc = pdf; pdfToc = _ui.value.toc; pdfChapters = _ui.value.chapterPages
                scale = 1.0; measured = false; paging = null
                val base = withContext(Dispatchers.IO) { openReflow(pub, null) }
                val initial = pub.readingOrder.getOrNull(page - 1)?.let { pub.locatorFromLink(it) }
                _ui.update { it.copy(textView = true) }
                _doc.value = Doc.Reflow(base.publication, base.factory, base.positions, base.resourceStart, initial)
                notice("Text view. Pinch out or press Back for the page.")
            } catch (e: Exception) {
                Log.w(TAG, "text view failed", e)
                notice("Couldn't make a text view of this PDF.")
            } finally { entering = false }
        }
    }

    fun exitTextView() {
        val pdf = pdfDoc ?: return
        val text = _doc.value as? Doc.Reflow
        val page = textViewPage().coerceIn(1, pdf.doc.pageCount)
        scale = 1.0; paging = null
        rawToc = pdfToc; rawChapters = pdfChapters; positionCount = pdf.doc.pageCount
        quietUntilNextChange = true
        _ui.update {
            it.copy(textView = false, page = page, pageLast = page, pageCount = pdf.doc.pageCount, toc = pdfToc, chapterPages = pdfChapters,
                tocIndex = pdfToc.indexOfLast { e -> e.page != null && e.page <= page }.takeIf { i -> i >= 0 })
        }
        tts.stop()
        lastLocator = null
        _doc.value = Doc.Pdf(pdf.doc, page, pdf.trim)
        pdfDoc = null
        text?.publication?.close()
        notice(null)
    }

    // ---------------- chrome & panels ----------------

    fun toggleChrome() = _ui.update { it.copy(chrome = !it.chrome) }
    fun hideChrome() { if (_ui.value.chrome) _ui.update { it.copy(chrome = false) } }
    fun openPanel(p: Panel) = _ui.update { it.copy(panel = p) }
    fun closePanel() = _ui.update { it.copy(panel = Panel.None) }

    /** Read-aloud moved on: keep its sentence on screen, without a return mark or haptics. */
    fun follow(locator: Locator) {
        quietUntilNextChange = true
        _commands.tryEmit(Command.GoLocator(locator))
    }

    fun follow(page: Int) {
        if (page == _ui.value.page) return
        quietUntilNextChange = true
        _commands.tryEmit(Command.GoPage(page, false))
    }

    internal fun setSearch(s: SearchUi) = _ui.update { it.copy(search = s) }
    internal fun setTts(t: TtsState) = _ui.update { it.copy(tts = t) }
    internal fun emit(c: Command) = _commands.tryEmit(c)
    internal val currentLocator get() = lastLocator

    // ---------------- saving & reading time ----------------

    private var saveJob: Job? = null
    private var pendingSeconds = 0
    private var lastTouch = 0L
    private var active = false

    fun touch() { lastTouch = System.currentTimeMillis() }

    fun setActive(on: Boolean) {
        active = on
        if (!on) saveNow(lastLocator)
    }

    /** Counts reading time: 10 s ticks while the book is on screen and was touched in the last 3 minutes. */
    private fun startActivityClock() = viewModelScope.launch {
        touch()
        while (isActive) {
            delay(10_000)
            if (active && System.currentTimeMillis() - lastTouch < 180_000) pendingSeconds += 10
            if (pendingSeconds >= 60) saveNow(lastLocator)
        }
    }

    private fun scheduleSave(locator: Locator?) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch { delay(1200); saveNow(locator) }
    }

    private fun saveNow(locator: Locator?) {
        val k = key ?: return
        if (_doc.value == null) return
        val u = _ui.value
        val seconds = pendingSeconds.also { pendingSeconds = 0 }
        if (u.textView) {
            val pdf = pdfDoc ?: return
            val n = textViewPage()
            val count = pdf.doc.pageCount
            library.save(k, seconds) {
                it.copy(progress = if (count <= 1) 0f else (n - 1f) / (count - 1f), locator = """{"page":$n}""", page = n, pages = count,
                    finished = n >= count && count > 1)
            }
            return
        }
        val progress = when (_doc.value) {
            is Doc.Reflow -> locator?.locations?.totalProgression?.toFloat() ?: frac(u)
            else -> frac(u)
        }
        val top = u.topLevel
        val topIndex = top.indexOfLast { e -> e.page != null && e.page <= u.page }.takeIf { it >= 0 }
        library.save(k, seconds) {
            it.copy(
                progress = progress,
                locator = locator?.toJSON()?.toString() ?: if (_doc.value is Doc.Pdf) """{"page":${u.page}}""" else it.locator,
                page = u.page,
                pages = u.pageCount,
                chapter = topIndex?.let { i -> top[i].title } ?: u.chapter,
                chapterIndex = topIndex?.plus(1),
                chapters = top.size.takeIf { n -> n > 0 },
                finished = u.pageLast >= u.pageCount && u.pageCount > 1,
            )
        }
    }

    private fun frac(u: ReaderUi) = if (u.pageCount <= 1) 0f else (u.pageLast - 1f) / (u.pageCount - 1f)

    override fun onCleared() {
        saveNow(lastLocator)
        pdfDoc?.doc?.close()
        tts.release()
        (_doc.value as? Doc.Pdf)?.doc?.close()
        (_doc.value as? Doc.Reflow)?.publication?.close()
    }

    companion object { private const val TAG = "Reader" }
}

