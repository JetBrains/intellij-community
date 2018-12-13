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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Key
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.ElementPatternCondition
import com.intellij.patterns.InitialPatternCondition
import com.intellij.util.ProcessingContext
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.toUElement

fun PsiReferenceRegistrar.registerUastReferenceProvider(pattern: (UElement, ProcessingContext) -> Boolean,
                                                        provider: UastReferenceProvider,
                                                        priority: Double = PsiReferenceRegistrar.DEFAULT_PRIORITY) {
  this.registerReferenceProvider(UastPatternAdapter(pattern, provider.supportedUElementTypes),
                                 UastReferenceProviderAdapter(provider),
                                 priority)
}

fun PsiReferenceRegistrar.registerUastReferenceProvider(pattern: ElementPattern<out UElement>,
                                                        provider: UastReferenceProvider,
                                                        priority: Double = PsiReferenceRegistrar.DEFAULT_PRIORITY) {
  this.registerReferenceProvider(UastPatternAdapter(pattern::accepts, provider.supportedUElementTypes),
                                 UastReferenceProviderAdapter(provider), priority)
}

abstract class UastReferenceProvider(open val supportedUElementTypes: List<Class<out UElement>> = listOf(UElement::class.java)) {

  constructor(cls: Class<out UElement>) : this(listOf(cls))

  abstract fun getReferencesByElement(element: UElement, context: ProcessingContext): Array<PsiReference>

  open fun acceptsTarget(target: PsiElement): Boolean = true
}

/**
 * NOTE: Consider using [uastInjectionHostReferenceProvider] instead.
 * @see org.jetbrains.uast.sourceInjectionHost
 * @see UastLiteralReferenceProvider
 */
fun uastLiteralReferenceProvider(provider: (ULiteralExpression, PsiLanguageInjectionHost) -> Array<PsiReference>): UastLiteralReferenceProvider =
  object : UastLiteralReferenceProvider() {

    override fun getReferencesByULiteral(uLiteral: ULiteralExpression,
                                         host: PsiLanguageInjectionHost,
                                         context: ProcessingContext): Array<PsiReference> = provider(uLiteral, host)

    override fun toString(): String = "uastLiteralReferenceProvider(${provider.javaClass})"

  }

fun uastInjectionHostReferenceProvider(provider: (UExpression, PsiLanguageInjectionHost) -> Array<PsiReference>): UastInjectionHostReferenceProvider =
  object : UastInjectionHostReferenceProvider() {

    override fun getReferencesForInjectionHost(uExpression: UExpression,
                                               host: PsiLanguageInjectionHost,
                                               context: ProcessingContext): Array<PsiReference> = provider(uExpression, host)
  }

fun <T : UElement> uastReferenceProvider(cls: Class<T>, provider: (T, PsiElement) -> Array<PsiReference>): UastReferenceProvider =
  object : UastReferenceProvider(cls) {

    override fun getReferencesByElement(element: UElement, context: ProcessingContext): Array<PsiReference> =
      provider(cls.cast(element), context[REQUESTED_PSI_ELEMENT])
  }

inline fun <reified T : UElement> uastReferenceProvider(noinline provider: (T, PsiElement) -> Array<PsiReference>): UastReferenceProvider =
  uastReferenceProvider(T::class.java, provider)

private val cachedUElement = Key.create<UElement>("UastReferenceRegistrar.cachedUElement")
internal val REQUESTED_PSI_ELEMENT = Key.create<PsiElement>("REQUESTED_PSI_ELEMENT")

private fun getOrCreateCachedElement(element: PsiElement,
                                     context: ProcessingContext?,
                                     supportedUElementTypes: List<Class<out UElement>>): UElement? =
  element as? UElement ?: context?.get(cachedUElement) ?: supportedUElementTypes.asSequence().mapNotNull {
    element.toUElement(it)
  }.firstOrNull()?.also { context?.put(cachedUElement, it) }

private class UastPatternAdapter(
  val predicate: (UElement, ProcessingContext) -> Boolean,
  val supportedUElementTypes: List<Class<out UElement>>
) : ElementPattern<PsiElement> {

  override fun accepts(o: Any?): Boolean = accepts(o, null)

  override fun accepts(o: Any?, context: ProcessingContext?): Boolean = when (o) {
    is PsiElement ->
      getOrCreateCachedElement(o, context, supportedUElementTypes)
        ?.let { predicate(it, (context ?: ProcessingContext()).apply { put(REQUESTED_PSI_ELEMENT, o) }) }
      ?: false
    else -> false
  }

  private val condition = ElementPatternCondition(object : InitialPatternCondition<PsiElement>(PsiElement::class.java) {
    override fun accepts(o: Any?, context: ProcessingContext?): Boolean = this@UastPatternAdapter.accepts(o, context)
  })

  override fun getCondition(): ElementPatternCondition<PsiElement> = condition
}

fun ElementPattern<out UElement>.asPsiPattern(vararg supportedUElementTypes: Class<out UElement>): ElementPattern<PsiElement> = UastPatternAdapter(
  this::accepts,
  if (supportedUElementTypes.isNotEmpty()) supportedUElementTypes.toList() else listOf(UElement::class.java)
)

private class UastReferenceProviderAdapter(val provider: UastReferenceProvider) : PsiReferenceProvider() {
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