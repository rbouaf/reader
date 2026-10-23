package app.reader.reader

import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import app.reader.looks.LocalLook

/** One contents entry. [page] is 1-based; [target] is whatever the engine needs to go there. */
@Immutable
data class TocEntry(val title: String, val depth: Int, val page: Int?, val target: Any?)

@Immutable
data class SearchHit(val before: String, val match: String, val after: String, val chapter: String?, val page: Int?, val target: Any?)

@Immutable
data class SearchUi(val query: String = "", val running: Boolean = false, val done: Boolean = false, val hits: List<SearchHit> = emptyList())

@Composable
fun ContentsPanel(entries: List<TocEntry>, current: Int?, onPick: (TocEntry) -> Unit, onClose: () -> Unit) {
    val kit = LocalLook.current.kit
    kit.Panel("Contents", onClose, null) {
        if (entries.isEmpty()) item { kit.PanelMessage("This book has no table of contents.") }
        itemsIndexed(entries) { i, e ->
            kit.PanelRow(e.title, null, e.page?.toString(), i == current, e.depth) { onPick(e) }
        }
    }
}

@Composable
fun SearchPanel(ui: SearchUi, onQuery: (String) -> Unit, onSubmit: () -> Unit, onPick: (SearchHit) -> Unit, onClose: () -> Unit) {
    val kit = LocalLook.current.kit
    kit.Panel(
        "Search", onClose,
        top = { kit.SearchField(ui.query, "words in this book", onQuery, onSubmit) },
    ) {
        when {
            ui.running && ui.hits.isEmpty() -> item { kit.PanelMessage("Searching…") }
            ui.done && ui.hits.isEmpty() && ui.query.isNotBlank() -> item { kit.PanelMessage("No results for “${ui.query}”.") }
            ui.hits.isNotEmpty() -> item {
                kit.PanelMessage(if (ui.running) "${ui.hits.size} results so far…" else "${ui.hits.size} result${if (ui.hits.size == 1) "" else "s"}")
            }
        }
        items(ui.hits) { h ->
            kit.PanelRow(
                primary = "…${h.before}${h.match}${h.after}…",
                secondary = h.chapter,
                trailing = h.page?.toString(),
                current = false,
                depth = 0,
            ) { onPick(h) }
        }
    }
}
