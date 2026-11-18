package com.midterm.petfocus

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.midterm.petfocus.databinding.FragmentFocusBinding
import com.midterm.petfocus.logic.GameRules
import com.midterm.petfocus.ui.focus.FocusMode
import com.midterm.petfocus.ui.focus.FocusUiState
import com.midterm.petfocus.ui.focus.FocusViewModel
import com.midterm.petfocus.ui.pet.PetViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class FocusFragment : Fragment(R.layout.fragment_focus) {

    private val vm: FocusViewModel by activityViewModels()
    private val petVm: PetViewModel by activityViewModels()

    private val peach get() = ContextCompat.getColor(requireContext(), R.color.peach)
    private val mint get() = ContextCompat.getColor(requireContext(), R.color.mint)
    private val surface get() = ContextCompat.getColor(requireContext(), R.color.surface_white)
    private val muted get() = ContextCompat.getColor(requireContext(), R.color.text_secondary)

    private val presetMinutes = listOf(15, 25, 45)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentFocusBinding.bind(view)
        val presets = listOf<MaterialButton>(b.btnPreset15, b.btnPreset25, b.btnPreset45)

        presets.forEachIndexed { i, btn -> btn.setOnClickListener { vm.select(presetMinutes[i]) } }
        b.btnPresetCustom.setOnClickListener { showCustomDialog() }

        // Nhấn giữ đồng hồ để chọn phiên 1 phút (tiện khi demo/kiểm thử)
        b.cardTimerCircle.setOnLongClickListener {
            if (!vm.state.value.running) {
                vm.select(1)
                Toast.makeText(requireContext(), "Chế độ demo: phiên 1 phút", Toast.LENGTH_SHORT).show()
            }
            true
        }

        b.btnStartFocus.setOnClickListener {
            val s = vm.state.value
            when {
                !s.running -> vm.start()
                s.mode == FocusMode.BREAK -> vm.giveUp() // bỏ qua giờ nghỉ, không bị phạt
                else -> confirmGiveUp()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(vm.state, vm.todaySummary, petVm.pet) { s, today, pet ->
                        Triple(s, today, pet?.name ?: "Mimi")
                    }.collect { (s, today, name) -> render(b, presets, s, today, name) }
                }
            }
        }
    }

    private fun render(
        b: FragmentFocusBinding,
        presets: List<MaterialButton>,
        s: FocusUiState,
        today: String,
        petName: String
    ) {
        val isBreak = s.running && s.mode == FocusMode.BREAK
        val accent = if (isBreak) mint else peach

        val totalSec = (s.remainingMs + 999) / 1000
        b.tvTimerCountdown.text = "%02d:%02d".format(totalSec / 60, totalSec % 60)
        b.cardTimerCircle.strokeColor = accent

        val (coins, exp) = GameRules.focusReward(s.selectedMinutes)
        b.tvFocusDesc.text = when {
            isBreak -> "🌿 Giờ nghỉ ${s.breakMinutes} phút: đứng dậy, uống nước. Rời app thoải mái!"
            s.running -> "Đừng rời app! Tắt màn hình thì không sao. Phần thưởng: +$coins 🪙 • +$exp EXP"
            else -> "Hoàn thành ${s.selectedMinutes} phút để nhận +$coins 🪙 • +$exp EXP"
        }

        b.tvSessionStatus.text = when {
            s.away -> "⚠️ Quay lại ngay kẻo thất bại!"
            isBreak -> "$petName đang nghỉ ngơi cùng bạn 😴"
            s.running -> "$petName đang ngồi học cùng bạn"
            else -> today
        }

        b.btnStartFocus.text = when {
            isBreak -> "Bỏ qua giờ nghỉ"
            s.running -> "Bỏ cuộc"
            else -> "Bắt đầu tập trung"
        }
        b.btnStartFocus.backgroundTintList =
            ColorStateList.valueOf(if (s.running && !isBreak) muted else accent)

        val isCustom = s.selectedMinutes !in presetMinutes
        b.btnPresetCustom.text = if (isCustom) "${s.selectedMinutes} phút" else "Khác"
        (presets + b.btnPresetCustom).forEachIndexed { i, btn ->
            val selected = if (i < presetMinutes.size) presetMinutes[i] == s.selectedMinutes else isCustom
            btn.isEnabled = !s.running
            btn.isAllCaps = false
            btn.backgroundTintList = ColorStateList.valueOf(if (selected) peach else surface)
            btn.setTextColor(if (selected) Color.WHITE else peach)
            btn.strokeColor = ColorStateList.valueOf(peach)
            btn.strokeWidth = if (selected) 0 else resources.displayMetrics.density.toInt()
            btn.alpha = if (s.running && !selected) 0.4f else 1f
        }
    }

    /** Hộp thoại kéo thanh trượt để chọn từ 5 đến 120 phút. */
    private fun showCustomDialog() {
        val ctx = requireContext()
        val current = vm.state.value.selectedMinutes.coerceIn(5, GameRules.MAX_FOCUS_MINUTES)
        val label = TextView(ctx).apply {
            textSize = 28f
            setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
        val slider = Slider(ctx).apply {
            valueFrom = 5f
            valueTo = GameRules.MAX_FOCUS_MINUTES.toFloat()
            stepSize = 5f
            value = (current / 5 * 5).coerceAtLeast(5).toFloat()
        }
        fun update() {
            val (c, e) = GameRules.focusReward(slider.value.toInt())
            label.text = "${slider.value.toInt()} phút\n+$c xu • +$e EXP"
        }
        slider.addOnChangeListener { _, _, _ -> update() }
        update()
        val pad = (20 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(label)
            addView(slider)
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle("Chọn thời gian tập trung")
            .setView(content)
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Chọn") { _, _ -> vm.select(slider.value.toInt()) }
            .show()
    }

    private fun confirmGiveUp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Bỏ cuộc?")
            .setMessage("Bạn sẽ không nhận được thưởng và thú cưng sẽ buồn một chút.")
            .setNegativeButton("Tiếp tục tập trung", null)
            .setPositiveButton("Bỏ cuộc") { _, _ -> vm.giveUp() }
            .show()
    }
}
