package com.midterm.petfocus

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.text.InputFilter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.midterm.petfocus.logic.GameRules
import com.midterm.petfocus.ui.focus.FocusMode
import com.midterm.petfocus.ui.focus.FocusResult
import com.midterm.petfocus.ui.focus.FocusViewModel
import com.midterm.petfocus.reminder.Reminders
import com.midterm.petfocus.ui.art.ArtIcons
import com.midterm.petfocus.ui.pet.PetViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val focusVm: FocusViewModel by viewModels()
    // Tạo sớm để chỉ số pet được cập nhật ngay khi mở app
    private val petVm: PetViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Android mới vẽ app tràn lên cả thanh trạng thái: đẩy nội dung xuống dưới để không bị đè
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.nav_host_fragment)) { v, insets ->
            v.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top)
            insets
        }

        // Mặc định mở màn hình Thú cưng đầu tiên
        if (savedInstanceState == null) {
            loadFragment(PetFragment())
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_pet -> {
                    loadFragment(PetFragment())
                    true
                }
                R.id.nav_tasks -> {
                    loadFragment(TasksFragment())
                    true
                }
                R.id.nav_focus -> {
                    loadFragment(FocusFragment())
                    true
                }
                R.id.nav_stats -> {
                    loadFragment(StatsFragment())
                    true
                }
                else -> false
            }
        }
        // Bấm lại tab đang mở thì không tạo lại màn hình
        bottomNav.setOnItemReselectedListener { }

        // Đang tập trung mà bấm Back thì hỏi lại, tránh thoát app làm thất bại phiên
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle("Đang tập trung")
                    .setMessage("Thoát app bây giờ sẽ làm phiên tập trung thất bại. Bạn có muốn bỏ cuộc?")
                    .setNegativeButton("Ở lại", null)
                    .setPositiveButton("Bỏ cuộc") { _, _ -> focusVm.giveUp() }
                    .show()
            }
        }.also { cb ->
            // Giờ nghỉ thì cho thoát bình thường
            lifecycleScope.launch {
                focusVm.state.collect { cb.isEnabled = it.running && it.mode == FocusMode.FOCUS }
            }
        })

        if (savedInstanceState == null) showOnboardingIfNeeded()

        // Hiện kết quả phiên tập trung dù đang ở tab nào
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                focusVm.result.collect { result -> result?.let(::showFocusResult) }
            }
        }
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, "Bạn có thể bật thông báo sau trong Cài đặt.", Toast.LENGTH_SHORT).show()
            }
        }

    /** Lần đầu mở app: chào mừng, đặt tên mèo, rồi xin quyền gửi thông báo nhắc nhở. */
    private fun showOnboardingIfNeeded() {
        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        if (prefs.getBoolean("onboarded", false)) return

        val pad = (20 * resources.displayMetrics.density).toInt()
        val input = EditText(this).apply {
            hint = "Ví dụ: Mimi"
            setText("Mimi")
            setSelection(text.length)
            filters = arrayOf(InputFilter.LengthFilter(20))
        }
        val container = FrameLayout(this).apply {
            setPadding(pad, 0, pad, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(ArtIcons.art(this, "Chào mừng đến FocusPet! 🐾"))
            .setMessage(
                "Làm xong việc và tập trung không dùng điện thoại để kiếm xu, nuôi mèo lớn lên.\n\n" +
                    "Hãy đặt tên cho bé mèo của bạn:"
            )
            .setView(container)
            .setCancelable(false)
            .setPositiveButton("Bắt đầu") { _, _ ->
                petVm.rename(input.text.toString().ifBlank { "Mimi" })
                prefs.edit().putBoolean("onboarded", true).apply()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Reminders.canNotify(this)) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            .show()
    }

    private fun showFocusResult(result: FocusResult) {
        focusVm.consumeResult()
        val dialog = MaterialAlertDialogBuilder(this)
        when (result) {
            is FocusResult.Completed -> {
                val r = result.reward
                var msg = "Bạn đã tập trung ${result.minutes} phút.\n+${r.coins} 🪙 • +${r.exp} EXP ⭐"
                if (r.levelsGained > 0) msg += "\n🎉 Thú cưng đã lên cấp!"
                val longBreak = result.breakMinutes >= GameRules.LONG_BREAK_MINUTES
                msg += if (longBreak) "\n\nĐã xong 4 phiên, nghỉ dài ${result.breakMinutes} phút nhé!"
                else "\n\nNghỉ ngắn ${result.breakMinutes} phút rồi học tiếp?"
                dialog.setTitle(ArtIcons.art(this, "Tuyệt vời! 🎉"))
                    .setMessage(ArtIcons.art(this, msg))
                    .setPositiveButton("Nghỉ ${result.breakMinutes} phút") { _, _ -> focusVm.startBreak(result.breakMinutes) }
                    .setNegativeButton("Để sau", null)
            }
            is FocusResult.Failed -> dialog.setTitle("Phiên tập trung thất bại")
                .setMessage(ArtIcons.art(this, result.reason))
                .setPositiveButton("OK", null)
            FocusResult.BreakDone -> dialog.setTitle(ArtIcons.art(this, "Hết giờ nghỉ 🌿"))
                .setMessage(ArtIcons.art(this, "Mèo đã nạp lại năng lượng ⚡ Sẵn sàng phiên tiếp theo chưa?"))
                .setPositiveButton("Bắt đầu phiên mới") { _, _ -> focusVm.start() }
                .setNegativeButton("Để sau", null)
        }
        dialog.show()
        petVm.refresh()
    }

    override fun onStart() {
        super.onStart()
        focusVm.onAppShown()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            val screenOn = getSystemService(PowerManager::class.java).isInteractive
            focusVm.onAppHidden(screenOn)
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .commit()
    }
}
