package `in`.sajag.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import `in`.sajag.SajagApp
import `in`.sajag.assess.ChoiceTask
import `in`.sajag.assess.FlagTask
import `in`.sajag.assess.Hazard
import `in`.sajag.assess.Modules
import `in`.sajag.assess.QuizTask
import `in`.sajag.assess.RangeTask
import `in`.sajag.assess.Scenario
import `in`.sajag.assess.Scoring
import `in`.sajag.assess.ScoringEvent
import `in`.sajag.assess.SequenceTask
import `in`.sajag.assess.SweepTask
import `in`.sajag.capability.Tier
import `in`.sajag.credential.CanonicalJson
import `in`.sajag.data.AttemptEntity
import `in`.sajag.data.EventEntity
import `in`.sajag.data.Profile
import `in`.sajag.data.Ulid
import `in`.sajag.i18n.LocalLang
import `in`.sajag.i18n.S
import `in`.sajag.i18n.T
import `in`.sajag.i18n.t
import `in`.sajag.sync.SyncWorker
import `in`.sajag.ui.ar.ArDrillState
import `in`.sajag.ui.ar.ArDrillView
import `in`.sajag.ui.ar.ArPhase
import `in`.sajag.ui.ar.ArProblem
import `in`.sajag.ui.theme.Amber
import `in`.sajag.ui.theme.Card
import `in`.sajag.ui.theme.Danger
import `in`.sajag.ui.theme.Ink
import `in`.sajag.ui.theme.Line
import `in`.sajag.ui.theme.Muted
import `in`.sajag.ui.theme.Scrim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Phase { LEARN, PERFORM }

/**
 * How a drill is shown. [wire] is the number the certificate format stores in
 * its "tier" field (1, 2 or 3); the app itself never says "tier".
 */
enum class DrillMode(val wire: Int, val label: T, val icon: ImageVector) {
    AR(1, S.modeAr, Ic.ViewInAr),
    CAMERA(2, S.modeCamera, Ic.Videocam),
    GUIDED(3, S.modeGuided, Ic.School);

    companion object {
        fun fromWire(wire: Int): DrillMode = entries.firstOrNull { it.wire == wire } ?: CAMERA
    }
}

/**
 * Learn -> Perform -> Assess, one beat at a time.
 *
 * Every worker action becomes an event in the append-only log, in exactly the
 * shape the rubric expects. Reaction times are measured from the moment a task
 * is handed over, never from when the instruction started playing, so our
 * narration pace never counts as the worker's hesitation.
 *
 * While the worker answers, nothing on screen reacts to whether an answer is
 * right: no hints, no ticks, no colour change. The only interruption is the
 * STOP card for an action that would kill in a real mine.
 *
 * The scene: with [arAvailable], world-anchored AR (the hazard stands on a real
 * floor or table); without it, or if AR fails, hazards drawn over the live
 * camera; with no camera, a drawn mine gallery. Each event records the mode
 * that was really on screen when it happened.
 */
