package app.reader.looks.editorial

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.reader.looks.ChoiceOption
import app.reader.looks.FormField
import app.reader.looks.Kit
import app.reader.looks.LookCard
import app.reader.looks.common.CardRows
import app.reader.looks.SheetAction
import app.reader.theme.LocalColors

/**
 * Editorial's kit: 04's report form. Groups are ruled boxes with an ink top rule and an italic
 * Bodoni title set into it; rows are separated by dashed hairlines; choices are segmented
 * Fragment Mono strips that fill with ink; switches and sliders are drawn as instruments.
 */
object EditorialKit : Kit {

    @Composable
    private fun Sized(content: @Composable (tablet: Boolean, twoColumn: Boolean) -> Unit) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            content(maxWidth >= 700.dp, maxWidth >= 1000.dp && maxWidth > maxHeight)
        }
    }

    private val lblSize = 19.sp

    @Composable
    private fun Lbl(text: String, modifier: Modifier = Modifier) {
        Text(text, style = ET.disp(colors(), lblSize, 1.2f), modifier = modifier)
    }

    @Composable
    private fun Hint(text: String, modifier: Modifier = Modifier) {
        Text(text, style = ET.mono(colors(), 11.5.sp, 1.5f, colors().mute), modifier = modifier)
    }

    // ---------------- settings ----------------

    @Composable
    override fun SettingsFrame(title: String, backLabel: String, onBack: () -> Unit, preview: @Composable () -> Unit, form: @Composable ColumnScope.() -> Unit) {
        Sized { tablet, twoColumn ->
            val c = colors()
            val insets = WindowInsets.safeDrawing.asPaddingValues()
            BoxWithConstraints(Modifier.fillMaxSize().background(c.paper).gridPaper(c.grid.copy(alpha = 0.7f), 48.dp)) {
                val side = when {
                    twoColumn -> 40.dp
                    tablet -> maxOf(16.dp, (maxWidth - 680.dp) / 2)
                    else -> 16.dp
                }
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().background(c.paper).edges(bottom = c.ink)
                            .padding(top = insets.calculateTopPadding()).height(if (twoColumn) 76.dp else 68.dp).padding(horizontal = side),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        EdButton(backLabel, onBack, back = true)
                        Text(title, style = ET.disp(c, if (twoColumn) 34.sp else 30.sp, 1f), maxLines = 1)
                    }
                    if (twoColumn) {
                        Row(Modifier.fillMaxSize().padding(horizontal = side), horizontalArrangement = Arrangement.spacedBy(44.dp)) {
                            Column(
                                Modifier.width(480.dp).fillMaxHeight().verticalScroll(rememberScrollState())
                                    .padding(top = 6.dp, bottom = insets.calculateBottomPadding() + 64.dp),
                                content = form,
                            )
                            Box(Modifier.weight(1f).fillMaxHeight().padding(top = 32.dp, bottom = insets.calculateBottomPadding() + 32.dp)) { preview() }
                        }
                    } else {
                        // the preview stays pinned above the scrolling form
                        Box(Modifier.fillMaxWidth().background(c.paper).padding(horizontal = side).padding(top = 14.dp, bottom = 12.dp).height(if (tablet) 200.dp else 160.dp)) { preview() }
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = side)
                                .padding(bottom = insets.calculateBottomPadding() + 64.dp),
                            content = form,
                        )
                    }
                }
            }
        }
    }

    @Composable
    override fun PreviewFrame(content: @Composable () -> Unit) {
        val c = colors()
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val big = maxHeight > 300.dp
            Box(
                Modifier.fillMaxSize().background(c.paper)
                    .edges(top = c.rule, right = c.rule, bottom = c.rule, left = c.ink, leftW = 1.5.dp)
                    .clipToBounds()
                    .padding(vertical = if (big) 48.dp else 18.dp, horizontal = if (big) 38.dp else 0.dp),
            ) { content() }
            Text(
                "Preview", style = ET.it(c, 16.sp, 1.1f),
                modifier = Modifier.align(Alignment.TopEnd).offset(x = if (big) (-18).dp else (-14).dp, y = (-12).dp).background(c.paper).padding(horizontal = 6.dp),
            )
        }
    }

    @Composable
    override fun Group(title: String, hint: String?, content: @Composable ColumnScope.() -> Unit) {
        val c = colors()
        Box(Modifier.fillMaxWidth().padding(top = 30.dp)) {
            Column(
                Modifier.fillMaxWidth().background(c.paper)
                    .edges(top = c.ink, topW = 1.5.dp, left = c.rule, right = c.rule, bottom = c.rule)
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
            ) {
                if (hint != null) Hint(hint, Modifier.padding(top = 4.dp, bottom = 2.dp))
                Separated(content)
            }
            Text(
                title, style = ET.it(c, 21.sp, 24f / 21f, c.ink),
                modifier = Modifier.offset(x = 10.dp, y = (-13).dp).background(c.paper).padding(horizontal = 8.dp),
            )
        }
    }

    /** Stacks the group's rows and draws a dashed hairline between each pair. */
    @Composable
    private fun ColumnScope.Separated(content: @Composable ColumnScope.() -> Unit) {
        val c = colors()
        val ys = remember { IntArray(64) }
        val count = remember { IntArray(1) }
        val scope = this
        Layout(
            content = { scope.content() },
            modifier = Modifier.fillMaxWidth().drawBehind {
                for (i in 0 until count[0]) dashedH(c.rule, ys[i] + 0.5f)
            },
        ) { ms, cs ->
            val ps = ms.map { it.measure(cs.copy(minHeight = 0)) }
            var y = 0
            count[0] = 0
            ps.forEachIndexed { i, p ->
                if (i > 0 && p.height > 0 && count[0] < ys.size) { ys[count[0]] = y; count[0]++ }
                y += p.height
            }
            layout(cs.maxWidth, y) {
                var yy = 0
                ps.forEach { it.place(0, yy); yy += it.height }
            }
        }
    }

    @Composable
    override fun Choice(label: String?, options: List<ChoiceOption>, selected: Int, hint: String?, onSelect: (Int) -> Unit) {
        val c = colors()
        Column(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(vertical = 12.dp)) {
            if (label != null) Lbl(label)
            val fonts = options.any { it.font != null }
            if (fonts) {
                Column(
                    Modifier.padding(top = if (label != null) 10.dp else 0.dp).fillMaxWidth().drawBehind {
                        val hair = 1.dp.toPx().coerceAtLeast(1f)
                        val rowH = size.height / options.size
                        drawRect(c.rule, Offset(hair / 2, hair / 2), Size(size.width - hair, size.height - hair), style = Stroke(hair))
                        for (k in 1 until options.size) drawRect(c.rule, Offset(0f, k * rowH - hair / 2), Size(size.width, hair))
                        if (selected in options.indices) drawRect(c.ink, Offset(0f, selected * rowH), Size(size.width, rowH))
                    },
                ) {
                    options.forEachIndexed { i, o ->
                        val sel = i == selected
                        Row(
                            Modifier.fillMaxWidth().height(50.dp).clickable { onSelect(i) }.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                o.label, style = ET.mono(c, 17.sp, 1.2f, if (sel) c.paper else c.mute).copy(fontFamily = o.font),
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                            )
                            if (sel) TraceGlyph(c.paper)
                        }
                    }
                }
            } else {
                Row(
                    Modifier.padding(top = if (label != null) 10.dp else 0.dp).fillMaxWidth().height(IntrinsicSize.Min).drawBehind {
                        val hair = 1.dp.toPx().coerceAtLeast(1f)
                        val cw = size.width / options.size
                        drawRect(c.rule, Offset(hair / 2, hair / 2), Size(size.width - hair, size.height - hair), style = Stroke(hair))
                        for (k in 1 until options.size) drawRect(c.rule, Offset(k * cw - hair / 2, 0f), Size(hair, size.height))
                        if (selected in options.indices) drawRect(c.ink, Offset(selected * cw, 0f), Size(cw, size.height))
                    },
                ) {
                    options.forEachIndexed { i, o ->
                        val sel = i == selected
                        Box(
                            Modifier.weight(1f).fillMaxHeight().heightIn(min = 44.dp).clickable { onSelect(i) }.padding(horizontal = 6.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(o.label, style = ET.mono(c, 12.5.sp, 1.2f, if (sel) c.paper else c.mute), textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            if (hint != null) Hint(hint, Modifier.padding(top = 8.dp))
        }
    }

    /** The little rising trace that marks the chosen typeface. */
    @Composable
    private fun TraceGlyph(color: androidx.compose.ui.graphics.Color) {
        Canvas(Modifier.size(22.dp, 12.dp)) {
            val u = size.width / 22f
            val p = Path().apply { moveTo(0f, 11 * u); lineTo(6 * u, 11 * u); lineTo(8 * u, 6 * u); lineTo(13 * u, 6 * u); lineTo(15 * u, 1 * u); lineTo(22 * u, 1 * u) }
            drawPath(p, color, style = Stroke(1.5f * u))
        }
    }

    @Composable
    override fun Toggle(label: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
        val c = colors()
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable { onChange(!checked) }.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Lbl(label)
                if (hint != null) Hint(hint, Modifier.padding(top = 3.dp))
            }
            Canvas(Modifier.size(56.dp, 30.dp)) {
                val hair = 1.dp.toPx().coerceAtLeast(1f)
                if (checked) {
                    var x = 0f
                    while (x < size.width) { drawRect(c.rule, Offset(x, 0f), Size(hair, size.height)); x += 4.dp.toPx() }
                }
                drawRect(if (checked) c.ink else c.mute, Offset(hair / 2, hair / 2), Size(size.width - hair, size.height - hair), style = Stroke(hair))
                val k = 20.dp.toPx(); val inset = 4.dp.toPx()
                val kx = if (checked) inset + 26.dp.toPx() else inset
                if (checked) drawRect(c.ink, Offset(kx, inset), Size(k, k))
                else {
                    drawRect(c.paper, Offset(kx, inset), Size(k, k))
                    drawRect(c.mute, Offset(kx + hair / 2, inset + hair / 2), Size(k - hair, k - hair), style = Stroke(hair))
                }
            }
        }
    }

    @Composable
    override fun Slider(label: String, valueText: String, fraction: Float, home: Float?, stepper: Boolean, detents: Int, onFraction: (Float) -> Unit, onStep: (Int) -> Unit) {
        val c = colors()
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Lbl(label, Modifier.weight(1f).alignByBaseline())
                Text(valueText, style = ET.mono(c, 13.sp, 1.2f), modifier = Modifier.alignByBaseline())
            }
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (stepper) StepButton("A−") { onStep(-1) }
                Track(fraction, home, detents, onFraction, Modifier.weight(1f).padding(top = if (stepper) 5.dp else 0.dp))
                if (stepper) StepButton("A+") { onStep(1) }
            }
        }
    }

    /** A 2px track, ink up to the value and dashed after it; a slotted thumb; detent ticks and the home mark below. */
    @Composable
    private fun Track(fraction: Float, home: Float?, detents: Int, onFraction: (Float) -> Unit, modifier: Modifier) {
        val c = colors()
        val f = fraction.coerceIn(0f, 1f)
        Canvas(
            modifier.fillMaxWidth().height(46.dp).padding(bottom = 0.dp)
                .pointerInput(Unit) {
                    val inset = 7.dp.toPx()
                    fun at(x: Float) = ((x - inset) / (size.width - 2 * inset)).coerceIn(0f, 1f)
                    detectTapGestures { onFraction(at(it.x)) }
                }
                .pointerInput(Unit) {
                    val inset = 7.dp.toPx()
                    fun at(x: Float) = ((x - inset) / (size.width - 2 * inset)).coerceIn(0f, 1f)
                    detectHorizontalDragGestures { ch, _ -> onFraction(at(ch.position.x)) }
                },
        ) {
            val inset = 7.dp.toPx()
            val span = size.width - 2 * inset
            val cy = 17.dp.toPx()
            val th = 2.dp.toPx()
            drawLine(c.rule, Offset(0f, cy), Offset(size.width, cy), th, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
            drawRect(c.ink, Offset(0f, cy - th / 2), Size(f * size.width, th))
            // one tick per detent step (both ends included) and the home mark
            val n = detents.coerceIn(1, 64)
            val hair = 1.dp.toPx().coerceAtLeast(1f)
            for (k in 0..n) drawRect(c.mute.copy(alpha = 0.7f), Offset(inset + span * k / n, size.height - 6.dp.toPx()), Size(hair, 6.dp.toPx()))
            if (home != null) {
                val hx = inset + span * home
                val tri = Path().apply {
                    moveTo(hx - 4.dp.toPx(), size.height + 7.dp.toPx()); lineTo(hx + 4.dp.toPx(), size.height + 7.dp.toPx()); lineTo(hx, size.height + 2.dp.toPx()); close()
                }
                drawPath(tri, c.ink)
            }
            // thumb
            val tx = inset + span * f
            val tw = 14.dp.toPx(); val tH = 30.dp.toPx()
            drawRect(c.paper, Offset(tx - tw / 2, cy - tH / 2), Size(tw, tH))
            drawRect(c.ink, Offset(tx - tw / 2 + hair / 2, cy - tH / 2 + hair / 2), Size(tw - hair, tH - hair), style = Stroke(hair))
            drawRect(c.ink, Offset(tx - 0.75.dp.toPx(), cy - tH / 2), Size(1.5.dp.toPx(), tH))
        }
    }

    @Composable
    private fun StepButton(label: String, onClick: () -> Unit) {
        val c = colors()
        val (src, pressed) = rememberPress()
        Box(
            Modifier.size(44.dp).background(if (pressed) c.ink else c.paper).outline(c.ink).clickable(src, null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Text(label, style = ET.mono(c, 13.sp, 1f, if (pressed) c.paper else c.ink)) }
    }

    @Composable
    override fun Action(label: String, value: String?, button: String, onClick: () -> Unit) {
        val c = colors()
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Lbl(label)
                if (value != null) Text(value, style = ET.mono(c, 11.5.sp, 1.5f, c.ink.copy(alpha = 0.85f)), modifier = Modifier.padding(top = 3.dp))
            }
            EdButton(button, onClick)
        }
    }

    @Composable
    override fun LookPicker(cards: List<LookCard>, current: String, onPick: (String) -> Unit) {
        CardRows(cards, 14.dp, Modifier.padding(top = 6.dp, bottom = 12.dp).padding(horizontal = 4.dp)) { card, m -> LookCardView(card, card.id == current, m) { onPick(card.id) } }
    }

    @Composable
    private fun LookCardView(card: LookCard, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
        val c = colors()
        Column(modifier.clickable(onClick = onClick)) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(412f / 620f)
                    .drawBehind {
                        if (selected) {
                            val o = 4.dp.toPx(); val w = 2.dp.toPx()
                            drawRect(c.ink, Offset(-o + w / 2, -o + w / 2), Size(size.width + 2 * o - w, size.height + 2 * o - w), style = Stroke(w))
                        }
                    }
                    .outline(c.ink.copy(alpha = 0.3f))
                    .padding(1.dp).clipToBounds(),
            ) { card.thumb() }
            Text(
                card.name, style = ET.mono(c, 13.sp, 1.25f).copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
                modifier = Modifier.padding(top = 8.dp), maxLines = 2,
            )
            Text(card.sub, style = ET.mono(c, 11.5.sp, 1.3f, c.ink.copy(alpha = 0.65f)), maxLines = 2, modifier = Modifier.padding(top = 4.dp))
        }
    }

    // ---------------- sheet ----------------

    @Composable
    override fun Sheet(title: String, subtitle: String?, actions: List<SheetAction>, onDismiss: () -> Unit) {
        Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Sized { _, _ ->
                val c = colors()
                val insets = WindowInsets.safeDrawing.asPaddingValues()
                Box(
                    Modifier.fillMaxSize().background(c.paper.copy(alpha = 0.7f))
                        .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss),
                ) {
                    Column(
                        Modifier.align(Alignment.BottomCenter).widthIn(max = 520.dp).fillMaxWidth()
                            .background(c.paper).edges(top = c.ink, topW = 1.5.dp)
                            .clickable(remember { MutableInteractionSource() }, null) {}
                            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp + insets.calculateBottomPadding()),
                    ) {
                        Text(title, style = ET.disp(c, 24.sp, 1.15f), modifier = Modifier.padding(end = 20.dp))
                        if (subtitle != null) Text(subtitle, style = ET.mono(c, 11.5.sp, 1.4f, c.mute), modifier = Modifier.padding(top = 6.dp))
                        Spacer(Modifier.height(14.dp))
                        actions.forEach { SheetButton(it) }
                    }
                }
            }
        }
    }

    /**
     * The sheet's frame and buttons around a column of entry fields: an italic label, then a
     * Fragment Mono line in the preview frame's box (ink left edge, rule elsewhere), then the hint.
     */
    @Composable
    override fun Form(title: String, subtitle: String?, fields: List<FormField>, saveLabel: String, onSave: () -> Unit, onDismiss: () -> Unit) {
        Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Sized { _, _ ->
                val c = colors()
                val insets = WindowInsets.safeDrawing.asPaddingValues()
                Box(
                    Modifier.fillMaxSize().background(c.paper.copy(alpha = 0.7f))
                        .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss)
                        .imePadding(),
                ) {
                    Column(
                        Modifier.align(Alignment.BottomCenter).widthIn(max = 520.dp).fillMaxWidth()
                            .padding(top = insets.calculateTopPadding())
                            .background(c.paper).edges(top = c.ink, topW = 1.5.dp)
                            .clickable(remember { MutableInteractionSource() }, null) {}
                            .verticalScroll(rememberScrollState())
                            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp + insets.calculateBottomPadding()),
                    ) {
                        Text(title, style = ET.disp(c, 24.sp, 1.15f), modifier = Modifier.padding(end = 20.dp))
                        if (subtitle != null) Text(subtitle, style = ET.mono(c, 11.5.sp, 1.4f, c.mute), modifier = Modifier.padding(top = 6.dp))
                        Spacer(Modifier.height(8.dp))
                        fields.forEach { FormEntry(it) }
                        Spacer(Modifier.height(14.dp))
                        SheetButton(SheetAction(saveLabel, onClick = onSave), primary = true)
                        SheetButton(SheetAction("Cancel", quiet = true, onClick = onDismiss))
                    }
                }
            }
        }
    }

    @Composable
    private fun FormEntry(f: FormField) {
        val c = colors()
        Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Text(f.label, style = ET.it(c, 17.sp, 1.2f, c.ink))
            BasicTextField(
                f.value, f.onChange,
                singleLine = true,
                textStyle = ET.mono(c, 14.sp, 1.3f),
                cursorBrush = SolidColor(c.ink),
                modifier = Modifier.padding(top = 6.dp).fillMaxWidth().heightIn(min = 48.dp)
                    .background(c.paper)
                    .edges(top = c.rule, right = c.rule, bottom = c.rule, left = c.ink, leftW = 1.5.dp),
                decorationBox = { inner ->
                    Box(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), contentAlignment = Alignment.CenterStart) { inner() }
                },
            )
            if (f.hint != null) Hint(f.hint, Modifier.padding(top = 6.dp))
        }
    }

    @Composable
    private fun SheetButton(a: SheetAction, primary: Boolean = false) {
        val c = colors()
        val (src, pressed) = rememberPress()
        Box(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).background(if (pressed) c.ink else c.paper)
                .then(if (primary) Modifier.edges(top = c.ink, topW = 1.5.dp) else Modifier.edges(top = c.rule))
                .clickable(src, null, onClick = a.onClick).padding(horizontal = 2.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            val fg = if (pressed) c.paper else if (a.quiet) c.mute else c.ink
            Text(
                a.label,
                style = when {
                    a.quiet -> ET.it(c, 19.sp, 1.2f, fg)
                    primary -> ET.disp(c, 21.sp, 1.2f, fg, FontWeight.SemiBold)
                    else -> ET.disp(c, 19.sp, 1.2f, fg, FontWeight.Normal)
                },
            )
            // the primary action carries the Continue arrow
            if (primary) Box(Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)) { Arrow(fg) }
        }
    }

    // ---------------- panels ----------------

    @Composable
    override fun Panel(title: String, onClose: () -> Unit, top: (@Composable () -> Unit)?, backLabel: String, items: LazyListScope.() -> Unit) {
        Sized { tablet, _ ->
            val c = colors()
            val insets = WindowInsets.safeDrawing.asPaddingValues()
            Box(
                Modifier.fillMaxSize().then(
                    if (tablet) Modifier.background(c.paper.copy(alpha = 0.6f)).clickable(remember { MutableInteractionSource() }, null, onClick = onClose) else Modifier,
                ),
            ) {
                Column(
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                        .then(if (tablet) Modifier.width(440.dp) else Modifier.fillMaxWidth())
                        .background(c.paper)
                        .then(if (tablet) Modifier.edges(left = c.ink, leftW = 1.5.dp) else Modifier)
                        .clickable(remember { MutableInteractionSource() }, null) {}
                        .imePadding(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().edges(bottom = c.ink).padding(top = insets.calculateTopPadding())
                            .height(68.dp).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        EdButton(backLabel, onClose, back = true)
                        Text(title, style = ET.disp(c, 30.sp, 1f), maxLines = 1)
                    }
                    if (top != null) Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) { top() }
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = insets.calculateBottomPadding() + 32.dp),
                        content = items,
                    )
                }
            }
        }
    }

    @Composable
    override fun PanelRow(primary: String, secondary: String?, trailing: String?, current: Boolean, depth: Int, onClick: () -> Unit) {
        val c0 = colors()
        val (src, pressed) = rememberPress()
        val c = if (pressed) c0.inverted() else c0
        CompositionLocalProvider(LocalColors provides c) {
            Row(
                Modifier.fillMaxWidth().background(if (pressed) c0.ink else c0.paper)
                    // a rule above and below: the top one sits on the previous row's bottom rule,
                    // so only the first row shows it as a new line
                    .drawBehind { dashedH(c0.rule, -0.5f); dashedH(c0.rule, size.height - 0.5f) }
                    .clickable(src, null, onClick = onClick)
                    .heightIn(min = 52.dp).padding(vertical = 11.dp).padding(start = (18 * depth).dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // the order in force: a filled pen head on the current entry, a hollow one elsewhere
                Canvas(Modifier.padding(top = 8.dp).size(6.dp)) {
                    val w = 1.5.dp.toPx()
                    if (current) drawRect(c.ink)
                    else drawRect(c.rule, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w))
                }
                Column(Modifier.weight(1f)) {
                    Text(primary, style = if (current) ET.it(c, 19.sp, 1.2f, c.ink) else ET.disp(c, 18.sp, 1.2f, weight = FontWeight.Normal))
                    if (secondary != null) Text(secondary, style = ET.mono(c, 11.sp, 1.4f, c.mute), modifier = Modifier.padding(top = 3.dp))
                }
                if (trailing != null) Text(trailing, style = ET.mono(c, 12.sp, 1.6f, if (current) c.ink else c.mute), modifier = Modifier.padding(top = 2.dp))
            }
        }
    }

    @Composable
    override fun SearchField(value: String, placeholder: String, onChange: (String) -> Unit, onSubmit: () -> Unit) {
        val c = colors()
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).outline(c.ink).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("find", style = ET.it(c, 17.sp, 1.1f), modifier = Modifier.padding(end = 12.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, style = ET.mono(c, 13.sp, 1.3f, c.mute))
                BasicTextField(
                    value, onChange,
                    textStyle = ET.mono(c, 13.sp, 1.3f),
                    singleLine = true,
                    cursorBrush = SolidColor(c.ink),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        }
    }

    @Composable
    override fun PanelMessage(text: String) {
        Text(text, style = ET.it(colors(), 17.sp, 1.3f), modifier = Modifier.padding(vertical = 14.dp))
    }

    @Composable
    override fun TtsBar(playing: Boolean, label: String, onPlayPause: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, onStop: () -> Unit) {
        val c = colors()
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        Column(
            Modifier.fillMaxWidth().background(c.paper).edges(top = c.ink)
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp + insets.calculateBottomPadding()),
        ) {
            Row(Modifier.fillMaxWidth()) {
                Text("Reading aloud", style = ET.it(c, 17.sp, 1.2f), modifier = Modifier.alignByBaseline())
                Text(
                    label, style = ET.mono(c, 12.sp, 1.2f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 10.dp).alignByBaseline(),
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EdButton("Previous", onPrevious)
                EdButton(if (playing) "Pause" else "Play", onPlayPause)
                EdButton("Next", onNext)
                Spacer(Modifier.weight(1f))
                EdButton("Stop", onStop)
            }
        }
    }
}
