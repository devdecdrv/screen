import os
from PIL import Image, ImageDraw

sizes = {
    "mipmap-mdpi":    48,
    "mipmap-hdpi":    72,
    "mipmap-xhdpi":   96,
    "mipmap-xxhdpi":  144,
    "mipmap-xxxhdpi": 192,
}
base = "dec_remote/android_app/app/src/main/res"
for folder, size in sizes.items():
    os.makedirs(os.path.join(base, folder), exist_ok=True)
    img = Image.new("RGBA", (size, size), (13, 17, 23, 255))
    draw = ImageDraw.Draw(img)
    m = size // 8
    draw.ellipse([m, m, size - m, size - m], fill=(88, 166, 255, 255))
    for name in ["ic_launcher.png", "ic_launcher_round.png"]:
        img.save(os.path.join(base, folder, name))
print("Icons generated")
