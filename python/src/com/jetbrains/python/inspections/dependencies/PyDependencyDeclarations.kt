// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.dependencies

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.jetbrains.python.packaging.PyRequirement
import org.jetbrains.annotations.ApiStatus

/**
 * Declared top-level dependencies with the PSI element declaring each, across every
 * [DependenciesPsiProvider]. Injection-aware, interpreter-free — the engine behind [DependenciesInspection].
 */
@ApiStatus.Internal
object PyDependencyDeclarations {
  /** Returned [PsiElement]s are live; anchor them to outlive the read action. */
  @RequiresReadLock
  fun forFile(file: PsiFile): DependencyMap = collect(file) { _, _ -> }

  // [onNonInjectedProvider] lets the inspection run its empty-file check without a second traversal.
  @RequiresReadLock
  internal fun collect(
    rootFile: PsiFile,
    onNonInjectedProvider: (PsiFile, DependenciesPsiProvider<*>) -> Unit,
  ): DependencyMap {
    val injectedLanguageManager = InjectedLanguageManager.getInstance(rootFile.project)
    val dependencyMap = mutableMapOf<PyRequirement, PsiElement>()

    PsiTreeUtil.processElements(rootFile) { element ->
      val resolvedFile = resolvePsiFile(injectedLanguageManager, element)
      val file = when (resolvedFile) {
        is ResolvedPsiFile.File -> resolvedFile.file
        is ResolvedPsiFile.InjectedFile -> resolvedFile.file
        ResolvedPsiFile.NonFile -> return@processElements true
      }
      val eligibleProviders = DependenciesPsiProviderData.dependenciesForFile(file) ?: return@processElements true

      for ((provider, dependencies) in eligibleProviders) {
        if (!resolvedFile.isInjected) onNonInjectedProvider(file, provider)
        for ((pyRequirement, psiElement) in dependencies) {
          // injected fragment -> report the host element so it can be highlighted in the outer file
          dependencyMap[pyRequirement] = if (!resolvedFile.isInjected) psiElement else element
        }
      }

      true
    }

    return dependencyMap
  }
}
