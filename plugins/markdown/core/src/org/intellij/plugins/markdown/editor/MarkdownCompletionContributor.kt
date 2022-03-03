// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor

import com.intellij.codeInsight.completion.*
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import org.intellij.plugins.markdown.editor.images.MarkdownImageTagCompletionProvider
import org.intellij.plugins.markdown.lang.MarkdownElementTypes
import org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile

class MarkdownCompletionContributor: CompletionContributor() {
  init {
    extend(CompletionType.BASIC, psiElement(MarkdownTokenTypes.FENCE_LANG), CodeFenceLanguageListCompletionProvider())
    extend(CompletionType.BASIC, imageTagPattern(), MarkdownImageTagCompletionProvider())
  }

  override fun beforeCompletion(context: CompletionInitializationContext) {
    if (context.file is MarkdownFile && context.dummyIdentifier != dummyIdentifier) {
      context.dummyIdentifier = dummyIdentifier
    }
  }

  private fun imageTagPattern(): PsiElementPattern.Capture<PsiElement> {
    return psiElement().andNot(psiElement().inside(PlatformPatterns.or(
      fromSet(MarkdownTokenTypeSets.LINKS),
      psiElement(MarkdownElementTypes.CODE_FENCE),
      psiElement(MarkdownElementTypes.CODE_SPAN),
      psiElement(MarkdownElementTypes.CODE_BLOCK)
    )))
  }

  private fun fromSet(set: TokenSet): ElementPattern<PsiElement> {
    val types = set.types
    return PlatformPatterns.or(*Array(types.size) { psiElement(types[it]) })
  }

  companion object {
    @JvmStatic
    val dummyIdentifier by lazy { "${CompletionInitializationContext.DUMMY_IDENTIFIER}\n" }
  }
}
