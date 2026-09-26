package `in`.sajag.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.sajag.SajagApp
import `in`.sajag.assess.Modules
import `in`.sajag.assess.Scoring
import `in`.sajag.credential.CanonicalJson
import `in`.sajag.credential.Credential
import `in`.sajag.credential.CredentialMinter
import `in`.sajag.credential.DeviceIdentity
import `in`.sajag.data.CredentialEntity
import `in`.sajag.data.Profile
import `in`.sajag.i18n.S
import `in`.sajag.i18n.t
import `in`.sajag.ui.theme.Danger
import `in`.sajag.ui.theme.DangerText
import `in`.sajag.ui.theme.Muted
import `in`.sajag.ui.theme.Safe
import `in`.sajag.ui.theme.SafeText
import `in`.sajag.util.hexToBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/** Competency codes in words, for the result screen and the passport. */
internal val COMPETENCY_NAMES = mapOf(
    "HAZ-ID" to `in`.sajag.i18n.T("Hazard recognition", "खतरे की पहचान"),
    "EQP-SEL" to `in`.sajag.i18n.T("Equipment choice", "उपकरण चयन"),
    "SEQ" to `in`.sajag.i18n.T("Correct sequence", "सही क्रम"),
    "EGR" to `in`.sajag.i18n.T("Safe escape", "सुरक्षित निकास"),
    "TECH" to `in`.sajag.i18n.T("Technique", "तकनीक"),
    "KNW" to `in`.sajag.i18n.T("Knowledge check", "ज्ञान जाँच"),
)

@Composable
fun ResultScreen(
    run: CompletedRun,
    profile: Profile,
    onCertificate: (String) -> Unit,
    onReplay: () -> Unit,
    onHome: () -> Unit,
) {
    val context = LocalContext.current
    val result = run.result
    var showDetails by remember { mutableStateOf(false) }
    var certificateQr by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(run.attemptId) {
        if (result.passed) {
            Alarm.success(context)
            // Saved straight away, and finished even if the worker leaves this
            // screen: a pass must never lose its certificate.
            certificateQr = withContext(Dispatchers.IO + NonCancellable) {
                runCatching { mintAndStore(context, run, profile) }.getOrNull()
            }
        }
    }
    val mode = DrillMode.fromWire(run.tier)

    Page(title = t(S.resultTitle), onBack = onHome, subtitle = t(Modules.title(run.moduleId))) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ScoreRing(result.aggregate, result.passed)
            Text(
                t(if (result.passed) S.passed else S.failed),
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                color = if (result.passed) SafeText else DangerText,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("${t(S.trainedIn)}: ${t(mode.label)}", Tone.INFO, icon = mode.icon)
                if (run.supervisorSig != null) StatusChip(t(S.signedOff), Tone.SAFE, icon = Ic.Verified)
            }
        }

        SectionCard {
            CardTitle(Ic.Insights, t(S.skills))
            Scoring.COMPETENCIES.forEach { code ->
                val value = result.competencies.getValue(code)
                val floor = Scoring.MODEL.getValue(code).second
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t(COMPETENCY_NAMES.getValue(code)), modifier = Modifier.weight(1f))
                    Text("$value", fontWeight = FontWeight.Bold, color = if (value >= floor) SafeText else DangerText)
                }
                ProgressBar(value / 100f, if (value >= floor) Safe else Danger)
                Text("${t(S.floor)} $floor", color = Muted, fontSize = 11.sp)
            }
        }

        Banner(
            if (run.supervisorSig != null) Ic.Verified else Ic.Info,
            t(if (run.supervisorSig != null) S.coSigned else S.notCoSigned),
            if (run.supervisorSig != null) Tone.SAFE else Tone.NEUTRAL,
        )

        if (!result.passed) {
            SectionCard {
                CardTitle(Ic.Warning, t(S.reasons), tint = DangerText)
                result.reasons.forEach { reason ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 8.dp).size(6.dp).background(Muted, CircleShape))
                        Spacer(Modifier.width(10.dp))
                        Text(reason)
                    }
                }
                if (result.replayBeats.isNotEmpty()) {
                    Text(t(S.replaySteps) + ": " + result.replayBeats.joinToString(", "), color = DangerText, fontWeight = FontWeight.Bold)
                }
            }
        }

        TextButton(onClick = { showDetails = !showDetails }) {
            Icon(if (showDetails) Ic.ExpandLess else Ic.ExpandMore, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(6.dp))
            Text(t(S.details))
        }
        if (showDetails) {
            SectionCard {
                result.items.forEach { item ->
                    Row {
                        Text(item.itemId, modifier = Modifier.width(150.dp), fontSize = 12.sp, color = Muted)
                        Text(
                            "${item.score.toInt()}", modifier = Modifier.width(36.dp), fontWeight = FontWeight.Bold,
                            color = if (item.score >= 70) SafeText else DangerText,
                        )
                        Text(item.detail, fontSize = 12.sp)
                    }
                }
            }
        }

        if (result.passed) {
            val qr = certificateQr
            BigButton(t(if (qr == null) S.working else S.getCertificate), icon = Ic.WorkspacePremium, enabled = qr != null) {
                if (qr != null) onCertificate(qr)
            }
        }
        SecondaryButton(t(S.replay), icon = Ic.Replay, onClick = onReplay)
        TextButton(onClick = onHome, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(t(S.home)) }
    }
}

/**
 * Mint a provisional certificate offline. The digest is computed over the same
 * canonical log the server hashes, so when the training centre re-scores and
 * issues the final credential, both carry the same credential id.
 */
private suspend fun mintAndStore(context: Context, run: CompletedRun, profile: Profile): String {
    val canonical = CanonicalJson.encode(run.events.map { e ->
        mapOf("beat" to e.beat, "item" to e.item, "type" to e.type) + e.payload
    })
    val digest = CredentialMinter.attemptDigest(canonical.toByteArray(Charsets.UTF_8))
    val (issuerIndex, key) = DeviceIdentity.signer(context)
    val today = LocalDate.now()
    val credential = Credential(
        issuerIndex = issuerIndex,
        subjectId = profile.workerHex.hexToBytes(),
        name = profile.name,
        employerCode = profile.employer,
        module = run.moduleId,
        tier = run.tier,
        score = run.result.aggregate,
        competencies = run.result.competencies,
        attemptDigest = digest,
        notBefore = today,
        expires = today.plusDays(365),
        provisional = true,
    )
    val qr = CredentialMinter.sign(credential, key)
    val zone = ZoneId.systemDefault()
    (context.applicationContext as SajagApp).db.credentials().put(
        CredentialEntity(
            credentialId = credential.credentialId,
            attemptId = run.attemptId,
            qrText = qr,
            provisional = true,
            issuedAtMs = System.currentTimeMillis(),
            expiresAtMs = credential.expires.atStartOfDay(zone).toInstant().toEpochMilli(),
        ),
    )
    return qr
}
