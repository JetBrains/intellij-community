// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("UastReferenceByUsageResolver")

package com.intellij.psi

import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbService
import com.intellij.psi.impl.cache.CacheManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastLanguagePlugin
import org.jetbrains.uast.toUElementOfType

private const val MAX_FILES_TO_FIND_USAGES: Int = 5

/**
 * Searches for direct variable usages not only in the same file but also in the module.
 * Must be used only from [PsiReference.resolve], do not call from [PsiReferenceProvider.getReferencesByElement].
 */
@ApiStatus.Experimental
internal fun getDirectVariableUsagesWithNonLocal(uVar: UVariable): Sequence<PsiElement> {
  val variablePsi = uVar.sourcePsi ?: return emptySequence()
  val project = variablePsi.project

  if (DumbService.isDumb(project)) return emptySequence() // do not try to search in dumb mode

  val cachedValue = CachedValuesManager.getManager(project).getCachedValue(variablePsi, CachedValueProvider {
    val anchors = findAllDirectVariableUsages(variablePsi).map(PsiAnchor::create)
    CachedValueProvider.Result.createSingleDependency(anchors, PsiModificationTracker.MODIFICATION_COUNT)
  })
  return cachedValue.asSequence().mapNotNull(PsiAnchor::retrieve)
}

private fun findAllDirectVariableUsages(variablePsi: PsiElement): Iterable<PsiElement> {
  val uVariable = variablePsi.toUElementOfType<UVariable>()
  val variableName = uVariable?.name
  if (variableName.isNullOrEmpty()) return emptyList()
  val currentFile = variablePsi.containingFile ?: return emptyList()

  val localUsages = findReferencedVariableUsages(variablePsi, variableName, arrayOf(currentFile))

  // non-local searches are limited for real-life use cases, we do not try to find all possible usages
  if (uVariable is ULocalVariable
      || (variablePsi is PsiModifierListOwner && variablePsi.hasModifier(JvmModifier.PRIVATE))
      || !STRICT_CONSTANT_NAME_PATTERN.matches(variableName)) {
    return localUsages
  }

  val module = ModuleUtilCore.findModuleForPsiElement(variablePsi) ?: return localUsages
  val uastScope = getUastScope(module.moduleScope)

  val searchHelper = PsiSearchHelper.getInstance(module.project)
  if (searchHelper.isCheapEnoughToSearch(variableName, uastScope, currentFile, null) != PsiSearchHelper.SearchCostResult.FEW_OCCURRENCES) {
    return localUsages
  }

  val cacheManager = CacheManager.getInstance(variablePsi.project)
  val containingFiles = cacheManager.getVirtualFilesWithWord(
    variableName,
    UsageSearchContext.IN_CODE,
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

  val nonLocalUsages = findReferencedVariableUsages(variablePsi, variableName, filesToSearch)

  return ContainerUtil.concat(localUsages, nonLocalUsages)
}

private fun getUastScope(originalScope: GlobalSearchScope): GlobalSearchScope {
  val fileTypes = UastLanguagePlugin.getInstances().map { it.language.associatedFileType }.toTypedArray()
  return GlobalSearchScope.getScopeRestrictedByFileTypes(originalScope, *fileTypes)
}
