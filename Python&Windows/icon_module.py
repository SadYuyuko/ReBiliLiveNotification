from PIL import Image, ImageDraw


def create_android_icon(size=256):
    image = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)

    draw.ellipse([0, 0, size, size], fill='#FFFFFF')

    color = '#00A1D6'
    s = size / 108.0
    pts = [(36.2 * s, 30 * s), (81.8 * s, 54 * s), (36.2 * s, 78 * s)]
    cx_tri = sum(p[0] for p in pts) / 3
    cy_tri = sum(p[1] for p in pts) / 3
    ox = size / 2 - cx_tri
    oy = size / 2 - cy_tri
    draw.polygon([(x + ox, y + oy) for x, y in pts], fill=color)

    return image


def create_default_avatar(size=64):
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    bg_color = '#b0b0b0'
    draw.ellipse([0, 0, size, size], fill=bg_color)
    body_color = '#d0d0d0'
    cx, cy = size // 2, size // 2
    head_r = size * 0.22
    draw.ellipse([cx - head_r, cy - head_r - size * 0.08, cx + head_r, cy + head_r - size * 0.08], fill=body_color)
    shoulder_w = size * 0.55
    body_top = cy + size * 0.15
    body_bot = size * 1.15
    draw.ellipse([cx - shoulder_w, body_top, cx + shoulder_w, body_bot], fill=body_color)
    return img
