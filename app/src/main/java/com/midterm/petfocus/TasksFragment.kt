package com.midterm.petfocus

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class TasksFragment : Fragment() {

    private lateinit var database: FocusPetDatabase
    private lateinit var adapter: TasksAdapter

    private lateinit var tvCoinsBadge: TextView
    private lateinit var tvPetMoodStatus: TextView
    private lateinit var pbPetMood: ProgressBar
    private lateinit var tvEmptyTasks: TextView
    private lateinit var rvTasks: RecyclerView
    private lateinit var chipGroupFilter: ChipGroup
    private lateinit var fabAddTask: ExtendedFloatingActionButton

    private var observeJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_tasks, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        database = FocusPetDatabase.getDatabase(requireContext())

        tvCoinsBadge = view.findViewById(R.id.tv_coins_badge)
        tvPetMoodStatus = view.findViewById(R.id.tv_pet_mood_status)
        pbPetMood = view.findViewById(R.id.pb_pet_mood)
        tvEmptyTasks = view.findViewById(R.id.tv_empty_tasks)
        rvTasks = view.findViewById(R.id.rv_tasks)
        chipGroupFilter = view.findViewById(R.id.chip_group_filter)
        fabAddTask = view.findViewById(R.id.fab_add_task)

        setupRecyclerView()
        setupFilters()
        refreshStatsUI()
        observeTasks(null)

        fabAddTask.setOnClickListener {
            showTaskDialog(existingTask = null)
        }
    }

    private fun setupRecyclerView() {
        adapter = TasksAdapter(
            onCompleteClick = { task -> markTaskCompleted(task) },
            onFailClick = { task -> confirmTaskFailed(task) },
            onDeleteClick = { task -> confirmDeleteTask(task) },
            onEditClick = { task -> showTaskDialog(existingTask = task) }
        )
        rvTasks.layoutManager = LinearLayoutManager(requireContext())
        rvTasks.adapter = adapter
    }

    private fun setupFilters() {
        chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            val filter = when (checkedIds.firstOrNull()) {
                R.id.chip_pending -> TaskStatus.PENDING
                R.id.chip_completed -> TaskStatus.COMPLETED
                R.id.chip_failed -> TaskStatus.FAILED
                else -> null
            }
            observeTasks(filter)
        }
    }

    private fun observeTasks(statusFilter: TaskStatus?) {
        observeJob?.cancel()
        observeJob = viewLifecycleOwner.lifecycleScope.launch {
            val flow = if (statusFilter == null) {
                database.taskDao().getAllTasks()
            } else {
                database.taskDao().getTasksByStatus(statusFilter.name)
            }

            flow.collect { list ->
                adapter.submitList(list)
                tvEmptyTasks.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showTaskDialog(existingTask: TaskEntity?) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_edit_task, null)

        val tvDialogTitle = dialogView.findViewById<TextView>(R.id.tv_dialog_title)
        val edtTitle = dialogView.findViewById<TextInputEditText>(R.id.edt_task_title)
        val edtDesc = dialogView.findViewById<TextInputEditText>(R.id.edt_task_desc)
        val rgDifficulty = dialogView.findViewById<RadioGroup>(R.id.rg_difficulty)

        if (existingTask != null) {
            tvDialogTitle.text = "Chỉnh sửa nhiệm vụ"
            edtTitle.setText(existingTask.title)
            edtDesc.setText(existingTask.description)
            when (existingTask.getDifficultyEnum()) {
                TaskDifficulty.EASY -> rgDifficulty.check(R.id.rb_easy)
                TaskDifficulty.MEDIUM -> rgDifficulty.check(R.id.rb_medium)
                TaskDifficulty.HARD -> rgDifficulty.check(R.id.rb_hard)
            }
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton(if (existingTask == null) "Thêm" else "Lưu", null)
            .setNegativeButton("Hủy", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = edtTitle.text?.toString()?.trim().orEmpty()
                val desc = edtDesc.text?.toString()?.trim().orEmpty()

                if (title.isEmpty()) {
                    edtTitle.error = "Vui lòng nhập tên công việc"
                    return@setOnClickListener
                }

                val selectedDifficulty = when (rgDifficulty.checkedRadioButtonId) {
                    R.id.rb_medium -> TaskDifficulty.MEDIUM
                    R.id.rb_hard -> TaskDifficulty.HARD
                    else -> TaskDifficulty.EASY
                }

                viewLifecycleOwner.lifecycleScope.launch {
                    if (existingTask == null) {
                        database.taskDao().insertTask(
                            TaskEntity(
                                title = title,
                                description = desc,
                                difficulty = selectedDifficulty.name,
                                rewardCoins = selectedDifficulty.rewardCoins,
                                moodPenalty = selectedDifficulty.moodPenalty
                            )
                        )
                        Toast.makeText(requireContext(), "Đã thêm công việc!", Toast.LENGTH_SHORT).show()
                    } else {
                        database.taskDao().updateTask(
                            existingTask.copy(
                                title = title,
                                description = desc,
                                difficulty = selectedDifficulty.name,
                                rewardCoins = selectedDifficulty.rewardCoins,
                                moodPenalty = selectedDifficulty.moodPenalty
                            )
                        )
                        Toast.makeText(requireContext(), "Đã cập nhật công việc!", Toast.LENGTH_SHORT).show()
                    }
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun markTaskCompleted(task: TaskEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            database.taskDao().updateTask(task.copy(status = TaskStatus.COMPLETED.name))
            PetStatsManager.addCoins(requireContext(), task.rewardCoins)
            refreshStatsUI()
            Toast.makeText(
                requireContext(),
                "Hoàn thành! +${task.rewardCoins} xu",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun confirmTaskFailed(task: TaskEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle("Không hoàn thành công việc?")
            .setMessage("Thú cưng của bạn sẽ bị trừ ${task.moodPenalty} điểm tâm trạng.")
            .setPositiveButton("Xác nhận") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    database.taskDao().updateTask(task.copy(status = TaskStatus.FAILED.name))
                    val newMood = PetStatsManager.decreaseMood(requireContext(), task.moodPenalty)
                    refreshStatsUI()
                    Toast.makeText(
                        requireContext(),
                        "Pet bị trừ ${task.moodPenalty} tâm trạng (Còn $newMood/100)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun confirmDeleteTask(task: TaskEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle("Xóa công việc")
            .setMessage("Xóa \"${task.title}\" khỏi danh sách?")
            .setPositiveButton("Xóa") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    database.taskDao().deleteTask(task)
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun refreshStatsUI() {
        val coins = PetStatsManager.getCoins(requireContext())
        val mood = PetStatsManager.getMood(requireContext())

        tvCoinsBadge.text = "🪙 $coins Xu"
        tvPetMoodStatus.text = "🐾 Tâm trạng Pet: $mood/100"
        pbPetMood.progress = mood

        val color = when {
            mood >= 70 -> "#66BB6A"
            mood >= 40 -> "#FFA726"
            else -> "#EF5350"
        }
        pbPetMood.progressTintList = ColorStateList.valueOf(Color.parseColor(color))
    }
}