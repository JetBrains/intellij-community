// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.controlflow

import com.intellij.codeInsight.controlflow.ControlFlowBuilder
import com.intellij.codeInsight.controlflow.impl.InstructionImpl
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PyUnreachableInstruction(builder: ControlFlowBuilder, val isUnreachableForTypeChecking: Boolean) : InstructionImpl(builder, null) {
  override fun getElementPresentation(): String {
    return if (isUnreachableForTypeChecking) "Unreachable for type checking" else "Unreachable"
  }
}