// SPDX-License-Identifier: Apache-2.0

package dev.patrickgold.florisboard.qatt

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.ime.keyboard3.ImeActions
import dev.patrickgold.florisboard.ime.keyboard3.LocalImeController
import org.k3lp.lib.text.K3String
import org.k3lp.lib.text.asK3String
import org.k3lp.lib.text.normalize
import org.k3lp.lib.text.unicode.NormalizationForm

/** The one session of the composition field (the keyboard has one window). */
val qattCompose = ComposeSession()

private val Grey = Color(0xFF9A9A9A)
private val Ink = Color(0xFFE6E6E6)
private val Frame = Color(0xFF1F1F1F)
private val Accent = Color(0xFFD9805F)

/** The pencil: opens and closes the composition field. */
@Composable
fun ComposePencil(modifier: Modifier = Modifier) {
    BasicText(
        text = if (qattCompose.open) "✕" else "✎",
        modifier = modifier
            .size(40.dp)
            .clickable { qattCompose.toggleField() }
            .padding(8.dp),
        style = TextStyle(fontSize = 22.sp, color = Grey),
    )
}

/** The candidate bar: while the composition field is open it replaces the suggestions of FlorisBoard. */
@Composable
fun QattCandidates(normal: @Composable () -> Unit) {
    if (!qattCompose.open) { normal(); return }
    val s = qattCompose.slots()
    Row(Modifier.fillMaxWidth().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
        Slot(s.left, Modifier.weight(1f), onLong = null)
        Slot(s.middle, Modifier.weight(1f), onLong = if (s.more.size > 1) ({ qattCompose.moreOpen = !qattCompose.moreOpen }) else null)
        Slot(s.right, Modifier.weight(1f), onLong = null)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Slot(c: Candidate?, modifier: Modifier, onLong: (() -> Unit)?) {
    Box(
        modifier
            .fillMaxHeight()
            .then(
                if (c == null) Modifier
                else Modifier.combinedClickable(onClick = { qattCompose.pick(c) }, onLongClick = onLong)
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (c != null) {
            BasicText(
                text = QattData.styled(c.text),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontSize = 22.sp, color = Ink, textAlign = TextAlign.Center),
            )
        }
    }
}

/**
 * The composition field, a floating frame above the keyboard: the text, below it the round switches PA, 喃 and QATT (which kinds of candidates there are) on the left and the
 * send button on the right. While it is open, everything the keyboard types goes into it (the emit interceptor of the controller); the send button inserts the text into the app.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComposePanel() {
    val imeController = LocalImeController.current
    DisposableEffect(Unit) {
        imeController.setEmitInterceptor { value ->
            when {
                value == ImeActions.Backspace -> qattCompose.backspace()
                value == ImeActions.Enter -> qattCompose.enter()
                value is K3String -> qattCompose.type(value.normalize(NormalizationForm.NFC).toText())
            }
        }
        onDispose { imeController.setEmitInterceptor(null) }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Frame)
            .border(BorderStroke(1.dp, Color(0x33FFFFFF)), RoundedCornerShape(26.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        if (qattCompose.moreOpen) {
            FlowRow(
                Modifier.fillMaxWidth().heightIn(max = 130.dp).verticalScroll(rememberScrollState()).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (c in qattCompose.slots().more) {
                    BasicText(
                        text = QattData.styled(c.text),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0x22FFFFFF))
                            .clickable { qattCompose.pick(c) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        style = TextStyle(fontSize = 22.sp, color = Ink),
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 40.dp).heightIn(max = 110.dp).verticalScroll(rememberScrollState(), reverseScrolling = true)) {
            if (qattCompose.text.isEmpty()) {
                BasicText("…", style = TextStyle(fontSize = 20.sp, color = Grey))
            } else {
                BasicText(QattData.styled(qattCompose.text + "▏"), style = TextStyle(fontSize = 20.sp, color = Ink))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Toggle("PA", qattCompose.pa) { qattCompose.togglePa() }
            Toggle("喃", qattCompose.nom) { qattCompose.toggleNom() }
            Toggle("QATT", qattCompose.qatt) { qattCompose.toggleQatt() }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Accent)
                    .clickable {
                        val t = qattCompose.text
                        if (t.isNotEmpty()) {
                            imeController.updateStateBlocking { emitText(t.asK3String()) }
                            qattCompose.clear()
                            qattCompose.close()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                BasicText("↑", style = TextStyle(fontSize = 26.sp, color = Color(0xFF2A1A12)))
            }
        }
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(40.dp)
            .defaultMinSize(minWidth = 40.dp)
            .clip(CircleShape)
            .background(if (on) Color(0x33FFFFFF) else Color.Transparent)
            .border(BorderStroke(1.dp, if (on) Color(0x88FFFFFF) else Color(0x33FFFFFF)), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = TextStyle(fontSize = 15.sp, color = if (on) Ink else Grey))
    }
}
