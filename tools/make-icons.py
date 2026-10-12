# -*- coding: utf-8 -*-
"""Generate monochrome launcher icons for the budget app (no external assets)."""
import os
from PIL import Image, ImageDraw

ROOT = r"C:\Users\Administrator\Documents\deepseek-harness\default-workspace\budget-book\app\res"
SS = 4  # supersampling factor
DENSITIES = [("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2), ("xxhdpi", 3), ("xxxhdpi", 4)]

WHITE = (255, 255, 255, 255)
BLACK = (0, 0, 0, 255)


def cap(d, x, y, w, color):
    r = w / 2.0
    d.ellipse([x - r, y - r, x + r, y + r], fill=color)


def draw_glyph(d, s, color, gh_ratio, gw_ratio, stroke_ratio):
    """Draw a geometric yuan sign centred on a canvas of side s."""
    w = s * stroke_ratio
    cx = s / 2.0
    gh = s * gh_ratio
    gw = s * gw_ratio
    top = (s - gh) / 2.0
    bottom = top + gh
    mid = top + gh * 0.46
    left = cx - gw / 2.0
    right = cx + gw / 2.0
    lw = max(1, int(round(w)))

    # V of the yuan sign
    d.line([left, top, cx, mid], fill=color, width=lw, joint="curve")
    d.line([right, top, cx, mid], fill=color, width=lw, joint="curve")
    # vertical stem
    d.line([cx, mid, cx, bottom], fill=color, width=lw)
    # two horizontal bars
    bar = gw * 0.94
    for f in (0.17, 0.41):
        by = mid + gh * f
        d.line([cx - bar / 2.0, by, cx + bar / 2.0, by], fill=color, width=lw)

    for (x, y) in ((left, top), (right, top), (cx, bottom),
                   (cx - bar / 2.0, mid + gh * 0.17), (cx + bar / 2.0, mid + gh * 0.17),
                   (cx - bar / 2.0, mid + gh * 0.41), (cx + bar / 2.0, mid + gh * 0.41)):
        cap(d, x, y, w, color)


def legacy_icon(px):
    s = px * SS
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, s - 1, s - 1], radius=int(s * 0.24), fill=BLACK)
    draw_glyph(d, s * 0.66, WHITE, gh_ratio=0.58, gw_ratio=0.62, stroke_ratio=0.115)
    return img.resize((px, px), Image.LANCZOS)


def adaptive_foreground(px):
    s = px * SS
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    draw_glyph(d, s * 0.44, WHITE, gh_ratio=0.56, gw_ratio=0.60, stroke_ratio=0.115)
    return img.resize((px, px), Image.LANCZOS)


def main():
    for name, scale in DENSITIES:
        folder = os.path.join(ROOT, "mipmap-" + name)
        os.makedirs(folder, exist_ok=True)

        legacy = legacy_icon(int(round(48 * scale)))
        legacy.save(os.path.join(folder, "ic_launcher.png"), "PNG")
        legacy.save(os.path.join(folder, "ic_launcher_round.png"), "PNG")

        fg = adaptive_foreground(int(round(108 * scale)))
        fg.save(os.path.join(folder, "ic_launcher_foreground.png"), "PNG")

        print("mipmap-%-8s legacy=%dpx fg=%dpx" % (name, legacy.size[0], fg.size[0]))


if __name__ == "__main__":
    main()
