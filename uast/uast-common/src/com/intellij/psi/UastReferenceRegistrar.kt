// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("UastReferenceRegistrar")

package com.intellij.psi

import com.intellij.codeInsight.completion.CompletionUtilCoreImpl
import com.intellij.model.search.SearchService
import com.intellij.openapi.util.Key
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.StandardPatterns
import com.intellij.patterns.uast.UElementPattern
import com.intellij.patterns.uast.injectionHostUExpression
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.ProcessingContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*
import org.jetbrains.uast.expressions.UInjectionHost

fun PsiReferenceRegistrar.registerUastReferenceProvider(pattern: (UElement, ProcessingContext) -> Boolean,
                                                        provider: UastReferenceProvider,
                                                        priority: Double = PsiReferenceRegistrar.DEFAULT_PRIORITY) {
  this.registerReferenceProvider(adaptPattern(pattern, provider.supportedUElementTypes),
                                 UastReferenceProviderAdapter(provider),
                                 priority)
}

fun PsiReferenceRegistrar.registerUastReferenceProvider(pattern: ElementPattern<out UElement>,
                                                        provider: UastReferenceProvider,
                                                        priority: Double = PsiReferenceRegistrar.DEFAULT_PRIORITY) {
  this.registerReferenceProvider(adaptPattern(pattern::accepts, provider.supportedUElementTypes),
                                 UastReferenceProviderAdapter(provider), priority)
}

fun uastInjectionHostReferenceProvider(provider: (UExpression, PsiLanguageInjectionHost) -> Array<PsiReference>): UastInjectionHostReferenceProvider =
  object : UastInjectionHostReferenceProvider() {

    override fun getReferencesForInjectionHost(uExpression: UExpression,
                                               host: PsiLanguageInjectionHost,
                                               context: ProcessingContext): Array<PsiReference> = provider(uExpression, host)

    override fun toString(): String = "uastInjectionHostReferenceProvider($provider)"
  }

fun <T : UElement> uastReferenceProvider(cls: Class<T>, provider: (T, PsiElement) -> Array<PsiReference>): UastReferenceProvider =
  object : UastReferenceProvider(cls) {

    override fun getReferencesByElement(element: UElement, context: ProcessingContext): Array<PsiReference> =
      provider(cls.cast(element), context[REQUESTED_PSI_ELEMENT])

    override fun toString(): String = "uastReferenceProvider($provider)"
  }

inline fun <reified T : UElement> uastReferenceProvider(noinline provider: (T, PsiElement) -> Array<PsiReference>): UastReferenceProvider =
  uastReferenceProvider(T::class.java, provider)

private val cachedUElement = Key.create<UElement>("UastReferenceRegistrar.cachedUElement")
internal val REQUESTED_PSI_ELEMENT = Key.create<PsiElement>("REQUESTED_PSI_ELEMENT")
internal val USAGE_PSI_ELEMENT = Key.create<PsiElement>("USAGE_PSI_ELEMENT")

internal fun getOrCreateCachedElement(element: PsiElement,
                                      context: ProcessingContext?,
                                      supportedUElementTypes: List<Class<out UElement>>): UElement? =
  element as? UElement ?: context?.get(cachedUElement) ?: supportedUElementTypes.asSequence().mapNotNull {
    element.toUElement(it)
  }.firstOrNull()?.also { context?.put(cachedUElement, it) }

private fun adaptPattern(
  predicate: (UElement, ProcessingContext) -> Boolean,
  supportedUElementTypes: List<Class<out UElement>>
): ElementPattern<out PsiElement> {
  val uastPatternAdapter = UastPatternAdapter(predicate, supportedUElementTypes)

  // optimisation until IDEA-211738 is implemented
  if (supportedUElementTypes == listOf(UInjectionHost::class.java)) {
    return StandardPatterns.instanceOf(PsiLanguageInjectionHost::class.java).and(uastPatternAdapter)
  }

  return uastPatternAdapter
}

fun ElementPattern<out UElement>.asPsiPattern(vararg supportedUElementTypes: Class<out UElement>): ElementPattern<PsiElement> = UastPatternAdapter(
  this::accepts,
  if (supportedUElementTypes.isNotEmpty()) supportedUElementTypes.toList() else listOf(UElement::class.java)
)

/**
 * Creates UAST reference provider that accepts additional PSI element that could be either the same as reference PSI element or reference
 * element that is used in the same file and satisfy usage pattern.
 *
 * @see registerReferenceProviderByUsage
 */
