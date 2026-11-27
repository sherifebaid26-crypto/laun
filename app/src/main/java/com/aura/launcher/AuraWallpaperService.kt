package com.aura.launcher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

/**
 * خلفية حيّة بترسم صورة الثيم الحالي. فيفو وأجهزة كتير بتحترم الخلفيات الحيّة على
 * شاشة القفل والرئيسية أكتر من الصورة العادية.
 */
class AuraWallpaperService : WallpaperService() {

    companion object {
        const val PREFS = "aura_wall"
        const val KEY_MOTION = "motion"
        fun motion(c: android.content.Context) = c.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_MOTION, false)
        fun setMotion(c: android.content.Context, on: Boolean) =
            c.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_MOTION, on).apply()
    }

    override fun onCreateEngine(): Engine = AuraEngine()

    private inner class AuraEngine : Engine() {
        private var bmp: Bitmap? = null
        private var loadedId: String? = null
        private var w = 0
        private var h = 0
        private var xOffset = 0.5f
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private val handler = Handler(Looper.getMainLooper())
        private var visible = false
        private var destroyed = false
        @Volatile private var loadingId: String? = null
        private val tick = object : Runnable {
            override fun run() {
                if (!visible) return
                draw()
                if (motion(applicationContext)) handler.postDelayed(this, 50L)
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setOffsetNotificationsEnabled(true)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            w = width
            h = height
            loadedId = "\u0000" // نجبر إعادة التحميل بمقاس الشاشة
            loadingId = null
            draw()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            handler.removeCallbacks(tick)
            if (visible) tick.run()
        }

        override fun onDestroy() {
            destroyed = true
            visible = false
            handler.removeCallbacks(tick)
            super.onDestroy()
        }

        override fun onOffsetsChanged(
            xOffset: Float, yOffset: Float, xOffsetStep: Float, yOffsetStep: Float, xPixelOffset: Int, yPixelOffset: Int,
        ) {
            this.xOffset = xOffset
            draw()
        }

        /** فك الصورة بيتم في Thread خلفي (مش على الـ main thread)، وبنفضل نرسم القديمة لحد ما الجديدة تجهز. */
        private fun reloadIfNeeded() {
            val id = ThemeStore.liveTheme(applicationContext)
            if (id == loadedId || id == loadingId) return
            if (id == null || w <= 0) { loadedId = id; bmp = null; return }
            loadingId = id
            val ww = w
            val hh = h
            val ctx = applicationContext
            Thread {
                val decoded = runCatching {
                    val f = ThemeStore.wallpaperFile(ctx, id)
                    if (!f.exists()) null else {
                        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(f.path, o)
                        var s = 1
                        while (o.outWidth / (s * 2) >= ww && o.outHeight / (s * 2) >= hh) s *= 2
                        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
                    }
                }.getOrNull()
                handler.post {
                    if (destroyed || loadingId != id) return@post
                    bmp = decoded
                    loadedId = id
                    loadingId = null
                    draw()
                }
            }.start()
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
                    val moving = motion(applicationContext)
                    val t = System.currentTimeMillis() / 1000.0
                    val breathe = if (moving) 1f + 0.04f * (0.5f + 0.5f * Math.sin(t / 6.0).toFloat()) else 1f
                    val scale = maxOf(w / b.width.toFloat(), h / b.height.toFloat()) * 1.1f * breathe
                    val extraX = b.width * scale - w
                    val sway = if (moving) 0.5f + 0.35f * Math.sin(t / 9.0).toFloat() else xOffset
                    val dx = -extraX * (if (moving) (xOffset * 0.4f + sway * 0.6f) else xOffset)
                    val dy = -(b.height * scale - h) / 2f + if (moving) (h * 0.012f * Math.cos(t / 7.0).toFloat()) else 0f
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
