package com.midterm.petfocus

import android.content.Context

object PetStatsManager {
    private const val PREF_NAME = "FocusPetStatsPrefs"
    private const val KEY_COINS = "pet_coins"
    private const val KEY_MOOD = "pet_mood"

    fun getCoins(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_COINS, 120) // Khớp mức 120 xu mặc định trên giao diện
    }

    fun addCoins(context: Context, amount: Int): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val updated = (getCoins(context) + amount).coerceAtLeast(0)
        prefs.edit().putInt(KEY_COINS, updated).apply()
        return updated
    }

    fun getMood(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_MOOD, 80) // Tâm trạng mặc định 80/100
    }

    fun decreaseMood(context: Context, penalty: Int): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val updated = (getMood(context) - penalty).coerceIn(0, 100)
        prefs.edit().putInt(KEY_MOOD, updated).apply()
        return updated
    }
}