package `in`.sajag.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.sajag.SajagApp
import `in`.sajag.assess.Modules
import `in`.sajag.assess.Scenario
import `in`.sajag.assess.Scoring
import `in`.sajag.data.Profile
import `in`.sajag.geo.Jharkhand
import `in`.sajag.i18n.S
import `in`.sajag.i18n.T
import `in`.sajag.i18n.t
import `in`.sajag.insight.AttemptSummary
import `in`.sajag.insight.SampleData
import `in`.sajag.insight.Trainer
import `in`.sajag.insight.TrainerStats
import `in`.sajag.ui.theme.Danger
import `in`.sajag.ui.theme.DangerText
import `in`.sajag.ui.theme.Muted
import `in`.sajag.ui.theme.Navy
import `in`.sajag.ui.theme.Safe
import `in`.sajag.ui.theme.SafeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * Every finished drill on this phone, scored again from its append-only
 * events with the scenario files the phone ships. Runs off the main thread.
 */
internal suspend fun loadSummaries(context: Context, workers: List<Profile>): List<AttemptSummary> = withContext(Dispatchers.IO) {
    val db = (context.applicationContext as SajagApp).db
    val attempts = db.attempts().allAttempts().first()
    val events = db.attempts().allEvents().groupBy { it.attemptId }
    val scenarios = Modules.cards.filter { it.available }.associate { card ->
        card.id to Scenario.load(context, Modules.content(card.id).asset)
    }
    attempts.mapNotNull { attempt ->
        val scenario = scenarios[attempt.moduleId] ?: return@mapNotNull null
        val log = events[attempt.attemptId] ?: return@mapNotNull null
        runCatching { Trainer.summarize(attempt, log, scenario, workers.firstOrNull { it.workerHex == attempt.workerId }) }.getOrNull()
    }
}

/**
 * The training centre's view: how the batch on this phone is doing, where
 * workers go wrong, and which records still have to reach the server. In
 * demo mode a switch shows sample data, labelled as such.
 */
@Composable
fun TrainingCentreScreen(app: AppState, onBack: () -> Unit) {
    val context = LocalContext.current
    var summaries by remember { mutableStateOf<List<AttemptSummary>?>(null) }
    LaunchedEffect(Unit) { summaries = loadSummaries(context, app.workers) }
    val loaded = summaries
    var source by remember { mutableIntStateOf(-1) }
    val useSample = app.demoMode && (source == 1 || (source == -1 && loaded != null && loaded.isEmpty()))
    val now = remember { System.currentTimeMillis() }
    val rows = if (useSample) SampleData.attempts(now) else loaded
    val modules = remember { Modules.cards.filter { it.available }.map { it.id } }
    val stats = remember(rows) { rows?.let { Trainer.stats(it, modules) } }

    Page(title = t(S.trainingCentre), onBack = onBack, subtitle = t(S.trainingCentreSubtitle)) {
        if (app.demoMode) {
            Segmented(listOf(t(S.thisPhone), t(S.sampleData)), if (useSample) 1 else 0, onSelect = { source = it })
        }
        if (useSample) SampleDataBanner()

        when {
            stats == null -> Box(Modifier.fillMaxWidth().heightIn(min = 200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Navy)
            }
            stats.attempts == 0 -> EmptyState(Ic.Insights, t(S.noDrillsTitle), t(if (app.demoMode) S.noDrillsDemo else S.noDrills))
            else -> Dashboard(stats)
        }
    }
}

