package com.midterm.petfocus.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PetDao {
    @Query("SELECT * FROM pet WHERE id = 1")
    fun observe(): Flow<PetState?>

    @Query("SELECT * FROM pet WHERE id = 1")
    suspend fun get(): PetState?

    @Upsert
    suspend fun upsert(pet: PetState)
}

@Dao
interface TaskDao {
    /** Chưa xong lên trước, rồi theo hạn chót gần nhất, rồi mới tạo gần đây. */
    @Query(
        """SELECT * FROM tasks ORDER BY isDone ASC,
           CASE WHEN dueDate IS NULL THEN 1 ELSE 0 END, dueDate ASC, createdAt DESC"""
    )
    fun observeAll(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): Task?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)

    /** Xóa việc kế tiếp (chưa làm) do một việc lặp lại sinh ra. */
    @Query("DELETE FROM tasks WHERE parentId = :parentId AND isDone = 0")
    suspend fun deletePendingChildren(parentId: Long)

    @Query("SELECT COUNT(*) FROM tasks WHERE isDone = 1 AND completedAt >= :since")
    fun observeDoneSince(since: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(coinsEarned), 0) FROM tasks WHERE isDone = 1 AND completedAt >= :since")
    fun observeCoinsSince(since: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM tasks WHERE isDone = 1 AND difficulty = 'HARD' AND completedAt >= :since")
    fun observeHardDoneSince(since: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM tasks WHERE isDone = 1 AND completedAt >= :since")
    suspend fun countDoneSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE isDone = 0")
    suspend fun countPending(): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE isDone = 1 AND difficulty = 'HARD' AND completedAt >= :since")
    suspend fun countHardDoneSince(since: Long): Int

    @Query("SELECT completedAt FROM tasks WHERE isDone = 1 AND completedAt IS NOT NULL")
    fun observeCompletedTimes(): Flow<List<Long>>

    @Query("SELECT COUNT(*) FROM tasks WHERE isDone = 0 AND dueDate IS NOT NULL AND dueDate + 86400000 <= :now")
    suspend fun countOverdue(now: Long): Int
}

@Dao
interface DailyDao {
    @Query("SELECT questId FROM daily_claims WHERE dayStart = :day")
    fun observeClaimed(day: Long): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM daily_claims WHERE dayStart = :day AND questId = :questId")
    suspend fun isClaimed(day: Long, questId: String): Int

    @Query("SELECT dayStart FROM daily_claims WHERE questId = :questId")
    suspend fun daysClaimed(questId: String): List<Long>

    @Insert
    suspend fun insert(claim: DailyClaim)
}

@Dao
interface BadgeDao {
    @Query("SELECT badgeId FROM badge_claims")
    fun observeClaimed(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM badge_claims WHERE badgeId = :id")
    suspend fun isClaimed(id: String): Int

    @Insert
    suspend fun insert(claim: BadgeClaim)
}

@Dao
interface OwnedItemDao {
    @Query("SELECT * FROM owned_items")
    fun observeAll(): Flow<List<OwnedItem>>

    @Query("SELECT * FROM owned_items WHERE itemId = :id")
    suspend fun get(id: String): OwnedItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: OwnedItem)

    @Query("UPDATE owned_items SET equipped = (itemId = :id)")
    suspend fun equipOnly(id: String)
}

@Dao
interface FocusDao {
    @Insert
    suspend fun insert(session: FocusSession): Long

    @Query("SELECT COALESCE(SUM(actualMinutes), 0) FROM focus_sessions WHERE status = 'COMPLETED' AND startTime >= :since")
    fun observeMinutesSince(since: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM focus_sessions WHERE status = 'COMPLETED' AND startTime >= :since")
    fun observeCompletedSince(since: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(actualMinutes), 0) FROM focus_sessions WHERE status = 'COMPLETED' AND startTime >= :since")
    suspend fun minutesSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM focus_sessions WHERE status = 'COMPLETED' AND startTime >= :since")
    suspend fun completedSince(since: Long): Int

    @Query("SELECT * FROM focus_sessions WHERE status = 'COMPLETED' ORDER BY startTime")
    fun observeCompleted(): Flow<List<FocusSession>>

    @Query("SELECT COUNT(*) FROM focus_sessions WHERE status != 'COMPLETED'")
    fun observeFailedCount(): Flow<Int>
}
