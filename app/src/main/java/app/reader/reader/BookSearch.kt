package app.reader.reader

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.search.search

/** A PDF hit: page (1-based) and the match rectangles in page points. */
data class PdfHit(val page: Int, val bounds: List<android.graphics.RectF>)

/** Finds words in the open book, streaming results into [ReaderUi.search]. */
@OptIn(ExperimentalReadiumApi::class)
class BookSearch(private val vm: ReaderViewModel, private val scope: CoroutineScope) {
    private var job: Job? = null
    private val _highlight = MutableStateFlow<SearchHit?>(null)
    /** The hit last jumped to, drawn on the page until the next search. */
    val highlight: StateFlow<SearchHit?> = _highlight.asStateFlow()

    fun query(q: String) = vm.setSearch(vm.ui.value.search.copy(query = q))

    fun run() {
        val q = vm.ui.value.search.query.trim()
        job?.cancel()
        _highlight.value = null
        if (q.length < 2) { vm.setSearch(SearchUi(query = vm.ui.value.search.query)); return }
        vm.setSearch(SearchUi(query = vm.ui.value.search.query, running = true))
        job = scope.launch {
            val hits = mutableListOf<SearchHit>()
            fun publish(done: Boolean) = vm.setSearch(SearchUi(vm.ui.value.search.query, running = !done, done = done, hits = hits.toList()))
            when (val d = vm.doc.value) {
                is Doc.Reflow -> {
                    val it = d.publication.search(q)
                    if (it == null) { publish(true); return@launch }
                    try {
                        while (hits.size < MAX) {
                            val batch = it.next().getOrNull() ?: break
                            batch.locators.forEach { l -> hits += hitOf(d, l) }
                            publish(false)
                        }
                    } finally { it.close() }
                }
                is Doc.Pdf -> for (i in 0 until d.doc.pageCount) {
                    val matches = d.doc.search(i, q)
                    if (matches.isEmpty()) continue
                    val text = d.doc.text(i)
                    matches.forEach { m -> hits += snippet(text, m.start, q.length, i + 1, PdfHit(i + 1, m.bounds)) }
                    publish(false)
                    if (hits.size >= MAX) break
                }
                null -> {}
            }
            publish(true)
        }
    }

    fun pick(hit: SearchHit) {
        _highlight.value = hit
        when (val t = hit.target) {
            is Locator -> vm.jumpTo(t)
            is PdfHit -> vm.jumpTo(t.page)
        }
        vm.closePanel()
        vm.hideChrome()
    }

    fun clear() { job?.cancel(); _highlight.value = null }

    private fun hitOf(d: Doc.Reflow, l: Locator): SearchHit {
        val page = l.locations.position ?: vm.pageFor(l)
        val chapter = vm.ui.value.toc.lastOrNull { e -> e.page != null && page != null && e.page <= page }?.title ?: l.title
        return SearchHit(
            before = l.text.before.orEmpty().squash().takeLast(40).trimStart(),
            match = l.text.highlight.orEmpty().squash(),
            after = l.text.after.orEmpty().squash().take(40).trimEnd(),
            chapter = chapter, page = page, target = l,
        )
    }

    private fun snippet(text: String, start: Int, len: Int, page: Int, target: PdfHit): SearchHit {
        if (start < 0 || start >= text.length) return SearchHit("", "", "", null, page, target)
        val end = (start + len).coerceAtMost(text.length)
        return SearchHit(
            before = text.substring((start - 40).coerceAtLeast(0), start).squash().trimStart(),
            match = text.substring(start, end),
            after = text.substring(end, (end + 40).coerceAtMost(text.length)).squash().trimEnd(),
            chapter = null, page = page, target = target,
        )
    }

    private fun String.squash() = replace(Regex("\\s+"), " ")

    companion object { const val MAX = 500 }
}

