// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi

import com.intellij.openapi.progress.impl.CancellationCheck
import com.intellij.util.ProcessingContext
import org.jetbrains.uast.UElement

internal class UastReferenceProviderAdapter(private val supportedUElementTypes: List<Class<out UElement>>,
                                            private val provider: UastReferenceProvider) : PsiReferenceProvider() {

  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
    val uElement = UastPatternAdapter.getOrCreateCachedElement(element, context, supportedUElementTypes) ?: return PsiReference.EMPTY_ARRAY
    return CancellationCheck.runWithCancellationCheck { provider.getReferencesByElement (uElement, context) }
  }

  override fun acceptsTarget(target: PsiElement): Boolean {
    return provider.acceptsTarget(target)
  }

  override fun acceptsHints(element: PsiElement, hints: PsiReferenceService.Hints): Boolean {
    if (!provider.acceptsHint(hints)) return false

    return super.acceptsHints(element, hints)
  }
}