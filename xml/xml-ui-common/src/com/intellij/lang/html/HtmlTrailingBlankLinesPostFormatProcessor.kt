// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.html

import com.intellij.formatting.FormattingContext
import com.intellij.lang.LanguageFormatting
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.formatter.xml.HtmlPolicy
import com.intellij.psi.formatter.xml.XmlBlock
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor

/**
 * Reduces the blank lines at the end of a file to "Keep blank lines", when the formatted range reaches the end of the file.
 * It applies to a file whose root formatting block is an [XmlBlock] with an [HtmlPolicy], for example HTML, Vue or Astro.
 * The processor never adds a line break.
 */
internal class HtmlTrailingBlankLinesPostFormatProcessor : PostFormatProcessor {
  override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement {
    val file = source.containingFile ?: return source
    processText(file, source.textRange, settings)
    return source
  }

  override fun processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange {
    if (InjectedLanguageManager.getInstance(source.project).isInjectedFragment(source)) return rangeToReformat
    val documentManager = PsiDocumentManager.getInstance(source.project)
    val document = documentManager.getDocument(source) ?: return rangeToReformat

    val text = document.charsSequence
    var contentEnd = text.length
    while (contentEnd > 0 && text[contentEnd - 1].isWhitespace()) contentEnd--
    if (contentEnd == 0 || rangeToReformat.endOffset < contentEnd) return rangeToReformat

    val lineBreaks = (contentEnd until text.length).count { text[it] == '\n' }
    // One line break always stays, so do not build the formatting model when there is nothing to remove
    if (lineBreaks <= 1) return rangeToReformat
    val keptLineBreaks = getHtmlPolicy(source, settings)?.keepBlankLines?.plus(1) ?: return rangeToReformat
    if (lineBreaks <= keptLineBreaks) return rangeToReformat

    val removed = text.length - contentEnd - keptLineBreaks
    document.replaceString(contentEnd, text.length, "\n".repeat(keptLineBreaks))
    documentManager.commitDocument(document)
    return TextRange(rangeToReformat.startOffset, maxOf(contentEnd, rangeToReformat.endOffset - removed))
  }

  private fun getHtmlPolicy(file: PsiFile, settings: CodeStyleSettings): HtmlPolicy? {
    val builder = LanguageFormatting.INSTANCE.forContext(file) ?: return null
    val rootBlock = builder.createModel(FormattingContext.create(file, settings)).rootBlock as? XmlBlock ?: return null
    return rootBlock.policy as? HtmlPolicy
  }

  override fun isWhitespaceOnly(): Boolean = true
}
