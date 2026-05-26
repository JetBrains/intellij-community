// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.formatter

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import com.intellij.psi.util.siblings
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.formatter.settings.MarkdownCustomCodeStyleSettings
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElementFactory
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownBlockQuote
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownList
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownListItem
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownParagraph
import org.intellij.plugins.markdown.lang.psi.util.hasType
import org.intellij.plugins.markdown.util.MarkdownPsiUtil

/**
 * Inserts block quote arrows `>` before wrapped text elements when reformatting block quotes.
 */
internal class BlockQuotePostFormatProcessor: PostFormatProcessor {
  override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement {
    if (shouldProcess(source.containingFile, settings) && source is MarkdownBlockQuote) {
      commit(source)
      processBlockQuote(source)
    }
    return source
  }

  override fun processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange {
    if (!shouldProcess(source, settings)) {
      return rangeToReformat
    }
    commit(source)
    val firstChild = source.firstChild ?: return rangeToReformat
    val quotes = firstChild.siblings(forward = true, withSelf = true).filterIsInstance<MarkdownBlockQuote>()
    for (quote in quotes) {
      if (rangeToReformat.intersects(rangeToReformat)) {
        processBlockQuote(quote)
      }
    }
    return rangeToReformat
  }

  private fun shouldProcess(file: PsiFile, settings: CodeStyleSettings): Boolean {
    if (file.language != MarkdownLanguage.INSTANCE || file !is MarkdownFile) {
      return false
    }
    val custom = settings.getCustomSettings(MarkdownCustomCodeStyleSettings::class.java)
    return custom.INSERT_QUOTE_ARROWS_ON_WRAP
  }

  private fun processParagraph(paragraph: MarkdownParagraph, level: Int) {
    val firstChild = paragraph.firstChild ?: return
    val elements = firstChild.siblings(forward = true, withSelf = true).filter(this::shouldProcessElement)
    for (element in elements) {
      repeat(level) {
        val arrow = MarkdownPsiElementFactory.createBlockQuoteArrow(paragraph.project)
        paragraph.addBefore(arrow, element)
      }
    }
  }

  private fun shouldProcessElement(element: PsiElement): Boolean {
    if (element.hasType(MarkdownTokenTypes.BLOCK_QUOTE) || element.hasType(MarkdownTokenTypeSets.WHITE_SPACES)) return false
    return element.prevSibling?.let(MarkdownPsiUtil.WhiteSpaces::isNewLine) == true
  }

  private fun processBlockQuote(blockQuote: MarkdownBlockQuote, level: Int = 1) {
    val firstChild = blockQuote.firstChild ?: return
    val children = firstChild.siblings(forward = true, withSelf = true)
    for (element in children) {
      when (element) {
        is MarkdownParagraph -> processParagraph(element, level)
        is MarkdownBlockQuote -> processBlockQuote(element, level + 1)
        is MarkdownList -> processList(element, level)
      }
    }
  }

  private fun processList(list: MarkdownList, level: Int) {
    for (item in list.children) {
      if (item !is MarkdownListItem) continue
      for (child in item.children) {
        when (child) {
          is MarkdownParagraph -> {
            fixMisplacedQuoteArrows(child, level)
            processParagraph(child, level)
          }
          is MarkdownList -> processList(child, level)
          is MarkdownBlockQuote -> processBlockQuote(child, level + 1)
        }
      }
    }
  }

  /**
   * Detects a "misplaced quote arrow" pattern produced by the formatter when wrapping a list-item
   * paragraph inside a block quote: `\n` + indent_ws + `>` (parsed as a `GT` literal because it is
   * no longer at column 0) + sep_ws + content. Rewrites the line to start with `>` at column 0,
   * dropping the `level * 2` characters of indent the formatter counted for the absent block-quote
   * prefix on the wrapped line.
   */
  private fun fixMisplacedQuoteArrows(paragraph: MarkdownParagraph, level: Int) {
    val document = paragraph.containingFile?.viewProvider?.document ?: return
    val firstChild = paragraph.firstChild ?: return
    val edits = firstChild.siblings(forward = true, withSelf = true).mapNotNull { element ->
      if (!element.hasType(MarkdownTokenTypes.GT)) return@mapNotNull null
      val indent = element.prevSibling?.takeIf(MarkdownPsiUtil.WhiteSpaces::isWhiteSpace) ?: return@mapNotNull null
      if (indent.prevSibling?.let(MarkdownPsiUtil.WhiteSpaces::isNewLine) != true) return@mapNotNull null
      val sep = element.nextSibling?.takeIf(MarkdownPsiUtil.WhiteSpaces::isWhiteSpace)
      val newSep = " ".repeat(maxOf(1, indent.textLength + (sep?.textLength ?: 0) - 2 * level))
      Triple(indent.textOffset, (sep ?: element).textRange.endOffset, ">$newSep")
    }.toList()
    if (edits.isEmpty()) return
    for ((start, end, text) in edits.asReversed()) document.replaceString(start, end, text)
    commit(paragraph)
  }

  private fun commit(element: PsiElement) {
    val viewProvider = when (element) {
      is PsiFile -> element.viewProvider
      else -> element.containingFile?.viewProvider
    }
    val document = viewProvider?.document ?: return
    PsiDocumentManager.getInstance(element.project).commitDocument(document)
  }
}
