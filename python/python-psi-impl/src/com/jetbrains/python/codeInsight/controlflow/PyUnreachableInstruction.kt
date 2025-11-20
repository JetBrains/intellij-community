package com.jetbrains.python.codeInsight.controlflow

import com.intellij.codeInsight.controlflow.ControlFlowBuilder
import com.intellij.codeInsight.controlflow.impl.InstructionImpl

class PyUnreachableInstruction(builder: ControlFlowBuilder, val isUnreachableForTypeChecking: Boolean) : InstructionImpl(builder, null) {
  override fun getElementPresentation(): String {
    return if (isUnreachableForTypeChecking) "Unreachable for type checking" else "Unreachable"
  }
}