package com.aura.launcher

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PIN محفوظ كـ PBKDF2 + salt. القفل ده داخل Aura بس (مش حماية على مستوى النظام). */
object Security {
    private const val LEGACY_ITERATIONS = 60_000   // الـ hash القديم (من غير بادئة)
    private const val ITERATIONS = 150_000         // الـ hash الجديد ("v2:")
    private const val V2 = "v2:"

    fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun pbkdf2(pin: String, salt: String, iterations: Int): String {
        val spec = PBEKeySpec(pin.toCharArray(), Base64.decode(salt, Base64.NO_WRAP), iterations, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun hash(pin: String, salt: String): String = V2 + pbkdf2(pin, salt, ITERATIONS)

    /** ثقيلة (مئات الميلي ثانية): نادِها من خلفية مش من الـ UI thread. */
    fun verify(state: AuraState, pin: String): Boolean {
        val h = state.pinHash
        val s = state.pinSalt
        if (h == null || s == null) return true
        val expected = if (h.startsWith(V2)) V2 + pbkdf2(pin, s, ITERATIONS) else pbkdf2(pin, s, LEGACY_ITERATIONS)
        return MessageDigest.isEqual(expected.toByteArray(), h.toByteArray())
    }
}

/**
 * تقييد المحاولات: بعد ٣ أخطاء متتالية بنقفل ٣٠ ث، وبعدها بيتضاعف (٦٠، ١٢٠ … لحد ١٥ دقيقة).
 * العدّاد محفوظ في SharedPreferences علشان قفل التطبيق وفتحه ما يصفّرهوش.
 */
object PinLock {
    private const val FREE_TRIES = 3
    private const val MAX_LOCK_MS = 15 * 60_000L

    private fun p(ctx: Context) =
        ctx.applicationContext.getSharedPreferences("aura_pinlock", Context.MODE_PRIVATE)

    /** كام ميلي ثانية فاضلة على فك القفل (٠ = مفتوح). */
    fun remainingMs(ctx: Context, now: Long = System.currentTimeMillis()): Long =
        (p(ctx).getLong("until", 0L) - now).coerceIn(0L, MAX_LOCK_MS)

    /** يسجّل محاولة غلط ويرجّع مدة القفل الجديدة (٠ لو لسه في محاولات مجانية). */
    fun fail(ctx: Context): Long {
        val sp = p(ctx)
        val n = sp.getInt("fails", 0) + 1
        val lockMs = if (n < FREE_TRIES) 0L
        else minOf(30_000L * (1L shl minOf(n - FREE_TRIES, 5)), MAX_LOCK_MS)
        sp.edit().putInt("fails", n).putLong("until", if (lockMs > 0) System.currentTimeMillis() + lockMs else 0L).apply()
        return lockMs
    }

    fun success(ctx: Context) { p(ctx).edit().clear().apply() }
}
