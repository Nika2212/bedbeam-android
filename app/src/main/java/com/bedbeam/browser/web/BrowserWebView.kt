package com.bedbeam.browser.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.bedbeam.browser.ui.Palette

/**
 * The single WebView, configured for a low-memory TV.
 * Popup/redirect/ad blocking arrives in step 3, full-screen video in step 4.
 */
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
class BrowserWebView(
    context: Context,
    private val listener: Listener,
) : WebView(context) {

    interface Listener {
        fun onPageStarted(url: String)
        fun onPageFinished(url: String, title: String?)
        /** The page's renderer process died (usually killed for memory). */
        fun onRendererGone()
    }

    init {
        setBackgroundColor(Palette.BG)
        isFocusable = true
        isFocusableInTouchMode = true
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false // let video players start from a remote "click"
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            // Open target=_blank links in this one tab. (Smarter popup handling comes with the ad blocker.)
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            setGeolocationEnabled(false)
            // Some video sites block or downgrade embedded WebViews: look like regular Chrome.
            userAgentString = userAgentString.replace("; wv)", ")").replace(" Version/4.0", "")
        }

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme?.lowercase()
                // Stay on the web: never hand off to app links (intent://, market://, tg://…).
                return scheme != "http" && scheme != "https" && scheme != "about" && scheme != "data" && scheme != "blob"
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) = listener.onPageStarted(url)

            override fun onPageFinished(view: WebView, url: String) = listener.onPageFinished(url, view.title)

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                listener.onRendererGone()
                return true // handled: don't crash the app
            }
        }
        webChromeClient = WebChromeClient()
    }

    /** Free everything this WebView holds. Call instead of just dropping the reference. */
    fun release() {
        stopLoading()
        webChromeClient = null
        loadUrl("about:blank")
        onPause()
        removeAllViews()
        destroy()
    }
}
