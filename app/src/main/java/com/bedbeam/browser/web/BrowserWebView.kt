package com.bedbeam.browser.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.bedbeam.browser.adblock.AdBlocker
import com.bedbeam.browser.ui.Palette
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * The single WebView, configured for a low-memory TV, with the ad blocker built in:
 * ad requests are cut, popups/new windows are only allowed from a click and to the same site,
 * navigations to ad domains are refused, and known ad boxes are hidden with CSS.
 * Full-screen video arrives in step 4.
 */
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
class BrowserWebView(
    context: Context,
    private val adBlocker: AdBlocker,
    private val listener: Listener,
) : WebView(context) {

    interface Listener {
        fun onPageStarted(url: String)
        fun onPageFinished(url: String, title: String?)
        /** The page's renderer process died (usually killed for memory). */
        fun onRendererGone()
    }

    /** Host of the page being shown; read from WebView's background threads. */
    @Volatile
    var pageHost: String? = null
        private set

    private val blocked = AtomicInteger()
    /** Ads, trackers and popups blocked on the current page. */
    val blockedCount get() = blocked.get()

    private var cosmetic: ScriptHandler? = null

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
            // New windows go through onCreateWindow, where popups are filtered.
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false // no window.open without a click
            setGeolocationEnabled(false)
            // Some video sites block or downgrade embedded WebViews: look like regular Chrome.
            userAgentString = userAgentString.replace("; wv)", ")").replace(" Version/4.0", "")
        }

        webViewClient = Client()
        webChromeClient = Chrome()
        refreshAdBoxHiding()
    }

    /** Re-apply the ad-box CSS after the per-site ad-block setting changed. */
    fun refreshAdBoxHiding() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        cosmetic?.remove()
        val off = adBlocker.disabledSitesJson()
        val js = """
            (function(){var off=$off;var h=location.hostname.replace(/^www\./,'');
            for(var i=0;i<off.length;i++){if(h===off[i]||h.endsWith('.'+off[i]))return;}
            var s=document.createElement('style');s.textContent=${jsString(AD_CSS)};
            (document.head||document.documentElement).appendChild(s);})();
        """.trimIndent()
        cosmetic = WebViewCompat.addDocumentStartJavaScript(this, js, setOf("*"))
    }

    /** Free everything this WebView holds. Call instead of just dropping the reference. */
    fun release() {
        stopLoading()
        cosmetic?.remove()
        cosmetic = null
        webChromeClient = null
        loadUrl("about:blank")
        onPause()
        removeAllViews()
        destroy()
    }

    private fun sameSite(a: String?, b: String?) =
        a != null && b != null && AdBlocker.site(a) == AdBlocker.site(b)

    private inner class Client : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val host = request.url.host
            if (request.isForMainFrame) {
                pageHost = host
                blocked.set(0)
                return null
            }
            if (adBlocker.shouldBlock(host, pageHost)) {
                blocked.incrementAndGet()
                return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
            }
            return null
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val scheme = request.url.scheme?.lowercase()
            // Stay on the web: never hand off to app links (intent://, market://, tg://…).
            if (scheme != "http" && scheme != "https" && scheme != "about" && scheme != "data" && scheme != "blob") {
                blocked.incrementAndGet()
                return true
            }
            // Refuse page/frame navigations to ad domains (ad redirects, "click-under" ads).
            if (adBlocker.shouldBlock(request.url.host, pageHost)) {
                blocked.incrementAndGet()
                return true
            }
            return false
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            Uri.parse(url).host?.let { pageHost = it }
            listener.onPageStarted(url)
        }

        override fun onPageFinished(view: WebView, url: String) = listener.onPageFinished(url, view.title)

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            listener.onRendererGone()
            return true // handled: don't crash the app
        }
    }

    private inner class Chrome : WebChromeClient() {
        /**
         * One tab: a new window is either opened in this tab (a click to the same site) or dropped
         * (no click, an ad domain, or another site, which on video sites is almost always a popunder).
         */
        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            val blocking = adBlocker.isEnabledFor(pageHost)
            if (!isUserGesture && blocking) {
                blocked.incrementAndGet()
                return false
            }
            // The new window's URL is only known once it navigates: give it a throwaway WebView to find out.
            val probe = WebView(view.context)
            var done = false
            fun finish(url: String?) {
                if (done) return
                done = true
                probe.post { probe.destroy() }
                val host = url?.let { Uri.parse(it).host } ?: return
                if (!blocking || (sameSite(host, pageHost) && !adBlocker.shouldBlock(host, pageHost))) {
                    loadUrl(url)
                } else {
                    blocked.incrementAndGet()
                }
            }
            probe.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                    finish(r.url.toString())
                    return true
                }

                override fun onPageStarted(v: WebView, url: String, favicon: Bitmap?) {
                    if (url != "about:blank") finish(url)
                }
            }
            probe.postDelayed({ finish(null) }, 5000) // a popup that never navigates: just drop it
            (resultMsg.obj as WebView.WebViewTransport).webView = probe
            resultMsg.sendToTarget()
            return true
        }
    }

    companion object {
        /** Common ad containers (Google, Yandex, AdFox and generic banners). Kept small and safe. */
        private const val AD_CSS =
            "ins.adsbygoogle,.adsbygoogle,[id^='google_ads_'],[id^='div-gpt-ad'],iframe[id^='google_ads_iframe']," +
                "iframe[src*='doubleclick.net'],iframe[src*='googlesyndication.com'],[id^='yandex_rtb']," +
                "[id^='adfox_'],div[id^='adfox'],.yandex-adv,[id^='begun_block'],[class^='adv-banner']," +
                "[data-ad-slot],amp-ad,amp-embed[type='taboola'],.taboola,[id^='taboola-']" +
                "{display:none!important;}"

        private fun jsString(s: String) = "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'"
    }
}
