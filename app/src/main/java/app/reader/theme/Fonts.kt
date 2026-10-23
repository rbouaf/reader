package app.reader.theme

import android.content.res.AssetManager
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/**
 * Every typeface ships in `assets/fonts/` so the same files serve Compose (UI, previews)
 * and the EPUB navigator (book text). Call [Fonts.init] once with the app's assets.
 */
object Fonts {
    private lateinit var assets: AssetManager
    fun init(a: AssetManager) { if (!::assets.isInitialized) assets = a }

    private val WEIGHTS = listOf(100, 200, 300, 400, 500, 600, 700, 800, 900)

    /** A variable font (one file per style) registered at each weight in [range]. */
    @OptIn(ExperimentalTextApi::class)
    private fun variable(file: String, italic: String?, range: IntRange, opsz: Float? = null): FontFamily {
        val fonts = buildList {
            for (w in WEIGHTS.filter { it in range }) {
                val settings = if (opsz != null) {
                    FontVariation.Settings(FontVariation.weight(w), FontVariation.Setting("opsz", opsz))
                } else FontVariation.Settings(FontVariation.weight(w))
                add(Font("fonts/$file", assets, FontWeight(w), FontStyle.Normal, settings))
                if (italic != null) add(Font("fonts/$italic", assets, FontWeight(w), FontStyle.Italic, settings))
            }
        }
        return FontFamily(fonts)
    }

    private fun statics(vararg faces: Triple<String, Int, Boolean>) = FontFamily(
        faces.map { (file, w, it) -> Font("fonts/$file", assets, FontWeight(w), if (it) FontStyle.Italic else FontStyle.Normal) },
    )

    // ---- UI faces ----
    val victorMono by lazy { variable("victor-mono.ttf", "victor-mono-italic.ttf", 100..700) }
    val bodoniDisplay by lazy { variable("bodoni-moda.ttf", "bodoni-moda-italic.ttf", 400..900, opsz = 72f) }
    val bodoniText by lazy { variable("bodoni-moda.ttf", "bodoni-moda-italic.ttf", 400..900, opsz = 11f) }
    val fragmentMono by lazy { statics(Triple("fragment-mono.ttf", 400, false), Triple("fragment-mono-italic.ttf", 400, true)) }
    val majorMono by lazy { statics(Triple("major-mono-display.ttf", 400, false)) }
    val azeretMono by lazy { variable("azeret-mono.ttf", "azeret-mono-italic.ttf", 100..900) }
    val instrumentSerif by lazy { statics(Triple("instrument-serif.ttf", 400, false), Triple("instrument-serif-italic.ttf", 400, true)) }
    val dmMono by lazy {
        statics(
            Triple("dm-mono-light.ttf", 300, false), Triple("dm-mono-light-italic.ttf", 300, true),
            Triple("dm-mono-regular.ttf", 400, false), Triple("dm-mono-italic.ttf", 400, true),
            Triple("dm-mono-medium.ttf", 500, false),
        )
    }
    val geist by lazy { variable("geist.ttf", "geist-italic.ttf", 100..900) }

    // ---- book faces ----
    private val bookFamilies = mutableMapOf<String, FontFamily>()

    /** Compose family for a reading face (settings swatches, previews, TXT fallbacks). */
    fun book(name: String): FontFamily = bookFamilies.getOrPut(name) {
        val face = BookFaces.byName(name) ?: return@getOrPut FontFamily.Serif
        when (val f = face.files) {
            is BookFiles.Variable -> variable(f.regular, f.italic, f.weights, f.opsz)
            is BookFiles.Static -> statics(*f.faces.map { Triple(it.file, it.weight, it.italic) }.toTypedArray())
        }
    }
}

sealed interface BookFiles {
    data class Variable(val regular: String, val italic: String, val weights: IntRange, val opsz: Float? = null) : BookFiles
    data class Face(val file: String, val weight: Int, val italic: Boolean)
    data class Static(val faces: List<Face>) : BookFiles
}

data class BookFace(val name: String, val files: BookFiles)

/** All reading faces any look offers. Each look picks four (see `Look.bookFonts`). */
object BookFaces {
    val all = listOf(
        BookFace("IBM Plex Sans", BookFiles.Variable("ibm-plex-sans.ttf", "ibm-plex-sans-italic.ttf", 100..700)),
        BookFace("Literata", BookFiles.Variable("literata.ttf", "literata-italic.ttf", 200..900, opsz = 12f)),
        BookFace("Atkinson Hyperlegible Next", BookFiles.Variable("atkinson.ttf", "atkinson-italic.ttf", 200..800)),
        BookFace("Victor Mono", BookFiles.Variable("victor-mono.ttf", "victor-mono-italic.ttf", 100..700)),
        BookFace("Newsreader", BookFiles.Variable("newsreader.ttf", "newsreader-italic.ttf", 200..800, opsz = 14f)),
        BookFace("Geist", BookFiles.Variable("geist.ttf", "geist-italic.ttf", 100..900)),
        BookFace("Azeret Mono", BookFiles.Variable("azeret-mono.ttf", "azeret-mono-italic.ttf", 100..900)),
        BookFace("Instrument Sans", BookFiles.Variable("instrument-sans.ttf", "instrument-sans-italic.ttf", 400..700)),
        BookFace(
            "Spectral",
            BookFiles.Static(
                listOf(
                    BookFiles.Face("spectral-regular.ttf", 400, false),
                    BookFiles.Face("spectral-italic.ttf", 400, true),
                    BookFiles.Face("spectral-semibold.ttf", 600, false),
                    BookFiles.Face("spectral-semibold-italic.ttf", 600, true),
                ),
            ),
        ),
    )

    fun byName(name: String) = all.firstOrNull { it.name == name }
}
