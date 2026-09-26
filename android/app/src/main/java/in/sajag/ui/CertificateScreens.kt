package `in`.sajag.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import `in`.sajag.SajagApp
import `in`.sajag.assess.Modules
import `in`.sajag.assess.Scoring
import `in`.sajag.credential.CredentialCodec
import `in`.sajag.credential.DeviceIdentity
import `in`.sajag.credential.VerifyResult
import `in`.sajag.i18n.S
import `in`.sajag.i18n.T
import `in`.sajag.i18n.t
import `in`.sajag.ui.theme.Amber
import `in`.sajag.ui.theme.AmberText
import `in`.sajag.ui.theme.Danger
import `in`.sajag.ui.theme.DangerBg
import `in`.sajag.ui.theme.DangerText
import `in`.sajag.ui.theme.Muted
import `in`.sajag.ui.theme.Navy
import `in`.sajag.ui.theme.Safe
import `in`.sajag.ui.theme.SafeBg
import `in`.sajag.ui.theme.SafeText
import java.time.LocalDate

/** ECC-M, alphanumeric-friendly: see credential.qr_version_for for why M. */
internal fun qrBitmap(text: String, px: Int = 720): Bitmap {
    val matrix = QRCodeWriter().encode(
        text, BarcodeFormat.QR_CODE, px, px,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2),
    )
    val pixels = IntArray(matrix.width * matrix.height) { i ->
        if (matrix.get(i % matrix.width, i / matrix.width)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
}

/**
 * The worker ID card's QR: "SJGW1:" then the 32-hex worker id. It says who a
 * worker is and nothing else; it is not signed and is not a certificate.
 */
internal const val WORKER_QR_PREFIX = "SJGW1:"

internal fun workerIdFromQr(text: String): String? {
    if (!text.startsWith(WORKER_QR_PREFIX)) return null
    val id = text.removePrefix(WORKER_QR_PREFIX).trim().lowercase()
    return id.takeIf { it.length == 32 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * While a certificate QR is on screen: full brightness, so a scanner can read
 * it in sunlight or on a scratched screen, and the screen stays on while the
 * inspector gets their phone out. Both are restored when the screen closes.
 */
@Composable
internal fun InspectionMode() {
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        val previous = window?.attributes?.screenBrightness
        view.keepScreenOn = true
        if (window != null) {
            val attrs = window.attributes
            attrs.screenBrightness = 1f
            window.attributes = attrs
        }
        onDispose {
            view.keepScreenOn = false
            if (window != null && previous != null) {
                val attrs = window.attributes
                attrs.screenBrightness = previous
                window.attributes = attrs
            }
        }
    }
}

/** The verifier's messages come from the shared codec in English; show them in the worker's language too. */
private fun reasonText(reason: String): T? = when {
    reason.startsWith("Not a Sajag") -> S.reasonNotSajag
    reason.startsWith("Damaged") -> S.reasonDamaged
    reason.startsWith("Unknown issuer") -> S.reasonUnknownIssuer
    reason.startsWith("Signature does not match") -> S.reasonAltered
    reason.contains("revoked") -> S.reasonRevoked
    reason.startsWith("Not valid until") -> S.reasonNotYet
    reason.startsWith("Expired") -> S.reasonExpired
    else -> null
}

private fun warningText(warning: String): T? = when {
    warning.startsWith("Provisional") -> S.provisional
    warning.contains("guided 2D") -> S.guidedWarning
    else -> null
}

@Composable
fun CertificateScreen(qr: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val bitmap = remember(qr) { qrBitmap(qr).asImageBitmap() }
    val verdict = remember(qr) { CredentialCodec.verify(qr, DeviceIdentity.trustList(context)) }
    InspectionMode()

    Page(title = t(S.certificate), onBack = onBack) {
        CertificateCard(verdict, bitmap)
        Banner(Ic.QrCode2, t(S.showToInspector), Tone.INFO)
    }
}

@Composable
private fun CertificateCard(verdict: VerifyResult, qr: ImageBitmap) {
    val cred = when (verdict) {
        is VerifyResult.Valid -> verdict.credential
        is VerifyResult.Rejected -> verdict.credential
    }
    val ok = verdict is VerifyResult.Valid
    val grey = Color(0xFF5F6368)
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFFFFFDF7),
        border = BorderStroke(2.dp, Amber),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(Navy, Color(0xFF1C4574))))
                    .padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ic.HealthAndSafety, contentDescription = null, tint = Amber, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("SAJAG", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp, letterSpacing = 4.sp)
                }
                Text(t(S.certificate).uppercase(), color = Amber, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Column(
                Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(t(S.issuedTo), color = grey, fontSize = 12.sp)
                Text(cred?.name ?: "-", color = Color.Black, fontSize = 28.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                Text(cred?.employerCode ?: "", color = grey)
                Text(
                    cred?.let { t(Modules.title(it.module)) } ?: "",
                    color = Navy, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip("${t(S.score)} ${cred?.score ?: "-"}", Tone.WARN)
                    cred?.let { StatusChip(t(DrillMode.fromWire(it.tier).label), Tone.INFO, icon = DrillMode.fromWire(it.tier).icon) }
                }
                Text("${t(S.expires)} ${cred?.expires ?: "-"}", color = grey, fontSize = 12.sp)
                Image(qr, contentDescription = t(S.certificateQr), modifier = Modifier.size(250.dp), filterQuality = FilterQuality.None)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (ok) Ic.CheckCircle else Ic.Cancel,
                        contentDescription = null,
                        tint = if (ok) SafeText else DangerText,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (ok) t(S.verifiedOffline) else (verdict as VerifyResult.Rejected).let { reasonText(it.reason)?.let { r -> t(r) } ?: it.reason },
                        color = if (ok) SafeText else DangerText, fontWeight = FontWeight.Bold,
                    )
                }
                if (cred?.provisional == true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Ic.HourglassTop, contentDescription = null, tint = AmberText, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(t(S.provisional), color = AmberText, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
                Text("ID ${cred?.credentialId?.take(16) ?: ""}", color = Color(0xFF757575), fontSize = 10.sp)
            }
        }
    }
}

