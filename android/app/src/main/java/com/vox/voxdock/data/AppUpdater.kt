package com.vox.voxdock.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

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

            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val outFile = File(dir, "voxdock-update.apk")

            val conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 10_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "VoxDock-Updater/0.2")
            }

            try {
                val code = conn.responseCode
                if (code !in 200..299) {
                    throw IllegalStateException("Download failed: HTTP $code")
                }
                val total = conn.contentLengthLong.coerceAtLeast(1L)
                conn.inputStream.use { input ->
                    FileOutputStream(outFile).use { output ->
                        val buf = ByteArray(16 * 1024)
                        var copied = 0L
                        var read: Int
                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                            copied += read
                            onProgress(((copied * 100) / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(installIntent)
        }
    }
}
