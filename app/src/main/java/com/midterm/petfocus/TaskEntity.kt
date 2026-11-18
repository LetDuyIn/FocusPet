package com.midterm.petfocus

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TaskDifficulty(
    val displayName: String,
    val rewardCoins: Int,
    val moodPenalty: Int
) {
    EASY("Dễ", 10, 5),
    MEDIUM("Trung bình", 25, 10),
    HARD("Khó", 50, 15)
}

enum class TaskStatus {
    PENDING,    // Đang cần làm
    COMPLETED,  // Đã hoàn thành (+xu)
    FAILED      // Không hoàn thành (-tâm trạng pet)
}

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val description: String = "",
    val difficulty: String = TaskDifficulty.EASY.name,
    val rewardCoins: Int = 10,
    val moodPenalty: Int = 5,
    val status: String = TaskStatus.PENDING.name,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun getDifficultyEnum(): TaskDifficulty =
        runCatching { TaskDifficulty.valueOf(difficulty) }.getOrDefault(TaskDifficulty.EASY)

    fun getStatusEnum(): TaskStatus =
        runCatching { TaskStatus.valueOf(status) }.getOrDefault(TaskStatus.PENDING)
}