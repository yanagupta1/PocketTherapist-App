package com.example.pockettherapist

import android.content.Context

object FirstTimeStore {

    private const val PREF_NAME = "first_time_prefs"
    private const val KEY_PREFIX = "is_first_time_"

    // Check if THIS user is new
    fun isFirstTime(context: Context, username: String): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PREFIX + username, true)
    }

    // Mark THIS user as done with onboarding
    fun setNotFirstTime(context: Context, username: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PREFIX + username, false).apply()
    }
}
