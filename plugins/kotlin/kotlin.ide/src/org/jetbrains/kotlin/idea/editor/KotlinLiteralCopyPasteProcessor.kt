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
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers.range
import org.jetbrains.kotlin.idea.codeinsights.impl.base.EntryChunk
import org.jetbrains.kotlin.idea.codeinsights.impl.base.LiteralChunk
import org.jetbrains.kotlin.idea.codeinsights.impl.base.NewLineChunk
import org.jetbrains.kotlin.idea.codeinsights.impl.base.TemplateChunk
import org.jetbrains.kotlin.idea.codeinsights.impl.base.TemplateTokenSequence
import org.jetbrains.kotlin.idea.codeinsights.impl.base.canBeStartOfIdentifierOrBlock
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getContentRange
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForReceiver
import org.jetbrains.kotlin.psi.psiUtil.isSingleQuoted
import org.jetbrains.kotlin.psi.psiUtil.startOffset

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

internal class KotlinLiteralCopyPasteProcessor : CopyPastePreProcessor {
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
            singleQuotedPaste(text, prefixLength, editor, selectionModel)
        } else {
            tripleQuotedPaste(text, prefixLength, selectionStartElement, beginTp, selectionModel, editor)
        }
    }

    /**
     * Escape the pasted [text] inserted into a single-quoted string with [interpolationPrefixLength].
     *
     * Special characters, quotes, and interpolation entries are escaped unconditionally.
     * Dollars of literal chunks are only escaped when they would otherwise start an interpolation entry,
     * see [findDollarsToEscapeOnPaste].
     */
    private fun singleQuotedPaste(
        text: String,
        interpolationPrefixLength: Int,
        editor: Editor,
        selectionModel: SelectionModel,
    ): String {
        val res = StringBuilder()
        val interpolationPrefix = "$".repeat(interpolationPrefixLength)
        val lineBreak = "\\n\"+\n $interpolationPrefix\""
        val chunks = TemplateTokenSequence(text, interpolationPrefixLength).toList()
        val dollarContext = createPasteDollarContext(editor, selectionModel, interpolationPrefixLength)
        var endsInLineBreak = false

        for ((index, chunk) in chunks.withIndex()) {
            when (chunk) {
                is LiteralChunk -> {
                    val chunkText = chunk.text
                    val indicesOfDollarsToEscape =
                        findDollarsToEscape(chunks, index, chunkText, dollarContext, interpolationPrefixLength, isSingleQuoted = true)
                    res.appendEscapedLiteralChunk(chunkText, indicesOfDollarsToEscape)
                }
                is EntryChunk -> {
                    val additionalEscapedChars = if (interpolationPrefixLength > 1) "\"" else "$\""
                    val chunkText = chunk.text
                    StringUtil.escapeStringCharacters(chunkText.length, chunkText, additionalEscapedChars, res)
                }
                is NewLineChunk -> {
                    res.append(lineBreak)
                }
            }
            endsInLineBreak = chunk is NewLineChunk
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
     * — dollar characters parsed as literal entries are only escaped if they start
     *   a new interpolation entry inside the host string, see [findDollarsToEscapeOnPaste].
     */
    private fun tripleQuotedPaste(
        text: String,
        interpolationPrefixLength: Int,
        selectionStartElement: PsiElement,
        beginTp: KtStringTemplateExpression,
        selectionModel: SelectionModel,
        editor: Editor,
    ): String {
        val chunks = TemplateTokenSequence(text, interpolationPrefixLength).toList()
        val indent = createIndent(beginTp, chunks, selectionStartElement)
        val dollarContext = createPasteDollarContext(editor, selectionModel, interpolationPrefixLength)

        return buildString {
            var indentToAdd = ""

            for ((index, chunk) in chunks.withIndex()) {
                when (chunk) {
                    is LiteralChunk -> {
                        val chunkText = chunk.text
                        val dollarIndicesToEscape =
                            findDollarsToEscape(chunks, index, chunkText, dollarContext, interpolationPrefixLength, isSingleQuoted = false)
                        // dollars are escaped first: `escapeTripleQuotes` introduces `${'"'}` entries that shouldn't be touched
                        val escapedDollar = createEscapedDollarEntryText(interpolationPrefixLength)
                        val chunkWithEscapedDollars = replaceDollarsWithEscapedText(chunkText, dollarIndicesToEscape, escapedDollar)
                        val chunkWithFullEscaping = escapeTripleQuotes(chunkWithEscapedDollars)
                        append(indentToAdd)
                        append(chunkWithFullEscaping)
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
            }
        }
    }

    private class PasteDollarContext(
        val dollarsBefore: Int,
        val dollarsAfter: Int,
        val charAfterDollars: Char?,
    )

    private fun createPasteDollarContext(
        editor: Editor,
        selectionModel: SelectionModel,
        interpolationPrefixLength: Int,
    ): PasteDollarContext {
        // Fallback for multiple carets — assume unsafe surroundings
        if (editor.caretModel.caretCount > 1) {
            return PasteDollarContext(
                dollarsBefore = maxOf(interpolationPrefixLength, 1),
                dollarsAfter = 0,
                charAfterDollars = UNKNOWN_IDENTIFIER_START_CHAR,
            )
        }
        val docCharSequence = editor.document.charsSequence
        val dollarsAfter = countFollowingDollars(docCharSequence, selectionModel)
        return PasteDollarContext(
            dollarsBefore = countPrecedingDollars(docCharSequence, selectionModel),
            dollarsAfter = dollarsAfter,
            charAfterDollars = docCharSequence.getOrNull(selectionModel.selectionEnd + dollarsAfter),
        )
    }

    private fun findDollarsToEscape(
        chunks: List<TemplateChunk>,
        chunkIndex: Int,
        chunkText: String,
        dollarContext: PasteDollarContext,
        interpolationPrefixLength: Int,
        isSingleQuoted: Boolean,
    ): List<Int> {
        val isLastChunk = chunkIndex == chunks.lastIndex
        val dollarsBefore = if (chunkIndex == 0) dollarContext.dollarsBefore else 0
        val dollarsAfter = if (isLastChunk) dollarContext.dollarsAfter else 0
        val charAfter = if (isLastChunk) dollarContext.charAfterDollars else chunks[chunkIndex + 1].firstChar(isSingleQuoted)

        return findDollarsToEscapeOnPaste(chunkText, dollarsBefore, dollarsAfter, charAfter, interpolationPrefixLength)
    }

    private fun TemplateChunk.firstChar(isSingleQuoted: Boolean): Char? = when (this) {
        is LiteralChunk -> text.firstOrNull()
        is EntryChunk -> '$'
        is NewLineChunk -> if (isSingleQuoted) '\\' else '\n'
    }

    private fun StringBuilder.appendEscapedLiteralChunk(chunkText: String, dollarsToEscape: List<Int>) {
        var from = 0
        for (dollarIndex in dollarsToEscape) {
            StringUtil.escapeStringCharacters(dollarIndex - from, chunkText.substring(from), LITERAL_CHUNK_ESCAPED_CHARS, this)
            append(SINGLE_QUOTED_ESCAPED_DOLLAR)
            from = dollarIndex + 1
        }
        StringUtil.escapeStringCharacters(chunkText.length - from, chunkText.substring(from), LITERAL_CHUNK_ESCAPED_CHARS, this)
    }

    private fun replaceDollarsWithEscapedText(chunkText: String, dollarIndicesToEscape: List<Int>, escapedDollar: String): String {
        if (dollarIndicesToEscape.isEmpty()) return chunkText
        return buildString {
            var from = 0
            for (dollarIndex in dollarIndicesToEscape) {
                append(chunkText, from, dollarIndex)
                append(escapedDollar)
                from = dollarIndex + 1
            }
            append(chunkText, from, chunkText.length)
        }
    }

    private fun createIndent(
        beginTp: KtStringTemplateExpression,
        chunks: List<TemplateChunk>,
        selectionStartElement: PsiElement,
    ): String {
        return if (!beginTp.isSingleQuoted() &&
            (beginTp.getQualifiedExpressionForReceiver()?.selectorExpression as? KtCallExpression)?.calleeExpression?.text == "trimIndent" &&
            chunks.firstOrNull()?.indent() == chunks.lastOrNull()?.indent()
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

    private fun escapeTripleQuotes(chunkText: String): String =
        tripleQuoteRe.replace(chunkText) { "\"\"" + "\${'\"'}".repeat(it.value.count() - 2) }

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

private const val LITERAL_CHUNK_ESCAPED_CHARS: String = "\""
private const val SINGLE_QUOTED_ESCAPED_DOLLAR: String = """\$"""
private const val UNKNOWN_IDENTIFIER_START_CHAR: Char = '_'

/**
 * Find indices of the dollars inside [chunkText] that have to be escaped to keep it plain text for a given [interpolationPrefixLength].
 * [dollarsBefore], [dollarsAfter] and [charAfter] describe the surroundings at the insertion point.
 */
@ApiStatus.Internal
fun findDollarsToEscapeOnPaste(
    chunkText: String,
    dollarsBefore: Int,
    dollarsAfter: Int,
    charAfter: Char?,
    interpolationPrefixLength: Int,
): List<Int> {
    val entryPrefixLength = maxOf(interpolationPrefixLength, 1)
    val dollarsToEscape = mutableListOf<Int>()
    var index = 0
    while (index < chunkText.length) {
        if (chunkText[index] != '$') {
            index++
            continue
        }
        // index pointing directly after the current dollar sequence
        var afterDollarSequenceIndex = index
        while (afterDollarSequenceIndex < chunkText.length && chunkText[afterDollarSequenceIndex] == '$') {
            afterDollarSequenceIndex++
        }

        val isTrailing = afterDollarSequenceIndex == chunkText.length
        val dollarsCountBefore = if (index == 0) dollarsBefore else 0
        val dollarsCountAfter = if (isTrailing) dollarsAfter else 0
        val charAfterDollars = if (isTrailing) charAfter else chunkText[afterDollarSequenceIndex]
        val totalSequentialDollarsCount = dollarsCountBefore + (afterDollarSequenceIndex - index) + dollarsCountAfter

        if (charAfterDollars?.canBeStartOfIdentifierOrBlock() == true
            && totalSequentialDollarsCount >= entryPrefixLength
            && dollarsCountAfter < entryPrefixLength // don't escape if there were enough dollars for prefix even before pasting
        ) {
            val lastDollarIndex = afterDollarSequenceIndex - 1
            dollarsToEscape += lastDollarIndex
        }
        index = afterDollarSequenceIndex
    }
    return dollarsToEscape
}
