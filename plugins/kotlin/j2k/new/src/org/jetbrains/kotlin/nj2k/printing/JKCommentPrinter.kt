// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.nj2k.printing

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.tree.JKComment
import org.jetbrains.kotlin.nj2k.tree.JKDeclaration
import org.jetbrains.kotlin.nj2k.tree.JKFormattingOwner

internal class JKCommentPrinter(private val printer: JKPrinter) {
    private val printedTokens = mutableSetOf<JKComment>()

    //TODO move to ast transformation phase
    private fun JKComment.shouldBeDropped(): Boolean =
        text.startsWith("//noinspection")

    private fun JKComment.createText() =
        if (this !in printedTokens) {
            printedTokens += this

            // hack till #KT-16845, #KT-23333 are fixed
            if (!isSingleLine && text.lastIndexOf("/*") != text.indexOf("/*")) {
                text.replace("/*", "/ *")
                    .replaceFirst("/ *", "/*")
            } else text
        } else null


    private fun List<JKComment>.createText(parent: JKFormattingOwner? = null): String = buildString {
        var needNewLine = false

        for (comment in this@createText) {
            if (parent?.commentsAfter?.contains(comment) == true) {
                // A comment may be contained in several JK elements (this is a side effect of comments collection in AST building phase).
                // We already account for this fact and don't print duplicate comments (see `printedTokens` property).
                // However, if we print a particular comment for a child element instead of the parent,
                // sometimes we incorrectly add a redundant line break.
                //
                // To work around this, if the comment is contained both in the parent and child elements, we print it only for the parent.
                continue
            }

            if (comment.shouldBeDropped()) continue
            val text = comment.createText() ?: continue
            if (needNewLine && comment.indent?.let { StringUtil.containsLineBreak(it) } != true) appendLine()
            append(comment.indent ?: ' ')
            append(text)
            needNewLine = text.startsWith("//") || '\n' in text
        }
    }

    private fun String.hasNoLineBreakAfterSingleLineComment() = lastIndexOf('\n') < lastIndexOf("//")

    fun printCommentsAfter(element: JKFormattingOwner) {
        val parent = (element as? JKTreeElement)?.parent as? JKFormattingOwner
        val text = element.commentsAfter.createText(parent)
        printer.print(text)

        if (element.hasLineBreakAfter) {
            printer.println(element.lineBreaksAfter)
        } else if (text.hasNoLineBreakAfterSingleLineComment()) {
            printer.println()
        }
    }

    fun printCommentsBefore(element: JKFormattingOwner) {
        val text = element.commentsBefore.createText()
        printer.print(text)

        val shouldAddLineBreakAfterComment = (element is JKDeclaration && element.commentsBefore.isNotEmpty()) ||
                text.hasNoLineBreakAfterSingleLineComment()

        if (element.hasLineBreakBefore) {
            printer.println(element.lineBreaksBefore)
        } else if (shouldAddLineBreakAfterComment) {
            printer.println()
        }
    }
}
