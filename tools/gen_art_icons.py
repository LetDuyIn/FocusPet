"""
Sinh bộ icon vẽ tay (vector drawable) cho FocusPet.

Mỗi icon là một danh sách lớp (layer): (màu tô, đường path, màu viền, độ dày viền, fillType).
Chạy lại script này sau khi sửa hình:  python3 tools/gen_art_icons.py
File được ghi vào app/src/main/res/drawable/art_*.xml (và các icon tab, nút, thông báo, icon app).
"""
import os

OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "drawable")

# Bảng màu của app
PEACH, ORANGE, YELLOW, GOLD = "#FF9F6B", "#F97316", "#FFC93C", "#E0A100"
MINT, GREEN, SKY, SKY_DARK, SKY_LIGHT = "#6BCB9F", "#3FAE7A", "#38BDF8", "#0EA5E9", "#BAE6FD"
INDIGO, PINK, PINK_LIGHT, BROWN, CHOCO = "#818CF8", "#FF7A8A", "#FFD3BF", "#D9A066", "#7C4A2D"
CREAM, WHITE, DARK, GRAY, GRAY_LIGHT = "#FFF8F0", "#FFFFFF", "#3D3A50", "#9CA3AF", "#C9C6D6"


def circle(cx, cy, r):
    n = lambda v: f"{round(v, 3):g}"
    return f"M{n(cx - r)},{n(cy)} a{n(r)},{n(r)} 0 1,0 {n(2 * r)},0 a{n(r)},{n(r)} 0 1,0 {n(-2 * r)},0 Z"


def L(path, fill=None, stroke=None, width=0, even_odd=False, alpha=None):
    return dict(path=path, fill=fill, stroke=stroke, width=width, even_odd=even_odd, alpha=alpha)


CAT_HEAD = ("M5,2.5 L9.2,6.6 C10.1,6.4 11,6.3 12,6.3 C13,6.3 13.9,6.4 14.8,6.6 L19,2.5 "
            "C19.6,4.6 19.9,6.9 19.6,9 C20.9,10.4 21.5,12.1 21.5,14 C21.5,18.4 17.3,21.5 12,21.5 "
            "C6.7,21.5 2.5,18.4 2.5,14 C2.5,12.1 3.1,10.4 4.4,9 C4.1,6.9 4.4,4.6 5,2.5 Z")
CAT_EARS = ("M6,4.6 L8.3,6.9 C7.4,7.2 6.6,7.6 5.9,8.1 C5.8,6.9 5.8,5.7 6,4.6 Z "
            "M18,4.6 L15.7,6.9 C16.6,7.2 17.4,7.6 18.1,8.1 C18.2,6.9 18.2,5.7 18,4.6 Z")
CAT_NOSE = "M10.9,15.6 L13.1,15.6 L12,16.9 Z"
HEART = ("M12,20.5 C11.6,20.5 11.2,20.3 10.9,20.1 C6.6,16.6 2.5,13.3 2.5,8.9 C2.5,5.9 4.8,3.6 7.6,3.6 "
         "C9.4,3.6 11,4.5 12,5.9 C13,4.5 14.6,3.6 16.4,3.6 C19.2,3.6 21.5,5.9 21.5,8.9 "
         "C21.5,13.3 17.4,16.6 13.1,20.1 C12.8,20.3 12.4,20.5 12,20.5 Z")
PAW_PAD = ("M12,11 C15,11 18,14.3 18,16.9 C18,19 16.5,20.2 14.6,19.9 C13.6,19.7 12.9,19.3 12,19.3 "
           "C11.1,19.3 10.4,19.7 9.4,19.9 C7.5,20.2 6,19 6,16.9 C6,14.3 9,11 12,11 Z")
PAW_TOES = " ".join([circle(5, 10, 2), circle(9.2, 6, 2.2), circle(14.8, 6, 2.2), circle(19, 10, 2)])

