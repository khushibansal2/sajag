package `in`.sajag.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.sajag.assess.ChoiceTask
import `in`.sajag.assess.FlagTask
import `in`.sajag.assess.QuizTask
import `in`.sajag.assess.RangeTask
import `in`.sajag.assess.SequenceTask
import `in`.sajag.assess.SweepTask
import `in`.sajag.i18n.S
import `in`.sajag.i18n.t
import `in`.sajag.ui.theme.CardAlt
import `in`.sajag.ui.theme.Ink
import `in`.sajag.ui.theme.Muted
import `in`.sajag.ui.theme.Navy
import `in`.sajag.ui.theme.Safe
import kotlin.math.roundToInt

@Composable
fun ChoiceTaskView(task: ChoiceTask, onDone: (List<String>) -> Unit) {
    val options = remember(task) { task.options.shuffled() }
    val selected = remember(task) { mutableStateListOf<String>() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { opt ->
            OptionCard(opt, opt.id in selected) {
                if (task.multi) {
                    if (opt.id in selected) selected.remove(opt.id) else selected.add(opt.id)
                } else {
                    selected.clear(); selected.add(opt.id)
                }
            }
        }
        BigButton(t(S.confirm), enabled = selected.isNotEmpty()) { onDone(selected.toList()) }
    }
}

/** Returns the tapped order and, if [SequenceTask.latencyStep] is set, the ms until that step was tapped. */
@Composable
fun SequenceTaskView(task: SequenceTask, onDone: (List<String>, Long?) -> Unit) {
    val start = remember(task) { SystemClock.elapsedRealtime() }
    val steps = remember(task) { task.steps.shuffled() }
    val order = remember(task) { mutableStateListOf<String>() }
    var stepLatency by remember(task) { mutableStateOf<Long?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(t(S.sequenceHint), color = Muted)
        steps.forEach { opt ->
            val index = order.indexOf(opt.id)
            OptionCard(opt, index >= 0, badge = if (index >= 0) "${index + 1}" else null) {
                if (index < 0) {
                    order.add(opt.id)
                    if (opt.id == task.latencyStep && stepLatency == null) {
                        stepLatency = SystemClock.elapsedRealtime() - start
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { if (order.isNotEmpty()) order.removeAt(order.lastIndex) },
                enabled = order.isNotEmpty(),
                modifier = Modifier.heightIn(min = 60.dp),
            ) { Text(t(S.undo), color = if (order.isNotEmpty()) Navy else Muted) }
            BigButton(t(S.confirm), modifier = Modifier.weight(1f), enabled = order.size == task.steps.size) {
                onDone(order.toList(), stepLatency)
            }
        }
    }
}

@Composable
fun FlagTaskView(task: FlagTask, onDone: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Randomise which answer sits on top so the worker cannot learn "top = right".
        val yesFirst = remember(task) { Math.random() < 0.5 }
        val yes: @Composable () -> Unit = { BigButton(t(task.yes), color = CardAlt, contentColor = Ink) { onDone(true) } }
        val no: @Composable () -> Unit = { BigButton(t(task.no), color = CardAlt, contentColor = Ink) { onDone(false) } }
        if (yesFirst) { yes(); no() } else { no(); yes() }
    }
}

@Composable
fun RangeTaskView(task: RangeTask, onDone: (Double) -> Unit) {
    var value by remember(task) { mutableFloatStateOf(task.start) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "${"%.1f".format(value)} ${task.unit}",
            style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = Navy,
        )
        Slider(
            value = value,
            onValueChange = { raw -> value = task.min + ((raw - task.min) / task.step).roundToInt() * task.step },
            valueRange = task.min..task.max,
            steps = (((task.max - task.min) / task.step).roundToInt() - 1).coerceAtLeast(0),
        )
        Row {
            Text("${task.min} ${task.unit}", color = Muted, modifier = Modifier.weight(1f))
            Text("${task.max} ${task.unit}", color = Muted)
        }
        BigButton(t(S.confirm)) { onDone("%.2f".format(java.util.Locale.US, value).toDouble()) }
    }
}

/** Drag-to-cover interaction: spray sweep, wall contact, sampling heights, lifeline pull. */
@Composable
fun SweepTaskView(task: SweepTask, onDone: (Double) -> Unit) {
    val bins = 20
    val covered = remember(task) { mutableStateListOf<Int>() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.fillMaxWidth().height(if (task.vertical) 200.dp else 110.dp)
                .clip(RoundedCornerShape(18.dp)).background(CardAlt)
                .pointerInput(task) {
                    detectDragGestures { change, _ ->
                        val p = change.position
                        val f = if (task.vertical) 1f - p.y / size.height else p.x / size.width
                        val bin = (f * bins).toInt().coerceIn(0, bins - 1)
                        if (bin !in covered) covered.add(bin)
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                for (b in covered) {
                    if (task.vertical) {
                        val h = size.height / bins
                        drawRect(Safe.copy(alpha = 0.8f), topLeft = Offset(0f, size.height - (b + 1) * h), size = Size(size.width, h - 2f))
                    } else {
                        val w = size.width / bins
                        drawRect(Safe.copy(alpha = 0.8f), topLeft = Offset(b * w, 0f), size = Size(w - 2f, size.height))
                    }
                }
            }
            Icon(
                if (task.vertical) Ic.ArrowUpward else Ic.SwapHoriz,
                contentDescription = null,
                tint = Navy.copy(alpha = 0.35f),
                modifier = Modifier.align(Alignment.Center).size(48.dp),
            )
        }
        Text("${covered.size * 100 / bins}%", style = MaterialTheme.typography.titleLarge, color = Navy, fontWeight = FontWeight.Bold)
        BigButton(t(S.done), enabled = covered.isNotEmpty()) { onDone(covered.size.toDouble() / bins) }
    }
}

@Composable
fun QuizTaskView(task: QuizTask, onDone: (List<String>) -> Unit) {
    var index by remember(task) { mutableIntStateOf(0) }
    val answers = remember(task) { mutableStateListOf<String>() }
    val question = task.questions[index]
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${index + 1} / ${task.questions.size}", color = Muted)
        Text(t(question.prompt), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        question.options.forEach { opt ->
            OptionCard(opt, selected = false) {
                answers.add("${question.id}_${opt.id}")
                if (index + 1 < task.questions.size) index++ else onDone(answers.toList())
            }
        }
    }
}
