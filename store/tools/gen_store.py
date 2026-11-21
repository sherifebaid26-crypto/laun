#!/usr/bin/env python3
"""يولّد خلفيات ثيمات Aura (إلصاقات أصلية مرسومة بالكود) + معاينات + store.json.

التشغيل:  python3 store/tools/gen_store.py   (من جذر الريبو)
"""
import json
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

W, H = 1080, 2400
OUT = os.path.join(os.path.dirname(__file__), "..")
rng = np.random.default_rng(7)


# ───────────── أدوات الرسم ─────────────
def hexc(s):
    s = s.lstrip("#")
    return np.array([int(s[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float32)


def gradient(stops, h=H, w=W):
    """تدرج رأسي. stops = [(pos 0..1, '#rrggbb'), ...]"""
    ys = np.linspace(0, 1, h)
    pos = [p for p, _ in stops]
    cols = np.stack([hexc(c) for _, c in stops])
    img = np.stack([np.interp(ys, pos, cols[:, i]) for i in range(3)], axis=1)
    return np.repeat(img[:, None, :], w, axis=1)


def to_img(arr):
    return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))


def glow(arr, cx, cy, radius, color, strength=1.0):
    yy, xx = np.mgrid[0:arr.shape[0], 0:arr.shape[1]]
    d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2) / radius
    a = np.clip(1 - d, 0, 1) ** 2 * strength
    return arr * (1 - a[..., None]) + hexc(color) * a[..., None]


def ridge(seed, base, amp, rough=5, w=W):
    """خط جبل: مجموع جيوب بترددات مختلفة."""
    r = np.random.default_rng(seed)
    x = np.linspace(0, 1, w)
    y = np.zeros(w)
    for k in range(1, rough + 1):
        y += (amp / k ** 1.15) * np.sin(2 * math.pi * (k * 0.9 + r.random()) * x + r.random() * 6.28)
    y += base
    return y


def fill_below(img, ys, color):
    d = ImageDraw.Draw(img)
    pts = [(x, float(ys[x])) for x in range(len(ys))] + [(len(ys), H), (0, H)]
    d.polygon(pts, fill=tuple(int(c) for c in color))


def stars(img, count, top, bottom, seed=3, maxr=2.2):
    r = np.random.default_rng(seed)
    d = ImageDraw.Draw(img)
    for _ in range(count):
        x, y = r.random() * W, top + r.random() * (bottom - top)
        b = int(120 + 135 * r.random() ** 2)
        rad = 0.6 + (maxr - 0.6) * r.random() ** 3
        d.ellipse([x - rad, y - rad, x + rad, y + rad], fill=(b, b, min(255, b + 15)))


def grain(img, sigma=1.6):
    a = np.asarray(img).astype(np.float32)
    a += rng.normal(0, sigma, a.shape[:2])[..., None]
    return to_img(a)


def vignette(arr, strength=0.35):
    yy, xx = np.mgrid[0:arr.shape[0], 0:arr.shape[1]]
    d = np.sqrt(((xx - W / 2) / (W / 2)) ** 2 + ((yy - H / 2) / (H / 2)) ** 2)
    return arr * (1 - strength * np.clip(d - 0.4, 0, 1)[..., None])


def mix(c1, c2, t):
    return hexc(c1) * (1 - t) + hexc(c2) * t


# ───────────── الخلفيات ─────────────
def dunes():
    arr = gradient([(0, "#2a1b3d"), (0.28, "#7a3b5e"), (0.5, "#e2775a"), (0.68, "#ffb86b")])
    arr = glow(arr, W * 0.62, H * 0.55, 520, "#fff1c9", 1.0)
    img = to_img(arr)
    layers = [(11, 0.60, 70, "#c9694f"), (12, 0.68, 85, "#a84e43"), (13, 0.77, 95, "#7d3a3a"),
              (14, 0.87, 80, "#4f2a33"), (15, 0.95, 60, "#2d1a25")]
    # قرص الشمس
    ImageDraw.Draw(img).ellipse([W * 0.62 - 95, H * 0.50 - 95, W * 0.62 + 95, H * 0.50 + 95], fill=(255, 238, 196))
    img = img.filter(ImageFilter.GaussianBlur(1.2))
    for seed, base, amp, col in layers:
        ys = ridge(seed, H * base, amp, rough=3)
        fill_below(img, ys, hexc(col))
    arr = vignette(np.asarray(img).astype(np.float32), 0.35)
    return grain(to_img(arr))


