// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi

import com.intellij.util.ProcessingContext
import org.jetbrains.uast.*

abstract class UastInjectionHostReferenceProvider : UastReferenceProvider() {

  override val supportedUElementTypes: List<Class<out UElement>> = listOf(UExpression::class.java)

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

abstract class UastStringLiteralReferenceProvider : UastReferenceProvider() {

  override val supportedUElementTypes: List<Class<out UElement>> = listOf(UExpression::class.java)

  override fun getReferencesByElement(element: UElement, context: ProcessingContext): Array<PsiReference> {
    val injectionHost = context[REQUESTED_PSI_ELEMENT] as? PsiLanguageInjectionHost ?: return PsiReference.EMPTY_ARRAY
    if (element is ULiteralExpression)
      return getReferencesByULiteral(element, injectionHost, context) // simple string (java usually)
    if (element is UPolyadicExpression) { // Kotlin, see KT-27283
      return element.operands.asSequence().filterIsInstance<ULiteralExpression>().flatMap {
        getReferencesByULiteral(it, injectionHost, context).asSequence()
      }.toList().toTypedArray()
    }

    return PsiReference.EMPTY_ARRAY
  }

  abstract fun getReferencesByULiteral(uLiteral: ULiteralExpression,
                                       host: PsiLanguageInjectionHost,
                                       context: ProcessingContext): Array<PsiReference>

}