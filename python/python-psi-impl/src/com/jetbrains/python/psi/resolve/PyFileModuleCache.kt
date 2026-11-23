// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.resolve

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.ParameterizedCachedValue
import com.intellij.psi.util.ParameterizedCachedValueProvider
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.annotations.ApiStatus

private val MODULE_FOR_FILE_SYSTEM_ITEM_KEY = Key.create<ParameterizedCachedValue<Module?, PsiElement>>("Python.moduleForFileSystemItem")
private val MODULE_FOR_ELEMENT_KEY = Key.create<ParameterizedCachedValue<Module?, PsiElement>>("Python.moduleForElement")
private val MODULE_FOR_ELEMENT_PROVIDER = ParameterizedCachedValueProvider<Module?, PsiElement> { element ->
  CachedValueProvider.Result.create(
    ModuleUtilCore.findModuleForPsiElement(element),
    PsiModificationTracker.MODIFICATION_COUNT,
    ProjectRootManager.getInstance(element.project),
  )
}

/** Finds the module for [element] and caches it until PSI or project roots change. */
@ApiStatus.Internal
fun findModuleForPsiElementCached(element: PsiElement): Module? {
  if (!element.isValid) return ModuleUtilCore.findModuleForPsiElement(element)

  val cacheHolder = (element as? PsiFileSystemItem) ?: element.containingFile
    ?: return ModuleUtilCore.findModuleForPsiElement(element)
  val key = if (element is PsiFileSystemItem) MODULE_FOR_FILE_SYSTEM_ITEM_KEY else MODULE_FOR_ELEMENT_KEY
  return CachedValuesManager.getManager(element.project).getParameterizedCachedValue(cacheHolder, key, MODULE_FOR_ELEMENT_PROVIDER, false, element)
}