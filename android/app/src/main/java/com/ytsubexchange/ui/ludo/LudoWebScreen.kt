package com.ytsubexchange.ui.ludo

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LudoWebScreen(
    playerCount: Int = 4,
    mode: LudoMode = LudoMode.VS_COMPUTER,
    onBack: () -> Unit,
    onGameEnd: (List<String>) -> Unit = {}
) {
    Column(Modifier.fillMaxSize().background(Color(0xFF1a0a3e))) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    @Suppress("DEPRECATION")
                    settings.allowFileAccessFromFileURLs = true
                    @Suppress("DEPRECATION")
                    settings.allowUniversalAccessFromFileURLs = true
                    settings.builtInZoomControls = false
                    settings.displayZoomControls = false
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    setBackgroundColor(android.graphics.Color.parseColor("#1a0a3e"))

                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onGameEnd(result: String) {
                            val colors = result.trim('[', ']').split(",").map { it.trim('"', ' ') }
                            onGameEnd(colors)
                        }
                    }, "Android")

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            // Menu is shown by default in ludo.html — no auto-start needed
                        }
                    }
                    loadUrl("file:///android_asset/ludo.html")
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
