package com.midterm.petfocus

import android.graphics.Color
import android.os.Bundle
import android.text.InputFilter
import android.view.View
import android.widget.EditText
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.midterm.petfocus.data.PetState
import com.midterm.petfocus.databinding.FragmentPetBinding
import com.midterm.petfocus.logic.GameRules
import com.midterm.petfocus.logic.Mood
import com.midterm.petfocus.logic.PetAction
import com.midterm.petfocus.ui.RewardFx
import com.midterm.petfocus.ui.art.ArtIcons
import com.midterm.petfocus.ui.pet.PetReaction
import com.midterm.petfocus.ui.pet.PetViewModel
import com.midterm.petfocus.ui.pet.ShopSheet
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PetFragment : Fragment(R.layout.fragment_pet) {

    private val vm: PetViewModel by activityViewModels()

    private var binding: FragmentPetBinding? = null
    private var currentAnim: String? = null
    /** Trạng thái pet lần hiển thị trước, để biết vừa nhận thêm bao nhiêu xu/EXP. */
    private var lastShown: PetState? = null
    /** Đang hiện phản hồi của một hành động thì tạm không ghi đè lời thoại/animation theo tâm trạng. */
    private var reactionJob: Job? = null

    private lateinit var actionButtons: Map<PetAction, MaterialButton>

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentPetBinding.bind(view)
        binding = b
        currentAnim = "pet_idle.json" // layout đã tự phát pet_idle

        actionButtons = mapOf(
            PetAction.FEED to b.btnFeed,
            PetAction.CUDDLE to b.btnCuddle,
            PetAction.CLEAN to b.btnClean,
            PetAction.SLEEP to b.btnSleep
        )
        actionButtons.forEach { (action, btn) -> btn.setOnClickListener { vm.act(action) } }

        b.tvPetLevel.setOnLongClickListener { showRenameDialog(); true }
        b.cardPetStage.setOnClickListener { vm.act(PetAction.CUDDLE) }
        // Bấm vào ô xu để mở cửa hàng
        b.tvCoins.setOnClickListener {
            if (childFragmentManager.findFragmentByTag(ShopSheet.TAG) == null) {
                ShopSheet().show(childFragmentManager, ShopSheet.TAG)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.pet.collect { it?.let(::render) } }
                launch { vm.reactions.collect(::showReaction) }
                launch {
                    vm.backgroundColor.collect { hex ->
                        b.cardPetStage.setCardBackgroundColor(
                            hex?.let { Color.parseColor(it) }
                                ?: ContextCompat.getColor(requireContext(), R.color.surface_white)
                        )
                    }
                }
                // Mỗi giây: cập nhật đếm ngược thời gian hồi của các nút
                launch {
                    while (true) {
                        vm.pet.value?.let(::renderButtons)
                        delay(1_000)
                    }
                }
                // Mỗi phút: trừ chỉ số theo thời gian thật
                launch {
                    while (true) {
                        vm.refresh()
                        delay(60_000)
                    }
                }
            }
        }
    }

    private fun render(p: PetState) {
        val b = binding ?: return
        b.tvPetLevel.text = "Level ${p.level} • ${p.name} 🐱"
        playRewardEffects(b, p)
        b.progressHunger.progress = p.hunger.toInt()
        b.progressHappiness.progress = p.happiness.toInt()
        b.progressEnergy.progress = p.energy.toInt()
        b.progressClean.progress = p.cleanliness.toInt()

        val need = GameRules.expToNext(p.level)
        b.progressExp.max = need
        b.progressExp.progress = p.exp
        b.tvExp.text = "⭐ ${p.exp}/$need"

        if (reactionJob?.isActive != true) {
            b.tvPetDialogue.text = GameRules.dialogue(p)
            playAnim(animForMood(GameRules.mood(p)))
        }
        renderButtons(p)
    }

    /**
     * So với lần hiển thị trước: xu thay đổi thì đếm số và hiện "+N 🪙" bay lên,
     * EXP tăng thì hiện "+N EXP", lên cấp thì mèo nảy lên.
     * Lần đầu mở màn hình chỉ hiện số, không chạy hiệu ứng.
     */
    private fun playRewardEffects(b: FragmentPetBinding, p: PetState) {
        val prev = lastShown
        lastShown = p
        val coinText = { c: Int -> "🪙 $c Xu 🛒" }
        if (prev == null) { b.tvCoins.text = coinText(p.coins); return }

        val coinDelta = p.coins - prev.coins
        RewardFx.countTo(b.tvCoins, prev.coins, p.coins, coinText)
        if (coinDelta != 0) {
            RewardFx.floatText(
                b.tvCoins,
                if (coinDelta > 0) "+$coinDelta 🪙" else "$coinDelta 🪙",
                if (coinDelta > 0) R.color.reward_text else R.color.danger_text
            )
        }
        val gainedExp = p.level > prev.level || p.exp > prev.exp
        if (p.level > prev.level) {
            RewardFx.bounce(b.lottiePet)
            RewardFx.floatText(b.lottiePet, "⭐ LEVEL ${p.level}!", R.color.peach_dark)
        } else if (gainedExp) {
            RewardFx.floatText(b.progressExp, "+${p.exp - prev.exp} EXP", R.color.peach_dark)
        }
    }

    private fun renderButtons(p: PetState) {
        val now = System.currentTimeMillis()
        actionButtons.forEach { (action, btn) ->
            val left = GameRules.cooldownLeft(p, action, now)
            btn.text = when {
                left > 0 -> "${action.label}\n${(left + 999) / 1000}s"
                action.cost > 0 -> "${action.label}\n${action.cost}🪙"
                // Luôn 2 dòng để 4 nút cao bằng nhau
                else -> "${action.label}\nMiễn phí"
            }
            btn.alpha = if (left > 0 || p.coins < action.cost) 0.5f else 1f
        }
    }

    private fun showReaction(r: PetReaction) {
        val b = binding ?: return
        b.tvPetDialogue.text = r.text
        r.anim?.let(::playAnim)
        if (r.levelUp) {
            val p = vm.pet.value
            Snackbar.make(
                b.root,
                ArtIcons.art(requireContext(), "🎉 ${p?.name ?: "Thú cưng"} lên Level ${p?.level}! Thưởng thêm 🪙 và vui vẻ đầy.")!!,
                Snackbar.LENGTH_LONG
            ).show()
        }
        reactionJob?.cancel()
        reactionJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(4_000)
            reactionJob = null
            vm.pet.value?.let(::render)
        }
    }

    private fun animForMood(mood: Mood) = when (mood) {
        Mood.SLEEPY -> "pet_sleep.json"
        Mood.HAPPY -> "pet_happy.json"
        else -> "pet_idle.json"
    }

    /** Chỉ đổi file Lottie khi thật sự khác, tránh parse lại và giật hình. */
    private fun playAnim(file: String) {
        val lottie = binding?.lottiePet ?: return
        if (file == currentAnim) return
        currentAnim = file
        lottie.setAnimation(file)
        lottie.playAnimation()
    }

    private fun showRenameDialog() {
        val input = EditText(requireContext()).apply {
            setText(vm.pet.value?.name)
            filters = arrayOf(InputFilter.LengthFilter(20))
            setSelection(text.length)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Đặt tên thú cưng")
            .setView(input)
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Lưu") { _, _ -> vm.rename(input.text.toString()) }
            .show()
    }

    override fun onDestroyView() {
        binding = null
        reactionJob = null
        lastShown = null
        super.onDestroyView()
    }
}
