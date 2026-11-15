"""Generates the splash/menu logo. Usage: python tools/make_logo.py "RELAPSED" "CLIENT" out.png"""
import sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter

big, small, out = sys.argv[1], sys.argv[2], sys.argv[3]
W, H = 1024, 384
FONT_DIR = "/usr/share/fonts/truetype/google-fonts/"
size = 210
fbig = ImageFont.truetype(FONT_DIR + "Poppins-Bold.ttf", size)
while ImageDraw.Draw(Image.new("L", (1, 1))).textlength(big, font=fbig) > W - 60 and size > 60:
    size -= 6
    fbig = ImageFont.truetype(FONT_DIR + "Poppins-Bold.ttf", size)
fsmall = ImageFont.truetype(FONT_DIR + "Poppins-Bold.ttf", 56)

# Gradient-filled big word
text_mask = Image.new("L", (W, H), 0)
d = ImageDraw.Draw(text_mask)
bw = d.textlength(big, font=fbig)
d.text(((W - bw) / 2, 10 + (210 - size) * 0.6), big, font=fbig, fill=255)

grad = Image.new("RGBA", (W, H))
c1, c2 = (56, 224, 255), (176, 64, 255)  # cyan -> purple
x0, x1 = (W - bw) / 2, (W + bw) / 2
for x in range(W):
    t = min(1, max(0, (x - x0) / (x1 - x0)))
    col = tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3)) + (255,)
    ImageDraw.Draw(grad).line([(x, 0), (x, H)], fill=col)

img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
# Glow
glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
glow.paste(grad, (0, 0), text_mask)
glow = glow.filter(ImageFilter.GaussianBlur(18))
glow.putalpha(glow.getchannel("A").point(lambda a: int(a * 0.55)))
img = Image.alpha_composite(img, glow)
word = Image.new("RGBA", (W, H), (0, 0, 0, 0))
word.paste(grad, (0, 0), text_mask)
img = Image.alpha_composite(img, word)

# Spaced small word
d = ImageDraw.Draw(img)
spaced = "  ".join(small)
sw = d.textlength(spaced, font=fsmall)
d.text(((W - sw) / 2, 270), spaced, font=fsmall, fill=(235, 240, 245, 255))
img.save(out)
print("wrote", out)
