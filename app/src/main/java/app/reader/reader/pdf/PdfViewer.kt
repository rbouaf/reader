package app.reader.reader.pdf

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.reader.data.AppSettings
import app.reader.data.PageAnim
import app.reader.data.Spread
import app.reader.data.TapSide
import app.reader.reader.Doc
import app.reader.reader.PdfHit
import app.reader.reader.MENU_BAND_DP
import app.reader.reader.ReaderViewModel
import app.reader.theme.Hx
import app.reader.theme.Colors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

/**
 * Real PDF pages, recoloured to the look's paper and ink (and so to the warmth), with
 * white margins trimmed, pinch zoom, and two-page spreads on a sideways tablet.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PdfViewer(d: Doc.Pdf, vm: ReaderViewModel, settings: AppSettings, colors: Colors) {
    val doc = d.doc
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.paper)) {
        val boxW = maxWidth
        val boxH = maxHeight
        val two = when (settings.spread) {
            Spread.Auto -> maxWidth >= 880.dp && maxWidth > maxHeight * 1.15f
            Spread.Two -> maxWidth >= 560.dp
            Spread.One -> false
        }
        // Spreads: the cover alone, then 2–3, 4–5, … like a printed book.
        val spreads = remember(two, doc.pageCount) {
            if (!two) (1..doc.pageCount).map { listOf(it) }
            else listOf(listOf(1)) + (2..doc.pageCount step 2).map { p -> listOfNotNull(p, (p + 1).takeIf { it <= doc.pageCount }) }
        }
        fun spreadOf(page: Int) = spreads.indexOfFirst { page in it }.coerceAtLeast(0)

        val pager = rememberPagerState(initialPage = spreadOf(vm.ui.value.page)) { spreads.size }
        var zoom by remember { mutableFloatStateOf(1f) }
        var pan by remember { mutableStateOf(Offset.Zero) }
        val s by rememberUpdatedState(settings)

        LaunchedEffect(pager, spreads) {
            snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { i ->
                val sp = spreads.getOrNull(i) ?: return@collect
                zoom = 1f; pan = Offset.Zero
                vm.onPdfPage(sp.first(), sp.last())
            }
        }
        LaunchedEffect(pager, spreads) {
            vm.commands.collect { c ->
                when (c) {
                    is ReaderViewModel.Command.GoPage -> pager.scrollToPage(spreadOf(c.page))
                    is ReaderViewModel.Command.Turn -> {
                        val target = pager.currentPage + if (c.forward) 1 else -1
                        if (target !in spreads.indices) vm.onEdge()
                        else if (s.anim == PageAnim.Slide) pager.animateScrollToPage(target) else pager.scrollToPage(target)
                    }
                    is ReaderViewModel.Command.GoLocator -> {}
                }
            }
        }

        val hit by vm.search.highlight.collectAsState()
        val filter = remember(colors, settings.pdfRecolor) { if (settings.pdfRecolor) recolour(colors.paper, colors.ink) else null }

        HorizontalPager(
            state = pager,
            beyondViewportPageCount = 1,
            userScrollEnabled = zoom <= 1.01f,
            modifier = Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        if (zoom > 1.01f) { if (vm.ui.value.chrome) vm.hideChrome() else vm.toggleChrome(); return@detectTapGestures }
                        vm.tap(p.x, p.y, size.width.toFloat(), size.height.toFloat(), MENU_BAND_DP.dp.toPx(), s.tapSide == TapSide.Right)
                    }
                }
                .pointerInput(Unit) {
                    zoomGestures({ zoom }, { z, p -> zoom = z; pan = p }, { pan }, vm::hx, vm::onUserInput) {
                        // text wrap zoom: pinching in from the whole page opens the re-wrapped text instead
                        if (s.wrapZoom) { vm.enterTextView(); true } else false
                    }
                },
        ) { index ->
            val pages = spreads[index]
            val current = index == pager.currentPage
            Row(
                Modifier.fillMaxSize().graphicsLayer {
                    if (current) { scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y }
                },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val pad = if (minOf(boxW, boxH) < 520.dp) 6.dp else 20.dp
                pages.forEach { p ->
                    PdfPage(
                        doc = doc, page = p,
                        crop = if (s.pdfTrim) (if (p % 2 == 1) d.trim.first else d.trim.second) else PdfDoc.FULL,
                        boxW = (boxW - pad * 2) / pages.size, boxH = boxH - pad * 2,
                        zoom = if (current) zoom else 1f,
                        filter = filter,
                        hits = (hit?.target as? PdfHit)?.takeIf { it.page == p }?.bounds.orEmpty(),
                        hitColor = colors.ink,
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfPage(
    doc: PdfDoc, page: Int, crop: RectF, boxW: androidx.compose.ui.unit.Dp, boxH: androidx.compose.ui.unit.Dp,
    zoom: Float, filter: ColorFilter?, hits: List<RectF>, hitColor: Color,
) {
    val size = doc.sizeOf(page - 1)
    val aspect = (size.width * crop.width()) / (size.height * crop.height())
    val (w, h) = if (boxW / boxH > aspect) (boxH * aspect) to boxH else boxW to (boxW / aspect)
    val density = LocalDensity.current
    val basePx = with(density) { w.toPx() }.roundToInt().coerceAtLeast(1)
    // re-render sharper once a pinch settles
    var detail by remember(page) { mutableFloatStateOf(1f) }
    LaunchedEffect(zoom) { delay(250); detail = zoom.coerceIn(1f, 4f) }
    val target = (basePx * detail).roundToInt().coerceAtMost(4096)
    val bitmap by produceState<Bitmap?>(null, page, crop, target) {
        value = doc.render(page - 1, target, crop) ?: value
    }
    Canvas(Modifier.size(w, h)) {
        val b = bitmap
        if (b != null) {
            drawImage(
                b.asImageBitmap(),
                dstSize = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt()),
                colorFilter = filter,
                filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
            )
        }
        if (hits.isNotEmpty()) {
            val sx = this.size.width / (size.width * crop.width())
            val sy = this.size.height / (size.height * crop.height())
            hits.forEach { r ->
                val x = (r.left - size.width * crop.left) * sx
                val y = (r.top - size.height * crop.top) * sy
                drawRect(hitColor.copy(alpha = 0.22f), Offset(x, y), Size(r.width() * sx, r.height() * sy))
                drawRect(hitColor, Offset(x, y + r.height() * sy), Size(r.width() * sx, 2.dp.toPx()))
            }
        }
    }
}

private const val MAX_ZOOM = 5f

/** Pinch to zoom (1–5×) and pan while zoomed; single-finger drags pass through to the pager at 1×. */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.zoomGestures(
    zoom: () -> Float, set: (Float, Offset) -> Unit, pan: () -> Offset, hx: (Hx) -> Unit, touched: () -> Unit, onPinchIn: () -> Boolean,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        touched()
        var pinch = 1f
        var handedOff = false
        do {
            val ev = awaitPointerEvent()
            val fingers = ev.changes.count { it.pressed }
            val z = zoom()
            if (handedOff) { ev.changes.forEach { it.consume() }; continue }
            if (fingers >= 2 && z <= 1.01f) {
                pinch *= ev.calculateZoom()
                if (pinch > 1.15f && onPinchIn()) { handedOff = true; ev.changes.forEach { it.consume() }; continue }
            }
            if (fingers >= 2 || z > 1.01f) {
                val nz = (z * ev.calculateZoom()).coerceIn(1f, MAX_ZOOM)
                // the closest zoom is a wall; settling back on the whole page is home
                if (nz >= MAX_ZOOM && z < MAX_ZOOM) hx(Hx.Edge)
                if (nz <= 1.01f && z > 1.01f) hx(Hx.DetentHome)
                val maxX = size.width * (nz - 1) / 2
                val maxY = size.height * (nz - 1) / 2
                val p = pan() + ev.calculatePan()
                set(nz, if (nz <= 1.01f) Offset.Zero else Offset(p.x.coerceIn(-maxX, maxX), p.y.coerceIn(-maxY, maxY)))
                if (fingers >= 2 || nz > 1.01f) ev.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (ev.changes.any { it.pressed })
        touched()
    }
}

/** Maps white → paper and black → ink through luminance, so pages take the look's colours. */
fun recolour(paper: Color, ink: Color): ColorFilter {
    fun row(p: Float, i: Float) = floatArrayOf((p - i) * 0.299f, (p - i) * 0.587f, (p - i) * 0.114f, 0f, i * 255f)
    val m = row(paper.red, ink.red) + row(paper.green, ink.green) + row(paper.blue, ink.blue) + floatArrayOf(0f, 0f, 0f, 1f, 0f)
    return ColorFilter.colorMatrix(ColorMatrix(m))
}

