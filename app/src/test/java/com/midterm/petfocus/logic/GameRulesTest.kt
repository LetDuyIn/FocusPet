package com.midterm.petfocus.logic

import com.midterm.petfocus.data.Difficulty
import com.midterm.petfocus.data.PetState
import com.midterm.petfocus.data.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameRulesTest {

    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L
    private fun pet(block: PetState.() -> PetState = { this }) = PetState(lastUpdated = now).block()

    // ---------- Lên cấp ----------

    @Test fun expToNext_tangTheoLevel() {
        assertEquals(100, GameRules.expToNext(1))
        assertEquals(300, GameRules.expToNext(3))
    }

    @Test fun addExp_chuaDuThiKhongLenCap() {
        val (p, gained) = GameRules.addExp(pet(), 40)
        assertEquals(0, gained)
        assertEquals(1, p.level)
        assertEquals(40, p.exp)
        assertEquals(50, p.coins)
    }

    @Test fun addExp_lenCapThuongXuVaVuiVeDay() {
        val (p, gained) = GameRules.addExp(pet { copy(exp = 90, happiness = 20f) }, 30)
        assertEquals(1, gained)
        assertEquals(2, p.level)
        assertEquals(20, p.exp) // 90 + 30 - 100
        assertEquals(50 + GameRules.LEVEL_UP_BONUS_PER_LEVEL * 2, p.coins)
        assertEquals(GameRules.MAX_STAT, p.happiness, 0.001f)
    }

    @Test fun addExp_lenNhieuCapMotLuc() {
        // Level 1 cần 100, level 2 cần 200 → 350 EXP lên 2 cấp, dư 50
        val (p, gained) = GameRules.addExp(pet(), 350)
        assertEquals(2, gained)
        assertEquals(3, p.level)
        assertEquals(50, p.exp)
    }

    // ---------- Chỉ số giảm theo thời gian ----------

    @Test fun applyDecay_motGio() {
        val p = GameRules.applyDecay(pet(), now + hour)
        assertEquals(74f, p.hunger, 0.01f)
        assertEquals(76f, p.happiness, 0.01f)
        assertEquals(77f, p.energy, 0.01f)
        assertEquals(77f, p.cleanliness, 0.01f)
        assertEquals(now + hour, p.lastUpdated)
    }

    @Test fun applyDecay_khongXuongDuoi0() {
        val p = GameRules.applyDecay(pet(), now + 100 * hour)
        assertEquals(0f, p.hunger, 0f)
        assertEquals(0f, p.happiness, 0f)
    }

    @Test fun applyDecay_thoiGianLuiThiGiuNguyen() {
        val original = pet()
        assertEquals(original, GameRules.applyDecay(original, now - hour))
    }

    // ---------- Thưởng công việc ----------

    @Test fun taskReward_dungHanNhanDu() {
        val task = Task(title = "a", difficulty = Difficulty.HARD, dueDate = GameRules.startOfDay(now))
        assertEquals(50 to 80, GameRules.taskReward(task, now))
    }

    @Test fun taskReward_treHanNhanMotNua() {
        val yesterday = GameRules.startOfDay(now) - GameRules.DAY_MS
        val task = Task(title = "a", difficulty = Difficulty.HARD, dueDate = yesterday)
        assertTrue(GameRules.isOverdue(task, now))
        assertEquals(25 to 40, GameRules.taskReward(task, now))
    }

    @Test fun isOverdue_viecDaXongKhongTinhQuaHan() {
        val yesterday = GameRules.startOfDay(now) - GameRules.DAY_MS
        assertFalse(GameRules.isOverdue(Task(title = "a", dueDate = yesterday, isDone = true), now))
        assertFalse(GameRules.isOverdue(Task(title = "a", dueDate = null), now))
    }

    // ---------- Thưởng tập trung ----------

    @Test fun focusReward_duoi45PhutKhongCoThuongThem() {
        assertEquals(25 to 50, GameRules.focusReward(25))
    }

    @Test fun focusReward_tu45PhutThuongThem20PhanTram() {
        assertEquals(54 to 108, GameRules.focusReward(45))
    }

    // ---------- Hành động chăm sóc ----------

    @Test fun checkAction_hopLe() {
        assertNull(GameRules.checkAction(pet(), PetAction.FEED, now))
    }

    @Test fun checkAction_dangHoiThiTuChoi() {
        val p = pet { copy(lastFeedAt = now - 10_000) }
        assertEquals(50_000L, GameRules.cooldownLeft(p, PetAction.FEED, now))
        assertNotNull(GameRules.checkAction(p, PetAction.FEED, now))
    }

    @Test fun checkAction_khongDuXu() {
        val reason = GameRules.checkAction(pet { copy(coins = 5) }, PetAction.CLEAN, now)
        assertTrue(reason!!.contains("Không đủ xu"))
    }

    @Test fun checkAction_noRoiThiKhongChoAn() {
        assertNotNull(GameRules.checkAction(pet { copy(hunger = 96f) }, PetAction.FEED, now))
    }

    @Test fun applyAction_choAnKhongVuotQua100() {
        val p = GameRules.applyAction(pet { copy(hunger = 90f) }, PetAction.FEED, now)
        assertEquals(100f, p.hunger, 0f)
        assertEquals(now, p.lastFeedAt)
    }

    // ---------- Bỏ tick: trừ EXP, có thể tụt cấp ----------

    @Test fun removeExp_trongCungCap() {
        val p = GameRules.removeExp(pet { copy(level = 2, exp = 50) }, 30)
        assertEquals(2, p.level)
        assertEquals(20, p.exp)
    }

    @Test fun removeExp_tutCapVaThuHoiXuThuongCap() {
        // Lên cấp từ 90/100 với +30 EXP rồi bỏ tick thì phải về đúng như cũ
        val before = pet { copy(exp = 90) }
        val (leveled, _) = GameRules.addExp(before, 30)
        val reverted = GameRules.removeExp(leveled, 30)
        assertEquals(before.level, reverted.level)
        assertEquals(before.exp, reverted.exp)
        assertEquals(before.coins, reverted.coins)
    }

    @Test fun removeExp_khongAmEXPKhongDuoiLevel1() {
        val p = GameRules.removeExp(pet { copy(exp = 10, coins = 0) }, 999)
        assertEquals(1, p.level)
        assertEquals(0, p.exp)
        assertEquals(0, p.coins)
    }

    // ---------- Việc lặp lại ----------

    @Test fun nextDueDate_khongLapThiNull() {
        assertNull(GameRules.nextDueDate(Task(title = "a"), now))
    }

    @Test fun nextDueDate_hangNgayLaNgayMai() {
        val today = GameRules.startOfDay(now)
        val next = GameRules.nextDueDate(Task(title = "a", dueDate = today, repeat = com.midterm.petfocus.data.Repeat.DAILY), now)
        assertEquals(GameRules.startOfDay(today + GameRules.DAY_MS + 3_600_000), next)
    }

    @Test fun nextDueDate_lamTreThiTinhTuHomNay() {
        val today = GameRules.startOfDay(now)
        val longAgo = today - 10 * GameRules.DAY_MS
        val next = GameRules.nextDueDate(Task(title = "a", dueDate = longAgo, repeat = com.midterm.petfocus.data.Repeat.WEEKLY), now)!!
        assertTrue(next > today)
    }

    // ---------- Phạt bỏ bê ----------

    @Test fun applyDecay_boDoiThiVuiVeGiamNhanhGap3() {
        // No bụng 0 sẵn: cả 1 giờ đều là bị bỏ đói → Vui vẻ giảm 4 × 3 = 12
        val p = GameRules.applyDecay(pet { copy(hunger = 0f) }, now + hour)
        assertEquals(68f, p.happiness, 0.01f)
    }

    @Test fun meoGian_chiChoAnDuocVaNuaEXP() {
        val angry = pet { copy(hunger = 0f, happiness = 0f) }
        assertEquals(Mood.ANGRY, GameRules.mood(angry))
        assertNotNull(GameRules.checkAction(angry, PetAction.CUDDLE, now))
        assertNull(GameRules.checkAction(angry, PetAction.FEED, now))
        assertEquals(20, GameRules.applyExpMultiplier(angry, 40))
        assertEquals(40, GameRules.applyExpMultiplier(pet(), 40))
    }

    // ---------- Tâm trạng ----------

    @Test fun mood_uuTienBuonNguTruoc() {
        assertEquals(Mood.SLEEPY, GameRules.mood(pet { copy(energy = 10f, hunger = 10f) }))
        assertEquals(Mood.HUNGRY, GameRules.mood(pet { copy(hunger = 10f) }))
        assertEquals(Mood.HAPPY, GameRules.mood(pet()))
        assertEquals(Mood.NORMAL, GameRules.mood(pet { copy(hunger = 50f) }))
    }
}
