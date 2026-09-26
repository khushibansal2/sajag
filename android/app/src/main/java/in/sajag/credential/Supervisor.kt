package `in`.sajag.credential

import android.content.Context
import `in`.sajag.util.hexToBytes
import `in`.sajag.util.toHex
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** The supervisor set up on this phone. [keyId] is how the training centre refers to them. */
data class SupervisorInfo(val name: String, val publicKeyHex: String) {
    val keyId: String get() = publicKeyHex.take(16)
}

/**
 * Supervisor co-signature on a shared training-centre phone (decision D-03,
 * anti-proxy without biometrics).
 *
 * Before a drill starts, the supervisor enters their PIN and this signs
 * "attemptId|workerId" with their Ed25519 key: exactly the message
 * `verify_supervisor_cosignature` in server/app/security.py checks. The result
 * is stored with the attempt as "publicKeyHex.signatureHex" and synced with it.
 *
 * The private key is kept encrypted with AES-256-GCM under a key derived from
 * the PIN (PBKDF2-HMAC-SHA256), so the worker holding the phone cannot sign
 * for the supervisor. A 4-digit PIN is a speed bump, not a vault: someone who
 * copies the phone's storage can try every PIN offline.
 * TODO(before pilot): keep the key in Android Keystore behind user
 * authentication, and register supervisor keys through the admin endpoint.
 *
 * Every function here does slow key derivation. Call from a background
 * dispatcher, never on the main thread.
 */
object Supervisor {
    private const val PREFS = "supervisor"
    private const val ROUNDS = 120_000

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun current(ctx: Context): SupervisorInfo? {
        val p = prefs(ctx)
        val name = p.getString("name", null) ?: return null
        val pub = p.getString("pub", null) ?: return null
        return SupervisorInfo(name, pub)
    }

    fun isValidPin(pin: String): Boolean = pin.length in 4..8 && pin.all { it.isDigit() }

    /** True when demo mode set this supervisor up (PIN 1234), so demo mode can remove it again. */
    fun isDemo(ctx: Context): Boolean = prefs(ctx).getBoolean("demo", false)

    fun setUp(ctx: Context, name: String, pin: String, demo: Boolean = false): SupervisorInfo {
        require(isValidPin(pin)) { "PIN must be 4 to 8 digits" }
        require(name.isNotBlank()) { "name is required" }
        val random = SecureRandom()
        val key = Ed25519PrivateKeyParameters(random)
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, pinKey(pin, salt), GCMParameterSpec(128, iv))
        val sealed = cipher.doFinal(key.encoded)
        val info = SupervisorInfo(name.trim(), key.generatePublicKey().encoded.toHex())
        prefs(ctx).edit()
            .putString("name", info.name)
            .putString("pub", info.publicKeyHex)
            .putString("salt", salt.toHex())
            .putString("iv", iv.toHex())
            .putString("sealed", sealed.toHex())
            .putBoolean("demo", demo)
            .apply()
        return info
    }

    fun remove(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }

    /** True if [pin] is this phone's supervisor PIN. Slow, like everything here. */
    fun checkPin(ctx: Context, pin: String): Boolean = isValidPin(pin) && unseal(ctx, pin) != null

    /** "publicKeyHex.signatureHex" over "attemptId|workerId", or null if the PIN is wrong. */
    fun coSign(ctx: Context, pin: String, attemptId: String, workerId: String): String? {
        val pub = prefs(ctx).getString("pub", null) ?: return null
        val raw = unseal(ctx, pin) ?: return null
        val key = Ed25519PrivateKeyParameters(raw, 0)
        val message = "$attemptId|$workerId".toByteArray(Charsets.UTF_8)
        val signature = Ed25519Signer().run {
            init(true, key)
            update(message, 0, message.size)
            generateSignature()
        }
        return "$pub.${signature.toHex()}"
    }

    /** The private key, or null if the PIN is wrong or no supervisor is set up. */
    private fun unseal(ctx: Context, pin: String): ByteArray? {
        val p = prefs(ctx)
        return runCatching {
            val salt = p.getString("salt", null)!!.hexToBytes()
            val iv = p.getString("iv", null)!!.hexToBytes()
            val sealed = p.getString("sealed", null)!!.hexToBytes()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, pinKey(pin, salt), GCMParameterSpec(128, iv))
            cipher.doFinal(sealed)
        }.getOrNull()
    }

    private fun pinKey(pin: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ROUNDS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }
}
