package com.midterm.petfocus

import android.app.Application
import com.midterm.petfocus.data.AppDatabase
import com.midterm.petfocus.data.GameRepository
import com.midterm.petfocus.reminder.Reminders

/** Tạo database và repository một lần cho cả app (DI thủ công, không cần Hilt). */
class FocusPetApp : Application() {
    val repository: GameRepository by lazy { GameRepository(AppDatabase.get(this)) }

    override fun onCreate() {
        super.onCreate()
        Reminders.createChannel(this)
        Reminders.schedule(this)
    }
}
