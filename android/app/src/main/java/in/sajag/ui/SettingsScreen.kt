package `in`.sajag.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import `in`.sajag.BuildConfig
import `in`.sajag.capability.ArSupport
import `in`.sajag.capability.Tier
import `in`.sajag.credential.Supervisor
import `in`.sajag.data.Workers
import `in`.sajag.geo.Jharkhand
import `in`.sajag.i18n.Lang
import `in`.sajag.i18n.LocalLang
import `in`.sajag.i18n.S
import `in`.sajag.i18n.t
import `in`.sajag.ui.theme.AmberBg
import `in`.sajag.ui.theme.Muted
import `in`.sajag.ui.theme.Navy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun Context.activity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private enum class Protected { CONTROL_ROOM, ASSEMBLY_POINT }

/**
 * Settings, grouped the way a phone's own settings are: language, the worker,
 * the supervisor's settings (PIN-protected once a supervisor is set up), the
 * drill, demo mode, and where the data comes from.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    app: AppState,
    onBack: () -> Unit,
    onWorkers: () -> Unit,
    onEditWorker: (String) -> Unit,
    onSupervisor: () -> Unit,
) {
    val context = LocalContext.current
    val lang = LocalLang.current
    val notify = LocalNotify.current
    var pinFor by remember { mutableStateOf<Protected?>(null) }
    var editing by remember { mutableStateOf<Protected?>(null) }
    var demoBusy by remember { mutableStateOf(false) }
    val profile = app.profile

    fun protectedEdit(what: Protected) {
        if (app.supervisor != null) pinFor = what else editing = what
    }

    Page(title = t(S.settings), onBack = onBack) {
        SectionHeader(t(S.language))
        SectionCard {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Lang.entries.forEach { l ->
                    FilterChip(
                        selected = l == app.lang,
                        onClick = { app.setLanguage(l) },
                        label = { Text(l.label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AmberBg, selectedLabelColor = Navy),
                    )
                }
            }
            if (app.lang == Lang.SAT) Text(t(S.santaliNotice), color = Muted, style = MaterialTheme.typography.bodySmall)
        }

        SectionHeader(t(S.worker))
        SectionCard {
            ListRow(
                Ic.Person,
                profile.name.ifBlank { t(S.worker) },
                listOfNotNull(
                    profile.employer.takeIf { it.isNotBlank() },
                    Jharkhand.byId(profile.district)?.let { t(it.name) },
                    "ID ${profile.shortId}",
                ).joinToString(" · "),
                trailing = { TextButton(onClick = { onEditWorker(profile.workerHex) }) { Text(t(S.edit)) } },
            )
            RowDivider()
            ListRow(Ic.Groups, t(S.workersOnPhone), "${app.workers.size}", onClick = onWorkers)
        }

        SectionHeader(t(S.supervisorSettings))
        SectionCard {
            ListRow(
                Ic.SupervisorAccount,
                t(S.supervisorSection),
                app.supervisor?.let { "${it.name} · ${t(S.supervisorKey)} ${it.keyId}" } ?: t(S.supervisorNone),
                onClick = onSupervisor,
            )
            RowDivider()
            ListRow(
                Ic.Call,
                t(S.controlRoomNumber),
                app.controlRoom.ifBlank { t(S.notSet) },
                onClick = { protectedEdit(Protected.CONTROL_ROOM) },
            )
            RowDivider()
            ListRow(
                Ic.Place,
                t(S.assemblyPoint),
                app.assemblyPoint.ifBlank { t(S.notSet) },
                onClick = { protectedEdit(Protected.ASSEMBLY_POINT) },
            )
            Text(
                t(if (app.supervisor != null) S.supervisorLockNote else S.supervisorUnlockedNote),
                color = Muted, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        SectionHeader(t(S.drills))
        SectionCard {
            SwitchRow(Ic.ViewInAr, t(S.useAr), t(S.useArHelp), app.useAr, onChange = app::setArEnabled)
            RowDivider()
            val (status, tone) = when {
                app.arSupport == ArSupport.READY && app.capability.ceiling == Tier.WORLD_ANCHORED -> S.arReady to Tone.SAFE
                app.arSupport == ArSupport.NEEDS_INSTALL -> S.arNeedsInstall to Tone.WARN
                app.arSupport == ArSupport.CHECKING -> S.arChecking to Tone.NEUTRAL
                app.arSupport == ArSupport.READY -> S.arLowSpec to Tone.NEUTRAL
                else -> S.arUnsupported to Tone.NEUTRAL
            }
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(t(status), tone, icon = Ic.Smartphone)
                Text(
                    "ARCore: ${app.capability.arcoreState} · RAM ${app.capability.totalRamMb} MB",
                    color = Muted, style = MaterialTheme.typography.bodySmall,
                )
                if (app.arSupport == ArSupport.NEEDS_INSTALL) {
                    SecondaryButton(t(S.installAr), icon = Ic.CloudUpload) {
                        val activity = context.activity()
                        if (activity == null || !ArSupport.requestInstall(activity)) notify(S.installArFailed.of(lang))
                    }
                }
            }
        }

        SectionHeader(t(S.demo))
        SectionCard {
            SwitchRow(Ic.Insights, t(S.demoMode), t(S.demoModeHelp), app.demoMode, onChange = { on ->
                if (demoBusy) return@SwitchRow
                demoBusy = true
                // The app's scope, not this screen's: leaving Settings must not skip the update.
                app.scope.launch {
                    val created = withContext(Dispatchers.Default) { app.setDemoModeBlocking(on) }
                    app.afterDemoModeChange()
                    demoBusy = false
                    notify(
                        when {
                            created -> S.demoSupervisorCreated.of(lang)
                            on -> S.demoOn.of(lang)
                            else -> S.demoOff.of(lang)
                        },
                    )
                }
            })
        }

        SectionHeader(t(S.about))
        SectionCard {
            ListRow(Ic.Info, "Sajag ${BuildConfig.VERSION_NAME}", t(S.aboutBody))
            RowDivider()
            ListRow(Ic.Map, t(S.mapDataTitle), t(S.mapAttribution))
            RowDivider()
            ListRow(Ic.Shield, t(S.iconsTitle), t(S.iconsBody))
        }
    }

    pinFor?.let { what ->
        PinDialog(
            title = t(S.supervisorPin),
            body = t(S.pinToChange),
            check = { pin -> Supervisor.checkPin(context, pin) },
            onOk = {
                pinFor = null
                editing = what
            },
            onDismiss = { pinFor = null },
        )
    }
    when (editing) {
        Protected.CONTROL_ROOM -> TextSettingDialog(
            title = t(S.controlRoomNumber),
            label = t(S.phoneNumber),
            initial = app.controlRoom,
            phone = true,
            onSave = {
                app.updateControlRoom(it)
                editing = null
                notify(S.saved.of(lang))
            },
            onDismiss = { editing = null },
        )
        Protected.ASSEMBLY_POINT -> TextSettingDialog(
            title = t(S.assemblyPoint),
            label = t(S.assemblyPointHint),
            initial = app.assemblyPoint,
            phone = false,
            onSave = {
                app.updateAssemblyPoint(it)
                editing = null
                notify(S.saved.of(lang))
            },
            onDismiss = { editing = null },
        )
        null -> Unit
    }
}

/** The workers who train on this shared phone; tap one to make them the active worker. */
@Composable
fun WorkerListScreen(app: AppState, onBack: () -> Unit, onAdd: () -> Unit, onEdit: (String) -> Unit) {
    val notify = LocalNotify.current
    val lang = LocalLang.current
    Page(title = t(S.workersOnPhone), onBack = onBack, subtitle = t(S.workersSubtitle)) {
        SectionCard {
            app.workers.forEachIndexed { i, w ->
                if (i > 0) RowDivider()
                val active = w.workerHex == app.profile.workerHex
                ListRow(
                    if (active) Ic.CheckCircle else Ic.Person,
                    w.name.ifBlank { "ID ${w.shortId}" },
                    listOfNotNull(
                        w.employer.takeIf { it.isNotBlank() },
                        Jharkhand.byId(w.district)?.let { t(it.name) },
                        "ID ${w.shortId}",
                    ).joinToString(" · "),
                    tint = if (active) Navy else Muted,
                    trailing = {
                        if (active) StatusChip(t(S.active), Tone.SAFE)
                        TextButton(onClick = { onEdit(w.workerHex) }) { Text(t(S.edit)) }
                    },
                    onClick = {
                        if (!active) {
                            app.selectWorker(w.workerHex)
                            notify("${S.nowTraining.of(lang)}: ${w.name}")
                        }
                    },
                )
            }
        }
        BigButton(t(S.addWorker), icon = Ic.PersonAdd, onClick = onAdd)
        Banner(Ic.Info, t(S.workersNote), Tone.NEUTRAL)
    }
}