@Composable
private fun Dashboard(stats: TrainerStats) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile(Ic.School, "${stats.attempts}", t(S.statAttempts), Modifier.weight(1f))
        StatTile(Ic.TrendingUp, stats.passRate?.let { "$it%" } ?: "-", t(S.statPassRate), Modifier.weight(1f), tint = Safe)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile(Ic.Groups, "${stats.workers}", t(S.statWorkers), Modifier.weight(1f))
        StatTile(Ic.CloudUpload, "${stats.pendingSync}", t(S.statPending), Modifier.weight(1f), tint = Muted)
    }

    SectionCard {
        CardTitle(Ic.Verified, t(S.signOffs))
        val fraction = if (stats.attempts == 0) 0f else stats.coSigned / stats.attempts.toFloat()
        Text("${stats.coSigned} / ${stats.attempts} ${t(S.signedOffShare)}")
        ProgressBar(fraction, Navy)
        Text(t(S.signOffWhy), color = Muted, style = MaterialTheme.typography.bodySmall)
    }

    SectionHeader(t(S.byModule))
    SectionCard {
        stats.byModule.forEachIndexed { i, m ->
            if (i > 0) RowDivider()
            val rate = if (m.attempts == 0) 0f else m.passed / m.attempts.toFloat()
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ic.forKey(Modules.cards.first { it.id == m.moduleId }.icon) ?: Ic.MenuBook, contentDescription = null, tint = Navy, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(t(Modules.title(m.moduleId)), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(if (m.attempts == 0) "-" else "${(rate * 100).toInt()}%", fontWeight = FontWeight.Bold)
                }
                ProgressBar(rate, if (rate >= 0.7f) Safe else Danger)
                Text("${m.attempts} ${t(S.statAttempts).lowercase()} · ${t(S.averageScore)} ${m.averageScore}", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    SectionHeader(t(S.skillsAcross))
    SectionCard {
        Scoring.COMPETENCIES.forEach { code ->
            val value = stats.competencyAverages[code] ?: 0
            val floor = Scoring.MODEL.getValue(code).second
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t(COMPETENCY_NAMES[code] ?: T(code, code)), modifier = Modifier.weight(1f))
                Text("$value", fontWeight = FontWeight.Bold, color = if (value >= floor) SafeText else DangerText)
            }
            ProgressBar(value / 100f, if (value >= floor) Safe else Danger)
        }
        Text(t(S.skillsFloorNote), color = Muted, style = MaterialTheme.typography.bodySmall)
    }

    if (stats.mostMissed.isNotEmpty()) {
        SectionHeader(t(S.mostMissed))
        SectionCard {
            stats.mostMissed.forEachIndexed { i, miss ->
                if (i > 0) RowDivider()
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(t(Modules.itemLabel(miss.moduleId, miss.itemId)), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(t(Modules.title(miss.moduleId)), color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.width(8.dp))
                    StatusChip("${miss.misses} / ${miss.attempts}", Tone.WARN)
                }
            }
            Text(t(S.mostMissedHelp), color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }

    if (stats.stopTypes.isNotEmpty()) {
        SectionHeader(t(S.stopActions))
        SectionCard {
            stats.stopTypes.forEachIndexed { i, (type, count) ->
                if (i > 0) RowDivider()
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Icon(Ic.Block, contentDescription = null, tint = DangerText, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(t(Modules.stopLabel(type)), modifier = Modifier.weight(1f))
                    StatusChip("$count", Tone.DANGER)
                }
            }
        }
    }

    SectionHeader(t(S.recentDrills))
    SectionCard {
        val format = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
        stats.recent.forEachIndexed { i, s ->
            if (i > 0) RowDivider()
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Avatar(s.workerName, size = 38.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(s.workerName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val place = Jharkhand.byId(s.district)?.let { t(it.name) }
                    Text(
                        listOfNotNull(s.moduleId, place, format.format(Date(s.startedAtMs))).joinToString(" · "),
                        color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusChip(t(DrillMode.fromWire(s.mode).label), Tone.INFO)
                        if (s.coSigned) StatusChip(t(S.signedOff), Tone.SAFE)
                        if (!s.synced) StatusChip(t(S.statusWaiting), Tone.NEUTRAL)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${s.score}", fontWeight = FontWeight.Black, fontSize = 20.sp, color = if (s.passed) SafeText else DangerText)
                    Text(t(if (s.passed) S.passed else S.failedShort), color = if (s.passed) SafeText else DangerText, fontSize = 11.sp)
                }
            }
        }
    }
}
