package com.midterm.petfocus.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.midterm.petfocus.FocusPetApp
import com.midterm.petfocus.MainActivity
import com.midterm.petfocus.R
import com.midterm.petfocus.data.PetState
import com.midterm.petfocus.logic.GameRules
import com.midterm.petfocus.logic.GrowthStage
import com.midterm.petfocus.logic.Mood
import com.midterm.petfocus.ui.art.ArtIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Widget màn hình chính: mèo, cấp, tâm trạng, No bụng / Vui vẻ, xu và số việc còn lại.
 *
 * Được cập nhật khi: hệ thống gọi định kỳ (30 phút), app ghi dữ liệu mới
 * (FocusPetApp theo dõi Flow của pet và công việc), và worker nhắc nhở chạy mỗi giờ.
 */
class PetWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // Đọc database ở luồng nền; goAsync giữ receiver sống tới khi xong
        val pending = goAsync()
        scope.launch {
            try {
                refresh(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Launcher có cho app tự ghim widget không, và người dùng chưa đặt widget nào. */
        fun canRequestPin(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            val placed = manager.getAppWidgetIds(ComponentName(context, PetWidget::class.java)).isNotEmpty()
            return manager.isRequestPinAppWidgetSupported && !placed
        }

        /** Mở hộp thoại của hệ thống để người dùng thêm widget ra màn hình chính. */
        fun requestPin(context: Context): Boolean =
            AppWidgetManager.getInstance(context)
                .requestPinAppWidget(ComponentName(context, PetWidget::class.java), null, null)

        /** Đọc dữ liệu mới nhất (có tính chỉ số giảm theo thời gian) rồi vẽ lại mọi widget. */
        suspend fun refresh(context: Context) {
            val repo = (context.applicationContext as FocusPetApp).repository
            render(context, repo.currentPet(), repo.pendingTaskCount())
        }

        /** Vẽ lại tất cả widget đang đặt trên màn hình chính. Không có widget nào thì bỏ qua. */
        fun render(context: Context, pet: PetState, pendingTasks: Int) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PetWidget::class.java))
            if (ids.isEmpty()) return

            val views = RemoteViews(context.packageName, R.layout.widget_pet)
            val stage = GrowthStage.of(pet.level)
            val mood = GameRules.mood(pet)
            val sad = mood in setOf(Mood.HUNGRY, Mood.SAD, Mood.ANGRY, Mood.DIRTY)

            views.setImageViewResource(R.id.iv_pet, if (sad) R.drawable.art_cat_sad else R.drawable.art_cat)
            val accessory = when (stage) {
                GrowthStage.KITTEN -> 0
                GrowthStage.JUNIOR -> R.drawable.art_bow
                GrowthStage.ADULT -> R.drawable.art_gradcap
                GrowthStage.ROYAL -> R.drawable.art_crown
            }
            views.setViewVisibility(R.id.iv_accessory, if (accessory == 0) View.GONE else View.VISIBLE)
            if (accessory != 0) views.setImageViewResource(R.id.iv_accessory, accessory)

            views.setTextViewText(R.id.tv_name, "${pet.name} • Lv.${pet.level} ${stage.label}")
            // Widget chỉ hiện được chữ thường nên bỏ emoji trong lời thoại
            views.setTextViewText(R.id.tv_mood, ArtIcons.plain(GameRules.dialogue(pet)))
            views.setProgressBar(R.id.progress_hunger, 100, pet.hunger.toInt(), false)
            views.setProgressBar(R.id.progress_happiness, 100, pet.happiness.toInt(), false)
            views.setTextViewText(R.id.tv_coins, "${pet.coins} xu")
            views.setTextViewText(
                R.id.tv_tasks,
                if (pendingTasks == 0) "Hết việc rồi!" else "Còn $pendingTasks việc"
            )

            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)

            manager.updateAppWidget(ids, views)
        }
    }
}
