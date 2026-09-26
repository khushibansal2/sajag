package `in`.sajag.ui.ar

import android.app.Activity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.android.filament.utils.KTX1Loader
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import `in`.sajag.assess.Hazard
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.arcore.createAnchorOrNull
import io.github.sceneview.ar.arcore.getUpdatedPlanes
import io.github.sceneview.ar.arcore.isValid
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.node.Node
import io.github.sceneview.rememberCollisionSystem
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberOnGestureListener
import io.github.sceneview.rememberView
import io.github.sceneview.SceneView
import io.github.sceneview.safeDestroyEnvironment
import io.github.sceneview.utils.readBuffer

enum class ArPhase {
    /** The AR session is starting. */
    STARTING,

    /** Looking for a flat surface: the worker should move the phone slowly. */
    SEARCHING,

    /** A surface is found: tap it to place the drill. */
    READY,

    /** The drill stands on a real surface. */
    PLACED,
}

/** Why tracking is poor right now, in terms the drill can explain to a worker. */
enum class ArProblem { TOO_DARK, TOO_FAST, NO_TEXTURE, CAMERA_BUSY }

/** What the drill screen needs to know about the AR view. All of it is Compose state. */
@Stable
class ArDrillState {
    var phase by mutableStateOf(ArPhase.STARTING)
        internal set
    var tracking by mutableStateOf(false)
        internal set
    var problem by mutableStateOf<ArProblem?>(null)
        internal set

    /** Set when the AR session could not start. The drill then falls back to the camera view. */
    var failure by mutableStateOf<String?>(null)
        internal set

    /** No surface after a long search: offer the camera view instead. */
    var searchingTooLong by mutableStateOf(false)
        internal set

    /** Placed automatically where the phone points, because nobody tapped. */
    var autoPlaced by mutableStateOf(false)
        internal set

    /**
     * True while the 3D scene stands on a surface and is tracked. A drill step
     * taken now is recorded as done in AR; otherwise the worker was looking at
     * the plain camera picture, and the step is recorded as camera mode.
     */
    val inUse: Boolean get() = phase == ArPhase.PLACED && tracking
}

/** Holds the latest ARCore frame and the view, for the tap handler. Not Compose state on purpose. */
private class FrameHolder {
    var frame: Frame? = null
    var view: ARSceneView? = null
    var readySinceNanos = 0L
    var startedNanos = 0L
}

private const val AUTO_PLACE_AFTER_NS = 7_000_000_000L
private const val SEARCH_TIMEOUT_NS = 20_000_000_000L

/** A neutral studio light that ships inside SceneView. */
private const val NEUTRAL_IBL = "environments/neutral/neutral_ibl.ktx"

/** A lifecycle the AR view can have, which follows the screen's own. */
private class GuardedLifecycleOwner : LifecycleOwner {
    val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
}

/**
 * The AR view's lifecycle, following the screen's. ARCore throws when it
 * cannot open the camera (another app holds it); here that becomes
 * [ArDrillState.failure], so the drill falls back to the camera view instead
 * of the app crashing.
 */
@Composable
private fun rememberGuardedLifecycle(state: ArDrillState): Lifecycle {
    val outer = LocalLifecycleOwner.current.lifecycle
    val owner = remember { GuardedLifecycleOwner() }
    DisposableEffect(outer, owner) {
        val observer = LifecycleEventObserver { _, _ ->
            if (state.failure == null) {
                try {
                    owner.registry.currentState = outer.currentState
                } catch (e: Exception) {
                    state.failure = e.message ?: e.javaClass.simpleName
                }
            }
        }
        outer.addObserver(observer)
        onDispose { outer.removeObserver(observer) }
    }
    return owner.registry
}

/**
 * World-anchored AR for one drill: the live camera, and the hazard for the
 * current step standing on a real floor or table that the worker taps.
 *
 * Uses ARCore through SceneView. Only mounted when ARCore is installed and the
 * camera is allowed (see DrillScreen); if the session still fails, [state]
 * reports it and the drill carries on in camera mode.
 */
