package com.midterm.petfocus.logic

import com.midterm.petfocus.data.PetState
import com.midterm.petfocus.data.Task
import java.util.Calendar

/** Tâm trạng của pet, quyết định animation và lời thoại. */
enum class Mood { HAPPY, NORMAL, HUNGRY, SAD, SLEEPY, DIRTY, ANGRY }

/** Các hành động chăm sóc pet: giá xu, thời gian hồi và EXP nhận được. */
enum class PetAction(val label: String, val cost: Int, val cooldownMs: Long, val exp: Int) {
    FEED("Cho ăn", 10, 60_000, 3),
    CUDDLE("Nựng", 0, 30_000, 2),
    CLEAN("Tắm", 15, 120_000, 3),
    SLEEP("Ngủ", 0, 180_000, 2)
}

/**
 * Giai đoạn trưởng thành của mèo theo cấp. Mèo to dần ([scale]) và đeo phụ kiện mới.
 * Phụ kiện là hình art_bow / art_gradcap / art_crown (gán ở tầng giao diện).
 */
enum class GrowthStage(val label: String, val minLevel: Int, val scale: Float) {
    KITTEN("Mèo con", 1, 0.72f),
    JUNIOR("Mèo nhỡ", 3, 0.84f),
    ADULT("Mèo trưởng thành", 6, 0.94f),
    ROYAL("Mèo hoàng gia", 10, 1.0f);

    /** Giai đoạn kế tiếp, null nếu đã lớn nhất. */
    val next: GrowthStage? get() = entries.getOrNull(ordinal + 1)

    companion object {
        fun of(level: Int): GrowthStage = entries.last { level >= it.minLevel }
    }
}

data class Reward(val coins: Int, val exp: Int, val levelsGained: Int = 0)

/**
 * Toàn bộ luật chơi nằm ở đây, là các hàm thuần (không đụng database/UI)
 * nên dễ đọc, dễ chỉnh số và dễ viết unit test.
 */
object GameRules {
    const val MAX_STAT = 100f
    const val STARTING_COINS = 50

    // Mỗi giờ chỉ số giảm bao nhiêu điểm
    private const val HUNGER_PER_HOUR = 6f
    private const val HAPPINESS_PER_HOUR = 4f
    private const val ENERGY_PER_HOUR = 3f
    private const val CLEAN_PER_HOUR = 3f

    const val LEVEL_UP_BONUS_PER_LEVEL = 20
    const val FOCUS_FAIL_PENALTY = 15f
    const val FOCUS_GIVE_UP_PENALTY = 8f
    const val FOCUS_SUCCESS_HAPPINESS = 10f
    const val LEAVE_GRACE_MS = 5_000L

    // ---- Pomodoro ----
    const val MAX_FOCUS_MINUTES = 120
    const val SHORT_BREAK_MINUTES = 5
    const val LONG_BREAK_MINUTES = 15
    const val SESSIONS_BEFORE_LONG_BREAK = 4
    const val BREAK_ENERGY = 10f
    const val BREAK_HAPPINESS = 5f

    /** Sau mỗi 4 phiên hoàn thành trong ngày thì được nghỉ dài, còn lại nghỉ ngắn. */
    fun breakMinutesAfter(completedToday: Int): Int =
        if (completedToday > 0 && completedToday % SESSIONS_BEFORE_LONG_BREAK == 0) LONG_BREAK_MINUTES
        else SHORT_BREAK_MINUTES

    fun expToNext(level: Int) = 100 * level

    private fun clamp(v: Float) = v.coerceIn(0f, MAX_STAT)

    /** Khi bị bỏ đói (No bụng = 0), Vui vẻ giảm nhanh gấp mấy lần bình thường. */
    const val STARVING_HAPPINESS_MULTIPLIER = 3f
    /** Mèo giận dỗi thì chỉ nhận được phần này của EXP. */
    const val ANGRY_EXP_MULTIPLIER = 0.5f

