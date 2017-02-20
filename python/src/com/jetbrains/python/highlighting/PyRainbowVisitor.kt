/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.highlighting

import com.intellij.codeInsight.daemon.RainbowVisitor
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

    getHighlightedParameterElements(namedParameter).forEach {
      addInfo(context, it, name, PyHighlighter.PY_PARAMETER)
    }
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

  private fun getHighlightedParameterElements(namedParameter: PyNamedParameter): List<PsiElement> {
    val nameIdentifier = namedParameter.nameIdentifier

    return if (namedParameter.isPositionalContainer || namedParameter.isKeywordContainer) {
      listOfNotNull(namedParameter.firstChild, nameIdentifier)
    }
    else {
      listOfNotNull(nameIdentifier)
    }
  }

  private fun addInfo(context: PsiElement, rainbowElement: PsiElement, name: String, colorKey: TextAttributesKey? = null) {
    addInfo(getInfo(context, rainbowElement, name, colorKey))
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