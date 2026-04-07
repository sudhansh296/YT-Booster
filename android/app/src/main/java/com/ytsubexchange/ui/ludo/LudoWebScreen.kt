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
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF1A0A00))
    ) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF2D1500)).padding(4.dp, 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, tint = Color.White)
            }
            Text(
                "🎲 Ludo",
                color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontSize = 18.sp
            )
            Spacer(Modifier.weight(1f))
            if (mode != LudoMode.VS_COMPUTER) {
                Text("50🪙 × $playerCount", color = Color(0xFFFFD700), fontSize = 13.sp,
                    modifier = Modifier.padding(end = 12.dp))
            }
        }

        // WebView board
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.allowFileAccessFromFileURLs = true
                    settings.allowUniversalAccessFromFileURLs = true
                    settings.builtInZoomControls = false
                    settings.displayZoomControls = false
                    // Hardware acceleration for WebGL/Three.js
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    webViewClient = WebViewClient()
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onGameEnd(result: String) {
                            // result = JSON array of color strings in finish order
                            val colors = result.trim('[', ']').split(",").map { it.trim('"', ' ') }
                            onGameEnd(colors)
                        }
                    }, "Android")
                    loadUrl("file:///android_asset/ludo.html")
                    webViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { wv ->
                // Start game with correct player count after page loads
                wv.evaluateJavascript("if(window.startGame) startGame($playerCount);", null)
            }
        )
    }
}
