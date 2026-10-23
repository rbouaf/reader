package app.reader.reader.pdf

import app.reader.data.TextToEpub
import app.reader.reader.TocEntry
import java.io.File

/**
 * The text view of a PDF: each page's text layer, re-joined into paragraphs, one XHTML file
 * per page so page N of the PDF is resource N-1 of the text edition. Columns, tables and
 * figures don't survive; scanned pages have no text at all.
 */
object PdfText {
    /** Builds (or reuses) the text edition. Returns null if the PDF has (almost) no text. */
    suspend fun build(doc: PdfDoc, title: String, author: String?, toc: List<TocEntry>, out: File, progress: (Float) -> Unit): File? {
        if (out.exists()) return out
        val pages = mutableListOf<String>()
        var chars = 0
        for (i in 0 until doc.pageCount) {
            val t = doc.text(i)
            chars += t.length
            pages += t
            if (i % 8 == 0) progress(i.toFloat() / doc.pageCount)
        }
        if (chars < doc.pageCount * 40) return null
        val tocByPage = toc.filter { it.page != null }.groupBy { it.page!! }
        val chapters = pages.mapIndexed { i, t ->
            val headings = tocByPage[i + 1].orEmpty()
            val html = buildString {
                headings.forEach { append("<h2>").append(TextToEpub.escape(it.title)).append("</h2>\n") }
                paragraphs(t).forEach { append("<p>").append(TextToEpub.escape(it)).append("</p>\n") }
                if (isEmpty()) append("<p></p>")
                append("<p style=\"text-align:center;opacity:.45;font-size:.8em\">— ${i + 1} —</p>")
            }
            TextToEpub.Chapter(headings.firstOrNull()?.title, html)
        }
        TextToEpub.pack(chapters, title, author, out)
        progress(1f)
        return out
    }

    /** Joins a page's lines into paragraphs: a short line that ends a sentence ends a paragraph. */
    fun paragraphs(text: String): List<String> {
        val lines = text.lines().map { it.trim() }
        val lengths = lines.filter { it.length > 20 }.map { it.length }.sorted()
        val typical = lengths.getOrNull(lengths.size * 3 / 4) ?: 60
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        fun flush() { if (cur.isNotBlank()) out += cur.toString().trim(); cur.clear() }
        for (l in lines) {
            if (l.isEmpty()) { flush(); continue }
            if (cur.endsWith("-") && l.firstOrNull()?.isLowerCase() == true) cur.setLength(cur.length - 1)
            else if (cur.isNotEmpty()) cur.append(' ')
            cur.append(l)
            if (l.length < typical * 0.7 && l.last() in ".!?:\"”’)") flush()
        }
        flush()
        return out
    }
}
