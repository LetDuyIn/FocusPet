package com.midterm.petfocus

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

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
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .commit()
    }
}