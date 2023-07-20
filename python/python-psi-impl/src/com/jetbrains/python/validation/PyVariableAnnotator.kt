package com.jetbrains.python.validation

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.highlighting.PyHighlighter
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.resolve.PyResolveUtil

class PyVariableAnnotator : PyAnnotator() {

  override fun visitPyTargetExpression(node: PyTargetExpression) {
    if (node.isNonLocalOrGlobal() || node.isQualified) return

    val scopeOwner = ScopeUtil.getScopeOwner(node)
    if (node.parent is PyAssignmentStatement && scopeOwner is PyFunction) {
      node.nameElement?.let { addHighlightingAnnotation(it, PyHighlighter.PY_LOCAL_VARIABLE) }
    }
  }

  override fun visitPyReferenceExpression(node: PyReferenceExpression) {
    PyResolveUtil.resolveLocally(node)
      .filterIsInstance(PyTargetExpression::class.java)
      .forEach { expression ->
        if (ScopeUtil.getScopeOwner(expression) is PyFunction && !expression.isNonLocalOrGlobal()) {
          addHighlightingAnnotation(node.node, PyHighlighter.PY_LOCAL_VARIABLE)
        }
      }
  }

  private fun PyQualifiedExpression.isNonLocalOrGlobal(): Boolean {
    val qName = this.asQualifiedName()
    val scopeOwner = ScopeUtil.getScopeOwner(this)

    if (qName != null && scopeOwner is PyFunction) {
      val scopesToLookUp = mutableListOf(scopeOwner)

      scopesToLookUp.addAll(PsiTreeUtil.findChildrenOfType(scopeOwner, PyFunction::class.java))
      scopesToLookUp.forEach { scope ->
        if (PyResolveUtil.resolveLocally(scope, qName.toString()).containsNonLocalOrGlobal()) {
          return true
        }
      }
    }
    return false
  }

  private fun Collection<PsiElement>.containsNonLocalOrGlobal(): Boolean =
    this.filterIsInstance(PyTargetExpression::class.java)
      .any { expression -> expression.parent.isNonLocalOrGlobal() }

  private fun PsiElement.isNonLocalOrGlobal(): Boolean =
    this is PyNonlocalStatement || this is PyGlobalStatement
}