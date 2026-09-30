package com.amosley.signal.art

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import androidx.core.content.res.ResourcesCompat
import com.amosley.signal.R
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

/**
 * Draws album covers and artist pictures from a name, in the app's visual language (gradients,
 * 2 px stripes, spaced Space Grotesk type). Deterministic per (name, style) so a style can be revisited.
 */
object ArtGenerator {
    const val STYLES = 6
    val styleNames = listOf("Horizon", "Panels", "Sun", "Stripes", "Glow", "Mono")

    private fun typeface(context: Context, weight: Int): Typeface {
        val base = runCatching { ResourcesCompat.getFont(context, R.font.space_grotesk) }.getOrNull() ?: Typeface.DEFAULT
        return if (Build.VERSION.SDK_INT >= 28) Typeface.create(base, weight, false) else Typeface.create(base, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun hsv(h: Float, s: Float, v: Float) = Color.HSVToColor(floatArrayOf(((h % 360) + 360) % 360, s, v))

    /** [artist] = picture for an artist (initials, portrait-like glow); otherwise an album cover with the title. */
    fun draw(context: Context, title: String, subtitle: String?, style: Int, size: Int = 1200, artist: Boolean = false): Bitmap {
        val seed = title.lowercase().hashCode().toLong() * 31 + style
        val rnd = Random(seed)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = size.toFloat()
        val hue = (abs(title.lowercase().hashCode() % 360) + (style / STYLES) * 67 % 360).toFloat()
        val hue2 = hue + 40 + rnd.nextInt(120)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val bold = typeface(context, 700)
        val medium = typeface(context, 500)

        when (style % STYLES) {
            0 -> { // Horizon: sky gradient, horizon line, spaced letters
                p.shader = LinearGradient(0f, 0f, 0f, s, intArrayOf(hsv(hue, 0.45f, 0.95f), hsv(hue2, 0.6f, 0.55f), hsv(hue2 + 30, 0.7f, 0.18f)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, s, s, p)
                p.shader = null
                p.color = Color.argb(60, 0, 0, 0)
                var x = 0f
                while (x < s) { val h = s * (0.08f + rnd.nextFloat() * 0.2f); c.drawRect(x, s - h, x + s * (0.03f + rnd.nextFloat() * 0.05f), s, p); x += s * 0.045f }
            }
            1 -> { // Panels: three vertical panels
                val w = s / 3
                for (i in 0..2) {
                    p.shader = LinearGradient(0f, 0f, 0f, s, hsv(hue + i * 50, 0.55f, 0.9f), hsv(hue + i * 50 + 30, 0.7f, 0.25f), Shader.TileMode.CLAMP)
                    c.drawRect(i * w, 0f, (i + 1) * w, s, p)
                }
                p.shader = null
                p.color = Color.WHITE
                c.drawRect(w - s * 0.006f, 0f, w + s * 0.006f, s, p)
                c.drawRect(2 * w - s * 0.006f, 0f, 2 * w + s * 0.006f, s, p)
            }
            2 -> { // Sun over a dusk gradient
                p.shader = LinearGradient(0f, 0f, 0f, s, hsv(hue2, 0.6f, 0.35f), hsv(hue, 0.8f, 0.12f), Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, s, s, p)
                p.shader = LinearGradient(0f, s * 0.25f, 0f, s * 0.75f, hsv(40f, 0.8f, 1f), hsv(hue + 330, 0.8f, 0.8f), Shader.TileMode.CLAMP)
                c.drawCircle(s / 2, s * 0.52f, s * 0.26f, p)
                p.shader = null
                p.color = hsv(hue, 0.8f, 0.12f)
                for (i in 0..5) c.drawRect(0f, s * (0.56f + i * 0.04f), s, s * (0.56f + i * 0.04f) + s * (0.004f + i * 0.004f), p)
            }
            3 -> { // Stripes: design placeholder look
                p.shader = LinearGradient(0f, 0f, s, s, hsv(hue, 0.6f, 0.62f), hsv(hue2, 0.55f, 0.28f), Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, s, s, p)
                p.shader = null
                p.color = Color.argb(18, 255, 255, 255)
                var x = 0f
                val step = s / 133f * 9f / 2f
                while (x < s) { c.drawRect(x, 0f, x + step * 0.45f, s, p); x += step }
            }
            4 -> { // Glow: soft colour blobs
                c.drawColor(hsv(hue2, 0.5f, 0.12f))
                repeat(4) {
                    val cx = s * (0.15f + rnd.nextFloat() * 0.7f)
                    val cy = s * (0.15f + rnd.nextFloat() * 0.7f)
                    val r = s * (0.35f + rnd.nextFloat() * 0.35f)
                    p.shader = RadialGradient(cx, cy, r, hsv(hue + it * 35, 0.7f, 0.95f), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                    c.drawCircle(cx, cy, r, p)
                }
                p.shader = null
            }
            else -> { // Mono: dark, amber bar, big type
                c.drawColor(Color.rgb(14, 14, 14))
                p.color = Color.rgb(242, 169, 59)
                c.drawRect(s * 0.08f, s * 0.08f, s * 0.2f, s * 0.1f, p)
                p.color = Color.argb(28, 255, 255, 255)
                for (i in 0 until 24) c.drawRect(s * 0.08f + i * s * 0.035f, s * 0.86f, s * 0.08f + i * s * 0.035f + s * 0.006f, s * (0.92f - rnd.nextFloat() * 0.06f), p)
            }
        }

        p.shader = null
        p.color = Color.WHITE
        if (artist) {
            // Initials, large and centred
            val initials = title.split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
            p.typeface = bold
            p.textAlign = Paint.Align.CENTER
            p.textSize = s * (if (initials.length > 1) 0.34f else 0.42f)
            p.setShadowLayer(s * 0.02f, 0f, s * 0.006f, Color.argb(90, 0, 0, 0))
            val y = s / 2 - (p.descent() + p.ascent()) / 2
            c.drawText(initials, s / 2, y, p)
            p.clearShadowLayer()
        } else {
            drawTitle(c, p, title.uppercase(), subtitle, s, bold, medium, spaced = style % STYLES in setOf(0, 1, 2))
        }
        return bmp
    }

    /** Title laid out in spaced rows (like "O V E R / T H E / HORIZON") or as a bold block. */
    private fun drawTitle(c: Canvas, p: Paint, title: String, subtitle: String?, s: Float, bold: Typeface, medium: Typeface, spaced: Boolean) {
        val words = title.split(Regex("\\s+")).filter { it.isNotBlank() }.ifEmpty { listOf("UNTITLED") }
        val rows = mutableListOf<String>()
        var cur = ""
        for (w in words) {
            val next = if (cur.isEmpty()) w else "$cur $w"
            if (next.length > 12 && cur.isNotEmpty()) { rows += cur; cur = w } else cur = next
        }
        if (cur.isNotEmpty()) rows += cur
        val lines = rows.take(4)
        p.typeface = if (spaced) medium else bold
        p.textAlign = Paint.Align.CENTER
        p.setShadowLayer(s * 0.012f, 0f, s * 0.004f, Color.argb(110, 0, 0, 0))
        val maxWidth = s * 0.8f
        val longest = lines.maxBy { it.length }
        var size = s * 0.16f
        p.textSize = size
        p.letterSpacing = if (spaced) 0.35f else -0.02f
        while (p.measureText(longest) > maxWidth && size > s * 0.04f) { size *= 0.92f; p.textSize = size }
        val lh = size * 1.15f
        val top = s / 2 - lh * lines.size / 2 - (p.ascent() + p.descent()) / 2 + lh / 2
        lines.forEachIndexed { i, line -> c.drawText(line, s / 2, top + i * lh, p) }
        if (!subtitle.isNullOrBlank()) {
            p.typeface = medium
            p.letterSpacing = 0.2f
            p.textSize = s * 0.035f
            p.color = Color.argb(220, 255, 255, 255)
            c.drawText(subtitle.uppercase(), s / 2, s * 0.9f, p)
        }
        p.letterSpacing = 0f
        p.clearShadowLayer()
    }

    fun toJpeg(bmp: Bitmap, quality: Int = 90): ByteArray = ByteArrayOutputStream().use { out ->
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
        out.toByteArray()
    }

    /** Loads a picked image, centre-crops it square and scales it to at most [size] px. */
    fun fromPicked(context: Context, uri: Uri, size: Int = 1200): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= size) sample *= 2
        val src = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val side = min(src.width, src.height)
        val out = Bitmap.createBitmap(min(side, size), min(side, size), Bitmap.Config.ARGB_8888)
        val srcRect = android.graphics.Rect((src.width - side) / 2, (src.height - side) / 2, (src.width - side) / 2 + side, (src.height - side) / 2 + side)
        Canvas(out).drawBitmap(src, srcRect, RectF(0f, 0f, out.width.toFloat(), out.height.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
        out
    }.getOrNull()
}