@Composable
fun ArDrillView(
    state: ArDrillState,
    hazard: Hazard,
    showHints: Boolean,
    permitTitle: String,
    permitSteps: List<String>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val cameraNode = rememberARCameraNode(engine)
    val view = rememberView(engine)
    val collisionSystem = rememberCollisionSystem(view)
    // A fixed neutral light rather than ARCore's estimate: SceneView 2.2.1
    // multiplies the estimated main light into itself every frame until the
    // scene goes dark, and leaks a light object per estimate.
    val environment = remember(engine) {
        SceneView.createEnvironment(
            engine = engine,
            isOpaque = true,
            indirectLight = KTX1Loader.createIndirectLight(engine, context.assets.readBuffer(NEUTRAL_IBL)),
            skybox = null,
        )
    }
    DisposableEffect(environment) { onDispose { engine.safeDestroyEnvironment(environment) } }
    val lifecycle = rememberGuardedLifecycle(state)
    val childNodes = rememberNodes()
    val holder = remember { FrameHolder() }
    val rig = remember(engine, materialLoader) { HazardRig(engine, materialLoader) }

    LaunchedEffect(rig, hazard, permitTitle, permitSteps) {
        rig.show(hazard, permitSteps, permitTitle)
        rig.setHintsVisible(showHints)
    }
    LaunchedEffect(rig, showHints) { rig.setHintsVisible(showHints) }

    fun place(frame: Frame, x: Float, y: Float): Boolean {
        val hit = frame.hitTest(x, y).firstOrNull {
            it.isValid(
                planeTypes = setOf(Plane.Type.HORIZONTAL_UPWARD_FACING),
                point = false,
                depthPoint = false,
                instantPlacementPoint = false,
            )
        } ?: return false
        val anchor = hit.createAnchorOrNull() ?: return false
        rig.placeAt(anchor, cameraNode.worldPosition, childNodes)
        state.phase = ArPhase.PLACED
        return true
    }

    // The gesture listener is created once, so it reads the current handler through this.
    val onTap by rememberUpdatedState { e: MotionEvent ->
        val frame = holder.frame
        if (frame != null && state.phase != ArPhase.STARTING && place(frame, e.x, e.y)) {
            state.autoPlaced = false
        }
    }

    ARScene(
        modifier = modifier,
        engine = engine,
        modelLoader = modelLoader,
        materialLoader = materialLoader,
        view = view,
        collisionSystem = collisionSystem,
        cameraNode = cameraNode,
        childNodes = childNodes,
        planeRenderer = state.phase != ArPhase.PLACED,
        environment = environment,
        lifecycle = lifecycle,
        sessionConfiguration = { _, config ->
            config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL)
            config.setDepthMode(Config.DepthMode.DISABLED)
            config.setInstantPlacementMode(Config.InstantPlacementMode.DISABLED)
            config.setLightEstimationMode(Config.LightEstimationMode.DISABLED)
        },
        onViewCreated = { holder.view = this },
        onSessionFailed = { e -> state.failure = e.message ?: e.javaClass.simpleName },
        onTrackingFailureChanged = { reason -> state.problem = reason?.toProblem() },
        onSessionUpdated = { _, frame ->
            holder.frame = frame
            val now = frame.timestamp
            if (holder.startedNanos == 0L) holder.startedNanos = now
            val tracking = frame.camera.trackingState == TrackingState.TRACKING
            if (state.tracking != tracking) state.tracking = tracking

            when (state.phase) {
                ArPhase.STARTING -> state.phase = ArPhase.SEARCHING
                ArPhase.SEARCHING -> {
                    val found = frame.getUpdatedPlanes().any {
                        it.type == Plane.Type.HORIZONTAL_UPWARD_FACING && it.trackingState == TrackingState.TRACKING
                    }
                    if (found) {
                        state.phase = ArPhase.READY
                        holder.readySinceNanos = now
                    } else if (!state.searchingTooLong && now - holder.startedNanos > SEARCH_TIMEOUT_NS) {
                        state.searchingTooLong = true
                    }
                }
                ArPhase.READY -> {
                    // Nobody tapped: place the drill where the phone is pointing.
                    val v = holder.view
                    if (v != null && tracking && now - holder.readySinceNanos > AUTO_PLACE_AFTER_NS &&
                        place(frame, v.width / 2f, v.height * 0.4f)
                    ) {
                        state.autoPlaced = true
                    }
                }
                ArPhase.PLACED -> Unit
            }
            rig.onFrame(now, cameraNode.worldPosition)
        },
        onGestureListener = rememberOnGestureListener(
            onSingleTapConfirmed = { e: MotionEvent, _: Node? -> onTap(e) },
        ),
    )

    // Declared after ARScene on purpose. Compose tears down in reverse order, so
    // this runs first, while the scene, the view and the ARCore session are all
    // still alive: the anchor is detached before the session starts closing.
    DisposableEffect(rig) {
        onDispose {
            rig.destroy(holder.view, childNodes)
            holder.view = null
            holder.frame = null
            // SceneView turns the screen-on flag on at every resume and never turns it off.
            (context as? Activity)?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

private fun TrackingFailureReason.toProblem(): ArProblem? = when (this) {
    TrackingFailureReason.NONE -> null
    TrackingFailureReason.INSUFFICIENT_LIGHT -> ArProblem.TOO_DARK
    TrackingFailureReason.EXCESSIVE_MOTION -> ArProblem.TOO_FAST
    TrackingFailureReason.INSUFFICIENT_FEATURES -> ArProblem.NO_TEXTURE
    else -> ArProblem.CAMERA_BUSY
}
