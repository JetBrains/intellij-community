package com.jetbrains.python.codeInsight.controlflow

import com.intellij.codeInsight.controlflow.ControlFlowBuilder
import com.intellij.codeInsight.controlflow.impl.InstructionImpl
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.impl.PyCallExpressionHelper
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyNeverType
import com.jetbrains.python.psi.types.PyTypeUtil
import com.jetbrains.python.psi.types.TypeEvalContext

class CallInstruction(builder: ControlFlowBuilder, call: PyCallExpression) : InstructionImpl(builder, call) {
  override fun getElement(): PyCallExpression {
    return super.getElement() as PyCallExpression
  }

  fun isNoReturnCall(context: TypeEvalContext): Boolean {
    val callees = element.multiResolveCalleeFunction(PyResolveContext.defaultContext(context))
    if (callees.size == 1) {
      val pyFunction = callees.single()
      return pyFunction is PyFunction &&
             hasReturnTypeAnnotation(pyFunction) &&
             context.getReturnType(pyFunction) is PyNeverType
    }
    if (callees.isNotEmpty()) return false

    // The callee is not a plain function, e.g. an attribute annotated with `Callable[...]`
    // or an instance whose `__call__` is such an attribute. Its return type always comes from an annotation.
    val callee = element.callee ?: return false
    val calleeType = PyCallExpressionHelper.getCalleeType(callee, PyResolveContext.defaultContext(context))
    val signatures = PyTypeUtil.getCallableItems(calleeType)
    return signatures.isNotEmpty() && signatures.all { it.getReturnType(context) is PyNeverType }
  }
}

private fun hasReturnTypeAnnotation(function: PyFunction): Boolean {
  return function.annotation != null || function.typeCommentAnnotation != null
}