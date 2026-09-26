package `in`.sajag.credential

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The on-device minter must produce exactly what the verifier accepts, and nothing it doesn't. */
class MinterRoundTripTest {
    private val key = Ed25519PrivateKeyParameters(ByteArray(32) { it.toByte() }, 0)
    private val trust = mapOf(7 to key.generatePublicKey().encoded)
    private val today = LocalDate.of(2026, 9, 13)

    private fun cred(name: String = "Birsa Munda", provisional: Boolean = true) = Credential(
        issuerIndex = 7,
        subjectId = ByteArray(16) { (it * 3).toByte() },
        name = name,
        employerCode = "CCL-DHN",
        module = "GAS-01",
        tier = 2,
        score = 91,
        competencies = mapOf("HAZ-ID" to 100, "EQP-SEL" to 100, "SEQ" to 88, "EGR" to 0, "TECH" to 76, "KNW" to 83),
        attemptDigest = CredentialMinter.attemptDigest("[]".toByteArray()),
        notBefore = today,
        expires = today.plusDays(365),
        provisional = provisional,
    )

    @Test
    fun mintedCredentialVerifies() {
        val qr = CredentialMinter.sign(cred(), key)
        val result = CredentialCodec.verify(qr, trust, today = today)
        assertTrue("expected Valid, got $result", result is VerifyResult.Valid)
        val back = (result as VerifyResult.Valid).credential
        assertEquals("Birsa Munda", back.name)
        assertEquals("GAS-01", back.module)
        assertEquals(91, back.score)
        assertEquals(88, back.competencies["SEQ"])
        assertTrue(result.warnings.any { it.startsWith("Provisional") })
    }

    @Test
    fun devanagariNameIsTruncatedOnACharacterBoundary() {
        val qr = CredentialMinter.sign(cred(name = "बिरसा मुंडा बिरसा मुंडा बिरसा"), key)
        val result = CredentialCodec.verify(qr, trust, today = today)
        assertTrue(result is VerifyResult.Valid)
        assertTrue((result as VerifyResult.Valid).credential.name.toByteArray().size <= 32)
    }

    @Test
    fun everySingleCharacterMutationIsRejected() {
        val qr = CredentialMinter.sign(cred(), key)
        val alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ \$%*+-./:"
        var accepted = 0
        for (i in 5 until qr.length) {
            val replacement = alphabet[(alphabet.indexOf(qr[i]) + 1) % alphabet.length]
            val tampered = qr.substring(0, i) + replacement + qr.substring(i + 1)
            if (CredentialCodec.verify(tampered, trust, today = today) is VerifyResult.Valid) accepted++
        }
        assertEquals(0, accepted)
    }

    @Test
    fun canonicalJsonMatchesPythonShape() {
        val json = CanonicalJson.encode(listOf(mapOf("type" to "ITEM", "beat" to "1.2", "item" to null, "value" to 0.4, "chosen" to listOf("co2"))))
        assertEquals("""[{"beat":"1.2","chosen":["co2"],"item":null,"type":"ITEM","value":0.4}]""", json)
    }
}
