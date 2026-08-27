package com.tsproxy.android.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tsproxy.Tsproxy
import com.tsproxy.android.BuildConfig
import com.tsproxy.android.service.TsProxyService
import com.tsproxy.android.TsProxyApp
import com.tsproxy.android.util.ReleaseInfo
import com.tsproxy.android.util.UpdateManager
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.io.File

private val Application.dataStore by preferencesDataStore("tsproxy_prefs")

data class UiState(
    val running: Boolean = false,
    val socksAddr: String = "127.0.0.1:1080",
    val hostname: String = "ts-socks5",
    val tsnetDir: String = "",
    val luciUrl: String = "https://100.73.70.18/cgi-bin/luci/",
    val tailscaleIP: String = "",
    val loginUrl: String = "",
    val logs: String = "",
    val crashLog: String = "",
    val statusText: String = "Stopped",
    val logPaused: Boolean = false,

    // Auto Update states
    val checkingUpdate: Boolean = false,
    val updateInfo: ReleaseInfo? = null,
    val downloadingUpdate: Boolean = false,
    val downloadProgress: Float = 0f,
    val updateStatusMessage: String = ""
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        loadConfig()
        startStatusPolling()
        checkForUpdates()
    }

    private fun loadConfig() {
        viewModelScope.launch {
            val prefs = getApplication<Application>().dataStore.data.first()
            _ui.value = _ui.value.copy(
                socksAddr = prefs[SOCKS_KEY] ?: "127.0.0.1:1080",
                hostname = prefs[HOSTNAME_KEY] ?: "ts-socks5",
                tsnetDir = prefs[TSNETDIR_KEY] ?: "",
                luciUrl = prefs[LUCI_URL_KEY] ?: "https://100.73.70.18/cgi-bin/luci/"
            )
        }
    }

    fun saveConfig(socks: String, hostname: String, tsnetDir: String, luciUrl: String) {
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { prefs ->
                prefs[SOCKS_KEY] = socks
                prefs[HOSTNAME_KEY] = hostname
                prefs[TSNETDIR_KEY] = tsnetDir
                prefs[LUCI_URL_KEY] = luciUrl
            }
            _ui.value = _ui.value.copy(
                socksAddr = socks,
                hostname = hostname,
                tsnetDir = tsnetDir,
                luciUrl = luciUrl
            )
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(checkingUpdate = true, updateStatusMessage = "Memeriksa pembaruan...")
            val currentVer = try {
                BuildConfig.VERSION_NAME
            } catch (_: Exception) {
                "1.0.5"
            }

            val release = UpdateManager.checkUpdate(currentVer)
            _ui.value = _ui.value.copy(
                checkingUpdate = false,
                updateInfo = release,
                updateStatusMessage = when {
                    release == null -> "Gagal memeriksa pembaruan atau sudah versi terbaru."
                    release.isNewer -> "Versi baru tersedia: ${release.tagName}"
                    else -> "Aplikasi sudah versi terbaru (${currentVer})."
                }
            )
        }
    }

    fun downloadAndInstallUpdate(context: Context) {
        val release = _ui.value.updateInfo ?: return
        if (release.downloadUrl.isEmpty()) return

        // 1. Check permission FIRST before starting any download
        if (!UpdateManager.hasInstallPermission(context)) {
            _ui.value = _ui.value.copy(
                updateStatusMessage = "Silakan aktifkan 'Izinkan dari sumber ini' terlebih dahulu."
            )
            UpdateManager.requestInstallPermission(context)
            return
        }

        viewModelScope.launch {
            val cacheDir = context.externalCacheDir ?: context.cacheDir
            val apkFile = File(cacheDir, "update_${release.versionName}.apk")

            // 2. Reuse already downloaded APK file to save data quota
            if (apkFile.exists() && apkFile.length() > 50_000_000L) {
                _ui.value = _ui.value.copy(
                    downloadingUpdate = false,
                    updateStatusMessage = "File APK sudah tersedia. Membuka installer..."
                )
                UpdateManager.installApk(context, apkFile)
                return@launch
            }

            // 3. Otherwise start download
            _ui.value = _ui.value.copy(
                downloadingUpdate = true,
                downloadProgress = 0f,
                updateStatusMessage = "Mengunduh pembaruan ${release.tagName}..."
            )

            val success = UpdateManager.downloadApk(
                downloadUrl = release.downloadUrl,
                outputFile = apkFile
            ) { progress ->
                _ui.value = _ui.value.copy(downloadProgress = progress)
            }

            if (success) {
                _ui.value = _ui.value.copy(
                    downloadingUpdate = false,
                    updateStatusMessage = "Unduhan selesai. Membuka installer..."
                )
                UpdateManager.installApk(context, apkFile)
            } else {
                _ui.value = _ui.value.copy(
                    downloadingUpdate = false,
                    updateStatusMessage = "Gagal mengunduh APK pembaruan."
                )
            }
        }
    }

    fun startProxy() {
        val ctx = getApplication<Application>()
        val state = _ui.value
        _ui.value = _ui.value.copy(statusText = "Memulai...")
        TsProxyApp.appendLog("UI: startProxy clicked, socks=${state.socksAddr} host=${state.hostname}")
        try {
            TsProxyService.start(ctx, state.socksAddr, state.hostname, state.tsnetDir)
        } catch (e: Exception) {
            val sw = java.io.StringWriter()
            e.printStackTrace(java.io.PrintWriter(sw))
            TsProxyApp.appendLog("UI startProxy EXCEPTION: $sw")
            _ui.value = _ui.value.copy(
                statusText = "Failed: ${e.message?.take(100) ?: "unknown"}",
                crashLog = TsProxyApp.readCrashLog()
            )
        }
    }

    fun stopProxy() {
        val ctx = getApplication<Application>()
        _ui.value = _ui.value.copy(statusText = "Menghentikan...")
        TsProxyApp.appendLog("UI: stopProxy clicked")
        try {
            TsProxyService.stop(ctx)
        } catch (e: Exception) {
            TsProxyApp.appendLog("UI stopProxy EXCEPTION: ${e.message}")
        }
    }

    fun clearCrashLog() {
        TsProxyApp.clearCrashLog()
        _ui.value = _ui.value.copy(crashLog = "")
    }

    fun clearLogs() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                Tsproxy.clearLogs()
            }
            _ui.value = _ui.value.copy(logs = "")
        }
    }

    fun toggleLogPause() {
        _ui.value = _ui.value.copy(logPaused = !_ui.value.logPaused)
    }

    private fun startStatusPolling() {
        viewModelScope.launch {
            while (true) {
                withContext(Dispatchers.IO) {
                    val running = Tsproxy.isRunning()
                    val ip = if (running) Tsproxy.getTailscaleIP() ?: "" else ""
                    val logs = Tsproxy.getLogs() ?: ""
                    val crashLog = TsProxyApp.readCrashLog()
                    val url = Tsproxy.getLoginURL() ?: ""
                    val newStatus = when {
                        running -> "Berjalan"
                        url.isNotEmpty() -> "Butuh Otorisasi"
                        else -> _ui.value.statusText
                    }
                    _ui.value = _ui.value.copy(
                        running = running,
                        tailscaleIP = ip,
                        logs = logs,
                        loginUrl = url,
                        crashLog = crashLog,
                        statusText = newStatus
                    )
                }
                kotlinx.coroutines.delay(2000)
            }
        }
    }

    companion object {
        private val SOCKS_KEY = stringPreferencesKey("socks_addr")
        private val HOSTNAME_KEY = stringPreferencesKey("hostname")
        private val TSNETDIR_KEY = stringPreferencesKey("tsnet_dir")
        private val LUCI_URL_KEY = stringPreferencesKey("luci_url")
    }
}
