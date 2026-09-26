package `in`.sajag.ui

import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import `in`.sajag.assess.Hazard

/**
 * Camera mode background: the live rear camera, with the hazard drawn over it.
 * Used when the phone cannot run world-anchored AR, or when AR fails.
 */
@Composable
fun CameraBackground(modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val preview = remember { Preview.Builder().build() }
    // The camera is held only while this is on screen. Released as soon as it
    // leaves, so the emergency torch and the next AR drill can have it back.
    DisposableEffect(lifecycleOwner) {
        var disposed = false
        var provider: ProcessCameraProvider? = null
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (disposed) return@addListener
            runCatching {
                val p = future.get()
                provider = p
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            runCatching { provider?.unbind(preview) }
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).also { view ->
                view.scaleType = PreviewView.ScaleType.FILL_CENTER
                // TextureView, not SurfaceView: a SurfaceView sits behind the
                // window, so the Compose backgrounds and overlays painted it black.
                view.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                preview.setSurfaceProvider(view.surfaceProvider)
            }
        },
    )
}

/** Guided mode background: a drawn underground gallery. No camera required. */
@Composable
fun GuidedBackground(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF2B2420), Color(0xFF15110E), Color(0xFF0B0908))))
        val vanish = Offset(size.width / 2, size.height * 0.36f)
        val rib = Color(0xFF4E4036)
        listOf(0f, size.width).forEach { x ->
            drawLine(rib, Offset(x, 0f), vanish, strokeWidth = 6f)
            drawLine(rib, Offset(x, size.height), vanish, strokeWidth = 6f)
        }
        for (i in 1..5) {
            val k = i / 6f
            val w = size.width * (1 - k)
            val h = size.height * (1 - k)
            drawRect(
                rib.copy(alpha = 0.9f - k * 0.6f),
                topLeft = Offset(vanish.x - w / 2, vanish.y - h * 0.36f),
                size = Size(w, h),
                style = Stroke(width = 5f * (1 - k) + 1f),
            )
        }
    }
}

/**
 * Hazard layer drawn over the camera or the gallery. Colour is the language:
 * red is danger, green is the safe path, amber marks a tracked anchor.
 *
 * [showHints] is true while the beat is being taught and false while the
 * worker answers. Anything that gives the answer away (the drawn gas layers
 * show where each gas collects) is only drawn while teaching; the scene
 * itself (fire, smoke, the alarm, a detector reading) stays in both.
 */