def nile_night():
    arr = gradient([(0, "#050b1f"), (0.35, "#0b1c3d"), (0.6, "#16345f"), (0.62, "#0b1a33"), (1, "#050a18")])
    arr = glow(arr, W * 0.74, H * 0.2, 300, "#3a5f9a", 0.55)
    img = to_img(arr)
    stars(img, 380, 0, int(H * 0.58))
    d = ImageDraw.Draw(img)
    mx, my = W * 0.74, H * 0.2
    d.ellipse([mx - 78, my - 78, mx + 78, my + 78], fill=(238, 242, 255))
    d.ellipse([mx - 50, my - 92, mx + 106, my + 64], fill=(11, 28, 61))  # هلال
    # ضفة بعيدة وشجر نخيل
    far = ridge(21, H * 0.60, 14, rough=4)
    fill_below(img, far, hexc("#08142a"))
    d = ImageDraw.Draw(img)
    for px, ph in [(120, 190), (210, 140), (930, 210), (1010, 150)]:
        base_y = float(far[min(px, W - 1)])
        d.line([(px, base_y), (px + 8, base_y - ph)], fill=(6, 12, 26), width=7)
        for ang in range(-70, 71, 28):
            a = math.radians(ang - 90)
            d.line([(px + 8, base_y - ph), (px + 8 + 90 * math.cos(a), base_y - ph + 70 * math.sin(a) + 40)],
                   fill=(6, 12, 26), width=5)
    # انعكاس القمر على النهر
    arr = np.asarray(img).astype(np.float32)
    for i in range(70):
        y = H * 0.62 + i * 16
        wdt = 140 * (1 - i / 90) * (0.5 + 0.5 * math.sin(i * 1.7))
        x0 = int(mx - wdt / 2 + 12 * math.sin(i * 0.8))
        yy = int(y)
        if yy < H - 4:
            arr[yy:yy + 4, max(0, x0):max(0, x0) + int(max(wdt, 4))] += np.array([70, 90, 120])
    # مركب شراعي
    img = to_img(arr)
    d = ImageDraw.Draw(img)
    bx, by = 330, H * 0.675
    d.polygon([(bx, by), (bx + 220, by), (bx + 180, by + 34), (bx + 40, by + 34)], fill=(7, 12, 22))
    d.polygon([(bx + 100, by - 6), (bx + 100, by - 250), (bx + 205, by - 20)], fill=(15, 26, 48))
    d.line([(bx + 100, by), (bx + 100, by - 260)], fill=(7, 12, 22), width=5)
    return grain(to_img(vignette(np.asarray(img).astype(np.float32), 0.4)))


def mountain_lake():
    arr = gradient([(0, "#243746"), (0.22, "#4e7480"), (0.42, "#9bb9b0"), (0.5, "#cfdcd0")])
    arr = glow(arr, W * 0.3, H * 0.34, 480, "#f3ecd2", 0.8)
    horizon = int(H * 0.56)
    img = to_img(arr)
    fog = hexc("#d3ded3")
    for i, (seed, base, amp, col) in enumerate([
        (31, 0.40, 130, "#8fb3a8"), (32, 0.45, 120, "#6c9788"),
        (33, 0.50, 105, "#4a7a68"), (34, 0.54, 70, "#2f5a4a")]):
        ys = ridge(seed, H * base, amp, rough=5)
        color = mix("#d3ded3", col, 0.35 + 0.2 * i) if i < 2 else hexc(col)
        fill_below(img, ys, color)
    # بحيرة: ننسخ النصف العلوي معكوساً مع تغبيش
    top = img.crop((0, 0, W, horizon))
    refl = top.transpose(Image.FLIP_TOP_BOTTOM).resize((W, H - horizon))
    refl = refl.filter(ImageFilter.GaussianBlur(7))
    ra = np.asarray(refl).astype(np.float32) * 0.78 + hexc("#16312c") * 0.22
    for y in range(0, H - horizon, 9):  # تموجات
        ra[y:y + 2] += 14 + 8 * math.sin(y / 17)
    img.paste(to_img(ra), (0, horizon))
    # ضباب عند خط الأفق
    arr = np.asarray(img).astype(np.float32)
    yy = np.arange(H)[:, None, None]
    band = np.exp(-((yy - horizon) / 70.0) ** 2)
    arr = arr * (1 - 0.35 * band) + fog * 0.35 * band
    # شجر صنوبر على الحافة اليمنى
    img = to_img(arr)
    d = ImageDraw.Draw(img)
    for tx, ty, th in [(930, horizon + 40, 360), (1010, horizon + 60, 300), (860, horizon + 70, 240)]:
        for k in range(7):
            yk = ty - th + k * th / 7
            half = 26 + k * 17
            d.polygon([(tx, yk - 34), (tx - half, yk + th / 7 + 10), (tx + half, yk + th / 7 + 10)],
                      fill=(14, 38, 31))
        d.rectangle([tx - 7, ty - 8, tx + 7, ty + 40], fill=(10, 24, 20))
    return grain(to_img(vignette(np.asarray(img).astype(np.float32), 0.3)))


