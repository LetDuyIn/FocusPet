package com.midterm.petfocus.ui.focus

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.midterm.petfocus.FocusPetApp
import com.midterm.petfocus.data.SessionStatus
import com.midterm.petfocus.logic.GameRules
import com.midterm.petfocus.logic.Reward
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FocusUiState(
    val selectedMinutes: Int = 25,
    val running: Boolean = false,
    val remainingMs: Long = 25 * 60_000L,
    /** Đang ở ngoài app và đang đếm thời gian ân hạn. */
    val away: Boolean = false
)

sealed class FocusResult {
    data class Completed(val minutes: Int, val reward: Reward) : FocusResult()
    data class Failed(val reason: String) : FocusResult()
}

/**
 * Dùng chung cho cả Activity (để biết khi nào người dùng rời app)
 * nên được tạo bằng activityViewModels(). Đồng hồ vẫn chạy khi đổi tab.
 *
 * Luật: tắt màn hình (úp điện thoại) vẫn tính là đang tập trung.
 * Mở app khác khi màn hình đang bật quá 5 giây thì thất bại.
 */
class FocusViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as FocusPetApp).repository

    private val _state = MutableStateFlow(FocusUiState())
    val state: StateFlow<FocusUiState> = _state.asStateFlow()

    /** Kết quả phiên chưa được hiển thị; UI hiện xong thì gọi [consumeResult]. */
    private val _result = MutableStateFlow<FocusResult?>(null)
    val result: StateFlow<FocusResult?> = _result.asStateFlow()

    val todaySummary: StateFlow<String> =
        combine(repo.focusMinutesToday(), repo.focusSessionsToday()) { min, count ->
            "Hôm nay: $min phút • $count phiên hoàn thành"
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private var tickJob: Job? = null
    private var graceJob: Job? = null
    private var endElapsed = 0L
    private var startWallTime = 0L

    fun select(minutes: Int) {
        if (_state.value.running) return
        _state.update { it.copy(selectedMinutes = minutes, remainingMs = minutes * 60_000L) }
    }

    fun start() {
        if (_state.value.running) return
        val minutes = _state.value.selectedMinutes
        startWallTime = System.currentTimeMillis()
        // Dùng elapsedRealtime để không bị ảnh hưởng khi người dùng chỉnh giờ hệ thống
        endElapsed = SystemClock.elapsedRealtime() + minutes * 60_000L
        _state.update { it.copy(running = true, remainingMs = minutes * 60_000L, away = false) }
        tickJob = viewModelScope.launch {
            while (true) {
                val left = endElapsed - SystemClock.elapsedRealtime()
                if (left <= 0) { complete(); break }
                _state.update { it.copy(remainingMs = left) }
                delay(250)
            }
        }
    }

    private suspend fun complete() {
        graceJob?.cancel()
        val minutes = _state.value.selectedMinutes
        reset()
        val reward = repo.completeFocus(startWallTime, minutes)
        _result.value = FocusResult.Completed(minutes, reward)
    }

    /** Người dùng tự bấm bỏ cuộc: phạt nhẹ hơn rời app. */
    fun giveUp() {
        if (!_state.value.running) return
        stop(SessionStatus.CANCELLED, "Bạn đã bỏ cuộc. Thú cưng hơi buồn một chút 😿")
    }

    private fun stop(status: SessionStatus, reason: String) {
        tickJob?.cancel(); graceJob?.cancel()
        val minutes = _state.value.selectedMinutes
        reset()
        viewModelScope.launch {
            repo.failFocus(startWallTime, minutes, status)
            _result.value = FocusResult.Failed(reason)
        }
    }

    private fun reset() {
        _state.update { it.copy(running = false, away = false, remainingMs = it.selectedMinutes * 60_000L) }
    }

    /** Activity gọi khi app bị đưa xuống nền. */
    fun onAppHidden(screenOn: Boolean) {
        if (!_state.value.running || !screenOn) return
        _state.update { it.copy(away = true) }
        graceJob = viewModelScope.launch {
            delay(GameRules.LEAVE_GRACE_MS)
            if (_state.value.running) {
                stop(SessionStatus.FAILED, "Bạn đã rời app khi đang tập trung. Thú cưng buồn lắm 💔")
            }
        }
    }

    /** Activity gọi khi app quay lại. */
    fun onAppShown() {
        graceJob?.cancel()
        _state.update { it.copy(away = false) }
    }

    fun consumeResult() { _result.value = null }
}
