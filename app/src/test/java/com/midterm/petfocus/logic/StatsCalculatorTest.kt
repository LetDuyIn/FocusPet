package com.midterm.petfocus.logic

import com.midterm.petfocus.data.FocusSession
import com.midterm.petfocus.data.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class StatsCalculatorTest {

    private val now = 1_800_000_000_000L
    private val today = GameRules.startOfDay(now)
    private fun daysAgo(n: Int): Long = Calendar.getInstance().run {
        timeInMillis = today
        add(Calendar.DAY_OF_YEAR, -n)
        GameRules.startOfDay(timeInMillis)
    }
    private fun session(day: Long, minutes: Int) = FocusSession(
        startTime = day + 3_600_000, endTime = day + 3_600_000 + minutes * 60_000L,
        plannedMinutes = minutes, actualMinutes = minutes, status = SessionStatus.COMPLETED
    )

    @Test fun streak_homNayVaHomQua() {
        assertEquals(2, StatsCalculator.currentStreak(setOf(today, daysAgo(1)), now))
    }

    @Test fun streak_homNayChuaLamVanGiuChuoiHomQua() {
        assertEquals(2, StatsCalculator.currentStreak(setOf(daysAgo(1), daysAgo(2)), now))
    }

    @Test fun streak_dutQuangThiVe0() {
        assertEquals(0, StatsCalculator.currentStreak(setOf(daysAgo(2), daysAgo(3)), now))
    }

    @Test fun bestStreak_chonChuoiDaiNhat() {
        val days = setOf(daysAgo(10), daysAgo(9), daysAgo(8), daysAgo(5), daysAgo(4), today)
        assertEquals(3, StatsCalculator.bestStreak(days))
        assertEquals(0, StatsCalculator.bestStreak(emptySet()))
    }

    @Test fun last7Days_congDonPhutTheoNgay() {
        val days = StatsCalculator.last7Days(
            listOf(session(today, 25), session(today, 15), session(daysAgo(2), 45), session(daysAgo(9), 60)),
            now
        )
        assertEquals(7, days.size)
        assertEquals("Nay", days.last().label)
        assertEquals(40, days.last().minutes)
        assertEquals(45, days[4].minutes)
        assertEquals(85, days.sumOf { it.minutes }) // phiên 9 ngày trước không tính
    }

    @Test fun build_tongHopVaHuyHieu() {
        val s = StatsCalculator.build(
            sessions = listOf(session(today, 30), session(daysAgo(1), 40)),
            taskCompletedTimes = listOf(today + 1000, daysAgo(2) + 1000),
            failedSessions = 1,
            level = 2,
            now = now
        )
        assertEquals(3, s.streak) // hôm nay, hôm qua, hôm kia
        assertEquals(70, s.totalFocusMinutes)
        assertEquals(2, s.focusSessions)
        assertEquals(2, s.tasksDone)
        assertTrue(Badge.FIRST_TASK.unlocked(s))
        assertTrue(Badge.FOCUS_60.unlocked(s))
        assertTrue(Badge.STREAK_3.unlocked(s))
        assertFalse(Badge.TASK_10.unlocked(s))
        assertFalse(Badge.LEVEL_5.unlocked(s))
    }
}
