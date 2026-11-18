package com.midterm.petfocus.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Độ khó của công việc quyết định phần thưởng. */
enum class Difficulty(val label: String, val coins: Int, val exp: Int) {
    EASY("Dễ", 10, 15),
    MEDIUM("Vừa", 25, 40),
    HARD("Khó", 50, 80)
}

enum class SessionStatus { COMPLETED, FAILED, CANCELLED }

/** Trạng thái thú cưng và ví xu. Bảng này luôn chỉ có 1 dòng (id = 1). */
@Entity(tableName = "pet")
data class PetState(
    @PrimaryKey val id: Int = 1,
    val name: String = "Mimi",
    val level: Int = 1,
    val exp: Int = 0,
    val coins: Int = 50,
    val hunger: Float = 80f,
    val happiness: Float = 80f,
    val energy: Float = 80f,
    val cleanliness: Float = 80f,
    val lastUpdated: Long = System.currentTimeMillis(),
    val lastFeedAt: Long = 0,
    val lastCuddleAt: Long = 0,
    val lastCleanAt: Long = 0,
    val lastSleepAt: Long = 0
)

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    /** Ngày hạn chót (mốc 0h của ngày đó), null nếu không có hạn. */
    val dueDate: Long? = null,
    val isDone: Boolean = false,
    val completedAt: Long? = null,
    val coinsEarned: Int = 0,
    val expEarned: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "focus_sessions")
data class FocusSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val plannedMinutes: Int,
    val actualMinutes: Int,
    val status: SessionStatus,
    val coinsEarned: Int = 0,
    val expEarned: Int = 0
)
