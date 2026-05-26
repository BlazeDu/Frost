package com.storm.coldwind

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration

object ThemeManager {
    private const val PREFS_NAME = "app_settings"
    private const val KEY_THEME_MODE = "theme_mode"

    // 主题变化监听器
    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyThemeChanged() {
        listeners.forEach { it.invoke() }
    }

    // 0: 跟随系统, 1: 深色模式, 2: 浅色模式
    fun saveThemeMode(context: Context, mode: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply()
        notifyThemeChanged()
    }

    fun getThemeMode(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_THEME_MODE, 0)
    }

    fun getThemeDisplayName(mode: Int): String {
        return when (mode) {
            0 -> "跟随系统"
            1 -> "深色模式"
            2 -> "浅色模式"
            else -> "跟随系统"
        }
    }

    // 判断当前是否为深色模式
    fun isDarkTheme(context: Context): Boolean {
        val mode = getThemeMode(context)
        return when (mode) {
            1 -> true
            2 -> false
            else -> {
                val uiMode = context.resources.configuration.uiMode
                (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    // 重启整个应用
    fun restartApp(activity: Activity) {
        val intent = Intent(activity, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.startActivity(intent)
        activity.finish()
    }
}