@Composable
fun DrillScreen(
    moduleId: String,
    attemptId: String,
    supervisorSig: String?,
    tierCeiling: Tier,
    arAvailable: Boolean,
    profile: Profile,
    onExit: () -> Unit,
    onFinish: (CompletedRun) -> Unit,
) {
    val context = LocalContext.current
    val lang = LocalLang.current
    val scope = rememberCoroutineScope()
    val guide = rememberGuide()
    val content = remember(moduleId) { Modules.content(moduleId) }
    val scenario = remember(moduleId) { Scenario.load(context, content.asset) }
    val startedAt = remember { System.currentTimeMillis() }
    KeepScreenOn()

    var cameraGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cameraGranted = it }
    val hasCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    LaunchedEffect(Unit) {
        if (!cameraGranted && hasCamera) permission.launch(Manifest.permission.CAMERA)
    }

    val arState = remember { ArDrillState() }
    var arOff by remember { mutableStateOf(false) }
    var arFailedNotice by remember { mutableStateOf(false) }
    LaunchedEffect(arState.failure) {
        if (arState.failure != null && !arOff) {
            arOff = true
            arFailedNotice = true
            delay(6_000)
            arFailedNotice = false
        }
    }
    val mode = when {
        !hasCamera || !cameraGranted -> DrillMode.GUIDED
        arAvailable && !arOff -> DrillMode.AR
        else -> DrillMode.CAMERA
    }
    // ARCore releases the camera on a background thread; give it a moment before CameraX asks for it.
    var cameraReady by remember { mutableStateOf(false) }
    LaunchedEffect(mode) {
        if (mode == DrillMode.CAMERA) {
            if (arAvailable) delay(700)
            cameraReady = true
        } else {
            cameraReady = false
        }
    }

    var beatIndex by remember { mutableIntStateOf(0) }
    var taskIndex by remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(Phase.LEARN) }
    var beatStart by remember { mutableLongStateOf(0L) }
    var taskStart by remember { mutableLongStateOf(0L) }
    var hardFailPending by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var sheetOpen by remember { mutableStateOf(true) }
    val events = remember { mutableListOf<ScoringEvent>() }
    val eventTimes = remember { mutableListOf<Long>() }
    val eventModes = remember { mutableListOf<Int>() }

    val beat = content.beats[beatIndex]
    val permitSteps = remember(beatIndex, lang) {
        if (beat.hazard != Hazard.PERMIT_BOARD) emptyList()
        else beat.tasks.filterIsInstance<SequenceTask>().firstOrNull()?.steps?.map { it.label.of(lang) } ?: emptyList()
    }
    val permitTitle = t(S.entryPermit)

    // A stray back gesture must not throw away a half-finished assessment.
    BackHandler { confirmLeave = true }

    LaunchedEffect(beatIndex, phase, lang) {
        if (phase == Phase.LEARN) {
            sheetOpen = true
            guide.say(beat.teach, lang)
        }
    }
    LaunchedEffect(beatIndex, taskIndex, phase) {
        if (phase == Phase.PERFORM) {
            sheetOpen = true
            taskStart = SystemClock.elapsedRealtime()
            guide.say(beat.tasks[taskIndex].prompt, lang)
        }
    }

    /** The mode really on screen now: AR only counts while the scene stands on a tracked surface. */
    fun modeNow(): Int = when (mode) {
        DrillMode.AR -> if (arState.inUse) DrillMode.AR.wire else DrillMode.CAMERA.wire
        else -> mode.wire
    }

    fun record(item: String?, type: String, payload: Map<String, Any?> = emptyMap()) {
        events += ScoringEvent(beat.beatId, item, type, payload)
        eventTimes += System.currentTimeMillis()
        eventModes += modeNow()
    }

    fun finish() {
        if (finishing) return
        finishing = true
        guide.stop()
        scope.launch {
            val snapshot = events.toList()
            val modes = eventModes.toList()
            val times = eventTimes.toList()
            // Scored at the most conservative mode used, exactly as the server does.
            val used = modes.maxOrNull() ?: mode.wire
            val db = (context.applicationContext as SajagApp).db
            withContext(Dispatchers.IO) {
                db.attempts().startAttempt(
                    AttemptEntity(
                        attemptId = attemptId,
                        workerId = profile.workerHex,
                        moduleId = moduleId,
                        scenarioVersion = "1",
                        tierCeiling = tierCeiling.wire,
                        startedAtMs = startedAt,
                        finishedAtMs = System.currentTimeMillis(),
                        supervisorSig = supervisorSig,
                    ),
                )
                db.attempts().appendAll(snapshot.mapIndexed { i, e ->
                    EventEntity(Ulid.next(), attemptId, i, times[i], e.beat, e.item, e.type, modes[i], CanonicalJson.encode(e.payload))
                })
            }
            SyncWorker.enqueue(context)
            val result = Scoring.score(snapshot, scenario, used)
            onFinish(CompletedRun(moduleId, attemptId, used, snapshot, result, supervisorSig))
        }
    }

    fun advance() {
        if (taskIndex + 1 < beat.tasks.size) {
            taskIndex++
            return
        }
        beat.beatLatencyItem?.let { record(it, "ITEM", mapOf("ms" to SystemClock.elapsedRealtime() - beatStart)) }
        if (beatIndex + 1 < content.beats.size) {
            beatIndex++
            taskIndex = 0
            phase = Phase.LEARN
        } else {
            finish()
        }
    }

    fun hardFail(type: String) {
        record(null, type)
        hardFailPending = true
        Alarm.fatal(context)
        guide.say(S.stopBody, lang)
    }

    fun elapsedTask() = SystemClock.elapsedRealtime() - taskStart

    val showHints = phase == Phase.LEARN
    val screenHeight = LocalConfiguration.current.screenHeightDp

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (mode) {
            DrillMode.AR -> ArDrillView(
                state = arState,
                hazard = beat.hazard,
                showHints = showHints,
                permitTitle = permitTitle,
                permitSteps = permitSteps,
                modifier = Modifier.fillMaxSize(),
            )
            DrillMode.CAMERA -> {
                if (cameraReady) CameraBackground(Modifier.fillMaxSize())
                HazardOverlay(beat.hazard, showHints = showHints)
            }
            DrillMode.GUIDED -> {
                GuidedBackground(Modifier.fillMaxSize())
                HazardOverlay(beat.hazard, showHints = showHints)
            }
        }

        Column(Modifier.fillMaxSize()) {
            DrillTopBar(
                title = t(content.title),
                step = "${t(S.step)} ${beatIndex + 1}/${content.beats.size}",
                mode = mode,
                arLive = mode == DrillMode.AR && arState.inUse,
                onClose = { confirmLeave = true },
                onListen = {
                    val text = if (phase == Phase.LEARN) beat.teach else beat.tasks[taskIndex].prompt
                    guide.say(text, lang)
                },
            )
            LinearProgressIndicator(
                progress = { (beatIndex + (if (phase == Phase.PERFORM) taskIndex.toFloat() / beat.tasks.size else 0f)) / content.beats.size },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = Amber,
                trackColor = Color.White.copy(alpha = 0.25f),
            )

            if (mode == DrillMode.GUIDED && hasCamera) {
                // Tappable: a worker who hit "Deny" by accident can ask again.
                DrillBanner(Ic.PhotoCamera, t(S.cameraOff), onClick = { permission.launch(Manifest.permission.CAMERA) })
            }
            AnimatedVisibility(visible = arFailedNotice, enter = fadeIn(), exit = fadeOut()) {
                DrillBanner(Ic.Info, t(S.arFellBack))
            }
            if (mode == DrillMode.AR) {
                ArGuidance(arState, onUseCamera = { arOff = true })
            }
            if (beat.hazard == Hazard.GAS_DETECTOR) {
                DetectorReading(Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp))
            }

            Spacer(Modifier.weight(1f))

            DrillSheet(
                open = sheetOpen,
                onToggle = { sheetOpen = !sheetOpen },
                title = if (phase == Phase.LEARN) t(beat.title) else t(beat.tasks[taskIndex].prompt),
                maxHeight = (screenHeight * 0.62f).dp,
            ) {
                when (phase) {
                    Phase.LEARN -> {
                        Text(t(beat.teach), style = MaterialTheme.typography.bodyLarge, color = Ink)
                        BigButton(t(S.begin), icon = Ic.PlayArrow) {
                            beatStart = SystemClock.elapsedRealtime()
                            phase = Phase.PERFORM
                        }
                    }
                    Phase.PERFORM -> key(beatIndex, taskIndex) {
                        // One answer per task: a quick double tap must not record twice or skip the next task.
                        var answered by remember { mutableStateOf(false) }
                        fun once(block: () -> Unit) {
                            if (answered || hardFailPending || finishing) return
                            answered = true
                            block()
                        }
                        when (val task = beat.tasks[taskIndex]) {
                            is ChoiceTask -> ChoiceTaskView(task) { chosen ->
                                once {
                                    task.latencyItem?.let { record(it, "ITEM", mapOf("ms" to elapsedTask())) }
                                    record(task.itemId, "ITEM", mapOf("chosen" to chosen))
                                    val fail = chosen.firstNotNullOfOrNull { task.hardFailOn[it] }
                                    if (fail != null) hardFail(fail) else advance()
                                }
                            }
                            is SequenceTask -> SequenceTaskView(task) { order, stepMs ->
                                once {
                                    if (task.latencyItem != null && stepMs != null) record(task.latencyItem, "ITEM", mapOf("ms" to stepMs))
                                    record(task.itemId, "ITEM", mapOf("order" to order))
                                    val must = task.mustStartWith
                                    if (must != null && order.firstOrNull() != must.first) hardFail(must.second) else advance()
                                }
                            }
                            is FlagTask -> FlagTaskView(task) { performed ->
                                once {
                                    record(task.itemId, "ITEM", mapOf("value" to performed))
                                    if (!performed && task.hardFailIfNo != null) hardFail(task.hardFailIfNo) else advance()
                                }
                            }
                            is RangeTask -> RangeTaskView(task) { value ->
                                once {
                                    record(task.itemId, "ITEM", mapOf("value" to value))
                                    val above = task.hardFailAbove?.takeIf { value > it.first }
                                    val below = task.hardFailBelow?.takeIf { value < it.first }
                                    val fail = (above ?: below)?.second
                                    if (fail != null) hardFail(fail) else advance()
                                }
                            }
                            is SweepTask -> SweepTaskView(task) { fraction ->
                                once {
                                    record(task.itemId, "ITEM", mapOf("value" to fraction))
                                    advance()
                                }
                            }
                            is QuizTask -> QuizTaskView(task) { answers ->
                                once {
                                    record(task.itemId, "ITEM", mapOf("chosen" to answers))
                                    advance()
                                }
                            }
                        }
                    }
                }
            }
        }

        if (hardFailPending) {
            StopCard(teach = t(beat.teach)) {
                hardFailPending = false
                advance()
            }
        }

        if (finishing) {
            Box(Modifier.fillMaxSize().blockTouches().background(Color(0x88000000)), contentAlignment = Alignment.Center) {
                Text(t(S.working), color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }

        if (confirmLeave) {
            AlertDialog(
                onDismissRequest = { confirmLeave = false },
                icon = { Icon(Ic.Warning, contentDescription = null, tint = Danger) },
                title = { Text(t(S.leaveTitle)) },
                text = { Text(t(S.leaveBody)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmLeave = false
                        guide.stop()
                        onExit()
                    }) { Text(t(S.leave), color = Danger, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmLeave = false }) { Text(t(S.stay)) }
                },
            )
        }
    }
}

