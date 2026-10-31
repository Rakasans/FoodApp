"""Builds Android adaptive-icon layers from the generated icon image.

Each pixel is modelled as a mix of two of four flat colours (background, page,
text line, pot). This flattens AI-generation noise and yields a clean,
transparent foreground plus a monochrome layer (pages + pot, text lines cut out).
"""
import itertools, os, sys
import numpy as np
from PIL import Image

src, res_dir = sys.argv[1], sys.argv[2]
# Palette sampled from the source image — update when the source image changes.
BG = np.array([40, 105, 77.])
PAGE = np.array([249, 250, 249.])
LINE = np.array([118, 197, 159.])
POT = np.array([141, 214, 181.])
cols = [BG, PAGE, LINE, POT]

im = np.array(Image.open(src).convert("RGB")).astype(float)
H, W, _ = im.shape
best_res = np.full((H, W), np.inf)
w = np.zeros((H, W, 4))  # weights per colour
for i, j in itertools.combinations(range(4), 2):
    a, b = cols[i], cols[j]
    v = b - a
    t = np.clip(((im - a) @ v) / (v @ v), 0, 1)
    res = np.linalg.norm(im - (a + t[..., None] * v), axis=2)
    better = res < best_res
    best_res[better] = res[better]
    w[better] = 0
    w[better, i] = 1 - t[better]
    w[better, j] = t[better]

alpha = 1 - w[..., 0]
fg_rgb = (w[..., 1:, None] * np.stack(cols[1:])[None, None]).sum(2) / np.maximum(alpha, 1e-6)[..., None]
mono_a = w[..., 1] + w[..., 3]  # pages + pot, text lines become holes

# Drop stray low-alpha specks from generator noise: keep only pixels near the
# solid shapes (alpha > 0.5, dilated by a few px for antialiased edges).
from PIL import ImageFilter
core = Image.fromarray(((alpha > 0.5) * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(7))
keep = np.array(core) > 0
alpha = np.where(keep, alpha, 0)
mono_a = np.where(keep, mono_a, 0)

ys, xs = np.where(alpha > 0.1)
x0, x1, y0, y1 = xs.min(), xs.max() + 1, ys.min(), ys.max() + 1
cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
diag = np.hypot(x1 - x0, y1 - y0)

# Adaptive icon: 108dp canvas, 66dp safe circle. Keep the motif's diagonal
# within ~62dp so no corner is clipped by any launcher mask.
BIG = 1728  # 108dp @16x
scale = (62 / 108 * BIG) / diag

def layer(rgb, a):
    rgba = np.dstack([rgb, a[..., None] * 255]).clip(0, 255).astype(np.uint8)
    crop = Image.fromarray(rgba, "RGBA").crop((int(cx - diag / 2), int(cy - diag / 2), int(cx + diag / 2), int(cy + diag / 2)))
    size = round(diag * scale)
    # premultiplied resize to avoid dark fringes
    crop = crop.resize((size, size), Image.LANCZOS, reducing_gap=3.0) if False else crop
    pm = np.array(crop).astype(float)
    pm[..., :3] *= pm[..., 3:] / 255
    pm_img = Image.fromarray(pm.clip(0, 255).astype(np.uint8), "RGBA").resize((size, size), Image.LANCZOS)
    out = Image.new("RGBA", (BIG, BIG), (0, 0, 0, 0))
    out.paste(pm_img, ((BIG - size) // 2, (BIG - size) // 2))
    return out  # premultiplied

def unpremul(img):
    a = np.array(img).astype(float)
    al = a[..., 3:]
    a[..., :3] = np.where(al > 0, a[..., :3] * 255 / np.maximum(al, 1), 0)
    return Image.fromarray(a.clip(0, 255).astype(np.uint8), "RGBA")

fg_big = layer(fg_rgb, alpha)
mono_big = layer(np.full_like(fg_rgb, 255), mono_a)

dens = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
for d, px in dens.items():
    p = os.path.join(res_dir, f"mipmap-{d}")
    os.makedirs(p, exist_ok=True)
    unpremul(fg_big.resize((px, px), Image.LANCZOS)).save(os.path.join(p, "ic_launcher_foreground.png"), optimize=True)
    unpremul(mono_big.resize((px, px), Image.LANCZOS)).save(os.path.join(p, "ic_launcher_monochrome.png"), optimize=True)

# 512px store/preview icon (full-bleed square) for README / Play Console
prev = Image.new("RGBA", (BIG, BIG), (0x2F, 0x6B, 0x4F, 255))
prev.alpha_composite(unpremul(fg_big))
prev.resize((512, 512), Image.LANCZOS).convert("RGB").save(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "docs", "brand", "icon-512.png"))
print("motif bbox", x0, x1, y0, y1, "scale", round(scale, 3))

if len(sys.argv) > 3:
    unpremul(fg_big).save(os.path.join(sys.argv[3], "fg_big.png"))
    unpremul(mono_big).save(os.path.join(sys.argv[3], "mono_big.png"))
