package com.midterm.petfocus.logic

/** Số liệu trong ngày dùng để tính tiến độ nhiệm vụ. */
data class DayCounts(
    val tasksDone: Int = 0,
    val hardTasksDone: Int = 0,
    val focusMinutes: Int = 0,
    val focusSessions: Int = 0
)

enum class QuestGroup { TASK, FOCUS }

/** Kho nhiệm vụ hằng ngày. Mỗi ngày chọn ra 3 nhiệm vụ từ đây. */
enum class Quest(
    val emoji: String,
    val title: String,
    val target: Int,
    val rewardCoins: Int,
    val group: QuestGroup,
    val progressOf: (DayCounts) -> Int
) {
    TASKS_1("✅", "Hoàn thành 1 việc", 1, 10, QuestGroup.TASK, { it.tasksDone }),
    TASKS_3("📋", "Hoàn thành 3 việc", 3, 30, QuestGroup.TASK, { it.tasksDone }),
    TASK_HARD("🏆", "Hoàn thành 1 việc Khó", 1, 25, QuestGroup.TASK, { it.hardTasksDone }),
    FOCUS_30("⏱️", "Tập trung tổng 30 phút", 30, 30, QuestGroup.FOCUS, { it.focusMinutes }),
    FOCUS_60("📚", "Tập trung tổng 60 phút", 60, 50, QuestGroup.FOCUS, { it.focusMinutes }),
    FOCUS_2_SESSIONS("🔥", "Hoàn thành 2 phiên tập trung", 2, 25, QuestGroup.FOCUS, { it.focusSessions });
}

data class QuestProgress(val quest: Quest, val progress: Int, val claimed: Boolean) {
    val done get() = progress >= quest.target
}

object Quests {
    const val CHECK_IN_ID = "CHECK_IN"
    const val ALL_DONE_ID = "ALL_DONE"
    const val ALL_DONE_BONUS = 30
    const val QUEST_EXP = 10

    /**
     * 3 nhiệm vụ của một ngày: 1 về công việc, 1 về tập trung, 1 bất kỳ còn lại.
     * Chọn theo số thứ tự của ngày nên cả ngày giữ nguyên, sang ngày mới thì đổi.
     */
    fun forDay(dayStart: Long): List<Quest> {
        // Đổi mốc 0h giờ địa phương thành số thứ tự ngày (cộng múi giờ để không lệch ngày)
        val offset = java.util.TimeZone.getDefault().getOffset(dayStart)
        val day = Math.floorDiv(dayStart + offset, GameRules.DAY_MS).toInt()
        val tasks = Quest.entries.filter { it.group == QuestGroup.TASK }
        val focus = Quest.entries.filter { it.group == QuestGroup.FOCUS }
        val first = tasks[Math.floorMod(day, tasks.size)]
        val second = focus[Math.floorMod(day * 7 + 3, focus.size)]
        val rest = Quest.entries - first - second
        val third = rest[Math.floorMod(day * 13 + 5, rest.size)]
        return listOf(first, second, third)
    }

    /** Điểm danh: ngày 1 được 10 xu, mỗi ngày liên tiếp thêm 5 xu, tối đa 40 xu từ ngày thứ 7. */
    fun checkInReward(streak: Int): Int = 10 + 5 * (streak.coerceIn(1, 7) - 1)

    /** Số ngày liên tiếp có trong [days], tính lùi từ [endDay] (tính cả endDay). */
    fun consecutiveDaysEnding(days: Set<Long>, endDay: Long): Int {
        var count = 0
        var d = endDay
        while (d in days) {
            count++
            d = GameRules.addDays(d, -1)
        }
        return count
    }
}
