package `in`.sajag.ui.ar

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.google.ar.core.Anchor
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Quaternion
import dev.romainguy.kotlin.math.inverse
import dev.romainguy.kotlin.math.rotation
import `in`.sajag.assess.Hazard
import `in`.sajag.ui.Ic
import io.github.sceneview.SceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.ImageNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The 3D content of one drill beat, standing on a real surface.
 *
 * Everything hangs off one [root] node, which hangs off one ARCore anchor, so
 * the fire stays on the table you tapped while you walk around it. Solid
 * things (a switchgear panel, cylinders, a tripod) are lit 3D shapes; fire,
 * smoke, glows and signs are unlit picture cards from [Sprites]. Picture cards
 * marked as billboards turn to face the phone every frame, around the vertical
 * axis only, so flames stay upright.
 *
 * Distances are real metres at [FLOOR_SCALE]. Placed on something closer to
 * the phone than a floor (a table or a bench), the whole scene is shown at
 * [TABLE_SCALE] so it fits. Anything that gives an answer away (the danger
 * ring's size, where each gas collects, the permit order) is under [hints],
 * which the drill hides while the worker answers.
 *
 * Must be used on the main thread, the thread that owns the Filament engine.
 */
internal class HazardRig(
    private val engine: Engine,
    private val materials: MaterialLoader,
) {
    val root: Node = Node(engine).apply { quiet() }

    private var anchorNode: AnchorNode? = null
    private var content: Node? = null
    private var hints: Node? = null
    private val created = mutableListOf<Node>()
    private val billboards = mutableListOf<Node>()
    private val animations = mutableListOf<(Float) -> Unit>()
    private val colors = HashMap<Long, MaterialInstance>()

    private var baseScale = FLOOR_SCALE
    private var appearedAt = -1f
    private var startNanos = -1L
    var isPlaced = false
        private set

    // ------------------------------------------------------------------ placing

    /** Puts the scene on [anchor], facing the phone at [cameraPosition]. Replaces any earlier anchor. */
    fun placeAt(anchor: Anchor, cameraPosition: Float3, sceneNodes: MutableList<Node>) {
        val existing = anchorNode
        val node = if (existing == null) {
            AnchorNode(engine = engine, anchor = anchor).also { n ->
                n.quiet()
                n.isPositionEditable = false
                n.addChildNode(root)
                anchorNode = n
                sceneNodes += n
            }
        } else {
            existing.detachAnchor()
            existing.anchor = anchor
            existing
        }
        val at = node.worldPosition
        // A surface a metre or more below the phone is a floor; closer is a table or a bench.
        baseScale = if (cameraPosition.y - at.y >= 1.0f) FLOOR_SCALE else TABLE_SCALE
        val yaw = yawTowards(at, cameraPosition)
        root.worldRotation = Float3(0f, yaw, 0f)
        appearedAt = -1f
        isPlaced = true
    }

    // ------------------------------------------------------------------ content

    /** Builds the scene for [hazard]. [steps] are the permit steps in the worker's language. */
    fun show(hazard: Hazard, steps: List<String>, permitTitle: String) {
        clearContent()
        val c = Node(engine).apply { quiet() }
        val h = Node(engine).apply { quiet() }
        root.addChildNode(c)
        c.addChildNode(h)
        // Destroyed in reverse order, so these two go last, after their children.
        created += c
        created += h
        content = c
        hints = h
        when (hazard) {
            Hazard.ELECTRICAL_FIRE -> electricalFire(c, h)
            Hazard.EXTINGUISHER_RACK -> extinguisherRack(c)
            Hazard.FIRE_ATTACK -> fireAttack(c, h)
            Hazard.SMOKE_EGRESS -> smokeEgress(c)
            Hazard.ALARM -> alarm(c)
            Hazard.GAS_LAYERS -> gasLayers(c, h)
            Hazard.GAS_DETECTOR -> gasDetector(c)
            Hazard.PPE_RACK -> ppeRack(c)
            Hazard.PERMIT_BOARD -> permitBoard(c, h, steps, permitTitle)
            Hazard.SUMP_RESCUE -> sumpRescue(c, h)
        }
        appearedAt = -1f
    }

    fun setHintsVisible(visible: Boolean) {
        hints?.isVisible = visible
    }

    /** Once per camera frame: animate, pop in after placing, and turn the cards to the phone. */
    fun onFrame(frameNanos: Long, cameraPosition: Float3) {
        if (startNanos < 0) startNanos = frameNanos
        val t = (frameNanos - startNanos) / 1_000_000_000f
        if (!isPlaced) return
        if (appearedAt < 0) appearedAt = t
        val pop = easeOutBack(min(1f, (t - appearedAt) / 0.45f))
        val s = baseScale * max(0.001f, pop)
        root.scale = Float3(s, s, s)
        for (a in animations) a(t)
        for (b in billboards) {
            if (!b.isVisible) continue
            val facing = Quaternion.fromEuler(Float3(0f, yawTowards(b.worldPosition, cameraPosition), 0f))
            // Not worldRotation: SceneView reads the parent's scale into that
            // conversion, and the whole rig is scaled (table size, pop-in).
            val parent = b.parent
            b.quaternion = if (parent == null) facing else inverse(rotation(parent.worldTransform).toQuaternion()) * facing
        }
    }

    /**
     * Frees everything this rig made, and takes the anchor out of [sceneNodes]
     * so nothing destroys it twice. Call while the scene and the ARCore session
     * are still alive: detaching an anchor from a closing session is unsafe.
     * Material instances belong to the loader, which frees them with the view.
     */
    fun destroy(sceneView: SceneView?, sceneNodes: MutableList<Node>) {
        clearContent()
        // The root first, while its parent is still in the scene, so its entities leave the scene cleanly.
        runCatching { root.destroy() }
        val anchor = anchorNode
        anchorNode = null
        if (anchor != null) {
            runCatching { sceneView?.removeChildNode(anchor) }
            sceneNodes.remove(anchor)
            runCatching { anchor.destroy() }
        }
        colors.clear()
        isPlaced = false
    }

    private fun clearContent() {
        animations.clear()
        billboards.clear()
        content?.let { root.removeChildNode(it) }
        // Children before parents, so every node is detached before it is destroyed.
        created.asReversed().forEach { node -> runCatching { node.destroy() } }
        created.clear()
        content = null
        hints = null
    }

    // ------------------------------------------------------------------ scenes

    private fun electricalFire(c: Node, h: Node) {
        cube(c, Float3(0.5f, 0.8f, 0.22f), Float3(0f, 0f, 0f), Color(0xFF78909C), metallic = 0.5f)
        cube(c, Float3(0.44f, 0.02f, 0.02f), Float3(0f, 0.62f, 0.115f), Color(0xFF37474F))
        cube(c, Float3(0.03f, 0.12f, 0.03f), Float3(0.19f, 0.34f, 0.12f), Color(0xFF263238))
        card(c, Sprites.electricalWarning(), 0.2f, Float3(0f, 0.5f, 0.112f), billboard = false)
        fire(c, Float3(0f, 0f, 0.2f), 0.75f)
        floorRing(h, Sprites.ring(RED, dashed = true), 2.0f, Float3(0f, 0.004f, 0f), pulse = true)
    }

    private fun extinguisherRack(c: Node) {
        cube(c, Float3(1.12f, 0.04f, 0.26f), Float3(0f, 0f, 0f), Color(0xFF8D6E63), roughness = 0.9f)
        val base = 0.04f
        cylinderUnit(c, -0.42f, base, band = null, label = Sprites.label("WATER", WHITE, 0xFF1565C0.toInt(), "पानी"))
        cylinderUnit(c, -0.14f, base, band = Color(0xFFFFF3C4), label = Sprites.label("FOAM", BLACK, 0xFFFFF3C4.toInt(), "फोम"))
        cylinderUnit(c, 0.14f, base, band = Color(0xFF212121), label = Sprites.label("CO₂", WHITE, 0xFF212121.toInt()))
        // Sand bucket: short and wide, sand on top.
        cylinder(c, 0.11f, 0.2f, Float3(0.42f, base, 0f), Color(0xFFD32F2F))
        cylinder(c, 0.1f, 0.012f, Float3(0.42f, base + 0.19f, 0f), Color(0xFFE0C07A), roughness = 1f)
        card(c, Sprites.label("SAND", BLACK, 0xFFE0C07A.toInt(), "रेत"), 0.18f, Float3(0.42f, 0.36f, 0f))
        fire(c, Float3(0.95f, 0f, -0.1f), 0.5f)
    }

    private fun cylinderUnit(c: Node, x: Float, base: Float, band: Color?, label: Bitmap) {
        cylinder(c, 0.07f, 0.46f, Float3(x, base, 0f), Color(0xFFD32F2F), roughness = 0.35f)
        cylinder(c, 0.03f, 0.05f, Float3(x, base + 0.46f, 0f), Color(0xFF212121))
        if (band != null) cylinder(c, 0.073f, 0.08f, Float3(x, base + 0.3f, 0f), band)
        sphere(c, 0.02f, Float3(x, base + 0.4f, 0.068f), Color(0xFFF5F5F5))
        card(c, label, 0.2f, Float3(x, 0.64f, 0f))
    }

    private fun fireAttack(c: Node, h: Node) {
        flat(c, Sprites.disc(0xF0201510.toInt(), 0x00201510), 0.7f, 0.7f, Float3(0f, 0.003f, 0f))
        fire(c, Float3(0f, 0f, 0f), 1.25f)
        floorRing(h, Sprites.ring(RED, dashed = false), 2.0f, Float3(0f, 0.006f, 0f), pulse = true)
        floorRing(h, Sprites.ring(GREEN, dashed = true), 5.0f, Float3(0f, 0.006f, 0f), pulse = false)
        card(h, Sprites.label("1 m", WHITE, RED_BG), 0.16f, Float3(0f, 0.12f, 1.0f))
        card(h, Sprites.label("2-3 m", WHITE, GREEN_BG), 0.2f, Float3(0f, 0.12f, 2.5f))
    }

    private fun smokeEgress(c: Node) {
        fire(c, Float3(0.7f, 0f, -1.4f), 0.8f)
        val layer = listOf(
            Float3(-1.1f, 1.55f, -0.8f), Float3(-0.4f, 1.75f, -0.4f), Float3(0.3f, 1.6f, -0.9f),
            Float3(0.9f, 1.8f, -0.2f), Float3(-0.8f, 1.35f, 0.3f), Float3(0.5f, 1.3f, 0.4f),
            Float3(0f, 1.5f, 0.2f), Float3(-0.2f, 1.1f, -1.2f), Float3(1.1f, 1.15f, -1.0f),
        )
        layer.forEachIndexed { i, p ->
            val puff = card(c, Sprites.smoke(dark = i % 2 == 0), 1.3f, p, height = 1.3f)
            val phase = i * 1.37f
            animations += { t ->
                puff.position = Float3(p.x + 0.12f * sin(0.25f * t + phase), p.y + 0.05f * sin(0.6f * t + phase), p.z)
            }
        }
        card(c, Sprites.label("EXIT", WHITE, 0xFF2E7D32.toInt(), "निकास"), 0.45f, Float3(-1.4f, 0.95f, -0.5f))
        for (i in 0 until 4) {
            val arrow = flat(c, Sprites.arrow(GREEN), 0.28f, 0.28f, Float3(-0.25f - i * 0.32f, 0.005f, -0.1f - i * 0.08f))
            arrow.rotation = Float3(0f, 90f, 0f)
            animations += { t ->
                val k = 1f + 0.25f * max(0f, sin(4f * t - i * 0.9f))
                arrow.scale = Float3(k, 1f, k)
            }
        }
    }

    private fun alarm(c: Node) {
        cylinder(c, 0.022f, 1.1f, Float3(0f, 0f, 0f), Color(0xFF90A4AE), metallic = 0.6f)
        cylinder(c, 0.08f, 0.06f, Float3(0f, 1.1f, 0f), Color(0xFF263238))
        sphere(c, 0.075f, Float3(0f, 1.2f, 0f), Color(0xFFE53935), roughness = 0.2f)
        val glow = card(c, Sprites.glow(RED), 0.7f, Float3(0f, 1.2f, 0f), height = 0.7f)
        animations += { t -> glow.isVisible = (t % 0.8f) < 0.4f }
        // Sound waves: rings that grow out of the beacon and start again.
        for (i in 0 until 3) {
            val wave = card(c, Sprites.ring(RED, dashed = false), 1f, Float3(0f, 1.2f, 0f), height = 1f)
            animations += { t ->
                val age = ((t + i * 0.6f) % 1.8f) / 1.8f
                val k = 0.2f + 1.0f * age
                wave.scale = Float3(k, k, 1f)
                wave.isVisible = age < 0.92f
            }
        }
        card(c, Sprites.label("ALARM", WHITE, RED_BG, "अलार्म"), 0.34f, Float3(0f, 1.5f, 0f))
    }

    private fun gasLayers(c: Node, h: Node) {
        surveyStaff(c, Float3(0.35f, 0f, 0.1f))
        val methane = flat(h, Sprites.disc(0x99FFEB3B.toInt(), 0x00FFEB3B), 2.4f, 2.4f, Float3(0f, 1.6f, 0f))
        val methane2 = flat(h, Sprites.disc(0x66FFEB3B, 0x00FFEB3B), 2.0f, 2.0f, Float3(0.1f, 1.45f, 0.1f))
        val co2 = flat(h, Sprites.disc(0x994FC3F7.toInt(), 0x004FC3F7), 2.2f, 2.2f, Float3(0f, 0.05f, 0f))
        val co2b = flat(h, Sprites.disc(0x664FC3F7, 0x004FC3F7), 1.8f, 1.8f, Float3(-0.1f, 0.16f, 0f))
        listOf(methane to 1.6f, methane2 to 1.45f, co2 to 0.05f, co2b to 0.16f).forEachIndexed { i, (node, y) ->
            animations += { t -> node.position = Float3(node.position.x, y + 0.03f * sin(0.9f * t + i), node.position.z) }
        }
        card(h, Sprites.label("CH₄ 2.8 %", BLACK, 0xFFFFEB3B.toInt(), "rises / ऊपर जाती है"), 0.34f, Float3(-0.55f, 1.7f, 0.2f))
        card(h, Sprites.label("CO₂ 4.0 %", BLACK, 0xFF4FC3F7.toInt(), "sinks / नीचे बैठती है"), 0.34f, Float3(-0.55f, 0.28f, 0.2f))
    }

    private fun gasDetector(c: Node) {
        cube(c, Float3(0.16f, 0.28f, 0.06f), Float3(0f, 0f, 0f), Color(0xFF263238))
        cube(c, Float3(0.17f, 0.05f, 0.065f), Float3(0f, 0f, 0f), Color(0xFFFFB300))
        card(c, Sprites.detectorScreen("CH₄ 1.60 %", "LIMIT 1.25 %"), 0.13f, Float3(0f, 0.15f, 0.031f), billboard = false)
        val led = sphere(c, 0.014f, Float3(0f, 0.25f, 0.03f), Color(0xFFFF1744), roughness = 0.1f)
        animations += { t -> led.isVisible = (t % 0.6f) < 0.3f }
        val sign = card(c, Sprites.detectorScreen("CH₄ 1.60 %", "LIMIT 1.25 %"), 0.5f, Float3(0f, 0.45f, 0f))
        animations += { t ->
            val k = 1f + 0.04f * sin(8f * t)
            sign.scale = Float3(k, k, 1f)
        }
    }

    private fun ppeRack(c: Node) {
        cylinder(c, 0.015f, 0.2f, Float3(-0.5f, 0f, -0.04f), Color(0xFF546E7A))
        cylinder(c, 0.015f, 0.2f, Float3(0.5f, 0f, -0.04f), Color(0xFF546E7A))
        cube(c, Float3(1.12f, 0.62f, 0.04f), Float3(0f, 0.2f, -0.04f), Color(0xFFECEFF1), roughness = 0.8f)
        val icons = listOf(Ic.HealthAndSafety, Ic.Sensors, Ic.Link, Ic.FlashlightOn, Ic.Masks)
        icons.forEachIndexed { i, icon ->
            card(c, Sprites.iconSign(icon, BLACK, AMBER), 0.19f, Float3(-0.42f + i * 0.21f, 0.51f, -0.017f), billboard = false)
        }
    }

    private fun permitBoard(c: Node, h: Node, steps: List<String>, title: String) {
        cylinder(c, 0.015f, 1.0f, Float3(-0.2f, 0f, -0.02f), Color(0xFF546E7A))
        cylinder(c, 0.015f, 1.0f, Float3(0.2f, 0f, -0.02f), Color(0xFF546E7A))
        cube(c, Float3(0.48f, 0.62f, 0.02f), Float3(0f, 0.42f, 0f), Color(0xFFFAFAFA))
        // Two cards in the same place: the blank one always, the filled one only while teaching.
        card(c, Sprites.permit(title, emptyList()), 0.42f, Float3(0f, 0.73f, 0.012f), billboard = false, height = 0.53f)
        card(h, Sprites.permit(title, steps), 0.42f, Float3(0f, 0.73f, 0.014f), billboard = false, height = 0.53f)
    }

    private fun sumpRescue(c: Node, h: Node) {
        flat(c, Sprites.disc(0xFF050505.toInt(), 0x00050505), 1.1f, 1.1f, Float3(0f, 0.003f, 0f))
        flat(c, Sprites.ring(0x607D8B, dashed = false), 1.0f, 1.0f, Float3(0f, 0.005f, 0f))
        val marker = flat(c, Sprites.glow(RED), 0.5f, 0.5f, Float3(0f, 0.008f, 0f))
        animations += { t ->
            val k = 0.8f + 0.25f * sin(5f * t)
            marker.scale = Float3(k, 1f, k)
        }
        sphere(c, 0.07f, Float3(0.05f, 0.03f, 0.05f), Color(0xFFFBC02D), roughness = 0.4f)
        // A davit over the opening, with the lifeline going down into it.
        cylinder(c, 0.022f, 1.3f, Float3(0.62f, 0f, 0f), Color(0xFFFBC02D), metallic = 0.3f)
        val arm = cylinder(c, 0.02f, 0.62f, Float3(0.31f, 1.29f, 0f), Color(0xFFFBC02D), metallic = 0.3f, centred = true)
        arm.rotation = Float3(0f, 0f, 90f)
        cylinder(c, 0.007f, 1.3f, Float3(0f, 0f, 0f), Color(0xFFFFD54F))
        floorRing(h, Sprites.ring(RED, dashed = true), 1.9f, Float3(0f, 0.006f, 0f), pulse = true)
        card(h, Sprites.label("DO NOT ENTER", WHITE, RED_BG, "अंदर न जाएँ"), 0.42f, Float3(0f, 0.95f, 0.5f))
    }

    // ------------------------------------------------------------------ building blocks

    /** Flames, their white-hot cores, a glow on the floor and smoke, around [at]. [size] 1 is about 50 cm tall. */
    private fun fire(parent: Node, at: Float3, size: Float) {
        flat(parent, Sprites.glow(0xFF8F00), 0.9f * size, 0.9f * size, Float3(at.x, at.y + 0.004f, at.z))
        val offsets = listOf(-0.09f to 0.02f, 0.08f to -0.01f, 0f to -0.05f, -0.03f to 0.05f, 0.04f to 0.04f)
        offsets.forEachIndexed { i, (dx, dz) ->
            val w = (if (i == 2) 0.26f else 0.2f) * size
            val hgt = (if (i == 2) 0.52f else 0.4f) * size
            val flame = card(parent, Sprites.flame(core = false), w, Float3(at.x + dx * size, at.y, at.z + dz * size), height = hgt, anchoredAtBottom = true)
            val phase = i * 1.9f
            animations += { t ->
                val sx = 1f + 0.07f * sin(11f * t + phase)
                val sy = 1f + 0.16f * sin(7.3f * t + phase * 1.7f) + 0.05f * sin(23f * t + phase)
                flame.scale = Float3(sx, sy, 1f)
            }
        }
        val core = card(parent, Sprites.flame(core = true), 0.2f * size, Float3(at.x, at.y, at.z + 0.02f * size), height = 0.36f * size, anchoredAtBottom = true)
        animations += { t -> core.scale = Float3(1f, 1f + 0.12f * sin(17f * t), 1f) }
        for (i in 0 until 5) {
            val puff = card(parent, Sprites.smoke(dark = i % 2 == 1), 0.3f * size, Float3(at.x, at.y + 0.5f * size, at.z), height = 0.3f * size)
            val phase = i * 0.52f
            animations += { t ->
                val life = 2.6f
                val age = (t + phase) % life
                val k = age / life
                val grow = (0.5f + 1.4f * k) * (if (k > 0.75f) (1f - k) / 0.25f else 1f)
                puff.position = Float3(
                    at.x + 0.07f * size * sin(1.3f * age + phase * 3f),
                    at.y + (0.45f + 0.9f * k) * size,
                    at.z,
                )
                puff.scale = Float3(max(0.001f, grow), max(0.001f, grow), 1f)
            }
        }
    }

    private fun surveyStaff(parent: Node, at: Float3) {
        cylinder(parent, 0.016f, 1.8f, at, Color(0xFFF5F5F5))
        for (i in 0 until 4) {
            cylinder(parent, 0.018f, 0.12f, Float3(at.x, at.y + 0.2f + i * 0.4f, at.z), Color(0xFFD32F2F))
        }
    }

    private fun floorRing(parent: Node, bitmap: Bitmap, diameter: Float, at: Float3, pulse: Boolean) {
        val ring = flat(parent, bitmap, diameter, diameter, at)
        if (pulse) animations += { t ->
            val k = 1f + 0.03f * sin(4f * t)
            ring.scale = Float3(k, 1f, k)
        }
    }

    private fun color(c: Color, metallic: Float, roughness: Float): MaterialInstance {
        val key = c.value.toLong() xor (metallic * 1000).toLong().shl(1) xor (roughness * 1000).toLong().shl(12)
        return colors.getOrPut(key) { materials.createColorInstance(c, metallic, roughness, 0.4f) }
    }

    /** A box standing on its bottom face at [at]. */
    private fun cube(parent: Node, size: Float3, at: Float3, c: Color, metallic: Float = 0f, roughness: Float = 0.6f): CubeNode =
        CubeNode(engine, size = size, center = Float3(0f, size.y / 2f, 0f), materialInstance = color(c, metallic, roughness))
            .also { add(parent, it, at) }

    /**
     * An upright cylinder standing on its bottom at [at], or centred on [at]
     * when [centred] (for one that will be turned on its side).
     */
    private fun cylinder(
        parent: Node, radius: Float, height: Float, at: Float3, c: Color,
        metallic: Float = 0f, roughness: Float = 0.5f, centred: Boolean = false,
    ): CylinderNode = CylinderNode(
        engine, radius = radius, height = height,
        center = Float3(0f, if (centred) 0f else height / 2f, 0f),
        materialInstance = color(c, metallic, roughness),
    ).also { add(parent, it, at) }

    private fun sphere(parent: Node, radius: Float, at: Float3, c: Color, roughness: Float = 0.4f): SphereNode =
        SphereNode(engine, radius = radius, center = Float3(0f, 0f, 0f), materialInstance = color(c, 0f, roughness))
            .also { add(parent, it, at) }

    /**
     * An upright picture card [width] wide. With [anchoredAtBottom] the card
     * stands on [at]; otherwise it is centred on it. Billboards turn to the phone.
     */
    private fun card(
        parent: Node, bitmap: Bitmap, width: Float, at: Float3,
        height: Float = width * bitmap.height / bitmap.width,
        billboard: Boolean = true, anchoredAtBottom: Boolean = false,
    ): ImageNode {
        val node = ImageNode(
            materialLoader = materials,
            bitmap = bitmap,
            size = Float3(width, height, 0f),
            center = Float3(0f, if (anchoredAtBottom) height / 2f else 0f, 0f),
            normal = Float3(0f, 0f, 1f),
        )
        node.isShadowCaster = false
        node.isShadowReceiver = false
        add(parent, node, at)
        if (billboard) billboards += node
        return node
    }

    /** A picture lying flat on the surface, its top pointing away from the worker. */
    private fun flat(parent: Node, bitmap: Bitmap, width: Float, depth: Float, at: Float3): ImageNode {
        val node = ImageNode(
            materialLoader = materials,
            bitmap = bitmap,
            size = Float3(width, 0f, depth),
            center = Float3(0f, 0f, 0f),
            normal = Float3(0f, 1f, 0f),
        )
        node.isShadowCaster = false
        node.isShadowReceiver = false
        add(parent, node, at)
        return node
    }

    private fun add(parent: Node, node: Node, at: Float3) {
        node.quiet()
        node.position = at
        parent.addChildNode(node)
        created += node
    }

    private fun Node.quiet() {
        isHittable = false
        isTouchable = false
        isEditable = false
    }

    companion object {
        const val FLOOR_SCALE = 1f
        const val TABLE_SCALE = 0.45f

        /** Colours given without alpha: [Sprites] adds its own transparency. */
        private const val RED = 0xE53935
        private const val GREEN = 0x43A047
        private val RED_BG = 0xFFC62828.toInt()
        private val GREEN_BG = 0xFF2E7D32.toInt()
        private val WHITE = 0xFFFFFFFF.toInt()
        private val BLACK = 0xFF111111.toInt()
        private val AMBER = 0xFFFFB300.toInt()

        /** Degrees to turn about the vertical so that local +Z points from [from] towards [to]. */
        fun yawTowards(from: Float3, to: Float3): Float =
            (atan2((to.x - from.x).toDouble(), (to.z - from.z).toDouble()) * 180.0 / PI).toFloat()

        private fun easeOutBack(x: Float): Float {
            val c1 = 1.70158f
            val c3 = c1 + 1f
            val u = x - 1f
            return 1f + c3 * u * u * u + c1 * u * u
        }
    }
}
