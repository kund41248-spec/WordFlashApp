# -*- coding: utf-8 -*-
"""
生成应用图标 PNG（各密度）。

为什么需要这个：
  Android 8.0+ 用自适应图标（mipmap-anydpi-v26/ic_launcher.xml），
  但 minSdk=24，Android 7 需要传统的位图图标。
  虽然有矢量方案，但部分启动器对 mipmap 中的矢量图支持不佳，
  因此按 Android Studio 的标准做法生成各密度 PNG 最稳妥。

设计：品牌蓝渐变圆角方块 + 白色书页 + 青绿记忆点
"""
import os
import sys
from PIL import Image, ImageDraw

OUT_ROOT = sys.argv[1]

# 各密度对应的图标边长（px）
DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

# 品牌配色
BLUE_TOP = (0x5B, 0x7B, 0xF5)
BLUE_BOTTOM = (0x3A, 0x52, 0xC9)
WHITE = (0xFF, 0xFF, 0xFF)
SPINE = (0x4F, 0x6B, 0xED)
LINE_STRONG = (0x4F, 0x6B, 0xED)
LINE_LIGHT = (0x9A, 0xA8, 0xF0)
GREEN = (0x00, 0xA8, 0x8C)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def gradient_square(size, radius_ratio):
    """生成对角渐变的圆角方块"""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    # 先铺对角渐变
    for y in range(size):
        for x in range(size):
            t = (x + y) / (2.0 * (size - 1)) if size > 1 else 0.0
            px[x, y] = lerp(BLUE_TOP, BLUE_BOTTOM, t) + (255,)

    # 再用圆角矩形 mask 裁切
    mask = Image.new("L", (size, size), 0)
    md = ImageDraw.Draw(mask)
    r = int(size * radius_ratio)
    md.rounded_rectangle([0, 0, size - 1, size - 1], radius=r, fill=255)

    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


def gradient_circle(size, inset_ratio=0.0):
    """生成对角渐变的圆形（用于 round 图标）"""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    for y in range(size):
        for x in range(size):
            t = (x + y) / (2.0 * (size - 1)) if size > 1 else 0.0
            px[x, y] = lerp(BLUE_TOP, BLUE_BOTTOM, t) + (255,)

    mask = Image.new("L", (size, size), 0)
    md = ImageDraw.Draw(mask)
    inset = int(size * inset_ratio)
    md.ellipse([inset, inset, size - 1 - inset, size - 1 - inset], fill=255)

    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


def draw_book(img, size, scale=1.0):
    """
    在图标中央画书页。
    所有坐标按 108 视口设计，再按 size/108 缩放。
    """
    d = ImageDraw.Draw(img)
    u = size / 108.0 * scale      # 单位换算

    def s(v):
        return v * u

    # 为了让内容居中，计算缩放带来的偏移
    off = (size - size * scale) / 2.0

    def X(v):
        return off + s(v)

    # 书页（圆角矩形）
    d.rounded_rectangle(
        [X(32), X(30), X(76), X(78)],
        radius=max(1, int(s(4))),
        fill=WHITE,
    )
    # 书脊
    d.rectangle([X(40), X(30), X(43), X(78)], fill=SPINE)
    # 三行文字示意
    d.rectangle([X(48), X(42), X(70), X(45)], fill=LINE_STRONG)
    d.rectangle([X(48), X(51), X(66), X(54)], fill=LINE_LIGHT)
    d.rectangle([X(48), X(60), X(62), X(63)], fill=LINE_LIGHT)
    # 记忆曲线点缀
    r = s(5)
    cx, cy = X(62), X(68)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=GREEN)

    return img


def build(size, round_icon=False):
    if round_icon:
        base = gradient_circle(size)
    else:
        base = gradient_square(size, radius_ratio=0.22)

    # 圆形图标内容略小一点，避免贴边
    scale = 0.82 if round_icon else 0.92
    return draw_book(base, size, scale=scale)


def main():
    created = 0
    for density, size in DENSITIES.items():
        folder = os.path.join(OUT_ROOT, f"mipmap-{density}")
        os.makedirs(folder, exist_ok=True)

        square = build(size, round_icon=False)
        square.save(os.path.join(folder, "ic_launcher.png"), "PNG", optimize=True)

        round_img = build(size, round_icon=True)
        round_img.save(os.path.join(folder, "ic_launcher_round.png"), "PNG", optimize=True)

        created += 2
        print(f"  mipmap-{density:8s} {size:3d}x{size:<3d}  ic_launcher.png + ic_launcher_round.png")

    print(f"\n共生成 {created} 个 PNG（{len(DENSITIES)} 个密度 x 2 种形状）")


if __name__ == "__main__":
    main()
