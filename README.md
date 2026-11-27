# Aura Launcher

لانشر أندرويد (Kotlin + Jetpack Compose) — عربي/إنجليزي، Android 12+ (minSdk 31).

## الحالة: 1.4.1
- شاشة رئيسية بصفحات وودجتس (نظام + ودجتس Aura المدمجة) ودرج تطبيقات وبحث.
- متجر ثيمات (كتالوج على GitHub)، حزم أيقونات، شارات إشعارات، وضع التركيز، حدود استخدام.
- قفل تطبيقات/صفحات داخل Aura بـ PIN (+ بصمة) — **قفل داخل التطبيق فقط، مش بديل لقفل النظام.**
- نسخ احتياطي واستعادة (JSON)، وضع آمن عند تكرار الكراش.

## البناء
كل push على `main` يشغّل Actions ويطلع `AuraLauncher-debug.apk` و`AuraLauncher-release.apk` في Artifacts.
لنسخة في Releases: `git tag v1.4.1 && git push --tags` (بيتطلب مفتاح التوقيع — تحت).

## مفتاح التوقيع (مهم)
مفيش مفتاح ولا كلمة سر داخل الريبو. أنشئ مفتاحاً مرة واحدة واحفظه بعيداً عن الريبو:

```bash
keytool -genkeypair -v -keystore aura-release.jks -alias aura -keyalg RSA -keysize 4096 -validity 36500
base64 -w0 aura-release.jks   # الناتج يتحط في Secret
```

ثم أضف في GitHub → Settings → Secrets and variables → Actions:
`AURA_KEYSTORE_BASE64` و`AURA_KEYSTORE_PASSWORD` و`AURA_KEY_ALIAS` و`AURA_KEY_PASSWORD`.

للبناء المحلي: صدّر نفس المتغيرات + `AURA_KEYSTORE_FILE=/path/to/aura-release.jks`.
بدونها بيتوقّع الـ release بمفتاح debug (للتجربة فقط).

> المفتاح القديم (`aura1234`) كان في تاريخ الريبو — اعتبره مكشوفاً ولا تستخدمه. تغيير المفتاح معناه أن
> المستخدمين الحاليين لازم يفكّوا النسخة القديمة ويثبّتوا الجديدة (أندرويد مش بيقبل تحديث بتوقيع مختلف).

## التثبيت على الموبايل
نزّل الـ APK، ثبّته، وبعدين: الإعدادات ← التطبيقات ← التطبيقات الافتراضية ← تطبيق الشاشة الرئيسية ← Aura Launcher.
