// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.lang.formatter

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import com.intellij.psi.util.siblings
import org.intellij.plugins.markdown.lang.MarkdownLanguage
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.formatter.settings.MarkdownCustomCodeStyleSettings
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElementFactory
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownBlockQuoteImpl
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownParagraphImpl
import org.intellij.plugins.markdown.util.MarkdownPsiUtil
import org.intellij.plugins.markdown.util.hasType

/**
 * Inserts block quote arrows `>` before wrapped text elements when reformatting block quotes.
 */
internal class BlockquotePostFormatProcessor: PostFormatProcessor {
  override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement {
    if (shouldProcess(source.containingFile, settings) && source is MarkdownBlockQuoteImpl) {
      commit(source)
      processBlockquote(source)
    }
    return source
  }

  override fun processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange {
    if (!shouldProcess(source, settings)) {
      return rangeToReformat
    }
    commit(source)
    val firstChild = source.firstChild?.firstChild ?: return rangeToReformat
    val quotes = firstChild.siblings(forward = true, withSelf = true).filterIsInstance<MarkdownBlockQuoteImpl>()
    for (quote in quotes) {
      if (rangeToReformat.intersects(rangeToReformat)) {
        processBlockquote(quote)
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

  private fun processParagraph(paragraph: MarkdownParagraphImpl, level: Int) {
    val firstChild = paragraph.firstChild ?: return
    println(DebugUtil.psiToString(paragraph, true, true))
    val elements = firstChild.siblings(forward = true, withSelf = true).filter(this::shouldProcessTextElement)
    for (element in elements) {
      repeat(level) {
        val arrow = MarkdownPsiElementFactory.createBlockquoteArrow(paragraph.project)
        paragraph.addBefore(arrow, element)
      }
    }
  }

  private fun shouldProcessTextElement(element: PsiElement): Boolean {
    return element.hasType(MarkdownTokenTypes.TEXT) && element.prevSibling?.let(MarkdownPsiUtil.WhiteSpaces::isNewLine) == true
  }

  private fun processBlockquote(blockquote: MarkdownBlockQuoteImpl, level: Int = 1) {
    val firstChild = blockquote.firstChild ?: return
    val children = firstChild.siblings(forward = true, withSelf = true)
    for (element in children) {
      when (element) {
        is MarkdownParagraphImpl -> processParagraph(element, level)
        is MarkdownBlockQuoteImpl -> processBlockquote(element, level + 1)
      }
    }
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
