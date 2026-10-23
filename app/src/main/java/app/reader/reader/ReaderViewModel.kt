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
import app.reader.reader.pdf.PdfDoc
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
) {
    val chapter get() = tocIndex?.let { toc.getOrNull(it)?.title }
    /** Top-level chapters only, for "chapter 3 of 7". */
    val topLevel get() = toc.filter { it.depth == 0 }
}

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
                    _ui.update { it.copy(title = book?.title ?: pubTitle ?: title, canReadAloud = tts.supported(doc)) }
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
        val toc = flatten(publication.tableOfContents).mapNotNull { (link, depth) ->
            val idx = publication.readingOrder.indexOfFirstWithHref(link.url()) ?: return@mapNotNull null
            TocEntry(link.title?.trim()?.ifEmpty { null } ?: "Untitled", depth, starts.getOrNull(idx), link)
        }
        _ui.update {
            it.copy(
                loading = false, pageCount = positions.size.coerceAtLeast(1), toc = toc,
                chapterPages = toc.filter { e -> e.depth == 0 }.mapNotNull { e -> e.page }.distinct().sorted()
                    .ifEmpty { toc.mapNotNull { e -> e.page }.distinct().sorted() },
            )
        }
        return Doc.Reflow(publication, EpubNavigatorFactory(publication), positions, starts, initial)
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
        return Doc.Pdf(doc, page.coerceIn(1, doc.pageCount), trim)
    }

    // ---------------- position ----------------

    /** From the EPUB navigator whenever the visible location changes. */
    fun onLocator(locator: Locator) {
        val d = _doc.value as? Doc.Reflow ?: return
        lastLocator = locator
        val page = pageOf(d, locator)
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
        tts.release()
        (_doc.value as? Doc.Pdf)?.doc?.close()
        (_doc.value as? Doc.Reflow)?.publication?.close()
    }

    companion object { private const val TAG = "Reader" }
}

