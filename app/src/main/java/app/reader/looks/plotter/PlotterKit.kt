package app.reader.looks.plotter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
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
import app.reader.looks.SheetAction
import app.reader.theme.LocalColors
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Plotter's kit: a drawing's form. Fine-pen boxes, a heavy-pen tag on each group, ink-filled selections. */
object PlotterKit : Kit {

    @Composable
    override fun SettingsFrame(title: String, backLabel: String, onBack: () -> Unit, preview: @Composable () -> Unit, form: @Composable ColumnScope.() -> Unit) {
        val c = colors()
        BoxWithConstraints(Modifier.fillMaxSize().mmGrid(c)) {
            val shape = PShape(tablet = maxWidth >= 700.dp, wide = maxWidth >= 1000.dp && maxWidth > maxHeight)
            val insets = WindowInsets.safeDrawing.asPaddingValues()
            val ld = LocalLayoutDirection.current
            val sx = insets.calculateStartPadding(ld); val ex = insets.calculateEndPadding(ld)
            val side = when {
                shape.wide -> 40.dp
                shape.tablet -> maxOf(16.dp, (maxWidth - 680.dp) / 2)
                else -> 16.dp
            }
            CompositionLocalProvider(LocalShape provides shape) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().background(c.paper).edges(c.ink, bottom = HV)
                            .padding(top = insets.calculateTopPadding()).height(if (shape.wide) 76.dp else 68.dp)
                            .padding(start = side + sx, end = side + ex),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PenButton(backLabel, onBack, back = true)
                        Spacer(Modifier.weight(1f))
                        Text(title, style = PT.serif(if (shape.wide) 38.sp else 34.sp, c.ink, lh = 40.sp))
                    }
                    if (shape.wide) {
                        Row(Modifier.fillMaxSize().padding(start = side + sx, end = side + ex)) {
                            Column(
                                Modifier.widthIn(min = 400.dp, max = 480.dp).weight(1f, fill = false).fillMaxHeight()
                                    .verticalScroll(rememberScrollState())
                                    .padding(top = 8.dp, bottom = insets.calculateBottomPadding() + 64.dp),
                                content = form,
                            )
                            Spacer(Modifier.width(44.dp))
                            Box(Modifier.weight(1f).fillMaxHeight().padding(top = 32.dp, bottom = insets.calculateBottomPadding() + 32.dp)) { preview() }
                        }
                    } else {
                        Box(
                            Modifier.padding(start = side + sx, end = side + ex).fillMaxWidth().background(c.paper)
                                .edges(c.ink, bottom = HL).padding(top = 16.dp, bottom = 14.dp),
                        ) {
                            Box(Modifier.fillMaxWidth().height(if (shape.tablet) 220.dp else 156.dp)) { preview() }
                        }
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                .padding(start = side + sx, end = side + ex, bottom = insets.calculateBottomPadding() + 64.dp),
                            content = form,
                        )
                    }
                }
            }
        }
    }

    @Composable
    override fun PreviewFrame(content: @Composable () -> Unit) {
        val c = colors(); val shape = LocalShape.current
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize().background(c.paper).penBox(c.ink, HL)
                    .drawBehind {
                        val hl = pen(HL); val half = 7.dp.toPx(); val inset = 4.dp.toPx() + half
                        crosshair(c.ink, size.width - inset, inset, half, hl)
                        crosshair(c.ink, inset, size.height - inset, half, hl)
                    }
                    .padding(vertical = if (shape.wide) 48.dp else 18.dp, horizontal = if (shape.wide) 38.dp else 0.dp)
                    .clipToBounds(),
            ) { content() }
            Text(
                "preview, scale 1:1", style = PT.label(c),
                modifier = Modifier.align(Alignment.TopEnd).offset(x = if (shape.wide) (-18).dp else (-12).dp, y = (-7).dp)
                    .background(c.paper).padding(horizontal = 5.dp),
            )
        }
    }

    @Composable
    override fun Group(title: String, hint: String?, content: @Composable ColumnScope.() -> Unit) {
        val c = colors()
        Box(Modifier.fillMaxWidth().padding(top = 34.dp)) {
            Column(
                Modifier.fillMaxWidth().background(c.paper).penBox(c.ink, HL).padding(HL)
                    .padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 4.dp),
            ) {
                if (hint != null) Text(hint, style = PT.mono(12.sp, c.mute, lh = 18.sp), modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
                // rows are separated by dashed hairlines; the first row's line is hidden
                Column(Modifier.fillMaxWidth().clipToBounds().layout { m, cons ->
                    val k = pen(HL).roundToInt()
                    val p = m.measure(cons)
                    layout(p.width, (p.height - k).coerceAtLeast(0)) { p.place(0, -k) }
                }, content = content)
            }
            Text(
                title, style = PT.serif(19.sp, c.ink, italic = true, lh = 24.sp),
                modifier = Modifier.offset(x = 10.dp, y = (-14).dp).background(c.paper).penBox(c.ink, HV)
                    .padding(horizontal = 9.dp + HV, vertical = HV),
            )
        }
    }

    /** `.row`: 12dp padding, at least 60dp, a dashed hairline above. */
    private fun Modifier.row(color: androidx.compose.ui.graphics.Color) = drawBehind {
        hDash(color, 0f, size.width, 0f, pen(HL))
    }.heightIn(min = 60.dp).padding(vertical = 12.dp)

    @Composable
    private fun Lbl(text: String, modifier: Modifier = Modifier) {
        Text(text, style = PT.mono(14.sp, colors().ink, lh = 18.2.sp), modifier = modifier)
    }

    @Composable
    private fun Hint(text: String, modifier: Modifier = Modifier) {
        Text(text, style = PT.mono(12.sp, colors().mute, lh = 18.sp), modifier = modifier)
    }

    @Composable
    override fun Choice(label: String?, options: List<ChoiceOption>, selected: Int, hint: String?, onSelect: (Int) -> Unit) {
        val c = colors()
        Column(Modifier.fillMaxWidth().row(c.ink), verticalArrangement = Arrangement.Center) {
            if (label != null) Lbl(label)
            val fonts = options.any { it.font != null }
            if (fonts) {
                Column(Modifier.fillMaxWidth().padding(top = if (label != null) 10.dp else 0.dp)) {
                    options.forEachIndexed { i, o ->
                        val sel = i == selected
                        val fg = if (sel) c.paper else c.ink
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 50.dp).background(if (sel) c.ink else c.paper)
                                .edges(c.ink, start = HL, end = HL, bottom = HL, top = if (i == 0) HL else 0.dp)
                                .clickable { onSelect(i) }.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(o.label, style = PT.serif(17.sp, fg).copy(fontFamily = o.font, letterSpacing = 0.sp), modifier = Modifier.weight(1f), maxLines = 1)
                            if (sel) Canvas(Modifier.size(14.dp)) {
                                val hl = pen(HL)
                                drawCircle(fg, size.minDimension / 2 - hl / 2, style = Stroke(hl))
                                hLine(fg, 0f, size.width, size.height / 2, hl)
                                vLine(fg, size.width / 2, 0f, size.height, hl)
                            }
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(top = if (label != null) 10.dp else 0.dp)) {
                    options.forEachIndexed { i, o ->
                        val sel = i == selected
                        Box(
                            Modifier.weight(1f).fillMaxHeight().heightIn(min = 44.dp).background(if (sel) c.ink else c.paper)
                                .edges(c.ink, start = if (i == 0) HL else 0.dp, top = HL, end = HL, bottom = HL)
                                .clickable { onSelect(i) }.padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                o.label, style = PT.mono(13.sp, if (sel) c.paper else c.ink), textAlign = TextAlign.Center,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            if (hint != null) Hint(hint, Modifier.padding(top = 8.dp))
        }
    }

    @Composable
    override fun Toggle(label: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
        val c = colors()
        Row(
            Modifier.fillMaxWidth().row(c.ink).clickable { onChange(!checked) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Lbl(label)
                if (hint != null) Hint(hint, Modifier.padding(top = 3.dp))
            }
            Spacer(Modifier.width(16.dp))
            Switch(checked)
        }
    }

    /** `.sw`: a fine box with a fine square knob; on, the box goes heavy and hatched and the knob fills. */
    @Composable
    private fun Switch(on: Boolean) {
        val c = colors()
        Canvas(Modifier.size(56.dp, 30.dp)) {
            val hl = pen(HL); val hv = pen(HV)
            val w = size.width; val h = size.height
            drawRect(c.paper)
            if (on) {
                clipRect {
                    val step = 4.dp.toPx() * sqrt(2f)
                    var x = -h
                    while (x < w) { drawLine(c.ink, Offset(x, h), Offset(x + h, 0f), hl); x += step }
                }
                drawRect(c.ink, Offset(hv / 2, hv / 2), Size(w - hv, h - hv), style = Stroke(hv))
                val k = 20.dp.toPx(); val ring = 3.dp.toPx()
                val x0 = 3.dp.toPx() + 26.dp.toPx() + hv - hl; val y0 = 3.dp.toPx() + hv - hl
                drawRect(c.paper, Offset(x0 - ring, y0 - ring), Size(k + 2 * ring, k + 2 * ring))
                drawRect(c.ink, Offset(x0, y0), Size(k, k))
            } else {
                drawRect(c.ink, Offset(hl / 2, hl / 2), Size(w - hl, h - hl), style = Stroke(hl))
                val k = 20.dp.toPx(); val o = 4.dp.toPx()
                drawRect(c.ink, Offset(o + hl / 2, o + hl / 2), Size(k - hl, k - hl), style = Stroke(hl))
            }
        }
    }

    @Composable
    override fun Slider(label: String, valueText: String, fraction: Float, home: Float?, stepper: Boolean, detents: Int, onFraction: (Float) -> Unit, onStep: (Int) -> Unit) {
        val c = colors()
        Column(Modifier.fillMaxWidth().row(c.ink)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Lbl(label, Modifier.weight(1f))
                Text(valueText, style = PT.mono(14.sp, c.ink))
            }
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Top) {
                if (stepper) { StepButton("A−") { onStep(-1) }; Spacer(Modifier.width(12.dp)) }
                Track(fraction, home, detents, onFraction, Modifier.weight(1f))
                if (stepper) { Spacer(Modifier.width(12.dp)); StepButton("A+") { onStep(1) } }
            }
        }
    }

    @Composable
    private fun StepButton(label: String, onClick: () -> Unit) {
        val c = colors()
        val src = remember { MutableInteractionSource() }
        val pressed by src.collectIsPressedAsState()
        Box(
            Modifier.size(44.dp).background(if (pressed) c.ink else c.paper).penBox(c.ink, HL).clickable(src, null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Text(label, style = PT.mono(14.sp, if (pressed) c.paper else c.ink)) }
    }

    /**
     * A drafting rule for a slider: a fine line with the heavy pen up to the thumb, a tall
     * thumb with a centre line, graduations underneath and a small arrowhead at the neutral point.
     */
    @Composable
    private fun Track(fraction: Float, home: Float?, detents: Int, onFraction: (Float) -> Unit, modifier: Modifier) {
        val c = colors()
        Canvas(
            modifier.height(34.dp + 12.dp + 8.dp)
                .pointerInput(Unit) { detectTapGestures { onFraction(((it.x - 9.dp.toPx()) / (size.width - 18.dp.toPx())).coerceIn(0f, 1f)) } }
                .pointerInput(Unit) { detectHorizontalDragGestures { ch, _ -> onFraction(((ch.position.x - 9.dp.toPx()) / (size.width - 18.dp.toPx())).coerceIn(0f, 1f)) } },
        ) {
            val hl = pen(HL); val hv = pen(HV)
            val w = size.width
            val cy = 17.dp.toPx()
            val tx = 9.dp.toPx() + fraction.coerceIn(0f, 1f) * (w - 18.dp.toPx())
            hLine(c.ink, 0f, w, cy, hl)
            drawRect(c.ink, Offset(0f, snap(cy, hv)), Size(tx, hv))
            // graduations: one per detent, under the thumb centre at each snap position
            val t0 = 9.dp.toPx(); val t1 = w - 9.dp.toPx(); val n = detents.coerceAtLeast(1)
            val gy1 = 34.dp.toPx() + 12.dp.toPx(); val gy0 = gy1 - 6.dp.toPx()
            val tick = c.ink.copy(alpha = 0.75f)
            for (i in 0..n) vLine(tick, t0 + (t1 - t0) * i / n, gy0, gy1, hl)
            if (home != null) {
                val hx = t0 + (t1 - t0) * home
                val tri = Path().apply { moveTo(hx, gy1 + 2.dp.toPx()); lineTo(hx + 4.dp.toPx(), gy1 + 7.dp.toPx()); lineTo(hx - 4.dp.toPx(), gy1 + 7.dp.toPx()); close() }
                drawPath(tri, c.ink)
            }
            // thumb
            val tw = 18.dp.toPx(); val th = 30.dp.toPx()
            val left = (tx - tw / 2).roundToInt().toFloat(); val top = (cy - th / 2).roundToInt().toFloat()
            drawRect(c.paper, Offset(left, top), Size(tw, th))
            drawRect(c.ink, Offset(left + hl / 2, top + hl / 2), Size(tw - hl, th - hl), style = Stroke(hl))
            vLine(c.ink, tx, top, top + th, hv)
        }
    }

    @Composable
    override fun Action(label: String, value: String?, button: String, onClick: () -> Unit) {
        val c = colors()
        Row(Modifier.fillMaxWidth().row(c.ink), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Lbl(label)
                if (value != null) Text(value, style = PT.mono(12.sp, c.ink, lh = 18.sp), modifier = Modifier.padding(top = 3.dp))
            }
            Spacer(Modifier.width(16.dp))
            PenButton(button, onClick)
        }
    }

    @Composable
    override fun LookPicker(cards: List<LookCard>, current: String, onPick: (String) -> Unit) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp)) {
            if (maxWidth >= 440.dp) {
                Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    cards.forEach { LookCardView(it, it.id == current, Modifier.weight(1f)) { onPick(it.id) } }
                }
            } else {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    cards.forEach { LookCardView(it, it.id == current, Modifier.width(112.dp)) { onPick(it.id) } }
                }
            }
        }
    }

    @Composable
    private fun LookCardView(card: LookCard, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
        val c = colors()
        Column(modifier.clickable(onClick = onClick).padding(vertical = 4.dp)) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(412f / 620f)
                    .drawBehind {
                        if (selected) {
                            val hv = pen(HV); val g = 3.dp.toPx() + hv
                            drawRect(c.ink, Offset(-g + hv / 2, -g + hv / 2), Size(size.width + 2 * g - hv, size.height + 2 * g - hv), style = Stroke(hv))
                        }
                    }
                    .penBox(if (selected) c.ink else c.ink.copy(alpha = 0.3f), HL).padding(HL).clipToBounds(),
            ) { card.thumb() }
            Text(
                card.name, style = PT.mono(13.sp, c.ink, weight = if (selected) 500 else 400, lh = 16.sp),
                modifier = Modifier.padding(top = 8.dp), maxLines = 2,
            )
            Text(card.sub, style = PT.mono(11.5.sp, c.mute, lh = 15.sp), maxLines = 2)
        }
    }

    @Composable
    override fun Sheet(title: String, subtitle: String?, actions: List<SheetAction>, onDismiss: () -> Unit) {
        Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            val c = colors()
            val insets = WindowInsets.safeDrawing.asPaddingValues()
            Box(
                Modifier.fillMaxSize().background(c.paper.copy(alpha = 0.7f))
                    .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss),
            ) {
                Column(
                    Modifier.align(Alignment.BottomCenter).widthIn(max = 520.dp).fillMaxWidth()
                        .background(c.paper).edges(c.ink, start = HV, top = HV, end = HV)
                        .clickable(remember { MutableInteractionSource() }, null) {}
                        .padding(start = 16.dp + HV, end = 16.dp + HV, top = 18.dp + HV, bottom = 10.dp + insets.calculateBottomPadding()),
                ) {
                    Text(title, style = PT.serif(28.sp, c.ink, italic = true, lh = 29.4.sp), modifier = Modifier.padding(end = 20.dp).cssLines(28.sp, 1.05f))
                    if (subtitle != null) Text(subtitle, style = PT.mono(12.sp, c.mute, italic = true), modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(14.dp))
                    actions.forEach { a -> SheetButton(a) }
                }
            }
        }
    }

    /**
     * Edit details: the sheet's card, its fields drawn as the record's ruled cell grid (italic
     * label over the value, the cell going heavy while it has focus), the record's solid ink band
     * as Save, and a quiet Cancel row.
     */
    @Composable
    override fun Form(title: String, subtitle: String?, fields: List<FormField>, saveLabel: String, onSave: () -> Unit, onDismiss: () -> Unit) {
        Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            val c = colors()
            Box(
                Modifier.fillMaxSize().background(c.paper.copy(alpha = 0.7f))
                    .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss)
                    .imePadding(),
            ) {
                Column(
                    Modifier.align(Alignment.BottomCenter).widthIn(max = 520.dp).fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                        .background(c.paper).edges(c.ink, start = HV, top = HV, end = HV)
                        .clickable(remember { MutableInteractionSource() }, null) {}
                        .verticalScroll(rememberScrollState())
                        // after imePadding, only the part of the bottom bars the keyboard doesn't cover is left
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                        .padding(start = 16.dp + HV, end = 16.dp + HV, top = 18.dp + HV, bottom = 10.dp),
                ) {
                    Text(title, style = PT.serif(28.sp, c.ink, italic = true, lh = 29.4.sp), modifier = Modifier.padding(end = 20.dp).cssLines(28.sp, 1.05f))
                    if (subtitle != null) Text(subtitle, style = PT.mono(12.sp, c.mute, italic = true), modifier = Modifier.padding(top = 6.dp))
                    Column(Modifier.padding(top = 14.dp).fillMaxWidth().edges(c.ink, start = HL, top = HL)) {
                        fields.forEachIndexed { i, f -> FormCell(f, last = i == fields.lastIndex, onSave) }
                    }
                    SaveBand(saveLabel, onSave, Modifier.padding(top = 14.dp))
                    SheetButton(SheetAction("Cancel", quiet = true, onClick = onDismiss), rule = false)
                }
            }
        }
    }

    /** One field: a fine-pen cell, heavy while focused; the whole cell takes the tap. */
    @Composable
    private fun FormCell(f: FormField, last: Boolean, onSave: () -> Unit) {
        val c = colors()
        val src = remember { MutableInteractionSource() }
        val focused by src.collectIsFocusedAsState()
        BasicTextField(
            f.value, f.onChange,
            singleLine = true,
            textStyle = PT.mono(14.sp, c.ink, lh = 20.sp),
            cursorBrush = SolidColor(c.ink),
            interactionSource = src,
            keyboardOptions = KeyboardOptions(imeAction = if (last) ImeAction.Done else ImeAction.Next),
            keyboardActions = KeyboardActions(onDone = { onSave() }),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).edges(c.ink, end = HL, bottom = HL)
                        .then(if (focused) Modifier.drawBehind {
                            val hv = pen(HV)
                            drawRect(c.ink, Offset(hv / 2, hv / 2), Size(size.width - hv, size.height - hv), style = Stroke(hv))
                        } else Modifier)
                        .padding(start = 9.dp, end = 9.dp, top = 7.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(f.label.lowercase(), style = PT.label(c), modifier = Modifier.cssLines(10.5.sp, 1f))
                    Box(Modifier.padding(top = 5.dp)) { inner() }
                    if (f.hint != null) Text(f.hint, style = PT.mono(11.sp, c.mute, italic = true, lh = 15.sp), modifier = Modifier.padding(top = 3.dp))
                }
            },
        )
    }

    /** The primary action: the record's solid ink band, its label in the display italic with a dimension arrow. */
    @Composable
    private fun SaveBand(label: String, onClick: () -> Unit, modifier: Modifier) {
        val c = colors()
        val src = remember { MutableInteractionSource() }
        val pressed by src.collectIsPressedAsState()
        val bg = if (pressed) c.paper else c.ink
        val fg = if (pressed) c.ink else c.paper
        Row(
            modifier.fillMaxWidth().heightIn(min = 52.dp).background(bg)
                .then(if (pressed) Modifier.penBox(c.ink, HV) else Modifier)
                .clickable(src, null, onClick = onClick).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = PT.serif(23.sp, fg, italic = true), modifier = Modifier.weight(1f))
            Canvas(Modifier.size(34.dp, 12.dp)) {
                val hl = pen(HL); val u = size.width / 34f
                val col = fg.copy(alpha = 0.82f)
                vLine(col, hl / 2, 1 * u, 11 * u, hl)
                hLine(col, 0f, 28 * u, 6 * u, hl)
                arrow(col, 34 * u, 6 * u, 0f, 9 * u, 3 * u)
            }
        }
    }

    @Composable
    private fun SheetButton(a: SheetAction, rule: Boolean = true) {
        val c = colors()
        val src = remember { MutableInteractionSource() }
        val pressed by src.collectIsPressedAsState()
        Box(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).background(if (pressed) c.ink else c.paper).edges(c.ink, top = if (rule) HL else 0.dp)
                .clickable(src, null, onClick = a.onClick).padding(horizontal = 2.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(a.label, style = PT.mono(14.sp, if (pressed) c.paper else if (a.quiet) c.mute else c.ink))
        }
    }

    @Composable
    override fun Panel(title: String, onClose: () -> Unit, top: (@Composable () -> Unit)?, backLabel: String, items: LazyListScope.() -> Unit) {
        val c = colors()
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val tablet = maxWidth >= 700.dp
            val insets = WindowInsets.safeDrawing.asPaddingValues()
            Box(
                Modifier.fillMaxSize().then(
                    if (tablet) Modifier.background(c.paper.copy(alpha = 0.6f)).clickable(remember { MutableInteractionSource() }, null, onClick = onClose)
                    else Modifier,
                ),
            ) {
                Column(
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                        .then(if (tablet) Modifier.width(440.dp) else Modifier.fillMaxWidth())
                        .mmGrid(c)
                        .then(if (tablet) Modifier.edges(c.ink, start = HV) else Modifier)
                        .clickable(remember { MutableInteractionSource() }, null) {}
                        .imePadding(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().background(c.paper).edges(c.ink, bottom = HV)
                            .padding(top = insets.calculateTopPadding()).height(68.dp).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PenButton(backLabel, onClose, back = true)
                        Spacer(Modifier.weight(1f))
                        Text(title, style = PT.serif(34.sp, c.ink, lh = 40.sp))
                    }
                    if (top != null) Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) { top() }
                    Spacer(Modifier.fillMaxWidth().height(16.dp + HL).edges(c.ink, bottom = HL))
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = insets.calculateBottomPadding() + 24.dp),
                        content = items,
                    )
                }
            }
        }
    }

    @Composable
    override fun PanelRow(primary: String, secondary: String?, trailing: String?, current: Boolean, depth: Int, onClick: () -> Unit) {
        val c0 = colors()
        val src = remember { MutableInteractionSource() }
        val pressed by src.collectIsPressedAsState()
        val c = if (pressed) c0.copy(ink = c0.paper, mute = c0.paper) else c0
        CompositionLocalProvider(LocalColors provides c) {
            Row(
                Modifier.fillMaxWidth().background(if (pressed) c0.ink else c0.paper).edges(c0.ink, bottom = HL)
                    .clickable(src, null, onClick = onClick).heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // the current entry gets the index's filled marker
                Box(Modifier.width(16.dp)) {
                    if (current) Canvas(Modifier.size(7.dp, 8.dp)) {
                        drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, size.height / 2); lineTo(0f, size.height); close() }, c.ink)
                    }
                }
                Column(Modifier.weight(1f).padding(start = (16 * depth).dp)) {
                    Text(primary, style = PT.serif(19.sp, c.ink, italic = current, lh = 22.sp))
                    if (secondary != null) Text(secondary, style = PT.mono(11.5.sp, c.mute), modifier = Modifier.padding(top = 3.dp))
                }
                if (trailing != null) Text(trailing, style = PT.mono(12.sp, c.mute), modifier = Modifier.padding(start = 12.dp))
            }
        }
    }

    @Composable
    override fun SearchField(value: String, placeholder: String, onChange: (String) -> Unit, onSubmit: () -> Unit) {
        val c = colors()
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).background(c.paper).penBox(c.ink, HL).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("find", style = PT.label(c))
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text(placeholder, style = PT.mono(14.sp, c.mute))
                BasicTextField(
                    value, onChange,
                    textStyle = PT.mono(14.sp, c.ink),
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
        val c = colors()
        Text(
            text, style = PT.mono(12.sp, c.mute, italic = true),
            modifier = Modifier.fillMaxWidth().background(c.paper).edges(c.ink, bottom = HL).padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }

    @Composable
    override fun TtsBar(playing: Boolean, label: String, onPlayPause: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, onStop: () -> Unit) {
        val c = colors()
        Column(
            Modifier.fillMaxWidth().background(c.paper).edges(c.ink, top = HV)
                .padding(start = 16.dp, end = 16.dp, top = 10.dp + HV, bottom = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("reading aloud", style = PT.label(c))
                Spacer(Modifier.width(10.dp))
                Text(label, style = PT.serif(18.sp, c.ink, italic = true), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PenButton("Previous", onPrevious, size = 12.5.sp, padX = 12.dp)
                PenButton(if (playing) "Pause" else "Play", onPlayPause, size = 12.5.sp, padX = 12.dp)
                PenButton("Next", onNext, size = 12.5.sp, padX = 12.dp)
                Spacer(Modifier.weight(1f))
                PenButton("Stop", onStop, size = 12.5.sp, padX = 12.dp)
            }
        }
    }
}
