package com.vox.voxdock.data

import kotlinx.serialization.Serializable

@Serializable
data class DashboardResponse(
    val serverTime: String = "",
    val alerts: AlertsPayload = AlertsPayload(),
    val weather: WeatherPayload = WeatherPayload(),
    val currency: CurrencyPayload? = null,
    val sensors: SensorsPayload? = null,
    val acta: ActaPayload = ActaPayload(),
    val unseen: List<UnseenFinding> = emptyList(),
    val power: PowerPayload = PowerPayload(),
    val notes: List<NoteItem> = emptyList(),
    val agentStatus: AgentStatus = AgentStatus(),
    val update: UpdatePayload? = null
)

@Serializable
data class UpdatePayload(
    val version: String = "",
    val apkUrl: String = "",
    val notes: String = "",
    val publishedAt: String = ""
)

@Serializable
data class OblastAlert(
    val uid: Int = 18,
    val code: String = "OD",
    val name: String = "Odesa",
    val level: String = "none" // "none" | "partial" | "active"
)

@Serializable
data class AlertsPayload(
    val source: String = "unconfigured",
    val updatedAt: String = "",
    val selectedUid: Int = 18,
    val selected: OblastAlert = OblastAlert(),
    val oblasts: List<OblastAlert> = emptyList()
)

@Serializable
data class CurrentWeather(
    val temp: Int = 0,
    val feelsLike: Int = 0,
    val windSpeed: Int = 0,
    val weatherCode: Int = 0,
    val seaTemp: Double? = null,
    val waveHeight: Double? = null,
    val sunrise: String? = null,
    val sunset: String? = null
)

@Serializable
data class DailyForecast(
    val date: String = "",
    val tempMin: Int = 0,
    val tempMax: Int = 0,
    val precipProb: Int = 0,
    val windMax: Int = 0,
    val weatherCode: Int = 0
)

@Serializable
data class WeatherPayload(
    val city: String = "Odesa",
    val lat: Double = 46.48,
    val lon: Double = 30.72,
    val updatedAt: String = "",
    val current: CurrentWeather = CurrentWeather(),
    val daily: List<DailyForecast> = emptyList()
)

@Serializable
data class CurrencyPayload(
    val usdBuy: Double = 0.0,
    val usdSell: Double = 0.0,
    val eurBuy: Double = 0.0,
    val eurSell: Double = 0.0,
    val updatedAt: String = ""
)

@Serializable
data class SensorsPayload(
    val aqi: Int = 0,
    val pm25: Double = 0.0,
    val pm10: Double = 0.0,
    val dust: Int = 0,
    val label: String = "GOOD",
    val updatedAt: String = ""
)

@Serializable
data class ActaTaskSummary(
    val id: String = "",
    val identifier: String = "",
    val title: String = "",
    val status: String = "",
    val priority: String = "",
    val url: String = "",
    val project: String = "",
    val assignee: String = "",
    val labels: List<String> = emptyList()
)

@Serializable
data class ActaPayload(
    val configured: Boolean = false,
    val count: Int = 0,
    val tasks: List<ActaTaskSummary> = emptyList(),
    val updatedAt: String = ""
)

@Serializable
data class UnseenFinding(
    val id: String = "",
    val category: String = "sea",
    val title: String = "",
    val explanation: String = "",
    val score: Double = 0.0,
    val metric: String = "",
    val observedAt: String = ""
)

@Serializable
data class PowerSlot(
    val startHour: Int = 0,
    val endHour: Int = 0,
    val state: String = "on" // "on" | "off" | "possible"
)

@Serializable
data class PowerPayload(
    val city: String = "Odesa",
    val street: String = "",
    val house: String = "",
    val queueGroup: String = "",
    val status: String = "unconfigured",
    val summary: String = "Set address & queue in Settings",
    val nextChange: String? = null,
    val slots: List<PowerSlot> = emptyList(),
    val updatedAt: String = ""
)

@Serializable
data class NoteItem(
    val id: Int = 0,
    val kind: String = "note",
    val text: String = "",
    val remindAt: String? = null,
    val pinned: Boolean = false,
    val source: String = "mcp",
    val createdAt: String = ""
)

@Serializable
data class AgentStatus(
    val level: String = "ok",
    val message: String = "VoxDock ready",
    val source: String = "system",
    val updatedAt: String = ""
)

