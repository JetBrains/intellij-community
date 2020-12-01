// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
}