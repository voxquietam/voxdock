package com.vox.voxdock.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            if (confirmIntent != null) {
                confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirmIntent) }
            }
        }
    }
}

object AppUpdater {

    fun isNewer(remoteVersion: String?, currentVersion: String): Boolean {
        if (remoteVersion.isNullOrBlank()) return false
        val r = parseSemver(remoteVersion)
        val c = parseSemver(currentVersion)
        for (i in 0 until 3) {
            if (r[i] != c[i]) return r[i] > c[i]
        }
        return false
    }

    private fun parseSemver(v: String): IntArray {
        val parts = v.trim().removePrefix("v").removePrefix("V").split(".")
        return IntArray(3) { idx ->
            parts.getOrNull(idx)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
        }
    }

    private fun openFollowingRedirects(startUrl: String): HttpURLConnection {
        var current = startUrl
        repeat(6) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 10_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "VoxDock-Updater/0.2")
            }
            val code = conn.responseCode
            if (code in listOf(301, 302, 303, 307, 308)) {
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                if (loc.isNullOrBlank()) throw IllegalStateException("Empty redirect location")
                current = loc
            } else {
                return conn
            }
        }
        throw IllegalStateException("Too many redirects")
    }

    /**
     * Uses Android's native PackageInstaller.Session so the APK is staged into system storage
     * before our own process is stopped during self-update (avoids FileProvider death on HyperOS/Android 14+).
     */
    suspend fun downloadAndInstall(
        context: Context,
        apkUrl: String,
        onProgress: (Int) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                val permIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(permIntent)
                throw IllegalStateException("Allow 'Install unknown apps' for VoxDock, then tap UPDATE again")
            }

            val conn = openFollowingRedirects(apkUrl)
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val sessionId = installer.createSession(params)
            val session = installer.openSession(sessionId)

            try {
                val code = conn.responseCode
                if (code !in 200..299) {
                    throw IllegalStateException("Download failed: HTTP $code")
                }
                val total = conn.contentLengthLong.coerceAtLeast(1L)
                conn.inputStream.use { input ->
                    session.openWrite("voxdock_update.apk", 0, -1).use { output ->
                        val buf = ByteArray(16 * 1024)
                        var copied = 0L
                        var read: Int
                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                            copied += read
                            onProgress(((copied * 100) / total).toInt().coerceIn(0, 100))
                        }
                        session.fsync(output)
                    }
                }

                val callbackIntent = Intent(context, InstallResultReceiver::class.java)
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val statusPi = PendingIntent.getBroadcast(context, sessionId, callbackIntent, flags)
                session.commit(statusPi.intentSender)
            } catch (e: Throwable) {
                runCatching { session.abandon() }
                throw e
            } finally {
                runCatching { session.close() }
                conn.disconnect()
            }
        }
    }
}
