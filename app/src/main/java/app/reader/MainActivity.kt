package app.reader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.reader.data.Book
import app.reader.data.current
import app.reader.data.sortedBy
import app.reader.looks.Counts
import app.reader.looks.LibraryActions
import app.reader.looks.LibraryUi
import app.reader.looks.LocalLook
import app.reader.looks.Looks
import app.reader.looks.SheetAction
import app.reader.looks.FormField
import app.reader.looks.common.SettingsScreen
import app.reader.theme.Hx
import app.reader.theme.LocalHaptics
import app.reader.theme.ReaderTheme
import app.reader.ui.AppChrome
import kotlinx.coroutines.launch

/** The library and its settings. Books open in [ReaderActivity]. */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Pick up files added or synced while we were away; positions written by the other device too.
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { app.library.scan() } }
        setContent { Root() }
    }
}

private enum class Screen { Library, Settings }

@Composable
private fun Root() {
    val context = LocalContext.current
    val app = context.app
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val lib by app.library.state.collectAsStateWithLifecycle()
    val look = Looks.byId(settings.look)
    var screen by rememberSaveable { mutableStateOf(Screen.Library) }
    var held by remember { mutableStateOf<Book?>(null) }
    var editing by remember { mutableStateOf<Book?>(null) }

    ReaderTheme(look, settings) {
        AppChrome(settings) {
            val hx = LocalHaptics.current
            val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
                if (uri != null) { hx(Hx.Confirm); app.library.setFolder(uri) }
            }
            BackHandler(screen == Screen.Settings) { screen = Screen.Library }
            AnimatedContent(screen to look.id, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { (s, _) ->
                val l = LocalLook.current
                when (s) {
                    Screen.Library -> {
                        val cur = lib.books.current()
                        val ui = LibraryUi(
                            folder = lib.folder?.let { lib.folderLabel },
                            current = cur,
                            others = lib.books.filter { it != cur }.sortedBy(settings.sort),
                            sort = settings.sort,
                            counts = Counts.of(lib.books),
                            scanning = lib.scanning,
                            loaded = lib.loaded,
                            error = lib.error,
                        )
                        l.Library(
                            ui,
                            LibraryActions(
                                open = { b -> context.startActivity(ReaderActivity.intent(context, b)) },
                                hold = { b -> held = b },
                                sort = { so -> if (so != settings.sort) { hx(Hx.Select); app.settings.update { it.copy(sort = so) } } },
                                settings = { screen = Screen.Settings },
                                chooseFolder = { pickFolder.launch(lib.folder) },
                            ),
                        )
                    }
                    Screen.Settings -> SettingsScreen(
                        settings = settings,
                        systemDark = isSystemInDarkTheme(),
                        folder = lib.folder?.let { lib.folderLabel },
                        update = app.settings::update,
                        onChooseFolder = { pickFolder.launch(lib.folder) },
                        onBack = { screen = Screen.Library },
                    )
                }
            }
            held?.let { b ->
                look.kit.Sheet(
                    title = b.title,
                    subtitle = b.path,
                    actions = listOf(
                        SheetAction("Open") { held = null; context.startActivity(ReaderActivity.intent(context, b)) },
                        SheetAction("Edit details") { held = null; editing = b },
                        SheetAction("Mark as finished") { hx(Hx.Confirm); app.library.markFinished(b.key); held = null },
                        SheetAction("Reset progress") { hx(Hx.Confirm); app.library.resetProgress(b.key); held = null },
                        SheetAction("Close", quiet = true) { held = null },
                    ),
                    onDismiss = { held = null },
                )
            }
            editing?.let { b -> EditDetails(b, onDone = { editing = null }) }
        }
    }
}


/** Long-press → Edit details: the shown title and author, and the file's actual name. */
@Composable
private fun EditDetails(b: Book, onDone: () -> Unit) {
    val app = LocalContext.current.app
    val kit = LocalLook.current.kit
    val hx = LocalHaptics.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var title by remember(b.key) { mutableStateOf(b.title) }
    var author by remember(b.key) { mutableStateOf(b.author.orEmpty()) }
    val base = b.fileName.substringBeforeLast('.')
    var name by remember(b.key) { mutableStateOf(base) }
    var error by remember(b.key) { mutableStateOf<String?>(null) }
    val ext = b.fileName.substringAfterLast('.', "")
    kit.Form(
        title = "Edit details",
        subtitle = error ?: b.path,
        fields = listOf(
            FormField("Title", title, "Leave empty to use the book's own title.") { title = it },
            FormField("Author", author, null) { author = it },
            FormField("File name", name, "Renames the file in your books folder${if (ext.isNotEmpty()) "; it stays .$ext" else ""}.") { name = it },
        ),
        saveLabel = "Save",
        onSave = {
            scope.launch {
                if (title != b.title || author != b.author.orEmpty()) {
                    val t = if (title.isBlank() || title == b.fileTitle) "" else title
                    val a = if (author.isBlank() || author == b.fileAuthor) "" else author
                    app.library.editDetails(b.key, t, a)
                }
                val err = if (name.trim() != base) app.library.rename(b.key, name) else null
                if (err == null) { hx(Hx.Confirm); onDone() } else { hx(Hx.Reject); error = err }
            }
        },
        onDismiss = onDone,
    )
}
