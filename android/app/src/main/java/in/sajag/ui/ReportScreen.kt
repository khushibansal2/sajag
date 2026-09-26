package `in`.sajag.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import `in`.sajag.data.HazardReport
import `in`.sajag.data.HazardReports
import `in`.sajag.data.Ulid
import `in`.sajag.geo.Jharkhand
import `in`.sajag.i18n.S
import `in`.sajag.i18n.T
import `in`.sajag.i18n.t
import `in`.sajag.sync.SyncWorker
import `in`.sajag.ui.theme.AmberBg
import `in`.sajag.ui.theme.Muted
import `in`.sajag.ui.theme.Navy
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

internal fun hazardLabel(type: String): T = when (type) {
    "FIRE" -> S.hazardFire
    "GAS" -> S.hazardGas
    "ELECTRICAL" -> S.hazardElectrical
    "FALL" -> S.hazardFall
    "MACHINERY" -> S.hazardMachine
    "SLIP" -> S.hazardSlip
    else -> S.hazardOther
}

internal fun hazardIcon(type: String): ImageVector = when (type) {
    "FIRE" -> Ic.LocalFireDepartment
    "GAS" -> Ic.Air
    "ELECTRICAL" -> Ic.Bolt
    "FALL" -> Ic.Landslide
    "MACHINERY" -> Ic.PrecisionManufacturing
    "SLIP" -> Ic.PersonalInjury
    else -> Ic.Report
}

internal fun severityLabel(severity: String): T = when (severity) {
    "HIGH" -> S.severityHigh
    "MEDIUM" -> S.severityMedium
    else -> S.severityLow
}

internal fun severityTone(severity: String): Tone = when (severity) {
    "HIGH" -> Tone.DANGER
    "MEDIUM" -> Tone.WARN
    else -> Tone.NEUTRAL
}

/**
 * The Report tab: record a hazard on the phone; the sync worker delivers it
 * when there is network. Only the type is required; place and note help but
 * a worker in a hurry can send without them. Sending asks once to confirm.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportScreen(app: AppState) {
    val context = LocalContext.current
    val notify = LocalNotify.current
    var reports by remember { mutableStateOf(HazardReports.all(context)) }
    var type by remember { mutableStateOf(app.reportPrefill) }
    var severity by remember { mutableStateOf(if (app.reportPrefill != null) "HIGH" else "MEDIUM") }
    var district by remember { mutableStateOf(app.profile.district) }
    var location by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { app.reportPrefill = null }

    // "Waiting" turns into "Sent" when the sync worker delivers a report.
    LaunchedEffect(Unit) {
        while (true) {
            delay(4_000)
            reports = HazardReports.all(context)
        }
    }

    fun send() {
        val chosen = type ?: return
        HazardReports.add(
            context,
            HazardReport(
                id = Ulid.next(),
                workerId = app.profile.workerHex,
                type = chosen,
                severity = severity,
                location = location.trim(),
                note = note.trim(),
                createdAtMs = System.currentTimeMillis(),
                district = district,
            ),
        )
        SyncWorker.enqueue(context)
        reports = HazardReports.all(context)
        type = null
        location = ""
        note = ""
        severity = "MEDIUM"
        Alarm.success(context)
        notify(S.reportSaved.of(app.lang))
    }

    Page(title = t(S.reportTitle), subtitle = t(S.reportSubtitle), insetBottom = false) {
        Banner(Ic.Info, t(S.emergencyNotAlarm), Tone.WARN)

        SectionCard {
            Text(t(S.reportWhat), fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HazardReports.TYPES.forEach { option ->
                    FilterChip(
                        selected = type == option,
                        onClick = { type = option },
                        label = { Text(t(hazardLabel(option))) },
                        leadingIcon = { Icon(hazardIcon(option), contentDescription = null, modifier = Modifier.size(18.dp)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AmberBg, selectedLabelColor = Navy, selectedLeadingIconColor = Navy),
                    )
                }
            }
            Text(t(S.reportSeverity), fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HazardReports.SEVERITIES.forEach { option ->
                    FilterChip(
                        selected = severity == option,
                        onClick = { severity = option },
                        label = { Text(t(severityLabel(option))) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AmberBg, selectedLabelColor = Navy),
                    )
                }
            }
            DistrictField(district, onChange = { district = it })
            OutlinedTextField(
                value = location, onValueChange = { location = it.take(80) },
                label = { Text(t(S.reportWhere)) }, singleLine = true,
                supportingText = { Text(t(S.optional)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = note, onValueChange = { note = it.take(280) },
                label = { Text(t(S.reportNote)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(), minLines = 2,
            )
            BigButton(t(S.send), icon = Ic.Send, enabled = type != null) { confirming = true }
            if (type == null) Text(t(S.pickTypeFirst), color = Muted, style = MaterialTheme.typography.bodySmall)
        }

        if (reports.isNotEmpty()) {
            SectionHeader(t(S.myReports))
            val format = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
            SectionCard {
                reports.forEachIndexed { i, r ->
                    if (i > 0) RowDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(hazardIcon(r.type), contentDescription = null, tint = Navy, modifier = Modifier.size(26.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${t(hazardLabel(r.type))} · ${t(severityLabel(r.severity))}", fontWeight = FontWeight.Bold)
                            val place = listOfNotNull(
                                r.location.takeIf { it.isNotBlank() },
                                Jharkhand.byId(r.district)?.let { t(it.name) },
                            ).joinToString(" · ")
                            if (place.isNotBlank()) Text(place, color = Muted)
                            Text(format.format(Date(r.createdAtMs)), color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        StatusChip(
                            t(if (r.sent) S.statusSent else S.statusWaiting),
                            if (r.sent) Tone.SAFE else Tone.NEUTRAL,
                            icon = if (r.sent) Ic.CloudDone else Ic.CloudUpload,
                        )
                    }
                }
            }
        }
    }

    if (confirming) {
        val chosen = type
        AlertDialog(
            onDismissRequest = { confirming = false },
            icon = { Icon(Ic.Send, contentDescription = null, tint = Navy) },
            title = { Text(t(S.confirmSendTitle)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (chosen != null) Text("${t(hazardLabel(chosen))} · ${t(severityLabel(severity))}", fontWeight = FontWeight.Bold)
                    val place = listOfNotNull(location.trim().takeIf { it.isNotBlank() }, Jharkhand.byId(district)?.let { t(it.name) }).joinToString(" · ")
                    if (place.isNotBlank()) Text(place)
                    Text(t(S.confirmSendBody), color = Muted)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    send()
                }) { Text(t(S.send), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(t(S.cancel)) } },
        )
    }
}
