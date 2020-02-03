// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.ProcessingContext

internal class UastReferenceProviderAdapter(val provider: UastReferenceProvider) : PsiReferenceProvider() {
  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
    val uElement = getOrCreateCachedElement(element, context, provider.supportedUElementTypes) ?: return PsiReference.EMPTY_ARRAY
    val references = provider.getReferencesByElement(uElement, context)
    if (ApplicationManager.getApplication().isUnitTestMode || ApplicationManager.getApplication().isInternal) {
      for (reference in references) {
        if (reference.element !== element)
          throw AssertionError(
            """reference $reference was created for $element but targets ${reference.element}, provider $provider"""
          )
      }
    }
    return references
  }

  override fun toString(): String = "UastReferenceProviderAdapter($provider)"

  override fun acceptsTarget(target: PsiElement): Boolean = provider.acceptsTarget(target)
}