ICONS = {
    # Xu: đồng vàng in hình dấu chân mèo
    "art_coin": [
        L(circle(12, 12, 10), GOLD),
        L(circle(12, 12, 8.6), YELLOW),
        L("M12,12.2 C13.9,12.2 15.4,13.9 15.4,15.3 C15.4,16.4 14.5,16.9 13.5,16.7 C12.9,16.6 12.5,16.4 12,16.4 "
          "C11.5,16.4 11.1,16.6 10.5,16.7 C9.5,16.9 8.6,16.4 8.6,15.3 C8.6,13.9 10.1,12.2 12,12.2 Z", GOLD),
        L(" ".join([circle(8.4, 10.6, 1.1), circle(10.6, 8.6, 1.2), circle(13.4, 8.6, 1.2), circle(15.6, 10.6, 1.1)]), GOLD),
    ],
    # Cửa hàng: túi mua sắm
    "art_bag": [
        L("M5.2,7.5 H18.8 L19.8,19.4 C19.9,20.8 18.8,22 17.4,22 H6.6 C5.2,22 4.1,20.8 4.2,19.4 Z", PEACH),
        L("M8.5,9 V6.8 C8.5,4.8 10.1,3.3 12,3.3 C13.9,3.3 15.5,4.8 15.5,6.8 V9", stroke=DARK, width=1.6),
        L(circle(8.5, 9, 0.9) + " " + circle(15.5, 9, 0.9), DARK),
        L("M8,14 C9.2,15.6 14.8,15.6 16,14", stroke=CREAM, width=1.4),
    ],
    # Chuỗi ngày: ngọn lửa
    "art_flame": [
        L("M12,2 C12.8,5.4 17.6,7.9 18.6,12.6 C19.6,17.4 16.4,22 12,22 C7.6,22 4.6,18.6 5.2,14.3 "
          "C5.6,11.6 7.3,10 8.2,8.4 C8.6,10.4 9.4,11.3 10.3,11.6 C9.8,8.2 10.8,4.6 12,2 Z", ORANGE),
        L("M12.3,10.5 C13.2,12.6 15.6,14 15.6,16.8 C15.6,19.1 14,20.6 12,20.6 C10,20.6 8.5,19.2 8.6,17.2 "
          "C8.7,15.7 9.6,14.8 10.2,14.1 C10.5,15.1 11,15.6 11.6,15.8 C11.2,14 11.5,12.1 12.3,10.5 Z", YELLOW),
    ],
    # Thời gian tập trung: chồng sách
    "art_books": [
        L("M3.5,15 H19.5 C20.3,15 21,15.7 21,16.5 V19.5 C21,20.3 20.3,21 19.5,21 H3.5 Z", INDIGO),
        L("M17.5,16.2 H20.2 V19.8 H17.5 Z", CREAM),
        L("M4.5,9.2 H19 C19.8,9.2 20.5,9.9 20.5,10.7 V13.5 C20.5,14.3 19.8,15 19,15 H4.5 Z", PEACH),
        L("M17,10.4 H19.7 V13.8 H17 Z", CREAM),
        L("M6,3.5 H18 C18.8,3.5 19.5,4.2 19.5,5 V7.7 C19.5,8.5 18.8,9.2 18,9.2 H6 Z", MINT),
        L("M16,4.7 H18.7 V8 H16 Z", CREAM),
    ],
    # Hoàn thành: ô vuông xanh có dấu tick
    "art_check": [
        L("M6,2.5 H18 C19.9,2.5 21.5,4.1 21.5,6 V18 C21.5,19.9 19.9,21.5 18,21.5 H6 C4.1,21.5 2.5,19.9 2.5,18 "
          "V6 C2.5,4.1 4.1,2.5 6,2.5 Z", MINT),
        L("M7,12.3 L10.4,15.6 L17,8.6", stroke=WHITE, width=2.6),
    ],
    # Phiên tập trung: đồng hồ bấm giờ
    "art_timer": [
        L("M10,1.8 H14 C14.6,1.8 15,2.2 15,2.8 C15,3.4 14.6,3.8 14,3.8 H10 C9.4,3.8 9,3.4 9,2.8 "
          "C9,2.2 9.4,1.8 10,1.8 Z", PEACH),
        L("M11.2,3.6 H12.8 V5.6 H11.2 Z", DARK),
        L(circle(12, 13.5, 8), CREAM, DARK, 1.8),
        L("M12,13.5 V9.2 M12,13.5 L14.8,15.2", stroke=ORANGE, width=1.8),
        L(circle(12, 13.5, 1), DARK),
    ],
    # Thống kê: biểu đồ cột
    "art_chart": [
        L("M4,13 C4,12.4 4.4,12 5,12 H7 C7.6,12 8,12.4 8,13 V20 H4 Z", MINT),
        L("M10,7 C10,6.4 10.4,6 11,6 H13 C13.6,6 14,6.4 14,7 V20 H10 Z", PEACH),
        L("M16,10 C16,9.4 16.4,9 17,9 H19 C19.6,9 20,9.4 20,10 V20 H16 Z", INDIGO),
        L("M2.5,20 H21.5 V21.6 H2.5 Z", DARK),
    ],
    # Lấp lánh
    "art_sparkle": [
        L("M10,3 C10.6,7.4 12.6,9.4 17,10 C12.6,10.6 10.6,12.6 10,17 C9.4,12.6 7.4,10.6 3,10 "
          "C7.4,9.4 9.4,7.4 10,3 Z", YELLOW),
        L("M18,13 C18.3,15.2 19.3,16.2 21.5,16.5 C19.3,16.8 18.3,17.8 18,20 C17.7,17.8 16.7,16.8 14.5,16.5 "
          "C16.7,16.2 17.7,15.2 18,13 Z", PEACH),
    ],
    # No bụng / đồ ăn: con cá
    "art_fish": [
        L("M11,7 C12,5 13.8,4.3 15.6,4.6 C14.6,5.5 14.2,6.5 14.2,7.3 Z", SKY_DARK),
        L("M3,12 C5.5,7.5 10,6 14,6.8 C16.6,7.3 18.4,9 19.3,10.3 L22,7.5 V16.5 L19.3,13.7 "
          "C18.4,15 16.6,16.7 14,17.2 C10,18 5.5,16.5 3,12 Z", SKY),
        L("M5.5,13.2 C8,15.6 11.5,16.3 14.6,15.5 C12,15 8.4,14.6 5.5,13.2 Z", SKY_LIGHT),
        L(circle(7.6, 11, 1.1), DARK),
    ],
    # Vui vẻ: trái tim
    "art_heart": [
        L(HEART, PINK),
        L("M6.2,7.6 C6.4,6.4 7.4,5.6 8.5,5.7", stroke=WHITE, width=1.3, alpha=0.8),
    ],
    "art_heart_white": [L(HEART, WHITE, GRAY_LIGHT, 1.4)],
    "art_heart_broken": [
        L(HEART, PINK),
        L("M12,5.9 L10.6,9.6 L13.2,12 L11,15.4 L12.2,20.2", stroke=CREAM, width=1.6),
    ],
    # Sức lực: tia sét
    "art_bolt": [L("M13.5,2 L5,13.5 H11 L9.8,22 L19,10 H13 Z", YELLOW, GOLD, 1)],
    # Sạch sẽ: giọt nước
    "art_drop": [
        L("M12,2.5 C12,2.5 5.5,10 5.5,14.5 C5.5,18.4 8.4,21.5 12,21.5 C15.6,21.5 18.5,18.4 18.5,14.5 "
          "C18.5,10 12,2.5 12,2.5 Z", SKY),
        L("M9.2,14.2 C9.2,12.6 10,11.3 10.8,10.3", stroke=WHITE, width=1.4, alpha=0.9),
    ],
    # EXP: ngôi sao
    "art_star": [L("M12,2.8 L14.7,8.6 L21,9.3 L16.3,13.6 L17.6,19.9 L12,16.7 L6.4,19.9 L7.7,13.6 "
                   "L3,9.3 L9.3,8.6 Z", YELLOW, "#F59E0B", 1.2)],
    # Mèo vui
    "art_cat": [
        L(CAT_HEAD, PEACH),
        L(CAT_EARS, PINK_LIGHT),
        L(circle(8.8, 13.3, 1.3) + " " + circle(15.2, 13.3, 1.3), DARK),
        L(circle(9.3, 12.8, 0.4) + " " + circle(15.7, 12.8, 0.4), WHITE),
        L(circle(6.4, 16, 1.1) + " " + circle(17.6, 16, 1.1), PINK, alpha=0.6),
        L(CAT_NOSE, PINK),
        L("M12,16.9 C11.8,17.9 10.7,18.1 10.2,17.4 M12,16.9 C12.2,17.9 13.3,18.1 13.8,17.4", stroke=DARK, width=0.7),
    ],
    # Mèo buồn / giận
    "art_cat_sad": [
        L(CAT_HEAD, PEACH),
        L(CAT_EARS, PINK_LIGHT),
        L("M7.5,12.6 L10,13.6 M16.5,12.6 L14,13.6", stroke=DARK, width=1.2),
        L(circle(8.8, 14.4, 1) + " " + circle(15.2, 14.4, 1), DARK),
        L(CAT_NOSE, PINK),
        L("M10,19 C10.8,17.8 13.2,17.8 14,19", stroke=DARK, width=0.8),
        L("M17.2,15.6 C17.2,15.6 16.2,17 16.2,17.7 C16.2,18.3 16.6,18.7 17.2,18.7 C17.8,18.7 18.2,18.3 18.2,17.7 "
          "C18.2,17 17.2,15.6 17.2,15.6 Z", SKY),
    ],
    # Buồn ngủ: trăng
    "art_moon": [
        L("M15.5,3 C11.2,3.6 8,7.4 8,12 C8,16.6 11.2,20.4 15.5,21 C14.4,21.3 13.2,21.5 12,21.5 "
          "C6.8,21.5 2.5,17.2 2.5,12 C2.5,6.8 6.8,2.5 12,2.5 C13.2,2.5 14.4,2.7 15.5,3 Z", YELLOW),
        L(circle(19, 7, 1) + " " + circle(20, 13, 0.7), YELLOW),
        L("M16.5,15.5 H20 L16.5,19 H20", stroke=INDIGO, width=1.1),
    ],
    "art_paw": [L(PAW_PAD, PEACH), L(PAW_TOES, PEACH)],
    # Việc lặp lại
    "art_repeat": [
        L("M5,11 C5,7.7 7.7,5 11,5 H16.5", stroke=MINT, width=2.2),
        L("M15.5,2 L20,5 L15.5,8 Z", MINT),
        L("M19,13 C19,16.3 16.3,19 13,19 H7.5", stroke=MINT, width=2.2),
        L("M8.5,16 L4,19 L8.5,22 Z", MINT),
    ],
    "art_lock": [
        L("M8,10.5 V7.5 C8,5.3 9.8,3.5 12,3.5 C14.2,3.5 16,5.3 16,7.5 V10.5", stroke=GRAY, width=2.2),
        L("M6,10 H18 C19.1,10 20,10.9 20,12 V19.5 C20,20.6 19.1,21.5 18,21.5 H6 C4.9,21.5 4,20.6 4,19.5 "
          "V12 C4,10.9 4.9,10 6,10 Z", YELLOW),
        L(circle(12, 14.6, 1.5) + " M11.3,15 H12.7 L13,18.2 H11 Z", DARK),
    ],
    # Ăn mừng
    "art_party": [
        L("M3,21 L8.5,7.5 L16.5,15.5 Z", PEACH),
        L("M5.4,15.1 L11.6,10.6 L13.2,12.2 L6.6,17.6 Z", YELLOW),
        L(circle(15, 4, 1.1), INDIGO), L(circle(19.5, 8, 1), MINT),
        L(circle(13, 8.2, 0.8), PINK), L(circle(20, 14, 1.1), YELLOW),
        L("M10.5,4.5 C11.5,2.5 13.5,3.5 12.5,5.5 M17,10 C19,9.5 19.5,11.5 21.5,10.5", stroke=SKY, width=1.2),
    ],
    "art_cookie": [
        L(circle(12, 12, 9.5), BROWN),
        L(" ".join([circle(8.5, 9, 1.3), circle(14.5, 8, 1.1), circle(10.5, 14.5, 1.3),
                    circle(15.5, 14, 1.2), circle(12, 11.3, 0.8), circle(7.5, 13.2, 0.7)]), CHOCO),
    ],
    "art_leaf": [
        L("M20,4 C11,4 4.5,9 4.5,16.5 C4.5,17.6 4.7,18.6 5,19.5 C6,19.8 7,20 8,20 C15.5,20 20,13 20,4 Z", MINT),
        L("M4,20.5 C8,15 12,11 16.5,7.5", stroke=GREEN, width=1.4),
    ],
    "art_sprout": [
        L("M12,21 V11", stroke=GREEN, width=1.8),
        L("M12,12 C12,8 9,5.5 4.5,5.5 C4.5,9.5 7.5,12 12,12 Z", MINT),
        L("M12,10 C12,6 14.8,3.5 19.5,3.5 C19.5,7.5 16.5,10 12,10 Z", MINT),
        L("M6,21 C6,19.3 8.7,18 12,18 C15.3,18 18,19.3 18,21 Z", BROWN),
    ],
    "art_bottle": [
        L("M10.5,2.5 H15.5 V4 H13 V6 H11 V4 H10.5 Z", GRAY),
        L("M10,6 H14 V8 H10 Z", INDIGO),
        L("M8,8 H16 C17.1,8 18,8.9 18,10 V19.5 C18,20.6 17.1,21.5 16,21.5 H8 C6.9,21.5 6,20.6 6,19.5 V10 "
          "C6,8.9 6.9,8 8,8 Z", SKY),
        L("M8,12 H16 V17 H8 Z", CREAM),
        L("M12,12.8 C12,12.8 10.6,14.4 10.6,15.2 C10.6,15.9 11.2,16.4 12,16.4 C12.8,16.4 13.4,15.9 13.4,15.2 "
          "C13.4,14.4 12,12.8 12,12.8 Z", SKY),
    ],
    "art_cloud": [
        L("M7,19 C4.5,19 2.5,17.1 2.5,14.7 C2.5,12.5 4.2,10.7 6.4,10.5 C7,7.6 9.5,5.5 12.5,5.5 "
          "C15.6,5.5 18.1,7.8 18.5,10.8 C20.3,11.2 21.5,12.8 21.5,14.7 C21.5,17.1 19.6,19 17.2,19 Z",
          WHITE, SKY, 1.4),
    ],
    "art_sunset": [
        L("M5,16 C5,12.1 8.1,9 12,9 C15.9,9 19,12.1 19,16 Z", ORANGE),
        L("M12,3.5 V6 M5.6,6.6 L7.3,8.3 M18.4,6.6 L16.7,8.3 M2.5,13 H4.5 M19.5,13 H21.5", stroke=YELLOW, width=1.6),
        L("M2.5,18.5 H21.5 M5.5,21 H18.5", stroke=PEACH, width=1.6),
    ],
    "art_clipboard": [
        L("M6,4 H18 C19.1,4 20,4.9 20,6 V20 C20,21.1 19.1,22 18,22 H6 C4.9,22 4,21.1 4,20 V6 C4,4.9 4.9,4 6,4 Z", PEACH),
        L("M6.5,6.5 H17.5 V19.5 H6.5 Z", CREAM),
        L("M9,2.5 H15 V6 H9 Z", DARK),
        L("M9,10 H15.5 M9,13 H15.5 M9,16 H13.5", stroke=GRAY_LIGHT, width=1.4),
    ],
    "art_trophy": [
        L("M7,5 H4.5 V7 C4.5,8.9 5.8,10 7.4,10 M17,5 H19.5 V7 C19.5,8.9 18.2,10 16.6,10", stroke=GOLD, width=1.6),
        L("M7,3 H17 V9 C17,11.8 14.8,14 12,14 C9.2,14 7,11.8 7,9 Z", YELLOW),
        L("M11,14 H13 V17.5 H11 Z", GOLD),
        L("M7.5,18 H16.5 C17.1,18 17.5,18.4 17.5,19 V21 H6.5 V19 C6.5,18.4 6.9,18 7.5,18 Z", BROWN),
        L("M12,5.2 L12.8,7 L14.6,7.2 L13.2,8.4 L13.6,10.2 L12,9.2 L10.4,10.2 L10.8,8.4 L9.4,7.2 L11.2,7 Z", WHITE),
    ],
    "art_bulb": [
        L("M12,2.5 C15.9,2.5 19,5.5 19,9.3 C19,11.8 17.6,13.6 16,15 C15.4,15.5 15,16.2 15,17 H9 "
          "C9,16.2 8.6,15.5 8,15 C6.4,13.6 5,11.8 5,9.3 C5,5.5 8.1,2.5 12,2.5 Z", YELLOW),
        L("M9,17.8 H15 V19.3 C15,20.5 14,21.5 12.8,21.5 H11.2 C10,21.5 9,20.5 9,19.3 Z", GRAY),
        L("M8.3,9 C8.3,7 9.8,5.6 11.5,5.4", stroke=WHITE, width=1.2),
    ],
    "art_warning": [
        L("M12,3 L22,20.5 H2 Z", YELLOW, GOLD, 1),
        L("M11,9 H13 L12.6,15 H11.4 Z " + circle(12, 17.6, 1.1), DARK),
    ],
}

