#!/usr/bin/env python3
"""fork:screen-changelog - what changed on screen between two Nuvio builds.

The desktop tests already draw screens and save them as PNGs (composeApp/build/test-screenshots and a few
preview PNGs). CI collects them after every build. This script compares this build's screens with the
last build's and writes:

  reel.mp4           a short film: title card, then each changed screen going from before to after with
                     the changed areas ringed, then every new screen
  compare-<name>.png before | after, side by side, changes ringed (the release page shows these)
  changes.md         the summary: which screens changed, how much, which are new or gone
  summary.json       the same as data

Small differences (a clock that moved on a minute, anti-aliasing) don't count: a screen changes only when
enough 16 px cells changed. Noise in a cell is ignored.

usage: changelog.py --new DIR [--old DIR] --out DIR [--sha SHA] [--old-sha SHA] [--assets URL]
"""

import argparse
import json
import os
import subprocess
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

W, H = 1920, 1080
FPS = 30
CELL = 16
PIXEL_DELTA = 40        # a pixel differs when a channel moves this much (0-255)
CELL_SHARE = 0.06       # a cell differs when this share of its pixels differ
MIN_CHANGED = 0.004     # a screen changed when this share of its cells differ
ACCENT = (229, 57, 53)
INK = (11, 12, 18)
PAPER = (238, 240, 246)
DIM = (140, 146, 170)


def font(size, bold=False):
    names = (["DejaVuSans-Bold.ttf", "Arial Bold.ttf", "arialbd.ttf"] if bold else ["DejaVuSans.ttf", "Arial.ttf", "arial.ttf"])
    for n in names:
        for d in ["", "/usr/share/fonts/truetype/dejavu/", "C:/Windows/Fonts/"]:
            try:
                return ImageFont.truetype(d + n, size)
            except OSError:
                pass
    return ImageFont.load_default()


def pngs(d):
    if not d or not os.path.isdir(d):
        return {}
    return {os.path.splitext(f)[0]: os.path.join(d, f) for f in sorted(os.listdir(d)) if f.lower().endswith(".png")}


def pretty(name):
    return name.replace("-", " ").replace("_", " ").strip().capitalize()


# ---------- comparing ----------

def compare(a_path, b_path):
    """Changed-cell grid and the share of cells changed. The old image is fitted to the new one's size."""
    b = Image.open(b_path).convert("RGB")
    a = Image.open(a_path).convert("RGB")
    resized = a.size != b.size
    if resized:
        a = a.resize(b.size, Image.BILINEAR)
    A = np.asarray(a, dtype=np.int16)
    B = np.asarray(b, dtype=np.int16)
    px = (np.abs(A - B).max(axis=2) > PIXEL_DELTA)
    gh, gw = px.shape[0] // CELL, px.shape[1] // CELL
    if gh == 0 or gw == 0:
        return None, (1.0 if px.any() else 0.0), resized
    cells = px[: gh * CELL, : gw * CELL].reshape(gh, CELL, gw, CELL).mean(axis=(1, 3)) > CELL_SHARE
    share = float(cells.mean())
    if resized:
        share = max(share, MIN_CHANGED)
    return cells, share, resized


def boxes(cells, pad=1, gap=3):
    """Rectangles (in pixels) around clusters of changed cells; nearby clusters merge."""
    if cells is None:
        return []
    gh, gw = cells.shape
    seen = np.zeros_like(cells, dtype=bool)
    found = []
    for y in range(gh):
        for x in range(gw):
            if not cells[y, x] or seen[y, x]:
                continue
            stack = [(y, x)]
            seen[y, x] = True
            y0 = y1 = y
            x0 = x1 = x
            while stack:
                cy, cx = stack.pop()
                y0, y1, x0, x1 = min(y0, cy), max(y1, cy), min(x0, cx), max(x1, cx)
                for ny in range(max(0, cy - gap), min(gh, cy + gap + 1)):
                    for nx in range(max(0, cx - gap), min(gw, cx + gap + 1)):
                        if cells[ny, nx] and not seen[ny, nx]:
                            seen[ny, nx] = True
                            stack.append((ny, nx))
            found.append([max(0, x0 - pad), max(0, y0 - pad), min(gw, x1 + 1 + pad), min(gh, y1 + 1 + pad)])
    # Merge overlapping boxes until none overlap.
    merged = True
    while merged:
        merged = False
        out = []
        for b in found:
            for o in out:
                if b[0] <= o[2] and o[0] <= b[2] and b[1] <= o[3] and o[1] <= b[3]:
                    o[:] = [min(b[0], o[0]), min(b[1], o[1]), max(b[2], o[2]), max(b[3], o[3])]
                    merged = True
                    break
            else:
                out.append(b)
        found = out
    found.sort(key=lambda b: -(b[2] - b[0]) * (b[3] - b[1]))
    return [(x0 * CELL, y0 * CELL, x1 * CELL, y1 * CELL) for x0, y0, x1, y1 in found[:12]]


# ---------- drawing ----------

