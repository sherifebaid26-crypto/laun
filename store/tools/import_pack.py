#!/usr/bin/env python3
"""يستورد باك الثيمات (manifest.json + wallpapers + previews) إلى المتجر.

التشغيل:  python3 store/tools/import_pack.py /path/to/Aura_100_Themes
بيضيف الثيمات بـ id بالشكل pack-001 وبيحدّث store/store.json (وبيمسح أي pack-* قديم الأول).
"""
import json
import os
import sys

import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..")
rng = np.random.default_rng(11)

CAT_AR = {
    "Royal Gold": "ذهبي ملكي", "Black Diamond": "ألماس أسود", "Midnight Blue": "أزرق منتصف الليل",
    "Royal Purple": "بنفسجي ملكي", "Emerald Luxe": "زمردي فاخر", "Rose Gold": "ذهبي وردي",
    "Titanium": "تيتانيوم", "Desert Gold": "ذهب الصحراء", "Arctic Silver": "فضي قطبي",
    "Crimson Night": "ليل قرمزي", "Ocean Teal": "تركواز المحيط", "Sapphire": "ياقوتي",
    "Copper Noir": "نحاسي داكن", "Ivory Minimal": "عاجي بسيط", "Graphite": "جرافيت",
    "Neon Violet": "بنفسجي نيون", "Cyber Lime": "ليموني سايبر", "Sunset Amber": "كهرماني الغروب",
    "Frozen Blue": "أزرق متجمد", "Velvet Burgundy": "عنابي مخملي",
}
SUFFIX_AR = {"": "", "Edition": "إصدار", "Noir": "نوار", "Prestige": "برستيج", "Signature": "سيجنتشر"}
SHAPE = {
    "rounded_square": ("ROUNDED", "مربع مدوّر", "Rounded"),
    "circle": ("CIRCLE", "دائري", "Circle"),
    "squircle": ("SQUIRCLE", "سكويركل", "Squircle"),
    "teardrop": ("TEARDROP", "قطرة", "Teardrop"),
}
ORIGINAL_CATEGORIES = {
    "dunes": ("طبيعة", "Scenery"), "nile-night": ("طبيعة", "Scenery"),
    "mountain-lake": ("طبيعة", "Scenery"), "aurora": ("طبيعة", "Scenery"),
    "soft-rose": ("بسيط", "Minimal"), "carbon": ("داكن", "Dark"),
}


def luminance(hexs):
    h = hexs.lstrip("#")
    r, g, b = (int(h[i:i + 2], 16) / 255 for i in (0, 2, 4))
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def upscale(path, size, quality, grain=0.0):
    im = Image.open(path).convert("RGB").resize(size, Image.LANCZOS)
    if grain:
        a = np.asarray(im).astype(np.float32)
        a += rng.normal(0, grain, a.shape[:2])[..., None]
        im = Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))
    return im, quality


def main(pack):
    with open(os.path.join(pack, "manifest.json"), encoding="utf-8") as f:
        themes = json.load(f)["themes"]
    store_path = os.path.join(OUT, "store.json")
    with open(store_path, encoding="utf-8") as f:
        store = json.load(f)

    entries = [t for t in store["themes"] if not t["id"].startswith("pack-")]
    for t in entries:
        if "category" not in t and t["id"] in ORIGINAL_CATEGORIES:
            ar, en = ORIGINAL_CATEGORIES[t["id"]]
            t["category"] = {"ar": ar, "en": en}

    for i, t in enumerate(themes, 1):
        tid = f"pack-{i:03d}"
        d = os.path.join(OUT, "themes", tid)
        os.makedirs(d, exist_ok=True)
        wp_im, _ = upscale(os.path.join(pack, t["wallpaper"]), (720, 1600), 86, grain=1.0)
        wp_path = os.path.join(d, "wallpaper.jpg")
        wp_im.save(wp_path, "JPEG", quality=86, optimize=True, progressive=True)
        wp_im.resize((540, 1200), Image.LANCZOS).save(os.path.join(d, "preview.jpg"), "JPEG", quality=80, optimize=True)

        cat = t["category"]
        suffix = t["name"][len(cat):].strip() if t["name"].startswith(cat) else ""
        cat_ar = CAT_AR.get(cat, cat)
        name_ar = (cat_ar + " " + SUFFIX_AR.get(suffix, suffix)).strip()
        shape, shape_ar, shape_en = SHAPE[t["iconStyle"]["shape"]]
        mono = bool(t["iconStyle"]["monochrome"])
        c = t["colors"]
        bg = t["iconStyle"]["background"].lstrip("#")
        entries.append({
            "id": tid,
            "name": {"ar": name_ar, "en": t["name"]},
            "category": {"ar": cat_ar, "en": cat},
            "tags": {"ar": [cat_ar, shape_ar, "أحادي" if mono else "ملوّن"],
                     "en": [cat, shape_en, "Mono" if mono else "Colored"]},
            "author": "Aura Pack",
            "sizeKb": os.path.getsize(wp_path) // 1024,
            "wallpaper": f"themes/{tid}/wallpaper.jpg",
            "preview": f"themes/{tid}/preview.jpg",
            "style": {
                "mode": "LIGHT" if luminance(c["background"]) > 0.5 else "DARK",
                "accent": c["accent"],
                "homeText": c["onBackground"],
                "clock": "SMALL",
                "icon": {"shape": shape, "plate": "#E6" + bg, "mono": mono, "tint": t["iconStyle"]["tint"]},
            },
        })

    store["themes"] = entries
    with open(store_path, "w", encoding="utf-8") as f:
        json.dump(store, f, ensure_ascii=False, indent=1)
    print(len(entries), "themes in store.json")


if __name__ == "__main__":
    main(sys.argv[1])
