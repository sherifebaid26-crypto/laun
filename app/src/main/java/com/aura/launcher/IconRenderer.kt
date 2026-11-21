package com.aura.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
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

/** يرسم الأيقونة بستايل الثيم (شكل + صحيفة + أيقونة أحادية على أندرويد 13+). */
object IconRenderer {

    fun render(drawable: Drawable, style: IconStyle, size: Int): Bitmap {
        if (style.shape == IconShape.ORIGINAL) return drawable.toBitmap(size, size)

        val s = size.toFloat()
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val hasPlate = (style.plate ushr 24) != 0L

        if (hasPlate) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = style.plate.toInt() }
            canvas.drawPath(shapePath(style.shape, s), p)
        }

        val mono: Drawable? =
            if (style.mono && Build.VERSION.SDK_INT >= 33 && drawable is AdaptiveIconDrawable) {
                drawable.monochrome
            } else null

        if (mono != null) {
            // الطبقة الأحادية مصبوغة فوق الصحيفة
            val m = mono.mutate()
            m.colorFilter = PorterDuffColorFilter(style.tint.toInt(), PorterDuff.Mode.SRC_IN)
            drawLayer(canvas, m, s, 1.15f)
        } else {
            // مع الصحيفة الأيقونة أصغر شوية علشان الصحيفة تبان كإطار
            val inner = if (hasPlate) 0.78f else 1f
            val t = (s * inner).toInt().coerceAtLeast(1)
            val content = maskedIcon(drawable, style.shape, t)
            val off = (s - t) / 2f
            canvas.drawBitmap(content, off, off, null)
        }
        return out
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
            IconShape.ORIGINAL -> addRect(r, Path.Direction.CW)
        }
    }
}
