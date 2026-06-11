// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.references.backtick

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownCodeSpan

internal class BacktickReferenceContributor: PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(
      PlatformPatterns.psiElement(MarkdownCodeSpan::class.java),
      object : PsiReferenceProvider() {
        override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
          val codeSpan = element as? MarkdownCodeSpan
          val contentRange = codeSpan?.getContentRange() ?: return PsiReference.EMPTY_ARRAY
          val content = contentRange.substring(codeSpan.text)
          if (content.isBlank()) return PsiReference.EMPTY_ARRAY

          val references = BacktickPathReferenceProvider.getReferences(codeSpan, contentRange, content)
          if (references.isNotEmpty()) return references
          return arrayOf(BacktickReference(codeSpan))
        }
      }
    )
  }
}
