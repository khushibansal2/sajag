package `in`.sajag.credential

import org.bouncycastle.crypto.digests.Blake2sDigest
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.zip.Deflater

/**
 * The signing half of the codec — mirror of `credential.sign` in the Python
 * core. [CredentialCodec] is the verifying half. MinterRoundTripTest checks the
 * two against each other; the golden vectors check both against Python.
 */
object CredentialMinter {
    private val EPOCH: LocalDate = LocalDate.of(2020, 1, 1)
    private const val NAME_MAX_BYTES = 32
    private const val EMP_MAX_BYTES = 12

    fun sign(cred: Credential, key: Ed25519PrivateKeyParameters): String {
        require(cred.subjectId.size == 16) { "subject_id must be exactly 16 bytes" }
        require(cred.attemptDigest.size == 16) { "attempt_digest must be exactly 16 bytes" }
        require(cred.tier in 1..3) { "tier must be 1, 2 or 3" }
        require(cred.score in 0..100) { "score must be 0..100" }
        require(cred.expires.isAfter(cred.notBefore)) { "expires must be after not_before" }
        val moduleIndex = MODULES.indexOf(cred.module)
        require(moduleIndex >= 0) { "unknown module ${cred.module}" }

        val payload = CborEncoder.encode(listOf(
            1L,
            cred.issuerIndex.toLong(),
            cred.subjectId,
            truncateUtf8(cred.name, NAME_MAX_BYTES),
            truncateUtf8(cred.employerCode, EMP_MAX_BYTES),
            moduleIndex.toLong(),
            cred.tier.toLong(),
            cred.score.toLong(),
            ByteArray(COMPETENCIES.size) { i ->
                cred.competencies.getValue(COMPETENCIES[i]).coerceIn(0, 100).toByte()
            },
            cred.attemptDigest,
            ChronoUnit.DAYS.between(EPOCH, cred.notBefore),
            ChronoUnit.DAYS.between(EPOCH, cred.expires),
            if (cred.provisional) 1L else 0L,
        ))
        val signature = Ed25519Signer().run {
            init(true, key)
            update(payload, 0, payload.size)
            generateSignature()
        }
        return "SJG1:" + Base45Encoder.encode(maybeDeflate(payload + signature))
    }

    /** BLAKE2s-128 over the canonical attempt log; doubles as the credential id. */
    fun attemptDigest(canonicalLog: ByteArray): ByteArray {
        val d = Blake2sDigest(128)
        d.update(canonicalLog, 0, canonicalLog.size)
        return ByteArray(16).also { d.doFinal(it, 0) }
    }

    /** Deflate only when it helps — it usually does not, because 64 bytes are signature. */
    private fun maybeDeflate(raw: ByteArray): ByteArray {
        val deflater = Deflater(9)
        deflater.setInput(raw)
        deflater.finish()
        val buf = ByteArray(raw.size + 64)
        val n = deflater.deflate(buf)
        val complete = deflater.finished()
        deflater.end()
        return if (complete && n < raw.size) byteArrayOf(1) + buf.copyOf(n) else byteArrayOf(0) + raw
    }

    fun truncateUtf8(text: String, maxBytes: Int): String {
        val raw = text.toByteArray(Charsets.UTF_8)
        if (raw.size <= maxBytes) return text
        var end = maxBytes
        while (end > 0 && (raw[end - 1].toInt() and 0xC0) == 0x80) end--
        if (end > 0 && (raw[end - 1].toInt() and 0xFF) >= 0xC0) end--
        return String(raw, 0, end, Charsets.UTF_8)
    }
}

/** RFC 9285 encoder; [Base45] is the decoder. */
object Base45Encoder {
    private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ \$%*+-./:"

    fun encode(bytes: ByteArray): String {
        val sb = StringBuilder()
        var i = 0
        while (i + 1 < bytes.size) {
            val n = ((bytes[i].toInt() and 0xFF) shl 8) or (bytes[i + 1].toInt() and 0xFF)
            sb.append(ALPHABET[n % 45]).append(ALPHABET[(n / 45) % 45]).append(ALPHABET[n / 2025])
            i += 2
        }
        if (i < bytes.size) {
            val n = bytes[i].toInt() and 0xFF
            sb.append(ALPHABET[n % 45]).append(ALPHABET[n / 45])
        }
        return sb.toString()
    }
}

