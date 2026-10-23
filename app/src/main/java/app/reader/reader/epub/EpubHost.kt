package app.reader.reader.epub

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.graphics.toArgb
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.reader.data.PageAnim
import app.reader.data.Spread
import app.reader.reader.Doc
import app.reader.reader.ReaderViewModel
import app.reader.theme.BookFaces
import app.reader.theme.BookFiles
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.css.FontStyle
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.ImageFilter
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.AbsoluteUrl

/** How the reflowable text should look; set by the book screen from the look and settings. */
data class ReadingStyle(
    val paper: androidx.compose.ui.graphics.Color,
    val ink: androidx.compose.ui.graphics.Color,
    val highlight: androidx.compose.ui.graphics.Color,
    val dark: Boolean,
    val font: String,
    val size: Int,
    val leading: Float,
    val margins: Double,
    val justify: Boolean,
    val hyphens: Boolean,
    val spread: Spread,
    val anim: PageAnim,
    val forwardOnRight: Boolean,
)

@OptIn(ExperimentalReadiumApi::class)
fun ReadingStyle.toPreferences() = EpubPreferences(
    backgroundColor = Color(paper.toArgb()),
    textColor = Color(ink.toArgb()),
    fontFamily = FontFamily(font),
    fontSize = size / 16.0,
    lineHeight = leading.toDouble(),
    pageMargins = margins,
    textAlign = if (justify) TextAlign.JUSTIFY else TextAlign.START,
    hyphens = hyphens,
    columnCount = when (spread) { Spread.Auto -> ColumnCount.AUTO; Spread.One -> ColumnCount.ONE; Spread.Two -> ColumnCount.TWO },
    // Readium only filters images in its dark theme; our colours still override the theme's
    theme = if (dark) Theme.DARK else Theme.LIGHT,
    imageFilter = if (dark) ImageFilter.DARKEN else null,
    publisherStyles = false,
    scroll = false,
)

/** The latest reading style, shared between the Compose screen and the navigator host. */
object ReadingStyles { val current = MutableStateFlow<ReadingStyle?>(null) }

/**
 * Hosts Readium's EPUB navigator (for EPUB, and TXT/MD converted to EPUB). Taps go through
 * our own zones so page turns, the controls and haptics behave the same as for PDFs.
 */
@OptIn(ExperimentalReadiumApi::class)
class EpubHost : Fragment() {
    private val vm: ReaderViewModel by activityViewModels()
    private var navigator: EpubNavigatorFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val d = vm.doc.value as? Doc.Reflow
        if (d == null) {
            childFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
            super.onCreate(savedInstanceState)
            requireActivity().finish()
            return
        }
        val style = ReadingStyles.current.value
        childFragmentManager.fragmentFactory = d.factory.createFragmentFactory(
            initialLocator = vm.currentLocator ?: d.initial,
            initialPreferences = style?.toPreferences() ?: EpubPreferences(),
            listener = object : EpubNavigatorFragment.Listener {
                override fun onExternalLinkActivated(url: AbsoluteUrl) {
                    runCatching { startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url.toString()))) }
                }
            },
            configuration = EpubNavigatorFragment.Configuration {
                servedAssets = listOf("fonts/.*")
                for (face in BookFaces.all) addFontFamilyDeclaration(FontFamily(face.name)) {
                    when (val f = face.files) {
                        is BookFiles.Variable -> {
                            addFontFace { addSource("fonts/${f.regular}"); setFontStyle(FontStyle.NORMAL); setFontWeight(f.weights.first..f.weights.last) }
                            addFontFace { addSource("fonts/${f.italic}"); setFontStyle(FontStyle.ITALIC); setFontWeight(f.weights.first..f.weights.last) }
                        }
                        is BookFiles.Static -> f.faces.forEach { s ->
                            addFontFace {
                                addSource("fonts/${s.file}")
                                setFontStyle(if (s.italic) FontStyle.ITALIC else FontStyle.NORMAL)
                                setFontWeight(s.weight..s.weight)
                            }
                        }
                    }
                }
            },
        )
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = FragmentContainerView(inflater.context).apply { id = View.generateViewId() }
        if (vm.doc.value !is Doc.Reflow) return view
        childFragmentManager.commitNow { add(view.id, EpubNavigatorFragment::class.java, Bundle(), TAG) }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val nav = childFragmentManager.findFragmentByTag(TAG) as? EpubNavigatorFragment ?: return
        navigator = nav
        vm.tts.firstVisible = { nav.firstVisibleElementLocator() }

        nav.addInputListener(object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                val w = view.width.takeIf { it > 0 } ?: return false
                val fx = event.point.x / w
                val style = ReadingStyles.current.value
                val fwdRight = style?.forwardOnRight ?: true
                when {
                    vm.ui.value.chrome -> vm.hideChrome()
                    fx < 0.3f -> vm.turn(!fwdRight)
                    fx > 0.7f -> vm.turn(fwdRight)
                    else -> vm.toggleChrome()
                }
                return true
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { nav.currentLocator.collect { vm.onLocator(it) } }
                launch { ReadingStyles.current.filterNotNull().collect { nav.submitPreferences(it.toPreferences()) } }
                launch {
                    vm.commands.collect { c ->
                        val animated = ReadingStyles.current.value?.anim == PageAnim.Slide
                        when (c) {
                            is ReaderViewModel.Command.Turn -> {
                                val ok = if (c.forward) nav.goForward(animated) else nav.goBackward(animated)
                                if (!ok) vm.onEdge()
                            }
                            is ReaderViewModel.Command.GoPage -> (vm.doc.value as? Doc.Reflow)?.positions?.getOrNull(c.page - 1)?.let { nav.go(it, false) }
                            is ReaderViewModel.Command.GoLocator -> nav.go(c.locator, false)
                        }
                    }
                }
                launch {
                    combine(vm.tts.utterance, vm.search.highlight, ReadingStyles.current) { u, h, s -> Triple(u, h, s) }.collect { (u, h, s) ->
                        val tint = (s?.highlight ?: androidx.compose.ui.graphics.Color(0x66FFD54F)).toArgb()
                        nav.applyDecorations(listOfNotNull(u?.let { Decoration("tts", it, Decoration.Style.Highlight(tint)) }), "tts")
                        nav.applyDecorations(listOfNotNull((h?.target as? Locator)?.let { Decoration("hit", it, Decoration.Style.Underline(tint)) }), "search")
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        navigator = null
        vm.tts.firstVisible = null
        super.onDestroyView()
    }

    companion object { private const val TAG = "navigator" }
}

