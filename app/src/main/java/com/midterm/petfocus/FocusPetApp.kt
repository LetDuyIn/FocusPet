package com.midterm.petfocus

import android.app.Application
import com.midterm.petfocus.data.AppDatabase
import com.midterm.petfocus.data.GameRepository
import com.midterm.petfocus.reminder.Reminders
import com.midterm.petfocus.widget.PetWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

/** Tạo database và repository một lần cho cả app (DI thủ công, không cần Hilt). */
class FocusPetApp : Application() {
    val repository: GameRepository by lazy { GameRepository(AppDatabase.get(this)) }

    /** Phạm vi coroutine sống cùng app, dùng để giữ widget luôn khớp với dữ liệu. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Reminders.createChannel(this)
        Reminders.schedule(this)

        // Mỗi khi pet hoặc danh sách việc đổi (cho ăn, nhận xu, thêm/xong việc...) thì vẽ lại widget
        appScope.launch {
            combine(repository.pet, repository.tasks) { pet, tasks -> pet to tasks.count { !it.isDone } }
                .conflate()
                .collect { (pet, pending) -> PetWidget.render(this@FocusPetApp, pet, pending) }
        }
    }
}
