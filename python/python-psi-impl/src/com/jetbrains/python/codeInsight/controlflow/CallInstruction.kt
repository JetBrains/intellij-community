package com.jetbrains.python.codeInsight.controlflow

import com.intellij.codeInsight.controlflow.ControlFlowBuilder
import com.intellij.codeInsight.controlflow.impl.InstructionImpl
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyNeverType
import com.jetbrains.python.psi.types.TypeEvalContext

class CallInstruction(builder: ControlFlowBuilder, call: PyCallExpression) : InstructionImpl(builder, call) {
  override fun getElement(): PyCallExpression {
    return super.getElement() as PyCallExpression
  }

  fun isNoReturnCall(context: TypeEvalContext): Boolean {
    val callees = element.multiResolveCalleeFunction(PyResolveContext.defaultContext(context))
    if (callees.size == 1) {
      val pyFunction = callees.single()
      if (pyFunction is PyFunction) {
        return context.getReturnType(pyFunction) is PyNeverType
      }
    }
    return false
  }
}