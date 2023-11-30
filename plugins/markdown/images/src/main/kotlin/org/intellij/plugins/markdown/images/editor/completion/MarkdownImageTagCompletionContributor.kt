package org.intellij.plugins.markdown.images.editor.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionInitializationContext
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import org.intellij.plugins.markdown.editor.MarkdownCompletionContributor
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile

internal class MarkdownImageTagCompletionContributor: CompletionContributor() {
  init {
    extend(CompletionType.BASIC, imageTagPattern(), MarkdownImageTagCompletionProvider())
  }

  private val dummyIdentifier
    get() = MarkdownCompletionContributor.dummyIdentifier

  override fun beforeCompletion(context: CompletionInitializationContext) {
    if (context.file is MarkdownFile && context.dummyIdentifier != dummyIdentifier) {
      context.dummyIdentifier = dummyIdentifier
    }
  }

  private fun imageTagPattern(): PsiElementPattern.Capture<PsiElement> {
    return PlatformPatterns.psiElement().andNot(PlatformPatterns.psiElement().inside(PlatformPatterns.or(
      fromSet(MarkdownTokenTypeSets.LINKS),
      PlatformPatterns.psiElement(MarkdownElementTypes.CODE_FENCE),
      PlatformPatterns.psiElement(MarkdownElementTypes.CODE_SPAN),
      PlatformPatterns.psiElement(MarkdownElementTypes.CODE_BLOCK)
    )))
  }

  private fun fromSet(set: TokenSet): ElementPattern<PsiElement> {
    val types = set.types
    return PlatformPatterns.or(*Array(types.size) { PlatformPatterns.psiElement(types[it]) })
  }
}