/** A drill keeps the screen on: a worker thinking about an answer should not lose the screen. */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun DrillTopBar(
    title: String,
    step: String,
    mode: DrillMode,
    arLive: Boolean,
    onClose: () -> Unit,
    onListen: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(Scrim).statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(52.dp)) {
            Icon(Ic.Close, contentDescription = t(S.leave), tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t(S.drillBadge),
                    color = Color.Black, fontWeight = FontWeight.Black, fontSize = 11.sp,
                    modifier = Modifier.background(Amber, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(step, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
            }
        }
        // The mode, with a live dot while the AR scene is anchored and tracked.
        Row(
            Modifier.background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (mode == DrillMode.AR) {
                Box(Modifier.size(8.dp).background(if (arLive) Color(0xFF69F0AE) else Color(0xFFFFD54F), CircleShape))
                Spacer(Modifier.width(6.dp))
            }
            Icon(mode.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(t(mode.label), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        IconButton(onClick = onListen, modifier = Modifier.size(52.dp)) {
            Icon(Ic.VolumeUp, contentDescription = t(S.listen), tint = Amber, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun DrillBanner(icon: ImageVector, text: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().background(Scrim)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Amber, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = Color.White, fontSize = 14.sp)
    }
}

/** What to do right now to get the AR scene placed and tracked. */
@Composable
private fun ArGuidance(state: ArDrillState, onUseCamera: () -> Unit) {
    var showMoved by remember { mutableStateOf(false) }
    LaunchedEffect(state.autoPlaced) {
        if (state.autoPlaced) {
            showMoved = true
            delay(5_000)
            showMoved = false
        }
    }
    val problem = state.problem
    val (icon, text) = when {
        state.phase == ArPhase.PLACED && problem != null && !state.tracking -> Ic.Warning to t(problemText(problem))
        state.phase == ArPhase.PLACED && showMoved -> Ic.TouchApp to t(S.arAutoPlaced)
        state.phase == ArPhase.PLACED -> return
        state.phase == ArPhase.READY -> Ic.TouchApp to t(S.arTapToPlace)
        problem != null -> Ic.Warning to t(problemText(problem))
        else -> Ic.CropFree to t(S.arSearching)
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
            .background(Scrim, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Amber, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Text(text, color = Color.White, style = MaterialTheme.typography.titleSmall)
        }
        if (state.searchingTooLong && state.phase != ArPhase.PLACED) {
            Surface(
                onClick = onUseCamera,
                color = Color.Transparent,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 44.dp),
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ic.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t(S.arUseCamera), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun problemText(problem: ArProblem): T = when (problem) {
    ArProblem.TOO_DARK -> S.arTooDark
    ArProblem.TOO_FAST -> S.arTooFast
    ArProblem.NO_TEXTURE -> S.arNoTexture
    ArProblem.CAMERA_BUSY -> S.arCameraBusy
}

/**
 * The white answer sheet over the camera. The handle folds it down to one line
 * so the worker can look at the whole scene, and opens it again.
 */
@Composable
private fun DrillSheet(
    open: Boolean,
    onToggle: () -> Unit,
    title: String,
    maxHeight: Dp,
    content: @Composable () -> Unit,
) {
    Surface(
        color = Card,
        contentColor = Ink,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight),
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 18.dp, end = 8.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 6.dp).width(40.dp).height(4.dp).background(Line, RoundedCornerShape(2.dp)))
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = if (open) 4 else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = if (open) 4.dp else 12.dp),
                    )
                }
                Icon(
                    if (open) Ic.ExpandMore else Ic.ExpandLess,
                    contentDescription = t(if (open) S.hideSheet else S.showSheet),
                    tint = Muted,
                    modifier = Modifier.padding(8.dp).size(28.dp),
                )
            }
            // Folded, the task stays composed at zero height, so a half-done
            // answer (quiz, sequence, sweep) is still there when it unfolds.
            Column(
                (if (open) Modifier else Modifier.height(0.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(start = 18.dp, end = 18.dp, bottom = if (open) 18.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { content() }
        }
    }
}

/** The STOP card: an action that would kill in a real mine. Red, loud, and clearly a drill. */
@Composable
private fun StopCard(teach: String, onUnderstood: () -> Unit) {
    // Covers the task underneath completely: a tap on the red card must not answer it.
    Box(
        Modifier.fillMaxSize().blockTouches().background(Color(0xF2B71C1C)).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                t(S.drillNotAlarm),
                color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.background(Color(0x33000000), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Icon(Ic.Block, contentDescription = null, tint = Color.White, modifier = Modifier.size(84.dp))
            Text(t(S.stopTitle), fontSize = 46.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text(t(S.stopBody), style = MaterialTheme.typography.titleMedium, color = Color.White, textAlign = TextAlign.Center)
            Text(teach, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center)
            BigButton(t(S.understood), color = Color.White, contentColor = Color(0xFFB71C1C), onClick = onUnderstood)
        }
    }
}
