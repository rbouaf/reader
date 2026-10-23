package app.reader.looks.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.reader.looks.FormField
import app.reader.theme.LocalColors

/** A look-neutral form, used until a look draws its own [app.reader.looks.Kit.Form]. */
@Composable
fun PlainForm(title: String, subtitle: String?, fields: List<FormField>, saveLabel: String, onSave: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalColors.current
    val base = TextStyle(color = c.ink, fontSize = 16.sp)
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        Box(
            Modifier.fillMaxSize().background(c.paper.copy(alpha = 0.75f))
                .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss).imePadding(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxWidth().background(c.paper).border(1.dp, c.ink)
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp + insets.calculateBottomPadding()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(title, style = base.copy(fontWeight = FontWeight.Bold))
                if (subtitle != null) Text(subtitle, style = base.copy(color = c.mute, fontSize = 13.sp))
                fields.forEach { f ->
                    Column {
                        Text(f.label, style = base.copy(color = c.mute, fontSize = 13.sp))
                        BasicTextField(
                            f.value, f.onChange, singleLine = true, textStyle = base, cursorBrush = SolidColor(c.ink),
                            modifier = Modifier.fillMaxWidth().border(1.dp, c.rule).padding(10.dp),
                        )
                        if (f.hint != null) Text(f.hint, style = base.copy(color = c.mute, fontSize = 12.sp))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.heightIn(min = 44.dp).background(c.ink).clickable(onClick = onSave).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                        Text(saveLabel, style = base.copy(color = c.paper, fontWeight = FontWeight.Bold))
                    }
                    Box(Modifier.heightIn(min = 44.dp).clickable(onClick = onDismiss).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                        Text("Cancel", style = base.copy(color = c.mute))
                    }
                }
            }
        }
    }
}
