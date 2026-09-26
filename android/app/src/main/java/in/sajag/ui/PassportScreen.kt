package `in`.sajag.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.sajag.assess.Modules
import `in`.sajag.assess.Scoring
import `in`.sajag.data.Profile
import `in`.sajag.geo.Jharkhand
import `in`.sajag.i18n.S
import `in`.sajag.i18n.T
import `in`.sajag.i18n.t
import `in`.sajag.ui.theme.Amber
import `in`.sajag.ui.theme.Card
import `in`.sajag.ui.theme.Danger
import `in`.sajag.ui.theme.DangerText
import `in`.sajag.ui.theme.Line
import `in`.sajag.ui.theme.Muted
import `in`.sajag.ui.theme.Navy
import `in`.sajag.ui.theme.NavySoft
import `in`.sajag.ui.theme.Safe
import `in`.sajag.ui.theme.SafeText
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The worker's safety passport: their ID card, every module's standing, skill
 * scores and what to practise next, built only from certificates whose
 * signature verifies on this phone. Nothing here is sample data. The ID card
 * shows from the first day, before any certificate exists.
 */
@Composable
fun PassportScreen(
    profile: Profile,
    held: List<Held>,
    onOpenCertificate: (String) -> Unit,
    onPractise: (String) -> Unit,
) {
    val today = remember { LocalDate.now() }
    val available = remember { Modules.cards.filter { it.available } }
    val best = remember(held) { bestByModule(held) }
    val current = held.filter { !it.credential.expires.isBefore(today) }
    val skillScores = Scoring.COMPETENCIES.associateWith { code -> current.maxOfOrNull { it.credential.competencies[code] ?: 0 } }
    // What to practise next: an untrained module, then an expired one, then the one that runs out soonest.
    val nextModule = available.firstOrNull { standingOf(best[it.id], today) == Standing.NONE }
        ?: available.firstOrNull { standingOf(best[it.id], today) == Standing.EXPIRED }
        ?: available.filter { standingOf(best[it.id], today) == Standing.RENEW_SOON }.minByOrNull { best.getValue(it.id).credential.expires }
    val weakest = skillScores.entries.filter { it.value != null }.minByOrNull { it.value ?: 0 }

    Page(title = t(S.passportTitle), insetBottom = false) {
        IdCard(profile)

        SectionHeader(t(S.modules))
        SectionCard {
            available.forEachIndexed { i, card ->
                if (i > 0) RowDivider()
                val c = best[card.id]?.credential
                val s = standingOf(best[card.id], today)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 56.dp)) {
                    Icon(Ic.forKey(card.icon) ?: Ic.MenuBook, contentDescription = null, tint = Navy, modifier = Modifier.size(28.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(t(card.title), fontWeight = FontWeight.SemiBold)
                        when (s) {
                            Standing.NONE -> StatusChip(t(S.notTrained), Tone.NEUTRAL)
                            Standing.EXPIRED -> StatusChip("${t(S.expired)} ${c?.expires}", Tone.DANGER)
                            Standing.RENEW_SOON -> StatusChip(
                                "${t(S.renewSoon)} · ${c?.let { ChronoUnit.DAYS.between(today, it.expires) }} ${t(S.daysLeft)}",
                                Tone.WARN,
                            )
                            Standing.CURRENT -> StatusChip("${t(S.certified)} · ${t(S.expires)} ${c?.expires}", Tone.SAFE)
                        }
                    }
                    if (c != null) Text("${c.score}", fontWeight = FontWeight.Black, fontSize = 22.sp, color = Navy)
                }
            }
        }

        if (weakest != null) {
            SectionHeader(t(S.skills))
            SectionCard {
                Text(t(S.skillsHelp), color = Muted, style = MaterialTheme.typography.bodySmall)
                Scoring.COMPETENCIES.forEach { code ->
                    val value = skillScores[code]
                    val floor = Scoring.MODEL.getValue(code).second
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t(COMPETENCY_NAMES[code] ?: T(code, code)), modifier = Modifier.weight(1f))
                        Text(
                            value?.toString() ?: "-", fontWeight = FontWeight.Bold,
                            color = when {
                                value == null -> Muted
                                value >= floor -> SafeText
                                else -> DangerText
                            },
                        )
                    }
                    ProgressBar((value ?: 0) / 100f, if ((value ?: 0) >= floor) Safe else Danger)
                }
            }
        }

        SectionCard {
            CardTitle(Ic.GpsFixed, t(S.practiseNext))
            if (nextModule != null) {
                Text(t(nextModule.title), fontWeight = FontWeight.Bold)
            } else {
                Text(t(S.allCurrent), color = SafeText)
            }
            if (weakest != null) {
                Text("${t(S.weakestSkill)}: ${t(COMPETENCY_NAMES[weakest.key] ?: T(weakest.key, weakest.key))} (${weakest.value})", color = Muted)
            }
            BigButton(t(S.practiseNow), icon = Ic.PlayArrow, enabled = profile.enrolled) {
                onPractise(nextModule?.id ?: available.first().id)
            }
        }

        SectionHeader(t(S.myCertificates))
        if (held.isEmpty()) {
            EmptyState(Ic.WorkspacePremium, t(S.noCertificatesTitle), t(S.noCertificates))
        }
        held.sortedByDescending { it.credential.notBefore }.forEach { h ->
            val c = h.credential
            val card = Modules.cards.firstOrNull { it.id == c.module }
            Surface(
                onClick = { onOpenCertificate(h.qr) },
                color = Card, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Line),
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ic.forKey(card?.icon ?: "") ?: Ic.Verified, contentDescription = null, tint = Navy, modifier = Modifier.size(30.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(t(Modules.title(c.module)), fontWeight = FontWeight.Bold)
                        Text("${t(S.score)} ${c.score} · ${t(DrillMode.fromWire(c.tier).label)} · ${t(S.expires)} ${c.expires}", color = Muted, fontSize = 12.sp)
                        if (c.provisional) StatusChip(t(S.provisionalShort), Tone.WARN, icon = Ic.HourglassTop)
                    }
                    Icon(Ic.ChevronRight, contentDescription = null, tint = Muted)
                }
            }
        }
    }
}

