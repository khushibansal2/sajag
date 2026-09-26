package `in`.sajag.sync

import `in`.sajag.data.AttemptEntity
import `in`.sajag.data.EventEntity
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * The phone must sign exactly the bytes the server verifies. GOLDEN was made by
 * server/app/security.py (sign_bundle) for the same key, attempt and events;
 * server/tests/test_sync_contract.py checks the same string from the other side.
 */
class SyncBundleTest {
    private val key = Ed25519PrivateKeyParameters(ByteArray(32) { it.toByte() }, 0)
    private val attemptId = "01J8Z3Q4R5S6T7V8W9X0Y1Z2A3"

    @Test
    fun signedBundleMatchesTheServerGoldenByteForByte() {
        val attempt = AttemptEntity(attemptId, "00112233445566778899aabbccddeeff", "FIRE-01", "1", 2, 1727190000000L, 1727190009000L, null)
        val events = listOf(
            // Deliberately out of order: the bundle sorts by seq.
            EventEntity("01J8Z3Q4R5S6T7V8W9X0Y1Z2A6", attemptId, 2, 1727190009000L, "1.2", null, "WATER_ON_ELECTRICAL", 2, "{}"),
            EventEntity("01J8Z3Q4R5S6T7V8W9X0Y1Z2A4", attemptId, 0, 1727190000000L, "1.2", "ext-choice", "ITEM", 2, """{"chosen":["co2"]}"""),
            EventEntity("01J8Z3Q4R5S6T7V8W9X0Y1Z2A5", attemptId, 1, 1727190004200L, "1.3", "sweep-coverage", "ITEM", 2, """{"note":"बिरसा","value":0.82}"""),
        )
        val body = SyncBundle.signed(attempt, events, key, Instant.parse("2026-09-24T15:00:00Z"))
        assertEquals(GOLDEN, body)
    }

    private companion object {
        const val GOLDEN = """{"deviceKeyId":"03a107bff3ce10be","devicePublicKey":"03a107bff3ce10be1d70dd18e74bc09967e4d6309ba50d5f1ddc8664125531b8","deviceSig":"269a091270297f7229944b9b0e3337366446a46af4188869ca28ff1397e613c2b4e9a814525f2224f69a6244d3348de375c5966d1396843912337c4fb168a707","events":[{"atMs":1727190000000,"attemptId":"01J8Z3Q4R5S6T7V8W9X0Y1Z2A3","beat":"1.2","eventId":"01J8Z3Q4R5S6T7V8W9X0Y1Z2A4","item":"ext-choice","payload":{"chosen":["co2"]},"seq":0,"tier":2,"type":"ITEM"},{"atMs":1727190004200,"attemptId":"01J8Z3Q4R5S6T7V8W9X0Y1Z2A3","beat":"1.3","eventId":"01J8Z3Q4R5S6T7V8W9X0Y1Z2A5","item":"sweep-coverage","payload":{"note":"बिरसा","value":0.82},"seq":1,"tier":2,"type":"ITEM"},{"atMs":1727190009000,"attemptId":"01J8Z3Q4R5S6T7V8W9X0Y1Z2A3","beat":"1.2","eventId":"01J8Z3Q4R5S6T7V8W9X0Y1Z2A6","item":null,"payload":{},"seq":2,"tier":2,"type":"WATER_ON_ELECTRICAL"}],"issuedAt":"2026-09-24T15:00:00Z","moduleId":"FIRE-01","supervisorSig":null,"tierCeiling":2,"workerId":"00112233445566778899aabbccddeeff"}"""
    }
}
