// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.highlighting

import com.intellij.codeInsight.daemon.RainbowVisitor
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.resolve.PyResolveContext

class PyRainbowVisitor : RainbowVisitor() {

  companion object {
    private val IGNORED_NAMES = setOf(PyNames.NONE, PyNames.TRUE, PyNames.FALSE)
    private val DEFAULT_HIGHLIGHTING_KEY = DefaultLanguageHighlighterColors.LOCAL_VARIABLE

    @JvmStatic
    val HIGHLIGHTING_KEYS = setOf(PyHighlighter.PY_PARAMETER, DEFAULT_HIGHLIGHTING_KEY)
  }

  override fun suitableForFile(file: PsiFile) = file is PyFile

  override fun visit(element: PsiElement) {
    when (element) {
      is PyReferenceExpression -> processReference(element)
      is PyTargetExpression -> processTarget(element)
      is PyNamedParameter -> processNamedParameter(element)
    }
  }

  override fun clone() = PyRainbowVisitor()

  private fun processReference(referenceExpression: PyReferenceExpression) {
    val context = getReferenceContext(referenceExpression, mutableSetOf()) ?: return
    val name = updateNameIfGlobal(context, referenceExpression.name) ?: return

    addInfo(context, referenceExpression, name)
  }

  private fun processTarget(targetExpression: PyTargetExpression) {
    val context = getTargetContext(targetExpression) ?: return
    val name = updateNameIfGlobal(context, targetExpression.name) ?: return

    addInfo(context, targetExpression, name)
  }

  private fun processNamedParameter(namedParameter: PyNamedParameter) {
    val context = getNamedParameterContext(namedParameter) ?: return
    val name = namedParameter.name ?: return
    val element = namedParameter.nameIdentifier ?: return

    addInfo(context, element, name, PyHighlighter.PY_PARAMETER)
  }

  private fun getReferenceContext(referenceExpression: PyReferenceExpression,
                                  visitedReferenceExpressions: MutableSet<PyReferenceExpression>): PsiElement? {
    if (referenceExpression.isQualified || referenceExpression.name in IGNORED_NAMES) return null

    val resolved = referenceExpression.reference.resolve()
    return when (resolved) {
      is PyTargetExpression -> getTargetContext(resolved)
      is PyNamedParameter -> getNamedParameterContext(resolved)
      is PyReferenceExpression -> {
        if (!visitedReferenceExpressions.add(resolved)) return getLeastCommonScope(visitedReferenceExpressions)
        return if (resolved.parent is PyAugAssignmentStatement) getReferenceContext(resolved, visitedReferenceExpressions) else null
      }
      else -> null
    }
  }

  private fun getTargetContext(targetExpression: PyTargetExpression): PsiElement? {
    if (targetExpression.isQualified || targetExpression.name in IGNORED_NAMES) return null

    val parent = targetExpression.parent
    if (parent is PyGlobalStatement) return targetExpression.containingFile
    if (parent is PyNonlocalStatement) {
      val outerResolved = targetExpression.reference.resolve()
      return if (outerResolved is PyTargetExpression) getTargetContext(outerResolved) else null
    }

    val resolveResults = targetExpression.getReference(PyResolveContext.noImplicits()).multiResolve(false)

    val resolvesToGlobal = resolveResults
      .asSequence()
      .map { it.element }
      .any { it is PyTargetExpression && it.parent is PyGlobalStatement }
    if (resolvesToGlobal) return targetExpression.containingFile

    val resolvedNonLocal = resolveResults
      .asSequence()
      .map { it.element }
      .filterIsInstance<PyTargetExpression>()
      .find { it.parent is PyNonlocalStatement }
    if (resolvedNonLocal != null) return getTargetContext(resolvedNonLocal)

    val scopeOwner = ScopeUtil.getScopeOwner(targetExpression)
    return if (scopeOwner is PyFile || scopeOwner is PyFunction || scopeOwner is PyLambdaExpression) scopeOwner else null
  }

  private fun getNamedParameterContext(namedParameter: PyNamedParameter): PsiElement? {
    if (namedParameter.isSelf) return null

    val scopeOwner = ScopeUtil.getScopeOwner(namedParameter)
    return if (scopeOwner is PyLambdaExpression || scopeOwner is PyFunction) scopeOwner else null
  }

  private fun updateNameIfGlobal(context: PsiElement, name: String?) = if (context is PyFile && name != null) "global_$name" else name

  private fun addInfo(context: PsiElement, rainbowElement: PsiElement, name: String, key: TextAttributesKey? = DEFAULT_HIGHLIGHTING_KEY) {
    addInfo(getInfo(context, rainbowElement, name, key))
  }

  private fun getLeastCommonScope(elements: Collection<PsiElement>): ScopeOwner? {
    var result: ScopeOwner? = null

    elements.forEach {
      val currentScopeOwner = ScopeUtil.getScopeOwner(it)
      if (result == null) {
        result = currentScopeOwner
      }
      else if (result != currentScopeOwner && currentScopeOwner != null && PsiTreeUtil.isAncestor(result, currentScopeOwner, true)) {
        result = currentScopeOwner
      }
    }

    return result
  }
}