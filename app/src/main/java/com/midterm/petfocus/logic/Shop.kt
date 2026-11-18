package com.midterm.petfocus.logic

import com.midterm.petfocus.data.PetState

enum class ItemType { FOOD, BACKGROUND }

/**
 * Một món trong cửa hàng.
 * - FOOD: dùng ngay khi mua, cộng chỉ số, không có thời gian hồi.
 * - BACKGROUND: mua một lần, giữ mãi, có thể đổi qua lại.
 */
data class ShopItem(
    val id: String,
    val emoji: String,
    val name: String,
    val description: String,
    val price: Int,
    val type: ItemType,
    val hunger: Float = 0f,
    val happiness: Float = 0f,
    val energy: Float = 0f,
    val cleanliness: Float = 0f,
    /** Màu nền ô hiển thị pet, chỉ dùng cho BACKGROUND. */
    val color: String? = null,
    val minLevel: Int = 1
)

object ShopCatalog {
    const val DEFAULT_BACKGROUND = "bg_default"

    val items = listOf(
        ShopItem("food_fish", "🐟", "Cá hồi nướng", "No bụng +50, Vui vẻ +10", 25, ItemType.FOOD, hunger = 50f, happiness = 10f),
        ShopItem("food_treat", "🍪", "Bánh thưởng", "Vui vẻ +25", 20, ItemType.FOOD, happiness = 25f),
        ShopItem("food_catnip", "🌿", "Cỏ mèo", "Vui vẻ +20, Sức lực +25", 30, ItemType.FOOD, happiness = 20f, energy = 25f),
        ShopItem("food_spa", "🧴", "Gói spa", "Sạch sẽ +60, Vui vẻ +10", 35, ItemType.FOOD, cleanliness = 60f, happiness = 10f),
        ShopItem(DEFAULT_BACKGROUND, "🤍", "Nền trắng", "Phông nền mặc định", 0, ItemType.BACKGROUND, color = "#FFFFFF"),
        ShopItem("bg_garden", "🌱", "Vườn xanh", "Phông nền xanh mát", 80, ItemType.BACKGROUND, color = "#E3F6EC"),
        ShopItem("bg_sky", "☁️", "Bầu trời", "Phông nền xanh da trời", 120, ItemType.BACKGROUND, color = "#E0F2FE", minLevel = 2),
        ShopItem("bg_sunset", "🌅", "Hoàng hôn", "Phông nền cam ấm áp", 180, ItemType.BACKGROUND, color = "#FFE8D6", minLevel = 3),
        ShopItem("bg_night", "🌙", "Đêm sao", "Phông nền tím huyền bí", 250, ItemType.BACKGROUND, color = "#E9E5FF", minLevel = 5)
    )

    fun find(id: String) = items.firstOrNull { it.id == id }

    /** Áp dụng tác dụng của đồ ăn lên pet (chưa trừ xu). */
    fun applyFood(p: PetState, item: ShopItem): PetState {
        fun c(v: Float) = v.coerceIn(0f, GameRules.MAX_STAT)
        return p.copy(
            hunger = c(p.hunger + item.hunger),
            happiness = c(p.happiness + item.happiness),
            energy = c(p.energy + item.energy),
            cleanliness = c(p.cleanliness + item.cleanliness)
        )
    }
}
