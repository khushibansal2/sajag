package `in`.sajag.credential

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

/**
 * The cross-language gate.
 *
 * The server mints certificates with the Python implementation; this phone
 * verifies them with `CredentialCodec.kt`. If those two disagree by one byte,
 * real certificates start failing at a pit mouth and nobody finds out until a
 * worker is turned away from a shift.
 *
 * Note this is a **unit test, not an instrumented test** — `src/test`, not
 * `src/androidTest`. The codec is pure JVM: BouncyCastle, `java.util.zip`, and
 * arithmetic. No device, no emulator, no camera. So it runs in CI on every push
 * in about a second, which is the only reason a test like this actually gets
 * run rather than admired.
 *
 * Regenerate the fixture whenever the Python codec changes:
 *
 *     cd core && python3 cli.py vectors 20 > ../jvm-vectors/vectors.json
 *     cd ../jvm-vectors && python3 tsv.py
 *     cp vectors.tsv ../android/app/src/test/resources/
 *
 * Never edit one side of the codec alone.
 */
class CodecVectorTest {

    private data class Vector(
        val qr: String,
        val issuerIndex: Int,
        val subjectIdHex: String,
        val name: String,
        val employerCode: String,
        val module: String,
        val tier: Int,
        val score: Int,
        val competencies: List<Int>,
        val attemptDigestHex: String,
        val notBefore: String,
        val expires: String,
        val provisional: Boolean,
    )

    private lateinit var issuerKeyHex: String
    private lateinit var vectors: List<Vector>

    private fun load() {
        val stream = javaClass.classLoader!!.getResourceAsStream("vectors.tsv")
            ?: fail("vectors.tsv missing from test resources — see the header of this file")
                .let { throw IllegalStateException("unreachable") }
        val lines = stream.bufferedReader(Charsets.UTF_8).readLines()
        issuerKeyHex = lines.first().trim()
        vectors = lines.drop(1).filter { it.isNotBlank() }.map { line ->
            val f = line.split("\t")
            Vector(
                qr = f[0], issuerIndex = f[1].toInt(), subjectIdHex = f[2], name = f[3],
                employerCode = f[4], module = f[5], tier = f[6].toInt(), score = f[7].toInt(),
                competencies = f[8].split(",").map(String::toInt),
                attemptDigestHex = f[9], notBefore = f[10], expires = f[11],
                provisional = f[12].toBoolean(),
            )
        }
    }

    private fun trustList(): Map<Int, ByteArray> = mapOf(7 to hex(issuerKeyHex))

    @Test
    fun `decodes every Python-minted credential identically`() {
        load()
        assertTrue("no vectors loaded", vectors.isNotEmpty())

        for ((i, v) in vectors.withIndex()) {
            // Verify as of a date inside the credential's own window, so this
            // test does not start failing in 2028 for the wrong reason.
            val midpoint = LocalDate.parse(v.notBefore).plusDays(1)
            val result = CredentialCodec.verify(v.qr, trustList(), today = midpoint)

            val cred = when (result) {
                is VerifyResult.Valid -> result.credential
                is VerifyResult.Rejected ->
                    throw AssertionError("vector $i rejected: ${result.reason}")
            }

            assertEquals("vector $i issuerIndex", v.issuerIndex, cred.issuerIndex)
            assertEquals("vector $i subjectId", v.subjectIdHex, hex(cred.subjectId))
            assertEquals("vector $i name", v.name, cred.name)
            assertEquals("vector $i employerCode", v.employerCode, cred.employerCode)
            assertEquals("vector $i module", v.module, cred.module)
            assertEquals("vector $i tier", v.tier, cred.tier)
            assertEquals("vector $i score", v.score, cred.score)
            assertEquals("vector $i attemptDigest", v.attemptDigestHex, hex(cred.attemptDigest))
            assertEquals("vector $i notBefore", v.notBefore, cred.notBefore.toString())
            assertEquals("vector $i expires", v.expires, cred.expires.toString())
            assertEquals("vector $i provisional", v.provisional, cred.provisional)

            COMPETENCIES.forEachIndexed { c, code ->
                assertEquals("vector $i competency $code",
                    v.competencies[c], cred.competencies[code])
            }
        }
    }