@ApiStatus.Experimental
fun uastReferenceProviderByUsage(provider: (UExpression, referencePsi: PsiLanguageInjectionHost, usagePsi: PsiElement) -> Array<PsiReference>): UastReferenceProvider =
  object : UastReferenceProvider(UInjectionHost::class.java) {

    override fun getReferencesByElement(element: UElement, context: ProcessingContext): Array<PsiReference> {
      val uLiteral = element as? UExpression ?: return PsiReference.EMPTY_ARRAY
      val host = context[REQUESTED_PSI_ELEMENT] as? PsiLanguageInjectionHost ?: return PsiReference.EMPTY_ARRAY
      val usagePsi = context[USAGE_PSI_ELEMENT] ?: context[REQUESTED_PSI_ELEMENT]

      return provider(uLiteral, host, usagePsi)
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
fun PsiReferenceRegistrar.registerReferenceProviderByUsage(expressionPattern: UElementPattern<*, *>,
                                                           usagePattern: UElementPattern<*, *>,
                                                           provider: UastReferenceProvider,
                                                           priority: Double = PsiReferenceRegistrar.DEFAULT_PRIORITY) {
  this.registerUastReferenceProvider(usagePattern, provider, priority)

  this.registerUastReferenceProvider(expressionPattern, object : UastReferenceProvider(UExpression::class.java) {
    override fun acceptsTarget(target: PsiElement): Boolean {
      return !target.project.isDefault && provider.acceptsTarget(target)
    }

    override fun getReferencesByElement(element: UElement, context: ProcessingContext): Array<PsiReference> {
      val sourcePsi = element.sourcePsi ?: return PsiReference.EMPTY_ARRAY
      val originalUElement = (CompletionUtilCoreImpl.getOriginalElement(sourcePsi) ?: sourcePsi).toUElement() ?: return PsiReference.EMPTY_ARRAY

      val parentVariable = when (val uastParent = originalUElement.uastParent) {
        is UVariable -> uastParent
        is UPolyadicExpression -> uastParent.uastParent as? UVariable // support .withUastParentOrSelf() patterns
        else -> null
      }

      if (parentVariable == null
          || parentVariable.name.isNullOrEmpty()
          || !parentVariable.type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        return PsiReference.EMPTY_ARRAY
      }

      val usage = getDirectVariableUsages(parentVariable).find { usage ->
        val refExpression = usage.toUElementOfType<UReferenceExpression>()
        refExpression != null && usagePattern.accepts(refExpression, context)
      } ?: return PsiReference.EMPTY_ARRAY

      context.put(USAGE_PSI_ELEMENT, usage)

      return provider.getReferencesByElement(originalUElement, context)
    }

    override fun toString(): String = "uastReferenceByUsageAdapter($provider)"
  }, priority)
}

@ApiStatus.Experimental
fun uInjectionHostInVariable() = injectionHostUExpression().filter {
  val uastParent = it.uastParent ?: getOriginalUastParent(it)
  uastParent is UVariable
}

@ApiStatus.Experimental
fun uExpressionInVariable() = injectionHostUExpression().filter {
  val uastParent = it.uastParent ?: getOriginalUastParent(it)
  uastParent is UVariable || (uastParent is UPolyadicExpression && uastParent.uastParent is UVariable)
}

private fun getOriginalUastParent(element: UElement): UElement? {
  // Kotlin sends non-original element on completion
  val src = element.sourcePsi ?: return null
  val original = CompletionUtilCoreImpl.getOriginalElement(src) ?: src
  return original.toUElement()?.uastParent
}

private fun getDirectVariableUsages(uVar: UVariable): Collection<PsiElement> {
  val variablePsi = uVar.sourcePsi ?: return emptyList()
  return CachedValuesManager.getManager(variablePsi.project).getCachedValue(variablePsi, CachedValueProvider {
    Result.createSingleDependency(findDirectVariableUsages(variablePsi), PsiModificationTracker.MODIFICATION_COUNT)
  })
}

private fun findDirectVariableUsages(variablePsi: PsiElement): Collection<PsiElement> {
  val variableName = variablePsi.toUElementOfType<UVariable>()?.name
  if (variableName.isNullOrEmpty()) return emptyList()
  val file = variablePsi.containingFile ?: return emptyList()

  return SearchService.getInstance()
    .searchWord(variablePsi.project, variableName)
    .inScope(LocalSearchScope(arrayOf(file), null, true))
    .buildQuery { _, occurrencePsi, _ ->
      // we get identifiers and need to process their parents, see IDEA-232166
      val uRef = occurrencePsi.parent.findContaining(UReferenceExpression::class.java)
      val expressionType = uRef?.getExpressionType()
      if (expressionType != null && expressionType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        val occurrenceResolved = uRef.tryResolve().toUElement()?.sourcePsi
        if (occurrenceResolved != null
            && PsiManager.getInstance(occurrencePsi.project).areElementsEquivalent(occurrenceResolved, variablePsi)) {
          return@buildQuery listOfNotNull(uRef.sourcePsi)
        }
      }
      emptyList<PsiElement>()
    }.findAll()
}