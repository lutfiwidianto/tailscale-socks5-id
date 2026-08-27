package com.tsproxy.android.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val isNewer: Boolean
)

object UpdateManager {

    // Primary and fallback repositories
    private const val PRIMARY_REPO = "lutfiwidianto/tailscale-socks5-id"
    private const val FALLBACK_REPO = "0xKrito/tailscale-socks5-Android"

    suspend fun checkUpdate(currentVersion: String): ReleaseInfo? = withContext(Dispatchers.IO) {
        var info = fetchReleaseFromRepo(PRIMARY_REPO, currentVersion)
        if (info == null) {
            info = fetchReleaseFromRepo(FALLBACK_REPO, currentVersion)
        }
        info
    }

    private fun fetchReleaseFromRepo(repo: String, currentVersion: String): ReleaseInfo? {
        return try {
            val url = URL("https://api.github.com/repos/$repo/releases/latest")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "ts-socks5-Android-App")
            }

            if (conn.responseCode != 200) {
                return null
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)

            val tagName = json.optString("tag_name", "")
            val releaseNotes = json.optString("body", "")
            val assets = json.optJSONArray("assets") ?: return null

            var apkUrl = ""
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                val browserDownloadUrl = asset.optString("browser_download_url", "")
                if (name.endsWith("release.apk", ignoreCase = true)) {
                    apkUrl = browserDownloadUrl
                    break
                } else if (name.endsWith(".apk", ignoreCase = true) && apkUrl.isEmpty()) {
                    apkUrl = browserDownloadUrl
                }
            }

            if (apkUrl.isEmpty() || tagName.isEmpty()) {
                return null
            }

            val cleanTag = tagName.removePrefix("v").trim()
            val cleanCurrent = currentVersion.removePrefix("v").trim()

            val isNewer = compareVersions(cleanTag, cleanCurrent) > 0

            ReleaseInfo(
                tagName = tagName,
                versionName = cleanTag,
                downloadUrl = apkUrl,
                releaseNotes = releaseNotes,
                isNewer = isNewer
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").mapNotNull { it.toIntOrNull() }
        val parts2 = v2.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(parts1.size, parts2.size)

        for (i in 0 until maxLen) {
            val num1 = parts1.getOrElse(i) { 0 }
            val num2 = parts2.getOrElse(i) { 0 }
            if (num1 != num2) {
                return num1.compareTo(num2)
            }
        }
        return 0
    }

    fun hasInstallPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun requestInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Toast.makeText(
                context,
                "Aktifkan 'Izinkan dari sumber ini' terlebih dahulu sebelum mengunduh pembaruan.",
                Toast.LENGTH_LONG
            ).show()
            try {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun downloadApk(
        downloadUrl: String,
        outputFile: File,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (outputFile.exists()) {
                outputFile.delete()
            }

            var currentUrl = downloadUrl
            var conn: HttpURLConnection
            var redirects = 0

            // Handle HTTP redirects (GitHub releases redirect to S3 storage)
            while (true) {
                val url = URL(currentUrl)
                conn = url.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.setRequestProperty("User-Agent", "ts-socks5-Android-App")

                val status = conn.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == HttpURLConnection.HTTP_SEE_OTHER ||
                    status == 307 || status == 308
                ) {
                    currentUrl = conn.getHeaderField("Location")
                    redirects++
                    if (redirects > 5) return@withContext false
                    continue
                }
                break
            }

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext false
            }

            val totalLength = conn.contentLengthLong
            val input = conn.inputStream
            val output = FileOutputStream(outputFile)

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalRead = 0L

            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                totalRead += bytesRead
                if (totalLength > 0) {
                    onProgress(totalRead.toFloat() / totalLength.toFloat())
                }
            }

            output.flush()
            output.close()
            input.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) {
            Toast.makeText(context, "File APK tidak ditemukan!", Toast.LENGTH_SHORT).show()
            return
        }

        if (!hasInstallPermission(context)) {
            requestInstallPermission(context)
            return
        }

        try {
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Gagal membuka installer APK: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
