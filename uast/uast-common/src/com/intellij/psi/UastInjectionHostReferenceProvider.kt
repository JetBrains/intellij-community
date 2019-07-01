// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi

import com.intellij.util.ProcessingContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*
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

/**
 * NOTE: Consider using [UastInjectionHostReferenceProvider] instead, because [PsiLanguageInjectionHost] could
 * correspond not only to [ULiteralExpression] in general case.
 * @see sourceInjectionHost
 */
@Deprecated("use UastInjectionHostReferenceProvider instead", ReplaceWith("UastInjectionHostReferenceProvider"))
@ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
abstract class UastLiteralReferenceProvider : UastReferenceProvider() {

  override val supportedUElementTypes: List<Class<out UElement>> = listOf(ULiteralExpression::class.java)

  override fun getReferencesByElement(element: UElement, context: ProcessingContext): Array<PsiReference> {
    val uLiteral = element as? ULiteralExpression ?: return PsiReference.EMPTY_ARRAY
    val host = uLiteral.psiLanguageInjectionHost ?: return PsiReference.EMPTY_ARRAY
    return getReferencesByULiteral(uLiteral, host, context)
  }

  abstract fun getReferencesByULiteral(uLiteral: ULiteralExpression,
                                       host: PsiLanguageInjectionHost,
                                       context: ProcessingContext): Array<PsiReference>

}
