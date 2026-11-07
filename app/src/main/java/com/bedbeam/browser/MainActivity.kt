package com.bedbeam.browser

import android.app.Activity
import android.content.ComponentCallbacks2
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import android.widget.Toast
import com.bedbeam.browser.adblock.AdBlocker
import com.bedbeam.browser.data.Bookmark
import com.bedbeam.browser.data.Bookmarks
import com.bedbeam.browser.data.History
import com.bedbeam.browser.data.UrlInput
import com.bedbeam.browser.ui.AddressScreen
import com.bedbeam.browser.ui.BookmarkActions
import com.bedbeam.browser.ui.CrossMenu
import com.bedbeam.browser.ui.EditSheet
import com.bedbeam.browser.ui.Palette
import com.bedbeam.browser.ui.StartScreen
import com.bedbeam.browser.ui.VideoOsd
import com.bedbeam.browser.web.BrowserWebView
import com.bedbeam.browser.web.PointerController
import com.bedbeam.browser.web.PointerView
import com.bedbeam.browser.web.VideoKeys

/**
 * One activity, one tab. Layers, bottom to top: the page (WebView + pointer), the start screen,
 * one overlay (address screen, cross menu, bookmark actions or edit sheet).
 */
class MainActivity : Activity(), BrowserWebView.Listener {

    private lateinit var bookmarks: Bookmarks
    private lateinit var history: History
    private lateinit var adBlocker: AdBlocker
    private lateinit var root: FrameLayout
    private lateinit var pageHost: FrameLayout
    private lateinit var start: StartScreen
    private lateinit var pointerView: PointerView
    private lateinit var pointer: PointerController
    private lateinit var fullscreenHost: FrameLayout
    private lateinit var osd: VideoOsd
    private lateinit var videoKeys: VideoKeys

    private var fullscreenView: View? = null
    private var fullscreenExit: WebChromeClient.CustomViewCallback? = null
    private val inFullscreen get() = fullscreenView != null
    private var videoPlaying = false

    private var web: BrowserWebView? = null
    /** Address of the page in the tab, kept even when the WebView is freed. */
    private var pageUrl: String? = null
    private var onPage = false
    private var stopped = false
    private var reloadOnStart = false

    private var overlay: View? = null
    private var overlayBack: (() -> Unit)? = null
    private var focusBeforeOverlay: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bookmarks = Bookmarks(this)
        history = History(this)
        adBlocker = AdBlocker(this).also { it.start() }

