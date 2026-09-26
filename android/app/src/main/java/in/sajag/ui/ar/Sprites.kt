package `in`.sajag.ui.ar

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.graphics.PathParser
import `in`.sajag.ui.Ic

/**
 * The pictures painted onto flat cards in the AR scene: flames, smoke, glows,
 * floor rings, signs and labels. Drawn once with Android's 2D canvas, so the
 * app ships no image files for them, and cached for the life of the process.
 *
 * Every bitmap has soft, transparent edges; the AR material draws them unlit,
 * so a flame stays bright in a dark gallery.
 */
internal object Sprites {
    private val cache = HashMap<String, Bitmap>()

    private fun cached(key: String, make: () -> Bitmap): Bitmap = synchronized(cache) { cache.getOrPut(key, make) }

    private fun bitmap(w: Int, h: Int): Pair<Bitmap, Canvas> {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        return b to Canvas(b)
    }

    private fun paint() = Paint(Paint.ANTI_ALIAS_FLAG)

    /** A teardrop flame, tip at the top. [core] is the small white-hot centre. */
    fun flame(core: Boolean): Bitmap = cached("flame$core") {
        val w = 128
        val h = 256
        val (b, c) = bitmap(w, h)
        val cx = w / 2f
        val baseY = h * 0.72f
        val r = w * (if (core) 0.26f else 0.40f)
        val tipY = if (core) h * 0.30f else h * 0.04f
        val path = Path().apply {
            moveTo(cx, tipY)
            cubicTo(cx + r * 0.25f, tipY + (baseY - tipY) * 0.35f, cx + r * 1.05f, baseY - r * 0.9f, cx + r, baseY)
            arcTo(RectF(cx - r, baseY - r, cx + r, baseY + r), 0f, 180f, false)
            cubicTo(cx - r * 1.05f, baseY - r * 0.9f, cx - r * 0.25f, tipY + (baseY - tipY) * 0.35f, cx, tipY)
            close()
        }
        val colors = if (core) {
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFF9C4.toInt(), 0xCCFFEE58.toInt(), 0x00FFC107)
        } else {
            intArrayOf(0xFFFFF8E1.toInt(), 0xFFFFEB3B.toInt(), 0xFFFFA000.toInt(), 0xF0E65100.toInt(), 0x00BF360C)
        }
        val stops = if (core) floatArrayOf(0f, 0.3f, 0.65f, 1f) else floatArrayOf(0f, 0.16f, 0.4f, 0.72f, 1f)
        val p = paint().apply {
            shader = RadialGradient(cx, baseY + r * 0.2f, h * (if (core) 0.42f else 0.64f), colors, stops, Shader.TileMode.CLAMP)
            maskFilter = BlurMaskFilter(w * 0.05f, BlurMaskFilter.Blur.NORMAL)
        }
        c.drawPath(path, p)
        b
    }

    /** A soft round glow, for embers, lamps and the floor under a fire. */
    fun glow(color: Int): Bitmap = cached("glow$color") {
        val s = 128
        val (b, c) = bitmap(s, s)
        val p = paint().apply {
            shader = RadialGradient(
                s / 2f, s / 2f, s / 2f,
                intArrayOf(withAlpha(color, 230), withAlpha(color, 120), withAlpha(color, 0)),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
            )
        }
        c.drawCircle(s / 2f, s / 2f, s / 2f, p)
        b
    }

    /** A puff of smoke: a few overlapping soft blobs, so it does not look like a ball. */
    fun smoke(dark: Boolean): Bitmap = cached("smoke$dark") {
        val s = 160
        val (b, c) = bitmap(s, s)
        val base = if (dark) 0x303030 else 0x8A8A8A
        val blobs = listOf(
            floatArrayOf(0.50f, 0.52f, 0.34f), floatArrayOf(0.34f, 0.58f, 0.24f),
            floatArrayOf(0.66f, 0.56f, 0.25f), floatArrayOf(0.46f, 0.36f, 0.24f), floatArrayOf(0.60f, 0.40f, 0.20f),
        )
        for (blob in blobs) {
            val x = blob[0] * s
            val y = blob[1] * s
            val r = blob[2] * s
            val p = paint().apply {
                shader = RadialGradient(
                    x, y, r,
                    intArrayOf(withAlpha(base, if (dark) 150 else 120), withAlpha(base, if (dark) 70 else 50), withAlpha(base, 0)),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
                )
            }
            c.drawCircle(x, y, r, p)
        }
        b
    }

    /** A circle painted on the floor: a danger zone or a safe line. */
    fun ring(color: Int, dashed: Boolean): Bitmap = cached("ring$color$dashed") {
        val s = 256
        val (b, c) = bitmap(s, s)
        val p = paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = s * 0.055f
            this.color = withAlpha(color, 235)
            if (dashed) pathEffect = DashPathEffect(floatArrayOf(s * 0.11f, s * 0.07f), 0f)
            maskFilter = BlurMaskFilter(s * 0.012f, BlurMaskFilter.Blur.NORMAL)
        }
        c.drawCircle(s / 2f, s / 2f, s * 0.44f, p)
        val fill = paint().apply {
            shader = RadialGradient(
                s / 2f, s / 2f, s * 0.44f,
                intArrayOf(withAlpha(color, 0), withAlpha(color, 0), withAlpha(color, 70)),
                floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP,
            )
        }
        c.drawCircle(s / 2f, s / 2f, s * 0.44f, fill)
        b
    }

    /** A filled disc that fades from [center] to [edge]: a pit, a gas layer. */
    fun disc(center: Int, edge: Int): Bitmap = cached("disc$center$edge") {
        val s = 256
        val (b, c) = bitmap(s, s)
        val p = paint().apply {
            shader = RadialGradient(s / 2f, s / 2f, s / 2f, intArrayOf(center, center, edge), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        }
        c.drawCircle(s / 2f, s / 2f, s / 2f, p)
        b
    }

    /** A chevron pointing to the top of the image. Laid on the floor, the top is away from the worker. */
    fun arrow(color: Int): Bitmap = cached("arrow$color") {
        val s = 128
        val (b, c) = bitmap(s, s)
        val path = Path().apply {
            moveTo(s * 0.5f, s * 0.12f)
            lineTo(s * 0.9f, s * 0.55f)
            lineTo(s * 0.72f, s * 0.68f)
            lineTo(s * 0.5f, s * 0.44f)
            lineTo(s * 0.28f, s * 0.68f)
            lineTo(s * 0.1f, s * 0.55f)
            close()
        }
        c.drawPath(path, paint().apply { this.color = withAlpha(color, 240) })
        b
    }

    /** A rounded sign with one or two lines of text. */
    fun label(text: String, fg: Int, bg: Int, sub: String? = null): Bitmap = cached("label$text|$fg|$bg|$sub") {
        val main = paint().apply {
            typeface = Typeface.DEFAULT_BOLD
            textSize = 46f
            color = fg
        }
        val small = paint().apply {
            typeface = Typeface.DEFAULT_BOLD
            textSize = 28f
            color = fg
        }
        val pad = 22f
        val width = maxOf(main.measureText(text), sub?.let { small.measureText(it) } ?: 0f) + pad * 2
        val height = pad * 2 + 46f + (if (sub != null) 36f else 0f)
        val (b, c) = bitmap(width.toInt() + 1, height.toInt() + 1)
        c.drawRoundRect(RectF(0f, 0f, width, height), 20f, 20f, paint().apply { color = bg })
        c.drawText(text, pad, pad + 40f, main)
        if (sub != null) c.drawText(sub, pad, pad + 40f + 36f, small)
        b
    }

    /** A Material icon on a round coloured sign. */
    fun iconSign(icon: ImageVector, fg: Int, bg: Int): Bitmap = cached("icon${icon.name}|$fg|$bg") {
        val s = 160
        val (b, c) = bitmap(s, s)
        c.drawCircle(s / 2f, s / 2f, s * 0.48f, paint().apply { color = bg })
        c.drawCircle(s / 2f, s / 2f, s * 0.48f, paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = s * 0.035f
            color = withAlpha(0x000000, 90)
        })
        drawIcon(c, icon, s * 0.2f, s * 0.2f, s * 0.6f, fg)
        b
    }

    /** The yellow triangle with a lightning bolt, as on every electrical panel. */
    fun electricalWarning(): Bitmap = cached("electrical") {
        val w = 180
        val h = 160
        val (b, c) = bitmap(w, h)
        val tri = Path().apply {
            moveTo(w / 2f, 8f)
            lineTo(w - 8f, h - 8f)
            lineTo(8f, h - 8f)
            close()
        }
        c.drawPath(tri, paint().apply { color = 0xFFFFD600.toInt() })
        c.drawPath(tri, paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 10f
            strokeJoin = Paint.Join.ROUND
            color = 0xFF111111.toInt()
        })
        drawIcon(c, Ic.Bolt, w * 0.3f, h * 0.3f, w * 0.4f, 0xFF111111.toInt())
        b
    }

    /** The hand-held gas detector's screen: a reading over the limit, in alarm red. */
    fun detectorScreen(reading: String, limit: String): Bitmap = cached("detector$reading$limit") {
        val w = 300
        val h = 150
        val (b, c) = bitmap(w, h)
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), 18f, 18f, paint().apply { color = 0xFF101418.toInt() })
        c.drawRoundRect(RectF(8f, 8f, w - 8f, h - 8f), 12f, 12f, paint().apply { color = 0xFF1E2A22.toInt() })
        c.drawText(reading, 22f, 78f, paint().apply {
            typeface = Typeface.DEFAULT_BOLD
            textSize = 54f
            color = 0xFFFF5252.toInt()
        })
        c.drawText(limit, 22f, 124f, paint().apply {
            typeface = Typeface.DEFAULT_BOLD
            textSize = 30f
            color = 0xFFB0BEC5.toInt()
        })
        b
    }

    /**
     * The confined-space entry permit on its board. While the worker answers
     * ([steps] empty) the board shows blank lines, so it cannot give away the order.
     */
    fun permit(title: String, steps: List<String>): Bitmap = cached("permit$title${steps.joinToString("|")}") {
        val w = 300
        val h = 380
        val (b, c) = bitmap(w, h)
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), 14f, 14f, paint().apply { color = 0xFFFAFAFA.toInt() })
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), 70f), 14f, 14f, paint().apply { color = 0xFFFFB300.toInt() })
        c.drawText(title, 20f, 48f, paint().apply {
            typeface = Typeface.DEFAULT_BOLD
            textSize = 32f
            color = 0xFF111111.toInt()
        })
        val line = paint().apply { color = 0xFFB0BEC5.toInt() }
        val box = paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = 0xFF37474F.toInt()
        }
        val text = paint().apply {
            textSize = 22f
            color = 0xFF263238.toInt()
        }
        for (i in 0 until 5) {
            val y = 104f + i * 56f
            c.drawRect(20f, y, 48f, y + 28f, box)
            val step = steps.getOrNull(i)
            if (step != null) c.drawText("${i + 1}. $step", 62f, y + 22f, text)
            else c.drawRect(62f, y + 10f, w - 24f, y + 18f, line)
        }
        b
    }

    /** A vertical gradient strip, for the survey staff's height marks. */
    fun staff(): Bitmap = cached("staff") {
        val w = 48
        val h = 512
        val (b, c) = bitmap(w, h)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFFFFFFFF.toInt(), 0xFFECEFF1.toInt(), Shader.TileMode.CLAMP)
        })
        val red = paint().apply { color = 0xFFD32F2F.toInt() }
        for (i in 0 until 8) {
            if (i % 2 == 0) c.drawRect(0f, i * h / 8f, w.toFloat(), (i + 1) * h / 8f, red)
        }
        b
    }

    private fun drawIcon(c: Canvas, icon: ImageVector, left: Float, top: Float, size: Float, color: Int) {
        val source = Ic.source(icon) ?: return
        val matrix = Matrix().apply {
            setScale(size / 24f, size / 24f)
            postTranslate(left, top)
        }
        val p = paint().apply { this.color = color }
        for (d in source.paths) {
            val path = runCatching { PathParser.createPathFromPathData(d) }.getOrNull() ?: continue
            if (source.evenOdd) path.fillType = Path.FillType.EVEN_ODD
            path.transform(matrix)
            c.drawPath(path, p)
        }
    }

    private fun withAlpha(rgb: Int, alpha: Int): Int = (alpha shl 24) or (rgb and 0x00FFFFFF)
}
