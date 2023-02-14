// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi

import com.intellij.codeInsight.completion.CompletionUtilCoreImpl
import com.intellij.lang.Language
import com.intellij.model.search.SearchService
import com.intellij.openapi.progress.impl.CancellationCheck
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Key
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.uast.UExpressionPattern
import com.intellij.patterns.uast.injectionHostUExpression
import com.intellij.psi.UastPatternAdapter.Companion.getOrCreateCachedElement
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceRegistrarImpl
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.ProcessingContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*
import org.jetbrains.uast.expressions.UInjectionHost

class UastReferenceByUsageAdapter(
  val expressionPattern: ElementPattern<out UElement>,
  val usagePattern: ElementPattern<out UElement>,
  val provider: UastReferenceProvider
) : PsiReferenceProvider() {

  val supportedUElementTypes: List<Class<UExpression>> = listOf(UExpression::class.java)

  override fun acceptsTarget(target: PsiElement): Boolean {
    return provider.acceptsTarget(target)
  }

  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
    val uElement = getOrCreateCachedElement(element, context, supportedUElementTypes) ?: return PsiReference.EMPTY_ARRAY
    return CancellationCheck.runWithCancellationCheck { getReferencesByElement(uElement, context) }
  }

  internal fun getReferencesByElement(element: UElement, context: ProcessingContext): Array<PsiReference> {
    val parentVariable = getUsageVariableTargetForInitializer(element) ?: return PsiReference.EMPTY_ARRAY

    val usage = getDirectVariableUsages(parentVariable).find { usage ->
      val refExpression = getUsageReferenceExpressionWithCache(usage, context)
      refExpression != null && usagePattern.accepts(refExpression, context)
    } ?: return PsiReference.EMPTY_ARRAY

    context.put(USAGE_PSI_ELEMENT, usage)

    return provider.getReferencesByElement(element, context)
  }

  override fun toString(): String = "uastReferenceByUsageAdapter($provider)"
}

private fun getUsageVariableTargetForInitializer(element: UElement): UVariable? {
  val parentVariable = when (val uastParent = getOriginalUastParent(element)) {
    is UVariable -> uastParent
    is UPolyadicExpression -> uastParent.uastParent as? UVariable // support .withUastParentOrSelf() patterns
    else -> null
  }

  if (parentVariable == null
      || parentVariable.name.isNullOrEmpty()
      || !parentVariable.type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
    return null
  }

  return parentVariable
}

/**
 * Find usages of variable or constant in the scope of module.
 *
 * @param element initializer of variable, String literal
 * @param targetHint target hint, usually POM target element
 * @return references that are supposed to be created in usage places
 */
@ApiStatus.Internal
fun getReferencesForDirectUsagesOfVariable(element: PsiElement, targetHint: PsiElement?): Array<PsiReference> {
  val uElement = element.toUElementOfType<UInjectionHost>() ?: return PsiReference.EMPTY_ARRAY
  val originalElement = CompletionUtilCoreImpl.getOriginalElement(element) ?: element
  val uParentVariable = getUsageVariableTargetForInitializer(uElement) ?: return PsiReference.EMPTY_ARRAY

  val registrar = ReferenceProvidersRegistry.getInstance().getRegistrar(Language.findLanguageByID("UAST")!!)
  val providerInfos = (registrar as PsiReferenceRegistrarImpl).getPairsByElement(originalElement, PsiReferenceService.Hints(targetHint, null))

  // by-usage providers must implement acceptsTarget correctly, we rely on fact that they accept targetHint
  val suitableProviders = providerInfos.asSequence()
    .map { it.provider }
    .filterIsInstance<UastReferenceByUsageAdapter>()
    .toList()

  val usages = getDirectVariableUsagesWithNonLocal(uParentVariable)
  for (usage in usages) {
    val refExpression = usage.toUElementOfType<UReferenceExpression>()

    if (refExpression != null) {
      val context = ProcessingContext()
      context.put(USAGE_PSI_ELEMENT, usage)
      context.put(REQUESTED_PSI_ELEMENT, originalElement)

      for (provider in suitableProviders) {
        if (provider.usagePattern.accepts(refExpression, context)) {
          val references = provider.provider.getReferencesByElement(refExpression, context)

          if (references.isNotEmpty()) {
            return references
          }
        }
      }
    }
  }

  return PsiReference.EMPTY_ARRAY
}