# Icon một màu (được tô màu bằng tint): tab, nút xóa, thông báo
MONO = {
    "ic_nav_pet": [L(CAT_HEAD + " " + circle(8.8, 13.2, 1.3) + " " + circle(15.2, 13.2, 1.3) + " " + CAT_NOSE,
                     "#FF000000", even_odd=True)],
    "ic_nav_tasks": [L("M6,4 H8 V3.5 C8,2.7 8.7,2 9.5,2 H14.5 C15.3,2 16,2.7 16,3.5 V4 H18 C19.1,4 20,4.9 20,6 "
                       "V20 C20,21.1 19.1,22 18,22 H6 C4.9,22 4,21.1 4,20 V6 C4,4.9 4.9,4 6,4 Z "
                       "M8.2,13.2 L9.6,11.8 L11,13.2 L15.4,8.8 L16.8,10.2 L11,16 Z", "#FF000000", even_odd=True)],
    "ic_nav_focus": [L("M10,1.5 H14 V3.4 H10 Z M11.2,3.4 H12.8 V5.5 H11.2 Z "
                       + circle(12, 13.5, 8) + " " + circle(12, 13.5, 6.2)
                       + " M11.2,9 H12.8 V13.1 L15.3,14.6 L14.5,16 L11.2,14 Z", "#FF000000", even_odd=True)],
    "ic_nav_stats": [L("M3.5,12.5 C3.5,11.7 4.2,11 5,11 H6.5 C7.3,11 8,11.7 8,12.5 V20 H3.5 Z "
                       "M9.75,6.5 C9.75,5.7 10.4,5 11.25,5 H12.75 C13.6,5 14.25,5.7 14.25,6.5 V20 H9.75 Z "
                       "M16,10 C16,9.2 16.7,8.5 17.5,8.5 H19 C19.8,8.5 20.5,9.2 20.5,10 V20 H16 Z "
                       "M2.5,20.5 H21.5 V22 H2.5 Z", "#FF000000")],
    "ic_delete": [L("M9,3 H15 L15.5,4.5 H20 V6.5 H4 V4.5 H8.5 Z "
                    "M5.5,8 H18.5 L17.6,20.2 C17.5,21.2 16.7,22 15.6,22 H8.4 C7.3,22 6.5,21.2 6.4,20.2 Z "
                    "M9.3,10.5 H10.8 V19 H9.3 Z M13.2,10.5 H14.7 V19 H13.2 Z", "#FF000000", even_odd=True)],
    "ic_notification": [L(PAW_PAD + " " + PAW_TOES, "#FFFFFFFF")],
}


