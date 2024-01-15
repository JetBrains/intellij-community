// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.nj2k.printing

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlin.nj2k.tree.*

internal class JKCommentPrinter(private val printer: JKPrinter) {
    // some comments may appear in the AST multiple times, so we keep track
    // of the already printed comments to avoid printing the same comment twice
    private val printedTokens = mutableSetOf<JKComment>()

    fun printCommentsAndLineBreaksBefore(element: JKFormattingOwner) {
        val text = element.commentsBefore.createText()
        printer.print(text)

        if (printer.lastSymbolIsLineBreak) return

        val shouldAddLineBreakAfterComment = (element is JKDeclaration && element.commentsBefore.isNotEmpty()) ||
                text.hasNoLineBreakAfterSingleLineComment()

        if (element.hasLineBreakBefore || shouldAddLineBreakAfterComment) {
            printer.println()
        }
    }

    fun printCommentsAndLineBreaksAfter(element: JKFormattingOwner) {
        val parent = (element as? JKTreeElement)?.parent as? JKFormattingOwner
        val text = element.commentsAfter.createText(parent)
        printer.print(text)

        if (element.hasLineBreakAfter) {
            printer.println(element.lineBreaksAfter)
        } else if (text.hasNoLineBreakAfterSingleLineComment()) {
            printer.println()
        }
    }

    private fun List<JKComment>.createText(parent: JKFormattingOwner? = null): String = buildString {
        var needNewLine = false

        for (comment in this@createText) {
            if (comment.shouldBeDropped(parent)) continue

            printedTokens += comment
            val text = comment.escapedText()
            if (needNewLine && comment.indent?.let { StringUtil.containsLineBreak(it) } != true) appendLine()
            append(comment.indent ?: ' ')
            append(text)
            needNewLine = text.startsWith("//") || '\n' in text
        }
    }

    /**
     * @param parent - the parent element of the comment owner
     */
    private fun JKComment.shouldBeDropped(parent: JKFormattingOwner?): Boolean {
        if (this in printedTokens) return true
        if (text.startsWith("//noinspection")) return true
        if (parent?.commentsAfter?.contains(this) == true) {
            // A comment may be contained in several JK elements (this is a side effect of comments collection in AST building phase).
            // We already account for this fact and don't print duplicate comments (see `printedTokens` property).
            // However, if we print a particular comment for a child element instead of the parent,
            // sometimes we incorrectly add a redundant line break.
            //
            // To work around this, if the comment is contained both in the parent and child elements, we print it only for the parent.
            return true
        }

        return false
    }

    private fun JKComment.escapedText(): String = when {
        isSingleLine -> text
        text.indexOf("/*") == text.lastIndexOf("/*") -> text
        else -> {
            // hack till #KT-16845, #KT-23333 are fixed
            text.replace("/*", "/ *").replaceFirst("/ *", "/*")
        }
    }

    private fun String.hasNoLineBreakAfterSingleLineComment(): Boolean =
        lastIndexOf('\n') < lastIndexOf("//")
}
