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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
        // Chrome-Style Modern Top Navigation Bar
        Surface(
            tonalElevation = 3.dp,
            shadowElevation = 3.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                // Back & Forward navigation (Chrome style)
                IconButton(
                    onClick = { webViewRef?.goBack() },
                    enabled = webViewRef?.canGoBack() == true,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = "Kembali",
                        tint = if (webViewRef?.canGoBack() == true) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }

                IconButton(
                    onClick = { webViewRef?.goForward() },
                    enabled = webViewRef?.canGoForward() == true,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Filled.ArrowForward,
                        contentDescription = "Maju",
                        tint = if (webViewRef?.canGoForward() == true) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }

                // Chrome-style Address Bar Pill
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    ) {
                        Icon(
                            if (currentUrl.startsWith("https://")) Icons.Filled.Lock else Icons.Filled.Language,
                            contentDescription = "Security Status",
                            tint = if (currentUrl.startsWith("https://")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        OutlinedTextField(
                            value = urlText,
                            onValueChange = { urlText = it },
                            modifier = Modifier.weight(1f),
                            textStyle = TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent
                            ),
                            contentPadding = PaddingValues(0.dp)
                        )
                        if (urlText.trim() != currentUrl.trim()) {
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
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Filled.ArrowForward, contentDescription = "Go", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // Refresh Button
                IconButton(
                    onClick = { webViewRef?.reload() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        if (isLoading) Icons.Filled.Close else Icons.Filled.Refresh,
                        contentDescription = "Muat Ulang",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Close Browser Button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Tutup", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        // Progress bar below address bar
        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.5.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // High-Performance Accelerated WebView (Fast LuCI Tab Switching)
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewRef = this

                    // Enable Hardware Acceleration for fast UI transitions
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)

                    // Enable Cookies
                    CookieManager.getInstance().apply {
                        setAcceptCookie(true)
                        setAcceptThirdPartyCookies(this@apply, true)
                    }

                    settings.apply {
                        // Clean modern Chrome Mobile User Agent for fast responsive LuCI rendering
                        userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true

                        // Fast local static asset caching (CSS, JS, icons) for instant menu switches
                        cacheMode = WebSettings.LOAD_DEFAULT

                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        javaScriptCanOpenWindowsAutomatically = true

                        // Offscreen pre-rastering for smooth scrolling & instantaneous tab transitions
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                            offscreenPreRaster = true
                        }
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
