package app.reader.looks

import androidx.compose.runtime.Immutable
import app.reader.data.Book
import app.reader.data.Format
import app.reader.data.Sort
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

// ---------------- library ----------------

@Immutable
data class LibraryUi(
    /** null until a folder is chosen: the look shows its "choose a folder" state. */
    val folder: String?,
    /** The book to continue, shown as the big record; not repeated in [others]. */
    val current: Book?,
    /** Every other book, already sorted by [sort]. */
    val others: List<Book>,
    val sort: Sort,
    val counts: Counts,
    val scanning: Boolean,
    /** False while the first scan runs (draw nothing or a quiet placeholder, not "empty"). */
    val loaded: Boolean,
    val error: String?,
) {
    val isEmpty get() = loaded && current == null && others.isEmpty()
    val all get() = listOfNotNull(current) + others
}

@Immutable
data class Counts(
    val total: Int = 0,
    val open: Int = 0,
    val new: Int = 0,
    val finished: Int = 0,
    val byFormat: Map<Format, Int> = emptyMap(),
    val bytes: Long = 0,
) {
    companion object {
        fun of(books: List<Book>) = Counts(
            total = books.size,
            open = books.count { it.isOpen },
            new = books.count { it.isNew },
            finished = books.count { it.isFinished },
            byFormat = books.groupingBy { it.format }.eachCount(),
            bytes = books.sumOf { it.size },
        )
    }
}

class LibraryActions(
    val open: (Book) -> Unit = {},
    /** Long-press: the caller shows the book sheet. Use `combinedClickable`, whose long-press haptic the app routes through its own. */
    val hold: (Book) -> Unit = {},
    val sort: (Sort) -> Unit = {},
    val settings: () -> Unit = {},
    val chooseFolder: () -> Unit = {},
)

// ---------------- reader chrome ----------------

enum class TtsState { Off, Playing, Paused }

@Immutable
data class ChromeUi(
    val title: String,
    val author: String?,
    val format: Format,
    /** Current chapter title, if the book has a table of contents. */
    val chapter: String?,
    /** 1-based index of the current chapter and how many there are. */
    val chapterIndex: Int?,
    val chapterCount: Int?,
    /** 1-based. With a two-page spread, [page]..[pageLast] are on screen. */
    val page: Int,
    val pageLast: Int,
    val pageCount: Int,
    /** 1-based pages where chapters start (for scrubber ticks). */
    val chapterPages: List<Int>,
    /** The page you were on before you started scrubbing or jumping, until you go back or move on. */
    val returnPage: Int?,
    val scrubbing: Boolean,
    /** Seconds read per day, the last 28 days, oldest first. */
    val activity: List<Int>,
    val tts: TtsState,
    val canSearch: Boolean,
    val canReadAloud: Boolean,
) {
    val progress get() = if (pageCount <= 1) 0f else (page - 1f) / (pageCount - 1f)
    fun fractionOf(p: Int) = if (pageCount <= 1) 0f else (p - 1f) / (pageCount - 1f)
}

class ChromeActions(
    val back: () -> Unit = {},
    val contents: () -> Unit = {},
    val search: () -> Unit = {},
    val readAloud: () -> Unit = {},
    /** Jump back to [ChromeUi.returnPage]. */
    val goBackToMark: () -> Unit = {},
    /** Scrubbing: see `Modifier.scrubber` in looks/common, which calls these. */
    val scrubStart: () -> Unit = {},
    val scrubTo: (page: Int) -> Unit = {},
    val scrubEnd: () -> Unit = {},
)

// ---------------- formatting shared by the looks ----------------

/** "This morning", "Yesterday", "Monday", "Last week", "September", "2023". */
fun openedLabel(millis: Long?, now: LocalDate = LocalDate.now()): String? {
    millis ?: return null
    val zone = ZoneId.systemDefault()
    val at = Instant.ofEpochMilli(millis).atZone(zone)
    val d = at.toLocalDate()
    val days = ChronoUnit.DAYS.between(d, now)
    return when {
        days <= 0L -> when (at.hour) { in 0..11 -> "This morning"; in 12..17 -> "This afternoon"; else -> "Tonight" }
        days == 1L -> "Yesterday"
        days < 7L -> d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        days < 14L -> "Last week"
        d.year == now.year || days < 300L -> d.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
        else -> d.year.toString()
    }
}

fun percent(book: Book) = (book.progress * 100).toInt()