def fit(img, w, h, bg=INK):
    """The image scaled to fit w x h, centred, plus the scale and offset used."""
    s = min(w / img.width, h / img.height)
    nw, nh = max(1, int(img.width * s)), max(1, int(img.height * s))
    canvas = Image.new("RGB", (w, h), bg)
    ox, oy = (w - nw) // 2, (h - nh) // 2
    canvas.paste(img.resize((nw, nh), Image.LANCZOS), (ox, oy))
    return canvas, s, ox, oy


def ring(draw, rects, s, ox, oy, alpha=1.0, width=5):
    col = tuple(int(c * alpha + i * (1 - alpha)) for c, i in zip(ACCENT, INK))
    for x0, y0, x1, y1 in rects:
        draw.rounded_rectangle([ox + x0 * s - 4, oy + y0 * s - 4, ox + x1 * s + 4, oy + y1 * s + 4], radius=10, outline=col, width=width)


HEADER = 120


def frame_base(title, sub, tag=None):
    f = Image.new("RGB", (W, H), INK)
    d = ImageDraw.Draw(f)
    d.text((60, 30), title, font=font(46, True), fill=PAPER)
    d.text((60, 84), sub, font=font(24), fill=DIM)
    if tag:
        tw = d.textlength(tag, font=font(24, True))
        d.rounded_rectangle([W - 60 - tw - 36, 40, W - 60, 86], radius=23, fill=ACCENT)
        d.text((W - 60 - tw - 18, 49), tag, font=font(24, True), fill=PAPER)
    return f


def compose_screen(base, img, rects=None, ring_alpha=0.0, label=None):
    area_w, area_h = W - 120, H - HEADER - 40
    shot, s, ox, oy = fit(img, area_w, area_h)
    f = base.copy()
    f.paste(shot, (60, HEADER))
    d = ImageDraw.Draw(f)
    if rects and ring_alpha > 0:
        ring(d, rects, s, 60 + ox, HEADER + oy, ring_alpha)
    if label:
        d.rounded_rectangle([60 + ox + 16, HEADER + oy + 16, 60 + ox + 16 + d.textlength(label, font=font(22, True)) + 28, HEADER + oy + 56], radius=12, fill=(0, 0, 0))
        d.text((60 + ox + 30, HEADER + oy + 22), label, font=font(22, True), fill=PAPER)
    return f


def wipe(a_frame, b_frame, t):
    """b revealed left to right over a, with a bright edge."""
    x = int(W * t)
    f = a_frame.copy()
    if x > 0:
        f.paste(b_frame.crop((0, HEADER, x, H)), (0, HEADER))
        ImageDraw.Draw(f).rectangle([x - 2, HEADER, x + 2, H], fill=ACCENT)
    return f


def ease(t):
    return t * t * (3 - 2 * t)


class Reel:
    def __init__(self, path):
        self.p = subprocess.Popen(
            ["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
             "-c:v", "libx264", "-preset", "veryfast", "-crf", "23", "-pix_fmt", "yuv420p", "-movflags", "+faststart", path],
            stdin=subprocess.PIPE,
        )
        self.frames = 0

    def hold(self, img, seconds):
        b = img.tobytes()
        for _ in range(int(seconds * FPS)):
            self.p.stdin.write(b)
            self.frames += 1

    def put(self, img):
        self.p.stdin.write(img.tobytes())
        self.frames += 1

    def close(self):
        self.p.stdin.close()
        if self.p.wait() != 0:
            raise SystemExit("ffmpeg failed")


def card(lines, big):
    f = Image.new("RGB", (W, H), INK)
    d = ImageDraw.Draw(f)
    d.rectangle([0, 0, 18, H], fill=ACCENT)
    d.text((120, 300), big, font=font(84, True), fill=PAPER)
    y = 430
    for i, line in enumerate(lines):
        d.text((124, y), line, font=font(34 if i == 0 else 28), fill=PAPER if i == 0 else DIM)
        y += 56 if i == 0 else 44
    return f


