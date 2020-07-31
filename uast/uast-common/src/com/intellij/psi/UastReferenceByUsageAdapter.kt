// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi

import com.intellij.codeInsight.completion.CompletionUtilCoreImpl
import com.intellij.lang.jvm.JvmModifier
import com.intellij.model.search.SearchService
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.util.Key
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.uast.injectionHostUExpression
import com.intellij.psi.impl.cache.CacheManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.PsiSearchHelper.SearchCostResult
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.ProcessingContext
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*

internal class UastReferenceByUsageAdapter(private val usagePattern: ElementPattern<out UElement>,
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
  it.uastParent is UVariable
}

@ApiStatus.Experimental
fun uExpressionInVariable() = injectionHostUExpression().filter {
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

  val cachedValue = CachedValuesManager.getManager(variablePsi.project).getCachedValue(variablePsi, CachedValueProvider {
    val anchors = findDirectVariableUsages(variablePsi).map(PsiAnchor::create)
    Result.createSingleDependency(anchors, PsiModificationTracker.MODIFICATION_COUNT)
  })
  return cachedValue.asSequence().mapNotNull(PsiAnchor::retrieve)
}

private const val MAX_FILES_TO_FIND_USAGES = 5
private val STRICT_CONSTANT_NAME_PATTERN = Regex("[\\p{Upper}_0-9]+")

private fun findDirectVariableUsages(variablePsi: PsiElement): Iterable<PsiElement> {
  val uVariable = variablePsi.toUElementOfType<UVariable>()
  val variableName = uVariable?.name
  if (variableName.isNullOrEmpty()) return emptyList()
  val currentFile = variablePsi.containingFile ?: return emptyList()

  val localUsages = findVariableUsages(variablePsi, variableName, arrayOf(currentFile))

  // non-local searches are limited for real-life use cases, we do not try to find all possible usages
  if (uVariable is ULocalVariable
      || (variablePsi is PsiModifierListOwner && variablePsi.hasModifier(JvmModifier.PRIVATE))
      || !STRICT_CONSTANT_NAME_PATTERN.matches(variableName)) {
    return localUsages
  }

  val module = ModuleUtilCore.findModuleForPsiElement(variablePsi) ?: return localUsages
  val uastScope = getUastScope(module.moduleScope)

  val searchHelper = PsiSearchHelper.getInstance(variablePsi.project)
  if (searchHelper.isCheapEnoughToSearch(variableName, uastScope, null, null) != SearchCostResult.FEW_OCCURRENCES) {
    return localUsages
  }

  val cacheManager = CacheManager.getInstance(variablePsi.project)
  val containingFiles = cacheManager.getVirtualFilesWithWord(
    variableName,
    UsageSearchContext.ANY,
    uastScope,
    true)
  val useScope = variablePsi.useScope

  val psiManager = PsiManager.getInstance(module.project)
  val filesToSearch = containingFiles.asSequence()
    .filter { useScope.contains(it) && it != currentFile.virtualFile }
    .mapNotNull { psiManager.findFile(it) }
    .sortedBy { it.virtualFile.canonicalPath }
    .take(MAX_FILES_TO_FIND_USAGES)
    .toList()
    .toTypedArray()

  val nonLocalUsages = findVariableUsages(variablePsi, variableName, filesToSearch)

  return ContainerUtil.concat(localUsages, nonLocalUsages)
}

private fun findVariableUsages(variablePsi: PsiElement, variableName: String, files: Array<PsiFile>): Collection<PsiElement> {
  if (files.isEmpty()) return emptyList()

  return SearchService.getInstance()
    .searchWord(variablePsi.project, variableName)
    .inScope(LocalSearchScope(files, null, true))
    .buildQuery { _, occurrencePsi, _ ->
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
}

private fun getUastScope(originalScope: GlobalSearchScope): GlobalSearchScope {
  val fileTypes = UastLanguagePlugin.getInstances().map { it.language.associatedFileType }.toTypedArray()
  return GlobalSearchScope.getScopeRestrictedByFileTypes(originalScope, *fileTypes)
}