from PIL import Image, ImageDraw


def create_microphone_icon():
    size = 64
    image = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    color = '#00A1D6'

    mic_w = 20
    mic_h = 34
    mic_x = (size - mic_w) // 2
    mic_y = 6
    draw.ellipse([mic_x, mic_y, mic_x + mic_w, mic_y + mic_w], fill=color)
    draw.ellipse([mic_x, mic_y + mic_h - mic_w, mic_x + mic_w, mic_y + mic_h], fill=color)
    draw.rectangle([mic_x, mic_y + mic_w//2, mic_x + mic_w, mic_y + mic_h - mic_w//2], fill=color)

    arc_padding = 7
    arc_thickness = 5

    arc_box = [
        mic_x - arc_padding,
        mic_y + 16,
        mic_x + mic_w + arc_padding,
        mic_y + mic_h + 8
    ]

    draw.arc(arc_box, start=0, end=180, fill=color, width=arc_thickness)
    arc_cy = (arc_box[1] + arc_box[3]) / 2
    r_tip = arc_thickness / 2 - 0.5
    draw.ellipse([arc_box[0]-r_tip, arc_cy-r_tip, arc_box[0]+r_tip, arc_cy+r_tip], fill=color)
    draw.ellipse([arc_box[2]-r_tip, arc_cy-r_tip, arc_box[2]+r_tip, arc_cy+r_tip], fill=color)

    stem_h = 7
    stem_y_start = arc_box[3]
    draw.line([size//2, stem_y_start, size//2, stem_y_start + stem_h], fill=color, width=arc_thickness)

    base_y = stem_y_start + stem_h
    base_w = 24

    draw.line([size//2 - base_w//2, base_y, size//2 + base_w//2, base_y], fill=color, width=arc_thickness)
    draw.ellipse([size//2 - base_w//2 - r_tip, base_y - r_tip, size//2 - base_w//2 + r_tip, base_y + r_tip], fill=color)
    draw.ellipse([size//2 + base_w//2 - r_tip, base_y - r_tip, size//2 + base_w//2 + r_tip, base_y + r_tip], fill=color)

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
