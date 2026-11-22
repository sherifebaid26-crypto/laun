package com.aura.launcher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

/**
 * خلفية حيّة بترسم صورة الثيم الحالي. فيفو وأجهزة كتير بتحترم الخلفيات الحيّة على
 * شاشة القفل والرئيسية أكتر من الصورة العادية.
 */
class AuraWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = AuraEngine()

    private inner class AuraEngine : Engine() {
        private var bmp: Bitmap? = null
        private var loadedId: String? = null
        private var w = 0
        private var h = 0
        private var xOffset = 0.5f
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setOffsetNotificationsEnabled(true)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            w = width
            h = height
            loadedId = "\u0000" // نجبر إعادة التحميل بمقاس الشاشة
            draw()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) draw()
        }

        override fun onOffsetsChanged(
            xOffset: Float, yOffset: Float, xOffsetStep: Float, yOffsetStep: Float, xPixelOffset: Int, yPixelOffset: Int,
        ) {
            this.xOffset = xOffset
            draw()
        }

        private fun reloadIfNeeded() {
            val id = ThemeStore.liveTheme(applicationContext)
            if (id == loadedId && bmp != null) return
            loadedId = id
            bmp = runCatching {
                if (id == null || w <= 0) null else {
                    val f = ThemeStore.wallpaperFile(applicationContext, id)
                    if (!f.exists()) null else {
                        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(f.path, o)
                        var s = 1
                        while (o.outWidth / (s * 2) >= w && o.outHeight / (s * 2) >= h) s *= 2
                        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
                    }
                }
            }.getOrNull()
        }

        private fun draw() {
            val holder = surfaceHolder
            val canvas: Canvas = runCatching { holder.lockCanvas() }.getOrNull() ?: return
            try {
                reloadIfNeeded()
                canvas.drawColor(Color.BLACK)
                val b = bmp
                if (b != null && w > 0 && h > 0) {
                    // نملا الشاشة (center-crop) مع حركة خفيفة مع تغيير الصفحات
                    val scale = maxOf(w / b.width.toFloat(), h / b.height.toFloat()) * 1.06f
                    val extraX = b.width * scale - w
                    val dx = -extraX * xOffset
                    val dy = -(b.height * scale - h) / 2f
                    val m = Matrix()
                    m.postScale(scale, scale)
                    m.postTranslate(dx, dy)
                    canvas.drawBitmap(b, m, paint)
                }
            } finally {
                runCatching { holder.unlockCanvasAndPost(canvas) }
            }
        }
    }
}
