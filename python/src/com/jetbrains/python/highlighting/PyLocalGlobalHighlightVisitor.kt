// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.HighlightInfoType.HighlightInfoTypeImpl
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.highlighting.PyLocalGlobalHighlightVisitor.Holder.LG_MARKER
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.TypeEvalContext

class PyLocalGlobalHighlightVisitor: HighlightVisitor {

  object Holder {
    val LG_MARKER: HighlightInfoType = HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, DefaultLanguageHighlighterColors.LOCAL_VARIABLE)
  }

  private var myHolder: HighlightInfoHolder? = null

  override fun suitableForFile(file: PsiFile): Boolean = file is PyFile

  override fun visit(element: PsiElement) {
    when (element) {
      is PyReferenceExpression -> processReference(element)
      is PyTargetExpression -> processTarget(element)
    }
  }

  override fun analyze(file: PsiFile, updateWholeFile: Boolean, holder: HighlightInfoHolder, action: Runnable): Boolean {
    myHolder = holder
    try {
      action.run()
    }
    finally {
      myHolder = null
    }
    return true
  }

  private fun addInfo(element: PsiElement, context: PsiElement) {
    val attributes = getTextAttributes(element, context) ?: return
    myHolder!!.add(createInfo(element, attributes))
  }

  private fun createInfo(element: PsiElement, attributes: TextAttributesKey): HighlightInfo? {
    return HighlightInfo
      .newHighlightInfo(LG_MARKER)
      .textAttributes(attributes)
      .range(element)
      .create()
  }

  private fun getTextAttributes(element: PsiElement, context: PsiElement): TextAttributesKey? {
    return if (element.parent is PyGlobalStatement || context is PyFile) {
      DefaultLanguageHighlighterColors.GLOBAL_VARIABLE
    } else DefaultLanguageHighlighterColors.LOCAL_VARIABLE
  }

  override fun clone(): HighlightVisitor = PyLocalGlobalHighlightVisitor()

  private fun processReference(referenceExpression: PyReferenceExpression) {
    val context = getReferenceContext(referenceExpression, mutableSetOf()) ?: return
    addInfo(referenceExpression, context)
  }

  private fun processTarget(targetExpression: PyTargetExpression) {
    val context = getTargetContext(targetExpression) ?: return
    addInfo(targetExpression, context)
  }

  private fun getReferenceContext(referenceExpression: PyReferenceExpression, visitedReferenceExpressions: MutableSet<PyReferenceExpression>): PsiElement? {
    if (referenceExpression.isQualified) return null

    return when (val resolved = referenceExpression.reference.resolve()) {
      is PyTargetExpression -> getTargetContext(resolved)
      is PyReferenceExpression -> {
        if (!visitedReferenceExpressions.add(resolved)) return getLeastCommonScope(visitedReferenceExpressions)
        return if (resolved.parent is PyAugAssignmentStatement) getReferenceContext(resolved, visitedReferenceExpressions) else null
      }
      else -> null
    }
  }

  private fun getTargetContext(targetExpression: PyTargetExpression): PsiElement? {
    if (targetExpression.isQualified) return null

    val parent = targetExpression.parent
    if (parent is PyGlobalStatement) return targetExpression.containingFile
    if (parent is PyNonlocalStatement) {
      val outerResolved = targetExpression.reference.resolve()
      return if (outerResolved is PyTargetExpression && outerResolved != targetExpression) getTargetContext(outerResolved) else null
    }

    val context = TypeEvalContext.codeInsightFallback(targetExpression.project)
    val resolveResults = targetExpression.getReference(PyResolveContext.defaultContext(context)).multiResolve(false)

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