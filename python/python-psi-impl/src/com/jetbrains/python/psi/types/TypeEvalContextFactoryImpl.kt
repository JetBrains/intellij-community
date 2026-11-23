// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class TypeEvalContextFactoryImpl : TypeEvalContextFactory {
  override fun codeCompletion(
    project: Project,
    origin: PsiFile?,
  ): TypeEvalContext {
    val constraints = TypeEvalConstraints(true, true, true, false, origin)
    return getContextFromCache(project, constraints) { TypeEvalContextImpl(constraints) }
  }

  override fun userInitiated(
    project: Project,
    origin: PsiFile?,
  ): TypeEvalContext {
    val constraints = TypeEvalConstraints(true, true, false, false, origin)
    return getContextFromCache(project, constraints) { TypeEvalContextImpl(constraints) }
  }

  override fun codeAnalysis(
    project: Project,
    origin: PsiFile?,
  ): TypeEvalContext {
    // The registry key selects only the class that a miss builds. Both classes have the same constraints.
    return getContextFromCache(project, TypeEvalConstraints(false, false, false, false, origin)) {
      buildCodeAnalysisContext(origin)
    }
  }

  override fun codeInsightFallback(project: Project?): TypeEvalContext {
    val constraints = TypeEvalConstraints(false, false, false, false, null)
    if (project != null) {
      return getContextFromCache(project, constraints) { TypeEvalContextImpl(constraints) }
    }
    return TypeEvalContextImpl(constraints)
  }

  override fun externalContext(project: Project): TypeEvalContext {
    val constraints = TypeEvalConstraints(false, false, false, true, null)
    return getContextFromCache(project, constraints) { TypeEvalContextImpl(constraints) }
  }

  override fun deepCodeInsight(project: Project): TypeEvalContext {
    val constraints = TypeEvalConstraints(false, true, false, false, null)
    return getContextFromCache(project, constraints) { TypeEvalContextImpl(constraints) }
  }

  /**
   * Returns the context the cache holds for these constraints, building one with [build] only if it holds none.
   *
   * @param project current project
   * @param constraints the constraints of the context wanted, which are also its key in the cache
   * @param build builds the context, called only on a miss
   * @return context to use
   * @see TypeEvalContextCache.getCachedContext
   */
  private inline fun getContextFromCache(
    project: Project,
    constraints: TypeEvalConstraints,
    build: () -> TypeEvalContext,
  ): TypeEvalContext {
    val cache = project.service<TypeEvalContextCache>()
    return cache.getCachedContext(constraints) ?: cache.getContext(build())
  }

  private fun buildCodeAnalysisContext(origin: PsiFile?): TypeEvalContext {
    if (Registry.`is`("python.optimized.type.eval.context")) {
      return TypeEvalContextImpl.OptimizedTypeEvalContext(false, false, false, origin)
    }
    return TypeEvalContextImpl(false, false, false, false, origin)
  }
}