@Serializable
enum class TileKind(
    val title: String,
    val minColSpan: Int,
    val maxColSpan: Int,
    val minRowSpan: Int,
    val maxRowSpan: Int,
    val defaultColSpan: Int,
    val defaultRowSpan: Int
) {
    CLOCK("Clock", 1, 2, 1, 2, 2, 1),
    ALERT("Air Raid", 1, 2, 1, 2, 1, 1),
    WEATHER("Weather", 1, 2, 1, 2, 1, 1),
    POWER("DTEK Power", 1, 2, 1, 2, 1, 1),
    CURRENCY("FX USD/EUR", 1, 2, 1, 1, 1, 1),
    SUNSET_WAVES("Sunset & Waves", 1, 2, 1, 1, 1, 1),
    AIR_SENSORS("Air Sensors", 1, 2, 1, 1, 1, 1),
    AGENT_STATUS("Agent Status", 1, 2, 1, 1, 1, 1),
    SPOTIFY("Now Playing", 2, 3, 1, 2, 2, 2),
    UNSEEN("Unseen", 1, 2, 1, 2, 1, 1),
    MCP_NOTES("MCP Notes", 2, 3, 1, 2, 2, 1),
    ACTA_TASKS("ACTA Tasks", 2, 3, 1, 3, 2, 2),
    INBOX("Inbox", 2, 4, 1, 3, 2, 2)
}

@Serializable
data class DockTile(
    val kind: TileKind,
    val col: Int = 0,
    val row: Int = 0,
    val colSpan: Int = kind.defaultColSpan,
    val rowSpan: Int = kind.defaultRowSpan
)

@Serializable
data class DockPage(
    val id: String,
    val name: String,
    val tiles: List<DockTile>
)

// 4x3 landscape Bento grid — every cell filled edge-to-edge with zero awkward gaps
fun defaultDockPages(): List<DockPage> = listOf(
    DockPage(
        id = "main",
        name = "Dock",
        tiles = listOf(
            DockTile(TileKind.CLOCK, col = 0, row = 0, colSpan = 2, rowSpan = 1),
            DockTile(TileKind.MCP_NOTES, col = 2, row = 0, colSpan = 2, rowSpan = 1),
            DockTile(TileKind.ALERT, col = 0, row = 1, colSpan = 1, rowSpan = 1),
            DockTile(TileKind.WEATHER, col = 1, row = 1, colSpan = 1, rowSpan = 1),
            DockTile(TileKind.INBOX, col = 2, row = 1, colSpan = 2, rowSpan = 2),
            DockTile(TileKind.POWER, col = 0, row = 2, colSpan = 1, rowSpan = 1),
            DockTile(TileKind.UNSEEN, col = 1, row = 2, colSpan = 1, rowSpan = 1)
        )
    ),
    DockPage(
        id = "media",
        name = "Media & Radar",
        tiles = listOf(
            DockTile(TileKind.SPOTIFY, col = 0, row = 0, colSpan = 2, rowSpan = 2),
            DockTile(TileKind.ACTA_TASKS, col = 2, row = 0, colSpan = 2, rowSpan = 2),
            DockTile(TileKind.CURRENCY, col = 0, row = 2, colSpan = 1, rowSpan = 1),
            DockTile(TileKind.SUNSET_WAVES, col = 1, row = 2, colSpan = 1, rowSpan = 1),
            DockTile(TileKind.AIR_SENSORS, col = 2, row = 2, colSpan = 1, rowSpan = 1),
            DockTile(TileKind.AGENT_STATUS, col = 3, row = 2, colSpan = 1, rowSpan = 1)
        )
    )
)

data class AppSettings(
    val workerUrl: String = "",
    val apiToken: String = "",
    val darkTheme: Boolean = true,
    val dockMode: Boolean = false,
    val autoDockOnCharge: Boolean = true,
    val nightOledEnabled: Boolean = true,
    val petEnabled: Boolean = true,
    val petType: String = "fox",
    val quietStartHour: Int = 23,
    val quietEndHour: Int = 7,
    val city: String = "Odesa",
    val lat: Double = 46.48,
    val lon: Double = 30.72,
    val oblastUid: Int = 18,
    val powerStreet: String = "",
    val powerHouse: String = "",
    val powerQueue: String = "",
    val enabledUnseen: Set<String> = setOf("sea", "air", "quake", "space", "bio"),
    val mutedSources: Set<String> = emptySet(),
    val pages: List<DockPage> = defaultDockPages()
)
