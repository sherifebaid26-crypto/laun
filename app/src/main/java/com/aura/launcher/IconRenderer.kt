package com.aura.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.drawable.toBitmap

/**
 * يرسم الأيقونة بستايل الثيم: شكل + صحيفة، ولو mono يطلّعها أحادية اللون بنفس الشكل لكل التطبيقات:
 * 1) الطبقة الأحادية الرسمية (أندرويد 13+)  2) مجسّم المقدمة لو شكلها رمز  3) نسخة بدرجة لون واحدة من الأيقونة كلها.
 */
object IconRenderer {

    fun render(drawable: Drawable, style: IconStyle, size: Int): Bitmap {
        if (style.shape == IconShape.ORIGINAL) return drawable.toBitmap(size, size)

        val s = size.toFloat()
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val hasPlate = (style.plate ushr 24) != 0L
        val tint = style.tint.toInt()

        if (hasPlate) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = style.plate.toInt() }
            canvas.drawPath(shapePath(style.shape, s), p)
        }

        if (style.mono) {
            val native: Drawable? =
                if (Build.VERSION.SDK_INT >= 33 && drawable is AdaptiveIconDrawable) drawable.monochrome else null
            when {
                native != null -> {
                    val m = native.mutate()
                    m.colorFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN)
                    drawLayer(canvas, m, s, 1.15f)
                }
                drawable is AdaptiveIconDrawable && glyphLike(drawable) -> {
                    val fg = drawable.foreground.mutate()
                    fg.colorFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN)
                    drawLayer(canvas, fg, s, 1.15f)
                }
                else -> {
                    val inner = if (hasPlate) 0.78f else 1f
                    val t = (s * inner).toInt().coerceAtLeast(1)
                    val content = maskedIcon(drawable, style.shape, t)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = duotone(tint) }
                    val off = (s - t) / 2f
                    canvas.drawBitmap(content, off, off, paint)
                }
            }
            return out
        }

        // مع الصحيفة الأيقونة أصغر شوية علشان الصحيفة تبان كإطار
        val inner = if (hasPlate) 0.78f else 1f
        val t = (s * inner).toInt().coerceAtLeast(1)
        val content = maskedIcon(drawable, style.shape, t)
        val off = (s - t) / 2f
        canvas.drawBitmap(content, off, off, null)
        return out
    }

    /** مقدمة الأيقونة شكلها رمز (مساحة شفافة كبيرة) → نقدر نحوّلها لمجسّم بلون واحد. */
    private fun glyphLike(d: AdaptiveIconDrawable): Boolean {
        val fg = d.foreground ?: return false
        val n = 32
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        drawLayer(Canvas(bmp), fg, n.toFloat(), 1.5f)
        val px = IntArray(n * n)
        bmp.getPixels(px, 0, n, 0, 0, n, n)
        val covered = px.count { (it ushr 24) > 40 }
        return covered / (n * n).toFloat() < 0.6f
    }

    /** رمادي ثم مضروب في لون الثيم = درجات من لون واحد. */
    private fun duotone(tint: Int): ColorMatrixColorFilter {
        val cm = ColorMatrix().apply { setSaturation(0f) }
        val r = Color.red(tint) / 255f
        val g = Color.green(tint) / 255f
        val b = Color.blue(tint) / 255f
        cm.postConcat(
            ColorMatrix(
                floatArrayOf(
                    r, 0f, 0f, 0f, 0f,
                    0f, g, 0f, 0f, 0f,
                    0f, 0f, b, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
        )
        return ColorMatrixColorFilter(cm)
    }

    /** الأيقونة الكاملة مقصوصة على الشكل المطلوب. */
    private fun maskedIcon(drawable: Drawable, shape: IconShape, t: Int): Bitmap {
        val tf = t.toFloat()
        val bmp = Bitmap.createBitmap(t, t, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (drawable is AdaptiveIconDrawable) {
            // الطبقات 108 والجزء الظاهر 72 → نكبّرها 1.5 علشان الجزء الظاهر يملأ الشكل
            drawable.background?.let { drawLayer(c, it, tf, 1.5f) }
            drawable.foreground?.let { drawLayer(c, it, tf, 1.5f) }
        } else {
            drawable.setBounds(0, 0, t, t)
            drawable.draw(c)
        }
        val inverse = shapePath(shape, tf).apply { fillType = Path.FillType.INVERSE_WINDING }
        val eraser = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }
        c.drawPath(inverse, eraser)
        return bmp
    }

    private fun drawLayer(c: Canvas, d: Drawable, s: Float, factor: Float) {
        val l = (s * factor).toInt()
        val off = ((s - l) / 2f).toInt()
        d.setBounds(off, off, off + l, off + l)
        d.draw(c)
    }

    private fun shapePath(shape: IconShape, s: Float): Path = Path().apply {
        val r = RectF(0f, 0f, s, s)
        when (shape) {
            IconShape.CIRCLE -> addOval(r, Path.Direction.CW)
            IconShape.SQUIRCLE -> addRoundRect(r, s * 0.36f, s * 0.36f, Path.Direction.CW)
            IconShape.ROUNDED -> addRoundRect(r, s * 0.22f, s * 0.22f, Path.Direction.CW)
            IconShape.TEARDROP -> {
                val big = s * 0.5f
                val small = s * 0.1f
                // tl, tr, br, bl (x,y لكل ركن): الركن السفلي الأيسر حاد = قطرة
                addRoundRect(r, floatArrayOf(big, big, big, big, big, big, small, small), Path.Direction.CW)
            }
            IconShape.ORIGINAL -> addRect(r, Path.Direction.CW)
        }
    }
}
