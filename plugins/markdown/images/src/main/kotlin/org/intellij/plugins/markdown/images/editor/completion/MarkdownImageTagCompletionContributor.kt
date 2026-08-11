package org.intellij.plugins.markdown.images.editor.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.patterns.PlatformPatterns
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets

internal class MarkdownImageTagCompletionContributor: CompletionContributor() {
  private val pattern = PlatformPatterns.psiElement().andNot(PlatformPatterns.psiElement().inside(PlatformPatterns.or(
    PlatformPatterns.psiElement().withElementType(MarkdownTokenTypeSets.LINKS),
    PlatformPatterns.psiElement(MarkdownElementTypes.CODE_FENCE),
    PlatformPatterns.psiElement(MarkdownElementTypes.CODE_SPAN),
    PlatformPatterns.psiElement(MarkdownElementTypes.CODE_BLOCK)
  )))

  init {
    extend(CompletionType.BASIC, pattern, MarkdownImageTagCompletionProvider())
  }
}
