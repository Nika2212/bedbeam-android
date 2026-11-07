package com.bedbeam.browser.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.bedbeam.browser.adblock.AdBlocker
import com.bedbeam.browser.ui.Palette
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * The single WebView, configured for a low-memory TV, with the ad blocker built in:
 * ad requests are cut, popups/new windows are only allowed from a click and to the same site,
 * navigations to ad domains are refused, and known ad boxes are hidden with CSS.
 * Also: full-screen video, DRM (Widevine) playback, downloads and file upload via [Listener].
 */
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
class BrowserWebView(
    context: Context,
    private val adBlocker: AdBlocker,
    private val listener: Listener,
    desktopMode: Boolean,
) : WebView(context) {

    /** Chrome's own mobile UA (without the WebView markers) and the matching desktop one. */
    private val mobileUa: String
    private val desktopUa: String

    /** Desktop sites: a desktop Chrome user agent, so sites serve their full layout and player. */
    fun setDesktopMode(on: Boolean) {
        val ua = if (on) desktopUa else mobileUa
        if (settings.userAgentString != ua) {
            settings.userAgentString = ua
            if (url != null) reload()
        }
    }

    interface Listener {
        /** A page asked to choose a file (<input type=file>). Return true if handled. */
        fun onFileChooser(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean
        /** A link started a download. */
        fun onDownload(url: String, userAgent: String, contentDisposition: String?, mimeType: String?)
        fun onPageStarted(url: String)
        fun onPageFinished(url: String, title: String?)
        /** The page's renderer process died (usually killed for memory). */
        fun onRendererGone()
        /** A player asked for full screen: show [view] over everything. */
        fun onEnterFullscreen(view: View, exit: WebChromeClient.CustomViewCallback)
        fun onExitFullscreen()
        /** Some video on the page started (true) or stopped (false) playing. */
        fun onVideoPlaying(playing: Boolean)
        /** Reply to a video command: position, duration (0 if unknown), paused. */
        fun onVideoState(t: Double, d: Double, paused: Boolean)
        /** A video command found no <video> on the page (e.g. the player loads it on first play). */
        fun onNoVideo()
        /** A text field on the page (any frame) got focus or was clicked: the keyboard should show. */
        fun onEditableFocused()
        /** Link mode: the focused element's centre in CSS px, with the page's CSS viewport width. */
        fun onLinkTarget(x: Double, y: Double, viewportWidth: Double)
    }

    /** Send a command to the top page (link mode). False if the page script isn't ready. */
    fun pageCommand(cmd: String): Boolean {
        val frame = topFrame ?: return false
        frame.postMessage(cmd)
        return true
    }

    /** Last frame that reported a video / a playing video: where remote commands go. */
    private var videoFrame: JavaScriptReplyProxy? = null
    private var playingFrame: JavaScriptReplyProxy? = null
    private var topFrame: JavaScriptReplyProxy? = null

    /** Send a command to the page's main video ("toggle", "seek:10", "state", "exitfs"). False if none. */
    fun videoCommand(cmd: String): Boolean {
        val frame = playingFrame ?: videoFrame ?: topFrame ?: return false
        frame.postMessage(cmd)
        return true
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
            // Don't move focus to the page's first element (and scroll to the top) whenever the
            // WebView regains focus, e.g. after the cross menu closes.
            setNeedInitialFocus(false)
        }
        // Some video sites block or downgrade embedded WebViews: look like regular Chrome.
        mobileUa = settings.userAgentString.replace("; wv)", ")").replace(" Version/4.0", "")
        val chrome = Regex("Chrome/[\\d.]+").find(mobileUa)?.value ?: "Chrome/120.0.0.0"
        desktopUa = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) $chrome Safari/537.36"
        settings.userAgentString = if (desktopMode) desktopUa else mobileUa

        // Logins and players embedded from another site (iframes) need their own cookies.
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

        setDownloadListener { dlUrl, ua, disposition, mime, _ -> listener.onDownload(dlUrl, ua, disposition, mime) }

        // While BedBeam is in the background, let Android reclaim the page process before other apps.
        setRendererPriorityPolicy(RENDERER_PRIORITY_IMPORTANT, true)

        webViewClient = Client()
        webChromeClient = Chrome()
        refreshAdBoxHiding()
        installVideoBridge()
    }

    private fun installVideoBridge() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        ) {
            Log.w(TAG, "video bridge unsupported by this WebView")
            return
        }
        WebViewCompat.addWebMessageListener(this, "bedbeamNative", setOf("*")) { _, message, _, isMain, frame ->
            val o = try { JSONObject(message.data ?: return@addWebMessageListener) } catch (e: Exception) { return@addWebMessageListener }
            when (o.optString("type")) {
                "hello" -> if (isMain) topFrame = frame
                "none" -> listener.onNoVideo()
                "video" -> videoFrame = frame
                "playing" -> {
                    playingFrame = frame
                    listener.onVideoPlaying(true)
                }
                "paused" -> if (frame == playingFrame) listener.onVideoPlaying(false)
                "state" -> listener.onVideoState(o.optDouble("t"), o.optDouble("d"), o.optBoolean("paused"))
                "edit" -> listener.onEditableFocused()
                "target" -> if (isMain) listener.onLinkTarget(o.optDouble("x"), o.optDouble("y"), o.optDouble("vw"))
            }
        }
        val js = context.assets.open("page_bridge.js").bufferedReader().use { it.readText() }
        WebViewCompat.addDocumentStartJavaScript(this, js, setOf("*"))
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
        releasing = true
        stopLoading()
        // Chromium keeps the page's renderer process cached after destroy() (~150 MB on a video site).
        // On a 1 GB device that memory is needed back now: end the process explicitly.
        try {
            webViewRenderProcess?.terminate()
        } catch (e: Exception) {
            Log.w(TAG, "could not end renderer", e)
        }
        cosmetic?.remove()
        cosmetic = null
        webChromeClient = null
        onPause()
        removeAllViews()
        destroy()
    }

    /** Set while [release] runs: the renderer exit it causes is expected, not a crash. */
    @Volatile
    private var releasing = false

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
            if (playingFrame != null) listener.onVideoPlaying(false)
            videoFrame = null
            playingFrame = null
            topFrame = null
            listener.onPageStarted(url)
        }

        override fun onPageFinished(view: WebView, url: String) = listener.onPageFinished(url, view.title)

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            if (!releasing) listener.onRendererGone()
            return true // handled: don't crash the app
        }
    }

    private inner class Chrome : WebChromeClient() {
        override fun onConsoleMessage(m: ConsoleMessage): Boolean {
            if (m.messageLevel() == ConsoleMessage.MessageLevel.ERROR) Log.w(TAG, "console: ${m.message()} @${m.sourceId()}:${m.lineNumber()}")
            return true
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) =
            listener.onEnterFullscreen(view, callback)

        override fun onHideCustomView() = listener.onExitFullscreen()

        /** Allow DRM-protected video (Widevine); refuse camera, microphone and the rest. */
        override fun onPermissionRequest(request: PermissionRequest) {
            val ok = request.resources.filter { it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }
            if (ok.isNotEmpty()) request.grant(ok.toTypedArray()) else request.deny()
        }

        override fun onShowFileChooser(
            view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams,
        ): Boolean = listener.onFileChooser(callback, params)

        // Black instead of the default grey play-button poster while a video loads.
        override fun getDefaultVideoPoster(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

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
        private const val TAG = "BedBeamVideo"

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
