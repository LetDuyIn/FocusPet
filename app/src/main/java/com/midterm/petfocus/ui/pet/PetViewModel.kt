package com.midterm.petfocus.ui.pet

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.midterm.petfocus.FocusPetApp
import com.midterm.petfocus.data.ActionResult
import com.midterm.petfocus.data.OwnedItem
import com.midterm.petfocus.data.PetState
import com.midterm.petfocus.logic.ItemType
import com.midterm.petfocus.logic.PetAction
import com.midterm.petfocus.logic.ShopCatalog
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Phản hồi sau một hành động: lời thoại trong bong bóng và animation tạm thời (null = không đổi). */
data class PetReaction(val text: String, val success: Boolean, val anim: String?, val levelUp: Boolean)

class PetViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as FocusPetApp).repository

    val pet: StateFlow<PetState?> = repo.pet
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val ownedItems: StateFlow<List<OwnedItem>> = repo.ownedItems
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Màu phông nền đang dùng cho ô hiển thị pet; null = nền mặc định của giao diện. */
    val backgroundColor: StateFlow<String?> = repo.ownedItems
        .map { list -> list.firstOrNull { it.equipped }?.let { ShopCatalog.find(it.itemId)?.color } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _reactions = MutableSharedFlow<PetReaction>(extraBufferCapacity = 4)
    val reactions: SharedFlow<PetReaction> = _reactions

    init { refresh() }

    /** Cập nhật chỉ số theo thời gian thật (gọi khi mở màn hình và mỗi phút). */
    fun refresh() = viewModelScope.launch { repo.refreshPet() }

    fun act(action: PetAction) = viewModelScope.launch {
        val reaction = when (val r = repo.doAction(action)) {
            is ActionResult.Success -> PetReaction(
                r.message, true,
                if (action == PetAction.SLEEP) "pet_sleep.json" else "pet_happy.json",
                r.levelsGained > 0
            )
            is ActionResult.Failure -> PetReaction(r.reason, false, null, false)
        }
        _reactions.emit(reaction)
    }

    fun buy(itemId: String) = viewModelScope.launch {
        val item = ShopCatalog.find(itemId) ?: return@launch
        val reaction = when (val r = repo.buy(itemId)) {
            is ActionResult.Success -> PetReaction(
                r.message, true, if (item.type == ItemType.FOOD) "pet_happy.json" else null, false
            )
            is ActionResult.Failure -> PetReaction(r.reason, false, null, false)
        }
        _reactions.emit(reaction)
    }

    fun equip(itemId: String) = viewModelScope.launch { repo.equipBackground(itemId) }

    fun rename(name: String) = viewModelScope.launch {
        if (name.isNotBlank()) repo.renamePet(name)
    }
}
