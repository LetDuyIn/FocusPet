package com.midterm.petfocus.ui.tasks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.midterm.petfocus.FocusPetApp
import com.midterm.petfocus.data.Repeat
import com.midterm.petfocus.data.Task
import com.midterm.petfocus.logic.GameRules
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TaskFilter { ALL, TODO, OVERDUE, DONE }

class TasksViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as FocusPetApp).repository

    val filter = MutableStateFlow(TaskFilter.ALL)

    val tasks: StateFlow<List<Task>> = combine(repo.tasks, filter) { list, f ->
        val now = System.currentTimeMillis()
        when (f) {
            TaskFilter.ALL -> list
            TaskFilter.TODO -> list.filter { !it.isDone }
            TaskFilter.OVERDUE -> list.filter { GameRules.isOverdue(it, now) }
            TaskFilter.DONE -> list.filter { it.isDone }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val summary: StateFlow<String> = combine(repo.tasks, repo.doneToday(), repo.taskCoinsToday()) { list, done, coins ->
        val now = System.currentTimeMillis()
        val left = list.count { !it.isDone }
        val overdue = list.count { GameRules.isOverdue(it, now) }
        buildString {
            append("Hôm nay xong $done việc • +$coins xu. Còn $left việc chưa làm")
            if (overdue > 0) append(", $overdue việc quá hạn ⚠️")
            append(".")
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    fun save(task: Task) = viewModelScope.launch { repo.saveTask(task) }

    fun complete(task: Task) = viewModelScope.launch {
        val (_, expectedExp) = GameRules.taskReward(task, System.currentTimeMillis())
        val reward = repo.completeTask(task.id) ?: return@launch
        val late = if (reward.coins < task.difficulty.coins) " (trễ hạn nên chỉ nhận một nửa)" else ""
        var msg = "Xong việc! +${reward.coins} xu • +${reward.exp} EXP$late"
        if (reward.exp < expectedExp) msg += "\n😾 Mèo đang giận nên chỉ nhận nửa EXP"
        if (task.repeat != Repeat.NONE) msg += "\n🔁 Đã tạo việc cho lần kế tiếp"
        if (reward.levelsGained > 0) msg += "\n🎉 Thú cưng đã lên cấp!"
        _messages.emit(msg)
    }

    fun uncomplete(task: Task) = viewModelScope.launch {
        val r = repo.uncompleteTask(task.id) ?: return@launch
        _messages.emit("Đã bỏ đánh dấu. Trừ lại ${r.coins} xu • ${r.exp} EXP")
    }

    fun delete(task: Task) = viewModelScope.launch { repo.deleteTask(task) }

    fun restore(task: Task) = viewModelScope.launch { repo.restoreTask(task) }
}
