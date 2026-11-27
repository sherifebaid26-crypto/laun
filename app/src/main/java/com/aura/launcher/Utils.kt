package com.aura.launcher

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.util.Locale
import java.util.UUID

/** زر نصه رمز (⟳ ✕ ＋): اسم لقارئ الشاشة + دور زر + مساحة لمس ٤٨dp على الأقل. حطّه بعد clickable. */
fun Modifier.a11yButton(label: String): Modifier = this
    .semantics(mergeDescendants = true) {
        contentDescription = label
        role = Role.Button
    }
    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
    .wrapContentSize(Alignment.Center)

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
