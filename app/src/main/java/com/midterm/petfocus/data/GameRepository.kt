package com.midterm.petfocus.data

import androidx.room.withTransaction
import com.midterm.petfocus.logic.GameRules
import com.midterm.petfocus.logic.PetAction
import com.midterm.petfocus.logic.Reward
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull

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
        val withCoins = pet.copy(
            coins = (pet.coins + coins).coerceAtLeast(0),
            happiness = (pet.happiness + happinessDelta).coerceIn(0f, GameRules.MAX_STAT)
        )
        val (leveled, gained) = GameRules.addExp(withCoins, exp)
        petDao.upsert(leveled)
        return Reward(coins, exp, gained)
    }

    // ---------- Công việc ----------
    val tasks: Flow<List<Task>> = taskDao.observeAll()
    fun doneToday(): Flow<Int> = taskDao.observeDoneSince(today())
    fun taskCoinsToday(): Flow<Int> = taskDao.observeCoinsSince(today())

    suspend fun saveTask(task: Task) {
        if (task.id == 0L) taskDao.insert(task) else taskDao.update(task)
    }

    /** Đánh dấu xong và trao thưởng. Trả về null nếu task đã xong từ trước (tránh nhận thưởng 2 lần). */
    suspend fun completeTask(taskId: Long): Reward? = db.withTransaction {
        val task = taskDao.get(taskId) ?: return@withTransaction null
        if (task.isDone) return@withTransaction null
        val now = now()
        val (coins, exp) = GameRules.taskReward(task, now)
        taskDao.update(task.copy(isDone = true, completedAt = now, coinsEarned = coins, expEarned = exp))
        grant(coins, exp, happinessDelta = 3f)
    }

    suspend fun deleteTask(task: Task) = taskDao.delete(task)

    /** Hoàn tác xóa: thêm lại đúng task cũ (giữ nguyên id). */
    suspend fun restoreTask(task: Task) { taskDao.insert(task) }

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
