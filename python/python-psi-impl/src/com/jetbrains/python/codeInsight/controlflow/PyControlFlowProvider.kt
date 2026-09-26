// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.controlflow

import com.intellij.codeInsight.controlflow.ControlFlow
import com.intellij.codeInsight.controlflow.ControlFlowProvider
import com.intellij.codeInsight.controlflow.Instruction
import com.intellij.psi.PsiElement
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil

class PyControlFlowProvider : ControlFlowProvider {
  override fun getControlFlow(element: PsiElement): ControlFlow? {
    val scopeOwner = ScopeUtil.getScopeOwner(element) ?: return null
    return ControlFlowCache.getControlFlow(scopeOwner)
  }

  override fun getAdditionalInfo(instruction: Instruction): String? {
    return when (instruction) {
      is ReadWriteInstruction -> "${instruction.access} ${instruction.name}"
      is RefutablePatternInstruction -> instruction.elementPresentation
      else -> null
    }
  }
}
