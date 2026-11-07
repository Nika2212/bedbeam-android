package com.bedbeam.browser

import android.app.Activity
import android.content.ComponentCallbacks2
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.bedbeam.browser.data.Bookmark
import com.bedbeam.browser.data.Bookmarks
import com.bedbeam.browser.data.History
import com.bedbeam.browser.data.UrlInput
import com.bedbeam.browser.ui.AddressScreen
import com.bedbeam.browser.ui.BookmarkActions
import com.bedbeam.browser.ui.EditSheet
import com.bedbeam.browser.ui.Palette
import com.bedbeam.browser.ui.StartScreen
import com.bedbeam.browser.web.BrowserWebView

/**
 * One activity, one tab. Layers, bottom to top: the page (WebView), the start screen, one overlay
 * (address screen, bookmark actions or edit sheet).
 */
class MainActivity : Activity(), BrowserWebView.Listener {

    private lateinit var bookmarks: Bookmarks
    private lateinit var history: History
    private lateinit var root: FrameLayout
    private lateinit var pageHost: FrameLayout
    private lateinit var start: StartScreen

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

        root = FrameLayout(this).apply { setBackgroundColor(Palette.BG) }
        pageHost = FrameLayout(this)
        root.addView(pageHost, match())
        start = StartScreen(
            this, bookmarks,
            onOpenUrl = ::openPage,
            onOpenAddress = { openAddress("") },
            onBookmarkActions = ::openBookmarkActions,
            onAddBookmark = ::openAddBookmark,
        )
        root.addView(start, match())
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
    }

    private fun newWebView(): BrowserWebView =
        BrowserWebView(this, this).also {
            web = it
            pageHost.addView(it, match())
        }

    private fun freeWebView() {
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

    @Deprecated("Back is handled here for API 29-35 without predictive back")
    override fun onBackPressed() {
        when {
            overlay != null -> overlayBack?.invoke()
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
