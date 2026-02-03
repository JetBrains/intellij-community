package com.jetbrains.python.codeInsight.controlflow

import com.intellij.codeInsight.controlflow.ControlFlowBuilder
import com.intellij.codeInsight.controlflow.Instruction
import com.intellij.codeInsight.controlflow.impl.InstructionImpl

/**
 * It is an implicit raise at the end of a finally block when we got here due to exception propagation
 * or return statement in try-except-else parts.
 */
class PyFinallyFailExitInstruction(builder: ControlFlowBuilder, val begin: Instruction) : InstructionImpl(builder, begin.element) {
  override fun getElementPresentation(): String = "finally fail exit"
}