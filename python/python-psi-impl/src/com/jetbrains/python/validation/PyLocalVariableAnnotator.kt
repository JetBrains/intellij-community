package com.jetbrains.python.validation

import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil
import com.jetbrains.python.highlighting.PyHighlighter
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.resolve.PyResolveUtil

class PyLocalVariableAnnotator : PyAnnotator() {

  override fun visitPyTargetExpression(node: PyTargetExpression) {
    if (!node.isLocalVariable()) return
    node.nameElement?.let { addHighlightingAnnotation(it, PyHighlighter.PY_LOCAL_VARIABLE) }
  }

  override fun visitPyReferenceExpression(node: PyReferenceExpression) {
    if (PyResolveUtil.resolveLocally(node).any { it is PyTargetExpression && it.isLocalVariable() }) {
      addHighlightingAnnotation(node.node, PyHighlighter.PY_LOCAL_VARIABLE)
    }
  }

  private fun PyTargetExpression.isLocalVariable(): Boolean {
    if (isQualified) return false
    val name = referencedName ?: return false
    if (name == PyNames.UNDERSCORE) return false
    val scopeOwner = ScopeUtil.getScopeOwner(this)
    if (scopeOwner !is PyFunction) return false
    val scope = ControlFlowCache.getScope(scopeOwner)
    return !(scope.isNonlocal(name) || scope.isGlobal(name))
  }
}