/**
 * The worker's safety ID card. The QR carries only the worker id, so a
 * supervisor can look the worker up; it is not a certificate, and the Verify
 * tab says so when it scans one.
 */
@Composable
private fun IdCard(profile: Profile) {
    var showQr by remember { mutableStateOf(false) }
    val qr = remember(profile.workerHex) { qrBitmap(WORKER_QR_PREFIX + profile.workerHex, px = 360).asImageBitmap() }
    Surface(shape = RoundedCornerShape(24.dp), color = Navy, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.background(Brush.linearGradient(listOf(Navy, Color(0xFF1C4574))))) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ic.HealthAndSafety, contentDescription = null, tint = Amber, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t(S.workerIdCard).uppercase(), color = Amber, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 13.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(profile.name, size = 60.dp, background = Amber, content = Color.Black)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(profile.name.ifBlank { t(S.worker) }, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (profile.employer.isNotBlank()) Text(profile.employer, color = NavySoft)
                        Jharkhand.byId(profile.district)?.let { d ->
                            Text(listOfNotNull(t(d.name), profile.site.takeIf { it.isNotBlank() }).joinToString(" · "), color = NavySoft, fontSize = 13.sp)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t(S.workerId), color = NavySoft, fontSize = 12.sp)
                        Text(profile.shortId, color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Surface(onClick = { showQr = !showQr }, shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.14f)) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Ic.QrCode2, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(t(if (showQr) S.hideQr else S.showQr), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (showQr) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Image(
                            qr, contentDescription = t(S.workerIdCard), filterQuality = FilterQuality.None,
                            modifier = Modifier.size(180.dp).background(Color.White, RoundedCornerShape(12.dp)).padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}
