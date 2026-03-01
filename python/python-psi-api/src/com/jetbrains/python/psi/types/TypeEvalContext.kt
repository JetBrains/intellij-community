// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.ProcessingContext
import com.jetbrains.python.psi.PyCallable
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyTypedElement
import org.jetbrains.annotations.ApiStatus

abstract class TypeEvalContext protected constructor() {
  abstract val usesExternalTypeProvider: Boolean
  abstract val processingContext: ProcessingContext
  abstract val origin: PsiFile?

  abstract fun allowDataFlow(element: PsiElement): Boolean
  abstract fun allowReturnTypes(element: PsiElement): Boolean
  abstract fun allowCallContext(element: PsiElement): Boolean
  abstract fun maySwitchToAST(element: PsiElement): Boolean
  abstract fun withTracing(): TypeEvalContext

  abstract fun trace(message: String, vararg args: Any?)

  abstract fun traceIndent()

  abstract fun traceUnindent()
  abstract fun printTrace(): String
  abstract fun tracing(): Boolean

  @ApiStatus.Internal
  abstract fun <R> assumeType(element: PyTypedElement, type: PyType?, func: (TypeEvalContext?) -> R): R?

  @ApiStatus.Internal
  abstract fun hasAssumptions(): Boolean

  @ApiStatus.Internal
  abstract fun isKnown(element: PyTypedElement): Boolean

  abstract fun getType(element: PyTypedElement): PyType?

  abstract fun getReturnType(callable: PyCallable): PyType?

  @ApiStatus.Internal
  abstract fun getContextTypeCache(): MutableMap<Pair<PyExpression?, Any?>, PyType?>
  abstract fun getKnownType(element: PyTypedElement): PyType?
  abstract fun getKnownReturnType(callable: PyCallable): PyType?

  /**
   * This class ensures that only [TypeEvalContext] instances can directly invoke
   * [PyTypedElement.getType] and everybody else has to
   * access its result though [.getType] or [.getReturnType].
   * Hence, the inferred type information cannot bypass caching in [TypeEvalContext].
   */
  sealed class Key

  private object KeyImpl : Key()

  protected fun getKey(): Key = KeyImpl

  @ApiStatus.Internal
  companion object {
    protected val logger: Logger = logger<TypeEvalContext>()

    /**
     * Create a context for code completion.
     *
     *
     * It is as detailed as [TypeEvalContext.userInitiated], but allows inferring types based on the context in which
     * the analyzed code was called or may be called. Since this is basically guesswork, the results should be used only for code completion.
     */
    @JvmStatic
    fun codeCompletion(project: Project, origin: PsiFile?): TypeEvalContext {
      return TypeEvalContextFactory.getInstance().codeCompletion(project, origin)
    }

    /**
     * Create the most detailed type evaluation context for user-initiated actions.
     *
     *
     * Should be used go to definition, find usages, refactorings, documentation.
     *
     *
     * For code completion see [TypeEvalContext.codeCompletion].
     */
    @JvmStatic
    fun userInitiated(project: Project, origin: PsiFile?): TypeEvalContext {
      return TypeEvalContextFactory.getInstance().userInitiated(project, origin)
    }

    /**
     * Create a type evaluation context for performing analysis operations on the specified file which is currently open in the editor,
     * without accessing stubs. For such a file, additional slow operations are allowed.
     *
     *
     * Inspections should not create a new type evaluation context. They should re-use the context of the inspection session.
     */
    @JvmStatic
    fun codeAnalysis(project: Project, origin: PsiFile?): TypeEvalContext {
      return TypeEvalContextFactory.getInstance().codeAnalysis(project, origin)
    }

    /**
     * Create the most shallow type evaluation context for code insight purposes when other more detailed contexts are not available.
     * It's use should be minimized.
     *
     * @param project pass project here to enable cache. Pass null if you do not have any project.
     * **Always** do your best to pass project here: it increases performance!
     */
    @JvmStatic
    fun codeInsightFallback(project: Project?): TypeEvalContext {
      return TypeEvalContextFactory.getInstance().codeInsightFallback(project)
    }

    /**
     * Create a type evaluation context for deeper and slower code insight.
     *
     *
     * Should be used only when normal code insight context is not enough for getting good results.
     */
    @JvmStatic
    fun deepCodeInsight(project: Project): TypeEvalContext {
      return TypeEvalContextFactory.getInstance().deepCodeInsight(project)
    }

  }
}