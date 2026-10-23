package app.reader.looks.common

import android.net.Uri
import app.reader.data.Book
import app.reader.data.Format
import app.reader.data.ReadingState
import app.reader.data.Sort
import app.reader.data.current
import app.reader.data.sortedBy
import app.reader.looks.ChromeUi
import app.reader.looks.Counts
import app.reader.looks.LibraryUi
import app.reader.looks.TtsState
import java.time.LocalDate
import java.time.ZoneId

/**
 * The mockups' sample library (mockups/v8/content.js). Used by the Style picker's
 * thumbnails and by the screenshot tests.
 */
object Sample {
    private val today: LocalDate = LocalDate.of(2026, 10, 22)

    private fun at(daysAgo: Long, hour: Int = 9) =
        today.minusDays(daysAgo).atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** A plausible reading pattern for the activity traces. */
    private fun activity(seed: Int, daysAgo: Long, intensity: Int): Map<String, Int> {
        if (intensity == 0) return emptyMap()
        var h = seed * 2654435761L.toInt()
        val out = mutableMapOf<String, Int>()
        for (d in daysAgo until daysAgo + 28) {
            h = h * 1103515245 + 12345
            val r = (h ushr 16) and 0xFF
            if (r < 110) continue
            out[today.minusDays(d).toString()] = (r - 100) * intensity * 6
        }
        return out
    }

    private fun book(
        id: String, title: String, author: String, file: String, size: Long, progress: Float,
        openedDaysAgo: Long?, chapter: Pair<Int, String>? = null, chapters: Int? = null, pages: Int? = null,
    ): Book {
        val fmt = Format.of(file)!!
        val finished = progress >= 1f
        return Book(
            key = Book.keyOf(file),
            uri = Uri.parse("content://sample/$id"),
            path = file,
            format = fmt,
            title = title,
            author = author,
            size = size,
            modified = at(400),
            state = ReadingState(
                progress = progress,
                openedAt = openedDaysAgo?.let { at(it, if (it == 0L) 8 else 21) },
                finished = finished,
                page = pages?.let { maxOf(1, (progress * it).toInt()) },
                pages = pages,
                chapter = chapter?.second,
                chapterIndex = chapter?.first,
                chapters = chapters,
                activity = activity(id.hashCode(), openedDaysAgo ?: 0, if (openedDaysAgo == null) 0 else if (finished) 1 else 3),
            ),
        )
    }

    private const val KB = 1024L
    private const val MB = 1024L * 1024

    val books: List<Book> = listOf(
        book("tea", "The Book of Tea", "Okakura Kakuzō", "the-book-of-tea.epub", 412 * KB, 0.41f, 0, 3 to "Taoism and Zennism", 7, 140),
        book("moby", "Moby-Dick; or, The Whale", "Herman Melville", "moby-dick.epub", 1331 * KB, 0.33f, 1, 45 to "The Affidavit", 135, 912),
        book("walden", "Walden", "Henry David Thoreau", "walden-1854-facsimile.pdf", 38 * MB, 0.12f, 3, null, null, 357),
        book("medit", "Meditations", "Marcus Aurelius", "meditations-long.epub", 640 * KB, 0.68f, 8),
        book("sicp", "Structure and Interpretation of Computer Programs", "Abelson & Sussman", "sicp-2e.pdf", 8090 * KB, 0.07f, 9),
        book("pillow", "The Pillow Book", "Sei Shōnagon", "pillow-book.epub", 890 * KB, 0.22f, 33),
        book("pattern", "A Pattern Language", "Christopher Alexander", "a-pattern-language.pdf", 96 * MB, 0.15f, 36),
        book("shadows", "In Praise of Shadows", "Jun’ichirō Tanizaki", "in-praise-of-shadows.pdf", 2150 * KB, 1f, 62),
        book("cities", "Invisible Cities", "Italo Calvino", "invisible-cities.epub", 520 * KB, 1f, 95),
        book("systems", "Thinking in Systems", "Donella H. Meadows", "thinking-in-systems.pdf", 4500 * KB, 0f, null),
        book("kokoro", "Kokoro", "Natsume Sōseki", "kokoro.md", 470 * KB, 0f, null),
        book("waves", "The Waves", "Virginia Woolf", "the-waves.txt", 610 * KB, 0f, null),
    )

    fun library(sort: Sort = Sort.Recent, books: List<Book> = this.books): LibraryUi {
        val cur = books.current()
        return LibraryUi(
            folder = "/storage/emulated/0/Books",
            current = cur,
            others = books.filter { it != cur }.sortedBy(sort),
            sort = sort,
            counts = Counts.of(books),
            scanning = false,
            loaded = true,
            error = null,
        )
    }

    val emptyLibrary = LibraryUi("/storage/emulated/0/Books", null, emptyList(), Sort.Recent, Counts(), false, true, null)
    val noFolder = LibraryUi(null, null, emptyList(), Sort.Recent, Counts(), false, true, null)

    fun chrome(tts: TtsState = TtsState.Off, scrubbing: Boolean = false, returnPage: Int? = null) = ChromeUi(
        title = "The Book of Tea",
        author = "Okakura Kakuzō",
        format = Format.Epub,
        chapter = "III. Taoism and Zennism",
        chapterIndex = 3,
        chapterCount = 7,
        page = 58,
        pageLast = 58,
        pageCount = 140,
        chapterPages = listOf(1, 22, 47, 71, 92, 110, 127),
        returnPage = returnPage,
        scrubbing = scrubbing,
        activity = books.first().activity(28, today),
        tts = tts,
        canSearch = true,
        canReadAloud = true,
    )

    /** The settings preview text. */
    const val previewHeading = "I. The Cup of Humanity"
    val previewParagraphs = listOf(
        "Tea began as a medicine and grew into a beverage. In China, in the eighth century, it entered the realm of poetry as one of the polite amusements. The fifteenth century saw Japan ennoble it into a religion of aestheticism—Teaism.",
        "Teaism is a cult founded on the adoration of the beautiful among the sordid facts of everyday existence. It inculcates purity and harmony, the mystery of mutual charity, the romanticism of the social order.",
        "It is essentially a worship of the Imperfect, as it is a tender attempt to accomplish something possible in this impossible thing we know as life.",
    )
}
