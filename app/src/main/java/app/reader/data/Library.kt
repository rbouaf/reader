package app.reader.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.Log
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Immutable
data class LibraryState(
    val folder: Uri? = null,
    /** Human path, e.g. `/storage/emulated/0/Books`. */
    val folderLabel: String = "",
    val books: List<Book> = emptyList(),
    val scanning: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
)

/**
 * The library is one folder (and its subfolders). Metadata is cached by uri + size + mtime;
 * reading state comes from the sidecar files in the folder, so it follows the folder's sync.
 */
class LibraryRepository(
    private val context: Context,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private val resolver get() = context.contentResolver
    private val cacheFile = File(context.filesDir, "library-cache.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var sidecars: Sidecars? = null
    private var scanJob: Job? = null

    @Serializable
    private data class Meta(val size: Long, val modified: Long, val title: String, val author: String? = null)

    init {
        settings.current.folder?.let { Uri.parse(it) }?.let { uri ->
            _state.value = LibraryState(folder = uri, folderLabel = labelOf(uri))
            scan()
        } ?: run { _state.value = LibraryState(loaded = true) }
    }

    fun setFolder(uri: Uri) {
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        // Let go of the old folder's grant: the app only ever uses one.
        resolver.persistedUriPermissions.filter { it.uri != uri }.forEach {
            runCatching { resolver.releasePersistableUriPermission(it.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        }
        settings.update { it.copy(folder = uri.toString()) }
        _state.value = LibraryState(folder = uri, folderLabel = labelOf(uri))
        scan()
    }

    fun scan() {
        val tree = _state.value.folder ?: return
        scanJob?.cancel()
        scanJob = scope.launch(Dispatchers.IO) {
            _state.update { it.copy(scanning = true, error = null) }
            try {
                val books = mutex.withLock { load(tree) }
                _state.update { it.copy(books = books, scanning = false, loaded = true) }
            } catch (e: SecurityException) {
                _state.update { it.copy(scanning = false, loaded = true, error = "Lost access to this folder. Choose it again in Settings.") }
            } catch (e: Exception) {
                Log.w(TAG, "scan failed", e)
                _state.update { it.copy(scanning = false, loaded = true, error = e.message) }
            }
        }
    }

    fun book(key: String): Book? = _state.value.books.firstOrNull { it.key == key }

    /**
     * Saves a book's reading state, in memory and in the folder. [seconds] is reading time
     * to add to today's tally for this device.
     */
    fun save(key: String, seconds: Int = 0, f: (ReadingState) -> ReadingState) {
        val book = book(key) ?: return
        val sc = sidecars ?: return
        val today = java.time.LocalDate.now().toString()
        val own = sc.ownActivity(key).toMutableMap()
        if (seconds > 0) own[today] = (own[today] ?: 0) + seconds
        val merged = book.state.activity.toMutableMap()
        if (seconds > 0) merged[today] = (merged[today] ?: 0) + seconds
        val next = f(book.state).copy(updatedAt = System.currentTimeMillis())
        _state.update { s -> s.copy(books = s.books.map { if (it.key == key) it.copy(state = next.copy(activity = merged)) else it }) }
        scope.launch(Dispatchers.IO) { mutex.withLock { sc.write(key, book.path, next.copy(activity = own)) } }
    }

    fun markFinished(key: String) = save(key) { it.copy(finished = true, progress = 1f) }
    fun resetProgress(key: String) = save(key) { ReadingState(activity = it.activity) }

    // ---------------- scanning ----------------

    private data class Entry(val uri: Uri, val path: String, val size: Long, val modified: Long, val format: Format)

    private fun load(tree: Uri): List<Book> {
        val entries = mutableListOf<Entry>()
        walk(tree, DocumentsContract.getTreeDocumentId(tree), "", entries)

        val cache: MutableMap<String, Meta> = runCatching {
            json.decodeFromString<Map<String, Meta>>(cacheFile.readText()).toMutableMap()
        }.getOrDefault(mutableMapOf())

        val sc = Sidecars(resolver, tree, settings.current.deviceId).also { sidecars = it }
        val states = sc.readAll()

        val books = entries.map { e ->
            val k = e.uri.toString()
            val meta = cache[k]?.takeIf { it.size == e.size && it.modified == e.modified }
                ?: extract(e).also { cache[k] = it }
            val key = Book.keyOf(e.path)
            Book(key, e.uri, e.path, e.format, meta.title, meta.author, e.size, e.modified, states[key] ?: ReadingState())
        }
        runCatching { cacheFile.writeText(json.encodeToString(cache.filterKeys { k -> entries.any { it.uri.toString() == k } })) }
        return books
    }

    private fun walk(tree: Uri, parentId: String, prefix: String, out: MutableList<Entry>) {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val cols = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED)
        val dirs = mutableListOf<Pair<String, String>>()
        resolver.query(uri, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                val name = c.getString(1) ?: continue
                if (name.startsWith(".")) continue // .reader, .stfolder, .stversions, …
                if (c.getString(2) == Document.MIME_TYPE_DIR) { dirs += id to name; continue }
                val format = Format.of(name) ?: continue
                out += Entry(DocumentsContract.buildDocumentUriUsingTree(tree, id), prefix + name, c.getLong(3), c.getLong(4), format)
            }
        }
        dirs.forEach { (id, name) -> walk(tree, id, "$prefix$name/", out) }
    }

    private fun extract(e: Entry): Meta {
        val fromName = Metadata.fromFileName(e.path.substringAfterLast('/'))
        val found = runCatching {
            when (e.format) {
                Format.Epub -> resolver.openInputStream(e.uri)?.use { Metadata.epub(it) }
                Format.Txt, Format.Md -> resolver.openInputStream(e.uri)?.use { Metadata.text(it, e.format) }
                Format.Pdf -> resolver.openFileDescriptor(e.uri, "r")?.use { pdfTitle(it) }
            }
        }.onFailure { Log.w(TAG, "metadata failed for ${e.path}", it) }.getOrNull()
        return Meta(
            size = e.size,
            modified = e.modified,
            title = found?.title?.takeIf { it.isNotBlank() } ?: fromName.title ?: e.path,
            author = found?.author?.takeIf { it.isNotBlank() } ?: fromName.author,
        )
    }

    private fun pdfTitle(fd: ParcelFileDescriptor): Metadata.Found? = Metadata.pdfInfo(fd)

    companion object {
        private const val TAG = "Library"

        /** `primary:Books/Fiction` → `/storage/emulated/0/Books/Fiction`. */
        fun labelOf(tree: Uri): String {
            val id = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return tree.toString()
            val (vol, path) = id.split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            val base = if (vol == "primary") "/storage/emulated/0" else "/storage/$vol"
            return if (path.isEmpty()) base else "$base/$path"
        }
    }
}
