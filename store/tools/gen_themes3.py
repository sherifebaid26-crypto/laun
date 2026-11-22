#!/usr/bin/env python3
"""يولّد ثيمات Aura الأصلية (كل ثيم بمشهد وأسلوب أيقونات مختلف، مش نفس القالب بألوان تانية).

التشغيل: python3 store/tools/gen_themes3.py   (بيكتب في store/themes/ وبيضيف الثيمات في store/store.json)
"""
import json, math, os, random, zipfile
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
W, H, SS = 720, 1620, 2
SLOTS = ["phone", "message", "browser", "camera", "gallery", "music", "clock", "calendar",
         "settings", "play", "weather", "folder"]


def hexc(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(c1, c2, t):
    return tuple(int(c1[i] * (1 - t) + c2[i] * t) for i in range(3)) + (255,)


# ───────────── أدوات الرسم ─────────────
def vgrad(stops, w=W * SS, h=H * SS):
    """تدرج رأسي: stops = [(pos 0..1, '#hex'), ...]"""
    ys = np.linspace(0, 1, h)
    out = np.zeros((h, w, 3), dtype=np.float32)
    pos = [s[0] for s in stops]
    cols = np.array([hexc(s[1])[:3] for s in stops], dtype=np.float32)
    for ch in range(3):
        out[:, :, ch] = np.interp(ys, pos, cols[:, ch])[:, None]
    return Image.fromarray(out.astype(np.uint8), "RGB").convert("RGBA")


def glow(img, cx, cy, r, color, strength=1.0):
    w, h = img.size
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2) / r
    a = np.clip(1 - d, 0, 1) ** 2 * strength
    layer = np.zeros((h, w, 4), dtype=np.uint8)
    layer[:, :, :3] = color[:3]
    layer[:, :, 3] = (a * 255).astype(np.uint8)
    return Image.alpha_composite(img, Image.fromarray(layer, "RGBA"))


def ridge(seed, w, base, amp, rough=3):
    rnd = random.Random(seed)
    ph = [rnd.random() * 6.28 for _ in range(6)]
    xs = np.linspace(0, 1, w)
    y = np.zeros(w)
    for k in range(1, 6):
        y += np.sin(xs * (k * rough) * 3.14 + ph[k]) / (k ** 1.1)
    return base - (y / 2.2) * amp


def mountains(img, base, amp, color, seed, rough=3, fade_to=None):
    d = ImageDraw.Draw(img)
    w, h = img.size
    ys = ridge(seed, w, base, amp, rough)
    pts = [(x, ys[x]) for x in range(0, w, 3)] + [(w, h), (0, h)]
    d.polygon(pts, fill=color)


def cloud(d, cx, cy, s, color):
    for dx, dy, r in [(-1.0, 0.2, 0.55), (-0.45, -0.1, 0.75), (0.25, -0.2, 0.85), (0.95, 0.15, 0.6), (0, 0.35, 0.8)]:
        d.ellipse([cx + dx * s - r * s, cy + dy * s - r * s, cx + dx * s + r * s, cy + dy * s + r * s], fill=color)


def stars(img, n, seed, minr=1, maxr=3, alpha=220, ymax=0.7):
    d = ImageDraw.Draw(img)
    rnd = random.Random(seed)
    for _ in range(n):
        x, y = rnd.random() * img.size[0], rnd.random() * img.size[1] * ymax
        r = rnd.uniform(minr, maxr) * SS
        a = int(rnd.uniform(0.4, 1) * alpha)
        d.ellipse([x - r, y - r, x + r, y + r], fill=(255, 255, 255, a))


def face(d, cx, cy, s, ink=(70, 50, 60, 255), blush=(255, 150, 170, 200)):
    for sx in (-1, 1):
        d.ellipse([cx + sx * 0.38 * s - 0.09 * s, cy - 0.08 * s, cx + sx * 0.38 * s + 0.09 * s, cy + 0.1 * s], fill=ink)
        d.ellipse([cx + sx * 0.62 * s - 0.14 * s, cy + 0.12 * s, cx + sx * 0.62 * s + 0.14 * s, cy + 0.26 * s], fill=blush)
    d.arc([cx - 0.12 * s, cy + 0.02 * s, cx + 0.12 * s, cy + 0.22 * s], 20, 160, fill=ink, width=max(2, int(0.03 * s)))


def heart(d, cx, cy, s, fill, outline=None, width=0):
    pts = []
    for i in range(0, 360, 4):
        t = math.radians(i)
        x = 16 * math.sin(t) ** 3
        y = 13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t)
        pts.append((cx + x * s / 16, cy - y * s / 16))
    if outline:
        d.line(pts + [pts[0]], fill=outline, width=width, joint="curve")
    else:
        d.polygon(pts, fill=fill)


def finish(img):
    return img.resize((W, H), Image.LANCZOS).convert("RGB")


