package com.midterm.petfocus.ui.tasks

import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.midterm.petfocus.data.Task
import com.midterm.petfocus.databinding.ItemTaskBinding
import com.midterm.petfocus.logic.GameRules
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TaskAdapter(
    private val onComplete: (Task) -> Unit,
    private val onEdit: (Task) -> Unit,
    private val onDelete: (Task) -> Unit
) : ListAdapter<Task, TaskAdapter.VH>(Diff) {

    private val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())

    inner class VH(val b: ItemTaskBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val task = getItem(position)
        val b = holder.b
        val now = System.currentTimeMillis()

        b.tvTaskTitle.text = task.title
        b.tvTaskTitle.paintFlags = if (task.isDone)
            b.tvTaskTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        else b.tvTaskTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        b.root.alpha = if (task.isDone) 0.6f else 1f

        // Gỡ listener trước khi set trạng thái để không kích hoạt nhầm khi RecyclerView tái sử dụng view
        b.cbTaskDone.setOnCheckedChangeListener(null)
        b.cbTaskDone.isChecked = task.isDone
        b.cbTaskDone.isEnabled = !task.isDone
        b.cbTaskDone.setOnCheckedChangeListener { _, checked -> if (checked) onComplete(task) }

        val overdue = GameRules.isOverdue(task, now)
        val due = task.dueDate?.let { dateFormat.format(Date(it)) }
        b.tvRewardBadge.text = when {
            task.isDone -> "Đã nhận +${task.coinsEarned} Xu • +${task.expEarned} EXP"
            overdue -> "Quá hạn $due • chỉ nhận ${task.difficulty.coins / 2} Xu"
            else -> buildString {
                append("${task.difficulty.label} • +${task.difficulty.coins} Xu • +${task.difficulty.exp} EXP")
                if (due != null) append(" • Hạn $due")
            }
        }
        val (bg, fg) = if (overdue) "#FEE2E2" to "#B91C1C" else "#D1FAE5" to "#00734F"
        b.tvRewardBadge.backgroundTintList = null
        b.tvRewardBadge.setBackgroundColor(Color.parseColor(bg))
        b.tvRewardBadge.setTextColor(Color.parseColor(fg))

        b.root.setOnClickListener { if (!task.isDone) onEdit(task) }
        b.btnDeleteTask.setOnClickListener { onDelete(task) }
    }

    private object Diff : DiffUtil.ItemCallback<Task>() {
        override fun areItemsTheSame(a: Task, b: Task) = a.id == b.id
        override fun areContentsTheSame(a: Task, b: Task) = a == b
    }
}
