package com.aura.launcher

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PIN محفوظ كـ PBKDF2 + salt. القفل ده داخل Aura بس (مش حماية على مستوى النظام). */
object Security {
    private const val ITERATIONS = 60_000

    fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun hash(pin: String, salt: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), Base64.decode(salt, Base64.NO_WRAP), ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun verify(state: AuraState, pin: String): Boolean {
        val h = state.pinHash
        val s = state.pinSalt
        if (h == null || s == null) return true
        return MessageDigest.isEqual(hash(pin, s).toByteArray(), h.toByteArray())
    }
}
