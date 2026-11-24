"""ثيمات بخلفيات CC0 (sheepla/wallpapers) + أيقونات مولّدة. بيضيف ثيمات o-* ولا يلمس الباقي."""
import json, os, sys, zipfile
from PIL import Image, ImageOps
sys.path.insert(0, os.path.dirname(__file__))
import gen_themes3 as g

SRC = sys.argv[1]
CREDIT = "sheepla/wallpapers (CC0)"
THEMES = [
    dict(id="o-snow-day", f="snow-mountain-day.jpg", cx=0.5, ar="جبل الثلج نهاراً", en="Snow Mountain Day", cat=("طبيعة", "Scenery"),
         tags=(["جبال", "ثلج", "هادئ"], ["Mountains", "Snow", "Calm"]), mode="LIGHT", accent="#3B6EA8", text="#16314F", clock="BIG_SERIF", shape="SQUIRCLE",
         plate=dict(kind="flat", c1="#eaf3fb", c2="#d3e5f5"), glyph="#2F5E94"),
    dict(id="o-snow-dusk", f="snow-mountain-twilight.jpg", cx=0.5, ar="جبل الثلج غروباً", en="Snow Mountain Dusk", cat=("طبيعة", "Scenery"),
         tags=(["جبال", "غروب", "بنفسجي"], ["Mountains", "Dusk", "Purple"]), mode="GLASS", accent="#C9A7FF", text="#FFFFFF", clock="BIG", shape="CIRCLE",
         plate=dict(kind="glass", c1="#3a2f63", c2="#241d44", ring="#C9A7FF", ring_a=140), glyph="#F1E8FF"),
    dict(id="o-snow-night", f="snow-mountain-night.jpg", cx=0.5, ar="جبل الثلج ليلاً", en="Snow Mountain Night", cat=("ليلي", "Night"),
         tags=(["جبال", "ليل", "أزرق"], ["Mountains", "Night", "Blue"]), mode="DARK", accent="#7FB2FF", text="#FFFFFF", clock="BIG", shape="SQUIRCLE",
         plate=dict(kind="gradient", c1="#1d2b4a", c2="#0f1830"), glyph="#DCE9FF"),
    dict(id="o-iceberg-day", f="iceberg-day.png", cx=0.5, ar="جبل الجليد نهاراً", en="Iceberg Day", cat=("بسيط", "Minimal"),
         tags=(["أزرق", "جليد", "بسيط"], ["Blue", "Ice", "Minimal"]), mode="DARK", accent="#7CC4FF", text="#FFFFFF", clock="SMALL", shape="CIRCLE",
         plate=dict(kind="outline", c1="#f2f9ff", c2="#e0eef9", ring="#2E7FBF", ring_a=200), glyph="#2E7FBF"),
    dict(id="o-iceberg-night", f="iceberg-night.png", cx=0.5, ar="جبل الجليد ليلاً", en="Iceberg Night", cat=("ليلي", "Night"),
         tags=(["أزرق", "جليد", "نيون"], ["Blue", "Ice", "Neon"]), mode="DARK", accent="#5CE1E6", text="#FFFFFF", clock="BIG", shape="CIRCLE",
         plate=dict(kind="neon", c1="#0c1a2b", c2="#08111d", ring="#5CE1E6"), glyph="#5CE1E6"),
]

def main():
    sp = os.path.join(g.OUT, "store.json")
    store = json.load(open(sp, encoding="utf-8"))
    store["themes"] = [t for t in store["themes"] if not t["id"].startswith("o-")]
    for t in THEMES:
        od = os.path.join(g.OUT, "themes", t["id"]); os.makedirs(od, exist_ok=True)
        im = Image.open(os.path.join(SRC, t["f"])).convert("RGB")
        im = ImageOps.fit(im, (1080, 2340), Image.LANCZOS, centering=(t["cx"], 0.5))
        im.save(os.path.join(od, "wallpaper.jpg"), "JPEG", quality=88, optimize=True, progressive=True)
        im.resize((540, 1170), Image.LANCZOS).save(os.path.join(od, "preview.jpg"), "JPEG", quality=80, optimize=True)
        with zipfile.ZipFile(os.path.join(od, "icons.zip"), "w", zipfile.ZIP_DEFLATED) as z:
            for s in g.SLOTS:
                p = f"/tmp/{t['id']}-{s}.png"
                g.make_icon(s, t["shape"], t["plate"], t["glyph"]).save(p); z.write(p, s + ".png"); os.remove(p)
        store["themes"].append({
            "id": t["id"], "name": {"ar": t["ar"], "en": t["en"]}, "category": {"ar": t["cat"][0], "en": t["cat"][1]},
            "tags": {"ar": t["tags"][0] + ["أيقونات خاصة"], "en": t["tags"][1] + ["Custom icons"]},
            "author": "Aura Labs · wallpaper: " + CREDIT,
            "sizeKb": (os.path.getsize(os.path.join(od, "wallpaper.jpg")) + os.path.getsize(os.path.join(od, "icons.zip"))) // 1024,
            "wallpaper": f"themes/{t['id']}/wallpaper.jpg", "preview": f"themes/{t['id']}/preview.jpg", "icons": f"themes/{t['id']}/icons.zip",
            "style": {"mode": t["mode"], "accent": t["accent"], "homeText": t["text"], "clock": t["clock"],
                      "icon": {"shape": t["shape"], "plate": "#E6" + t["plate"]["c1"].lstrip("#"), "mono": False, "tint": "#FFFFFF"}},
        })
        print("ok", t["id"])
    json.dump(store, open(sp, "w", encoding="utf-8"), ensure_ascii=False, indent=1)

main()
