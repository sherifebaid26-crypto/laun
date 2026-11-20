# Aura Launcher

لانشر أندرويد (Kotlin + Jetpack Compose) — عربي/إنجليزي، Android 12+.

## الحالة: 0.1.0
- هيكل المشروع + CI يبني APK على GitHub Actions
- يشتغل كلانشر (Home) ويعرض شبكة التطبيقات ويفتحها
- أيقونة adaptive + monochrome، RTL، نص عربي/إنجليزي

## البناء
كل push على `main` يشغّل Actions ويطلع `AuraLauncher-debug.apk` في Artifacts.
لنسخة في Releases: `git tag v0.1.0 && git push --tags`.

## التثبيت على الموبايل
نزّل الـ APK، ثبّته، وبعدين: الإعدادات ← التطبيقات ← التطبيقات الافتراضية ← تطبيق الشاشة الرئيسية ← Aura Launcher.
