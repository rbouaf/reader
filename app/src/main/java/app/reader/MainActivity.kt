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

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) app.library.setFolder(uri)
    }

    ReaderTheme(look, settings) {
        AppChrome(settings) {
            val hx = LocalHaptics.current
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
                                hold = { b -> hx(Hx.LongPress); held = b },
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
                        SheetAction("Mark as finished") { hx(Hx.Confirm); app.library.markFinished(b.key); held = null },
                        SheetAction("Reset progress") { hx(Hx.Confirm); app.library.resetProgress(b.key); held = null },
                        SheetAction("Close", quiet = true) { held = null },
                    ),
                    onDismiss = { held = null },
                )
            }
        }
    }
}

