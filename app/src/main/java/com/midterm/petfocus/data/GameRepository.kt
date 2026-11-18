package com.midterm.petfocus.data

import androidx.room.withTransaction
import com.midterm.petfocus.logic.Badge
import com.midterm.petfocus.logic.DayCounts
import com.midterm.petfocus.logic.Quest
import com.midterm.petfocus.logic.QuestProgress
import com.midterm.petfocus.logic.Quests
import com.midterm.petfocus.logic.GameRules
import com.midterm.petfocus.logic.ItemType
import com.midterm.petfocus.logic.PetAction
import com.midterm.petfocus.logic.Reward
import com.midterm.petfocus.logic.ShopCatalog
import com.midterm.petfocus.logic.StatsCalculator
import com.midterm.petfocus.logic.StatsSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

sealed class ActionResult {
    data class Success(val message: String, val levelsGained: Int) : ActionResult()
    data class Failure(val reason: String) : ActionResult()
}

/**
 * Nơi duy nhất ghi dữ liệu. Mọi thay đổi xu/EXP/chỉ số đều chạy trong transaction
 * và luôn trừ chỉ số theo thời gian (decay) trước khi cộng/trừ để không bị lệch.
 */
class GameRepository(private val db: AppDatabase) {
    private val petDao = db.petDao()
    private val taskDao = db.taskDao()
    private val focusDao = db.focusDao()
    private val ownedDao = db.ownedItemDao()
    private val badgeDao = db.badgeDao()
    private val dailyDao = db.dailyDao()

    private fun now() = System.currentTimeMillis()
    private fun today() = GameRules.startOfDay(now())

    // ---------- Thú cưng ----------
    val pet: Flow<PetState> = petDao.observe().filterNotNull()

    private suspend fun loadPet(now: Long): PetState =
        GameRules.applyDecay(petDao.get() ?: PetState(lastUpdated = now), now)

    /** Tạo pet nếu chưa có và cập nhật chỉ số theo thời gian thật. */
    suspend fun refreshPet() = db.withTransaction { petDao.upsert(loadPet(now())) }

    suspend fun renamePet(name: String) = db.withTransaction {
        petDao.upsert(loadPet(now()).copy(name = name.trim().take(20)))
    }

    suspend fun doAction(action: PetAction): ActionResult = db.withTransaction {
        val now = now()
        val pet = loadPet(now)
        GameRules.checkAction(pet, action, now)?.let {
            petDao.upsert(pet)
            return@withTransaction ActionResult.Failure(it)
        }
        val acted = GameRules.applyAction(pet, action, now).let { it.copy(coins = it.coins - action.cost) }
        val (leveled, gained) = GameRules.addExp(acted, action.exp)
        petDao.upsert(leveled)
        ActionResult.Success(GameRules.actionMessage(leveled, action), gained)
    }

    private suspend fun grant(coins: Int, exp: Int, happinessDelta: Float = 0f): Reward {
        val pet = loadPet(now())
        val actualExp = GameRules.applyExpMultiplier(pet, exp)
        val withCoins = pet.copy(
            coins = (pet.coins + coins).coerceAtLeast(0),
            happiness = (pet.happiness + happinessDelta).coerceIn(0f, GameRules.MAX_STAT)
        )
        val (leveled, gained) = GameRules.addExp(withCoins, actualExp)
        petDao.upsert(leveled)
        return Reward(coins, actualExp, gained)
    }

    // ---------- Công việc ----------
    val tasks: Flow<List<Task>> = taskDao.observeAll()
    fun doneToday(): Flow<Int> = taskDao.observeDoneSince(today())
    fun taskCoinsToday(): Flow<Int> = taskDao.observeCoinsSince(today())

    suspend fun saveTask(task: Task) {
        if (task.id == 0L) taskDao.insert(task) else taskDao.update(task)
    }

    /**
     * Đánh dấu xong và trao thưởng. Trả về null nếu task đã xong từ trước (tránh nhận thưởng 2 lần).
     * Việc lặp lại thì tự tạo việc kế tiếp với hạn mới.
     */
    suspend fun completeTask(taskId: Long): Reward? = db.withTransaction {
        val task = taskDao.get(taskId) ?: return@withTransaction null
        if (task.isDone) return@withTransaction null
        val now = now()
        val (coins, exp) = GameRules.taskReward(task, now)
        val reward = grant(coins, exp, happinessDelta = 3f)
        // Lưu số EXP thực nhận (có thể bị giảm nửa khi mèo giận) để bỏ tick trừ lại cho đúng
        taskDao.update(task.copy(isDone = true, completedAt = now, coinsEarned = reward.coins, expEarned = reward.exp))
        GameRules.nextDueDate(task, now)?.let { nextDue ->
            taskDao.insert(
                task.copy(
                    id = 0, dueDate = nextDue, isDone = false, completedAt = null,
                    coinsEarned = 0, expEarned = 0, createdAt = now, parentId = task.id
                )
            )
        }
        reward
    }

