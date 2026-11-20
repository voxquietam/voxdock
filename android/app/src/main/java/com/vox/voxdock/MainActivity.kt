package com.vox.voxdock

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.vox.voxdock.data.AppSettings
import com.vox.voxdock.data.AppUpdater
import com.vox.voxdock.data.DashboardResponse
import com.vox.voxdock.data.SettingsStore
import com.vox.voxdock.data.VoxDockApi
import com.vox.voxdock.notifications.MediaMonitor
import com.vox.voxdock.notifications.NotificationCaptureService
import com.vox.voxdock.notifications.NotificationStore
import com.vox.voxdock.ui.*
import com.vox.voxdock.ui.theme.VoxDockTheme
import com.vox.voxdock.widget.VoxDockWidgetProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

private enum class ActiveOverlay { NONE, MAP, WEATHER, POWER, UNSEEN, ACTA, SETTINGS }

class MainActivity : ComponentActivity() {
    private lateinit var settingsStore: SettingsStore
    private var isCharging by mutableStateOf(false)
    private var batteryPct by mutableIntStateOf(100)

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            if (level >= 0 && scale > 0) {
                batteryPct = ((level * 100) / scale).coerceIn(0, 100)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        settingsStore = SettingsStore(this)
        registerReceiver(powerReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        runCatching {
            NotificationListenerService.requestRebind(
                ComponentName(this, NotificationCaptureService::class.java)
            )
        }
        MediaMonitor.start(this)

        setContent {
            val settings by settingsStore.state.collectAsState()
            val notifications by NotificationStore.items.collectAsState()
            val nowPlaying by MediaMonitor.state.collectAsState()
            val scope = rememberCoroutineScope()

            var dashboard by remember { mutableStateOf(DashboardResponse()) }
            var syncError by remember { mutableStateOf<String?>(null) }
            var updateProgress by remember { mutableStateOf<Int?>(null) }
            var overlay by remember { mutableStateOf(ActiveOverlay.NONE) }
            var lastInteractionMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
            var dimmed by remember { mutableStateOf(false) }

            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val effectiveDock = settings.dockMode || (settings.autoDockOnCharge && isCharging && isLandscape)
            val nightOled = effectiveDock && settings.nightOledEnabled && isQuietHour(settings) && dimmed

            // Keep screen on, toggle immersive bars & control brightness in Dock Mode
            LaunchedEffect(effectiveDock, dimmed, dashboard.alerts.selected.level, settings.darkTheme) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !settings.darkTheme
                controller.isAppearanceLightNavigationBars = !settings.darkTheme
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (effectiveDock) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    controller.show(WindowInsetsCompat.Type.systemBars())
                }
                val lp = window.attributes
                lp.screenBrightness = when {
                    !effectiveDock -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    dashboard.alerts.selected.level == "active" -> 0.9f
                    dimmed -> 0.08f
                    else -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
                window.attributes = lp
            }

            // Auto-dim timer in Dock Mode (30 seconds of inactivity)
            LaunchedEffect(effectiveDock, lastInteractionMs) {
                if (!effectiveDock) {
                    dimmed = false
                    return@LaunchedEffect
                }
                dimmed = false
                delay(30_000L)
                dimmed = true
            }

            // Keep MediaMonitor fresh on resume without clobbering instant callback state
            LaunchedEffect(Unit) {
                MediaMonitor.refresh(this@MainActivity)
            }

            // Periodic poll of Cloudflare Worker
            LaunchedEffect(settings.workerUrl, settings.apiToken, settings.city, settings.oblastUid, settings.enabledUnseen, effectiveDock) {
                while (true) {
                    if (settings.workerUrl.isNotBlank()) {
                        val res = VoxDockApi.fetchDashboard(settings)
                        res.onSuccess {
                            if (it.notes.size > dashboard.notes.size || it.alerts.selected.level == "active") {
                                lastInteractionMs = System.currentTimeMillis()
                            }
                            dashboard = it
                            syncError = null
                            VoxDockWidgetProvider.pushState(this@MainActivity, it)
                        }.onFailure { err ->
                            syncError = err.message
                        }
                    }
                    delay(if (effectiveDock) 20_000L else 60_000L)
                }
            }

            VoxDockTheme(darkTheme = settings.darkTheme) {
                when (overlay) {
                    ActiveOverlay.MAP -> UkraineMapOverlay(
                        alerts = dashboard.alerts,
                        onClose = { overlay = ActiveOverlay.NONE }
                    )
                    ActiveOverlay.WEATHER -> WeatherForecastOverlay(
                        weather = dashboard.weather,
                        onClose = { overlay = ActiveOverlay.NONE }
                    )
                    ActiveOverlay.POWER -> PowerScheduleOverlay(
                        power = dashboard.power,
                        onOpenSettings = { overlay = ActiveOverlay.SETTINGS },
                        onClose = { overlay = ActiveOverlay.NONE }
                    )
                    ActiveOverlay.UNSEEN -> UnseenOverlay(
                        city = settings.city,
                        findings = dashboard.unseen,
                        onClose = { overlay = ActiveOverlay.NONE }
                    )
                    ActiveOverlay.ACTA -> ActaOverlay(
                        acta = dashboard.acta,
                        onClose = { overlay = ActiveOverlay.NONE }
                    )
                    ActiveOverlay.SETTINGS -> SettingsOverlay(
                        settings = settings,
                        onSave = { updated ->
                            settingsStore.update { updated }
                            scope.launch {
                                VoxDockApi.savePowerConfig(updated)
                                VoxDockApi.fetchDashboard(updated).onSuccess {
                                    dashboard = it
                                    syncError = null
                                    VoxDockWidgetProvider.pushState(this@MainActivity, it)
                                }
                            }
                        },
                        onClose = { overlay = ActiveOverlay.NONE }
                    )
                    ActiveOverlay.NONE -> {
                        val availableUpdate = dashboard.update?.takeIf {
                            AppUpdater.isNewer(it.version, BuildConfig.VERSION_NAME)
                        }
                        DashboardScreen(
                            dashboard = dashboard,
                            settings = settings.copy(dockMode = effectiveDock),
                            notifications = notifications,
                            nowPlaying = nowPlaying,
                            batteryPct = batteryPct,
                            isCharging = isCharging,
                            syncError = syncError,
                            updateAvailable = availableUpdate,
                            updateProgress = updateProgress,
                            isNightOled = nightOled,
                            onToggleTheme = { settingsStore.update { it.copy(darkTheme = !it.darkTheme) } },
                            onToggleDock = { settingsStore.update { it.copy(dockMode = !it.dockMode) } },
                            onOpenMap = {
                                lastInteractionMs = System.currentTimeMillis()
                                overlay = ActiveOverlay.MAP
                            },
                            onOpenWeather = {
                                lastInteractionMs = System.currentTimeMillis()
                                overlay = ActiveOverlay.WEATHER
                            },
                            onOpenPower = {
                                lastInteractionMs = System.currentTimeMillis()
                                overlay = ActiveOverlay.POWER
                            },
                            onOpenUnseen = {
                                lastInteractionMs = System.currentTimeMillis()
                                overlay = ActiveOverlay.UNSEEN
                            },
                            onOpenActa = {
                                lastInteractionMs = System.currentTimeMillis()
                                overlay = ActiveOverlay.ACTA
                            },
                            onOpenSettings = {
                                lastInteractionMs = System.currentTimeMillis()
                                overlay = ActiveOverlay.SETTINGS
                            },
                            onUpdatePages = { updatedPages ->
                                settingsStore.update { it.copy(pages = updatedPages) }
                            },
                            onTriggerUpdate = {
                                val target = availableUpdate ?: return@DashboardScreen
                                if (updateProgress != null) return@DashboardScreen
                                updateProgress = 0
                                scope.launch {
                                    val res = AppUpdater.downloadAndInstall(
                                        context = this@MainActivity,
                                        apkUrl = target.apkUrl,
                                        onProgress = { pct -> updateProgress = pct }
                                    )
                                    updateProgress = null
                                    res.onFailure { err ->
                                        android.widget.Toast.makeText(
                                            this@MainActivity,
                                            err.message ?: "Update failed",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            },
                            onDismissNote = { noteId ->
                            dashboard = dashboard.copy(notes = dashboard.notes.filterNot { it.id == noteId })
                            scope.launch { VoxDockApi.dismissNote(settings, noteId) }
                        },
                        onOpenNotification = { item ->
                            val sent = runCatching {
                                val pi = item.contentIntent ?: return@runCatching false
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                    val opts = android.app.ActivityOptions.makeBasic()
                                        .setPendingIntentBackgroundActivityStartMode(
                                            android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                                        ).toBundle()
                                    pi.send(this@MainActivity, 0, null, null, null, null, opts)
                                } else {
                                    pi.send()
                                }
                                true
                            }.getOrDefault(false)
                            if (!sent) {
                                runCatching {
                                    packageManager.getLaunchIntentForPackage(item.packageName)
                                        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        ?.let { startActivity(it) }
                                }
                            }
                        },
                        onDismissNotification = { key ->
                            NotificationStore.dismissAndCancel(key)
                        },
                        onToggleMuteSource = { src ->
                            settingsStore.update { cur ->
                                val next = if (cur.mutedSources.contains(src)) {
                                    cur.mutedSources - src
                                } else {
                                    cur.mutedSources + src
                                }
                                cur.copy(mutedSources = next)
                            }
                        },
                        onWakeInteraction = { lastInteractionMs = System.currentTimeMillis() }
                    )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        MediaMonitor.stop()
        runCatching { unregisterReceiver(powerReceiver) }
        super.onDestroy()
    }

    private fun isQuietHour(s: AppSettings): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return if (s.quietStartHour > s.quietEndHour) {
            hour >= s.quietStartHour || hour < s.quietEndHour
        } else {
            hour in s.quietStartHour until s.quietEndHour
        }
    }
}
