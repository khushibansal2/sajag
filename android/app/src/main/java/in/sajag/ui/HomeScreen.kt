package `in`.sajag.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.sajag.R
import `in`.sajag.assess.Modules
import `in`.sajag.geo.Jharkhand
import `in`.sajag.i18n.S
import `in`.sajag.i18n.t
import `in`.sajag.ui.theme.Amber
import `in`.sajag.ui.theme.Bg
import `in`.sajag.ui.theme.Card
import `in`.sajag.ui.theme.Danger
import `in`.sajag.ui.theme.DangerBg
import `in`.sajag.ui.theme.DangerText
import `in`.sajag.ui.theme.Ink
import `in`.sajag.ui.theme.Line
import `in`.sajag.ui.theme.Muted
import `in`.sajag.ui.theme.Navy
import `in`.sajag.ui.theme.NavySoft
import `in`.sajag.ui.theme.SafeText
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Home: who is training, the emergency button, the modules with where the
 * worker stands on each, and the supervisor's tools.
 */
@Composable
fun HomeScreen(
    app: AppState,
    held: List<Held>,
    pendingSync: Int,
    onOpenModule: (String) -> Unit,
    onEmergency: () -> Unit,
    onSettings: () -> Unit,
    onWorkers: () -> Unit,
    onTrainingCentre: () -> Unit,
    onRiskMap: () -> Unit,
    onSupervisor: () -> Unit,
    onPassport: () -> Unit,
) {
    val profile = app.profile
    val today = remember { LocalDate.now() }
    val best = remember(held) { bestByModule(held) }
    val available = Modules.cards.filter { it.available }
    val certified = available.count { standingOf(best[it.id], today).let { s -> s == Standing.CURRENT || s == Standing.RENEW_SOON } }

    Column(Modifier.fillMaxSize().background(Bg)) {
        // Top bar: the app's name and the way to Settings.
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 64.dp).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(36.dp).background(Navy, CircleShape), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text("Sajag", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Navy, modifier = Modifier.weight(1f))
            if (app.demoMode) StatusChip(t(S.demo), Tone.WARN)
            IconButton(onClick = onSettings, modifier = Modifier.size(52.dp)) {
                Icon(Ic.Settings, contentDescription = t(S.settings), tint = Ink)
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            WorkerCard(
                name = profile.name,
                detail = listOfNotNull(
                    profile.employer.takeIf { it.isNotBlank() },
                    Jharkhand.byId(profile.district)?.let { t(it.name) },
                ).joinToString(" · "),
                shortId = profile.shortId,
                certified = certified,
                total = available.size,
                canSwitch = app.workers.size > 1,
                onSwitch = onWorkers,
                onPassport = onPassport,
            )

            EmergencyCard(onEmergency)

            SectionHeader(t(S.modules))
            Modules.cards.forEach { card ->
                val standing = standingOf(best[card.id], today)
                ModuleRow(
                    icon = Ic.forKey(card.icon) ?: Ic.MenuBook,
                    title = t(card.title),
                    subtitle = if (card.available) "${Modules.content(card.id).minutes} ${t(S.minutes)} · ${card.id}" else t(S.comingSoon),
                    status = when {
                        !card.available -> null
                        standing == Standing.CURRENT -> t(S.certified) to Tone.SAFE
                        standing == Standing.RENEW_SOON -> {
                            val days = best[card.id]?.let { ChronoUnit.DAYS.between(today, it.credential.expires) } ?: 0
                            "${t(S.renewSoon)} · $days ${t(S.daysLeft)}" to Tone.WARN
                        }
                        standing == Standing.EXPIRED -> t(S.expired) to Tone.DANGER
                        else -> t(S.notTrained) to Tone.NEUTRAL
                    },
                    enabled = card.available,
                    onClick = { onOpenModule(card.id) },
                )
            }

            SectionHeader(t(S.supervisorTools))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolTile(Ic.Insights, t(S.trainingCentre), Modifier.weight(1f), onTrainingCentre)
                ToolTile(Ic.Map, t(S.riskMap), Modifier.weight(1f), onRiskMap)
                ToolTile(Ic.SupervisorAccount, t(S.signOffTool), Modifier.weight(1f), onSupervisor)
            }

            Row(Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (pendingSync > 0) Ic.CloudUpload else Ic.CloudDone,
                    contentDescription = null,
                    tint = if (pendingSync > 0) Muted else SafeText,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (pendingSync > 0) "$pendingSync ${t(S.pendingSync)}" else t(S.allSynced),
                    color = if (pendingSync > 0) Muted else SafeText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun WorkerCard(
    name: String,
    detail: String,
    shortId: String,
    certified: Int,
    total: Int,
    canSwitch: Boolean,
    onSwitch: () -> Unit,
    onPassport: () -> Unit,
) {
    Surface(onClick = onPassport, shape = RoundedCornerShape(24.dp), color = Navy, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.background(Brush.linearGradient(listOf(Navy, Color(0xFF1C4574))))) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(name, size = 52.dp, background = Amber, content = Color.Black)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(t(S.namaste), color = NavySoft, style = MaterialTheme.typography.bodyMedium)
                        Text(name, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (detail.isNotBlank()) Text(detail, color = NavySoft, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (canSwitch) {
                        Surface(onClick = onSwitch, shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.14f)) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Ic.SwapHoriz, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(t(S.switchWorker), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("$certified / $total ${t(S.modulesCertified)}", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.size(6.dp))
                        Box(Modifier.fillMaxWidth().padding(end = 16.dp).heightIn(min = 6.dp).background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))) {
                            Box(
                                Modifier.fillMaxWidth(if (total == 0) 0f else certified / total.toFloat()).heightIn(min = 6.dp)
                                    .background(Amber, RoundedCornerShape(50)),
                            )
                        }
                    }
                    Text("ID $shortId", color = NavySoft, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** The one red thing on the home screen. It opens emergency steps; it is not an alarm. */
@Composable
private fun EmergencyCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = DangerBg,
        border = BorderStroke(1.5.dp, Danger),
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).background(Danger, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Ic.Emergency, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(t(S.emergency), color = DangerText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(t(S.emergencyCardBody), color = Ink, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Ic.ChevronRight, contentDescription = null, tint = DangerText)
        }
    }
}

@Composable
private fun ModuleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    status: Pair<String, Tone>?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = Card,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Line),
        modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp).alpha(if (enabled) 1f else 0.55f),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(Navy.copy(alpha = 0.08f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Navy, modifier = Modifier.size(28.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall)
                if (status != null) StatusChip(status.first, status.second)
            }
            if (enabled) Icon(Ic.ChevronRight, contentDescription = null, tint = Muted)
        }
    }
}

@Composable
private fun ToolTile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 96.dp),
        color = Card,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Line),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(40.dp).background(Navy.copy(alpha = 0.08f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Navy, modifier = Modifier.size(22.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 2)
        }
    }
}
