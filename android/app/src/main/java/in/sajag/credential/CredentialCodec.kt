package `in`.sajag.credential

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.time.LocalDate
import java.util.zip.Inflater

/**
 * Kotlin port of the credential codec. This is a MIRROR of
 * core/sajag_core/credential.py — the two must agree byte for byte, because
 * the server mints with the Python one and the inspector's phone verifies with
 * this one.
 *
 * Rule for changing anything in here: change the Python first, regenerate the
 * golden vectors (`python3 cli.py vectors > ../android/.../vectors.json`), and
 * make CodecVectorTest pass. Never edit one side alone.
 *
 * Why hand-rolled CBOR rather than a library: the format is four major types
 * and 120 lines. A library would be a 300 KB dependency in an APK with a
 * 40 MB ceiling, and it would still need the same golden-vector test.
 */

private const val PREFIX = "SJG1:"
private const val SIG_LEN = 64
private val EPOCH: LocalDate = LocalDate.of(2020, 1, 1)

val COMPETENCIES = listOf("HAZ-ID", "EQP-SEL", "SEQ", "EGR", "TECH", "KNW")
val MODULES = listOf("FIRE-01", "GAS-01", "MACH-01", "HEIGHT-01", "ELEC-01")

data class Credential(
    val issuerIndex: Int,
    val subjectId: ByteArray,
    val name: String,
    val employerCode: String,
    val module: String,
    val tier: Int,
    val score: Int,
    val competencies: Map<String, Int>,
    val attemptDigest: ByteArray,
    val notBefore: LocalDate,
    val expires: LocalDate,
    val provisional: Boolean,
) {
    val credentialId: String get() = attemptDigest.joinToString("") { "%02x".format(it) }
}

sealed class VerifyResult {
    /** [warnings] is never empty for a provisional or guided-mode certificate
     *  (format field "tier" = 3). The verdict screen must render them — hiding
     *  them is how a mine ends up treating a guided-drill pass as a camera or
     *  AR pass without knowing. */
    data class Valid(val credential: Credential, val warnings: List<String>) : VerifyResult()

    /** [reason] is shown to the inspector verbatim. "Invalid" with no reason
     *  is how an inspector stops trusting the tool and waves people through. */
    data class Rejected(val reason: String, val credential: Credential?) : VerifyResult()
}

object CredentialCodec {

    /**
     * Verify a scanned QR with no network and no database.
     *
     * @param trustList issuer index -> raw 32-byte Ed25519 public key, loaded
     *   from the signed trust list cached on this device.
     * @param revoked  credential ids from the signed revocation delta.
     */
    fun verify(
        qrText: String,
        trustList: Map<Int, ByteArray>,
        revoked: Set<String> = emptySet(),
        today: LocalDate = LocalDate.now(),
    ): VerifyResult {
        if (!qrText.startsWith(PREFIX)) {
            return VerifyResult.Rejected("Not a Sajag certificate", null)
        }

        val raw = try {
            inflateIfNeeded(Base45.decode(qrText.substring(PREFIX.length)))
        } catch (e: Exception) {
            return VerifyResult.Rejected("Damaged code — ${e.message}", null)
        }

        if (raw.size <= SIG_LEN) {
            return VerifyResult.Rejected("Damaged code — payload too short", null)
        }

        val payload = raw.copyOfRange(0, raw.size - SIG_LEN)
        val signature = raw.copyOfRange(raw.size - SIG_LEN, raw.size)

        val cred = try {
            decodePayload(Cbor.decode(payload))
        } catch (e: Exception) {
            return VerifyResult.Rejected("Damaged code — ${e.message}", null)
        }

        val keyBytes = trustList[cred.issuerIndex]
            ?: return VerifyResult.Rejected("Unknown issuer — update the trust list", cred)

        // A corrupt trust-list entry (wrong length, not a curve point) makes
        // BouncyCastle throw. That must surface as a rejection with a reason,
        // never as a crash in the inspector's hand.
        val signatureOk = try {
            Ed25519Signer().run {
                init(false, Ed25519PublicKeyParameters(keyBytes, 0))
                update(payload, 0, payload.size)
                verifySignature(signature)
            }
        } catch (e: IllegalArgumentException) {
            false
        }
        if (!signatureOk) {
            return VerifyResult.Rejected("Signature does not match — certificate altered", cred)
        }

        if (cred.credentialId in revoked) {
            return VerifyResult.Rejected("Certificate has been revoked", cred)
        }
        if (today.isBefore(cred.notBefore)) {
            return VerifyResult.Rejected("Not valid until ${cred.notBefore}", cred)
        }
        if (today.isAfter(cred.expires)) {
            return VerifyResult.Rejected("Expired on ${cred.expires}", cred)
        }

        val warnings = buildList {
            if (cred.provisional) add(
                "Provisional — issued offline by the worker's device and not yet " +
                    "confirmed by the training centre."
            )
            if (cred.tier == 3) add("Trained in guided 2D mode (no camera).")
        }
        return VerifyResult.Valid(cred, warnings)
    }

