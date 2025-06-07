package com.jetbrains.python.codeInsight.controlflow

import com.intellij.codeInsight.controlflow.ControlFlowBuilder
import com.intellij.codeInsight.controlflow.impl.InstructionImpl
import com.jetbrains.python.psi.PyAssertStatement
import com.jetbrains.python.psi.PyRaiseStatement
import com.jetbrains.python.psi.PyStatement

class PyRaiseInstruction private constructor(builder: ControlFlowBuilder, raiseOrAssert: PyStatement): InstructionImpl(builder, raiseOrAssert) {
  constructor(builder: ControlFlowBuilder, raise: PyRaiseStatement): this(builder, raise as PyStatement)
  constructor(builder: ControlFlowBuilder, assert: PyAssertStatement): this(builder, assert as PyStatement)

  override fun getElementPresentation(): String = "raise: ${element}"
}