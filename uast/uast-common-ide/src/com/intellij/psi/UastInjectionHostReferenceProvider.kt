// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi

import com.intellij.util.ProcessingContext
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.expressions.UInjectionHost

abstract class UastInjectionHostReferenceProvider : UastReferenceProvider() {

  override val supportedUElementTypes: List<Class<out UElement>> = listOf(UInjectionHost::class.java)

  override fun getReferencesByElement(element: UElement, context: ProcessingContext): Array<PsiReference> {
    val uLiteral = element as? UExpression ?: return PsiReference.EMPTY_ARRAY
    val host = context[REQUESTED_PSI_ELEMENT] as? PsiLanguageInjectionHost ?: return PsiReference.EMPTY_ARRAY
    return getReferencesForInjectionHost(uLiteral, host, context)
  }

  abstract fun getReferencesForInjectionHost(uExpression: UExpression,
                                             host: PsiLanguageInjectionHost,
                                             context: ProcessingContext): Array<PsiReference>
}