@Composable
fun HazardOverlay(hazard: Hazard, modifier: Modifier = Modifier, showHints: Boolean = true) {
    val transition = rememberInfiniteTransition(label = "hazard")
    val flicker by transition.animateFloat(
        0.88f, 1.12f, infiniteRepeatable(tween(170, easing = LinearEasing), RepeatMode.Reverse), label = "flicker",
    )
    val pulse by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse",
    )
    val drift by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "drift",
    )

    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val focus = Offset(size.width / 2, size.height * 0.27f)
            val r = size.minDimension * 0.14f
            drawAnchorCorners(focus, r * 2.3f)
            when (hazard) {
                Hazard.ELECTRICAL_FIRE -> {
                    drawPanel(focus.copy(y = focus.y - r * 0.2f), r)
                    drawFire(focus.copy(y = focus.y + r * 0.9f), r * 0.8f, flicker)
                    drawDangerZone(focus, r * 2.1f, pulse)
                }
                Hazard.EXTINGUISHER_RACK -> {
                    drawRack(focus, r)
                    drawFire(Offset(size.width * 0.86f, focus.y + r), r * 0.45f, flicker)
                }
                Hazard.FIRE_ATTACK -> {
                    drawFire(focus.copy(y = focus.y + r * 0.6f), r, flicker)
                    drawDangerZone(focus, r * 1.5f, pulse)
                    drawSafeLine(focus.copy(y = focus.y + r * 2.2f), r * 2.4f)
                }
                Hazard.SMOKE_EGRESS -> {
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color(0xF0181818), Color(0xB0282828), Color(0x20000000)),
                            endY = size.height * (0.5f + 0.08f * drift),
                        ),
                    )
                    drawExitArrows(size.height * 0.46f, drift)
                }
                Hazard.ALARM -> {
                    drawRect(Color(0xFFE53935).copy(alpha = 0.55f * pulse), style = Stroke(width = 30f))
                }
                Hazard.GAS_LAYERS -> if (showHints) {
                    drawGasBand(0f, size.height * 0.16f, Color(0xFFFFEB3B), drift)
                    drawGasBand(size.height * 0.40f, size.height * 0.52f, Color(0xFF4FC3F7), 1 - drift)
                } else Unit
                Hazard.GAS_DETECTOR -> {
                    drawGasBand(0f, size.height * 0.2f, Color(0xFFFFEB3B), drift)
                }
                Hazard.PPE_RACK -> Unit
                Hazard.PERMIT_BOARD -> Unit
                Hazard.SUMP_RESCUE -> {
                    drawOval(Color(0xFF050505), topLeft = Offset(focus.x - r * 1.6f, focus.y), size = Size(r * 3.2f, r * 1.3f))
                    drawDangerZone(focus.copy(y = focus.y + r * 0.65f), r * 2f, pulse)
                    drawLine(Color(0xFFFFC107), Offset(focus.x + r * 2.6f, focus.y - r * 1.6f), Offset(focus.x, focus.y + r * 0.6f), strokeWidth = 8f, cap = StrokeCap.Round)
                }
            }
        }

        when (hazard) {
            Hazard.GAS_LAYERS -> if (showHints) {
                GasLabel("CH₄ 2.8 %", Color(0xFFFFEB3B), Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 64.dp))
                GasLabel("CO₂ 4.0 %", Color(0xFF4FC3F7), Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = 300.dp))
            } else Unit
            Hazard.SMOKE_EGRESS -> Row(
                Modifier.align(Alignment.TopEnd).padding(end = 18.dp, top = 250.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("EXIT", color = Color(0xFF66BB6A), fontWeight = FontWeight.Black, fontSize = 22.sp)
                Icon(Ic.ArrowForward, contentDescription = null, tint = Color(0xFF66BB6A), modifier = Modifier.size(28.dp))
                Icon(Ic.ArrowForward, contentDescription = null, tint = Color(0xFF66BB6A), modifier = Modifier.size(28.dp))
            }
            Hazard.ALARM -> Icon(
                Ic.NotificationsActive,
                contentDescription = null,
                tint = Color(0xFFFF5252),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 90.dp).size(72.dp),
            )
            Hazard.PPE_RACK -> Row(
                Modifier.align(Alignment.TopCenter).padding(top = 110.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                listOf(Ic.HealthAndSafety, Ic.Masks, Ic.Sensors, Ic.Link, Ic.FlashlightOn).forEach { icon ->
                    Icon(icon, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(44.dp))
                }
            }
            Hazard.PERMIT_BOARD -> Row(
                Modifier.align(Alignment.TopCenter).padding(top = 120.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Ic.Assignment, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(10.dp))
                Text("PERMIT", fontSize = 34.sp, color = Color(0xFFFFC107), fontWeight = FontWeight.Black)
            }
            else -> Unit
        }
    }
}

/**
 * The gas detector's reading, over any background (AR, camera or drawn). It
 * is the scene itself, not a hint, so it stays on while the worker answers.
 */
