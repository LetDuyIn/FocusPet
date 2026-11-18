package com.midterm.petfocus.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.midterm.petfocus.FocusPetApp
import com.midterm.petfocus.MainActivity
import com.midterm.petfocus.R
import com.midterm.petfocus.logic.GameRules
import com.midterm.petfocus.logic.Mood
import com.midterm.petfocus.ui.art.ArtIcons
import java.util.concurrent.TimeUnit

object Reminders {
    private const val CHANNEL_ID = "pet_reminders"
    private const val WORK_NAME = "pet_reminder"
    private const val NOTIFICATION_ID = 1001
    private const val PREFS = "reminders"
    private const val KEY_LAST = "last_notified_at"
    /** Không nhắc dồn dập: tối thiểu 3 giờ mới nhắc một lần. */
    private const val MIN_GAP_MS = 3 * 60 * 60 * 1000L

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Nhắc chăm thú cưng", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "Nhắc khi thú cưng đói, buồn hoặc có việc quá hạn" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Kiểm tra mỗi giờ. KEEP: nếu đã lên lịch rồi thì giữ nguyên, không tạo trùng. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<PetReminderWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun notifyIfDue(context: Context, title: String, text: String, now: Long = System.currentTimeMillis()) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (now - prefs.getLong(KEY_LAST, 0) < MIN_GAP_MS) return
        if (!canNotify(context)) return

        val intent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            prefs.edit().putLong(KEY_LAST, now).apply()
        } catch (_: SecurityException) {
            // Người dùng vừa thu hồi quyền thông báo
        }
    }
}

/** Chạy nền mỗi giờ: cập nhật chỉ số pet và nhắc nếu pet cần chăm hoặc có việc quá hạn. */
class PetReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as FocusPetApp).repository
        val pet = repo.currentPet()
        val overdue = repo.overdueCount()
        val text = when (GameRules.mood(pet)) {
            Mood.ANGRY -> "${pet.name} giận dỗi rồi vì bị bỏ đói! Vào cho ăn để làm lành nhé 😾"
            Mood.HUNGRY -> "${pet.name} đói lắm rồi, vào cho ăn đi Sen ơi 🍣"
            Mood.SAD -> "${pet.name} đang buồn, vào nựng một chút nhé 😿"
            Mood.SLEEPY -> "${pet.name} mệt rồi, cho ${pet.name} đi ngủ nha 😴"
            Mood.DIRTY -> "${pet.name} bẩn quá, tắm cho ${pet.name} đi 🛁"
            else -> if (overdue > 0) "Bạn có $overdue việc đã quá hạn. Làm xong để kiếm xu nhé!" else null
        }
        if (text != null) Reminders.notifyIfDue(applicationContext, "FocusPet", ArtIcons.plain(text))
        return Result.success()
    }
}
