"""Writes a layered PSD of the round Przepisarnia icon (minimal PSD writer, RLE)."""
import struct, sys
import numpy as np
from PIL import Image, ImageDraw

fg_path, mono_path, out_psd, out_png = sys.argv[1:5]
BIG = 1728                       # 108 dp @16x (adaptive canvas)
vis = BIG * 72 // 108            # visible 72 dp area
off = (BIG - vis) // 2
S = 1024
GREEN = (0x2F, 0x6B, 0x4F)

def crop_vis(img):
    return img.crop((off, off, off + vis, off + vis)).resize((S, S), Image.LANCZOS)

fg = crop_vis(Image.open(fg_path).convert("RGBA"))
mono = crop_vis(Image.open(mono_path).convert("RGBA"))

def circle_mask(d_px, ss=4):
    m = Image.new("L", (S * ss, S * ss), 0)
    r = d_px * ss / 2
    c = S * ss / 2
    ImageDraw.Draw(m).ellipse([c - r, c - r, c + r, c + r], 255)
    return m.resize((S, S), Image.LANCZOS)

def solid(color, mask=None):
    img = Image.new("RGBA", (S, S), color + (255,))
    if mask is not None:
        img.putalpha(mask)
    return img

bg_circle = solid(GREEN, circle_mask(S))
bg_square = solid(GREEN)
# guide: safe zone 66 dp of the 72 dp visible area
safe_d = S * 66 / 72
ring = Image.new("L", (S * 4, S * 4), 0)
d = ImageDraw.Draw(ring); c = S * 2; r = safe_d * 2
d.ellipse([c - r, c - r, c + r, c + r], outline=255, width=12)
guide = solid((255, 64, 64), ring.resize((S, S), Image.LANCZOS))
mono_dark = Image.alpha_composite(solid((0x26, 0x32, 0x2D), circle_mask(S)),
                                  Image.composite(solid((200, 230, 210)), Image.new("RGBA", (S, S), (0, 0, 0, 0)), mono.split()[3]))

# (name, image, visible), bottom → top
layers = [
    ("Tło – kwadrat (pełne płótno)", bg_square, False),
    ("Tło – koło", bg_circle, True),
    ("Motyw – książka i garnek", fg, True),
    ("Wersja monochromatyczna (podgląd)", mono_dark, False),
    ("Strefa bezpieczna 66 dp (pomocnicza)", guide, False),
]

def packbits_row(row):
    out = bytearray(); n = len(row); i = 0
    while i < n:
        j = i + 1
        while j < n and j - i < 128 and row[j] == row[i]:
            j += 1
        if j - i >= 3:
            out += bytes([257 - (j - i), row[i]]); i = j; continue
        j = i
        while j < n and j - i < 128:
            if j + 2 < n and row[j] == row[j + 1] == row[j + 2]:
                break
            j += 1
        out.append(j - i - 1); out += bytes(row[i:j]); i = j
    return bytes(out)

def rle_channel(ch):
    rows = [packbits_row(r.tobytes()) for r in ch]
    # Keep every channel's data even-length (valid alternative row encoding),
    # so readers that pad channels to 2 bytes (e.g. Pillow) stay aligned.
    if sum(len(r) for r in rows) % 2:
        for k, r in enumerate(ch):
            raw = r.tobytes()
            alt = bytes([0, raw[0]]) + packbits_row(raw[1:])
            if (len(alt) - len(rows[k])) % 2:
                rows[k] = alt
                break
    counts = b"".join(struct.pack(">H", len(r)) for r in rows)
    return counts, b"".join(rows)

def pascal(name, pad):
    b = name.encode("ascii", "replace")[:255]
    s = bytes([len(b)]) + b
    while len(s) % pad:
        s += b"\0"
    return s

def luni(name):
    u = name.encode("utf-16-be")
    data = struct.pack(">I", len(name)) + u
    if len(data) % 4:
        data += b"\0" * (4 - len(data) % 4)
    return b"8BIM" + b"luni" + struct.pack(">I", len(data)) + data

records, chandata = b"", b""
for name, img, visible in layers:
    a = np.array(img)
    bbox = img.getbbox() or (0, 0, 1, 1)
    l, t, r, b = bbox
    a = a[t:b, l:r]
    chans = [(0, a[..., 0]), (1, a[..., 1]), (2, a[..., 2]), (-1, a[..., 3])]
    rec = struct.pack(">iiiiH", t, l, b, r, 4)
    blobs = []
    for cid, ch in chans:
        counts, data = rle_channel(ch)
        blob = struct.pack(">H", 1) + counts + data
        blobs.append(blob)
        rec += struct.pack(">hI", cid, len(blob))
    flags = 0x08 | (0 if visible else 0x02)
    extra = struct.pack(">I", 0) + struct.pack(">I", 0) + pascal(name, 4) + luni(name)
    rec += b"8BIMnorm" + struct.pack(">BBBB", 255, 0, flags, 0) + struct.pack(">I", len(extra)) + extra
    records += rec
    chandata += b"".join(blobs)

layer_info = struct.pack(">h", len(layers)) + records + chandata
if len(layer_info) % 2:
    layer_info += b"\0"
layer_info = struct.pack(">I", len(layer_info)) + layer_info
lmi = layer_info + struct.pack(">I", 0)

# merged composite (visible layers over white)
comp = Image.new("RGBA", (S, S), (255, 255, 255, 255))
for _, img, vis_ in layers:
    if vis_:
        comp = Image.alpha_composite(comp, img)
ca = np.array(comp.convert("RGB"))
counts_all, data_all = b"", b""
for k in range(3):
    c_, d_ = rle_channel(ca[..., k]); counts_all += c_; data_all += d_

with open(out_psd, "wb") as f:
    f.write(b"8BPS" + struct.pack(">H", 1) + b"\0" * 6 + struct.pack(">HIIHH", 3, S, S, 8, 3))
    f.write(struct.pack(">I", 0))          # color mode data
    f.write(struct.pack(">I", 0))          # image resources
    f.write(struct.pack(">I", len(lmi)) + lmi)
    f.write(struct.pack(">H", 1) + counts_all + data_all)

out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
out = Image.alpha_composite(Image.alpha_composite(out, bg_circle), fg)
out.save(out_png)
print("ok")