/** The Verify tab: the check a supervisor, gate guard or DGMS inspector runs, offline. */
@Composable
fun VerifyScreen() {
    val context = LocalContext.current
    val trust = remember { DeviceIdentity.trustList(context) }
    val db = remember { (context.applicationContext as SajagApp).db }
    val wallet by db.credentials().wallet().collectAsState(initial = emptyList())
    val workers = remember { `in`.sajag.data.Workers.all(context) }
    var text by remember { mutableStateOf("") }
    var verdict by remember { mutableStateOf<VerifyResult?>(null) }
    var workerCard by remember { mutableStateOf<String?>(null) }

    fun check(raw: String) {
        val code = raw.trim()
        val worker = workerIdFromQr(code)
        if (worker != null) {
            workerCard = worker
            verdict = null
        } else {
            workerCard = null
            verdict = CredentialCodec.verify(code, trust)
        }
    }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { res ->
        res.contents?.let {
            text = it
            check(it)
        }
    }
    // A sound and a buzz as well as the colour: the verdict must reach an
    // inspector who is looking at the worker, not at the screen.
    LaunchedEffect(verdict) {
        when (verdict) {
            is VerifyResult.Valid -> Alarm.success(context)
            is VerifyResult.Rejected -> Alarm.reject(context)
            null -> Unit
        }
    }

    Page(title = t(S.verifyCertificate), subtitle = t(S.verifySubtitle), insetBottom = false) {
        verdict?.let { VerdictCard(it) }
        workerCard?.let { id ->
            val name = workers.firstOrNull { it.workerHex == id }?.name
            val count = remember(wallet, id) { heldCertificates(wallet, trust, id, LocalDate.now()).size }
            WorkerCardResult(id, name, count)
        }
        BigButton(t(S.scanQr), icon = Ic.QrCodeScanner) {
            scanner.launch(
                ScanOptions()
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setBeepEnabled(false)
                    .setOrientationLocked(false)
                    .setPrompt("Sajag"),
            )
        }
        SectionCard {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                label = { Text(t(S.orPaste)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(), minLines = 2,
            )
            SecondaryButton(t(S.check), icon = Ic.Check, enabled = text.isNotBlank()) { check(text) }
        }
        Banner(Ic.Shield, t(S.verifyOfflineNote), Tone.NEUTRAL)
    }
}

@Composable
private fun WorkerCardResult(id: String, name: String?, certificates: Int) {
    SectionCard {
        CardTitle(Ic.Badge, t(S.workerIdCard))
        Text(name ?: t(S.unknownWorker), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("${t(S.workerId)}  ${id.take(8).uppercase()}", fontFamily = FontFamily.Monospace, color = Muted)
        Text("${t(S.certificatesOnPhone)}: $certificates", fontWeight = FontWeight.SemiBold)
        Banner(Ic.Info, t(S.idCardNotCertificate), Tone.WARN)
    }
}

@Composable
fun VerdictCard(verdict: VerifyResult) {
    val ok = verdict is VerifyResult.Valid
    val cred = when (verdict) {
        is VerifyResult.Valid -> verdict.credential
        is VerifyResult.Rejected -> verdict.credential
    }
    Surface(
        color = if (ok) SafeBg else DangerBg,
        border = BorderStroke(2.dp, if (ok) Safe else Danger),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (ok) Ic.CheckCircle else Ic.Cancel,
                    contentDescription = null,
                    tint = if (ok) SafeText else DangerText,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    t(if (ok) S.valid else S.rejected),
                    fontSize = 30.sp, fontWeight = FontWeight.Black, color = if (ok) SafeText else DangerText,
                )
            }
            if (verdict is VerifyResult.Rejected) {
                val local = reasonText(verdict.reason)
                Text(local?.let { t(it) } ?: verdict.reason, fontWeight = FontWeight.Bold, color = DangerText)
                if (local != null) Text(verdict.reason, color = Muted, fontSize = 12.sp)
            }
            cred?.let { c ->
                val mode = DrillMode.fromWire(c.tier)
                Text(c.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${c.employerCode} · ${t(Modules.title(c.module))}")
                Text("${t(S.score)} ${c.score} · ${t(mode.label)} · ${t(S.expires)} ${c.expires}", color = Muted)
                Text(Scoring.COMPETENCIES.joinToString("  ") { "$it ${c.competencies[it]}" }, fontSize = 12.sp, color = Muted)
                Text("ID ${c.credentialId}", fontSize = 11.sp, color = Muted)
            }
            if (verdict is VerifyResult.Valid) {
                verdict.warnings.forEach { warning ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Ic.Warning, contentDescription = null, tint = AmberText, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(warningText(warning)?.let { t(it) } ?: warning, color = AmberText)
                    }
                }
            }
        }
    }
}
