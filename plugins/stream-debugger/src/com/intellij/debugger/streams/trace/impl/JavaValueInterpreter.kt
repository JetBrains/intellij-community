// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.impl

import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.core.StreamDebuggerBundle
import com.intellij.debugger.streams.core.trace.GenericEvaluationContext
import com.intellij.debugger.streams.core.trace.XValueInterpreter
import com.intellij.openapi.project.Project
import com.intellij.psi.CommonClassNames
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.frame.XValue

class JavaEvaluationContext(val context: EvaluationContextImpl) : GenericEvaluationContext {
  override val project: Project
    get() = context.project
}

class JavaValueInterpreter : XValueInterpreter {
  override suspend fun extract(session: XDebugSession, result: XValue): XValueInterpreter.Result {
    if (result is JavaValue) {
      val reference = result.descriptor.getValue()
      if (reference is com.sun.jdi.ArrayReference) {
        return XValueInterpreter.Result.Array(JvmArrayReference(reference), hasInnerExceptions(reference), JavaEvaluationContext(result.evaluationContext))
      } else if (reference is com.sun.jdi.ObjectReference) {
        val type = reference.referenceType()
        var classType = type as? com.sun.jdi.ClassType
        if (classType != null) {
          while (classType != null && CommonClassNames.JAVA_LANG_THROWABLE != classType.name()) {
            classType = classType.superclass()
          }
          if (classType != null) {
            val exceptionMessage: String? = DebuggerUtils.tryExtractExceptionMessage(reference)
            val descriptionWithReason = if (exceptionMessage == null)
              StreamDebuggerBundle.message("stream.debugger.evaluation.failed.with.exception", type.name())
            else
              StreamDebuggerBundle.message("stream.debugger.evaluation.failed.with.exception.and.message", type.name(), exceptionMessage)
            return XValueInterpreter.Result.Error(descriptionWithReason)
          }
        }
      }
    }
    return XValueInterpreter.Result.Unknown
  }

  private fun hasInnerExceptions(resultArray: com.sun.jdi.ArrayReference): Boolean {
    val result = resultArray.getValue(1) as com.sun.jdi.ArrayReference
    val type: com.sun.jdi.ReferenceType = result.referenceType()
    if (type is com.sun.jdi.ArrayType) {
      if (type.componentTypeName().contains("Throwable")) {
        return true
      }
    }

    return false
  }
}