/** Deterministic CBOR subset encoder (uint, bstr, tstr, array); [Cbor] decodes. */
object CborEncoder {
    fun encode(item: Any): ByteArray = ByteArrayOutputStream().also { write(it, item) }.toByteArray()

    private fun head(out: ByteArrayOutputStream, major: Int, n: Long) {
        val m = major shl 5
        when {
            n < 24 -> out.write(m or n.toInt())
            n < 0x100 -> { out.write(m or 24); out.write(n.toInt()) }
            n < 0x10000 -> { out.write(m or 25); bigEndian(out, n, 2) }
            n < 0x100000000L -> { out.write(m or 26); bigEndian(out, n, 4) }
            else -> { out.write(m or 27); bigEndian(out, n, 8) }
        }
    }

    private fun bigEndian(out: ByteArrayOutputStream, n: Long, width: Int) {
        for (i in width - 1 downTo 0) out.write(((n shr (8 * i)) and 0xFF).toInt())
    }

    private fun write(out: ByteArrayOutputStream, item: Any) {
        when (item) {
            is Long -> { require(item >= 0) { "negative integers are not in this subset" }; head(out, 0, item) }
            is Int -> write(out, item.toLong())
            is ByteArray -> { head(out, 2, item.size.toLong()); out.write(item) }
            is String -> {
                val b = item.toByteArray(Charsets.UTF_8)
                head(out, 3, b.size.toLong()); out.write(b)
            }
            is List<*> -> { head(out, 4, item.size.toLong()); item.forEach { write(out, it!!) } }
            else -> throw IllegalArgumentException("${item::class} is not in this CBOR subset")
        }
    }
}

/**
 * Byte-exact mirror of Python's `json.dumps(x, separators=(",", ":"),
 * sort_keys=True, ensure_ascii=False)` after rounding floats to 4 dp. Used for
 * `scoring.canonical_event_log` (the credential digest) and for
 * `security.canonical_bundle_bytes` (the sync bundle signature). If this
 * drifts, a server-minted credential stops matching the attempt the phone
 * recorded, and the server rejects every bundle this phone sends.
 */
object CanonicalJson {
    /** JSON that is already canonical (for example a stored event payload), written as is. */
    class Raw(val json: String)

    fun encode(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    /**
     * Python's repr() of a float that has already been rounded to 4 dp: plain
     * notation, always with a decimal point. Double.toString would give
     * "5.0E-4" where Python gives "0.0005".
     */
    fun pyFloat(d: Double): String {
        require(!d.isNaN() && !d.isInfinite()) { "NaN and infinity are not JSON" }
        if (d == 0.0) return if (1.0 / d < 0) "-0.0" else "0.0"
        val plain = java.math.BigDecimal(d.toString()).stripTrailingZeros().toPlainString()
        return if ('.' in plain) plain else "$plain.0"
    }

    private fun write(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is Boolean -> sb.append(v)
            is Int, is Long -> sb.append(v)
            is Float -> write(sb, v.toString().toDouble())
            is Double -> sb.append(pyFloat(Math.rint(v * 10000.0) / 10000.0))
            is String -> string(sb, v)
            is Raw -> sb.append(v.json)
            is Map<*, *> -> {
                sb.append('{')
                v.entries.sortedBy { it.key.toString() }.forEachIndexed { i, e ->
                    if (i > 0) sb.append(',')
                    string(sb, e.key.toString()); sb.append(':'); write(sb, e.value)
                }
                sb.append('}')
            }
            is List<*> -> {
                sb.append('[')
                v.forEachIndexed { i, e -> if (i > 0) sb.append(','); write(sb, e) }
                sb.append(']')
            }
            else -> string(sb, v.toString())
        }
    }

    private fun string(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            '\b' -> sb.append("\\b")
            '' -> sb.append("\\f")
            else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
        }
        sb.append('"')
    }
}
