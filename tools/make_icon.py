"""產生 Spewtify 圖示（v2，2026-11-27 使用者指定）：黑底＋綠色圓環＋中間 5 根圓角聲波條。
用法：python make_icon.py <res 資料夾> [--preview out.png]
座標取自使用者給的 843x845 圖（圓心 421,423），換算到自適應圖示 108x108。"""
import sys, os
from PIL import Image, ImageDraw

GREEN = "#1BC250"
DARK = "#070807"
SRC_CX, SRC_CY = 421.0, 423.0
RING_OUTER, RING_W = 339.0, 36.0          # 原圖像素
BARS = [(205, 267, 429, 501), (297, 359, 327, 539), (389, 452, 235, 610),
        (483, 545, 386, 539), (575, 638, 437, 497)]  # x1, x2, y1, y2（原圖像素）
BAR_CORNER = 14.0
FG_RING_OUTER = 29.0   # 自適應圖示裡圓環外半徑（安全區 33；圓形遮罩看得到 36）


def geom(view=108.0, outer=FG_RING_OUTER):
    """回傳 (圓環中線半徑, 圓環粗, 聲波條[(x1,y1,x2,y2)], 圓角)，座標在 view x view 裡。"""
    s = outer / RING_OUTER
    c = view / 2
    tx = lambda x: c + (x - SRC_CX) * s
    ty = lambda y: c + (y - SRC_CY) * s
    bars = [(tx(x1), ty(y1), tx(x2 + 1), ty(y2 + 1)) for x1, x2, y1, y2 in BARS]
    return (RING_OUTER - RING_W / 2) * s, RING_W * s, bars, BAR_CORNER * s


def rrect_path(x1, y1, x2, y2, r):
    return (f"M{x1 + r:.2f},{y1:.2f}H{x2 - r:.2f}A{r:.2f},{r:.2f} 0 0 1 {x2:.2f},{y1 + r:.2f}"
            f"V{y2 - r:.2f}A{r:.2f},{r:.2f} 0 0 1 {x2 - r:.2f},{y2:.2f}"
            f"H{x1 + r:.2f}A{r:.2f},{r:.2f} 0 0 1 {x1:.2f},{y2 - r:.2f}"
            f"V{y1 + r:.2f}A{r:.2f},{r:.2f} 0 0 1 {x1 + r:.2f},{y1:.2f}Z")


def vector_xml(color, view=108.0, outer=FG_RING_OUTER, size_dp=108):
    rm, rw, bars, rc = geom(view, outer)
    c = view / 2
    lines = ['<?xml version="1.0" encoding="utf-8"?>',
             '<!-- Spewtify 圖示：由 tools/make_icon.py 產生，不要手改 -->',
             '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
             f'    android:width="{size_dp}dp"', f'    android:height="{size_dp}dp"',
             f'    android:viewportWidth="{view:g}"', f'    android:viewportHeight="{view:g}">',
             f'    <path android:strokeColor="{color}" android:strokeWidth="{rw:.2f}" android:fillColor="#00000000" '
             f'android:pathData="M{c - rm:.2f},{c:.2f}a{rm:.2f},{rm:.2f} 0 1,0 {2 * rm:.2f},0a{rm:.2f},{rm:.2f} 0 1,0 {-2 * rm:.2f},0" />']
    for b in bars:
        lines.append(f'    <path android:fillColor="{color}" android:pathData="{rrect_path(*b, rc)}" />')
    lines.append('</vector>')
    return "\n".join(lines) + "\n"


def render(size, shape):
    """shape: square＝舊版方形圖示、round＝圓形圖示、preview＝滿版預覽。"""
    ss = 4
    S = size * ss
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if shape == "square":
        d.rounded_rectangle([S * 0.02, S * 0.02, S * 0.98, S * 0.98], radius=S * 0.2, fill=DARK)
    elif shape == "round":
        d.ellipse([S * 0.02, S * 0.02, S * 0.98, S * 0.98], fill=DARK)
    else:
        d.rectangle([0, 0, S, S], fill=DARK)
    # 舊版圖示沒有遮罩：內容放大到跟原圖差不多的比例（圓環約佔 80%）
    outer = S * 0.40 if shape != "preview" else S * 0.402
    rm, rw, bars, rc = geom(S, outer)
    c = S / 2
    d.ellipse([c - rm - rw / 2, c - rm - rw / 2, c + rm + rw / 2, c + rm + rw / 2], outline=GREEN, width=round(rw))
    for x1, y1, x2, y2 in bars:
        d.rounded_rectangle([x1, y1, x2, y2], radius=rc, fill=GREEN)
    return img.resize((size, size), Image.LANCZOS)


def main():
    res = sys.argv[1]
    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        row = Image.new("RGBA", (3 * 260, 260), (40, 40, 40, 255))
        for i, sh in enumerate(["preview", "square", "round"]):
            im = render(240, sh)
            row.paste(im, (10 + i * 260, 10), im)
        row.save(out)
        print("preview", out)
        return

    def write(name, text):
        with open(os.path.join(res, "drawable", name), "w", encoding="utf-8", newline="\n") as f:
            f.write(text)

    write("ic_launcher_foreground.xml", vector_xml(GREEN))
    write("ic_launcher_monochrome.xml", vector_xml("#FFFFFF"))
    # 通知列小圖示：系統會自己上色，只看形狀；圓環放大到幾乎滿版
    write("app_icon.xml", vector_xml("#FF000000", view=24.0, outer=11.5, size_dp=24))
    for dpi, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
        render(px, "square").save(os.path.join(res, f"mipmap-{dpi}", "ic_launcher.webp"), "WEBP", lossless=True)
        render(px, "round").save(os.path.join(res, f"mipmap-{dpi}", "ic_launcher_round.webp"), "WEBP", lossless=True)
    print("written")


if __name__ == "__main__":
    main()
