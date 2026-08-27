package com.tsproxy.android.ui

import android.annotation.SuppressLint
import android.net.http.SslError
import android.view.View
import android.webkit.CookieManager
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LuciBrowserScreen(
    initialUrl: String = "https://100.73.70.18/cgi-bin/luci/",
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
                    .addDirect()
                    .build()
                val executor = Executors.newSingleThreadExecutor()
                ProxyController.getInstance().setProxyOverride(proxyConfig, executor, Runnable {
                    // Proxy applied callback
                })
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
        // Ultra-Compact Single Row Toolbar
        Surface(
            tonalElevation = 2.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                // Close button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Tutup", modifier = Modifier.size(20.dp))
                }

                // Back button
                IconButton(
                    onClick = { webViewRef?.goBack() },
                    enabled = webViewRef?.canGoBack() == true,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali", modifier = Modifier.size(18.dp))
                }

                // Forward button
                IconButton(
                    onClick = { webViewRef?.goForward() },
                    enabled = webViewRef?.canGoForward() == true,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Filled.ArrowForward, contentDescription = "Maju", modifier = Modifier.size(18.dp))
                }

                // Refresh button
                IconButton(
                    onClick = { webViewRef?.reload() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Muat Ulang", modifier = Modifier.size(18.dp))
                }

                // Compact URL Field
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    textStyle = TextStyle(fontSize = 12.sp),
                    singleLine = true,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                )

                // Go button
                IconButton(
                    onClick = {
                        var formatted = urlText.trim()
                        if (!formatted.startsWith("http://") && !formatted.startsWith("https://")) {
                            formatted = "https://$formatted"
                        }
                        currentUrl = formatted
                        urlText = formatted
                        webViewRef?.loadUrl(formatted)
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Buka", modifier = Modifier.size(20.dp))
                }
            }
        }

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
        }

        // High-Performance Embedded WebView
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewRef = this
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)

                    // Enable Cookies
                    CookieManager.getInstance().apply {
                        setAcceptCookie(true)
                        setAcceptThirdPartyCookies(this@apply, true)
                    }

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT // Fast static asset caching (CSS, JS)
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        javaScriptCanOpenWindowsAutomatically = true
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
