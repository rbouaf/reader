package app.reader.looks

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.font.FontFamily

/**
 * The building blocks a look draws its own way. The shared screens in `looks/common`
 * (Settings, the long-press sheet, Contents, Search, the read-aloud bar) are written once
 * against this interface, the way `kit.js` gave every mockup the same settings markup.
 *
 * Kits only draw. Haptics, state and value clamping happen in the shared code that calls them.
 * Layout must follow the incoming constraints (use BoxWithConstraints), never the window size,
 * so screens also render inside the Style picker's thumbnails and in screenshot tests.
 */
interface Kit {
    /** Settings screen: a header with a back button, the form, and the live preview
     *  (pinned above the form on phones, beside it on landscape tablets). */
    @Composable fun SettingsFrame(
        title: String,
        backLabel: String,
        onBack: () -> Unit,
        preview: @Composable () -> Unit,
        form: @Composable ColumnScope.() -> Unit,
    )

    /** A titled group of settings rows. */
    @Composable fun Group(title: String, hint: String?, content: @Composable ColumnScope.() -> Unit)

    /** A set of mutually exclusive options (radio set / segmented control). */
    @Composable fun Choice(label: String?, options: List<ChoiceOption>, selected: Int, hint: String?, onSelect: (Int) -> Unit)

    /** An on/off row. */
    @Composable fun Toggle(label: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit)

    /**
     * A slider row. [fraction] is 0..1; [home] is the neutral point (0..1) or null.
     * [onFraction] reports raw drag positions (the caller snaps and plays detents).
     * With [stepper], also draw − / + buttons that call [onStep] with -1 or +1.
     * [detents] is how many steps the range has (e.g. 10 for warmth, 14 for size): draw that many
     * graduations if the look draws any.
     */
    @Composable fun Slider(
        label: String,
        valueText: String,
        fraction: Float,
        home: Float?,
        stepper: Boolean,
        detents: Int,
        onFraction: (Float) -> Unit,
        onStep: (Int) -> Unit,
    )

    /** A row with a value and a button, e.g. the books folder with "Change". */
    @Composable fun Action(label: String, value: String?, button: String, onClick: () -> Unit)

    /** The Style picker: one card per look, each with a live thumbnail. */
    @Composable fun LookPicker(cards: List<LookCard>, current: String, onPick: (String) -> Unit)

    /** A frame for the reading preview in Settings ([content] draws the sample page text). */
    @Composable fun PreviewFrame(content: @Composable () -> Unit)

    /**
     * A modal form with text fields (long-press → Edit details). Draw it like [Sheet]: same
     * frame, the look's field style, a primary [saveLabel] button and a quiet Cancel.
     * The default is a plain version; looks override it.
     */
    @Composable fun Form(title: String, subtitle: String?, fields: List<FormField>, saveLabel: String, onSave: () -> Unit, onDismiss: () -> Unit) =
        app.reader.looks.common.PlainForm(title, subtitle, fields, saveLabel, onSave, onDismiss)

    /** A modal action sheet (long-press on a book). */
    @Composable fun Sheet(title: String, subtitle: String?, actions: List<SheetAction>, onDismiss: () -> Unit)

    /**
     * A panel over the book for Contents and Search: full screen on phones, a side panel
     * on tablets. [top] sits under the title (the search field); [items] is a lazy list.
     * [backLabel] names where closing returns to.
     */
    @Composable fun Panel(title: String, onClose: () -> Unit, top: (@Composable () -> Unit)?, backLabel: String = "Book", items: LazyListScope.() -> Unit)

    /** A row inside a [Panel]: a contents entry (indented by [depth]) or a search hit. */
    @Composable fun PanelRow(primary: String, secondary: String?, trailing: String?, current: Boolean, depth: Int, onClick: () -> Unit)

    /** A one-line text field for search. */
    @Composable fun SearchField(value: String, placeholder: String, onChange: (String) -> Unit, onSubmit: () -> Unit)

    /** Quiet text inside a panel: "No results", "Searching…". */
    @Composable fun PanelMessage(text: String)

    /** The strip shown over the book while reading aloud. */
    @Composable fun TtsBar(playing: Boolean, label: String, onPlayPause: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, onStop: () -> Unit)
}

@Immutable
data class ChoiceOption(val label: String, val font: FontFamily? = null)

/** One text field in a [Kit.Form]. [hint] is quiet text under it (e.g. "Renames the file in the folder"). */
@Immutable
data class FormField(val label: String, val value: String, val hint: String? = null, val onChange: (String) -> Unit)

@Immutable
data class SheetAction(val label: String, val quiet: Boolean = false, val onClick: () -> Unit)

@Immutable
data class LookCard(val id: String, val name: String, val sub: String, val thumb: @Composable () -> Unit)
