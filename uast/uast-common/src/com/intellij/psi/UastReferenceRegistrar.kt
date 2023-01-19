// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("UastReferenceRegistrar")

package com.intellij.psi

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.StandardPatterns
import com.intellij.util.ProcessingContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.expressions.UInjectionHost

/**
 * Groups all UAST-based reference providers by chunks with the same priority and supported UElement types.
 */
fun PsiReferenceRegistrar.registerUastReferenceProvider(pattern: (UElement, ProcessingContext) -> Boolean,
                                                        provider: UastReferenceProvider,
                                                        priority: Double = PsiReferenceRegistrar.DEFAULT_PRIORITY) {
  val adapter = UastReferenceProviderAdapter(provider.supportedUElementTypes, provider)
  this.registerReferenceProvider(adaptPattern(pattern, provider.supportedUElementTypes), adapter, priority)
}

fun PsiReferenceRegistrar.registerUastReferenceProvider(pattern: ElementPattern<out UElement>,
                                                        provider: UastReferenceProvider,
                                                        priority: Double = PsiReferenceRegistrar.DEFAULT_PRIORITY) {
  this.registerUastReferenceProvider(pattern::accepts, provider, priority)
}

fun uastInjectionHostReferenceProvider(provider: (UExpression, PsiLanguageInjectionHost) -> Array<PsiReference>): UastInjectionHostReferenceProvider =
  uastInjectionHostReferenceProvider(null, provider)

fun uastInjectionHostReferenceProvider(targetClass: Class<out PsiElement>?,
                                       provider: (UExpression, PsiLanguageInjectionHost) -> Array<PsiReference>): UastInjectionHostReferenceProvider =
  object : UastInjectionHostReferenceProvider() {
    override fun getReferencesForInjectionHost(uExpression: UExpression,
                                               host: PsiLanguageInjectionHost,
                                               context: ProcessingContext): Array<PsiReference> = provider(uExpression, host)

    override fun acceptsTarget(target: PsiElement): Boolean {
      return targetClass?.isInstance(target) ?: true
    }

    override fun toString(): String = "uastInjectionHostReferenceProvider($provider)"
  }

fun <T : UElement> uastReferenceProvider(cls: Class<T>, targetClass: Class<out PsiElement>?,
                                         provider: (T, PsiElement) -> Array<PsiReference>): UastReferenceProvider =
  object : UastReferenceProvider(cls) {
    override fun getReferencesByElement(element: UElement, context: ProcessingContext): Array<PsiReference> {
      return provider(cls.cast(element), getRequestedPsiElement(context))
    }

    override fun acceptsTarget(target: PsiElement): Boolean {
      return targetClass?.isInstance(target) ?: true
    }

    override fun toString(): String = "uastReferenceProvider($provider)"
  }

fun getRequestedPsiElement(context: ProcessingContext): PsiElement {
  return context[REQUESTED_PSI_ELEMENT]
}

fun <T : UElement> uastReferenceProvider(cls: Class<T>, provider: (T, PsiElement) -> Array<PsiReference>): UastReferenceProvider =
  uastReferenceProvider(cls, null, provider)

inline fun <reified T : UElement> uastReferenceProvider(noinline provider: (T, PsiElement) -> Array<PsiReference>): UastReferenceProvider =
  uastReferenceProvider(T::class.java, provider)

internal val REQUESTED_PSI_ELEMENT: Key<PsiElement> = Key.create("REQUESTED_PSI_ELEMENT")
internal val USAGE_PSI_ELEMENT: Key<PsiElement> = Key.create("USAGE_PSI_ELEMENT")

internal fun adaptPattern(pattern: (UElement, ProcessingContext) -> Boolean,
                          supportedUElementTypes: List<Class<out UElement>>): ElementPattern<out PsiElement> {
  val uastPatternAdapter = UastPatternAdapter(pattern, supportedUElementTypes)

  // optimisation until IDEA-211738 is implemented
  if (supportedUElementTypes.size == 1 && supportedUElementTypes[0] == UInjectionHost::class.java) {
    return StandardPatterns.instanceOf(PsiLanguageInjectionHost::class.java).and(uastPatternAdapter)
  }

  return uastPatternAdapter
}

@ApiStatus.Experimental
fun uastReferenceProviderByUsage(provider: (UExpression, referencePsi: PsiLanguageInjectionHost, usagePsi: PsiElement) -> Array<PsiReference>): UastReferenceProvider =
  uastReferenceProviderByUsage(null, provider)

/**
 * Creates UAST reference provider that accepts additional PSI element that could be either the same as reference PSI element or reference
 * element that is used in the same file and satisfy usage pattern.
 *
 * @see registerReferenceProviderByUsage
 */
@ApiStatus.Experimental
fun uastReferenceProviderByUsage(targetClass: Class<out PsiElement>?,
                                 provider: (UExpression, referencePsi: PsiLanguageInjectionHost, usagePsi: PsiElement) -> Array<PsiReference>): UastReferenceProvider =
  object : UastReferenceProvider(UInjectionHost::class.java) {
    override fun getReferencesByElement(element: UElement, context: ProcessingContext): Array<PsiReference> {
      val uLiteral = element as? UExpression ?: return PsiReference.EMPTY_ARRAY
      val host = getRequestedPsiElement(context) as? PsiLanguageInjectionHost ?: return PsiReference.EMPTY_ARRAY
      val usagePsi = context[USAGE_PSI_ELEMENT] ?: getRequestedPsiElement(context)

      return provider(uLiteral, host, usagePsi)
    }

    override fun acceptsTarget(target: PsiElement): Boolean {
      return targetClass?.isInstance(target) ?: true
    }

    override fun toString(): String = "uastByUsageReferenceProvider($provider)"
  }

/**
 * Registers a provider that will be called on the expressions that directly satisfy the [usagePattern] or at least one of the expression
 * usages satisfies the pattern if it was assigned to a variable. The provider will search for usages of variables only for expressions that
 * satisfy [expressionPattern]. There are standard expression patterns for usage search: [uInjectionHostInVariable] and [uExpressionInVariable].
 *
 * Consider using [uastReferenceProviderByUsage] if you need to obtain additional context from a usage place.
 */
@ApiStatus.Experimental
@JvmOverloads
fun PsiReferenceRegistrar.registerReferenceProviderByUsage(expressionPattern: ElementPattern<out UElement>,
                                                           usagePattern: ElementPattern<out UElement>,
                                                           provider: UastReferenceProvider,
                                                           priority: Double = PsiReferenceRegistrar.DEFAULT_PRIORITY) {
  this.registerUastReferenceProvider(usagePattern, provider, priority)

  if (Registry.`is`("uast.references.by.usage", true)) {
    val adapter = UastReferenceByUsageAdapter(expressionPattern, usagePattern, provider)
    this.registerReferenceProvider(adaptPattern(expressionPattern::accepts, adapter.supportedUElementTypes), adapter, priority)
  }
}

@ApiStatus.Experimental
fun PsiReferenceRegistrar.registerReferenceProviderByUsage(usagePattern: ElementPattern<out UElement>,
                                                           provider: UastReferenceProvider,
                                                           priority: Double = PsiReferenceRegistrar.DEFAULT_PRIORITY) {
  registerReferenceProviderByUsage(uExpressionInVariable(), usagePattern, provider, priority)
}
