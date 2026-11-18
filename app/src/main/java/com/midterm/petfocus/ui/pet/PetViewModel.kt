package com.midterm.petfocus.ui.pet

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.midterm.petfocus.FocusPetApp
import com.midterm.petfocus.data.ActionResult
import com.midterm.petfocus.data.PetState
import com.midterm.petfocus.logic.PetAction
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Phản hồi sau một hành động: lời thoại hiện trong bong bóng và animation tạm thời. */
data class PetReaction(val text: String, val success: Boolean, val action: PetAction, val levelUp: Boolean)

class PetViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as FocusPetApp).repository

    val pet: StateFlow<PetState?> = repo.pet
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _reactions = MutableSharedFlow<PetReaction>(extraBufferCapacity = 4)
    val reactions: SharedFlow<PetReaction> = _reactions

    init { refresh() }

    /** Cập nhật chỉ số theo thời gian thật (gọi khi mở màn hình và mỗi phút). */
    fun refresh() = viewModelScope.launch { repo.refreshPet() }

    fun act(action: PetAction) = viewModelScope.launch {
        val reaction = when (val r = repo.doAction(action)) {
            is ActionResult.Success -> PetReaction(r.message, true, action, r.levelsGained > 0)
            is ActionResult.Failure -> PetReaction(r.reason, false, action, false)
        }
        _reactions.emit(reaction)
    }

    fun rename(name: String) = viewModelScope.launch {
        if (name.isNotBlank()) repo.renamePet(name)
    }
}
