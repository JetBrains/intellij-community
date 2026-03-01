package com.jetbrains.python.psi.types

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiFile

class TypeEvalContextFactoryImpl : TypeEvalContextFactory {
  override fun codeCompletion(
    project: Project,
    origin: PsiFile?,
  ): TypeEvalContext {
    return getContextFromCache(project, TypeEvalContextImpl(true, true, true, origin))
  }

  override fun userInitiated(
    project: Project,
    origin: PsiFile?,
  ): TypeEvalContext {
    return getContextFromCache(project, TypeEvalContextImpl(true, true, false, origin))
  }

  override fun codeAnalysis(
    project: Project,
    origin: PsiFile?,
  ): TypeEvalContext {
    return getContextFromCache(project, buildCodeAnalysisContext(origin))
  }

  override fun codeInsightFallback(project: Project?): TypeEvalContext {
    val anchor = TypeEvalContextImpl(false, false, false, null)
    if (project != null) {
      return getContextFromCache(project, anchor)
    }
    return anchor
  }

  override fun deepCodeInsight(project: Project): TypeEvalContext {
    return getContextFromCache(project, TypeEvalContextImpl(false, true, false, null))
  }

  /**
   * Moves context through cache returning one from cache (if exists).
   *
   * @param project current project
   * @param context context to fetch from cache
   * @return context to use
   * @see TypeEvalContextCache.getContext
   */
  private fun getContextFromCache(project: Project, context: TypeEvalContext): TypeEvalContext {
    return project.service<TypeEvalContextCache>().getContext(context)
  }

  private fun buildCodeAnalysisContext(origin: PsiFile?): TypeEvalContext {
    if (Registry.`is`("python.optimized.type.eval.context")) {
      return TypeEvalContextImpl.OptimizedTypeEvalContext(false, false, false, origin)
    }
    return TypeEvalContextImpl(false, false, false, origin)
  }
}