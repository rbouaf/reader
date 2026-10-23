package app.reader.data

import android.os.ParcelFileDescriptor
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.zip.ZipInputStream

/** Lightweight title/author extraction, run once per file and cached. */
object Metadata {
    data class Found(val title: String?, val author: String?)

    /** `Ursula K. Le Guin - The Dispossessed.epub`, `the_dispossessed.pdf`, … */
    fun fromFileName(name: String): Found {
        val base = name.substringBeforeLast('.').replace('_', ' ').replace(Regex("\\s+"), " ").trim()
        val parts = base.split(" - ", " – ", " — ", limit = 2)
        return if (parts.size == 2 && parts[0].length in 2..60) Found(pretty(parts[1]), parts[0].trim())
        else Found(pretty(base), null)
    }

    private fun pretty(s: String): String {
        val t = s.trim()
        // all-lowercase slugs like "the-book-of-tea" read better capitalised
        return if (t == t.lowercase() && ' ' !in t && '-' in t) t.split('-').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
        else if (t == t.lowercase()) t.split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
        else t
    }

    /** Streams the zip until it has the container and the package document. */
    fun epub(input: InputStream): Found? {
        val small = mutableMapOf<String, ByteArray>()
        var opfPath: String? = null
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val n = e.name
                if (n == "META-INF/container.xml" || n.endsWith(".opf", ignoreCase = true)) {
                    small[n] = zip.readBytes()
                    if (n == "META-INF/container.xml") opfPath = rootfile(small[n]!!)
                }
                val p = opfPath
                if (p != null && small.containsKey(p)) return opf(small[p]!!)
            }
        }
        val p = opfPath ?: small.keys.firstOrNull { it.endsWith(".opf", true) } ?: return null
        return small[p]?.let(::opf)
    }

    private fun rootfile(xml: ByteArray): String? {
        val x = parser(xml)
        while (x.next() != XmlPullParser.END_DOCUMENT) {
            if (x.eventType == XmlPullParser.START_TAG && x.name == "rootfile") return x.getAttributeValue(null, "full-path")
        }
        return null
    }

    private fun opf(xml: ByteArray): Found {
        val x = parser(xml)
        var title: String? = null
        var author: String? = null
        while (x.next() != XmlPullParser.END_DOCUMENT) {
            if (x.eventType != XmlPullParser.START_TAG) continue
            val local = x.name.substringAfter(':')
            if (local == "title" && title == null) title = x.nextText().trim()
            else if (local == "creator" && author == null) author = x.nextText().trim()
            else if (local == "manifest") break
        }
        return Found(title, author)
    }

    private fun parser(xml: ByteArray): XmlPullParser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(xml.inputStream(), null)
    }

    /** Markdown: front matter or the first `# Heading`. Text: Project Gutenberg headers or a short first line. */
    fun text(input: InputStream, format: Format): Found {
        val head = TextDecoding.decode(input.readNBytes(8192)).lines().map { it.trim() }
        var title: String? = null
        var author: String? = null
        if (head.firstOrNull() == "---") {
            for (l in head.drop(1)) {
                if (l == "---") break
                Regex("^title:\\s*[\"']?(.+?)[\"']?$").find(l)?.let { title = it.groupValues[1] }
                Regex("^author:\\s*[\"']?(.+?)[\"']?$").find(l)?.let { author = it.groupValues[1] }
            }
        }
        for (l in head) {
            if (title == null) Regex("^Title:\\s*(.+)$").find(l)?.let { title = it.groupValues[1] }
            if (author == null) Regex("^Author:\\s*(.+)$").find(l)?.let { author = it.groupValues[1] }
        }
        if (title == null && format == Format.Md) title = head.firstOrNull { it.startsWith("# ") }?.removePrefix("# ")
        return Found(title, author)
    }

    /** Uncompressed Info dictionary or XMP packet near the start or end of the file. */
    fun pdfInfo(fd: ParcelFileDescriptor): Found? {
        FileInputStream(fd.fileDescriptor).channel.use { ch ->
            val size = ch.size()
            fun read(at: Long, n: Int): String {
                val b = ByteBuffer.allocate(minOf(n.toLong(), size - at).toInt().coerceAtLeast(0))
                ch.read(b, at)
                return String(b.array(), Charsets.ISO_8859_1)
            }
            val text = read(0, 65536) + read(maxOf(0, size - 65536), 65536)
            val xmpTitle = Regex("<dc:title>.*?<rdf:li[^>]*>(.*?)</rdf:li>", RegexOption.DOT_MATCHES_ALL).find(text)?.groupValues?.get(1)
            val xmpAuthor = Regex("<dc:creator>.*?<rdf:li[^>]*>(.*?)</rdf:li>", RegexOption.DOT_MATCHES_ALL).find(text)?.groupValues?.get(1)
            val title = xmpTitle?.let(::utf8) ?: pdfString(text, "Title")
            val author = xmpAuthor?.let(::utf8) ?: pdfString(text, "Author")
            return Found(title?.takeUnless { looksLikeJunk(it) }, author?.takeUnless { looksLikeJunk(it) })
        }
    }

    private fun utf8(s: String) = String(s.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8).trim()

    private fun pdfString(text: String, key: String): String? {
        val m = Regex("/$key\\s*\\(((?:\\\\.|[^\\\\)])*)\\)").find(text) ?: return null
        val raw = m.groupValues[1].replace(Regex("\\\\(.)")) { it.groupValues[1] }
        val bytes = raw.toByteArray(Charsets.ISO_8859_1)
        return if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE).trim()
        else raw.trim()
    }

    /** Producer leftovers like "Microsoft Word - draft3.doc" or "untitled". */
    private fun looksLikeJunk(s: String) = s.isBlank() || s.length > 200 ||
        Regex("^(untitled|microsoft word|document\\d*|unknown)", RegexOption.IGNORE_CASE).containsMatchIn(s) ||
        Regex("\\.(docx?|indd|pdf|tex|qxd)$", RegexOption.IGNORE_CASE).containsMatchIn(s)
}

/** UTF-8 if it decodes cleanly (BOMs included), else Windows-1252, which covers old Latin text files. */
object TextDecoding {
    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        val dec = Charsets.UTF_8.newDecoder()
        return try {
            dec.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: java.nio.charset.CharacterCodingException) {
            // a multibyte char cut off by a truncated read is still UTF-8
            val trimmed = (1..3).firstNotNullOfOrNull { n ->
                runCatching { Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes, 0, bytes.size - n)).toString() }.getOrNull()
            }
            trimmed ?: String(bytes, charset("windows-1252"))
        }
    }
}
