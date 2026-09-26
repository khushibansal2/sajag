package `in`.sajag.ui

import `in`.sajag.credential.Credential
import `in`.sajag.credential.CredentialCodec
import `in`.sajag.credential.VerifyResult
import `in`.sajag.data.CredentialEntity
import `in`.sajag.util.toHex
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A genuine certificate held on this phone, with the QR text it came from. */
data class Held(val qr: String, val credential: Credential)

enum class Standing { CURRENT, RENEW_SOON, EXPIRED, NONE }

const val RENEW_WINDOW_DAYS = 60L

/**
 * The certificates on this phone that belong to [workerHex] and whose
 * signature verifies. An expired certificate is still genuine: it is checked
 * again as of its first valid day, to tell "expired" from "forged".
 */
fun heldCertificates(
    wallet: List<CredentialEntity>,
    trust: Map<Int, ByteArray>,
    workerHex: String,
    today: LocalDate,
): List<Held> = wallet.mapNotNull { row ->
    val cred = when (val v = CredentialCodec.verify(row.qrText, trust, today = today)) {
        is VerifyResult.Valid -> v.credential
        is VerifyResult.Rejected -> v.credential?.takeIf {
            CredentialCodec.verify(row.qrText, trust, today = it.notBefore) is VerifyResult.Valid
        }
    }
    cred?.takeIf { it.subjectId.toHex() == workerHex }?.let { Held(row.qrText, it) }
}

/** Per module, the certificate that lasts longest. */
fun bestByModule(held: List<Held>): Map<String, Held> =
    held.groupBy { it.credential.module }.mapValues { (_, v) -> v.maxBy { it.credential.expires } }

fun standingOf(best: Held?, today: LocalDate): Standing {
    val c = best?.credential ?: return Standing.NONE
    return when {
        c.expires.isBefore(today) -> Standing.EXPIRED
        ChronoUnit.DAYS.between(today, c.expires) <= RENEW_WINDOW_DAYS -> Standing.RENEW_SOON
        else -> Standing.CURRENT
    }
}
