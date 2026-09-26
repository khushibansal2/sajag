package `in`.sajag.capability

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.app.ActivityManager
import com.google.ar.core.ArCoreApk

/**
 * The capability ladder — decision D-02, and the single most load-bearing
 * piece of logic in the app.
 *
 * Two separate failures need one mechanism:
 *
 *   the DEVICE failure  ARCore runs only on Google-certified models. The list
 *                       is broad above roughly Rs 15,000 and thin below it,
 *                       and the app cannot install ARCore for a worker whose
 *                       phone was never certified.
 *
 *   the SITE failure    A dark, dusty, featureless underground gallery defeats
 *                       markerless plane detection on ANY phone, flagship
 *                       included. A fallback built only for cheap hardware
 *                       still dies underground.
 *
 * So the probe sets a CEILING once, and each drill uses the best mode that
 * works right now: AR while the scene stands on a tracked surface, the camera
 * view when AR is missing or lost, a drawn gallery with no camera. Every event
 * records the mode really on screen. All three modes emit the same telemetry
 * into the same scoring rubric and produce the same credential schema. The
 * mode is recorded on the certificate (its "tier" field), never hidden.
 *
 * Do not "simplify" this to a single ARCore availability check. That check is
 * how a demo passes on the developer's phone and fails on the user's.
 */
/**
 * The three drill modes. [wire] is the value of the certificate's "tier"
 * field; the app calls them AR, Camera and Guided and never shows "tier".
 */
enum class Tier(val wire: Int) {
    /** AR: ARCore tracking; the hazard stands on a real floor or table and stays there. */
    WORLD_ANCHORED(1),

    /** Camera: hazards drawn over the live camera picture. Needs no ARCore. */
    MARKER_IMU(2),

    /** Guided: a drawn mine gallery and touch. Needs no camera. Same decisions,
     *  same rubric, timings calibrated for this mode. */
    GUIDED_2D(3);

    companion object {
        fun fromWire(v: Int) = entries.first { it.wire == v }
    }
}

data class DeviceCapability(
    val ceiling: Tier,
    val arcoreState: String,
    val hasGyroscope: Boolean,
    val totalRamMb: Int,
    val sustainedPerformance: Boolean,
    val reason: String,
) {
    /** Why the phone got this ceiling, for support logs. The drill itself tells
     *  the worker which mode it is in; nobody is silently downgraded. */
    fun explain(): String = reason
}

object TierProbe {

    private const val MIN_RAM_MB_FOR_AR = 3_000
    private const val MIN_RAM_MB_AT_ALL = 1_500

    /**
     * Runs once at enrolment and again after any OS update. Cheap enough to
     * run on demand; never run it inside a scene.
     *
     * [ArCoreApk.checkAvailability] can return a TRANSIENT state on first call
     * while it queries Play Services. Callers must treat a transient result as
     * "ask again shortly", not as "unsupported" — getting this wrong is how a
     * certified phone gets pinned to camera mode forever.
     */
    fun probe(context: Context): DeviceCapability {
        val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val hasGyro = sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
        val hasCamera = context.packageManager
            .hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val totalRamMb = (memInfo.totalMem / (1024 * 1024)).toInt()

        val sustained = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            (context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager)
                .isSustainedPerformanceModeSupported

        val arState = runCatching { ArCoreApk.getInstance().checkAvailability(context) }
            .getOrNull()
        val arName = arState?.name ?: "QUERY_FAILED"
        val arSupported = arState?.isSupported == true

        val ceiling: Tier
        val reason: String
        when {
            !hasCamera || totalRamMb < MIN_RAM_MB_AT_ALL -> {
                ceiling = Tier.GUIDED_2D
                reason = "No usable camera or too little memory for a camera session. " +
                    "Drills run in guided mode; the certificate says so."
            }
            arSupported && hasGyro && totalRamMb >= MIN_RAM_MB_FOR_AR -> {
                ceiling = Tier.WORLD_ANCHORED
                reason = "AR is available on this phone."
            }
            else -> {
                // The camera view draws hazards over the picture; it needs no gyroscope.
                ceiling = Tier.MARKER_IMU
                reason = "This phone cannot run AR ($arName" + (if (hasGyro) "" else ", no gyroscope") +
                    (if (totalRamMb < MIN_RAM_MB_FOR_AR) ", $totalRamMb MB RAM" else "") +
                    "). Drills draw the hazards over the camera picture."
            }
        }

        return DeviceCapability(
            ceiling = ceiling,
            arcoreState = arName,
            hasGyroscope = hasGyro,
            totalRamMb = totalRamMb,
            sustainedPerformance = sustained,
            reason = reason,
        )
    }
}

