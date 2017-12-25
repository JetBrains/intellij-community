/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:JvmName("UastReferenceRegistrar")

package com.intellij.psi

import com.intellij.openapi.util.Key
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.ElementPatternCondition
import com.intellij.patterns.InitialPatternCondition
import com.intellij.util.ProcessingContext
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.psiLanguageInjectionHost
import org.jetbrains.uast.toUElement

fun PsiReferenceRegistrar.registerUastReferenceProvider(pattern: (UElement, ProcessingContext) -> Boolean,
                                                        provider: UastReferenceProvider,
                                                        priority: Double = PsiReferenceRegistrar.DEFAULT_PRIORITY) {
  this.registerReferenceProvider(UastPatternAdapter(pattern), UastReferenceProviderAdapter(provider), priority)
}

fun PsiReferenceRegistrar.registerUastReferenceProvider(pattern: ElementPattern<out UElement>,
                                                        provider: UastReferenceProvider,
                                                        priority: Double = PsiReferenceRegistrar.DEFAULT_PRIORITY) {
  this.registerReferenceProvider(UastPatternAdapter(pattern::accepts), UastReferenceProviderAdapter(provider), priority)
}

abstract class UastReferenceProvider {

  abstract fun getReferencesByElement(element: UElement, context: ProcessingContext): Array<PsiReference>

}

fun uastLiteralReferenceProvider(provider: (ULiteralExpression, PsiLanguageInjectionHost) -> Array<PsiReference>) =
  object : UastReferenceProvider() {
    override fun getReferencesByElement(element: UElement, context: ProcessingContext): Array<PsiReference> {
      val uLiteral = element as? ULiteralExpression ?: return PsiReference.EMPTY_ARRAY
      val host = uLiteral.psiLanguageInjectionHost ?: return PsiReference.EMPTY_ARRAY
      return provider(uLiteral, host)
    }
  }

private val cachedUElement = Key.create<UElement>("UastReferenceRegistrar.cachedUElement")

private fun getOrCreateCachedElement(element: PsiElement, context: ProcessingContext?): UElement? =
  element as? UElement ?:
  context?.get(cachedUElement) ?:
  element.toUElement()?.also { context?.put(cachedUElement, it) }

private class UastPatternAdapter(val predicate: (UElement, ProcessingContext) -> Boolean) : ElementPattern<PsiElement> {

  override fun accepts(o: Any?): Boolean = accepts(o, null)

  override fun accepts(o: Any?, context: ProcessingContext?): Boolean = when (o) {
    is PsiElement ->
      getOrCreateCachedElement(o, context)
        ?.let { predicate(it, context ?: ProcessingContext()) }
      ?: false
    else -> false
  }

  private val condition = ElementPatternCondition(object : InitialPatternCondition<PsiElement>(PsiElement::class.java) {
    override fun accepts(o: Any?, context: ProcessingContext?): Boolean = this@UastPatternAdapter.accepts(o, context)
  })

  override fun getCondition(): ElementPatternCondition<PsiElement> = condition
}

private class UastReferenceProviderAdapter(val provider: UastReferenceProvider) : PsiReferenceProvider() {
  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
    val uElement = getOrCreateCachedElement(element, context) ?: return PsiReference.EMPTY_ARRAY
    return provider.getReferencesByElement(uElement, context)
  }

  override fun acceptsTarget(target: PsiElement): Boolean = true
}