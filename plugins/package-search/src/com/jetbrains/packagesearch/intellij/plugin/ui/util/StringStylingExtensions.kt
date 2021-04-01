package com.jetbrains.packagesearch.intellij.plugin.ui.util

import com.intellij.openapi.util.NlsSafe
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle

@NlsSafe
internal fun @receiver:NlsSafe String.withHtmlStyling(
    wordWrap: Boolean = false,
    lineHeight: String? = null
): String {
    // TODO use HtmlChunk instead
    val styleString = buildString {
        if (wordWrap) append("word-wrap:normal; width: 100%;")
        if (lineHeight != null) {
            append(" line-height: ")
            append(lineHeight)
            append(";")
        }
    }

    return PackageSearchBundle.message("packagesearch.ui.util.htmlStylingWrapper", styleString, this)
}