    /**
     * Trừ chỉ số theo thời gian đã trôi qua kể từ lần cập nhật cuối.
     * Phạt bỏ bê: khoảng thời gian No bụng đã về 0 thì Vui vẻ giảm nhanh gấp 3.
     */
    fun applyDecay(p: PetState, now: Long): PetState {
        val hours = (now - p.lastUpdated).coerceAtLeast(0) / 3_600_000f
        if (hours <= 0f) return p
        val starvingHours = (hours - p.hunger / HUNGER_PER_HOUR).coerceAtLeast(0f)
        val happinessLoss = HAPPINESS_PER_HOUR * (hours + starvingHours * (STARVING_HAPPINESS_MULTIPLIER - 1))
        return p.copy(
            hunger = clamp(p.hunger - HUNGER_PER_HOUR * hours),
            happiness = clamp(p.happiness - happinessLoss),
            energy = clamp(p.energy - ENERGY_PER_HOUR * hours),
            cleanliness = clamp(p.cleanliness - CLEAN_PER_HOUR * hours),
            lastUpdated = now
        )
    }

    /** Cộng EXP, tự lên cấp (có thể lên nhiều cấp). Mỗi lần lên cấp được thưởng xu và vui vẻ đầy. */
    fun addExp(p: PetState, exp: Int): Pair<PetState, Int> {
        var level = p.level
        var e = p.exp + exp
        var coins = p.coins
        var gained = 0
        while (e >= expToNext(level)) {
            e -= expToNext(level)
            level++
            gained++
            coins += LEVEL_UP_BONUS_PER_LEVEL * level
        }
        val happiness = if (gained > 0) MAX_STAT else p.happiness
        return p.copy(level = level, exp = e, coins = coins, happiness = happiness) to gained
    }

    /**
     * Trừ EXP khi bỏ tick một việc đã xong. Có thể tụt cấp; tụt cấp nào thì
     * thu hồi luôn số xu thưởng của cấp đó, để tick/bỏ tick liên tục không kiếm thêm được gì.
     */
    fun removeExp(p: PetState, exp: Int): PetState {
        var level = p.level
        var e = p.exp - exp
        var coins = p.coins
        while (e < 0 && level > 1) {
            coins -= LEVEL_UP_BONUS_PER_LEVEL * level
            level--
            e += expToNext(level)
        }
        return p.copy(level = level, exp = e.coerceAtLeast(0), coins = coins.coerceAtLeast(0))
    }

    /** Mèo giận dỗi (bị bỏ đói tới mức buồn hẳn) thì chỉ nhận nửa EXP. */
    fun applyExpMultiplier(p: PetState, exp: Int): Int =
        if (mood(p) == Mood.ANGRY) (exp * ANGRY_EXP_MULTIPLIER).toInt() else exp

    /**
     * Hạn của lần lặp kế tiếp: tính từ hạn cũ (hoặc hôm nay nếu không có hạn);
     * nếu làm trễ thì tính từ hôm nay để việc mới không bị quá hạn ngay.
     */
    fun nextDueDate(task: Task, now: Long): Long? {
        if (task.repeat.days == 0) return null
        val today = startOfDay(now)
        val base = maxOf(task.dueDate ?: today, today)
        return Calendar.getInstance().run {
            timeInMillis = base
            add(Calendar.DAY_OF_YEAR, task.repeat.days)
            startOfDay(timeInMillis)
        }
    }

    fun isOverdue(task: Task, now: Long): Boolean =
        !task.isDone && task.dueDate != null && now >= task.dueDate + DAY_MS

    /** Làm xong đúng hạn nhận đủ, trễ hạn chỉ nhận một nửa. */
    fun taskReward(task: Task, now: Long): Pair<Int, Int> {
        val d = task.difficulty
        return if (isOverdue(task, now)) d.coins / 2 to d.exp / 2 else d.coins to d.exp
    }

    /** 1 xu và 2 EXP cho mỗi phút tập trung, thưởng thêm 20% nếu từ 45 phút trở lên. */
    fun focusReward(minutes: Int): Pair<Int, Int> {
        val bonus = if (minutes >= 45) 1.2f else 1f
        return (minutes * bonus).toInt() to (minutes * 2 * bonus).toInt()
    }

    fun cooldownLeft(p: PetState, action: PetAction, now: Long): Long {
        val last = when (action) {
            PetAction.FEED -> p.lastFeedAt
            PetAction.CUDDLE -> p.lastCuddleAt
            PetAction.CLEAN -> p.lastCleanAt
            PetAction.SLEEP -> p.lastSleepAt
        }
        return (last + action.cooldownMs - now).coerceAtLeast(0)
    }

