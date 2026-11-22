package com.aura.launcher

import java.util.Locale
import java.util.UUID

/** لغة التطبيق: auto (حسب الجهاز) أو ar أو en. */
object Lang {
    @Volatile var code: String = "ar"
    val isAr: Boolean get() = (if (code == "auto") Locale.getDefault().language else code) == "ar"
}

/** نص حسب لغة التطبيق: عربي أو إنجليزي. */
fun tr(ar: String, en: String): String = if (Lang.isAr) ar else en

fun newId(): String = UUID.randomUUID().toString().take(8)

/** توحيد النص للبحث: يشيل التشكيل والتطويل ويوحّد الألف والتاء المربوطة والألف المقصورة. */
fun normalizeForSearch(input: String): String {
    val sb = StringBuilder(input.length)
    for (c in input.lowercase()) {
        when {
            c in 'ً'..'ٟ' || c == 'ـ' -> Unit
            c == 'أ' || c == 'إ' || c == 'آ' -> sb.append('ا')
            c == 'ة' -> sb.append('ه')
            c == 'ى' -> sb.append('ي')
            else -> sb.append(c)
        }
    }
    return sb.toString().trim()
}
