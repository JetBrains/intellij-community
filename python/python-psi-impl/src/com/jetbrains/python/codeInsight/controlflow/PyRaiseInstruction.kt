package com.jetbrains.python.codeInsight.controlflow

import com.intellij.codeInsight.controlflow.ControlFlowBuilder
import com.intellij.codeInsight.controlflow.impl.InstructionImpl
import com.jetbrains.python.psi.PyRaiseStatement

class PyRaiseInstruction(builder: ControlFlowBuilder, raiseStatement: PyRaiseStatement?): InstructionImpl(builder, raiseStatement)