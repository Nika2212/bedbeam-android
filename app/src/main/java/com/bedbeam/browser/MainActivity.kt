package com.bedbeam.browser

import android.app.Activity
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.LocaleList
import java.util.Locale
import android.os.Bundle
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import com.bedbeam.browser.data.Settings
import com.bedbeam.browser.ui.SettingsScreen
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
import com.bedbeam.browser.web.LinkKeys
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
    private lateinit var settings: Settings
    private lateinit var root: FrameLayout
    private lateinit var pageHost: FrameLayout
    private lateinit var start: StartScreen
    private lateinit var pointerView: PointerView
    private lateinit var pointer: PointerController
    private lateinit var fullscreenHost: FrameLayout
    private lateinit var osd: VideoOsd
    private lateinit var videoKeys: VideoKeys
    private lateinit var linkKeys: LinkKeys
    /** Link mode (arrows jump between links) instead of pointer mode; kept across pages. */
    private var linkMode = false

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
        Palette.load(this) // colours from res/values/colors.xml, before any screen is built
        bookmarks = Bookmarks(this)
        history = History(this)
        adBlocker = AdBlocker(this).also { it.start() }
        settings = Settings(this)
        UrlInput.engine = settings.engine

        root = FrameLayout(this).apply { setBackgroundColor(Palette.BG) }
        pageHost = FrameLayout(this)
        root.addView(pageHost, match())
        pointerView = PointerView(this)
        pageHost.addView(pointerView, match()) // stays above the WebView, which is inserted at index 0
        pointer = PointerController(pointerView, { web }, onHoldOk = ::openCrossMenu)
        pointerView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> pointer.ensurePlaced() }
        linkKeys = LinkKeys(send = { web?.pageCommand(it) ?: false }, onHoldOk = ::openCrossMenu)
        start = StartScreen(
            this, bookmarks,
            onOpenUrl = ::openPage,
            onOpenAddress = { openAddress("") },
            onBookmarkActions = ::openBookmarkActions,
            onAddBookmark = ::openAddBookmark,
            onOpenSettings = ::openSettings,
        )
        pointer.speedFactor = settings.pointerSpeedFactor
        root.addView(start, match())
        fullscreenHost = FrameLayout(this).apply {
            setBackgroundColor(Palette.VIDEO_BG)
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
        BrowserWebView(this, adBlocker, this, settings.desktopMode).also {
            web = it
            pageHost.addView(it, 0, match())
            reapplyLanguage()
        }

    private fun freeWebView() {
        if (inFullscreen) exitFullscreen()
        videoPlaying = false
        updateKeepScreenOn()
        web?.let {
            pageHost.removeView(it)
            it.release() // also ends the page's renderer process (~150 MB on a video site)
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
        pointerView.visibility = if (linkMode) View.GONE else View.VISIBLE
        updateKeepScreenOn()
        web?.requestFocus()
    }

    override fun onVideoPlaying(playing: Boolean) {
        videoPlaying = playing
        updateKeepScreenOn()
        if (playing && !inFullscreen && settings.autoFullscreen) web?.videoCommand("fs")
    }

    // ---- downloads & file upload ------------------------------------------------------------

    override fun onDownload(url: String, userAgent: String, contentDisposition: String?, mimeType: String?) {
        val uri = Uri.parse(url)
        if (uri.scheme != "http" && uri.scheme != "https") {
            Toast.makeText(this, R.string.dl_cant_save, Toast.LENGTH_SHORT).show()
            return
        }
        val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
        try {
            val req = DownloadManager.Request(uri)
                .setMimeType(mimeType)
                .addRequestHeader("User-Agent", userAgent)
                .setTitle(name)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            CookieManager.getInstance().getCookie(url)?.let { req.addRequestHeader("Cookie", it) }
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
            Toast.makeText(this, getString(R.string.dl_started, name), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.dl_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
        }
    }

    private var fileCallback: ValueCallback<Array<Uri>>? = null

    override fun onFileChooser(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean {
        fileCallback?.onReceiveValue(null)
        fileCallback = callback
        return try {
            @Suppress("DEPRECATION")
            startActivityForResult(params.createIntent(), REQ_FILE)
            true
        } catch (e: ActivityNotFoundException) {
            fileCallback = null
            Toast.makeText(this, R.string.no_file_picker, Toast.LENGTH_SHORT).show()
            false
        }
    }

    @Deprecated("Activity result API needs AndroidX; this app stays on the platform API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_FILE) {
            fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            fileCallback = null
        }
    }

    // ---- settings ---------------------------------------------------------------------------

    // ---- language -------------------------------------------------------------------------------

    /** Apply the language chosen in settings (or keep the system's) before any screen is built. */
    override fun attachBaseContext(base: Context) = super.attachBaseContext(localized(base))

    private fun localized(base: Context): Context {
        val code = Settings.languageCode(base) ?: return base
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocales(LocaleList(Locale.forLanguageTag(code)))
        return base.createConfigurationContext(cfg)
    }

    /** Creating a WebView can reset the app's resources to the system language: put ours back. */
    private fun reapplyLanguage() {
        val code = Settings.languageCode(this) ?: return
        val cfg = Configuration(resources.configuration)
        cfg.setLocales(LocaleList(Locale.forLanguageTag(code)))
        @Suppress("DEPRECATION")
        resources.updateConfiguration(cfg, resources.displayMetrics)
    }

    private fun uiLocale(): Locale = resources.configuration.locales[0]

    private fun onOff(on: Boolean) = getString(if (on) R.string.on else R.string.off)

    // ---- settings screen ---------------------------------------------------------------------------

    private fun openSettings() {
        var confirmClear = false
        lateinit var screen: SettingsScreen
        val rows = listOf(
            SettingsScreen.Row(getString(R.string.s_language), getString(R.string.s_language_hint),
                { getString(Settings.LANGUAGES[settings.language].second) }) {
                settings.language = (settings.language + 1) % Settings.LANGUAGES.size
                recreate() // rebuild every screen in the new language
            },
            SettingsScreen.Row(getString(R.string.s_search), getString(R.string.s_search_hint), { settings.engine.name }) {
                settings.searchEngine = (settings.searchEngine + 1) % Settings.ENGINES.size
                UrlInput.engine = settings.engine
            },
            SettingsScreen.Row(getString(R.string.s_pointer), getString(R.string.s_pointer_hint),
                { getString(Settings.POINTER_SPEEDS[settings.pointerSpeed].first) }) {
                settings.pointerSpeed = (settings.pointerSpeed + 1) % Settings.POINTER_SPEEDS.size
                pointer.speedFactor = settings.pointerSpeedFactor
            },
            SettingsScreen.Row(getString(R.string.s_autofs), getString(R.string.s_autofs_hint),
                { onOff(settings.autoFullscreen) }) {
                settings.autoFullscreen = !settings.autoFullscreen
            },
            SettingsScreen.Row(getString(R.string.s_desktop), getString(R.string.s_desktop_hint),
                { onOff(settings.desktopMode) }) {
                settings.desktopMode = !settings.desktopMode
                web?.setDesktopMode(settings.desktopMode)
            },
            SettingsScreen.Row(getString(R.string.s_adlists), getString(R.string.s_adlists_hint),
                {
                    val n = getString(R.string.ad_domains, String.format(uiLocale(), "%,d", adBlocker.domainCount))
                    val at = adBlocker.updatedAt
                    if (at == 0L) n else "$n · " + java.text.SimpleDateFormat("d MMM", uiLocale()).format(java.util.Date(at))
                }) {
                Toast.makeText(this, R.string.updating_lists, Toast.LENGTH_SHORT).show()
                adBlocker.updateNow { ok ->
                    Toast.makeText(this, if (ok) R.string.lists_updated else R.string.lists_update_failed, Toast.LENGTH_SHORT).show()
                    screen.refresh()
                }
            },
            SettingsScreen.Row(getString(R.string.s_clear), getString(R.string.s_clear_hint),
                { if (confirmClear) getString(R.string.s_press_again) else "" }) {
                if (!confirmClear) {
                    confirmClear = true
                } else {
                    confirmClear = false
                    clearBrowsingData()
                    Toast.makeText(this, R.string.data_cleared, Toast.LENGTH_SHORT).show()
                }
            },
            SettingsScreen.Row(getString(R.string.s_about), getString(R.string.s_about_hint),
                { "BedBeam ${BuildConfig.VERSION_NAME} · Chromium ${WebViewCompat.getCurrentWebViewPackage(this)?.versionName?.substringBefore('.') ?: "?"}" }) {},
        )
        screen = SettingsScreen(this, rows)
        showOverlay(screen) { closeOverlay() }
        screen.focusFirst()
    }

    private fun clearBrowsingData() {
        history.clear()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
        val w = web
        if (w != null) w.clearCache(true) else {
            // clearCache needs a WebView; a bare one starts no page process.
            WebView(this).apply { clearCache(true); destroy() }
        }
    }

    override fun onVideoState(t: Double, d: Double, paused: Boolean) {
        val afterToggle = videoKeys.consumeToggleFlag()
        if (inFullscreen) videoKeys.onState(t, d, paused, afterToggle)
    }

    // ---- link mode ------------------------------------------------------------------------------

    private fun setLinkMode(on: Boolean) {
        linkMode = on
        pointer.stop()
        linkKeys.stop()
        if (on) {
            pointerView.visibility = View.GONE
            val (fx, fy) = pointer.fraction()
            web?.pageCommand("link:start:$fx,$fy") // ring starts on the link nearest the pointer
        } else {
            web?.pageCommand("link:clear")
            pointerView.visibility = View.VISIBLE
            pointer.ensurePlaced()
        }
    }

    override fun onEditableFocused() {
        val w = web ?: return
        if (overlay != null) return
        // The field got focus, but the keyboard only opens if the WebView itself holds focus.
        if (!w.hasFocus()) w.requestFocus()
        w.postDelayed({
            (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .showSoftInput(w, 0)
        }, 120)
    }

    override fun onLinkTarget(x: Double, y: Double, viewportWidth: Double) {
        val w = web ?: return
        if (viewportWidth <= 0) return
        val scale = w.width / viewportWidth // CSS px → view px
        pointer.tapAt((x * scale).toFloat(), (y * scale).toFloat())
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
        // Screens under the overlay must not take focus: arrows would otherwise jump to
        // invisible buttons behind it (e.g. from one settings row to the start screen's search).
        start.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        pageHost.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        root.addView(view, match())
    }

    private fun closeOverlay(restoreFocus: Boolean = true) {
        val v = overlay ?: return
        (v as? AddressScreen)?.hideKeyboard()
        (v as? EditSheet)?.hideKeyboard()
        root.removeView(v)
        overlay = null
        overlayBack = null
        start.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        pageHost.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
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
                CrossMenu.Action(getString(R.string.menu_back), CrossMenu.ICON_BACK) { if (w.canGoBack()) w.goBack() else showStart() },
                CrossMenu.Action(getString(R.string.menu_forward), CrossMenu.ICON_FORWARD, enabled = w.canGoForward()) { w.goForward() },
            ),
            CrossMenu.Dir.RIGHT to listOf(
                CrossMenu.Action(getString(R.string.menu_reload), CrossMenu.ICON_RELOAD) { w.reload() },
                CrossMenu.Action(getString(if (bookmarked) R.string.menu_bookmarked else R.string.menu_bookmark), CrossMenu.ICON_BOOKMARK, enabled = !bookmarked) {
                    val title = w.title?.takeIf { it.isNotBlank() && !it.startsWith("http") }
                        ?: UrlInput.displayHost(url)
                    bookmarks.add(Bookmark(title.take(40), url))
                    start.render()
                    Toast.makeText(this, R.string.toast_bookmarked, Toast.LENGTH_SHORT).show()
                },
            ),
            CrossMenu.Dir.UP to listOf(
                CrossMenu.Action(getString(R.string.menu_address), CrossMenu.ICON_ADDRESS) { openAddress(url) },
                CrossMenu.Action(getString(R.string.menu_start), CrossMenu.ICON_HOME) { showStart() },
            ),
            CrossMenu.Dir.DOWN to listOf(
                if (linkMode) CrossMenu.Action(getString(R.string.menu_to_pointer), CrossMenu.ICON_POINTER) { setLinkMode(false) }
                else CrossMenu.Action(getString(R.string.menu_to_links), CrossMenu.ICON_LINK) { setLinkMode(true) },
                CrossMenu.Action(getString(if (adOn) R.string.menu_adblock_on else R.string.menu_adblock_off), CrossMenu.ICON_SHIELD) {
                    adBlocker.setEnabledFor(host, !adOn)
                    w.refreshAdBoxHiding()
                    w.reload()
                    Toast.makeText(this, getString(if (adOn) R.string.toast_adblock_off else R.string.toast_adblock_on, host), Toast.LENGTH_SHORT).show()
                },
            ),
        )
        val sub = when {
            !adOn -> getString(R.string.center_adblock_off)
            else -> getString(R.string.blocked_count, w.blockedCount)
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
        val sheet = EditSheet(this, getString(R.string.rename_bookmark), listOf(EditSheet.Field(getString(R.string.field_name), b.name))) { values ->
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
            this, getString(R.string.add_bookmark),
            listOf(EditSheet.Field(getString(R.string.field_address), "", isUrl = true), EditSheet.Field(getString(R.string.field_name_optional), "")),
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
            if (if (linkMode) linkKeys.handleKey(event) else pointer.handleKey(event)) return true
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

    companion object {
        private const val REQ_FILE = 1
    }

    private fun match() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
}
