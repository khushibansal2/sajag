package `in`.sajag.credential

import android.content.Context
import `in`.sajag.BuildConfig
import `in`.sajag.util.hexToBytes
import `in`.sajag.util.toHex
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import java.security.SecureRandom

/**
 * Who signs a provisional certificate, and whose signatures this phone trusts.
 *
 * RELEASE: certificates are signed with the device's own enrolment key (issuer
 * index [DEVICE_ISSUER_INDEX]). The key is registered with the training centre
 * at enrolment. The phone trusts its own key plus the training centre's issuer
 * key, which is baked in at build time:
 *   gradlew assembleWorkerRelease -PsajagIssuerPublicKey=<64 hex chars>
 * The published development key is never trusted in a release build, so a
 * certificate forged with it is rejected by every release verifier.
 * TODO(before pilot): move the device key into Android Keystore;
 * SharedPreferences is not a secure element.
 *
 * DEBUG: signs with the development issuer key that the FastAPI server and the
 * web dashboard also use (index 7, bytes 0..31). That is what lets a
 * certificate minted on one demo phone verify on a second phone and in the
 * dashboard. It is a published key and anyone can sign with it, which is why
 * only debug builds sign with it or trust it.
 */
object DeviceIdentity {
    const val DEV_ISSUER_INDEX = 7
    const val DEVICE_ISSUER_INDEX = 200

    private val devIssuerKey = Ed25519PrivateKeyParameters(ByteArray(32) { it.toByte() }, 0)

    fun deviceKey(context: Context): Ed25519PrivateKeyParameters {
        val prefs = context.getSharedPreferences("identity", Context.MODE_PRIVATE)
        prefs.getString("device_sk", null)?.let { return Ed25519PrivateKeyParameters(it.hexToBytes(), 0) }
        val key = Ed25519PrivateKeyParameters(SecureRandom())
        prefs.edit().putString("device_sk", key.encoded.toHex()).apply()
        return key
    }

    /** The 16-hex-character id the server knows this device by: the start of its public key. */
    fun deviceKeyId(context: Context): String =
        deviceKey(context).generatePublicKey().encoded.toHex().take(16)

    /** (issuer index, key) used to sign certificates on this build. */
    fun signer(context: Context): Pair<Int, Ed25519PrivateKeyParameters> =
        if (BuildConfig.DEBUG) DEV_ISSUER_INDEX to devIssuerKey
        else DEVICE_ISSUER_INDEX to deviceKey(context)

    /**
     * The issuer keys this phone accepts. In production this becomes the signed
     * list from /v1/trust-list.
     */
    fun trustList(context: Context): Map<Int, ByteArray> {
        val list = LinkedHashMap<Int, ByteArray>()
        if (BuildConfig.DEBUG) {
            list[DEV_ISSUER_INDEX] = devIssuerKey.generatePublicKey().encoded
        } else {
            val issuerHex = BuildConfig.ISSUER_PUBLIC_KEY_HEX
            if (issuerHex.length == 64) list[DEV_ISSUER_INDEX] = issuerHex.hexToBytes()
        }
        list[DEVICE_ISSUER_INDEX] = deviceKey(context).generatePublicKey().encoded
        return list
    }
}
