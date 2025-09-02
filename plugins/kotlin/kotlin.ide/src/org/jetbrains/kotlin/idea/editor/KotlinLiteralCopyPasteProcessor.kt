// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.editor

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RawText
import com.intellij.openapi.editor.SelectionModel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.range
import org.jetbrains.kotlin.idea.codeinsights.impl.base.*
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.*

private val PsiElement.templateContentRange: TextRange?
    get() = this.getParentOfType<KtStringTemplateExpression>(false)?.let {
        it.textRange.cutOut(it.getContentRange())
    }


private fun PsiFile.getTemplateIfAtLiteral(offset: Int, at: PsiElement? = findElementAt(offset)): KtStringTemplateExpression? {
    if (at == null) return null
    return when (at.node?.elementType) {
        KtTokens.REGULAR_STRING_PART, KtTokens.ESCAPE_SEQUENCE, KtTokens.LONG_TEMPLATE_ENTRY_START, KtTokens.SHORT_TEMPLATE_ENTRY_START -> at.parent
            .parent as? KtStringTemplateExpression
        KtTokens.CLOSING_QUOTE -> if (offset == at.startOffset) at.parent as? KtStringTemplateExpression else null
        else -> null
    }
}


//Copied from StringLiteralCopyPasteProcessor to avoid erroneous inheritance
private fun deduceBlockSelectionWidth(startOffsets: IntArray, endOffsets: IntArray, text: String): Int {
    val fragmentCount = startOffsets.size
    assert(fragmentCount > 0)
    var totalLength = fragmentCount - 1 // number of line breaks inserted between fragments
    for (i in 0 until fragmentCount) {
        totalLength += endOffsets[i] - startOffsets[i]
    }
    return if (totalLength < text.length && (text.length + 1) % fragmentCount == 0) {
        (text.length + 1) / fragmentCount - 1
    } else {
        -1
    }
}

class KotlinLiteralCopyPasteProcessor : CopyPastePreProcessor {
    override fun preprocessOnCopy(file: PsiFile, startOffsets: IntArray, endOffsets: IntArray, text: String): String? {
        if (file !is KtFile) {
            return null
        }
        val buffer = StringBuilder()
        var changed = false
        val fileText = file.text
        val deducedBlockSelectionWidth = deduceBlockSelectionWidth(startOffsets, endOffsets, text)

        for (i in startOffsets.indices) {
            if (i > 0) {
                buffer.append('\n') // LF is added for block selection
            }
            val fileRange = TextRange(startOffsets[i], endOffsets[i])
            var givenTextOffset = fileRange.startOffset
            while (givenTextOffset < fileRange.endOffset) {
                val element: PsiElement? = file.findElementAt(givenTextOffset)
                if (element == null) {
                    buffer.append(fileText.substring(givenTextOffset, fileRange.endOffset - 1))
                    break
                }
                val elTp = element.node.elementType
                if (elTp == KtTokens.ESCAPE_SEQUENCE && fileRange.contains(element.range) &&
                    element.templateContentRange?.contains(fileRange) == true
                ) {
                    val tpEntry = element.parent as KtEscapeStringTemplateEntry
                    changed = true
                    buffer.append(tpEntry.unescapedValue)
                    givenTextOffset = element.endOffset
                } else if (elTp == KtTokens.SHORT_TEMPLATE_ENTRY_START || elTp == KtTokens.LONG_TEMPLATE_ENTRY_START) {
                    //Process inner templates without escaping
                    val tpEntry = element.parent
                    val inter = fileRange.intersection(tpEntry.range)!!
                    buffer.append(fileText.substring(inter.startOffset, inter.endOffset))
                    givenTextOffset = inter.endOffset
                } else {
                    val inter = fileRange.intersection(element.range)!!
                    buffer.append(fileText.substring(inter.startOffset, inter.endOffset))
                    givenTextOffset = inter.endOffset
                }
            }
            val blockSelectionPadding = deducedBlockSelectionWidth - fileRange.length
            for (j in 0 until blockSelectionPadding) {
                buffer.append(' ')
            }
        }

        return if (changed) buffer.toString() else null
    }

    /**
     * Paste processing for `$`-prefixed strings consists of two parts:
     * * Paste preprocessing with full escaping — files copied from the outside should still be reasonably handled.
     * * Paste postprocessing for handling Kotlin to Kotlin cases, where interpolation info can be transferred.
     */
    override fun preprocessOnPaste(project: Project, file: PsiFile, editor: Editor, text: String, rawText: RawText?): String {
        if (file !is KtFile) {
            return text
        }
        PsiDocumentManager.getInstance(project).commitDocument(editor.document)
        val selectionModel = editor.selectionModel
        val selectionStartElement = file.findElementAt(selectionModel.selectionStart) ?: return text
        val beginTp = file.getTemplateIfAtLiteral(selectionModel.selectionStart, selectionStartElement) ?: return text
        val endTp = file.getTemplateIfAtLiteral(selectionModel.selectionEnd) ?: return text
        if (beginTp.isSingleQuoted() != endTp.isSingleQuoted()) {
            return text
        }
        val interpolationPrefix = beginTp.interpolationPrefix
        if (interpolationPrefix != endTp.interpolationPrefix) return text
        val prefixLength = interpolationPrefix?.textLength ?: 0

        return if (beginTp.isSingleQuoted()) {
            singleQuotedPaste(text, prefixLength)
        } else {
            tripleQuotedPaste(text, prefixLength, selectionStartElement, beginTp, selectionModel, editor)
        }
    }

