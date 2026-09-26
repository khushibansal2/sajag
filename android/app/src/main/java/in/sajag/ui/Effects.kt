package `in`.sajag.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.sajag.R
import `in`.sajag.ui.theme.Amber
import `in`.sajag.ui.theme.Bg
import `in`.sajag.ui.theme.CardAlt
import `in`.sajag.ui.theme.Danger
import `in`.sajag.ui.theme.Ink
import `in`.sajag.ui.theme.Muted
import `in`.sajag.ui.theme.Navy
import `in`.sajag.ui.theme.Safe
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun SplashScreen(onDone: () -> Unit) {
    val scale = remember { Animatable(0.7f) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 220f)) }
        fade.animateTo(1f, tween(450))
        delay(700)
        onDone()
    }
    Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value; alpha = fade.value },
        ) {
            Box(Modifier.size(132.dp).background(Navy, CircleShape), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(150.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text("SAJAG", fontSize = 40.sp, fontWeight = FontWeight.Black, color = Navy, letterSpacing = 8.sp)
            Text("सजग", fontSize = 24.sp, color = Ink)
            Spacer(Modifier.height(8.dp))
            Text("Train Safe · Certify Smart · Return Home", color = Muted, fontSize = 14.sp)
        }
        Text(
            "SIH26041 · Government of Jharkhand",
            color = Muted, fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 28.dp),
        )
    }
}

/** Score ring that fills up and counts from 0 — the result reveal. */
@Composable
fun ScoreRing(score: Int, passed: Boolean, diameter: Dp = 190.dp) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(score) { progress.animateTo(score / 100f, tween(1500, easing = FastOutSlowInEasing)) }
    val color = if (passed) Safe else Danger
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 18.dp.toPx()
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(CardAlt, -90f, 360f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(color, -90f, 360f * progress.value, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(progress.value * 100).roundToInt()}", fontSize = 56.sp, fontWeight = FontWeight.Black, color = Ink)
            Text("/ 100", color = Muted)
        }
    }
}

private data class ConfettiPiece(
    val x: Float, val delay: Float, val speed: Float, val color: Color, val spin: Float, val drift: Float,
)

@Composable
fun Confetti(modifier: Modifier = Modifier) {
    val palette = listOf(Amber, Safe, Color(0xFF29B6F6), Color(0xFFFF7043), Color(0xFFAB47BC), Navy)
    val pieces = remember {
        List(110) {
            ConfettiPiece(
                x = Random.nextFloat(), delay = Random.nextFloat() * 0.7f, speed = 0.7f + Random.nextFloat() * 0.8f,
                color = palette.random(), spin = Random.nextFloat() * 360f, drift = (Random.nextFloat() - 0.5f) * 0.25f,
            )
        }
    }
    val time = remember { Animatable(0f) }
    LaunchedEffect(Unit) { time.animateTo(1f, tween(3800, easing = LinearEasing)) }
    if (time.value >= 1f) return
    Canvas(modifier.fillMaxSize()) {
        val t = time.value
        pieces.forEach { p ->
            val y = -0.08f - p.delay + t * p.speed * 2f
            if (y < -0.05f || y > 1.05f) return@forEach
            val x = p.x + p.drift * t + sin(t * 12f + p.spin) * 0.02f
            val c = Offset(x * size.width, y * size.height)
            rotate(p.spin + t * 900f, pivot = c) {
                drawRect(p.color, topLeft = Offset(c.x - 7f, c.y - 12f), size = Size(14f, 24f))
            }
        }
    }
}

/** Siren and vibration for a fatal action, a short chime for a pass, a low buzz for a rejected certificate. */
object Alarm {
    fun fatal(context: Context) {
        tone(AudioManager.STREAM_ALARM, ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 1200)
        vibrate(context, longArrayOf(0, 350, 150, 350, 150, 600))
    }

    fun success(context: Context) {
        tone(AudioManager.STREAM_MUSIC, ToneGenerator.TONE_PROP_ACK, 400)
        vibrate(context, longArrayOf(0, 60, 80, 60))
    }

    fun reject(context: Context) {
        tone(AudioManager.STREAM_MUSIC, ToneGenerator.TONE_PROP_NACK, 500)
        vibrate(context, longArrayOf(0, 250, 120, 250))
    }

    private fun tone(stream: Int, tone: Int, ms: Int) {
        runCatching {
            val generator = ToneGenerator(stream, 90)
            generator.startTone(tone, ms)
            Handler(Looper.getMainLooper()).postDelayed({ generator.release() }, ms + 300L)
        }
    }

    private fun vibrate(context: Context, pattern: LongArray) {
        runCatching {
            context.getSystemService(Vibrator::class.java)
                ?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }
}
