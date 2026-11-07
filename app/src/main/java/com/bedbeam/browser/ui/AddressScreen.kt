package com.bedbeam.browser.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bedbeam.browser.R
import com.bedbeam.browser.data.Bookmarks
import com.bedbeam.browser.data.History
import com.bedbeam.browser.data.UrlInput

/** Address entry with suggestions from bookmarks, history and search (design: "Typing an address"). */
@SuppressLint("ViewConstructor")
class AddressScreen(
    context: Context,
    private val bookmarks: Bookmarks,
    private val history: History,
    private val onGo: (String) -> Unit,
) : LinearLayout(context) {

    private enum class Kind(val icon: Int, val tag: String) {
        GO(R.drawable.ic_arrow_right, "Open"),
        BOOKMARK(R.drawable.ic_star, "Bookmark"),
        HISTORY(R.drawable.ic_history, "History"),
        SEARCH(R.drawable.ic_search, "Search"),
    }

    private data class Suggestion(val kind: Kind, val title: String, val sub: String, val url: String)

    private val d = Dims(context)
    private val input = EditText(context)
    private val list = LinearLayout(context)

    init {
        orientation = VERTICAL
        setBackgroundColor(Palette.BG)
        setPadding(d.px(72), d.px(40), d.px(72), 0)
        clipChildren = false
        clipToPadding = false
        isClickable = true // swallow touches meant for screens below

        val field = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(d.px(28), 0, d.px(28), 0)
            background = rounded(Palette.TEXT, d.f(34), d.px(4), Palette.ACCENT)
        }
        field.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_search)
            imageTintList = ColorStateList.valueOf(Palette.ON_LIGHT_MUTED)
        }, LayoutParams(d.px(24), d.px(24)))
        input.apply {
            background = null
            setPadding(d.px(16), 0, d.px(16), 0)
            textStyle(d, 22, Palette.BG, 500)
            setHintTextColor(Palette.ON_LIGHT_MUTED)
            hint = "Search or type a web address"
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
            setSelectAllOnFocus(true)
            setOnEditorActionListener { _, id, ev ->
                val enter = ev != null && ev.keyCode == KeyEvent.KEYCODE_ENTER && ev.action == KeyEvent.ACTION_DOWN
                if (id == EditorInfo.IME_ACTION_GO || id == EditorInfo.IME_ACTION_DONE || enter) {
                    go(UrlInput.toUrl(text.toString()))
                    true
                } else false
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) = showSuggestions(s?.toString().orEmpty())
            })
        }
        field.addView(input, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        field.addView(TextView(context).apply {
            text = "Enter = Go"
            textStyle(d, 16, Palette.ON_LIGHT_MUTED)
        })
        addView(field, LayoutParams(LayoutParams.MATCH_PARENT, d.px(68)))

        list.orientation = VERTICAL
        addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = d.px(20) })
    }

    /** Show the screen with [initial] text selected and the keyboard up. */
    fun open(initial: String) {
        input.setText(initial)
        input.requestFocus()
        input.selectAll()
        showSuggestions(initial)
        input.postDelayed({
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }, 150)
    }

    fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(input.windowToken, 0)
    }

    private fun go(url: String) {
        if (url.isBlank()) return
        hideKeyboard()
        onGo(url)
    }

    private fun showSuggestions(query: String) {
        list.removeAllViews()
        suggestions(query).forEach { list.addView(row(it), LayoutParams(LayoutParams.MATCH_PARENT, d.px(60)).apply { topMargin = d.px(6) }) }
    }

    private fun suggestions(query: String): List<Suggestion> {
        val q = query.trim()
        val out = mutableListOf<Suggestion>()
        val seen = HashSet<String>()
        fun add(s: Suggestion) {
            if (seen.add(s.url)) out += s
        }
        if (q.isEmpty()) {
            history.all().take(5).forEach { add(Suggestion(Kind.HISTORY, it.title, UrlInput.displayHost(it.url), it.url)) }
            return out
        }
        if (UrlInput.looksLikeUrl(q)) {
            val url = UrlInput.toUrl(q)
            add(Suggestion(Kind.GO, UrlInput.displayHost(url), url, url))
        }
        bookmarks.all().filter { it.name.contains(q, true) || it.url.contains(q, true) }.take(2)
            .forEach { add(Suggestion(Kind.BOOKMARK, it.name, UrlInput.displayHost(it.url), it.url)) }
        history.all().filter { it.title.contains(q, true) || it.url.contains(q, true) }.take(3)
            .forEach { add(Suggestion(Kind.HISTORY, it.title, it.url.removePrefix("https://").removePrefix("http://"), it.url)) }
        add(Suggestion(Kind.SEARCH, "Search for “$q”", UrlInput.engine.name, UrlInput.searchUrl(q)))
        return out.take(5)
    }

    private fun row(s: Suggestion) = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = true
        isClickable = true
        setPadding(d.px(22), 0, d.px(22), 0)
        val icon = ImageView(context).apply { setImageResource(s.kind.icon) }
        addView(icon, LayoutParams(d.px(22), d.px(22)))
        addView(TextView(context).apply {
            text = s.title
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            textStyle(d, 20, Palette.TEXT, 500)
        }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = d.px(18) })
        addView(TextView(context).apply {
            text = s.sub
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            textStyle(d, 18, Palette.MUTED)
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = d.px(18) })
        addView(TextView(context).apply {
            text = s.kind.tag
            textStyle(d, 15, Palette.MUTED)
        }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = d.px(18) })
        fun style(has: Boolean) {
            background = if (has) rounded(Palette.SURFACE2, d.f(14), d.px(2), Palette.ACCENT) else null
            icon.imageTintList = ColorStateList.valueOf(if (has) Palette.ACCENT else Palette.MUTED)
        }
        style(false)
        onFocus { style(it) }
        setOnClickListener { go(s.url) }
    }
}
