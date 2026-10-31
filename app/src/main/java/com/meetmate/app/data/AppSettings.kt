package com.meetmate.app.data

import android.content.Context

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

/** Small user preferences, kept in SharedPreferences. */
object AppSettings {
    private const val PREFS = "meetmate_settings"
    private const val KEY_THEME = "theme_mode"

    fun themeMode(context: Context): ThemeMode {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_THEME, null)
        return ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.SYSTEM
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_THEME, mode.name)
            .apply()
    }
}
