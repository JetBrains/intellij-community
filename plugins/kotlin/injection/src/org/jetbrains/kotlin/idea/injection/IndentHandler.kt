// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.injection

import com.intellij.codeInsight.intention.impl.QuickEditHandler
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.util.SmartList
import com.intellij.util.text.splitToTextRanges
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

internal interface IndentHandler {
    fun getUntrimmedRanges(literal: KtStringTemplateExpression, givenRange: TextRange): List<TextRange>
}

internal object NoIndentHandler: IndentHandler {
    override fun getUntrimmedRanges(literal: KtStringTemplateExpression, givenRange: TextRange): List<TextRange> = listOf(givenRange)
}

internal class TrimIndentHandler(private val marginChar: String? = null) : IndentHandler {
    override fun getUntrimmedRanges(literal: KtStringTemplateExpression, givenRange: TextRange): List<TextRange> {
        val text = literal.text

        val valueTextRange = ElementManipulators.getValueTextRange(literal)

        val ranges = SmartList<TextRange>()
        var minLength: Int? = null

        val linesRanges = splitToTextRanges(text, "\n").toList()
        for ((i, lineRange0) in linesRanges.withIndex()) {
            val lineRange = valueTextRange.intersection(lineRange0) ?: continue
            val lineText = lineRange.subSequence(text)
            val blank = lineText.isBlank()
            if (!blank || i != linesRanges.lastIndex && i != 0) {
                val range = TextRange.create(
                    lineRange.startOffset.coerceAtLeast(valueTextRange.startOffset),
                    (lineRange.endOffset + 1).coerceAtMost(valueTextRange.endOffset)
                )
                ranges.add(range)
            }

            if (!blank) {
                val whitespaces = lineText.indexOfFirst { !it.isWhitespace() }
                val indent = if (marginChar != null) {
                    if (lineText.substring(whitespaces, whitespaces + marginChar.length) == marginChar) whitespaces + marginChar.length else continue
                } else
                    whitespaces
                minLength = minLength?.coerceAtMost(indent) ?: indent
            }
        }

        if (ranges.isEmpty()) return listOf(givenRange)
        
        // Dont change the indent if Fragment Editor is open
        minLength = com.intellij.codeInsight.intention.impl.reuseFragmentEditorIndent(literal) { minLength }

        val indentText = minLength?.let { " ".repeat(it) }?.let { spaces ->
            if (marginChar == null) return@let spaces
            spaces.dropLast(marginChar.length) + marginChar
        }
        literal.trimIndent = indentText

        if (text[ranges[ranges.lastIndex].endOffset - 1] == '\n')
            ranges[ranges.lastIndex] = ranges[ranges.lastIndex].run { TextRange.create(startOffset, endOffset - 1) }

        val rangesNormalized = if (minLength != null && !indentText.isNullOrEmpty())
            ranges.map { lineRange ->
                if (lineRange.length >= minLength) {
                    if (indentText.contentEquals(text.subSequence(lineRange.startOffset, lineRange.startOffset + minLength)))
                        TextRange.create(lineRange.startOffset + minLength, lineRange.endOffset)
                    else
                        lineRange
                } else
                    lineRange
            }
        else ranges

        return rangesNormalized.mapNotNull { it.intersection(givenRange) }
    }
}