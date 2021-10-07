package com.jetbrains.packagesearch.intellij.plugin.ui.components

import com.intellij.ui.HyperlinkLabel
import org.jetbrains.annotations.Nls

internal class BrowsableLinkLabel : HyperlinkLabel() {

    var urlClickedListener: (() -> Unit)? = null

    init {
        addHyperlinkListener { urlClickedListener?.invoke() }
    }

    var url: String? = null
        set(value) {
            if (value.isBrowsableUrl) {
                isVisible = true
                setHyperlinkTarget(value)
                setIcon(null) // We need to reset it every time — calling setHyperlinkTarget() sets it, because reasons
            } else {
                isVisible = false
            }
            field = value
        }

    private val String?.isBrowsableUrl: Boolean
        get() {
            if (isNullOrBlank()) return false
            val normalizedUrl = trim()
            return normalizedUrl.startsWith("http://") || normalizedUrl.startsWith("https://")
        }

    fun setDisplayText(@Nls text: String?) {
        if (text != null) {
            setHyperlinkText(text)
        } else {
            super.setText(null)
        }
    }
}
