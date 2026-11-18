package com.midterm.petfocus.logic

import com.midterm.petfocus.data.FocusSession
import java.util.Calendar

data class DayStat(val dayStart: Long, val label: String, val minutes: Int)

/** Ảnh chụp số liệu dùng cho màn Thống kê và để xét huy hiệu. */
data class StatsSnapshot(
    val streak: Int,
    val bestStreak: Int,
    val totalFocusMinutes: Int,
    val focusSessions: Int,
    val failedSessions: Int,
    val tasksDone: Int,
    val level: Int,
    val last7Days: List<DayStat>
)

enum class Badge(val emoji: String, val title: String, val description: String, val unlocked: (StatsSnapshot) -> Boolean) {
    FIRST_TASK("✅", "Khởi đầu", "Hoàn thành công việc đầu tiên", { it.tasksDone >= 1 }),
    TASK_10("📋", "Chăm chỉ", "Hoàn thành 10 công việc", { it.tasksDone >= 10 }),
    TASK_50("🏆", "Cỗ máy làm việc", "Hoàn thành 50 công việc", { it.tasksDone >= 50 }),
    FIRST_FOCUS("⏱️", "Tập trung lần đầu", "Hoàn thành 1 phiên tập trung", { it.focusSessions >= 1 }),
    FOCUS_60("📚", "Một giờ vàng", "Tổng cộng 60 phút tập trung", { it.totalFocusMinutes >= 60 }),
    FOCUS_600("🧠", "Bậc thầy tập trung", "Tổng cộng 10 giờ tập trung", { it.totalFocusMinutes >= 600 }),
    STREAK_3("🔥", "Giữ lửa", "Chuỗi 3 ngày liên tục", { it.bestStreak >= 3 }),
    STREAK_7("🌟", "Cả tuần bền bỉ", "Chuỗi 7 ngày liên tục", { it.bestStreak >= 7 }),
    LEVEL_5("🐱", "Sen chuyên nghiệp", "Nuôi thú cưng lên Level 5", { it.level >= 5 })
}

object StatsCalculator {
    private val dayLabels = arrayOf("CN", "T2", "T3", "T4", "T5", "T6", "T7")

    fun build(
        sessions: List<FocusSession>,
        taskCompletedTimes: List<Long>,
        failedSessions: Int,
        level: Int,
        now: Long
    ): StatsSnapshot {
        val activeDays = (sessions.map { it.startTime } + taskCompletedTimes)
            .map { GameRules.startOfDay(it) }
            .toSortedSet()
        return StatsSnapshot(
            streak = currentStreak(activeDays, now),
            bestStreak = bestStreak(activeDays),
            totalFocusMinutes = sessions.sumOf { it.actualMinutes },
            focusSessions = sessions.size,
            failedSessions = failedSessions,
            tasksDone = taskCompletedTimes.size,
            level = level,
            last7Days = last7Days(sessions, now)
        )
    }

    /** Số phút tập trung của 7 ngày gần nhất, ngày cuối là hôm nay. */
    fun last7Days(sessions: List<FocusSession>, now: Long): List<DayStat> {
        val byDay = sessions.groupBy { GameRules.startOfDay(it.startTime) }
            .mapValues { (_, list) -> list.sumOf { it.actualMinutes } }
        return (6 downTo 0).map { back ->
            val day = shiftDays(GameRules.startOfDay(now), -back)
            val cal = Calendar.getInstance().apply { timeInMillis = day }
            val label = if (back == 0) "Nay" else dayLabels[cal.get(Calendar.DAY_OF_WEEK) - 1]
            DayStat(day, label, byDay[day] ?: 0)
        }
    }

    /** Chuỗi ngày liên tục có hoạt động. Hôm nay chưa làm gì thì vẫn giữ chuỗi tính tới hôm qua. */
    fun currentStreak(activeDays: Set<Long>, now: Long): Int {
        var day = GameRules.startOfDay(now)
        if (day !in activeDays) day = shiftDays(day, -1)
        var count = 0
        while (day in activeDays) {
            count++
            day = shiftDays(day, -1)
        }
        return count
    }

    fun bestStreak(activeDays: Set<Long>): Int {
        var best = 0
        var run = 0
        var prev: Long? = null
        for (day in activeDays.sorted()) {
            run = if (prev != null && shiftDays(prev, 1) == day) run + 1 else 1
            best = maxOf(best, run)
            prev = day
        }
        return best
    }

    /** Cộng/trừ ngày bằng Calendar để đúng cả khi đổi giờ mùa hè. */
    private fun shiftDays(dayStart: Long, days: Int): Long = Calendar.getInstance().run {
        timeInMillis = dayStart
        add(Calendar.DAY_OF_YEAR, days)
        GameRules.startOfDay(timeInMillis)
    }
}
