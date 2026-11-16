#!/usr/bin/env python3
"""Client branding art, drawn exactly like the RelapsedMC server's wordmark (lag-network tools/pack/genpack.py).

Writes into src/client/resources/assets/factionsclient/:
  textures/gui/logo.png      1024x384  white RELAPSED, purple glow, gradient bar, tiny MC bottom-right
  textures/gui/splash_logo.png 1024x384  the same logo without the bar: the loading screen draws its progress bar there
                                       (bar position printed for LoadingOverlayMixin.BAR_*)
  textures/gui/gradient.png  256x16    cyan -> purple -> pink (buttons, bars, switches)
  icon.png                   128x128   "R" monogram (Mod Menu / window)
Run: python3 tools/make_logo.py   (needs Pillow and the Inter font in /usr/share/fonts/opentype/inter)
"""
import os
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.join(os.path.dirname(__file__), "..")
ASSETS = os.path.join(ROOT, "src", "client", "resources", "assets", "factionsclient")
FONT_DIR = "/usr/share/fonts/opentype/inter"
CYAN, PURPLE, PINK = (0x38, 0xE0, 0xFF), (0xB0, 0x40, 0xFF), (0xFF, 0x4D, 0xD2)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def gradient(w, h, stops, sheen=True):
    g = Image.new("RGBA", (w, h))
    px = g.load()
    for x in range(w):
        t = x / max(1, w - 1)
        seg = min(len(stops) - 2, int(t * (len(stops) - 1)))
        c = lerp(stops[seg], stops[seg + 1], t * (len(stops) - 1) - seg)
        for y in range(h):
            k = 1.12 - 0.28 * (y / max(1, h - 1)) if sheen else 1.0
            px[x, y] = (min(255, int(c[0] * k)), min(255, int(c[1] * k)), min(255, int(c[2] * k)), 255)
    return g


BAR_RECT = None  # (x1, y1, x2, y2) of the bar inside the wordmark image, set by wordmark()


def wordmark(scale, with_bar=True):
    global BAR_RECT
    f = ImageFont.truetype(os.path.join(FONT_DIR, "InterDisplay-Black.otf"), 38 * scale)
    word, spacing, pad = "RELAPSED", 5 * scale, 6 * scale
    bb = f.getbbox(word)
    w = int(bb[2] - bb[0] + spacing * len(word) + pad * 2)
    h = bb[3] - bb[1] + pad * 2
    m = Image.new("L", (w, h))
    d = ImageDraw.Draw(m)
    x = pad
    for ch in word:
        d.text((x - bb[0], pad - bb[1]), ch, font=f, fill=255)
        x += f.getlength(ch) + spacing
    W, H = w + 4 * scale, h + 24 * scale
    out = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    glow.paste((176, 64, 255, 255), (2 * scale, 0), m)
    out = Image.alpha_composite(out, glow.filter(ImageFilter.GaussianBlur(5 * scale)))
    face = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    face.paste((255, 255, 255, 255), (2 * scale, 0), m)
    out = Image.alpha_composite(out, face)
    f2 = ImageFont.truetype(os.path.join(FONT_DIR, "InterDisplay-Black.otf"), 14 * scale)
    mb = f2.getbbox("MC")
    mw = int(mb[2] - mb[0] + 3 * scale * 2)
    mm = Image.new("L", (mw + 2, mb[3] - mb[1] + 2))
    md = ImageDraw.Draw(mm)
    mx = 0
    for ch in "MC":
        md.text((mx - mb[0], -mb[1]), ch, font=f2, fill=255)
        mx += f2.getlength(ch) + 3 * scale
    mcx, mcy = W - mm.width - 3 * scale, h - 1 * scale
    mcl = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    mcl.paste(gradient(mm.width, mm.height, [PURPLE, PINK]), (mcx, mcy), mm)
    out = Image.alpha_composite(out, mcl.filter(ImageFilter.GaussianBlur(3 * scale)))
    out = Image.alpha_composite(out, mcl)
    bar = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    bm = Image.new("L", (W, H))
    by = mcy + mm.height // 2 - 1 * scale
    BAR_RECT = (4 * scale, by, mcx - 4 * scale, by + 3 * scale)
    if not with_bar:
        return out
    ImageDraw.Draw(bm).rounded_rectangle(BAR_RECT, radius=2 * scale, fill=255)
    bar.paste(gradient(W, H, [CYAN, PURPLE, PINK]), (0, 0), bm)
    out = Image.alpha_composite(out, bar.filter(ImageFilter.GaussianBlur(2 * scale)))
    out = Image.alpha_composite(out, bar)
    return out


def logo():
    W, H = 1024, 384
    full = wordmark(8)
    box = full.getbbox()
    k = min((W - 48) / (box[2] - box[0]), (H - 48) / (box[3] - box[1]))
    size = (int((box[2] - box[0]) * k), int((box[3] - box[1]) * k))
    ox, oy = (W - size[0]) // 2, (H - size[1]) // 2

    def place(mark):
        out = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        out.alpha_composite(mark.crop(box).resize(size, Image.LANCZOS), (ox, oy))
        return out

    place(full).save(os.path.join(ASSETS, "textures", "gui", "logo.png"))
    rect = BAR_RECT
    place(wordmark(8, with_bar=False)).save(os.path.join(ASSETS, "textures", "gui", "splash_logo.png"))
    x1, y1, x2, y2 = [(rect[0] - box[0]) * k + ox, (rect[1] - box[1]) * k + oy, (rect[2] - box[0]) * k + ox, (rect[3] - box[1]) * k + oy]
    print(f"splash bar (texture px): x {x1:.1f}..{x2:.1f}, y {y1:.1f}..{y2:.1f}")


def icon(size=128):
    s = size * 4
    im = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    m = Image.new("L", (s, s))
    ImageDraw.Draw(m).rounded_rectangle((0, 0, s - 1, s - 1), radius=s // 5, fill=255)
    im.paste(gradient(s, s, [(20, 10, 40), (40, 14, 70)]), (0, 0), m)
    bm = Image.new("L", (s, s))
    ImageDraw.Draw(bm).rounded_rectangle((0, 0, s - 1, s - 1), radius=s // 5, outline=255, width=s // 28)
    im.paste(gradient(s, s, [CYAN, PURPLE, PINK]), (0, 0), bm)
    f = ImageFont.truetype(os.path.join(FONT_DIR, "Inter-BlackItalic.otf"), int(s * 0.78))
    tm = Image.new("L", (s, s))
    bb = f.getbbox("R")
    ImageDraw.Draw(tm).text(((s - (bb[2] - bb[0])) // 2 - bb[0], (s - (bb[3] - bb[1])) // 2 - bb[1]), "R", font=f, fill=255)
    glow = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    glow.paste(gradient(s, s, [CYAN, PURPLE, PINK]), (0, 0), tm)
    im = Image.alpha_composite(im, glow.filter(ImageFilter.GaussianBlur(s // 30)))
    im = Image.alpha_composite(im, glow)
    im.resize((size, size), Image.LANCZOS).save(os.path.join(ASSETS, "icon.png"))


def bar():
    gradient(256, 16, [CYAN, PURPLE, PINK], sheen=False).save(os.path.join(ASSETS, "textures", "gui", "gradient.png"))


if __name__ == "__main__":
    logo()
    icon()
    bar()
    print("wrote logo.png, icon.png, gradient.png")