@Composable
fun DetectorReading(modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "detector").animateFloat(
        0f, 1f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "blink",
    )
    Row(
        modifier.background(Color(0xE6101418), RoundedCornerShape(14.dp)).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val alarmRed = Color(0xFFFF5252).copy(alpha = 0.65f + 0.35f * pulse)
        Icon(Ic.Sensors, contentDescription = null, tint = alarmRed, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Text("CH₄ 1.60 %", color = alarmRed, fontWeight = FontWeight.Black, fontSize = 20.sp)
        Spacer(Modifier.width(12.dp))
        Text("LIMIT 1.25 %", color = Color(0xFFB0BEC5), fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun GasLabel(text: String, color: Color, modifier: Modifier) {
    Text(
        text, color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp,
        modifier = modifier.background(Color(0xB0000000), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

private fun DrawScope.drawAnchorCorners(c: Offset, half: Float) {
    val len = half * 0.25f
    val color = Color(0xCCFFC107)
    listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f).forEach { (sx, sy) ->
        val corner = Offset(c.x + sx * half, c.y + sy * half)
        drawLine(color, corner, corner.copy(x = corner.x - sx * len), strokeWidth = 6f, cap = StrokeCap.Round)
        drawLine(color, corner, corner.copy(y = corner.y - sy * len), strokeWidth = 6f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawFire(c: Offset, r: Float, k: Float) {
    drawCircle(
        Brush.radialGradient(listOf(Color(0xAAFF6D00), Color.Transparent), center = c, radius = r * 2.4f * k),
        radius = r * 2.4f * k, center = c,
    )
    for (i in 0 until 5) {
        val dx = (i - 2) * r * 0.3f
        val h = r * (1.3f + 0.35f * ((i * 37) % 5) / 5f) * (if (i % 2 == 0) k else 2f - k)
        drawOval(
            Brush.verticalGradient(
                listOf(Color(0xFFFFF176), Color(0xFFFF9800), Color(0xDDD50000)),
                startY = c.y - h, endY = c.y + r * 0.3f,
            ),
            topLeft = Offset(c.x + dx - r * 0.24f, c.y - h),
            size = Size(r * 0.48f, h + r * 0.3f),
        )
    }
}

private fun DrawScope.drawDangerZone(c: Offset, r: Float, p: Float) {
    drawCircle(Color(0x33E53935), radius = r, center = c)
    drawCircle(Color(0xFFE53935).copy(alpha = 0.35f + 0.5f * p), radius = r, center = c, style = Stroke(width = 9f))
}

private fun DrawScope.drawSafeLine(c: Offset, half: Float) {
    drawLine(Color(0xFF43A047), Offset(c.x - half, c.y), Offset(c.x + half, c.y), strokeWidth = 10f, cap = StrokeCap.Round)
}

private fun DrawScope.drawPanel(c: Offset, r: Float) {
    drawRoundRect(Color(0xFF546E7A), topLeft = Offset(c.x - r * 0.8f, c.y - r), size = Size(r * 1.6f, r * 1.9f), cornerRadius = CornerRadius(12f))
    val bolt = Path().apply {
        moveTo(c.x + r * 0.1f, c.y - r * 0.7f)
        lineTo(c.x - r * 0.3f, c.y + r * 0.05f)
        lineTo(c.x, c.y + r * 0.05f)
        lineTo(c.x - r * 0.15f, c.y + r * 0.7f)
        lineTo(c.x + r * 0.35f, c.y - r * 0.15f)
        lineTo(c.x + r * 0.05f, c.y - r * 0.15f)
        close()
    }
    drawPath(bolt, Color(0xFFFFEB3B))
}

private fun DrawScope.drawRack(c: Offset, r: Float) {
    val colors = listOf(Color(0xFF1E88E5), Color(0xFFBDBDBD), Color(0xFFD32F2F), Color(0xFF8D6E63))
    colors.forEachIndexed { i, color ->
        val x = c.x + (i - 1.5f) * r * 0.95f
        drawRoundRect(color, topLeft = Offset(x - r * 0.3f, c.y - r * 0.9f), size = Size(r * 0.6f, r * 2f), cornerRadius = CornerRadius(r * 0.3f))
        drawCircle(Color(0xFF212121), radius = r * 0.12f, center = Offset(x, c.y - r * 1.05f))
    }
    drawLine(Color(0xFF6D4C41), Offset(c.x - r * 2.2f, c.y + r * 1.15f), Offset(c.x + r * 2.2f, c.y + r * 1.15f), strokeWidth = 12f)
}

private fun DrawScope.drawExitArrows(y: Float, drift: Float) {
    val green = Color(0xFF43A047)
    drawLine(green.copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y), strokeWidth = 14f)
    val gap = size.width / 5
    for (i in -1..5) {
        val x = (i + drift) * gap
        val path = Path().apply {
            moveTo(x, y - 26f); lineTo(x + 30f, y); lineTo(x, y + 26f)
        }
        drawPath(path, green, style = Stroke(width = 10f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawGasBand(top: Float, bottom: Float, color: Color, drift: Float) {
    drawRect(
        Brush.verticalGradient(listOf(color.copy(alpha = 0.05f), color.copy(alpha = 0.38f), color.copy(alpha = 0.05f)), startY = top, endY = bottom),
        topLeft = Offset(0f, top), size = Size(size.width, bottom - top),
    )
    for (i in 0 until 6) {
        val cx = ((i / 6f + drift) % 1f) * size.width
        drawCircle(color.copy(alpha = 0.14f), radius = (bottom - top) * 0.45f, center = Offset(cx, (top + bottom) / 2))
    }
}
