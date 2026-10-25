package app.reader.looks.radar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** Radar's kit: hairline boxes on a coordinate grid, segmented bars, switches, a reticle slider thumb. */
object RadarKit : Kit {

    /** The coordinate grid behind settings: a line every 24dp. */
    private fun Modifier.coordinateGrid(color: androidx.compose.ui.graphics.Color) = drawBehind {
        val step = 24.dp.toPx(); val w = 1.dp.toPx()
        val col = color.copy(alpha = 0.45f)
        var x = w / 2
        while (x < size.width) { drawLine(col, Offset(x, 0f), Offset(x, size.height), w); x += step }
        var y = w / 2
        while (y < size.height) { drawLine(col, Offset(0f, y), Offset(size.width, y), w); y += step }
    }

    @Composable
    override fun SettingsFrame(title: String, backLabel: String, onBack: () -> Unit, preview: @Composable () -> Unit, form: @Composable ColumnScope.() -> Unit) {
        val c = colors()
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        BoxWithConstraints(Modifier.fillMaxSize().background(c.paper).coordinateGrid(c.grid)) {
            val twoColumn = maxWidth >= 1000.dp && maxWidth > maxHeight
            val tablet = maxWidth >= 700.dp
            val side = when { twoColumn -> 40.dp; tablet -> maxOf(16.dp, (maxWidth - 680.dp) / 2); else -> 16.dp }
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().background(c.paper).hairline(c.ink, bottom = true)
                        .padding(top = insets.calculateTopPadding()).height(if (twoColumn) 76.dp else 68.dp).padding(horizontal = side),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    BackBtn(backLabel, onBack)
                    Text(disp(title), style = RS.display(c, if (twoColumn) 28.sp else 24.sp, 1.1f))
                }
                if (twoColumn) {
                    Row(Modifier.fillMaxSize().padding(horizontal = side), horizontalArrangement = Arrangement.spacedBy(44.dp)) {
                        Column(
                            Modifier.widthIn(min = 400.dp, max = 480.dp).fillMaxHeight().verticalScroll(rememberScrollState())
                                .padding(top = 6.dp, bottom = insets.calculateBottomPadding() + 64.dp),
                            content = form,
                        )
                        Box(Modifier.weight(1f).fillMaxHeight().padding(top = 32.dp, bottom = insets.calculateBottomPadding() + 32.dp)) { preview() }
                    }
                } else {
                    Box(Modifier.fillMaxWidth().background(c.paper).padding(horizontal = side).padding(top = 14.dp, bottom = 12.dp).height(160.dp)) { preview() }
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = side)
                            .padding(bottom = insets.calculateBottomPadding() + 64.dp),
                        content = form,
                    )
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
                Modifier.fillMaxSize().background(c.paper).border(1.dp, c.rule)
                    .padding(vertical = if (big) 48.dp else 18.dp, horizontal = if (big) 38.dp else 0.dp),
            ) { content() }
            Text(
                "preview", style = RS.mono(c, 10.5.sp, 300, c.mute, lh = 12.sp),
                modifier = Modifier.align(Alignment.TopEnd).offset(x = (-14).dp, y = (-7).dp).background(c.paper).padding(horizontal = 4.dp),
            )
        }
    }

    @Composable
    override fun Group(title: String, hint: String?, content: @Composable ColumnScope.() -> Unit) {
        val c = colors()
        Box(Modifier.fillMaxWidth().padding(top = 26.dp)) {
            Column(Modifier.fillMaxWidth().background(c.paper).border(1.dp, c.rule).padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)) {
                if (hint != null) Text(hint, style = RS.mono(c, 11.5.sp, 300, c.mute, lh = 17.sp), modifier = Modifier.padding(vertical = 2.dp))
                // each row draws a dashed rule on top; hide the first one
                Column(
                    Modifier.fillMaxWidth().drawWithContent {
                        drawContent(); drawRect(c.paper, Offset.Zero, Size(size.width, 1.5.dp.toPx()))
                    },
                    content = content,
                )
            }
            Text(
                title, style = RS.mono(c, 12.sp, 500, lh = 18.sp, ls = (-0.01).em),
                modifier = Modifier.offset(x = 12.dp, y = (-9).dp).background(c.paper).padding(horizontal = 8.dp),
            )
        }
    }

    /** A settings row: 12dp padding, at least 60dp, a dashed rule above. */
    @Composable
    private fun RowBox(content: @Composable ColumnScope.() -> Unit) {
        val c = colors()
        Column(Modifier.fillMaxWidth().hairline(c.rule, top = true, dashed = true).heightIn(min = 60.dp).padding(vertical = 12.dp), content = content)
    }

    @Composable private fun Lbl(text: String, modifier: Modifier = Modifier) {
        val c = colors()
        Text(text, style = RS.mono(c, 14.sp, lh = 18.sp, ls = (-0.015).em), modifier = modifier)
    }

    @Composable private fun Hint(text: String, modifier: Modifier = Modifier) {
        val c = colors()
        Text(text, style = RS.mono(c, 11.5.sp, 300, c.mute, lh = 17.sp), modifier = modifier)
    }

    @Composable
    override fun Choice(label: String?, options: List<ChoiceOption>, selected: Int, hint: String?, onSelect: (Int) -> Unit) {
        val c = colors()
        RowBox {
            if (label != null) Lbl(label, Modifier.padding(bottom = 10.dp))
            if (options.any { it.font != null }) {
                Column {
                    options.forEachIndexed { i, o -> FontOption(o, i == selected, i) { onSelect(i) } }
                }
            } else {
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).height(IntrinsicSize.Min).border(1.dp, c.rule)) {
                    options.forEachIndexed { i, o ->
                        val sel = i == selected
                        Box(
                            Modifier.weight(1f).fillMaxHeight()
                                .drawBehind { if (i > 0) drawLine(c.rule, Offset(0.5.dp.toPx(), 0f), Offset(0.5.dp.toPx(), size.height), 1.dp.toPx()) }
                                .background(if (sel) c.ink else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable { onSelect(i) }
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                o.label, style = RS.mono(c, 13.sp, color = if (sel) c.paper else c.mute, lh = 16.sp, ls = (-0.02).em),
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
    private fun FontOption(o: ChoiceOption, sel: Boolean, index: Int, onClick: () -> Unit) {
        val c = colors()
        Row(
            Modifier.fillMaxWidth().offset(y = (-index).dp).heightIn(min = 50.dp)
                .background(if (sel) c.ink else c.paper).border(1.dp, if (sel) c.ink else c.rule)
                .clickable(onClick = onClick).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                o.label, style = RS.mono(c, 17.sp, color = if (sel) c.paper else c.mute, lh = 22.sp).copy(fontFamily = o.font ?: app.reader.theme.Fonts.azeretMono, fontFeatureSettings = null),
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (sel) Canvas(Modifier.size(14.dp)) {
                drawCircle(c.paper, 3.dp.toPx())
                drawCircle(c.paper, 6.5.dp.toPx(), style = Stroke(1.dp.toPx()))
            }
        }
    }

    @Composable
    override fun Toggle(label: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
        val c = colors()
        Box(Modifier.clickable(remember { MutableInteractionSource() }, null) { onChange(!checked) }) {
            RowBox {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Lbl(label)
                        if (hint != null) Hint(hint, Modifier.padding(top = 3.dp))
                    }
                    Switch(checked)
                }
            }
        }
    }

    /** 56×30 box; on, it fills with a fine hatch and the square knob slides right and goes solid. */
    @Composable
    private fun Switch(on: Boolean) {
        val c = colors()
        Canvas(Modifier.size(56.dp, 30.dp)) {
            val px = 1.dp.toPx()
            if (on) {
                var x = 0f
                while (x < size.width) { drawRect(c.rule, Offset(x, 0f), Size(px, size.height)); x += 4.dp.toPx() }
            }
            drawRect(if (on) c.ink else c.mute, Offset(px / 2, px / 2), Size(size.width - px, size.height - px), style = Stroke(px))
            val k = Offset(if (on) 30.dp.toPx() else 4.dp.toPx(), 4.dp.toPx())
            val ks = Size(20.dp.toPx(), 20.dp.toPx())
            if (on) drawRect(c.ink, k, ks)
            else { drawRect(c.paper, k, ks); drawRect(c.mute, k + Offset(px / 2, px / 2), Size(ks.width - px, ks.height - px), style = Stroke(px)) }
        }
    }

    @Composable
    override fun Slider(label: String, valueText: String, fraction: Float, home: Float?, stepper: Boolean, detents: Int, onFraction: (Float) -> Unit, onStep: (Int) -> Unit) {
        val c = colors()
        RowBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Lbl(label, Modifier.weight(1f))
                Text(valueText, style = RS.mono(c, 14.sp, 500, lh = 18.sp))
            }
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (stepper) Step("A−") { onStep(-1) }
                Range(fraction, home, detents, onFraction, Modifier.weight(1f))
                if (stepper) Step("A+") { onStep(1) }
            }
        }
    }

    @Composable
    private fun Step(label: String, onClick: () -> Unit) {
        val c = colors()
        val src = remember { MutableInteractionSource() }
        val pressed by src.collectIsPressedAsState()
        Box(
            Modifier.size(44.dp).background(if (pressed) c.ink else c.paper).border(1.dp, c.ink).clickable(src, null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Text(label, style = RS.mono(c, 13.sp, color = if (pressed) c.paper else c.ink, lh = 16.sp)) }
    }

    /** A 2dp track (ink read, dashed rule to come), a crosshair thumb, detent ticks and the home marker below. */
    @Composable
    private fun Range(fraction: Float, home: Float?, detents: Int, onFraction: (Float) -> Unit, modifier: Modifier) {
        val c = colors()
        Canvas(
            modifier.height(52.dp)
                .pointerInput(Unit) {
                    val inset = 7.dp.toPx()
                    detectTapGestures { onFraction(((it.x - inset) / (size.width - 2 * inset)).coerceIn(0f, 1f)) }
                }
                .pointerInput(Unit) {
                    val inset = 7.dp.toPx()
                    detectHorizontalDragGestures { ch, _ -> onFraction(((ch.position.x - inset) / (size.width - 2 * inset)).coerceIn(0f, 1f)) }
                },
        ) {
            val px = 1.dp.toPx()
            val inset = 7.dp.toPx()
            val tw = size.width - 2 * inset
            val cy = 17.dp.toPx()
            val tx = inset + fraction.coerceIn(0f, 1f) * tw
            var x = 0f
            while (x < size.width) { drawRect(c.rule, Offset(x, cy - px), Size(minOf(3.dp.toPx(), size.width - x), 2 * px)); x += 6.dp.toPx() }
            drawRect(c.ink, Offset(0f, cy - px), Size(tx, 2 * px))
            // detents below
            val tickTop = 40.dp.toPx()
            val n = detents.coerceAtLeast(1)
            for (i in 0..n) {
                val dx = inset + tw * i / n.toFloat()
                drawLine(c.mute.copy(alpha = c.mute.alpha * 0.7f), Offset(dx, tickTop), Offset(dx, tickTop + 6.dp.toPx()), px)
            }
            if (home != null) {
                val hx = inset + home * tw
                val b = size.height
                drawPath(Path().apply { moveTo(hx - 4.dp.toPx(), b); lineTo(hx + 4.dp.toPx(), b); lineTo(hx, b - 5.dp.toPx()); close() }, c.ink)
            }
            // thumb: 14×30, paper, ink box, crosshair
            val tl = Offset(tx - 7.dp.toPx(), cy - 15.dp.toPx())
            drawRect(c.paper, tl, Size(14.dp.toPx(), 30.dp.toPx()))
            drawRect(c.ink, tl + Offset(px / 2, px / 2), Size(14.dp.toPx() - px, 30.dp.toPx() - px), style = Stroke(px))
            drawLine(c.ink, Offset(tx, tl.y), Offset(tx, tl.y + 30.dp.toPx()), px)
            drawLine(c.ink, Offset(tl.x, cy), Offset(tl.x + 14.dp.toPx(), cy), px)
        }
    }

    @Composable
    override fun Action(label: String, value: String?, button: String, onClick: () -> Unit) {
        val c = colors()
        RowBox {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Lbl(label)
                    if (value != null) Text(value, style = RS.mono(c, 11.5.sp, 300, c.ink.copy(alpha = 0.85f), lh = 17.sp), modifier = Modifier.padding(top = 3.dp))
                }
                Blk(button, onClick, padH = 14.dp)
            }
        }
    }

    @Composable
    override fun LookPicker(cards: List<LookCard>, current: String, onPick: (String) -> Unit) {
        CardRows(cards, 10.dp, Modifier.padding(top = 6.dp, bottom = 12.dp)) { card, m -> LookCardView(card, card.id == current, m) { onPick(card.id) } }
    }

    @Composable
    private fun LookCardView(card: LookCard, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
        val c = colors()
        Column(modifier.clickable(onClick = onClick).padding(top = 4.dp, bottom = 4.dp)) {
            Box(
                Modifier.fillMaxWidth()
                    .then(if (selected) Modifier.border(2.dp, c.ink).padding(5.dp) else Modifier.padding(2.dp))
                    .aspectRatio(412f / 620f).border(1.dp, c.ink.copy(alpha = 0.3f)).padding(1.dp),
            ) { card.thumb() }
            Text(card.name, style = RS.mono(c, 13.sp, if (selected) 700 else 400, lh = 16.sp), modifier = Modifier.padding(top = 8.dp), maxLines = 2)
            Text(card.sub, style = RS.mono(c, 11.5.sp, color = c.ink.copy(alpha = 0.65f), lh = 15.sp), maxLines = 2, modifier = Modifier.padding(top = 2.dp))
        }
    }

    @Composable
    override fun Sheet(title: String, subtitle: String?, actions: List<SheetAction>, onDismiss: () -> Unit) {
        SheetFrame(title, subtitle, onDismiss, keyboard = false) {
            Spacer(Modifier.height(14.dp))
            actions.forEach { SheetButton(it) }
        }
    }

    /**
     * The Sheet's frame holding labelled hairline fields; the focused field goes ink with reticle
     * corners. Save is a solid ink bar, Cancel a quiet sheet row.
     */
    @Composable
    override fun Form(title: String, subtitle: String?, fields: List<FormField>, saveLabel: String, onSave: () -> Unit, onDismiss: () -> Unit) {
        SheetFrame(title, subtitle, onDismiss, keyboard = true) {
            Spacer(Modifier.height(10.dp))
            fields.forEach { FormInput(it) }
            Spacer(Modifier.height(16.dp))
            SaveBar(saveLabel, onSave)
            Spacer(Modifier.height(4.dp))
            SheetButton(SheetAction("Cancel", quiet = true, onClick = onDismiss))
        }
    }

    /**
     * Shared by [Sheet] and [Form]: a paper scrim, and a bottom panel under an ink hairline. Where the
     * panel doesn't span the screen (tablets) it gets rule sides and the reticle's corner brackets.
     * With [keyboard] it rides above the IME and scrolls.
     */
    @Composable
    private fun SheetFrame(title: String, subtitle: String?, onDismiss: () -> Unit, keyboard: Boolean, content: @Composable ColumnScope.() -> Unit) {
        Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            val c = colors()
            val insets = WindowInsets.safeDrawing.asPaddingValues()
            BoxWithConstraints(
                Modifier.fillMaxSize().background(c.paper.copy(alpha = 0.7f))
                    .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss)
                    .padding(top = insets.calculateTopPadding())
                    .then(if (keyboard) Modifier.imePadding() else Modifier),
            ) {
                val floating = maxWidth > 520.dp
                Column(
                    Modifier.align(Alignment.BottomCenter).widthIn(max = 520.dp).fillMaxWidth()
                        .background(c.paper)
                        .then(if (floating) Modifier.brackets(c.ink).drawBehind {
                            val w = 1.dp.toPx()
                            drawLine(c.rule, Offset(w / 2, 0f), Offset(w / 2, size.height), w)
                            drawLine(c.rule, Offset(size.width - w / 2, 0f), Offset(size.width - w / 2, size.height), w)
                        } else Modifier)
                        .hairline(c.ink, top = true)
                        .clickable(remember { MutableInteractionSource() }, null) {}
                        .then(if (keyboard) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                        .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp + insets.calculateBottomPadding()),
                ) {
                    Text(title, style = RS.mono(c, 16.sp, 500, lh = 21.6.sp, ls = (-0.03).em), modifier = Modifier.padding(end = 20.dp))
                    if (subtitle != null) Text(subtitle, style = RS.mono(c, 12.sp, 300, c.mute, lh = 17.sp), modifier = Modifier.padding(top = 6.dp))
                    content()
                }
            }
        }
    }

    @Composable
    private fun FormInput(f: FormField) {
        val c = colors()
        val src = remember { MutableInteractionSource() }
        val focused by src.collectIsFocusedAsState()
        Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Text(f.label.lowercase(), style = RS.label(c, 12.sp), modifier = Modifier.padding(bottom = 6.dp))
            Box(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).border(1.dp, if (focused) c.ink else c.rule)
                    .then(if (focused) Modifier.brackets(c.ink, len = 8.dp) else Modifier)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    f.value, f.onChange,
                    textStyle = RS.mono(c, 14.sp, lh = 18.sp, ls = (-0.015).em),
                    singleLine = true,
                    cursorBrush = SolidColor(c.ink),
                    interactionSource = src,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (f.hint != null) Hint(f.hint, Modifier.padding(top = 6.dp))
        }
    }

    /** The primary action: a solid ink bar, outlined while pressed. */
    @Composable
    private fun SaveBar(label: String, onClick: () -> Unit) {
        val c = colors()
        val src = remember { MutableInteractionSource() }
        val pressed by src.collectIsPressedAsState()
        Box(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).background(if (pressed) c.paper else c.ink).border(1.dp, c.ink)
                .clickable(src, null, onClick = onClick).padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(label, style = RS.mono(c, 14.sp, 500, color = if (pressed) c.ink else c.paper, lh = 18.sp))
        }
    }

    @Composable
    private fun SheetButton(a: SheetAction) {
        val c = colors()
        val src = remember { MutableInteractionSource() }
        val pressed by src.collectIsPressedAsState()
        Box(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).hairline(c.rule, top = true)
                .background(if (pressed) c.ink else androidx.compose.ui.graphics.Color.Transparent)
                .clickable(src, null, onClick = a.onClick).padding(horizontal = 2.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(a.label, style = RS.mono(c, 14.sp, color = if (pressed) c.paper else if (a.quiet) c.mute else c.ink, lh = 18.sp))
        }
    }

    @Composable
    override fun Panel(title: String, onClose: () -> Unit, top: (@Composable () -> Unit)?, backLabel: String, items: LazyListScope.() -> Unit) {
        val c = colors()
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val tablet = maxWidth >= 700.dp
            Box(
                Modifier.fillMaxSize().then(
                    if (tablet) Modifier.background(c.paper.copy(alpha = 0.6f)).clickable(remember { MutableInteractionSource() }, null, onClick = onClose)
                    else Modifier,
                ),
            ) {
                Column(
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                        .then(if (tablet) Modifier.width(440.dp) else Modifier.fillMaxWidth())
                        .background(c.paper)
                        .then(if (tablet) Modifier.border(1.dp, c.rule).brackets(c.ink) else Modifier)
                        .clickable(remember { MutableInteractionSource() }, null) {}
                        .imePadding(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().hairline(c.ink, bottom = true).padding(top = insets.calculateTopPadding())
                            .height(68.dp).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        BackBtn(backLabel, onClose)
                        Text(disp(title), style = RS.display(c, 24.sp, 1.1f))
                    }
                    if (top != null) Box(Modifier.padding(horizontal = 16.dp).padding(top = 14.dp)) { top() }
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
        val src = remember { MutableInteractionSource() }
        val pressed by src.collectIsPressedAsState()
        val c = if (pressed) c0.copy(ink = c0.paper, mute = c0.paper, rule = c0.paper) else c0
        CompositionLocalProvider(LocalColors provides c) {
            Row(
                Modifier.fillMaxWidth().hairline(c0.rule, bottom = true)
                    .background(if (pressed) c0.ink else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(src, null, onClick = onClick)
                    .heightIn(min = 52.dp).padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(22.dp), contentAlignment = Alignment.CenterStart) { if (current) SortBlip(true) }
                Column(Modifier.weight(1f).padding(start = (14 * depth).dp)) {
                    Text(primary, style = RS.mono(c, 13.5.sp, if (current) 600 else 400, lh = 18.sp, ls = (-0.02).em))
                    if (secondary != null) Text(secondary, style = RS.mono(c, 11.5.sp, 300, c.mute, lh = 16.sp), modifier = Modifier.padding(top = 2.dp))
                }
                if (trailing != null) Text(trailing, style = RS.mono(c, 12.sp, 300, if (current) c.ink else c.mute), modifier = Modifier.padding(start = 12.dp))
            }
        }
    }

    @Composable
    override fun SearchField(value: String, placeholder: String, onChange: (String) -> Unit, onSubmit: () -> Unit) {
        val c = colors()
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).border(1.dp, c.ink).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Glyph(Glyphs.Search, c.mute, 14.dp)
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text(placeholder, style = RS.mono(c, 13.sp, 300, c.mute))
                BasicTextField(
                    value, onChange,
                    textStyle = RS.mono(c, 13.sp),
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
        Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Glyph(Glyphs.End, c.mute, 14.dp)
            Text(text, style = RS.label(c, 12.sp))
        }
    }

    @Composable
    override fun TtsBar(playing: Boolean, label: String, onPlayPause: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, onStop: () -> Unit) {
        val c = colors()
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        Column(
            Modifier.fillMaxWidth().background(c.paper).hairline(c.ink, top = true)
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp + insets.calculateBottomPadding()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Glyph(Glyphs.Aloud, c.ink, 12.dp)
                Text("reading aloud", style = RS.label(c))
                Text(label, style = RS.mono(c, 12.5.sp, 300, c.ink, ls = (-0.015).em), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconBlk(Glyphs.Prev, "Previous", onPrevious)
                Blk(if (playing) "Pause" else "Play", onPlayPause, padH = 14.dp, icon = { Glyph(if (playing) Glyphs.Pause else Glyphs.Play, it, 12.dp) })
                IconBlk(Glyphs.Next, "Next", onNext)
                Spacer(Modifier.weight(1f))
                Blk("Stop", onStop, padH = 14.dp, icon = { Glyph(Glyphs.Stop, it, 12.dp) })
            }
        }
    }

    @Composable
    private fun IconBlk(g: Glyphs, desc: String, onClick: () -> Unit) {
        val c = colors()
        val src = remember { MutableInteractionSource() }
        val pressed by src.collectIsPressedAsState()
        Box(
            Modifier.size(44.dp).background(if (pressed) c.ink else c.paper).border(1.dp, c.ink)
                .clickable(src, null, onClick = onClick)
                .then(Modifier.semanticsLabel(desc)),
            contentAlignment = Alignment.Center,
        ) { Glyph(g, if (pressed) c.paper else c.ink, 14.dp) }
    }

    private fun Modifier.semanticsLabel(s: String) = semantics { contentDescription = s }
}