    private fun singleQuotedPaste(text: String, interpolationPrefixLength: Int): String {
        val res = StringBuilder()
        val interpolationPrefix = "$".repeat(interpolationPrefixLength)
        val lineBreak = "\\n\"+\n $interpolationPrefix\""
        val additionalEscapedChars = if (interpolationPrefixLength > 1) "\"" else "$\""
        var endsInLineBreak = false
        TemplateTokenSequence(text, interpolationPrefixLength).forEach {
            when (it) {
                is LiteralChunk -> StringUtil.escapeStringCharacters(it.text.length, it.text, additionalEscapedChars, res)
                is EntryChunk -> res.append(it.text)
                is NewLineChunk -> res.append(lineBreak)
            }
            endsInLineBreak = it is NewLineChunk
        }
        return if (endsInLineBreak) {
            res.removeSuffix(lineBreak).toString() + "\\n"
        } else {
            res.toString()
        }
    }

    /**
     * Escape unsafe triple quotes and dollar characters in the pasted text.
     *
     * [text] is parsed as if it was inside a string with the passed [interpolationPrefixLength]
     * The parser output is then sanitized:
     * — the last dollar in prefix is escaped in interpolation entries;
     * — dollar characters parsed as literal entries are only escaped in the beginning and the end
     *   if they form a new interpolation entry inside the host string.
     */
    private fun tripleQuotedPaste(
        text: String,
        interpolationPrefixLength: Int,
        selectionStartElement: PsiElement,
        beginTp: KtStringTemplateExpression,
        selectionModel: SelectionModel,
        editor: Editor,
    ): String {
        val templateTokenSequence = TemplateTokenSequence(text, interpolationPrefixLength)
        val indent = createIndent(beginTp, templateTokenSequence, selectionStartElement)
        val docCharSequence = editor.document.charsSequence

        return buildString {
            var indentToAdd = ""

            fun handleChunk(chunk: TemplateChunk, shouldEscapeDollars: Boolean) = when (chunk) {
                is LiteralChunk -> {
                    val noQuotes = escapeTripleQuotes(chunk)
                    val escapedLiteralChunk = if (shouldEscapeDollars) {
                        escapeDangerousDollars(noQuotes, docCharSequence, selectionModel, interpolationPrefixLength)
                    } else noQuotes
                    append(indentToAdd)
                    append(escapedLiteralChunk)
                    indentToAdd = ""
                }
                is EntryChunk -> {
                    append(indentToAdd)
                    append(chunk.toEscapedText(interpolationPrefixLength))
                    indentToAdd = ""
                }
                is NewLineChunk -> {
                    appendLine()
                    indentToAdd = indent
                }
            }

            val sequenceIterator = templateTokenSequence.iterator()
            if (!sequenceIterator.hasNext()) { return@buildString }

            val firstChunk = sequenceIterator.next()
            handleChunk(firstChunk, shouldEscapeDollars = true)

            while (sequenceIterator.hasNext()) {
                val nextChunk = sequenceIterator.next()
                handleChunk(nextChunk, shouldEscapeDollars = !sequenceIterator.hasNext())
            }
        }
    }

    private fun createIndent(
        beginTp: KtStringTemplateExpression,
        templateTokenSequence: TemplateTokenSequence,
        selectionStartElement: PsiElement,
    ): String {
        return if (!beginTp.isSingleQuoted() &&
            (beginTp.getQualifiedExpressionForReceiver()?.selectorExpression as? KtCallExpression)?.calleeExpression?.text == "trimIndent" &&
            templateTokenSequence.firstOrNull()?.indent() == templateTokenSequence.lastOrNull()?.indent()
        ) {
            selectionStartElement.parent?.prevSibling?.text?.takeIf { it.all { c -> c == ' ' || c == '\t' } }
        } else {
            null
        } ?: ""
    }

    private fun TemplateChunk?.indent() = when (this) {
        is LiteralChunk -> this.text.takeWhile { it.isWhitespace() }
        is EntryChunk -> this.text.takeWhile { it.isWhitespace() }
        else -> ""
    }

    private val tripleQuoteRe: Regex = Regex("\"{3,}")

    private fun escapeTripleQuotes(chunk: LiteralChunk): String =
        tripleQuoteRe.replace(chunk.text) { "\"\"" + "\${'\"'}".repeat(it.value.count() - 2) }