def aurora():
    arr = gradient([(0, "#04060f"), (0.5, "#08122a"), (1, "#0a0f1c")])
    img = to_img(arr)
    stars(img, 320, 0, int(H * 0.7), seed=9)
    arr = np.asarray(img).astype(np.float32)
    x = np.linspace(0, 1, W)
    yy = np.arange(H)[:, None].astype(np.float32)
    for k, (col, ybase, amp, ph) in enumerate([("#2bff9b", 0.34, 190, 0.0), ("#7c4dff", 0.28, 160, 1.7),
                                               ("#26c6da", 0.40, 140, 3.1)]):
        curve = H * ybase + amp * np.sin(2 * math.pi * (1.1 + 0.3 * k) * x + ph) + 70 * np.sin(2 * math.pi * 3.3 * x + ph)
        dist = (yy - curve[None, :]) / (170 + 40 * k)
        band = np.exp(-np.where(dist < 0, dist * dist * 1.6, dist * dist * 0.35))
        # خيوط رأسية تدّي إحساس الستارة
        streak = 0.75 + 0.25 * np.sin(x * 2 * math.pi * 38 + k)[None, :]
        arr += band[..., None] * streak[..., None] * hexc(col) * 0.95
    img = to_img(arr).filter(ImageFilter.GaussianBlur(3))
    far = ridge(41, H * 0.86, 40, rough=5)
    fill_below(img, far, hexc("#04070d"))
    near = ridge(42, H * 0.93, 30, rough=4)
    fill_below(img, near, hexc("#020408"))
    return grain(to_img(vignette(np.asarray(img).astype(np.float32), 0.3)))


def soft_rose():
    arr = gradient([(0, "#ffe3ec"), (0.5, "#ffc9d9"), (1, "#f7a8c4")])
    for cx, cy, r, c, s in [(200, 500, 520, "#fff4f8", 0.9), (900, 900, 480, "#ffd0e0", 0.8),
                            (300, 1700, 600, "#ffeaf2", 0.7), (860, 2100, 520, "#f48fb1", 0.6),
                            (540, 1300, 380, "#fff0f5", 0.5)]:
        arr = glow(arr, cx, cy, r, c, s)
    return grain(to_img(arr), 1.2)


