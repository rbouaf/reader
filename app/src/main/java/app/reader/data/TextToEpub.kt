package app.reader.data

import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.Heading
import org.commonmark.node.Node
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.io.File
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * TXT and Markdown become a small EPUB so they flow through the same engine as EPUBs:
 * same fonts, margins, pagination, search and read-aloud. Chapters come from headings
 * (Markdown) or chapter lines (text); long stretches are split so pages stay fast.
 */
object TextToEpub {
    /** One XHTML file. [anchors] are extra contents entries inside it (title, element id). */
    data class Chapter(val title: String?, val html: String, val anchors: List<Pair<String, String>> = emptyList())

    fun convert(bytes: ByteArray, format: Format, title: String, author: String?, out: File) {
        val text = TextDecoding.decode(bytes).replace("\r\n", "\n").replace('\r', '\n')
        val chapters = if (format == Format.Md) markdown(text) else plain(text)
        out.parentFile?.mkdirs()
        val tmp = File(out.path + ".tmp")
        tmp.outputStream().use { write(it, title, author, chapters) }
        tmp.renameTo(out)
    }

    // ---------------- markdown ----------------

    private val extensions = listOf(TablesExtension.create(), StrikethroughExtension.create())
    private val parser = Parser.builder().extensions(extensions).build()

    /** A renderer that gives headings ids h1, h2, … in document order, for contents links. */
    private fun renderer(ids: MutableMap<Node, String>) = HtmlRenderer.builder().extensions(extensions).escapeHtml(true)
        .attributeProviderFactory { org.commonmark.renderer.html.AttributeProvider { node, _, attrs -> ids[node]?.let { attrs["id"] = it } } }
        .build()

    fun markdown(source: String): List<Chapter> {
        val body = source.replace(Regex("\\A---\\n.*?\\n---\\n", RegexOption.DOT_MATCHES_ALL), "")
        val doc = parser.parse(body)
        val headings = generateSequence(doc.firstChild) { it.next }.filterIsInstance<Heading>().toList()
        val ids = headings.withIndex().associate { (i, h) -> h as Node to "h${i + 1}" }.toMutableMap()
        val renderer = renderer(ids)

        // Most notes fit in one flow: no forced page breaks, contents link to heading anchors.
        if (body.length < 60_000) {
            val anchors = headings.filter { it.level <= 3 }.map { plainText(it) to ids.getValue(it) }
            return listOf(Chapter(null, renderer.render(doc), anchors))
        }

        // Long documents split on the highest heading level used more than once.
        val levels = headings.groupingBy { it.level }.eachCount()
        val splitLevel = (1..3).firstOrNull { (levels[it] ?: 0) > 1 } ?: (1..3).firstOrNull { levels.containsKey(it) }
        val chapters = mutableListOf<Chapter>()
        var title: String? = null
        val part = StringBuilder()
        fun flush() {
            if (part.isNotBlank()) chapters += Chapter(title, part.toString())
            part.clear()
        }
        var n: Node? = doc.firstChild
        while (n != null) {
            val next = n.next
            if (n is Heading && n.level == splitLevel) {
                flush()
                title = plainText(n)
            }
            part.append(renderer.render(n))
            n = next
        }
        flush()
        return chapters.flatMap { split(it) }.ifEmpty { listOf(Chapter(null, "<p></p>")) }
    }

    private fun plainText(n: Node): String {
        val sb = StringBuilder()
        fun walk(x: Node?) {
            var c = x
            while (c != null) {
                if (c is org.commonmark.node.Text) sb.append(c.literal)
                if (c is org.commonmark.node.Code) sb.append(c.literal)
                walk(c.firstChild)
                c = c.next
            }
        }
        walk(n.firstChild)
        return sb.toString().trim()
    }

    // ---------------- plain text ----------------

    private val chapterLine = Regex(
        "^(?:(?:chapter|book|part|section|letter|canto)\\s+(?:[0-9]+|[ivxlcdm]+|[a-z]+)\\b.{0,60}|[ivxlcdm]{1,7}\\.?|[0-9]{1,3}\\.?)$",
        RegexOption.IGNORE_CASE,
    )

