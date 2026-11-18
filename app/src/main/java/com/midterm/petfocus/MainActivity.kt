package com.midterm.petfocus

import android.os.Bundle
import android.os.PowerManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.midterm.petfocus.ui.focus.FocusResult
import com.midterm.petfocus.ui.focus.FocusViewModel
import com.midterm.petfocus.ui.pet.PetViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val focusVm: FocusViewModel by viewModels()
    // Tạo sớm để chỉ số pet được cập nhật ngay khi mở app
    private val petVm: PetViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
            lifecycleScope.launch { focusVm.state.collect { cb.isEnabled = it.running } }
        })

        // Hiện kết quả phiên tập trung dù đang ở tab nào
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                focusVm.result.collect { result -> result?.let(::showFocusResult) }
            }
        }
    }

    private fun showFocusResult(result: FocusResult) {
        focusVm.consumeResult()
        val (title, message) = when (result) {
            is FocusResult.Completed -> {
                val r = result.reward
                var msg = "Bạn đã tập trung ${result.minutes} phút.\n+${r.coins} xu • +${r.exp} EXP"
                if (r.levelsGained > 0) msg += "\n🎉 Thú cưng đã lên cấp!"
                "Tuyệt vời! 🎉" to msg
            }
            is FocusResult.Failed -> "Phiên tập trung thất bại" to result.reason
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
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
