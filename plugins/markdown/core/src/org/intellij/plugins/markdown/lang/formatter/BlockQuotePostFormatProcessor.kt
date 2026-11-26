// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.formatter

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.siblings
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.intellij.plugins.markdown.lang.formatter.settings.MarkdownCustomCodeStyleSettings
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownBlockQuote
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeBlock
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownParagraph
import org.intellij.plugins.markdown.util.MarkdownPsiUtil

/**
 * Inserts block quote arrows `>` before wrapped text elements when reformatting block quotes.
 */
internal class BlockQuotePostFormatProcessor: PostFormatProcessor {
  override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement {
    if (shouldProcess(source.containingFile, settings) && source is MarkdownBlockQuote) {
      commit(source)
      processBlockQuote(source)
      doPostponedFormatting(source)
    }
    return source
  }

  override fun processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange {
    if (!shouldProcess(source, settings)) {
      return rangeToReformat
    }
    val document = obtainDocument(source) ?: return rangeToReformat
    commit(source)
    val firstChild = source.firstChild ?: return rangeToReformat
    val quotes = firstChild.siblings(forward = true, withSelf = true).filterIsInstance<MarkdownBlockQuote>().toList().asReversed()
    val validRange = rangeToReformat.intersection(TextRange(0, document.textLength)) ?: return rangeToReformat
    // The inserted `>` markers move the end of the range
    val range = document.createRangeMarker(validRange).apply {
      isGreedyToLeft = true
      isGreedyToRight = true
    }
    try {
      for (quote in quotes) {
        if (validRange.intersectsStrict(quote.textRange)) {
          processBlockQuote(quote)
        }
      }
      doPostponedFormatting(source)
      return range.textRange
    }
    finally {
      range.dispose()
    }
  }

  private fun shouldProcess(file: PsiFile, settings: CodeStyleSettings): Boolean {
    if (file.language != MarkdownLanguage.INSTANCE || file !is MarkdownFile) {
      return false
    }
    val custom = settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java)
    return custom.INSERT_QUOTE_ARROWS_ON_WRAP
  }

  /**
   * The parser ends a block quote before a line without `>`, so a line wrapped out of the last quoted paragraph
   * follows the quote as a paragraph or a code block. Gives each line of that block the line prefix of the quoted paragraph.
   */
  private fun processBlockQuote(blockQuote: MarkdownBlockQuote) {
    val document = obtainDocument(blockQuote) ?: return
    val paragraph = PsiTreeUtil.findChildrenOfType(blockQuote, MarkdownParagraph::class.java).lastOrNull() ?: return
    val newLine = blockQuote.nextSibling?.takeIf(MarkdownPsiUtil.WhiteSpaces::isNewLine) ?: return
    val wrapped = newLine.nextSibling?.takeIf { it is MarkdownParagraph || it is MarkdownCodeBlock } ?: return

    val text = document.charsSequence
    val start = paragraph.textRange.startOffset
    val prefix = text.subSequence(document.getLineStartOffset(document.getLineNumber(start)), start)
      .map { if (it == '>' || it.isWhitespace()) it else ' ' }
      .joinToString("")
    val range = wrapped.textRange
    val lines = document.getLineNumber(range.startOffset)..document.getLineNumber(range.endOffset)
    for (line in lines) {
      val lineStart = document.getLineStartOffset(line)
      var end = StringUtil.skipWhitespaceForward(text, lineStart)
      // The formatter indents a quoted line of a list item, so its `>` becomes a part of an indented code block
      if (end < text.length && text[end] == '>') {
        end = StringUtil.skipWhitespaceForward(text, end + 1)
      }
      document.replaceString(lineStart, end, prefix)
    }
  }

  private fun commit(element: PsiElement) {
    val document = obtainDocument(element) ?: return
    PsiDocumentManager.getInstance(element.project).commitDocument(document)
  }

  private fun doPostponedFormatting(element: PsiElement) {
    val document = obtainDocument(element) ?: return
    PsiDocumentManager.getInstance(element.project).doPostponedOperationsAndUnblockDocument(document)
  }

  private fun obtainDocument(element: PsiElement): Document? {
    return element.containingFile?.viewProvider?.document
  }
}
