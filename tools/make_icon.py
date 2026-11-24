"""產生 Spewtify 圖示：綠色圓底＋音波條排成的 W。
用法：python make_icon.py <res 資料夾> [--preview out.png]
座標系統＝自適應圖示 108x108（安全區半徑 33）。"""
import sys, os
from PIL import Image, ImageDraw

GREEN = "#1FD16A"
DARK = "#121212"
CX = CY = 54.0
R = 31.0
# W 折線（正規化，y 向下）：左上 → 左下 → 中間 → 右下 → 右上
W_PTS = [(-1, -1), (-0.5, 1), (0, -0.15), (0.5, 1), (1, -1)]
N_BARS = 9
HALF_W, AMP = 18.5, 11.0
BAR_HALF = 4.2     # 每根音波條半長
BAR_WIDTH = 3.9    # 音波條粗細（圓頭）


def w_y(xn):
    for (x0, y0), (x1, y1) in zip(W_PTS, W_PTS[1:]):
        if x0 <= xn <= x1:
            return y0 + (y1 - y0) * (xn - x0) / (x1 - x0)
    raise ValueError(xn)


def bars():
    out = []
    for i in range(N_BARS):
        xn = -1 + 2 * i / (N_BARS - 1)
        x = CX + xn * HALF_W
        y = CY + w_y(xn) * AMP
        # 越靠兩端越長，像音量包絡
        half = BAR_HALF * (1.25 if i in (0, N_BARS - 1) else 1.0)
        out.append((x, y - half, y + half))
    return out


def vector_xml(with_circle, bar_color, circle_color=GREEN):
    lines = ['<?xml version="1.0" encoding="utf-8"?>',
             '<!-- Spewtify 圖示：由 make_icon.py 產生 -->',
             '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
             '    android:width="108dp"', '    android:height="108dp"',
             '    android:viewportWidth="108"', '    android:viewportHeight="108">']
    if with_circle:
        lines.append(f'    <path android:fillColor="{circle_color}" android:pathData="'
                     f'M{CX - R},{CY}a{R},{R} 0 1,0 {2 * R},0a{R},{R} 0 1,0 {-2 * R},0z" />')
    for x, y1, y2 in bars():
        lines.append(f'    <path android:strokeColor="{bar_color}" android:strokeWidth="{BAR_WIDTH}" '
                     f'android:strokeLineCap="round" android:pathData="M{x:.2f},{y1:.2f}L{x:.2f},{y2:.2f}" />')
    lines.append('</vector>')
    return "\n".join(lines) + "\n"


def render(size, shape):
    """shape: square＝舊版方形圖示（深色圓角方底）、round＝圓形圖示、preview＝大預覽。"""
    ss = 4
    S = size * ss
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # 舊版圖示沒有遮罩，把 108 的內容縮到四周留白
    k = S / 108.0
    if shape == "square":
        d.rounded_rectangle([2 * k, 2 * k, 106 * k, 106 * k], radius=22 * k, fill=DARK)
    elif shape == "round":
        d.ellipse([2 * k, 2 * k, 106 * k, 106 * k], fill=DARK)
    else:
        d.rectangle([0, 0, S, S], fill=DARK)
    scale = 1.45 if shape in ("square", "round") else 1.0   # 舊版圖示讓綠圓放大一點
    def t(v, c=54.0):
        return (c + (v - c) * scale) * k
    d.ellipse([t(CX - R), t(CY - R), t(CX + R), t(CY + R)], fill=GREEN)
    bw = BAR_WIDTH * scale * k
    for x, y1, y2 in bars():
        d.line([t(x), t(y1), t(x), t(y2)], fill=DARK, width=round(bw))
        for yy in (y1, y2):
            d.ellipse([t(x) - bw / 2, t(yy) - bw / 2, t(x) + bw / 2, t(yy) + bw / 2], fill=DARK)
    return img.resize((size, size), Image.LANCZOS)


def main():
    res = sys.argv[1]
    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        row = Image.new("RGBA", (3 * 260, 260), (40, 40, 40, 255))
        for i, sh in enumerate(["preview", "square", "round"]):
            row.paste(render(240, sh), (10 + i * 260, 10), render(240, sh))
        row.save(out)
        print("preview", out)
        return
    with open(os.path.join(res, "drawable", "ic_launcher_foreground.xml"), "w", encoding="utf-8", newline="\n") as f:
        f.write(vector_xml(True, DARK))
    with open(os.path.join(res, "drawable", "ic_launcher_monochrome.xml"), "w", encoding="utf-8", newline="\n") as f:
        f.write(vector_xml(False, "#FFFFFF"))
    for dpi, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
        render(px, "square").save(os.path.join(res, f"mipmap-{dpi}", "ic_launcher.webp"), "WEBP", lossless=True)
        render(px, "round").save(os.path.join(res, f"mipmap-{dpi}", "ic_launcher_round.webp"), "WEBP", lossless=True)
    print("written")


if __name__ == "__main__":
    main()