    private fun inflateIfNeeded(blob: ByteArray): ByteArray {
        require(blob.isNotEmpty()) { "empty payload" }
        return when (blob[0].toInt()) {
            0 -> blob.copyOfRange(1, blob.size)
            1 -> Inflater().run {
                setInput(blob, 1, blob.size - 1)
                val out = ByteArray(4096)
                val n = inflate(out)
                end()
                out.copyOfRange(0, n)
            }
            else -> throw IllegalArgumentException("unknown compression marker ${blob[0]}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun decodePayload(items: List<Any>): Credential {
        require(items.size >= 13) { "payload is not a 13-element array" }
        require((items[0] as Long).toInt() == 1) { "unsupported credential version" }
        val comps = items[8] as ByteArray
        require(comps.size == COMPETENCIES.size) { "competency block has the wrong length" }
        return Credential(
            issuerIndex = (items[1] as Long).toInt(),
            subjectId = items[2] as ByteArray,
            name = items[3] as String,
            employerCode = items[4] as String,
            module = MODULES[(items[5] as Long).toInt()],
            tier = (items[6] as Long).toInt(),
            score = (items[7] as Long).toInt(),
            competencies = COMPETENCIES.mapIndexed { i, c ->
                c to (comps[i].toInt() and 0xFF)
            }.toMap(),
            attemptDigest = items[9] as ByteArray,
            notBefore = EPOCH.plusDays(items[10] as Long),
            expires = EPOCH.plusDays(items[11] as Long),
            provisional = ((items[12] as Long).toInt() and 1) != 0,
        )
    }
}

/** RFC 9285. Every character is in the QR alphanumeric charset, which is why
 *  this and not base64 — see the note in core/sajag_core/base45.py. */
object Base45 {
    private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ \$%*+-./:"

    fun decode(text: String): ByteArray {
        val values = text.map {
            ALPHABET.indexOf(it).also { i ->
                require(i >= 0) { "character '$it' is not in the base45 alphabet" }
            }
        }
        require(values.size % 3 != 1) { "length % 3 == 1 is never valid base45" }
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        while (i + 2 < values.size) {
            val n = values[i] + values[i + 1] * 45 + values[i + 2] * 45 * 45
            require(n <= 0xFFFF) { "triplet overflows two bytes" }
            out.write(n shr 8); out.write(n and 0xFF)
            i += 3
        }
        if (values.size % 3 == 2) {
            val n = values[values.size - 2] + values[values.size - 1] * 45
            require(n <= 0xFF) { "final pair overflows one byte" }
            out.write(n)
        }
        return out.toByteArray()
    }
}

/** Deterministic CBOR subset: uint, bstr, tstr, array. Mirror of cbor_min.py. */
object Cbor {
    fun decode(buf: ByteArray): List<Any> {
        val (item, used) = decodeAt(buf, 0)
        require(used == buf.size) { "${buf.size - used} trailing byte(s)" }
        @Suppress("UNCHECKED_CAST")
        return item as List<Any>
    }

    private fun decodeAt(buf: ByteArray, start: Int): Pair<Any, Int> {
        require(start < buf.size) { "truncated" }
        val b = buf[start].toInt() and 0xFF
        val major = b shr 5
        var value = (b and 0x1F).toLong()
        var i = start + 1
        if (value >= 24) {
            val width = when (value.toInt()) {
                24 -> 1; 25 -> 2; 26 -> 4; 27 -> 8
                else -> throw IllegalArgumentException("unsupported additional-info $value")
            }
            require(i + width <= buf.size) { "truncated length field" }
            value = 0
            repeat(width) { value = (value shl 8) or (buf[i++].toLong() and 0xFF) }
        }
        return when (major) {
            0 -> value to i
            2, 3 -> {
                val end = i + value.toInt()
                require(end <= buf.size) { "string runs past end of buffer" }
                val slice = buf.copyOfRange(i, end)
                // A tampered QR routinely lands here with bytes that are not
                // valid UTF-8. Decoding must throw, never crash the app or
                // silently substitute replacement characters.
                val out: Any = if (major == 2) slice else {
                    val decoder = Charsets.UTF_8.newDecoder()
                    decoder.decode(java.nio.ByteBuffer.wrap(slice)).toString()
                }
                out to end
            }
            4 -> {
                val list = ArrayList<Any>(value.toInt())
                repeat(value.toInt()) {
                    val (element, next) = decodeAt(buf, i)
                    list.add(element); i = next
                }
                list to i
            }
            else -> throw IllegalArgumentException("major type $major is not in this subset")
        }
    }
}
