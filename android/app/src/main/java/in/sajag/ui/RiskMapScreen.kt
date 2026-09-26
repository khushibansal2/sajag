package `in`.sajag.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.sajag.data.HazardReport
import `in`.sajag.data.HazardReports
import `in`.sajag.geo.Jharkhand
import `in`.sajag.i18n.LocalLang
import `in`.sajag.i18n.S
import `in`.sajag.i18n.T
import `in`.sajag.i18n.t
import `in`.sajag.insight.AttemptSummary
import `in`.sajag.insight.DistrictRisk
import `in`.sajag.insight.RiskLevel
import `in`.sajag.insight.RiskModel
import `in`.sajag.insight.SampleData
import `in`.sajag.ui.theme.Ink
import `in`.sajag.ui.theme.Muted
import `in`.sajag.ui.theme.Navy
import java.text.DateFormat
import java.util.Date

private val LEVEL_FILL = mapOf(
    RiskLevel.NONE to Color(0xFFE3E7EC),
    RiskLevel.LOW to Color(0xFFA5D6A7),
    RiskLevel.MEDIUM to Color(0xFFFFCC80),
    RiskLevel.HIGH to Color(0xFFEF9A9A),
)

private fun levelLabel(level: RiskLevel): T = when (level) {
    RiskLevel.NONE -> S.riskNone
    RiskLevel.LOW -> S.riskLow
    RiskLevel.MEDIUM -> S.riskMedium
    RiskLevel.HIGH -> S.riskHigh
}

private fun levelTone(level: RiskLevel): Tone = when (level) {
    RiskLevel.NONE -> Tone.NEUTRAL
    RiskLevel.LOW -> Tone.SAFE
    RiskLevel.MEDIUM -> Tone.WARN
    RiskLevel.HIGH -> Tone.DANGER
}

/** Short names so labels fit inside the smaller districts. */
private fun shortName(id: String, name: T, lang: `in`.sajag.i18n.Lang): String = when (id) {
    "WEST_SINGHBHUM" -> if (lang == `in`.sajag.i18n.Lang.EN) "W. Singhbhum" else "प. सिंहभूम"
    "EAST_SINGHBHUM" -> if (lang == `in`.sajag.i18n.Lang.EN) "E. Singhbhum" else "पू. सिंहभूम"
    "SERAIKELA_KHARSAWAN" -> if (lang == `in`.sajag.i18n.Lang.EN) "Seraikela" else "सरायकेला"
    else -> name.of(lang)
}

/**
 * Jharkhand, district by district, coloured by risk worked out on this phone
 * from hazard reports and drill results (see [RiskModel] for the rule, which
 * is also printed on the screen). In demo mode a switch shows sample data,
 * labelled as such.
 */
