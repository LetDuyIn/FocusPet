package com.midterm.petfocus

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.midterm.petfocus.data.Difficulty
import com.midterm.petfocus.data.Repeat
import com.midterm.petfocus.data.Task
import com.midterm.petfocus.databinding.DialogAddTaskBinding
import com.midterm.petfocus.databinding.FragmentTasksBinding
import com.midterm.petfocus.logic.GameRules
import com.midterm.petfocus.ui.RewardFx
import com.midterm.petfocus.ui.tasks.TaskAdapter
import com.midterm.petfocus.ui.tasks.TaskFilter
import com.midterm.petfocus.ui.tasks.TasksViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TasksFragment : Fragment(R.layout.fragment_tasks) {

    private val vm: TasksViewModel by viewModels()
    private var binding: FragmentTasksBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentTasksBinding.bind(view)
        binding = b

        lateinit var adapter: TaskAdapter
        adapter = TaskAdapter(
            onComplete = { task, anchor ->
                val (coins, _) = GameRules.taskReward(task, System.currentTimeMillis())
                RewardFx.floatText(anchor, "+$coins 🪙")
                vm.complete(task)
            },
            onUncheck = { task ->
                // Hỏi lại trước khi trừ thưởng; bấm Hủy thì vẽ lại để checkbox tick trở lại
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Bỏ đánh dấu hoàn thành?")
                    .setMessage(
                        "Sẽ trừ lại ${task.coinsEarned} xu và ${task.expEarned} EXP đã nhận." +
                            if (task.repeat.days > 0) "\nViệc lặp kế tiếp (nếu chưa làm) cũng bị xóa." else ""
                    )
                    .setNegativeButton("Hủy") { _, _ -> adapter.notifyItemChanged(adapter.currentList.indexOf(task)) }
                    .setOnCancelListener { adapter.notifyItemChanged(adapter.currentList.indexOf(task)) }
                    .setPositiveButton("Bỏ đánh dấu") { _, _ -> vm.uncomplete(task) }
                    .show()
            },
            onEdit = { showTaskDialog(it) },
            onDelete = { deleteWithUndo(it) }
        )
        b.rvTasks.adapter = adapter

        // Vuốt sang trái/phải để xóa
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val pos = vh.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) deleteWithUndo(adapter.currentList[pos])
            }
        }).attachToRecyclerView(b.rvTasks)

        b.fabAddTask.setOnClickListener { showTaskDialog(null) }

        b.chipFilters.setOnCheckedStateChangeListener { _, ids ->
            vm.filter.value = when (ids.firstOrNull()) {
                R.id.chip_todo -> TaskFilter.TODO
                R.id.chip_overdue -> TaskFilter.OVERDUE
                R.id.chip_done -> TaskFilter.DONE
                else -> TaskFilter.ALL
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.tasks.collect {
                        adapter.submitList(it)
                        b.tvEmpty.isVisible = it.isEmpty()
                    }
                }
                launch { vm.summary.collect { b.tvTasksSummary.text = it } }
                launch { vm.messages.collect { Snackbar.make(b.root, it, Snackbar.LENGTH_LONG).show() } }
            }
        }
    }

    private fun deleteWithUndo(task: Task) {
        vm.delete(task)
        val root = binding?.root ?: return
        Snackbar.make(root, "Đã xóa “${task.title}”", Snackbar.LENGTH_LONG)
            .setAction("Hoàn tác") { vm.restore(task) }
            .show()
    }

    /** Dùng chung cho thêm mới (task = null) và sửa task. */
    private fun showTaskDialog(task: Task?) {
        val d = DialogAddTaskBinding.inflate(LayoutInflater.from(requireContext()))
        var dueDate: Long? = task?.dueDate
        val fmt = SimpleDateFormat("EEEE, dd/MM/yyyy", Locale.forLanguageTag("vi"))

        fun selectedDifficulty() = when (d.toggleDifficulty.checkedButtonId) {
            R.id.btn_easy -> Difficulty.EASY
            R.id.btn_hard -> Difficulty.HARD
            else -> Difficulty.MEDIUM
        }
        fun refresh() {
            val diff = selectedDifficulty()
            d.tvRewardPreview.text = "Thưởng: +${diff.coins} xu • +${diff.exp} EXP (trễ hạn chỉ nhận 50%)"
            d.tvDue.text = dueDate?.let { "Hạn: " + fmt.format(Date(it)) } ?: "Không có hạn chót"
            d.btnClearDue.isVisible = dueDate != null
        }

        d.etTitle.setText(task?.title.orEmpty())
        d.toggleDifficulty.check(
            when (task?.difficulty ?: Difficulty.MEDIUM) {
                Difficulty.EASY -> R.id.btn_easy
                Difficulty.MEDIUM -> R.id.btn_medium
                Difficulty.HARD -> R.id.btn_hard
            }
        )
        d.toggleDifficulty.addOnButtonCheckedListener { _, _, _ -> refresh() }
        d.toggleRepeat.check(
            when (task?.repeat ?: Repeat.NONE) {
                Repeat.NONE -> R.id.btn_repeat_none
                Repeat.DAILY -> R.id.btn_repeat_daily
                Repeat.WEEKLY -> R.id.btn_repeat_weekly
            }
        )
        fun selectedRepeat() = when (d.toggleRepeat.checkedButtonId) {
            R.id.btn_repeat_daily -> Repeat.DAILY
            R.id.btn_repeat_weekly -> Repeat.WEEKLY
            else -> Repeat.NONE
        }
        d.btnClearDue.setOnClickListener { dueDate = null; refresh() }
        d.btnPickDue.setOnClickListener {
            val cal = Calendar.getInstance().apply { dueDate?.let { timeInMillis = it } }
            DatePickerDialog(requireContext(), { _, y, m, day ->
                val picked = Calendar.getInstance().apply { set(y, m, day) }.timeInMillis
                dueDate = GameRules.startOfDay(picked)
                refresh()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
                datePicker.minDate = GameRules.startOfDay(System.currentTimeMillis())
            }.show()
        }
        refresh()

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (task == null) "Thêm công việc" else "Sửa công việc")
            .setView(d.root)
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Lưu", null)
            .show()

        // Tự xử lý nút Lưu để không đóng hộp thoại khi tên còn trống
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val title = d.etTitle.text?.toString()?.trim().orEmpty()
            if (title.isEmpty()) {
                d.tilTitle.error = "Hãy nhập tên công việc"
                return@setOnClickListener
            }
            val base = task ?: Task(title = title)
            val repeat = selectedRepeat()
            // Việc lặp lại mà chưa chọn hạn thì lấy hạn là hôm nay
            val due = dueDate ?: if (repeat != Repeat.NONE) GameRules.startOfDay(System.currentTimeMillis()) else null
            vm.save(base.copy(title = title, difficulty = selectedDifficulty(), dueDate = due, repeat = repeat))
            dialog.dismiss()
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}
