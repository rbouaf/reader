package app.reader.reader.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import java.io.Closeable

/**
 * A PDF opened with the platform renderer. PdfRenderer allows one open page at a time,
 * so every call goes through a single-threaded dispatcher.
 */
class PdfDoc(private val fd: ParcelFileDescriptor) : Closeable {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(1)
    private val renderer = PdfRenderer(fd)
    val pageCount: Int = renderer.pageCount

    /** Page sizes in points, filled as pages are opened. Page 0's size stands in until then. */
    private val sizes = arrayOfNulls<android.util.Size>(pageCount)
    private val boxes = arrayOfNulls<RectF>(pageCount)
    private val texts = LruCache<Int, String>(64)
    @Volatile private var closed = false

    val firstSize: android.util.Size = page(0) { android.util.Size(it.width, it.height) }

    fun sizeOf(i: Int): android.util.Size = sizes[i] ?: firstSize

    private inline fun <T> page(i: Int, f: (PdfRenderer.Page) -> T): T = renderer.openPage(i).use { p ->
        if (sizes[i] == null) sizes[i] = android.util.Size(p.width, p.height)
        f(p)
    }

    /**
     * Renders page [i] at [widthPx] wide, showing only [crop] (fractions of the page), on white.
     */
    suspend fun render(i: Int, widthPx: Int, crop: RectF?): Bitmap? = withContext(io) {
        if (closed) return@withContext null
        page(i) { p ->
            val c = crop ?: FULL
            val cw = p.width * c.width()
            val ch = p.height * c.height()
            val scale = widthPx / cw
            val h = (ch * scale).toInt().coerceIn(1, 8192)
            val w = widthPx.coerceIn(1, 8192)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            val m = Matrix().apply {
                postTranslate(-p.width * c.left, -p.height * c.top)
                postScale(scale, scale)
            }
            p.render(bmp, Rect(0, 0, w, h), m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bmp
        }
    }

    /**
     * Where the ink is, as fractions of the page, from a small grey render. Used to trim
     * white margins. Blank or nearly full pages return the whole page.
     */
    suspend fun contentBox(i: Int): RectF = boxes[i] ?: withContext(io) {
        if (closed) return@withContext FULL
        val box = runCatching {
            page(i) { p ->
                val w = 200
                val h = (200f * p.height / p.width).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                p.render(bmp, Rect(0, 0, w, h), Matrix().apply { postScale(w / p.width.toFloat(), h / p.height.toFloat()) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val px = IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }
                bmp.recycle()
                inkBox(px, w, h)
            }
        }.getOrDefault(FULL)
        boxes[i] = box
        box
    }

    /**
     * One trim box for even pages and one for odd, each the union of a sample of pages,
     * so the zoom stays steady from page to page (a short last page doesn't jump in size).
     * The cover is left out of the sample.
     */
    suspend fun trimBoxes(): Pair<RectF, RectF> {
        val sample = if (pageCount <= 2) (0 until pageCount).toList()
        else (1 until pageCount).let { r -> if (r.count() <= 16) r.toList() else (0 until 16).map { 1 + it * (pageCount - 2) / 15 } }
        val unions = arrayOf<RectF?>(null, null)
        for (i in sample) {
            val b = contentBox(i)
            val u = unions[i % 2]
            if (u == null) unions[i % 2] = RectF(b) else u.union(b)
        }
        val even = unions[0] ?: unions[1] ?: FULL
        val odd = unions[1] ?: even
        return even to odd
    }

    /** The page's text in reading order, for read-aloud. Empty for scans. */
    suspend fun text(i: Int): String = texts[i] ?: withContext(io) {
        val t = runCatching { page(i) { p -> p.textContents.joinToString("\n") { it.text } } }
            .onFailure { Log.w(TAG, "no text API", it) }.getOrDefault("")
        texts.put(i, t)
        t
    }

    data class Match(val page: Int, val start: Int, val bounds: List<RectF>)

    suspend fun search(i: Int, query: String): List<Match> = withContext(io) {
        if (closed) return@withContext emptyList()
        runCatching { page(i) { p -> p.searchText(query).map { Match(i, it.textStartIndex, it.bounds) } } }
            .getOrDefault(emptyList())
    }

    override fun close() {
        closed = true
        runCatching { renderer.close() }
        runCatching { fd.close() }
    }

    companion object {
        private const val TAG = "PdfDoc"
        val FULL = RectF(0f, 0f, 1f, 1f)

        fun inkBox(px: IntArray, w: Int, h: Int): RectF {
            var l = w; var t = h; var r = -1; var b = -1
            for (y in 0 until h) for (x in 0 until w) {
                val c = px[y * w + x]
                val lum = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
                if (lum < 225) { if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y }
            }
            if (r < 0) return FULL
            val pad = 0.025f
            val box = RectF(
                (l.toFloat() / w - pad).coerceAtLeast(0f), (t.toFloat() / h - pad).coerceAtLeast(0f),
                ((r + 1f) / w + pad).coerceAtMost(1f), ((b + 1f) / h + pad).coerceAtMost(1f),
            )
            // only worth it when it removes a real margin; keep tiny boxes (a lone page number) whole
            return if (box.width() * box.height() > 0.92f || box.width() < 0.25f || box.height() < 0.2f) FULL else box
        }
    }
}
