package com.bedbeam.browser.ui

import android.annotation.SuppressLint
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bedbeam.browser.R
import com.bedbeam.browser.data.Bookmarks
import com.bedbeam.browser.data.History
import com.bedbeam.browser.data.UrlInput

/** Address entry with suggestions (layout: screen_address, item_suggestion). */
@SuppressLint("ViewConstructor")
class AddressScreen(
    context: Context,
    private val bookmarks: Bookmarks,
    private val history: History,
    private val onGo: (String) -> Unit,
) : FrameLayout(context) {

    private enum class Kind(val icon: Int, val tag: Int) {
        GO(R.drawable.ic_arrow_right, R.string.sug_open),
        BOOKMARK(R.drawable.ic_star, R.string.sug_bookmark),
        HISTORY(R.drawable.ic_history, R.string.sug_history),
        SEARCH(R.drawable.ic_search, R.string.sug_search),
    }

    private data class Suggestion(val kind: Kind, val title: String, val sub: String, val url: String)

    private val inflater = LayoutInflater.from(context)
    private val input: EditText
    private val list: LinearLayout

    init {
        inflater.inflate(R.layout.screen_address, this, true)
        input = findViewById(R.id.address_input)
        list = findViewById(R.id.suggestions)
        input.setOnEditorActionListener { _, id, ev ->
            val enter = ev != null && ev.keyCode == KeyEvent.KEYCODE_ENTER && ev.action == KeyEvent.ACTION_DOWN
            if (id == EditorInfo.IME_ACTION_GO || id == EditorInfo.IME_ACTION_DONE || enter) {
                go(UrlInput.toUrl(input.text.toString()))
                true
            } else false
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = showSuggestions(s?.toString().orEmpty())
        })
    }

    /** Show the screen with [initial] text selected and the keyboard up. */
    fun open(initial: String) {
        input.setText(initial)
        input.requestFocus()
        input.selectAll()
        showSuggestions(initial)
        input.postDelayed({ imm().showSoftInput(input, InputMethodManager.SHOW_IMPLICIT) }, 150)
    }

    fun hideKeyboard() {
        imm().hideSoftInputFromWindow(input.windowToken, 0)
    }

    private fun imm() = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    private fun go(url: String) {
        if (url.isBlank()) return
        hideKeyboard()
        onGo(url)
    }

    private fun showSuggestions(query: String) {
        list.removeAllViews()
        suggestions(query).forEach { s ->
            val row = inflater.inflate(R.layout.item_suggestion, list, false)
            row.findViewById<ImageView>(R.id.icon).setImageResource(s.kind.icon)
            row.findViewById<TextView>(R.id.title).text = s.title
            row.findViewById<TextView>(R.id.sub).text = s.sub
            row.findViewById<TextView>(R.id.tag).setText(s.kind.tag)
            row.setOnClickListener { go(s.url) }
            list.addView(row)
        }
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
        add(Suggestion(Kind.SEARCH, context.getString(R.string.search_for, q), UrlInput.engine.name, UrlInput.searchUrl(q)))
        return out.take(5)
    }
}
