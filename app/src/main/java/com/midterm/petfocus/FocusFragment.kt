package com.midterm.petfocus

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.midterm.petfocus.databinding.FragmentFocusBinding
import com.midterm.petfocus.logic.GameRules
import com.midterm.petfocus.ui.focus.FocusUiState
import com.midterm.petfocus.ui.focus.FocusViewModel
import com.midterm.petfocus.ui.pet.PetViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class FocusFragment : Fragment(R.layout.fragment_focus) {

    private val vm: FocusViewModel by activityViewModels()
    private val petVm: PetViewModel by activityViewModels()

    private val peach = Color.parseColor("#FF9F6B")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentFocusBinding.bind(view)
        val presets = mapOf(b.btnPreset15 to 15, b.btnPreset25 to 25, b.btnPreset45 to 45)

        presets.forEach { (btn, min) -> btn.setOnClickListener { vm.select(min) } }

        // Nhấn giữ đồng hồ để chọn phiên 1 phút (tiện khi demo/kiểm thử)
        b.cardTimerCircle.setOnLongClickListener {
            if (!vm.state.value.running) {
                vm.select(1)
                Toast.makeText(requireContext(), "Chế độ demo: phiên 1 phút", Toast.LENGTH_SHORT).show()
            }
            true
        }

        b.btnStartFocus.setOnClickListener {
            if (vm.state.value.running) confirmGiveUp() else vm.start()
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
        presets: Map<MaterialButton, Int>,
        s: FocusUiState,
        today: String,
        petName: String
    ) {
        val totalSec = (s.remainingMs + 999) / 1000
        b.tvTimerCountdown.text = "%02d:%02d".format(totalSec / 60, totalSec % 60)

        val (coins, exp) = GameRules.focusReward(s.selectedMinutes)
        b.tvFocusDesc.text = if (s.running)
            "Đừng rời app! Tắt màn hình thì không sao. Phần thưởng: +$coins xu • +$exp EXP"
        else "Hoàn thành ${s.selectedMinutes} phút để nhận +$coins xu • +$exp EXP"

        b.tvSessionStatus.text = when {
            s.away -> "⚠️ Quay lại ngay kẻo thất bại!"
            s.running -> "$petName đang ngồi học cùng bạn"
            else -> today
        }

        b.btnStartFocus.text = if (s.running) "Bỏ cuộc" else "Bắt đầu tập trung"
        b.btnStartFocus.backgroundTintList =
            ColorStateList.valueOf(if (s.running) Color.parseColor("#8A87A0") else peach)

        presets.forEach { (btn, min) ->
            val selected = min == s.selectedMinutes
            btn.isEnabled = !s.running
            btn.backgroundTintList = ColorStateList.valueOf(if (selected) peach else Color.WHITE)
            btn.setTextColor(if (selected) Color.WHITE else peach)
            btn.strokeColor = ColorStateList.valueOf(peach)
            btn.strokeWidth = if (selected) 0 else resources.displayMetrics.density.toInt()
            btn.alpha = if (s.running && !selected) 0.4f else 1f
        }
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
