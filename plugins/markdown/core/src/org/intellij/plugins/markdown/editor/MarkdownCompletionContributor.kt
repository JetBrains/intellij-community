// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionInitializationContext
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.patterns.PlatformPatterns.psiElement
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile

class MarkdownCompletionContributor: CompletionContributor() {
  init {
    extend(CompletionType.BASIC, psiElement(MarkdownTokenTypes.FENCE_LANG), CodeFenceLanguageListCompletionProvider())
  }

  override fun beforeCompletion(context: CompletionInitializationContext) {
    if (context.file is MarkdownFile && context.dummyIdentifier != dummyIdentifier) {
      context.dummyIdentifier = dummyIdentifier
    }
  }

  companion object {
    @JvmStatic
    val dummyIdentifier by lazy { "${CompletionInitializationContext.DUMMY_IDENTIFIER}\n" }
  }
}