/** Add a worker ([workerHex] null) or change one's details. The worker id never changes. */
@Composable
fun WorkerFormScreen(app: AppState, workerHex: String?, onDone: () -> Unit) {
    val notify = LocalNotify.current
    val lang = LocalLang.current
    val existing = remember(workerHex) { app.workers.firstOrNull { it.workerHex == workerHex } }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var employer by remember { mutableStateOf(existing?.employer ?: "") }
    var district by remember { mutableStateOf(existing?.district ?: app.profile.district) }
    var site by remember { mutableStateOf(existing?.site ?: "") }
    val valid = Workers.cleanName(name).isNotBlank() && Workers.cleanEmployer(employer).isNotBlank() && district.isNotBlank()

    Page(title = t(if (existing == null) S.addWorker else S.editWorker), onBack = onDone) {
        SectionCard {
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(40) },
                label = { Text(t(S.yourName)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = employer, onValueChange = { employer = it.take(12) },
                label = { Text(t(S.employerCode)) }, singleLine = true,
                supportingText = { Text(t(S.employerHelp)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )
            DistrictField(district, onChange = { district = it })
            OutlinedTextField(
                value = site, onValueChange = { site = it.take(60) },
                label = { Text(t(S.siteName)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            if (existing != null) Text("${t(S.workerId)} ${existing.shortId}", color = Muted, fontWeight = FontWeight.Medium)
        }
        BigButton(t(S.save), icon = Ic.Check, enabled = valid, modifier = Modifier.heightIn(min = 60.dp)) {
            if (existing == null) {
                val created = app.addWorker(name, employer, district, site)
                notify("${S.nowTraining.of(lang)}: ${created.name}")
            } else {
                app.updateWorker(existing.copy(name = name, employer = employer, district = district, site = site))
                notify(S.saved.of(lang))
            }
            onDone()
        }
    }
}
