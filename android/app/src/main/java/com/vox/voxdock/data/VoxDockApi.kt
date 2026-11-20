package com.vox.voxdock.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object VoxDockApi {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    suspend fun fetchDashboard(settings: AppSettings): Result<DashboardResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val base = settings.workerUrl.trim().trimEnd('/')
                require(base.startsWith("http")) { "Worker URL is not configured" }

                val unseen = settings.enabledUnseen.joinToString(",")
                val cityEnc = URLEncoder.encode(settings.city, "UTF-8")
                val urlStr =
                    "$base/api/dashboard?lat=${settings.lat}&lon=${settings.lon}" +
                        "&city=$cityEnc&oblastUid=${settings.oblastUid}&unseen=$unseen"

                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 10000
                    if (settings.apiToken.isNotBlank()) {
                        setRequestProperty("Authorization", "Bearer ${settings.apiToken.trim()}")
                    }
                }

                try {
                    val code = conn.responseCode
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (code !in 200..299) {
                        throw IllegalStateException("HTTP $code: $body")
                    }
                    json.decodeFromString(DashboardResponse.serializer(), body)
                } finally {
                    conn.disconnect()
                }
            }
        }

    suspend fun dismissNote(settings: AppSettings, noteId: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val base = settings.workerUrl.trim().trimEnd('/')
                if (!base.startsWith("http")) return@runCatching
                val conn = (URL("$base/api/notes/$noteId").openConnection() as HttpURLConnection).apply {
                    requestMethod = "DELETE"
                    connectTimeout = 6000
                    readTimeout = 6000
                    if (settings.apiToken.isNotBlank()) {
                        setRequestProperty("Authorization", "Bearer ${settings.apiToken.trim()}")
                    }
                }
                try {
                    conn.responseCode
                } finally {
                    conn.disconnect()
                }
            }
        }

    suspend fun savePowerConfig(settings: AppSettings): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val base = settings.workerUrl.trim().trimEnd('/')
                if (!base.startsWith("http")) return@runCatching
                val payload = """{"city":"${escape(settings.city)}","street":"${escape(settings.powerStreet)}","house":"${escape(settings.powerHouse)}","queueGroup":"${escape(settings.powerQueue)}"}"""
                val conn = (URL("$base/api/power/config").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 6000
                    readTimeout = 6000
                    setRequestProperty("Content-Type", "application/json")
                    if (settings.apiToken.isNotBlank()) {
                        setRequestProperty("Authorization", "Bearer ${settings.apiToken.trim()}")
                    }
                }
                try {
                    OutputStreamWriter(conn.outputStream).use { it.write(payload) }
                    conn.responseCode
                } finally {
                    conn.disconnect()
                }
            }
        }

    private fun escape(v: String): String =
        v.replace("\\", "\\\\").replace("\"", "\\\"")
}
