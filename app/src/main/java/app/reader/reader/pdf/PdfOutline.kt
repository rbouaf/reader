package app.reader.reader.pdf

import android.content.Context
import android.net.Uri
import android.util.Log
import app.reader.reader.TocEntry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode

/** A PDF's own table of contents (its outline, or "bookmarks"), read with PdfBox. */
object PdfOutline {
    fun read(context: Context, uri: Uri): List<TocEntry> = runCatching {
        PDFBoxResourceLoader.init(context.applicationContext)
        context.contentResolver.openInputStream(uri)!!.use { input ->
            PDDocument.load(input, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val outline = doc.documentCatalog.documentOutline ?: return@use emptyList()
                val pages = doc.pages
                val out = mutableListOf<TocEntry>()
                fun walk(node: PDOutlineNode, depth: Int) {
                    for (item in node.children()) {
                        val page = runCatching { item.findDestinationPage(doc)?.let { pages.indexOf(it) + 1 } }.getOrNull()?.takeIf { it > 0 }
                        val title = item.title?.trim().orEmpty()
                        if (title.isNotEmpty()) out += TocEntry(title, depth, page, page)
                        if (depth < 4) walk(item, depth + 1)
                    }
                }
                walk(outline, 0)
                out
            }
        }
    }.onFailure { Log.w("PdfOutline", "no outline", it) }.getOrDefault(emptyList())
}
