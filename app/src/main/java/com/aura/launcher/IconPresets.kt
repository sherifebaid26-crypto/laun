package com.aura.launcher

/** أشكال أيقونات جاهزة بضغطة واحدة (بتتطبق على كل التطبيقات، شاملة الدرج). */
data class IconPreset(val ar: String, val en: String, val style: IconStyle)

val IconPresets = listOf(
    IconPreset("كلاسيك", "Classic", IconStyle()),
    IconPreset("دائري", "Circle", IconStyle(shape = IconShape.CIRCLE)),
    IconPreset("سكويركل", "Squircle", IconStyle(shape = IconShape.SQUIRCLE)),
    IconPreset("مربع ناعم", "Soft square", IconStyle(shape = IconShape.ROUNDED)),
    IconPreset("قطرة", "Drop", IconStyle(shape = IconShape.TEARDROP)),
    IconPreset("لوحة بيضاء", "White plate", IconStyle(shape = IconShape.SQUIRCLE, plate = 0xF2FFFFFF)),
    IconPreset("لوحة داكنة", "Dark plate", IconStyle(shape = IconShape.ROUNDED, plate = 0xE6181820)),
    IconPreset(
        "أحادي أبيض", "Mono white",
        IconStyle(shape = IconShape.CIRCLE, plate = 0x40FFFFFF, mono = true, tint = 0xFFFFFFFF),
    ),
    IconPreset(
        "أحادي ذهبي", "Mono gold",
        IconStyle(shape = IconShape.SQUIRCLE, plate = 0x66000000, mono = true, tint = 0xFFFFD54F),
    ),
)