def vector(layers, size=24, viewport=24, group=None):
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           '<!-- Sinh tự động bởi tools/gen_art_icons.py, sửa hình ở đó rồi chạy lại -->',
           f'<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           f'    android:width="{size}dp" android:height="{size}dp"',
           f'    android:viewportWidth="{viewport}" android:viewportHeight="{viewport}">']
    indent = "    "
    if group:
        out.append(f'    <group {group}>')
        indent = "        "
    for l in layers:
        attrs = [f'android:pathData="{l["path"]}"']
        if l["fill"]:
            attrs.append(f'android:fillColor="{l["fill"]}"')
        if l["stroke"]:
            attrs += [f'android:strokeColor="{l["stroke"]}"', f'android:strokeWidth="{l["width"]}"',
                      'android:strokeLineCap="round"', 'android:strokeLineJoin="round"']
        if l["even_odd"]:
            attrs.append('android:fillType="evenOdd"')
        if l["alpha"] is not None:
            key = "fillAlpha" if l["fill"] else "strokeAlpha"
            attrs.append(f'android:{key}="{l["alpha"]}"')
        out.append(f'{indent}<path ' + f'\n{indent}    '.join(attrs) + ' />')
    if group:
        out.append('    </group>')
    out.append('</vector>')
    return "\n".join(out) + "\n"