# ───────────── المشاهد (كل واحد تركيب مختلف) ─────────────
def scene_midnight_peak():
    img = vgrad([(0, "#05070f"), (0.55, "#0c1426"), (1, "#141c30")])
    stars(img, 260, 1)
    img = glow(img, W * SS * 0.72, H * SS * 0.2, 520 * SS, (150, 180, 255), 0.45)
    d = ImageDraw.Draw(img)
    cx, cy, r = W * SS * 0.72, H * SS * 0.2, 90 * SS
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(235, 240, 255, 255))
    d.ellipse([cx - r + 30 * SS, cy - r + 10 * SS, cx + r + 30 * SS, cy + r + 10 * SS], fill=(12, 20, 38, 255))
    for i, (b, a, col) in enumerate([(0.62, 260, "#101a30"), (0.72, 220, "#0b1224"), (0.83, 180, "#070b18"), (0.93, 120, "#04060d")]):
        mountains(img, H * SS * b, a * SS, hexc(col), 10 + i, 2 + i)
    return finish(img)


def scene_alpine_mist():
    img = vgrad([(0, "#e8efe9"), (0.45, "#d3e0d6"), (1, "#bcd0c1")])
    img = glow(img, W * SS * 0.3, H * SS * 0.3, 700 * SS, (255, 255, 255), 0.8)
    for i, (b, a, col) in enumerate([(0.52, 300, "#9db5a6"), (0.62, 260, "#7f9a8a"), (0.72, 200, "#5d7c6a")]):
        mountains(img, H * SS * b, a * SS, hexc(col, 255), 30 + i, 2 + i)
    img = glow(img, W * SS * 0.5, H * SS * 0.66, 520 * SS, (255, 255, 255), 0.55)
    d = ImageDraw.Draw(img)
    lake_y = H * SS * 0.78
    d.rectangle([0, lake_y, W * SS, H * SS], fill=hexc("#58786a"))
    ys = ridge(31, W * SS, H * SS * 0.62, 260 * SS, 3)
    refl = [(x, lake_y + (lake_y - ys[x]) * 0.35) for x in range(0, W * SS, 4)] + [(W * SS, lake_y), (0, lake_y)]
    d.polygon(refl, fill=hexc("#6a8c7b"))
    img = img.filter(ImageFilter.GaussianBlur(1.2 * SS))
    return finish(img)


def scene_pastel_puffs():
    img = vgrad([(0, "#ffd6e7"), (0.5, "#ffe9d9"), (1, "#cfe4ff")])
    d = ImageDraw.Draw(img)
    rnd = random.Random(5)
    for _ in range(18):
        heart(d, rnd.random() * W * SS, rnd.random() * H * SS, rnd.uniform(24, 60) * SS, (255, 255, 255, 120))
    for cx, cy, s, col in [(0.3, 0.62, 150, "#ffffff"), (0.74, 0.76, 190, "#ffeef5"), (0.2, 0.88, 160, "#eaf3ff"), (0.78, 0.5, 100, "#fff6e0")]:
        cloud(d, cx * W * SS, cy * H * SS, s * SS, hexc(col))
        face(d, cx * W * SS, (cy + 0.012) * H * SS, s * 0.9 * SS)
    return finish(img)


def scene_galactic():
    img = vgrad([(0, "#02030a"), (0.6, "#0a0820"), (1, "#14102e")])
    img = glow(img, W * SS * 0.7, H * SS * 0.78, 700 * SS, (120, 70, 200), 0.5)
    stars(img, 320, 7, ymax=1.0)
    d = ImageDraw.Draw(img)
    cx, cy, r = W * SS * 0.68, H * SS * 0.72, 190 * SS
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=hexc("#352a6b"))
    d.ellipse([cx - r * 0.9, cy - r * 0.95, cx + r * 0.4, cy + r * 0.2], fill=hexc("#4c3b97"))
    for k, a in [(1.9, 90), (2.3, 60)]:
        rr = r * k
        d.ellipse([cx - rr, cy - rr * 0.28, cx + rr, cy + rr * 0.28], outline=(180, 160, 255, a), width=3 * SS)
    mx, my, mr = W * SS * 0.22, H * SS * 0.2, 54 * SS
    d.ellipse([mx - mr, my - mr, mx + mr, my + mr], fill=hexc("#d9d7ee"))
    d.ellipse([mx - 20 * SS, my - 16 * SS, mx - 2 * SS, my + 2 * SS], fill=hexc("#b9b6d8"))
    for rr in (150, 230, 310):
        d.ellipse([mx - rr * SS, my - rr * SS, mx + rr * SS, my + rr * SS], outline=(255, 255, 255, 28), width=2 * SS)
    return finish(img)


def scene_golden_hour():
    img = vgrad([(0, "#2b1b4a"), (0.35, "#c2476b"), (0.6, "#ff9a5a"), (1, "#ffd08a")])
    img = glow(img, W * SS * 0.5, H * SS * 0.56, 640 * SS, (255, 230, 170), 0.9)
    d = ImageDraw.Draw(img)
    cx, cy, r = W * SS * 0.5, H * SS * 0.56, 120 * SS
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=hexc("#fff1c9"))
    for i, (b, a, col) in enumerate([(0.68, 90, "#a9455a"), (0.76, 120, "#6e2c4f"), (0.86, 100, "#3d1a3a"), (0.95, 80, "#1f0e24")]):
        mountains(img, H * SS * b, a * SS, hexc(col), 50 + i, 1 + i)
    rnd = random.Random(3)
    for _ in range(7):
        x, y = rnd.uniform(0.15, 0.85) * W * SS, rnd.uniform(0.38, 0.5) * H * SS
        s = rnd.uniform(10, 22) * SS
        d.arc([x - s, y - s * 0.5, x, y + s * 0.5], 200, 340, fill=(40, 20, 40, 255), width=3 * SS)
        d.arc([x, y - s * 0.5, x + s, y + s * 0.5], 200, 340, fill=(40, 20, 40, 255), width=3 * SS)
    return finish(img)


