package com.jetbrains.python.codeInsight.controlflow

import com.intellij.codeInsight.controlflow.ControlFlowBuilder
import com.intellij.codeInsight.controlflow.impl.InstructionImpl
import com.jetbrains.python.psi.PyWithItem
import com.jetbrains.python.psi.types.TypeEvalContext

class PyWithContextExitInstruction(builder: ControlFlowBuilder, withItem: PyWithItem): InstructionImpl(builder, withItem) {
  override fun getElementPresentation(): String = "exit context manager: ${element.text}"
  override fun getElement(): PyWithItem = super.getElement() as PyWithItem

  /**
   * While traversing CFG, use this method to know if you should let your traversal consider this node.
   * Usually, you would want it only if the context manager DOES suppress exceptions.
   */
  fun isSuppressingExceptions(context: TypeEvalContext): Boolean = element.isSuppressingExceptions(context)
}