    /** Bỏ tick một việc đã xong: trừ lại đúng số xu/EXP đã nhận, xóa việc lặp kế tiếp nếu chưa làm. */
    suspend fun uncompleteTask(taskId: Long): Reward? = db.withTransaction {
        val task = taskDao.get(taskId) ?: return@withTransaction null
        if (!task.isDone) return@withTransaction null
        val pet = GameRules.removeExp(loadPet(now()), task.expEarned)
        petDao.upsert(pet.copy(coins = (pet.coins - task.coinsEarned).coerceAtLeast(0)))
        taskDao.update(task.copy(isDone = false, completedAt = null, coinsEarned = 0, expEarned = 0))
        taskDao.deletePendingChildren(task.id)
        Reward(task.coinsEarned, task.expEarned)
    }

    suspend fun deleteTask(task: Task) = taskDao.delete(task)

    /** Hoàn tác xóa: thêm lại đúng task cũ (giữ nguyên id). */
    suspend fun restoreTask(task: Task) { taskDao.insert(task) }

    // ---------- Cửa hàng ----------
    val ownedItems: Flow<List<OwnedItem>> = ownedDao.observeAll()

    /** Mua đồ ăn (dùng ngay) hoặc phông nền (giữ lại và dùng luôn). */
    suspend fun buy(itemId: String): ActionResult = db.withTransaction {
        val item = ShopCatalog.find(itemId) ?: return@withTransaction ActionResult.Failure("Không tìm thấy món này.")
        val pet = loadPet(now())
        val reason = when {
            pet.level < item.minLevel -> "Cần đạt Level ${item.minLevel} để mua ${item.name}."
            item.type == ItemType.BACKGROUND && ownedDao.get(item.id) != null -> "Bạn đã có ${item.name} rồi."
            pet.coins < item.price -> "Không đủ xu! Cần ${item.price} xu."
            else -> null
        }
        if (reason != null) return@withTransaction ActionResult.Failure(reason)

        val paid = pet.copy(coins = pet.coins - item.price)
        when (item.type) {
            ItemType.FOOD -> {
                petDao.upsert(ShopCatalog.applyFood(paid, item))
                ActionResult.Success("${item.emoji} Măm măm! ${pet.name} thích ${item.name} lắm!", 0)
            }
            ItemType.BACKGROUND -> {
                petDao.upsert(paid)
                ownedDao.insert(OwnedItem(item.id))
                ownedDao.equipOnly(item.id)
                ActionResult.Success("Đã mua và dùng phông nền ${item.name} ${item.emoji}", 0)
            }
        }
    }

    suspend fun equipBackground(itemId: String) = db.withTransaction {
        if (itemId == ShopCatalog.DEFAULT_BACKGROUND && ownedDao.get(itemId) == null) {
            ownedDao.insert(OwnedItem(itemId))
        }
        if (ownedDao.get(itemId) != null) ownedDao.equipOnly(itemId)
    }

    // ---------- Thống kê ----------
    val stats: Flow<StatsSnapshot> = combine(
        focusDao.observeCompleted(),
        taskDao.observeCompletedTimes(),
        focusDao.observeFailedCount(),
        pet
    ) { sessions, taskTimes, failed, p ->
        StatsCalculator.build(sessions, taskTimes, failed, p.level, now())
    }

    // ---------- Nhiệm vụ hằng ngày & điểm danh ----------

    /** Tiến độ 3 nhiệm vụ của hôm nay (ngày tính lúc gọi hàm). */
    fun dailyQuests(): Flow<List<QuestProgress>> {
        val day = today()
        val counts = combine(
            taskDao.observeDoneSince(day), taskDao.observeHardDoneSince(day),
            focusDao.observeMinutesSince(day), focusDao.observeCompletedSince(day)
        ) { done, hard, minutes, sessions -> DayCounts(done, hard, minutes, sessions) }
        return combine(counts, dailyDao.observeClaimed(day)) { c, claimed ->
            Quests.forDay(day).map { q -> QuestProgress(q, q.progressOf(c).coerceAtMost(q.target), q.name in claimed) }
        }
    }

    /** Đã nhận rương thưởng hoàn thành cả 3 nhiệm vụ hôm nay chưa. */
    fun allDoneClaimedToday(): Flow<Boolean> =
        dailyDao.observeClaimed(today()).map { Quests.ALL_DONE_ID in it }

    /** Nhận thưởng một nhiệm vụ đã hoàn thành. Trả về null nếu chưa xong hoặc đã nhận. */
    suspend fun claimQuest(quest: Quest): Reward? = db.withTransaction {
        val day = today()
        if (quest !in Quests.forDay(day) || dailyDao.isClaimed(day, quest.name) > 0) return@withTransaction null
        if (quest.progressOf(countsFor(day)) < quest.target) return@withTransaction null
        dailyDao.insert(DailyClaim(day, quest.name, quest.rewardCoins, now()))
        grant(quest.rewardCoins, Quests.QUEST_EXP)
    }

