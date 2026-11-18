package com.midterm.petfocus.ui

import android.animation.ValueAnimator
import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.midterm.petfocus.R

/** Các hiệu ứng nhỏ khi nhận thưởng: chữ bay lên, nảy, đếm số. */
object RewardFx {

    /** Hiện một dòng chữ (vd "+25 🪙") ngay trên [anchor] rồi bay lên và mờ dần. */
    fun floatText(anchor: View, text: String, colorRes: Int = R.color.reward_text) {
        val activity = anchor.context as? Activity ?: return
        val root = activity.findViewById<ViewGroup>(android.R.id.content) as? FrameLayout ?: return
        val density = anchor.resources.displayMetrics.density

        val label = TextView(activity).apply {
            this.text = text
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(activity, colorRes))
            setShadowLayer(4 * density, 0f, 1 * density, ContextCompat.getColor(activity, R.color.bg_cream))
            gravity = Gravity.CENTER
        }
        val loc = IntArray(2).also { anchor.getLocationInWindow(it) }
        val rootLoc = IntArray(2).also { root.getLocationInWindow(it) }
        root.addView(label, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        label.post {
            label.x = (loc[0] - rootLoc[0] + anchor.width / 2f - label.width / 2f)
            label.y = (loc[1] - rootLoc[1] - label.height / 2f)
            label.animate()
                .translationYBy(-70 * density)
                .alpha(0f)
                .setDuration(1100)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { root.removeView(label) }
                .start()
        }
    }

    /** Nảy lên một nhịp, dùng khi pet lên cấp. */
    fun bounce(view: View) {
        view.animate().cancel()
        view.scaleX = 1f; view.scaleY = 1f
        view.animate().scaleX(1.25f).scaleY(1.25f).translationY(-30 * view.resources.displayMetrics.density)
            .setDuration(220)
            .withEndAction {
                view.animate().scaleX(1f).scaleY(1f).translationY(0f)
                    .setDuration(500).setInterpolator(OvershootInterpolator(3f)).start()
            }
            .start()
    }

    /** Đếm số chạy từ [from] tới [to] trên TextView, [format] dựng chữ hiển thị. */
    fun countTo(view: TextView, from: Int, to: Int, format: (Int) -> String) {
        if (from == to) { view.text = format(to); return }
        ValueAnimator.ofInt(from, to).apply {
            duration = 600
            addUpdateListener { view.text = format(it.animatedValue as Int) }
            start()
        }
    }
}