internal val STRICT_CONSTANT_NAME_PATTERN: Regex = Regex("[\\p{Upper}_0-9]+")

@ApiStatus.Experimental
fun uInjectionHostInVariable(): UExpressionPattern<*, *> = injectionHostUExpression().filter {
  it.uastParent is UVariable
}

@ApiStatus.Experimental
fun uInjectionHostInStrictConstant(): UExpressionPattern<*, *> = injectionHostUExpression().filter {
  val uastParent = it.uastParent
  uastParent is UVariable && uastParent.name?.matches(STRICT_CONSTANT_NAME_PATTERN) == true
}

@ApiStatus.Experimental
fun uExpressionInVariable(): UExpressionPattern<*, *> = injectionHostUExpression().filter {
  val parent = it.uastParent
  parent is UVariable || (parent is UPolyadicExpression && parent.uastParent is UVariable)
}

private val USAGE_REFERENCE_EXPRESSION: Key<UReferenceExpression> = Key.create("uast.referenceExpressions.byUsage")

private fun getUsageReferenceExpressionWithCache(usage: PsiElement, context: ProcessingContext): UReferenceExpression? {
  val cachedElement = context.sharedContext.get(USAGE_REFERENCE_EXPRESSION, usage)
  if (cachedElement != null) return cachedElement

  val newElement = usage.toUElementOfType<UReferenceExpression>()
  if (newElement != null) {
    context.sharedContext.put(USAGE_REFERENCE_EXPRESSION, usage, newElement)
  }
  return newElement
}

private fun getOriginalUastParent(element: UElement): UElement? {
  // Kotlin sends non-original element on completion
  val src = element.sourcePsi ?: return null
  val original = CompletionUtilCoreImpl.getOriginalElement(src) ?: src
  return original.toUElement()?.uastParent
}

private fun getDirectVariableUsages(uVar: UVariable): Sequence<PsiElement> {
  val variablePsi = uVar.sourcePsi ?: return emptySequence()
  val project = variablePsi.project

  if (DumbService.isDumb(project)) return emptySequence() // do not try to search in dumb mode

  val cachedValue = CachedValuesManager.getManager(project).getCachedValue(variablePsi, CachedValueProvider {
    val anchors = findLocalDirectVariableUsages(variablePsi).map(PsiAnchor::create)
    Result.createSingleDependency(anchors, PsiModificationTracker.MODIFICATION_COUNT)
  })
  return cachedValue.asSequence().mapNotNull(PsiAnchor::retrieve)
}

private fun findLocalDirectVariableUsages(variablePsi: PsiElement): Iterable<PsiElement> {
  val uVariable = variablePsi.toUElementOfType<UVariable>()
  val variableName = uVariable?.name
  if (variableName.isNullOrEmpty()) return emptyList()
  val currentFile = variablePsi.containingFile ?: return emptyList()

  return findReferencedVariableUsages(variablePsi, variableName, arrayOf(currentFile))
}

internal fun findReferencedVariableUsages(variablePsi: PsiElement, variableName: String, files: Array<PsiFile>): List<PsiElement> {
  if (files.isEmpty()) return emptyList()

  return SearchService.getInstance()
    .searchWord(variablePsi.project, variableName)
    .inScope(LocalSearchScope(files, null, true))
    .buildQuery { (_, occurrencePsi, _) ->
      val uRef = occurrencePsi.findContaining(UReferenceExpression::class.java)
      val expressionType = uRef?.getExpressionType()
      if (expressionType != null && expressionType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        val occurrenceResolved = uRef.tryResolve()
        if (occurrenceResolved != null
            && PsiManager.getInstance(occurrencePsi.project).areElementsEquivalent(occurrenceResolved, variablePsi)) {
          return@buildQuery listOfNotNull(uRef.sourcePsi)
        }
      }
      emptyList<PsiElement>()
    }
    .findAll()
    .sortedWith(compareBy({ it.containingFile?.virtualFile?.canonicalPath ?: "" }, { it.textOffset }))
}