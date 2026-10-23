package app.reader.looks.trace

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.LaunchedEffect
import app.reader.looks.ChoiceOption
import app.reader.looks.Kit
import app.reader.looks.LookCard
import app.reader.looks.SheetAction
import app.reader.theme.LocalColors

/** Trace's kit, the same text form as Readout's (the mockup shares it): Radio sets `( ) (*)`, checkboxes `[ ] [x]`, text-bar sliders, `[ buttons ]`. */
object TraceKit : Kit {

    @Composable
    private fun Sized(content: @Composable (tablet: Boolean, twoColumn: Boolean) -> Unit) {
        BoxWithConstraints(Modifier.fillMaxSize().background(colors().paper)) {
            val g = Grid.of(maxWidth)
            CompositionLocalProvider(LocalGrid provides g) {
                content(maxWidth >= 700.dp, maxWidth >= 1000.dp && maxWidth > maxHeight)
            }
        }
    }

    @Composable
    override fun SettingsFrame(title: String, backLabel: String, onBack: () -> Unit, preview: @Composable () -> Unit, form: @Composable ColumnScope.() -> Unit) {
        Sized { tablet, twoColumn ->
            val g = grid(); val c = colors()
            val insets = WindowInsets.safeDrawing.asPaddingValues()
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val side = when {
                    twoColumn -> g.ch(4)
                    tablet -> maxOf(g.ch(4), (maxWidth - g.ch(80)) / 2)
                    else -> g.ch(2)
                }
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().background(c.paper).doubleRule(c.ink, bottom = true)
                            .padding(top = insets.calculateTopPadding()).height(g.lines(3)).padding(horizontal = side),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(backLabel, onBack, pre = "< ")
                        Spacer(Modifier.weight(1f))
                        Text(title, style = RT.title(g, c))
                    }
                    if (twoColumn) {
                        Row(Modifier.fillMaxSize().padding(horizontal = side), horizontalArrangement = Arrangement.spacedBy(g.ch(5))) {
                            Column(
                                Modifier.width(g.ch(58)).fillMaxHeight().verticalScroll(rememberScrollState())
                                    .padding(bottom = insets.calculateBottomPadding() + g.lines(3)),
                                content = form,
                            )
                            Box(Modifier.weight(1f).fillMaxHeight().padding(top = g.lines(1.5f), bottom = g.lines(2))) { preview() }
                        }
                    } else {
                        // phones and portrait tablets: the preview stays pinned above the scrolling form
                        Box(Modifier.padding(horizontal = side).padding(vertical = g.lines(1)).height(g.lines(if (tablet) 10 else 8))) { preview() }
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = side)
                                .padding(bottom = insets.calculateBottomPadding() + g.lines(3)),
                            content = form,
                        )
                    }
                }
            }
        }
    }

    @Composable
    override fun PreviewFrame(content: @Composable () -> Unit) {
        val g = grid(); val c = colors()
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().ruledBox(c.rule, double = false).padding(vertical = g.lines(1))) { content() }
            Text(
                "preview", style = RT.label(g, c),
                modifier = Modifier.align(Alignment.TopEnd).offset(x = -g.ch(1), y = -g.lines(0.5f)).background(c.paper).padding(horizontal = g.ch(1)),
            )
        }
    }

    @Composable
    override fun Group(title: String, hint: String?, content: @Composable ColumnScope.() -> Unit) {
        val g = grid(); val c = colors()
        Box(Modifier.fillMaxWidth().padding(top = g.lines(1.5f))) {
            Column(Modifier.fillMaxWidth().ruledBox(c.rule, double = false).padding(top = g.lines(0.5f), start = g.ch(2) - 1.dp, end = g.ch(2) - 1.dp, bottom = g.lines(0.5f))) {
                if (hint != null) Text(hint, style = RT.dim(g, c), modifier = Modifier.padding(top = g.lines(0.5f)))
                content()
            }
            Text(
                title, style = RT.title(g, c),
                modifier = Modifier.offset(x = g.ch(1), y = -g.lines(0.5f)).background(c.paper).padding(horizontal = g.ch(1)),
            )
        }
    }

    @Composable
    private fun Label(text: String) {
        val g = grid(); val c = colors()
        Text(
            buildAnnotatedString { append(text); withStyle(SpanStyle(color = c.mute)) { append(":") } },
            style = RT.base(g, c).copy(fontStyle = FontStyle.Italic),
        )
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun Choice(label: String?, options: List<ChoiceOption>, selected: Int, hint: String?, onSelect: (Int) -> Unit) {
        val g = grid(); val c = colors()
        Column(Modifier.padding(vertical = g.lines(0.5f))) {
            if (label != null) Label(label)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(g.ch(2))) {
                options.forEachIndexed { i, o ->
                    val sel = i == selected
                    Row(Modifier.heightIn(min = 44.dp).clickable { onSelect(i) }, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (sel) "(*) " else "( ) ", style = RT.base(g, c).copy(color = if (sel) c.ink else c.mute))
                        Text(
                            o.label,
                            style = if (o.font != null) RT.base(g, c).copy(fontFamily = o.font, fontSize = 16.sp, color = if (sel) c.ink else c.mute, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                            else if (sel) RT.title(g, c) else RT.base(g, c).copy(color = c.mute),
                        )
                    }
                }
            }
            if (hint != null) Text(hint, style = RT.dim(g, c), modifier = Modifier.padding(top = 2.dp))
        }
    }

    @Composable
    override fun Toggle(label: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
        val g = grid(); val c = colors()
        Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = g.lines(0.5f)).heightIn(min = 32.dp)) {
            Text(if (checked) "[x]" else "[ ]", style = if (checked) RT.title(g, c) else RT.base(g, c).copy(color = c.mute), modifier = Modifier.width(g.ch(4)))
            Column(Modifier.weight(1f)) {
                Text(label, style = RT.base(g, c).copy(fontStyle = FontStyle.Italic))
                if (hint != null) Text(hint, style = RT.dim(g, c))
            }
        }
    }

    @Composable
    override fun Slider(label: String, valueText: String, fraction: Float, home: Float?, stepper: Boolean, detents: Int, onFraction: (Float) -> Unit, onStep: (Int) -> Unit) {
        val g = grid(); val c = colors()
        Column(Modifier.padding(vertical = g.lines(0.5f))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(label)
                Spacer(Modifier.weight(1f))
                Text(valueText, style = RT.title(g, c))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(g.ch(1))) {
                if (stepper) StepButton("A−") { onStep(-1) }
                TextSlider(fraction, onFraction, Modifier.weight(1f))
                if (stepper) StepButton("A+") { onStep(1) }
            }
        }
    }

    @Composable
    private fun StepButton(label: String, onClick: () -> Unit) {
        val g = grid(); val c = colors()
        val src = remember { MutableInteractionSource() }
        val pressed by src.collectIsPressedAsState()
        Box(Modifier.heightIn(min = 44.dp).clickable(src, null, onClick = onClick), contentAlignment = Alignment.Center) {
            Text(
                buildAnnotatedString { withStyle(SpanStyle(color = c.mute)) { append("[") }; append(label); withStyle(SpanStyle(color = c.mute)) { append("]") } },
                style = RT.base(g, c).copy(color = if (pressed) c.paper else c.ink),
                modifier = if (pressed) Modifier.background(c.ink) else Modifier,
            )
        }
    }

    /** `////////▌.......`: the thumb is a block cursor; drag or tap anywhere along it. */
    @Composable
    private fun TextSlider(fraction: Float, onFraction: (Float) -> Unit, modifier: Modifier) {
        val g = grid(); val c = colors()
        BoxWithConstraints(
            modifier.height(44.dp)
                .pointerInput(Unit) { detectTapGestures { onFraction((it.x / size.width).coerceIn(0f, 1f)) } }
                .pointerInput(Unit) { detectHorizontalDragGestures { ch, _ -> onFraction((ch.position.x / size.width).coerceIn(0f, 1f)) } },
            contentAlignment = Alignment.CenterStart,
        ) {
            val n = (maxWidth / g.chDp).toInt().coerceAtLeast(2)
            val at = Math.round(fraction.coerceIn(0f, 1f) * (n - 1))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = c.ink)) { append("/".repeat(at)) }
                    withStyle(SpanStyle(color = c.paper, background = c.ink)) { append(" ") }
                    withStyle(SpanStyle(color = c.rule)) { append(".".repeat(n - 1 - at)) }
                },
                style = RT.base(g, c), maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
            )
        }
    }

    @Composable
    override fun Action(label: String, value: String?, button: String, onClick: () -> Unit) {
        val g = grid(); val c = colors()
        Row(Modifier.fillMaxWidth().padding(vertical = g.lines(0.5f)), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label(label)
                if (value != null) Text(value, style = RT.base(g, c))
            }
            Spacer(Modifier.width(g.ch(2)))
            TextButton(button, onClick)
        }
    }

    @Composable
    override fun LookPicker(cards: List<LookCard>, current: String, onPick: (String) -> Unit) {
        val g = grid()
        BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = g.lines(0.5f))) {
            if (maxWidth >= 500.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(g.ch(1))) {
                    cards.forEach { LookCardView(it, it.id == current, Modifier.weight(1f)) { onPick(it.id) } }
                }
            } else {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(g.ch(2))) {
                    cards.forEach { LookCardView(it, it.id == current, Modifier.width(112.dp)) { onPick(it.id) } }
                }
            }
        }
    }

    @Composable
    private fun LookCardView(card: LookCard, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
        val g = grid(); val c = colors()
        Column(modifier.clickable(onClick = onClick).padding(vertical = 4.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(412f / 620f).ruledBox(if (selected) c.ink else c.rule, double = selected).padding(if (selected) 4.dp else 2.dp)) {
                card.thumb()
            }
            Text((if (selected) "(*) " else "") + card.name, style = if (selected) RT.title(g, c) else RT.base(g, c), modifier = Modifier.padding(top = 6.dp), maxLines = 2)
            Text(card.sub, style = RT.dim(g, c), maxLines = 2)
        }
    }

    @Composable
    override fun Sheet(title: String, subtitle: String?, actions: List<SheetAction>, onDismiss: () -> Unit) {
        Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Sized { tablet, _ ->
                val g = grid(); val c = colors()
                val insets = WindowInsets.safeDrawing.asPaddingValues()
                Box(Modifier.fillMaxSize().background(c.paper.copy(alpha = 0.75f)).clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss)) {
                    Column(
                        Modifier.align(Alignment.BottomCenter).then(if (tablet) Modifier.widthIn(max = g.ch(64)) else Modifier.fillMaxWidth())
                            .background(c.paper).doubleRule(c.ink, top = true)
                            .clickable(remember { MutableInteractionSource() }, null) {}
                            .padding(start = g.ch(2), end = g.ch(2), top = g.lines(1), bottom = g.lines(1) + insets.calculateBottomPadding()),
                    ) {
                        Text(title, style = RT.title(g, c))
                        if (subtitle != null) Text(subtitle, style = RT.label(g, c), modifier = Modifier.padding(bottom = g.lines(1)))
                        actions.forEach { a -> SheetButton(a) }
                    }
                }
            }
        }
    }

    @Composable
    private fun SheetButton(a: SheetAction) {
        val g = grid(); val c = colors()
        val src = remember { MutableInteractionSource() }
        val pressed by src.collectIsPressedAsState()
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).background(if (pressed) c.ink else c.paper).clickable(src, null, onClick = a.onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("> ", style = RT.base(g, c).copy(color = if (pressed) c.paper else c.mute))
            Text(a.label, style = RT.base(g, c).copy(color = if (pressed) c.paper else if (a.quiet) c.mute else c.ink))
        }
    }

    @Composable
    override fun Panel(title: String, onClose: () -> Unit, top: (@Composable () -> Unit)?, items: LazyListScope.() -> Unit) {
        Sized { tablet, _ ->
            val g = grid(); val c = colors()
            val insets = WindowInsets.safeDrawing.asPaddingValues()
            Box(Modifier.fillMaxSize().then(if (tablet) Modifier.background(c.paper.copy(alpha = 0.6f)).clickable(remember { MutableInteractionSource() }, null, onClick = onClose) else Modifier)) {
                Column(
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                        .then(if (tablet) Modifier.width(g.ch(56)) else Modifier.fillMaxWidth())
                        .background(c.paper)
                        .then(if (tablet) Modifier.ruledBox(c.ink, double = true) else Modifier)
                        .clickable(remember { MutableInteractionSource() }, null) {}
                        .imePadding(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().doubleRule(c.ink, bottom = true).padding(top = insets.calculateTopPadding())
                            .height(g.lines(3)).padding(horizontal = g.ch(2)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton("Book", onClose, pre = "< ")
                        Spacer(Modifier.weight(1f))
                        Text(title, style = RT.title(g, c))
                    }
                    if (top != null) Box(Modifier.padding(horizontal = g.ch(2)).padding(top = g.lines(1))) { top() }
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = g.ch(2), end = g.ch(2), top = g.lines(0.5f), bottom = insets.calculateBottomPadding() + g.lines(2)),
                        content = items,
                    )
                }
            }
        }
    }

    @Composable
    override fun PanelRow(primary: String, secondary: String?, trailing: String?, current: Boolean, depth: Int, onClick: () -> Unit) {
        val g = grid(); val c0 = colors()
        val src = remember { MutableInteractionSource() }
        val pressed by src.collectIsPressedAsState()
        val c = if (pressed) c0.copy(ink = c0.paper, mute = c0.paper) else c0
        Row(
            Modifier.fillMaxWidth().background(if (pressed) c0.ink else c0.paper).clickable(src, null, onClick = onClick)
                .heightIn(min = 44.dp).padding(vertical = 6.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(if (current) ">" else " ", style = RT.title(g, c), modifier = Modifier.width(g.ch(2)))
            Column(Modifier.weight(1f).padding(start = g.ch(2 * depth))) {
                Text(primary, style = if (current) RT.title(g, c) else RT.base(g, c))
                if (secondary != null) Text(secondary, style = RT.dim(g, c))
            }
            if (trailing != null) Text(trailing, style = RT.dim(g, c), modifier = Modifier.padding(start = g.ch(2)))
        }
    }

    @Composable
    override fun SearchField(value: String, placeholder: String, onChange: (String) -> Unit, onSubmit: () -> Unit) {
        val g = grid(); val c = colors()
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Row(Modifier.fillMaxWidth().ruledBox(c.rule, double = false).padding(horizontal = g.ch(1), vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("find: ", style = RT.label(g, c))
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text(placeholder, style = RT.dim(g, c))
                BasicTextField(
                    value, onChange,
                    textStyle = RT.base(g, c),
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
        val g = grid(); val c = colors()
        Row(Modifier.padding(vertical = g.lines(1)), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = RT.dim(g, c))
            BlockCursor(Modifier.padding(start = 2.dp))
        }
    }

    @Composable
    override fun TtsBar(playing: Boolean, label: String, onPlayPause: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, onStop: () -> Unit) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val g = Grid.of(maxWidth)
            CompositionLocalProvider(LocalGrid provides g) {
                val c = colors()
                Column(Modifier.fillMaxWidth().background(c.paper).doubleRule(c.ink, top = true).padding(horizontal = g.ch(2), vertical = g.lines(0.25f))) {
                    Text("reading aloud  $label", style = RT.label(g, c), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(g.ch(2))) {
                        TextButton("<<", onPrevious)
                        TextButton(if (playing) "pause" else "play", onPlayPause)
                        TextButton(">>", onNext)
                        Spacer(Modifier.weight(1f))
                        TextButton("stop", onStop)
                    }
                }
            }
        }
    }
}
