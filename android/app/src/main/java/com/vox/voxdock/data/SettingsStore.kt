package com.vox.voxdock.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("voxdock_prefs", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    private fun load(): AppSettings {
        val defaultUnseen = setOf("sea", "air", "quake", "space", "bio")
        return AppSettings(
            workerUrl = prefs.getString("workerUrl", "") ?: "",
            apiToken = prefs.getString("apiToken", "") ?: "",
            darkTheme = prefs.getBoolean("darkTheme", true),
            dockMode = prefs.getBoolean("dockMode", false),
            autoDockOnCharge = prefs.getBoolean("autoDockOnCharge", true),
            nightOledEnabled = prefs.getBoolean("nightOledEnabled", true),
            quietStartHour = prefs.getInt("quietStartHour", 23),
            quietEndHour = prefs.getInt("quietEndHour", 7),
            city = prefs.getString("city", "Odesa") ?: "Odesa",
            lat = prefs.getFloat("lat", 46.48f).toDouble(),
            lon = prefs.getFloat("lon", 30.72f).toDouble(),
            oblastUid = prefs.getInt("oblastUid", 18),
            powerStreet = prefs.getString("powerStreet", "") ?: "",
            powerHouse = prefs.getString("powerHouse", "") ?: "",
            powerQueue = prefs.getString("powerQueue", "") ?: "",
            enabledUnseen = prefs.getStringSet("enabledUnseen", defaultUnseen)?.toSet()
                ?: defaultUnseen,
            mutedSources = prefs.getStringSet("mutedSources", emptySet())?.toSet() ?: emptySet()
        )
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_state.value)
        _state.value = next
        prefs.edit()
            .putString("workerUrl", next.workerUrl)
            .putString("apiToken", next.apiToken)
            .putBoolean("darkTheme", next.darkTheme)
            .putBoolean("dockMode", next.dockMode)
            .putBoolean("autoDockOnCharge", next.autoDockOnCharge)
            .putBoolean("nightOledEnabled", next.nightOledEnabled)
            .putInt("quietStartHour", next.quietStartHour)
            .putInt("quietEndHour", next.quietEndHour)
            .putString("city", next.city)
            .putFloat("lat", next.lat.toFloat())
            .putFloat("lon", next.lon.toFloat())
            .putInt("oblastUid", next.oblastUid)
            .putString("powerStreet", next.powerStreet)
            .putString("powerHouse", next.powerHouse)
            .putString("powerQueue", next.powerQueue)
            .putStringSet("enabledUnseen", next.enabledUnseen)
            .putStringSet("mutedSources", next.mutedSources)
            .apply()
    }
}
