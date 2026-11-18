package com.midterm.petfocus

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.midterm.petfocus.databinding.FragmentStatsBinding
import com.midterm.petfocus.databinding.ItemBadgeBinding
import com.midterm.petfocus.databinding.ItemStatBinding
import com.midterm.petfocus.logic.Badge
import com.midterm.petfocus.logic.StatsSnapshot
import com.midterm.petfocus.ui.RewardFx
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class StatsFragment : Fragment(R.layout.fragment_stats) {

    private val repo get() = (requireActivity().application as FocusPetApp).repository

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentStatsBinding.bind(view)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(repo.stats, repo.claimedBadges) { s, claimed -> s to claimed }
                    .collect { (s, claimed) -> render(b, s, claimed) }
            }
        }
    }

    private fun render(b: FragmentStatsBinding, s: StatsSnapshot, claimed: Set<String>) {
        setStat(b.statStreak, "🔥 ${s.streak}", "Chuỗi ngày (kỷ lục ${s.bestStreak})")
        setStat(b.statMinutes, formatMinutes(s.totalFocusMinutes), "Tổng thời gian tập trung")
        setStat(b.statTasks, "✅ ${s.tasksDone}", "Công việc đã xong")
        val rate = if (s.focusSessions + s.failedSessions == 0) 0
        else s.focusSessions * 100 / (s.focusSessions + s.failedSessions)
        setStat(b.statSessions, "⏱️ ${s.focusSessions}", "Phiên thành công ($rate%)")

        b.chartFocus.days = s.last7Days

        b.tvStatsSubtitle.text = when {
            s.streak == 0 -> "Hôm nay hãy làm một việc hoặc một phiên tập trung để bắt đầu chuỗi!"
            else -> "Bạn đang giữ chuỗi ${s.streak} ngày, cố lên! 💪"
        }

        val unlocked = Badge.entries.count { it.unlocked(s) }
        b.tvBadgesTitle.text = "Huy hiệu ($unlocked/${Badge.entries.size})"
        b.layoutBadges.removeAllViews()
        // Thứ tự: chờ nhận thưởng → đã nhận → chưa mở khóa
        Badge.entries.sortedBy {
            when {
                it.unlocked(s) && it.name !in claimed -> 0
                it.unlocked(s) -> 1
                else -> 2
            }
        }.forEach { badge ->
            val item = ItemBadgeBinding.inflate(LayoutInflater.from(requireContext()), b.layoutBadges, false)
            val ok = badge.unlocked(s)
            val isClaimed = badge.name in claimed
            item.tvEmoji.text = if (ok) badge.emoji else "🔒"
            item.tvTitle.text = badge.title
            item.tvDesc.text = "${badge.description} • +${badge.rewardCoins} 🪙"
            item.tvState.text = if (isClaimed) "✔️" else ""
            item.btnClaim.isVisible = ok && !isClaimed
            item.btnClaim.text = "Nhận ${badge.rewardCoins} 🪙"
            item.btnClaim.setOnClickListener { btn -> claim(b, badge, s, btn) }
            item.root.alpha = if (ok) 1f else 0.5f
            b.layoutBadges.addView(item.root)
        }
    }

    private fun claim(b: FragmentStatsBinding, badge: Badge, s: StatsSnapshot, anchor: View) {
        anchor.isEnabled = false
        RewardFx.floatText(anchor, "+${badge.rewardCoins} 🪙")
        viewLifecycleOwner.lifecycleScope.launch {
            val reward = repo.claimBadge(badge, s)
            if (reward != null) {
                Snackbar.make(b.root, "${badge.emoji} Đã nhận ${reward.coins} xu từ huy hiệu “${badge.title}”", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun setStat(stat: ItemStatBinding, value: String, label: String) {
        stat.tvValue.text = value
        stat.tvLabel.text = label
    }

    private fun formatMinutes(min: Int) = if (min < 60) "📚 ${min}p" else "📚 ${min / 60}h${"%02d".format(min % 60)}"
}
