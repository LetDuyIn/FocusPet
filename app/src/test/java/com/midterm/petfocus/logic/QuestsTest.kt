package com.midterm.petfocus.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestsTest {

    private val today = GameRules.startOfDay(1_800_000_000_000L)

    @Test fun forDay_3NhiemVuKhacNhau_coCongViecVaTapTrung() {
        repeat(60) { i ->
            val quests = Quests.forDay(GameRules.addDays(today, i))
            assertEquals(3, quests.size)
            assertEquals(3, quests.toSet().size)
            assertTrue(quests.any { it.group == QuestGroup.TASK })
            assertTrue(quests.any { it.group == QuestGroup.FOCUS })
        }
    }

    @Test fun forDay_caNgayGiuNguyen_doiTheoNgay() {
        assertEquals(Quests.forDay(today), Quests.forDay(today))
        val week = (0 until 7).map { Quests.forDay(GameRules.addDays(today, it)) }.toSet()
        assertTrue("Một tuần phải có ít nhất 2 bộ nhiệm vụ khác nhau", week.size > 1)
    }

    @Test fun progress_tinhTheoSoLieuTrongNgay() {
        val counts = DayCounts(tasksDone = 2, hardTasksDone = 1, focusMinutes = 45, focusSessions = 2)
        assertEquals(2, Quest.TASKS_3.progressOf(counts))
        assertFalse(QuestProgress(Quest.TASKS_3, 2, false).done)
        assertTrue(QuestProgress(Quest.FOCUS_30, 30, false).done)
        assertEquals(1, Quest.TASK_HARD.progressOf(counts))
        assertEquals(2, Quest.FOCUS_2_SESSIONS.progressOf(counts))
    }

    @Test fun checkInReward_tangDanToiDa40() {
        assertEquals(10, Quests.checkInReward(1))
        assertEquals(15, Quests.checkInReward(2))
        assertEquals(40, Quests.checkInReward(7))
        assertEquals(40, Quests.checkInReward(30))
    }

    @Test fun consecutiveDays_demNgayLienTiep() {
        val yesterday = GameRules.addDays(today, -1)
        val days = setOf(yesterday, GameRules.addDays(today, -2), GameRules.addDays(today, -4))
        assertEquals(2, Quests.consecutiveDaysEnding(days, yesterday))
        assertEquals(0, Quests.consecutiveDaysEnding(days, today))
    }
}
