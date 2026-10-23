package app.reader.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Reading state saved next to the books, in `<folder>/.reader/<bookKey>.<deviceId>.json`.
 *
 * Each device writes only its own file, so a folder synced by Syncthing (or anything else)
 * never sees two devices edit the same file. Reading merges them: the newest file wins for
 * the position, and reading activity adds up across devices.
 */
class Sidecars(
    private val resolver: ContentResolver,
    private val tree: Uri,
    private val deviceId: String,
) {
    @Serializable
    private data class File(
        val v: Int = 1,
        val path: String = "",
        val device: String = "",
        val state: ReadingState = ReadingState(),
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private var dirId: String? = null
    /** file name → document id inside `.reader`, filled by [readAll] and by writes. */
    private val ids = mutableMapOf<String, String>()
    /** This device's own activity per book: written back as-is so other devices' time isn't counted twice. */
    private val ownActivity = mutableMapOf<String, Map<String, Int>>()

    fun ownActivity(key: String): Map<String, Int> = ownActivity[key].orEmpty()

    private val rootId get() = DocumentsContract.getTreeDocumentId(tree)

    /** All books' merged states, keyed by book key. */
    fun readAll(): Map<String, ReadingState> {
        val dir = findDir() ?: return emptyMap()
        val perKey = mutableMapOf<String, MutableList<Pair<String, ReadingState>>>()
        children(dir) { id, name, _ ->
            ids[name] = id
            val m = NAME.matchEntire(name) ?: return@children
            val state = runCatching {
                resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, id))!!.use {
                    json.decodeFromString<File>(it.readBytes().decodeToString()).state
                }
            }.onFailure { Log.w(TAG, "unreadable sidecar $name", it) }.getOrNull() ?: return@children
            if (m.groupValues[2] == deviceId) ownActivity[m.groupValues[1]] = state.activity
            perKey.getOrPut(m.groupValues[1]) { mutableListOf() } += m.groupValues[2] to state
        }
        return perKey.mapValues { (_, states) -> merge(states) }
    }

    /** Writes this device's file. [state.activity] must be this device's own tally. */
    fun write(key: String, path: String, state: ReadingState) {
        ownActivity[key] = state.activity
        runCatching {
            val dir = findDir() ?: createDir() ?: return
            val name = "$key.$deviceId.json"
            val id = ids[name] ?: run {
                val parent = DocumentsContract.buildDocumentUriUsingTree(tree, dir)
                val created = DocumentsContract.createDocument(resolver, parent, "application/json", name) ?: return
                DocumentsContract.getDocumentId(created).also { ids[name] = it }
            }
            val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
            resolver.openOutputStream(uri, "wt")!!.use {
                it.write(json.encodeToString(File(path = path, device = deviceId, state = state)).toByteArray())
            }
        }.onFailure { Log.w(TAG, "could not write sidecar for $path", it) }
    }

    private fun findDir(): String? {
        dirId?.let { return it }
        children(rootId) { id, name, mime -> if (name == DIR && mime == Document.MIME_TYPE_DIR) dirId = id }
        return dirId
    }

    private fun createDir(): String? {
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
        val uri = DocumentsContract.createDocument(resolver, root, Document.MIME_TYPE_DIR, DIR) ?: return null
        return DocumentsContract.getDocumentId(uri).also { dirId = it }
    }

    private inline fun children(parentId: String, f: (id: String, name: String, mime: String) -> Unit) {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        resolver.query(uri, arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE), null, null, null)?.use { c ->
            while (c.moveToNext()) f(c.getString(0), c.getString(1) ?: "", c.getString(2) ?: "")
        }
    }

    /** Forgets this device's file for a book (after a rename moved it to a new key). */
    fun delete(key: String) {
        val id = ids.remove("$key.$deviceId.json") ?: return
        runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
        ownActivity.remove(key)
    }

    companion object {
        const val DIR = ".reader"
        private const val TAG = "Sidecars"
        private val NAME = Regex("""([0-9a-f]{16})\.(.+)\.json""")

        /** Newest device wins for position; activity sums per day across devices. */
        fun merge(states: List<Pair<String, ReadingState>>): ReadingState {
            val newest = states.maxBy { it.second.updatedAt }.second
            val activity = mutableMapOf<String, Int>()
            states.forEach { (_, s) -> s.activity.forEach { (d, sec) -> activity[d] = (activity[d] ?: 0) + sec } }
            return newest.copy(
                activity = activity,
                openedAt = states.mapNotNull { it.second.openedAt }.maxOrNull(),
                meta = states.mapNotNull { it.second.meta }.maxByOrNull { it.at },
                formerKeys = states.flatMap { it.second.formerKeys }.distinct(),
            )
        }
    }
}