    /** Nhận rương thưởng khi đã nhận đủ cả 3 nhiệm vụ trong ngày. */
    suspend fun claimAllDoneBonus(): Reward? = db.withTransaction {
        val day = today()
        if (dailyDao.isClaimed(day, Quests.ALL_DONE_ID) > 0) return@withTransaction null
        if (Quests.forDay(day).any { dailyDao.isClaimed(day, it.name) == 0 }) return@withTransaction null
        dailyDao.insert(DailyClaim(day, Quests.ALL_DONE_ID, Quests.ALL_DONE_BONUS, now()))
        grant(Quests.ALL_DONE_BONUS, 0, happinessDelta = 10f)
    }

    /**
     * Điểm danh mỗi ngày khi mở app. Trả về (chuỗi ngày liên tiếp, xu nhận được),
     * hoặc null nếu hôm nay đã điểm danh rồi.
     */
    suspend fun checkIn(): Pair<Int, Int>? = db.withTransaction {
        val day = today()
        if (dailyDao.isClaimed(day, Quests.CHECK_IN_ID) > 0) return@withTransaction null
        val previous = dailyDao.daysClaimed(Quests.CHECK_IN_ID).toSet()
        val streak = Quests.consecutiveDaysEnding(previous, GameRules.addDays(day, -1)) + 1
        val coins = Quests.checkInReward(streak)
        dailyDao.insert(DailyClaim(day, Quests.CHECK_IN_ID, coins, now()))
        grant(coins, 0)
        streak to coins
    }

    // Đọc trực tiếp (không qua Flow) vì đang ở trong transaction
    private suspend fun countsFor(day: Long) = DayCounts(
        tasksDone = taskDao.countDoneSince(day),
        hardTasksDone = taskDao.countHardDoneSince(day),
        focusMinutes = focusDao.minutesSince(day),
        focusSessions = focusDao.completedSince(day)
    )

    // ---------- Huy hiệu ----------
    val claimedBadges: Flow<Set<String>> = badgeDao.observeClaimed().map { it.toSet() }

    /** Nhận xu thưởng của một huy hiệu đã mở khóa. Mỗi huy hiệu chỉ nhận một lần. */
    suspend fun claimBadge(badge: Badge, snapshot: StatsSnapshot): Reward? = db.withTransaction {
        if (!badge.unlocked(snapshot) || badgeDao.isClaimed(badge.name) > 0) return@withTransaction null
        badgeDao.insert(BadgeClaim(badge.name, now()))
        grant(badge.rewardCoins, 0, happinessDelta = 5f)
    }

    // ---------- Nhắc nhở ----------
    suspend fun currentPet(): PetState = db.withTransaction {
        loadPet(now()).also { petDao.upsert(it) }
    }

    suspend fun overdueCount(): Int = taskDao.countOverdue(now())

    // ---------- Tập trung ----------
    fun focusMinutesToday(): Flow<Int> = focusDao.observeMinutesSince(today())
    fun focusSessionsToday(): Flow<Int> = focusDao.observeCompletedSince(today())

    suspend fun completeFocus(startTime: Long, minutes: Int): Reward = db.withTransaction {
        val (coins, exp) = GameRules.focusReward(minutes)
        focusDao.insert(
            FocusSession(
                startTime = startTime, endTime = now(), plannedMinutes = minutes,
                actualMinutes = minutes, status = SessionStatus.COMPLETED,
                coinsEarned = coins, expEarned = exp
            )
        )
        val pet = loadPet(now())
        petDao.upsert(pet.copy(energy = (pet.energy - minutes / 5f).coerceAtLeast(0f)))
        grant(coins, exp, happinessDelta = GameRules.FOCUS_SUCCESS_HAPPINESS)
    }

    /** Nghỉ đủ giờ Pomodoro: mèo hồi sức và vui hơn một chút. */
    suspend fun finishBreak() = db.withTransaction {
        val p = loadPet(now())
        petDao.upsert(
            p.copy(
                energy = (p.energy + GameRules.BREAK_ENERGY).coerceAtMost(GameRules.MAX_STAT),
                happiness = (p.happiness + GameRules.BREAK_HAPPINESS).coerceAtMost(GameRules.MAX_STAT)
            )
        )
    }

    suspend fun failFocus(startTime: Long, planned: Int, status: SessionStatus) = db.withTransaction {
        val actual = ((now() - startTime) / 60_000).toInt()
        focusDao.insert(
            FocusSession(
                startTime = startTime, endTime = now(), plannedMinutes = planned,
                actualMinutes = actual, status = status
            )
        )
        val penalty = if (status == SessionStatus.FAILED) GameRules.FOCUS_FAIL_PENALTY else GameRules.FOCUS_GIVE_UP_PENALTY
        grant(0, 0, happinessDelta = -penalty)
    }
}