    fun plain(source: String): List<Chapter> {
        var text = source
        // Project Gutenberg's licence header and footer aren't part of the book
        Regex("\\*\\*\\* ?START OF (THE|THIS) PROJECT GUTENBERG.*?\\*\\*\\*", RegexOption.IGNORE_CASE).find(text)?.let { text = text.substring(it.range.last + 1) }
        Regex("\\*\\*\\* ?END OF (THE|THIS) PROJECT GUTENBERG", RegexOption.IGNORE_CASE).find(text)?.let { text = text.substring(0, it.range.first) }

        val paragraphs = text.split(Regex("\\n\\s*\\n")).map { it.trim('\n') }.filter { it.isNotBlank() }
        // Hard-wrapped text (lines all under ~80 chars) joins its lines; anything else keeps line breaks.
        val lines = paragraphs.flatMap { it.lines() }
        val wrapped = lines.size > 20 && lines.count { it.length in 50..80 } > lines.size * 0.5

        fun isHeadingLine(p: String) = p.trim().let { '\n' !in it && it.length <= 70 && chapterLine.matches(it) }
        // A heading only counts if real text follows before the next one: that skips the
        // book's own contents list ("CHAPTER 1. Loomings." … one per line).
        val heading = BooleanArray(paragraphs.size)
        val lastAt = mutableMapOf<String, Int>()
        paragraphs.forEachIndexed { i, p -> if (isHeadingLine(p)) lastAt[p.trim().lowercase()] = i }
        for (i in paragraphs.indices) {
            if (!isHeadingLine(paragraphs[i])) continue
            // the same line again later means this one is an entry in the book's own contents
            if (lastAt[paragraphs[i].trim().lowercase()] != i) continue
            var body = 0
            var j = i + 1
            while (j < paragraphs.size && !isHeadingLine(paragraphs[j]) && body < 400) { body += paragraphs[j].length; j++ }
            heading[i] = body >= 400
        }

        val chapters = mutableListOf<Chapter>()
        var title: String? = null
        val part = StringBuilder()
        fun flush() {
            if (part.isNotBlank()) chapters += Chapter(title, part.toString())
            part.clear()
        }
        for ((i, p) in paragraphs.withIndex()) {
            val oneLine = p.trim()
            if (heading[i]) {
                flush()
                title = oneLine
                part.append("<h2>").append(esc(oneLine)).append("</h2>\n")
                continue
            }
            val body = if (wrapped) esc(p.lines().joinToString(" ") { it.trim() }) else p.lines().joinToString("<br/>") { esc(it) }
            part.append("<p>").append(body).append("</p>\n")
        }
        flush()
        return chapters.flatMap { split(it) }.ifEmpty { listOf(Chapter(null, "<p></p>")) }
    }

    /** Keeps each XHTML file under ~120 KB by splitting at paragraph boundaries. */
    private fun split(c: Chapter, max: Int = 120_000): List<Chapter> {
        if (c.html.length <= max) return listOf(c)
        val out = mutableListOf<Chapter>()
        var start = 0
        while (start < c.html.length) {
            var end = minOf(c.html.length, start + max)
            if (end < c.html.length) {
                val cut = c.html.lastIndexOf("</p>", end)
                end = if (cut > start) cut + 4 else end
            }
            out += Chapter(if (out.isEmpty()) c.title else null, c.html.substring(start, end))
            start = end
        }
        return out
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    // ---------------- packaging ----------------

    /** Writes ready-made chapters (one XHTML file each) as an EPUB. */
    fun pack(chapters: List<Chapter>, title: String, author: String?, out: File) {
        out.parentFile?.mkdirs()
        val tmp = File(out.path + ".tmp")
        tmp.outputStream().use { write(it, title, author, chapters) }
        tmp.renameTo(out)
    }

    fun escape(s: String) = esc(s)

    private fun write(os: OutputStream, title: String, author: String?, chapters: List<Chapter>) {
        ZipOutputStream(os).use { zip ->
            // mimetype first and uncompressed, as the spec requires
            val mime = "application/epub+zip".toByteArray()
            zip.putNextEntry(ZipEntry("mimetype").apply {
                method = ZipEntry.STORED; size = mime.size.toLong(); compressedSize = mime.size.toLong()
                crc = CRC32().apply { update(mime) }.value
            })
            zip.write(mime); zip.closeEntry()

            fun put(name: String, s: String) { zip.putNextEntry(ZipEntry(name)); zip.write(s.toByteArray()); zip.closeEntry() }

            put("META-INF/container.xml", """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>""")

            val ids = chapters.indices.map { "c%04d".format(it) }
            put("OEBPS/content.opf", """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="id">reader-${title.hashCode()}</dc:identifier>
    <dc:title>${esc(title)}</dc:title>
    ${author?.let { "<dc:creator>${esc(it)}</dc:creator>" } ?: ""}
    <dc:language>en</dc:language>
    <meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>
  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    ${ids.joinToString("\n    ") { """<item id="$it" href="$it.xhtml" media-type="application/xhtml+xml"/>""" }}
  </manifest>
  <spine>
    ${ids.joinToString("\n    ") { """<itemref idref="$it"/>""" }}
  </spine>
</package>""")

            val toc = chapters.flatMapIndexed { i, c ->
                listOfNotNull(c.title?.let { """<li><a href="${ids[i]}.xhtml">${esc(it)}</a></li>""" }) +
                    c.anchors.map { (t, id) -> """<li><a href="${ids[i]}.xhtml#$id">${esc(t)}</a></li>""" }
            }
                .ifEmpty { listOf("""<li><a href="${ids[0]}.xhtml">${esc(title)}</a></li>""") }
            put("OEBPS/nav.xhtml", """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>${esc(title)}</title></head>
<body><nav epub:type="toc"><ol>${toc.joinToString("")}</ol></nav></body></html>""")

            chapters.forEachIndexed { i, c ->
                put("OEBPS/${ids[i]}.xhtml", """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>${esc(c.title ?: title)}</title>
<style>pre, code { white-space: pre-wrap; } table { border-collapse: collapse; } td, th { border: 1px solid currentColor; padding: .2em .4em; } blockquote { margin-left: 1.2em; opacity: .85; }</style>
</head><body>
${c.html}
</body></html>""")
            }
        }
    }
}