    /**
     * The fixture deliberately contains Devanagari and Ol Chiki names. A
     * 28-character Hindi name is 84 UTF-8 bytes; the budget is in bytes and
     * truncation lands on a character boundary. If this ever fails, someone has
     * changed the truncation to cut by characters and a worker's name will come
     * back as half a codepoint.
     */
    @Test
    fun `handles Indic script names byte-identically to Python`() {
        load()
        val indic = vectors.filter { v -> v.name.any { it.code > 0x7F } }
        assertTrue("fixture has no Indic-script names — regenerate it", indic.isNotEmpty())
        for (v in indic) {
            val midpoint = LocalDate.parse(v.notBefore).plusDays(1)
            val r = CredentialCodec.verify(v.qr, trustList(), today = midpoint)
            assertTrue("Indic vector rejected", r is VerifyResult.Valid)
            assertEquals(v.name, (r as VerifyResult.Valid).credential.name)
        }
    }

    /**
     * The tamper demo, exhaustively. This is the moment in the six-minute jury
     * demo where a QR with one character changed comes back red — so it had
     * better be true for every character, not the one we tried on stage.
     */
    @Test
    fun `no single-character mutation ever verifies`() {
        load()
        val qr = vectors.first().qr
        val today = LocalDate.parse(vectors.first().notBefore).plusDays(1)
        var accepted = 0
        var mutated = 0

        for (pos in PREFIX_LEN until qr.length) {
            val idx = B45.indexOf(qr[pos])
            if (idx < 0) continue
            val swapped = B45[(idx + 1) % 45]
            val bad = qr.substring(0, pos) + swapped + qr.substring(pos + 1)
            mutated++
            when (CredentialCodec.verify(bad, trustList(), today = today)) {
                is VerifyResult.Valid -> accepted++
                is VerifyResult.Rejected -> Unit   // correct
            }
        }

        assertTrue("no mutations were generated", mutated > 100)
        assertEquals("$accepted of $mutated tampered codes verified", 0, accepted)
    }

    @Test
    fun `a credential signed by a stranger is rejected`() {
        load()
        val strangerKey = ByteArray(32) { 0x42 }
        val r = CredentialCodec.verify(vectors.first().qr, mapOf(7 to strangerKey))
        assertTrue(r is VerifyResult.Rejected)
    }

    @Test
    fun `an unknown issuer index names the problem instead of failing vaguely`() {
        load()
        val r = CredentialCodec.verify(vectors.first().qr, mapOf(99 to hex(issuerKeyHex)))
        assertTrue(r is VerifyResult.Rejected)
        assertTrue((r as VerifyResult.Rejected).reason.contains("Unknown issuer"))
    }

    /**
     * A verifier that crashes on a bad scan is a verifier an inspector stops
     * trusting. The Python suite found exactly this bug — a tampered payload
     * producing invalid UTF-8 threw instead of rejecting — so it is pinned on
     * both sides.
     */
    @Test
    fun `garbage input is rejected, never thrown`() {
        load()
        val junk = listOf(
            "", "hello", "SJG1:", "SJG1:ZZZZ", "SJG1:" + "0".repeat(400),
            "SJG1:%%%%%%", "https://example.com/cert/1", "SJG1:BB8B",
        )
        for (j in junk) {
            val r = try {
                CredentialCodec.verify(j, trustList())
            } catch (e: Exception) {
                throw AssertionError("verify() threw on input ${j.take(16)!!}: $e")
            }
            assertTrue("accepted junk: $j", r is VerifyResult.Rejected)
        }
    }

    /**
     * Belt and braces: prove BouncyCastle is actually wired up. If the
     * dependency is missing or stripped by R8, every other test in this class
     * would fail confusingly; this one fails clearly.
     */
    @Test
    fun `bouncycastle Ed25519 is available`() {
        val pub = Ed25519PublicKeyParameters(ByteArray(32) { 1 }, 0)
        val signer = Ed25519Signer().apply { init(false, pub); update(ByteArray(4), 0, 4) }
        signer.verifySignature(ByteArray(64))   // false is fine; not throwing is the point
    }

    private companion object {
        const val PREFIX_LEN = 5
        const val B45 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ \$%*+-./:"

        fun hex(s: String) = ByteArray(s.length / 2) {
            s.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }

        fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    }
}
