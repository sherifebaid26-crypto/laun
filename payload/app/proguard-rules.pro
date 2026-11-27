# Aura Launcher — قواعد ProGuard/R8

# أرقام الأسطر في سجل الكراش (Diagnostics) تفضل مقروءة
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# الـ enums بتتقرا بالاسم من JSON (valueOf) — لازم تفضل
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# خدمات النظام بتتنادى بالاسم من الـ manifest
-keep class com.aura.launcher.AuraNotificationService { *; }
-keep class com.aura.launcher.AuraAccessibilityService { *; }
-keep class com.aura.launcher.AuraWallpaperService { *; }