def scene_blue_sky():
    img = vgrad([(0, "#1f6fe0"), (0.55, "#59a8f5"), (1, "#bfe3ff")])
    d = ImageDraw.Draw(img)
    for cx, cy, s in [(0.25, 0.3, 150), (0.8, 0.42, 120), (0.45, 0.58, 190), (0.9, 0.72, 140), (0.15, 0.84, 170)]:
        cloud(d, cx * W * SS, cy * H * SS, s * SS, (255, 255, 255, 235))
    rnd = random.Random(9)
    for _ in range(70):
        x, y = rnd.random() * W * SS, rnd.random() * H * SS
        r = rnd.uniform(2, 6) * SS
        d.ellipse([x - r, y - r, x + r, y + r], fill=(255, 255, 255, rnd.randint(110, 230)))
        for k in range(8):
            t = math.radians(k * 45)
            d.line([x, y, x + math.cos(t) * r * 4, y + math.sin(t) * r * 4], fill=(255, 255, 255, 90), width=SS)
    return finish(img.filter(ImageFilter.GaussianBlur(0.8 * SS)))


def scene_neon_grid():
    img = vgrad([(0, "#12052b"), (0.5, "#3a0b5e"), (0.62, "#ff3d8b"), (0.63, "#12052b"), (1, "#0a0220")])
    d = ImageDraw.Draw(img)
    cx, cy, r = W * SS * 0.5, H * SS * 0.5, 230 * SS
    for i in range(14):
        y0 = cy - r + i * (2 * r / 14)
        d.rectangle([cx - r, y0, cx + r, y0 + (i * 0.7 + 1) * SS], fill=(18, 5, 43, 255))
    img = glow(img, cx, cy, 520 * SS, (255, 60, 150), 0.5)
    d = ImageDraw.Draw(img)
    hy = H * SS * 0.63
    for i in range(-14, 15):
        d.line([W * SS / 2 + i * 12 * SS, hy, W * SS / 2 + i * 150 * SS, H * SS], fill=(255, 70, 190, 150), width=2 * SS)
    y, step = hy, 10 * SS
    while y < H * SS:
        d.line([0, y, W * SS, y], fill=(80, 220, 255, 140), width=2 * SS)
        step *= 1.32
        y += step
    return finish(img)


