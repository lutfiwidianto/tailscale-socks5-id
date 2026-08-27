package com.tsproxy.android.ui

import android.annotation.SuppressLint
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LuciBrowserScreen(
    initialUrl: String = "http://100.64.0.1",
    socksAddress: String = "127.0.0.1:1080",
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var urlText by remember { mutableStateOf(initialUrl) }
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Configure SOCKS5 proxy for WebView via WebViewFeature if supported
    LaunchedEffect(socksAddress) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            try {
                val cleanAddr = socksAddress.removePrefix("http://").removePrefix("socks5://")
                val proxyConfig = ProxyConfig.Builder()
                    .addProxyRule("socks5://$cleanAddr")
                    .addProxyRule("direct://")
                    .build()
                val executor = Executors.newSingleThreadExecutor()
                ProxyController.getInstance().setProxyOverride(proxyConfig, executor) {
                    // Proxy applied callback
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Toolbar & Address Bar
        Surface(
            tonalElevation = 4.dp,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Tutup Browser")
                    }

                    OutlinedTextField(
                        value = urlText,
                        onValueChange = { urlText = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("http://100.x.y.z atau http://192.168.1.1") },
                        trailingIcon = {
                            IconButton(onClick = {
                                var formatted = urlText.trim()
                                if (!formatted.startsWith("http://") && !formatted.startsWith("https://")) {
                                    formatted = "http://$formatted"
                                }
                                currentUrl = formatted
                                urlText = formatted
                                webViewRef?.loadUrl(formatted)
                            }) {
                                Icon(Icons.Filled.ArrowForward, contentDescription = "Buka URL")
                            }
                        }
                    )
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row {
                        IconButton(
                            onClick = { webViewRef?.goBack() },
                            enabled = webViewRef?.canGoBack() == true
                        ) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali")
                        }
                        IconButton(
                            onClick = { webViewRef?.goForward() },
                            enabled = webViewRef?.canGoForward() == true
                        ) {
                            Icon(Icons.Filled.ArrowForward, contentDescription = "Maju")
                        }
                        IconButton(
                            onClick = { webViewRef?.reload() }
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Muat Ulang")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        AssistChip(
                            onClick = {
                                val url = "http://100.64.0.1"
                                urlText = url
                                currentUrl = url
                                webViewRef?.loadUrl(url)
                            },
                            label = { Text("http://100.64.0.1") }
                        )
                    }
                }
            }
        }

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        // Embedded WebView
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewRef = this
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false
                            url?.let {
                                if (it != currentUrl) {
                                    currentUrl = it
                                    urlText = it
                                }
                            }
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            isLoading = true
                        }

                        @SuppressLint("WebViewClientOnReceivedSslError")
                        override fun onReceivedSslError(
                            view: WebView?,
                            handler: SslErrorHandler?,
                            error: SslError?
                        ) {
                            // Bypass self-signed SSL errors common on LuCI HTTPS
                            handler?.proceed()
                        }
                    }

                    loadUrl(currentUrl)
                }
            },
            update = { webView ->
                webViewRef = webView
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}
