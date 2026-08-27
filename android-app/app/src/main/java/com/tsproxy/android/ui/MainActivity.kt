package com.tsproxy.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var showConfig by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "ts-socks5",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            StatusBadge(ui.running)
        }

        Spacer(Modifier.height(16.dp))

        // Status card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                StatusRow("Status", ui.statusText)
                if (ui.tailscaleIP.isNotEmpty()) {
                    StatusRow("Tailscale IP", ui.tailscaleIP)
                }
                StatusRow("SOCKS5", ui.socksAddr)
                StatusRow("Nama Host (Device Name)", ui.hostname)

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.startProxy() },
                        enabled = !ui.running,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.PlayArrow, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Mulai")
                    }
                    Button(
                        onClick = { vm.stopProxy() },
                        enabled = ui.running,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Stop, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Berhenti")
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Config toggle
        OutlinedButton(
            onClick = { showConfig = !showConfig },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                if (showConfig) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                null
            )
            Spacer(Modifier.width(4.dp))
            Text("Konfigurasi")
        }

        AnimatedVisibility(showConfig) {
            Column {
                ConfigSection(ui, vm)
                Spacer(Modifier.height(12.dp))
                SettingsSection()
            }
        }

        Spacer(Modifier.height(12.dp))

        // Login URL button (if present)
        if (ui.loginUrl.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "需要 Tailscale 认证",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ui.loginUrl)))
                    }) {
                        Icon(Icons.Filled.OpenInBrowser, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Buka Link Login")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // ts日志 — scrollable, newest first, with pause and copy
        val logLines = remember(ui.logs) {
            if (ui.logs.isEmpty()) emptyList()
            else ui.logs.split("\n").filter { it.isNotEmpty() }.reversed()
        }
        // When paused, freeze the displayed lines
        var frozenLogs by remember { mutableStateOf<List<String>>(emptyList()) }
        val displayLogs = if (ui.logPaused) frozenLogs else logLines
        LaunchedEffect(ui.logPaused, logLines) {
            if (!ui.logPaused) frozenLogs = logLines
        }

        val clipboardManager = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val haptics = LocalHapticFeedback.current

        LogCard(
            title = "Log Tailscale (${displayLogs.size})",
            lines = displayLogs,
            onClear = { vm.clearLogs() },
            onPauseToggle = { vm.toggleLogPause() },
            paused = ui.logPaused,
            onCopyAll = {
                val text = displayLogs.joinToString("\n")
                clipboardManager.setPrimaryClip(ClipData.newPlainText("Log Tailscale", text))
                Toast.makeText(ctx, "Berhasil menyalin ${displayLogs.size} log", Toast.LENGTH_SHORT).show()
            },
            onCopyLine = { line ->
                clipboardManager.setPrimaryClip(ClipData.newPlainText("日志", line))
                Toast.makeText(ctx, "Berhasil disalin", Toast.LENGTH_SHORT).show()
            },
            haptics = haptics,
            containerColor = null // default
        )

        Spacer(Modifier.height(12.dp))

        // 程序日志 — scrollable with copy
        val crashLines = remember(ui.crashLog) {
            if (ui.crashLog.isEmpty()) emptyList()
            else ui.crashLog.trim().split("\n").filter { it.isNotEmpty() }
        }

        if (crashLines.isNotEmpty()) {
            LogCard(
                title = "Log Aplikasi (${crashLines.size})",
                lines = crashLines,
                onClear = { vm.clearCrashLog() },
                onPauseToggle = null, // no pause for program log
                paused = false,
                onCopyAll = {
                    val text = crashLines.joinToString("\n")
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("Log Aplikasi", text))
                    Toast.makeText(ctx, "Berhasil menyalin ${crashLines.size} log", Toast.LENGTH_SHORT).show()
                },
                onCopyLine = { line ->
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("日志", line))
                    Toast.makeText(ctx, "Berhasil disalin", Toast.LENGTH_SHORT).show()
                },
                haptics = haptics,
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}


@Composable
fun LogLine(
    line: String,
    containerColor: androidx.compose.ui.graphics.Color?,
    onCopyLine: (String) -> Unit,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCopyLine(line)
                    }
                )
            }
            .padding(vertical = 2.dp, horizontal = 4.dp)
    ) {
        Text(
            text = line,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            color = if (containerColor != null)
                MaterialTheme.colorScheme.onErrorContainer
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        )
    }
}
@Composable
fun LogCard(
    title: String,
    lines: List<String>,
    onClear: () -> Unit,
    onPauseToggle: (() -> Unit)?,
    paused: Boolean,
    onCopyAll: () -> Unit,
    onCopyLine: (String) -> Unit,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    containerColor: androidx.compose.ui.graphics.Color?
) {
    val cardColors = if (containerColor != null) {
        CardDefaults.cardColors(containerColor = containerColor)
    } else {
        CardDefaults.cardColors()
    }

    Card(modifier = Modifier.fillMaxWidth(), colors = cardColors) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (containerColor != null) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onPauseToggle != null) {
                        TextButton(onClick = onPauseToggle) {
                            Text(if (paused) "Lanjutkan" else "Jeda")
                        }
                    }
                    TextButton(onClick = onCopyAll) {
                        Text("Salin")
                    }
                    TextButton(onClick = onClear) {
                        Text("Bersihkan")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            if (lines.isEmpty()) {
                Text(
                    if (paused) "Dijeda, klik lanjutkan untuk memuat ulang" else "Tidak ada log",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (containerColor != null) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val listState = rememberLazyListState()
                LaunchedEffect(lines.size) {
                    if (lines.isNotEmpty() && !paused) {
                        listState.scrollToItem(0)
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    items(lines) { line ->
                        LogLine(
                            line = line,
                            containerColor = containerColor,
                            onCopyLine = onCopyLine,
                            haptics = haptics
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConfigSection(ui: UiState, vm: MainViewModel) {
    var socks by remember { mutableStateOf(ui.socksAddr) }
    var hostname by remember { mutableStateOf(ui.hostname) }
    var tsnetDir by remember { mutableStateOf(ui.tsnetDir) }

    LaunchedEffect(ui.socksAddr, ui.hostname, ui.tsnetDir) {
        socks = ui.socksAddr
        hostname = ui.hostname
        tsnetDir = ui.tsnetDir
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "基本配置",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = socks,
                onValueChange = { socks = it },
                label = { Text("Alamat Listen SOCKS5") },
                placeholder = { Text("Contoh: 127.0.0.1:1080") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = hostname,
                onValueChange = { hostname = it },
                label = { Text("Nama Perangkat Tailscale") },
                placeholder = { Text("Contoh: ts-socks5") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = tsnetDir,
                onValueChange = { tsnetDir = it },
                label = { Text("Direktori Data tsnet (Opsional)") },
                placeholder = { Text("Default: Otomatis") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { vm.saveConfig(socks, hostname, tsnetDir) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Save, null)
                Spacer(Modifier.width(4.dp))
                Text("Simpan Konfigurasi")
            }

            Spacer(Modifier.height(8.dp))

            Text(
                "Clash 配置参考: 将 100.64.0.0/10 和 fd7a:115c:a1e0::/48 路由到 socks5://127.0.0.1:1080",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsSection() {
    val ctx = LocalContext.current

    var batteryIgnored by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
                pm.isIgnoringBatteryOptimizations(ctx.packageName)
            } else {
                true
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "保活设置",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))

            // Battery optimization
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "电池优化",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        if (batteryIgnored) "Diizinkan (Bypass)" else "Tidak diizinkan, aplikasi mungkin mati di background",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (batteryIgnored)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                }
                if (!batteryIgnored && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${ctx.packageName}")
                                }
                                ctx.startActivity(intent)
                                batteryIgnored = true
                            } catch (_: Exception) {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                ctx.startActivity(intent)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("Pengaturan", style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Icon(
                        Icons.Filled.CheckCircle,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Persistent notification
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "常驻通知",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "确保通知不可被系统清除",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = {
                        try {
                            val intent = Intent().apply {
                                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                            }
                            ctx.startActivity(intent)
                        } catch (_: Exception) {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${ctx.packageName}")
                            }
                            ctx.startActivity(intent)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("Pengaturan", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun StatusBadge(running: Boolean) {
    val color = if (running) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.error
    val text = if (running) "Berjalan" else "Berhenti"

    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                if (running) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Text(text, color = color, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
