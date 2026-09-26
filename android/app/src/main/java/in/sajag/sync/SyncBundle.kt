package `in`.sajag.sync

import `in`.sajag.credential.CanonicalJson
import `in`.sajag.data.AttemptEntity
import `in`.sajag.data.EventEntity
import `in`.sajag.util.toHex
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * One attempt as it goes on the wire to /v1/sync/bundle.
 *
 * The body is the canonical JSON of every field, plus `deviceSig`: an Ed25519
 * signature over the canonical JSON of every OTHER field. That is byte for byte
 * what `canonical_bundle_bytes` in server/app/security.py rebuilds, so the
 * server can refuse a bundle that was altered, sent by another phone, is stale
 * or is a replay. SyncBundleTest and server/tests/test_sync_contract.py pin
 * the same golden bundle on both sides; change one and both fail.
 *
 * Event payloads are stored already canonical (see DrillScreen) and are
 * written through untouched.
 */
object SyncBundle {
    fun signed(
        attempt: AttemptEntity,
        events: List<EventEntity>,
        key: Ed25519PrivateKeyParameters,
        issuedAt: Instant = Instant.now(),
    ): String {
        val publicKey = key.generatePublicKey().encoded.toHex()
        val unsigned: Map<String, Any?> = mapOf(
            "workerId" to attempt.workerId,
            "moduleId" to attempt.moduleId,
            "tierCeiling" to attempt.tierCeiling,
            "deviceKeyId" to publicKey.take(16),
            "devicePublicKey" to publicKey,
            "issuedAt" to issuedAt.truncatedTo(ChronoUnit.SECONDS).toString(),
            "supervisorSig" to attempt.supervisorSig,
            "events" to events.sortedBy { it.seq }.map { e ->
                mapOf(
                    "eventId" to e.eventId,
                    "attemptId" to e.attemptId,
                    "seq" to e.seq,
                    "atMs" to e.atMs,
                    "beat" to e.beat,
                    "item" to e.itemId,
                    "type" to e.type,
                    "tier" to e.tier,
                    "payload" to CanonicalJson.Raw(e.payloadJson),
                )
            },
        )
        val bytes = CanonicalJson.encode(unsigned).toByteArray(Charsets.UTF_8)
        val signature = Ed25519Signer().run {
            init(true, key)
            update(bytes, 0, bytes.size)
            generateSignature()
        }
        return CanonicalJson.encode(unsigned + ("deviceSig" to signature.toHex()))
    }
}