def scene_soft_check():
    img = Image.new("RGBA", (W * SS, H * SS), hexc("#f6f0ff"))
    d = ImageDraw.Draw(img)
    s = 90 * SS
    for yy in range(0, H * SS, s):
        for xx in range(0, W * SS, s):
            if ((xx // s) + (yy // s)) % 2 == 0:
                d.rectangle([xx, yy, xx + s, yy + s], fill=hexc("#e4dcf7"))
    img = glow(img, W * SS * 0.5, H * SS * 0.3, 700 * SS, (255, 255, 255), 0.7)
    d = ImageDraw.Draw(img)
    heart(d, W * SS * 0.5, H * SS * 0.5, 300 * SS, hexc("#ffb8d2"))
    heart(d, W * SS * 0.5, H * SS * 0.5, 210 * SS, hexc("#ffd6e6"))
    face(d, W * SS * 0.5, H * SS * 0.5, 130 * SS)
    for i in range(24):
        a = math.radians(i * 15)
        px, py = W * SS * 0.5 + math.cos(a) * 380 * SS, H * SS * 0.5 + math.sin(a) * 400 * SS
        d.ellipse([px - 7 * SS, py - 7 * SS, px + 7 * SS, py + 7 * SS], fill=hexc("#ffffff"))
    return finish(img)


def scene_hearts():
    img = vgrad([(0, "#ffb3c9"), (1, "#ff7aa6")])
    d = ImageDraw.Draw(img)
    rnd = random.Random(12)
    for _ in range(46):
        c = rnd.choice([(255, 255, 255, 120), (255, 220, 232, 150), (200, 60, 110, 90), (255, 190, 210, 140)])
        heart(d, rnd.random() * W * SS, rnd.random() * H * SS, rnd.uniform(60, 190) * SS, c)
    img = glow(img, W * SS * 0.5, H * SS * 0.5, 500 * SS, (255, 255, 255), 0.5)
    d = ImageDraw.Draw(img)
    heart(d, W * SS * 0.5, H * SS * 0.5, 260 * SS, (255, 255, 255, 235))
    heart(d, W * SS * 0.5, H * SS * 0.5, 190 * SS, hexc("#ff5d8f"))
    return finish(img)


def scene_fresh_green():
    img = vgrad([(0, "#9ad9a8"), (1, "#5fb97a")])
    yy, xx = np.mgrid[0:H * SS, 0:W * SS].astype(np.float32)
    d_ = np.sqrt((xx - W * SS / 2) ** 2 + (yy - H * SS * 0.5) ** 2) / (W * SS * 0.7)
    arr = np.array(img).astype(np.float32)
    k = np.clip(1 - d_, 0, 1)[:, :, None]
    arr[:, :, :3] = arr[:, :, :3] * (1 - k * 0.45) + np.array([240, 255, 240]) * (k * 0.45)
    img = Image.fromarray(arr.astype(np.uint8), "RGBA")
    d = ImageDraw.Draw(img)
    heart(d, W * SS * 0.5, H * SS * 0.5, 300 * SS, None, outline=(255, 255, 255, 255), width=30 * SS)
    return finish(img)


def scene_gold_flow():
    img = vgrad([(0, "#08080a"), (1, "#16130f")])
    img = glow(img, W * SS * 0.55, H * SS * 0.62, 650 * SS, (200, 150, 70), 0.35)
    d = ImageDraw.Draw(img)
    n = 9
    for i in range(n):
        t = i / (n - 1)
        pts = []
        for s in np.linspace(0, 1, 90):
            x = W * SS * (0.15 + 0.75 * s) + math.sin(s * 3 + t * 2) * 60 * SS
            y = H * SS * (0.85 - 0.5 * math.sin(s * 3.14) * (0.45 + 0.55 * t)) - t * 260 * SS * s
            pts.append((x, y))
        top = [(x, y - (40 + 80 * t) * SS * math.sin(k / 89 * 3.14)) for k, (x, y) in enumerate(pts)]
        poly = pts + top[::-1]
        col = mix((40, 40, 44), (222, 170, 90), t) if i % 2 == 0 else mix((70, 70, 78), (150, 150, 160), t)
        d.polygon(poly, fill=(col[0], col[1], col[2], 235))
    return finish(img.filter(ImageFilter.GaussianBlur(0.6 * SS)))


def scene_lighthouse():
    img = vgrad([(0, "#bfe8ff"), (0.5, "#e8f6ff"), (0.62, "#6cc3e8"), (1, "#2f8fc2")])
    d = ImageDraw.Draw(img)
    for cx, cy, s in [(0.2, 0.2, 90), (0.75, 0.3, 120), (0.5, 0.45, 70)]:
        cloud(d, cx * W * SS, cy * H * SS, s * SS, (255, 255, 255, 230))
    ix, iy = W * SS * 0.5, H * SS * 0.64
    d.ellipse([ix - 300 * SS, iy - 20 * SS, ix + 300 * SS, iy + 150 * SS], fill=hexc("#7bcf6a"))
    d.ellipse([ix - 300 * SS, iy + 40 * SS, ix + 300 * SS, iy + 170 * SS], fill=hexc("#e8c98a"))
    tw, th = 56 * SS, 280 * SS
    d.polygon([(ix - tw, iy), (ix + tw, iy), (ix + tw * 0.6, iy - th), (ix - tw * 0.6, iy - th)], fill=hexc("#ffffff"))
    for k in range(3):
        y0 = iy - th + (k * 2 + 1) * th / 7
        d.polygon([(ix - tw * (0.6 + 0.4 * (y0 - (iy - th)) / th), y0), (ix + tw * (0.6 + 0.4 * (y0 - (iy - th)) / th), y0),
                   (ix + tw * (0.6 + 0.4 * (y0 + th / 7 - (iy - th)) / th), y0 + th / 7),
                   (ix - tw * (0.6 + 0.4 * (y0 + th / 7 - (iy - th)) / th), y0 + th / 7)], fill=hexc("#e8554e"))
    d.rectangle([ix - 44 * SS, iy - th - 44 * SS, ix + 44 * SS, iy - th], fill=hexc("#3a4a5a"))
    d.polygon([(ix - 58 * SS, iy - th - 44 * SS), (ix + 58 * SS, iy - th - 44 * SS), (ix, iy - th - 96 * SS)], fill=hexc("#e8554e"))
    d.ellipse([ix - 18 * SS, iy - th - 36 * SS, ix + 18 * SS, iy - th - 4 * SS], fill=hexc("#ffe27a"))
    for hx, col in [(-210, "#f2a65a"), (-120, "#ef7f8a"), (150, "#6bb3f2"), (230, "#f2c94c")]:
        d.rectangle([ix + hx * SS - 34 * SS, iy + 20 * SS, ix + hx * SS + 34 * SS, iy + 70 * SS], fill=hexc(col))
        d.polygon([(ix + hx * SS - 42 * SS, iy + 20 * SS), (ix + hx * SS + 42 * SS, iy + 20 * SS), (ix + hx * SS, iy - 22 * SS)], fill=hexc("#8a4f3a"))
    for i in range(9):
        y = H * SS * (0.72 + i * 0.03)
        d.arc([W * SS * (0.05 + (i % 3) * 0.3), y, W * SS * (0.3 + (i % 3) * 0.3), y + 30 * SS], 200, 340, fill=(255, 255, 255, 150), width=3 * SS)
    return finish(img)


def scene_bamboo():
    img = vgrad([(0, "#eef6df"), (1, "#c9dfae")])
    img = glow(img, W * SS * 0.7, H * SS * 0.25, 600 * SS, (255, 255, 235), 0.9)
    d = ImageDraw.Draw(img)
    rnd = random.Random(21)
    for layer in range(3):
        for _ in range(5 + layer * 2):
            x = rnd.random() * W * SS
            wd = (22 - layer * 6) * SS
            col = [(110, 150, 80, 255), (86, 128, 62, 255), (58, 98, 44, 255)][layer]
            d.rectangle([x, 0, x + wd, H * SS], fill=(col[0], col[1], col[2], 190 + layer * 20))
            for k in range(1, 12):
                y = k * H * SS / 12 + rnd.uniform(-20, 20) * SS
                d.rectangle([x - 2 * SS, y, x + wd + 2 * SS, y + 6 * SS], fill=(40, 70, 30, 200))
            for _ in range(5):
                y = rnd.random() * H * SS
                dirx = rnd.choice([-1, 1])
                d.polygon([(x + wd / 2, y), (x + wd / 2 + dirx * 130 * SS, y - 24 * SS), (x + wd / 2 + dirx * 70 * SS, y + 14 * SS)], fill=(col[0], col[1] + 20, col[2], 200))
    img = glow(img, W * SS * 0.5, H * SS * 0.92, 800 * SS, (255, 255, 255), 0.4)
    return finish(img)


# ───────────── الأيقونات ─────────────
IS = 576  # دقة الرسم (تنزل لـ 144)


def shape_mask(shape, size=IS):
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    if shape == "CIRCLE":
        d.ellipse([0, 0, size - 1, size - 1], fill=255)
    elif shape == "SQUIRCLE":
        pts = []
        for i in range(0, 360, 2):
            t = math.radians(i)
            c, s = math.cos(t), math.sin(t)
            x = math.copysign(abs(c) ** 0.5, c)
            y = math.copysign(abs(s) ** 0.5, s)
            pts.append((size / 2 + x * size / 2, size / 2 + y * size / 2))
        d.polygon(pts, fill=255)
    elif shape == "ROUNDED":
        d.rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * 0.26), fill=255)
    else:  # TEARDROP
        d.ellipse([0, 0, size - 1, size - 1], fill=255)
        d.rectangle([size // 2, size // 2, size - 1, size - 1], fill=255)
    return m


def draw_glyph(slot, d, c, r, col, bg=None):
    cx, cy = c
    w = max(2, int(r * 0.16))
    if slot == "phone":
        d.arc([cx - r * 0.7, cy - r * 0.7, cx + r * 0.7, cy + r * 0.7], 95, 195, fill=col, width=int(r * 0.34))
        for ang in (95, 195):
            a = math.radians(ang)
            px, py = cx + math.cos(a) * r * 0.55, cy + math.sin(a) * r * 0.55
            d.ellipse([px - r * 0.22, py - r * 0.22, px + r * 0.22, py + r * 0.22], fill=col)
    elif slot == "message":
        d.rounded_rectangle([cx - r * 0.78, cy - r * 0.62, cx + r * 0.78, cy + r * 0.42], radius=r * 0.3, fill=col)
        d.polygon([(cx - r * 0.35, cy + r * 0.35), (cx - r * 0.62, cy + r * 0.8), (cx + r * 0.1, cy + r * 0.38)], fill=col)
    elif slot == "browser":
        d.ellipse([cx - r * 0.72, cy - r * 0.72, cx + r * 0.72, cy + r * 0.72], outline=col, width=w)
        d.ellipse([cx - r * 0.3, cy - r * 0.72, cx + r * 0.3, cy + r * 0.72], outline=col, width=w)
        d.line([cx - r * 0.72, cy, cx + r * 0.72, cy], fill=col, width=w)
    elif slot == "camera":
        d.rounded_rectangle([cx - r * 0.78, cy - r * 0.42, cx + r * 0.78, cy + r * 0.6], radius=r * 0.18, fill=col)
        d.rounded_rectangle([cx - r * 0.3, cy - r * 0.64, cx + r * 0.3, cy - r * 0.38], radius=r * 0.08, fill=col)
    elif slot == "gallery":
        d.rounded_rectangle([cx - r * 0.75, cy - r * 0.62, cx + r * 0.75, cy + r * 0.62], radius=r * 0.16, outline=col, width=w)
        d.polygon([(cx - r * 0.6, cy + r * 0.5), (cx - r * 0.15, cy - r * 0.05), (cx + r * 0.15, cy + r * 0.3), (cx + r * 0.35, cy + r * 0.1), (cx + r * 0.62, cy + r * 0.5)], fill=col)
        d.ellipse([cx + r * 0.2, cy - r * 0.42, cx + r * 0.46, cy - r * 0.16], fill=col)
    elif slot == "music":
        d.ellipse([cx - r * 0.65, cy + r * 0.2, cx - r * 0.15, cy + r * 0.7], fill=col)
        d.ellipse([cx + r * 0.15, cy + r * 0.05, cx + r * 0.65, cy + r * 0.55], fill=col)
        d.rectangle([cx - r * 0.2, cy - r * 0.65, cx - r * 0.02, cy + r * 0.45], fill=col)
        d.rectangle([cx + r * 0.6, cy - r * 0.8, cx + r * 0.78, cy + r * 0.3], fill=col)
        d.polygon([(cx - r * 0.2, cy - r * 0.65), (cx + r * 0.78, cy - r * 0.8), (cx + r * 0.78, cy - r * 0.5), (cx - r * 0.2, cy - r * 0.35)], fill=col)
    elif slot == "clock":
        d.ellipse([cx - r * 0.72, cy - r * 0.72, cx + r * 0.72, cy + r * 0.72], outline=col, width=w)
        d.line([cx, cy, cx, cy - r * 0.45], fill=col, width=w)
        d.line([cx, cy, cx + r * 0.32, cy + r * 0.14], fill=col, width=w)
    elif slot == "calendar":
        d.rounded_rectangle([cx - r * 0.72, cy - r * 0.62, cx + r * 0.72, cy + r * 0.72], radius=r * 0.16, outline=col, width=w)
        d.rectangle([cx - r * 0.72, cy - r * 0.62, cx + r * 0.72, cy - r * 0.22], fill=col)
        for gx in (-0.4, 0, 0.4):
            for gy in (0.08, 0.42):
                d.ellipse([cx + gx * r - r * 0.07, cy + gy * r - r * 0.07, cx + gx * r + r * 0.07, cy + gy * r + r * 0.07], fill=col)
    elif slot == "settings":
        for k in range(8):
            a = math.radians(k * 45)
            x, y = cx + math.cos(a) * r * 0.62, cy + math.sin(a) * r * 0.62
            d.ellipse([x - r * 0.16, y - r * 0.16, x + r * 0.16, y + r * 0.16], fill=col)
        d.ellipse([cx - r * 0.55, cy - r * 0.55, cx + r * 0.55, cy + r * 0.55], fill=col)
    elif slot == "play":
        d.polygon([(cx - r * 0.5, cy - r * 0.68), (cx + r * 0.72, cy), (cx - r * 0.5, cy + r * 0.68)], fill=col)
    elif slot == "weather":
        sx, sy, sr = cx + r * 0.32, cy - r * 0.34, r * 0.34
        for k in range(8):
            ang = math.radians(k * 45)
            d.line([sx + math.cos(ang) * sr * 1.3, sy + math.sin(ang) * sr * 1.3, sx + math.cos(ang) * sr * 1.75, sy + math.sin(ang) * sr * 1.75], fill=col, width=w)
        d.ellipse([sx - sr, sy - sr, sx + sr, sy + sr], fill=col)

        def cl(g, color):
            d.ellipse([cx - r * 0.78 - g, cy - r * 0.02 - g, cx - r * 0.18 + g, cy + r * 0.58 + g], fill=color)
            d.ellipse([cx - r * 0.45 - g, cy - r * 0.3 - g, cx + r * 0.25 + g, cy + r * 0.4 + g], fill=color)
            d.ellipse([cx + r * 0.0 - g, cy - r * 0.05 - g, cx + r * 0.62 + g, cy + r * 0.55 + g], fill=color)
            d.rounded_rectangle([cx - r * 0.6 - g, cy + r * 0.12 - g, cx + r * 0.5 + g, cy + r * 0.58 + g], radius=r * 0.22, fill=color)
        if bg is not None:
            cl(r * 0.12, bg)
        cl(0, col)
    elif slot == "folder":
        d.rounded_rectangle([cx - r * 0.78, cy - r * 0.55, cx - r * 0.1, cy - r * 0.2], radius=r * 0.1, fill=col)
        d.rounded_rectangle([cx - r * 0.78, cy - r * 0.35, cx + r * 0.78, cy + r * 0.6], radius=r * 0.14, fill=col)


def make_icon(slot, shape, plate, glyph_col, tint=None):
    """plate: dict(kind=flat|gradient|glass|neumo|neon|outline, c1, c2, ring)"""
    size = IS
    mask = shape_mask(shape)
    base = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    kind = plate["kind"]
    c1, c2 = hexc(plate["c1"]), hexc(plate.get("c2", plate["c1"]))
    fill = Image.new("RGBA", (size, size))
    arr = np.zeros((size, size, 4), dtype=np.uint8)
    for ch in range(4):
        arr[:, :, ch] = np.linspace(c1[ch], c2[ch], size)[:, None]
    fill = Image.fromarray(arr, "RGBA")
    if kind == "glass":
        hl = Image.new("RGBA", (size, size), (255, 255, 255, 0))
        ImageDraw.Draw(hl).ellipse([-size * 0.2, -size * 0.55, size * 1.2, size * 0.45], fill=(255, 255, 255, 46))
        fill = Image.alpha_composite(fill, hl)
    base.paste(fill, (0, 0), mask)
    d = ImageDraw.Draw(base)
    if kind in ("neon", "outline", "glass"):
        ring = hexc(plate.get("ring", "#ffffff"), plate.get("ring_a", 255))
        edge = mask.filter(ImageFilter.MinFilter(13))
        ringlayer = Image.new("RGBA", (size, size), ring)
        ringmask = Image.fromarray(np.clip(np.array(mask, dtype=np.int16) - np.array(edge, dtype=np.int16), 0, 255).astype(np.uint8), "L")
        base.paste(ringlayer, (0, 0), ringmask)
    if kind == "neumo":
        sh = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        base2 = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        edge = mask.filter(ImageFilter.MinFilter(25)).filter(ImageFilter.GaussianBlur(14))
        dark = Image.new("RGBA", (size, size), hexc(plate.get("shadow", "#b8b0d0"), 160))
        base.paste(dark, (0, 0), Image.fromarray(np.clip(np.array(mask, dtype=np.int16) - np.array(edge, dtype=np.int16), 0, 255).astype(np.uint8), "L"))
        d = ImageDraw.Draw(base)
    draw_glyph(slot, d, (size / 2, size / 2), size * 0.3, hexc(glyph_col), bg=hexc(plate["c1"]))
    if kind == "neon":
        gl = base.filter(ImageFilter.GaussianBlur(10))
        base = Image.alpha_composite(gl, base)
    # توهّج خارجي صغير يتقصّ بالإطار النهائي، فنرجّع للحجم المطلوب
    return base.resize((144, 144), Image.LANCZOS)


# ───────────── تعريف الثيمات ─────────────
THEMES = [
    dict(id="n-midnight-peak", ar="قمة منتصف الليل", en="Midnight Peak", cat=("ليلي", "Night"), tags=(["أسود", "جبال", "فخم"], ["Black", "Mountains", "Elegant"]),
         scene=scene_midnight_peak, mode="DARK", accent="#9CB4FF", text="#FFFFFF", clock="BIG", shape="CIRCLE",
         plate=dict(kind="glass", c1="#1b2540", c2="#0c1224", ring="#9CB4FF", ring_a=150), glyph="#EAF0FF"),
    dict(id="n-alpine-mist", ar="ضباب الألب", en="Alpine Mist", cat=("طبيعة", "Scenery"), tags=(["أخضر", "جبال", "هادئ"], ["Green", "Mountains", "Calm"]),
         scene=scene_alpine_mist, mode="LIGHT", accent="#4F7A63", text="#1E2E26", clock="BIG_SERIF", shape="ROUNDED",
         plate=dict(kind="flat", c1="#7f9f8d", c2="#6b8c79"), glyph="#F4FAF6"),
    dict(id="n-pastel-puffs", ar="سحب باستيل", en="Pastel Puffs", cat=("لطيف", "Cute"), tags=(["وردي", "كاواي", "ناعم"], ["Pink", "Kawaii", "Soft"]),
         scene=scene_pastel_puffs, mode="LIGHT", accent="#FF7FAE", text="#5A2D45", clock="SMALL", shape="CIRCLE",
         plate=dict(kind="gradient", c1="#ffb3cf", c2="#ff8fb8"), glyph="#FFFFFF"),
    dict(id="n-galactic", ar="رحلة المجرة", en="Galactic Drift", cat=("فضاء", "Space"), tags=(["أرجواني", "فضاء", "نيون"], ["Purple", "Space", "Neon"]),
         scene=scene_galactic, mode="DARK", accent="#B79CFF", text="#F2EEFF", clock="SMALL", shape="CIRCLE",
         plate=dict(kind="neon", c1="#120e2c", c2="#0a0820", ring="#B79CFF"), glyph="#D9CCFF"),
    dict(id="n-golden-hour", ar="الساعة الذهبية", en="Golden Hour", cat=("طبيعة", "Scenery"), tags=(["برتقالي", "غروب", "دافئ"], ["Orange", "Sunset", "Warm"]),
         scene=scene_golden_hour, mode="DARK", accent="#FFC27A", text="#FFF4E3", clock="BIG", shape="SQUIRCLE",
         plate=dict(kind="glass", c1="#5a2548", c2="#3d1a3a", ring="#FFD9A0", ring_a=130), glyph="#FFE9C7"),
    dict(id="n-blue-sky", ar="سماء زرقاء", en="Blue Sky", cat=("طبيعة", "Scenery"), tags=(["أزرق", "سحاب", "منعش"], ["Blue", "Clouds", "Fresh"]),
         scene=scene_blue_sky, mode="LIGHT", accent="#1F6FE0", text="#FFFFFF", clock="BIG", shape="SQUIRCLE",
         plate=dict(kind="flat", c1="#ffffff", c2="#eaf4ff"), glyph="#2B78E8"),
    dict(id="n-neon-grid", ar="شبكة النيون", en="Neon Grid", cat=("مستقبلي", "Futuristic"), tags=(["نيون", "ريترو", "سينثويف"], ["Neon", "Retro", "Synthwave"]),
         scene=scene_neon_grid, mode="DARK", accent="#FF3D9A", text="#FFFFFF", clock="BIG", shape="ROUNDED",
         plate=dict(kind="neon", c1="#14052e", c2="#0a021c", ring="#47E1FF"), glyph="#FF6FC0"),
    dict(id="n-soft-check", ar="مربعات ناعمة", en="Soft Check", cat=("لطيف", "Cute"), tags=(["بنفسجي", "قلب", "ناعم"], ["Lilac", "Heart", "Soft"]),
         scene=scene_soft_check, mode="LIGHT", accent="#B78BFF", text="#4B3A73", clock="SMALL", shape="SQUIRCLE",
         plate=dict(kind="neumo", c1="#fbf8ff", c2="#ece4fb", shadow="#b9acdc"), glyph="#8F6FE0"),
    dict(id="n-hearts", ar="حب وقلوب", en="Hearts", cat=("لطيف", "Cute"), tags=(["وردي", "قلوب", "رومانسي"], ["Pink", "Hearts", "Romantic"]),
         scene=scene_hearts, mode="LIGHT", accent="#FF3E7F", text="#FFFFFF", clock="BIG", shape="TEARDROP",
         plate=dict(kind="gradient", c1="#ff5d8f", c2="#e83a76"), glyph="#FFFFFF"),
    dict(id="n-fresh-green", ar="أخضر منعش", en="Fresh Green", cat=("بسيط", "Minimal"), tags=(["أخضر", "بسيط", "قلب"], ["Green", "Minimal", "Heart"]),
         scene=scene_fresh_green, mode="LIGHT", accent="#2E9D5B", text="#FFFFFF", clock="SMALL", shape="CIRCLE",
         plate=dict(kind="outline", c1="#ffffff", c2="#ffffff", ring="#2E9D5B", ring_a=0), glyph="#2E9D5B"),
    dict(id="n-gold-flow", ar="تدفق ذهبي", en="Gold Flow", cat=("فخامة", "Luxury"), tags=(["ذهبي", "أسود", "تجريدي"], ["Gold", "Black", "Abstract"]),
         scene=scene_gold_flow, mode="DARK", accent="#E0AA5A", text="#F6E7CC", clock="BIG_SERIF", shape="ROUNDED",
         plate=dict(kind="glass", c1="#1c1a17", c2="#100f0d", ring="#E0AA5A", ring_a=170), glyph="#E8B968"),
    dict(id="n-lighthouse", ar="جزيرة المنارة", en="Lighthouse Isle", cat=("لطيف", "Cute"), tags=(["أزرق", "بحر", "ملوّن"], ["Blue", "Sea", "Colorful"]),
         scene=scene_lighthouse, mode="LIGHT", accent="#E8554E", text="#10405A", clock="BIG", shape="ROUNDED",
         plate=dict(kind="gradient", c1="#ffffff", c2="#dff3ff"), glyph="#E8554E"),
    dict(id="n-bamboo", ar="غابة الخيزران", en="Bamboo Mist", cat=("طبيعة", "Scenery"), tags=(["أخضر", "خيزران", "هادئ"], ["Green", "Bamboo", "Calm"]),
         scene=scene_bamboo, mode="LIGHT", accent="#4C7A34", text="#23381A", clock="BIG_SERIF", shape="SQUIRCLE",
         plate=dict(kind="flat", c1="#e9f3d5", c2="#d9e8bd"), glyph="#4C7A34"),
]


def main():
    sp = os.path.join(OUT, "store.json")
    store = json.load(open(sp, encoding="utf-8"))
    store["themes"] = [t for t in store["themes"] if not t["id"].startswith("n-")]
    for t in THEMES:
        od = os.path.join(OUT, "themes", t["id"])
        os.makedirs(od, exist_ok=True)
        im = t["scene"]()
        im.save(os.path.join(od, "wallpaper.jpg"), "JPEG", quality=86, optimize=True, progressive=True)
        im.resize((540, 1215), Image.LANCZOS).save(os.path.join(od, "preview.jpg"), "JPEG", quality=80, optimize=True)
        with zipfile.ZipFile(os.path.join(od, "icons.zip"), "w", zipfile.ZIP_DEFLATED) as z:
            for s in SLOTS:
                ic = make_icon(s, t["shape"], t["plate"], t["glyph"])
                p = os.path.join("/tmp", f"{t['id']}-{s}.png")
                ic.save(p)
                z.write(p, s + ".png")
                os.remove(p)
        plate_c = t["plate"]["c1"]
        store["themes"].append({
            "id": t["id"],
            "name": {"ar": t["ar"], "en": t["en"]},
            "category": {"ar": t["cat"][0], "en": t["cat"][1]},
            "tags": {"ar": t["tags"][0] + ["أيقونات خاصة"], "en": t["tags"][1] + ["Custom icons"]},
            "author": "Aura Labs",
            "sizeKb": (os.path.getsize(os.path.join(od, "wallpaper.jpg")) + os.path.getsize(os.path.join(od, "icons.zip"))) // 1024,
            "wallpaper": f"themes/{t['id']}/wallpaper.jpg",
            "preview": f"themes/{t['id']}/preview.jpg",
            "icons": f"themes/{t['id']}/icons.zip",
            "style": {
                "mode": t["mode"], "accent": t["accent"], "homeText": t["text"], "clock": t["clock"],
                "icon": {"shape": t["shape"], "plate": "#E6" + plate_c.lstrip("#"), "mono": False, "tint": "#FFFFFF"},
            },
        })
        print("ok", t["id"])
    json.dump(store, open(sp, "w", encoding="utf-8"), ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
