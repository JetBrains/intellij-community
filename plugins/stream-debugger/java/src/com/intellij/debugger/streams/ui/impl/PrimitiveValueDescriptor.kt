// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.ui.impl

import com.intellij.debugger.DebuggerContext
import com.intellij.debugger.engine.ContextUtil
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.memory.utils.InstanceValueDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiExpression
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

internal class PrimitiveValueDescriptor internal constructor(project: Project, value: Value?) : InstanceValueDescriptor(project, value) {
  override fun calcValueName(): String? {
    val value = getValue()
    if (value == null) {
      return "value"
    }
    if (value is ObjectReference) {
      return super.calcValueName()
    }

    return value.type().name()
  }

  @Throws(EvaluateException::class)
  override fun getDescriptorEvaluation(debuggerContext: DebuggerContext?): PsiExpression? {
    val value = getValue()
    if (value is ObjectReference) {
      return super.getDescriptorEvaluation(debuggerContext)
    }

    val elementFactory = JavaPsiFacade.getInstance(myProject).getElementFactory()
    return elementFactory.createExpressionFromText(value.toString(), ContextUtil.getContextElement(debuggerContext))
  }
}
