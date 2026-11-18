package com.midterm.petfocus

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.midterm.petfocus.data.AppDatabase
import com.midterm.petfocus.data.PetState
import com.midterm.petfocus.reminder.PetReminderWorker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Chạy thẳng PetReminderWorker (không chờ WorkManager lên lịch mỗi giờ)
 * để kiểm tra thông báo có hiện ra đúng lúc không.
 */
@RunWith(AndroidJUnit4::class)
class PetReminderWorkerTest {

    @get:Rule val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val context = ApplicationProvider.getApplicationContext<FocusPetApp>()
    private val notifications = context.getSystemService(NotificationManager::class.java)

    @Before fun setUp() {
        notifications.cancelAll()
        context.getSharedPreferences("reminders", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun setPet(pet: PetState) = runBlocking { AppDatabase.get(context).petDao().upsert(pet) }

    private fun runWorker(): ListenableWorker.Result = runBlocking {
        TestListenableWorkerBuilder<PetReminderWorker>(context).build().doWork()
    }

    private fun hasReminder() = notifications.activeNotifications.any { it.packageName == context.packageName }

    @Test fun meoDoi_thiGuiThongBao() {
        setPet(PetState(hunger = 5f, lastUpdated = System.currentTimeMillis()))
        assertEquals(ListenableWorker.Result.success(), runWorker())
        assertTrue(hasReminder())
    }

    @Test fun meoKhoe_thiKhongLamPhien() {
        setPet(PetState(hunger = 90f, happiness = 90f, energy = 90f, cleanliness = 90f, lastUpdated = System.currentTimeMillis()))
        runWorker()
        assertFalse(hasReminder())
    }

    @Test fun vuaNhacXong_thiChoDu3GioMoiNhacTiep() {
        setPet(PetState(hunger = 5f, lastUpdated = System.currentTimeMillis()))
        runWorker()
        notifications.cancelAll()
        runWorker()
        assertFalse(hasReminder())
    }
}
