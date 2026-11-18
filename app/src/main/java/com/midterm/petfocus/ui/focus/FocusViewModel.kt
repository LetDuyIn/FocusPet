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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** FOCUS: phiên tập trung (rời app là thua). BREAK: giờ nghỉ Pomodoro (rời app thoải mái). */
enum class FocusMode { FOCUS, BREAK }

data class FocusUiState(
    val selectedMinutes: Int = 25,
    val mode: FocusMode = FocusMode.FOCUS,
    val running: Boolean = false,
    val remainingMs: Long = 25 * 60_000L,
    /** Số phút của giờ nghỉ đang chạy. */
    val breakMinutes: Int = 0,
    /** Đang ở ngoài app và đang đếm thời gian ân hạn. */
    val away: Boolean = false
)

sealed class FocusResult {
    /** [breakMinutes]: giờ nghỉ gợi ý (5 phút, hoặc 15 phút sau mỗi 4 phiên). */
    data class Completed(val minutes: Int, val reward: Reward, val breakMinutes: Int) : FocusResult()
    data class Failed(val reason: String) : FocusResult()
    data object BreakDone : FocusResult()
}

/**
 * Dùng chung cho cả Activity (để biết khi nào người dùng rời app)
 * nên được tạo bằng activityViewModels(). Đồng hồ vẫn chạy khi đổi tab.
 *
 * Luật: tắt màn hình (úp điện thoại) vẫn tính là đang tập trung.
 * Mở app khác khi màn hình đang bật quá 5 giây thì thất bại.
 * Giờ nghỉ thì được rời app thoải mái.
 */
class FocusViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as FocusPetApp).repository

    private val _state = MutableStateFlow(FocusUiState())
    val state: StateFlow<FocusUiState> = _state.asStateFlow()

    /** Kết quả phiên chưa được hiển thị; UI hiện xong thì gọi [consumeResult]. */
    private val _result = MutableStateFlow<FocusResult?>(null)
    val result: StateFlow<FocusResult?> = _result.asStateFlow()

    val sessionsToday: StateFlow<Int> = repo.focusSessionsToday()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val todaySummary: StateFlow<String> =
        combine(repo.focusMinutesToday(), repo.focusSessionsToday()) { min, count ->
            val toLong = GameRules.SESSIONS_BEFORE_LONG_BREAK - count % GameRules.SESSIONS_BEFORE_LONG_BREAK
            "Hôm nay: $min phút • $count phiên • còn $toLong phiên tới nghỉ dài"
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private var tickJob: Job? = null
    private var graceJob: Job? = null
    private var endElapsed = 0L
    private var startWallTime = 0L

    fun select(minutes: Int) {
        if (_state.value.running) return
        val m = minutes.coerceIn(1, GameRules.MAX_FOCUS_MINUTES)
        _state.update { it.copy(selectedMinutes = m, remainingMs = m * 60_000L) }
    }

    fun start() {
        if (_state.value.running) return
        val minutes = _state.value.selectedMinutes
        startWallTime = System.currentTimeMillis()
        _state.update { it.copy(mode = FocusMode.FOCUS, running = true, remainingMs = minutes * 60_000L, away = false) }
        runTimer(minutes) { completeFocus() }
    }

    /** Bắt đầu giờ nghỉ Pomodoro. */
    fun startBreak(minutes: Int) {
        if (_state.value.running) return
        _state.update {
            it.copy(mode = FocusMode.BREAK, running = true, breakMinutes = minutes, remainingMs = minutes * 60_000L, away = false)
        }
        runTimer(minutes) { completeBreak() }
    }

    // Dùng elapsedRealtime để không bị ảnh hưởng khi người dùng chỉnh giờ hệ thống
    private fun runTimer(minutes: Int, onDone: suspend () -> Unit) {
        endElapsed = SystemClock.elapsedRealtime() + minutes * 60_000L
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (true) {
                val left = endElapsed - SystemClock.elapsedRealtime()
                if (left <= 0) { onDone(); break }
                _state.update { it.copy(remainingMs = left) }
                delay(250)
            }
        }
    }

    private suspend fun completeFocus() {
        graceJob?.cancel()
        val minutes = _state.value.selectedMinutes
        reset()
        val reward = repo.completeFocus(startWallTime, minutes)
        // Đếm lại sau khi lưu phiên vừa xong để biết có tới lượt nghỉ dài không
        val done = repo.focusSessionsToday().first()
        val breakMinutes = if (minutes <= 1) 1 else GameRules.breakMinutesAfter(done)
        _result.value = FocusResult.Completed(minutes, reward, breakMinutes)
    }

    private suspend fun completeBreak() {
        reset()
        repo.finishBreak()
        _result.value = FocusResult.BreakDone
    }

    /** Nút chính khi đang chạy: phiên tập trung thì bỏ cuộc (bị phạt), giờ nghỉ thì bỏ qua (không phạt). */
    fun giveUp() {
        val s = _state.value
        if (!s.running) return
        if (s.mode == FocusMode.BREAK) {
            tickJob?.cancel()
            reset()
            return
        }
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
        _state.update {
            it.copy(mode = FocusMode.FOCUS, running = false, away = false, remainingMs = it.selectedMinutes * 60_000L)
        }
    }

    /** Activity gọi khi app bị đưa xuống nền. Chỉ phiên tập trung mới bị tính rời app. */
    fun onAppHidden(screenOn: Boolean) {
        val s = _state.value
        if (!s.running || s.mode != FocusMode.FOCUS || !screenOn) return
        _state.update { it.copy(away = true) }
        graceJob = viewModelScope.launch {
            delay(GameRules.LEAVE_GRACE_MS)
            if (_state.value.running && _state.value.mode == FocusMode.FOCUS) {
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
