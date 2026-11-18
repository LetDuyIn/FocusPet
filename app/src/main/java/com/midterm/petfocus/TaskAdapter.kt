package com.midterm.petfocus

import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox

class TasksAdapter(
    private val onCompleteClick: (TaskEntity) -> Unit,
    private val onFailClick: (TaskEntity) -> Unit,
    private val onDeleteClick: (TaskEntity) -> Unit,
    private val onEditClick: (TaskEntity) -> Unit
) : ListAdapter<TaskEntity, TasksAdapter.TaskViewHolder>(TaskDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cbDone: MaterialCheckBox = itemView.findViewById(R.id.cb_task_done)
        private val tvTitle: TextView = itemView.findViewById(R.id.tv_task_title)
        private val tvDesc: TextView = itemView.findViewById(R.id.tv_task_desc)
        private val tvDifficulty: TextView = itemView.findViewById(R.id.tv_task_difficulty)
        private val tvStatusBadge: TextView = itemView.findViewById(R.id.tv_task_status_badge)
        private val layoutActions: LinearLayout = itemView.findViewById(R.id.layout_task_actions)
        private val btnFail: MaterialButton = itemView.findViewById(R.id.btn_fail_task)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btn_delete_task)

        fun bind(task: TaskEntity) {
            tvTitle.text = task.title

            if (task.description.isBlank()) {
                tvDesc.visibility = View.GONE
            } else {
                tvDesc.visibility = View.VISIBLE
                tvDesc.text = task.description
            }

            val diff = task.getDifficultyEnum()
            tvDifficulty.text = "${diff.displayName} • +${task.rewardCoins} xu"
            when (diff) {
                TaskDifficulty.EASY -> {
                    tvDifficulty.setBackgroundColor(Color.parseColor("#E8F5E9"))
                    tvDifficulty.setTextColor(Color.parseColor("#2E7D32"))
                }
                TaskDifficulty.MEDIUM -> {
                    tvDifficulty.setBackgroundColor(Color.parseColor("#FFF3E0"))
                    tvDifficulty.setTextColor(Color.parseColor("#EF6C00"))
                }
                TaskDifficulty.HARD -> {
                    tvDifficulty.setBackgroundColor(Color.parseColor("#FFEBEE"))
                    tvDifficulty.setTextColor(Color.parseColor("#C62828"))
                }
            }

            cbDone.setOnCheckedChangeListener(null)

            when (task.getStatusEnum()) {
                TaskStatus.PENDING -> {
                    cbDone.isChecked = false
                    cbDone.isEnabled = true
                    tvTitle.paintFlags = tvTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                    tvTitle.setTextColor(Color.parseColor("#3D3A50"))
                    layoutActions.visibility = View.VISIBLE
                    tvStatusBadge.visibility = View.GONE

                    cbDone.setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) onCompleteClick(task)
                    }
                }
                TaskStatus.COMPLETED -> {
                    cbDone.isChecked = true
                    cbDone.isEnabled = false
                    tvTitle.paintFlags = tvTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    tvTitle.setTextColor(Color.parseColor("#9E9E9E"))
                    layoutActions.visibility = View.GONE
                    tvStatusBadge.visibility = View.VISIBLE
                    tvStatusBadge.text = "✓ Hoàn thành (+${task.rewardCoins} xu)"
                    tvStatusBadge.setTextColor(Color.parseColor("#2E7D32"))
                }
                TaskStatus.FAILED -> {
                    cbDone.isChecked = false
                    cbDone.isEnabled = false
                    tvTitle.paintFlags = tvTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    tvTitle.setTextColor(Color.parseColor("#9E9E9E"))
                    layoutActions.visibility = View.GONE
                    tvStatusBadge.visibility = View.VISIBLE
                    tvStatusBadge.text = "✕ Không hoàn thành (Pet -${task.moodPenalty} tâm trạng)"
                    tvStatusBadge.setTextColor(Color.parseColor("#D32F2F"))
                }
            }

            btnFail.setOnClickListener { onFailClick(task) }
            btnDelete.setOnClickListener { onDeleteClick(task) }
            itemView.setOnClickListener {
                if (task.getStatusEnum() == TaskStatus.PENDING) onEditClick(task)
            }
        }
    }

    class TaskDiffCallback : DiffUtil.ItemCallback<TaskEntity>() {
        override fun areItemsTheSame(oldItem: TaskEntity, newItem: TaskEntity) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: TaskEntity, newItem: TaskEntity) = oldItem == newItem
    }
}