// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi

import com.intellij.codeInsight.completion.CompletionUtilCoreImpl
import com.intellij.model.search.SearchService
import com.intellij.openapi.util.Key
import com.intellij.patterns.uast.UElementPattern
import com.intellij.patterns.uast.injectionHostUExpression
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.ProcessingContext
import gnu.trove.THashMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*

internal class UastReferenceByUsageAdapter(private val usagePattern: UElementPattern<*, *>,
                                           private val provider: UastReferenceProvider) : UastReferenceProvider(UExpression::class.java) {

  override fun acceptsTarget(target: PsiElement): Boolean {
    return !target.project.isDefault && provider.acceptsTarget(target)
  }

  override fun getReferencesByElement(element: UElement, context: ProcessingContext): Array<PsiReference> {
    val parentVariable = when (val uastParent = getOriginalUastParent(element)) {
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
      val refExpression = getUsageReferenceExpressionWithCache(usage, context)
      refExpression != null && usagePattern.accepts(refExpression, context)
    } ?: return PsiReference.EMPTY_ARRAY

    context.put(USAGE_PSI_ELEMENT, usage)

    return provider.getReferencesByElement(element, context)
  }

  override fun toString(): String = "uastReferenceByUsageAdapter($provider)"
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

private val USAGE_REFERENCE_EXPRESSIONS = Key.create<MutableMap<PsiElement, UReferenceExpression>>("uast.referenceExpressions.byUsage")

private fun getUsageReferenceExpressionWithCache(usage: PsiElement, context: ProcessingContext): UReferenceExpression? {
  var cache = context.get(USAGE_REFERENCE_EXPRESSIONS)
  if (cache == null) {
    cache = THashMap()
    context.put(USAGE_REFERENCE_EXPRESSIONS, cache)
  }
  val cachedElement = cache[usage]
  if (cachedElement != null) return cachedElement

  val newElement = usage.toUElementOfType<UReferenceExpression>()
  if (newElement != null) {
    cache[usage] = newElement
  }
  return newElement
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
    CachedValueProvider.Result.createSingleDependency(findDirectVariableUsages(variablePsi), PsiModificationTracker.MODIFICATION_COUNT)
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
        val occurrenceResolved = uRef.tryResolve()
        if (occurrenceResolved != null
            && PsiManager.getInstance(occurrencePsi.project).areElementsEquivalent(occurrenceResolved, variablePsi)) {
          return@buildQuery listOfNotNull(uRef.sourcePsi)
        }
      }
      emptyList<PsiElement>()
    }.findAll()
}