package com.midterm.petfocus.ui.pet

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import com.midterm.petfocus.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.midterm.petfocus.databinding.BottomSheetShopBinding
import com.midterm.petfocus.databinding.ItemShopBinding
import com.midterm.petfocus.logic.ItemType
import com.midterm.petfocus.logic.ShopCatalog
import com.midterm.petfocus.logic.ShopItem
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Một dòng trong cửa hàng kèm trạng thái của người chơi với món đó. */
data class ShopRow(val item: ShopItem, val owned: Boolean, val equipped: Boolean, val locked: Boolean, val affordable: Boolean)

class ShopSheet : BottomSheetDialogFragment() {

    private val vm: PetViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        BottomSheetShopBinding.inflate(inflater, container, false).root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = BottomSheetShopBinding.bind(view)
        val adapter = ShopAdapter { row ->
            if (row.item.type == ItemType.BACKGROUND && row.owned) vm.equip(row.item.id) else vm.buy(row.item.id)
        }
        b.rvShop.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(vm.pet, vm.ownedItems) { pet, owned -> pet to owned }.collect { (pet, owned) ->
                        if (pet == null) return@collect
                        b.tvShopCoins.text = "🪙 ${pet.coins} Xu"
                        val ownedIds = owned.map { it.itemId }.toSet()
                        val equippedId = owned.firstOrNull { it.equipped }?.itemId ?: ShopCatalog.DEFAULT_BACKGROUND
                        adapter.submitList(ShopCatalog.items.map { item ->
                            ShopRow(
                                item = item,
                                owned = item.id in ownedIds || item.id == ShopCatalog.DEFAULT_BACKGROUND,
                                equipped = item.id == equippedId,
                                locked = pet.level < item.minLevel,
                                affordable = pet.coins >= item.price
                            )
                        })
                    }
                }
                launch {
                    vm.reactions.collect {
                        b.tvShopMessage.text = it.text
                        b.tvShopMessage.setTextColor(
                            ContextCompat.getColor(requireContext(), if (it.success) R.color.reward_text else R.color.danger_text)
                        )
                    }
                }
            }
        }
    }

    companion object { const val TAG = "shop" }
}

private class ShopAdapter(private val onClick: (ShopRow) -> Unit) :
    ListAdapter<ShopRow, ShopAdapter.VH>(object : DiffUtil.ItemCallback<ShopRow>() {
        override fun areItemsTheSame(a: ShopRow, b: ShopRow) = a.item.id == b.item.id
        override fun areContentsTheSame(a: ShopRow, b: ShopRow) = a == b
    }) {

    class VH(val b: ItemShopBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemShopBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = getItem(position)
        val b = holder.b
        val item = row.item
        b.tvEmoji.text = item.emoji
        b.tvName.text = item.name
        b.tvDesc.text = item.description
        // Đặt lại nền mỗi lần bind để view tái sử dụng không giữ màu của món trước
        b.tvEmoji.setBackgroundColor(item.color?.let { Color.parseColor(it) } ?: Color.TRANSPARENT)

        val isBg = item.type == ItemType.BACKGROUND
        val (label, enabled) = when {
            isBg && row.equipped -> "Đang dùng" to false
            isBg && row.owned -> "Dùng" to true
            row.locked -> "🔒 Lv.${item.minLevel}" to false
            else -> "${item.price} 🪙" to true
        }
        b.btnBuy.text = label
        b.btnBuy.isEnabled = enabled
        b.btnBuy.alpha = if (enabled && !row.owned && !row.affordable) 0.5f else 1f
        val tint = when {
            isBg && row.owned -> R.color.mint
            row.locked -> R.color.locked_gray
            else -> R.color.peach
        }
        b.btnBuy.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(b.root.context, tint))
        b.btnBuy.setTextColor(Color.WHITE)
        b.btnBuy.setOnClickListener { onClick(row) }
    }
}