    /** Trả về lý do không làm được, hoặc null nếu hợp lệ. */
    fun checkAction(p: PetState, action: PetAction, now: Long): String? {
        val left = cooldownLeft(p, action, now)
        return when {
            mood(p) == Mood.ANGRY && action != PetAction.FEED ->
                "${p.name} đang giận dỗi vì bị bỏ đói! Cho ăn trước đã 😾"
            left > 0 -> "${p.name} cần nghỉ thêm ${(left + 999) / 1000} giây nữa."
            p.coins < action.cost -> "Không đủ xu! Cần ${action.cost} xu, hãy làm thêm việc nhé."
            action == PetAction.FEED && p.hunger >= 95f -> "${p.name} no căng bụng rồi!"
            action == PetAction.CLEAN && p.cleanliness >= 95f -> "${p.name} đang thơm tho lắm rồi!"
            action == PetAction.SLEEP && p.energy >= 95f -> "${p.name} đang tỉnh như sáo, chưa muốn ngủ."
            else -> null
        }
    }

    /** Áp dụng tác dụng của hành động (chưa trừ xu, chưa cộng EXP). */
    fun applyAction(p: PetState, action: PetAction, now: Long): PetState = when (action) {
        PetAction.FEED -> p.copy(
            hunger = clamp(p.hunger + 30), happiness = clamp(p.happiness + 5),
            energy = clamp(p.energy + 5), lastFeedAt = now
        )
        PetAction.CUDDLE -> p.copy(happiness = clamp(p.happiness + 12), lastCuddleAt = now)
        PetAction.CLEAN -> p.copy(
            cleanliness = clamp(p.cleanliness + 50), happiness = clamp(p.happiness - 3), lastCleanAt = now
        )
        PetAction.SLEEP -> p.copy(
            energy = clamp(p.energy + 40), hunger = clamp(p.hunger - 5), lastSleepAt = now
        )
    }

    fun actionMessage(p: PetState, action: PetAction): String = when (action) {
        PetAction.FEED -> "Ngon quá! Cảm ơn Sen 🍣"
        PetAction.CUDDLE -> "Rừ rừ... ${p.name} thích được nựng 💕"
        PetAction.CLEAN -> "Hừm, ướt hết rồi... nhưng thơm phức! 🛁"
        PetAction.SLEEP -> "Zzz... ${p.name} ngủ một giấc ngon lành 😴"
    }

    fun mood(p: PetState): Mood = when {
        p.hunger <= 0f && p.happiness <= 0f -> Mood.ANGRY
        p.energy < 25f -> Mood.SLEEPY
        p.hunger < 30f -> Mood.HUNGRY
        p.happiness < 30f -> Mood.SAD
        p.cleanliness < 30f -> Mood.DIRTY
        p.hunger >= 70f && p.happiness >= 70f -> Mood.HAPPY
        else -> Mood.NORMAL
    }

    fun dialogue(p: PetState): String = when (mood(p)) {
        Mood.ANGRY -> "Hứ! Bỏ đói ${p.name} lâu thế! Không chơi nữa 😾 (chỉ nhận nửa EXP tới khi được cho ăn)"
        Mood.SLEEPY -> "Buồn ngủ quá... cho ${p.name} ngủ một chút nha 😪"
        Mood.HUNGRY -> "Sen ơi, đói quá... cho ăn đi mà 🥺"
        Mood.SAD -> "${p.name} buồn... Sen nựng ${p.name} với 😿"
        Mood.DIRTY -> "Lông bết hết rồi, tắm cho ${p.name} đi!"
        Mood.HAPPY -> "Meow! Hôm nay Sen làm bài tập chưa? 😺"
        Mood.NORMAL -> "Làm xong việc để kiếm xu nuôi ${p.name} nhé!"
    }

    // ---- Thời gian ----
    const val DAY_MS = 24 * 60 * 60 * 1000L

    /** Cộng/trừ ngày bằng Calendar để đúng cả khi đổi giờ mùa hè. */
    fun addDays(dayStart: Long, days: Int): Long = Calendar.getInstance().run {
        timeInMillis = dayStart
        add(Calendar.DAY_OF_YEAR, days)
        startOfDay(timeInMillis)
    }

    fun startOfDay(time: Long): Long = Calendar.getInstance().run {
        timeInMillis = time
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        timeInMillis
    }
}
