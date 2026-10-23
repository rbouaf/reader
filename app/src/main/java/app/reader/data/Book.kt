package app.reader.data

import android.net.Uri
import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.time.LocalDate

enum class Format(val label: String, vararg val extensions: String) {
    Epub("epub", "epub"),
    Pdf("pdf", "pdf"),
    Txt("txt", "txt", "text"),
    Md("md", "md", "markdown");

    /** EPUB, TXT and MD reflow through the same engine; PDF keeps its pages. */
    val reflows get() = this != Pdf

    companion object {
        fun of(fileName: String): Format? {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return entries.firstOrNull { ext in it.extensions }
        }
    }
}

/**
 * What a device knows about reading one book. Saved per device in the book folder
 * (see [Sidecars]) and merged across devices.
 */
@Serializable
data class ReadingState(
    val progress: Float = 0f,
    /** Readium Locator JSON for reflowable books, `{"page":N}` for PDFs. */
    val locator: String? = null,
    val openedAt: Long? = null,
    val finished: Boolean = false,
    val page: Int? = null,
    val pages: Int? = null,
    val chapter: String? = null,
    val chapterIndex: Int? = null,
    val chapters: Int? = null,
    /** Seconds read per day (ISO date). Feeds the activity traces. */
    val activity: Map<String, Int> = emptyMap(),
    val updatedAt: Long = 0,
    /** Title/author typed by the user (long-press → Edit details); newest edit wins across devices. */
    val meta: BookMeta? = null,
    /** Keys this book had before a rename, so positions saved under the old name still apply. */
    val formerKeys: List<String> = emptyList(),
)

@Serializable
data class BookMeta(val title: String? = null, val author: String? = null, val at: Long = 0)

@Immutable
data class Book(
    /** Stable across devices: derived from the path inside the library folder. */
    val key: String,
    val uri: Uri,
    /** Path relative to the library folder, e.g. `Fiction/Middlemarch.epub`. */
    val path: String,
    val format: Format,
    val title: String,
    val author: String?,
    val size: Long,
    val modified: Long,
    val state: ReadingState = ReadingState(),
    /** Title and author as found in the file, before any edits. */
    val fileTitle: String = title,
    val fileAuthor: String? = author,
) {
    val fileName get() = path.substringAfterLast('/')

    /** This book with the user's title/author edits applied (or removed, if cleared). */
    fun withEdits(): Book = copy(
        title = state.meta?.title?.takeIf { it.isNotBlank() } ?: fileTitle,
        author = state.meta?.author?.takeIf { it.isNotBlank() } ?: fileAuthor,
    )

    val progress get() = if (state.finished) 1f else state.progress.coerceIn(0f, 1f)
    val isFinished get() = state.finished || state.progress >= 0.999f
    val isNew get() = !isFinished && state.openedAt == null && state.progress <= 0f
    val isOpen get() = !isFinished && !isNew

    /** Seconds read on each of the last [days] days, oldest first. */
    fun activity(days: Int, today: LocalDate = LocalDate.now()): List<Int> =
        (days - 1 downTo 0).map { state.activity[today.minusDays(it.toLong()).toString()] ?: 0 }

    companion object {
        fun keyOf(path: String): String {
            val d = MessageDigest.getInstance("SHA-1").digest(path.lowercase().toByteArray())
            return d.take(8).joinToString("") { "%02x".format(it) }
        }
    }
}

private val ARTICLES = Regex("^(the|a|an) ", RegexOption.IGNORE_CASE)

fun List<Book>.sortedBy(sort: Sort): List<Book> = when (sort) {
    Sort.Recent -> sortedWith(
        compareByDescending<Book> { it.state.openedAt ?: 0L }.thenByDescending { it.modified },
    )
    Sort.Title -> sortedBy { it.title.replace(ARTICLES, "").lowercase() }
    Sort.Author -> sortedWith(compareBy<Book> { it.author == null }.thenBy { it.author?.lowercase() }.thenBy { it.title.lowercase() })
}

/** The book to continue: the most recently opened one that's started and not finished. */
fun List<Book>.current(): Book? = filter { it.isOpen }.maxByOrNull { it.state.openedAt ?: 0L }

fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 10 -> "%d MB".format(bytes / (1024 * 1024))
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    else -> "%d KB".format(maxOf(1, bytes / 1024))
}