# ---------- main ----------

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--new", required=True)
    ap.add_argument("--old")
    ap.add_argument("--out", required=True)
    ap.add_argument("--sha", default="")
    ap.add_argument("--old-sha", default="")
    ap.add_argument("--assets", default="", help="URL prefix the release assets will live at, for images in changes.md")
    ap.add_argument("--no-video", action="store_true")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)

    new, old = pngs(a.new), pngs(a.old)
    first = not old
    changed, same, added = [], [], []
    removed = sorted(set(old) - set(new))
    for name in new:
        if name not in old:
            added.append(name)
            continue
        cells, share, resized = compare(old[name], new[name])
        if share >= MIN_CHANGED:
            changed.append({"name": name, "share": share, "boxes": boxes(cells), "resized": resized})
        else:
            same.append(name)
    changed.sort(key=lambda c: -c["share"])

    sha, osha = a.sha[:7], a.old_sha[:7]

    # Side-by-side compares for the release page.
    for c in changed:
        before, after = Image.open(old[c["name"]]).convert("RGB"), Image.open(new[c["name"]]).convert("RGB")
        cw, ch = 1200, int(1200 * after.height / max(1, after.width))
        sheet = Image.new("RGB", (cw * 2 + 60, ch + 90), INK)
        d = ImageDraw.Draw(sheet)
        b_img, _, bx, by = fit(before, cw, ch)
        a_img, s, ax, ay = fit(after, cw, ch)
        sheet.paste(b_img, (20, 70))
        sheet.paste(a_img, (cw + 40, 70))
        ring(ImageDraw.Draw(sheet), c["boxes"], s, cw + 40 + ax, 70 + ay, width=4)
        d.text((20, 18), f"Before {osha}".strip(), font=font(30, True), fill=DIM)
        d.text((cw + 40, 18), f"After {sha} · {c['share'] * 100:.1f}% changed".strip(), font=font(30, True), fill=PAPER)
        c["compare"] = f"compare-{c['name']}.png"
        sheet.save(os.path.join(a.out, c["compare"]), optimize=True)

    # The summary.
    md = []
    if first:
        md.append(f"### Screens: first gallery, {len(new)} screens")
        md.append("")
        md.append("Nothing to compare yet. From the next build on, this lists what changed on screen.")
    else:
        md.append(f"### Screens: {len(changed)} changed, {len(added)} new, {len(removed)} gone, {len(same)} the same")
        md.append("")
        md.append(f"Build `{sha}` against `{osha}`." + (" Watch the reel: [reel.mp4](" + a.assets + "reel.mp4)" if a.assets and (changed or added) else ""))
        for c in changed:
            md.append("")
            md.append(f"**{pretty(c['name'])}**: {c['share'] * 100:.1f}% of the screen" + (" (size changed)" if c["resized"] else ""))
            if a.assets:
                md.append(f"![{c['name']}]({a.assets}{c['compare']})")
        if added:
            md.append("")
            md.append("**New:** " + ", ".join(pretty(n) for n in added))
        if removed:
            md.append("")
            md.append("**Gone:** " + ", ".join(pretty(n) for n in removed))
        if same:
            md.append("")
            md.append(f"<details><summary>The same ({len(same)})</summary>\n\n" + ", ".join(pretty(n) for n in same) + "\n</details>")
    with open(os.path.join(a.out, "changes.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(md) + "\n")
    with open(os.path.join(a.out, "summary.json"), "w", encoding="utf-8") as f:
        json.dump({"sha": a.sha, "oldSha": a.old_sha, "first": first,
                   "changed": [{k: v for k, v in c.items() if k != "boxes"} | {"boxes": len(c["boxes"])} for c in changed],
                   "added": added, "removed": removed, "same": same}, f, indent=1)

    # The reel.
    if a.no_video or (not changed and not added):
        print("\n".join(md))
        return
    reel = Reel(os.path.join(a.out, "reel.mp4"))
    title = "First look" if first else f"{len(changed)} screen{'s' if len(changed) != 1 else ''} changed"
    lines = [f"Nuvio · Spyder {sha}".strip()]
    if not first:
        lines.append(f"since {osha}" + (f" · {len(added)} new" if added else "") + (f" · {len(removed)} gone" if removed else ""))
    lines += [pretty(c["name"]) for c in changed[:5]]
    reel.hold(card(lines, title), 2.2)

    for i, c in enumerate(changed):
        before, after = Image.open(old[c["name"]]).convert("RGB"), Image.open(new[c["name"]]).convert("RGB")
        base = frame_base(pretty(c["name"]), f"{c['share'] * 100:.1f}% of the screen changed · {i + 1} of {len(changed)}", "CHANGED")
        fa = compose_screen(base, before, label="BEFORE")
        fb = compose_screen(base, after, label="AFTER")
        reel.hold(fa, 1.0)
        n = int(0.9 * FPS)
        for k in range(n):
            reel.put(wipe(fa, fb, ease((k + 1) / n)))
        for k in range(int(0.5 * FPS)):
            reel.put(compose_screen(base, after, c["boxes"], ease((k + 1) / (0.5 * FPS)), label="AFTER"))
        ringed = compose_screen(base, after, c["boxes"], 1.0, label="AFTER")
        reel.hold(ringed, 1.4)
        # Flick back and forth once so the change is unmistakable.
        reel.hold(compose_screen(base, before, c["boxes"], 1.0, label="BEFORE"), 0.45)
        reel.hold(ringed, 0.9)

    for name in added[:20]:
        img = Image.open(new[name]).convert("RGB")
        base = frame_base(pretty(name), "first time in the gallery" if first else "new screen", "NEW")
        f = compose_screen(base, img)
        black = base.copy()
        n = int(0.5 * FPS)
        for k in range(n):
            reel.put(Image.blend(black, f, ease((k + 1) / n)))
        reel.hold(f, 1.6)

    reel.hold(card([f"{len(changed)} changed · {len(added)} new · {len(removed)} gone · {len(same)} the same"], "That's the build"), 1.6)
    reel.close()
    print("\n".join(md))
    print(f"reel: {reel.frames / FPS:.1f}s")


if __name__ == "__main__":
    sys.exit(main())