@Composable
fun RiskMapScreen(app: AppState, onBack: () -> Unit) {
    val context = LocalContext.current
    val lang = LocalLang.current
    var summaries by remember { mutableStateOf<List<AttemptSummary>?>(null) }
    val reports = remember { HazardReports.all(context) }
    LaunchedEffect(Unit) { summaries = loadSummaries(context, app.workers) }
    val loaded = summaries
    val realEmpty = loaded != null && loaded.isEmpty() && reports.none { it.district.isNotBlank() }
    var source by remember { mutableIntStateOf(-1) }
    // Demo mode with nothing real to show starts on the sample; otherwise on this phone's data.
    val useSample = app.demoMode && (source == 1 || (source == -1 && realEmpty))
    val now = remember { System.currentTimeMillis() }
    val data: Pair<List<HazardReport>, List<AttemptSummary>>? = when {
        useSample -> SampleData.reports(now) to SampleData.attempts(now)
        loaded == null -> null
        else -> reports to loaded
    }
    val risks = remember(data) { data?.let { RiskModel.compute(it.first, it.second, now) } ?: emptyMap() }
    var selected by remember { mutableStateOf<String?>(null) }

    Page(title = t(S.riskMap), onBack = onBack, subtitle = t(S.riskMapSubtitle)) {
        if (app.demoMode) {
            Segmented(listOf(t(S.thisPhone), t(S.sampleData)), if (useSample) 1 else 0, onSelect = { source = it; selected = null })
        }
        if (useSample) SampleDataBanner()

        SectionCard {
            if (data == null) {
                Box(Modifier.fillMaxWidth().heightIn(min = 200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Navy)
                }
            } else {
                DistrictMap(risks, selected, lang, onSelect = { selected = if (selected == it) null else it })
                Legend()
            }
        }

        val chosen = selected?.let { Jharkhand.byId(it) }
        if (chosen != null) {
            val risk = risks[chosen.id]
            DistrictDetail(t(chosen.name), risk, data?.first?.filter { it.district == chosen.id }.orEmpty())
        } else if (data != null) {
            if (risks.isEmpty()) {
                EmptyState(Ic.Map, t(S.noRiskDataTitle), t(if (app.demoMode) S.noRiskDataDemo else S.noRiskData))
            } else {
                SectionHeader(t(S.topDistricts))
                SectionCard {
                    Text(t(S.tapDistrict), color = Muted, style = MaterialTheme.typography.bodySmall)
                    risks.values.sortedByDescending { it.points }.take(6).forEach { r ->
                        val d = Jharkhand.byId(r.district) ?: return@forEach
                        Row(
                            Modifier.fillMaxWidth().clickable { selected = r.district }.heightIn(min = 44.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(14.dp).background(LEVEL_FILL.getValue(r.level), RoundedCornerShape(4.dp)))
                            Spacer(Modifier.width(10.dp))
                            Text(t(d.name), modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            StatusChip(t(levelLabel(r.level)), levelTone(r.level))
                            Spacer(Modifier.width(8.dp))
                            Text("%.1f".format(r.points), color = Muted, modifier = Modifier.width(40.dp))
                        }
                    }
                }
            }
        }

        SectionCard {
            CardTitle(Ic.Help, t(S.howColour))
            Text(t(S.riskRule), style = MaterialTheme.typography.bodyMedium)
        }
        Text(t(S.mapAttribution), color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DistrictMap(risks: Map<String, DistrictRisk>, selected: String?, lang: `in`.sajag.i18n.Lang, onSelect: (String) -> Unit) {
    val density = LocalDensity.current
    val select by rememberUpdatedState(onSelect)
    val paths = remember {
        Jharkhand.districts.map { d ->
            d to Path().apply {
                for (ring in d.rings) {
                    moveTo(ring[0], ring[1])
                    var i = 2
                    while (i < ring.size) {
                        lineTo(ring[i], ring[i + 1])
                        i += 2
                    }
                    close()
                }
            }
        }
    }
    val fill = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; color = Ink.toArgb() } }
    val halo = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
    }
    Canvas(
        Modifier.fillMaxWidth().aspectRatio(Jharkhand.WIDTH / Jharkhand.HEIGHT).pointerInput(Unit) {
            detectTapGestures { offset ->
                val k = size.width / Jharkhand.WIDTH
                Jharkhand.districtAt(offset.x / k, offset.y / k)?.let { select(it.id) }
            }
        },
    ) {
        val k = size.width / Jharkhand.WIDTH
        val border = with(density) { 1.5.dp.toPx() } / k
        withTransform({ scale(k, k, pivot = Offset.Zero) }) {
            for ((d, path) in paths) {
                val level = risks[d.id]?.level ?: RiskLevel.NONE
                drawPath(path, LEVEL_FILL.getValue(level))
                drawPath(path, Color.White, style = Stroke(width = border))
            }
            paths.firstOrNull { it.first.id == selected }?.let { (_, path) ->
                drawPath(path, Navy, style = Stroke(width = border * 2.4f))
            }
        }
        val textSize = with(density) { 9.sp.toPx() }
        fill.textSize = textSize
        halo.textSize = textSize
        val canvas = drawContext.canvas.nativeCanvas
        for ((d, _) in paths) {
            val label = shortName(d.id, d.name, lang)
            val x = d.labelX * k
            val y = d.labelY * k + textSize / 3f
            canvas.drawText(label, x, y, halo)
            canvas.drawText(label, x, y, fill)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend() {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(RiskLevel.NONE, RiskLevel.LOW, RiskLevel.MEDIUM, RiskLevel.HIGH).forEach { level ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).background(LEVEL_FILL.getValue(level), RoundedCornerShape(4.dp)))
                Spacer(Modifier.width(6.dp))
                Text(t(levelLabel(level)), style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DistrictDetail(name: String, risk: DistrictRisk?, reports: List<HazardReport>) {
    val level = risk?.level ?: RiskLevel.NONE
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            StatusChip(t(levelLabel(level)), levelTone(level))
        }
        if (risk == null) {
            Text(t(S.districtNoData), color = Muted)
        } else {
            Text("${t(S.riskPoints)}: ${"%.1f".format(risk.points)}", color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(Ic.Report, "${risk.reports}", t(S.statReports), Modifier.weight(1f))
                StatTile(Ic.Warning, "${risk.highReports}", t(S.statHighReports), Modifier.weight(1f), tint = Color(0xFFC62828))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(Ic.School, "${risk.drills}", t(S.statDrills), Modifier.weight(1f))
                StatTile(Ic.Block, "${risk.stops}", t(S.statStops), Modifier.weight(1f), tint = Color(0xFFC62828))
            }
            if (risk.failedDrills > 0) Text("${t(S.failedDrills)}: ${risk.failedDrills}", color = Muted)
            if (risk.topHazards.isNotEmpty()) {
                Text(t(S.reportedHazards), fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    risk.topHazards.forEach { (type, count) -> StatusChip("${t(hazardLabel(type))} · $count", Tone.NEUTRAL, icon = hazardIcon(type)) }
                }
            }
            if (reports.isNotEmpty()) {
                Text(t(S.latestReports), fontWeight = FontWeight.Bold)
                val format = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
                reports.sortedByDescending { it.createdAtMs }.take(3).forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        androidx.compose.material3.Icon(hazardIcon(r.type), contentDescription = null, tint = Navy, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${t(hazardLabel(r.type))} · ${t(severityLabel(r.severity))}", fontWeight = FontWeight.SemiBold)
                            Text(listOf(r.location, format.format(Date(r.createdAtMs))).filter { it.isNotBlank() }.joinToString(" · "), color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
