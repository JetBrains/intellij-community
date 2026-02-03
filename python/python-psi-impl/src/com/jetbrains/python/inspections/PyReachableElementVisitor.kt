package com.jetbrains.python.inspections

import com.jetbrains.python.codeInsight.controlflow.Reachability
import com.jetbrains.python.codeInsight.controlflow.getReachabilityForInspection
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.types.TypeEvalContext

class PyReachableElementVisitor(
  private val delegate: PyElementVisitor, 
  private val context: TypeEvalContext
) : PyElementVisitor() {
  override fun visitPyElement(node: PyElement) {
    if (node.getReachabilityForInspection(context) == Reachability.REACHABLE) {
      node.accept(delegate)
    }
  }
}