/** Whether world-anchored AR can run right now, as the Settings screen explains it. */
enum class ArSupport {
    /** Google Play Services for AR is installed and this phone is supported. */
    READY,

    /** Supported phone, but Google Play Services for AR is missing or too old. */
    NEEDS_INSTALL,

    /** Play Services is still answering; ask again shortly. */
    CHECKING,

    /** This phone cannot run ARCore. The drill uses the camera view. */
    UNSUPPORTED;

    companion object {
        fun check(context: Context): ArSupport {
            val availability = runCatching { ArCoreApk.getInstance().checkAvailability(context) }.getOrNull()
                ?: return UNSUPPORTED
            return when {
                availability == ArCoreApk.Availability.SUPPORTED_INSTALLED -> READY
                availability == ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD ||
                    availability == ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> NEEDS_INSTALL
                availability.isTransient -> CHECKING
                else -> UNSUPPORTED
            }
        }

        /**
         * Opens Google Play to install or update Google Play Services for AR.
         * Returns false if the phone cannot take it or the request failed.
         */
        fun requestInstall(activity: android.app.Activity): Boolean = runCatching {
            ArCoreApk.getInstance().requestInstall(activity, true)
            true
        }.getOrDefault(false)
    }
}

/**
 * Runtime demotion for the Unity content player (not used by the Compose
 * drill, which records the mode per event instead). Watches ARCore tracking
 * quality during a scene and drops to camera mode when the session stops being
 * trustworthy — which in a dark gallery is common and expected, not exceptional.
 *
 * Demotion is one-way within a beat. Flapping between modes mid-beat is far
 * more disorienting for the worker than simply continuing in camera mode, and
 * it would make the timing rubric meaningless.
 */
class TrackingWatchdog(
    private val ceiling: Tier,
    private val onDemote: (from: Tier, to: Tier, why: String) -> Unit,
) {
    var current: Tier = ceiling
        private set

    private var poorFrames = 0

    /** Call once per frame with ARCore's tracking state. */
    fun onFrame(trackingOk: Boolean, featurePoints: Int, luxEstimate: Float) {
        if (current != Tier.WORLD_ANCHORED) return

        val poor = !trackingOk || featurePoints < MIN_FEATURE_POINTS || luxEstimate < MIN_LUX
        poorFrames = if (poor) poorFrames + 1 else 0

        if (poorFrames >= DEMOTE_AFTER_FRAMES) {
            val why = when {
                luxEstimate < MIN_LUX -> "too dark to track"
                featurePoints < MIN_FEATURE_POINTS -> "surfaces have no features to track"
                else -> "tracking lost"
            }
            current = Tier.MARKER_IMU
            onDemote(Tier.WORLD_ANCHORED, current, why)
        }
    }

    /** Reset between beats. The next beat starts at the ceiling again — a dark
     *  corner should not condemn the whole module to camera mode. */
    fun resetForNextBeat() {
        current = ceiling
        poorFrames = 0
    }

    private companion object {
        /** ~2 seconds at 30 fps. Long enough not to fire on a hand wave,
         *  short enough that the worker is not left aiming at nothing. */
        const val DEMOTE_AFTER_FRAMES = 60
        const val MIN_FEATURE_POINTS = 15
        const val MIN_LUX = 8f
    }
}