        root = FrameLayout(this).apply { setBackgroundColor(Palette.BG) }
        pageHost = FrameLayout(this)
        root.addView(pageHost, match())
        pointerView = PointerView(this)
        pageHost.addView(pointerView, match()) // stays above the WebView, which is inserted at index 0
        pointer = PointerController(pointerView, { web }, onHoldOk = ::openCrossMenu)
        pointerView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> pointer.ensurePlaced() }
        start = StartScreen(
            this, bookmarks,
            onOpenUrl = ::openPage,
            onOpenAddress = { openAddress("") },
            onBookmarkActions = ::openBookmarkActions,
            onAddBookmark = ::openAddBookmark,
        )
        root.addView(start, match())
        fullscreenHost = FrameLayout(this).apply {
            setBackgroundColor(0xFF000000.toInt())
            visibility = View.GONE
        }
        osd = VideoOsd(this)
        fullscreenHost.addView(osd, match())
        videoKeys = VideoKeys(osd, command = { cmd ->
            web?.videoCommand(cmd) == true || (cmd == "toggle" && tapFullscreenCenter())
        }, onHoldOk = ::openCrossMenu)
        root.addView(fullscreenHost, match())
        setContentView(root)
        start.focusTile(0)
    }

    // ---- page -----------------------------------------------------------------------------

    private fun openPage(url: String) {
        closeOverlay(restoreFocus = false)
        pageUrl = url
        onPage = true
        val w = web ?: newWebView()
        w.loadUrl(url)
        start.visibility = View.GONE
        w.requestFocus()
        pointer.ensurePlaced()
    }

    private fun newWebView(): BrowserWebView =
        BrowserWebView(this, adBlocker, this).also {
            web = it
            pageHost.addView(it, 0, match())
        }

    private fun freeWebView() {
        if (inFullscreen) exitFullscreen()
        videoPlaying = false
        updateKeepScreenOn()
        web?.let {
            pageHost.removeView(it)
            it.release()
        }
        web = null
    }

    private fun showStart() {
        onPage = false
        pageUrl = null
        freeWebView() // one tab: leaving the page frees all its memory
        start.visibility = View.VISIBLE
        start.restoreFocus()
    }

    override fun onPageStarted(url: String) {
        pageUrl = url
    }

    override fun onPageFinished(url: String, title: String?) {
        if (url.startsWith("http")) {
            pageUrl = url
            history.add(url, title)
        }
    }

    // ---- full-screen video ----------------------------------------------------------------------

    override fun onEnterFullscreen(view: View, exit: WebChromeClient.CustomViewCallback) {
        if (inFullscreen) {
            exit.onCustomViewHidden()
            return
        }
        fullscreenView = view
        fullscreenExit = exit
        fullscreenHost.addView(view, 0, match())
        fullscreenHost.visibility = View.VISIBLE
        pointer.stop()
        pointerView.visibility = View.GONE
        updateKeepScreenOn()
        web?.videoCommand("state")
    }

    override fun onExitFullscreen() = removeFullscreen()

    /** Leave full screen from our side (Back, or leaving the page). */
    private fun exitFullscreen() {
        val exit = fullscreenExit
        web?.videoCommand("exitfs")
        removeFullscreen()
        exit?.onCustomViewHidden()
    }

    private fun removeFullscreen() {
        val v = fullscreenView ?: return
        fullscreenView = null
        fullscreenExit = null
        fullscreenHost.removeView(v)
        fullscreenHost.visibility = View.GONE
        osd.hideNow()
        pointerView.visibility = View.VISIBLE
        updateKeepScreenOn()
        web?.requestFocus()
    }

    override fun onVideoPlaying(playing: Boolean) {
        videoPlaying = playing
        updateKeepScreenOn()
    }

    override fun onVideoState(t: Double, d: Double, paused: Boolean) {
        val afterToggle = videoKeys.consumeToggleFlag()
        if (inFullscreen) videoKeys.onState(t, d, paused, afterToggle)
    }

    override fun onNoVideo() {
        // OK found no <video> yet: click the middle of the player instead, which starts it in most players.
        if (inFullscreen && videoKeys.consumeToggleFlag()) tapFullscreenCenter()
    }

    private fun tapFullscreenCenter(): Boolean {
        val v = fullscreenView ?: return false
        val x = v.width / 2f
        val y = v.height / 2f
        val t = android.os.SystemClock.uptimeMillis()
        listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP).forEach { action ->
            val e = android.view.MotionEvent.obtain(t, t + (if (action == android.view.MotionEvent.ACTION_UP) 50 else 0), action, x, y, 0)
            e.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            v.dispatchTouchEvent(e)
            e.recycle()
        }
        return true
    }

    /** No screensaver while a video plays or is full screen. */
    private fun updateKeepScreenOn() {
        if (inFullscreen || videoPlaying) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onRendererGone() {
        // The page process was killed (usually for memory). Rebuild the WebView and reload.
        val url = pageUrl
        freeWebView()
        if (onPage && url != null) {
            if (stopped) reloadOnStart = true else openPage(url)
        }
    }

    // ---- overlays -------------------------------------------------------------------------

    private fun showOverlay(view: View, onBack: () -> Unit) {
        closeOverlay(restoreFocus = false)
        pointer.stop()
        focusBeforeOverlay = currentFocus
        overlay = view
        overlayBack = onBack
        root.addView(view, match())
    }

    private fun closeOverlay(restoreFocus: Boolean = true) {
        val v = overlay ?: return
        (v as? AddressScreen)?.hideKeyboard()
        (v as? EditSheet)?.hideKeyboard()
        root.removeView(v)
        overlay = null
        overlayBack = null
        if (restoreFocus) focusBeforeOverlay?.takeIf { it.isAttachedToWindow }?.requestFocus()
            ?: if (!onPage) start.restoreFocus() else web?.requestFocus()
        focusBeforeOverlay = null
    }

    private fun openAddress(initial: String) {
        val screen = AddressScreen(this, bookmarks, history, onGo = ::openPage)
        showOverlay(screen) { closeOverlay() }
        screen.open(initial)
    }

    private fun openCrossMenu() {
        val w = web ?: return
        val url = pageUrl ?: w.url ?: return
        val bookmarked = bookmarks.all().any { it.url.trimEnd('/') == url.trimEnd('/') }
        val host = UrlInput.displayHost(url)
        val adOn = adBlocker.isEnabledFor(host)
        val arms = mapOf(
            CrossMenu.Dir.LEFT to listOf(
                CrossMenu.Action("Back", CrossMenu.ICON_BACK) { if (w.canGoBack()) w.goBack() else showStart() },
                CrossMenu.Action("Forward", CrossMenu.ICON_FORWARD, enabled = w.canGoForward()) { w.goForward() },
            ),
            CrossMenu.Dir.RIGHT to listOf(
                CrossMenu.Action("Reload", CrossMenu.ICON_RELOAD) { w.reload() },
                CrossMenu.Action(if (bookmarked) "Bookmarked" else "Bookmark", CrossMenu.ICON_BOOKMARK, enabled = !bookmarked) {
                    val title = w.title?.takeIf { it.isNotBlank() && !it.startsWith("http") }
                        ?: UrlInput.displayHost(url)
                    bookmarks.add(Bookmark(title.take(40), url))
                    start.render()
                    Toast.makeText(this, "Bookmarked", Toast.LENGTH_SHORT).show()
                },
            ),
            CrossMenu.Dir.UP to listOf(
                CrossMenu.Action("Address", CrossMenu.ICON_ADDRESS) { openAddress(url) },
                CrossMenu.Action("Start screen", CrossMenu.ICON_HOME) { showStart() },
            ),
            // Inner slot becomes "Switch to Links" in step 5.
            CrossMenu.Dir.DOWN to listOf(
                CrossMenu.Action(if (adOn) "Ad-block: On" else "Ad-block: Off", CrossMenu.ICON_SHIELD) {
                    adBlocker.setEnabledFor(host, !adOn)
                    w.refreshAdBoxHiding()
                    w.reload()
                    Toast.makeText(this, if (adOn) "Ad-block off for $host" else "Ad-block on for $host", Toast.LENGTH_SHORT).show()
                },
            ),
        )
        val sub = when {
            !adOn -> "Ad-block off"
            w.blockedCount == 1 -> "1 blocked"
            else -> "${w.blockedCount} blocked"
        }
        val menu = CrossMenu(this, UrlInput.displayHost(url), sub, arms, onClose = { closeOverlay() })
        showOverlay(menu) { closeOverlay() }
    }

    private fun openBookmarkActions(index: Int) {
        val b = bookmarks.all().getOrNull(index) ?: return
        val sheet = BookmarkActions(
            this, b,
            onOpen = { openPage(b.url) },
            onRename = { openRename(index) },
            onMove = {
                closeOverlay(restoreFocus = false)
                start.startMove(index)
            },
            onRemove = {
                bookmarks.remove(index)
                closeOverlay(restoreFocus = false)
                start.render(index)
            },
        )
        showOverlay(sheet) { closeOverlay() }
        sheet.focusFirst()
    }

    private fun openRename(index: Int) {
        val b = bookmarks.all().getOrNull(index) ?: return
        val sheet = EditSheet(this, "Rename bookmark", listOf(EditSheet.Field("Name", b.name))) { values ->
            val name = values[0].ifBlank { b.name }
            bookmarks.update(index, b.copy(name = name))
            closeOverlay(restoreFocus = false)
            start.render(index)
        }
        showOverlay(sheet) { closeOverlay(restoreFocus = false); start.focusTile(index) }
        sheet.focusFirst()
    }

    private fun openAddBookmark() {
        val sheet = EditSheet(
            this, "Add bookmark",
            listOf(EditSheet.Field("Address", "", isUrl = true), EditSheet.Field("Name (optional)", "")),
        ) { values ->
            val url = UrlInput.toUrl(values[0])
            if (url.isNotBlank()) {
                val name = values[1].ifBlank {
                    UrlInput.displayHost(url).substringBefore('.').replaceFirstChar { it.uppercase() }
                }
                bookmarks.add(Bookmark(name, url))
            }
            closeOverlay(restoreFocus = false)
            start.render(bookmarks.all().size - 1)
        }
        showOverlay(sheet) { closeOverlay() }
        sheet.focusFirst()
    }

    // ---- keys & lifecycle -------------------------------------------------------------------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val menu = overlay as? CrossMenu
        if (menu != null && menu.handleKey(event)) return true
        if (overlay == null && inFullscreen && videoKeys.handleKey(event)) return true
        if (onPage && overlay == null && web != null) {
            if (event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) web?.videoCommand("toggle")
                return true
            }
            if (pointer.handleKey(event)) return true
        }
        return super.dispatchKeyEvent(event)
    }

    @Deprecated("Back is handled here for API 29-35 without predictive back")
    override fun onBackPressed() {
        when {
            overlay != null -> overlayBack?.invoke()
            inFullscreen -> exitFullscreen()
            onPage -> {
                val w = web
                if (w != null && w.canGoBack()) w.goBack() else showStart()
            }
            else -> finish()
        }
    }

    override fun onResume() {
        super.onResume()
        web?.onResume()
    }

    override fun onPause() {
        web?.onPause()
        super.onPause()
    }

    override fun onStart() {
        super.onStart()
        stopped = false
        web?.resumeTimers()
        if (reloadOnStart) {
            reloadOnStart = false
            pageUrl?.let { openPage(it) }
        }
    }

    override fun onStop() {
        stopped = true
        web?.pauseTimers()
        super.onStop()
    }

    @Deprecated("Levels still delivered on API 29-35")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // In the background and the system is short on memory: free the page, reload it on return.
        if (stopped && level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND && web != null) {
            freeWebView()
            reloadOnStart = onPage && pageUrl != null
        }
    }

    override fun onDestroy() {
        freeWebView()
        super.onDestroy()
    }

    private fun match() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
}
