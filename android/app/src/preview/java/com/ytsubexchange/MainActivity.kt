package com.ytsubexchange

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

/**
 * Preview flavor MainActivity — sirf WebView hai, backend se connected.
 * Existing prod code bilkul touch nahi hua.
 */
class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                useWideViewPort = true
                loadWithOverviewMode = true
                allowFileAccess = true
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    // YouTube links bahar browser mein khulein
                    val url = request.url.toString()
                    if (url.contains("youtube.com") || url.contains("youtu.be")) {
                        startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, request.url))
                        return true
                    }
                    return false
                }
            }
            // Backend URL — prototype HTML serve ho raha hai server se
            loadUrl(PREVIEW_URL)
        }

        setContentView(webView)

        // Back button — WebView history navigate kare
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack()
                else finish()
            }
        })
    }

    companion object {
        // Server pe full-preview.html serve karo ya koi bhi URL
        const val PREVIEW_URL = "https://api.picrypto.in/preview"
    }
}
