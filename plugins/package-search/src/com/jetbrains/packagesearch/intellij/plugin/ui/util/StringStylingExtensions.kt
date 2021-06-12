package com.jetbrains.packagesearch.intellij.plugin.ui.util

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk

@NlsSafe
internal fun @receiver:NlsSafe String.withHtmlStyling(
    wordWrap: Boolean = false,
    lineHeight: String? = null
): String {
    val styleString = buildString {
        if (wordWrap) append("word-wrap:normal; width: 100%;")
        if (lineHeight != null) {
            append(" line-height: ")
            append(lineHeight)
            append(";")
        }
    }

    return HtmlChunk.html()
        .child(
            HtmlChunk.body().style(styleString)
                .addText(this)
        ).toString()
}