    private fun escapeDangerousDollars(
        unescapedInput: String,
        documentCharSequence: CharSequence,
        selectionModel: SelectionModel,
        interpolationPrefixLength: Int,
    ): String {
        if (!unescapedInput.endsWith("$") && !unescapedInput.startsWith("$")) return unescapedInput

        val firstPastedDollarsCount = unescapedInput.takeWhile { c -> c == '$' }.count()
        val lastPastedDollarsCount = unescapedInput.takeLastWhile { c -> c == '$' }.count()
        val previousTrailingDollarsCount = countPrecedingDollars(documentCharSequence, selectionModel)
        val dollarsAfterPastedTextCount = countFollowingDollars(documentCharSequence, selectionModel)

        val isOnlyDollarsPasted = unescapedInput.all { it == '$' }
        val charAfterFirstPastedDollars = unescapedInput.getOrNull(firstPastedDollarsCount)
        val charAfterTrailingDollars = documentCharSequence.getOrNull(selectionModel.selectionEnd + dollarsAfterPastedTextCount)

        return if (isOnlyDollarsPasted) {
            adjustPastedTextAllDollars(
                unescapedInput,
                charAfterTrailingDollars,
                previousTrailingDollarsCount,
                dollarsAfterPastedTextCount,
                interpolationPrefixLength
            )
        } else {
            adjustPastedText(
                unescapedInput, charAfterFirstPastedDollars, charAfterTrailingDollars,
                previousTrailingDollarsCount, firstPastedDollarsCount, lastPastedDollarsCount,
                dollarsAfterPastedTextCount, interpolationPrefixLength,
            )
        }
    }

    private fun adjustPastedTextAllDollars(
        unescapedInput: String,
        charAfterTrailingDollars: Char?,
        dollarCountBefore: Int,
        dollarCountAfter: Int,
        interpolationPrefixLength: Int,
    ): String {
        if (charAfterTrailingDollars?.canBeStartOfIdentifierOrBlock() != true) return unescapedInput
        if (dollarCountBefore + dollarCountAfter + unescapedInput.length < interpolationPrefixLength) return unescapedInput
        return unescapedInput.dropLast(1) + createEscapedDollarEntryText(interpolationPrefixLength)
    }

    private fun adjustPastedText(
        unescapedInput: String,
        charAfterFirstPastedDollars: Char?,
        charAfterTrailingDollars: Char?,
        previousTrailingDollarsCount: Int,
        firstPastedDollarsCount: Int,
        lastPastedDollarsCount: Int,
        dollarsAfterPastedTextCount: Int,
        interpolationPrefixLength: Int,
    ): String = buildString {
        if (firstPastedDollarsCount > 0
            && charAfterFirstPastedDollars?.canBeStartOfIdentifierOrBlock() == true
            && previousTrailingDollarsCount + firstPastedDollarsCount >= interpolationPrefixLength
        ) {
            this.append(unescapedInput.take(firstPastedDollarsCount - 1))
            this.append(createEscapedDollarEntryText(interpolationPrefixLength))
        } else {
            this.append(unescapedInput.take(firstPastedDollarsCount))
        }

        val pastedTextWithoutLeadingDollars = unescapedInput.drop(firstPastedDollarsCount)

        if (lastPastedDollarsCount > 0
            && charAfterTrailingDollars?.canBeStartOfIdentifierOrBlock() == true
            && lastPastedDollarsCount + dollarsAfterPastedTextCount >= interpolationPrefixLength
        ) {
            this.append(pastedTextWithoutLeadingDollars.dropLast(1))
            this.append(createEscapedDollarEntryText(interpolationPrefixLength))
        } else {
            this.append(pastedTextWithoutLeadingDollars)
        }
    }

    private fun countPrecedingDollars(docCharSequence: CharSequence, selectionModel: SelectionModel): Int {
        var previousTrailingDollarsCount = 0
        while (docCharSequence.getOrNull(selectionModel.selectionStart - previousTrailingDollarsCount - 1) == '$') {
            previousTrailingDollarsCount++
        }
        return previousTrailingDollarsCount
    }

    private fun countFollowingDollars(docCharSequence: CharSequence, selectionModel: SelectionModel): Int {
        var nextEntryLeadingDollars = 0
        while (docCharSequence.getOrNull(selectionModel.selectionEnd + nextEntryLeadingDollars) == '$') {
            nextEntryLeadingDollars++
        }
        return nextEntryLeadingDollars
    }

    private fun EntryChunk.toEscapedText(interpolationPrefixLength: Int): String {
        if (!text.startsWith("$")) return text
        val leadingDollars = text.takeWhile { it == '$' }
        val escapedDollar = createEscapedDollarEntryText(interpolationPrefixLength)
        val textAfterDollars = text.substring(leadingDollars.length)
        return leadingDollars.drop(1) + escapedDollar + textAfterDollars
    }

    private fun createEscapedDollarEntryText(prefixLength: Int): String = "${"$".repeat(maxOf(prefixLength, 1))}{'$'}"
}
