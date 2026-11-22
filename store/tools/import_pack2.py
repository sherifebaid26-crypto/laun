#!/usr/bin/env python3
"""يستورد باك الـ100 ثيم (SVG) إلى المتجر: خلفية JPG + معاينة + icons.zip (12 أيقونة PNG) لكل ثيم.

التشغيل:  python3 store/tools/import_pack2.py /path/to/Aura_100_Theme_Pack
بيمسح أي ثيمات pack-* وt-* القديمة وبيضيف t-001..t-100 في store/store.json.
"""
import glob
import json
import os
import shutil
import subprocess
import sys
import zipfile

from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")

CAT_AR = {
    "Space": "فضاء", "Nature": "طبيعة", "Wildlife": "حياة برية", "Food": "طعام", "Music": "موسيقى",
    "Travel": "سفر", "Botanical": "نباتات", "Sports": "رياضة", "Ocean": "محيط", "Architecture": "عمارة",
    "Abstract": "تجريدي", "Luxury": "فخامة", "Japanese": "ياباني", "Cute": "لطيف", "Retro": "ريترو",
    "Cozy": "دافئ", "Minimal": "بسيط", "Futuristic": "مستقبلي", "Photography": "تصوير", "Anime": "أنمي",
    "Cars": "سيارات", "Floral": "زهور",
}
NAME_AR = {
    1: "أسد في الظلام", 2: "جبل وبحر", 3: "وردي كاواي", 4: "حلم أزرق", 5: "حديقة زن", 6: "ذهب ملكي",
    7: "مدينة سايبربنك", 8: "الفضاء العميق", 9: "هروب للمحيط", 10: "ليل الأنمي", 11: "كثبان الصحراء",
    12: "حقول اللافندر", 13: "مطر الساكورا", 14: "زجاج زمردي", 15: "ألعاب الأركيد", 16: "قيادة منتصف الليل",
    17: "الشفق القطبي", 18: "قهوة وكتب", 19: "قصر رخامي", 20: "دوائر نيون", 21: "همس الغابة",
    22: "نزهة الخريف", 23: "بحيرة متجمدة", 24: "جمر بركاني", 25: "ساكورا القمر", 26: "طائر الورق",
    27: "رمل بسيط", 28: "استوديو أحادي", 29: "باستيل بوب", 30: "موتشي النعناع", 31: "كوكب بكسل",
    32: "فيبرويف 88", 33: "الساعة الذهبية", 34: "نافذة ممطرة", 35: "مكتبة منتصف الليل", 36: "ياقوت الليل",
    37: "مخمل ياقوتي", 38: "خطوط نحاسية", 39: "توهج شمسي", 40: "زهرة المجرة",
}
SHAPE = {"circle": "CIRCLE", "squircle": "SQUIRCLE", "rounded_square": "ROUNDED"}
SLOTS = ["phone", "message", "browser", "camera", "gallery", "music", "clock", "calendar",
         "settings", "play", "weather", "folder"]


def lum(h):
    h = h.lstrip("#")
    r, g, b = (int(h[i:i + 2], 16) / 255 for i in (0, 2, 4))
    f = lambda c: c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
    return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b)


def contrast(a, b):
    la, lb = lum(a), lum(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


def svg_png(svg, out, w, h):
    subprocess.run(["rsvg-convert", "-w", str(w), "-h", str(h), svg, "-o", out], check=True)


def main(pack):
    store_path = os.path.join(OUT, "store.json")
    store = json.load(open(store_path, encoding="utf-8"))
    entries = [t for t in store["themes"] if not (t["id"].startswith("pack-") or t["id"].startswith("t-"))]
    for d in glob.glob(os.path.join(OUT, "themes", "pack-*")) + glob.glob(os.path.join(OUT, "themes", "t-*")):
        shutil.rmtree(d)

    tmp = "/tmp/aura_import"
    shutil.rmtree(tmp, ignore_errors=True)
    os.makedirs(tmp)

    dirs = sorted(glob.glob(os.path.join(pack, "themes", "*")))
    for i, d in enumerate(dirs, 1):
        t = json.load(open(os.path.join(d, "theme.json"), encoding="utf-8"))
        tid = f"t-{i:03d}"
        od = os.path.join(OUT, "themes", tid)
        os.makedirs(od, exist_ok=True)

        png = os.path.join(tmp, "w.png")
        svg_png(os.path.join(d, "wallpapers", "main.svg"), png, 720, 1620)
        im = Image.open(png).convert("RGB")
        im.save(os.path.join(od, "wallpaper.jpg"), "JPEG", quality=86, optimize=True, progressive=True)
        im.resize((540, 1215), Image.LANCZOS).save(os.path.join(od, "preview.jpg"), "JPEG", quality=80, optimize=True)

        with zipfile.ZipFile(os.path.join(od, "icons.zip"), "w", zipfile.ZIP_DEFLATED) as z:
            for s in SLOTS:
                p = os.path.join(tmp, s + ".png")
                svg_png(os.path.join(d, "icons", s + ".svg"), p, 144, 144)
                z.write(p, s + ".png")

        c = {k: v.strip() for k, v in t["colors"].items()}
        ls = t["launcher_settings"]
        bg = c["background"]
        dark = lum(bg) < 0.4
        text = c["on_background"]
        if contrast(text, bg) < 4.5:
            text = "#FFFFFF" if dark else "#101010"
        accent = c["primary"]
        cat = t["category"]
        cat_ar = CAT_AR.get(cat, cat)
        if i in NAME_AR:
            name_ar = NAME_AR[i]
        else:
            n = t["name"].rsplit(" ", 1)[-1]
            name_ar = f"{cat_ar} إصدار {n}"
        shape = SHAPE[ls["icon_shape"]]
        entries.append({
            "id": tid,
            "name": {"ar": name_ar, "en": t["name"]},
            "category": {"ar": cat_ar, "en": cat},
            "tags": {"ar": [cat_ar, "أيقونات خاصة"], "en": [cat, "Custom icons"]},
            "author": "Aura Labs",
            "sizeKb": (os.path.getsize(os.path.join(od, "wallpaper.jpg")) + os.path.getsize(os.path.join(od, "icons.zip"))) // 1024,
            "wallpaper": f"themes/{tid}/wallpaper.jpg",
            "preview": f"themes/{tid}/preview.jpg",
            "icons": f"themes/{tid}/icons.zip",
            "style": {
                "mode": "DARK" if dark else "LIGHT",
                "accent": accent,
                "homeText": text,
                "clock": "BIG" if t["widgets"]["clock_style"] == "digital" else "SMALL",
                "icon": {"shape": shape, "plate": "#E6" + bg.lstrip("#"), "mono": False, "tint": "#FFFFFFFF"},
            },
        })

    store["themes"] = entries
    json.dump(store, open(store_path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print(len(entries), "themes in store.json")


if __name__ == "__main__":
    main(sys.argv[1])
