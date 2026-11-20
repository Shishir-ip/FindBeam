package com.findbeam.app.core.store

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** App-wide user preferences (theme, feedback, behaviour) + device aliases. */
class SettingsStore(context: Context) {

    enum class ThemeMode(val label: String) {
        SYSTEM("Auto"), LIGHT("Light"), DARK("Dark");

        companion object {
            fun from(id: Int) = entries.getOrElse(id) { SYSTEM }
        }

        val id: Int get() = ordinal
    }

    data class Settings(
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val sound: Boolean = true,
        val haptics: Boolean = true,
        val keepScreenOn: Boolean = true
    )

    private val prefs = context.getSharedPreferences("findbeam_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(
        Settings(
            themeMode = ThemeMode.from(prefs.getInt("theme", ThemeMode.SYSTEM.id)),
            sound = prefs.getBoolean("sound", true),
            haptics = prefs.getBoolean("haptics", true),
            keepScreenOn = prefs.getBoolean("keepScreenOn", true)
        )
    )
    val settings: StateFlow<Settings> = _settings

    fun setTheme(mode: ThemeMode) = update { it.copy(themeMode = mode) }
    fun setSound(on: Boolean) = update { it.copy(sound = on) }
    fun setHaptics(on: Boolean) = update { it.copy(haptics = on) }
    fun setKeepScreenOn(on: Boolean) = update { it.copy(keepScreenOn = on) }

    private fun update(transform: (Settings) -> Settings) {
        val next = transform(_settings.value)
        _settings.value = next
        prefs.edit {
            putInt("theme", next.themeMode.id)
            putBoolean("sound", next.sound)
            putBoolean("haptics", next.haptics)
            putBoolean("keepScreenOn", next.keepScreenOn)
        }
    }

    // ---- Device aliases (rename "Unknown device" to something friendly) ----

    fun alias(address: String): String? =
        prefs.getString("alias_$address", null)

    fun aliases(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        prefs.all.forEach { (k, v) ->
            if (k.startsWith("alias_") && v is String) {
                map[k.removePrefix("alias_")] = v
            }
        }
        return map
    }

    fun setAlias(address: String, name: String?) {
        prefs.edit {
            if (name.isNullOrBlank()) remove("alias_$address")
            else putString("alias_$address", name.trim())
        }
    }
}
