package app.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.reader.data.AppSettings
import app.reader.data.ThemeChoice
import app.reader.looks.ChromeActions
import app.reader.looks.LibraryActions
import app.reader.looks.Look
import app.reader.looks.Looks
import app.reader.looks.TtsState
import app.reader.looks.common.BookPreview
import app.reader.looks.common.Sample
import app.reader.looks.common.SettingsScreen
import app.reader.reader.ContentsPanel
import app.reader.reader.TocEntry
import app.reader.theme.LocalColors
import app.reader.theme.Mode
import app.reader.theme.ReaderTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Screenshots of every look on phone and tablet, in light, dark and black.
 *
 *   LOOKS=readout ./gw :app:recordRoborazziDebug --tests app.reader.LookShots
 *
 * PNGs land in app/screens/<look>/. Set SHOTS=library,settings,chrome,panel,empty to narrow it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LookShots {
    private val looks: List<Look> = System.getenv("LOOKS")?.takeIf { it.isNotBlank() }?.split(',')?.map { Looks.byId(it.trim()) } ?: Looks.all
    private val shots: Set<String> = System.getenv("SHOTS")?.takeIf { it.isNotBlank() }?.split(',')?.map { it.trim() }?.toSet()
        ?: setOf("library", "settings", "chrome", "panel", "empty")

    private data class Size(val name: String, val q: String)
    private val phone = Size("phone", "w412dp-h915dp-port-xxhdpi")
    private val land = Size("tablet-land", "w1280dp-h800dp-land-xhdpi")
    private val port = Size("tablet-port", "w800dp-h1280dp-port-xhdpi")

    private fun shoot(look: Look, size: Size, mode: Mode, name: String, warmth: Float = 0f, content: @Composable () -> Unit) {
        RuntimeEnvironment.setQualifiers(size.q)
        captureRoboImage("screens/${look.id}/$name-${size.name}-${mode.name.lowercase()}${if (warmth > 0) "-warm" else ""}.png") {
            ReaderTheme(look, mode, warmth) { content() }
        }
    }

    @Test fun all() {
        for (look in looks) {
            if ("library" in shots) {
                for (m in Mode.entries) shoot(look, phone, m, "library") { look.Library(Sample.library(), LibraryActions()) }
                shoot(look, phone, look.defaultMode, "library", warmth = 0.6f) { look.Library(Sample.library(), LibraryActions()) }
                shoot(look, land, look.defaultMode, "library") { look.Library(Sample.library(), LibraryActions()) }
                shoot(look, port, look.defaultMode, "library") { look.Library(Sample.library(), LibraryActions()) }
                val other = if (look.defaultMode == Mode.Light) Mode.Dark else Mode.Light
                shoot(look, land, other, "library") { look.Library(Sample.library(), LibraryActions()) }
            }
            if ("empty" in shots) {
                shoot(look, phone, look.defaultMode, "nofolder") { look.Library(Sample.noFolder, LibraryActions()) }
                shoot(look, phone, look.defaultMode, "emptyfolder") { look.Library(Sample.emptyLibrary, LibraryActions()) }
            }
            if ("settings" in shots) {
                val s = AppSettings(look = look.id, theme = null)
                for (size in listOf(phone, land, port)) shoot(look, size, look.defaultMode, "settings") {
                    SettingsScreen(s, false, "/storage/emulated/0/Books", {}, {}, {})
                }
                shoot(look, phone, Mode.Black, "settings") {
                    SettingsScreen(s.copy(theme = ThemeChoice.Black), false, "/storage/emulated/0/Books", {}, {}, {})
                }
            }
            if ("chrome" in shots) {
                for (size in listOf(phone, land)) for (m in listOf(look.defaultMode, Mode.Black)) shoot(look, size, m, "chrome") {
                    FakePage(); look.ReaderChrome(Sample.chrome(returnPage = 41), ChromeActions())
                }
                shoot(look, phone, look.defaultMode, "scrubbing") {
                    FakePage(); look.ReaderChrome(Sample.chrome(scrubbing = true, returnPage = 41), ChromeActions())
                }
                shoot(look, phone, look.defaultMode, "tts") {
                    FakePage()
                    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
                        look.kit.TtsBar(true, "III. Taoism and Zennism", {}, {}, {}, {})
                    }
                }
            }
            if ("panel" in shots) {
                val toc = listOf("I. The Cup of Humanity", "II. The Schools of Tea", "III. Taoism and Zennism", "IV. The Tea-Room", "V. Art Appreciation", "VI. Flowers", "VII. Tea-Masters")
                    .mapIndexed { i, t -> TocEntry(t, 0, i * 20 + 1, null) }
                for (size in listOf(phone, land)) shoot(look, size, look.defaultMode, "contents") {
                    FakePage(); ContentsPanel(toc, current = 2, onPick = {}, onClose = {})
                }
            }
        }
    }

    @Composable private fun FakePage() {
        Box(Modifier.fillMaxSize().background(LocalColors.current.paper).padding(top = 60.dp)) {
            BookPreview(AppSettings())
        }
    }
}