def carbon():
    base = np.zeros((H, W, 3), dtype=np.float32) + hexc("#0b0b0e")
    yy, xx = np.mgrid[0:H, 0:W]
    weave = ((xx // 6 + yy // 6) % 2) * 5.0 + (np.sin((xx + yy) / 9.0) * 3)
    base += weave[..., None]
    base = glow(base, W * 0.2, H * 0.12, 700, "#2a2a33", 0.7)
    base = glow(base, W * 0.85, H * 0.95, 650, "#4a1316", 0.9)
    img = to_img(base)
    d = ImageDraw.Draw(img)
    d.line([(0, H * 0.72), (W, H * 0.64)], fill=(255, 82, 82), width=3)
    d.line([(0, H * 0.725), (W, H * 0.645)], fill=(120, 30, 34), width=10)
    img = img.filter(ImageFilter.GaussianBlur(0.8))
    return grain(to_img(vignette(np.asarray(img).astype(np.float32), 0.45)), 1.0)


# ───────────── تعريف الثيمات ─────────────
THEMES = [
    dict(id="dunes", fn=dunes, ar="كثبان الغروب", en="Sunset Dunes",
         tags_ar=["برتقالي", "صحراء", "واقعي"], tags_en=["Orange", "Desert", "Realistic"],
         mode="GLASS", accent="#FFB74D", homeText="#FFFFFF", clock="BIG",
         icon=dict(shape="SQUIRCLE", plate="#59000000", mono=False, tint="#FFFFFF")),
    dict(id="nile-night", fn=nile_night, ar="ليل النيل", en="Nile Night",
         tags_ar=["أزرق", "ليل", "نيل"], tags_en=["Blue", "Night", "Nile"],
         mode="DARK", accent="#4FC3F7", homeText="#FFFFFF", clock="BIG",
         icon=dict(shape="CIRCLE", plate="#40FFFFFF", mono=False, tint="#FFFFFF")),
    dict(id="mountain-lake", fn=mountain_lake, ar="جبال وبحيرة", en="Misty Lake",
         tags_ar=["أخضر", "طبيعة", "واقعي"], tags_en=["Green", "Scenery", "Realistic"],
         mode="COLOR", accent="#6FBF9A", homeText="#FFFFFF", clock="BIG_SERIF",
         icon=dict(shape="ROUNDED", plate="#66264A3E", mono=False, tint="#FFFFFF")),
    dict(id="aurora", fn=aurora, ar="الشفق القطبي", en="Aurora",
         tags_ar=["أخضر", "فضاء", "فن"], tags_en=["Green", "Space", "Art"],
         mode="DARK", accent="#69F0AE", homeText="#FFFFFF", clock="SMALL",
         icon=dict(shape="CIRCLE", plate="#33FFFFFF", mono=False, tint="#FFFFFF")),
    dict(id="soft-rose", fn=soft_rose, ar="وردي هادئ", en="Soft Rose",
         tags_ar=["وردي", "بسيط", "فاتح"], tags_en=["Pink", "Minimal", "Light"],
         mode="LIGHT", accent="#EC407A", homeText="#4A2B3A", clock="BIG_SERIF",
         icon=dict(shape="CIRCLE", plate="#D9FFFFFF", mono=True, tint="#B03A62")),
    dict(id="carbon", fn=carbon, ar="كربون أسود", en="Carbon Black",
         tags_ar=["أسود", "أحمر", "عصري"], tags_en=["Black", "Red", "Modern"],
         mode="DARK", accent="#FF5252", homeText="#FFFFFF", clock="SMALL",
         icon=dict(shape="ROUNDED", plate="#26FFFFFF", mono=False, tint="#FFFFFF")),
]


def main():
    catalog = []
    for t in THEMES:
        d = os.path.join(OUT, "themes", t["id"])
        os.makedirs(d, exist_ok=True)
        img = t["fn"]().convert("RGB")
        assert img.size == (W, H)
        wp = os.path.join(d, "wallpaper.jpg")
        img.save(wp, "JPEG", quality=86, optimize=True, progressive=True)
        img.resize((540, 1200), Image.LANCZOS).save(os.path.join(d, "preview.jpg"), "JPEG", quality=80, optimize=True)
        size_kb = os.path.getsize(wp) // 1024
        catalog.append({
            "id": t["id"],
            "name": {"ar": t["ar"], "en": t["en"]},
            "tags": {"ar": t["tags_ar"], "en": t["tags_en"]},
            "author": "Aura",
            "sizeKb": size_kb,
            "wallpaper": f"themes/{t['id']}/wallpaper.jpg",
            "preview": f"themes/{t['id']}/preview.jpg",
            "style": {"mode": t["mode"], "accent": t["accent"], "homeText": t["homeText"],
                      "clock": t["clock"], "icon": t["icon"]},
        })
        print(t["id"], size_kb, "KB")
    with open(os.path.join(OUT, "store.json"), "w", encoding="utf-8") as f:
        json.dump({"version": 1, "themes": catalog}, f, ensure_ascii=False, indent=2)


if __name__ == "__main__":
    main()