def write(name, text):
    with open(os.path.join(OUT, name + ".xml"), "w", encoding="utf-8") as f:
        f.write(text)


if __name__ == "__main__":
    for name, layers in {**ICONS, **MONO}.items():
        write(name, vector(layers))

    # Icon app: mèo (lớp trước) trên nền cam đào (lớp sau), khung 108dp, vùng an toàn 66dp ở giữa
    app_cat = [layer for layer in ICONS["art_cat"]]
    app_cat[0] = L(CAT_HEAD, CREAM)          # mặt mèo màu kem cho nổi trên nền cam
    group = 'android:translateX="21.6" android:translateY="20.5" android:scaleX="2.7" android:scaleY="2.7"'
    write("ic_launcher_foreground", vector(app_cat, size=108, viewport=108, group=group))
    write("ic_launcher_monochrome", vector(MONO["ic_nav_pet"], size=108, viewport=108, group=group))
    write("ic_launcher_background", vector([
        L("M0,0 H108 V108 H0 Z", PEACH),
        L(circle(54, 54, 40), "#FFB48A"),
        L(" ".join([circle(18, 22, 3), circle(90, 30, 2.5), circle(84, 88, 3), circle(22, 84, 2)]), "#FFC9A8"),
    ], size=108, viewport=108))
    print("Đã sinh", len(ICONS) + len(MONO) + 3, "icon vào", os.path.abspath(OUT))
